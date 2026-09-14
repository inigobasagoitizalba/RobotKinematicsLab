package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import java.io.File
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OneMicronAntiCorruptionRound1Test {

    @Test
    fun corruptFeatureCountIsRejectedBeforeAllocation() {
        val root = Files.createTempDirectory("micron-model-size-corruption").toFile()
        val file = File(root, "corrupt.rkl-micron")
        DataOutputStream(FileOutputStream(file).buffered()).use { output ->
            output.writeUTF("RKL_VERIFIED_MICRON_IK")
            output.writeInt(1)
            output.writeUTF("run")
            output.writeUTF(OneMicronIkFeatureProfile.KINEMATICS_108.name)
            output.writeInt(100)
            output.writeDouble(ONE_MICRON_METERS)
            output.writeDouble(0.01)
            output.writeDouble(0.1)
            output.writeInt(Int.MAX_VALUE)
        }

        assertThrows(IllegalArgumentException::class.java) {
            OneMicronIkStorageRepository(File(root, "runs"), File(root, "models")).loadModel(file)
        }
    }

    @Test
    fun insufficientMemoryBudgetNeverClaimsThatThirtyRowsAreSafe() {
        val bytesForThirty =
            OneMicronIkMemoryEstimator.estimatedWorkingSetBytes(
                OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361,
                30
            )

        assertEquals(
            0,
            OneMicronIkMemoryEstimator.safeRowLimit(
                OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361,
                bytesForThirty - 1
            )
        )
    }

    @Test
    fun trainingMemoryGateIncludesModelOptimizerAndActiveWorkerBuffers() {
        val profile = OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361
        val oneWorkerBudget =
            OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                profile = profile,
                rows = 30,
                hiddenUnits = 64,
                workerCount = 1,
                batchSize = 30
            )
        val eightWorkerBudget =
            OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                profile = profile,
                rows = 30,
                hiddenUnits = 64,
                workerCount = 8,
                batchSize = 30
            )

        assertTrue(eightWorkerBudget > oneWorkerBudget)
        assertEquals(
            30,
            OneMicronIkMemoryEstimator.safeTrainingRowLimit(
                profile,
                oneWorkerBudget,
                hiddenUnits = 64,
                workerCount = 1,
                batchSize = 30
            )
        )
        assertEquals(
            0,
            OneMicronIkMemoryEstimator.safeTrainingRowLimit(
                profile,
                oneWorkerBudget - 1L,
                hiddenUnits = 64,
                workerCount = 1,
                batchSize = 30
            )
        )
    }

    @Test
    fun effectivePlanUsesTheSameCompleteWorkingSetForPreviewAndExecution() {
        val featureCount = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361).size
        val budget = OneMicronIkMemoryEstimator.estimatedWorkingSetBytes(featureCount, 48_032)

        val plan =
            OneMicronIkMemoryEstimator.effectivePlan(
                requestedRows = 100_000,
                featureCount = featureCount,
                hiddenUnits = 64,
                workerCount = 4,
                batchSize = 128,
                budgetBytes = budget
            )

        assertTrue(plan.isRunnable)
        assertTrue(plan.wasCapped)
        assertTrue(plan.effectiveRows < 48_032)
        assertTrue(plan.estimatedBytes <= budget)
        assertEquals(
            plan.estimatedBytes,
            OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                featureCount,
                plan.effectiveRows,
                plan.hiddenUnits,
                plan.workerCount,
                plan.batchSize
            )
        )
    }

    @Test
    fun oneByteBelowMinimumFullPlanIsRejectedInsteadOfAdvertisingThirtyRows() {
        val featureCount = 361
        val minimum =
            OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                featureCount,
                30,
                hiddenUnits = 64,
                workerCount = 2,
                batchSize = 30
            )

        val invalid =
            OneMicronIkMemoryEstimator.effectivePlan(
                requestedRows = 100_000,
                featureCount = featureCount,
                hiddenUnits = 64,
                workerCount = 2,
                batchSize = 30,
                budgetBytes = minimum - 1
            )
        val valid =
            OneMicronIkMemoryEstimator.effectivePlan(
                requestedRows = 30,
                featureCount = featureCount,
                hiddenUnits = 64,
                workerCount = 2,
                batchSize = 30,
                budgetBytes = minimum
            )

        assertEquals(0, invalid.effectiveRows)
        assertTrue(!invalid.isRunnable)
        assertEquals(30, valid.effectiveRows)
        assertTrue(valid.isRunnable)
    }

    @Test
    fun selectedFeatureSubsetDoesNotReserveBuffersForUnselectedColumns() {
        val budget = 32L * 1_024L * 1_024L
        val full = OneMicronIkMemoryEstimator.safeTrainingRowLimit(361, budget, 64, 2, 128)
        val subset = OneMicronIkMemoryEstimator.safeTrainingRowLimit(108, budget, 64, 2, 128)

        assertTrue(subset > full)
    }

    @Test
    fun rowOnlyBudgetCannotBypassFullTrainingMemoryGate() {
        val profile = OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361
        val error = assertThrows(IllegalArgumentException::class.java) {
            OneMicronIkTrainingEngine().run(
                OneMicronIkTrainingConfig(
                    runName = "unsafe-training-memory",
                    datasetPath = "/dataset/that/does/not/exist.csv",
                    profile = profile,
                    maximumRows = 30,
                    hiddenUnits = 1_024,
                    workerCount = 8,
                    batchSize = 30,
                    maximumWorkingMemoryBytes =
                        OneMicronIkMemoryEstimator.estimatedWorkingSetBytes(profile, 30)
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("model and worker buffers"))
    }

    @Test
    fun invalidSolverConfigurationCannotBeEncodedAsPlausibleFeatures() {
        val robot = DatasetRobotPresets().buildDefaults().first().robot
        val seed = RobotState(robot.joints.map { it.homeValue })
        val target = ForwardKinematicsSolver().solve(robot, seed).endEffectorPosition

        assertThrows(IllegalArgumentException::class.java) {
            OneMicronIkFeatureEncoder.encode(
                robot,
                seed,
                target,
                IKConfig(maxIterations = -1, tolerance = -1.0, damping = -1.0, maxStep = -1.0),
                OneMicronIkFeatureProfile.KINEMATICS_108
            )
        }
    }

    @Test
    fun nonFiniteRuntimeFeaturesCannotReachModelArithmetic() {
        val model = zeroModel(3)

        assertThrows(IllegalArgumentException::class.java) {
            model.predict(floatArrayOf(0f, Float.NaN, 1f))
        }
    }

    @Test
    fun nonFiniteTrainingHyperparametersFailBeforeDatasetAccess() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            OneMicronIkTrainingEngine().run(
                OneMicronIkTrainingConfig(
                    runName = "invalid-rate",
                    datasetPath = "/dataset/that/does/not/exist.csv",
                    maximumRows = 30,
                    learningRate = Double.NaN
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("Learning rate"))
    }

    @Test
    fun invalidOneMicronSolverContractFailsBeforeDatasetAccess() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            OneMicronIkTrainingEngine().run(
                OneMicronIkTrainingConfig(
                    runName = "invalid-solver",
                    datasetPath = "/dataset/that/does/not/exist.csv",
                    maximumRows = 30,
                    solverConfig = IKConfig(tolerance = -1.0)
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("tolerance"))
    }

    @Test
    fun normalizationUsesDoubleIntermediatesForExtremeFiniteFeatures() {
        val samples =
            List(30) { index ->
                OneMicronIkSample(
                    features = floatArrayOf(if (index % 2 == 0) Float.MAX_VALUE else -Float.MAX_VALUE),
                    normalizedJointDelta = FloatArray(ONE_MICRON_MAX_JOINTS),
                    outputMask = FloatArray(ONE_MICRON_MAX_JOINTS) { if (it == 0) 1f else 0f },
                    robotIndex = 0,
                    seedState = RobotState(listOf(0.0)),
                    target = Vec3.ZERO,
                    deterministicIterations = 1,
                    sourceRowIndex = index.toLong(),
                    splitFingerprint = index.toLong(),
                    robotFingerprint = (index % 3).toLong()
                )
            }
        val robot = DatasetRobotPresets().buildDefaults().first().robot
        val dataset =
            OneMicronIkDataset(
                sourcePath = "synthetic",
                profile = OneMicronIkFeatureProfile.KINEMATICS_108,
                featureNames = listOf("extreme"),
                robots = listOf(robot),
                robotIds = listOf("robot"),
                samples = samples,
                rowsRead = samples.size,
                skippedRows = 0,
                rejectedByMicronContract = 0
            )

        val prepared = OneMicronIkDatasetPreparer().prepare(
            dataset,
            splitSeed = 17,
            strategy = com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy.SAMPLE_GROUPED
        )

        assertTrue(prepared.normalization.standardDeviations.single() > 1e30f)
        assertTrue(prepared.normalizedFeatures.all { row -> row.single().isFinite() })
    }

    @Test
    fun reorderedFeatureSchemaCannotMasqueradeAsCompatibleStoredModel() {
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val reordered = names.toMutableList().apply { this[0] = "corrupted_feature" }

        assertThrows(IllegalArgumentException::class.java) {
            storedModel(reordered)
        }
    }

    @Test
    fun trailingPayloadInvalidatesPersistedModel() {
        val root = Files.createTempDirectory("micron-model-corruption").toFile()
        val storage = OneMicronIkStorageRepository(File(root, "runs"), File(root, "models"))
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val result = minimalTrainingResult(storedModel(names))
        val saved = storage.save(result)
        File(saved.modelPath).appendBytes(byteArrayOf(0x13, 0x37))

        assertThrows(IllegalArgumentException::class.java) {
            storage.loadModel(File(saved.modelPath))
        }
    }

    @Test
    fun unsafeRunIdIsRejectedBeforeWritingOutsideManagedStorage() {
        val root = Files.createTempDirectory("micron-run-path").toFile()
        val runs = File(root, "runs")
        val models = File(root, "models")
        val storage = OneMicronIkStorageRepository(runs, models)
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val result = minimalTrainingResult(storedModel(names)).copy(runId = "../escaped")

        assertThrows(IllegalArgumentException::class.java) { storage.save(result) }

        assertTrue(!File(root, "escaped").exists())
        assertTrue(runs.listFiles().isNullOrEmpty())
        assertTrue(models.listFiles().isNullOrEmpty())
    }

    @Test
    fun duplicateRunCannotOverwritePublishedModelOrEvidence() {
        val root = Files.createTempDirectory("micron-run-collision").toFile()
        val storage = OneMicronIkStorageRepository(File(root, "runs"), File(root, "models"))
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val result = minimalTrainingResult(storedModel(names))
        val saved = storage.save(result)
        val model = File(saved.modelPath)
        val report = File(saved.reportPath)
        val history = File(report.parentFile, "learning-curve.csv")
        val before = listOf(model, report, history).associateWith(File::readBytes)

        assertThrows(IllegalArgumentException::class.java) { storage.save(result) }

        before.forEach { (file, bytes) -> assertTrue(bytes.contentEquals(file.readBytes())) }
        assertEquals(result.runId, storage.loadModel(model).runId)
    }

    @Test
    fun interruptedPublishedRunIsQuarantinedAndDoesNotBlockAnExactRetry() {
        val root = Files.createTempDirectory("micron-run-interrupted").toFile()
        val runs = File(root, "runs").apply { mkdirs() }
        val models = File(root, "models")
        val interruptedRun = File(runs, "anti-corruption").apply { mkdirs() }
        val preservedText = "must remain recoverable"
        val preservedEvidence = File(interruptedRun, "interrupted-evidence.txt").apply { writeText(preservedText) }
        val storage = OneMicronIkStorageRepository(runs, models)
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val result = minimalTrainingResult(storedModel(names))

        val saved = storage.save(result)

        assertTrue(File(saved.modelPath).isFile)
        assertTrue(File(saved.reportPath).isFile)
        assertEquals(result.runId, storage.loadModel(File(saved.modelPath)).runId)
        val quarantine =
            runs.listFiles(File::isDirectory).orEmpty().single { directory ->
                directory.name.startsWith(".${result.runId}-interrupted-")
            }
        assertEquals(
            preservedText,
            File(quarantine, preservedEvidence.name).readText()
        )
    }

    private fun zeroModel(inputCount: Int): LocalIkRegressionModel =
        LocalIkRegressionModel(
            inputFeatureCount = inputCount,
            hiddenUnitCount = 4,
            inputWeights = FloatArray(inputCount * 4),
            hiddenBiases = FloatArray(4),
            outputWeights = FloatArray(4 * ONE_MICRON_MAX_JOINTS),
            outputBiases = FloatArray(ONE_MICRON_MAX_JOINTS)
        )

    private fun storedModel(names: List<String>): StoredOneMicronIkModel =
        StoredOneMicronIkModel(
            runId = "anti-corruption",
            profile = OneMicronIkFeatureProfile.KINEMATICS_108,
            solverConfig = oneMicronSolverConfig(),
            featureNames = names,
            normalization = FeatureNormalization(FloatArray(names.size), FloatArray(names.size) { 1f }),
            model = zeroModel(names.size)
        )

    private fun minimalTrainingResult(stored: StoredOneMicronIkModel): OneMicronIkTrainingResult =
        OneMicronIkTrainingResult(
            runId = stored.runId,
            config =
                OneMicronIkTrainingConfig(
                    runName = "anti-corruption",
                    datasetPath = "unused.csv",
                    profile = stored.profile,
                    maximumRows = 30
                ),
            model = stored.model,
            normalization = stored.normalization,
            featureNames = stored.featureNames,
            epochs = emptyList(),
            bestEpoch = 0,
            trainRows = 24,
            validationRows = 3,
            testRows = 3,
            skippedRows = 0,
            verification =
                OneMicronIkVerificationMetrics(
                    samples = 0,
                    rawNeuralSuccessRate = 0.0,
                    neuralThenRefineSuccessRate = 0.0,
                    deterministicBaselineSuccessRate = 0.0,
                    rawMedianErrorMeters = 0.0,
                    rawP95ErrorMeters = 0.0,
                    refinedMedianErrorMeters = 0.0,
                    baselineMedianErrorMeters = 0.0,
                    meanRefinedIterations = 0.0,
                    meanBaselineIterations = 0.0,
                    meanNeuralInferenceNanos = 0.0
                ),
            startedAtEpochMillis = 1L,
            finishedAtEpochMillis = 2L
        )
}
