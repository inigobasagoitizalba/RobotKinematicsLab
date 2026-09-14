package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OneMicronExperimentContractTest {

    @Test
    fun validMultiConfigurationPlanUsesOneExactEffectiveContract() {
        val full = OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361)
        val small = OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.KINEMATICS_108)
        val budget = OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(361, 60, 64, 2, 32)

        val plan = OneMicronExperimentPlanner.plan(request(listOf(small, full), budget = budget))
        val configs = plan.trainingConfigs()

        assertEquals(60, plan.effectiveRows)
        assertTrue(plan.wasCapped)
        assertEquals(listOf(60, 60), configs.map(OneMicronIkTrainingConfig::maximumRows))
        assertEquals(listOf(100_000, 100_000), configs.map(OneMicronIkTrainingConfig::requestedMaximumRows))
        assertTrue(configs.all { it.randomSeed == 42 && it.splitStrategy == TrainingSplitStrategy.ROBOT_HELD_OUT })
        assertEquals(small.includedFeatureNames, configs[0].resolvedFeatureSelection.includedFeatureNames)
        assertEquals(full.includedFeatureNames, configs[1].resolvedFeatureSelection.includedFeatureNames)
    }

    @Test
    fun unknownFeatureIsRejectedWhileExactReorderedSubsetIsPreserved() {
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val valid = OneMicronFeatureSelectionSpec("ordered", "Ordered subset", OneMicronIkFeatureProfile.KINEMATICS_108, listOf(names[2], names[0]))
        val invalid = OneMicronFeatureSelectionSpec("corrupt", "Corrupt subset", OneMicronIkFeatureProfile.KINEMATICS_108, listOf(names[0], "unknown_input"))
        val budget = 64L * 1024L * 1024L

        val accepted = OneMicronExperimentPlanner.plan(request(listOf(valid), budget = budget)).trainingConfigs().single()
        assertEquals(listOf(names[2], names[0]), accepted.resolvedFeatureSelection.includedFeatureNames)
        assertThrows(IllegalArgumentException::class.java) {
            OneMicronExperimentPlanner.plan(request(listOf(invalid), budget = budget))
        }
    }

    @Test
    fun invalidFingerprintAndDuplicateConfigurationIdsCannotEnterExecution() {
        val selection = OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.KINEMATICS_108)
        val valid = request(listOf(selection), budget = 64L * 1024L * 1024L)

        assertTrue(OneMicronExperimentPlanner.plan(valid).effectiveRows >= 30)
        assertThrows(IllegalArgumentException::class.java) {
            OneMicronExperimentPlanner.plan(valid.copy(datasetScientificFingerprint = "not-a-sha256"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OneMicronExperimentPlanner.plan(valid.copy(featureSelections = listOf(selection, selection)))
        }
    }

    @Test
    fun safeRecommendationChangesVisibleFieldsOnlyToSupportedValues() {
        val selection = OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361)
        val minimumBudget = OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(361, 30, 4, 1, 1)
        val unsafe =
            request(listOf(selection), budget = minimumBudget).copy(
                requestedRows = 100_000,
                hiddenUnits = 1_024,
                batchSize = 128
            )

        assertThrows(IllegalArgumentException::class.java) { OneMicronExperimentPlanner.plan(unsafe) }
        val recommended = requireNotNull(OneMicronExperimentPlanner.recommendedSafePlan(unsafe))
        assertEquals(30, recommended.effectiveRows)
        assertEquals(4, recommended.request.hiddenUnits)
        assertEquals(1, recommended.request.batchSize)
        assertTrue(recommended.armMemoryPlans.single().isRunnable)
    }

    @Test
    fun unroundedToleranceBoundaryAcceptsBelowAndEqualButRejectsAboveAndInvalid() {
        assertTrue(OneMicronVerificationCriterion.acceptsPositionResidual(Math.nextDown(ONE_MICRON_METERS)))
        assertTrue(OneMicronVerificationCriterion.acceptsPositionResidual(ONE_MICRON_METERS))
        assertFalse(OneMicronVerificationCriterion.acceptsPositionResidual(Math.nextUp(ONE_MICRON_METERS)))
        assertFalse(OneMicronVerificationCriterion.acceptsPositionResidual(Double.NaN))
        assertFalse(OneMicronVerificationCriterion.acceptsPositionResidual(-0.0 - Double.MIN_VALUE))
    }

    @Test
    fun routeObservationsAcceptConsistentEvidenceAndRejectInflatedAiRate() {
        val observations =
            listOf(
                OneMicronVerificationObservation(10, 1, VerifiedIkPath.NEURAL_DIRECT, ONE_MICRON_METERS, Double.NaN, 2e-6, ONE_MICRON_METERS, false, 0, 0, 8),
                OneMicronVerificationObservation(11, 2, VerifiedIkPath.DETERMINISTIC_FALLBACK, 2e-6, 1.5e-6, ONE_MICRON_METERS, ONE_MICRON_METERS, true, 7, 9, 9)
            )
        val valid = metrics(observations, rawRate = 0.5)

        assertEquals(0.5, valid.directNeuralSuccessRate, 0.0)
        assertEquals(0.5, valid.fallbackOnlySuccessRate, 0.0)
        assertThrows(IllegalArgumentException::class.java) { metrics(observations, rawRate = 1.0) }
    }

    @Test
    fun savedReportContainsTheExactVisibleAndExecutedContract() {
        val root = Files.createTempDirectory("micron-contract-report").toFile()
        val selection = OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.KINEMATICS_108)
        val config =
            OneMicronExperimentPlanner.plan(request(listOf(selection), budget = 64L * 1024L * 1024L).copy(requestedRows = 100))
                .trainingConfigs().single()
        val model = zeroModel(selection.featureCount)
        val result =
            OneMicronIkTrainingResult(
                runId = "exact-contract",
                config = config,
                model = model,
                normalization = FeatureNormalization(FloatArray(selection.featureCount), FloatArray(selection.featureCount) { 1f }),
                featureNames = selection.includedFeatureNames,
                epochs = emptyList(),
                bestEpoch = 0,
                trainRows = 70,
                validationRows = 15,
                testRows = 15,
                skippedRows = 0,
                verification = metrics(emptyList(), 0.0, samples = 0, cumulativeRefinedRate = 0.0, pipelineRate = 0.0, baselineRate = 0.0),
                startedAtEpochMillis = 1,
                finishedAtEpochMillis = 2
            )
        val saved = OneMicronIkStorageRepository(File(root, "runs"), File(root, "models")).save(result)
        val properties = Properties().apply { File(saved.reportPath).inputStream().use(::load) }

        assertEquals(config.datasetScientificFingerprint, properties.getProperty("datasetScientificFingerprint"))
        assertEquals(config.datasetRobotIds.joinToString(","), properties.getProperty("datasetRobotIds"))
        assertEquals(config.resolvedFeatureSelection.includedFeatureNames.joinToString(","), properties.getProperty("orderedFeatureNames"))
        assertEquals(config.splitStrategy.name, properties.getProperty("splitStrategy"))
        assertEquals(config.requestedMaximumRows.toString(), properties.getProperty("requestedMaximumRows"))
        assertEquals(config.maximumRows.toString(), properties.getProperty("effectiveMaximumRows"))
        assertEquals(config.randomSeed.toString(), properties.getProperty("randomSeed"))
        assertEquals(config.verificationSampleLimit.toString(), properties.getProperty("untouchedCasesRequested"))
    }

    private fun request(selections: List<OneMicronFeatureSelectionSpec>, budget: Long): OneMicronExperimentRequest =
        OneMicronExperimentRequest(
            runName = "controlled",
            datasetPath = "/scientific/dataset.csv",
            datasetScientificFingerprint = "a".repeat(64),
            datasetRowCount = 100_000,
            datasetRobotIds = listOf("robot-a", "robot-b", "robot-c"),
            featureSelections = selections,
            splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
            requestedRows = 100_000,
            epochs = 50,
            batchSize = 32,
            hiddenUnits = 64,
            learningRate = 0.001,
            randomSeed = 42,
            verificationSampleLimit = 1_000,
            workerCount = 2,
            workingMemoryBudgetBytes = budget,
            solverConfig = oneMicronSolverConfig()
        )

    private fun metrics(
        observations: List<OneMicronVerificationObservation>,
        rawRate: Double,
        samples: Int = observations.size,
        cumulativeRefinedRate: Double = rawRate,
        pipelineRate: Double = if (samples == 0) 0.0 else 1.0,
        baselineRate: Double = if (samples == 0) 0.0 else observations.count { it.pureSolverCertified }.toDouble() / samples
    ): OneMicronIkVerificationMetrics =
        OneMicronIkVerificationMetrics(
            samples = samples,
            rawNeuralSuccessRate = rawRate,
            neuralThenRefineSuccessRate = cumulativeRefinedRate,
            deterministicBaselineSuccessRate = baselineRate,
            rawMedianErrorMeters = 0.0,
            rawP95ErrorMeters = 0.0,
            refinedMedianErrorMeters = 0.0,
            baselineMedianErrorMeters = 0.0,
            meanRefinedIterations = 0.0,
            meanBaselineIterations = 0.0,
            meanNeuralInferenceNanos = 0.0,
            verifiedPipelineSuccessRate = pipelineRate,
            observations = observations
        )

    private fun zeroModel(inputCount: Int): LocalIkRegressionModel =
        LocalIkRegressionModel(
            inputFeatureCount = inputCount,
            hiddenUnitCount = 4,
            inputWeights = FloatArray(inputCount * 4),
            hiddenBiases = FloatArray(4),
            outputWeights = FloatArray(4 * ONE_MICRON_MAX_JOINTS),
            outputBiases = FloatArray(ONE_MICRON_MAX_JOINTS)
        )
}
