package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerCaseAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTransitionAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ChartHeatMapLegendItem
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.spatial.DiagnosticWorkspace3DExplorer
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

private enum class HeatMapExplorerDimension {
    MATRIX_2D,
    WORKSPACE_3D
}

@Composable
fun HeatMapChartsPage(
    report: Layer1DiagnosticReport,
    heatMapIndex: DiagnosticHeatMapIndex,
    focusCategory: DiagnosticChartCategory,
    sequentialRuns: List<DiagnosticRunResult>
) {
    var explorerDimension by remember {
        mutableStateOf(HeatMapExplorerDimension.MATRIX_2D)
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("diagnostics-matrix-and-3d")
                .tutorialAnchor(TutorialTargets.DiagnosticsMatrix),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (explorerDimension == HeatMapExplorerDimension.MATRIX_2D) {
            FilledTonalButton(
                onClick = { explorerDimension = HeatMapExplorerDimension.MATRIX_2D },
                modifier = Modifier.weight(1f)
            ) {
                Text("2D Matrix")
            }
        } else {
            OutlinedButton(
                onClick = { explorerDimension = HeatMapExplorerDimension.MATRIX_2D },
                modifier = Modifier.weight(1f)
            ) {
                Text("2D Matrix")
            }
        }

        if (explorerDimension == HeatMapExplorerDimension.WORKSPACE_3D) {
            FilledTonalButton(
                onClick = { explorerDimension = HeatMapExplorerDimension.WORKSPACE_3D },
                modifier = Modifier.weight(1f)
            ) {
                Text("3D Workspace")
            }
        } else {
            OutlinedButton(
                onClick = { explorerDimension = HeatMapExplorerDimension.WORKSPACE_3D },
                modifier = Modifier.weight(1f)
            ) {
                Text("3D Workspace")
            }
        }
    }

    JargonAwareText(
        text = "A heat map uses colour to compare table cells. The 3D view groups nearby samples into voxels; each voxel is one small cube of workspace, so its result depends on grid resolution.",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (explorerDimension == HeatMapExplorerDimension.WORKSPACE_3D) {
        DiagnosticWorkspace3DExplorer(
            report = report,
            runs = sequentialRuns
        )
        return
    }

    when (focusCategory) {
        DiagnosticChartCategory.OVERVIEW -> {
            AcceptedRejectedHeatMap(index = heatMapIndex)
            StatusByExpectedClassHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.LINK_COUNT_SCALING -> {
            LinkCountSeedSuccessHeatMap(index = heatMapIndex)
            LinkCountTopologySuccessHeatMap(index = heatMapIndex)
            FailureCodeByLinkCountHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.SEED_SENSITIVITY -> {
            LinkCountSeedSuccessHeatMap(index = heatMapIndex)
            SeedTopologySuccessHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.TOPOLOGY -> {
            LinkCountTopologySuccessHeatMap(index = heatMapIndex)
            SeedTopologySuccessHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.STATUS_FAILURE_CODES -> {
            AcceptedRejectedHeatMap(index = heatMapIndex)
            FailureCodeByLinkCountHeatMap(index = heatMapIndex)
            StatusByExpectedClassHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.ERROR_DISTRIBUTION -> {
            TransitionMatrixHeatMaps(
                transitions = report.transitionAggregates,
                showSuccess = false,
                showError = true,
                showIterations = false
            )
            CaseMetricMatrixHeatMap(cases = report.perCaseAggregates)
        }

        DiagnosticChartCategory.ITERATIONS_CONVERGENCE ->
            TransitionMatrixHeatMaps(
                transitions = report.transitionAggregates,
                showSuccess = false,
                showError = false,
                showIterations = true
            )

        DiagnosticChartCategory.TRANSITIONS ->
            TransitionMatrixHeatMaps(
                transitions = report.transitionAggregates,
                showSuccess = true,
                showError = true,
                showIterations = true
            )

        DiagnosticChartCategory.JOINT_LIMITS -> {
            JointNameByCaseHeatMap(index = heatMapIndex)
            JointNameByTransitionHeatMap(index = heatMapIndex)
        }

        DiagnosticChartCategory.PROGRESS_CLASSES ->
            ProgressClassBySeedBucketHeatMap(index = heatMapIndex)

        DiagnosticChartCategory.PER_CASE ->
            CaseMetricMatrixHeatMap(cases = report.perCaseAggregates)

        else -> Unit
    }

    ChartSectionCard(
        title = "🗺️ Heat map notes",
        subtitle = "Matrix explorer is filtered to ${focusCategory.title}; it is a view, not a duplicate analysis category."
    ) {
        JargonAwareText(
            text = "Read the legend before comparing colours: each heat map can use a different scale. Warm colours below mean larger residuals, not automatically a more important result."
        )
        ChartLegendMetricRow(
            label = "Acceptance",
            value = "Green = strong, red = weak",
            color = Color(0xFF2E7D32)
        )

        ChartLegendMetricRow(
            label = "Error intensity",
            value = "Warmer colors indicate larger residuals",
            color = Color(0xFFEF6C00)
        )

        ChartLegendMetricRow(
            label = "Joint pressure",
            value = "Higher pressure trends toward red",
            color = Color(0xFFC62828)
        )
    }
}

/**
 * Renders every distinct two-dimensional diagnostic matrix exactly once for the automatic archive.
 *
 * The interactive explorer groups these matrices by topic, which intentionally repeats a few
 * useful views. The evidence archive instead uses this canonical list to avoid duplicate PNGs.
 * The 3D workspace is not called from this path and therefore can never enter the static archive.
 */
@Composable
internal fun DiagnosticTwoDimensionalMatrixArchivePage(
    report: Layer1DiagnosticReport,
    heatMapIndex: DiagnosticHeatMapIndex
) {
    diagnosticTwoDimensionalMatrixFigures.forEach { figure ->
        when (figure) {
            DiagnosticTwoDimensionalMatrixFigure.ACCEPTED_REJECTED ->
                AcceptedRejectedHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.STATUS_EXPECTED_CLASS ->
                StatusByExpectedClassHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.LINK_COUNT_SEED_SUCCESS ->
                LinkCountSeedSuccessHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.LINK_COUNT_TOPOLOGY_SUCCESS ->
                LinkCountTopologySuccessHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.SEED_TOPOLOGY_SUCCESS ->
                SeedTopologySuccessHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.FAILURE_CODE_LINK_COUNT ->
                FailureCodeByLinkCountHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.TRANSITION_SUCCESS ->
                TransitionMatrixHeatMaps(
                    transitions = report.transitionAggregates,
                    showSuccess = true,
                    showError = false,
                    showIterations = false
                )

            DiagnosticTwoDimensionalMatrixFigure.TRANSITION_FINAL_ERROR ->
                TransitionMatrixHeatMaps(
                    transitions = report.transitionAggregates,
                    showSuccess = false,
                    showError = true,
                    showIterations = false
                )

            DiagnosticTwoDimensionalMatrixFigure.TRANSITION_ITERATIONS ->
                TransitionMatrixHeatMaps(
                    transitions = report.transitionAggregates,
                    showSuccess = false,
                    showError = false,
                    showIterations = true
                )

            DiagnosticTwoDimensionalMatrixFigure.JOINT_NAME_CASE ->
                JointNameByCaseHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.JOINT_NAME_TRANSITION ->
                JointNameByTransitionHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.PROGRESS_CLASS_SEED_BUCKET ->
                ProgressClassBySeedBucketHeatMap(index = heatMapIndex)

            DiagnosticTwoDimensionalMatrixFigure.CASE_METRIC ->
                CaseMetricMatrixHeatMap(cases = report.perCaseAggregates)
        }
    }
}

@Composable
private fun AcceptedRejectedHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val total = index.seedLink.values.sumOf { it.total }
    if (total <= 0) return

    val accepted = index.seedLink.values.sumOf { it.accepted }
    val rejected = (total - accepted).coerceAtLeast(0)

    GenericHeatMapChart(
        title = "🗺️ Accepted vs Rejected",
        subtitle = "Two-block outcome view across all sequential runs.",
        rowLabels = listOf("All runs"),
        columnLabels = listOf("Accepted", "Rejected"),
        cells =
            listOf(
                ChartHeatMapCell(
                    row = "All runs",
                    column = "Accepted",
                    value = accepted.toDouble(),
                    displayValue = "$accepted · ${formatChartPercent(safeRatio(accepted, total))}",
                    color = Color(0xFF2E7D32)
                ),
                ChartHeatMapCell(
                    row = "All runs",
                    column = "Rejected",
                    value = rejected.toDouble(),
                    displayValue = "$rejected · ${formatChartPercent(safeRatio(rejected, total))}",
                    color = Color(0xFFC62828)
                )
            ),
        xAxisLabel = "Solver outcome",
        yAxisLabel = "Sequential dataset",
        legendItems =
            listOf(
                ChartHeatMapLegendItem("Accepted", accepted.toString(), Color(0xFF2E7D32)),
                ChartHeatMapLegendItem("Rejected", rejected.toString(), Color(0xFFC62828))
            )
    )
}

@Composable
private fun TransitionMatrixHeatMaps(
    transitions: List<DiagnosticTransitionAggregate>,
    showSuccess: Boolean,
    showError: Boolean,
    showIterations: Boolean
) {
    val compactTransitions = compactTransitionAggregates(transitions)
    if (compactTransitions.isEmpty()) return

    val caseLabels = compactTransitionCaseLabels(compactTransitions)
    val aggregationNote =
        "Scenario prefixes are combined into R/U case roles using exact run-count weighting."

    if (showSuccess) {
        GenericHeatMapChart(
            title = "🗺️ Transition success heat map",
            subtitle = "Rows = source role, columns = destination role. $aggregationNote",
            rowLabels = caseLabels,
            columnLabels = caseLabels,
            cells =
                compactTransitions.map { transition ->
                    val ratio = safeRatio(transition.acceptedCount, transition.runCount)
                    ChartHeatMapCell(
                        row = transition.fromCaseId,
                        column = transition.toCaseId,
                        value = ratio,
                        displayValue = formatChartPercent(ratio),
                        color = successColor(ratio)
                    )
                },
            xAxisLabel = "Destination case role",
            yAxisLabel = "Source case role"
        )
    }

    if (showError) {
        val maxError = compactTransitions.maxOfOrNull { it.averageFinalError } ?: 1.0
        GenericHeatMapChart(
            title = "🗺️ Transition final-error heat map",
            subtitle = "Weighted mean residual error. $aggregationNote",
            rowLabels = caseLabels,
            columnLabels = caseLabels,
            cells =
                compactTransitions.map { transition ->
                    ChartHeatMapCell(
                        row = transition.fromCaseId,
                        column = transition.toCaseId,
                        value = transition.averageFinalError,
                        displayValue = "${formatChartDouble(transition.averageFinalError)} m",
                        color = errorColor(normalizeAgainstMax(transition.averageFinalError, maxError))
                    )
                },
            xAxisLabel = "Destination case role",
            yAxisLabel = "Source case role"
        )
    }

    if (showIterations) {
        val maxIterations = compactTransitions.maxOfOrNull { it.averageIterations } ?: 1.0
        GenericHeatMapChart(
            title = "🗺️ Transition iterations heat map",
            subtitle = "Weighted mean solver cost. $aggregationNote",
            rowLabels = caseLabels,
            columnLabels = caseLabels,
            cells =
                compactTransitions.map { transition ->
                    ChartHeatMapCell(
                        row = transition.fromCaseId,
                        column = transition.toCaseId,
                        value = transition.averageIterations,
                        displayValue = formatChartDouble(transition.averageIterations),
                        color = costColor(normalizeAgainstMax(transition.averageIterations, maxIterations))
                    )
                },
            xAxisLabel = "Destination case role",
            yAxisLabel = "Source case role"
        )
    }
}

@Composable
private fun LinkCountSeedSuccessHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val seeds =
        index.seeds
            .map { it.toString() }

    val linkCounts =
        index.linkCounts
            .map { "${it}L" }

    val cells =
        seeds.flatMap { seedLabel ->
            linkCounts.map { linkLabel ->
                val seed =
                    seedLabel.toIntOrNull() ?: 0

                val linkCount =
                    linkLabel.removeSuffix("L").toIntOrNull() ?: 0

                val count = index.seedLink[seed to linkCount]

                val successRate =
                    safeRatio(
                        numerator = count?.accepted ?: 0,
                        denominator = count?.total ?: 0
                    )

                ChartHeatMapCell(
                    row = seedLabel,
                    column = linkLabel,
                    value = successRate,
                    displayValue = formatChartPercent(successRate),
                    color = successColor(successRate)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Link Count × Seed Success",
        subtitle = "Rows = seeds, columns = link counts.",
        rowLabels = seeds,
        columnLabels = linkCounts,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel = "Seed"
    )
}

@Composable
private fun LinkCountTopologySuccessHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val topologyModes =
        index.topologyModes

    val linkCounts =
        index.linkCounts
            .map { "${it}L" }

    val cells =
        topologyModes.flatMap { topologyMode ->
            linkCounts.map { linkLabel ->
                val linkCount =
                    linkLabel.removeSuffix("L").toIntOrNull() ?: 0

                val count = index.topologyLink[topologyMode to linkCount]

                val successRate =
                    safeRatio(
                        numerator = count?.accepted ?: 0,
                        denominator = count?.total ?: 0
                    )

                ChartHeatMapCell(
                    row = topologyMode,
                    column = linkLabel,
                    value = successRate,
                    displayValue = formatChartPercent(successRate),
                    color = successColor(successRate)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Link Count × Topology Success",
        subtitle = "Rows = topology modes, columns = link counts.",
        rowLabels = topologyModes,
        columnLabels = linkCounts,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel = "Topology mode"
    )
}

@Composable
private fun SeedTopologySuccessHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val seeds =
        index.seeds
            .map { it.toString() }

    val topologyModes =
        index.topologyModes

    val cells =
        seeds.flatMap { seedLabel ->
            topologyModes.map { topologyMode ->
                val seed =
                    seedLabel.toIntOrNull() ?: 0

                val count = index.seedTopology[seed to topologyMode]

                val successRate =
                    safeRatio(
                        numerator = count?.accepted ?: 0,
                        denominator = count?.total ?: 0
                    )

                ChartHeatMapCell(
                    row = seedLabel,
                    column = topologyMode,
                    value = successRate,
                    displayValue = formatChartPercent(successRate),
                    color = successColor(successRate)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Seed × Topology Success",
        subtitle = "Rows = seeds, columns = topology modes.",
        rowLabels = seeds,
        columnLabels = topologyModes,
        cells = cells,
        xAxisLabel = "Topology mode",
        yAxisLabel = "Seed"
    )
}

@Composable
private fun FailureCodeByLinkCountHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val failureCodes =
        index.failureCodes

    val linkCounts =
        index.linkCounts
            .map { "${it}L" }

    val cells =
        failureCodes.flatMap { failureCode ->
            linkCounts.map { linkLabel ->
                val linkCount =
                    linkLabel.removeSuffix("L").toIntOrNull() ?: 0

                val count = index.failureLink[failureCode to linkCount] ?: 0

                ChartHeatMapCell(
                    row = failureCode,
                    column = linkLabel,
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = countColor(count)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Failure Code × Link Count",
        subtitle = "Rows = detail codes, columns = link counts.",
        rowLabels = failureCodes,
        columnLabels = linkCounts,
        cells = cells,
        xAxisLabel = "Link count",
        yAxisLabel = "Failure/detail code"
    )
}

@Composable
private fun StatusByExpectedClassHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val statuses =
        index.statuses

    val expectedClasses =
        index.expectedClasses

    val cells =
        statuses.flatMap { status ->
            expectedClasses.map { expectedClass ->
                val count = index.statusExpectedClass[status to expectedClass] ?: 0

                ChartHeatMapCell(
                    row = status,
                    column = expectedClass,
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = countColor(count)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Status × Expected Class",
        subtitle = "Rows = status, columns = expected class.",
        rowLabels = statuses,
        columnLabels = expectedClasses,
        cells = cells,
        xAxisLabel = "Expected class",
        yAxisLabel = "Status"
    )
}

@Composable
private fun ProgressClassBySeedBucketHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val progressClasses =
        listOf(
            "SOLVED",
            "NEAR_SOLVED",
            "CLOSE_MISS",
            "IMPROVED_BUT_NOT_ENOUGH",
            "STALLED",
            "WORSENED",
            "INVALID_NUMERICAL",
            "UNKNOWN"
        )

    val seedBuckets =
        listOf(
            "EASY",
            "MEDIUM",
            "HARD",
            "EXTREME",
            "UNKNOWN"
        )

    val cells =
        progressClasses.flatMap { progressClass ->
            seedBuckets.map { seedBucket ->
                val count = index.progressSeedBucket[progressClass to seedBucket] ?: 0

                ChartHeatMapCell(
                    row = progressClass,
                    column = seedBucket,
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = progressCountColor(progressClass, count)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Progress Class × Seed Bucket",
        subtitle = "Rows = progress class, columns = seed bucket.",
        rowLabels = progressClasses,
        columnLabels = seedBuckets,
        cells = cells,
        xAxisLabel = "Seed bucket",
        yAxisLabel = "Progress class"
    )
}

@Composable
private fun CaseMetricMatrixHeatMap(
    cases: List<DiagnosticPerCaseAggregate>
) {
    val sortedCases =
        compactCaseMatrixAggregates(cases)

    if (sortedCases.isEmpty()) {
        return
    }

    val metricLabels =
        listOf(
            "acceptance",
            "avg error",
            "max error",
            "iterations",
            "joint pressure"
        )

    val maxAverageError =
        sortedCases
            .maxOfOrNull {
                it.averageError
            }
            ?.coerceAtLeast(1.0)
            ?: 1.0

    val maxMaxError =
        sortedCases
            .maxOfOrNull {
                it.maxError
            }
            ?.coerceAtLeast(1.0)
            ?: 1.0

    val maxIterations =
        sortedCases
            .maxOfOrNull {
                it.averageIterations
            }
            ?.coerceAtLeast(1.0)
            ?: 1.0

    val cells =
        sortedCases.flatMap { case ->
            val acceptanceRate =
                safeRatio(case.acceptedCount, case.runCount)

            listOf(
                ChartHeatMapCell(
                    row = case.caseId,
                    column = "acceptance",
                    value = acceptanceRate,
                    displayValue = formatChartPercent(acceptanceRate),
                    color = successColor(acceptanceRate)
                ),
                ChartHeatMapCell(
                    row = case.caseId,
                    column = "avg error",
                    value = case.averageError,
                    displayValue =
                        "${formatChartDouble(case.averageError)} m",
                    color =
                        errorColor(
                            case.averageError / maxAverageError
                        )
                ),
                ChartHeatMapCell(
                    row = case.caseId,
                    column = "max error",
                    value = case.maxError,
                    displayValue =
                        "${formatChartDouble(case.maxError)} m",
                    color =
                        errorColor(
                            case.maxError / maxMaxError
                        )
                ),
                ChartHeatMapCell(
                    row = case.caseId,
                    column = "iterations",
                    value = case.averageIterations,
                    displayValue =
                        formatChartDouble(case.averageIterations),
                    color =
                        costColor(
                            case.averageIterations / maxIterations
                        )
                ),
                ChartHeatMapCell(
                    row = case.caseId,
                    column = "joint pressure",
                    value = case.averageJointPressure,
                    displayValue =
                        formatChartPercent(
                            case.averageJointPressure
                                .coerceIn(0.0, 1.0)
                        ),
                    color =
                        pressureColor(
                            case.averageJointPressure
                        )
                )
            )
        }

    GenericHeatMapChart(
        title = "🗺️ Case × Metric Matrix",
        subtitle = "Rows = R/U case roles aggregated across benchmark scenarios; columns = metrics.",
        rowLabels = sortedCases.map { it.caseId },
        columnLabels = metricLabels,
        cells = cells,
        xAxisLabel = "Metric",
        yAxisLabel = "Case"
    )
}

@Composable
private fun JointNameByCaseHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val jointNames = index.jointNames
    val cases = index.caseIds

    if (jointNames.isEmpty() || cases.isEmpty()) {
        return
    }

    val cells =
        jointNames.flatMap { jointName ->
            cases.map { caseId ->
                val count = index.jointCase[jointName to caseId] ?: 0

                ChartHeatMapCell(
                    row = jointName,
                    column = caseId,
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = jointCountColor(count)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Joint Name × Case Pressure",
        subtitle = "Rows = joint names, columns = cases.",
        rowLabels = jointNames,
        columnLabels = cases,
        cells = cells,
        xAxisLabel = "Case",
        yAxisLabel = "Joint name"
    )
}

@Composable
private fun JointNameByTransitionHeatMap(
    index: DiagnosticHeatMapIndex
) {
    val jointNames = index.jointNames
    val transitions = index.transitions

    if (jointNames.isEmpty() || transitions.isEmpty()) {
        return
    }

    val cells =
        jointNames.flatMap { jointName ->
            transitions.map { transition ->
                val count = index.jointTransition[jointName to transition] ?: 0

                ChartHeatMapCell(
                    row = jointName,
                    column = transition,
                    value = count.toDouble(),
                    displayValue = count.toString(),
                    color = jointCountColor(count)
                )
            }
        }

    GenericHeatMapChart(
        title = "🗺️ Joint Name × Transition Pressure",
        subtitle = "Rows = joint names, columns = transitions.",
        rowLabels = jointNames,
        columnLabels = transitions,
        cells = cells,
        xAxisLabel = "Transition",
        yAxisLabel = "Joint name"
    )
}

@Composable
private fun GenericHeatMapChart(
    title: String,
    subtitle: String,
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String,
    legendItems: List<ChartHeatMapLegendItem> = emptyList()
) {
    ProfessionalHeatMapChart(
        title = title,
        subtitle = subtitle,
        rowLabels = rowLabels,
        columnLabels = columnLabels,
        cells = cells,
        xAxisLabel = xAxisLabel,
        yAxisLabel = yAxisLabel,
        legendItems = legendItems
    )
}

private fun normalizeHeatMapProgressClass(
    progressClass: String
): String {
    return when {
        progressClass.contains("SOLVED", true) &&
                progressClass.contains("NEAR", true) ->
            "NEAR_SOLVED"

        progressClass.contains("CLOSE", true) ->
            "CLOSE_MISS"

        progressClass.contains("SOLVED", true) ->
            "SOLVED"

        progressClass.contains("IMPROVED", true) ->
            "IMPROVED_BUT_NOT_ENOUGH"

        progressClass.contains("STALLED", true) ->
            "STALLED"

        progressClass.contains("WORSENED", true) ->
            "WORSENED"

        progressClass.contains("INVALID", true) ||
                progressClass.contains("NUMERICAL", true) ->
            "INVALID_NUMERICAL"

        else ->
            "UNKNOWN"
    }
}

private fun normalizeHeatMapSeedBucket(
    bucket: String
): String {
    return when {
        bucket.contains("EASY", true) -> "EASY"
        bucket.contains("MEDIUM", true) -> "MEDIUM"
        bucket.contains("HARD", true) -> "HARD"
        bucket.contains("EXTREME", true) -> "EXTREME"
        else -> "UNKNOWN"
    }
}

private fun successColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 -> Color(0xFF2E7D32)
        ratio >= 0.50 -> Color(0xFFF9A825)
        ratio > 0.0 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
}

private fun errorColor(
    value: Double
): Color {
    return when {
        !value.isFinite() -> Color(0xFFC62828)
        value <= 0.01 -> Color(0xFF2E7D32)
        value <= 0.10 -> Color(0xFFF9A825)
        value <= 0.50 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
}

private fun costColor(
    ratio: Double
): Color {
    return when {
        ratio <= 0.25 -> Color(0xFF2E7D32)
        ratio <= 0.50 -> Color(0xFFF9A825)
        ratio <= 0.75 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
}

private fun pressureColor(
    pressure: Double
): Color {
    return when {
        pressure <= 0.0 -> Color(0xFF2E7D32)
        pressure < 0.33 -> Color(0xFF1565C0)
        pressure < 0.66 -> Color(0xFFF9A825)
        pressure < 1.0 -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
}

private fun countColor(
    count: Int
): Color {
    return when {
        count <= 0 -> Color(0xFFBDBDBD)
        count < 10 -> Color(0xFF90CAF9)
        count < 100 -> Color(0xFF42A5F5)
        else -> Color(0xFF1565C0)
    }
}

private fun progressCountColor(
    progressClass: String,
    count: Int
): Color {
    if (count <= 0) {
        return Color(0xFFBDBDBD)
    }

    return when (normalizeHeatMapProgressClass(progressClass)) {
        "SOLVED" -> Color(0xFF2E7D32)
        "NEAR_SOLVED" -> Color(0xFF8BC34A)
        "CLOSE_MISS" -> Color(0xFFF9A825)
        "IMPROVED_BUT_NOT_ENOUGH" -> Color(0xFFEF6C00)
        "STALLED" -> Color(0xFF6D4C41)
        "WORSENED" -> Color(0xFFC62828)
        "INVALID_NUMERICAL" -> Color(0xFFAD1457)
        else -> Color(0xFF546E7A)
    }
}

private fun jointCountColor(
    count: Int
): Color {
    return when {
        count <= 0 -> Color(0xFFBDBDBD)
        count < 10 -> Color(0xFFFFE082)
        count < 100 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }
}

private fun normalizeAgainstMax(
    value: Double,
    maxValue: Double
): Double {
    return if (
        value.isFinite() &&
        maxValue.isFinite() &&
        maxValue > 0.0
    ) {
        (value / maxValue).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
}
