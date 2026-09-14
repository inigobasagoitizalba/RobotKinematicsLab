package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel

enum class ExplainabilityPhase {
    LOADING_MODEL,
    READING_DATASET,
    REBUILDING_SPLIT,
    EXPLAINING_SAMPLES,
    AGGREGATING,
    COMPLETED
}

data class ExplainabilityProgress(
    val phase: ExplainabilityPhase,
    val completedWork: Int,
    val totalWork: Int,
    val message: String
)

data class FeatureAttribution(
    val featureName: String,
    val attribution: Double,
    val normalizedFeatureValue: Double
)

data class GlobalFeatureImportance(
    val featureName: String,
    val meanAbsoluteAttribution: Double,
    val meanSignedAttribution: Double
)

data class LocalPredictionExplanation(
    val sourceRowIndex: Long,
    val trueLabel: TrainingLabel,
    val predictedLabel: TrainingLabel,
    val contrastLabel: TrainingLabel,
    val confidence: Double,
    val baseMargin: Double,
    val predictionMargin: Double,
    val completenessError: Double,
    val attributions: List<FeatureAttribution>
)

data class ExplainabilityResult(
    val runId: String,
    val profile: TrainingFeatureProfile,
    val candidateId: String,
    val explainedSampleCount: Int,
    val integratedGradientSteps: Int,
    val globalImportance: List<GlobalFeatureImportance>,
    val localExplanations: List<LocalPredictionExplanation>,
    val explainedSampleAccuracy: Double,
    val globalImportanceStability: Double,
    val meanCompletenessError: Double,
    val maximumCompletenessError: Double,
    val durationMillis: Long,
    val featureSelectionName: String = profile.displayName
)

/** This engine explains classifier logits, not the separate joint-regression or solver engines. */
object ExplainabilityOutcomeScope {
    const val ID = "classifier-logit-margin-only-v1"
    const val DESCRIPTION = "Classifier logit margin only. These attributions do not explain neural IK joint proposals, hybrid refinement, deterministic fallback or pure deterministic solver outcomes."
    fun canExplainIkRoute(route: com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath): Boolean = false
}

val ExplainabilityResult.outcomeScope: String get() = ExplainabilityOutcomeScope.ID
