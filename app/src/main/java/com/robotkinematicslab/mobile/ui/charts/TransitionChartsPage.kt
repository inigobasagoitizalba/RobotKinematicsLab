package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart

@Composable
fun TransitionChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>
) {
    val transitions =
        compactTransitionAggregates(report.transitionAggregates)

    if (transitions.isEmpty()) {
        EmptyChartDataCard(
            title = "🔁 Transition Matrix",
            subtitle = "No transition aggregate data is available for this diagnostic report."
        )

        return
    }

    val caseLabels = compactTransitionCaseLabels(transitions)

    val maxAverageIterations =
        transitions
            .maxOfOrNull {
                it.averageIterations
            }
            ?.coerceAtLeast(1.0)
            ?: 1.0

    TransitionHeatMapChart(
        title = "🔁 Transition success heat map",
        subtitle = "Rows = source role and columns = destination role. Scenario prefixes are combined using exact run-count weighting.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                val acceptanceRate =
                    safeRatio(
                        numerator = transition.acceptedCount,
                        denominator = transition.runCount
                    )

                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = acceptanceRate,
                    displayValue = formatChartPercent(acceptanceRate),
                    color = transitionSuccessHeatColor(acceptanceRate)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    TransitionHeatMapChart(
        title = "🔁 Transition final-error heat map",
        subtitle = "Shows hardest target transitions by residual final error.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = transition.averageFinalError,
                    displayValue = "${formatChartDouble(transition.averageFinalError)} m",
                    color = transitionErrorHeatColor(transition.averageFinalError)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    TransitionHeatMapChart(
        title = "🔁 Transition iteration heat map",
        subtitle = "Shows expensive transitions by average iteration count.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                val normalizedCost =
                    if (
                        transition.averageIterations.isFinite() &&
                        maxAverageIterations.isFinite() &&
                        maxAverageIterations > 0.0
                    ) {
                        (transition.averageIterations / maxAverageIterations)
                            .coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }

                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = transition.averageIterations,
                    displayValue = formatChartDouble(transition.averageIterations),
                    color = transitionIterationHeatColor(normalizedCost)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    TransitionHeatMapChart(
        title = "🔁 Transition far-failure heat map",
        subtitle = "Shows severe transition traps.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                val farRate =
                    safeRatio(
                        numerator = transition.farFailureCount,
                        denominator = transition.runCount
                    )

                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = farRate,
                    displayValue = formatChartPercent(farRate),
                    color = transitionFailureHeatColor(farRate)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    TransitionHeatMapChart(
        title = "🔁 Transition improvement-ratio heat map",
        subtitle = "Shows whether the solver moves in the right direction.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                val ratio =
                    transition.averageImprovementRatio.coerceIn(0.0, 1.0)

                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = ratio,
                    displayValue = formatChartPercent(ratio),
                    color = transitionImprovementHeatColor(ratio)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    TransitionHeatMapChart(
        title = "🔁 Transition count heat map",
        subtitle = "Shows sampling distribution across transition pairs.",
        rowLabels = caseLabels,
        columnLabels = caseLabels,
        cells =
            transitions.map { transition ->
                ChartHeatMapCell(
                    row = transition.fromCaseId ?: "START",
                    column = transition.toCaseId,
                    value = transition.runCount.toDouble(),
                    displayValue = transition.runCount.toString(),
                    color = transitionCountHeatColor(transition.runCount)
                )
            },
        xAxisLabel = "Destination case",
        yAxisLabel = "Source case"
    )

    HorizontalDoubleBarChart(
        title = "🔁 Top 10 Hardest Transitions",
        subtitle = "Ranked by average final error.",
        items =
            transitions
                .sortedByDescending {
                    it.averageFinalError
                }
                .take(10)
                .map { transition ->
                    ChartDoubleBarItem(
                        label = transitionLabel(transition.fromCaseId, transition.toCaseId),
                        value = transition.averageFinalError,
                        displayValue = "${formatChartDouble(transition.averageFinalError)} m",
                        color = transitionErrorHeatColor(transition.averageFinalError)
                    )
                }
    )

    HorizontalDoubleBarChart(
        title = "🔁 Top 10 Most Expensive Transitions",
        subtitle = "Ranked by average iterations.",
        items =
            transitions
                .sortedByDescending {
                    it.averageIterations
                }
                .take(10)
                .map { transition ->
                    ChartDoubleBarItem(
                        label = transitionLabel(transition.fromCaseId, transition.toCaseId),
                        value = transition.averageIterations,
                        displayValue = formatChartDouble(transition.averageIterations),
                        color =
                            transitionIterationHeatColor(
                                if (maxAverageIterations > 0.0) {
                                    transition.averageIterations / maxAverageIterations
                                } else {
                                    0.0
                                }
                            )
                    )
                }
    )

    GroupedRatioBarChart(
        title = "🔁 Top 10 Best Transitions",
        subtitle = "Ranked by strict acceptance rate.",
        items =
            transitions
                .sortedByDescending {
                    safeRatio(
                        numerator = it.acceptedCount,
                        denominator = it.runCount
                    )
                }
                .take(10)
                .map { transition ->
                    val acceptanceRate =
                        safeRatio(
                            numerator = transition.acceptedCount,
                            denominator = transition.runCount
                        )

                    ChartRatioItem(
                        label = transitionLabel(transition.fromCaseId, transition.toCaseId),
                        ratio = acceptanceRate,
                        displayValue = formatChartPercent(acceptanceRate),
                        color = transitionSuccessHeatColor(acceptanceRate)
                    )
                }
    )

    GroupedRatioBarChart(
        title = "🔁 START-to-Case Comparison",
        subtitle = "Shows which targets are hard from the initial state.",
        items =
            sequentialRuns
                .filter {
                    it.transitionFromCaseId == null
                }
                .groupBy {
                    it.transitionToCaseId
                }
                .toList()
                .sortedBy {
                    it.first ?: "UNKNOWN"
                }
                .map { entry ->
                    val toCase =
                        entry.first ?: "UNKNOWN"

                    val runs =
                        entry.second

                    val acceptanceRate =
                        safeRatio(
                            numerator = runs.count { it.solverAccepted },
                            denominator = runs.size
                        )

                    ChartRatioItem(
                        label = "START → $toCase",
                        ratio = acceptanceRate,
                        displayValue = formatChartPercent(acceptanceRate),
                        color = transitionSuccessHeatColor(acceptanceRate)
                    )
                }
    )

    GroupedRatioBarChart(
        title = "🔁 Same-Case Stability",
        subtitle = "R2→R2, R3→R3, etc. should usually be easy. Failures here are important.",
        items =
            transitions
                .filter {
                    it.fromCaseId == it.toCaseId
                }
                .sortedBy {
                    it.toCaseId
                }
                .map { transition ->
                    val acceptanceRate =
                        safeRatio(
                            numerator = transition.acceptedCount,
                            denominator = transition.runCount
                        )

                    ChartRatioItem(
                        label = transitionLabel(transition.fromCaseId, transition.toCaseId),
                        ratio = acceptanceRate,
                        displayValue = formatChartPercent(acceptanceRate),
                        color = transitionSuccessHeatColor(acceptanceRate)
                    )
                }
    )

    ChartSectionCard(
        title = "🔁 Transition Summary",
        subtitle = "Quick interpretation helpers."
    ) {
        val hardestTransition =
            transitions.maxByOrNull {
                it.averageFinalError
            }

        val mostSampledTransition =
            transitions.maxByOrNull {
                it.runCount
            }

        val mostExpensiveTransition =
            transitions.maxByOrNull {
                it.averageIterations
            }

        val mostSevereFarFailureTransition =
            transitions.maxByOrNull {
                safeRatio(
                    numerator = it.farFailureCount,
                    denominator = it.runCount
                )
            }

        ChartMetricRow(
            label = "Transition pairs",
            value = transitions.size.toString()
        )

        ChartMetricRow(
            label = "Hardest transition",
            value =
                if (hardestTransition != null) {
                    transitionLabel(
                        fromCaseId = hardestTransition.fromCaseId,
                        toCaseId = hardestTransition.toCaseId
                    )
                } else {
                    "NA"
                }
        )

        ChartMetricRow(
            label = "Most expensive transition",
            value =
                if (mostExpensiveTransition != null) {
                    transitionLabel(
                        fromCaseId = mostExpensiveTransition.fromCaseId,
                        toCaseId = mostExpensiveTransition.toCaseId
                    )
                } else {
                    "NA"
                }
        )

        ChartMetricRow(
            label = "Most sampled transition",
            value =
                if (mostSampledTransition != null) {
                    transitionLabel(
                        fromCaseId = mostSampledTransition.fromCaseId,
                        toCaseId = mostSampledTransition.toCaseId
                    )
                } else {
                    "NA"
                }
        )

        ChartMetricRow(
            label = "Highest far-failure transition",
            value =
                if (mostSevereFarFailureTransition != null) {
                    transitionLabel(
                        fromCaseId = mostSevereFarFailureTransition.fromCaseId,
                        toCaseId = mostSevereFarFailureTransition.toCaseId
                    )
                } else {
                    "NA"
                }
        )
    }
}

@Composable
private fun TransitionHeatMapChart(
    title: String,
    subtitle: String,
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String
) {
    ProfessionalHeatMapChart(
        title = title,
        subtitle = subtitle,
        rowLabels = rowLabels,
        columnLabels = columnLabels,
        cells = cells,
        xAxisLabel = xAxisLabel,
        yAxisLabel = yAxisLabel
    )
}

private fun transitionLabel(
    fromCaseId: String?,
    toCaseId: String?
): String {
    return "${fromCaseId ?: "START"} → ${toCaseId ?: "UNKNOWN"}"
}

private fun transitionSuccessHeatColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.50 ->
            Color(0xFFF9A825)

        ratio > 0.0 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun transitionErrorHeatColor(
    error: Double
): Color {
    return when {
        !error.isFinite() ->
            Color(0xFFC62828)

        error <= 0.01 ->
            Color(0xFF2E7D32)

        error <= 0.10 ->
            Color(0xFFF9A825)

        error <= 0.50 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun transitionIterationHeatColor(
    normalizedCost: Double
): Color {
    val ratio =
        normalizedCost.coerceIn(0.0, 1.0)

    return when {
        ratio <= 0.25 ->
            Color(0xFF2E7D32)

        ratio <= 0.50 ->
            Color(0xFFF9A825)

        ratio <= 0.75 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun transitionFailureHeatColor(
    ratio: Double
): Color {
    return when {
        ratio <= 0.0 ->
            Color(0xFF2E7D32)

        ratio < 0.25 ->
            Color(0xFFF9A825)

        ratio < 0.50 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun transitionImprovementHeatColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.50 ->
            Color(0xFFF9A825)

        ratio > 0.0 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun transitionCountHeatColor(
    count: Int
): Color {
    return when {
        count <= 0 ->
            Color(0xFFBDBDBD)

        count < 10 ->
            Color(0xFF90CAF9)

        count < 100 ->
            Color(0xFF42A5F5)

        else ->
            Color(0xFF1565C0)
    }
}
