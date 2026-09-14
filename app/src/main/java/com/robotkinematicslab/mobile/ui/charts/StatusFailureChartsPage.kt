package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartTimelineCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts.ProfessionalTimelineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun StatusFailureChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    displayRuns: List<DiagnosticRunResult>
) {
    val statusItems =
        report.statusDistribution
            .sortedByDescending {
                it.count
            }
            .map { item ->
                ChartBarItem(
                    label = item.status,
                    value = item.count,
                    color = statusColor(item.status)
                )
            }

    val detailCodeItems =
        report.detailCodeDistribution
            .sortedByDescending {
                it.count
            }
            .map { item ->
                ChartBarItem(
                    label = item.detailCode,
                    value = item.count,
                    color = detailCodeColor(item.detailCode)
                )
            }

    ProfessionalHorizontalBarChart(
        title = "⚠️ Status Distribution",
        subtitle = "Overall solver status frequencies.",
        items = statusItems,
        xAxisLabel = "Count"
    )

    ProfessionalHorizontalBarChart(
        title = "⚠️ Detail-Code Distribution",
        subtitle = "Overall detail-code frequencies.",
        items = detailCodeItems,
        xAxisLabel = "Count"
    )

    ProfessionalHorizontalBarChart(
        title = "⚠️ Failure-Code Distribution Among Rejected Only",
        subtitle = "Rejected runs grouped by detail code.",
        items =
            sequentialRuns
                .filter {
                    !it.solverAccepted
                }
                .groupBy {
                    it.detailCode
                }
                .toList()
                .sortedByDescending {
                    it.second.size
                }
                .map { entry ->
                    ChartBarItem(
                        label = entry.first,
                        value = entry.second.size,
                        color = detailCodeColor(entry.first)
                    )
                },
        xAxisLabel = "Rejected runs"
    )

    StatusByExpectedClassChart(
        runs = sequentialRuns
    )

    DetailCodeByExpectedClassChart(
        runs = sequentialRuns
    )

    StatusByLinkCountChart(
        runs = sequentialRuns
    )

    DetailCodeByLinkCountHeatMap(
        runs = sequentialRuns
    )

    StatusTimelineChart(
        runs = displayRuns
    )

    AcceptedRejectedTimelineChart(
        runs = displayRuns
    )

    ChartSectionCard(
        title = "⚠️ Failure / Status Summary",
        subtitle = "Quick safety indicators from sequential diagnostic runs."
    ) {
        ChartMetricRow(
            label = "Sequential runs",
            value = sequentialRuns.size.toString()
        )

        ChartMetricRow(
            label = "Accepted",
            value = sequentialRuns.count { it.solverAccepted }.toString()
        )

        ChartMetricRow(
            label = "Rejected",
            value = sequentialRuns.count { !it.solverAccepted }.toString()
        )

        ChartMetricRow(
            label = "False accepts",
            value = report.datasetAcceptanceSummary.falseAcceptCount.toString()
        )

        ChartMetricRow(
            label = "False rejects",
            value = report.datasetAcceptanceSummary.falseRejectCount.toString()
        )
    }
}

@Composable
private fun StatusByExpectedClassChart(
    runs: List<DiagnosticRunResult>
) {
    val expectedClasses =
        runs
            .map {
                it.expectedClass.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "⚠️ Status by Expected Class",
        subtitle = "Separates reachable failures from unreachable rejections."
    ) {
        if (expectedClasses.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No expected-class status data available"
            )

            return@ChartSectionCard
        }

        expectedClasses.forEach { expectedClass ->
            val classRuns =
                runs.filter {
                    it.expectedClass.toString() == expectedClass
                }

            val statuses =
                classRuns
                    .map {
                        it.status
                    }
                    .distinct()
                    .sorted()

            StackedBarChart(
                title = "⚠️ $expectedClass",
                totalLabel = "Solver status distribution for this expected class.",
                slices =
                    statuses.map { status ->
                        ChartSlice(
                            label = status,
                            value =
                                classRuns.count {
                                    it.status == status
                                },
                            color = statusColor(status)
                        )
                    }
            )
        }
    }
}

@Composable
private fun DetailCodeByExpectedClassChart(
    runs: List<DiagnosticRunResult>
) {
    val expectedClasses =
        runs
            .map {
                it.expectedClass.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "⚠️ Detail Code by Expected Class",
        subtitle = "Shows whether reachable and unreachable cases fail for different reasons."
    ) {
        if (expectedClasses.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No expected-class detail-code data available"
            )

            return@ChartSectionCard
        }

        expectedClasses.forEach { expectedClass ->
            val classRuns =
                runs.filter {
                    it.expectedClass.toString() == expectedClass
                }

            val detailCodes =
                classRuns
                    .map {
                        it.detailCode
                    }
                    .distinct()
                    .sorted()

            StackedBarChart(
                title = "⚠️ $expectedClass Detail Codes",
                totalLabel = "Detail-code distribution for this expected class.",
                slices =
                    detailCodes.map { detailCode ->
                        ChartSlice(
                            label = detailCode,
                            value =
                                classRuns.count {
                                    it.detailCode == detailCode
                                },
                            color = detailCodeColor(detailCode)
                        )
                    }
            )
        }
    }
}

