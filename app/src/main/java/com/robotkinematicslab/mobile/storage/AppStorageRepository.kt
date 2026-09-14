package com.robotkinematicslab.mobile.storage

import android.content.Context
import com.robotkinematicslab.mobile.dataset.RobotLibraryCodec
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticCaseResultCsvWriter
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunHistoryCsvWriter
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.input.TargetPositionValidator
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties

data class RobotLabSavedState(
    val robot: RobotDefinition,
    val jointValues: List<Double>,
    val target: Vec3?,
    val controlMode: String,
    val updatedAtEpochMillis: Long
)

data class DiagnosticDraft(
    val reachableCountText: String = "4",
    val unreachableCountText: String = "4",
    val robotLinkCountText: String = "3",
    val minLinkCountText: String = "2",
    val maxLinkCountText: String = "10",
    val samplesPerLinkCountText: String = "250",
    val unlimitedSampleCountText: String = "5000",
    val seedText: String = "42",
    val experimentalMode: Boolean = false,
    val unlimitedSamplesEnabled: Boolean = false,
    val manualRangeMode: Boolean = false,
    val jointMode: DiagnosticJointMode = DiagnosticJointMode.AUTO,
    val runAllTopologies: Boolean = false,
    val storeFullRunHistory: Boolean = true,
    val stressLevel: Float = 0.5f,
    val ikMaxIterationsText: String = "800",
    val ikToleranceText: String = "0.00001",
    val ikDampingText: String = "0.05",
    val ikMaxStepText: String = "0.02"
)

data class DatasetBuilderDraft(
    val datasetName: String = "robot_kinematics_experiment",
    val samplesPerRobotText: String = "20000",
    val randomSeedText: String = "42",
    val reachablePercentText: String = "70",
    val targetMode: DatasetTargetMode = DatasetTargetMode.MIXED,
    val filterMode: DatasetFilterMode = DatasetFilterMode.ALL,
    val appendToExisting: Boolean = true,
    val maxIterationsText: String = "800",
    val toleranceText: String = "0.00001",
    val dampingText: String = "0.05",
    val maxStepText: String = "0.02",
    val selectedRobotIds: Set<String> = emptySet()
)

data class DiagnosticSessionSummary(
    val id: String,
    val experimentName: String,
    val createdAtEpochMillis: Long,
    val verdict: String,
    val runCount: Int,
    val caseCount: Int,
    val seeds: List<Int>,
    val linkCounts: List<Int>,
    val directoryPath: String,
    val runHistoryPath: String,
    val readableReportPath: String
)

enum class StorageCategory(
    val title: String,
    val description: String
) {
    ROBOTS("Robots", "Saved DH definitions and the current Robot Lab workspace."),
    SESSIONS("Sessions", "Diagnostic reports, full run histories and readable summaries."),
    DATASETS("Datasets", "Scientific CSV datasets and their reproducibility manifests."),
    MODELS("AI models", "Reserved registry for trained model artifacts and metadata."),
    TRAINING("Training", "Reserved registry for training runs, checkpoints and evaluations.")
}

data class StorageCategorySummary(
    val category: StorageCategory,
    val fileCount: Int,
    val byteCount: Long,
    val lastUpdatedEpochMillis: Long?,
    val directoryPath: String
)

data class AppStorageSnapshot(
    val rootPath: String,
    val categories: List<StorageCategorySummary>,
    val diagnosticSessions: List<DiagnosticSessionSummary>
)

