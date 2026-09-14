package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedDatasetTrainingIntegrationTest {

    @Test
    fun appGeneratedRowsTrainBothProfilesWithoutLeakingTheOutcome() {
        val robots = DatasetRobotPresets().buildDefaults().take(4)
        val rowsPerRobot = 60
        val csv = Files.createTempFile("app-generated-training", ".csv").toFile()
        val generation =
            ScientificDatasetGenerator().generate(
                config =
                    DatasetGenerationConfig(
                        datasetName = "training-integration",
                        robots = robots,
                        samplesPerRobot = rowsPerRobot,
                        randomSeed = 2_604,
                        targetMode = DatasetTargetMode.MIXED,
                        reachableFraction = 0.65,
                        filterMode = DatasetFilterMode.ALL,
                        append = false,
                        ikConfig =
                            IKConfig(
                                maxIterations = 120,
                                tolerance = 1e-4,
                                damping = 0.05,
                                maxStep = 0.05
                            )
                    ),
                csvFile = csv,
                existingRowCount = 0,
                generationIndex = 0
            )

        assertTrue(generation.completed)
        assertEquals(robots.size * rowsPerRobot, generation.addedRows)

        val result =
            LocalTrainingEngine().train(
                LocalTrainingConfig(
                    runName = "generated-dataset-integration",
                    datasetPath = csv.absolutePath,
                    compareFeatureProfiles = true,
                    modelKind = TrainingModelKind.AUTOMATIC,
                    resourceMode = TrainingResourceMode.QUICK,
                    maximumRows = generation.addedRows,
                    epochs = 18,
                    batchSize = 32,
                    learningRate = 0.003,
                    randomSeed = 91,
                    earlyStoppingPatience = 5
                )
            )

        val baseline = requireNotNull(result.comparison.baseline)
        val context = requireNotNull(result.comparison.contextEnhanced)
        assertEquals(108, baseline.featureNames.size)
        assertEquals(130, context.featureNames.size)
        assertTrue(baseline.testMetrics.macroF1.isFinite())
        assertTrue(context.testMetrics.macroF1.isFinite())
        assertTrue(baseline.testRowCount > 0)
        assertEquals(baseline.testRowCount, context.testRowCount)
        assertTrue(result.iterations.isNotEmpty())

        println(
            "GENERATED_DATASET_ML_RESULT " +
                "baseline=${baseline.candidateId},baselineMacroF1=${baseline.testMetrics.macroF1}," +
                "context=${context.candidateId},contextMacroF1=${context.testMetrics.macroF1}," +
                "delta=${result.comparison.macroF1Delta},testRows=${baseline.testRowCount}"
        )
    }
}
