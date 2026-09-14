package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NumericalTrainingGuardTest {

    @Test
    fun adamRejectsEntireNonFiniteUpdateBeforeMutatingParameters() {
        val model =
            LocalClassifierModel(
                kind = TrainingModelKind.LINEAR_SOFTMAX,
                inputFeatureCount = 1,
                classCount = 2,
                hiddenUnitCount = 0,
                inputWeights = floatArrayOf(0.25f, -0.25f),
                hiddenBiases = FloatArray(0),
                outputWeights = FloatArray(0),
                outputBiases = FloatArray(2)
            )
        val before = model.inputWeights.clone()

        assertThrows(IllegalArgumentException::class.java) {
            AdamOptimizer(model).updateInputWeights(
                model.inputWeights,
                floatArrayOf(1f, Float.POSITIVE_INFINITY),
                0.001
            )
        }

        assertArrayEquals(before, model.inputWeights, 0f)
    }

    @Test
    fun classifierRejectsNonFiniteInferenceFeatureBeforeSoftmax() {
        val model =
            LocalClassifierModel(
                kind = TrainingModelKind.LINEAR_SOFTMAX,
                inputFeatureCount = 1,
                classCount = 2,
                hiddenUnitCount = 0,
                inputWeights = FloatArray(2),
                hiddenBiases = FloatArray(0),
                outputWeights = FloatArray(0),
                outputBiases = FloatArray(2)
            )

        assertThrows(IllegalArgumentException::class.java) {
            model.probabilities(floatArrayOf(Float.NaN))
        }
    }

    @Test
    fun multiArrayAdamUpdateIsTransactionalWhenLaterBlockWouldOverflow() {
        val model =
            LocalClassifierModel(
                kind = TrainingModelKind.LINEAR_SOFTMAX,
                inputFeatureCount = 1,
                classCount = 2,
                hiddenUnitCount = 0,
                inputWeights = floatArrayOf(0.25f, -0.25f),
                hiddenBiases = FloatArray(0),
                outputWeights = FloatArray(0),
                outputBiases = floatArrayOf(0.5f, -0.5f)
            )
        val beforeInput = model.inputWeights.clone()
        val beforeBias = model.outputBiases.clone()

        assertThrows(IllegalArgumentException::class.java) {
            AdamOptimizer(model).updateLinear(
                model = model,
                inputGradient = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE),
                outputBiasGradient = floatArrayOf(1f, -1f),
                learningRate = 1e39
            )
        }

        assertArrayEquals(beforeInput, model.inputWeights, 0f)
        assertArrayEquals(beforeBias, model.outputBiases, 0f)
    }
}
