package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.percentile
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.ProfessionalBoxPlotChart
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.ProfessionalHistogramChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import kotlin.math.log10

@Composable
fun ErrorDistributionChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        EmptyChartDataCard(
            title = "🎯 Error Distribution",
            subtitle = "No sequential run data is available for error distribution charts."
        )

        return
    }

    val finalErrors =
        sequentialRuns
            .map {
                it.finalError
            }
            .filterFiniteNonNegative()

    val logFinalErrors =
        finalErrors
            .filter {
                it > 0.0
            }
            .map {
                log10(it)
            }
            .filter {
                it.isFinite()
            }

    val initialErrors =
        sequentialRuns
            .map {
                it.initialError
            }
            .filterFiniteNonNegative()

    val improvementAmounts =
        sequentialRuns
            .map {
                it.initialError - it.finalError
            }
            .filter {
                it.isFinite()
            }

    val improvementRatios =
        sequentialRuns
            .map {
                it.improvementRatio
            }
            .filter {
                it.isFinite()
            }

    val reachableRuns =
        sequentialRuns.filter {
            it.expectedClass.toString().contains(
                other = "REACHABLE",
                ignoreCase = true
            ) && !it.expectedClass.toString().contains(
                other = "UNREACHABLE",
                ignoreCase = true
            )
        }

    val unreachableRuns =
        sequentialRuns.filter {
            it.expectedClass.toString().contains(
                other = "UNREACHABLE",
                ignoreCase = true
            )
        }

    val reachableFinalErrors =
        reachableRuns
            .map {
                it.finalError
            }
            .filterFiniteNonNegative()

    val unreachableFinalErrors =
        unreachableRuns
            .map {
                it.finalError
            }
            .filterFiniteNonNegative()

    ChartSectionCard(
        title = "🎯 Error Distribution Summary",
        subtitle = "High-level residual error statistics for sequential runs."
    ) {
        ChartMetricRow(
            label = "Sequential runs",
            value = sequentialRuns.size.toString()
        )

        ChartMetricRow(
            label = "Average final error",
            value = "${formatChartDouble(finalErrors.averageSafe())} m"
        )

        ChartMetricRow(
            label = "Max final error",
            value = "${formatChartDouble(finalErrors.maxOrNull() ?: 0.0)} m"
        )

        ChartMetricRow(
            label = "Average initial error",
            value = "${formatChartDouble(initialErrors.averageSafe())} m"
        )

        ChartMetricRow(
            label = "Average improvement amount",
            value = "${formatChartDouble(improvementAmounts.averageSafe())} m"
        )

        ChartMetricRow(
            label = "Average improvement ratio",
            value = formatChartPercent(
                improvementRatios.averageSafe().coerceIn(0.0, 1.0)
            )
        )
    }

    HistogramChart(
        title = "🎯 Final Error Histogram",
        subtitle = "Real distribution of residual error.",
        values = finalErrors,
        valueSuffix = " m",
        color = Color(0xFF1565C0)
    )

    HistogramChart(
        title = "🎯 Log-Scale Final Error Histogram",
        subtitle = "Better for IK errors that span tiny to large residuals. X axis is log10(error).",
        values = logFinalErrors,
        valueSuffix = "",
        color = Color(0xFF6A1B9A)
    )

    HistogramChart(
        title = "🎯 Initial Error Histogram",
        subtitle = "Shows seed difficulty distribution.",
        values = initialErrors,
        valueSuffix = " m",
        color = Color(0xFF546E7A)
    )

    HistogramChart(
        title = "🎯 Improvement Amount Histogram",
        subtitle = "Shows how much error was reduced. Negative values mean the solver worsened.",
        values = improvementAmounts,
        valueSuffix = " m",
        color = Color(0xFF2E7D32)
    )

    HistogramChart(
        title = "🎯 Improvement Ratio Histogram",
        subtitle = "Shows whether the solver usually helps.",
        values = improvementRatios,
        valueSuffix = "",
        color = Color(0xFF00897B)
    )

    HistogramChart(
        title = "🎯 Reachable Final Error Histogram",
        subtitle = "Isolates false-reject severity on reachable targets.",
        values = reachableFinalErrors,
        valueSuffix = " m",
        color = Color(0xFFF9A825)
    )

    HistogramChart(
        title = "🎯 Unreachable Final Error Histogram",
        subtitle = "Shows distance from unreachable targets after solver attempts.",
        values = unreachableFinalErrors,
        valueSuffix = " m",
        color = Color(0xFFC62828)
    )

    BoxPlotChart(
        title = "🎯 Final Error Box Plot by Expected Class",
        subtitle = "Reachable vs unreachable residual spread.",
        groups = listOf(
            BoxPlotGroup(
                label = "Reachable",
                values = reachableFinalErrors,
                color = Color(0xFFF9A825)
            ),
            BoxPlotGroup(
                label = "Unreachable",
                values = unreachableFinalErrors,
                color = Color(0xFFC62828)
            )
        )
    )

    BoxPlotChart(
        title = "🎯 Final Error Box Plot by Link Count",
        subtitle = "Strong scaling diagnostic. Uses link-count aggregates.",
        groups =
            report.linkCountAggregates
                .sortedBy {
                    it.linkCount
                }
                .map { aggregate ->
                    BoxPlotGroup(
                        label = "${aggregate.linkCount}L",
                        values = listOf(
                            aggregate.averageFinalError,
                            aggregate.maxFinalError
                        ).filterFiniteNonNegative(),
                        color = Color(0xFF1565C0)
                    )
                }
    )

    CdfChart(
        title = "🎯 Final Error CDF",
        subtitle = "Shows what percent of runs solve below each residual error level.",
        values = finalErrors,
        xLabelSuffix = " m",
        color = Color(0xFF1565C0)
    )

    ThresholdCurveChart(
        title = "🎯 Threshold Curve",
        subtitle = "X = tolerance threshold, Y = percent of runs below threshold.",
        values = finalErrors,
        color = Color(0xFF2E7D32)
    )
}

