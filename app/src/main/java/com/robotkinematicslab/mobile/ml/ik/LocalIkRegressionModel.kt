package com.robotkinematicslab.mobile.ml.ik

import kotlin.math.tanh

data class LocalIkRegressionModel(
    val inputFeatureCount: Int,
    val hiddenUnitCount: Int,
    val outputCount: Int = ONE_MICRON_MAX_JOINTS,
    val inputWeights: FloatArray,
    val hiddenBiases: FloatArray,
    val outputWeights: FloatArray,
    val outputBiases: FloatArray
) {
    init {
        require(inputFeatureCount > 0)
        require(hiddenUnitCount > 0)
        require(outputCount > 0)
        require(inputWeights.size == inputFeatureCount * hiddenUnitCount)
        require(hiddenBiases.size == hiddenUnitCount)
        require(outputWeights.size == hiddenUnitCount * outputCount)
        require(outputBiases.size == outputCount)
        require(inputWeights.all(Float::isFinite))
        require(hiddenBiases.all(Float::isFinite))
        require(outputWeights.all(Float::isFinite))
        require(outputBiases.all(Float::isFinite))
    }

    fun predict(
        normalizedFeatures: FloatArray,
        output: FloatArray = FloatArray(outputCount)
    ): FloatArray =
        predictInto(normalizedFeatures, output, FloatArray(hiddenUnitCount))

    internal fun predictInto(
        normalizedFeatures: FloatArray,
        output: FloatArray,
        hidden: FloatArray
    ): FloatArray {
        require(normalizedFeatures.size == inputFeatureCount)
        require(normalizedFeatures.all(Float::isFinite)) {
            "Inference features must all be finite."
        }
        require(output.size == outputCount)
        require(hidden.size == hiddenUnitCount)
        for (hiddenIndex in 0 until hiddenUnitCount) {
            var value = hiddenBiases[hiddenIndex]
            val offset = hiddenIndex * inputFeatureCount
            for (featureIndex in 0 until inputFeatureCount) {
                value += inputWeights[offset + featureIndex] * normalizedFeatures[featureIndex]
            }
            require(value.isFinite()) { "Hidden inference activation became non-finite." }
            hidden[hiddenIndex] = if (value > 0f) value else 0f
        }
        for (outputIndex in 0 until outputCount) {
            var value = outputBiases[outputIndex]
            val offset = outputIndex * hiddenUnitCount
            for (hiddenIndex in 0 until hiddenUnitCount) {
                value += outputWeights[offset + hiddenIndex] * hidden[hiddenIndex]
            }
            require(value.isFinite()) { "Output inference activation became non-finite." }
            output[outputIndex] = tanh(value.toDouble()).toFloat()
        }
        require(output.all(Float::isFinite)) { "Inference output must remain finite." }
        return output
    }

    fun deepCopy(): LocalIkRegressionModel =
        copy(
            inputWeights = inputWeights.clone(),
            hiddenBiases = hiddenBiases.clone(),
            outputWeights = outputWeights.clone(),
            outputBiases = outputBiases.clone()
        )

    val parameterCount: Int
        get() = inputWeights.size + hiddenBiases.size + outputWeights.size + outputBiases.size
}
