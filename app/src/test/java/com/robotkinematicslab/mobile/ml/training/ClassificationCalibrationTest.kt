package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetSplit
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationCalibrationTest {

    @Test
    fun reportsProbabilityCalibrationWithoutChangingClassificationMetrics() {
        val dataset =
            TrainingDataset(
                sourcePath = "memory",
                profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                featureNames = listOf("x"),
                samples =
                    listOf(
                        sample(0),
                        sample(1),
                        sample(1),
                        sample(0)
                    ),
                skippedRowCount = 0,
                duplicateFingerprintCount = 0
            )
        val prepared =
            PreparedTrainingDataset(
                source = dataset,
                split =
                    TrainingDatasetSplit(
                        trainIndices = intArrayOf(),
                        validationIndices = intArrayOf(),
                        testIndices = intArrayOf(0, 1, 2, 3),
                        duplicateFingerprintsKeptTogether = true,
                        strategy = TrainingSplitStrategy.SAMPLE_GROUPED
                    ),
                normalizedFeatures = dataset.samples.map { it.features.clone() }.toTypedArray(),
                normalization = FeatureNormalization(floatArrayOf(0f), floatArrayOf(1f)),
                classWeights = floatArrayOf(1f, 1f)
            )
        val model =
            LocalClassifierModel(
                kind = TrainingModelKind.LINEAR_SOFTMAX,
                inputFeatureCount = 1,
                classCount = 2,
                hiddenUnitCount = 0,
                inputWeights = floatArrayOf(-0.6931472f, 0.6931472f),
                hiddenBiases = floatArrayOf(),
                outputWeights = floatArrayOf(),
                outputBiases = floatArrayOf(0f, 0f)
            )

        val metrics = ClassificationEvaluator.evaluate(model, prepared, intArrayOf(0, 1, 2, 3))

        assertEquals(1.0, metrics.accuracy, 0.0)
        assertEquals(0.08, metrics.brierScore, 1e-6)
        assertEquals(0.2, metrics.expectedCalibrationError, 1e-6)
        assertEquals(1, metrics.calibrationBins.size)
        assertTrue(metrics.calibrationBins.single().sampleCount == 4)
        assertEquals(listOf(2, 2), metrics.classSupport)
        assertTrue(metrics.hasCompleteClassCoverage)
    }

    @Test
    fun exposesMissingTruthClassesInsteadOfHidingUndefinedRecall() {
        val dataset =
            TrainingDataset(
                sourcePath = "memory",
                profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                featureNames = listOf("x"),
                samples = listOf(sample(0), sample(0)),
                skippedRowCount = 0,
                duplicateFingerprintCount = 0
            )
        val prepared =
            PreparedTrainingDataset(
                source = dataset,
                split = TrainingDatasetSplit(intArrayOf(), intArrayOf(), intArrayOf(0, 1), true, TrainingSplitStrategy.SAMPLE_GROUPED),
                normalizedFeatures = dataset.samples.map { it.features.clone() }.toTypedArray(),
                normalization = FeatureNormalization(floatArrayOf(0f), floatArrayOf(1f)),
                classWeights = floatArrayOf(1f, 1f)
            )
        val metrics = ClassificationEvaluator.evaluate(
            LocalClassifierModel(
                kind = TrainingModelKind.LINEAR_SOFTMAX,
                inputFeatureCount = 1,
                classCount = 2,
                hiddenUnitCount = 0,
                inputWeights = floatArrayOf(-1f, 1f),
                hiddenBiases = floatArrayOf(),
                outputWeights = floatArrayOf(),
                outputBiases = floatArrayOf(0f, 0f)
            ),
            prepared,
            intArrayOf(0, 1)
        )

        assertEquals(listOf(2, 0), metrics.classSupport)
        assertEquals(listOf(1), metrics.missingTruthClassIndices)
        assertTrue(!metrics.hasCompleteClassCoverage)
    }

    @Test
    fun scientificSlicesRemainSeparatedByTargetSource() {
        val samples =
            listOf(
                sample(0).copy(targetClass = "REACHABLE", targetSamplingStrategy = "FK", robotId = "r1", topologyKey = "R"),
                sample(1).copy(targetClass = "UNREACHABLE", targetSamplingStrategy = "OUTSIDE", robotId = "r2", topologyKey = "P")
            )
        val dataset = TrainingDataset("memory", TrainingFeatureProfile.BASELINE_KINEMATICS, listOf("x"), samples, 0, 0)
        val prepared = PreparedTrainingDataset(
            dataset,
            TrainingDatasetSplit(intArrayOf(), intArrayOf(), intArrayOf(0, 1), true, TrainingSplitStrategy.SAMPLE_GROUPED),
            samples.map { it.features.clone() }.toTypedArray(),
            FeatureNormalization(floatArrayOf(0f), floatArrayOf(1f)),
            floatArrayOf(1f, 1f)
        )
        val model = LocalClassifierModel(
            TrainingModelKind.LINEAR_SOFTMAX, 1, 2, 0,
            floatArrayOf(-0.6931472f, 0.6931472f), floatArrayOf(), floatArrayOf(), floatArrayOf(0f, 0f)
        )

        val slices = ClassificationEvaluator.evaluateScientificSlices(model, prepared, intArrayOf(0, 1))

        assertEquals(2, slices.count { it.id.startsWith("target-class:") })
        assertEquals(2, slices.count { it.id.startsWith("target-source:") })
        assertEquals(2, slices.count { it.id.startsWith("robot:") })
        assertEquals(2, slices.count { it.id.startsWith("topology:") })
    }

    private fun sample(label: Int) =
        EncodedTrainingSample(
            features = floatArrayOf(if (label == 0) -1f else 1f),
            labelIndex = label,
            splitFingerprint = label.toLong(),
            robotFingerprint = 1L,
            sourceRowIndex = label.toLong()
        )
}
