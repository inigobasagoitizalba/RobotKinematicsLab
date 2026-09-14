package com.robotkinematicslab.mobile.dataset.continuous

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetGenerationResult
import com.robotkinematicslab.mobile.dataset.DatasetGenerationProgress
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeThermalLevel
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousDatasetGenerationCoordinatorTest {

    @Test
    fun duplicateStartDoesNotCorruptTheStateOfTheActiveRun() {
        val fixture = fixture()
        val enteredRunner = CountDownLatch(1)
        val runner = ContinuousDatasetBatchRunner { request ->
            enteredRunner.countDown()
            while (!request.cancellationRequested.get()) Thread.yield()
            DatasetGenerationResult(
                request.csvFile.absolutePath,
                0,
                request.existingRowCount,
                0,
                completed = false,
                cancelled = true,
                message = "Cancelled"
            )
        }
        val coordinator = fixture.coordinator(runner)
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(enteredRunner.await(5, TimeUnit.SECONDS))

            val duplicate = coordinator.start(fixture.plan, fixture.robots)

            assertTrue(duplicate.isFailure)
            assertFalse(coordinator.state.value.status == ContinuousDatasetStatus.ERROR)
            coordinator.pause()
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun pauseDuringDeviceInspectionPreventsTheNextBatchFromStarting() {
        val fixture = fixture()
        val enteredInspection = CountDownLatch(1)
        val releaseInspection = CountDownLatch(1)
        val runnerCalls = AtomicInteger(0)
        val coordinator =
            ContinuousDatasetGenerationCoordinator(
                storageRepository = fixture.storage,
                settingsRepository = fixture.settings,
                deviceProfileProvider = {
                    enteredInspection.countDown()
                    check(releaseInspection.await(5, TimeUnit.SECONDS))
                    device()
                },
                batchRunner = ContinuousDatasetBatchRunner {
                    runnerCalls.incrementAndGet()
                    error("A batch must not start after pause was requested")
                },
                freeStorageBytes = { Long.MAX_VALUE },
                delayMillis = {}
            )
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(enteredInspection.await(5, TimeUnit.SECONDS))
            coordinator.pause()
            releaseInspection.countDown()

            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)
            assertEquals(0, runnerCalls.get())
        } finally {
            releaseInspection.countDown()
            coordinator.close()
        }
    }

    @Test
    fun lateProgressCannotMoveAPausingRunBackToRunning() {
        val fixture = fixture()
        val enteredRunner = CountDownLatch(1)
        val lateProgressPublished = CountDownLatch(1)
        val allowReturn = CountDownLatch(1)
        val runner = ContinuousDatasetBatchRunner { request ->
            enteredRunner.countDown()
            while (!request.cancellationRequested.get()) Thread.yield()
            request.onProgress(
                DatasetGenerationProgress(
                    requestedRows = 25,
                    addedRows = 1,
                    attempts = 1,
                    currentRobotIndex = 0,
                    totalRobots = 1,
                    currentRobotName = "Late robot",
                    message = "Late progress"
                )
            )
            lateProgressPublished.countDown()
            check(allowReturn.await(5, TimeUnit.SECONDS))
            DatasetGenerationResult(
                request.csvFile.absolutePath,
                0,
                request.existingRowCount,
                1,
                completed = false,
                cancelled = true,
                message = "Cancelled"
            )
        }
        val coordinator = fixture.coordinator(runner)
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(enteredRunner.await(5, TimeUnit.SECONDS))
            coordinator.pause()
            assertTrue(lateProgressPublished.await(5, TimeUnit.SECONDS))

            assertEquals(ContinuousDatasetStatus.PAUSING, coordinator.state.value.status)
            assertFalse(coordinator.state.value.message == "Late progress")
            allowReturn.countDown()
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)
        } finally {
            allowReturn.countDown()
            coordinator.close()
        }
    }

    @Test
    fun callbacksRetainedByAnOldRunnerCannotMutateANewerRun() {
        val fixture = fixture()
        val firstFinished = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        var staleProgress: ((DatasetGenerationProgress) -> Unit)? = null
        var staleCheckpoint: ((Int, Long) -> Unit)? = null
        val calls = AtomicInteger(0)
        val runner = ContinuousDatasetBatchRunner { request ->
            if (calls.incrementAndGet() == 1) {
                staleProgress = request.onProgress
                staleCheckpoint = request.onCheckpoint
                firstFinished.countDown()
                DatasetGenerationResult(
                    request.csvFile.absolutePath,
                    0,
                    request.existingRowCount,
                    0,
                    completed = false,
                    cancelled = true,
                    message = "First lifecycle ended"
                )
            } else {
                secondEntered.countDown()
                while (!request.cancellationRequested.get()) Thread.yield()
                DatasetGenerationResult(
                    request.csvFile.absolutePath,
                    0,
                    request.existingRowCount,
                    0,
                    completed = false,
                    cancelled = true,
                    message = "Second lifecycle cancelled"
                )
            }
        }
        val coordinator = fixture.coordinator(runner)
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(firstFinished.await(5, TimeUnit.SECONDS))
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)

            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(secondEntered.await(5, TimeUnit.SECONDS))
            val currentMessage = coordinator.state.value.message

            requireNotNull(staleProgress).invoke(
                DatasetGenerationProgress(
                    requestedRows = 25,
                    addedRows = 25,
                    attempts = 25,
                    currentRobotIndex = 0,
                    totalRobots = 1,
                    currentRobotName = "Stale robot",
                    message = "Stale lifecycle progress"
                )
            )
            requireNotNull(staleCheckpoint).invoke(25, 25L)

            assertEquals(currentMessage, coordinator.state.value.message)
            assertNull(fixture.storage.loadManifest(fixture.plan.datasetName))
            coordinator.pause()
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun exceptionAfterPartialCsvWriteIsQuarantinedAcrossProcessRecovery() {
        val fixture = fixture()
        val failed = fixture.coordinator(
            ContinuousDatasetBatchRunner { request ->
                writeSyntheticRows(request.csvFile, rows = 1, append = false)
                error("Injected failure after a partial CSV write")
            }
        )
        assertTrue(failed.start(fixture.plan, fixture.robots).isSuccess)
        awaitStatus(failed, ContinuousDatasetStatus.ERROR)
        val partialCsv = fixture.storage.resolveCsvFile(fixture.plan.datasetName)
        val partialBytes = partialCsv.readBytes()
        assertEquals(1L, fixture.storage.countCsvDataRows(partialCsv))
        assertTrue(requireNotNull(fixture.settings.load()).pendingBatch != null)
        failed.close()

        val runnerCalls = AtomicInteger(0)
        val recovered = fixture.coordinator(
            ContinuousDatasetBatchRunner {
                runnerCalls.incrementAndGet()
                error("Recovery must block before invoking the generator")
            }
        )
        try {
            assertTrue(recovered.start(fixture.plan, fixture.robots).isSuccess)
            awaitStatus(recovered, ContinuousDatasetStatus.ERROR)

            assertEquals(0, runnerCalls.get())
            assertTrue(recovered.state.value.message.contains("checkpoint disagree"))
            assertTrue(partialBytes.contentEquals(partialCsv.readBytes()))
            assertNull(fixture.storage.loadManifest(fixture.plan.datasetName))
            assertTrue(requireNotNull(fixture.settings.load()).pendingBatch != null)
        } finally {
            recovered.close()
        }
    }

    @Test
    fun committedBatchSurvivesPauseAndIsRecordedOnce() {
        val fixture = fixture()
        val firstCheckpoint = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val runner = ContinuousDatasetBatchRunner { request ->
            if (calls.incrementAndGet() == 1) {
                writeSyntheticRows(request.csvFile, request.config.samplesPerRobot, append = false)
                val total = request.existingRowCount + request.config.samplesPerRobot
                request.onCheckpoint(request.config.samplesPerRobot, total)
                firstCheckpoint.countDown()
                DatasetGenerationResult(
                    request.csvFile.absolutePath,
                    request.config.samplesPerRobot,
                    total,
                    request.config.samplesPerRobot,
                    completed = true,
                    cancelled = false,
                    message = "Committed"
                )
            } else {
                while (!request.cancellationRequested.get()) Thread.yield()
                DatasetGenerationResult(
                    request.csvFile.absolutePath,
                    0,
                    request.existingRowCount,
                    0,
                    completed = false,
                    cancelled = true,
                    message = "Cancelled"
                )
            }
        }
        val coordinator = fixture.coordinator(runner)
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(firstCheckpoint.await(5, TimeUnit.SECONDS))
            coordinator.pause()
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)

            val manifest = fixture.storage.loadManifest(fixture.plan.datasetName)
            assertEquals(25L, manifest?.rowCount)
            assertEquals(1, manifest?.generationCount)
            assertEquals(25L, coordinator.state.value.committedDatasetRows)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun firstBatchCommittedBeforeProcessDeathIsReconciledWithoutDuplication() {
        val fixture = fixture()
        val pending = fixture.pendingBatch()
        fixture.settings.save(ContinuousDatasetRecovery(fixture.plan, wasRunning = true, pendingBatch = pending))
        val csv = fixture.storage.resolveCsvFile(fixture.plan.datasetName)
        writeSyntheticRows(csv, 5, append = false)
        val observedGeneration = AtomicInteger(-1)
        val enteredRunner = CountDownLatch(1)
        val runner = ContinuousDatasetBatchRunner { request ->
            observedGeneration.set(request.generationIndex)
            enteredRunner.countDown()
            while (!request.cancellationRequested.get()) Thread.yield()
            DatasetGenerationResult(
                request.csvFile.absolutePath,
                0,
                request.existingRowCount,
                0,
                completed = false,
                cancelled = true,
                message = "Cancelled"
            )
        }
        val coordinator = fixture.coordinator(runner)
        try {
            assertEquals(ContinuousDatasetStatus.INTERRUPTED, coordinator.state.value.status)
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            assertTrue(enteredRunner.await(5, TimeUnit.SECONDS))
            coordinator.pause()
            awaitStatus(coordinator, ContinuousDatasetStatus.PAUSED)

            assertEquals(1, observedGeneration.get())
            assertEquals(5L, fixture.storage.loadManifest(fixture.plan.datasetName)?.rowCount)
            assertEquals(1, fixture.storage.loadManifest(fixture.plan.datasetName)?.generationCount)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun pendingDatasetRejectsChangedScientificPlanWithoutReplacingRecovery() {
        val fixture = fixture()
        val recovery = ContinuousDatasetRecovery(
            fixture.plan, wasRunning = true,
            pendingBatch = fixture.pendingBatch()
        )
        fixture.settings.save(recovery)
        val coordinator = fixture.coordinator(ContinuousDatasetBatchRunner { error("Runner must not be called") })
        try {
            val result = coordinator.start(fixture.plan.copy(randomSeed = fixture.plan.randomSeed + 1), fixture.robots)
            assertTrue("A pending batch must reject a changed scientific plan", result.isFailure)
            assertEquals(recovery, fixture.settings.load())
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun pendingDatasetRejectsChangedRobotWithSameIdentifier() {
        val fixture = fixture()
        val recovery = ContinuousDatasetRecovery(fixture.plan, true, fixture.pendingBatch())
        fixture.settings.save(recovery)
        val saved = fixture.robots.single()
        val changed = saved.copy(robot = saved.robot.copy(
            dhParameters = saved.robot.dhParameters.mapIndexed { index, dh ->
                if (index == 0) dh.copy(a = dh.a + 0.01) else dh
            }
        ))
        val coordinator = fixture.coordinator(ContinuousDatasetBatchRunner { error("Runner must not be called") })
        try {
            assertTrue(coordinator.start(fixture.plan, listOf(changed)).isFailure)
            assertEquals(recovery, fixture.settings.load())
        } finally { coordinator.close() }
    }

    @Test
    fun legacyPendingBatchIsPreservedAndCannotBeRelabelled() {
        val fixture = fixture()
        val recovery = ContinuousDatasetRecovery(fixture.plan, true, ContinuousDatasetPendingBatch(0L, 0, 5, 10L))
        fixture.settings.save(recovery)
        val csv = fixture.storage.resolveCsvFile(fixture.plan.datasetName)
        writeSyntheticRows(csv, 5, append = false)
        val before = csv.readBytes()
        val coordinator = fixture.coordinator(ContinuousDatasetBatchRunner { error("Runner must not be called") })
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isFailure)
            assertTrue(coordinator.state.value.message.contains("legacy pending batch"))
            assertEquals(recovery, fixture.settings.load())
            assertTrue(before.contentEquals(csv.readBytes()))
            assertNull(fixture.storage.loadManifest(fixture.plan.datasetName))
        } finally { coordinator.close() }
    }

    @Test
    fun recoveryRejectsChangedOrderedHeaderWithoutModifyingCsv() {
        val fixture = fixture()
        fixture.settings.save(ContinuousDatasetRecovery(fixture.plan, true, fixture.pendingBatch()))
        val csv = fixture.storage.resolveCsvFile(fixture.plan.datasetName)
        csv.parentFile.mkdirs()
        csv.writeText(ScientificDatasetCsvWriter.HEADER.reversed().joinToString(",") + "\n")
        val before = csv.readBytes()
        val coordinator = fixture.coordinator(ContinuousDatasetBatchRunner { error("Runner must not be called") })
        try {
            assertTrue(coordinator.start(fixture.plan, fixture.robots).isSuccess)
            awaitStatus(coordinator, ContinuousDatasetStatus.ERROR)
            assertTrue(before.contentEquals(csv.readBytes()))
            assertNull(fixture.storage.loadManifest(fixture.plan.datasetName))
            assertEquals(fixture.pendingBatch(), fixture.settings.load()?.pendingBatch)
        } finally { coordinator.close() }
    }

    @Test
    fun pendingDatasetBlocksStartingAnotherNameUntilRecoveryIsResolved() {
        val fixture = fixture()
        fixture.settings.save(
            ContinuousDatasetRecovery(
                fixture.plan,
                wasRunning = true,
                pendingBatch = fixture.pendingBatch()
            )
        )
        val coordinator = fixture.coordinator(
            ContinuousDatasetBatchRunner { error("Runner must not be called") }
        )
        try {
            val result = coordinator.start(fixture.plan.copy(datasetName = "different"), fixture.robots)

            assertTrue(result.isFailure)
            assertTrue(coordinator.state.value.message.contains("Resume the interrupted dataset"))
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun closingBeforeInterruptedPlanIsResumedPreservesItsPendingCheckpoint() {
        val fixture = fixture()
        val pending = ContinuousDatasetPendingBatch(125L, 5, 25, 10L)
        fixture.settings.save(
            ContinuousDatasetRecovery(
                fixture.plan,
                wasRunning = true,
                pendingBatch = pending
            )
        )
        val coordinator = fixture.coordinator(
            ContinuousDatasetBatchRunner { error("Runner must not be called") }
        )

        coordinator.close()

        val saved = requireNotNull(fixture.settings.load())
        assertEquals(false, saved.wasRunning)
        assertEquals(pending, saved.pendingBatch)
    }

    @Test
    fun pausingAnInterruptedPlanPreservesItsPendingCheckpoint() {
        val fixture = fixture()
        val pending = ContinuousDatasetPendingBatch(125L, 5, 25, 10L)
        fixture.settings.save(
            ContinuousDatasetRecovery(
                fixture.plan,
                wasRunning = true,
                pendingBatch = pending
            )
        )
        val coordinator = fixture.coordinator(
            ContinuousDatasetBatchRunner { error("Runner must not be called") }
        )
        try {
            assertEquals(ContinuousDatasetStatus.INTERRUPTED, coordinator.state.value.status)

            coordinator.pause()

            assertEquals(ContinuousDatasetStatus.PAUSED, coordinator.state.value.status)
            assertEquals(pending, requireNotNull(fixture.settings.load()).pendingBatch)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun closedCoordinatorRejectsStaleStartWithoutMarkingRecoveryRunning() {
        val fixture = fixture()
        val coordinator = fixture.coordinator(
            ContinuousDatasetBatchRunner { error("Runner must not be called") }
        )
        coordinator.close()

        val staleStart = coordinator.start(fixture.plan, fixture.robots)

        assertTrue(staleStart.isFailure)
        assertFalse(fixture.settings.load()?.wasRunning == true)
    }

    @Test
    fun missingCsvOrLowStorageStopsBeforeAnyGeneratorCanRun() {
        val missingFileFixture = fixture()
        missingFileFixture.storage.saveManifest(
            DatasetManifest(
                datasetName = missingFileFixture.plan.datasetName,
                csvPath = missingFileFixture.storage.resolveCsvFile(missingFileFixture.plan.datasetName).absolutePath,
                rowCount = 1L,
                generationCount = 1,
                robotIds = missingFileFixture.plan.robotIds,
                samplesPerRobotLastRun = 1,
                randomSeed = missingFileFixture.plan.randomSeed,
                targetMode = missingFileFixture.plan.targetMode,
                reachableFraction = missingFileFixture.plan.reachableFraction,
                filterMode = missingFileFixture.plan.filterMode,
                lastUpdatedEpochMillis = 1L,
                ikConfig = missingFileFixture.plan.ikConfig,
                scientificFingerprint = com.robotkinematicslab.mobile.dataset.DatasetScientificContract.fingerprint(missingFileFixture.generationConfig())
            )
        )
        val calls = AtomicInteger(0)
        val runner = ContinuousDatasetBatchRunner {
            calls.incrementAndGet()
            error("Unsafe generator call")
        }
        val missingFileCoordinator = missingFileFixture.coordinator(runner)
        try {
            assertTrue(missingFileCoordinator.start(missingFileFixture.plan, missingFileFixture.robots).isSuccess)
            awaitStatus(missingFileCoordinator, ContinuousDatasetStatus.ERROR)
            assertTrue(missingFileCoordinator.state.value.message.contains("CSV file is missing"))
            assertEquals(0, calls.get())
        } finally {
            missingFileCoordinator.close()
        }

        val lowStorageFixture = fixture()
        val lowStorageCoordinator =
            ContinuousDatasetGenerationCoordinator(
                storageRepository = lowStorageFixture.storage,
                settingsRepository = lowStorageFixture.settings,
                deviceProfileProvider = { device() },
                batchRunner = runner,
                freeStorageBytes = { 0L },
                delayMillis = {}
            )
        try {
            assertTrue(lowStorageCoordinator.start(lowStorageFixture.plan, lowStorageFixture.robots).isSuccess)
            awaitStatus(lowStorageCoordinator, ContinuousDatasetStatus.ERROR)
            assertTrue(lowStorageCoordinator.state.value.message.contains("storage safety reserve"))
            assertEquals(0, calls.get())
        } finally {
            lowStorageCoordinator.close()
        }
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("continuous-coordinator").toFile()
        val robots = DatasetRobotPresets().buildDefaults().take(1)
        val plan = ContinuousDatasetPlan(
            datasetName = "continuous-coordinator-study",
            robotIds = robots.map { it.id },
            randomSeed = 2604,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5,
            filterMode = DatasetFilterMode.ALL,
            ikConfig = IKConfig(maxIterations = 20, tolerance = 1e-6, damping = 0.01, maxStep = 0.02)
        )
        return Fixture(
            storage = DatasetStorageRepository(root.resolve("datasets")),
            settings = ContinuousDatasetSettingsRepository(root.resolve("preferences")),
            robots = robots,
            plan = plan
        )
    }

    private fun Fixture.generationConfig() = com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig(
            datasetName = plan.datasetName, robots = robots, samplesPerRobot = 5,
            randomSeed = plan.randomSeed, targetMode = plan.targetMode,
            reachableFraction = plan.reachableFraction, filterMode = plan.filterMode,
            append = true, maxAttemptsMultiplier = plan.maxAttemptsMultiplier,
            ikConfig = plan.ikConfig, metricPolicy = plan.metricPolicy
        )
    private fun Fixture.pendingBatch(): ContinuousDatasetPendingBatch {
        return ContinuousDatasetPendingBatch(
            0L, 0, 5, 10L,
            com.robotkinematicslab.mobile.dataset.DatasetScientificContract.batchFingerprint(generationConfig(), 0L, 0)
        )
    }

    private fun Fixture.coordinator(runner: ContinuousDatasetBatchRunner) =
        ContinuousDatasetGenerationCoordinator(
            storageRepository = storage,
            settingsRepository = settings,
            deviceProfileProvider = { device() },
            batchRunner = runner,
            freeStorageBytes = { Long.MAX_VALUE },
            delayMillis = {},
        )

    private fun awaitStatus(
        coordinator: ContinuousDatasetGenerationCoordinator,
        expected: ContinuousDatasetStatus
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (coordinator.state.value.status != expected && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(expected, coordinator.state.value.status)
    }

    private fun writeSyntheticRows(file: java.io.File, rows: Int, append: Boolean) {
        file.parentFile?.mkdirs()
        java.io.FileOutputStream(file, append).bufferedWriter().use { writer ->
            if (!append) writer.appendLine(ScientificDatasetCsvWriter.HEADER.joinToString(","))
            repeat(rows) { writer.appendLine("synthetic-row-$it") }
        }
    }

    private fun device() = DeviceComputeProfile(
        logicalCpuCores = 8,
        totalSystemMemoryBytes = 8L * 1_024L * 1_024L * 1_024L,
        availableSystemMemoryBytes = 4L * 1_024L * 1_024L * 1_024L,
        lowMemoryThresholdBytes = 512L * 1_024L * 1_024L,
        appHeapLimitBytes = 512L * 1_024L * 1_024L,
        lowMemory = false,
        thermalLevel = ComputeThermalLevel.NONE,
        deviceName = "Test device"
    )

    private data class Fixture(
        val storage: DatasetStorageRepository,
        val settings: ContinuousDatasetSettingsRepository,
        val robots: List<com.robotkinematicslab.mobile.dataset.SavedRobot>,
        val plan: ContinuousDatasetPlan
    )
}
