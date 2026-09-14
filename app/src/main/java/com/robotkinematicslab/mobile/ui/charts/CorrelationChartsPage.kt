package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.formatChartDouble
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard

@Composable
fun CorrelationChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    displayRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        EmptyChartDataCard(
            title = "🔬 Correlations",
            subtitle = "No sequential run data is available for correlation charts."
        )

        return
    }

    CorrelationSummaryCard(
        report = report,
        runs = sequentialRuns,
        displayedRunCount = displayRuns.size
    )

    CorrelationScatterChart(
        title = "🔬 Initial Error vs Final Error",
        subtitle = "Shows whether bad initial seeds cause bad final results.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.initialError,
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Initial error",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Initial Error vs Improvement Ratio",
        subtitle = "Shows whether hard starts still improve.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.initialError,
                    y = run.improvementRatio.coerceIn(0.0, 1.0),
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Initial error",
        yLabel = "Improvement ratio"
    )

    CorrelationScatterChart(
        title = "🔬 Initial Error vs Iterations",
        subtitle = "Shows solver cost as initial difficulty increases.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.initialError,
                    y = run.iterations.toDouble(),
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Initial error",
        yLabel = "Iterations"
    )

    CorrelationScatterChart(
        title = "🔬 Iterations vs Final Error",
        subtitle = "Shows whether more iterations reduce residual error.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.iterations.toDouble(),
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Iterations",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Joint-Limit Pressure vs Final Error",
        subtitle = "Shows whether clamp pressure is related to final residual error.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.jointLimitPressureRatio.coerceIn(0.0, 1.0),
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Joint-limit pressure",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Joint-Limit Pressure vs Iterations",
        subtitle = "Shows whether limits cause slow convergence.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.jointLimitPressureRatio.coerceIn(0.0, 1.0),
                    y = run.iterations.toDouble(),
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Joint-limit pressure",
        yLabel = "Iterations"
    )

    CorrelationScatterChart(
        title = "🔬 Joint Delta Norm vs Final Error",
        subtitle = "Shows whether large joint moves help or hurt.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.jointDeltaNorm,
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Joint delta norm",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Max Single-Joint Movement vs Final Error",
        subtitle = "Detects single-joint saturation behavior.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.maxSingleJointMovement,
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Max single-joint movement",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Seed Conditioning vs Final Error",
        subtitle = "Tests whether a poorly conditioned starting pose predicts residual error.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.seedLogConditionNumber,
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Seed log10 condition number",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Normalized Joint Travel vs Final Error",
        subtitle = "Compares motion across mixed revolute/prismatic robots on a common scale.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.normalizedJointTravelRms,
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Normalized joint travel RMS",
        yLabel = "Final error"
    )

    CorrelationScatterChart(
        title = "🔬 Backtracking Retries vs Solve Time",
        subtitle = "Measures the runtime cost of rejected candidate steps.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.backtrackingRetryCount.toDouble(),
                    y = run.solveDurationNanos / 1_000.0,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Backtracking retries",
        yLabel = "Solve duration (µs)"
    )

    CorrelationScatterChart(
        title = "🔬 Improvement Ratio vs Final Error",
        subtitle = "Separates useful-but-not-enough improvements from hard failures.",
        points =
            displayRuns.map { run ->
                CorrelationPoint(
                    x = run.improvementRatio.coerceIn(0.0, 1.0),
                    y = run.finalError,
                    color = acceptedColor(run.solverAccepted)
                )
            },
        xLabel = "Improvement ratio",
        yLabel = "Final error"
    )

    SolverParameterCorrelationCard(report = report)
}

@Composable
private fun CorrelationSummaryCard(
    report: Layer1DiagnosticReport,
    runs: List<DiagnosticRunResult>,
    displayedRunCount: Int
) {
    ChartSectionCard(
        title = "🔬 Correlation Summary",
        subtitle = "Quick statistical hints before inspecting scatter plots."
    ) {
        val initialVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.initialError },
                yValues = runs.map { it.finalError }
            )

        val iterationsVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.iterations.toDouble() },
                yValues = runs.map { it.finalError }
            )

        val pressureVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.jointLimitPressureRatio },
                yValues = runs.map { it.finalError }
            )

        val improvementVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.improvementRatio },
                yValues = runs.map { it.finalError }
            )

        val conditioningVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.seedLogConditionNumber },
                yValues = runs.map { it.finalError }
            )

        val normalizedTravelVsFinal =
            pearsonCorrelation(
                xValues = runs.map { it.normalizedJointTravelRms },
                yValues = runs.map { it.finalError }
            )

        val retriesVsDuration =
            pearsonCorrelation(
                xValues = runs.map { it.backtrackingRetryCount.toDouble() },
                yValues = runs.map { it.solveDurationNanos.toDouble() }
            )

        ChartMetricRow(
            label = "Sequential runs",
            value = runs.size.toString()
        )

        ChartMetricRow(
            label = "Runs rendered per scatter plot",
            value = "$displayedRunCount deterministic samples"
        )

        ChartMetricRow(
            label = "Accepted",
            value = runs.count { it.solverAccepted }.toString()
        )

        ChartMetricRow(
            label = "Rejected",
            value = runs.count { !it.solverAccepted }.toString()
        )

        ChartMetricRow(
            label = "Initial error ↔ final error",
            value = formatCorrelation(initialVsFinal)
        )

        ChartMetricRow(
            label = "Iterations ↔ final error",
            value = formatCorrelation(iterationsVsFinal)
        )

        ChartMetricRow(
            label = "Joint pressure ↔ final error",
            value = formatCorrelation(pressureVsFinal)
        )

        ChartMetricRow(
            label = "Improvement ratio ↔ final error",
            value = formatCorrelation(improvementVsFinal)
        )

        ChartMetricRow(
            label = "Seed conditioning ↔ final error",
            value = formatCorrelation(conditioningVsFinal)
        )

        ChartMetricRow(
            label = "Normalized travel ↔ final error",
            value = formatCorrelation(normalizedTravelVsFinal)
        )

        ChartMetricRow(
            label = "Backtracking ↔ solve duration",
            value = formatCorrelation(retriesVsDuration)
        )

        ChartMetricRow(
            label = "Solver tolerance",
            value = formatChartDouble(report.config.solver.ikTolerance, digits = 6)
        )
    }
}

