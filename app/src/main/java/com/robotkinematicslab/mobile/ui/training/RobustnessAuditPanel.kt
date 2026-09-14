package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.robotkinematicslab.mobile.ml.research.RobustnessEvidenceArchive
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.research.ModelRobustnessProgress
import com.robotkinematicslab.mobile.ml.research.StoredModelRobustnessEngine
import com.robotkinematicslab.mobile.ml.research.StoredModelRobustnessResult
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
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
internal fun RobustnessAuditPanel(
    runs: List<TrainingRunSummary>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val repository = remember(context) { TrainingStorageRepository(context) }
    val engine = remember(repository) { StoredModelRobustnessEngine(repository) }
    val sampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val cancellation = remember { AtomicBoolean(false) }
    val eligible = remember(runs) { runs.filter { it.modelPaths.isNotEmpty() } }
    var selectedRunId by rememberSaveable { mutableStateOf(eligible.firstOrNull()?.runId) }
    var selectedModelPath by rememberSaveable { mutableStateOf(eligible.firstOrNull()?.modelPaths?.firstOrNull()) }
    var result by remember { mutableStateOf<StoredModelRobustnessResult?>(null) }
    var progress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    val telemetry = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("Select one stored model for deterministic perturbation testing.") }
    var startedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(selectedRunId,selectedModelPath) {
        result=null
        val selected=eligible.firstOrNull { it.runId==selectedRunId }
        val path=selectedModelPath
        if(selected!=null && path!=null && activeJob==null) {
            val restored=withContext(Dispatchers.IO) { runCatching { RobustnessEvidenceArchive.latest(File(selected.directoryPath),selected.runId,File(path)) } }
            restored.onSuccess { report -> result=report; if(report!=null) message="Original saved robustness audit reopened; no recomputation." }.onFailure { message=it.message ?: "Saved audit could not be verified." }
        }
    }

    fun start() {
        if (processCoordinator.isActive(ResearchProcessIds.ROBUSTNESS_AUDIT)) {
            message = "A robustness audit is already active. Open Research activity to inspect it."
            return
        }
        val run = eligible.firstOrNull { it.runId == selectedRunId } ?: return
        val modelPath = selectedModelPath ?: return
        cancellation.set(false)
        result = null
        progress = null
        telemetry.clear()
        telemetryCaptureGate.reset()
        startedAt = System.currentTimeMillis()
        message = "Rebuilding the untouched test split…"
        activeJob = processCoordinator.launch(
            id = ResearchProcessIds.ROBUSTNESS_AUDIT,
            title = "Model robustness audit",
            kind = ResearchProcessKind.ANALYSIS,
            cancellationAction = { cancellation.set(true) }
        ) { process ->
            process.report(0.0, "Rebuilding held-out split", message)
            try {
                val analysis = withContext(Dispatchers.IO) {
                    engine.analyze(
                        run = run,
                        modelPath = modelPath,
                        cancellationRequested = cancellation::get,
                        onProgress = { update ->
                            process.report(
                                progressFraction =
                                    if (update.total > 0) update.completed.toDouble() / update.total.toDouble() else null,
                                stage = "Perturbation audit · ${update.completed}/${update.total}",
                                detail = update.message
                            )
                            if (
                                telemetryCaptureGate.shouldCapture(
                                    force = update.completed == 0 || update.completed >= update.total
                                )
                            ) {
                                val state = robustnessProgress(update, startedAt, sampler, running = true)
                                scope.launch {
                                    progress = state
                                    telemetry += DiagnosticPerformanceSample.fromProgressState(telemetry.size + 1, state)
                                }
                            }
                        }
                    )
                }
                withContext(Dispatchers.IO) { RobustnessEvidenceArchive.save(File(run.directoryPath),analysis) }
                result = analysis
                message = "Numerical robustness audit completed."
                process.completed(message)
                val last = progress
                if (last != null) {
                    progress = last.copy(isRunning = false, phase = DiagnosticProgressPhase.COMPLETED, message = message)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                process.cancelled("Robustness audit was cancelled safely.")
            } catch (error: Exception) {
                message = if (cancellation.get()) "Robustness audit cancelled safely." else error.message ?: "Robustness audit failed."
                progress = progress?.copy(isRunning=false,phase=DiagnosticProgressPhase.FAILED,message=message)
                if (cancellation.get()) process.cancelled(message) else process.failed(message)
            } finally {
                activeJob = null
            }
        }.getOrElse { error ->
            message = error.message ?: "Robustness audit could not start."
            null
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        JargonHelpNotice()
        TrainingDisclosureSection(
            title = "Stored-model perturbation audit",
            subtitle =
                "Applies deterministic ±σ changes only to the original held-out features. " +
                    "This measures numerical model sensitivity, not physical sensor noise."
        ) {
            JargonAwareText(
                "Here σ means one standard deviation of a feature. Each test nudges normalized held-out inputs by a controlled amount, then measures sensitivity against the unchanged prediction; it does not simulate a real sensor unit.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (eligible.isEmpty()) {
                Text("No stored model is available.")
            } else {
                ExpandableSelectionCollection(
                    items = eligible,
                    initialVisibleCount = 8,
                    itemName = "model runs",
                    isSelected = { it.runId == selectedRunId },
                    toggleTestTag = "robustness-runs-show-all"
                ) { run ->
                    RobustnessChoice(run.runId == selectedRunId, run.runName, activeJob == null) {
                        selectedRunId = run.runId
                        selectedModelPath = run.modelPaths.firstOrNull()
                        result = null
                    }
                }
                val selected = eligible.firstOrNull { it.runId == selectedRunId }
                selected?.modelPaths?.forEach { path ->
                    RobustnessChoice(path == selectedModelPath, ScientificEntityNameResolver.model(path).primary, activeJob == null) {
                        selectedModelPath = path
                        result = null
                    }
                }
            }
        }
        if (eligible.isNotEmpty()) {
            if (activeJob == null) {
                Button(onClick = ::start, modifier = Modifier.fillMaxWidth()) { Text("Run held-out robustness audit") }
            } else {
                OutlinedButton(
                    onClick = {
                        processCoordinator.requestCancel(ResearchProcessIds.ROBUSTNESS_AUDIT)
                        message = "Cancellation requested…"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel robustness audit") }
            }
        }
        Text(message, modifier = Modifier.politeLiveRegion().testTag("training-robustness-status"))

        progress?.let { state ->
            DiagnosticLoadingProgressCard(
                progressState = state,
                performanceSamples = telemetry,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Robustness Audit Running",
                        finishedTitle = "Robustness Audit Evidence",
                        progressSectionTitle = "Perturbation campaign",
                        completedLabel = "Evaluations complete",
                        remainingLabel = "Evaluations left",
                        rateLabel = "Evaluations per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Model robustness"
                    ),
                timeline = robustnessTimeline(state.isRunning, state.phase == DiagnosticProgressPhase.COMPLETED)
            )
        }
        result?.let { report ->
            ProvideAutomaticFigureLibraryContext(
                collection = "model-robustness",
                analysisId = report.runId,
                executionId = "robustness-${report.modelSha256}-${report.slices.hashCode()}"
            ) {
                TrainingDisclosureSection(
                    title = "Robustness contract",
                    subtitle = "Zero perturbation checks numerical equivalence on the sampled original test rows.",
                    initiallyExpanded = true
                ) {
                    JargonAwareText(
                        "Zero perturbation compares identical normalized inputs in the paired audit. The audit uses evenly selected original test rows (up to the configured cap); its subset score need not equal the full independent-test score. No physical sensor distribution is simulated.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RobustnessReport(report)
                }
            }
        }
    }
}

@Composable
private fun RobustnessReport(result: StoredModelRobustnessResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartMetricRow("Model", result.modelName)
        ChartMetricRow("Variables", result.featureCount.toString())
        ChartMetricRow("Untouched test rows", result.heldOutSampleCount.toString())
        ChartMetricRow("Perturbation unit", result.perturbationUnit)
        Text("Deterministic ±σ changes are applied in normalized model-input space and clipped to [-8,8]. This is numerical feature sensitivity, not a calibrated physical sensor-noise or universal safety test. Truth-loss is 1 minus probability assigned to the true class.")
        TrainingDisclosureSection(title="Audit provenance",subtitle="Original run, model bytes, corpus and sampled rows.") {
            Text("Run ${result.runId}; model SHA-256 ${result.modelSha256}; corpus SHA-256 ${result.corpusSha256}")
            Text("Original sampled CSV row IDs: ${result.sampleRowIds.joinToString()}")
        }
    }
    ProfessionalLineChart(
        title = "Perturbation magnitude versus accuracy drop",
        subtitle = "A flat curve is stable. Positive values mean held-out accuracy deteriorated.",
        points = result.slices.map { ChartLinePoint(it.magnitude, it.accuracyDrop) },
        xAxisLabel = "Deterministic perturbation (σ)",
        yAxisLabel = "Accuracy drop",
        color = Color(0xFFC62828),
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )
    ProfessionalLineChart(
        title = "Perturbation magnitude versus truth-loss increase",
        subtitle = "Shows confidence degradation even before a class prediction flips.",
        points = result.slices.map { ChartLinePoint(it.magnitude, it.meanLossIncrease) },
        xAxisLabel = "Deterministic perturbation (σ)",
        yAxisLabel = "Mean probability-loss increase",
        color = Color(0xFFF57C00),
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )
    ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
        title = "New failures introduced by perturbation",
        subtitle = "Cases that were correct and became incorrect, divided by ALL audited test samples at that magnitude (not only originally correct cases).",
        items = result.slices.map { slice ->
            ChartBarItem(
                "${decimal(slice.magnitude)}σ · ${percent(slice.failureIntroductionRate)} · n=${slice.sampleCount}",
                (slice.failureIntroductionRate * 10_000.0).toInt(),
                Color(0xFF1565C0)
            )
        },
        xAxisLabel = "Introduced-failure rate (%)"
    )
}

@Composable
private fun RobustnessChoice(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun robustnessProgress(
    update: ModelRobustnessProgress,
    startedAt: Long,
    sampler: DiagnosticSystemTelemetrySampler,
    running: Boolean
): DiagnosticProgressState {
    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1_000.0
    val rate = if (elapsed > 0.0) update.completed / elapsed else 0.0
    val remaining = (update.total - update.completed).coerceAtLeast(0)
    return DiagnosticProgressState(
        isRunning = running,
        phase = if (running) DiagnosticProgressPhase.AGGREGATING else DiagnosticProgressPhase.COMPLETED,
        completedRuns = update.completed,
        totalRuns = update.total.coerceAtLeast(update.completed).coerceAtLeast(1),
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsed,
        message = update.message,
        telemetry = sampler.sample()
    )
}

private fun robustnessTimeline(running: Boolean, completed: Boolean): List<DiagnosticTimelineStep> =
    listOf(
        DiagnosticTimelineStep("Reload exact model contract", if (running) DiagnosticTimelineStatus.RUNNING else if(completed) DiagnosticTimelineStatus.COMPLETE else DiagnosticTimelineStatus.PENDING),
        DiagnosticTimelineStep("Rebuild untouched split", if (completed) DiagnosticTimelineStatus.COMPLETE else DiagnosticTimelineStatus.PENDING),
        DiagnosticTimelineStep("Apply deterministic σ perturbations", if (completed) DiagnosticTimelineStatus.COMPLETE else DiagnosticTimelineStatus.PENDING),
        DiagnosticTimelineStep("Aggregate paired degradation", if (completed) DiagnosticTimelineStatus.COMPLETE else DiagnosticTimelineStatus.PENDING)
    )

private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)
private fun percent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)
