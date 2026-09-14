package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetSplit
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationEvaluatorBoundaryTest {

    @Test
    fun `one valid held out row produces one observation`() {
        val metrics = ClassificationEvaluator.evaluate(model(), prepared(), intArrayOf(0))

        assertEquals(1, metrics.sampleCount)
        assertEquals(1, metrics.confusionMatrix.sumOf { row -> row.sum() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate evaluation row cannot inflate scientific metrics`() {
        ClassificationEvaluator.evaluate(model(), prepared(), intArrayOf(0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range evaluation row is rejected at the boundary`() {
        ClassificationEvaluator.evaluate(model(), prepared(), intArrayOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalized row with a foreign feature schema is rejected`() {
        ClassificationEvaluator.evaluate(
            model(),
            prepared().copy(normalizedFeatures = arrayOf(floatArrayOf(0f, 1f))),
            intArrayOf(0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `scientific slicing rejects a foreign row before grouping`() {
        ClassificationEvaluator.evaluateScientificSlices(model(), prepared(), intArrayOf(1))
    }

    private fun model() =
        LocalClassifierModel(
            kind = TrainingModelKind.LINEAR_SOFTMAX,
            inputFeatureCount = 1,
            classCount = 2,
            hiddenUnitCount = 0,
            inputWeights = floatArrayOf(0f, 0f),
            hiddenBiases = floatArrayOf(),
            outputWeights = floatArrayOf(),
            outputBiases = floatArrayOf(0f, 0f)
        )

    private fun prepared(): PreparedTrainingDataset {
        val sample =
            EncodedTrainingSample(
                features = floatArrayOf(0f),
                labelIndex = 0,
                splitFingerprint = 1L,
                robotFingerprint = 1L,
                sourceRowIndex = 1L
            )
        val dataset =
            TrainingDataset(
                sourcePath = "memory",
                profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                featureNames = listOf("x"),
                samples = listOf(sample),
                skippedRowCount = 0,
                duplicateFingerprintCount = 0
            )
        return PreparedTrainingDataset(
            source = dataset,
            split = TrainingDatasetSplit(
                trainIndices = intArrayOf(),
                validationIndices = intArrayOf(),
                testIndices = intArrayOf(0),
                duplicateFingerprintsKeptTogether = true,
                strategy = TrainingSplitStrategy.SAMPLE_GROUPED
            ),
            normalizedFeatures = arrayOf(floatArrayOf(0f)),
            normalization = FeatureNormalization(floatArrayOf(0f), floatArrayOf(1f)),
            classWeights = floatArrayOf(1f, 1f)
        )
    }
}
