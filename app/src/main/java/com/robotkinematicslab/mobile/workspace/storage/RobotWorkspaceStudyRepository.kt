package com.robotkinematicslab.mobile.workspace.storage

import android.content.Context
import com.robotkinematicslab.mobile.dataset.RobotLibraryCodec
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceSample
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceConvergenceCheckpoint
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceSampleKind
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceSamplingProtocol
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass
import com.robotkinematicslab.mobile.workspace.analysis.robotWorkspaceFingerprint
import com.robotkinematicslab.mobile.math.utility.Vec3
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Properties

class RobotWorkspaceStudyRepository(
    private val studiesDirectory: File,
    private val robotCodec: RobotLibraryCodec = RobotLibraryCodec()
) {

    constructor(context: Context) : this(
        AppStoragePaths(context)
            .also(AppStoragePaths::ensureStructureAndMigrateLegacyData)
            .workspaceStudiesDirectory
    )

    @Synchronized
    fun save(study: RobotWorkspaceStudy): RobotWorkspaceStudySummary {
        validateStudy(study)
        require(studiesDirectory.mkdirs() || studiesDirectory.isDirectory) { "Workspace study storage is unavailable." }
        val directory = File(studiesDirectory, study.studyId)
        require(!directory.exists()) { "Workspace study already exists: ${study.studyId}" }
        val stagingDirectory = Files.createTempDirectory(studiesDirectory.toPath(), ".${study.studyId}-").toFile()
        val dataFile = File(directory, DATA_FILE_NAME)
        val summary = study.toSummary(directory, dataFile)
        try {
            writeStudy(File(stagingDirectory, DATA_FILE_NAME), study)
            writeSummary(File(stagingDirectory, MANIFEST_FILE_NAME), summary)
            moveWithoutReplacing(stagingDirectory, directory)
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }
        return summary
    }

    @Synchronized
    fun listStudies(): List<RobotWorkspaceStudySummary> {
        studiesDirectory.mkdirs()
        return studiesDirectory.listFiles(File::isDirectory)
            ?.mapNotNull { directory -> readSummary(File(directory, MANIFEST_FILE_NAME), directory) }
            ?.sortedByDescending(RobotWorkspaceStudySummary::createdAtEpochMillis)
            .orEmpty()
    }

    @Synchronized
    fun load(summary: RobotWorkspaceStudySummary): RobotWorkspaceStudy {
        val directory = File(summary.directoryPath)
        require(directory.canonicalFile.parentFile == studiesDirectory.canonicalFile) {
            "Workspace study is outside the managed storage directory."
        }
        val currentSummary =
            requireNotNull(readSummary(File(directory, MANIFEST_FILE_NAME), directory)) {
                "Workspace study manifest is missing or invalid."
            }
        require(currentSummary.studyId == summary.studyId) { "Workspace study identity changed on disk." }
        return readStudy(File(currentSummary.dataPath)).also { study ->
            validateStudy(study)
            require(study.studyId == currentSummary.studyId) { "Workspace data identity differs from its manifest." }
            require(study.studyName == currentSummary.studyName) { "Workspace study name differs from its manifest." }
            require(study.robot.name == currentSummary.robotName) { "Workspace robot name differs from its manifest." }
            require(study.robotFingerprint == currentSummary.robotFingerprint) {
                "Workspace robot fingerprint differs from its manifest."
            }
            require(study.createdAtEpochMillis == currentSummary.createdAtEpochMillis) {
                "Workspace timestamp differs from its manifest."
            }
            require(study.config.sampleCount == currentSummary.sampleCount) {
                "Workspace sample count differs from its manifest."
            }
            require(study.config.voxelResolution == currentSummary.voxelResolution) {
                "Workspace voxel resolution differs from its manifest."
            }
            require(study.observedEnvelopeFraction == currentSummary.observedEnvelopeFraction) {
                "Workspace envelope fraction differs from its manifest."
            }
        }
    }

    private fun writeStudy(file: File, study: RobotWorkspaceStudy) {
        DataOutputStream(FileOutputStream(file).buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(DATA_SCHEMA_VERSION)
            output.writeUTF(study.studyId)
            output.writeUTF(study.studyName)
            output.writeLong(study.createdAtEpochMillis)
            output.writeUTF(study.robotFingerprint)

            val robotBytes = ByteArrayOutputStream().also { bytes ->
                robotCodec.write(listOf(SavedRobot(study.robotFingerprint, study.robot)), bytes)
            }.toByteArray()
            output.writeInt(robotBytes.size)
            output.write(robotBytes)

            output.writeInt(study.config.sampleCount)
            output.writeInt(study.config.randomSeed)
            output.writeInt(study.config.voxelResolution)
            output.writeInt(study.config.replicationCount)
            output.writeUTF(study.config.samplingProtocol.name)
            output.writeInt(study.workerCount)
            output.writeLong(study.durationMillis)
            output.writeDouble(study.conservativeRadiusMeters)
            output.writeDouble(study.voxelCellSizeMeters)
            output.writeDouble(study.conservativeSphereVolumeCubicMeters)
            output.writeDouble(study.classifiedEnvelopeVolumeCubicMeters)
            output.writeDouble(study.observedVoxelVolumeCubicMeters)
            output.writeDouble(study.unobservedCandidateVolumeCubicMeters)
            output.writeDouble(study.observedEnvelopeFraction)
            output.writeDouble(study.lastQuarterRelativeVolumeGain)
            output.writeInt(study.invalidSampleCount)

            output.writeInt(study.samples.size)
            study.samples.forEach { sample ->
                output.writeInt(sample.sequenceIndex)
                output.writeInt(sample.replicationIndex)
                output.writeUTF(sample.kind.name)
                output.writeInt(sample.jointValues.size)
                sample.jointValues.forEach(output::writeDouble)
                output.writeVec3(sample.endEffector)
            }

            output.writeInt(study.voxels.size)
            study.voxels.forEach { voxel ->
                output.writeInt(voxel.xIndex)
                output.writeInt(voxel.yIndex)
                output.writeInt(voxel.zIndex)
                output.writeVec3(voxel.center)
                output.writeUTF(voxel.classification.name)
                output.writeInt(voxel.sampleHitCount)
                output.writeInt(voxel.firstObservedSampleIndex ?: NO_SAMPLE_INDEX)
            }

            output.writeInt(study.convergence.size)
            study.convergence.forEach { checkpoint ->
                output.writeInt(checkpoint.sampleCount)
                output.writeInt(checkpoint.occupiedVoxelCount)
                output.writeDouble(checkpoint.observedVoxelVolumeCubicMeters)
            }
        }
    }

    private fun readStudy(file: File): RobotWorkspaceStudy {
        require(file.isFile) { "Workspace study data is missing." }
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == MAGIC) { "Unknown workspace study format." }
            require(input.readInt() == DATA_SCHEMA_VERSION) { "Unsupported workspace study schema." }
            val studyId = input.readUTF()
            val studyName = input.readUTF()
            val createdAt = input.readLong()
            val fingerprint = input.readUTF()
            val robotByteCount = input.readInt()
            require(robotByteCount in 1..MAXIMUM_ROBOT_BYTES) { "Invalid stored robot payload size." }
            val robotBytes = ByteArray(robotByteCount)
            input.readFully(robotBytes)
            val robot =
                robotCodec.read(ByteArrayInputStream(robotBytes)).single().also { saved ->
                    require(saved.id == fingerprint) { "Stored workspace robot identity is inconsistent." }
                }.robot

            val config =
                RobotWorkspaceAnalysisConfig(
                    sampleCount = input.readInt(),
                    randomSeed = input.readInt(),
                    voxelResolution = input.readInt(),
                    replicationCount = input.readInt(),
                    samplingProtocol = WorkspaceSamplingProtocol.valueOf(input.readUTF())
                )
            val workerCount = input.readInt()
            val durationMillis = input.readLong()
            val radius = input.readDouble()
            val cellSize = input.readDouble()
            val sphereVolume = input.readDouble()
            val classifiedVolume = input.readDouble()
            val observedVolume = input.readDouble()
            val unobservedVolume = input.readDouble()
            val observedFraction = input.readDouble()
            val lastQuarterGain = input.readDouble()
            val invalidSamples = input.readInt()

            val sampleCount = input.readInt()
            require(sampleCount in 1..RobotWorkspaceAnalysisConfig.MAXIMUM_SAMPLE_COUNT)
            val samples =
                List(sampleCount) {
                    val sequenceIndex = input.readInt()
                    val replicationIndex = input.readInt()
                    val kind = WorkspaceSampleKind.valueOf(input.readUTF())
                    val jointCount = input.readInt()
                    require(jointCount == robot.joints.size && jointCount in 1..MAXIMUM_JOINTS)
                    RobotWorkspaceSample(
                        sequenceIndex = sequenceIndex,
                        replicationIndex = replicationIndex,
                        kind = kind,
                        jointValues = List(jointCount) { input.readDouble() },
                        endEffector = input.readVec3()
                    )
                }

            val voxelCount = input.readInt()
            require(voxelCount in 1..MAXIMUM_VOXELS)
            val voxels =
                List(voxelCount) {
                    RobotWorkspaceVoxel(
                        xIndex = input.readInt(),
                        yIndex = input.readInt(),
                        zIndex = input.readInt(),
                        center = input.readVec3(),
                        classification = WorkspaceVoxelClass.valueOf(input.readUTF()),
                        sampleHitCount = input.readInt(),
                        firstObservedSampleIndex = input.readInt().takeUnless { it == NO_SAMPLE_INDEX }
                    )
                }

            val checkpointCount = input.readInt()
            require(checkpointCount in 1..MAXIMUM_CHECKPOINTS)
            val convergence =
                List(checkpointCount) {
                    WorkspaceConvergenceCheckpoint(
                        sampleCount = input.readInt(),
                        occupiedVoxelCount = input.readInt(),
                        observedVoxelVolumeCubicMeters = input.readDouble()
                    )
                }
            require(input.read() == -1) { "Workspace study contains unexpected trailing data." }

            return RobotWorkspaceStudy(
                studyId = studyId,
                studyName = studyName,
                createdAtEpochMillis = createdAt,
                robotFingerprint = fingerprint,
                robot = robot,
                config = config,
                workerCount = workerCount,
                durationMillis = durationMillis,
                conservativeRadiusMeters = radius,
                voxelCellSizeMeters = cellSize,
                conservativeSphereVolumeCubicMeters = sphereVolume,
                classifiedEnvelopeVolumeCubicMeters = classifiedVolume,
                observedVoxelVolumeCubicMeters = observedVolume,
                unobservedCandidateVolumeCubicMeters = unobservedVolume,
                observedEnvelopeFraction = observedFraction,
                lastQuarterRelativeVolumeGain = lastQuarterGain,
                invalidSampleCount = invalidSamples,
                samples = samples,
                voxels = voxels,
                convergence = convergence
            )
        }
    }

    private fun writeSummary(file: File, summary: RobotWorkspaceStudySummary) {
        val properties =
            Properties().apply {
                setProperty("schemaVersion", MANIFEST_SCHEMA_VERSION.toString())
                setProperty("studyId", summary.studyId)
                setProperty("studyName", summary.studyName)
                setProperty("robotName", summary.robotName)
                setProperty("robotFingerprint", summary.robotFingerprint)
                setProperty("createdAtEpochMillis", summary.createdAtEpochMillis.toString())
                setProperty("sampleCount", summary.sampleCount.toString())
                setProperty("voxelResolution", summary.voxelResolution.toString())
                setProperty("observedEnvelopeFraction", summary.observedEnvelopeFraction.toString())
                setProperty("dataPath", summary.dataPath)
            }
        file.outputStream().buffered().use { properties.store(it, "Robot Kinematics Lab workspace study") }
    }

    private fun readSummary(file: File, directory: File): RobotWorkspaceStudySummary? {
        if (!file.isFile) return null
        return runCatching {
            val properties = Properties().apply { file.inputStream().buffered().use(::load) }
            require(properties.getProperty("schemaVersion").toInt() == MANIFEST_SCHEMA_VERSION)
            val studyId = properties.getProperty("studyId").also { require(it == directory.name && SAFE_ID.matches(it)) }
            val dataFile = File(directory, DATA_FILE_NAME)
            require(dataFile.isFile)
            RobotWorkspaceStudySummary(
                studyId = studyId,
                studyName = properties.getProperty("studyName").also { require(it.isNotBlank()) },
                robotName = properties.getProperty("robotName").also { require(it.isNotBlank()) },
                robotFingerprint = properties.getProperty("robotFingerprint").also { require(it.isNotBlank()) },
                createdAtEpochMillis = properties.getProperty("createdAtEpochMillis").toLong().also { require(it >= 0L) },
                sampleCount = properties.getProperty("sampleCount").toInt().also {
                    require(it in RobotWorkspaceAnalysisConfig.MINIMUM_SAMPLE_COUNT..RobotWorkspaceAnalysisConfig.MAXIMUM_SAMPLE_COUNT)
                },
                voxelResolution = properties.getProperty("voxelResolution").toInt().also {
                    require(it in RobotWorkspaceAnalysisConfig.MINIMUM_VOXEL_RESOLUTION..RobotWorkspaceAnalysisConfig.MAXIMUM_VOXEL_RESOLUTION)
                },
                observedEnvelopeFraction = properties.getProperty("observedEnvelopeFraction").toDouble().also {
                    require(it.isFinite() && it in 0.0..1.0)
                },
                directoryPath = directory.absolutePath,
                dataPath = dataFile.absolutePath
            )
        }.getOrNull()
    }

    private fun validateStudy(study: RobotWorkspaceStudy) {
        require(SAFE_ID.matches(study.studyId)) { "Workspace study identifier is not storage-safe." }
        require(study.createdAtEpochMillis > 0L) { "Workspace study timestamp must be positive." }
        require(robotWorkspaceFingerprint(study.robot) == study.robotFingerprint) {
            "Workspace study robot fingerprint does not match its definition."
        }
        require(study.samples.all { sample ->
            sample.jointValues.size == study.robot.joints.size &&
                sample.jointValues.indices.all { index ->
                    sample.jointValues[index] in study.robot.joints[index].minValue..study.robot.joints[index].maxValue
                } &&
                sample.endEffector.norm() <=
                    study.conservativeRadiusMeters +
                    maxOf(POSITION_EPSILON, study.conservativeRadiusMeters * RELATIVE_POSITION_EPSILON)
        }) { "Workspace study contains a sample outside its scientific robot contract." }
        require(study.voxels.all { voxel ->
            voxel.xIndex >= 0 &&
                voxel.yIndex >= 0 &&
                voxel.zIndex >= 0 &&
                voxel.xIndex < study.config.voxelResolution &&
                voxel.yIndex < study.config.voxelResolution &&
                voxel.zIndex < study.config.voxelResolution
        }) { "Workspace study contains an invalid voxel coordinate." }
    }

    private fun RobotWorkspaceStudy.toSummary(directory: File, dataFile: File): RobotWorkspaceStudySummary =
        RobotWorkspaceStudySummary(
            studyId = studyId,
            studyName = studyName,
            robotName = robot.name,
            robotFingerprint = robotFingerprint,
            createdAtEpochMillis = createdAtEpochMillis,
            sampleCount = config.sampleCount,
            voxelResolution = config.voxelResolution,
            observedEnvelopeFraction = observedEnvelopeFraction,
            directoryPath = directory.absolutePath,
            dataPath = dataFile.absolutePath
        )

    private fun DataOutputStream.writeVec3(value: Vec3) {
        writeDouble(value.x)
        writeDouble(value.y)
        writeDouble(value.z)
    }

    private fun DataInputStream.readVec3(): Vec3 = Vec3(readDouble(), readDouble(), readDouble())

    private fun moveWithoutReplacing(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath())
    }

    companion object {
        private const val MAGIC = 0x524B5753
        private const val DATA_SCHEMA_VERSION = 1
        private const val MANIFEST_SCHEMA_VERSION = 1
        private const val MANIFEST_FILE_NAME = "manifest.properties"
        private const val DATA_FILE_NAME = "workspace-study.rkws"
        private const val MAXIMUM_ROBOT_BYTES = 1_000_000
        private const val MAXIMUM_JOINTS = 10
        private const val MAXIMUM_VOXELS = 40_000
        private const val MAXIMUM_CHECKPOINTS = 16
        private const val NO_SAMPLE_INDEX = -1
        private const val POSITION_EPSILON = 1e-8
        private const val RELATIVE_POSITION_EPSILON = 1e-10
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,160}")
    }
}
