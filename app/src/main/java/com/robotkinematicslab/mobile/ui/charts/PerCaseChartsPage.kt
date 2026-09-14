package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerCaseAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun PerCaseChartsPage(
    report: Layer1DiagnosticReport
) {
    val cases =
        report.perCaseAggregates.sortedBy {
            it.caseId
        }

    if (cases.isEmpty()) {
        EmptyChartDataCard(
            title = "🧪 Per-Case",
            subtitle = "No per-case aggregate data is available for this diagnostic report."
        )

        return
    }

    GroupedRatioBarChart(
        title = "🧪 Acceptance Rate by Case",
        subtitle = "Shows which target cases are accepted or rejected most often.",
        items =
            cases.map { case ->
                val acceptanceRate =
                    safeRatio(
                        numerator = case.sequentialAcceptedCount,
                        denominator = case.sequentialRunCount
                    )

                ChartRatioItem(
                    label = case.caseId,
                    ratio = acceptanceRate,
                    displayValue = formatChartPercent(acceptanceRate),
                    color = caseAcceptanceColor(
                        acceptanceRate = acceptanceRate,
                        expectedClass = case.expectedClass.toString()
                    )
                )
            }
    )

    GroupedRatioBarChart(
        title = "🧪 Close-or-Better Rate by Case",
        subtitle = "Strict accepted + near solved + close miss. Useful for tolerance tuning.",
        items =
            cases.map { case ->
                val closeOrBetterCount =
                    case.sequentialAcceptedCount +
                            case.nearSolvedCount +
                            case.closeMissCount

                val closeOrBetterRate =
                    safeRatio(
                        numerator = closeOrBetterCount,
                        denominator = case.sequentialRunCount
                    )

                ChartRatioItem(
                    label = case.caseId,
                    ratio = closeOrBetterRate,
                    displayValue = formatChartPercent(closeOrBetterRate),
                    color = caseAcceptanceColor(
                        acceptanceRate = closeOrBetterRate,
                        expectedClass = case.expectedClass.toString()
                    )
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🧪 Final Error by Case",
        subtitle = "Shows hard target cases by average residual error.",
        items =
            cases.map { case ->
                ChartDoubleBarItem(
                    label = case.caseId,
                    value = case.averageSequentialError,
                    displayValue = "${formatChartDouble(case.averageSequentialError)} m",
                    color = caseErrorColor(case.averageSequentialError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🧪 Max Final Error by Case",
        subtitle = "Shows worst-case residual error per target case.",
        items =
            cases.map { case ->
                ChartDoubleBarItem(
                    label = case.caseId,
                    value = case.maxSequentialError,
                    displayValue = "${formatChartDouble(case.maxSequentialError)} m",
                    color = caseErrorColor(case.maxSequentialError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🧪 Iterations by Case",
        subtitle = "Shows expensive target cases.",
        items =
            cases.map { case ->
                ChartDoubleBarItem(
                    label = case.caseId,
                    value = case.averageSequentialIterations,
                    displayValue = formatChartDouble(case.averageSequentialIterations),
                    color = Color(0xFF1565C0)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🧪 Joint-Limit Pressure by Case",
        subtitle = "Shows targets that push the solver toward joint limits.",
        items =
            cases.map { case ->
                ChartDoubleBarItem(
                    label = case.caseId,
                    value = case.averageJointLimitPressureRatio,
                    displayValue =
                        formatChartPercent(
                            case.averageJointLimitPressureRatio.coerceIn(0.0, 1.0)
                        ),
                    color = casePressureColor(case.averageJointLimitPressureRatio)
                )
            }
    )

    ProfessionalHorizontalBarChart(
        title = "🧪 False Reject Count by Reachable Case",
        subtitle = "Reachable target cases that were rejected by the solver.",
        items =
            cases
                .filter {
                    isReachableExpectedClass(it.expectedClass.toString())
                }
                .map { case ->
                    ChartBarItem(
                        label = case.caseId,
                        value = case.sequentialRejectedCount,
                        color = Color(0xFFF9A825)
                    )
                },
        xAxisLabel = "False rejects",
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    ProfessionalHorizontalBarChart(
        title = "🧪 False Accept Count by Unreachable Case",
        subtitle = "Unreachable target cases that were accepted by the solver.",
        items =
            cases
                .filter {
                    isUnreachableExpectedClass(it.expectedClass.toString())
                }
                .map { case ->
                    ChartBarItem(
                        label = case.caseId,
                        value = case.sequentialAcceptedCount,
                        color = Color(0xFFC62828)
                    )
                },
        xAxisLabel = "False accepts",
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    cases.forEach { case ->
        StackedBarChart(
            title = "🧪 Strict / Near / Close / Far — ${case.caseId}",
            totalLabel = "Best per-target quality view.",
            slices =
                listOf(
                    ChartSlice(
                        label = "Strict accepted",
                        value = case.sequentialAcceptedCount,
                        color = Color(0xFF2E7D32)
                    ),
                    ChartSlice(
                        label = "Near solved",
                        value = case.nearSolvedCount,
                        color = Color(0xFF8BC34A)
                    ),
                    ChartSlice(
                        label = "Close miss",
                        value = case.closeMissCount,
                        color = Color(0xFFF9A825)
                    ),
                    ChartSlice(
                        label = "Far failure",
                        value = case.farFailureCount,
                        color = Color(0xFFC62828)
                    )
                )
        )
    }

    CaseTargetScatterChart(
        title = "🧪 Case Target Coordinate Scatter",
        subtitle = "2D projection of target locations.",
        cases = cases,
        colorByReachability = false
    )

    CaseTargetScatterChart(
        title = "🧪 Target Reachability Map",
        subtitle = "Reachable vs unreachable target layout.",
        cases = cases,
        colorByReachability = true
    )

    ChartSectionCard(
        title = "🧪 Per-Case Safety Summary",
        subtitle = "Quick list of the most important target-level risks."
    ) {
        val worstAverageErrorCase =
            cases.maxByOrNull {
                it.averageSequentialError
            }

        val worstFalseRejectCase =
            cases
                .filter {
                    isReachableExpectedClass(it.expectedClass.toString())
                }
                .maxByOrNull {
                    it.sequentialRejectedCount
                }

        val worstFalseAcceptCase =
            cases
                .filter {
                    isUnreachableExpectedClass(it.expectedClass.toString())
                }
                .maxByOrNull {
                    it.sequentialAcceptedCount
                }

        val worstFarFailureCase =
            cases.maxByOrNull {
                it.farFailureCount
            }

        val bestCloseOrBetterCase =
            cases.maxByOrNull { case ->
                safeRatio(
                    numerator =
                        case.sequentialAcceptedCount +
                                case.nearSolvedCount +
                                case.closeMissCount,
                    denominator = case.sequentialRunCount
                )
            }

        ChartMetricRow(
            label = "Case count",
            value = cases.size.toString()
        )

        ChartMetricRow(
            label = "Worst average error case",
            value = worstAverageErrorCase?.caseId ?: "NA"
        )

        ChartMetricRow(
            label = "Worst reachable false-reject case",
            value = worstFalseRejectCase?.caseId ?: "NA"
        )

        ChartMetricRow(
            label = "Worst unreachable false-accept case",
            value = worstFalseAcceptCase?.caseId ?: "NA"
        )

        ChartMetricRow(
            label = "Worst far-failure case",
            value = worstFarFailureCase?.caseId ?: "NA"
        )

        ChartMetricRow(
            label = "Best close-or-better case",
            value = bestCloseOrBetterCase?.caseId ?: "NA"
        )
    }
}

@Composable
private fun CaseTargetScatterChart(
    title: String,
    subtitle: String,
    cases: List<DiagnosticPerCaseAggregate>,
    colorByReachability: Boolean
) {
    val points =
        cases.map { case ->
            ChartPoint(
                label = case.caseId,
                x = case.target.x,
                y = case.target.y,
                color =
                    if (colorByReachability) {
                        if (isReachableExpectedClass(case.expectedClass.toString())) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                    } else {
                        casePointColor(case.caseId)
                    }
            )
        }
            .filter {
                it.x.isFinite() &&
                        it.y.isFinite()
            }

    ProfessionalScatterChart(
        title = title,
        subtitle = subtitle,
        points = points,
        xAxisLabel = "Target X coordinate",
        yAxisLabel = "Target Y coordinate"
    )

    if (colorByReachability) {
        ChartSectionCard(
            title = "🧪 Target Map Legend",
            subtitle = "Colour meaning for the target coordinate scatter above."
        ) {
            ChartLegendMetricRow(
                label = "Reachable",
                value = cases.count {
                    isReachableExpectedClass(it.expectedClass.toString())
                }.toString(),
                color = Color(0xFF2E7D32)
            )

            ChartLegendMetricRow(
                label = "Unreachable",
                value = cases.count {
                    isUnreachableExpectedClass(it.expectedClass.toString())
                }.toString(),
                color = Color(0xFFC62828)
            )
        }
    } else {
        ChartSectionCard(
            title = "🧪 Case Target Scatter Legend",
            subtitle = "First visible case labels for the scatter above."
        ) {
            cases.take(8).forEach { case ->
                ChartLegendMetricRow(
                    label = case.caseId,
                    value = case.expectedClass.toString(),
                    color = casePointColor(case.caseId)
                )
            }
        }
    }
}

private fun isReachableExpectedClass(
    expectedClass: String
): Boolean {
    return expectedClass.contains(
        other = "REACHABLE",
        ignoreCase = true
    ) && !expectedClass.contains(
        other = "UNREACHABLE",
        ignoreCase = true
    )
}

private fun isUnreachableExpectedClass(
    expectedClass: String
): Boolean {
    return expectedClass.contains(
        other = "UNREACHABLE",
        ignoreCase = true
    )
}

private fun caseAcceptanceColor(
    acceptanceRate: Double,
    expectedClass: String
): Color {
    return when {
        isUnreachableExpectedClass(expectedClass) && acceptanceRate > 0.0 ->
            Color(0xFFC62828)

        isUnreachableExpectedClass(expectedClass) ->
            Color(0xFF2E7D32)

        acceptanceRate >= 0.90 ->
            Color(0xFF2E7D32)

        acceptanceRate >= 0.50 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun caseErrorColor(
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

private fun casePressureColor(
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

private fun casePointColor(
    caseId: String
): Color {
    return when {
        caseId.startsWith("R", ignoreCase = true) ->
            Color(0xFF2E7D32)

        caseId.startsWith("U", ignoreCase = true) ->
            Color(0xFFC62828)

        else ->
            Color(0xFF1565C0)
    }
}
