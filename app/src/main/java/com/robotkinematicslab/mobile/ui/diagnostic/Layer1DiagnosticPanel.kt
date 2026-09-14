package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticAutoBenchmarkPreset
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticExperiment
import com.robotkinematicslab.mobile.ui.charts.DiagnosticChartsScreen
import com.robotkinematicslab.mobile.ui.charts.DiagnosticFigureArchiveHost
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
import com.robotkinematicslab.mobile.ui.diagnostic.numericalsafety.NumericalSafetyAuditPanel
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.DiagnosticDraft
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal enum class DiagnosticTutorialSurface {
    BASE,
    NUMERICAL_SAFETY,
    CHARTS
}

/**
 * Resolves only which already-existing diagnostic surface owns a tutorial target. A CHARTS result
 * is still conditional on a real report being available; callers must never fabricate one.
 */
internal fun diagnosticSurfaceForTutorialTarget(
    target: TutorialTargetId?
): DiagnosticTutorialSurface? =
    when (target) {
        TutorialTargets.Diagnostics,
        TutorialTargets.DiagnosticsConfiguration,
        TutorialTargets.DiagnosticsPresets,
        TutorialTargets.DiagnosticsRun,
        TutorialTargets.DiagnosticsCancel,
        TutorialTargets.DiagnosticsResults,
        TutorialTargets.DiagnosticsResultFolders,
        TutorialTargets.DiagnosticsCharts,
        TutorialTargets.NumericalSafety,
        TutorialTargets.Telemetry,
        TutorialTargets.TelemetryRaw,
        TutorialTargets.TelemetryLoading,
        TutorialTargets.TelemetryTimeline,
        TutorialTargets.TelemetryGestures -> DiagnosticTutorialSurface.BASE

        TutorialTargets.NumericalSafetyConfiguration,
        TutorialTargets.NumericalSafetyRun,
        TutorialTargets.NumericalSafetyCancel,
        TutorialTargets.NumericalSafetyResults -> DiagnosticTutorialSurface.NUMERICAL_SAFETY

        TutorialTargets.DiagnosticsChartCategory,
        TutorialTargets.DiagnosticsMatrix,
        TutorialTargets.DiagnosticsWorkspace3D,
        TutorialTargets.DiagnosticsWorkspace3DGestures,
        TutorialTargets.ChartGestures,
        TutorialTargets.ChartGuidanceAndFigures -> DiagnosticTutorialSurface.CHARTS

        else -> null
    }

