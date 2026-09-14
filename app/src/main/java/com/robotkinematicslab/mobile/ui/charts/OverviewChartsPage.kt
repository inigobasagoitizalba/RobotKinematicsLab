package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.DonutChart
import com.robotkinematicslab.mobile.ui.charts.basic.GaugeChart
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun OverviewChartsPage(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    val dataset =
        report.datasetAcceptanceSummary

    DonutChart(
        title = "📊 Final Verdict Summary",
        subtitle = "Shows PASS, PASS_WITH_WARNINGS, FAIL, or EXPERIMENTAL_RESULT_ONLY.",
        centerLabel = compactVerdictLabel(report.finalVerdict.toString()),
        slices = listOf(
            ChartSlice(
                label = report.finalVerdict.toString(),
                value = 1,
                color = verdictColor(report.finalVerdict.toString())
            )
        )
    )

    DonutChart(
        title = "📊 Sequential Accepted vs Rejected",
        subtitle = "Fast visual of strict sequential success rate.",
        centerLabel = formatChartPercent(
            safeRatio(
                numerator = summary.sequentialAcceptedCount,
                denominator = summary.sequentialRuns
            )
        ),
        slices = listOf(
            ChartSlice(
                label = "Accepted",
                value = summary.sequentialAcceptedCount,
                color = Color(0xFF2E7D32)
            ),
            ChartSlice(
                label = "Rejected",
                value = summary.sequentialRejectedCount,
                color = Color(0xFFC62828)
            )
        ),
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    StackedBarChart(
        title = "📊 Reachable Accepted / Rejected",
        totalLabel = "Shows false rejects on FK-proven reachable targets.",
        slices = listOf(
            ChartSlice(
                label = "Reachable accepted",
                value = summary.sequentialReachableAccepted,
                color = Color(0xFF2E7D32)
            ),
            ChartSlice(
                label = "Reachable rejected / false rejects",
                value = summary.sequentialReachableRejected,
                color = Color(0xFFF9A825)
            )
        ),
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    StackedBarChart(
        title = "📊 Unreachable Accepted / Rejected",
        totalLabel = "Shows false accepts on unreachable targets.",
        slices = listOf(
            ChartSlice(
                label = "Unreachable accepted / false accepts",
                value = summary.sequentialUnreachableAccepted,
                color = Color(0xFFC62828)
            ),
            ChartSlice(
                label = "Unreachable rejected",
                value = summary.sequentialUnreachableRejected,
                color = Color(0xFF2E7D32)
            )
        ),
        // The two slices have opposite desirable directions: fewer false accepts and more
        // correct rejections. A single "higher" or "lower" badge would be misleading.
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    GroupedRatioBarChart(
        title = "📊 Dataset Acceptance Ratio",
        subtitle = "Compares reachable acceptance against unreachable rejection.",
        items = listOf(
            ChartRatioItem(
                label = "Reachable acceptance",
                ratio = dataset.reachableAcceptanceRate,
                displayValue = formatChartPercent(dataset.reachableAcceptanceRate),
                color = Color(0xFF2E7D32)
            ),
            ChartRatioItem(
                label = "Unreachable rejection",
                ratio = dataset.unreachableRejectionRate,
                displayValue = formatChartPercent(dataset.unreachableRejectionRate),
                color = Color(0xFF1565C0)
            )
        )
    )

    val oraclePassRatio =
        safeRatio(
            numerator = summary.oracleReachableAccepted,
            denominator = summary.oracleReachableRuns
        )

    GaugeChart(
        title = "📊 Oracle Pass Rate",
        subtitle = "Confirms FK-proven reachable targets solve from their source q.",
        ratio = oraclePassRatio,
        valueLabel = formatChartPercent(oraclePassRatio),
        color = oracleColor(oraclePassRatio)
    )

    StackedBarChart(
        title = "📊 Strict vs Near vs Close vs Far",
        totalLabel = "Better than plain accepted/rejected because it shows almost-solved cases.",
        slices = listOf(
            ChartSlice(
                label = "Strict accepted",
                value = summary.sequentialAcceptedCount,
                color = Color(0xFF2E7D32)
            ),
            ChartSlice(
                label = "Near solved",
                value = summary.nearSolvedCount,
                color = Color(0xFF8BC34A)
            ),
            ChartSlice(
                label = "Close miss",
                value = summary.closeMissCountSafe(),
                color = Color(0xFFF9A825)
            ),
            ChartSlice(
                label = "Far / other rejected",
                value = farOrOtherRejectedCount(summary),
                color = Color(0xFFC62828)
            )
        )
    )

    ChartSectionCard(
        title = "📊 Dataset Safety Summary",
        subtitle = "Summary values used by the overview charts."
    ) {
        ChartMetricRow(
            label = "False accepts",
            value = dataset.falseAcceptCount.toString()
        )

        ChartMetricRow(
            label = "False rejects",
            value = dataset.falseRejectCount.toString()
        )

        ChartMetricRow(
            label = "Average final error",
            value = "${formatChartDouble(summary.averageSequentialError)} m"
        )

        ChartMetricRow(
            label = "Max final error",
            value = "${formatChartDouble(summary.maxSequentialError)} m"
        )

        ChartMetricRow(
            label = "Average iterations",
            value = formatChartDouble(summary.averageSequentialIterations)
        )
    }
}

private fun DiagnosticSummary.closeMissCountSafe(): Int {
    return 0
}

private fun farOrOtherRejectedCount(
    summary: DiagnosticSummary
): Int {
    val remaining =
        summary.sequentialRejectedCount -
                summary.nearSolvedCount -
                summary.closeMissCountSafe()

    return remaining.coerceAtLeast(0)
}

private fun compactVerdictLabel(
    verdict: String
): String {
    return when (verdict) {
        "PASS" ->
            "PASS"

        "PASS_WITH_WARNINGS" ->
            "WARN"

        "FAIL" ->
            "FAIL"

        "EXPERIMENTAL_RESULT_ONLY" ->
            "EXP"

        else ->
            verdict
    }
}

private fun verdictColor(
    verdict: String
): Color {
    return when {
        verdict == "PASS" ->
            Color(0xFF2E7D32)

        verdict == "PASS_WITH_WARNINGS" ->
            Color(0xFFF9A825)

        verdict == "EXPERIMENTAL_RESULT_ONLY" ->
            Color(0xFF1565C0)

        verdict == "FAIL" ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}

private fun oracleColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.99 ->
            Color(0xFF2E7D32)

        ratio >= 0.80 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}