class AppStorageRepository(
    private val paths: AppStoragePaths,
    private val robotCodec: RobotLibraryCodec = RobotLibraryCodec()
) {

    constructor(context: Context) : this(AppStoragePaths(context))

    init {
        paths.ensureStructureAndMigrateLegacyData()
    }

    @Synchronized
    fun saveRobotLabState(state: RobotLabSavedState) {
        require(RobotDefinitionValidator().validate(state.robot).isValid) {
            "Cannot save an invalid Robot Lab robot."
        }
        require(RobotStateValidator().validate(state.robot, RobotState(state.jointValues)).isValid) {
            "Cannot save invalid Robot Lab joint values."
        }
        require(state.target == null || TargetPositionValidator().isValid(state.target)) {
            "Cannot save a non-finite Robot Lab target."
        }
        require(state.controlMode == "FK" || state.controlMode == "IK") {
            "Robot Lab control mode must be FK or IK."
        }
        require(state.updatedAtEpochMillis > 0L) { "Robot Lab timestamp must be positive." }
        paths.robotLabDirectory.mkdirs()
        val robotFile = File(paths.robotLabDirectory, ROBOT_LAB_ROBOT_FILE)
        val robotSha256 = AtomicFilePublisher.write(robotFile) { robotTemporary ->
            robotTemporary.outputStream().buffered().use { output ->
                robotCodec.write(
                    robots = listOf(SavedRobot(id = "robot-lab-current", robot = state.robot)),
                    output = output
                )
            }
            sha256(robotTemporary)
        }

        val properties =
            Properties().apply {
                setProperty("schemaVersion", ROBOT_LAB_SCHEMA_VERSION.toString())
                setProperty("robotSha256", robotSha256)
                setProperty("jointValues", state.jointValues.joinToString(UNIT_SEPARATOR))
                setProperty("controlMode", state.controlMode)
                setProperty("updatedAtEpochMillis", state.updatedAtEpochMillis.toString())
                state.target?.let { target ->
                    setProperty("targetX", target.x.toString())
                    setProperty("targetY", target.y.toString())
                    setProperty("targetZ", target.z.toString())
                }
            }
        writePropertiesAtomically(
            file = File(paths.robotLabDirectory, ROBOT_LAB_STATE_FILE),
            properties = properties,
            comment = "Robot Kinematics Lab current workspace"
        )
    }

    @Synchronized
    fun loadRobotLabState(): RobotLabSavedState? {
        val robotFile = File(paths.robotLabDirectory, ROBOT_LAB_ROBOT_FILE)
        val stateFile = File(paths.robotLabDirectory, ROBOT_LAB_STATE_FILE)
        if (!robotFile.exists() || !stateFile.exists()) return null

        return runCatching {
            val robot = robotFile.inputStream().buffered().use(robotCodec::read).single().robot
            val properties = readProperties(stateFile)
            val schemaVersion = requireRobotLabSchema(properties)
            if (schemaVersion >= ROBOT_LAB_INTEGRITY_SCHEMA_VERSION) {
                require(properties.getProperty("robotSha256") == sha256(robotFile)) {
                    "Saved Robot Lab robot and workspace metadata do not belong together."
                }
            }
            val joints =
                properties.getProperty("jointValues", "")
                    .split(UNIT_SEPARATOR)
                    .mapNotNull(String::toDoubleOrNull)
            require(joints.size == robot.joints.size) {
                "Saved Robot Lab joint count does not match its robot."
            }
            val target =
                if (properties.containsKey("targetX") &&
                    properties.containsKey("targetY") &&
                    properties.containsKey("targetZ")
                ) {
                    Vec3(
                        properties.getProperty("targetX").toDouble(),
                        properties.getProperty("targetY").toDouble(),
                        properties.getProperty("targetZ").toDouble()
                    )
                } else {
                    null
                }

            require(RobotDefinitionValidator().validate(robot).isValid)
            require(RobotStateValidator().validate(robot, RobotState(joints)).isValid)
            require(target == null || TargetPositionValidator().isValid(target))
            val controlMode = properties.getProperty("controlMode", "IK")
            require(controlMode == "FK" || controlMode == "IK")

            RobotLabSavedState(
                robot = robot,
                jointValues = joints,
                target = target,
                controlMode = controlMode,
                updatedAtEpochMillis = properties.getProperty("updatedAtEpochMillis").toLong().also {
                    require(it > 0L)
                }
            )
        }.getOrNull()
    }

    @Synchronized
    fun saveDiagnosticDraft(draft: DiagnosticDraft) {
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("reachableCountText", draft.reachableCountText)
                setProperty("unreachableCountText", draft.unreachableCountText)
                setProperty("robotLinkCountText", draft.robotLinkCountText)
                setProperty("minLinkCountText", draft.minLinkCountText)
                setProperty("maxLinkCountText", draft.maxLinkCountText)
                setProperty("samplesPerLinkCountText", draft.samplesPerLinkCountText)
                setProperty("unlimitedSampleCountText", draft.unlimitedSampleCountText)
                setProperty("seedText", draft.seedText)
                setProperty("experimentalMode", draft.experimentalMode.toString())
                setProperty("unlimitedSamplesEnabled", draft.unlimitedSamplesEnabled.toString())
                setProperty("manualRangeMode", draft.manualRangeMode.toString())
                setProperty("jointMode", draft.jointMode.name)
                setProperty("runAllTopologies", draft.runAllTopologies.toString())
                setProperty("storeFullRunHistory", draft.storeFullRunHistory.toString())
                setProperty("stressLevel", draft.stressLevel.toString())
                setProperty("ikMaxIterationsText", draft.ikMaxIterationsText)
                setProperty("ikToleranceText", draft.ikToleranceText)
                setProperty("ikDampingText", draft.ikDampingText)
                setProperty("ikMaxStepText", draft.ikMaxStepText)
            }
        writePropertiesAtomically(
            file = File(paths.preferencesDirectory, DIAGNOSTIC_DRAFT_FILE),
            properties = properties,
            comment = "Robot Kinematics Lab diagnostic draft"
        )
    }

    @Synchronized
    fun loadDiagnosticDraft(): DiagnosticDraft {
        val file = File(paths.preferencesDirectory, DIAGNOSTIC_DRAFT_FILE)
        if (!file.exists()) return DiagnosticDraft()

        return runCatching {
            val properties = readProperties(file)
            requireCurrentSchema(properties)
            val defaults = DiagnosticDraft()
            val stressLevel = properties.getProperty("stressLevel")?.toFloatOrNull() ?: defaults.stressLevel
            require(stressLevel.isFinite() && stressLevel in 0f..1f)
            DiagnosticDraft(
                reachableCountText = properties.getProperty("reachableCountText", defaults.reachableCountText),
                unreachableCountText = properties.getProperty("unreachableCountText", defaults.unreachableCountText),
                robotLinkCountText = properties.getProperty("robotLinkCountText", defaults.robotLinkCountText),
                minLinkCountText = properties.getProperty("minLinkCountText", defaults.minLinkCountText),
                maxLinkCountText = properties.getProperty("maxLinkCountText", defaults.maxLinkCountText),
                samplesPerLinkCountText = properties.getProperty("samplesPerLinkCountText", defaults.samplesPerLinkCountText),
                unlimitedSampleCountText = properties.getProperty("unlimitedSampleCountText", defaults.unlimitedSampleCountText),
                seedText = properties.getProperty("seedText", defaults.seedText),
                experimentalMode = properties.booleanOrDefault("experimentalMode", defaults.experimentalMode),
                unlimitedSamplesEnabled = properties.booleanOrDefault("unlimitedSamplesEnabled", defaults.unlimitedSamplesEnabled),
                manualRangeMode = properties.booleanOrDefault("manualRangeMode", defaults.manualRangeMode),
                jointMode =
                    properties.getProperty("jointMode")
                        ?.let { runCatching { DiagnosticJointMode.valueOf(it) }.getOrNull() }
                        ?: defaults.jointMode,
                runAllTopologies = properties.booleanOrDefault("runAllTopologies", defaults.runAllTopologies),
                storeFullRunHistory = properties.booleanOrDefault("storeFullRunHistory", defaults.storeFullRunHistory),
                stressLevel = stressLevel,
                ikMaxIterationsText = properties.getProperty("ikMaxIterationsText", defaults.ikMaxIterationsText),
                ikToleranceText = properties.getProperty("ikToleranceText", defaults.ikToleranceText),
                ikDampingText = properties.getProperty("ikDampingText", defaults.ikDampingText),
                ikMaxStepText = properties.getProperty("ikMaxStepText", defaults.ikMaxStepText)
            )
        }.getOrDefault(DiagnosticDraft())
    }

    @Synchronized
    fun saveDatasetBuilderDraft(draft: DatasetBuilderDraft) {
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("datasetName", draft.datasetName)
                setProperty("samplesPerRobotText", draft.samplesPerRobotText)
                setProperty("randomSeedText", draft.randomSeedText)
                setProperty("reachablePercentText", draft.reachablePercentText)
                setProperty("targetMode", draft.targetMode.name)
                setProperty("filterMode", draft.filterMode.name)
                setProperty("appendToExisting", draft.appendToExisting.toString())
                setProperty("maxIterationsText", draft.maxIterationsText)
                setProperty("toleranceText", draft.toleranceText)
                setProperty("dampingText", draft.dampingText)
                setProperty("maxStepText", draft.maxStepText)
                setProperty("selectedRobotIds", draft.selectedRobotIds.sorted().joinToString(UNIT_SEPARATOR))
            }
        writePropertiesAtomically(
            file = File(paths.preferencesDirectory, DATASET_DRAFT_FILE),
            properties = properties,
            comment = "Robot Kinematics Lab dataset builder draft"
        )
    }

    @Synchronized
    fun loadDatasetBuilderDraft(): DatasetBuilderDraft {
        val file = File(paths.preferencesDirectory, DATASET_DRAFT_FILE)
        if (!file.exists()) return DatasetBuilderDraft()

        return runCatching {
            val properties = readProperties(file)
            requireCurrentSchema(properties)
            val defaults = DatasetBuilderDraft()
            DatasetBuilderDraft(
                datasetName = properties.getProperty("datasetName", defaults.datasetName),
                samplesPerRobotText = properties.getProperty("samplesPerRobotText", defaults.samplesPerRobotText),
                randomSeedText = properties.getProperty("randomSeedText", defaults.randomSeedText),
                reachablePercentText = properties.getProperty("reachablePercentText", defaults.reachablePercentText),
                targetMode =
                    properties.getProperty("targetMode")
                        ?.let { runCatching { DatasetTargetMode.valueOf(it) }.getOrNull() }
                        ?: defaults.targetMode,
                filterMode =
                    properties.getProperty("filterMode")
                        ?.let { runCatching { DatasetFilterMode.valueOf(it) }.getOrNull() }
                        ?: defaults.filterMode,
                appendToExisting = properties.booleanOrDefault("appendToExisting", defaults.appendToExisting),
                maxIterationsText = properties.getProperty("maxIterationsText", defaults.maxIterationsText),
                toleranceText = properties.getProperty("toleranceText", defaults.toleranceText),
                dampingText = properties.getProperty("dampingText", defaults.dampingText),
                maxStepText = properties.getProperty("maxStepText", defaults.maxStepText),
                selectedRobotIds =
                    properties.getProperty("selectedRobotIds", "")
                        .split(UNIT_SEPARATOR)
                        .filter(String::isNotBlank)
                        .toSet()
            )
        }.getOrDefault(DatasetBuilderDraft())
    }

    @Synchronized
    fun saveDiagnosticSession(report: Layer1DiagnosticReport): DiagnosticSessionSummary {
        val createdAt = System.currentTimeMillis()
        val id = "diagnostic-${createdAt}-${safeFileStem(report.experimentName)}"
        require(paths.diagnosticsDirectory.mkdirs() || paths.diagnosticsDirectory.isDirectory)
        val directory = File(paths.diagnosticsDirectory, id)
        require(!directory.exists()) { "Diagnostic session already exists: $id" }
        val stagingDirectory = Files.createTempDirectory(paths.diagnosticsDirectory.toPath(), ".$id-").toFile()
        // Always publish and return one canonical path representation. macOS exposes
        // /var through /private/var; mixing the alias in the returned summary with the
        // canonical path reconstructed by the reader made a just-saved session unequal
        // to itself and could create duplicate logical identities in callers.
        val canonicalDirectory = directory.canonicalFile
        val reportFile = File(canonicalDirectory, "report.txt")
        val runHistoryFile = File(canonicalDirectory, "run-history.csv")
        val casesFile = File(canonicalDirectory, "case-results.csv")

        val linkCounts = report.runResults.map { it.linkCount }.distinct().sorted()
        val summary =
            DiagnosticSessionSummary(
                id = id,
                experimentName = report.experimentName,
                createdAtEpochMillis = createdAt,
                verdict = report.finalVerdict.name,
                runCount = report.runResults.size,
                caseCount = report.cases.size,
                seeds = report.config.seeds.seeds,
                linkCounts = linkCounts,
                directoryPath = canonicalDirectory.absolutePath,
                runHistoryPath = runHistoryFile.absolutePath,
                readableReportPath = reportFile.absolutePath
            )
        try {
            writeTextAtomically(File(stagingDirectory, reportFile.name), report.toHumanReadableText())
            DiagnosticRunHistoryCsvWriter(File(stagingDirectory, runHistoryFile.name).absolutePath).let { writer ->
                try {
                    writer.open()
                    report.runResults.forEach(writer::writeRun)
                } finally {
                    writer.close()
                }
            }
            DiagnosticCaseResultCsvWriter(File(stagingDirectory, casesFile.name).absolutePath).use { writer ->
                report.cases.forEach(writer::writeCaseResult)
            }
            saveDiagnosticSessionManifest(summary, File(stagingDirectory, SESSION_MANIFEST_FILE))
            moveWithoutReplacing(stagingDirectory, directory)
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }
        return summary
    }

    fun listDiagnosticSessions(): List<DiagnosticSessionSummary> {
        paths.ensureStructureAndMigrateLegacyData()
        return paths.diagnosticsDirectory
            .listFiles(File::isDirectory)
            ?.mapNotNull { directory ->
                readDiagnosticSessionManifest(File(directory, SESSION_MANIFEST_FILE))
            }
            ?.sortedByDescending { it.createdAtEpochMillis }
            .orEmpty()
    }

    fun snapshot(): AppStorageSnapshot {
        paths.ensureStructureAndMigrateLegacyData()
        val categories =
            listOf(
                storageSummary(
                    StorageCategory.ROBOTS,
                    listOf(paths.robotsDirectory, paths.robotLabDirectory),
                    paths.robotsDirectory
                ),
                storageSummary(
                    StorageCategory.SESSIONS,
                    listOf(paths.diagnosticsDirectory, paths.telemetryDirectory, paths.workspaceStudiesDirectory),
                    paths.sessionsDirectory
                ),
                storageSummary(StorageCategory.DATASETS, paths.datasetsDirectory),
                storageSummary(StorageCategory.MODELS, paths.modelsDirectory),
                storageSummary(StorageCategory.TRAINING, paths.trainingDirectory)
            )
        return AppStorageSnapshot(
            rootPath = paths.rootDirectory.absolutePath,
            categories = categories,
            diagnosticSessions = listDiagnosticSessions()
        )
    }

    private fun storageSummary(
        category: StorageCategory,
        directory: File
    ): StorageCategorySummary {
        return storageSummary(category, listOf(directory), directory)
    }

    private fun storageSummary(
        category: StorageCategory,
        directories: List<File>,
        displayDirectory: File
    ): StorageCategorySummary {
        var fileCount = 0
        var byteCount = 0L
        var lastUpdatedEpochMillis: Long? = null

        directories.forEach { directory ->
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    fileCount += 1
                    byteCount += file.length()
                    val lastModified = file.lastModified()
                    if (lastUpdatedEpochMillis == null || lastModified > lastUpdatedEpochMillis!!) {
                        lastUpdatedEpochMillis = lastModified
                    }
                }
            }
        }

        return StorageCategorySummary(
            category = category,
            fileCount = fileCount,
            byteCount = byteCount,
            lastUpdatedEpochMillis = lastUpdatedEpochMillis,
            directoryPath = displayDirectory.absolutePath
        )
    }

    private fun saveDiagnosticSessionManifest(
        summary: DiagnosticSessionSummary,
        file: File
    ) {
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("id", summary.id)
                setProperty("experimentName", summary.experimentName)
                setProperty("createdAtEpochMillis", summary.createdAtEpochMillis.toString())
                setProperty("verdict", summary.verdict)
                setProperty("runCount", summary.runCount.toString())
                setProperty("caseCount", summary.caseCount.toString())
                setProperty("seeds", summary.seeds.joinToString(UNIT_SEPARATOR))
                setProperty("linkCounts", summary.linkCounts.joinToString(UNIT_SEPARATOR))
                setProperty("directoryPath", summary.directoryPath)
                setProperty("runHistoryPath", summary.runHistoryPath)
                setProperty("readableReportPath", summary.readableReportPath)
            }
        writePropertiesAtomically(file, properties, "Robot Kinematics Lab diagnostic session")
    }

    private fun readDiagnosticSessionManifest(file: File): DiagnosticSessionSummary? {
        if (!file.exists()) return null
        return runCatching {
            val properties = readProperties(file)
            requireCurrentSchema(properties)
            val sessionDirectory = requireNotNull(file.parentFile).canonicalFile
            require(sessionDirectory.parentFile == paths.diagnosticsDirectory.canonicalFile)
            val id = properties.getProperty("id").also {
                require(SAFE_SESSION_ID.matches(it) && it == sessionDirectory.name)
            }
            val runHistory = File(sessionDirectory, "run-history.csv").canonicalFile
            val report = File(sessionDirectory, "report.txt").canonicalFile
            val cases = File(sessionDirectory, "case-results.csv").canonicalFile
            require(runHistory.isFile && report.isFile && cases.isFile)
            require(File(properties.getProperty("directoryPath", "")).canonicalFile == sessionDirectory)
            require(File(properties.getProperty("runHistoryPath", "")).canonicalFile == runHistory)
            require(File(properties.getProperty("readableReportPath", "")).canonicalFile == report)
            DiagnosticSessionSummary(
                id = id,
                experimentName = properties.getProperty("experimentName").also { require(it.isNotBlank()) },
                createdAtEpochMillis = properties.getProperty("createdAtEpochMillis").toLong().also { require(it > 0L) },
                verdict = properties.getProperty("verdict"),
                runCount = properties.getProperty("runCount").toInt().also { require(it >= 0) },
                caseCount = properties.getProperty("caseCount").toInt().also { require(it >= 0) },
                seeds = properties.integerList("seeds"),
                linkCounts = properties.integerList("linkCounts"),
                directoryPath = sessionDirectory.absolutePath,
                runHistoryPath = runHistory.absolutePath,
                readableReportPath = report.absolutePath
            )
        }.getOrNull()
    }

    private fun writePropertiesAtomically(
        file: File,
        properties: Properties,
        comment: String
    ) {
        AtomicFilePublisher.write(file) { temporary ->
            temporary.outputStream().buffered().use { properties.store(it, comment) }
        }
    }

    private fun writeTextAtomically(file: File, text: String) {
        AtomicFilePublisher.write(file) { temporary ->
            temporary.bufferedWriter().use { writer ->
                writer.write(text)
                writer.flush()
            }
        }
    }

    private fun moveWithoutReplacing(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath())
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").also { digest ->
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun readProperties(file: File): Properties {
        return Properties().apply {
            file.inputStream().buffered().use(::load)
        }
    }

    private fun requireCurrentSchema(properties: Properties) {
        require(properties.getProperty("schemaVersion")?.toIntOrNull() == SCHEMA_VERSION) {
            "Unsupported app storage schema."
        }
    }

    private fun requireRobotLabSchema(properties: Properties): Int {
        val schema = properties.getProperty("schemaVersion")?.toIntOrNull()
        require(schema != null && schema in 1..ROBOT_LAB_SCHEMA_VERSION) {
            "Unsupported Robot Lab storage schema."
        }
        return requireNotNull(schema)
    }

    private fun Properties.booleanOrDefault(key: String, default: Boolean): Boolean {
        return getProperty(key)?.toBooleanStrictOrNull() ?: default
    }

    private fun Properties.integerList(key: String): List<Int> {
        return getProperty(key, "").takeIf(String::isNotBlank)
            ?.split(UNIT_SEPARATOR)
            ?.map(String::toInt)
            .orEmpty()
    }

    private fun safeFileStem(value: String): String {
        return value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(60)
            .ifBlank { "experiment" }
    }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val ROBOT_LAB_SCHEMA_VERSION = 2
        private const val ROBOT_LAB_INTEGRITY_SCHEMA_VERSION = 2
        private const val UNIT_SEPARATOR = "\u001F"
        private const val ROBOT_LAB_ROBOT_FILE = "current-robot.rklb"
        private const val ROBOT_LAB_STATE_FILE = "workspace.properties"
        private const val DIAGNOSTIC_DRAFT_FILE = "diagnostic-draft.properties"
        private const val DATASET_DRAFT_FILE = "dataset-builder-draft.properties"
        private const val SESSION_MANIFEST_FILE = "session.properties"
        private val SAFE_SESSION_ID = Regex("diagnostic-[0-9]+-[a-z0-9._-]{1,60}")
    }
}