@Composable
fun Layer1DiagnosticPanel(
    modifier: Modifier = Modifier,
    tutorialTarget: TutorialTargetId? = null
) {
    val cancellationRequested =
        remember {
            AtomicBoolean(false)
        }

    val diagnosticExperiment =
        remember(cancellationRequested) {
            Layer1DiagnosticExperiment(
                isCancellationRequested = cancellationRequested::get
            )
        }

    val coroutineScope =
        rememberCoroutineScope()

    val context =
        LocalContext.current

    val processCoordinator =
        remember(context) {
            ResearchProcessCoordinator.get(context.applicationContext)
        }

    val appStorageRepository =
        remember(context) {
            AppStorageRepository(context.applicationContext)
        }

    val restoredDraft =
        remember(appStorageRepository) {
            appStorageRepository.loadDiagnosticDraft()
        }

    val telemetrySampler =
        remember {
            DiagnosticSystemTelemetrySampler(
                context = context.applicationContext
            )
        }

    val performanceSamples =
        remember {
            mutableStateListOf<DiagnosticPerformanceSample>()
        }

    val lastTelemetryCaptureNanos =
        remember {
            AtomicLong(0L)
        }

    val performanceSampleIndex =
        remember {
            AtomicInteger(0)
        }

    val lastPublishedPhase =
        remember {
            AtomicReference<DiagnosticProgressPhase?>(null)
        }

    var reachableCountText by remember {
        mutableStateOf(restoredDraft.reachableCountText)
    }

    var unreachableCountText by remember {
        mutableStateOf(restoredDraft.unreachableCountText)
    }

    var robotLinkCountText by remember {
        mutableStateOf(restoredDraft.robotLinkCountText)
    }

    var minLinkCountText by remember {
        mutableStateOf(restoredDraft.minLinkCountText)
    }

    var maxLinkCountText by remember {
        mutableStateOf(restoredDraft.maxLinkCountText)
    }

    var samplesPerLinkCountText by remember {
        mutableStateOf(restoredDraft.samplesPerLinkCountText)
    }

    var unlimitedSampleCountText by remember {
        mutableStateOf(restoredDraft.unlimitedSampleCountText)
    }

    var seedText by remember {
        mutableStateOf(restoredDraft.seedText)
    }

    var experimentalMode by remember {
        mutableStateOf(restoredDraft.experimentalMode)
    }

    var unlimitedSamplesEnabled by remember {
        mutableStateOf(restoredDraft.unlimitedSamplesEnabled)
    }

    var manualRangeMode by remember {
        mutableStateOf(restoredDraft.manualRangeMode)
    }

    var selectedJointMode by remember {
        mutableStateOf(restoredDraft.jointMode)
    }

    var runAllTopologies by remember {
        mutableStateOf(restoredDraft.runAllTopologies)
    }

    var storeFullRunHistory by remember {
        mutableStateOf(restoredDraft.storeFullRunHistory)
    }

    var stressLevel by remember {
        mutableFloatStateOf(restoredDraft.stressLevel)
    }

    var ikMaxIterationsText by remember {
        mutableStateOf(restoredDraft.ikMaxIterationsText)
    }

    var ikToleranceText by remember {
        mutableStateOf(restoredDraft.ikToleranceText)
    }

    var ikDampingText by remember {
        mutableStateOf(restoredDraft.ikDampingText)
    }

    var ikMaxStepText by remember {
        mutableStateOf(restoredDraft.ikMaxStepText)
    }

    var report by remember {
        mutableStateOf<Layer1DiagnosticReport?>(null)
    }

    var reportFigureExecutionId by remember {
        mutableStateOf<String?>(null)
    }

    var showCharts by remember {
        mutableStateOf(false)
    }

    var showNumericalSafetyAudit by remember {
        mutableStateOf(false)
    }

    var progressState by remember {
        mutableStateOf<DiagnosticProgressState?>(null)
    }

    var validationMessage by remember {
        mutableStateOf(
            "Ready. Safe mode uses 2–10 links. Experimental mode allows 11+ links."
        )
    }

    LaunchedEffect(tutorialTarget, report != null) {
        when (diagnosticSurfaceForTutorialTarget(tutorialTarget)) {
            DiagnosticTutorialSurface.BASE -> {
                showNumericalSafetyAudit = false
                showCharts = false
            }

            DiagnosticTutorialSurface.NUMERICAL_SAFETY -> {
                showCharts = false
                showNumericalSafetyAudit = true
            }

            DiagnosticTutorialSurface.CHARTS -> {
                showNumericalSafetyAudit = false
                // A chart workspace is meaningful only for a genuine completed report. If none
                // exists, remain on configuration so the coach can explain the prerequisite.
                showCharts = report != null
            }

            null -> Unit
        }
    }

    val currentDraft = DiagnosticDraft(
                    reachableCountText = reachableCountText,
                    unreachableCountText = unreachableCountText,
                    robotLinkCountText = robotLinkCountText,
                    minLinkCountText = minLinkCountText,
                    maxLinkCountText = maxLinkCountText,
                    samplesPerLinkCountText = samplesPerLinkCountText,
                    unlimitedSampleCountText = unlimitedSampleCountText,
                    seedText = seedText,
                    experimentalMode = experimentalMode,
                    unlimitedSamplesEnabled = unlimitedSamplesEnabled,
                    manualRangeMode = manualRangeMode,
                    jointMode = selectedJointMode,
                    runAllTopologies = runAllTopologies,
                    storeFullRunHistory = storeFullRunHistory,
                    stressLevel = stressLevel,
                    ikMaxIterationsText = ikMaxIterationsText,
                    ikToleranceText = ikToleranceText,
                    ikDampingText = ikDampingText,
                    ikMaxStepText = ikMaxStepText
                )
    val setupEvaluation = DiagnosticSetupContract.evaluate(currentDraft)

    LaunchedEffect(
        reachableCountText,
        unreachableCountText,
        robotLinkCountText,
        minLinkCountText,
        maxLinkCountText,
        samplesPerLinkCountText,
        unlimitedSampleCountText,
        seedText,
        experimentalMode,
        unlimitedSamplesEnabled,
        manualRangeMode,
        selectedJointMode,
        runAllTopologies,
        storeFullRunHistory,
        stressLevel,
        ikMaxIterationsText,
        ikToleranceText,
        ikDampingText,
        ikMaxStepText
    ) {
        delay(300L)
        withContext(Dispatchers.IO) {
            appStorageRepository.saveDiagnosticDraft(
                currentDraft
            )
        }
    }

    val currentReportForCharts =
        report

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        val archiveExecutionId = reportFigureExecutionId
        if (currentReportForCharts != null && archiveExecutionId != null) {
            // Kept behind the real screen so figure evidence is completed even when the user never
            // opens a chart family. Each batch is invisible, non-semantic and memory-bounded.
            DiagnosticFigureArchiveHost(
                report = currentReportForCharts,
                executionId = archiveExecutionId,
                modifier = Modifier.matchParentSize()
            )
        }

        when {
            showNumericalSafetyAudit ->
                NumericalSafetyAuditPanel(
                    onBack = { showNumericalSafetyAudit = false },
                    modifier = Modifier.fillMaxWidth()
                )

            showCharts && currentReportForCharts != null ->
                DiagnosticChartsScreen(
                    report = currentReportForCharts,
                    onBack = {
                        showCharts = false
                    },
                    figureExecutionId = archiveExecutionId,
                    modifier = Modifier.fillMaxWidth()
                )

            else -> Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                        .testTag("diagnostics-root")
                        .tutorialAnchor(TutorialTargets.Diagnostics),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
        Text(
            text = "Layer 1 Diagnostic Experiment",
            style = MaterialTheme.typography.titleLarge
        )

        JargonHelpNotice()

        JargonAwareText(
            text = "Reachable targets are generated by FK from valid joint states. " +
                    "Sequential checks test recovery from one selected target to the next. " +
                    "Safe-mode results use 2–10 links. Experimental results above 10 links are reported separately.",
            style = MaterialTheme.typography.bodyMedium
        )

        com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection(
            title = "Advanced mathematical safety lab", summary = "Separate A/B checks of numerical safeguards; this does not alter the Run Diagnostics plan.",
            testTag = "diagnostics-advanced-lab") {
        OutlinedButton(
            onClick = { showNumericalSafetyAudit = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("OpenNumericalSafetyAudit")
                .tutorialAnchor(TutorialTargets.NumericalSafety)
        ) {
            Text("Open mathematical safety A/B lab")
        }

        }

        DiagnosticInputCard(
            setupEvaluation = setupEvaluation,
            reachableCountText = reachableCountText,
            onReachableCountChange = {
                reachableCountText = it
            },

            unreachableCountText = unreachableCountText,
            onUnreachableCountChange = {
                unreachableCountText = it
            },

            robotLinkCountText = robotLinkCountText,
            onRobotLinkCountChange = {
                robotLinkCountText = it
            },

            minLinkCountText = minLinkCountText,
            onMinLinkCountChange = {
                minLinkCountText = it
            },

            maxLinkCountText = maxLinkCountText,
            onMaxLinkCountChange = {
                maxLinkCountText = it
            },

            samplesPerLinkCountText = samplesPerLinkCountText,
            onSamplesPerLinkCountChange = {
                samplesPerLinkCountText = it
            },

            unlimitedSampleCountText = unlimitedSampleCountText,
            onUnlimitedSampleCountChange = {
                unlimitedSampleCountText = it
            },

            seedText = seedText,
            onSeedChange = {
                seedText = it
            },

            experimentalMode = experimentalMode,
            onExperimentalModeChange = {
                experimentalMode = it

                if (!it) {
                    unlimitedSamplesEnabled = false
                }
            },

            unlimitedSamplesEnabled = unlimitedSamplesEnabled,
            onUnlimitedSamplesEnabledChange = {
                unlimitedSamplesEnabled = it
            },

            manualRangeMode = manualRangeMode,
            onManualRangeModeChange = {
                manualRangeMode = it
            },

            selectedJointMode = selectedJointMode,
            onJointModeChange = {
                selectedJointMode = it
            },

            runAllTopologies = runAllTopologies,
            onRunAllTopologiesChange = {
                runAllTopologies = it
            },

            stressLevel = stressLevel,
            onStressLevelChange = {
                stressLevel = it
            },

            ikMaxIterationsText = ikMaxIterationsText,
            onIkMaxIterationsChange = {
                ikMaxIterationsText = it
            },

            ikToleranceText = ikToleranceText,
            onIkToleranceChange = {
                ikToleranceText = it
            },

            ikDampingText = ikDampingText,
            onIkDampingChange = {
                ikDampingText = it
            },

            ikMaxStepText = ikMaxStepText,
            onIkMaxStepChange = {
                ikMaxStepText = it
            },

            onUseAutoBenchmarkPreset = {
                val presetConfig =
                    DiagnosticAutoBenchmarkPreset.buildConfig()

                reachableCountText =
                    presetConfig.sampling.reachableCount.toString()

                unreachableCountText =
                    presetConfig.sampling.unreachableCount.toString()

                robotLinkCountText =
                    presetConfig.topology.robotLinkCount.toString()

                minLinkCountText =
                    presetConfig.topology.minLinkCount.toString()

                maxLinkCountText =
                    presetConfig.topology.maxLinkCount.toString()

                samplesPerLinkCountText =
                    presetConfig.sampling.samplesPerLinkCount.toString()

                unlimitedSampleCountText =
                    presetConfig.sampling.unlimitedSampleCount.toString()

                seedText =
                    presetConfig.seeds.seeds.joinToString(
                        separator = ", "
                    )

                experimentalMode =
                    presetConfig.topology.experimentalModeEnabled

                unlimitedSamplesEnabled =
                    presetConfig.sampling.unlimitedSamplesEnabled

                manualRangeMode =
                    true

                selectedJointMode =
                    presetConfig.topology.jointMode

                runAllTopologies =
                    presetConfig.topology.runAllTopologies

                stressLevel =
                    presetConfig.topology.stressLevel.toFloat()

                ikMaxIterationsText =
                    presetConfig.solver.ikMaxIterations.toString()

                ikToleranceText =
                    presetConfig.solver.ikTolerance.toString()

                ikDampingText =
                    presetConfig.solver.ikDamping.toString()

                ikMaxStepText =
                    presetConfig.solver.ikMaxStep.toString()

                validationMessage =
                    DiagnosticAutoBenchmarkPreset.buildSummaryText()
            },

            onUseBalancedPreset = {
                val solver = DiagnosticSolverPreset.BALANCED.config
                ikMaxIterationsText = solver.ikMaxIterations.toString()
                ikToleranceText = solver.ikTolerance.toString()
                ikDampingText = solver.ikDamping.toString()
                ikMaxStepText = solver.ikMaxStep.toString()
                validationMessage = "Balanced solver preset applied."
            },

            onUsePrecisionPreset = {
                val solver = DiagnosticSolverPreset.PRECISION.config
                ikMaxIterationsText = solver.ikMaxIterations.toString()
                ikToleranceText = solver.ikTolerance.toString()
                ikDampingText = solver.ikDamping.toString()
                ikMaxStepText = solver.ikMaxStep.toString()
                validationMessage = "Precision solver preset applied."
            },

            onUseExplorationPreset = {
                val solver = DiagnosticSolverPreset.EXPLORATION.config
                ikMaxIterationsText = solver.ikMaxIterations.toString()
                ikToleranceText = solver.ikTolerance.toString()
                ikDampingText = solver.ikDamping.toString()
                ikMaxStepText = solver.ikMaxStep.toString()
                validationMessage = "Exploration solver preset applied."
            },

            isRunning = progressState?.isRunning == true,

            onCancel = {
                processCoordinator.requestCancel(ResearchProcessIds.DIAGNOSTIC_BENCHMARK)
                validationMessage = "Cancelling diagnostic experiment safely…"
            },

            onRun = runDiagnostic@{
                if (processCoordinator.isActive(ResearchProcessIds.DIAGNOSTIC_BENCHMARK)) {
                    validationMessage = "A diagnostic benchmark is already running. Open Research activity to inspect it."
                    return@runDiagnostic
                }
                val checked = DiagnosticSetupContract.evaluate(currentDraft)
                if (!checked.canRun) {
                    validationMessage = checked.errors.values.joinToString("\n")
                    return@runDiagnostic
                }
                val config = requireNotNull(checked.config)
                val samplesPerLinkCount = requireNotNull(checked.plan).samplesPerLinkCount
                        report =
                            null

                        showCharts =
                            false

                        performanceSamples.clear()
                        cancellationRequested.set(false)
                        lastTelemetryCaptureNanos.set(0L)
                        performanceSampleIndex.set(0)
                        lastPublishedPhase.set(null)

                        val initialProgress =
                            DiagnosticProgressState(
                                isRunning = true,
                                phase = DiagnosticProgressPhase.PLANNING,
                                message =
                                    if (config.performance.storeFullRunHistory) {
                                        "Starting diagnostic benchmark with full run history."
                                    } else {
                                        "Starting diagnostic benchmark in lightweight performance mode."
                                    },
                                telemetry = telemetrySampler.sample()
                            )

                        progressState =
                            initialProgress

                        performanceSamples +=
                            DiagnosticPerformanceSample.fromProgressState(
                                sampleIndex = performanceSampleIndex.incrementAndGet(),
                                progressState = initialProgress
                            )

                        validationMessage =
                            if (config.performance.storeFullRunHistory) {
                                "Diagnostic benchmark running with full run history..."
                            } else {
                                "Diagnostic benchmark running in lightweight performance mode..."
                            }

                        processCoordinator.launch(
                            id = ResearchProcessIds.DIAGNOSTIC_BENCHMARK,
                            title = "Diagnostic benchmark",
                            kind = ResearchProcessKind.DIAGNOSTIC,
                            cancellationAction = { cancellationRequested.set(true) }
                        ) { process ->
                            process.report(
                                progressFraction = 0.0,
                                stage = "Planning benchmark",
                                detail = validationMessage
                            )
                            try {
                                val generatedReport =
                                    withContext(Dispatchers.Default) {
                                        diagnosticExperiment.runExperiment(
                                            config = config,
                                            onProgress = { state ->
                                                val capturedAtNanos =
                                                    System.nanoTime()

                                                val previousCaptureNanos =
                                                    lastTelemetryCaptureNanos.get()

                                                val previousPhase =
                                                    lastPublishedPhase.get()

                                                val phaseChanged =
                                                    previousPhase != state.phase

                                                val terminalPhase =
                                                    state.phase == DiagnosticProgressPhase.COMPLETED ||
                                                            state.phase == DiagnosticProgressPhase.FAILED

                                                val captureIntervalPassed =
                                                    capturedAtNanos - previousCaptureNanos >=
                                                        HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS

                                                if (phaseChanged || terminalPhase || captureIntervalPassed) {
                                                    lastTelemetryCaptureNanos.set(capturedAtNanos)
                                                    lastPublishedPhase.set(state.phase)

                                                    val enrichedState =
                                                        state.copy(
                                                            telemetry = telemetrySampler.sample()
                                                        )

                                                    val sample =
                                                        DiagnosticPerformanceSample.fromProgressState(
                                                            sampleIndex = performanceSampleIndex.incrementAndGet(),
                                                            progressState = enrichedState
                                                        )

                                                    val processFraction =
                                                        if (enrichedState.totalRuns > 0) {
                                                            enrichedState.completedRuns.toDouble() / enrichedState.totalRuns.toDouble()
                                                        } else {
                                                            null
                                                        }
                                                    process.report(
                                                        progressFraction = processFraction,
                                                        stage = enrichedState.phase.name.lowercase().replace('_', ' '),
                                                        detail = enrichedState.message
                                                    )

                                                    coroutineScope.launch {
                                                        progressState =
                                                            enrichedState

                                                        performanceSamples +=
                                                            sample
                                                    }
                                                }
                                            }
                                        )
                                    }

                                val savedSession =
                                    try {
                                        progressState =
                                            progressState?.copy(
                                                isRunning = true,
                                                phase = DiagnosticProgressPhase.AGGREGATING,
                                                message = "Saving the completed diagnostic session and full CSV history.",
                                                telemetry = telemetrySampler.sample()
                                            )
                                        withContext(Dispatchers.IO) {
                                            appStorageRepository.saveDiagnosticSession(generatedReport)
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        null
                                    }

                                reportFigureExecutionId =
                                    savedSession?.id
                                        ?: ChartFigureExporter.newAutomaticFigureExecutionId()
                                report = generatedReport

                                val completionMessage =
                                    when {
                                        config.topology.experimentalModeEnabled && config.sampling.unlimitedSamplesEnabled ->
                                            "Diagnostic experiment completed. Warning: unlimited experimental sample mode was used."

                                        config.topology.experimentalModeEnabled ->
                                            "Diagnostic experiment completed. Results above 10 links are experimental only."

                                        samplesPerLinkCount > 5000 ->
                                            "Diagnostic experiment completed. Warning: high sample count was used."

                                        !config.performance.storeFullRunHistory ->
                                            "Diagnostic experiment completed in lightweight performance mode."

                                        else ->
                                            "Diagnostic experiment completed."
                                    }

                                validationMessage =
                                    if (savedSession == null) {
                                        "$completionMessage The report is available now, but its persistent session export could not be written."
                                    } else {
                                        "$completionMessage Session saved with ${savedSession.runCount} run records."
                                    }

                                val finalProgress =
                                    progressState?.copy(
                                        isRunning = false,
                                        phase = DiagnosticProgressPhase.COMPLETED,
                                        completedRuns =
                                            progressState?.totalRuns ?: 0,
                                        message = validationMessage,
                                        telemetry = telemetrySampler.sample()
                                    )

                                if (finalProgress != null) {
                                    progressState =
                                        finalProgress

                                    performanceSamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = performanceSampleIndex.incrementAndGet(),
                                            progressState = finalProgress
                                        )
                                }
                                process.completed(validationMessage)
                            } catch (cancelled: CancellationException) {
                                if (!cancellationRequested.get()) {
                                    throw cancelled
                                }
                                validationMessage = "Diagnostic experiment cancelled. No partial report was published."

                                val cancelledProgress =
                                    progressState?.copy(
                                        isRunning = false,
                                        phase = DiagnosticProgressPhase.IDLE,
                                        message = validationMessage,
                                        telemetry = telemetrySampler.sample()
                                    )
                                progressState = cancelledProgress
                                if (cancelledProgress != null) {
                                    performanceSamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = performanceSampleIndex.incrementAndGet(),
                                            progressState = cancelledProgress
                                        )
                                }
                                process.cancelled(validationMessage)
                            } catch (exception: Exception) {
                                validationMessage =
                                    "Diagnostic experiment failed: ${exception.message ?: "Unknown error"}"

                                val failedProgress =
                                    progressState?.copy(
                                        isRunning = false,
                                        phase = DiagnosticProgressPhase.FAILED,
                                        message = validationMessage,
                                        telemetry = telemetrySampler.sample()
                                    )

                                if (failedProgress != null) {
                                    progressState =
                                        failedProgress

                                    performanceSamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = performanceSampleIndex.incrementAndGet(),
                                            progressState = failedProgress
                                        )
                                }
                                process.failed(validationMessage)
                            }
                        }.onFailure { error ->
                            validationMessage = error.message ?: "Diagnostic benchmark could not start."
                            val failedStartProgress =
                                progressState?.copy(
                                    isRunning = false,
                                    phase = DiagnosticProgressPhase.FAILED,
                                    message = validationMessage,
                                    telemetry = telemetrySampler.sample()
                                )
                            progressState = failedStartProgress
                            if (failedStartProgress != null) {
                                performanceSamples +=
                                    DiagnosticPerformanceSample.fromProgressState(
                                        sampleIndex = performanceSampleIndex.incrementAndGet(),
                                        progressState = failedStartProgress
                                    )
                            }
                        }
            }
        )

        StatusCard(
            message = validationMessage
        )

        progressState?.let { currentProgress ->
            DiagnosticLoadingProgressCard(
                progressState = currentProgress,
                performanceSamples = performanceSamples
            )
        }

                report?.let { currentReport ->
                    DiagnosticResultsExplorer(
                        report = currentReport,
                        onOpenCharts = { showCharts = true }
                    )
                }
            }
        }
    }
}
