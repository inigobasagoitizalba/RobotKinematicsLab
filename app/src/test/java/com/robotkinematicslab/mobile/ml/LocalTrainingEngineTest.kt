package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.ml.data.DeterministicCsvRowSampler
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonLoader
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingCancelledException
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalTrainingEngineTest {

    @Test
    fun customControlsAndReducedActualWorkersSurviveRealTrainingAndReopen() {
        val csv = createSyntheticScientificDataset(90)
        val root = Files.createTempDirectory("training-controls-evidence").toFile()
        try {
            val config = LocalTrainingConfig("custom-controls",csv.absolutePath,compareFeatureProfiles=false,
                singleFeatureProfile=TrainingFeatureProfile.BASELINE_KINEMATICS,modelKind=TrainingModelKind.COMPACT_MLP,
                maximumRows=90,epochs=2,batchSize=17,hiddenUnits=7,learningRate=.004,l2Regularization=.0002,earlyStoppingPatience=3,workerCount=4)
            val result = LocalTrainingEngine(runtimeWorkerLimit={ 1 }).train(config)
            assertEquals(setOf(1),result.workerBatchCounts.keys)
            assertTrue(result.workerBatchCounts.getValue(1)>0)
            val repository=TrainingStorageRepository(File(root,"runs"),File(root,"models"))
            repository.save(result)
            val run=repository.listRuns().single()
            val controls=requireNotNull(run.trainingControls)
            assertEquals(result.comparison.variants.single().splitEvidence,run.variants.single().splitEvidence)
            assertEquals(result.comparison.variants.single().datasetWarnings,run.variants.single().datasetWarnings)
            assertEquals(com.robotkinematicslab.mobile.ml.training.StoredTrainingControls.from(config,result.workerBatchCounts),controls)
            val manifest=File(run.directoryPath,"summary.properties")
            val properties=java.util.Properties().apply { manifest.inputStream().use(::load) }
            properties.remove("earlyStoppingPatience")
            manifest.outputStream().use { properties.store(it,"corrupt") }
            val bytes=manifest.readBytes()
            assertTrue(repository.listRuns().isEmpty())
            org.junit.Assert.assertArrayEquals(bytes,manifest.readBytes())
        } finally { root.deleteRecursively();csv.delete() }
    }

    @Test
    fun storedInferenceReplaysBothSamplingModesAndRejectsReplacedCorpus() {
        for (strategy in TrainingSplitStrategy.entries) {
            val csv = createSyntheticScientificDataset(360)
            val root = Files.createTempDirectory("classifier-inference-replay").toFile()
            val repository = TrainingStorageRepository(File(root, "runs"), File(root, "models"))
            val config = LocalTrainingConfig("replay", csv.absolutePath, compareFeatureProfiles = false,
                singleFeatureProfile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                modelKind = TrainingModelKind.LINEAR_SOFTMAX, splitStrategy = strategy,
                maximumRows = 90, epochs = 1, randomSeed = 42)
            val result = repository.save(LocalTrainingEngine().train(config))
            val run = repository.listRuns().single()
            val stored = repository.loadModel(File(result.modelPaths.single()))
            assertNotNull(stored.inferenceContract)
            val replay = com.robotkinematicslab.mobile.ml.storage.TrainingInferenceReplay.rebuild(run, stored)
            val original = requireNotNull(ScientificDatasetTrainingReader().load(csv, stored.profile, 90,
                sampleAcrossEntireFile = strategy == TrainingSplitStrategy.ROBOT_HELD_OUT, samplingSeed = 42).dataset)
            assertEquals(original.samples.map { it.sourceRowIndex }, replay.first.samples.map { it.sourceRowIndex })
            val eligibility = com.robotkinematicslab.mobile.ml.explainability.ExplainabilityPreflight.validate(repository,run,result.modelPaths.single(),16,8)
            assertEquals(replay.second.testIndices.size,eligibility.availableTestRows)
            assertEquals(minOf(16,replay.second.testIndices.size),eligibility.effectiveSamples)
            val explanation = com.robotkinematicslab.mobile.ml.explainability.LocalModelExplainabilityEngine(repository)
                .explain(run, result.modelPaths.single(), maximumSamples = 16, integratedGradientSteps = 8)
            assertTrue(explanation.localExplanations.all { local -> replay.second.testIndices.any { index ->
                replay.first.samples[index].sourceRowIndex == local.sourceRowIndex } })
            assertThrows(IllegalArgumentException::class.java) {
                com.robotkinematicslab.mobile.ml.storage.TrainingInferenceReplay.rebuild(run.copy(randomSeed = 99), stored)
            }
            assertThrows(IllegalArgumentException::class.java) {
                com.robotkinematicslab.mobile.ml.storage.TrainingInferenceReplay.rebuild(run, stored.copy(inferenceContract = null))
            }
            csv.appendText("\n")
            assertThrows(IllegalArgumentException::class.java) {
                com.robotkinematicslab.mobile.ml.explainability.LocalModelExplainabilityEngine(repository)
                    .explain(run, result.modelPaths.single(), maximumSamples = 16, integratedGradientSteps = 8)
            }
        }
    }

    @Test
    fun corruptModelFeatureCountIsRejectedBeforeAllocation() {
        val root = Files.createTempDirectory("classifier-model-corruption").toFile()
        val file = File(root, "corrupt.rklm")
        DataOutputStream(FileOutputStream(file).buffered()).use { output ->
            output.writeUTF("ROBOT_KINEMATICS_LOCAL_MODEL")
            output.writeInt(2)
            output.writeUTF("run")
            output.writeUTF(TrainingFeatureProfile.BASELINE_KINEMATICS.name)
            output.writeUTF("baseline")
            output.writeUTF("Baseline")
            output.writeUTF("linear")
            output.writeUTF(TrainingModelKind.LINEAR_SOFTMAX.name)
            output.writeInt(3)
            output.writeInt(3)
            output.writeInt(0)
            output.writeInt(Int.MAX_VALUE)
        }

        assertThrows(IllegalArgumentException::class.java) {
            TrainingStorageRepository(File(root, "runs"), File(root, "models")).loadModel(file)
        }
    }

    @Test
    fun labelParsingRejectsContradictoryScientificOutcomes() {
        assertEquals(
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.ACCEPTED,
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.fromCsv("ACCEPTED", "true", "SUCCESS")
        )
        assertEquals(
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.UNCERTAIN,
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.fromCsv(
                "UNCERTAIN",
                "true",
                "SUCCESS_WITH_WARNING"
            )
        )
        assertNull(
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.fromCsv("ACCEPTED", "false", "SUCCESS")
        )
        assertNull(
            com.robotkinematicslab.mobile.ml.data.TrainingLabel.fromCsv("REJECTED", "true", "NO_CONVERGENCE")
        )
    }

    @Test
    fun readerSkipsContradictoryInvalidAndMalformedRows() {
        val csv = createSyntheticScientificDataset(34)
        val lines = csv.readLines().toMutableList()
        val header = lines.first().split(',')

        fun corrupt(row: Int, column: String, value: String) {
            val fields = lines[row].split(',').toMutableList()
            fields[header.indexOf(column)] = value
            lines[row] = fields.joinToString(",")
        }

        corrupt(row = 1, column = "solverAccepted", value = "false")
        corrupt(row = 2, column = "jointTypes", value = "REVOLUTE;ALIEN;REVOLUTE")
        corrupt(row = 3, column = "targetX", value = "\"0.2")
        corrupt(row = 4, column = "detailCode", value = "INVALID_TARGET")
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val result =
            ScientificDatasetTrainingReader().load(
                csv,
                TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = 34
            )

        val dataset = requireNotNull(result.dataset)
        assertEquals(30, dataset.samples.size)
        assertEquals(4, dataset.skippedRowCount)
    }

    @Test
    fun readerRejectsRowsFromAnUnsupportedSchema() {
        val csv = createSyntheticScientificDataset(35)
        val lines = csv.readLines().toMutableList()
        val schemaColumn = lines.first().split(',').indexOf("schemaVersion")
        for (row in 1 until lines.size) {
            val fields = lines[row].split(',').toMutableList()
            fields[schemaColumn] = "999"
            lines[row] = fields.joinToString(",")
        }
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val result =
            ScientificDatasetTrainingReader().load(
                csv,
                TrainingFeatureProfile.BASELINE_KINEMATICS,
                maximumRows = 35
            )

        assertNull(result.dataset)
        assertTrue(result.errorMessage.orEmpty().contains("At least 30 valid rows"))
    }

    @Test
    fun readerRejectsBlankRobotIdentityInsteadOfCreatingOneSharedSplitGroup() {
        val csv = createSyntheticScientificDataset(31)
        val lines = csv.readLines().toMutableList()
        val header = lines.first().split(',')
        val fields = lines[1].split(',').toMutableList()
        fields[header.indexOf("robotId")] = ""
        lines[1] = fields.joinToString(",")
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val loaded = ScientificDatasetTrainingReader().load(
            csv,
            TrainingFeatureProfile.BASELINE_KINEMATICS,
            maximumRows = 31
        )

        assertEquals(30, requireNotNull(loaded.dataset).samples.size)
        assertEquals(1, loaded.dataset?.skippedRowCount)
    }

    @Test
    fun readerRejectsRobotIdThatChangesPhysicalDefinitionMidDataset() {
        val csv = createSyntheticScientificDataset(35)
        val lines = csv.readLines().toMutableList()
        val header = lines.first().split(',')
        val robotIdColumn = header.indexOf("robotId")
        val fields = lines[2].split(',').toMutableList()
        fields[robotIdColumn] = lines[1].split(',')[robotIdColumn]
        lines[2] = fields.joinToString(",")
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val loaded = ScientificDatasetTrainingReader().load(
            csv,
            TrainingFeatureProfile.BASELINE_KINEMATICS,
            maximumRows = 35
        )

        assertNull(loaded.dataset)
        assertTrue(loaded.errorMessage.orEmpty().contains("changes physical definition"))
    }

    @Test
    fun heldOutGroupingUsesMorphologyRatherThanDisplayAlias() {
        val csv = createSyntheticScientificDataset(35)
        val lines = csv.readLines().toMutableList()
        val header = lines.first().split(',')
        val dhColumn = header.indexOf("dhAMeters")
        val robotIdColumn = header.indexOf("robotId")
        val seedColumn = header.indexOf("seedJointValues")
        val targetXColumn = header.indexOf("targetX")
        val targetYColumn = header.indexOf("targetY")
        val targetZColumn = header.indexOf("targetZ")
        val second = lines[2].split(',').toMutableList()
        second[dhColumn] = lines[1].split(',')[dhColumn]
        second[robotIdColumn] = "robot-alias"
        second[seedColumn] = "0.10;0.200;0.000"
        second[targetXColumn] = "0.000"
        second[targetYColumn] = "0.0000"
        second[targetZColumn] = "0.0"
        lines[2] = second.joinToString(",")
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val dataset = requireNotNull(
            ScientificDatasetTrainingReader().load(
                csv,
                TrainingFeatureProfile.BASELINE_KINEMATICS,
                maximumRows = 35
            ).dataset
        )

        assertTrue(dataset.samples[0].robotId != dataset.samples[1].robotId)
        assertEquals(dataset.samples[0].robotFingerprint, dataset.samples[1].robotFingerprint)
        assertEquals(dataset.samples[0].splitFingerprint, dataset.samples[1].splitFingerprint)
    }

    @Test
    fun readerBuildsExplicitBaselineAndContextSchemas() {
        val csv = createSyntheticScientificDataset(120)
        val reader = ScientificDatasetTrainingReader()

        val baseline = requireNotNull(reader.load(csv, TrainingFeatureProfile.BASELINE_KINEMATICS, 120).dataset)
        val context = requireNotNull(reader.load(csv, TrainingFeatureProfile.CONTEXT_ENHANCED, 120).dataset)

        assertEquals(108, baseline.featureNames.size)
        assertEquals(130, context.featureNames.size)
        assertEquals(120, baseline.samples.size)
        assertEquals(120, context.samples.size)
        assertEquals(baseline.samples.map { it.labelIndex }, context.samples.map { it.labelIndex })
        assertTrue(context.featureNames.contains("seed_log_condition_number"))
        assertTrue(baseline.featureNames.none { it == "seed_log_condition_number" })
    }

    @Test
    fun comparisonIsReproducibleAndContextCanAddPredictiveSignal() {
        val csv = createSyntheticScientificDataset(360)
        val config =
            LocalTrainingConfig(
                runName = "scientific-comparison",
                datasetPath = csv.absolutePath,
                compareFeatureProfiles = true,
                modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                resourceMode = TrainingResourceMode.QUICK,
                maximumRows = 360,
                epochs = 35,
                batchSize = 32,
                learningRate = 0.01,
                randomSeed = 73,
                earlyStoppingPatience = 8
            )

        val first = LocalTrainingEngine().train(config)
        val second = LocalTrainingEngine().train(config)
        val firstContext = requireNotNull(first.comparison.contextEnhanced)
        val firstBaseline = requireNotNull(first.comparison.baseline)
        val secondContext = requireNotNull(second.comparison.contextEnhanced)

        assertEquals(firstContext.testMetrics.macroF1, secondContext.testMetrics.macroF1, 0.0)
        assertEquals(first.iterations.map { it.validationMetrics.macroF1 }, second.iterations.map { it.validationMetrics.macroF1 })
        assertTrue(firstContext.testMetrics.macroF1 > 0.90)
        assertTrue(firstContext.testMetrics.macroF1 > firstBaseline.testMetrics.macroF1 + 0.20)
        assertTrue(firstContext.duplicateFingerprintsKeptTogether)
    }

    @Test
    fun parallelMiniBatchTrainingIsReproducibleForFixedSeedAndWorkerCount() {
        val csv = createSyntheticScientificDataset(240)
        val config =
            LocalTrainingConfig(
                runName = "parallel-reproducibility",
                datasetPath = csv.absolutePath,
                compareFeatureProfiles = false,
                singleFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                modelKind = TrainingModelKind.COMPACT_MLP,
                resourceMode = TrainingResourceMode.QUICK,
                maximumRows = 240,
                epochs = 8,
                batchSize = 48,
                hiddenUnits = 12,
                randomSeed = 91,
                earlyStoppingPatience = 4,
                workerCount = 3
            )

        val first = LocalTrainingEngine().train(config)
        val second = LocalTrainingEngine().train(config)

        assertEquals(
            first.iterations.map { it.trainingLoss },
            second.iterations.map { it.trainingLoss }
        )
        assertEquals(
            requireNotNull(first.comparison.contextEnhanced).testMetrics.macroF1,
            requireNotNull(second.comparison.contextEnhanced).testMetrics.macroF1,
            0.0
        )
    }

    @Test
    fun parallelTrainingReportsEverySchedulerWorkCycleAndClosesTheReporter() {
        val csv = createSyntheticScientificDataset(180)
        val starts = AtomicInteger(0)
        val finishes = AtomicInteger(0)
        val closes = AtomicInteger(0)
        val engine =
            LocalTrainingEngine(
                workCycleReporterFactory = {
                    object : ComputeWorkCycleReporter {
                        override fun startCycle(): Long {
                            starts.incrementAndGet()
                            return System.nanoTime()
                        }

                        override fun finishCycle(startedAtNanos: Long) {
                            assertTrue(startedAtNanos > 0L)
                            finishes.incrementAndGet()
                        }

                        override fun close() {
                            closes.incrementAndGet()
                        }
                    }
                }
            )

        engine.train(
            LocalTrainingConfig(
                runName = "performance-hint-lifecycle",
                datasetPath = csv.absolutePath,
                compareFeatureProfiles = false,
                singleFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                modelKind = TrainingModelKind.COMPACT_MLP,
                resourceMode = TrainingResourceMode.QUICK,
                maximumRows = 180,
                epochs = 2,
                batchSize = 30,
                hiddenUnits = 8,
                earlyStoppingPatience = 2,
                workerCount = 3
            )
        )

        assertTrue(starts.get() > 0)
        assertEquals(starts.get(), finishes.get())
        assertEquals(1, closes.get())
    }

    @Test
    fun liveSingleWorkerCapPreventsParallelShardSubmission() {
        val csv = createSyntheticScientificDataset(90)
        val starts = AtomicInteger(0)
        val engine =
            LocalTrainingEngine(
                runtimeWorkerLimit = { 1 },
                workCycleReporterFactory = {
                    object : ComputeWorkCycleReporter {
                        override fun startCycle(): Long = starts.incrementAndGet().toLong()
                        override fun finishCycle(startedAtNanos: Long) = Unit
                        override fun close() = Unit
                    }
                }
            )

        val result =
            engine.train(
                LocalTrainingConfig(
                    runName = "live-worker-cap",
                    datasetPath = csv.absolutePath,
                    compareFeatureProfiles = false,
                    modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                    maximumRows = 90,
                    epochs = 1,
                    batchSize = 30,
                    earlyStoppingPatience = 1,
                    workerCount = 64
                )
            )

        assertTrue(result.iterations.isNotEmpty())
        assertEquals(0, starts.get())
    }

    @Test
    fun cancellationInsideParallelBatchIsUnwrappedAndStopsBeforeAnIterationCommits() {
        val csv = createSyntheticScientificDataset(180)
        val trainingStarted = AtomicBoolean(false)
        val cancellationChecks = AtomicInteger(0)
        val committedIterations = AtomicInteger(0)

        assertThrows(LocalTrainingCancelledException::class.java) {
            LocalTrainingEngine(runtimeWorkerLimit = { 4 }).train(
                config =
                    LocalTrainingConfig(
                        runName = "cancel-parallel-batch",
                        datasetPath = csv.absolutePath,
                        compareFeatureProfiles = false,
                        modelKind = TrainingModelKind.COMPACT_MLP,
                        maximumRows = 180,
                        epochs = 3,
                        batchSize = 120,
                        hiddenUnits = 16,
                        earlyStoppingPatience = 3,
                        workerCount = 4
                    ),
                cancellationRequested = {
                    trainingStarted.get() && cancellationChecks.incrementAndGet() >= 4
                },
                onProgress = { progress ->
                    if (progress.phase == com.robotkinematicslab.mobile.ml.training.LocalTrainingPhase.TRAINING_CONTEXT) {
                        trainingStarted.set(true)
                    }
                },
                onIteration = { committedIterations.incrementAndGet() }
            )
        }

        assertEquals(0, committedIterations.get())
        assertTrue(cancellationChecks.get() >= 4)
    }

    @Test
    fun interruptedCallingThreadCancelsBeforeReadingOrTraining() {
        val csv = createSyntheticScientificDataset(90)

        Thread.currentThread().interrupt()
        try {
            assertThrows(LocalTrainingCancelledException::class.java) {
                LocalTrainingEngine().train(
                    LocalTrainingConfig(
                        runName = "interrupted-before-start",
                        datasetPath = csv.absolutePath,
                        modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                        maximumRows = 90,
                        epochs = 1,
                        batchSize = 30,
                        workerCount = 1
                    )
                )
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun cancellationDuringDatasetRead_isReportedAsCancellationNotInvalidData() {
        val csv = createSyntheticScientificDataset(120)
        var checks = 0

        assertThrows(LocalTrainingCancelledException::class.java) {
            LocalTrainingEngine().train(
                config =
                    LocalTrainingConfig(
                        runName = "cancel-during-read",
                        datasetPath = csv.absolutePath,
                        compareFeatureProfiles = false,
                        modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                        maximumRows = 120,
                        epochs = 1
                    ),
                cancellationRequested = {
                    checks += 1
                    checks >= 3
                }
            )
        }
    }

    @Test
    fun robotHeldOutSplitNeverPlacesOneRobotInMultiplePartitions() {
        val csv = createSyntheticScientificDataset(350)
        val dataset =
            requireNotNull(
                ScientificDatasetTrainingReader()
                    .load(csv, TrainingFeatureProfile.CONTEXT_ENHANCED, 350)
                    .dataset
            )
        val prepared =
            TrainingDatasetPreparer().prepare(
                dataset = dataset,
                splitSeed = 17,
                splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT
            )

        fun robots(indices: IntArray): Set<Long> =
            indices.map { dataset.samples[it].robotFingerprint }.toSet()

        val trainRobots = robots(prepared.split.trainIndices)
        val validationRobots = robots(prepared.split.validationIndices)
        val testRobots = robots(prepared.split.testIndices)
        assertTrue(trainRobots.intersect(validationRobots).isEmpty())
        assertTrue(trainRobots.intersect(testRobots).isEmpty())
        assertTrue(validationRobots.intersect(testRobots).isEmpty())
        assertEquals(TrainingSplitStrategy.ROBOT_HELD_OUT, prepared.split.strategy)
    }

    @Test
    fun wholeFileSamplingPreventsOrderedCsvFromCollapsingHeldOutRunToOneRobot() {
        val csv = createSyntheticScientificDataset(350)
        val lines = csv.readLines()
        val header = lines.first()
        val robotColumn = header.split(',').indexOf("robotId")
        csv.writeText(
            buildList {
                add(header)
                addAll(lines.drop(1).sortedBy { it.split(',')[robotColumn] })
            }.joinToString("\n", postfix = "\n")
        )

        val dataset = requireNotNull(
            ScientificDatasetTrainingReader().load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = 60,
                sampleAcrossEntireFile = true,
                samplingSeed = 17
            ).dataset
        )
        assertEquals(60, dataset.samples.size)
        assertTrue(dataset.samples.map { it.robotFingerprint }.distinct().size >= 3)
        TrainingDatasetPreparer().prepare(dataset, 17, TrainingSplitStrategy.ROBOT_HELD_OUT)
    }

    @Test
    fun strictWholeFileAuditRejectsCorruptionOutsideTheSelectedCoverageSample() {
        val rowCount = 120
        val maximumRows = 30
        val samplingSeed = 17
        val csv = createSyntheticScientificDataset(rowCount)
        val selectedRows = DeterministicCsvRowSampler.indices(rowCount, maximumRows, samplingSeed)
        val corruptDataRow = (0 until rowCount).first { it !in selectedRows }
        val lines = csv.readLines().toMutableList()
        lines[corruptDataRow + 1] = "corrupt-row-outside-sample"
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val sampledOnly =
            ScientificDatasetTrainingReader().load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = maximumRows,
                sampleAcrossEntireFile = true,
                samplingSeed = samplingSeed,
                expectedDataRowCount = rowCount.toLong()
            )
        val strict =
            ScientificDatasetTrainingReader().load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = maximumRows,
                sampleAcrossEntireFile = true,
                samplingSeed = samplingSeed,
                expectedDataRowCount = rowCount.toLong(),
                requireEveryRowValid = true
            )

        assertNotNull(sampledOnly.dataset)
        assertNull(strict.dataset)
        assertTrue(strict.errorMessage.orEmpty().contains("integrity validation"))
    }

    @Test
    fun strictAuditScansBeyondPrefixWithoutExpandingTheRequestedSample() {
        val csv = createSyntheticScientificDataset(120)
        val reader = ScientificDatasetTrainingReader()

        val valid =
            reader.load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = 30,
                requireEveryRowValid = true
            )
        assertEquals(30, requireNotNull(valid.dataset).samples.size)

        val lines = csv.readLines().toMutableList()
        lines[101] = "corrupt-row-after-prefix"
        csv.writeText(lines.joinToString("\n", postfix = "\n"))
        val corrupt =
            reader.load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = 30,
                requireEveryRowValid = true
            )

        assertNull(corrupt.dataset)
        assertTrue(corrupt.errorMessage.orEmpty().contains("integrity validation"))
    }

    @Test
    fun directDatasetReadReportsCancellationInsteadOfInvalidRowCount() {
        val csv = createSyntheticScientificDataset(120)
        var cancellationChecks = 0

        val cancelled =
            ScientificDatasetTrainingReader().load(
                file = csv,
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                maximumRows = 120,
                cancellationRequested = {
                    cancellationChecks += 1
                    cancellationChecks >= 2
                },
                requireEveryRowValid = true
            )

        assertNull(cancelled.dataset)
        assertEquals("Dataset validation was cancelled.", cancelled.errorMessage)
    }

    @Test
    fun completedRunPersistsHistoryModelsAndRegistrySummary() {
        val csv = createSyntheticScientificDataset(180)
        val result =
            LocalTrainingEngine().train(
                LocalTrainingConfig(
                    runName = "stored-run",
                    datasetPath = csv.absolutePath,
                    modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                    maximumRows = 180,
                    epochs = 10,
                    batchSize = 32,
                    learningRate = 0.01,
                    earlyStoppingPatience = 4
                )
            )
        val root = Files.createTempDirectory("local-training-storage").toFile()
        val repository =
            TrainingStorageRepository(
                trainingDirectory = File(root, "training"),
                modelsDirectory = File(root, "models")
            )

        val stored = repository.save(result)
        val summary = repository.listRuns().single()

        assertTrue(File(stored.historyCsvPath).exists())
        assertTrue(stored.modelPaths.all { File(it).exists() })
        assertEquals(result.runId, summary.runId)
        assertNotNull(summary.baselineTestMacroF1)
        assertNotNull(summary.contextTestMacroF1)
        assertEquals(result.config.randomSeed, summary.randomSeed)
        assertEquals(result.config.splitStrategy, summary.splitStrategy)
        assertEquals(result.config.maximumRows, summary.maximumRows)
        assertEquals(result.comparison.variants.size, summary.variants.size)
        assertEquals(
            result.comparison.variants.map { it.featureSelectionId },
            summary.variants.map { it.featureSelectionId }
        )
        assertEquals(
            result.comparison.variants.map { it.featureNames.size },
            summary.variants.map { it.featureCount }
        )

        val storedHistory = repository.loadIterationHistory(summary)
        assertEquals(result.iterations.size, storedHistory.size)
        assertEquals(result.iterations.first().candidateId, storedHistory.first().candidateId)

        val original = requireNotNull(result.comparison.baseline)
        val reloaded = repository.loadModel(File(stored.modelPaths.first { it.contains("baseline") }))
        assertEquals(original.profile, reloaded.profile)
        assertEquals(original.featureNames, reloaded.featureNames)
        assertTrue(original.model.inputWeights.contentEquals(reloaded.model.inputWeights))
        assertTrue(original.normalization.means.contentEquals(reloaded.normalization.means))

        val checked = ModelComparisonLoader(repository).validateSelection(summary,summary.modelPaths[0],summary.modelPaths[1])
        val visualComparison = ModelComparisonLoader(repository).load(summary, maximumVisualPoints = 25,expectedEligibility=checked)
        assertEquals(checked.testCount,requireNotNull(visualComparison.leftTestMetrics).sampleCount)
        assertEquals(checked.testCount,requireNotNull(visualComparison.rightTestMetrics).sampleCount)
        assertThrows(IllegalArgumentException::class.java) {
            ModelComparisonLoader(repository).load(summary,maximumVisualPoints=25,expectedEligibility=checked.copy(leftModelSha256="0".repeat(64)))
        }
        assertTrue(visualComparison.points.isNotEmpty())
        assertTrue(visualComparison.points.size <= 25)
        assertTrue(visualComparison.aggregate.heldOutPointCount >= visualComparison.points.size)
        assertEquals(storedHistory.size, visualComparison.history.size)
        visualComparison.points.forEach { point ->
            assertEquals(3, point.robot.joints.size)
            assertEquals(
                point.baselinePrediction.predictedLabel == point.oracleLabel,
                point.baselinePrediction.correct
            )
            assertEquals(
                point.contextPrediction.predictedLabel == point.oracleLabel,
                point.contextPrediction.correct
            )
            assertEquals(3, point.baselinePrediction.probabilities.size)
            assertEquals(3, point.contextPrediction.probabilities.size)
            assertEquals(1.0, point.baselinePrediction.probabilities.sum(), 1e-5)
            assertEquals(1.0, point.contextPrediction.probabilities.sum(), 1e-5)
        }

        val repeatedComparison = ModelComparisonLoader(repository).load(summary, maximumVisualPoints = 25)
        assertEquals(
            visualComparison.points.map { it.sourceRowIndex },
            repeatedComparison.points.map { it.sourceRowIndex }
        )
        assertEquals(
            visualComparison.points.map { it.baselinePrediction },
            repeatedComparison.points.map { it.baselinePrediction }
        )
        assertEquals(
            visualComparison.points.map { it.contextPrediction },
            repeatedComparison.points.map { it.contextPrediction }
        )

        assertThrows(com.robotkinematicslab.mobile.ml.comparison.ModelComparisonCancelledException::class.java) {
            ModelComparisonLoader(repository).load(
                summary,
                maximumVisualPoints = 25,
                cancellationRequested = { true }
            )
        }

        val publishedFiles =
            (stored.modelPaths.map(::File) + File(stored.historyCsvPath) + File(stored.summaryPath))
                .associateWith(File::readBytes)
        assertThrows(IllegalArgumentException::class.java) { repository.save(result) }
        publishedFiles.forEach { (file, bytes) -> assertTrue(bytes.contentEquals(file.readBytes())) }

        val outside = Files.createTempFile("forged-training-history", ".csv").toFile().apply {
            writeText(File(stored.historyCsvPath).readText())
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.loadIterationHistory(summary.copy(historyCsvPath = outside.absolutePath))
        }

        val summaryFile = File(stored.summaryPath)
        val corruptProperties = java.util.Properties().apply { summaryFile.inputStream().use(::load) }
        corruptProperties.setProperty("variant.0.testMacroF1", "NaN")
        summaryFile.outputStream().use { corruptProperties.store(it, "corrupt scientific metric") }
        val corruptSummaryBytes = summaryFile.readBytes()
        assertTrue(repository.listRuns().isEmpty())
        assertTrue(corruptSummaryBytes.contentEquals(summaryFile.readBytes()))
    }

    @Test
    fun featureSetIdsThatDifferOnlyByCaseNeverOverwriteEachOthersModels() {
        val csv = createSyntheticScientificDataset(90)
        val baselineNames = ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.BASELINE_KINEMATICS)
        val result =
            LocalTrainingEngine().train(
                LocalTrainingConfig(
                    runName = "case-sensitive-feature-ids",
                    datasetPath = csv.absolutePath,
                    modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                    maximumRows = 90,
                    epochs = 1,
                    batchSize = 30,
                    earlyStoppingPatience = 1,
                    featureSelections =
                        listOf(
                            FeatureSelectionSpec("CaseArm", "Upper case arm", TrainingFeatureProfile.BASELINE_KINEMATICS, baselineNames),
                            FeatureSelectionSpec("casearm", "Lower case arm", TrainingFeatureProfile.BASELINE_KINEMATICS, baselineNames)
                        )
                )
            )
        val root = Files.createTempDirectory("case-sensitive-model-storage").toFile()
        val repository = TrainingStorageRepository(File(root, "runs"), File(root, "models"))

        val stored = repository.save(result)

        assertEquals(2, stored.modelPaths.distinct().size)
        assertTrue(stored.modelPaths.all { File(it).isFile })
        assertEquals(
            setOf("CaseArm", "casearm"),
            stored.modelPaths.map { repository.loadModel(File(it)).featureSelectionId }.toSet()
        )
    }

    private fun createSyntheticScientificDataset(rowCount: Int): File {
        val file = Files.createTempFile("scientific-training", ".csv").toFile()
        val header = ScientificDatasetCsvWriter.HEADER
        file.bufferedWriter().use { writer ->
            writer.appendLine(header.joinToString(","))
            repeat(rowCount) { rowIndex ->
                val classIndex = rowIndex % 3
                val acceptance = listOf("ACCEPTED", "UNCERTAIN", "REJECTED")[classIndex]
                val status = listOf("SUCCESS", "SUCCESS_WITH_WARNING", "NO_CONVERGENCE")[classIndex]
                val initialError = listOf(0.05, 0.45, 0.95)[classIndex]
                val values =
                    mapOf(
                        "schemaVersion" to ScientificDatasetCsvWriter.SCHEMA_VERSION,
                        "globalRowIndex" to rowIndex.toString(),
                        "batchId" to "batch-${rowIndex / 30}",
                        "baseRandomSeed" to "42",
                        "robotRandomSeed" to (10_000L + rowIndex).toString(),
                        "randomProtocol" to "test",
                        "robotId" to "robot-${rowIndex % 7}",
                        "robotName" to "Synthetic",
                        "jointCount" to "3",
                        "jointTypes" to "REVOLUTE;REVOLUTE;REVOLUTE",
                        "dhThetaRad" to "0;0;0",
                        "dhDMeters" to "0.2;0;0",
                        // Give every synthetic robot id a genuinely different morphology.
                        // A held-out-robot test must not relabel one identical mechanism seven times.
                        "dhAMeters" to "0;${0.5 + (rowIndex % 7) * 0.01};0.4",
                        "dhAlphaRad" to "1.57079632679;0;0",
                        "jointMinValues" to "-3.14;-3.14;-3.14",
                        "jointMaxValues" to "3.14;3.14;3.14",
                        "jointHomeValues" to "0;0;0",
                        "ikMaxIterations" to "800",
                        "ikToleranceMeters" to "0.00001",
                        "ikDamping" to "0.05",
                        "ikMaxStep" to "0.02",
                        "sampleIndex" to rowIndex.toString(),
                        "targetClass" to "REACHABLE",
                        "targetSamplingStrategy" to "UNIFORM_JOINT_SPACE_FK",
                        "targetSourceJointValues" to "0;0;0",
                        "seedJointValues" to "0.1;0.2;${(rowIndex % 17) * 0.01}",
                        "targetX" to ((rowIndex / 3 % 11) * 0.01).toString(),
                        "targetY" to ((rowIndex / 9 % 7) * 0.01).toString(),
                        "targetZ" to ((rowIndex / 21 % 5) * 0.01).toString(),
                        "solutionJointValues" to "0.1;0.2;0.3",
                        "solverAccepted" to (classIndex != 2).toString(),
                        "acceptanceClass" to acceptance,
                        "status" to status,
                        "detailCode" to
                            if (classIndex == 1) {
                                "NEAR_SINGULARITY_WARNING"
                            } else {
                                "NONE"
                            },
                        "converged" to (classIndex != 2).toString(),
                        "finalError" to listOf(0.000001, 0.000005, 0.2)[classIndex].toString(),
                        "iterations" to "40",
                        "initialError" to initialError.toString(),
                        "improvement" to "0.1",
                        "improvementRatio" to "0.5",
                        "progressClass" to acceptance,
                        "seedDistanceBucket" to "MEDIUM",
                        "iterationSaturationRatio" to "0.05",
                        "jointDeltaNorm" to "0.1",
                        "maxSingleJointMovement" to "0.1",
                        "seedMinNormalizedLimitMargin" to listOf(0.8, 0.4, 0.1)[classIndex].toString(),
                        "seedConditionNumber" to listOf(2.0, 100.0, 10000.0)[classIndex].toString(),
                        "seedLogConditionNumber" to listOf(0.3, 2.0, 4.0)[classIndex].toString(),
                        "normalizedJointTravelRms" to "0.1",
                        "finalMinNormalizedLimitMargin" to "0.2",
                        "backtrackingRetryCount" to "0",
                        "solveDurationNanos" to "1000",
                        "nearLimitJointCount" to "0",
                        "nearLimitJointNames" to "",
                        "jointLimitPressureRatio" to "0"
                    )
                writer.appendLine(header.joinToString(",") { values[it].orEmpty() })
            }
        }
        return file
    }
}
