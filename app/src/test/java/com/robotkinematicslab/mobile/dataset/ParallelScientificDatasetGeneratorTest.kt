package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelScientificDatasetGeneratorTest {

    @Test
    fun parallelGeneration_preservesCanonicalScientificRows() {
        val sequentialFile = Files.createTempFile("dataset-sequential", ".csv").toFile()
        val parallelFile = Files.createTempFile("dataset-parallel", ".csv").toFile()
        val config = config(samplesPerRobot = 5)

        val sequential = ScientificDatasetGenerator().generate(config, sequentialFile, 0L, 0)
        val parallel = ParallelScientificDatasetGenerator().generate(
            config = config,
            csvFile = parallelFile,
            existingRowCount = 0L,
            generationIndex = 0,
            workerCount = 2
        )

        assertTrue(sequential.completed)
        assertTrue(parallel.completed)
        assertEquals(sequential.addedRows, parallel.addedRows)
        assertEquals(stableRows(sequentialFile), stableRows(parallelFile))
    }

    @Test
    fun oneRobotCanUseSeveralWorkersWithoutChangingItsScientificRows() {
        val sequentialFile = Files.createTempFile("dataset-one-robot-sequential", ".csv").toFile()
        val parallelFile = Files.createTempFile("dataset-one-robot-parallel", ".csv").toFile()
        val config = config(samplesPerRobot = 64).copy(robots = config(64).robots.take(1))
        val workerThreads = ConcurrentHashMap.newKeySet<String>()

        val sequential = ScientificDatasetGenerator().generate(config, sequentialFile, 0L, 0)
        val parallel = ParallelScientificDatasetGenerator().generate(
            config = config,
            csvFile = parallelFile,
            existingRowCount = 0L,
            generationIndex = 0,
            workerCount = 4,
            onProgress = { workerThreads += Thread.currentThread().name }
        )

        assertTrue(sequential.completed)
        assertTrue(parallel.completed)
        assertEquals(stableRows(sequentialFile), stableRows(parallelFile))
        assertTrue(workerThreads.count { it.startsWith("dataset-worker-") } > 1)
    }

    @Test
    fun parallelAppend_preservesExistingRowsAndSingleHeader() {
        val file = Files.createTempFile("dataset-parallel-append", ".csv").toFile()
        val generator = ParallelScientificDatasetGenerator()
        val initial = generator.generate(config(2), file, 0L, 0, workerCount = 2)
        val originalRows = file.readLines().drop(1)

        val appended = generator.generate(
            config = config(3).copy(append = true),
            csvFile = file,
            existingRowCount = initial.totalRows,
            generationIndex = 1,
            workerCount = 2
        )

        assertTrue(appended.completed)
        assertEquals(10L, appended.totalRows)
        assertEquals(11, file.readLines().size)
        assertEquals(1, file.readLines().count { it.startsWith("schemaVersion,") })
        assertEquals(originalRows, file.readLines().drop(1).take(originalRows.size))
    }

    @Test
    fun appendRequestedForNewFile_stillCreatesAValidSingleHeaderDataset() {
        val directory = Files.createTempDirectory("dataset-new-append").toFile()
        val file = directory.resolve("new.csv")

        val result = ParallelScientificDatasetGenerator().generate(
            config = config(2).copy(robots = config(2).robots.take(1), append = true),
            csvFile = file,
            existingRowCount = 0L,
            generationIndex = 0,
            workerCount = 2
        )

        assertTrue(result.completed)
        assertEquals(2, result.addedRows)
        assertEquals(3, file.readLines().size)
        assertEquals(1, file.readLines().count { it.startsWith("schemaVersion,") })
    }

    @Test
    fun cancelledParallelGeneration_doesNotReplaceExistingDataset() {
        val file = Files.createTempFile("dataset-parallel-cancel", ".csv").toFile()
        file.writeText("existing-content\n")

        val result = ParallelScientificDatasetGenerator().generate(
            config = config(2).copy(append = true),
            csvFile = file,
            existingRowCount = 1L,
            generationIndex = 1,
            workerCount = 2,
            cancellationRequested = AtomicBoolean(true)
        )

        assertFalse(result.completed)
        assertTrue(result.cancelled)
        assertEquals("existing-content\n", file.readText())
    }

    @Test
    fun interruptedCallerDoesNotStartShardsOrReplaceExistingDataset() {
        val file = Files.createTempFile("dataset-parallel-interrupt", ".csv").toFile()
        file.writeText("known-good\n")

        Thread.currentThread().interrupt()
        try {
            val result =
                ParallelScientificDatasetGenerator().generate(
                    config = config(64),
                    csvFile = file,
                    existingRowCount = 1L,
                    generationIndex = 1,
                    workerCount = 4
                )

            assertFalse(result.completed)
            assertTrue(result.cancelled)
            assertEquals(0, result.addedRows)
            assertEquals(1L, result.totalRows)
            assertEquals("known-good\n", file.readText())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun cancelledSingleRobotGeneration_discardsUncommittedRows() {
        val file = Files.createTempFile("dataset-single-robot-cancel", ".csv").toFile()
        file.writeText("existing-content\n")
        val cancellation = AtomicBoolean(false)

        val result =
            ParallelScientificDatasetGenerator().generate(
                config = config(20).copy(robots = config(20).robots.take(1), append = true),
                csvFile = file,
                existingRowCount = 1L,
                generationIndex = 1,
                workerCount = 1,
                cancellationRequested = cancellation,
                onProgress = { progress ->
                    if (progress.addedRows >= 1) cancellation.set(true)
                }
            )

        assertFalse(result.completed)
        assertTrue(result.cancelled)
        assertEquals(0, result.addedRows)
        assertEquals(1L, result.totalRows)
        assertEquals("existing-content\n", file.readText())
    }

    @Test
    fun appendRejectsCorruptExistingSchemaWithoutReplacingTheFile() {
        val file = Files.createTempFile("dataset-corrupt-append", ".csv").toFile()
        val original = "wrong,header\n1,2\n"
        file.writeText(original)

        val failure =
            runCatching {
                ParallelScientificDatasetGenerator().generate(
                    config = config(1).copy(robots = config(1).robots.take(1), append = true),
                    csvFile = file,
                    existingRowCount = 1L,
                    generationIndex = 1,
                    workerCount = 1
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("different dataset schema"))
        assertEquals(original, file.readText())
    }

    @Test
    fun sameDestinationRejectsConcurrentGenerationInsteadOfLosingAnUpdate() {
        val file = Files.createTempFile("dataset-concurrent", ".csv").toFile()
        val enteredGeneration = CountDownLatch(1)
        val allowGenerationToContinue = CountDownLatch(1)
        val cancellation = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first =
                executor.submit<DatasetGenerationResult> {
                    ParallelScientificDatasetGenerator().generate(
                        config = config(5).copy(robots = config(5).robots.take(1)),
                        csvFile = file,
                        existingRowCount = 0L,
                        generationIndex = 0,
                        workerCount = 1,
                        cancellationRequested = cancellation,
                        onProgress = {
                            if (enteredGeneration.count > 0L) {
                                enteredGeneration.countDown()
                                allowGenerationToContinue.await(5, TimeUnit.SECONDS)
                            }
                        }
                    )
                }

            assertTrue(enteredGeneration.await(5, TimeUnit.SECONDS))
            val duplicateFailure =
                runCatching {
                    ParallelScientificDatasetGenerator().generate(
                        config = config(1).copy(robots = config(1).robots.take(1)),
                        csvFile = file,
                        existingRowCount = 0L,
                        generationIndex = 0,
                        workerCount = 1
                    )
                }.exceptionOrNull()

            assertTrue(duplicateFailure is IllegalStateException)
            assertTrue(duplicateFailure?.message.orEmpty().contains("already writing"))
            cancellation.set(true)
            allowGenerationToContinue.countDown()
            assertFalse(first.get(10, TimeUnit.SECONDS).completed)
        } finally {
            allowGenerationToContinue.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun lockedPreflightRejectsStateChangedAfterInitialValidationWithoutWriting() {
        val directory = Files.createTempDirectory("dataset-preflight-race").toFile()
        val repository = DatasetStorageRepository(directory)
        val datasetName = "Preflight race"
        val file = repository.resolveCsvFile(datasetName)
        val generationConfig =
            config(1).copy(
                datasetName = datasetName,
                robots = config(1).robots.take(1),
                append = true
            )
        file.writeText(ScientificDatasetCsvWriter.HEADER.joinToString(",") + "\ninitial-row\n")
        val initialManifest =
            buildDatasetManifest(
                existingManifest = null,
                config = generationConfig,
                csvPath = file.absolutePath,
                totalRows = 1L,
                addedRows = 1L,
                generationIndex = 0,
                updatedAtEpochMillis = 1L
            )
        repository.saveManifest(initialManifest)
        val validatedTarget = repository.resolveAppendOrCreateTarget(datasetName)

        // A second producer commits after the caller validates but before it owns the lock.
        file.appendText("concurrent-row\n")
        repository.saveManifest(
            buildDatasetManifest(
                existingManifest = initialManifest,
                config = generationConfig,
                csvPath = file.absolutePath,
                totalRows = 2L,
                addedRows = 1L,
                generationIndex = 1,
                updatedAtEpochMillis = 2L
            )
        )
        val stateAfterConcurrentCommit = file.readBytes()
        val delegateCalls = AtomicInteger(0)
        val generator =
            ParallelScientificDatasetGenerator(
                delegateFactory = {
                    delegateCalls.incrementAndGet()
                    ScientificDatasetGenerator()
                }
            )

        val failure =
            runCatching {
                generator.generate(
                    config = generationConfig,
                    csvFile = file,
                    existingRowCount = 1L,
                    generationIndex = 1,
                    workerCount = 1,
                    lockedPreflight = {
                        val currentTarget = repository.resolveAppendOrCreateTarget(datasetName)
                        check(currentTarget == validatedTarget) {
                            "Dataset changed after provenance validation"
                        }
                    }
                )
            }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("changed after provenance validation"))
        assertEquals(0, delegateCalls.get())
        assertTrue(stateAfterConcurrentCommit.contentEquals(file.readBytes()))
        assertEquals(2L, repository.loadManifest(datasetName)?.rowCount)
    }

    @Test
    fun aggregateRequestOverflow_isRejectedBeforeCreatingOutput() {
        val directory = Files.createTempDirectory("dataset-overflow").toFile()
        val file = directory.resolve("overflow.csv")
        val manyRobots = List(2_148) { index ->
            config(1).robots.first().copy(id = "robot-$index")
        }

        val failure = runCatching {
            ParallelScientificDatasetGenerator().generate(
                config = config(1_000_000).copy(robots = manyRobots),
                csvFile = file,
                existingRowCount = 0L,
                generationIndex = 0,
                workerCount = 8
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("too many rows"))
        assertFalse(file.exists())
    }

    @Test
    fun duplicateRobotIds_areRejectedBeforeCreatingOutput() {
        val directory = Files.createTempDirectory("dataset-duplicate-ids").toFile()
        val file = directory.resolve("duplicates.csv")
        val duplicated = config(1).robots.map { it.copy(id = "same-id") }

        val failure = runCatching {
            ParallelScientificDatasetGenerator().generate(
                config = config(1).copy(robots = duplicated),
                csvFile = file,
                existingRowCount = 0L,
                generationIndex = 0,
                workerCount = 2
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("must be unique"))
        assertFalse(file.exists())
    }

    @Test
    fun controlCharactersAreRejectedBeforeTheyCanCorruptCsvRecordBoundaries() {
        val directory = Files.createTempDirectory("dataset-control-character").toFile()
        val unsafeNameFile = directory.resolve("unsafe-name.csv")
        val unsafeIdFile = directory.resolve("unsafe-id.csv")

        val unsafeName = runCatching {
            ParallelScientificDatasetGenerator().generate(
                config = config(1).copy(datasetName = "study\nsecond-record"),
                csvFile = unsafeNameFile,
                existingRowCount = 0L,
                generationIndex = 0,
                workerCount = 1
            )
        }.exceptionOrNull()
        val unsafeId = runCatching {
            ParallelScientificDatasetGenerator().generate(
                config = config(1).copy(robots = listOf(config(1).robots.first().copy(id = "robot\u001fother"))),
                csvFile = unsafeIdFile,
                existingRowCount = 0L,
                generationIndex = 0,
                workerCount = 1
            )
        }.exceptionOrNull()

        assertTrue(unsafeName is IllegalArgumentException)
        assertTrue(unsafeId is IllegalArgumentException)
        assertFalse(unsafeNameFile.exists())
        assertFalse(unsafeIdFile.exists())
    }

    private fun config(samplesPerRobot: Int) = DatasetGenerationConfig(
        datasetName = "parallel-generator-test",
        robots = DatasetRobotPresets().buildDefaults().take(2),
        samplesPerRobot = samplesPerRobot,
        randomSeed = 2604,
        targetMode = DatasetTargetMode.MIXED,
        reachableFraction = 0.5,
        filterMode = DatasetFilterMode.ALL,
        append = false,
        ikConfig = IKConfig(maxIterations = 10)
    )

    private fun stableRows(file: java.io.File): List<Map<String, String>> {
        val lines = file.readLines()
        val header = lines.first().split(',')
        return lines.drop(1).map { line ->
            header.zip(line.split(',')).toMap() - "solveDurationNanos"
        }
    }
}
