package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetSplit
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import kotlin.math.sqrt

class OneMicronIkDatasetPreparer {

    fun prepare(
        dataset: OneMicronIkDataset,
        splitSeed: Int,
        strategy: TrainingSplitStrategy
    ): PreparedOneMicronIkDataset {
        val split = split(dataset.samples, splitSeed, strategy)
        require(split.trainIndices.isNotEmpty() && split.validationIndices.isNotEmpty() && split.testIndices.isNotEmpty())
        val featureCount = dataset.featureNames.size
        val meansD = DoubleArray(featureCount)
        split.trainIndices.forEach { sampleIndex ->
            val features = dataset.samples[sampleIndex].features
            for (index in 0 until featureCount) meansD[index] += features[index]
        }
        val means = FloatArray(featureCount) { (meansD[it] / split.trainIndices.size).toFloat() }
        require(means.all(Float::isFinite)) {
            "Training feature means must remain finite and Float-representable."
        }
        val variances = DoubleArray(featureCount)
        split.trainIndices.forEach { sampleIndex ->
            val features = dataset.samples[sampleIndex].features
            for (index in 0 until featureCount) {
                val delta = features[index].toDouble() - means[index].toDouble()
                variances[index] += delta * delta
            }
        }
        val deviations =
            FloatArray(featureCount) { index ->
                sqrt(variances[index] / split.trainIndices.size.toDouble())
                    .takeIf { it.isFinite() && it in 1e-8..Float.MAX_VALUE.toDouble() }
                    ?.toFloat()
                    ?: 1f
            }
        val normalized =
            Array(dataset.samples.size) { sampleIndex ->
                FloatArray(featureCount) { featureIndex ->
                    ((dataset.samples[sampleIndex].features[featureIndex].toDouble() -
                        means[featureIndex].toDouble()) / deviations[featureIndex].toDouble())
                        .coerceIn(-8.0, 8.0)
                        .toFloat()
                }
            }
        return PreparedOneMicronIkDataset(dataset, split, FeatureNormalization(means, deviations), normalized)
    }

    fun normalize(features: FloatArray, normalization: FeatureNormalization): FloatArray {
        require(features.size == normalization.means.size)
        return FloatArray(features.size) { index ->
            ((features[index].toDouble() - normalization.means[index].toDouble()) /
                normalization.standardDeviations[index].toDouble())
                .coerceIn(-8.0, 8.0)
                .toFloat()
        }
    }

    private fun split(
        samples: List<OneMicronIkSample>,
        seed: Int,
        strategy: TrainingSplitStrategy
    ): TrainingDatasetSplit {
        val groups =
            samples.indices.groupBy { index ->
                when (strategy) {
                    TrainingSplitStrategy.SAMPLE_GROUPED -> samples[index].splitFingerprint
                    TrainingSplitStrategy.ROBOT_HELD_OUT -> samples[index].robotFingerprint
                }
            }
        require(groups.size >= 3) { "At least three independent groups are required for train, validation and test." }
        val train = mutableListOf<Int>()
        val validation = mutableListOf<Int>()
        val test = mutableListOf<Int>()
        groups.forEach { (fingerprint, indices) ->
            val bucket = ((mix(fingerprint xor seed.toLong()) ushr 1) % 10_000).toInt()
            when {
                bucket < 7_000 -> train += indices
                bucket < 8_500 -> validation += indices
                else -> test += indices
            }
        }
        if (train.isEmpty() || validation.isEmpty() || test.isEmpty()) {
            train.clear(); validation.clear(); test.clear()
            groups.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                when (index % 10) {
                    0 -> test += entry.value
                    1 -> validation += entry.value
                    else -> train += entry.value
                }
            }
        }
        return TrainingDatasetSplit(
            train.sorted().toIntArray(),
            validation.sorted().toIntArray(),
            test.sorted().toIntArray(),
            true,
            strategy
        )
    }

    private fun mix(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
