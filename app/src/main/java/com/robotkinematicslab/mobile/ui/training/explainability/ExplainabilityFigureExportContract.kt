package com.robotkinematicslab.mobile.ui.training.explainability

import java.util.Locale

/**
 * Stable identifiers for the complete two-dimensional explainability evidence set.
 *
 * Keep this list explicit: it is the contract used by the UI and by regression tests to make
 * accidental loss of an explainability figure visible during development.
 */
internal enum class ExplainabilityFigureId(val title: String) {
    GLOBAL_FEATURE_IMPORTANCE("Global feature importance"),
    SCIENTIFIC_FAMILY_IMPORTANCE("Importance by scientific family"),
    FAMILY_CO_ACTIVATION("Attribution co-activation matrix"),
    FEATURE_IMPACT_DISTRIBUTION("Feature impact distribution"),
    LOCAL_CONTRIBUTION_WATERFALL("Local contribution waterfall")
}

internal data class ExplainabilityFigureExportSession(
    val collection: String,
    val analysisId: String,
    val executionId: String,
    val metadata: String
)

internal object ExplainabilityFigureExportContract {
    const val COLLECTION = "explainable-ai"

    val requiredFigures: Set<ExplainabilityFigureId> = ExplainabilityFigureId.entries.toSet()

    fun createSession(
        runId: String,
        candidateId: String,
        featureSelectionName: String,
        explainedSampleCount: Int,
        integratedGradientSteps: Int,
        completedAtEpochMillis: Long
    ): ExplainabilityFigureExportSession {
        require(runId.isNotBlank())
        require(candidateId.isNotBlank())
        require(explainedSampleCount > 0)
        require(integratedGradientSteps > 0)
        require(completedAtEpochMillis > 0L)
        val runToken = identifierToken(runId)
        val modelToken = identifierToken(candidateId)
        return ExplainabilityFigureExportSession(
            collection = COLLECTION,
            analysisId = "xai-$runToken-$modelToken",
            executionId = "completed-$completedAtEpochMillis",
            metadata =
                "Run $runId · model $candidateId · $featureSelectionName · " +
                    "held-out n=$explainedSampleCount · IG steps=$integratedGradientSteps · " +
                    "completed epoch ms=$completedAtEpochMillis · " +
                    com.robotkinematicslab.mobile.ml.explainability.ExplainabilityOutcomeScope.DESCRIPTION
        )
    }

    fun waterfallTitle(caseNumber: Int, sourceRowIndex: Long): String {
        require(caseNumber > 0)
        require(sourceRowIndex >= 0L)
        return "Why did the model make this prediction? · ${ExplainabilityFigureId.LOCAL_CONTRIBUTION_WATERFALL.title} · " +
            "case $caseNumber · source row $sourceRowIndex"
    }

    private fun identifierToken(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "unknown" }
}
