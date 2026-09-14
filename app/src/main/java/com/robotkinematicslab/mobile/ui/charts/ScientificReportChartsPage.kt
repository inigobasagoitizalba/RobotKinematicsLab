package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.ProfessionalBoxPlotChart
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.ProfessionalHistogramChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.sqrt
import com.robotkinematicslab.mobile.math.statistics.WilsonScoreInterval

@Composable
fun ScientificReportChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>
) {
    val finalErrors =
        sequentialRuns
            .map {
                it.finalError
            }
            .filterFiniteNonNegativeScientific()

    ChartSectionCard(
        title = "📑 Scientific Report Summary",
        subtitle = "Paper-style metrics useful for reports, comparisons, and reliability claims."
    ) {
        ChartMetricRow(
            label = "Sequential runs",
            value = sequentialRuns.size.toString()
        )

        ChartMetricRow(
            label = "Link-count groups",
            value = report.linkCountAggregates.size.toString()
        )

        ChartMetricRow(
            label = "Seed groups",
            value = report.seedAggregates.size.toString()
        )

        ChartMetricRow(
            label = "Strong reliability claim allowed",
            value = report.benchmarkPlan.strongReliabilityClaimAllowed.toString()
        )

        ChartMetricRow(
            label = "Verdict",
            value = report.finalVerdict.toString()
        )
    }

    ScientificLineChart(
        title = "📑 Reliability Curve",
        subtitle = "Success rate vs link count.",
        points =
            report.linkCountAggregates
                .sortedBy {
                    it.linkCount
                }
                .map {
                    ScientificPoint(
                        x = it.linkCount.toDouble(),
                        y = it.strictAcceptanceRate,
                        label = "${it.linkCount}L"
                    )
                },
        xLabel = "Link count",
        yLabel = "Strict acceptance rate",
        color = Color(0xFF1565C0),
        yAsPercent = true,
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )

    ScientificCdfChart(
        title = "📑 Error CDF",
        subtitle = "Shows percent of runs below each final-error threshold.",
        values = finalErrors,
        xLabelSuffix = " m",
        color = Color(0xFF1565C0)
    )

    ScientificCdfChart(
        title = "📑 Log Error CDF",
        subtitle = "CDF with log10(final error) on X. Best for IK precision spread.",
        values =
            finalErrors
                .filter {
                    it > 0.0
                }
                .map {
                    log10(it)
                }
                .filter {
                    it.isFinite()
                },
        xLabelSuffix = " log10(m)",
        color = Color(0xFF6A1B9A)
    )

    ScientificBoxPlotByLinkCount(
        report = report
    )

    ScientificViolinApproximationChart(
        title = "📑 Violin-Style Final Error Distribution",
        subtitle = "Approximate density using histogram bars. Wider/taller bins mean more runs in that error range.",
        values = finalErrors,
        color = Color(0xFF00897B)
    )

    ConfidenceIntervalSuccessChart(
        report = report
    )

    SeedVarianceChart(
        report = report
    )

    ParetoFailureChart(
        report = report
    )

    ScientificLineChart(
        title = "📑 Runtime / Performance Scaling",
        subtitle = "Estimated work by link count. Uses run count × average iterations.",
        points =
            report.linkCountAggregates
                .sortedBy {
                    it.linkCount
                }
                .map {
                    ScientificPoint(
                        x = it.linkCount.toDouble(),
                        y = it.runCount.toDouble() * it.averageIterations,
                        label = "${it.linkCount}L"
                    )
                },
        xLabel = "Link count",
        yLabel = "Estimated solver work",
        color = Color(0xFFEF6C00),
        yAsPercent = false
    )

    ChartSectionCard(
        title = "📑 Scientific Chart Notes",
        subtitle = "Current limitations to improve later."
    ) {
        ChartMetricRow(
            label = "Box plot by link count",
            value = "Uses aggregate avg/max approximation"
        )

        ChartMetricRow(
            label = "Confidence intervals",
            value = "Uses normal approximation"
        )

        ChartMetricRow(
            label = "Best future upgrade",
            value = "Store per-run linkCount, topologyMode, seed"
        )
    }
}

