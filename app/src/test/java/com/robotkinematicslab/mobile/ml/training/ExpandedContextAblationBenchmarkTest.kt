package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in family ablation for the 383-input research profile using the APK's exact MLP. */
class ExpandedContextAblationBenchmarkTest {

    @Test
    fun ablatePredeclaredExpandedFeatureFamilies() {
        val datasetPath = System.getenv("RKL_EXPANDED_ABLATION_DATASET").orEmpty()
        assumeTrue("Set RKL_EXPANDED_ABLATION_DATASET to run this benchmark.", datasetPath.isNotBlank())
        val source = File(datasetPath)
        assertTrue(source.isFile)
        val output = File(
            System.getenv("RKL_EXPANDED_ABLATION_OUTPUT")
                ?: "build/reports/expanded-context-ablation"
        ).apply { mkdirs() }
        val seeds = com.robotkinematicslab.mobile.audit.OptInCampaignInputs.seeds(env("RKL_EXPANDED_ABLATION_SEEDS", "2604,2605,2606"))
        val maximumRows = env("RKL_EXPANDED_ABLATION_MAXIMUM_ROWS", "100000").toInt()
        val workers = env("RKL_EXPANDED_ABLATION_WORKERS", "8").toInt()
        val loaded = ScientificDatasetTrainingReader().load(
            file = source,
            profile = TrainingFeatureProfile.CONTEXT_EXPANDED,
            maximumRows = maximumRows
        )
        val complete = requireNotNull(loaded.dataset) { loaded.errorMessage ?: "Dataset load failed." }
        require(complete.featureNames.size == EXPANDED_COUNT)

        val families = listOf(
            Family("seed_task_geometry", SEED_TASK_START until JACOBIAN_START),
            Family("jacobian_spectrum", JACOBIAN_START until DLS_START),
            Family("directional_dls", DLS_START until LIMIT_START),
            Family("limit_pressure", LIMIT_START until CHAIN_START),
            Family("chain_geometry", CHAIN_START until PER_JOINT_START),
            Family("per_joint_transforms", PER_JOINT_START until EXPANDED_COUNT)
        )
        val subsets = buildList {
            add(Subset("context_frozen_130", (0 until CONTEXT_COUNT).toList(), TrainingFeatureProfile.CONTEXT_ENHANCED))
            add(Subset("expanded_full_383", complete.featureNames.indices.toList()))
            families.forEach { family ->
                add(
                    Subset(
                        id = "without_${family.id}",
                        retained = complete.featureNames.indices.filter { it !in family.indices }
                    )
                )
            }
        }

        val rows = mutableListOf(RUN_HEADER)
        seeds.forEach { seed ->
            subsets.forEach { selection ->
                val dataset = subset(complete, selection)
                val prepared = TrainingDatasetPreparer().prepare(
                    dataset = dataset,
                    splitSeed = seed,
                    splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT
                )
                val config = LocalTrainingConfig(
                    runName = "expanded-ablation-${selection.id}-$seed",
                    datasetPath = source.absolutePath,
                    compareFeatureProfiles = false,
                    singleFeatureProfile = selection.profile,
                    modelKind = TrainingModelKind.COMPACT_MLP,
                    resourceMode = TrainingResourceMode.MAXIMUM_ACCURACY,
                    splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
                    maximumRows = maximumRows,
                    epochs = 40,
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
                val metrics = ClassificationEvaluator.evaluate(
                    model = trained.model,
                    dataset = prepared,
                    indices = prepared.split.testIndices,
                    measureInference = true
                )
                rows += listOf(
                    seed,
                    selection.id,
                    selection.retained.size,
                    parameterCount(trained.model),
                    trained.bestEpoch,
                    format(metrics.accuracy),
                    format(metrics.balancedAccuracy),
                    format(metrics.macroF1),
                    format(metrics.logLoss),
                    format(recall(metrics, TrainingLabel.ACCEPTED.ordinal)),
                    format(recall(metrics, TrainingLabel.UNCERTAIN.ordinal)),
                    format(recall(metrics, TrainingLabel.REJECTED.ordinal)),
                    format(metrics.inferenceNanosPerSample),
                    trained.durationMillis
                ).joinToString(",")
                File(output, "runs.csv").writeText(rows.joinToString("\n", postfix = "\n"))
                println("[expanded-ablation] seed=$seed profile=${selection.id} f1=${format(metrics.macroF1)}")
            }
        }
        File(output, "families.csv").writeText(
            "family,start_inclusive,end_exclusive,count\n" +
                families.joinToString("\n", postfix = "\n") {
                    "${it.id},${it.indices.first},${it.indices.last + 1},${it.indices.count()}"
                }
        )
        File(output, "configuration.txt").writeText(
            "dataset=${source.absolutePath}\nseeds=${seeds.joinToString(",")}\nrows=$maximumRows\n" +
                "split=ROBOT_HELD_OUT\nmodel=COMPACT_MLP\nhidden_units=32\nepochs=40\nbatch=512\nworkers=$workers\n"
        )
    }

    private fun subset(source: TrainingDataset, selection: Subset): TrainingDataset =
        TrainingDataset(
            sourcePath = source.sourcePath,
            profile = selection.profile,
            featureNames = selection.retained.map(source.featureNames::get),
            samples = source.samples.map { sample ->
                EncodedTrainingSample(
                    features = FloatArray(selection.retained.size) { sample.features[selection.retained[it]] },
                    labelIndex = sample.labelIndex,
                    splitFingerprint = sample.splitFingerprint,
                    robotFingerprint = sample.robotFingerprint,
                    sourceRowIndex = sample.sourceRowIndex
                )
            },
            skippedRowCount = source.skippedRowCount,
            duplicateFingerprintCount = source.duplicateFingerprintCount
        )

    private fun parameterCount(model: com.robotkinematicslab.mobile.ml.model.LocalClassifierModel): Int =
        model.inputWeights.size + model.hiddenBiases.size + model.outputWeights.size + model.outputBiases.size

    private fun recall(metrics: ClassificationMetrics, index: Int): Double {
        val total = metrics.confusionMatrix[index].sum()
        return if (total == 0) Double.NaN else metrics.confusionMatrix[index][index].toDouble() / total
    }

    private fun env(name: String, default: String) = System.getenv(name) ?: default
    private fun format(value: Double) = String.format(Locale.US, "%.12f", value)

    private data class Subset(
        val id: String,
        val retained: List<Int>,
        val profile: TrainingFeatureProfile = TrainingFeatureProfile.CONTEXT_EXPANDED
    )

    private data class Family(val id: String, val indices: IntRange)

    companion object {
        private const val CONTEXT_COUNT = 130
        private const val SEED_TASK_START = 130
        private const val JACOBIAN_START = 152
        private const val DLS_START = 169
        private const val LIMIT_START = 191
        private const val CHAIN_START = 209
        private const val PER_JOINT_START = 233
        private const val EXPANDED_COUNT = 383
        private const val RUN_HEADER =
            "seed,profile,feature_count,parameter_count,best_epoch,accuracy,balanced_accuracy," +
                "macro_f1,log_loss,accepted_recall,uncertain_recall,rejected_recall," +
                "inference_ns_per_sample,fit_ms"
    }
}
