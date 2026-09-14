package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart

@Composable
fun SolverComparisonChartsPage(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    val solver =
        report.config.solver

    val strictAcceptanceRate =
        safeRatio(
            numerator = summary.sequentialAcceptedCount,
            denominator = summary.sequentialRuns
        )

    val falseRejectRate =
        safeRatio(
            numerator = summary.sequentialReachableRejected,
            denominator = summary.sequentialReachableRuns
        )

    val falseAcceptRate =
        safeRatio(
            numerator = summary.sequentialUnreachableAccepted,
            denominator = summary.sequentialUnreachableRuns
        )

    val estimatedCost =
        summary.sequentialRuns.toDouble() * summary.averageSequentialIterations

    val solveDurationsMicros =
        report.runResults
            .mapNotNull { run ->
                run.solveDurationNanos
                    .takeIf { it >= 0L }
                    ?.div(1_000.0)
            }
            .sorted()

    val averageSolveDurationMicros =
        if (solveDurationsMicros.isEmpty()) {
            0.0
        } else {
            solveDurationsMicros.average()
        }

    val p95SolveDurationMicros =
        if (solveDurationsMicros.isEmpty()) {
            0.0
        } else {
            solveDurationsMicros[
                ((solveDurationsMicros.size - 1) * 0.95)
                    .toInt()
            ]
        }

    val averageBacktrackingRetries =
        if (report.runResults.isEmpty()) {
            0.0
        } else {
            report.runResults
                .map { it.backtrackingRetryCount }
                .average()
        }

    ChartSectionCard(
        title = "⚙️ Solver Configuration Summary",
        subtitle = "Current report baseline. True preset comparison needs storing multiple reports."
    ) {
        ChartMetricRow(
            label = "IK max iterations",
            value = solver.ikMaxIterations.toString()
        )

        ChartMetricRow(
            label = "IK tolerance",
            value = formatChartDouble(solver.ikTolerance, digits = 6)
        )

        ChartMetricRow(
            label = "IK damping",
            value = formatChartDouble(solver.ikDamping, digits = 6)
        )

        ChartMetricRow(
            label = "IK max step",
            value = formatChartDouble(solver.ikMaxStep, digits = 6)
        )

        ChartMetricRow(
            label = "Strict acceptance",
            value = formatChartPercent(strictAcceptanceRate)
        )

        ChartMetricRow(
            label = "False reject rate",
            value = formatChartPercent(falseRejectRate)
        )

        ChartMetricRow(
            label = "False accept rate",
            value = formatChartPercent(falseAcceptRate)
        )
    }

    GroupedRatioBarChart(
        title = "⚙️ Solver Preset Success Comparison",
        subtitle = "Current report as baseline. Add stored reports later for Balanced vs Precision vs Exploration.",
        items = listOf(
            ChartRatioItem(
                label = "Current config",
                ratio = strictAcceptanceRate,
                displayValue = formatChartPercent(strictAcceptanceRate),
                color = solverSuccessColor(strictAcceptanceRate)
            )
        )
    )

    HorizontalDoubleBarChart(
        title = "⚙️ Solver Preset Final Error Comparison",
        subtitle = "Current report baseline for residual error.",
        items = listOf(
            ChartDoubleBarItem(
                label = "Average final error",
                value = summary.averageSequentialError,
                displayValue = "${formatChartDouble(summary.averageSequentialError)} m",
                color = solverErrorColor(summary.averageSequentialError)
            ),
            ChartDoubleBarItem(
                label = "Max final error",
                value = summary.maxSequentialError,
                displayValue = "${formatChartDouble(summary.maxSequentialError)} m",
                color = solverErrorColor(summary.maxSequentialError)
            )
        )
    )

    HorizontalDoubleBarChart(
        title = "⚙️ Solver Preset Iteration Cost",
        subtitle = "Current report baseline for computational cost.",
        items = listOf(
            ChartDoubleBarItem(
                label = "Average iterations",
                value = summary.averageSequentialIterations,
                displayValue = formatChartDouble(summary.averageSequentialIterations),
                color = Color(0xFF1565C0)
            ),
            ChartDoubleBarItem(
                label = "Estimated total iteration work",
                value = estimatedCost,
                displayValue = formatChartDouble(estimatedCost),
                color = Color(0xFFEF6C00)
            )
        )
    )

    HorizontalDoubleBarChart(
        title = "⚙️ Measured Solver Runtime",
        subtitle = "Wall-clock solve duration captured for each run; use repeated runs for scientific comparison.",
        items = listOf(
            ChartDoubleBarItem(
                label = "Average solve duration",
                value = averageSolveDurationMicros,
                displayValue = "${formatChartDouble(averageSolveDurationMicros)} µs",
                color = Color(0xFF1565C0)
            ),
            ChartDoubleBarItem(
                label = "P95 solve duration",
                value = p95SolveDurationMicros,
                displayValue = "${formatChartDouble(p95SolveDurationMicros)} µs",
                color = Color(0xFFEF6C00)
            ),
            ChartDoubleBarItem(
                label = "Average backtracking retries",
                value = averageBacktrackingRetries,
                displayValue = formatChartDouble(averageBacktrackingRetries),
                color = Color(0xFF6A1B9A)
            )
        )
    )

    SolverParameterMarkerCard(
        title = "⚙️ Damping Sensitivity Curve",
        subtitle = "Future line chart: X = damping, Y = success/error/cost.",
        parameterName = "Current damping",
        parameterValue = formatChartDouble(solver.ikDamping, digits = 6),
        currentResultLabel = "Current strict acceptance",
        currentResultValue = formatChartPercent(strictAcceptanceRate)
    )

    SolverParameterMarkerCard(
        title = "⚙️ Max-Step Sensitivity Curve",
        subtitle = "Future line chart: X = max step, Y = success/error/cost.",
        parameterName = "Current max step",
        parameterValue = formatChartDouble(solver.ikMaxStep, digits = 6),
        currentResultLabel = "Current average final error",
        currentResultValue = "${formatChartDouble(summary.averageSequentialError)} m"
    )

    SolverParameterMarkerCard(
        title = "⚙️ Tolerance vs False Reject Curve",
        subtitle = "Future line chart: X = tolerance, Y = false reject rate.",
        parameterName = "Current tolerance",
        parameterValue = formatChartDouble(solver.ikTolerance, digits = 6),
        currentResultLabel = "Current false reject rate",
        currentResultValue = formatChartPercent(falseRejectRate)
    )

    SolverParameterMarkerCard(
        title = "⚙️ Max Iterations vs Success Curve",
        subtitle = "Future line chart: X = max iterations, Y = strict acceptance.",
        parameterName = "Current max iterations",
        parameterValue = solver.ikMaxIterations.toString(),
        currentResultLabel = "Current strict acceptance",
        currentResultValue = formatChartPercent(strictAcceptanceRate)
    )

    SolverParetoScatterChart(
        title = "⚙️ Numerical Precision / Cost Pareto Chart",
        subtitle = "Current report as one point. Later each preset/config becomes one point.",
        points = listOf(
            SolverParetoPoint(
                x = estimatedCost,
                y = strictAcceptanceRate,
                label = "Current config",
                color = solverSuccessColor(strictAcceptanceRate)
            )
        )
    )

    ChartSectionCard(
        title = "⚙️ What Is Needed for True Solver Comparison",
        subtitle = "The UI is ready, but true comparison needs multiple stored reports."
    ) {
        ChartMetricRow(
            label = "Needed report store",
            value = "List<Layer1DiagnosticReport>"
        )

        ChartMetricRow(
            label = "Needed label",
            value = "Balanced / Precision / Exploration / Custom"
        )

        ChartMetricRow(
            label = "Best future model",
            value = "DiagnosticReportComparisonSet"
        )

        ChartMetricRow(
            label = "Then charts become",
            value = "Real multi-preset grouped/line/scatter charts"
        )
    }
}

