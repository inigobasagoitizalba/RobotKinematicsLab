package com.robotkinematicslab.mobile.ml.research

import com.robotkinematicslab.mobile.ml.explainability.GlobalFeatureImportance
import com.robotkinematicslab.mobile.ml.explainability.LocalPredictionExplanation
import kotlin.math.abs

object FeatureFamilyAnalyzer {

    fun aggregate(importances: List<GlobalFeatureImportance>): List<FeatureFamilyImportance> =
        importances.groupBy { familyOf(it.featureName) }
            .map { (family, values) ->
                FeatureFamilyImportance(
                    familyId = family,
                    featureCount = values.size,
                    totalAbsoluteImportance = values.sumOf(GlobalFeatureImportance::meanAbsoluteAttribution),
                    meanAbsoluteImportance = values.map(GlobalFeatureImportance::meanAbsoluteAttribution).average(),
                    signedImportance = values.sumOf(GlobalFeatureImportance::meanSignedAttribution)
                )
            }
            .sortedByDescending(FeatureFamilyImportance::totalAbsoluteImportance)

    fun familyOf(featureName: String): String {
        val name = featureName.lowercase()
        return when {
            name.startsWith("joint_") && (
                "margin" in name || "barrier" in name || "pressure" in name || "extent" in name
            ) -> "Joint limits & pressure"
            name.startsWith("joint_") && ("sin" in name || "cos" in name || "theta" in name || "alpha" in name) ->
                "Joint angular geometry"
            name.startsWith("joint_") -> "Per-joint state"
            "jacob" in name || "sigma" in name || "condition" in name || "rank" in name || "manipul" in name ->
                "Jacobian & manipulability"
            "dls" in name || "damping" in name || "step" in name -> "Solver preview"
            "target" in name || name in setOf("x", "y", "z") -> "Target geometry"
            "reach" in name || "workspace" in name -> "Workspace geometry"
            "link" in name || "dh_" in name || name.startsWith("a_") || name.startsWith("d_") -> "Robot geometry"
            "seed" in name || "home" in name -> "Seed & home state"
            "tolerance" in name || "iteration" in name -> "Solver configuration"
            else -> "Core kinematics"
        }
    }

    fun ablation(
        familyId: String,
        fullModelScore: Double,
        ablatedModelScore: Double,
        fullTrainingMillis: Long,
        ablatedTrainingMillis: Long,
        fullInferenceNanos: Double,
        ablatedInferenceNanos: Double
    ): FeatureFamilyAblation {
        require(familyId.isNotBlank())
        require(fullModelScore.isFinite() && fullModelScore in 0.0..1.0)
        require(ablatedModelScore.isFinite() && ablatedModelScore in 0.0..1.0)
        require(fullTrainingMillis >= 0L && ablatedTrainingMillis >= 0L)
        require(fullInferenceNanos.isFinite() && fullInferenceNanos >= 0.0)
        require(ablatedInferenceNanos.isFinite() && ablatedInferenceNanos >= 0.0)
        return FeatureFamilyAblation(
            familyId = familyId,
            fullModelScore = fullModelScore,
            ablatedModelScore = ablatedModelScore,
            scoreDelta = fullModelScore - ablatedModelScore,
            trainingTimeDeltaMillis = fullTrainingMillis - ablatedTrainingMillis,
            inferenceTimeDeltaNanos = fullInferenceNanos - ablatedInferenceNanos
        )
    }

    /**
     * Descriptive attribution co-activation, not a causal SHAP-interaction value.
     * Families are strong together when their absolute local attributions co-occur.
     */
    fun coActivation(
        explanations: List<LocalPredictionExplanation>,
        maximumFamilies: Int = 8
    ): List<FeatureFamilyCoActivation> {
        require(maximumFamilies > 0)
        if (explanations.isEmpty()) return emptyList()
        val bySample = explanations.map { explanation ->
            explanation.attributions.groupBy { familyOf(it.featureName) }
                .mapValues { (_, values) -> values.sumOf { abs(it.attribution) } }
        }
        val families = bySample.flatMap { it.keys }
            .distinct()
            .associateWith { family -> bySample.sumOf { sample -> sample[family] ?: 0.0 } }
            .entries
            .sortedByDescending(Map.Entry<String, Double>::value)
            .take(maximumFamilies)
            .map(Map.Entry<String, Double>::key)
        val raw = buildList {
            families.forEach { row ->
                families.forEach { column ->
                    val mean = bySample.map { sample -> (sample[row] ?: 0.0) * (sample[column] ?: 0.0) }.average()
                    add(Triple(row, column, mean))
                }
            }
        }
        val maximum = (raw.maxOfOrNull { it.third } ?: 0.0).coerceAtLeast(1e-12)
        return raw.map { (row, column, value) ->
            FeatureFamilyCoActivation(
                rowFamily = row,
                columnFamily = column,
                meanAbsoluteProduct = value,
                normalizedStrength = (value / maximum).coerceIn(0.0, 1.0),
                sampleCount = explanations.size
            )
        }
    }
}
