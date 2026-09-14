package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun LinkCountChartsPage(
    report: Layer1DiagnosticReport
) {
    val aggregates =
        report.linkCountAggregates.sortedBy {
            it.linkCount
        }

    if (aggregates.isEmpty()) {
        EmptyChartDataCard(
            title = "🔗 Link Count Scaling",
            subtitle = "No link-count aggregate data is available for this diagnostic report."
        )

        return
    }

    GroupedRatioBarChart(
        title = "🔗 Success Rate by Link Count",
        subtitle = "Shows how strict IK reliability scales across robot link counts.",
        items =
            aggregates.map { aggregate ->
                ChartRatioItem(
                    label = "${aggregate.linkCount} links",
                    ratio = aggregate.strictAcceptanceRate,
                    displayValue = formatChartPercent(aggregate.strictAcceptanceRate),
                    color = linkSuccessColor(aggregate.strictAcceptanceRate)
                )
            }
    )

    GroupedRatioBarChart(
        title = "🔗 Close-or-Better Rate by Link Count",
        subtitle = "Shows whether higher-link robots fail hard or mostly miss strict tolerance.",
        items =
            aggregates.map { aggregate ->
                val closeOrBetterCount =
                    aggregate.strictAcceptedCount +
                            aggregate.nearSolvedCount +
                            aggregate.closeMissCount

                val closeOrBetterRate =
                    safeRatio(
                        numerator = closeOrBetterCount,
                        denominator = aggregate.runCount
                    )

                ChartRatioItem(
                    label = "${aggregate.linkCount} links",
                    ratio = closeOrBetterRate,
                    displayValue = formatChartPercent(closeOrBetterRate),
                    color = linkSuccessColor(closeOrBetterRate)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🔗 Average Final Error by Link Count",
        subtitle = "Shows average residual error as robot complexity changes.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "${aggregate.linkCount} links",
                    value = aggregate.averageFinalError,
                    displayValue = "${formatChartDouble(aggregate.averageFinalError)} m",
                    color = errorColor(aggregate.averageFinalError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🔗 Max Final Error by Link Count",
        subtitle = "Reveals worst-case instability for each link count.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "${aggregate.linkCount} links",
                    value = aggregate.maxFinalError,
                    displayValue = "${formatChartDouble(aggregate.maxFinalError)} m",
                    color = errorColor(aggregate.maxFinalError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🔗 Average Iterations by Link Count",
        subtitle = "Shows computational cost scaling.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "${aggregate.linkCount} links",
                    value = aggregate.averageIterations,
                    displayValue = formatChartDouble(aggregate.averageIterations),
                    color = Color(0xFF1565C0)
                )
            }
    )

    GroupedRatioBarChart(
        title = "🔗 Average Improvement Ratio by Link Count",
        subtitle = "Shows whether the solver still moves in the right direction.",
        items =
            aggregates.map { aggregate ->
                val ratio =
                    aggregate.averageImprovementRatio.coerceIn(0.0, 1.0)

                ChartRatioItem(
                    label = "${aggregate.linkCount} links",
                    ratio = ratio,
                    displayValue = formatChartPercent(ratio),
                    color = improvementColor(ratio)
                )
            },
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )

    HorizontalBarChart(
        title = "🔗 Far Failure Count by Link Count",
        items =
            aggregates.map { aggregate ->
                ChartBarItem(
                    label = "${aggregate.linkCount} links",
                    value = aggregate.farFailureCount,
                    color = Color(0xFFC62828)
                )
            }
    )

    aggregates.forEach { aggregate ->
        StackedBarChart(
            title = "🔗 Near / Close / Far — ${aggregate.linkCount} Links",
            totalLabel = "Best single view for difficulty at this link count.",
            slices = listOf(
                ChartSlice(
                    label = "Strict accepted",
                    value = aggregate.strictAcceptedCount,
                    color = Color(0xFF2E7D32)
                ),
                ChartSlice(
                    label = "Near solved",
                    value = aggregate.nearSolvedCount,
                    color = Color(0xFF8BC34A)
                ),
                ChartSlice(
                    label = "Close miss",
                    value = aggregate.closeMissCount,
                    color = Color(0xFFF9A825)
                ),
                ChartSlice(
                    label = "Far failure",
                    value = aggregate.farFailureCount,
                    color = Color(0xFFC62828)
                )
            )
        )
    }

    HorizontalBarChart(
        title = "🔗 Sample Volume by Link Count",
        items =
            aggregates.map { aggregate ->
                ChartBarItem(
                    label = "${aggregate.linkCount} links",
                    value = aggregate.runCount,
                    color = Color(0xFF1565C0)
                )
            },
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )
}

private fun linkSuccessColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.70 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun errorColor(
    error: Double
): Color {
    return when {
        !error.isFinite() ->
            Color(0xFFC62828)

        error <= 0.01 ->
            Color(0xFF2E7D32)

        error <= 0.10 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun improvementColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.50 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}
