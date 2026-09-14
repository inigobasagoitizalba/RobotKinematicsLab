package com.robotkinematicslab.mobile.ml.research

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.project
import com.robotkinematicslab.mobile.ml.storage.StoredLocalModel
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.storage.TrainingInferenceReplay
import com.robotkinematicslab.mobile.ml.data.ClassifierFeatureNormalizer
import java.io.File

data class ModelRobustnessProgress(
    val completed: Int,
    val total: Int,
    val message: String
)

data class StoredModelRobustnessResult(
    val modelName: String,
    val featureCount: Int,
    val heldOutSampleCount: Int,
    val perturbationUnit: String,
    val slices: List<RobustnessSlice>,
    val runId: String = "",
    val modelSha256: String = "",
    val corpusSha256: String = "",
    val sampleRowIds: List<Long> = emptyList()
)

/**
 * Tests numerical sensitivity in normalized feature space on the original untouched test split.
 * This is not presented as physical sensor noise: one unit is one training standard deviation.
 */
class StoredModelRobustnessEngine(
    private val repository: TrainingStorageRepository,
    private val reader: ScientificDatasetTrainingReader = ScientificDatasetTrainingReader(),
    private val preparer: TrainingDatasetPreparer = TrainingDatasetPreparer()
) {

    fun analyze(
        run: TrainingRunSummary,
        modelPath: String,
        magnitudes: List<Double> = listOf(0.0, 0.01, 0.05, 0.10, 0.25),
        maximumSamples: Int = 1_000,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ModelRobustnessProgress) -> Unit = {}
    ): StoredModelRobustnessResult {
        require(modelPath in run.modelPaths) { "The model does not belong to the selected run." }
        require(magnitudes.isNotEmpty() && magnitudes.distinct().size == magnitudes.size)
        require(magnitudes.all { it.isFinite() && it >= 0.0 })
        require(maximumSamples in 1..100_000 && magnitudes.size<=256)
        val originalModelHash = RobustnessEvidenceArchive.sha256(File(modelPath))
        val model = repository.loadModel(File(modelPath))
        require(model.runId == run.runId)
        val (dataset, split) = TrainingInferenceReplay.rebuild(run, model, reader, cancellationRequested)
        val testSamples = evenlySample(split.testIndices.map(dataset.samples::get), maximumSamples)
        require(testSamples.isNotEmpty()) { "The original test split is empty." }
        val observations = ArrayList<PerturbationObservation>(testSamples.size * magnitudes.size)
        val total = run.maximumRows + testSamples.size * magnitudes.size
        var completed = run.maximumRows
        magnitudes.sorted().forEach { magnitude ->
            testSamples.forEachIndexed { index, sample ->
                check(!cancellationRequested()) { "Robustness analysis cancelled." }
                val normalized = normalize(sample, model)
                val base = predict(normalized, sample.labelIndex, model)
                val perturbed = normalized.clone()
                if (magnitude > 0.0) {
                    perturbed.indices.forEach { featureIndex ->
                        perturbed[featureIndex] =
                            (perturbed[featureIndex] + magnitude.toFloat() * deterministicDirection(sample.sourceRowIndex, featureIndex))
                                .coerceIn(-8f, 8f)
                    }
                }
                val changed = predict(perturbed, sample.labelIndex, model)
                observations +=
                    PerturbationObservation(
                        sampleId = sample.sourceRowIndex,
                        magnitude = magnitude,
                        baselineLoss = 1.0 - base.truthProbability,
                        perturbedLoss = 1.0 - changed.truthProbability,
                        baselineCorrect = base.correct,
                        perturbedCorrect = changed.correct
                    )
                completed++
                if (index == 0 || index % 100 == 0 || index == testSamples.lastIndex) {
                    onProgress(
                        ModelRobustnessProgress(
                            completed = completed,
                            total = total,
                            message = "Testing ±${magnitude}σ deterministic feature perturbations."
                        )
                    )
                }
            }
        }
        require(RobustnessEvidenceArchive.sha256(File(modelPath))==originalModelHash) { "Model changed during robustness analysis." }
        return StoredModelRobustnessResult(
            modelName = model.featureSelectionName,
            featureCount = model.featureNames.size,
            heldOutSampleCount = testSamples.size,
            perturbationUnit = "training-standard-deviation σ",
            runId=run.runId, modelSha256=originalModelHash, corpusSha256=requireNotNull(model.inferenceContract).corpusSha256, sampleRowIds=testSamples.map { it.sourceRowIndex },
            slices = ScientificEvidenceCalculator.robustness(observations)
        )
    }

    private data class Prediction(val truthProbability: Double, val correct: Boolean)

    private fun normalize(sample: EncodedTrainingSample, model: StoredLocalModel): FloatArray =
        ClassifierFeatureNormalizer.normalize(sample.features, model.normalization)

    private fun predict(features: FloatArray, truthIndex: Int, model: StoredLocalModel): Prediction {
        val probabilities = model.model.probabilities(features)
        require(probabilities.all { it.isFinite() && it in 0f..1f })
        val predicted = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        return Prediction(probabilities[truthIndex].toDouble(), predicted == truthIndex)
    }

    private fun deterministicDirection(sampleId: Long, featureIndex: Int): Float {
        var value = sampleId xor (featureIndex.toLong() * -7046029254386353131L)
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return if ((value xor (value ushr 31)) and 1L == 0L) -1f else 1f
    }

    private fun <T> evenlySample(values: List<T>, maximum: Int): List<T> {
        if (values.size <= maximum) return values
        if (maximum == 1) return listOf(values.first())
        return (0 until maximum).map { index -> values[((values.size - 1L) * index / (maximum - 1L)).toInt()] }
    }
}
