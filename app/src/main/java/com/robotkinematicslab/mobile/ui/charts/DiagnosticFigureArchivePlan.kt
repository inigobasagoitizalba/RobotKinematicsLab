package com.robotkinematicslab.mobile.ui.charts

/**
 * One bounded rendering batch in the automatic diagnostic evidence archive.
 *
 * Diagnostic result pages are intentionally rendered one at a time after a run completes. This
 * keeps capture memory bounded while ensuring that archive coverage does not depend on which
 * analysis family the user opens in the interactive chart browser.
 */
internal sealed interface DiagnosticFigureArchiveBatch {
    val stableId: String

    data class AnalysisFamily(
        val category: DiagnosticChartCategory
    ) : DiagnosticFigureArchiveBatch {
        override val stableId: String = "analysis-${category.name.lowercase()}"
    }

    data object UniqueTwoDimensionalMatrices : DiagnosticFigureArchiveBatch {
        override val stableId: String = "matrix-2d-unique"
    }
}

/** Canonical inventory of distinct static matrices. Interactive 3D views are not figures here. */
internal enum class DiagnosticTwoDimensionalMatrixFigure(
    val stableId: String
) {
    ACCEPTED_REJECTED("accepted-rejected"),
    STATUS_EXPECTED_CLASS("status-expected-class"),
    LINK_COUNT_SEED_SUCCESS("link-count-seed-success"),
    LINK_COUNT_TOPOLOGY_SUCCESS("link-count-topology-success"),
    SEED_TOPOLOGY_SUCCESS("seed-topology-success"),
    FAILURE_CODE_LINK_COUNT("failure-code-link-count"),
    TRANSITION_SUCCESS("transition-success"),
    TRANSITION_FINAL_ERROR("transition-final-error"),
    TRANSITION_ITERATIONS("transition-iterations"),
    JOINT_NAME_CASE("joint-name-case"),
    JOINT_NAME_TRANSITION("joint-name-transition"),
    PROGRESS_CLASS_SEED_BUCKET("progress-class-seed-bucket"),
    CASE_METRIC("case-metric")
}

internal val diagnosticTwoDimensionalMatrixFigures: List<DiagnosticTwoDimensionalMatrixFigure> =
    DiagnosticTwoDimensionalMatrixFigure.entries

/**
 * Complete static diagnostic archive contract.
 *
 * HEAT_MAPS is a selector alias rather than an analysis family, so it is replaced by one explicit
 * two-dimensional matrix batch. The interactive 3D workspace is deliberately absent: it cannot be
 * represented faithfully as one static scientific figure and is the sole requested exclusion.
 */
internal val diagnosticFigureArchiveBatches: List<DiagnosticFigureArchiveBatch> =
    buildList {
        diagnosticAnalysisCategories.forEach { category ->
            add(DiagnosticFigureArchiveBatch.AnalysisFamily(category))
        }
        add(DiagnosticFigureArchiveBatch.UniqueTwoDimensionalMatrices)
    }

internal fun diagnosticArchiveContainsEveryAnalysisFamily(
    batches: List<DiagnosticFigureArchiveBatch> = diagnosticFigureArchiveBatches
): Boolean {
    val archivedCategories =
        batches.filterIsInstance<DiagnosticFigureArchiveBatch.AnalysisFamily>()
            .map { it.category }
            .toSet()
    return archivedCategories == diagnosticAnalysisCategories.toSet()
}

internal fun diagnosticArchiveContainsInteractive3D(
    batches: List<DiagnosticFigureArchiveBatch> = diagnosticFigureArchiveBatches
): Boolean =
    batches.any { batch ->
        batch.stableId.contains("3d", ignoreCase = true) ||
            batch.stableId.contains("workspace", ignoreCase = true)
    }