@Composable
private fun SolverParameterMarkerCard(
    title: String,
    subtitle: String,
    parameterName: String,
    parameterValue: String,
    currentResultLabel: String,
    currentResultValue: String
) {
    ChartSectionCard(
        title = title,
        subtitle = subtitle
    ) {
        ChartMetricRow(
            label = parameterName,
            value = parameterValue
        )

        ChartMetricRow(
            label = currentResultLabel,
            value = currentResultValue
        )

        ChartMetricRow(
            label = "Status",
            value = "Single-report marker only"
        )

        ChartMetricRow(
            label = "Future requirement",
            value = "Run same benchmark across parameter sweep"
        )
    }
}

@Composable
private fun SolverParetoScatterChart(
    title: String,
    subtitle: String,
    points: List<SolverParetoPoint>
) {
    val cleanPoints =
        points.filter {
            it.x.isFinite() &&
                    it.y.isFinite() &&
                    it.x >= 0.0 &&
                    it.y >= 0.0
        }

    ProfessionalScatterChart(
        title = title,
        subtitle = subtitle,
        points =
            cleanPoints.map { point ->
                ChartPoint(
                    x = point.x,
                    y = point.y,
                    color = point.color,
                    label = point.label
                )
            },
        xAxisLabel = "Estimated iteration cost",
        yAxisLabel = "Strict acceptance"
    )

    if (cleanPoints.isNotEmpty()) {
        ChartSectionCard(
            title = "📌 Pareto Point Details",
            subtitle = "Current solver configuration point values."
        ) {
            cleanPoints.forEach { point ->
                ChartMetricRow(
                    label = point.label,
                    value = "cost=${formatChartDouble(point.x)}, success=${formatChartPercent(point.y)}"
                )
            }
        }
    }
}

private data class SolverParetoPoint(
    val x: Double,
    val y: Double,
    val label: String,
    val color: Color
)

private fun solverSuccessColor(
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

private fun solverErrorColor(
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