@Composable
private fun HistogramChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    valueSuffix: String,
    color: Color
) {
    ProfessionalHistogramChart(
        title = title,
        subtitle = subtitle,
        values = values,
        xAxisLabel = "Error value$valueSuffix",
        yAxisLabel = "Frequency",
        color = color,
        logScale = false
    )
}

@Composable
private fun BoxPlotChart(
    title: String,
    subtitle: String,
    groups: List<BoxPlotGroup>
) {
    val cleanGroups =
        groups.map { group ->
            ChartBoxPlotItem(
                label = group.label,
                values = group.values.filterFiniteNonNegative(),
                color = group.color
            )
        }

    ProfessionalBoxPlotChart(
        title = title,
        subtitle = subtitle,
        items = cleanGroups,
        xAxisLabel = "Residual error distribution",
        yAxisLabel = "Target group"
    )
}

@Composable
private fun CdfChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    xLabelSuffix: String,
    color: Color
) {
    val sortedValues =
        values
            .filterFiniteNonNegative()
            .sorted()

    val points =
        sortedValues.mapIndexed { index, value ->
            ChartLinePoint(
                x = value,
                y =
                    (index + 1).toDouble() /
                            sortedValues.size.toDouble()
            )
        }

    ProfessionalLineChart(
        title = title,
        subtitle = subtitle,
        points = points,
        xAxisLabel = "Residual error$xLabelSuffix",
        yAxisLabel = "Cumulative probability",
        color = color
    )

    if (sortedValues.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 CDF Statistics",
            subtitle = "Percentile summary for cumulative residual distribution."
        ) {
            ChartLegendMetricRow(
                label = "P50",
                value =
                    "${formatChartDouble(percentile(sortedValues, 0.50))}$xLabelSuffix",
                color = color
            )

            ChartLegendMetricRow(
                label = "P90",
                value =
                    "${formatChartDouble(percentile(sortedValues, 0.90))}$xLabelSuffix",
                color = color
            )

            ChartLegendMetricRow(
                label = "P99",
                value =
                    "${formatChartDouble(percentile(sortedValues, 0.99))}$xLabelSuffix",
                color = color
            )
        }
    }
}

@Composable
private fun ThresholdCurveChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    color: Color
) {
    val cleanValues =
        values.filterFiniteNonNegative()

    if (cleanValues.isEmpty()) {
        ChartSectionCard(
            title = title,
            subtitle = subtitle
        ) {
            Text(
                text = "No threshold data available.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF555555)
            )
        }

        return
    }

    val thresholds =
        buildThresholds(cleanValues)

    val points =
        thresholds.map { threshold ->
            ChartLinePoint(
                x = threshold,
                y =
                    cleanValues.count {
                        it <= threshold
                    }.toDouble() / cleanValues.size.toDouble()
            )
        }

    ProfessionalLineChart(
        title = title,
        subtitle = subtitle,
        points = points,
        xAxisLabel = "Threshold tolerance (m)",
        yAxisLabel = "Acceptance ratio",
        color = color
    )

    ChartSectionCard(
        title = "📌 Threshold Statistics",
        subtitle = "Acceptance percentages below important tolerance levels."
    ) {
        ChartLegendMetricRow(
            label = "≤ 0.00001 m",
            value =
                formatChartPercent(
                    percentBelow(cleanValues, 0.00001)
                ),
            color = color
        )

        ChartLegendMetricRow(
            label = "≤ 0.001 m",
            value =
                formatChartPercent(
                    percentBelow(cleanValues, 0.001)
                ),
            color = color
        )

        ChartLegendMetricRow(
            label = "≤ 0.01 m",
            value =
                formatChartPercent(
                    percentBelow(cleanValues, 0.01)
                ),
            color = color
        )

        ChartLegendMetricRow(
            label = "≤ 0.1 m",
            value =
                formatChartPercent(
                    percentBelow(cleanValues, 0.1)
                ),
            color = color
        )
    }
}

private data class BoxPlotGroup(
    val label: String,
    val values: List<Double>,
    val color: Color
)

private data class BoxStats(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double
)

private fun List<Double>.filterFiniteNonNegative(): List<Double> {
    return filter {
        it.isFinite() && it >= 0.0
    }
}

private fun List<Double>.averageSafe(): Double {
    val cleanValues =
        filter {
            it.isFinite()
        }

    return if (cleanValues.isEmpty()) {
        0.0
    } else {
        cleanValues.average()
    }
}


private fun buildThresholds(
    values: List<Double>
): List<Double> {
    val maxValue =
        values.maxOrNull()?.coerceAtLeast(0.00001) ?: 0.00001

    val baseThresholds =
        listOf(
            0.00001,
            0.0001,
            0.001,
            0.01,
            0.1,
            1.0
        )

    val dynamicThresholds =
        (1..10).map { index ->
            maxValue * (index.toDouble() / 10.0)
        }

    return (baseThresholds + dynamicThresholds)
        .filter {
            it.isFinite() && it >= 0.0
        }
        .distinct()
        .sorted()
}

private fun percentBelow(
    values: List<Double>,
    threshold: Double
): Double {
    if (values.isEmpty()) {
        return 0.0
    }

    return values.count {
        it <= threshold
    }.toDouble() / values.size.toDouble()
}
