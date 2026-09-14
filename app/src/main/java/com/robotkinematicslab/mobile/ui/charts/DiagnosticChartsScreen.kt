package com.robotkinematicslab.mobile.ui.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.FinalTelemetryFigureArchiver
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS
import com.robotkinematicslab.mobile.ui.shared.progress.TelemetrySessionRepository
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.charts.basic.LocalChartCardsCollapsible
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticChartsScreen(
    report: Layer1DiagnosticReport,
    onBack: () -> Unit,
    figureExecutionId: String? = null,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember {
        mutableStateOf(DiagnosticChartCategory.OVERVIEW)
    }

    var selectedViewMode by remember {
        mutableStateOf(DiagnosticChartViewMode.ALL_CHARTS)
    }
    val screenScrollState = rememberScrollState()

    val context = LocalContext.current.applicationContext
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val telemetryRepository = remember(context) { TelemetrySessionRepository(context) }
    val finalTelemetryFigureArchiver = remember(context) { FinalTelemetryFigureArchiver(context) }
    val activeProject = remember(context) { ResearchProjectRepository(context).activeProject() }
    var preparedData by remember(report, selectedCategory, selectedViewMode) {
        mutableStateOf<DiagnosticChartPreparedData?>(null)
    }
    var loadingProgress by remember(report, selectedCategory, selectedViewMode) {
        mutableStateOf(
            DiagnosticProgressState(
                isRunning = true,
                phase = DiagnosticProgressPhase.AGGREGATING,
                totalRuns = CHART_PREPARATION_STEPS.size,
                message = "Preparing ${selectedCategory.title}."
            )
        )
    }
    val performanceSamples = remember(report, selectedCategory, selectedViewMode) {
        mutableStateListOf<DiagnosticPerformanceSample>()
    }

    LaunchedEffect(report, selectedCategory, selectedViewMode) {
        val startedAtMs = System.currentTimeMillis()
        var sampleIndex = 0

        fun publishProgress(
            completedSteps: Int,
            message: String,
            running: Boolean = true,
            failed: Boolean = false
        ) {
            val elapsedSeconds =
                ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)) / 1000.0
            val stepsPerSecond =
                if (elapsedSeconds > 0.0) completedSteps / elapsedSeconds else 0.0
            val remaining = (CHART_PREPARATION_STEPS.size - completedSteps).coerceAtLeast(0)
            val estimate =
                if (stepsPerSecond > 0.0) remaining / stepsPerSecond else Double.NaN

            loadingProgress =
                DiagnosticProgressState(
                    isRunning = running,
                    phase =
                        when {
                            failed -> DiagnosticProgressPhase.FAILED
                            !running -> DiagnosticProgressPhase.COMPLETED
                            else -> DiagnosticProgressPhase.AGGREGATING
                        },
                    completedRuns = completedSteps,
                    totalRuns = CHART_PREPARATION_STEPS.size,
                    runsPerSecond = stepsPerSecond,
                    estimatedSecondsRemaining = estimate,
                    elapsedSeconds = elapsedSeconds,
                    message = message,
                    telemetry = telemetrySampler.sample()
                )

            performanceSamples +=
                DiagnosticPerformanceSample.fromProgressState(
                    sampleIndex = ++sampleIndex,
                    progressState = loadingProgress
                )
        }

        preparedData = null
        performanceSamples.clear()
        publishProgress(0, "Reading completed benchmark data.")

        val telemetryTicker =
            launch {
                while (isActive) {
                    delay(HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS)
                    val current = loadingProgress
                    if (current.isRunning) {
                        publishProgress(
                            completedSteps = current.completedRuns,
                            message = current.message
                        )
                    }
                }
            }

        try {
            val partition =
                withContext(Dispatchers.Default) {
                    partitionDiagnosticRuns(report.runResults)
                }

            publishProgress(1, "Separating sequential and oracle runs.")

            val sortedPartition =
                withContext(Dispatchers.Default) {
                    sortDiagnosticRuns(partition)
                }

            publishProgress(2, "Building the run timeline.")

            val metricCounts =
                withContext(Dispatchers.Default) {
                    countDiagnosticMetricFiniteness(sortedPartition.sequentialRuns)
                }

            publishProgress(3, "Checking numeric values used by charts.")

            val categoryPreparation =
                withContext(Dispatchers.Default) {
                    val groupCount =
                        countDiagnosticCategoryGroups(
                            category = selectedCategory,
                            runs = sortedPartition.sequentialRuns
                        )
                    val heatMapIndex =
                        if (selectedViewMode == DiagnosticChartViewMode.MATRIX_EXPLORER) {
                            buildDiagnosticHeatMapIndex(sortedPartition.sequentialRuns)
                        } else {
                            null
                        }

                    groupCount to heatMapIndex
                }

            publishProgress(4, "Preparing ${selectedCategory.title} groups and axes.")

            val result =
                DiagnosticChartPreparedData(
                    sequentialRuns = sortedPartition.sequentialRuns,
                    oracleRuns = sortedPartition.oracleRuns,
                    // Keep the scientific source intact. Individual compact previews may select a
                    // peak-preserving level of detail, while inspectors and exports use every run.
                    displayRuns = sortedPartition.sequentialRuns,
                    finiteMetricCount = metricCounts.first,
                    nonFiniteMetricCount = metricCounts.second,
                    categoryGroupCount = categoryPreparation.first,
                    heatMapIndex = categoryPreparation.second
                )

            publishProgress(
                completedSteps = CHART_PREPARATION_STEPS.size,
                message = "${selectedCategory.title} is ready.",
                running = false
            )
            val completedTelemetry = performanceSamples.toList()
            withContext(Dispatchers.IO) {
                runCatching {
                    val session =
                        telemetryRepository.save(
                            projectName = activeProject.name,
                            sessionType = "Chart preparation",
                            title = "${selectedCategory.title} · ${selectedViewMode.title}",
                            samples = completedTelemetry
                        )
                    finalTelemetryFigureArchiver.archive(
                        projectId = activeProject.id,
                        session = session,
                        samples = completedTelemetry
                    )
                }
            }
            preparedData = result
            screenScrollState.scrollTo(0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publishProgress(
                completedSteps = loadingProgress.completedRuns,
                message = "Chart preparation failed: ${error.message ?: error::class.simpleName}",
                running = false,
                failed = true
            )
        } finally {
            telemetryTicker.cancel()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(screenScrollState)
            .padding(12.dp)
            .testTag("diagnostics-charts-root")
            .tutorialAnchor(TutorialTargets.DiagnosticsCharts),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Interactive diagnostic charts",
            style = MaterialTheme.typography.titleLarge
        )

        JargonHelpNotice()

        JargonAwareText(
            text = "Charts are read-only views of the completed diagnostic report. They do not rerun FK, IK, or benchmark logic. Residuals and final error show how far a solved position remains from its target; percentiles and outliers expose difficult cases that an average can hide. A logarithmic scale compares multiplicative changes when values span very different sizes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Diagnostics")
        }

        Text(
            text = "Analysis family",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CompactSelectionMenu(
            options = diagnosticAnalysisCategories,
            selected = selectedCategory,
            label = { category -> "${category.symbol} ${category.title}" },
            onSelected = { category ->
                selectedCategory = category
                if (!category.supportsMatrixExplorer()) {
                    selectedViewMode = DiagnosticChartViewMode.ALL_CHARTS
                }
            },
            modifier =
                Modifier
                    .testTag("diagnostics-chart-category")
                    .tutorialAnchor(TutorialTargets.DiagnosticsChartCategory),
            optionTestTag = { category ->
                "diagnostics-chart-category-${category.name.lowercase()}"
            }
        )

        JargonAwareText(
            text = selectedCategory.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (selectedCategory.supportsMatrixExplorer()) {
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics-matrix-view-selector")
                        .tutorialAnchor(TutorialTargets.DiagnosticsMatrix),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticChartViewMode.entries.forEach { viewMode ->
                    if (selectedViewMode == viewMode) {
                        FilledTonalButton(
                            onClick = { selectedViewMode = viewMode },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(viewMode.title)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedViewMode = viewMode },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(viewMode.title)
                        }
                    }
                }
            }

            JargonAwareText(
                text = selectedViewMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val currentPreparedData = preparedData

        if (currentPreparedData == null) {
            DiagnosticLoadingProgressCard(
                progressState = loadingProgress,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Building ${selectedCategory.title}",
                        finishedTitle = "${selectedCategory.title} Loading",
                        progressSectionTitle = "Chart Construction Progress",
                        completedLabel = "Completed preparation stages",
                        remainingLabel = "Stages left",
                        rateLabel = "Stages per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Chart preparation"
                    ),
                timeline = chartPreparationTimeline(loadingProgress)
            )
        } else {
            ProvideAutomaticFigureLibraryContext(
                collection = "diagnostics",
                analysisId = report.experimentName,
                executionId = figureExecutionId
            ) {
                CompositionLocalProvider(LocalChartCardsCollapsible provides true) {
                    DiagnosticChartPageRouter(
                        selectedCategory = selectedCategory,
                        selectedViewMode = selectedViewMode,
                        report = report,
                        preparedData = currentPreparedData
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticChartPageRouter(
    selectedCategory: DiagnosticChartCategory,
    selectedViewMode: DiagnosticChartViewMode,
    report: Layer1DiagnosticReport,
    preparedData: DiagnosticChartPreparedData
) {
    if (selectedViewMode == DiagnosticChartViewMode.MATRIX_EXPLORER) {
        HeatMapChartsPage(
            report = report,
            heatMapIndex = requireNotNull(preparedData.heatMapIndex),
            focusCategory = selectedCategory,
            sequentialRuns = preparedData.sequentialRuns
        )
        return
    }

    when (selectedCategory) {
        DiagnosticChartCategory.OVERVIEW ->
            OverviewChartsPage(report = report)

        DiagnosticChartCategory.LINK_COUNT_SCALING ->
            LinkCountChartsPage(report = report)

        DiagnosticChartCategory.SEED_SENSITIVITY ->
            SeedSensitivityChartsPage(report = report)

        DiagnosticChartCategory.TOPOLOGY ->
            TopologyChartsPage(report = report, sequentialRuns = preparedData.sequentialRuns)

        DiagnosticChartCategory.STATUS_FAILURE_CODES ->
            StatusFailureChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                displayRuns = preparedData.displayRuns
            )

        DiagnosticChartCategory.ERROR_DISTRIBUTION ->
            ErrorDistributionChartsPage(report = report, sequentialRuns = preparedData.sequentialRuns)

        DiagnosticChartCategory.ITERATIONS_CONVERGENCE ->
            IterationChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                displayRuns = preparedData.displayRuns
            )

        DiagnosticChartCategory.TRANSITIONS ->
            TransitionChartsPage(report = report, sequentialRuns = preparedData.sequentialRuns)

        DiagnosticChartCategory.JOINT_LIMITS ->
            JointLimitChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                displayRuns = preparedData.displayRuns
            )

        DiagnosticChartCategory.SEED_DISTANCE ->
            SeedDistanceChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                displayRuns = preparedData.displayRuns
            )

        DiagnosticChartCategory.PROGRESS_CLASSES ->
            ProgressClassChartsPage(report = report, sequentialRuns = preparedData.sequentialRuns)

        DiagnosticChartCategory.PER_CASE ->
            PerCaseChartsPage(report = report)

        DiagnosticChartCategory.CORRELATIONS ->
            CorrelationChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                displayRuns = preparedData.displayRuns
            )

        DiagnosticChartCategory.HEAT_MAPS ->
            OverviewChartsPage(report = report)

        DiagnosticChartCategory.SCIENTIFIC_REPORT ->
            ScientificReportChartsPage(report = report, sequentialRuns = preparedData.sequentialRuns)

        DiagnosticChartCategory.SOLVER_COMPARISON ->
            SolverComparisonChartsPage(report = report)

        DiagnosticChartCategory.EXPORT_BACKED_CHARTS ->
            ExportBackedChartsPage(
                report = report,
                sequentialRuns = preparedData.sequentialRuns,
                oracleRuns = preparedData.oracleRuns
            )
    }
}

private val CHART_PREPARATION_STEPS =
    listOf(
        "Read completed benchmark",
        "Separate run types",
        "Build run timeline",
        "Check numeric chart values",
        "Prepare chart groups and axes"
    )

private fun chartPreparationTimeline(
    progress: DiagnosticProgressState
): List<DiagnosticTimelineStep> {
    return CHART_PREPARATION_STEPS.mapIndexed { index, label ->
        val status =
            when {
                progress.phase == DiagnosticProgressPhase.FAILED && index == progress.completedRuns ->
                    DiagnosticTimelineStatus.FAILED
                index < progress.completedRuns -> DiagnosticTimelineStatus.COMPLETE
                progress.isRunning && index == progress.completedRuns -> DiagnosticTimelineStatus.RUNNING
                else -> DiagnosticTimelineStatus.PENDING
            }

        DiagnosticTimelineStep(
            label = label,
            status = status
        )
    }
}