@Composable
private fun SolverParameterCorrelationCard(
    report: Layer1DiagnosticReport
) {
    ChartSectionCard(
        title = "🔬 Solver Parameters vs Results",
        subtitle = "Reserved for comparing multiple reports or solver presets later."
    ) {
        ChartMetricRow(
            label = "IK max iterations",
            value = report.config.solver.ikMaxIterations.toString()
        )

        ChartMetricRow(
            label = "IK tolerance",
            value = formatChartDouble(report.config.solver.ikTolerance, digits = 6)
        )

        ChartMetricRow(
            label = "IK damping",
            value = formatChartDouble(report.config.solver.ikDamping, digits = 6)
        )

        ChartMetricRow(
            label = "IK max step",
            value = formatChartDouble(report.config.solver.ikMaxStep, digits = 6)
        )

        ChartMetricRow(
            label = "Current limitation",
            value = "Single report only"
        )

        ChartMetricRow(
            label = "Future chart",
            value = "Preset/parameter sweep comparison"
        )
    }
}

@Composable
private fun CorrelationScatterChart(
    title: String,
    subtitle: String,
    points: List<CorrelationPoint>,
    xLabel: String,
    yLabel: String
) {
    val cleanPoints =
        points.filter {
            it.x.isFinite() &&
                    it.y.isFinite() &&
                    it.x >= 0.0 &&
                    it.y >= 0.0
        }

    val chartPoints =
        cleanPoints.map { point ->
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

    if (cleanPoints.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 Scatter Plot Statistics",
            subtitle = "Legend and statistical summary for the correlation chart."
        ) {
            ChartLegendMetricRow(
                label = "Accepted runs",
                value =
                    cleanPoints.count {
                        it.color == Color(0xFF2E7D32)
                    }.toString(),
                color = Color(0xFF2E7D32)
            )

            ChartLegendMetricRow(
                label = "Rejected runs",
                value =
                    cleanPoints.count {
                        it.color == Color(0xFFC62828)
                    }.toString(),
                color = Color(0xFFC62828)
            )

            ChartMetricRow(
                label = "Correlation coefficient",
                value =
                    formatCorrelation(
                        pearsonCorrelation(
                            xValues = cleanPoints.map { it.x },
                            yValues = cleanPoints.map { it.y }
                        )
                    )
            )

            ChartMetricRow(
                label = "Total points",
                value = cleanPoints.size.toString()
            )
        }
    }
}

private data class CorrelationPoint(
    val x: Double,
    val y: Double,
    val color: Color
)

private fun acceptedColor(
    solverAccepted: Boolean
): Color {
    return if (solverAccepted) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFC62828)
    }
}

private fun pearsonCorrelation(
    xValues: List<Double>,
    yValues: List<Double>
): Double? {
    val pairs =
        xValues
            .zip(yValues)
            .filter {
                it.first.isFinite() && it.second.isFinite()
            }

    if (pairs.size < 2) {
        return null
    }

    val averageX =
        pairs.map {
            it.first
        }.average()

    val averageY =
        pairs.map {
            it.second
        }.average()

    val numerator =
        pairs.sumOf { pair ->
            (pair.first - averageX) * (pair.second - averageY)
        }

    val denominatorX =
        pairs.sumOf { pair ->
            val delta =
                pair.first - averageX

            delta * delta
        }

    val denominatorY =
        pairs.sumOf { pair ->
            val delta =
                pair.second - averageY

            delta * delta
        }

    val denominator =
        kotlin.math.sqrt(denominatorX * denominatorY)

    if (denominator == 0.0 || !denominator.isFinite()) {
        return null
    }

    return numerator / denominator
}

private fun formatCorrelation(
    value: Double?
): String {
    return if (value == null || !value.isFinite()) {
        "NA"
    } else {
        formatChartDouble(value, digits = 3)
    }
}
