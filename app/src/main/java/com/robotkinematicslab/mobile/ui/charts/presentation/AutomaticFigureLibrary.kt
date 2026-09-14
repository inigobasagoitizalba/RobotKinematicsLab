package com.robotkinematicslab.mobile.ui.charts.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class AutomaticFigureLibraryContext(
    val collection: String,
    val analysisId: String,
    val executionId: String
)

val LocalAutomaticFigureLibraryContext =
    staticCompositionLocalOf<AutomaticFigureLibraryContext?> { null }

@Composable
fun ProvideAutomaticFigureLibraryContext(
    collection: String,
    analysisId: String,
    executionId: String? = null,
    content: @Composable () -> Unit
) {
    val resolvedExecutionId =
        remember(collection, analysisId, executionId) {
            executionId ?: ChartFigureExporter.newAutomaticFigureExecutionId()
        }
    CompositionLocalProvider(
        LocalAutomaticFigureLibraryContext provides
            AutomaticFigureLibraryContext(
                collection = collection,
                analysisId = analysisId,
                executionId = resolvedExecutionId
            ),
        content = content
    )
}

internal fun inferAutomaticFigureCollection(title: String): String {
    val value = title.lowercase()
    return when {
        value.containsAny("dataset", "coverage", "ood", "out-of-distribution", "class balance") ->
            "dataset-quality"
        value.containsAny("workspace", "reachable region", "envelope", "workspace boundary") ->
            "workspace"
        value.containsAny("safety", "guard", "non-finite", "fault", "protection") ->
            "numerical-safety"
        value.containsAny(
            "training",
            "validation",
            "macro-f1",
            "confusion matrix",
            "class recall",
            "reliability curve",
            "calibration",
            "feature impact",
            "model comparison"
        ) -> "training"
        value.containsAny(
            "diagnostic",
            "solver",
            "residual",
            "iteration",
            "convergence",
            "failure",
            "topology",
            "seed",
            "latency"
        ) -> "diagnostics"
        else -> "other"
    }
}

private fun String.containsAny(vararg terms: String): Boolean = terms.any(::contains)
