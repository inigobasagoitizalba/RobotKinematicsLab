package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDataContractTest {

    @Test
    fun datasetRejectsNonFiniteFeaturesAndUnknownLabels() {
        assertRejected {
            dataset(EncodedTrainingSample(floatArrayOf(Float.NaN), 0, 1L, 1L, 1L))
        }
        assertRejected {
            dataset(EncodedTrainingSample(floatArrayOf(1f), 99, 1L, 1L, 1L))
        }
    }

    @Test
    fun normalizationAndModelArtifactsRejectNonFiniteParameters() {
        assertRejected { FeatureNormalization(floatArrayOf(Float.NaN), floatArrayOf(1f)) }
        assertRejected { FeatureNormalization(floatArrayOf(0f), floatArrayOf(Float.POSITIVE_INFINITY)) }
        assertRejected { linearModel(floatArrayOf(Float.NaN, 0f, 0f)) }
    }

    @Test
    fun finiteModelProducesNormalizedThreeClassProbabilities() {
        val probabilities = linearModel(floatArrayOf(0f, 0f, 0f)).probabilities(floatArrayOf(2f))

        assertEquals(3, probabilities.size)
        assertTrue(probabilities.all(Float::isFinite))
        assertEquals(1.0, probabilities.sum().toDouble(), 1e-6)
    }

    @Test
    fun normalizationKeepsVeryLargeFiniteFeaturesNumericallyMeaningful() {
        val samples =
            (0 until 6).flatMap { group ->
                listOf(
                    EncodedTrainingSample(
                        features = floatArrayOf(3.0e38f),
                        labelIndex = 0,
                        splitFingerprint = group.toLong(),
                        robotFingerprint = group.toLong(),
                        sourceRowIndex = (group * 2).toLong()
                    ),
                    EncodedTrainingSample(
                        features = floatArrayOf(-3.0e38f),
                        labelIndex = 2,
                        splitFingerprint = group.toLong(),
                        robotFingerprint = group.toLong(),
                        sourceRowIndex = (group * 2 + 1).toLong()
                    )
                )
            }
        val prepared =
            TrainingDatasetPreparer().prepare(
                TrainingDataset(
                    sourcePath = "memory",
                    profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                    featureNames = listOf("large_feature"),
                    samples = samples,
                    skippedRowCount = 0,
                    duplicateFingerprintCount = 0
                ),
                splitSeed = 17
            )

        assertTrue(prepared.normalization.standardDeviations.single().isFinite())
        assertTrue(prepared.normalization.standardDeviations.single() > 1.0e30f)
        assertTrue(prepared.normalizedFeatures.all { row -> row.single().isFinite() })
        assertTrue(prepared.normalizedFeatures.all { row -> row.single() in -8f..8f })
    }

    @Test
    fun splitPublishesExactClassCoverageForEveryPartition() {
        val samples =
            (0 until 90).map { index ->
                EncodedTrainingSample(
                    features = floatArrayOf(index.toFloat()),
                    labelIndex = index % 3,
                    splitFingerprint = index.toLong(),
                    robotFingerprint = (index / 9).toLong(),
                    sourceRowIndex = index.toLong()
                )
            }
        val split = TrainingDatasetPreparer().split(samples, 2604)

        assertEquals(split.trainIndices.size, split.trainClassCounts.sum())
        assertEquals(split.validationIndices.size, split.validationClassCounts.sum())
        assertEquals(split.testIndices.size, split.testClassCounts.sum())
        split.testClassCounts.forEachIndexed { label, count ->
            assertEquals(count, split.testIndices.count { samples[it].labelIndex == label })
        }
    }

    @Test
    fun preparationFlagsAPerfectTargetSourceShortcut() {
        val samples =
            (0 until 120).map { index ->
                val label = index % 2
                EncodedTrainingSample(
                    features = floatArrayOf(index.toFloat()),
                    labelIndex = label,
                    splitFingerprint = index.toLong(),
                    robotFingerprint = (index / 12).toLong(),
                    sourceRowIndex = index.toLong(),
                    robotId = "r${index / 12}",
                    topologyKey = "REVOLUTE",
                    targetClass = if (label == 0) "REACHABLE" else "UNREACHABLE",
                    targetSamplingStrategy = if (label == 0) "FK" else "OUTSIDE_BOUND"
                )
            }
        val prepared = TrainingDatasetPreparer().prepare(
            TrainingDataset("memory", TrainingFeatureProfile.BASELINE_KINEMATICS, listOf("x"), samples, 0, 0),
            splitSeed = 2604
        )

        assertTrue(prepared.shortcutWarnings.any { it.startsWith("Target class") })
        assertTrue(prepared.shortcutWarnings.any { it.startsWith("Target sampling strategy") })
    }

    private fun dataset(sample: EncodedTrainingSample): TrainingDataset {
        return TrainingDataset(
            sourcePath = "memory",
            profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
            featureNames = listOf("x"),
            samples = listOf(sample),
            skippedRowCount = 0,
            duplicateFingerprintCount = 0
        )
    }

    private fun linearModel(weights: FloatArray): LocalClassifierModel {
        return LocalClassifierModel(
            kind = TrainingModelKind.LINEAR_SOFTMAX,
            inputFeatureCount = 1,
            classCount = 3,
            hiddenUnitCount = 0,
            inputWeights = weights,
            hiddenBiases = FloatArray(0),
            outputWeights = FloatArray(0),
            outputBiases = FloatArray(3)
        )
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
