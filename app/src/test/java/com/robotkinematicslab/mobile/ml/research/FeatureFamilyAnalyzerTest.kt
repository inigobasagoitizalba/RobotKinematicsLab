package com.robotkinematicslab.mobile.ml.research

import com.robotkinematicslab.mobile.ml.explainability.GlobalFeatureImportance
import com.robotkinematicslab.mobile.ml.explainability.FeatureAttribution
import com.robotkinematicslab.mobile.ml.explainability.LocalPredictionExplanation
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFamilyAnalyzerTest {

    @Test
    fun `feature importance is aggregated without changing total attribution`() {
        val raw =
            listOf(
                GlobalFeatureImportance("joint_1_seed_min_margin", 0.4, 0.2),
                GlobalFeatureImportance("joint_2_limit_pressure", 0.3, -0.1),
                GlobalFeatureImportance("jacobian_condition", 0.2, 0.2),
                GlobalFeatureImportance("target_x", 0.1, 0.1)
            )

        val families = FeatureFamilyAnalyzer.aggregate(raw)

        assertEquals(1.0, families.sumOf(FeatureFamilyImportance::totalAbsoluteImportance), 1e-12)
        assertEquals("Joint limits & pressure", families.first().familyId)
        assertEquals(2, families.first().featureCount)
    }

    @Test
    fun `ablation delta is positive only when family removal worsens score`() {
        val ablation = FeatureFamilyAnalyzer.ablation("Limits", 0.90, 0.84, 1_000, 800, 20.0, 18.0)

        assertEquals(0.06, ablation.scoreDelta, 1e-12)
        assertTrue(ablation.trainingTimeDeltaMillis > 0)
        assertTrue(ablation.inferenceTimeDeltaNanos > 0.0)
    }

    @Test
    fun `family coactivation is symmetric finite and normalized`() {
        val explanations =
            listOf(
                explanation(
                    FeatureAttribution("target_x", 0.4, 0.1),
                    FeatureAttribution("jacobian_condition", -0.2, 0.2)
                ),
                explanation(
                    FeatureAttribution("target_x", 0.2, 0.0),
                    FeatureAttribution("jacobian_condition", 0.3, -0.1)
                )
            )

        val cells = FeatureFamilyAnalyzer.coActivation(explanations)

        assertTrue(cells.isNotEmpty())
        assertTrue(cells.all { it.meanAbsoluteProduct.isFinite() && it.normalizedStrength in 0.0..1.0 })
        cells.forEach { cell ->
            val mirror = cells.single { it.rowFamily == cell.columnFamily && it.columnFamily == cell.rowFamily }
            assertEquals(cell.meanAbsoluteProduct, mirror.meanAbsoluteProduct, 1e-12)
        }
    }

    private fun explanation(vararg attribution: FeatureAttribution) =
        LocalPredictionExplanation(
            sourceRowIndex = 1,
            trueLabel = TrainingLabel.ACCEPTED,
            predictedLabel = TrainingLabel.ACCEPTED,
            contrastLabel = TrainingLabel.REJECTED,
            confidence = 0.8,
            baseMargin = 0.0,
            predictionMargin = 1.0,
            completenessError = 0.0,
            attributions = attribution.toList()
        )
}