@Composable
private fun ScientificLineChart(
    title: String,
    subtitle: String,
    points: List<ScientificPoint>,
    xLabel: String,
    yLabel: String,
    color: Color,
    yAsPercent: Boolean,
    directionOverride: ChartReadingDirection? = null
) {
    val cleanPoints =
        points
            .filter {
                it.x.isFinite() &&
                        it.y.isFinite() &&
                        it.x >= 0.0 &&
                        it.y >= 0.0
            }
            .sortedBy {
                it.x
            }

    ProfessionalLineChart(
        title = title,
        subtitle = subtitle,
        points =
            cleanPoints.map { point ->
                ChartLinePoint(
                    x = point.x,
                    y = point.y
                )
            },
        xAxisLabel = xLabel,
        yAxisLabel = yLabel,
        color = color,
        directionOverride = directionOverride
    )

    if (cleanPoints.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 $title Statistics",
            subtitle = "Scientific line-chart numeric summary."
        ) {
            ChartMetricRow(
                label = "Min",
                value =
                    if (yAsPercent) {
                        formatChartPercent(cleanPoints.minOf { it.y })
                    } else {
                        formatChartDouble(cleanPoints.minOf { it.y })
                    }
            )

            ChartMetricRow(
                label = "Max",
                value =
                    if (yAsPercent) {
                        formatChartPercent(cleanPoints.maxOf { it.y })
                    } else {
                        formatChartDouble(cleanPoints.maxOf { it.y })
                    }
            )
        }
    }
}

@Composable
private fun ScientificCdfChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    xLabelSuffix: String,
    color: Color
) {
    val sortedValues =
        values
            .filter {
                it.isFinite()
            }
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
        xAxisLabel = "Value$xLabelSuffix",
        yAxisLabel = "Cumulative probability",
        color = color
    )

    if (sortedValues.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 $title Percentiles",
            subtitle = "Percentile summary for the CDF."
        ) {
            ChartMetricRow(
                label = "P50",
                value = "${formatChartDouble(scientificPercentile(sortedValues, 0.50))}$xLabelSuffix"
            )

            ChartMetricRow(
                label = "P90",
                value = "${formatChartDouble(scientificPercentile(sortedValues, 0.90))}$xLabelSuffix"
            )

            ChartMetricRow(
                label = "P99",
                value = "${formatChartDouble(scientificPercentile(sortedValues, 0.99))}$xLabelSuffix"
            )
        }
    }
}

@Composable
private fun ScientificBoxPlotByLinkCount(
    report: Layer1DiagnosticReport
) {
    val items =
        report.linkCountAggregates
            .sortedBy {
                it.linkCount
            }
            .map {
                ChartBoxPlotItem(
                    label = "${it.linkCount}L",
                    values =
                        listOf(
                            0.0,
                            it.averageFinalError,
                            it.maxFinalError
                        ).filterFiniteNonNegativeScientific(),
                    color = Color(0xFF1565C0)
                )
            }

    ProfessionalBoxPlotChart(
        title = "📑 Box Plot by Link Count",
        subtitle = "Shows distribution shape by link count. Current version uses aggregate approximation.",
        items = items,
        xAxisLabel = "Final error",
        yAxisLabel = "Link count"
    )
}

@Composable
private fun ScientificViolinApproximationChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    color: Color
) {
    ProfessionalHistogramChart(
        title = title,
        subtitle = subtitle,
        values = values,
        xAxisLabel = "Final error",
        yAxisLabel = "Approximate density",
        binCount = 16,
        color = color,
        logScale = false
    )
}

@Composable
private fun ConfidenceIntervalSuccessChart(
    report: Layer1DiagnosticReport
) {
    val seedItems =
        report.seedAggregates
            .sortedBy {
                it.seed
            }
            .map { seed ->
                val interval =
                    WilsonScoreInterval.at95Percent(
                        successes = seed.strictAcceptedCount,
                        trials = seed.runCount
                    )

                ScientificConfidenceItem(
                    label = "Seed ${seed.seed}",
                    rate = seed.strictAcceptanceRate,
                    lower = interval.lower,
                    upper = interval.upper
                )
            }

    ChartSectionCard(
        title = "📑 Confidence Interval Success Chart",
        subtitle = "Wilson 95% confidence interval for strict acceptance by seed."
    ) {
        if (seedItems.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No seed data available"
            )

            return@ChartSectionCard
        }

        seedItems.forEach { item ->
            ChartMetricRow(
                label = item.label,
                value = "${formatChartPercent(item.rate)} [${formatChartPercent(item.lower)}, ${formatChartPercent(item.upper)}]"
            )
        }
    }
}

