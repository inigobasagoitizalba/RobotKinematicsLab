package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in nested feature-growth experiment. Every point is a strict prefix of the same 383-variable schema. */
class CumulativeFeatureGrowthBenchmarkTest {
    @Test
    fun trainEveryPredeclaredCumulativeFeatureBoundary() {
        val datasetPath = System.getenv("RKL_CUMULATIVE_FEATURE_DATASET").orEmpty()
        assumeTrue(datasetPath.isNotBlank())
        val source = File(datasetPath)
        require(source.isFile)
        val output = File(System.getenv("RKL_CUMULATIVE_FEATURE_OUTPUT") ?: "build/reports/cumulative-feature-growth").apply { mkdirs() }
        val maximumRows = env("RKL_CUMULATIVE_FEATURE_ROWS", "100000").toInt()
        val workers = env("RKL_CUMULATIVE_FEATURE_WORKERS", "8").toInt()
        val epochs = env("RKL_CUMULATIVE_FEATURE_EPOCHS", "40").toInt()
        val seeds = env("RKL_CUMULATIVE_FEATURE_SEEDS", "2604,2605,2606").split(',').map(String::trim).map(String::toInt)
        val boundaries = listOf(108, 130, 152, 169, 191, 209, 233, 383)
        val loaded = ScientificDatasetTrainingReader().load(source, TrainingFeatureProfile.CONTEXT_EXPANDED, maximumRows)
        val complete = requireNotNull(loaded.dataset) { loaded.errorMessage ?: "Dataset load failed." }
        require(complete.featureNames.size == 383)
        val lines = mutableListOf("seed,feature_count,accuracy,balanced_accuracy,macro_f1,log_loss,ece,inference_ns_per_sample,training_ms,best_epoch,test_rows")

        seeds.forEach { seed ->
            boundaries.forEach { featureCount ->
                val dataset = prefix(complete, featureCount)
                val prepared = TrainingDatasetPreparer().prepare(dataset, seed, TrainingSplitStrategy.ROBOT_HELD_OUT)
                val config = LocalTrainingConfig(
                    runName = "cumulative-$featureCount-$seed",
                    datasetPath = source.absolutePath,
                    compareFeatureProfiles = false,
                    singleFeatureProfile = dataset.profile,
                    modelKind = TrainingModelKind.COMPACT_MLP,
                    resourceMode = TrainingResourceMode.MAXIMUM_ACCURACY,
                    splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
                    maximumRows = maximumRows,
                    epochs = epochs,
                    batchSize = 512,
                    hiddenUnits = 32,
                    randomSeed = seed,
                    earlyStoppingPatience = 8,
                    workerCount = workers
                )
                val trained = LocalModelTrainer().train(
                    dataset = prepared,
                    specification = CandidateSpecification("compact-mlp-32", TrainingModelKind.COMPACT_MLP, 32),
                    config = config,
                    nextGlobalIteration = { 0 },
                    cancellationRequested = { false },
                    runtimeWorkerLimit = { workers },
                    onIteration = {}
                )
                val metrics = ClassificationEvaluator.evaluate(trained.model, prepared, prepared.split.testIndices, true)
                lines += listOf(
                    seed, featureCount, metrics.accuracy, metrics.balancedAccuracy, metrics.macroF1,
                    metrics.logLoss, metrics.expectedCalibrationError, metrics.inferenceNanosPerSample,
                    trained.durationMillis, trained.bestEpoch, prepared.split.testIndices.size
                ).joinToString(",")
                File(output, "runs.csv").writeText(lines.joinToString("\n", postfix = "\n"))
                println("[cumulative-growth] seed=$seed variables=$featureCount f1=${metrics.macroF1}")
            }
        }
    }

    private fun prefix(source: TrainingDataset, count: Int): TrainingDataset =
        TrainingDataset(
            sourcePath = source.sourcePath,
            profile = when (count) {
                108 -> TrainingFeatureProfile.BASELINE_KINEMATICS
                130 -> TrainingFeatureProfile.CONTEXT_ENHANCED
                else -> TrainingFeatureProfile.CONTEXT_EXPANDED
            },
            featureNames = source.featureNames.take(count),
            samples = source.samples.map { sample ->
                EncodedTrainingSample(
                    features = sample.features.copyOf(count),
                    labelIndex = sample.labelIndex,
                    splitFingerprint = sample.splitFingerprint,
                    robotFingerprint = sample.robotFingerprint,
                    sourceRowIndex = sample.sourceRowIndex
                )
            },
            skippedRowCount = source.skippedRowCount,
            duplicateFingerprintCount = source.duplicateFingerprintCount
        )

    private fun env(name: String, fallback: String) = System.getenv(name) ?: fallback
}
