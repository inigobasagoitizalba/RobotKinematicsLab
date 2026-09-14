package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.DonutChart
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart

@Composable
fun ExportBackedChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    oracleRuns: List<DiagnosticRunResult>
) {
    ChartSectionCard(
        title = "📦 Export-ready data & charts",
        subtitle = "Checks which diagnostic data and saved chart images are already available."
    ) {
        ChartMetricRow(
            label = "Human-readable report",
            value = "Available"
        )

        ChartMetricRow(
            label = "Run-level rows",
            value = report.runResults.size.toString()
        )

        ChartMetricRow(
            label = "Sequential run rows",
            value = sequentialRuns.size.toString()
        )

        ChartMetricRow(
            label = "Oracle / one-shot rows",
            value = oracleRuns.size.toString()
        )

        ChartMetricRow(
            label = "Aggregate tables",
            value = aggregateTableCount(report).toString()
        )

        ChartMetricRow(
            label = "Recommended next export",
            value = "Run-level CSV"
        )
    }

    DonutChart(
        title = "📦 Run-Level Export Coverage",
        subtitle = "Every solver run can become one CSV row.",
        centerLabel = report.runResults.size.toString(),
        slices = listOf(
            ChartSlice(
                label = "Sequential stability rows",
                value = sequentialRuns.size,
                color = Color(0xFF1565C0)
            ),
            ChartSlice(
                label = "Oracle / one-shot rows",
                value = oracleRuns.size,
                color = Color(0xFF2E7D32)
            )
        )
    )

    HorizontalBarChart(
        title = "📦 Aggregate Export Table Sizes",
        items = listOf(
            ChartBarItem(
                label = "Link-count aggregates",
                value = report.linkCountAggregates.size,
                color = Color(0xFF1565C0)
            ),
            ChartBarItem(
                label = "Seed aggregates",
                value = report.seedAggregates.size,
                color = Color(0xFF2E7D32)
            ),
            ChartBarItem(
                label = "Topology aggregates",
                value = report.topologyAggregates.size,
                color = Color(0xFFF9A825)
            ),
            ChartBarItem(
                label = "Per-case aggregates",
                value = report.perCaseAggregates.size,
                color = Color(0xFF6A1B9A)
            ),
            ChartBarItem(
                label = "Transition aggregates",
                value = report.transitionAggregates.size,
                color = Color(0xFFEF6C00)
            ),
            ChartBarItem(
                label = "Status distribution",
                value = report.statusDistribution.size,
                color = Color(0xFF546E7A)
            ),
            ChartBarItem(
                label = "Detail-code distribution",
                value = report.detailCodeDistribution.size,
                color = Color(0xFFC62828)
            )
        )
    )

    ExportReadinessCard(
        title = "📦 CSV Run-Level Export",
        status = exportStatus(
            ready = report.runResults.isNotEmpty()
        ),
        purpose = "Every sequential/oracle solver run becomes one row.",
        recommendedColumns = listOf(
            "runIndex",
            "runKind",
            "selectedCaseId",
            "expectedClass",
            "solverAccepted",
            "status",
            "detailCode",
            "initialError",
            "finalError",
            "improvementRatio",
            "progressClass",
            "seedDistanceBucket",
            "seedMinNormalizedLimitMargin",
            "seedLogConditionNumber",
            "iterations",
            "iterationSaturationRatio",
            "jointDeltaNorm",
            "maxSingleJointMovement",
            "normalizedJointTravelRms",
            "finalMinNormalizedLimitMargin",
            "backtrackingRetryCount",
            "solveDurationNanos",
            "jointLimitPressureRatio",
            "nearLimitJointNames",
            "transitionFromCaseId",
            "transitionToCaseId"
        )
    )

    ExportReadinessCard(
        title = "📦 CSV Aggregate Export",
        status = exportStatus(
            ready = aggregateTableCount(report) > 0
        ),
        purpose = "Exports summary tables for quick spreadsheet analysis.",
        recommendedColumns = listOf(
            "linkCountAggregates",
            "seedAggregates",
            "topologyAggregates",
            "perCaseAggregates",
            "transitionAggregates",
            "statusDistribution",
            "detailCodeDistribution",
            "datasetAcceptanceSummary",
            "fallbackSummary"
        )
    )

    ExportReadinessCard(
        title = "📦 JSON Report Export",
        status = exportStatus(
            ready = true
        ),
        purpose = "Full machine-readable diagnostic report.",
        recommendedColumns = listOf(
            "experimentName",
            "config",
            "benchmarkPlan",
            "finalVerdict",
            "targetCases",
            "cases",
            "runResults",
            "aggregates",
            "summary"
        )
    )

    ChartSectionCard(
        title = "📦 Automatic Figure Library",
        subtitle = "Completed scientific charts are stored automatically as PNG files; Appearance & save also provides a manual save action."
    ) {
        ChartMetricRow(
            label = "Status",
            value = "Available"
        )

        ChartMetricRow(
            label = "Organisation",
            value = "Analysis family / date / experiment / chart type"
        )

        ChartMetricRow(
            label = "Format",
            value = "PNG at the device-rendered figure resolution"
        )

        ChartMetricRow(
            label = "Saved in",
            value = "Storage / Project evidence / Figures / automatic"
        )

        ChartMetricRow(
            label = "Scientific traceability",
            value = "CSV and JSON remain the canonical numeric sources"
        )
    }

    ExportReadinessCard(
        title = "📦 Report Bundle",
        status = exportStatus(
            ready = report.runResults.isNotEmpty()
        ),
        purpose = "Human-readable text plus CSV tables for reproducible diagnostics.",
        recommendedColumns = listOf(
            "diagnostic_report.txt",
            "run_results.csv",
            "link_count_aggregates.csv",
            "seed_aggregates.csv",
            "topology_aggregates.csv",
            "per_case_aggregates.csv",
            "transition_aggregates.csv",
            "status_distribution.csv",
            "detail_code_distribution.csv"
        )
    )

    ChartSectionCard(
        title = "📦 Export Safety Notes",
        subtitle = "Important for ML dataset cleanliness."
    ) {
        ChartMetricRow(
            label = "Strong reliability exports",
            value =
                if (report.benchmarkPlan.strongReliabilityClaimAllowed) {
                    "Allowed"
                } else {
                    "Not allowed"
                }
        )

        ChartMetricRow(
            label = "Experimental mode",
            value = report.benchmarkPlan.isExperimental.toString()
        )

        ChartMetricRow(
            label = "Unlimited mode",
            value = report.benchmarkPlan.isUnlimited.toString()
        )

        ChartMetricRow(
            label = "False accepts",
            value = report.datasetAcceptanceSummary.falseAcceptCount.toString()
        )

        ChartMetricRow(
            label = "False rejects",
            value = report.datasetAcceptanceSummary.falseRejectCount.toString()
        )

        ChartMetricRow(
            label = "Export recommendation",
            value =
                if (report.datasetAcceptanceSummary.falseAcceptCount == 0 &&
                    report.benchmarkPlan.strongReliabilityClaimAllowed
                ) {
                    "Safe candidate for ML filtering"
                } else {
                    "Export for diagnostics only"
                }
        )
    }

    ChartSectionCard(
        title = "📦 Next Backend Step",
        subtitle = "What to add when you are ready to write files."
    ) {
        ChartMetricRow(
            label = "First function",
            value = "toRunLevelCsv()"
        )

        ChartMetricRow(
            label = "Second function",
            value = "toAggregateCsvBundle()"
        )

        ChartMetricRow(
            label = "Third function",
            value = "toJsonReport()"
        )

        ChartMetricRow(
            label = "Best UI action",
            value = "Export Report Bundle"
        )
    }
}