@Composable
private fun SeedVarianceChart(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.seedComparisonSummary

    ChartSectionCard(
        title = "📑 Seed Variance Chart",
        subtitle = "Shows stability across random seeds."
    ) {
        ChartMetricRow(
            label = "Seed count",
            value = summary.seedCount.toString()
        )

        ChartMetricRow(
            label = "Best seed",
            value = summary.bestSeed?.toString() ?: "N/A"
        )

        ChartMetricRow(
            label = "Worst seed",
            value = summary.worstSeed?.toString() ?: "N/A"
        )

        ChartMetricRow(
            label = "Average strict acceptance",
            value = formatChartPercent(summary.averageStrictAcceptanceRate)
        )

        ChartMetricRow(
            label = "Spread",
            value = formatChartPercent(summary.strictAcceptanceRateSpread)
        )

        ChartMetricRow(
            label = "Variance",
            value = formatChartDouble(summary.strictAcceptanceRateVariance, digits = 6)
        )

        ChartMetricRow(
            label = "Seed sensitivity",
            value = summary.seedSensitivityLabel
        )
    }

    GroupedRatioBarChart(
        title = "📑 Seed Spread / Variance Bars",
        subtitle = "Lower is more stable.",
        items = listOf(
            ChartRatioItem(
                label = "Spread",
                ratio = summary.strictAcceptanceRateSpread.coerceIn(0.0, 1.0),
                displayValue = formatChartPercent(summary.strictAcceptanceRateSpread),
                color = scientificRiskColor(summary.strictAcceptanceRateSpread)
            ),
            ChartRatioItem(
                label = "Variance",
                ratio = summary.strictAcceptanceRateVariance.coerceIn(0.0, 1.0),
                displayValue = formatChartDouble(summary.strictAcceptanceRateVariance, digits = 6),
                color = scientificRiskColor(summary.strictAcceptanceRateVariance)
            )
        ),
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )
}

@Composable
private fun ParetoFailureChart(
    report: Layer1DiagnosticReport
) {
    val failureItems =
        report.detailCodeDistribution
            .filter {
                it.detailCode != "NONE"
            }
            .sortedByDescending {
                it.count
            }

    val total =
        failureItems
            .sumOf {
                it.count
            }
            .coerceAtLeast(1)

    var cumulative =
        0

    ChartSectionCard(
        title = "📑 Pareto Failure Chart",
        subtitle = "Sorted failure/detail codes with cumulative contribution."
    ) {
        if (failureItems.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No failure-code data available"
            )

            return@ChartSectionCard
        }

        failureItems.forEach { item ->
            cumulative += item.count

            val cumulativeRatio =
                cumulative.toDouble() / total.toDouble()

            ChartMetricRow(
                label = item.detailCode,
                value = "${item.count} / cumulative ${formatChartPercent(cumulativeRatio)}"
            )
        }
    }

    HorizontalBarChart(
        title = "📑 Failure Cause Counts",
        items =
            failureItems.map {
                ChartBarItem(
                    label = it.detailCode,
                    value = it.count,
                    color = Color(0xFFC62828)
                )
            }
    )
}

private data class ScientificPoint(
    val x: Double,
    val y: Double,
    val label: String
)

private data class ScientificConfidenceItem(
    val label: String,
    val rate: Double,
    val lower: Double,
    val upper: Double
)

private fun List<Double>.filterFiniteNonNegativeScientific(): List<Double> {
    return filter {
        it.isFinite() && it >= 0.0
    }
}

private fun scientificPercentile(
    sortedValues: List<Double>,
    percentile: Double
): Double {
    if (sortedValues.isEmpty()) {
        return 0.0
    }

    val clampedPercentile =
        percentile.coerceIn(0.0, 1.0)

    val rawIndex =
        clampedPercentile * (sortedValues.size - 1).toDouble()

    val lowerIndex =
        floor(rawIndex).toInt()

    val upperIndex =
        ceil(rawIndex).toInt()

    if (lowerIndex == upperIndex) {
        return sortedValues[lowerIndex]
    }

    val weight =
        rawIndex - lowerIndex.toDouble()

    return sortedValues[lowerIndex] * (1.0 - weight) +
            sortedValues[upperIndex] * weight
}

private fun scientificRiskColor(
    value: Double
): Color {
    return when {
        value < 0.05 ->
            Color(0xFF2E7D32)

        value < 0.15 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}
