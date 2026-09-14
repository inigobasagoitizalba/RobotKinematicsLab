package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.ProfessionalBoxPlotChart
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.ProfessionalHistogramChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.DonutChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart

@Composable
fun JointLimitChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    displayRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard(
            title = "🧱 Joint Limits",
            subtitle = "No sequential run data is available for joint-limit charts."
        )

        return
    }

    val nearLimitCount =
        sequentialRuns.count {
            it.nearLimitJointNames.isNotEmpty()
        }

    val fullPressureCount =
        sequentialRuns.count {
            it.jointLimitPressureRatio >= 1.0
        }

    val rejectedRuns =
        sequentialRuns.filter {
            !it.solverAccepted
        }

    val acceptedRuns =
        sequentialRuns.filter {
            it.solverAccepted
        }

    DonutChart(
        title = "🧱 Near-Limit Run Ratio",
        subtitle = "Shows how often joint limits are involved.",
        centerLabel =
            formatChartPercent(
                safeRatio(
                    numerator = nearLimitCount,
                    denominator = sequentialRuns.size
                )
            ),
        slices = listOf(
            ChartSlice(
                label = "Runs near joint limit",
                value = nearLimitCount,
                color = Color(0xFFF9A825)
            ),
            ChartSlice(
                label = "Runs not near limit",
                value = sequentialRuns.size - nearLimitCount,
                color = Color(0xFF2E7D32)
            )
        )
    )

    HorizontalBarChart(
        title = "🧱 Near-Limit Joint Frequency",
        items =
            sequentialRuns
                .flatMap {
                    it.nearLimitJointNames
                }
                .groupingBy {
                    it
                }
                .eachCount()
                .toList()
                .sortedByDescending {
                    it.second
                }
                .map { item ->
                    ChartBarItem(
                        label = item.first,
                        value = item.second,
                        color = Color(0xFFF9A825)
                    )
                }
    )

    JointPressureHistogramChart(
        title = "🧱 Joint-Limit Pressure Histogram",
        subtitle = "Distribution of joint-limit pressure ratio across all sequential runs.",
        values =
            sequentialRuns.map {
                it.jointLimitPressureRatio
            },
        color = Color(0xFF1565C0)
    )

    JointPressureHistogramChart(
        title = "🧱 Rejected Joint-Limit Pressure Histogram",
        subtitle = "Isolates failed/rejected runs to see whether limits contribute to failure.",
        values =
            rejectedRuns.map {
                it.jointLimitPressureRatio
            },
        color = Color(0xFFC62828)
    )

    JointPressureBoxPlotChart(
        title = "🧱 Accepted vs Rejected Pressure",
        subtitle = "Shows whether high joint-limit pressure predicts failure.",
        groups = listOf(
            JointPressureBoxGroup(
                label = "Accepted",
                values =
                    acceptedRuns.map {
                        it.jointLimitPressureRatio
                    },
                color = Color(0xFF2E7D32)
            ),
            JointPressureBoxGroup(
                label = "Rejected",
                values =
                    rejectedRuns.map {
                        it.jointLimitPressureRatio
                    },
                color = Color(0xFFC62828)
            )
        )
    )

    HorizontalDoubleBarChart(
        title = "🧱 Joint-Limit Pressure by Case",
        subtitle = "Shows target cases that force limits.",
        items =
            sequentialRuns
                .groupBy {
                    it.selectedCaseId
                }
                .toList()
                .sortedBy {
                    it.first
                }
                .map { entry ->
                    val caseId =
                        entry.first

                    val averagePressure =
                        entry.second
                            .map {
                                it.jointLimitPressureRatio
                            }
                            .averageSafe()

                    ChartDoubleBarItem(
                        label = caseId,
                        value = averagePressure,
                        displayValue = formatChartPercent(averagePressure.coerceIn(0.0, 1.0)),
                        color = pressureColor(averagePressure)
                    )
                }
    )

    JointPressureTransitionHeatMap(
        title = "🧱 Joint-Limit Pressure by Transition",
        subtitle = "Shows transition traps where the solver approaches joint limits.",
        runs = sequentialRuns
    )

    ChartSectionCard(
        title = "🧱 Joint-Limit Pressure by Topology",
        subtitle = "Shows topology-specific clamp risk."
    ) {
        ChartMetricRow(
            label = "Status",
            value = "Coming soon"
        )

        ChartMetricRow(
            label = "Reason",
            value = "Topology aggregate does not expose pressure yet"
        )

        ChartMetricRow(
            label = "Planned metric",
            value = "Average joint-limit pressure by topology"
        )
    }

    ChartSectionCard(
        title = "🧱 Full Joint-Limit Pressure Count",
        subtitle = "Shows severe clamp events."
    ) {
        ChartMetricRow(
            label = "Full pressure runs",
            value = fullPressureCount.toString()
        )

        ChartMetricRow(
            label = "Full pressure ratio",
            value =
                formatChartPercent(
                    safeRatio(
                        numerator = fullPressureCount,
                        denominator = sequentialRuns.size
                    )
                )
        )

        ChartMetricRow(
            label = "Rejected near-limit runs",
            value =
                rejectedRuns.count {
                    it.nearLimitJointNames.isNotEmpty()
                }.toString()
        )

        ChartMetricRow(
            label = "Average pressure",
            value =
                formatChartPercent(
                    sequentialRuns
                        .map {
                            it.jointLimitPressureRatio
                        }
                        .averageSafe()
                        .coerceIn(0.0, 1.0)
                )
        )
    }

    JointMovementScatterChart(
        title = "🧱 Joint Movement vs Final Error",
        subtitle = "Shows large motion that still leaves high residual error.",
        points =
            displayRuns.map { run ->
                JointMovementPoint(
                    x = run.jointDeltaNorm,
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Joint delta norm",
        yLabel = "Final error"
    )

    JointMovementScatterChart(
        title = "🧱 Max Single-Joint Movement vs Final Error",
        subtitle = "Detects single-joint saturation-like behavior.",
        points =
            displayRuns.map { run ->
                JointMovementPoint(
                    x = run.maxSingleJointMovement,
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Max single-joint movement",
        yLabel = "Final error"
    )

    JointMovementScatterChart(
        title = "🧱 Seed Limit Margin vs Final Error",
        subtitle = "A lower value means the solve starts closer to at least one joint limit.",
        points =
            displayRuns.map { run ->
                JointMovementPoint(
                    x = run.seedMinNormalizedLimitMargin,
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Seed minimum normalized margin",
        yLabel = "Final error"
    )

    JointMovementScatterChart(
        title = "🧱 Final Limit Margin vs Final Error",
        subtitle = "Shows whether residual error coincides with solutions ending near a joint limit.",
        points =
            displayRuns.map { run ->
                JointMovementPoint(
                    x = run.finalMinNormalizedLimitMargin,
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Final minimum normalized margin",
        yLabel = "Final error"
    )

    ChartSectionCard(
        title = "🧱 Normalized Limit-Margin Summary",
        subtitle = "Margins are comparable across joints because each is divided by its allowed range."
    ) {
        ChartMetricRow(
            label = "Average seed minimum margin",
            value =
                formatChartPercent(
                    sequentialRuns
                        .map { it.seedMinNormalizedLimitMargin }
                        .averageSafe()
                )
        )

        ChartMetricRow(
            label = "Average final minimum margin",
            value =
                formatChartPercent(
                    sequentialRuns
                        .map { it.finalMinNormalizedLimitMargin }
                        .averageSafe()
                )
        )

        ChartMetricRow(
            label = "Average normalized travel RMS",
            value =
                formatChartDouble(
                    sequentialRuns
                        .map { it.normalizedJointTravelRms }
                        .averageSafe()
                )
        )
    }
}

@Composable
private fun JointPressureHistogramChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    color: Color
) {
    val cleanValues =
        values.filter {
            it.isFinite() && it >= 0.0
        }

    ProfessionalHistogramChart(
        title = title,
        subtitle = subtitle,
        values = cleanValues,
        xAxisLabel = "Joint-limit pressure ratio",
        yAxisLabel = "Frequency",
        color = color,
        logScale = false
    )

    if (cleanValues.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 $title Statistics",
            subtitle = "Summary values for this joint-pressure distribution."
        ) {
            ChartMetricRow(
                label = "Samples",
                value = cleanValues.size.toString()
            )

            ChartMetricRow(
                label = "Average pressure",
                value = formatChartPercent(cleanValues.averageSafe().coerceIn(0.0, 1.0))
            )

            ChartMetricRow(
                label = "Max pressure",
                value = formatChartPercent((cleanValues.maxOrNull() ?: 0.0).coerceIn(0.0, 1.0))
            )
        }
    }
}

@Composable
private fun JointPressureBoxPlotChart(
    title: String,
    subtitle: String,
    groups: List<JointPressureBoxGroup>
) {
    val items =
        groups.map { group ->
            ChartBoxPlotItem(
                label = group.label,
                values =
                    group.values.filter {
                        it.isFinite() && it >= 0.0
                    },
                color = group.color
            )
        }

    ProfessionalBoxPlotChart(
        title = title,
        subtitle = subtitle,
        items = items,
        xAxisLabel = "Joint-limit pressure ratio",
        yAxisLabel = "Run class"
    )
}

@Composable
private fun JointPressureTransitionHeatMap(
    title: String,
    subtitle: String,
    runs: List<DiagnosticRunResult>
) {
    val grouped =
        runs.groupBy {
            (it.transitionFromCaseId ?: "START") to (it.transitionToCaseId ?: "UNKNOWN")
        }

    val labels =
        buildJointTransitionLabels(
            grouped.keys.toList()
        )

    val cells =
        grouped.map { entry ->
            val averagePressure =
                entry.value
                    .map {
                        it.jointLimitPressureRatio
                    }
                    .averageSafe()
                    .coerceIn(0.0, 1.0)

            ChartHeatMapCell(
                row = entry.key.first,
                column = entry.key.second,
                value = averagePressure,
                displayValue = formatChartPercent(averagePressure),
                color = pressureColor(averagePressure)
            )
        }

    ProfessionalHeatMapChart(
        title = title,
        subtitle = subtitle,
        rowLabels = labels,
        columnLabels = labels,
        cells = cells,
        xAxisLabel = "To case",
        yAxisLabel = "From case"
    )
}

@Composable
private fun JointMovementScatterChart(
    title: String,
    subtitle: String,
    points: List<JointMovementPoint>,
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

private data class JointPressureBoxGroup(
    val label: String,
    val values: List<Double>,
    val color: Color
)

private data class JointMovementPoint(
    val x: Double,
    val y: Double,
    val color: Color
)

private fun buildJointTransitionLabels(
    keys: List<Pair<String, String>>
): List<String> {
    val labels =
        mutableSetOf<String>()

    keys.forEach { key ->
        labels += key.first
        labels += key.second
    }

    return labels.sortedWith(
        compareBy<String> {
            if (it == "START") {
                0
            } else {
                1
            }
        }.thenBy {
            it
        }
    )
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

private fun pressureColor(
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
