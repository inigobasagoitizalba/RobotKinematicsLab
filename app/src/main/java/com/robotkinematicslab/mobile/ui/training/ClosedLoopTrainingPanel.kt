package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopOutcome
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopPhase
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopProgress
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopSessionSnapshot
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopSessionSummary
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopTrainingConfig
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopTrainingCoordinator
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopTrainingStorageRepository
import com.robotkinematicslab.mobile.ml.closedloop.DefaultClosedLoopDatasetGrower
import com.robotkinematicslab.mobile.ml.closedloop.DefaultClosedLoopTrainingCycleRunner
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.performance.compute.ComputeRuntimeGuard
import com.robotkinematicslab.mobile.performance.compute.AndroidPerformanceHintReporter
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ml.closedloop.closedLoopCycleCount
import com.robotkinematicslab.mobile.ml.closedloop.humanLabel
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLegend
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ClosedLoopTrainingPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val datasetStorage = remember(context) { DatasetStorageRepository(context) }
    val robotLibrary = remember(context) { RobotLibraryRepository(context) }
    val trainingStorage = remember(context) { TrainingStorageRepository(context) }
    val sessionStorage = remember(context) { ClosedLoopTrainingStorageRepository(context) }
    val computeGuard = remember(context) { ComputeRuntimeGuard(context) }
    val coordinator = remember(context) {
        ClosedLoopTrainingCoordinator(
            cycleRunner =
                DefaultClosedLoopTrainingCycleRunner(
                    trainingEngine =
                        LocalTrainingEngine(
                            runtimeWorkerLimit = computeGuard::currentWorkerLimit,
                            workCycleReporterFactory = { AndroidPerformanceHintReporter(context) }
                        ),
                    trainingStorage = trainingStorage,
                    datasetStorage = datasetStorage
                ),
            datasetGrower =
                DefaultClosedLoopDatasetGrower(
                    datasetStorage = datasetStorage,
                    robotLibrary = robotLibrary,
                    workerCountProvider = computeGuard::currentWorkerLimit
                ),
            sessionStore = sessionStorage
        )
    }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    val cancellation = remember { AtomicBoolean(false) }

    var manifests by remember { mutableStateOf(datasetStorage.listManifests()) }
    var selectedManifest by remember { mutableStateOf(manifests.firstOrNull()) }
    var sessions by remember { mutableStateOf(sessionStorage.listSessions()) }
    var selectedSessionId by remember { mutableStateOf<String?>(null) }
    var resumeSnapshot by remember { mutableStateOf<ClosedLoopSessionSnapshot?>(null) }

    var sessionName by remember { mutableStateOf("closed_loop_context_model") }
    var compareProfiles by remember { mutableStateOf(true) }
    var evaluationProfile by remember { mutableStateOf(TrainingFeatureProfile.CONTEXT_ENHANCED) }
    var modelKind by remember { mutableStateOf(TrainingModelKind.AUTOMATIC) }
    var resourceMode by remember { mutableStateOf(TrainingResourceMode.BALANCED) }
    var splitStrategy by remember { mutableStateOf(TrainingSplitStrategy.ROBOT_HELD_OUT) }
    var trainingRowCapText by remember { mutableStateOf("100000") }
    var epochsText by remember { mutableStateOf("40") }
    var batchSizeText by remember { mutableStateOf("128") }
    var learningRateText by remember { mutableStateOf("0.003") }
    var l2Text by remember { mutableStateOf("0.0001") }
    var hiddenUnitsText by remember { mutableStateOf("24") }
    var seedText by remember { mutableStateOf("42") }
    var patienceText by remember { mutableStateOf("8") }

    var minimumMacroF1Text by remember { mutableStateOf("0.80") }
    var maximumDisagreementPercentText by remember { mutableStateOf("10") }
    var maximumCyclesText by remember { mutableStateOf("5") }
    var rowsPerRobotIncrementText by remember { mutableStateOf("1000") }
    var maximumDatasetRowsText by remember { mutableStateOf("100000") }
    var inferenceRetriesText by remember { mutableStateOf("3") }
    var trainingRetriesText by remember { mutableStateOf("1") }
    var minimumImprovementText by remember { mutableStateOf("0.001") }
    var stagnantCyclesText by remember { mutableStateOf("2") }
    var timeBudgetMinutesText by remember { mutableStateOf("0") }
    var autoGrowDataset by remember { mutableStateOf(true) }

    var activeJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<ClosedLoopProgress?>(null) }
    var telemetryProgress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    val performanceSamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    var result by remember { mutableStateOf<ClosedLoopSessionSnapshot?>(null) }
    var statusMessage by remember {
        mutableStateOf("Ready. Deterministic IK labels drive validation; the final test remains isolated.")
    }
    var startedAtMillis by remember { mutableLongStateOf(0L) }

    fun loadSnapshot(snapshot: ClosedLoopSessionSnapshot) {
        val config = snapshot.config
        val training = config.localTrainingConfig
        resumeSnapshot = snapshot.takeIf { it.outcome != ClosedLoopOutcome.ACCEPTED }
        sessionName = config.sessionName
        selectedManifest = manifests.firstOrNull { it.datasetName == config.datasetName }
        compareProfiles = training.compareFeatureProfiles
        evaluationProfile = config.evaluationProfile
        modelKind = training.modelKind
        resourceMode = training.resourceMode
        splitStrategy = training.splitStrategy
        trainingRowCapText = training.maximumRows.toString()
        epochsText = training.epochs.toString()
        batchSizeText = training.batchSize.toString()
        learningRateText = training.learningRate.toString()
        l2Text = training.l2Regularization.toString()
        hiddenUnitsText = training.hiddenUnits.toString()
        seedText = training.randomSeed.toString()
        patienceText = training.earlyStoppingPatience.toString()
        minimumMacroF1Text = config.minimumMacroF1.toString()
        maximumDisagreementPercentText = (config.maximumOracleDisagreementRate * 100.0).toString()
        maximumCyclesText = config.maximumCycles.toString()
        rowsPerRobotIncrementText = config.samplesPerRobotIncrement.toString()
        maximumDatasetRowsText = config.maximumDatasetRows.toString()
        inferenceRetriesText = config.maximumInferenceRetries.toString()
        trainingRetriesText = config.maximumTrainingRetries.toString()
        minimumImprovementText = config.minimumMacroF1Improvement.toString()
        stagnantCyclesText = config.maximumStagnantCycles.toString()
        timeBudgetMinutesText = config.maximumElapsedMinutes.toString()
        autoGrowDataset = config.automaticallyGrowDataset
        result = snapshot
        statusMessage = "Loaded ${snapshot.sessionId}. Adjust its budget if needed, then continue."
    }

    fun plan(workerCount: Int, safeRows: Int): Pair<ClosedLoopTrainingConfig?, String?> =
        buildClosedLoopConfig(
                manifest = selectedManifest,
                sessionName = sessionName,
                compareProfiles = compareProfiles,
                evaluationProfile = evaluationProfile,
                modelKind = modelKind,
                resourceMode = resourceMode,
                splitStrategy = splitStrategy,
                trainingRowCapText = trainingRowCapText,
                epochsText = epochsText,
                batchSizeText = batchSizeText,
                learningRateText = learningRateText,
                l2Text = l2Text,
                hiddenUnitsText = hiddenUnitsText,
                seedText = seedText,
                patienceText = patienceText,
                minimumMacroF1Text = minimumMacroF1Text,
                maximumDisagreementPercentText = maximumDisagreementPercentText,
                maximumCyclesText = maximumCyclesText,
                rowsPerRobotIncrementText = rowsPerRobotIncrementText,
                maximumDatasetRowsText = maximumDatasetRowsText,
                inferenceRetriesText = inferenceRetriesText,
                trainingRetriesText = trainingRetriesText,
                minimumImprovementText = minimumImprovementText,
                stagnantCyclesText = stagnantCyclesText,
                timeBudgetMinutesText = timeBudgetMinutesText,
                autoGrowDataset = autoGrowDataset,
                workerCount = workerCount,
                safeTrainingRows = safeRows
            )

    fun start(resume: ClosedLoopSessionSnapshot?) {
        if (processCoordinator.isActive(ResearchProcessIds.CLOSED_LOOP_TRAINING)) {
            statusMessage = "A closed-loop training session is already active. Open Research activity to inspect it."
            return
        }
        val manifest = selectedManifest
        val computePolicy = computeGuard.currentPolicy(forceRefresh = true)
        val built = plan(computePolicy.effectiveWorkerCount, computePolicy.estimatedSafeTrainingRows)
        if (built.second != null) {
            statusMessage = requireNotNull(built.second)
            return
        }
        val config = requireNotNull(built.first)
        val selected = requireNotNull(manifest)
        cancellation.set(false)
        progress = null
        result = null
        performanceSamples.clear()
        telemetryCaptureGate.reset()
        val capturedTelemetry = mutableListOf<DiagnosticPerformanceSample>()
        startedAtMillis = System.currentTimeMillis()
        statusMessage =
            (if (resume == null) "Starting" else "Resuming") +
                " closed-loop training with ${config.localTrainingConfig.workerCount} worker(s)…"
        activeJob =
            processCoordinator.launch(
                id = ResearchProcessIds.CLOSED_LOOP_TRAINING,
                title = "Closed-loop training · ${config.sessionName}",
                kind = ResearchProcessKind.TRAINING,
                cancellationAction = { cancellation.set(true) }
            ) { process ->
                process.report(0.0, "Preparing closed loop", statusMessage)
                try {
                    val actualRows = withContext(Dispatchers.IO) {
                        datasetStorage.countCsvDataRows(File(selected.csvPath))
                    }
                    val completed =
                        withContext(Dispatchers.Default) {
                            coordinator.run(
                                config = config,
                                initialDatasetRows = actualRows,
                                resumeFrom = resume,
                                cancellationRequested = cancellation,
                                onProgress = { update ->
                                    process.report(
                                        progressFraction = update.fraction,
                                        stage = update.phase.name.lowercase().replace('_', ' '),
                                        detail = update.message
                                    )
                                    if (
                                        telemetryCaptureGate.shouldCapture(
                                            force = update.phase in setOf(
                                                ClosedLoopPhase.COMPLETED,
                                                ClosedLoopPhase.MANUAL_INTERVENTION,
                                                ClosedLoopPhase.CANCELLED,
                                                ClosedLoopPhase.FAILED
                                            )
                                        )
                                    ) {
                                        val mapped =
                                            mapClosedLoopProgress(
                                                update = update,
                                                startedAtMillis = startedAtMillis,
                                                telemetrySampler = telemetrySampler
                                            )
                                        val sample =
                                            DiagnosticPerformanceSample.fromProgressState(
                                                sampleIndex = capturedTelemetry.size + 1,
                                                progressState = mapped
                                            )
                                        capturedTelemetry += sample
                                        scope.launch {
                                            progress = update
                                            telemetryProgress = mapped
                                            performanceSamples += sample
                                        }
                                    }
                                }
                            )
                        }
                    result = completed
                    withContext(Dispatchers.IO) {
                        sessionStorage.saveTelemetry(completed.sessionId, capturedTelemetry)
                    }
                    resumeSnapshot = completed.takeIf { it.outcome != ClosedLoopOutcome.ACCEPTED }
                    statusMessage = completed.statusMessage
                    sessions = withContext(Dispatchers.IO) { sessionStorage.listSessions() }
                    manifests = withContext(Dispatchers.IO) { datasetStorage.listManifests() }
                    process.completed(statusMessage)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    process.cancelled("Closed-loop training was cancelled at a safe boundary.")
                } catch (error: Exception) {
                    statusMessage = error.message ?: "Closed-loop training could not start."
                    process.failed(statusMessage)
                } finally {
                    activeJob = null
                }
            }.getOrElse { error ->
                statusMessage = error.message ?: "Closed-loop training could not start."
                null
            }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Closed-Loop Training", style = MaterialTheme.typography.headlineSmall)
        JargonHelpNotice()
        JargonAwareText(
            "Train → verify stored inference → compare with saved IK validation labels → grow the validated dataset → retrain. " +
                "Every decision, retry, model and resource sample is retained.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrainingDisclosureSection(
            title = "1 · Validated data source",
            subtitle = "Dataset growth reuses its saved robots, seed protocol, target mix and IK configuration."
        ) {
            if (manifests.isEmpty()) {
                Text("Create a managed dataset in the Dataset tab first.")
            }
            CompactSelectionMenu(options = manifests, selected = selectedManifest,
                label = { ScientificEntityNameResolver.dataset(it.csvPath, it.datasetName).primary },
                enabled = activeJob == null, onSelected = { selectedManifest = it; resumeSnapshot = null })
            selectedManifest?.let { source ->
                Text("${source.rowCount} recorded rows · ${source.robotIds.distinct().size} robot IDs · Classification labels from deterministic IK. This is not verified 1 μm IK regression.")
                Text(if(source.hasCompleteBatchProvenance) "Complete recorded append provenance; every CSV row and generation contract is revalidated before each training cycle." else "Incompatible: append provenance is incomplete. Review the source before training.")
                FeatureSetExplorer("Closed Loop / data source", source.csvPath)
            }
            OutlinedButton(
                onClick = {
                    manifests = datasetStorage.listManifests()
                    selectedManifest =
                        selectedManifest?.let { selected ->
                            manifests.firstOrNull { it.datasetName == selected.datasetName }
                        } ?: manifests.firstOrNull()
                },
                enabled = activeJob == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh managed datasets") }
        }

        TrainingDisclosureSection(
            title = "2 · Scientific acceptance gate",
            subtitle =
                "Both thresholds use validation labels from deterministic IK. " +
                    "Test metrics are reporting-only for each run. Growth may change partitions; there is no fixed final session holdout. Disagreement = 1 − validation accuracy.",
            modifier = Modifier
                .testTag("training-closed-loop-controls")
                .tutorialAnchor(TutorialTargets.TrainingClosedLoop)
        ) {
            JargonAwareText(
                "Both criteria must pass on the judged profile’s validation partition. Macro-F1 gives each class equal weight; raise its minimum to be stricter. Disagreement is the fraction of predictions differing from stored IK labels; lower its maximum to be stricter. This does not re-solve IK. Failing either criterion follows the configured growth/stop policy. Test metrics never select a cycle, but are repeatedly reported and are not an untouched final session evaluation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ClosedLoopNumericField("Minimum macro-F1 (0–1)", minimumMacroF1Text, activeJob == null) {
                minimumMacroF1Text = it
            }
            ClosedLoopNumericField("Maximum disagreement with IK (%)", maximumDisagreementPercentText, activeJob == null) {
                maximumDisagreementPercentText = it
            }
            Text("Model judged by the gate", style = MaterialTheme.typography.titleSmall)
            CompactSelectionMenu(options = TrainingFeatureProfile.entries, selected = evaluationProfile,
                label = { it.displayName }, enabled = activeJob == null, onSelected = { evaluationProfile = it })
            Text("Judged: ${evaluationProfile.displayName}. " + if(compareProfiles)
                "Additional full profiles trained: ${TrainingFeatureProfile.entries.filter { it != evaluationProfile }.joinToString { it.displayName }}. Each adds candidate training, metrics and stored models; only the judged profile controls the gate."
                else "No additional profiles are trained.")
            ClosedLoopCheckboxRow(
                checked = compareProfiles,
                label = "Also train and store all other full feature profiles",
                enabled = activeJob == null,
                onCheckedChange = { compareProfiles = it }
            )
        }

        TrainingDisclosureSection(
            title = "3 · Loop and recovery policy",
            subtitle = "Zero minutes means no time limit. The best model is always retained even when the loop escalates."
        ) {
            JargonAwareText(
                "One cycle means train, validate and decide. If improvement stalls, escalation adds validated rows and retries; the best committed checkpoint remains available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("Session name") },
                enabled = activeJob == null,
                modifier = Modifier.fillMaxWidth()
            )
            ClosedLoopNumericField("Maximum total cycles", maximumCyclesText, activeJob == null) { maximumCyclesText = it }
            ClosedLoopNumericField("New rows per robot after rejection", rowsPerRobotIncrementText, activeJob == null) {
                rowsPerRobotIncrementText = it
            }
            ClosedLoopNumericField("Hard dataset row ceiling", maximumDatasetRowsText, activeJob == null) {
                maximumDatasetRowsText = it
            }
            ClosedLoopNumericField("Inference rebuild retries", inferenceRetriesText, activeJob == null) {
                inferenceRetriesText = it
            }
            ClosedLoopNumericField("Training-error retries", trainingRetriesText, activeJob == null) {
                trainingRetriesText = it
            }
            ClosedLoopNumericField("Minimum macro-F1 improvement per cycle", minimumImprovementText, activeJob == null) {
                minimumImprovementText = it
            }
            ClosedLoopNumericField("Stagnant cycles before escalation", stagnantCyclesText, activeJob == null) {
                stagnantCyclesText = it
            }
            ClosedLoopNumericField("Time budget in minutes (0 = unlimited)", timeBudgetMinutesText, activeJob == null) {
                timeBudgetMinutesText = it
            }
            ClosedLoopCheckboxRow(
                checked = autoGrowDataset,
                label = "Automatically ask Data Factory for more validated rows",
                enabled = activeJob == null,
                onCheckedChange = { autoGrowDataset = it }
            )
        }

        TrainingDisclosureSection(
            title = "4 · Model and compute controls",
            subtitle = "Fixed seed and split rule; selected rows and partition membership may change as the corpus grows. Retries use unchanged parameters."
        ) {
            Text("Leakage-safe split", style = MaterialTheme.typography.titleSmall)
            TrainingSplitStrategy.entries.forEach { strategy ->
                ClosedLoopSelectionButton(
                    selected = splitStrategy == strategy,
                    label = strategy.displayName,
                    enabled = activeJob == null,
                    onClick = { splitStrategy = strategy }
                )
            }
            Text("Model search", style = MaterialTheme.typography.titleSmall)
            TrainingModelKind.entries.forEach { kind ->
                ClosedLoopSelectionButton(
                    selected = modelKind == kind,
                    label = kind.displayName,
                    enabled = activeJob == null,
                    onClick = { modelKind = kind; if (!com.robotkinematicslab.mobile.ml.training.TrainingControlContract.hiddenUnitsApply(kind,resourceMode)) hiddenUnitsText = "24" }
                )
            }
            Text("Automatic search budget", style = MaterialTheme.typography.titleSmall)
            TrainingSearchBudgetHelp(modelKind,resourceMode,hiddenUnitsText.toIntOrNull())
            ResourceLoadLegend(modifier = Modifier.fillMaxWidth())
            TrainingResourceMode.entries.forEach { mode ->
                TrainingResourceModeButton(
                    mode = mode,
                    selected = resourceMode == mode && modelKind == TrainingModelKind.AUTOMATIC,
                    enabled = activeJob == null && modelKind == TrainingModelKind.AUTOMATIC,
                    onClick = { resourceMode = mode; if (!com.robotkinematicslab.mobile.ml.training.TrainingControlContract.hiddenUnitsApply(modelKind,mode)) hiddenUnitsText = "24" }
                )
            }
            ClosedLoopNumericField("Rows used per training cycle (cap)", trainingRowCapText, activeJob == null) {
                trainingRowCapText = it
            }
            JargonAwareText(
                "Every cycle samples across the complete managed CSV. After growth, every newly committed row is forced into the next bounded sample; the remaining slots are selected deterministically from older rows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ClosedLoopNumericField("Maximum epochs per candidate", epochsText, activeJob == null) { epochsText = it }
            ClosedLoopNumericField("Mini-batch size", batchSizeText, activeJob == null) { batchSizeText = it }
            ClosedLoopNumericField("Learning rate", learningRateText, activeJob == null) { learningRateText = it }
            ClosedLoopNumericField("L2 regularization", l2Text, activeJob == null) { l2Text = it }
            ClosedLoopNumericField("Hidden units", hiddenUnitsText, activeJob == null && com.robotkinematicslab.mobile.ml.training.TrainingControlContract.hiddenUnitsApply(modelKind,resourceMode)) { hiddenUnitsText = it }
            ClosedLoopNumericField("Random seed", seedText, activeJob == null) { seedText = it }
            ClosedLoopNumericField("Early-stopping patience", patienceText, activeJob == null) { patienceText = it }
        }

        ChartSectionCard(
            title = "5 · Execute",
            subtitle = "Cancellation finishes the current safe boundary; valid generated rows and completed models remain registered."
        ) {
            if (activeJob == null) {
                val policy = computeGuard.currentPolicy()
                val preview = plan(policy.effectiveWorkerCount, policy.estimatedSafeTrainingRows)
                val resumeError = preview.first?.let { planned -> resumeSnapshot?.let { saved ->
                    runCatching { coordinator.validateResume(planned, selectedManifest?.rowCount ?: 0, saved) }.exceptionOrNull()?.message
                } }
                (preview.second ?: resumeError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { start(resumeSnapshot) },
                    enabled = preview.first != null && resumeError == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (resumeSnapshot == null) "Start closed-loop training" else "Continue loaded session")
                }
                if (resumeSnapshot != null) {
                    OutlinedButton(
                        onClick = {
                            resumeSnapshot = null
                            result = null
                            statusMessage = "New session mode selected."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start as a new session instead") }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        processCoordinator.requestCancel(ResearchProcessIds.CLOSED_LOOP_TRAINING)
                        statusMessage = "Cancellation requested; stopping at a safe boundary…"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel safely") }
            }
            Text(
                statusMessage,
                modifier = Modifier.politeLiveRegion().testTag("training-closed-loop-status")
            )
        }

        progress?.let { update ->
            Text("Current cycle ${update.cycle} of at most ${update.maximumCycles}; ${update.datasetRows} available dataset rows; judged ${evaluationProfile.displayName}.")
            update.localTrainingProgress?.let { local -> Text("Current training: ${local.completedWorkUnits}/${local.totalWorkUnits} actual training work units · ${local.phase.name.lowercase().replace('_', ' ')}.") }
            Text("Session bar and rate use a planning scale of 1,000 progress points per allowed cycle, weighted 80% training and 20% verification, growth and finalization. These are not predictions, epochs or measured solver calls; early acceptance completes the session without executing unused cycles.", style = MaterialTheme.typography.bodySmall)
        }
        telemetryProgress?.let { mapped ->
            DiagnosticLoadingProgressCard(
                progressState = mapped,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Closed-Loop Training Running",
                        finishedTitle = "Closed-Loop Training Evidence",
                        progressSectionTitle = "Closed-Loop Progress",
                        completedLabel = "Session budget progress points",
                        remainingLabel = "Remaining budget progress points",
                        rateLabel = "Budget progress points per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Closed-loop training"
                    ),
                timeline = closedLoopTimeline(result?.phase ?: progress?.phase ?: ClosedLoopPhase.IDLE, result)
            )
        }

        result?.let { ClosedLoopResultSection(it) }

        if (sessions.isNotEmpty()) {
            TrainingDisclosureSection(
                title = "Stored closed-loop sessions",
                subtitle = "Open saved evidence without changing its identity. Interrupted sessions can continue only with the same dataset, training and gate contract; loop budgets may be adjusted and are recorded."
            ) {
                ExpandableSelectionCollection(
                    items = sessions,
                    initialVisibleCount = 12,
                    itemName = "closed-loop sessions",
                    isSelected = { it.sessionId == selectedSessionId },
                    toggleTestTag = "closed-loop-sessions-show-all"
                ) { session ->
                    ClosedLoopSelectionButton(
                        selected = selectedSessionId == session.sessionId,
                        label = "${session.sessionName} · ${session.outcome.humanLabel()} · ${closedLoopCycleCount(session.completedCycles)}",
                        enabled = activeJob == null,
                        onClick = { selectedSessionId = session.sessionId.takeUnless { selectedSessionId == it } }
                    )
                    if (selectedSessionId == session.sessionId) {
                        ClosedLoopSessionSummaryRows(session)
                        OutlinedButton(
                            onClick = {
                                sessionStorage.load(session.sessionId)?.let(::loadSnapshot)
                            },
                            enabled = activeJob == null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Load settings and evidence") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ClosedLoopResultSection(snapshot: ClosedLoopSessionSnapshot) {
    ProvideAutomaticFigureLibraryContext(
        collection = "closed-loop-training",
        analysisId = snapshot.sessionId,
        executionId = "closed-loop-${snapshot.sessionId}"
    ) {
        TrainingDisclosureSection(
            title = "Closed-loop decision",
            subtitle = "Best means greatest judged-profile validation macro-F1, then lowest disagreement on ties. This historical best may differ from the accepted latest model. A failed or cancelled session does not claim gate completion.",
            initiallyExpanded = true
        ) {
        ChartMetricRow("Outcome", snapshot.outcome.humanLabel())
        ChartMetricRow("Completed cycles", snapshot.completedCycles.toString())
        ChartMetricRow("Current dataset rows", snapshot.currentDatasetRows.toString())
        ChartMetricRow("Best judged-profile validation macro-F1", snapshot.bestMacroF1?.let(::closedLoopDecimal) ?: "N/A")
        ChartMetricRow(
            "Disagreement of that same best-validation model",
            snapshot.bestOracleDisagreementRate?.let(::closedLoopPercent) ?: "N/A"
        )
        ChartMetricRow("Best run", snapshot.bestRunId ?: "N/A")
        snapshot.latestEvidence?.let { evidence ->
            HorizontalDivider()
            ChartMetricRow("Latest run reporting-only test macro-F1", closedLoopDecimal(evidence.independentTestMacroF1))
            ChartMetricRow("Latest run reporting-only test accuracy", closedLoopPercent(evidence.independentTestAccuracy))
            ChartMetricRow("Latest run reporting-only test log loss", closedLoopDecimal(evidence.independentTestLogLoss))
        }
        Text(snapshot.statusMessage)
        snapshot.bestModelPath?.let { Text("Best model: $it", style = MaterialTheme.typography.bodySmall) }
        JargonAwareText("Resource telemetry is stored with this session as resource-telemetry.csv.", style = MaterialTheme.typography.bodySmall)

        val cyclePoints =
            snapshot.events
                .filter { it.phase == ClosedLoopPhase.COMPARING_WITH_ORACLE && it.macroF1 != null }
                .distinctBy { it.cycle }
                .map { ChartLinePoint(it.cycle.toDouble(), requireNotNull(it.macroF1)) }
        if (cyclePoints.size >= 2) {
            ProfessionalLineChart(
                title = "Closed-loop validation macro-F1 by cycle",
                subtitle = "Validation feedback controls the loop; test metrics are reported separately for each changing corpus.",
                points = cyclePoints,
                xAxisLabel = "Cycle",
                yAxisLabel = "Validation macro-F1",
                color = Color(0xFF2E7D32)
            )
        }

        ChartSectionCard(
            title = "Decision trace",
            subtitle = "Latest events from the persistent state-machine history."
        ) {
            ExpandableSelectionCollection(items = snapshot.events.reversed(), initialVisibleCount = 12,
                itemName = "events", isSelected = { false }, toggleTestTag = "closed-loop-events-all") { event ->
                TrainingDisclosureSection(title = "${event.eventIndex} · Cycle ${event.cycle} · attempt ${event.trainingAttempt} · ${event.phase.name.lowercase().replace('_', ' ')}",
                    subtitle = event.message) {
                    Text(event.message)
                    Text("Dataset rows: ${event.datasetRows}; rows trained: ${event.effectiveRows ?: "not recorded"}; judged profile: ${event.evaluationProfile?.displayName ?: "legacy not recorded"}.")
                    Text("Run ID: ${event.runId ?: "none"}; source corpus SHA-256: ${event.corpusSha256 ?: "not recorded"}.")
                    ClosedLoopArtifactLinks(snapshot.sessionId, event)
                }
            }
        }
        }
    }
}

@Composable
private fun ClosedLoopSessionSummaryRows(summary: ClosedLoopSessionSummary) {
    Text(DateFormat.getDateTimeInstance().format(Date(summary.updatedAtEpochMillis)))
    Text("Dataset: ${summary.datasetName} · ${summary.currentDatasetRows} rows")
    Text(
        "Best judged-profile validation F1 ${summary.bestMacroF1?.let(::closedLoopDecimal) ?: "N/A"} · " +
            "disagreement ${summary.bestOracleDisagreementRate?.let(::closedLoopPercent) ?: "N/A"}"
    )
    Text("Judged profile: ${summary.evaluationProfile?.displayName ?: "legacy not recorded"}. ${summary.statusMessage}")
    Text(summary.directoryPath, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ClosedLoopSelectionButton(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ClosedLoopCheckboxRow(
    checked: Boolean,
    label: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ClosedLoopNumericField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(128)) },
        enabled = enabled,
        label = { Text(label) },
        isError = trainingControlFieldError(label,value) != null,
        supportingText = { (trainingControlFieldError(label,value) ?: trainingControlHint(label))?.let { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildClosedLoopConfig(
    manifest: DatasetManifest?,
    sessionName: String,
    compareProfiles: Boolean,
    evaluationProfile: TrainingFeatureProfile,
    modelKind: TrainingModelKind,
    resourceMode: TrainingResourceMode,
    splitStrategy: TrainingSplitStrategy,
    trainingRowCapText: String,
    epochsText: String,
    batchSizeText: String,
    learningRateText: String,
    l2Text: String,
    hiddenUnitsText: String,
    seedText: String,
    patienceText: String,
    minimumMacroF1Text: String,
    maximumDisagreementPercentText: String,
    maximumCyclesText: String,
    rowsPerRobotIncrementText: String,
    maximumDatasetRowsText: String,
    inferenceRetriesText: String,
    trainingRetriesText: String,
    minimumImprovementText: String,
    stagnantCyclesText: String,
    timeBudgetMinutesText: String,
    autoGrowDataset: Boolean,
    workerCount: Int,
    safeTrainingRows: Int
): Pair<ClosedLoopTrainingConfig?, String?> {
    if (manifest == null || !File(manifest.csvPath).exists()) return null to "Select an existing managed dataset."
    if (sessionName.isBlank()) return null to "Session name must not be blank."
    fun integer(value: String, label: String): Int =
        value.toIntOrNull() ?: throw IllegalArgumentException("$label must be a whole number.")
    fun long(value: String, label: String): Long =
        value.toLongOrNull() ?: throw IllegalArgumentException("$label must be a whole number.")
    fun decimal(value: String, label: String): Double =
        value.toDoubleOrNull() ?: throw IllegalArgumentException("$label must be numeric.")

    return runCatching {
        val requestedTrainingRowCap = integer(trainingRowCapText, "Training row cap")
        val trainingRowCap = minOf(requestedTrainingRowCap, safeTrainingRows)
        val epochs = integer(epochsText, "Epochs")
        val batchSize = integer(batchSizeText, "Batch size")
        val learningRate = decimal(learningRateText, "Learning rate")
        val l2 = decimal(l2Text, "L2 regularization")
        val hiddenUnits = integer(hiddenUnitsText, "Hidden units")
        val seed = integer(seedText, "Random seed")
        val patience = integer(patienceText, "Early-stopping patience")
        val minimumMacroF1 = decimal(minimumMacroF1Text, "Minimum macro-F1")
        val disagreement = decimal(maximumDisagreementPercentText, "Maximum disagreement") / 100.0
        val maximumCycles = integer(maximumCyclesText, "Maximum cycles")
        val rowsIncrement = integer(rowsPerRobotIncrementText, "Rows per robot increment")
        val maximumDatasetRows = long(maximumDatasetRowsText, "Dataset row ceiling")
        val inferenceRetries = integer(inferenceRetriesText, "Inference retries")
        val trainingRetries = integer(trainingRetriesText, "Training retries")
        val minimumImprovement = decimal(minimumImprovementText, "Minimum improvement")
        val stagnantCycles = integer(stagnantCyclesText, "Stagnant cycles")
        val timeBudget = integer(timeBudgetMinutesText, "Time budget")

        val compatibility = com.robotkinematicslab.mobile.ml.data.TrainingDatasetCompatibilityEvaluator.evaluate(manifest,
            com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements(trainingRowCap.coerceIn(30, 1_000_000),
                if(splitStrategy == TrainingSplitStrategy.ROBOT_HELD_OUT) 3 else 1))
        require(compatibility.level != com.robotkinematicslab.mobile.ml.data.DatasetCompatibilityLevel.INCOMPATIBLE) {
            compatibility.reasons.joinToString("; ")
        }
        require(trainingRowCap in 30..1_000_000) { "Training row cap must be between 30 and 1,000,000." }
        require(epochs in 1..1_000) { "Epochs must be between 1 and 1,000." }
        require(batchSize in 1..8_192) { "Batch size must be between 1 and 8,192." }
        require(learningRate.isFinite() && learningRate in 1e-6..1.0) { "Learning rate is outside its safe range." }
        require(l2.isFinite() && l2 in 0.0..1.0) { "L2 regularization is outside its safe range." }
        require(hiddenUnits in 2..512) { "Hidden units must be between 2 and 512." }
        require(patience in 1..100) { "Patience must be between 1 and 100." }
        require(minimumMacroF1.isFinite() && minimumMacroF1 in 0.0..1.0) { "Macro-F1 threshold must be 0–1." }
        require(disagreement.isFinite() && disagreement in 0.0..1.0) { "Disagreement must be 0–100%." }
        require(maximumCycles in 1..100) { "Maximum cycles must be 1–100." }
        require(rowsIncrement in 1..1_000_000) { "Rows per robot must be 1–1,000,000." }
        require(maximumDatasetRows in 30L..10_000_000L) { "Dataset ceiling must be 30–10,000,000 rows." }
        require(maximumDatasetRows >= manifest.rowCount) { "Dataset ceiling cannot be below the current dataset size." }
        require(inferenceRetries in 0..10 && trainingRetries in 0..10) { "Retries must be 0–10." }
        require(minimumImprovement.isFinite() && minimumImprovement in 0.0..1.0) { "Minimum improvement must be 0–1." }
        require(stagnantCycles in 1..100) { "Stagnant cycles must be 1–100." }
        require(timeBudget in 0..100_000) { "Time budget must be 0–100,000 minutes." }

        val localConfig =
            LocalTrainingConfig(
                runName = sessionName.trim(),
                datasetPath = manifest.csvPath,
                compareFeatureProfiles = compareProfiles,
                singleFeatureProfile = evaluationProfile,
                modelKind = modelKind,
                resourceMode = resourceMode,
                splitStrategy = splitStrategy,
                maximumRows = trainingRowCap,
                epochs = epochs,
                batchSize = batchSize,
                learningRate = learningRate,
                l2Regularization = l2,
                hiddenUnits = hiddenUnits,
                randomSeed = seed,
                earlyStoppingPatience = patience,
                workerCount = workerCount,
                sampleAcrossEntireDataset = true
            )
        ClosedLoopTrainingConfig(
            sessionName = sessionName.trim(),
            datasetName = manifest.datasetName,
            localTrainingConfig = localConfig,
            evaluationProfile = evaluationProfile,
            maximumCycles = maximumCycles,
            samplesPerRobotIncrement = rowsIncrement,
            maximumDatasetRows = maximumDatasetRows,
            maximumInferenceRetries = inferenceRetries,
            maximumTrainingRetries = trainingRetries,
            minimumMacroF1 = minimumMacroF1,
            maximumOracleDisagreementRate = disagreement,
            minimumMacroF1Improvement = minimumImprovement,
            maximumStagnantCycles = stagnantCycles,
            maximumElapsedMinutes = timeBudget,
            automaticallyGrowDataset = autoGrowDataset
        ) to null
    }.getOrElse { error -> null to (error.message ?: "One or more closed-loop controls are invalid.") }
}

private fun mapClosedLoopProgress(
    update: ClosedLoopProgress,
    startedAtMillis: Long,
    telemetrySampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val total = update.maximumCycles * 1_000
    val completed = (update.fraction * total).toInt().coerceIn(0, total)
    val elapsed = ((System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)) / 1_000.0
    val rate = if (elapsed > 0.0) completed / elapsed else 0.0
    val remaining = (total - completed).coerceAtLeast(0)
    val terminal = update.phase in setOf(
        ClosedLoopPhase.COMPLETED,
        ClosedLoopPhase.MANUAL_INTERVENTION,
        ClosedLoopPhase.CANCELLED,
        ClosedLoopPhase.FAILED
    )
    val phase =
        when (update.phase) {
            ClosedLoopPhase.IDLE,
            ClosedLoopPhase.VALIDATING_DATASET -> DiagnosticProgressPhase.PLANNING
            ClosedLoopPhase.TRAINING_MODEL,
            ClosedLoopPhase.VERIFYING_INFERENCE,
            ClosedLoopPhase.RETRYING -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
            ClosedLoopPhase.COMPARING_WITH_ORACLE,
            ClosedLoopPhase.GROWING_DATASET -> DiagnosticProgressPhase.AGGREGATING
            ClosedLoopPhase.COMPLETED -> DiagnosticProgressPhase.COMPLETED
            ClosedLoopPhase.MANUAL_INTERVENTION,
            ClosedLoopPhase.CANCELLED,
            ClosedLoopPhase.FAILED -> DiagnosticProgressPhase.FAILED
        }
    return DiagnosticProgressState(
        isRunning = !terminal,
        phase = phase,
        completedRuns = completed,
        totalRuns = total,
        currentLinkCount = null,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsed,
        message = "Cycle ${update.cycle}/${update.maximumCycles} · ${update.message}",
        telemetry = telemetrySampler.sample()
    )
}

internal fun closedLoopTimeline(phase: ClosedLoopPhase, snapshot: ClosedLoopSessionSnapshot? = null): List<DiagnosticTimelineStep> {
    val ordered = listOf(
        ClosedLoopPhase.VALIDATING_DATASET to "Validate managed dataset and provenance",
        ClosedLoopPhase.TRAINING_MODEL to "Train; report validation and per-run test",
        ClosedLoopPhase.VERIFYING_INFERENCE to "Reload model and verify inference",
        ClosedLoopPhase.COMPARING_WITH_ORACLE to "Compare saved IK validation labels",
        ClosedLoopPhase.GROWING_DATASET to "Optional dataset growth",
        ClosedLoopPhase.COMPLETED to "Commit session decision")
    val terminal = phase in setOf(ClosedLoopPhase.COMPLETED, ClosedLoopPhase.MANUAL_INTERVENTION, ClosedLoopPhase.CANCELLED, ClosedLoopPhase.FAILED)
    val cycle = snapshot?.events?.maxOfOrNull { it.cycle }
    val events = snapshot?.events?.filter { it.cycle == cycle }.orEmpty()
    val visited = events.map { it.phase }.toSet()
    val lastObserved = events.lastOrNull { it.phase in ordered.map { pair -> pair.first } }?.phase
    val current = if(phase == ClosedLoopPhase.RETRYING) 1 else ordered.indexOfFirst { it.first == phase }
    return ordered.mapIndexed { index, (step, label) ->
        val status = if(snapshot != null && terminal) when {
            step == ClosedLoopPhase.COMPLETED -> when(snapshot.outcome) {
                ClosedLoopOutcome.ACCEPTED -> DiagnosticTimelineStatus.COMPLETE
                ClosedLoopOutcome.FAILED -> DiagnosticTimelineStatus.FAILED
                ClosedLoopOutcome.CANCELLED -> DiagnosticTimelineStatus.CANCELLED
                else -> DiagnosticTimelineStatus.PENDING
            }
            step !in visited -> DiagnosticTimelineStatus.PENDING
            step == lastObserved && phase == ClosedLoopPhase.CANCELLED -> DiagnosticTimelineStatus.CANCELLED
            step == lastObserved && phase == ClosedLoopPhase.FAILED -> DiagnosticTimelineStatus.FAILED
            else -> DiagnosticTimelineStatus.COMPLETE
        } else when {
            terminal -> DiagnosticTimelineStatus.PENDING
            index < current -> DiagnosticTimelineStatus.COMPLETE
            index == current -> DiagnosticTimelineStatus.RUNNING
            else -> DiagnosticTimelineStatus.PENDING
        }
        DiagnosticTimelineStep(label, status,
            description = if(snapshot != null) {
                events.lastOrNull { it.phase == step }?.message ?: "This stage was not recorded in the displayed cycle. ${snapshot.statusMessage}"
            } else "Current-cycle activity. Growth is optional; final completion requires a committed session checkpoint.")
    }
}

private fun closedLoopDecimal(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.4f", value) else "N/A"

private fun closedLoopPercent(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100.0) else "N/A"
