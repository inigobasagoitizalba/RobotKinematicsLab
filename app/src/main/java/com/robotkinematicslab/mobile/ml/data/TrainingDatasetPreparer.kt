package com.robotkinematicslab.mobile.ml.data

import kotlin.math.sqrt

class TrainingDatasetPreparer {

    fun prepare(
        dataset: TrainingDataset,
        splitSeed: Int,
        splitStrategy: TrainingSplitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED
    ): PreparedTrainingDataset {
        val split = buildSplit(dataset.samples, splitSeed, splitStrategy)
        require(split.trainIndices.isNotEmpty()) { "Training split is empty." }
        require(split.validationIndices.isNotEmpty()) { "Validation split is empty." }
        require(split.testIndices.isNotEmpty()) { "Test split is empty." }

        val featureCount = dataset.featureNames.size
        val meanAccumulators = DoubleArray(featureCount)
        split.trainIndices.forEach { sampleIndex ->
            val features = dataset.samples[sampleIndex].features
            for (featureIndex in 0 until featureCount) {
                meanAccumulators[featureIndex] += features[featureIndex].toDouble()
            }
        }
        val means =
            FloatArray(featureCount) { featureIndex ->
                val mean = meanAccumulators[featureIndex] / split.trainIndices.size.toDouble()
                require(mean.isFinite() && mean in -Float.MAX_VALUE..Float.MAX_VALUE) {
                    "Feature $featureIndex has a non-representable training mean."
                }
                mean.toFloat()
            }

        val varianceAccumulators = DoubleArray(featureCount)
        split.trainIndices.forEach { sampleIndex ->
            val features = dataset.samples[sampleIndex].features
            for (featureIndex in 0 until featureCount) {
                val delta = features[featureIndex].toDouble() - means[featureIndex].toDouble()
                varianceAccumulators[featureIndex] += delta * delta
            }
        }
        val deviations = FloatArray(featureCount)
        for (featureIndex in 0 until featureCount) {
            val deviation =
                sqrt(varianceAccumulators[featureIndex] / split.trainIndices.size.toDouble())
            deviations[featureIndex] =
                if (deviation.isFinite() && deviation >= 1e-8 && deviation <= Float.MAX_VALUE) {
                    deviation.toFloat()
                } else {
                    1f
                }
        }

        val normalization = FeatureNormalization(means, deviations)
        val normalized =
            Array(dataset.samples.size) { sampleIndex ->
                ClassifierFeatureNormalizer.normalize(dataset.samples[sampleIndex].features, normalization)
            }

        val classCounts = IntArray(TrainingLabel.entries.size)
        split.trainIndices.forEach { sampleIndex ->
            classCounts[dataset.samples[sampleIndex].labelIndex]++
        }
        require(classCounts.count { it > 0 } >= 2) {
            "The training split must contain at least two outcome classes. " +
                "Counts were ${classCounts.joinToString()}."
        }
        val classWeights =
            FloatArray(classCounts.size) { classIndex ->
                if (classCounts[classIndex] == 0) {
                    0f
                } else {
                    split.trainIndices.size.toFloat() /
                        (classCounts.count { it > 0 }.toFloat() * classCounts[classIndex].toFloat())
                }
            }

        return PreparedTrainingDataset(
            source = dataset,
            split = split,
            normalizedFeatures = normalized,
            normalization = normalization,
            classWeights = classWeights,
            shortcutWarnings = splitWarnings(split) + detectShortcutWarnings(dataset.samples)
        )
    }

    /** Rebuilds the exact deterministic partition used during training without recomputing moments. */
    fun split(
        samples: List<EncodedTrainingSample>,
        splitSeed: Int,
        splitStrategy: TrainingSplitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED
    ): TrainingDatasetSplit = buildSplit(samples, splitSeed, splitStrategy)