@Composable
private fun ExportReadinessCard(
    title: String,
    status: ExportStatus,
    purpose: String,
    recommendedColumns: List<String>
) {
    ChartSectionCard(
        title = title,
        subtitle = purpose
    ) {
        ChartMetricRow(
            label = "Status",
            value = status.label
        )

        ChartMetricRow(
            label = "Readiness",
            value = formatChartPercent(status.readiness)
        )

        GroupedRatioBarChart(
            title = "$title Readiness",
            subtitle = "How close this export type is to being usable.",
            items = listOf(
                ChartRatioItem(
                    label = status.label,
                    ratio = status.readiness,
                    displayValue = formatChartPercent(status.readiness),
                    color = status.color
                )
            )
        )

        ChartMetricRow(
            label = "Recommended fields",
            value = recommendedColumns.size.toString()
        )

        recommendedColumns
            .take(8)
            .forEach { column ->
                ChartMetricRow(
                    label = "Field",
                    value = column
                )
            }

        if (recommendedColumns.size > 8) {
            ChartMetricRow(
                label = "More fields",
                value = "+${recommendedColumns.size - 8}"
            )
        }
    }
}

private data class ExportStatus(
    val label: String,
    val readiness: Double,
    val color: Color
)

private fun exportStatus(
    ready: Boolean
): ExportStatus {
    return if (ready) {
        ExportStatus(
            label = "Ready for backend export function",
            readiness = 1.0,
            color = Color(0xFF2E7D32)
        )
    } else {
        ExportStatus(
            label = "Needs data first",
            readiness = 0.25,
            color = Color(0xFFF9A825)
        )
    }
}

private fun aggregateTableCount(
    report: Layer1DiagnosticReport
): Int {
    return listOf(
        report.linkCountAggregates.isNotEmpty(),
        report.seedAggregates.isNotEmpty(),
        report.topologyAggregates.isNotEmpty(),
        report.perCaseAggregates.isNotEmpty(),
        report.transitionAggregates.isNotEmpty(),
        report.statusDistribution.isNotEmpty(),
        report.detailCodeDistribution.isNotEmpty()
    ).count {
        it
    }
}
