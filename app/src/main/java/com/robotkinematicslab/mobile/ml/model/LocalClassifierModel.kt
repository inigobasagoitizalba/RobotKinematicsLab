package com.robotkinematicslab.mobile.ml.model

import kotlin.math.exp

enum class TrainingModelKind(
    val displayName: String
) {
    AUTOMATIC("Automatic comparison"),
    LINEAR_SOFTMAX("Linear softmax"),
    COMPACT_MLP("Compact neural network")
}

data class LocalClassifierModel(
    val kind: TrainingModelKind,
    val inputFeatureCount: Int,
    val classCount: Int,
    val hiddenUnitCount: Int,
    val inputWeights: FloatArray,
    val hiddenBiases: FloatArray,
    val outputWeights: FloatArray,
    val outputBiases: FloatArray
) {
    init {
        require(kind != TrainingModelKind.AUTOMATIC)
        require(inputFeatureCount > 0)
        require(classCount >= 2)
        if (kind == TrainingModelKind.LINEAR_SOFTMAX) {
            require(hiddenUnitCount == 0)
            require(inputWeights.size == inputFeatureCount * classCount)
            require(hiddenBiases.isEmpty())
            require(outputWeights.isEmpty())
        } else {
            require(hiddenUnitCount > 0)
            require(inputWeights.size == inputFeatureCount * hiddenUnitCount)
            require(hiddenBiases.size == hiddenUnitCount)
            require(outputWeights.size == hiddenUnitCount * classCount)
        }
        require(outputBiases.size == classCount)
        require(inputWeights.all(Float::isFinite))
        require(hiddenBiases.all(Float::isFinite))
        require(outputWeights.all(Float::isFinite))
        require(outputBiases.all(Float::isFinite))
    }

    fun probabilities(
        features: FloatArray,
        output: FloatArray = FloatArray(classCount)
    ): FloatArray =
        probabilitiesInto(
            features = features,
            output = output,
            hidden = if (kind == TrainingModelKind.COMPACT_MLP) FloatArray(hiddenUnitCount) else EMPTY_BUFFER
        )

    internal fun logits(features: FloatArray): FloatArray = logitsInto(
        features, FloatArray(classCount),
        if (kind == TrainingModelKind.COMPACT_MLP) FloatArray(hiddenUnitCount) else EMPTY_BUFFER)

    internal fun probabilitiesInto(features: FloatArray, output: FloatArray, hidden: FloatArray): FloatArray {
        logitsInto(features, output, hidden)
        softmaxInPlace(output)
        return output
    }

    private fun logitsInto(features: FloatArray, output: FloatArray, hidden: FloatArray): FloatArray {
        require(features.size == inputFeatureCount)
        require(features.all(Float::isFinite)) { "Inference features must all be finite." }
        require(output.size == classCount)
        require(kind == TrainingModelKind.LINEAR_SOFTMAX || hidden.size == hiddenUnitCount)

        if (kind == TrainingModelKind.LINEAR_SOFTMAX) {
            for (classIndex in 0 until classCount) {
                var value = outputBiases[classIndex]
                val offset = classIndex * inputFeatureCount
                for (featureIndex in 0 until inputFeatureCount) {
                    value += inputWeights[offset + featureIndex] * features[featureIndex]
                }
                output[classIndex] = value
            }
        } else {
            for (hiddenIndex in 0 until hiddenUnitCount) {
                var value = hiddenBiases[hiddenIndex]
                val offset = hiddenIndex * inputFeatureCount
                for (featureIndex in 0 until inputFeatureCount) {
                    value += inputWeights[offset + featureIndex] * features[featureIndex]
                }
                hidden[hiddenIndex] = if (value > 0f) value else 0f
            }
            for (classIndex in 0 until classCount) {
                var value = outputBiases[classIndex]
                val offset = classIndex * hiddenUnitCount
                for (hiddenIndex in 0 until hiddenUnitCount) {
                    value += outputWeights[offset + hiddenIndex] * hidden[hiddenIndex]
                }
                output[classIndex] = value
            }
        }

        return output
    }

    fun predict(features: FloatArray): Int {
        val probabilities = probabilities(features)
        var best = 0
        for (index in 1 until probabilities.size) {
            if (probabilities[index] > probabilities[best]) best = index
        }
        return best
    }

    fun deepCopy(): LocalClassifierModel =
        copy(
            inputWeights = inputWeights.clone(),
            hiddenBiases = hiddenBiases.clone(),
            outputWeights = outputWeights.clone(),
            outputBiases = outputBiases.clone()
        )

    companion object {
        private val EMPTY_BUFFER = FloatArray(0)

        internal fun softmaxInPlace(values: FloatArray) {
            require(values.isNotEmpty()) { "Softmax requires at least one logit." }
            require(values.all(Float::isFinite)) { "Softmax logits must all be finite." }
            val maximum = values.maxOrNull() ?: 0f
            var sum = 0.0
            for (index in values.indices) {
                val exponential = exp((values[index] - maximum).toDouble())
                values[index] = exponential.toFloat()
                sum += exponential
            }
            require(sum.isFinite() && sum > 0.0) { "Softmax normalization must remain finite and positive." }
            val denominator = sum.toFloat()
            for (index in values.indices) {
                values[index] /= denominator
            }
            require(values.all(Float::isFinite)) { "Softmax probabilities must all be finite." }
        }
    }
}
