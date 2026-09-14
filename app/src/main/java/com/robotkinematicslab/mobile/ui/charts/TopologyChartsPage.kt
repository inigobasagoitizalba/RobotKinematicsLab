package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun TopologyChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>
) {
    val aggregates =
        report.topologyAggregates

    if (aggregates.isEmpty()) {
        EmptyChartDataCard(
            title = "🦾 Topology / Joint Mode",
            subtitle = "No topology aggregate data is available for this diagnostic report."
        )

        return
    }

    val labeledAggregates =
        aggregates.map { aggregate ->
            TopologyChartAggregateView(
                label = aggregate.jointMode.toString(),
                runCount = aggregate.runCount,
                strictAcceptanceRate = aggregate.strictAcceptanceRate,
                strictAcceptedCount = aggregate.strictAcceptedCount,
                nearSolvedCount = aggregate.nearSolvedCount,
                closeMissCount = aggregate.closeMissCount,
                farFailureCount = aggregate.farFailureCount,
                averageFinalError = aggregate.averageFinalError,
                maxFinalError = aggregate.maxFinalError,
                averageIterations = aggregate.averageIterations,
                averageImprovementRatio = aggregate.averageImprovementRatio,
                averageJointLimitPressureRatio = aggregate.averageJointLimitPressureRatio,
                runsWithNearJointLimit = aggregate.runsWithNearJointLimit,
                fullJointLimitPressureCount = aggregate.fullJointLimitPressureCount
            )
        }

    GroupedRatioBarChart(
        title = "🦾 Acceptance Rate by Topology",
        subtitle = "AUTO vs REVOLUTE_ONLY vs PRISMATIC_ONLY vs MIXED.",
        items =
            labeledAggregates.map { aggregate ->
                ChartRatioItem(
                    label = aggregate.label,
                    ratio = aggregate.strictAcceptanceRate,
                    displayValue = formatChartPercent(aggregate.strictAcceptanceRate),
                    color = topologySuccessColor(aggregate.strictAcceptanceRate)
                )
            },
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )

    HorizontalDoubleBarChart(
        title = "🦾 Final Error by Topology",
        subtitle = "Shows which topology leaves worse residual error.",
        items =
            labeledAggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = aggregate.label,
                    value = aggregate.averageFinalError,
                    displayValue = "${formatChartDouble(aggregate.averageFinalError)} m",
                    color = topologyErrorColor(aggregate.averageFinalError)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    HorizontalDoubleBarChart(
        title = "🦾 Max Final Error by Topology",
        subtitle = "Shows worst-case topology residual error.",
        items =
            labeledAggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = aggregate.label,
                    value = aggregate.maxFinalError,
                    displayValue = "${formatChartDouble(aggregate.maxFinalError)} m",
                    color = topologyErrorColor(aggregate.maxFinalError)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    HorizontalDoubleBarChart(
        title = "🦾 Iterations by Topology",
        subtitle = "Shows solver cost by topology group.",
        items =
            labeledAggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = aggregate.label,
                    value = aggregate.averageIterations,
                    displayValue = formatChartDouble(aggregate.averageIterations),
                    color = Color(0xFF1565C0)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    GroupedRatioBarChart(
        title = "🦾 Average Improvement Ratio by Topology",
        subtitle = "Shows whether the solver moves in the right direction for each topology.",
        items =
            labeledAggregates.map { aggregate ->
                val ratio =
                    aggregate.averageImprovementRatio.coerceIn(0.0, 1.0)

                ChartRatioItem(
                    label = aggregate.label,
                    ratio = ratio,
                    displayValue = formatChartPercent(ratio),
                    color = topologyImprovementColor(ratio)
                )
            },
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )

    labeledAggregates.forEach { aggregate ->
        StackedBarChart(
            title = "🦾 Near / Close / Far — ${aggregate.label}",
            totalLabel = "Best topology quality view for this joint-mode group.",
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

    LinkCountTopologyHeatMap(
        runs = sequentialRuns
    )

    FailureCodeByTopologyChart(
        runs = sequentialRuns
    )

    HorizontalDoubleBarChart(
        title = "🦾 Joint-Limit Pressure by Topology",
        subtitle = "Shows which topology hits joint limits most.",
        items =
            labeledAggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = aggregate.label,
                    value = aggregate.averageJointLimitPressureRatio,
                    displayValue =
                        formatChartPercent(
                            aggregate.averageJointLimitPressureRatio.coerceIn(0.0, 1.0)
                        ),
                    color = topologyPressureColor(aggregate.averageJointLimitPressureRatio)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    HorizontalBarChart(
        title = "🦾 Near-Limit Runs by Topology",
        items =
            labeledAggregates.map { aggregate ->
                ChartBarItem(
                    label = aggregate.label,
                    value = aggregate.runsWithNearJointLimit,
                    color = Color(0xFFF9A825)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    HorizontalBarChart(
        title = "🦾 Full Joint-Limit Pressure Count by Topology",
        items =
            labeledAggregates.map { aggregate ->
                ChartBarItem(
                    label = aggregate.label,
                    value = aggregate.fullJointLimitPressureCount,
                    color = Color(0xFFC62828)
                )
            },
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    ChartSectionCard(
        title = "🦾 Topology Safety Summary",
        subtitle = "Quick topology-level risk view."
    ) {
        val weakestTopology =
            labeledAggregates.minByOrNull {
                it.strictAcceptanceRate
            }

        val highestErrorTopology =
            labeledAggregates.maxByOrNull {
                it.averageFinalError
            }

        val highestPressureTopology =
            labeledAggregates.maxByOrNull {
                it.averageJointLimitPressureRatio
            }

        ChartMetricRow(
            label = "Topology groups",
            value = labeledAggregates.size.toString()
        )

        ChartMetricRow(
            label = "Weakest topology",
            value = weakestTopology?.label ?: "NA"
        )

        ChartMetricRow(
            label = "Highest-error topology",
            value = highestErrorTopology?.label ?: "NA"
        )

        ChartMetricRow(
            label = "Highest joint-pressure topology",
            value = highestPressureTopology?.label ?: "NA"
        )
    }
}

@Composable
private fun LinkCountTopologyHeatMap(
    runs: List<DiagnosticRunResult>
) {
    val topologyModes =
        runs
            .map {
                it.jointMode.toString()
            }
            .distinct()
            .sorted()

    val linkCounts =
        runs
            .map {
                it.linkCount
            }
            .distinct()
            .sorted()
            .map {
                "${it}L"
            }

    val cells =
        topologyModes.flatMap { topologyMode ->
            linkCounts.map { linkLabel ->
                val linkCount =
                    linkLabel.removeSuffix("L").toIntOrNull() ?: 0

                val cellRuns =
                    runs.filter {
                        it.jointMode.toString() == topologyMode &&
                                it.linkCount == linkCount
                    }

                val acceptanceRate =
                    safeRatio(
                        numerator = cellRuns.count { it.solverAccepted },
                        denominator = cellRuns.size
                    )

                ChartHeatMapCell(
                    row = topologyMode,
                    column = linkLabel,
                    value = acceptanceRate,
                    displayValue = formatChartPercent(acceptanceRate),
                    color = topologySuccessColor(acceptanceRate)
                )
            }
        }

    ProfessionalHeatMapChart(
        title = "🦾 Link count × topology heat map",
        subtitle = "Rows = topology modes, columns = link counts, cell = acceptance rate.",
        rowLabels = topologyModes,
        columnLabels = linkCounts,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel = "Topology mode",
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )
}

@Composable
private fun FailureCodeByTopologyChart(
    runs: List<DiagnosticRunResult>
) {
    val topologyModes =
        runs
            .map {
                it.jointMode.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "🦾 Failure-Code by Topology",
        subtitle = "Shows whether topology changes the failure mode."
    ) {
        if (topologyModes.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No topology failure-code data available"
            )

            return@ChartSectionCard
        }

        topologyModes.forEach { topologyMode ->
            val topologyRuns =
                runs.filter {
                    it.jointMode.toString() == topologyMode
                }

            val detailCodes =
                topologyRuns
                    .map {
                        it.detailCode
                    }
                    .distinct()
                    .sorted()

            StackedBarChart(
                title = "🦾 $topologyMode Detail Codes",
                totalLabel = "Detail-code distribution for this topology.",
                slices =
                    detailCodes.map { detailCode ->
                        ChartSlice(
                            label = detailCode,
                            value =
                                topologyRuns.count {
                                    it.detailCode == detailCode
                                },
                            color = topologyDetailCodeColor(detailCode)
                        )
                    }
            )
        }
    }
}

private data class TopologyChartAggregateView(
    val label: String,
    val runCount: Int,
    val strictAcceptanceRate: Double,
    val strictAcceptedCount: Int,
    val nearSolvedCount: Int,
    val closeMissCount: Int,
    val farFailureCount: Int,
    val averageFinalError: Double,
    val maxFinalError: Double,
    val averageIterations: Double,
    val averageImprovementRatio: Double,
    val averageJointLimitPressureRatio: Double,
    val runsWithNearJointLimit: Int,
    val fullJointLimitPressureCount: Int
)

private fun topologySuccessColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.70 ->
            Color(0xFFF9A825)

        ratio > 0.0 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun topologyErrorColor(
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

private fun topologyImprovementColor(
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

private fun topologyPressureColor(
    pressure: Double
): Color {
    return when {
        pressure <= 0.0 ->
            Color(0xFF2E7D32)

        pressure < 0.33 ->
            Color(0xFF1565C0)

        pressure < 0.66 ->
            Color(0xFFF9A825)

        pressure < 1.0 ->
            Color(0xFFEF6C00)

        else ->
            Color(0xFFC62828)
    }
}

private fun topologyDetailCodeColor(
    detailCode: String
): Color {
    return when {
        detailCode == "NONE" ->
            Color(0xFF2E7D32)

        detailCode.contains("WARNING", ignoreCase = true) ->
            Color(0xFFF9A825)

        detailCode.contains("STAGNATION", ignoreCase = true) ->
            Color(0xFFF57C00)

        detailCode.contains("MAX_ITERATIONS", ignoreCase = true) ->
            Color(0xFFEF6C00)

        detailCode.contains("FAILED", ignoreCase = true) ||
                detailCode.contains("INVALID", ignoreCase = true) ||
                detailCode.contains("ERROR", ignoreCase = true) ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}