@Composable
private fun StatusByLinkCountChart(
    runs: List<DiagnosticRunResult>
) {
    val linkCounts =
        runs
            .map {
                it.linkCount
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "⚠️ Status by Link Count",
        subtitle = "Shows how solver failure modes change as robot complexity increases."
    ) {
        if (linkCounts.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No link-count status data available"
            )

            return@ChartSectionCard
        }

        linkCounts.forEach { linkCount ->
            val linkRuns =
                runs.filter {
                    it.linkCount == linkCount
                }

            val statuses =
                linkRuns
                    .map {
                        it.status
                    }
                    .distinct()
                    .sorted()

            StackedBarChart(
                title = "⚠️ $linkCount Links",
                totalLabel = "Status distribution for this link count.",
                slices =
                    statuses.map { status ->
                        ChartSlice(
                            label = status,
                            value =
                                linkRuns.count {
                                    it.status == status
                                },
                            color = statusColor(status)
                        )
                    }
            )
        }
    }
}

@Composable
private fun DetailCodeByLinkCountHeatMap(
    runs: List<DiagnosticRunResult>
) {
    val detailCodes =
        runs
            .map {
                it.detailCode
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

    val columnLabels =
        linkCounts.map {
            "${it}L"
        }

    val cells =
        detailCodes.flatMap { detailCode ->
            linkCounts.map { linkCount ->
                val count =
                    runs.count {
                        it.detailCode == detailCode &&
                                it.linkCount == linkCount
                    }

                ChartHeatMapCell(
                    row = detailCode,
                    column = "${linkCount}L",
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = countHeatColor(count)
                )
            }
        }

    ProfessionalHeatMapChart(
        title = "⚠️ Detail code by link count heat map",
        subtitle = "Rows = detail codes, columns = link counts.",
        rowLabels = detailCodes,
        columnLabels = columnLabels,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel = "Detail code"
    )
}

@Composable
private fun StatusTimelineChart(
    runs: List<DiagnosticRunResult>
) {
    ProfessionalTimelineChart(
        title = "⚠️ Status Timeline by Run Index",
        subtitle = "Shows whether failures cluster over time.",
        cells =
            runs.map { run ->
                ChartTimelineCell(
                    label = run.status,
                    color = statusColor(run.status)
                )
            },
        xAxisLabel = "Run index",
        legendItems =
            runs.map {
                it.status
            }
                .distinct()
                .sorted()
                .map { status ->
                    ChartSlice(
                        label = status,
                        value =
                            runs.count {
                                it.status == status
                            },
                        color = statusColor(status)
                    )
                }
    )
}

@Composable
private fun AcceptedRejectedTimelineChart(
    runs: List<DiagnosticRunResult>
) {
    val acceptedCount =
        runs.count {
            it.solverAccepted
        }

    val rejectedCount =
        runs.count {
            !it.solverAccepted
        }

    val acceptanceRate =
        formatChartPercent(
            safeRatio(
                numerator = acceptedCount,
                denominator = runs.size
            )
        )

    ProfessionalTimelineChart(
        title = "⚠️ Accepted / Rejected Timeline",
        subtitle = "Shows sequential instability or repeated bad transitions.",
        cells =
            runs.map { run ->
                ChartTimelineCell(
                    label =
                        if (run.solverAccepted) {
                            "Accepted"
                        } else {
                            "Rejected"
                        },
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xAxisLabel = "Run index",
        legendItems =
            listOf(
                ChartSlice(
                    label = "Accepted",
                    value = acceptedCount,
                    color = Color(0xFF2E7D32)
                ),
                ChartSlice(
                    label = "Rejected",
                    value = rejectedCount,
                    color = Color(0xFFC62828)
                )
            ),
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    ChartSectionCard(
        title = "⚠️ Accepted / Rejected Timeline Summary",
        subtitle = "Sequential acceptance ratio for the timeline above."
    ) {
        ChartLegendMetricRow(
            label = "Acceptance rate",
            value = acceptanceRate,
            color = Color(0xFF1565C0)
        )
    }
}

private fun statusColor(
    status: String
): Color {
    return when {
        status.contains("SUCCESS", ignoreCase = true) &&
                status.contains("WARNING", ignoreCase = true) ->
            Color(0xFFF9A825)

        status.contains("SUCCESS", ignoreCase = true) ->
            Color(0xFF2E7D32)

        status.contains("WARNING", ignoreCase = true) ->
            Color(0xFFF9A825)

        status.contains("NO_CONVERGENCE", ignoreCase = true) ->
            Color(0xFFF57C00)

        status.contains("MAX_ITERATIONS", ignoreCase = true) ->
            Color(0xFFEF6C00)

        status.contains("FAIL", ignoreCase = true) ||
                status.contains("INVALID", ignoreCase = true) ||
                status.contains("ERROR", ignoreCase = true) ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}

private fun detailCodeColor(
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
                detailCode.contains("NON_FINITE", ignoreCase = true) ||
                detailCode.contains("INVALID", ignoreCase = true) ||
                detailCode.contains("ERROR", ignoreCase = true) ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}

private fun countHeatColor(
    count: Int
): Color {
    return when {
        count <= 0 ->
            Color(0xFFBDBDBD)

        count < 10 ->
            Color(0xFF90CAF9)

        count < 100 ->
            Color(0xFF42A5F5)

        else ->
            Color(0xFF1565C0)
    }
}
