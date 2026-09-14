package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.research.CoverageObservation
import com.robotkinematicslab.mobile.ml.research.DatasetCoverageAnalyzer
import com.robotkinematicslab.mobile.ml.research.DatasetCoverageSummary
import com.robotkinematicslab.mobile.ml.research.DatasetResidualCsvReader
import com.robotkinematicslab.mobile.ml.research.DatasetResidualEvidence
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ScatterLegendItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DatasetQualityPanel(
    manifests: List<DatasetManifest>,
    continuousState: ContinuousDatasetState,
    onOpenContinuous: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val reader = remember { ScientificDatasetTrainingReader() }
    val preparer = remember { TrainingDatasetPreparer() }
    val sampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val cancellation = remember { AtomicBoolean(false) }
    var selectedPath by remember(manifests) { mutableStateOf(manifests.firstOrNull()?.csvPath) }
    var profile by remember { mutableStateOf(TrainingFeatureProfile.CONTEXT_EXPANDED) }
    var result by remember { mutableStateOf<DatasetQualityAnalysis?>(null) }
    var splitStrategy by remember { mutableStateOf<TrainingSplitStrategy?>(null) }
    var message by remember { mutableStateOf("Choose a saved dataset and calculate its training-to-test coverage.") }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    var coveragePhase by remember { mutableStateOf(CoverageAuditPhase.VALIDATE) }
    val performanceSamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    val selectedManifest = manifests.firstOrNull { it.csvPath == selectedPath }
    val selectedDatasetIsGrowing =
        continuousState.isActive &&
            selectedManifest?.datasetName == continuousState.plan?.datasetName

    fun analyze() {
        if (processCoordinator.isActive(ResearchProcessIds.DATASET_QUALITY_AUDIT)) {
            message = "A dataset quality audit is already active. Open Research activity to inspect it."
            return
        }
        val manifest = manifests.firstOrNull { it.csvPath == selectedPath } ?: return
        val totalDatasetRows = manifest.rowCount.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        cancellation.set(false)
        result = null
        splitStrategy = null
        progress = null
        coveragePhase = CoverageAuditPhase.VALIDATE
        performanceSamples.clear()
        telemetryCaptureGate.reset()
        startedAt = System.currentTimeMillis()
        message = "Reading and validating coverage evidence…"
        activeJob = processCoordinator.launch(
            id = ResearchProcessIds.DATASET_QUALITY_AUDIT,
            title = "Dataset quality · ${manifest.datasetName}",
            kind = ResearchProcessKind.ANALYSIS,
            cancellationAction = { cancellation.set(true) }
        ) { process ->
            process.report(0.0, "Validating dataset", message)
            try {
                val analysis = withContext(Dispatchers.IO) {
                    val load = reader.load(
                        file = File(manifest.csvPath),
                        profile = profile,
                        maximumRows = 5_000,
                        cancellationRequested = cancellation::get,
                        sampleAcrossEntireFile = true,
                        samplingSeed = manifest.randomSeed,
                        expectedDataRowCount = manifest.rowCount,
                        requireEveryRowValid = true,
                        onProgress = { update ->
                            process.report(
                                progressFraction = update.rowsRead.toDouble() / totalDatasetRows.toDouble(),
                                stage = "Reading coverage evidence",
                                detail = update.message
                            )
                            if (
                                telemetryCaptureGate.shouldCapture(
                                    force = update.rowsRead == 0 || update.rowsRead >= totalDatasetRows
                                )
                            ) {
                                val state = coverageProgress(
                                    running = true,
                                    completed = update.rowsRead.coerceAtMost(totalDatasetRows),
                                    total = totalDatasetRows,
                                    message = update.message,
                                    startedAt = startedAt,
                                    sampler = sampler
                                )
                                scope.launch {
                                    if (!cancellation.get()) {
                                        progress = state
                                        performanceSamples += DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = performanceSamples.size + 1,
                                            progressState = state
                                        )
                                    }
                                }
                            }
                        }
                    )
                    ensureDatasetQualityAuditActive(cancellation::get)
                    val dataset = requireNotNull(load.dataset) { load.errorMessage ?: "Dataset could not be read." }
                    scope.launch {
                        if (!cancellation.get()) {
                            coveragePhase = CoverageAuditPhase.PARTITION
                            progress = coverageProgress(
                                running = true,
                                completed = totalDatasetRows,
                                total = totalDatasetRows,
                                message = "Building an independent held-out partition.",
                                startedAt = startedAt,
                                sampler = sampler
                            )
                        }
                    }
                    process.report(0.76, "Building held-out partition", "Separating reference and untouched evaluation rows.")
                    ensureDatasetQualityAuditActive(cancellation::get)
                    val selectedSplit =
                        runCatching {
                            preparer.split(dataset.samples, manifest.randomSeed, TrainingSplitStrategy.ROBOT_HELD_OUT)
                        }.getOrElse {
                            preparer.split(dataset.samples, manifest.randomSeed, TrainingSplitStrategy.SAMPLE_GROUPED)
                        }
                    ensureDatasetQualityAuditActive(cancellation::get)
                    val reference = selectedSplit.trainIndices.map { index -> dataset.samples[index].toCoverage(index) }
                    val evaluation = selectedSplit.testIndices.map { index -> dataset.samples[index].toCoverage(index) }
                    require(reference.isNotEmpty() && evaluation.isNotEmpty()) {
                        "The selected dataset cannot form independent coverage partitions."
                    }
                    scope.launch {
                        if (!cancellation.get()) coveragePhase = CoverageAuditPhase.GEOMETRY
                    }
                    process.report(0.84, "Measuring coverage geometry", "Calculating reference-to-evaluation distances.")
                    ensureDatasetQualityAuditActive(cancellation::get)
                    val coverage = DatasetCoverageAnalyzer.analyze(
                        reference = reference,
                        evaluation = evaluation,
                        maximumReferencePoints = 512
                    )
                    ensureDatasetQualityAuditActive(cancellation::get)
                    scope.launch {
                        if (!cancellation.get()) coveragePhase = CoverageAuditPhase.MEASURE
                    }
                    process.report(0.92, "Reading residual evidence", "Measuring solver residuals across the saved dataset.")
                    val residual = DatasetResidualCsvReader.read(
                        file = File(manifest.csvPath),
                        maximumRows = 5_000,
                        samplingSeed = manifest.randomSeed,
                        expectedDataRowCount = manifest.rowCount,
                        cancellationRequested = cancellation::get
                    )
                    ensureDatasetQualityAuditActive(cancellation::get)
                    DatasetQualityAnalysis(coverage, residual) to selectedSplit.strategy
                }
                ensureDatasetQualityAuditActive(cancellation::get)
                result = analysis.first
                splitStrategy = analysis.second
                coveragePhase = CoverageAuditPhase.COMPLETE
                message = "Coverage analysis complete on untouched test rows."
                val state = coverageProgress(
                    running = false,
                    completed = totalDatasetRows,
                    total = totalDatasetRows,
                    message = message,
                    startedAt = startedAt,
                    sampler = sampler
                )
                progress = state
                performanceSamples += DiagnosticPerformanceSample.fromProgressState(
                    sampleIndex = performanceSamples.size + 1,
                    progressState = state
                )
                process.completed(message)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                message = "Coverage analysis cancelled safely."
                progress = null
                performanceSamples.clear()
                process.cancelled(message)
            } catch (error: Exception) {
                message = if (cancellation.get()) "Coverage analysis cancelled safely." else error.message ?: "Coverage analysis failed."
                progress = null
                performanceSamples.clear()
                if (cancellation.get()) process.cancelled(message) else process.failed(message)
            } finally {
                activeJob = null
            }
        }.getOrElse { error ->
            message = error.message ?: "Dataset coverage analysis could not start."
            null
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .tutorialAnchor(TutorialTargets.DatasetQuality),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ContinuousDatasetQualitySummary(
            state = continuousState,
            onOpen = onOpenContinuous
        )

        ChartSectionCard(
            title = "Dataset coverage and OOD audit",
            subtitle =
                "Fit the geometry of the reference partition only, then measure how far untouched test rows lie outside it. " +
                    "This checks coverage; it does not claim that distance alone proves physical invalidity."
        ) {
            if (manifests.isEmpty()) {
                Text("No saved dataset exists yet.")
            } else {
                DatasetDisclosureSection(
                    title = "Saved dataset",
                    summary =
                        selectedManifest?.let { manifest ->
                            "${manifest.datasetName} · ${manifest.rowCount} rows · ${manifest.robotIds.size} robot(s)"
                        } ?: "Choose one saved dataset",
                    initiallyExpanded = selectedManifest == null,
                    testTag = "dataset-quality-dataset-selection"
                ) {
                    manifests.take(12).forEachIndexed { index, manifest ->
                        QualityChoice(
                            selected = manifest.csvPath == selectedPath,
                            label = "${manifest.datasetName} · ${manifest.rowCount} rows · ${manifest.robotIds.size} ${if (manifest.robotIds.size == 1) "robot" else "robots"}",
                            enabled = activeJob == null,
                            onClick = {
                                selectedPath = manifest.csvPath
                                result = null
                            },
                            modifier =
                                if (index == 0) {
                                    Modifier.tutorialAnchor(TutorialTargets.DatasetQualityDataset)
                                } else {
                                    Modifier
                                }
                        )
                    }
                }

                DatasetDisclosureSection(
                    title = "Feature contract",
                    summary = qualityProfileLabel(profile),
                    testTag = "dataset-quality-feature-contract"
                ) {
                    TrainingFeatureProfile.entries.chunked(2).forEachIndexed { rowIndex, profileRow ->
                        Row(
                            modifier =
                                if (rowIndex == 0) {
                                    Modifier
                                        .fillMaxWidth()
                                        .tutorialAnchor(TutorialTargets.DatasetQualityProfile)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            profileRow.forEach { item ->
                                QualityChoice(
                                    selected = item == profile,
                                    label = qualityProfileLabel(item),
                                    enabled = activeJob == null,
                                    onClick = {
                                        profile = item
                                        result = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (profileRow.size == 1) {
                                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (activeJob == null) {
                    Button(
                        onClick = ::analyze,
                        enabled = !selectedDatasetIsGrowing,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .tutorialAnchor(TutorialTargets.DatasetQualityCalculate)
                    ) {
                        Text("Calculate held-out coverage")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            processCoordinator.requestCancel(ResearchProcessIds.DATASET_QUALITY_AUDIT)
                            message = "Cancellation requested…"
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .tutorialAnchor(TutorialTargets.DatasetQualityCancel)
                    ) { Text("Cancel coverage analysis") }
                }
                if (selectedDatasetIsGrowing) {
                    JargonAwareText(
                        "Pause continuous growth before auditing this dataset so the held-out snapshot and manifest cannot change during analysis.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            JargonAwareText(message)
        }

        progress?.let { state ->
            val progressContent: @Composable () -> Unit = {
                DiagnosticLoadingProgressCard(
                    progressState = state,
                    performanceSamples = performanceSamples,
                    config =
                        DiagnosticLoadingCardConfig(
                            runningTitle = "Dataset Coverage Analysis",
                            finishedTitle = "Dataset Coverage Evidence",
                            progressSectionTitle = "Coverage calculation",
                            completedLabel = "Rows processed",
                            remainingLabel = "Rows left",
                            rateLabel = "Rows per second",
                            showBenchmarkCoordinates = false,
                            telemetrySessionType = "Dataset coverage"
                        ),
                    timeline = coverageTimeline(coveragePhase)
                )
            }
            if (activeJob == null) {
                DatasetDisclosureSection(
                    title = "Completed analysis telemetry",
                    summary = "Rows processed, timing and device resource history",
                    testTag = "dataset-quality-completed-telemetry",
                    content = progressContent
                )
            } else {
                progressContent()
            }
        }

        result?.let { analysis ->
            DatasetCoverageReport(
                analysis = analysis,
                splitStrategy = splitStrategy,
                analysisId = selectedManifest?.datasetName ?: "latest-dataset",
                executionId = "coverage-$startedAt"
            )
        }
    }
}

private fun qualityProfileLabel(profile: TrainingFeatureProfile): String = when (profile) {
    TrainingFeatureProfile.BASELINE_KINEMATICS -> "Baseline · 108"
    TrainingFeatureProfile.CONTEXT_ENHANCED -> "Context · 130"
    TrainingFeatureProfile.CONTEXT_EXPANDED -> "Expanded · 383"
    TrainingFeatureProfile.CONTEXT_RESEARCH_V2 -> "Research v2 · 468"
}

@Composable
private fun DatasetCoverageReport(
    analysis: DatasetQualityAnalysis,
    splitStrategy: TrainingSplitStrategy?,
    analysisId: String,
    executionId: String
) {
    val coverage = analysis.coverage
    val residual = analysis.residual
    ProvideAutomaticFigureLibraryContext(
        collection = "dataset-quality",
        analysisId = analysisId,
        executionId = executionId
    ) {
        Column(
            modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetQualityResult),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        ChartSectionCard(
            title = "Quality summary",
            subtitle = "The headline coverage and solver signals stay visible; expand the evidence you want to inspect."
        ) {
            ChartMetricRow("OOD rate", percent(coverage.outOfDistributionRate))
            ChartMetricRow("Accepted rate", percent(residual.acceptedRate))
            ChartMetricRow("Untouched test rows", coverage.evaluationCount.toString())
            ChartMetricRow("Rows audited", residual.sampleCount.toString())
        }

        DatasetDisclosureSection(
            title = "Coverage metrics",
            summary = "Partition, feature-space distance and marginal range evidence",
            testTag = "dataset-quality-coverage-metrics"
        ) {
            ChartMetricRow("Partition strategy", splitStrategy?.displayName ?: "Unknown")
            ChartMetricRow("Reference rows available", coverage.referenceCount.toString())
            ChartMetricRow("Reference rows compared", coverage.comparedReferenceCount.toString())
            ChartMetricRow("Untouched test rows", coverage.evaluationCount.toString())
            ChartMetricRow("Reference / held-out robot groups", "${coverage.referenceGroupCount} / ${coverage.evaluationGroupCount}")
            ChartMetricRow("Feature dimensions", coverage.featureCount.toString())
            ChartMetricRow("Constant dimensions", coverage.constantFeatureCount.toString())
            ChartMetricRow("Reference-calibrated OOD threshold", decimal(coverage.calibratedDistanceThreshold))
            ChartMetricRow("Mean nearest distance", decimal(coverage.meanNearestDistance))
            ChartMetricRow("P95 nearest distance", decimal(coverage.p95NearestDistance))
            ChartMetricRow("OOD rate", percent(coverage.outOfDistributionRate))
            ChartMetricRow("Any marginal range escape", percent(coverage.rangeEscapeRate))
            ChartMetricRow("Mean dimensions outside range", percent(coverage.meanOutsideTrainingRangeFraction))
        }

        val orderedDistances = coverage.points.map { it.normalizedNearestReferenceDistance }.sorted()
        val byGroup = coverage.points.groupBy { it.groupId }.entries.sortedByDescending { entry ->
            entry.value.count { it.outOfDistribution }.toDouble() / entry.value.size
        }
        DatasetDisclosureSection(
            title = "Coverage charts",
            summary = "Nearest-neighbour distance, range escape and OOD rate by robot",
            initiallyExpanded = true,
            testTag = "dataset-quality-coverage-charts"
        ) {
            ProfessionalLineChart(
                title = "Held-out nearest-neighbour distance",
                subtitle = "The steep right tail contains the least represented test configurations.",
                points = orderedDistances.mapIndexed { index, distance ->
                    ChartLinePoint((index + 1.0) / orderedDistances.size, distance)
                },
                xAxisLabel = "Empirical test quantile",
                yAxisLabel = "Normalized nearest distance",
                color = Color(0xFF6A1B9A)
            )

            ProfessionalScatterChart(
                title = "Coverage distance versus range escape",
                subtitle = "Red points exceed the reference-calibrated distance. Marginal range escape remains descriptive evidence.",
                points = coverage.points.map { point ->
                    ChartPoint(
                        x = point.normalizedNearestReferenceDistance,
                        y = point.outsideTrainingRangeFraction,
                        color = if (point.outOfDistribution) Color(0xFFC62828) else Color(0xFF2E7D32),
                        label = "row ${point.sampleId} · ${point.groupId} · OOD ${point.outOfDistribution}"
                    )
                },
                xAxisLabel = "Normalized nearest distance",
                yAxisLabel = "Fraction outside training ranges",
                legendItems =
                    listOf(
                        ScatterLegendItem("Covered", "Within threshold", Color(0xFF2E7D32)),
                        ScatterLegendItem("OOD", "Inspect or expand data", Color(0xFFC62828))
                    ),
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )

            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title = "OOD rate by robot",
                subtitle = "Groups with small support remain labelled with their exact test-row count.",
                items = byGroup.take(20).map { (group, points) ->
                    val rate = points.count { it.outOfDistribution }.toDouble() / points.size
                    ChartBarItem("$group · ${percent(rate)} · n=${points.size}", (rate * 10_000.0).toInt(), coverageColor(rate))
                },
                xAxisLabel = "OOD rate (%)"
            )
        }

        DatasetDisclosureSection(
            title = "Solver residual metrics",
            summary = "Acceptance, residual percentiles and cumulative error reduction",
            testTag = "dataset-quality-residual-metrics"
        ) {
            JargonAwareText(
                "Initial and final Cartesian residuals come from a uniform sample of the saved CSV; non-finite failures remain counted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChartMetricRow("Rows audited", residual.sampleCount.toString())
            ChartMetricRow("Accepted rate", percent(residual.acceptedRate))
            ChartMetricRow("Non-finite final residual", percent(residual.nonFiniteResidualRate))
            ChartMetricRow("Median initial error", distance(residual.medianInitialErrorMeters))
            ChartMetricRow("Median final error", distance(residual.medianFinalErrorMeters))
            ChartMetricRow("P95 / P99 final error", "${distance(residual.p95FinalErrorMeters)} / ${distance(residual.p99FinalErrorMeters)}")
            ChartMetricRow("Cumulative initial error", distance(residual.cumulativeInitialErrorMeters))
            ChartMetricRow("Cumulative finite final error", distance(residual.cumulativeFiniteFinalErrorMeters))
            ChartMetricRow("Finite-residual reduction", percent(residual.finiteResidualReductionFraction))
        }

        DatasetDisclosureSection(
            title = "Solver residual charts",
            summary = "Initial-to-final residuals and solver acceptance by robot",
            initiallyExpanded = true,
            testTag = "dataset-quality-residual-charts"
        ) {
            ProfessionalScatterChart(
                title = "Initial versus final Cartesian residual",
                subtitle = "Points below the diagonal improved. Red points were not accepted by the deterministic solver.",
                points = residual.observations.mapNotNull { row ->
                    row.finalErrorMeters?.let { final ->
                        ChartPoint(
                            x = row.initialErrorMeters,
                            y = final,
                            color = if (row.accepted) Color(0xFF2E7D32) else Color(0xFFC62828),
                            label = "row ${row.sampleId} · ${row.robotId} · accepted ${row.accepted}"
                        )
                    }
                },
                xAxisLabel = "Initial error (m)",
                yAxisLabel = "Final error (m)",
                legendItems =
                    listOf(
                        ScatterLegendItem("Accepted", "Finite certified result", Color(0xFF2E7D32)),
                        ScatterLegendItem("Rejected", "Finite but not accepted", Color(0xFFC62828))
                    ),
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )

            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title = "Solver acceptance by robot",
                subtitle = "A low bar can reveal topology-specific difficulty or a poorly covered target policy.",
                items = residual.byRobot.take(20).map { slice ->
                    ChartBarItem(
                        "${slice.robotId} · ${percent(slice.acceptedRate)} · n=${slice.sampleCount}",
                        (slice.acceptedRate * 10_000.0).toInt(),
                        if (slice.acceptedRate >= 0.90) Color(0xFF2E7D32) else Color(0xFFF57C00)
                    )
                },
                xAxisLabel = "Accepted rate (%)"
            )
        }
    }
}
}

private data class DatasetQualityAnalysis(
    val coverage: DatasetCoverageSummary,
    val residual: DatasetResidualEvidence
)

private fun com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample.toCoverage(index: Int) =
    CoverageObservation(
        sampleId = sourceRowIndex.takeIf { it >= 0L } ?: index.toLong(),
        groupId = robotId.ifBlank { topologyKey.ifBlank { "Unknown" } },
        features = DoubleArray(features.size) { features[it].toDouble() }
    )

@Composable
private fun QualityChoice(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
}

private fun coverageProgress(
    running: Boolean,
    completed: Int,
    total: Int,
    message: String,
    startedAt: Long,
    sampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1_000.0
    val safeTotal = total.coerceAtLeast(completed).coerceAtLeast(1)
    val rate = if (elapsed > 0.0) completed / elapsed else 0.0
    return DiagnosticProgressState(
        isRunning = running,
        phase = if (running) DiagnosticProgressPhase.AGGREGATING else DiagnosticProgressPhase.COMPLETED,
        completedRuns = completed,
        totalRuns = safeTotal,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (running && rate > 0.0) (safeTotal - completed).coerceAtLeast(0) / rate else 0.0,
        elapsedSeconds = elapsed,
        message = message,
        telemetry = sampler.sample()
    )
}

private enum class CoverageAuditPhase { VALIDATE, PARTITION, GEOMETRY, MEASURE, COMPLETE }

internal fun ensureDatasetQualityAuditActive(cancellationRequested: () -> Boolean) {
    if (cancellationRequested()) {
        throw kotlinx.coroutines.CancellationException("Dataset coverage analysis was cancelled.")
    }
}

private fun coverageTimeline(phase: CoverageAuditPhase): List<DiagnosticTimelineStep> {
    val labels = listOf(
        "Validate scientific CSV",
        "Build independent partition",
        "Fit reference-only geometry",
        "Measure untouched rows"
    )
    return labels.mapIndexed { index, label ->
        val status = when {
            phase == CoverageAuditPhase.COMPLETE || index < phase.ordinal -> DiagnosticTimelineStatus.COMPLETE
            index == phase.ordinal -> DiagnosticTimelineStatus.RUNNING
            else -> DiagnosticTimelineStatus.PENDING
        }
        DiagnosticTimelineStep(label, status)
    }
}

private fun coverageColor(rate: Double): Color =
    when {
        rate <= 0.02 -> Color(0xFF2E7D32)
        rate <= 0.10 -> Color(0xFF7CB342)
        rate <= 0.25 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

private fun decimal(value: Double): String = String.format(Locale.US, "%.5f", value)
private fun percent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)

private fun distance(metres: Double): String =
    when {
        !metres.isFinite() -> "N/A"
        metres < 1e-3 -> String.format(Locale.US, "%.3f µm", metres * 1e6)
        metres < 1.0 -> String.format(Locale.US, "%.3f mm", metres * 1e3)
        else -> String.format(Locale.US, "%.4f m", metres)
    }
