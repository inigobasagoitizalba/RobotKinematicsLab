package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.io.File
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in end-to-end evidence campaign: exact APK generator and trainer, same robots/seeds per tolerance. */
class MultiToleranceFeatureComparisonCampaignTest {
    @Test
    fun compare108Against383AcrossRequestedTolerances() {
        assumeTrue(System.getenv("RKL_RUN_MULTI_TOLERANCE_CAMPAIGN") == "true")
        val output = File(System.getenv("RKL_MULTI_TOLERANCE_OUTPUT") ?: "build/reports/multi-tolerance-108-vs-383").apply { mkdirs() }
        val rowsPerRobot = env("RKL_MULTI_TOLERANCE_ROWS_PER_ROBOT", "1000").toInt()
        val epochs = env("RKL_MULTI_TOLERANCE_EPOCHS", "30").toInt()
        val workers = env("RKL_MULTI_TOLERANCE_WORKERS", "8").toInt()
        val trainingSeeds = env("RKL_MULTI_TOLERANCE_TRAINING_SEEDS", "2604,2605,2606").split(',').map(String::trim).map(String::toInt)
        val tolerances = env("RKL_MULTI_TOLERANCE_VALUES", "0.001,0.0001,0.000001").split(',').map(String::trim).map(String::toDouble)
        val robots = DatasetRobotPresets().buildDefaults().take(10)
        val selections = listOf(
            FeatureSelectionSpec.complete(TrainingFeatureProfile.BASELINE_KINEMATICS),
            FeatureSelectionSpec.complete(TrainingFeatureProfile.CONTEXT_EXPANDED)
        )
        val summary = mutableListOf("tolerance_m,training_seed,selection_id,feature_count,test_rows,accuracy,balanced_accuracy,macro_f1,log_loss,ece,inference_ns_per_sample,training_ms,dataset_generation_ms")

        var originalInputs: List<String>? = null
        tolerances.forEach { tolerance ->
            val dataset = File(output, "dataset-tolerance-${scientific(tolerance)}.csv")
            val generationStart = System.currentTimeMillis()
            val generation = ScientificDatasetGenerator().generate(
                config = DatasetGenerationConfig(
                    datasetName = "comparison-108-vs-383-${scientific(tolerance)}",
                    robots = robots,
                    samplesPerRobot = rowsPerRobot,
                    randomSeed = 2604,
                    targetMode = DatasetTargetMode.MIXED,
                    reachableFraction = 0.65,
                    filterMode = DatasetFilterMode.ALL,
                    append = false,
                    ikConfig = IKConfig(maxIterations = 800, tolerance = tolerance, damping = 0.01, maxStep = 0.02)
                ),
                csvFile = dataset,
                existingRowCount = 0L,
                generationIndex = 0
            )
            check(generation.completed) { generation.message }
            val pairedColumns = listOf("robotId", "sampleIndex", "targetClass", "targetSourceJointValues",
                "seedJointValues", "targetX", "targetY", "targetZ")
            val inputs = dataset.bufferedReader().use { csv ->
                val header = com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader.parseCsvLine(requireNotNull(csv.readLine()))
                val indices = pairedColumns.map(header::indexOf)
                require(indices.all { it >= 0 })
                csv.lineSequence().map { line ->
                    val values = com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader.parseCsvLine(line)
                    indices.joinToString("|") { values[it] }
                }.toList()
            }
            check(inputs.size == rowsPerRobot * robots.size)
            check(originalInputs == null || originalInputs == inputs) {
                "Tolerance comparisons must use identical targets and initial joint states."
            }
            originalInputs = inputs
            val generationMillis = System.currentTimeMillis() - generationStart
            trainingSeeds.forEach { seed ->
                val result = LocalTrainingEngine().train(
                    LocalTrainingConfig(
                        runName = "tolerance-${scientific(tolerance)}-seed-$seed",
                        datasetPath = dataset.absolutePath,
                        featureSelections = selections,
                        compareFeatureProfiles = false,
                        modelKind = TrainingModelKind.COMPACT_MLP,
                        resourceMode = TrainingResourceMode.MAXIMUM_ACCURACY,
                        splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
                        maximumRows = rowsPerRobot * robots.size,
                        epochs = epochs,
                        batchSize = 512,
                        hiddenUnits = 32,
                        randomSeed = seed,
                        earlyStoppingPatience = 8,
                        workerCount = workers
                    )
                )
                result.comparison.variants.forEach { variant ->
                    val metrics = variant.testMetrics
                    summary += listOf(
                        tolerance, seed, variant.featureSelectionId, variant.featureNames.size,
                        variant.testRowCount, metrics.accuracy, metrics.balancedAccuracy, metrics.macroF1,
                        metrics.logLoss, metrics.expectedCalibrationError, metrics.inferenceNanosPerSample,
                        variant.trainingDurationMillis, generationMillis
                    ).joinToString(",")
                }
                File(output, "summary.csv").writeText(summary.joinToString("\n", postfix = "\n"))
            }
        }
    }

    private fun env(name: String, fallback: String) = System.getenv(name) ?: fallback
    private fun scientific(value: Double) = String.format(Locale.US, "%.0e", value).replace('+', '_')
}
