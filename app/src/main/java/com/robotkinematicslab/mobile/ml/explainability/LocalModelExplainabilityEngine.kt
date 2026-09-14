package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.project
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.data.ClassifierFeatureNormalizer
import com.robotkinematicslab.mobile.ml.storage.TrainingInferenceReplay
import com.robotkinematicslab.mobile.ml.storage.StoredLocalModel
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class LocalModelExplainabilityEngine(
    private val storage: TrainingStorageRepository,
    private val reader: ScientificDatasetTrainingReader = ScientificDatasetTrainingReader(),
    private val preparer: TrainingDatasetPreparer = TrainingDatasetPreparer()
) {
    fun explain(
        run: TrainingRunSummary,
        profile: TrainingFeatureProfile,
        maximumSamples: Int = 512,
        integratedGradientSteps: Int = 32,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ExplainabilityProgress) -> Unit = {}
    ): ExplainabilityResult = explain(run, run.modelPaths.firstOrNull { path ->
        runCatching { storage.loadModel(File(path)).profile == profile }.getOrDefault(false)
    } ?: error("This training run has no stored ${profile.displayName} model."), maximumSamples, integratedGradientSteps, cancellationRequested, onProgress)

    fun explain(
        run: TrainingRunSummary,
        modelPath: String,
        maximumSamples: Int = 512,
        integratedGradientSteps: Int = 32,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ExplainabilityProgress) -> Unit = {}
    ): ExplainabilityResult {
        require(maximumSamples in ExplainabilityPreflight.MIN_SAMPLES..ExplainabilityPreflight.MAX_SAMPLES)
        require(integratedGradientSteps in ExplainabilityPreflight.MIN_STEPS..ExplainabilityPreflight.MAX_STEPS)
        val started = System.currentTimeMillis()
        fun checkCancellation() {
            if (cancellationRequested() || Thread.currentThread().isInterrupted) throw ExplainabilityCancelledException()
        }
        onProgress(ExplainabilityProgress(ExplainabilityPhase.LOADING_MODEL, 0, maximumSamples + 4, "Loading the selected model and feature contract."))
        require(modelPath in run.modelPaths) { "The selected model does not belong to this training run." }
        val stored = storage.loadModel(File(modelPath))
        val profile = stored.profile
        require(stored.runId == run.runId)
        checkCancellation()

        onProgress(ExplainabilityProgress(ExplainabilityPhase.READING_DATASET, 1, maximumSamples + 4, "Validating and encoding the original scientific dataset."))
        val (dataset, split) = TrainingInferenceReplay.rebuild(run, stored, reader, cancellationRequested, ::checkCancellation)
        checkCancellation()
        onProgress(ExplainabilityProgress(ExplainabilityPhase.REBUILDING_SPLIT, 2, maximumSamples + 4, "Verified original rows, normalized inputs and test predictions."))
        require(split.testIndices.isNotEmpty()) { "The reconstructed test partition is empty." }
        val selectedIndices = evenlySample(split.testIndices, maximumSamples)
        val explanations = ArrayList<LocalPredictionExplanation>(selectedIndices.size)
        selectedIndices.forEachIndexed { position, sampleIndex ->
            checkCancellation()
            explanations += explainSample(stored, dataset.samples[sampleIndex], integratedGradientSteps)
            if (position == 0 || position % 8 == 0 || position == selectedIndices.lastIndex) {
                onProgress(
                    ExplainabilityProgress(
                        ExplainabilityPhase.EXPLAINING_SAMPLES,
                        position + 4,
                        selectedIndices.size + 4,
                        "Explaining held-out prediction ${position + 1} of ${selectedIndices.size}."
                    )
                )
            }
        }
        onProgress(ExplainabilityProgress(ExplainabilityPhase.AGGREGATING, selectedIndices.size + 3, selectedIndices.size + 4, "Aggregating local evidence into global feature importance."))
        val global = stored.featureNames.indices.map { featureIndex ->
            val values = explanations.map { it.attributions[featureIndex].attribution }
            GlobalFeatureImportance(
                featureName = stored.featureNames[featureIndex],
                meanAbsoluteAttribution = values.sumOf(::abs) / values.size,
                meanSignedAttribution = values.average()
            )
        }.sortedByDescending(GlobalFeatureImportance::meanAbsoluteAttribution)
        val errors = explanations.map(LocalPredictionExplanation::completenessError)
        val midpoint = (explanations.size / 2).coerceAtLeast(1)
        val firstHalfImportance = meanAbsoluteImportance(explanations.subList(0, midpoint), stored.featureNames.size)
        val secondHalfImportance = meanAbsoluteImportance(explanations.subList(if (midpoint == explanations.size) 0 else midpoint, explanations.size), stored.featureNames.size)
        val result = ExplainabilityResult(
            runId = run.runId,
            profile = profile,
            candidateId = stored.candidateId,
            explainedSampleCount = explanations.size,
            integratedGradientSteps = integratedGradientSteps,
            globalImportance = global,
            localExplanations = explanations,
            explainedSampleAccuracy = explanations.count { it.trueLabel == it.predictedLabel }.toDouble() / explanations.size,
            globalImportanceStability = cosineSimilarity(firstHalfImportance, secondHalfImportance),
            meanCompletenessError = errors.average(),
            maximumCompletenessError = errors.maxOrNull() ?: 0.0,
            durationMillis = System.currentTimeMillis() - started,
            featureSelectionName = "${stored.featureSelectionName} · ${stored.featureNames.size} variables"
        )
        onProgress(ExplainabilityProgress(ExplainabilityPhase.COMPLETED, selectedIndices.size + 4, selectedIndices.size + 4, "Explainability report is ready."))
        return result
    }

    internal fun explainNormalized(
        model: LocalClassifierModel,
        featureNames: List<String>,
        normalized: FloatArray,
        trueLabel: TrainingLabel,
        sourceRowIndex: Long,
        steps: Int
    ): LocalPredictionExplanation {
        val probabilities = model.probabilities(normalized.clone()).map(Float::toDouble)
        val predicted = probabilities.indices.maxByOrNull(probabilities::get) ?: 0
        val contrast = probabilities.indices.filter { it != predicted }.maxByOrNull(probabilities::get) ?: 0
        val base = FloatArray(normalized.size)
        val baseMargin = margin(model, base, predicted, contrast)
        val predictionMargin = margin(model, normalized, predicted, contrast)
        val sums = DoubleArray(normalized.size)
        for (step in 0 until steps) {
            val alpha = (step + 0.5) / steps
            val point = FloatArray(normalized.size) { normalized[it] * alpha.toFloat() }
            val gradient = marginGradient(model, point, predicted, contrast)
            for (featureIndex in sums.indices) sums[featureIndex] += gradient[featureIndex]
        }
        val attributions = featureNames.indices.map { featureIndex ->
            FeatureAttribution(
                featureName = featureNames[featureIndex],
                attribution = normalized[featureIndex] * sums[featureIndex] / steps,
                normalizedFeatureValue = normalized[featureIndex].toDouble()
            )
        }
        return LocalPredictionExplanation(
            sourceRowIndex = sourceRowIndex,
            trueLabel = trueLabel,
            predictedLabel = TrainingLabel.entries[predicted],
            contrastLabel = TrainingLabel.entries[contrast],
            confidence = probabilities[predicted],
            baseMargin = baseMargin,
            predictionMargin = predictionMargin,
            completenessError = abs((predictionMargin - baseMargin) - attributions.sumOf(FeatureAttribution::attribution)),
            attributions = attributions
        )
    }

    private fun explainSample(stored: StoredLocalModel, sample: EncodedTrainingSample, steps: Int): LocalPredictionExplanation {
        val normalized = ClassifierFeatureNormalizer.normalize(sample.features, stored.normalization)
        return explainNormalized(stored.model, stored.featureNames, normalized, TrainingLabel.entries[sample.labelIndex], sample.sourceRowIndex, steps)
    }

    private fun margin(model: LocalClassifierModel, input: FloatArray, predicted: Int, contrast: Int): Double {
        val logits = logits(model, input)
        return logits[predicted] - logits[contrast]
    }

    private fun logits(model: LocalClassifierModel, input: FloatArray): DoubleArray =
        model.logits(input).map(Float::toDouble).toDoubleArray()

    private fun marginGradient(model: LocalClassifierModel, input: FloatArray, predicted: Int, contrast: Int): DoubleArray {
        if (model.kind == TrainingModelKind.LINEAR_SOFTMAX) {
            return DoubleArray(input.size) { featureIndex ->
                model.inputWeights[predicted * input.size + featureIndex].toDouble() -
                    model.inputWeights[contrast * input.size + featureIndex].toDouble()
            }
        }
        val active = BooleanArray(model.hiddenUnitCount) { hiddenIndex ->
            var value = model.hiddenBiases[hiddenIndex]
            val offset = hiddenIndex * input.size
            for (featureIndex in input.indices) value += model.inputWeights[offset + featureIndex] * input[featureIndex]
            value > 0f
        }
        return DoubleArray(input.size) { featureIndex ->
            var gradient = 0.0
            for (hiddenIndex in 0 until model.hiddenUnitCount) {
                if (!active[hiddenIndex]) continue
                val outputDifference =
                    model.outputWeights[predicted * model.hiddenUnitCount + hiddenIndex] -
                        model.outputWeights[contrast * model.hiddenUnitCount + hiddenIndex]
                gradient += outputDifference * model.inputWeights[hiddenIndex * input.size + featureIndex]
            }
            gradient
        }
    }

    private fun evenlySample(indices: IntArray, maximum: Int): IntArray {
        if (indices.size <= maximum) return indices
        return IntArray(maximum) { position -> indices[(position.toLong() * indices.size / maximum).toInt()] }
    }

    private fun meanAbsoluteImportance(explanations: List<LocalPredictionExplanation>, featureCount: Int): DoubleArray {
        require(explanations.isNotEmpty())
        return DoubleArray(featureCount) { featureIndex ->
            explanations.sumOf { abs(it.attributions[featureIndex].attribution) } / explanations.size
        }
    }

    private fun cosineSimilarity(first: DoubleArray, second: DoubleArray): Double {
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        for (index in first.indices) {
            dot += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        val denominator = sqrt(firstNorm) * sqrt(secondNorm)
        return if (denominator > 0.0) (dot / denominator).coerceIn(0.0, 1.0) else 1.0
    }
}

class ExplainabilityCancelledException : RuntimeException("Explainability generation was cancelled.")