    private fun buildSplit(
        samples: List<EncodedTrainingSample>,
        splitSeed: Int,
        splitStrategy: TrainingSplitStrategy
    ): TrainingDatasetSplit {
        val grouped =
            samples.indices.groupBy { sampleIndex ->
                when (splitStrategy) {
                    TrainingSplitStrategy.SAMPLE_GROUPED -> samples[sampleIndex].splitFingerprint
                    TrainingSplitStrategy.ROBOT_HELD_OUT -> samples[sampleIndex].robotFingerprint
                }
            }
        require(grouped.size >= 3) {
            "${splitStrategy.displayName} requires at least three independent split groups; found ${grouped.size}."
        }
        val train = mutableListOf<Int>()
        val validation = mutableListOf<Int>()
        val test = mutableListOf<Int>()

        grouped.forEach { (fingerprint, indices) ->
            val mixed = mixFingerprint(fingerprint xor splitSeed.toLong())
            val bucket = ((mixed ushr 1) % 10_000L).toInt()
            when {
                bucket < 7_000 -> train += indices
                bucket < 8_500 -> validation += indices
                else -> test += indices
            }
        }

        val usedSortedFallback = train.isEmpty() || validation.isEmpty() || test.isEmpty()
        if (usedSortedFallback) {
            train.clear()
            validation.clear()
            test.clear()
            grouped.entries.sortedBy { it.key }.forEachIndexed { groupIndex, entry ->
                when (groupIndex % 10) {
                    0 -> test += entry.value
                    1 -> validation += entry.value
                    else -> train += entry.value
                }
            }
        }

        return TrainingDatasetSplit(
            trainIndices = train.sorted().toIntArray(),
            validationIndices = validation.sorted().toIntArray(),
            testIndices = test.sorted().toIntArray(),
            duplicateFingerprintsKeptTogether = true,
            strategy = splitStrategy,
            trainClassCounts = classCounts(samples, train),
            validationClassCounts = classCounts(samples, validation),
            testClassCounts = classCounts(samples, test),
            usedSortedFallback = usedSortedFallback
        )
    }

    fun splitWarnings(split: TrainingDatasetSplit): List<String> = buildList {
        listOf("Training" to split.missingTrainingClasses, "Validation" to split.missingValidationClasses, "Test" to split.missingTestClasses).forEach { (name, missing) ->
            if (missing.isNotEmpty()) add("$name partition has no rows for ${missing.joinToString { it.csvValue }}. Metrics for absent classes are not measured; comparisons have limited class coverage.")
        }
        add(if(split.usedSortedFallback) "Allocation used the sorted-group fallback." else "Allocation used seeded hash buckets.")
        add("Actual split: ${split.trainIndices.size} train / ${split.validationIndices.size} validation / ${split.testIndices.size} test rows. Hash buckets target 70/15/15% of groups, not exact row proportions; if any partition is empty, sorted groups use a deterministic 8/1/1 cycle that does not depend on the seed.")
    }

    private fun classCounts(samples: List<EncodedTrainingSample>, indices: List<Int>): List<Int> {
        val counts = IntArray(TrainingLabel.entries.size)
        indices.forEach { index -> counts[samples[index].labelIndex]++ }
        return counts.toList()
    }

    private fun detectShortcutWarnings(samples: List<EncodedTrainingSample>): List<String> {
        val warnings = mutableListOf<String>()
        fun inspect(name: String, key: (EncodedTrainingSample) -> String) {
            samples.groupBy(key).filterKeys(String::isNotBlank).forEach { (value, group) ->
                if (group.size < 30) return@forEach
                val counts = group.groupingBy(EncodedTrainingSample::labelIndex).eachCount()
                val purity = counts.values.maxOrNull().orEmpty().toDouble() / group.size
                if (purity >= 0.95) {
                    warnings += "$name '$value' predicts one outcome for ${(purity * 100.0).toInt()}% of ${group.size} rows."
                }
            }
        }
        inspect("Target class", EncodedTrainingSample::targetClass)
        inspect("Target sampling strategy", EncodedTrainingSample::targetSamplingStrategy)
        inspect("Robot", EncodedTrainingSample::robotId)
        inspect("Topology", EncodedTrainingSample::topologyKey)
        return warnings.distinct()
    }

    private fun Int?.orEmpty(): Int = this ?: 0

    private fun mixFingerprint(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
