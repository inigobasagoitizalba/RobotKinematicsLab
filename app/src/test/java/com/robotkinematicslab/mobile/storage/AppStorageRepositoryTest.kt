package com.robotkinematicslab.mobile.storage

import com.robotkinematicslab.mobile.dataset.RobotLibraryCodec
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticExperiment
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Vec3
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageRepositoryTest {

    @Test
    fun robotLabWorkspace_roundTripsRobotJointTargetAndMode() {
        val repository = repository()
        val expected =
            RobotLabSavedState(
                robot = robot(),
                jointValues = listOf(0.25),
                target = Vec3(0.1, 0.2, 0.3),
                controlMode = "IK",
                updatedAtEpochMillis = 1234L
            )

        repository.saveRobotLabState(expected)

        assertEquals(expected, repository.loadRobotLabState())
    }

    @Test
    fun diagnosticDraft_roundTripsEveryScientificInput() {
        val repository = repository()
        val expected =
            DiagnosticDraft(
                reachableCountText = "9",
                seedText = "42, 101",
                experimentalMode = true,
                manualRangeMode = true,
                stressLevel = 0.75f,
                ikMaxIterationsText = "1200"
            )

        repository.saveDiagnosticDraft(expected)

        assertEquals(expected, repository.loadDiagnosticDraft())
    }

    @Test
    fun datasetBuilderDraft_roundTripsSelectionAndGenerationPolicy() {
        val repository = repository()
        val expected =
            DatasetBuilderDraft(
                datasetName = "thesis-comparison",
                samplesPerRobotText = "100000",
                randomSeedText = "101",
                reachablePercentText = "65",
                targetMode = DatasetTargetMode.MIXED,
                filterMode = DatasetFilterMode.ACCEPTED_ONLY,
                appendToExisting = true,
                selectedRobotIds = setOf("r1", "r9")
            )

        repository.saveDatasetBuilderDraft(expected)

        assertEquals(expected, repository.loadDatasetBuilderDraft())
    }

    @Test
    fun staleLegacyTemporaryDirectoriesCannotBlockWorkspaceOrPreferencePublication() {
        val documents = Files.createTempDirectory("app-storage-stale-documents").toFile()
        val internal = Files.createTempDirectory("app-storage-stale-internal").toFile()
        val paths = AppStoragePaths(documents, internal)
        val repository = AppStorageRepository(paths)
        val staleRobotTemporary = File(paths.robotLabDirectory, "current-robot.rklb.tmp")
        val staleDraftTemporary = File(paths.preferencesDirectory, "diagnostic-draft.properties.tmp")
        assertTrue(staleRobotTemporary.mkdir())
        assertTrue(staleDraftTemporary.mkdir())
        val state = RobotLabSavedState(robot(), listOf(0.25), Vec3.ZERO, "FK", 99L)
        val draft = DiagnosticDraft(reachableCountText = "17", seedText = "42")

        repository.saveRobotLabState(state)
        repository.saveDiagnosticDraft(draft)

        assertEquals(state, repository.loadRobotLabState())
        assertEquals(draft, repository.loadDiagnosticDraft())
        assertTrue(staleRobotTemporary.isDirectory)
        assertTrue(staleDraftTemporary.isDirectory)
    }

    @Test
    fun legacyData_isCopiedIntoOrganizedStructureWithoutDeletingOriginals() {
        val documents = Files.createTempDirectory("app-storage-documents").toFile()
        val internal = Files.createTempDirectory("app-storage-internal").toFile()
        val legacyDatasetDirectory = File(documents, "RobotKinematicsDatasets").apply { mkdirs() }
        val legacyDataset = File(legacyDatasetDirectory, "study.csv").apply { writeText("a,b\n1,2\n") }
        val legacyRobotLibrary = File(internal, "dataset_robot_library.rklb")
        legacyRobotLibrary.outputStream().buffered().use { output ->
            RobotLibraryCodec().write(listOf(SavedRobot("r1", robot())), output)
        }

        val paths = AppStoragePaths(documents, internal)
        AppStorageRepository(paths)

        assertTrue(legacyDataset.exists())
        assertTrue(legacyRobotLibrary.exists())
        assertTrue(File(paths.datasetsDirectory, legacyDataset.name).exists())
        assertTrue(paths.robotLibraryFile.exists())
    }

    @Test
    fun snapshot_hasAllExplicitStorageCategories() {
        val snapshot = repository().snapshot()

        assertEquals(StorageCategory.entries, snapshot.categories.map { it.category })
        assertTrue(snapshot.categories.all { File(it.directoryPath).isDirectory })
    }

    @Test
    fun snapshotSummary_countsNestedFilesBytesAndLatestTimestampExactly() {
        val documents = Files.createTempDirectory("app-storage-documents").toFile()
        val internal = Files.createTempDirectory("app-storage-internal").toFile()
        val paths = AppStoragePaths(documents, internal)
        val repository = AppStorageRepository(paths)
        val first = File(paths.robotsDirectory, "nested/first.bin")
        val second = File(paths.robotLabDirectory, "second.bin")
        first.parentFile?.mkdirs()
        first.writeBytes(byteArrayOf(1, 2, 3))
        second.writeBytes(byteArrayOf(4, 5, 6, 7, 8))
        assertTrue(first.setLastModified(1_000L))
        assertTrue(second.setLastModified(2_000L))

        val robots =
            repository.snapshot().categories.single { it.category == StorageCategory.ROBOTS }

        assertEquals(2, robots.fileCount)
        assertEquals(8L, robots.byteCount)
        assertEquals(2_000L, robots.lastUpdatedEpochMillis)
        assertEquals(paths.robotsDirectory.absolutePath, robots.directoryPath)
    }

    @Test
    fun invalidRobotLabStateCannotBeSavedOrReintroducedFromDisk() {
        val documents = Files.createTempDirectory("invalid-state-documents").toFile()
        val internal = Files.createTempDirectory("invalid-state-internal").toFile()
        val paths = AppStoragePaths(documents, internal)
        val repository = AppStorageRepository(paths)
        val valid =
            RobotLabSavedState(
                robot = robot(),
                jointValues = listOf(0.0),
                target = Vec3.ZERO,
                controlMode = "IK",
                updatedAtEpochMillis = 1L
            )

        assertRejected { repository.saveRobotLabState(valid.copy(jointValues = listOf(Double.NaN))) }
        assertRejected { repository.saveRobotLabState(valid.copy(target = Vec3(Double.NaN, 0.0, 0.0))) }

        repository.saveRobotLabState(valid)
        val stateFile = File(paths.robotLabDirectory, "workspace.properties")
        val properties = Properties().apply { stateFile.inputStream().use(::load) }
        properties.setProperty("jointValues", "NaN")
        stateFile.outputStream().use { output -> properties.store(output, "corrupt") }
        assertNull(repository.loadRobotLabState())
    }

    @Test
    fun robotAndWorkspaceManifestCannotBeSilentlyMixed() {
        val documents = Files.createTempDirectory("mixed-state-documents").toFile()
        val internal = Files.createTempDirectory("mixed-state-internal").toFile()
        val paths = AppStoragePaths(documents, internal)
        val repository = AppStorageRepository(paths)
        val valid =
            RobotLabSavedState(robot(), listOf(0.0), Vec3.ZERO, "IK", 1L)
        repository.saveRobotLabState(valid)
        val stateFile = File(paths.robotLabDirectory, "workspace.properties")
        val stateBytes = stateFile.readBytes()
        File(paths.robotLabDirectory, "current-robot.rklb").outputStream().buffered().use { output ->
            RobotLibraryCodec().write(
                listOf(SavedRobot("robot-lab-current", robot().copy(name = "Different valid robot"))),
                output
            )
        }

        assertNull(repository.loadRobotLabState())
        assertTrue(stateBytes.contentEquals(stateFile.readBytes()))
    }

    @Test
    fun unsupportedDraftSchemaFallsBackToSafeDefaults() {
        val documents = Files.createTempDirectory("future-draft-documents").toFile()
        val internal = Files.createTempDirectory("future-draft-internal").toFile()
        val paths = AppStoragePaths(documents, internal)
        val repository = AppStorageRepository(paths)
        repository.saveDiagnosticDraft(DiagnosticDraft(reachableCountText = "99"))
        val file = File(paths.preferencesDirectory, "diagnostic-draft.properties")
        val properties = Properties().apply { file.inputStream().use(::load) }
        properties.setProperty("schemaVersion", "999")
        file.outputStream().use { output -> properties.store(output, "future") }

        assertEquals(DiagnosticDraft(), repository.loadDiagnosticDraft())
    }

    @Test
    fun completedDiagnosticSession_savesManifestReadableReportAndCompleteCsvHistory() {
        val repository = repository()
        val report =
            Layer1DiagnosticExperiment().runExperiment(
                DiagnosticBenchmarkConfig(
                    sampling =
                        DiagnosticSamplingConfig(
                            reachableCount = 1,
                            unreachableCount = 1,
                            runCount = 2,
                            samplesPerLinkCount = 2
                        ),
                    seeds = DiagnosticSeedConfig(listOf(42)),
                    topology =
                        DiagnosticTopologyConfig(
                            robotLinkCount = 2,
                            minLinkCount = 2,
                            maxLinkCount = 2
                        ),
                    solver = DiagnosticSolverConfig(ikMaxIterations = 10),
                    performance =
                        DiagnosticPerformanceConfig(
                            storeFullRunHistory = true,
                            storePerCaseDetails = true
                        )
                )
            )

        val saved = repository.saveDiagnosticSession(report)
        val restored = repository.listDiagnosticSessions().single()

        assertEquals(saved, restored)
        assertEquals(report.runResults.size, restored.runCount)
        assertTrue(File(restored.readableReportPath).readText().contains(report.experimentName))
        assertEquals(report.runResults.size + 1, File(restored.runHistoryPath).readLines().size)
        assertTrue(File(restored.directoryPath, "case-results.csv").exists())

        val manifest = File(restored.directoryPath, "session.properties")
        val properties = Properties().apply { manifest.inputStream().use(::load) }
        properties.setProperty("runHistoryPath", Files.createTempFile("outside-history", ".csv").toString())
        manifest.outputStream().use { properties.store(it, "tampered") }
        val tamperedBytes = manifest.readBytes()
        assertTrue(repository.listDiagnosticSessions().isEmpty())
        assertTrue(tamperedBytes.contentEquals(manifest.readBytes()))
    }

    private fun repository(): AppStorageRepository {
        val documents = Files.createTempDirectory("app-storage-documents").toFile()
        val internal = Files.createTempDirectory("app-storage-internal").toFile()
        return AppStorageRepository(AppStoragePaths(documents, internal))
    }

    private fun robot(): RobotDefinition {
        return RobotDefinition(
            name = "Stored Robot",
            dhParameters = listOf(DHParameter(theta = 0.0, d = 0.1, a = 0.2, alpha = 0.0)),
            joints =
                listOf(
                    JointDefinition(
                        name = "J1",
                        type = JointType.REVOLUTE,
                        minValue = -1.0,
                        maxValue = 1.0,
                        homeValue = 0.0
                    )
                )
        )
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
