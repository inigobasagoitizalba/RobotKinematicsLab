package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.ProfessionalHistogramChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart

@Composable
fun IterationChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    displayRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        EmptyChartDataCard(
            title = "⏱️ Iterations & Convergence",
            subtitle = "No sequential run data is available for iteration charts."
        )

        return
    }

    val iterations =
        sequentialRuns
            .map {
                it.iterations.toDouble()
            }
            .filter {
                it.isFinite() && it >= 0.0
            }

    val iterationSaturation =
        sequentialRuns
            .map {
                it.iterationSaturationRatio
            }
            .filter {
                it.isFinite() && it >= 0.0
            }

    ChartSectionCard(
        title = "⏱️ Iteration / Convergence Summary",
        subtitle = "High-level solve-cost and saturation statistics."
    ) {
        ChartMetricRow(
            label = "Sequential runs",
            value = sequentialRuns.size.toString()
        )

        ChartMetricRow(
            label = "Average iterations",
            value = formatChartDouble(iterations.averageSafe())
        )

        ChartMetricRow(
            label = "Max iterations observed",
            value = formatChartDouble(iterations.maxOrNull() ?: 0.0)
        )

        ChartMetricRow(
            label = "Average iteration saturation",
            value =
                formatChartPercent(
                    iterationSaturation
                        .averageSafe()
                        .coerceIn(0.0, 1.0)
                )
        )

        ChartMetricRow(
            label = "Runs above 90% saturation",
            value =
                sequentialRuns.count {
                    it.iterationSaturationRatio >= 0.90
                }.toString()
        )

        ChartMetricRow(
            label = "Max-iteration hits",
            value =
                sequentialRuns.count {
                    isMaxIterationRun(it)
                }.toString()
        )
    }

    IterationHistogramChart(
        title = "⏱️ Iteration Histogram",
        subtitle = "Shows solve-cost distribution.",
        values = iterations,
        valueSuffix = " iterations",
        color = Color(0xFF1565C0)
    )

    IterationHistogramChart(
        title = "⏱️ Iteration Saturation Histogram",
        subtitle = "Shows how often runs approach the max-iteration budget.",
        values = iterationSaturation,
        valueSuffix = "",
        color = Color(0xFFEF6C00)
    )

    HorizontalDoubleBarChart(
        title = "⏱️ Average Iterations by Status",
        subtitle = "Shows computational cost per solver outcome.",
        items =
            sequentialRuns
                .groupBy {
                    it.status
                }
                .toList()
                .sortedByDescending { entry ->
                    entry.second.size
                }
                .map { entry ->
                    val status =
                        entry.first

                    val statusRuns =
                        entry.second

                    val averageIterations =
                        statusRuns
                            .map {
                                it.iterations.toDouble()
                            }
                            .averageSafe()

                    ChartDoubleBarItem(
                        label = status,
                        value = averageIterations,
                        displayValue = formatChartDouble(averageIterations),
                        color = iterationStatusColor(status)
                    )
                }
    )

    HorizontalDoubleBarChart(
        title = "⏱️ Iterations by Link Count",
        subtitle = "Shows computational cost scaling by link count.",
        items =
            sequentialRuns
                .groupBy {
                    it.linkCount
                }
                .toList()
                .sortedBy {
                    it.first
                }
                .map { entry ->
                    val linkCount =
                        entry.first

                    val averageIterations =
                        entry.second
                            .map {
                                it.iterations.toDouble()
                            }
                            .averageSafe()

                    ChartDoubleBarItem(
                        label = "$linkCount links",
                        value = averageIterations,
                        displayValue = formatChartDouble(averageIterations),
                        color = Color(0xFF1565C0)
                    )
                }
    )

    ScatterChart(
        title = "⏱️ Iterations by Final Error",
        subtitle = "Shows whether more iterations reduce residual error.",
        points =
            displayRuns.map { run ->
                ScatterPoint(
                    x = run.iterations.toDouble(),
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Iterations",
        yLabel = "Final error"
    )

    ScatterChart(
        title = "⏱️ Iterations by Initial Error",
        subtitle = "Shows whether hard initial seeds require more solver work.",
        points =
            displayRuns.map { run ->
                ScatterPoint(
                    x = run.initialError,
                    y = run.iterations.toDouble(),
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Initial error",
        yLabel = "Iterations"
    )

    GroupedRatioBarChart(
        title = "⏱️ Max-Iteration Hit Rate by Link Count",
        subtitle = "Shows where solver budget may be insufficient.",
        items =
            sequentialRuns
                .groupBy {
                    it.linkCount
                }
                .toList()
                .sortedBy {
                    it.first
                }
                .map { entry ->
                    val linkCount =
                        entry.first

                    val linkRuns =
                        entry.second

                    val hitRate =
                        safeRatio(
                            numerator =
                                linkRuns.count {
                                    isMaxIterationRun(it)
                                },
                            denominator = linkRuns.size
                        )

                    ChartRatioItem(
                        label = "$linkCount links",
                        ratio = hitRate,
                        displayValue = formatChartPercent(hitRate),
                        color = iterationRiskColor(hitRate)
                    )
                }
    )

    HorizontalDoubleBarChart(
        title = "⏱️ Iterations by Topology",
        subtitle = "Shows topology computational difficulty.",
        items =
            sequentialRuns
                .groupBy {
                    it.jointMode.toString()
                }
                .toList()
                .sortedBy {
                    it.first
                }
                .map { entry ->
                    val averageIterations =
                        entry.second
                            .map {
                                it.iterations.toDouble()
                            }
                            .averageSafe()

                    ChartDoubleBarItem(
                        label = entry.first,
                        value = averageIterations,
                        displayValue = formatChartDouble(averageIterations),
                        color = Color(0xFF1565C0)
                    )
                }
    )

    IterationSaturationHeatMap(
        title = "⏱️ Iteration saturation heat map — seed × link count",
        subtitle = "Rows = seeds, columns = link counts, cell = average saturation.",
        runs = sequentialRuns,
        rowMode = IterationHeatMapRowMode.SEED
    )

    IterationSaturationHeatMap(
        title = "⏱️ Iteration saturation heat map — topology × link count",
        subtitle = "Rows = topology modes, columns = link counts, cell = average saturation.",
        runs = sequentialRuns,
        rowMode = IterationHeatMapRowMode.TOPOLOGY
    )
}

@Composable
private fun IterationHistogramChart(
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
        xAxisLabel = "Iterations$valueSuffix",
        yAxisLabel = "Frequency",
        color = color,
        logScale = false
    )

    if (values.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 $title Statistics",
            subtitle = "Summary values for this iteration distribution."
        ) {
            ChartMetricRow(
                label = "Samples",
                value = values.size.toString()
            )

            ChartMetricRow(
                label = "Min",
                value = "${formatChartDouble(values.minOrNull() ?: 0.0)}$valueSuffix"
            )

            ChartMetricRow(
                label = "Max",
                value = "${formatChartDouble(values.maxOrNull() ?: 0.0)}$valueSuffix"
            )

            ChartMetricRow(
                label = "Mean",
                value = "${formatChartDouble(values.averageSafe())}$valueSuffix"
            )
        }
    }
}

@Composable
private fun ScatterChart(
    title: String,
    subtitle: String,
    points: List<ScatterPoint>,
    xLabel: String,
    yLabel: String
) {
    val chartPoints =
        points
            .filter {
                it.x.isFinite() &&
                        it.y.isFinite() &&
                        it.x >= 0.0 &&
                        it.y >= 0.0
            }
            .map { point ->
                ChartPoint(
                    x = point.x,
                    y = point.y,
                    color = point.color
                )
            }

    ProfessionalScatterChart(
        title = title,
        subtitle = subtitle,
        points = chartPoints,
        xAxisLabel = xLabel,
        yAxisLabel = yLabel
    )
}

@Composable
private fun IterationSaturationHeatMap(
    title: String,
    subtitle: String,
    runs: List<DiagnosticRunResult>,
    rowMode: IterationHeatMapRowMode
) {
    val rowLabels =
        when (rowMode) {
            IterationHeatMapRowMode.SEED ->
                runs
                    .map {
                        it.seed.toString()
                    }
                    .distinct()
                    .sorted()

            IterationHeatMapRowMode.TOPOLOGY ->
                runs
                    .map {
                        it.jointMode.toString()
                    }
                    .distinct()
                    .sorted()
        }

    val linkLabels =
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
        rowLabels.flatMap { rowLabel ->
            linkLabels.map { linkLabel ->
                val linkCount =
                    linkLabel.removeSuffix("L").toIntOrNull() ?: 0

                val cellRuns =
                    runs.filter { run ->
                        run.linkCount == linkCount &&
                                when (rowMode) {
                                    IterationHeatMapRowMode.SEED ->
                                        run.seed.toString() == rowLabel

                                    IterationHeatMapRowMode.TOPOLOGY ->
                                        run.jointMode.toString() == rowLabel
                                }
                    }

                val averageSaturation =
                    cellRuns
                        .map {
                            it.iterationSaturationRatio
                        }
                        .filter {
                            it.isFinite()
                        }
                        .averageSafe()
                        .coerceIn(0.0, 1.0)

                ChartHeatMapCell(
                    row = rowLabel,
                    column = linkLabel,
                    value = averageSaturation,
                    displayValue = formatChartPercent(averageSaturation),
                    color = iterationSaturationColor(averageSaturation)
                )
            }
        }

    ProfessionalHeatMapChart(
        title = title,
        subtitle = subtitle,
        rowLabels = rowLabels,
        columnLabels = linkLabels,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel =
            when (rowMode) {
                IterationHeatMapRowMode.SEED -> "Seed"
                IterationHeatMapRowMode.TOPOLOGY -> "Topology"
            }
    )
}

private enum class IterationHeatMapRowMode {
    SEED,
    TOPOLOGY
}

private data class ScatterPoint(
    val x: Double,
    val y: Double,
    val color: Color
)

private fun isMaxIterationRun(
    run: DiagnosticRunResult
): Boolean {
    return run.status.contains(
        other = "MAX_ITERATIONS",
        ignoreCase = true
    ) || run.detailCode.contains(
        other = "MAX_ITERATIONS",
        ignoreCase = true
    ) || run.iterationSaturationRatio >= 0.999
}

private fun iterationStatusColor(
    status: String
): Color {
    return when {
        status.contains("SUCCESS", ignoreCase = true) ->
            Color(0xFF2E7D32)

        status.contains("WARNING", ignoreCase = true) ->
            Color(0xFFF9A825)

        status.contains("MAX_ITERATIONS", ignoreCase = true) ->
            Color(0xFFEF6C00)

        status.contains("NO_CONVERGENCE", ignoreCase = true) ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}

private fun iterationRiskColor(
    ratio: Double
): Color {
    return when {
        ratio <= 0.01 ->
            Color(0xFF2E7D32)

        ratio <= 0.10 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun iterationSaturationColor(
    ratio: Double
): Color {
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
