package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Opt-in, reproducible feature-selection audit for the exact compact MLP used by the APK.
 *
 * The benchmark removes complete, predeclared physical feature groups and retrains the model.
 * This is intentionally stronger evidence than ranking one fitted model's weights. Ordinary
 * unit-test runs skip it unless RKL_FEATURE_SELECTION_DATASET is supplied.
 */
class FeatureSelectionBenchmarkTest {

    @Test
    fun retrainExactAndroidModelWithContextFeatureAblations() {
        val datasetPath = System.getenv("RKL_FEATURE_SELECTION_DATASET").orEmpty()
        assumeTrue(
            "Set RKL_FEATURE_SELECTION_DATASET to run the feature-selection audit.",
            datasetPath.isNotBlank()
        )
        val sourceFile = File(datasetPath)
        assertTrue("Dataset does not exist: $datasetPath", sourceFile.isFile)

        val outputDirectory = File(
            System.getenv("RKL_FEATURE_SELECTION_OUTPUT")
                ?: "build/reports/feature-selection-android-mlp"
        ).apply { mkdirs() }
        val maximumRows = env("RKL_FEATURE_SELECTION_MAXIMUM_ROWS", "100000").toInt()
        val seeds = com.robotkinematicslab.mobile.audit.OptInCampaignInputs.seeds(env("RKL_FEATURE_SELECTION_SEEDS", "2604,2605,2606,2607,2608"))
        val workers = env(
            "RKL_FEATURE_SELECTION_WORKERS",
            (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 8).toString()
        ).toInt()

        val loaded = ScientificDatasetTrainingReader().load(
            file = sourceFile,
            profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
            maximumRows = maximumRows
        )
        val fullDataset = requireNotNull(loaded.dataset) {
            loaded.errorMessage ?: "Dataset could not be loaded."
        }
        val names = fullDataset.featureNames
        require(names.size == 130) { "Expected the frozen 130-feature contract; found ${names.size}." }

        val groups = contextGroups()
        val allProfiles = buildList {
            add(
                FeatureSubset(
                    id = "baseline_108",
                    description = "Baseline kinematics only",
                    retainedIndices = (0 until BASELINE_COUNT).toList(),
                    trainingProfile = TrainingFeatureProfile.BASELINE_KINEMATICS
                )
            )
            add(
                FeatureSubset(
                    id = "context_full_130",
                    description = "Complete context contract",
                    retainedIndices = names.indices.toList()
                )
            )
            groups.forEach { group ->
                val removed = group.featureNames.toSet()
                add(
                    FeatureSubset(
                        id = "without_${group.id}",
                        description = "Full context without ${group.description}",
                        retainedIndices = names.indices.filter { names[it] !in removed }
                    )
                )
            }
            add(
                removing(
                    id = "without_revolute_fraction",
                    description = "Remove the exact complement of prismatic_joint_fraction",
                    names = names,
                    removedNames = setOf("revolute_joint_fraction")
                )
            )
            add(
                removing(
                    id = "without_prismatic_fraction",
                    description = "Remove the exact complement of revolute_joint_fraction",
                    names = names,
                    removedNames = setOf("prismatic_joint_fraction")
                )
            )
            add(
                removing(
                    id = "without_availability_flags",
                    description = "Frozen-corpus audit without the three constant availability flags",
                    names = names,
                    removedNames = AVAILABILITY_FLAGS
                )
            )
            add(
                removing(
                    id = "frozen_redundancy_pruned",
                    description = "Frozen-corpus constants plus one exact joint-fraction complement",
                    names = names,
                    removedNames = AVAILABILITY_FLAGS + "revolute_joint_fraction"
                )
            )
            val constantNames = corpusConstantFeatureNames(fullDataset)
            add(
                FeatureSubset(
                    id = "corpus_nonconstant",
                    description = "Transductive audit only: features varying in this frozen corpus",
                    retainedIndices = names.indices.filter { names[it] !in constantNames }
                )
            )
        }
        val requestedProfileIds = System.getenv("RKL_FEATURE_SELECTION_PROFILES")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()
        val profiles = if (requestedProfileIds.isEmpty()) {
            allProfiles
        } else {
            allProfiles.filter { it.id in requestedProfileIds }.also { selected ->
                require(selected.map { it.id }.toSet() == requestedProfileIds) {
                    "Unknown feature-selection profile requested."
                }
            }
        }

        val runRows = mutableListOf<String>()
        val importanceRows = mutableListOf<String>()
        runRows += RUN_HEADER
        importanceRows += IMPORTANCE_HEADER

        seeds.forEach { seed ->
            profiles.forEach { profile ->
                System.gc()
                val subset = subset(fullDataset, profile)
                val prepared = TrainingDatasetPreparer().prepare(
                    dataset = subset,
                    splitSeed = seed,
                    splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT
                )
                val config = LocalTrainingConfig(
                    runName = "feature-selection-${profile.id}-$seed",
                    datasetPath = sourceFile.absolutePath,
                    compareFeatureProfiles = false,
                    singleFeatureProfile = profile.trainingProfile,
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
                    specification = CandidateSpecification(
                        id = "compact-mlp-32",
                        kind = TrainingModelKind.COMPACT_MLP,
                        hiddenUnits = 32
                    ),
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
                runRows += runRow(
                    seed = seed,
                    profile = profile,
                    metrics = metrics,
                    model = trained.model,
                    bestEpoch = trained.bestEpoch,
                    fitMillis = trained.durationMillis,
                    testRows = prepared.split.testIndices.size
                )
                File(outputDirectory, "runs.csv").writeText(runRows.joinToString("\n", postfix = "\n"))

                if (profile.id == "context_full_130") {
                    permutationImportance(
                        seed = seed,
                        model = trained.model,
                        prepared = prepared,
                        maximumSamples = 5_000
                    ).forEach { row ->
                        importanceRows += listOf(
                            row.seed,
                            csv(row.feature),
                            format(row.referenceMacroF1),
                            format(row.permutedMacroF1),
                            format(row.macroF1Drop)
                        ).joinToString(",")
                    }
                    File(outputDirectory, "validation_permutation_importance.csv").writeText(
                        importanceRows.joinToString("\n", postfix = "\n")
                    )
                }
                println(
                    "[feature-selection] seed=$seed profile=${profile.id} " +
                        "features=${profile.retainedIndices.size} macroF1=${format(metrics.macroF1)} " +
                        "logLoss=${format(metrics.logLoss)}"
                )
            }
        }

        val summary = summarize(runRows.drop(1))
        File(outputDirectory, "summary.csv").writeText(summary.joinToString("\n", postfix = "\n"))
        File(outputDirectory, "configuration.txt").writeText(
            buildString {
                appendLine("dataset=${sourceFile.absolutePath}")
                appendLine("maximum_rows=$maximumRows")
                appendLine("seeds=${seeds.joinToString(",")}")
                appendLine("split=ROBOT_HELD_OUT")
                appendLine("model=COMPACT_MLP")
                appendLine("hidden_units=32")
                appendLine("epochs=40")
                appendLine("batch_size=512")
                appendLine("workers=$workers")
                appendLine("corpus_constant_features=${corpusConstantFeatureNames(fullDataset).sorted().joinToString(",")}")
                groups.forEach { group ->
                    appendLine("group_${group.id}=${group.featureNames.joinToString(",")}")
                }
            }
        )
        println("[feature-selection] Results: ${outputDirectory.absolutePath}")
    }

    private fun subset(source: TrainingDataset, selection: FeatureSubset): TrainingDataset =
        TrainingDataset(
            sourcePath = source.sourcePath,
            profile = selection.trainingProfile,
            featureNames = selection.retainedIndices.map(source.featureNames::get),
            samples = source.samples.map { sample ->
                EncodedTrainingSample(
                    features = FloatArray(selection.retainedIndices.size) { outputIndex ->
                        sample.features[selection.retainedIndices[outputIndex]]
                    },
                    labelIndex = sample.labelIndex,
                    splitFingerprint = sample.splitFingerprint,
                    robotFingerprint = sample.robotFingerprint,
                    sourceRowIndex = sample.sourceRowIndex
                )
            },
            skippedRowCount = source.skippedRowCount,
            duplicateFingerprintCount = source.duplicateFingerprintCount
        )

    private fun removing(
        id: String,
        description: String,
        names: List<String>,
        removedNames: Set<String>
    ): FeatureSubset =
        FeatureSubset(
            id = id,
            description = description,
            retainedIndices = names.indices.filter { names[it] !in removedNames }
        )

    private fun corpusConstantFeatureNames(dataset: TrainingDataset): Set<String> {
        val minimums = FloatArray(dataset.featureNames.size) { Float.POSITIVE_INFINITY }
        val maximums = FloatArray(dataset.featureNames.size) { Float.NEGATIVE_INFINITY }
        dataset.samples.forEach { sample ->
            sample.features.indices.forEach { index ->
                minimums[index] = minOf(minimums[index], sample.features[index])
                maximums[index] = maxOf(maximums[index], sample.features[index])
            }
        }
        return dataset.featureNames.indices
            .filter { index -> maximums[index] - minimums[index] <= 1e-12f }
            .map(dataset.featureNames::get)
            .toSet()
    }

    private fun permutationImportance(
        seed: Int,
        model: LocalClassifierModel,
        prepared: com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset,
        maximumSamples: Int
    ): List<PermutationImportance> {
        val evaluationIndices = prepared.split.validationIndices.take(maximumSamples).toIntArray()
        val reference = ClassificationEvaluator.evaluate(model, prepared, evaluationIndices).macroF1
        return prepared.source.featureNames.indices.map { featureIndex ->
            val originals = FloatArray(evaluationIndices.size) { position ->
                prepared.normalizedFeatures[evaluationIndices[position]][featureIndex]
            }
            evaluationIndices.indices.forEach { position ->
                val sourcePosition = (position * 7 + 1) % evaluationIndices.size
                prepared.normalizedFeatures[evaluationIndices[position]][featureIndex] = originals[sourcePosition]
            }
            val permuted = ClassificationEvaluator.evaluate(model, prepared, evaluationIndices).macroF1
            evaluationIndices.indices.forEach { position ->
                prepared.normalizedFeatures[evaluationIndices[position]][featureIndex] = originals[position]
            }
            PermutationImportance(
                seed = seed,
                feature = prepared.source.featureNames[featureIndex],
                referenceMacroF1 = reference,
                permutedMacroF1 = permuted,
                macroF1Drop = reference - permuted
            )
        }
    }

    private fun runRow(
        seed: Int,
        profile: FeatureSubset,
        metrics: ClassificationMetrics,
        model: LocalClassifierModel,
        bestEpoch: Int,
        fitMillis: Long,
        testRows: Int
    ): String =
        listOf(
            seed,
            profile.id,
            csv(profile.description),
            profile.retainedIndices.size,
            parameterCount(model),
            bestEpoch,
            testRows,
            format(metrics.accuracy),
            format(metrics.balancedAccuracy),
            format(metrics.macroF1),
            format(metrics.logLoss),
            format(recall(metrics, TrainingLabel.ACCEPTED.ordinal)),
            format(recall(metrics, TrainingLabel.UNCERTAIN.ordinal)),
            format(recall(metrics, TrainingLabel.REJECTED.ordinal)),
            format(metrics.inferenceNanosPerSample),
            fitMillis
        ).joinToString(",")

    private fun summarize(rows: List<String>): List<String> {
        val parsed = rows.map { line -> line.split(',') }
        val header =
            "profile,feature_count,parameter_count,runs,runs_with_complete_class_coverage,macro_f1_mean,macro_f1_std," +
                "balanced_accuracy_mean,log_loss_mean,accepted_recall_mean," +
                "uncertain_recall_mean,rejected_recall_mean,inference_ns_mean,fit_ms_mean"
        val summaries = parsed.groupBy { it[1] }.values.map { group ->
            val macroF1 = group.map { it[9].toDouble() }
            listOf(
                group.first()[1],
                group.first()[3],
                group.first()[4],
                group.size,
                group.count { row -> row[11].toDouble().isFinite() && row[12].toDouble().isFinite() && row[13].toDouble().isFinite() },
                format(macroF1.average()),
                format(sampleStandardDeviation(macroF1)),
                format(group.map { it[8].toDouble() }.average()),
                format(group.map { it[10].toDouble() }.average()),
                format(group.map { it[11].toDouble() }.finiteAverage()),
                format(group.map { it[12].toDouble() }.finiteAverage()),
                format(group.map { it[13].toDouble() }.finiteAverage()),
                format(group.map { it[14].toDouble() }.average()),
                format(group.map { it[15].toDouble() }.average())
            ).joinToString(",")
        }
        return listOf(header) + summaries
    }

    private fun sampleStandardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { value -> (value - mean) * (value - mean) } / (values.size - 1))
    }

    private fun List<Double>.finiteAverage(): Double {
        val finite = filter(Double::isFinite)
        return if (finite.isEmpty()) Double.NaN else finite.average()
    }

    private fun parameterCount(model: LocalClassifierModel): Int =
        model.inputWeights.size + model.hiddenBiases.size + model.outputWeights.size + model.outputBiases.size

    private fun recall(metrics: ClassificationMetrics, classIndex: Int): Double {
        val row = metrics.confusionMatrix[classIndex]
        val count = row.sum()
        return if (count == 0) Double.NaN else row[classIndex].toDouble() / count
    }

    private fun contextGroups(): List<FeatureGroup> =
        listOf(
            FeatureGroup(
                "initial_error",
                "the initial Cartesian error",
                listOf("initial_cartesian_error", "initial_error_available")
            ),
            FeatureGroup(
                "seed_margin",
                "the seed-to-limit margin",
                listOf("seed_min_normalized_limit_margin", "seed_margin_available")
            ),
            FeatureGroup(
                "seed_condition",
                "the seed Jacobian conditioning",
                listOf("seed_log_condition_number", "seed_condition_available")
            ),
            FeatureGroup(
                "reach",
                "radius and conservative reach context",
                listOf(
                    "target_radius",
                    "conservative_reach_bound",
                    "target_reach_ratio",
                    "workspace_boundary_proximity"
                )
            ),
            FeatureGroup(
                "joint_mix",
                "prismatic and revolute fractions",
                listOf("prismatic_joint_fraction", "revolute_joint_fraction")
            ),
            FeatureGroup(
                "link_geometry",
                "link extent summaries",
                listOf("mean_link_extent", "link_extent_standard_deviation")
            ),
            FeatureGroup(
                "joint_span",
                "joint span summaries",
                listOf("mean_joint_span", "minimum_joint_span")
            ),
            FeatureGroup(
                "normalized_target",
                "reach-normalized target coordinates",
                listOf("normalized_target_x", "normalized_target_y", "normalized_target_z")
            ),
            FeatureGroup(
                "seed_home_offset",
                "normalized seed-to-home summaries",
                listOf(
                    "seed_home_offset_rms",
                    "seed_home_offset_mean_absolute",
                    "seed_home_offset_max_absolute"
                )
            )
        )

    private fun env(name: String, default: String): String = System.getenv(name) ?: default

    private fun format(value: Double): String = String.format(Locale.US, "%.12f", value)

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private data class FeatureSubset(
        val id: String,
        val description: String,
        val retainedIndices: List<Int>,
        val trainingProfile: TrainingFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED
    )

    private data class FeatureGroup(
        val id: String,
        val description: String,
        val featureNames: List<String>
    )

    private data class PermutationImportance(
        val seed: Int,
        val feature: String,
        val referenceMacroF1: Double,
        val permutedMacroF1: Double,
        val macroF1Drop: Double
    )

    companion object {
        private const val BASELINE_COUNT = 108
        private val AVAILABILITY_FLAGS =
            setOf(
                "initial_error_available",
                "seed_margin_available",
                "seed_condition_available"
            )
        private const val RUN_HEADER =
            "seed,profile,description,feature_count,parameter_count,best_epoch,test_rows," +
                "accuracy,balanced_accuracy,macro_f1,log_loss,accepted_recall," +
                "uncertain_recall,rejected_recall,inference_ns_per_sample,fit_ms"
        private const val IMPORTANCE_HEADER =
            "seed,feature,reference_macro_f1,permuted_macro_f1,macro_f1_drop"
    }
}
