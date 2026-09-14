package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import kotlin.math.ln

object ClassificationEvaluator {

    private const val CALIBRATION_BIN_COUNT = 10

    fun evaluate(
        model: LocalClassifierModel,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        measureInference: Boolean = false
    ): ClassificationMetrics {
        require(indices.distinct().size == indices.size) {
            "Evaluation indices must be unique; duplicated rows would bias every reported metric."
        }
        require(indices.all { it in dataset.source.samples.indices }) {
            "Evaluation indices must refer to existing dataset rows."
        }
        require(dataset.normalizedFeatures.size == dataset.source.samples.size) {
            "Normalized feature rows must match the source dataset row count."
        }
        require(
            indices.all { sampleIndex ->
                dataset.normalizedFeatures[sampleIndex].size == model.inputFeatureCount &&
                    dataset.source.samples[sampleIndex].labelIndex in 0 until model.classCount
            }
        ) {
            "Evaluation rows must match the model feature and class contract."
        }
        val classCount = model.classCount
        val confusion = Array(classCount) { IntArray(classCount) }
        val probabilities = FloatArray(classCount)
        val hidden = FloatArray(model.hiddenUnitCount)
        var correct = 0
        var logLoss = 0.0
        var brierScore = 0.0
        val calibrationCounts = IntArray(CALIBRATION_BIN_COUNT)
        val calibrationCorrect = IntArray(CALIBRATION_BIN_COUNT)
        val calibrationConfidenceSums = DoubleArray(CALIBRATION_BIN_COUNT)
        val startedAt = if (measureInference) System.nanoTime() else 0L

        indices.forEach { sampleIndex ->
            val truth = dataset.source.samples[sampleIndex].labelIndex
            model.probabilitiesInto(dataset.normalizedFeatures[sampleIndex], probabilities, hidden)
            var predicted = 0
            for (classIndex in 1 until classCount) {
                if (probabilities[classIndex] > probabilities[predicted]) predicted = classIndex
            }
            confusion[truth][predicted]++
            if (truth == predicted) correct++
            logLoss -= ln(probabilities[truth].toDouble().coerceAtLeast(1e-12))
            probabilities.forEachIndexed { classIndex, probability ->
                val expected = if (classIndex == truth) 1.0 else 0.0
                val difference = probability.toDouble() - expected
                brierScore += difference * difference
            }
            val confidence = probabilities[predicted].toDouble().coerceIn(0.0, 1.0)
            val binIndex = (confidence * CALIBRATION_BIN_COUNT).toInt().coerceAtMost(CALIBRATION_BIN_COUNT - 1)
            calibrationCounts[binIndex]++
            calibrationConfidenceSums[binIndex] += confidence
            if (truth == predicted) calibrationCorrect[binIndex]++
        }

        val elapsed = if (measureInference) System.nanoTime() - startedAt else 0L
        val supportedClasses =
            (0 until classCount).filter { truth -> confusion[truth].sum() > 0 }
        val recalls = mutableListOf<Double>()
        val f1Values = mutableListOf<Double>()
        supportedClasses.forEach { classIndex ->
            val truePositive = confusion[classIndex][classIndex].toDouble()
            val falseNegative = confusion[classIndex].sum().toDouble() - truePositive
            val falsePositive = confusion.sumOf { row -> row[classIndex] }.toDouble() - truePositive
            val recall = truePositive / (truePositive + falseNegative).coerceAtLeast(1.0)
            val precision = truePositive / (truePositive + falsePositive).coerceAtLeast(1.0)
            val f1 =
                if (precision + recall > 0.0) {
                    2.0 * precision * recall / (precision + recall)
                } else {
                    0.0
                }
            recalls += recall
            f1Values += f1
        }

        val calibrationBins =
            buildList {
                calibrationCounts.forEachIndexed { binIndex, count ->
                    if (count > 0) add(
                        CalibrationBin(
                        lowerConfidence = binIndex.toDouble() / CALIBRATION_BIN_COUNT,
                        upperConfidence = (binIndex + 1).toDouble() / CALIBRATION_BIN_COUNT,
                        sampleCount = count,
                        meanConfidence = calibrationConfidenceSums[binIndex] / count,
                        empiricalAccuracy = calibrationCorrect[binIndex].toDouble() / count
                    )
                    )
                }
            }
        val expectedCalibrationError =
            if (indices.isEmpty()) {
                Double.NaN
            } else {
                calibrationBins.sumOf { bin ->
                    bin.sampleCount.toDouble() / indices.size *
                        kotlin.math.abs(bin.empiricalAccuracy - bin.meanConfidence)
                }
            }

        return ClassificationMetrics(
            sampleCount = indices.size,
            accuracy = if (indices.isNotEmpty()) correct.toDouble() / indices.size else 0.0,
            balancedAccuracy = recalls.averageOrZero(),
            macroF1 = f1Values.averageOrZero(),
            logLoss = if (indices.isNotEmpty()) logLoss / indices.size else Double.NaN,
            confusionMatrix = confusion.map(IntArray::toList),
            inferenceNanosPerSample =
                if (measureInference && indices.isNotEmpty()) {
                    elapsed.toDouble() / indices.size
                } else {
                    Double.NaN
                },
            brierScore = if (indices.isNotEmpty()) brierScore / indices.size else Double.NaN,
            expectedCalibrationError = expectedCalibrationError,
            calibrationBins = calibrationBins,
            classSupport = confusion.map { row -> row.sum() }
        )
    }

    fun evaluateScientificSlices(
        model: LocalClassifierModel,
        dataset: PreparedTrainingDataset,
        indices: IntArray
    ): List<ClassificationSliceMetrics> {
        require(indices.distinct().size == indices.size) {
            "Scientific slice indices must be unique."
        }
        require(indices.all { it in dataset.source.samples.indices }) {
            "Scientific slice indices must refer to existing dataset rows."
        }
        val definitions = linkedMapOf<String, Pair<String, (com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample) -> String>>()
        definitions["target-class"] = "Target class" to { it.targetClass }
        definitions["target-source"] = "Target source" to { it.targetSamplingStrategy }
        definitions["robot"] = "Robot" to { it.robotId }
        definitions["topology"] = "Topology" to { it.topologyKey }
        return buildList {
            definitions.forEach { (prefix, definition) ->
                val (displayPrefix, selector) = definition
                indices.groupBy { index -> selector(dataset.source.samples[index]) }
                    .filterKeys(String::isNotBlank)
                    .toSortedMap()
                    .forEach { (value, sliceIndices) ->
                        add(
                            ClassificationSliceMetrics(
                                id = "$prefix:$value",
                                displayName = "$displayPrefix: $value",
                                metrics = evaluate(model, dataset, sliceIndices.toIntArray())
                            )
                        )
                    }
            }
        }
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
}
