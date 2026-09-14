package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelExplainabilityEngineTest {
    private fun engine(): LocalModelExplainabilityEngine {
        val root = Files.createTempDirectory("xai-engine-test").toFile()
        return LocalModelExplainabilityEngine(TrainingStorageRepository(root.resolve("runs"), root.resolve("models")))
    }

    @Test
    fun signedAttributionsKeepContrastAndOriginalRowForDifferentPredictedClasses() {
        val model=LocalClassifierModel(TrainingModelKind.LINEAR_SOFTMAX,2,3,0,
            floatArrayOf(2f,-1f,0f,0f,-2f,1f),floatArrayOf(),floatArrayOf(),floatArrayOf(0f,0f,0f))
        for (direction in listOf(1f,-1f)) {
            val explanation=engine().explainNormalized(model,listOf("joint_2_seed","joint_4_dh_a"),floatArrayOf(direction,direction),TrainingLabel.ACCEPTED,197,32)
            assertEquals(197L,explanation.sourceRowIndex)
            assertEquals(if(direction>0) TrainingLabel.entries[0] else TrainingLabel.entries[2],explanation.predictedLabel)
            assertEquals(TrainingLabel.entries[1],explanation.contrastLabel)
            assertEquals(2.0,explanation.attributions[0].attribution,0.000001)
            assertEquals(-1.0,explanation.attributions[1].attribution,0.000001)
            assertEquals(1.0,explanation.predictionMargin-explanation.baseMargin,0.000001)
            assertTrue(explanation.completenessError<0.000001)
        }
    }

    @Test
    fun attributionMarginAndReluGatesUseTheActualFloatModelForwardPass() {
        val model = LocalClassifierModel(TrainingModelKind.COMPACT_MLP, 3, 3, 1,
            floatArrayOf(1e8f, 1f, -1e8f), floatArrayOf(0f), floatArrayOf(1f, 0f, -1f), floatArrayOf(0f, 0f, 0f))
        val normalized = floatArrayOf(1f, 1f, 1f)
        val probabilities = model.probabilities(normalized)
        assertEquals(probabilities[0], probabilities[1], 0f)
        val explanation = engine().explainNormalized(model, listOf("first", "second", "third"), normalized,
            TrainingLabel.ACCEPTED, 17, 8)
        // Float forward accumulation closes this gate: ((1e8 + 1) - 1e8) == 0.
        assertEquals(0.0, explanation.predictionMargin, 0.0)
        assertTrue(explanation.attributions.all { it.attribution == 0.0 })
        assertEquals(probabilities[0].toDouble(), explanation.confidence, 0.0)
    }

    @Test
    fun explanationUsesExactlyTheTrainingNormalizedVectorAndPrediction() {
        val model = LocalClassifierModel(TrainingModelKind.LINEAR_SOFTMAX, 1, 3, 0,
            floatArrayOf(1f, 0f, -1f), floatArrayOf(), floatArrayOf(), floatArrayOf(0f, 0f, 0f))
        val stored = com.robotkinematicslab.mobile.ml.storage.StoredLocalModel(
            "run", com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile.BASELINE_KINEMATICS,
            "linear", listOf("target_x"),
            com.robotkinematicslab.mobile.ml.data.FeatureNormalization(floatArrayOf(-3e38f), floatArrayOf(3e38f)), model)
        val sample = com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample(floatArrayOf(3e38f), 0, 1, 1, 7)
        val originalNormalized = floatArrayOf(((3e38f.toDouble() - (-3e38f).toDouble()) / 3e38f.toDouble()).toFloat())
        val originalPrediction = model.probabilities(originalNormalized)
        // Exercise the actual engine method, which also existed before the regression fix.
        val method = LocalModelExplainabilityEngine::class.java.getDeclaredMethod("explainSample",
            com.robotkinematicslab.mobile.ml.storage.StoredLocalModel::class.java,
            com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        val explanation = method.invoke(engine(), stored, sample, 8) as LocalPredictionExplanation
        assertEquals(originalNormalized.single().toDouble(), explanation.attributions.single().normalizedFeatureValue, 0.0)
        assertEquals(originalPrediction[0].toDouble(), explanation.confidence, 0.0)
        assertEquals(TrainingLabel.ACCEPTED, explanation.predictedLabel)
    }

    @Test
    fun linearAttributionsExactlyReconstructPredictionMargin() {
        val model = LocalClassifierModel(
            kind = TrainingModelKind.LINEAR_SOFTMAX,
            inputFeatureCount = 2,
            classCount = 3,
            hiddenUnitCount = 0,
            inputWeights = floatArrayOf(1f, 0f, 0f, 1f, -1f, -1f),
            hiddenBiases = floatArrayOf(),
            outputWeights = floatArrayOf(),
            outputBiases = floatArrayOf(0f, 0f, 0f)
        )
        val explanation = engine().explainNormalized(
            model, listOf("first", "second"), floatArrayOf(2f, 0.5f),
            TrainingLabel.ACCEPTED, sourceRowIndex = 7, steps = 8
        )

        assertEquals(TrainingLabel.ACCEPTED, explanation.predictedLabel)
        assertTrue(explanation.completenessError < 1e-9)
        assertEquals(
            explanation.predictionMargin - explanation.baseMargin,
            explanation.attributions.sumOf(FeatureAttribution::attribution),
            1e-9
        )
    }

    @Test
    fun neuralAttributionsAreFiniteDirectionalAndFaithful() {
        val model = LocalClassifierModel(
            kind = TrainingModelKind.COMPACT_MLP,
            inputFeatureCount = 2,
            classCount = 3,
            hiddenUnitCount = 2,
            inputWeights = floatArrayOf(1f, 0f, 0f, 1f),
            hiddenBiases = floatArrayOf(0.1f, 0.1f),
            outputWeights = floatArrayOf(1f, 1f, 0f, 0f, -1f, -1f),
            outputBiases = floatArrayOf(0f, 0f, 0f)
        )
        val explanation = engine().explainNormalized(
            model, listOf("joint_margin", "condition_number"), floatArrayOf(1.5f, 0.5f),
            TrainingLabel.ACCEPTED, sourceRowIndex = 9, steps = 32
        )

        assertEquals(TrainingLabel.ACCEPTED, explanation.predictedLabel)
        assertTrue(explanation.attributions.all { it.attribution.isFinite() })
        assertTrue(explanation.attributions.all { it.attribution > 0.0 })
        assertTrue(explanation.completenessError < 1e-5)
    }
}
