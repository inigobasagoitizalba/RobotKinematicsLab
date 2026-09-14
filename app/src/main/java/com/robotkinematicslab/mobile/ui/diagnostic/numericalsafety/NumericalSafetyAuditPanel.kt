package com.robotkinematicslab.mobile.ui.diagnostic.numericalsafety

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyBranchSummary
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyExperiment
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyExperimentConfig
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyImpact
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyProgress
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyReport
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetySavedSession
import com.robotkinematicslab.mobile.diagnostics.numericalsafety.NumericalSafetyStorageRepository
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun NumericalSafetyAuditPanel(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val cancellation = remember { AtomicBoolean(false) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val telemetryIndex = remember { AtomicInteger(0) }
    val storage = remember(context) { NumericalSafetyStorageRepository(context) }
    val telemetrySamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    var linkCountsText by remember { mutableStateOf("3, 5, 10") }
    var trialsText by remember { mutableStateOf("8") }
    var seedText by remember { mutableStateOf("42") }
    var toleranceText by remember { mutableStateOf("0.000001") }
    var maxIterationsText by remember { mutableStateOf("800") }
    var dampingText by remember { mutableStateOf("0.01") }
    var maxStepText by remember { mutableStateOf("0.02") }
    var allJointModes by remember { mutableStateOf(true) }
    var selectedJointMode by remember { mutableStateOf(DiagnosticJointMode.MIXED) }
    var includeReference by remember { mutableStateOf(true) }
    var includeFaults by remember { mutableStateOf(true) }
    var carryState by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    var report by remember { mutableStateOf<NumericalSafetyReport?>(null) }
    var saved by remember { mutableStateOf<NumericalSafetySavedSession?>(null) }
    var message by remember { mutableStateOf("Ready. The unguarded branch is isolated from every production path.") }
    var showRawRows by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("numerical-safety-root")
            .tutorialAnchor(TutorialTargets.NumericalSafety),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = {
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to diagnostic benchmark")
        }

        Text("Mathematical Safety A/B Lab", style = MaterialTheme.typography.titleLarge)
        JargonHelpNotice()
        JargonAwareText(
            "Matched robot, target, seed and solver settings are sent through the protected production IK and an isolated unguarded reference. " +
                "Natural incidence and injected faults are reported separately.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scientific negative control", fontWeight = FontWeight.Bold)
                JargonAwareText(
                    "Turning this on does not weaken the app. The unsafe code exists only inside this laboratory, its exceptions are contained, " +
                        "and its outputs can never enter Robot Lab, dataset generation, training or inference.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        ChartSectionCard(
            title = "Experiment design",
            subtitle = "Quick preset: 3, 5 and 10 links × all joint modes × 8 valid targets, plus separate adversarial probes.",
            modifier =
                Modifier
                    .testTag("numerical-safety-configuration")
                    .tutorialAnchor(TutorialTargets.NumericalSafetyConfiguration)
        ) {
            OutlinedTextField(
                value = linkCountsText,
                onValueChange = { linkCountsText = it.filter { character -> character.isDigit() || character == ',' || character == ' ' } },
                label = { Text("Link counts, comma separated") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            OutlinedTextField(
                value = trialsText,
                onValueChange = { trialsText = it.filter(Char::isDigit) },
                label = { Text("Valid targets per topology") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it.filter { character -> character.isDigit() || character == '-' } },
                label = { Text("Deterministic seed") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )

            LabeledSwitch(
                label = "Run every joint topology",
                description = "Auto, revolute-only, prismatic-only and mixed.",
                checked = allJointModes,
                onCheckedChange = { allJointModes = it },
                enabled = progress?.isRunning != true
            )
            if (!allJointModes) {
                CompactSelectionMenu(
                    options = DiagnosticJointMode.entries,
                    selected = selectedJointMode,
                    label = { it.readableName() },
                    onSelected = { selectedJointMode = it },
                    enabled = progress?.isRunning != true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LabeledSwitch(
                label = "Include isolated unguarded reference",
                description = "Required for causal A/B percentages. Disable it to certify only the production path.",
                checked = includeReference,
                onCheckedChange = { includeReference = it },
                enabled = progress?.isRunning != true
            )
            LabeledSwitch(
                label = "Inject adversarial faults",
                description = "NaN target, infinite target, NaN seed state and overflowing finite DH geometry. Kept out of natural rates.",
                checked = includeFaults,
                onCheckedChange = { includeFaults = it },
                enabled = progress?.isRunning != true
            )
            LabeledSwitch(
                label = "Sequential state carry-over",
                description = "Each branch carries its own previous result, exposing cascading numerical damage across commands.",
                checked = carryState,
                onCheckedChange = { carryState = it },
                enabled = progress?.isRunning != true
            )
        }

        ChartSectionCard(
            title = "Numerical contract",
            subtitle = "The same seed, tolerance, maximum iterations, DLS damping and maximum step are supplied to both branches; only the safety stack differs."
        ) {
            OutlinedTextField(
                value = toleranceText,
                onValueChange = { toleranceText = decimalInput(it) },
                label = { Text("Certification tolerance (metres)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            OutlinedTextField(
                value = maxIterationsText,
                onValueChange = { maxIterationsText = it.filter(Char::isDigit) },
                label = { Text("Maximum iterations") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            OutlinedTextField(
                value = dampingText,
                onValueChange = { dampingText = decimalInput(it) },
                label = { Text("Base DLS damping") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            OutlinedTextField(
                value = maxStepText,
                onValueChange = { maxStepText = decimalInput(it) },
                label = { Text("Protected maximum normalized step") },
                modifier = Modifier.fillMaxWidth(),
                enabled = progress?.isRunning != true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        toleranceText = "0.00001"
                        trialsText = "4"
                        linkCountsText = "3, 5"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = progress?.isRunning != true
                ) { Text("Quick") }
                OutlinedButton(
                    onClick = {
                        toleranceText = "0.000001"
                        trialsText = "8"
                        linkCountsText = "3, 5, 10"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = progress?.isRunning != true
                ) { Text("1 µm research") }
            }
        }

        if (progress?.isRunning == true) {
            Button(
                onClick = {
                    processCoordinator.requestCancel(ResearchProcessIds.NUMERICAL_SAFETY_AUDIT)
                    message = "Cancelling after the current paired trial…"
                },
                modifier = Modifier.fillMaxWidth()
                    .testTag("numerical-safety-cancel")
                    .tutorialAnchor(TutorialTargets.NumericalSafetyCancel)
            ) { Text("Cancel safely") }
        } else {
            Button(
                onClick = {
                    if (processCoordinator.isActive(ResearchProcessIds.NUMERICAL_SAFETY_AUDIT)) {
                        message = "A mathematical safety campaign is already active. Open Research activity to inspect it."
                        return@Button
                    }
                    val parsed = parseConfig(
                        linkCountsText,
                        trialsText,
                        seedText,
                        toleranceText,
                        maxIterationsText,
                        dampingText,
                        maxStepText,
                        if (allJointModes) DiagnosticJointMode.entries else listOf(selectedJointMode),
                        includeReference,
                        includeFaults,
                        carryState
                    )
                    if (parsed == null) {
                        message = "Check the fields: links 2–100, trials 1–10,000 and finite positive solver values are required."
                    } else {
                        cancellation.set(false)
                        report = null
                        saved = null
                        showRawRows = false
                        telemetrySamples.clear()
                        telemetryIndex.set(0)
                        val runStartedAtNanos = System.nanoTime()
                        val lastTelemetryCaptureNanos = AtomicLong(runStartedAtNanos)
                        val capturedPhases = ConcurrentHashMap.newKeySet<String>().apply { add("PLANNING") }
                        val initial = DiagnosticProgressState(
                            isRunning = true,
                            phase = DiagnosticProgressPhase.PLANNING,
                            totalRuns = parsed.totalTrialCount,
                            message = "Preparing matched numerical-safety cases.",
                            telemetry = telemetrySampler.sample()
                        )
                        progress = initial
                        telemetrySamples += DiagnosticPerformanceSample.fromProgressState(telemetryIndex.incrementAndGet(), initial)
                        message = "Numerical-safety A/B campaign running…"
                        processCoordinator.launch(
                            id = ResearchProcessIds.NUMERICAL_SAFETY_AUDIT,
                            title = "Mathematical safety A/B",
                            kind = ResearchProcessKind.DIAGNOSTIC,
                            cancellationAction = { cancellation.set(true) }
                        ) { process ->
                            process.report(0.0, "Preparing paired cases", message)
                            try {
                                val generated = withContext(Dispatchers.Default) {
                                    NumericalSafetyExperiment(cancellation::get).run(parsed) { update ->
                                        process.report(
                                            progressFraction =
                                                if (update.totalTrials > 0) {
                                                    update.completedTrials.toDouble() / update.totalTrials.toDouble()
                                                } else {
                                                    null
                                                },
                                            stage = update.phase.lowercase().replace('_', ' '),
                                            detail = update.message
                                        )
                                        if (update.phase != "COMPLETED") {
                                            val capturedAtNanos = System.nanoTime()
                                            val firstPhaseOccurrence = capturedPhases.add(update.phase)
                                            val captureDue =
                                                capturedAtNanos - lastTelemetryCaptureNanos.get() >=
                                                    HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
                                            if (firstPhaseOccurrence || captureDue) {
                                                lastTelemetryCaptureNanos.set(capturedAtNanos)
                                                val uiState = update.toUiProgress(telemetrySampler)
                                                val sample =
                                                    DiagnosticPerformanceSample.fromProgressState(
                                                        telemetryIndex.incrementAndGet(),
                                                        uiState
                                                    )
                                                scope.launch {
                                                    progress = uiState
                                                    telemetrySamples += sample
                                                }
                                            }
                                        }
                                    }
                                }
                                val savingElapsedSeconds =
                                    (System.nanoTime() - runStartedAtNanos).toDouble() / 1_000_000_000.0
                                val savingProgress =
                                    initial.copy(
                                        isRunning = true,
                                        phase = DiagnosticProgressPhase.AGGREGATING,
                                        completedRuns = generated.trials.size,
                                        totalRuns = generated.trials.size,
                                        runsPerSecond =
                                            if (savingElapsedSeconds > 0.0) {
                                                generated.trials.size / savingElapsedSeconds
                                            } else {
                                                0.0
                                            },
                                        estimatedSecondsRemaining = 0.0,
                                        elapsedSeconds = savingElapsedSeconds,
                                        message = "Saving the safety-ablation report and paired raw evidence.",
                                        telemetry = telemetrySampler.sample()
                                    )
                                progress = savingProgress
                                telemetrySamples +=
                                    DiagnosticPerformanceSample.fromProgressState(
                                        telemetryIndex.incrementAndGet(),
                                        savingProgress
                                    )
                                val stored = withContext(Dispatchers.IO) { storage.save(generated) }
                                report = generated
                                saved = stored
                                message = "Completed and exported ${generated.trials.size} paired trial definitions."
                                process.completed(message)
                                val finalElapsedSeconds =
                                    (System.nanoTime() - runStartedAtNanos).toDouble() / 1_000_000_000.0
                                val finalProgress = savingProgress.copy(
                                    isRunning = false,
                                    phase = DiagnosticProgressPhase.COMPLETED,
                                    completedRuns = generated.trials.size,
                                    totalRuns = generated.trials.size,
                                    runsPerSecond =
                                        if (finalElapsedSeconds > 0.0) {
                                            generated.trials.size / finalElapsedSeconds
                                        } else {
                                            0.0
                                        },
                                    estimatedSecondsRemaining = 0.0,
                                    elapsedSeconds = finalElapsedSeconds,
                                    message = message,
                                    telemetry = telemetrySampler.sample()
                                )
                                progress = finalProgress
                                telemetrySamples +=
                                    DiagnosticPerformanceSample.fromProgressState(
                                        telemetryIndex.incrementAndGet(),
                                        finalProgress
                                    )
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                val cancelledProgress = progress?.copy(
                                    isRunning = false,
                                    phase = DiagnosticProgressPhase.IDLE,
                                    message = "Mathematical safety campaign was cancelled safely.",
                                    elapsedSeconds =
                                        (System.nanoTime() - runStartedAtNanos).toDouble() / 1_000_000_000.0,
                                    telemetry = telemetrySampler.sample()
                                )
                                progress = cancelledProgress
                                if (cancelledProgress != null) {
                                    telemetrySamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            telemetryIndex.incrementAndGet(),
                                            cancelledProgress
                                        )
                                }
                                process.cancelled("Mathematical safety campaign was cancelled safely.")
                            } catch (failure: Exception) {
                                message =
                                    if (cancellation.get()) "Campaign cancelled; no partial scientific report was published."
                                    else "Campaign failed safely: ${failure.message ?: "unknown error"}"
                                val failedProgress = progress?.copy(
                                    isRunning = false,
                                    phase = if (cancellation.get()) DiagnosticProgressPhase.IDLE else DiagnosticProgressPhase.FAILED,
                                    message = message,
                                    elapsedSeconds =
                                        (System.nanoTime() - runStartedAtNanos).toDouble() / 1_000_000_000.0,
                                    telemetry = telemetrySampler.sample()
                                )
                                progress = failedProgress
                                if (failedProgress != null) {
                                    telemetrySamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            telemetryIndex.incrementAndGet(),
                                            failedProgress
                                        )
                                }
                                if (cancellation.get()) process.cancelled(message) else process.failed(message)
                            }
                        }.onFailure { failure ->
                            message = failure.message ?: "Mathematical safety campaign could not start."
                            val failedStartProgress = progress?.copy(
                                isRunning = false,
                                phase = DiagnosticProgressPhase.FAILED,
                                message = message,
                                elapsedSeconds =
                                    (System.nanoTime() - runStartedAtNanos).toDouble() / 1_000_000_000.0,
                                telemetry = telemetrySampler.sample()
                            )
                            progress = failedStartProgress
                            if (failedStartProgress != null) {
                                telemetrySamples +=
                                    DiagnosticPerformanceSample.fromProgressState(
                                        telemetryIndex.incrementAndGet(),
                                        failedStartProgress
                                    )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("RunNumericalSafetyAudit")
                    .tutorialAnchor(TutorialTargets.NumericalSafetyRun)
            ) { Text("Run mathematical safety A/B") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            JargonAwareText(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }

        progress?.let { current ->
            DiagnosticLoadingProgressCard(
                progressState = current,
                performanceSamples = telemetrySamples,
                config = DiagnosticLoadingCardConfig(
                    runningTitle = "Numerical safety audit running",
                    finishedTitle = "Numerical safety audit telemetry",
                    progressSectionTitle = "Paired experiment progress",
                    completedLabel = "Trials completed",
                    remainingLabel = "Trials remaining",
                    showBenchmarkCoordinates = true,
                    telemetrySessionType = "Numerical safety ablation"
                ),
                timeline = safetyTimeline(current.phase)
            )
        }

        report?.let { current ->
            ProvideAutomaticFigureLibraryContext(
                collection = "numerical-safety",
                analysisId = current.protocolId,
                executionId = saved?.id ?: "numerical-safety-${current.createdAtEpochMillis}"
            ) {
                NumericalSafetyResults(current, saved, showRawRows) { showRawRows = !showRawRows }
            }
        }
    }
}

@Composable
private fun NumericalSafetyResults(
    report: NumericalSafetyReport,
    saved: NumericalSafetySavedSession?,
    showRawRows: Boolean,
    onToggleRawRows: () -> Unit
) {
    val guarded = report.validGuardedSummary
    val raw = report.validUnguardedSummary
    ChartSectionCard(
        title = "Defensible result",
        subtitle = "Valid inputs only. Injected faults never contribute to these rates.",
        modifier =
            Modifier
                .testTag("numerical-safety-results")
                .tutorialAnchor(TutorialTargets.NumericalSafetyResults)
    ) {
        ChartMetricRow("Protocol", report.protocolId)
        ChartMetricRow("Matched valid trials", guarded.trialCount.toString())
        ChartMetricRow("Guarded certified success", percent(guarded.validatedSuccessRate))
        ChartMetricRow(
            "Guarded Wilson 95% interval",
            "${percent(guarded.validatedSuccessInterval95.lower)} – ${percent(guarded.validatedSuccessInterval95.upper)}"
        )
        raw?.let { ChartMetricRow("Unguarded certified success", percent(it.validatedSuccessRate)) }
        report.validImpact?.let { impact ->
            ChartMetricRow("Success improvement", signedPoints(impact.successRateDeltaPercentagePoints))
            ChartMetricRow("Paired finite in-limit residual coverage", "${impact.pairedFiniteResidualCount}/${impact.pairedTrialCount}")
            ChartMetricRow("Paired residual reduction", percentValue(impact.pairedResidualReductionPercent))
            ChartMetricRow("Mean protected time cost (indicative)", signedPercent(impact.meanTimeCostPercent))
            ChartMetricRow("Backtracking retries", impact.guardedBacktrackingRetries.toString())
            ChartMetricRow("Singularity interventions", impact.guardedSingularityInterventions.toString())
        }
        JargonAwareText(report.interpretation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JargonAwareText(
            "Short-run timing is device telemetry, not a controlled benchmark: JIT, scheduling and temperature can dominate it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (raw != null) {
        ProfessionalHorizontalBarChart(
            title = "Certified result rate",
            subtitle = "A result counts only when the branch reports convergence and an independent FK residual satisfies the selected tolerance inside every joint limit.",
            items = listOf(
                ChartBarItem("Guarded · ${percent(guarded.validatedSuccessRate)}", rateUnits(guarded.validatedSuccessRate), Color(0xFF2E7D32)),
                ChartBarItem("Unguarded · ${percent(raw.validatedSuccessRate)}", rateUnits(raw.validatedSuccessRate), Color(0xFFC62828))
            ),
            xAxisLabel = "Certified rate × 10,000"
        )
        ProfessionalHorizontalBarChart(
            title = "Numerical safety events",
            subtitle = "Counts from valid inputs. Lower is better; fault-injection evidence is shown separately below.",
            items = listOf(
                ChartBarItem("Guarded non-finite", guarded.nonFiniteEncounterCount, Color(0xFF2E7D32)),
                ChartBarItem("Raw non-finite", raw.nonFiniteEncounterCount, Color(0xFFC62828)),
                ChartBarItem("Guarded limit violations", guarded.jointLimitViolationCount, Color(0xFF00838F)),
                ChartBarItem("Raw limit violations", raw.jointLimitViolationCount, Color(0xFFF57C00)),
                ChartBarItem("Guarded false success", guarded.unsafeSuccessCount, Color(0xFF6A1B9A)),
                ChartBarItem("Raw false success", raw.unsafeSuccessCount, Color(0xFFAD1457))
            ),
            xAxisLabel = "Observed events",
            directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
        )
    }

    val cumulative = report.cumulativeValidResiduals
    if (cumulative.size >= 2) {
        ProfessionalLineChart(
            title = "Protected cumulative residual burden",
            subtitle = "Direct sum only where both branches produced a finite, in-limit state and an independently recomputed residual.",
            points = cumulative.map { ChartLinePoint(it.trialIndex.toDouble(), it.guardedResidualSumMeters * 1e6) },
            xAxisLabel = "Valid campaign trial",
            yAxisLabel = "Cumulative residual (µm)",
            color = Color(0xFF2E7D32)
        )
        if (report.validUnguardedSummary != null) {
            ProfessionalLineChart(
                title = "Unguarded cumulative residual burden",
                subtitle = "Same paired admissible denominator and ordering as the protected chart above; failures and illegal joint motion are counted separately, never assigned an arbitrary distance.",
                points = cumulative.map { ChartLinePoint(it.trialIndex.toDouble(), it.unguardedResidualSumMeters * 1e6) },
                xAxisLabel = "Valid campaign trial",
                yAxisLabel = "Cumulative residual (µm)",
                color = Color(0xFFC62828)
            )
        }
    }

    report.adversarialGuardedSummary?.let { guardedFaults ->
        ChartSectionCard(
            title = "Injected-fault containment",
            subtitle = "These deliberately corrupt inputs measure defensive behaviour, not real-world incidence."
        ) {
            summarizeBranch("Guarded", guardedFaults)
            report.adversarialUnguardedSummary?.let { summarizeBranch("Unguarded", it) }
            report.adversarialImpact?.let { impact ->
                ChartMetricRow("Raw non-finite events prevented", impact.rawNonFiniteEventsPrevented.toString())
                ChartMetricRow("Raw limit violations prevented", impact.rawLimitViolationsPrevented.toString())
                ChartMetricRow("Unsafe successes prevented", impact.unsafeSuccessesPrevented.toString())
            }
        }
    }

    ChartSectionCard(
        title = "Evidence export",
        subtitle = "The report and every branch-level raw row are stored under Workspace studies and counted by Storage."
    ) {
        EvidencePath("Readable report", saved?.reportPath ?: "Saving…")
        EvidencePath("Paired raw CSV", saved?.trialCsvPath ?: "Saving…")
        OutlinedButton(onClick = onToggleRawRows, modifier = Modifier.fillMaxWidth()) {
            Text(if (showRawRows) "Hide row preview" else "Preview raw rows")
        }
        if (showRawRows) {
            report.trials.take(24).forEach { trial ->
                JargonAwareText(
                    "#${trial.index + 1} · ${trial.cohort.displayName} · ${trial.scenario.displayName}\n" +
                        "Guarded: ${trial.guarded.status}, residual ${distance(trial.guarded.independentResidualMeters)}\n" +
                        "Raw: ${trial.unguarded?.status ?: "not run"}, residual ${trial.unguarded?.let { distance(it.independentResidualMeters) } ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (report.trials.size > 24) {
                JargonAwareText("${report.trials.size - 24} additional trials are preserved in the CSV.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EvidencePath(label: String, path: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(
            path,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun summarizeBranch(label: String, summary: NumericalSafetyBranchSummary) {
    ChartMetricRow("$label rejected/contained", (summary.trialCount - summary.validatedSuccessCount).toString())
    ChartMetricRow("$label non-finite events", summary.nonFiniteEncounterCount.toString())
    ChartMetricRow("$label unsafe success claims", summary.unsafeSuccessCount.toString())
}

@Composable
private fun LabeledSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun parseConfig(
    linkCountsText: String,
    trialsText: String,
    seedText: String,
    toleranceText: String,
    maxIterationsText: String,
    dampingText: String,
    maxStepText: String,
    jointModes: List<DiagnosticJointMode>,
    includeReference: Boolean,
    includeFaults: Boolean,
    carryState: Boolean
): NumericalSafetyExperimentConfig? = runCatching {
    val links = linkCountsText.split(',').mapNotNull { it.trim().toIntOrNull() }.distinct()
    NumericalSafetyExperimentConfig(
        linkCounts = links,
        jointModes = jointModes,
        validTrialsPerTopology = requireNotNull(trialsText.toIntOrNull()),
        includeAdversarialProbes = includeFaults,
        includeUnguardedReference = includeReference,
        carryStateBetweenTargets = carryState,
        randomSeed = requireNotNull(seedText.toIntOrNull()),
        stressLevel = 0.9,
        ikConfig = IKConfig(
            maxIterations = requireNotNull(maxIterationsText.toIntOrNull()),
            tolerance = requireNotNull(toleranceText.toDoubleOrNull()),
            damping = requireNotNull(dampingText.toDoubleOrNull()),
            maxStep = requireNotNull(maxStepText.toDoubleOrNull())
        )
    )
}.getOrNull()

private fun NumericalSafetyProgress.toUiProgress(
    telemetrySampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState =
    DiagnosticProgressState(
        isRunning = phase != "COMPLETED",
        phase = when (phase) {
            "PLANNING" -> DiagnosticProgressPhase.PLANNING
            "VALID_CAMPAIGN" -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
            "ADVERSARIAL_PROBES" -> DiagnosticProgressPhase.ORACLE_CHECKS
            "AGGREGATING" -> DiagnosticProgressPhase.AGGREGATING
            "COMPLETED" -> DiagnosticProgressPhase.COMPLETED
            else -> DiagnosticProgressPhase.IDLE
        },
        completedRuns = completedTrials,
        totalRuns = totalTrials,
        currentLinkCount = currentLinkCount,
        currentJointMode = currentJointMode,
        runsPerSecond = trialsPerSecond,
        estimatedSecondsRemaining = estimatedSecondsRemaining,
        elapsedSeconds = elapsedSeconds,
        message = message,
        telemetry = telemetrySampler.sample()
    )

private fun safetyTimeline(phase: DiagnosticProgressPhase): List<DiagnosticTimelineStep> {
    val labels = listOf(
        "Design" to "Build matched, deterministic cases and validate the experimental contract.",
        "Valid A/B" to "Measure natural numerical behaviour on FK-proven reachable targets.",
        "Fault probes" to "Inject NaN, infinity and overflow without mixing them into natural incidence.",
        "Aggregate" to "Recompute residuals, intervals, deltas and safety-event counts.",
        "Export" to "Persist the readable report and raw paired CSV."
    )
    val current = when (phase) {
        DiagnosticProgressPhase.PLANNING -> 0
        DiagnosticProgressPhase.SEQUENTIAL_RUNS -> 1
        DiagnosticProgressPhase.ORACLE_CHECKS -> 2
        DiagnosticProgressPhase.AGGREGATING -> 3
        DiagnosticProgressPhase.COMPLETED -> 5
        DiagnosticProgressPhase.FAILED -> 4
        else -> 0
    }
    return labels.mapIndexed { index, pair ->
        DiagnosticTimelineStep(
            label = pair.first,
            description = pair.second,
            status = when {
                phase == DiagnosticProgressPhase.FAILED && index == current -> DiagnosticTimelineStatus.FAILED
                index < current -> DiagnosticTimelineStatus.COMPLETE
                index == current -> DiagnosticTimelineStatus.RUNNING
                else -> DiagnosticTimelineStatus.PENDING
            }
        )
    }
}

private fun DiagnosticJointMode.readableName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun decimalInput(value: String): String =
    value.filter { it.isDigit() || it == '.' || it == '-' || it == 'e' || it == 'E' || it == '+' }

private fun percent(value: Double): String =
    if (value.isFinite()) java.lang.String.format(Locale.US, "%.2f%%", value * 100.0) else "N/A"

private fun percentValue(value: Double): String =
    if (value.isFinite()) java.lang.String.format(Locale.US, "%.2f%%", value) else "N/A"

private fun signedPoints(value: Double): String =
    if (value.isFinite()) java.lang.String.format(Locale.US, "%+.2f pp", value) else "N/A"

private fun signedPercent(value: Double): String =
    if (value.isFinite()) java.lang.String.format(Locale.US, "%+.2f%%", value) else "N/A"

private fun rateUnits(value: Double): Int =
    if (value.isFinite()) (value.coerceIn(0.0, 1.0) * 10_000.0).roundToInt() else 0

private fun distance(metres: Double): String = when {
    !metres.isFinite() -> "non-finite"
    metres < 1e-6 -> java.lang.String.format(Locale.US, "%.2f nm", metres * 1e9)
    metres < 1e-3 -> java.lang.String.format(Locale.US, "%.3f µm", metres * 1e6)
    metres < 1.0 -> java.lang.String.format(Locale.US, "%.3f mm", metres * 1e3)
    else -> java.lang.String.format(Locale.US, "%.6f m", metres)
}
