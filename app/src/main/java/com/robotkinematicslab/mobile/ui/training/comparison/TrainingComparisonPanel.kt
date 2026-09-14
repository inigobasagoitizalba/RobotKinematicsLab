package com.robotkinematicslab.mobile.ui.training.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import com.robotkinematicslab.mobile.ui.shared.EvidenceSelectionDropdown
import com.robotkinematicslab.mobile.ui.training.StoredModelChoice
import com.robotkinematicslab.mobile.ui.training.storedModelChoices
import com.robotkinematicslab.mobile.ml.comparison.ComparisonEligibility
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.ml.comparison.ComparisonLoadPhase
import com.robotkinematicslab.mobile.ml.comparison.ComparisonPointFilter
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonCancelledException
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonLoadProgress
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonLoader
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonPoint
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonSession
import com.robotkinematicslab.mobile.ml.comparison.ModelPointPrediction
import com.robotkinematicslab.mobile.ml.comparison.filteredBy
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.render.RobotScene3D
import com.robotkinematicslab.mobile.render.RobotSceneLegend
import com.robotkinematicslab.mobile.render.robotWorkspaceRadius
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import com.robotkinematicslab.mobile.ui.training.TrainingDisclosureSection
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ComparisonPoseMode(val displayName: String) {
    INITIAL("Initial seed pose"),
    ORACLE_SOLUTION("Deterministic IK result")
}

internal fun selectedRunIdForRequest(
    runs: List<TrainingRunSummary>,
    requestedRunId: String?
): String? =
    if (requestedRunId == null) runs.firstOrNull()?.runId
    else runs.firstOrNull { it.runId == requestedRunId }?.runId

@Composable
fun TrainingComparisonPanel(
    modifier: Modifier = Modifier,
    initialRunId: String? = null
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val repository = remember(context) { TrainingStorageRepository(context) }
    val loader = remember(repository) { ModelComparisonLoader(repository) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val cancellation = remember { AtomicBoolean(false) }
    var runs by remember { mutableStateOf(repository.listRuns()) }
    val initiallySelectedRun = runs.firstOrNull { it.runId == selectedRunIdForRequest(runs, initialRunId) }
    var selectedRunId by rememberSaveable(initialRunId) { mutableStateOf(initiallySelectedRun?.runId) }
    var leftModelPath by rememberSaveable(initialRunId) { mutableStateOf(initiallySelectedRun?.modelPaths?.firstOrNull()) }
    var rightModelPath by rememberSaveable(initialRunId) { mutableStateOf(initiallySelectedRun?.modelPaths?.getOrNull(1)) }
    var session by remember { mutableStateOf<ModelComparisonSession?>(null) }
    var progress by remember { mutableStateOf<ModelComparisonLoadProgress?>(null) }
    var telemetryProgress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    val performanceSamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var statusMessage by remember(initialRunId) {
        mutableStateOf(
            if (initialRunId != null && initiallySelectedRun == null) {
                "The requested saved run is unavailable. No different run was substituted."
            } else {
                "Choose a stored run and any two of its trained feature sets."
            }
        )
    }
    var startedAtMillis by remember { mutableLongStateOf(0L) }
    var filter by rememberSaveable { mutableStateOf(ComparisonPointFilter.ALL) }
    var selectedSourceRow by rememberSaveable { mutableStateOf<Long?>(null) }
    var poseMode by rememberSaveable { mutableStateOf(ComparisonPoseMode.ORACLE_SOLUTION) }

    val selectedRun = runs.firstOrNull { it.runId == selectedRunId }
    var modelChoices by remember { mutableStateOf<List<StoredModelChoice>>(emptyList()) }
    var eligibility by remember { mutableStateOf<ComparisonEligibility?>(null) }
    var validationMessage by remember { mutableStateOf("Choose a run and two distinct models.") }
    var validating by remember { mutableStateOf(false) }
    var refreshRevision by remember { mutableStateOf(0) }
    val selectionKey = Triple(selectedRunId,leftModelPath,rightModelPath)
    var validatedKey by remember { mutableStateOf<Triple<String?,String?,String?>?>(null) }
    LaunchedEffect(selectionKey,refreshRevision) {
        eligibility=null;validatedKey=null;validating=true
        val run=selectedRun
        if(run==null) { modelChoices=emptyList();validationMessage="Choose an available saved run.";validating=false;return@LaunchedEffect }
        val jobContext=currentCoroutineContext()
        modelChoices=withContext(Dispatchers.IO) { storedModelChoices(repository,run) }
        val checked=withContext(Dispatchers.IO) { runCatching { loader.validateSelection(run,leftModelPath,rightModelPath) { !jobContext.isActive } } }
        currentCoroutineContext().ensureActive()
        eligibility=checked.getOrNull();validatedKey=selectionKey;validating=false
        validationMessage=checked.fold({ "Verified: ${it.testCount} identical test rows, order, labels and original replay contracts." },{ it.message ?: "This model pair cannot be verified." })
    }

    val filteredPoints = remember(session, filter) { session?.points.orEmpty().filteredBy(filter) }
    val selectedPoint =
        filteredPoints.firstOrNull { it.sourceRowIndex == selectedSourceRow }
            ?: filteredPoints.firstOrNull()
    LaunchedEffect(filteredPoints, selectedPoint?.sourceRowIndex) {
        selectedSourceRow = selectedPoint?.sourceRowIndex
    }

    fun startComparison() {
        if (processCoordinator.isActive(ResearchProcessIds.TRAINING_COMPARISON)) {
            statusMessage = "A visual comparison is already being prepared. Open Research activity to inspect it."
            return
        }
        if(eligibility==null || validatedKey!=selectionKey || validating) { statusMessage=validationMessage;return }
        val run = runs.firstOrNull { it.runId == selectedRunId } ?: return
        val checkedEligibility=eligibility
        val requestedLeft=leftModelPath
        val requestedRight=rightModelPath
        cancellation.set(false)
        session = null
        progress = null
        telemetryProgress = null
        performanceSamples.clear()
        telemetryCaptureGate.reset()
        selectedSourceRow = null
        filter = ComparisonPointFilter.ALL
        startedAtMillis = System.currentTimeMillis()
        statusMessage = "Rebuilding the held-out experiment evidence…"
        activeJob =
            processCoordinator.launch(
                id = ResearchProcessIds.TRAINING_COMPARISON,
                title = "Held-out model comparison",
                kind = ResearchProcessKind.ANALYSIS,
                cancellationAction = { cancellation.set(true) }
            ) { process ->
                process.report(0.0, "Rebuilding held-out evidence", statusMessage)
                try {
                    val loaded =
                        withContext(Dispatchers.IO) {
                            loader.load(
                                run = run,
                                expectedEligibility = checkedEligibility,
                                leftModelPath = requestedLeft,
                                rightModelPath = requestedRight,
                                cancellationRequested = cancellation::get,
                                onProgress = { update ->
                                    process.report(
                                        progressFraction = update.fraction,
                                        stage = update.phase.name.lowercase().replace('_', ' '),
                                        detail = update.message
                                    )
                                    if (
                                        telemetryCaptureGate.shouldCapture(
                                            force = update.phase == ComparisonLoadPhase.COMPLETED
                                        )
                                    ) {
                                        scope.launch {
                                            progress = update
                                            val mapped = mapComparisonProgress(update, startedAtMillis, telemetrySampler)
                                            telemetryProgress = mapped
                                            performanceSamples +=
                                                DiagnosticPerformanceSample.fromProgressState(
                                                    sampleIndex = performanceSamples.size + 1,
                                                    progressState = mapped
                                                )
                                        }
                                    }
                                }
                            )
                        }
                    session = loaded
                    selectedSourceRow = loaded.points.firstOrNull()?.sourceRowIndex
                    statusMessage =
                        "Ready: ${loaded.points.size} visual points from ${loaded.aggregate.heldOutPointCount} held-out comparisons."
                    process.completed(statusMessage)
                } catch (_: ModelComparisonCancelledException) {
                    statusMessage = "Comparison loading cancelled safely."
                    process.cancelled(statusMessage)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    process.cancelled("Comparison loading was cancelled safely.")
                } catch (error: Exception) {
                    statusMessage = error.message ?: "The comparison could not be loaded."
                    process.failed(statusMessage)
                } finally {
                    activeJob = null
                }
            }.getOrElse { error ->
                statusMessage = error.message ?: "The comparison could not start."
                null
            }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Compare trained models", style = MaterialTheme.typography.headlineSmall)
        JargonHelpNotice()
        JargonAwareText(
            "Select a saved run, rebuild its held-out test set, then inspect both predictions on exactly the same robot-target pair. The deterministic IK reference is the trusted answer used to judge both models.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrainingDisclosureSection(title="1 · Choose a saved run",subtitle=selectedRun?.runName ?: "No run selected",modifier=Modifier.testTag("training-comparison-controls").tutorialAnchor(TutorialTargets.TrainingComparison)) {
            EvidenceSelectionDropdown("Training run",runs,selectedRun,{ "${it.runName} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it.startedAtEpochMillis))}" },
                onSelected={ run -> selectedRunId=run.runId;leftModelPath=run.modelPaths.firstOrNull();rightModelPath=run.modelPaths.getOrNull(1);session=null;selectedSourceRow=null },enabled=activeJob==null,testTag="comparison-run-selector")
            selectedRun?.let { run ->
                Text("${run.modelPaths.size} saved model(s) · ${run.splitStrategy.displayName} · seed ${run.randomSeed} · row cap ${run.maximumRows}")
                run.variants.forEach { variant -> Text("${variant.featureSelectionName}: ${variant.trainRowCount} train / ${variant.validationRowCount} validation / ${variant.testRowCount} test rows; test macro-F1 ${String.format(Locale.US,"%.2f%%",variant.testMacroF1*100)}") }
                Text("Run ID: ${run.runId}",style=MaterialTheme.typography.bodySmall)
            }
            if(initialRunId!=null && selectedRun==null) Text("The requested run is unavailable. No other run was substituted.")
            OutlinedButton(onClick={ runs=repository.listRuns();refreshRevision+=1;session=null },enabled=activeJob==null,modifier=Modifier.fillMaxWidth()) { Text("Refresh stored runs") }
        }
        TrainingDisclosureSection(title="2 · Choose two distinct models",subtitle=eligibility?.let { "Comparing: ${it.leftLabel} ↔ ${it.rightLabel}" } ?: "Both models must share the verified test population") {
            EvidenceSelectionDropdown("Left model",modelChoices,modelChoices.firstOrNull { it.path==leftModelPath },StoredModelChoice::label,
                onSelected={ leftModelPath=it.path;session=null },enabled=activeJob==null,testTag="comparison-left-model")
            EvidenceSelectionDropdown("Right model",modelChoices,modelChoices.firstOrNull { it.path==rightModelPath },StoredModelChoice::label,
                onSelected={ rightModelPath=it.path;session=null },enabled=activeJob==null,testTag="comparison-right-model")
            Text("● Left series",color=BaselineColor);Text("◆ Right series",color=ContextColor)
            Text("Selection controls use one style. Series retain these side-specific colors throughout the figures.",style=MaterialTheme.typography.bodySmall)
            modelChoices.filter { it.path==leftModelPath || it.path==rightModelPath }.forEach { Text("Technical model ID: ${it.technicalId}",style=MaterialTheme.typography.bodySmall) }
        }
        Text(if(validating) "Verifying original test evidence…" else validationMessage,modifier=Modifier.politeLiveRegion().testTag("comparison-eligibility"))

        if (activeJob == null) {
            Button(
                    onClick = ::startComparison,
                    enabled = eligibility!=null && validatedKey==selectionKey && !validating,
                    modifier = Modifier.fillMaxWidth().testTag("load-visual-comparison")
                ) { Text("3 · Load comparison") }
        } else {
            OutlinedButton(
                    onClick = {
                        processCoordinator.requestCancel(ResearchProcessIds.TRAINING_COMPARISON)
                        statusMessage = "Cancellation requested…"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel comparison loading") }
            }
        Text(statusMessage, modifier = Modifier.politeLiveRegion().testTag("training-comparison-status"))

        telemetryProgress?.let { mapped ->
            DiagnosticLoadingProgressCard(
                progressState = mapped,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Visual Comparison Loading",
                        finishedTitle = "Visual Comparison Load Evidence",
                        progressSectionTitle = "Comparison preparation",
                        completedLabel = "Completed work units",
                        remainingLabel = "Work units left",
                        rateLabel = "Work units per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Model visual comparison"
                    ),
                timeline = comparisonTimeline(progress?.phase)
            )
        }

        session?.let { loaded ->
            ComparisonWorkspaceControls(
                session = loaded,
                filter = filter,
                onFilterChange = { filter = it },
                filteredPoints = filteredPoints,
                selectedPoint = selectedPoint,
                onPointSelected = { selectedSourceRow = it.sourceRowIndex },
                poseMode = poseMode,
                onPoseModeChange = { poseMode = it }
            )
            selectedPoint?.let { point ->
                PointComparisonWorkspace(point, poseMode, loaded.baselineLabel, loaded.contextLabel)
            }
            TrainingDisclosureSection(
                title = "Model comparison evidence",
                subtitle = "Held-out aggregate and paired scientific charts.",
                initiallyExpanded = true
            ) {
                val executionId =
                    remember(loaded) {
                        "comparison-" +
                            ChartFigureExporter.automaticDataFingerprint(
                                listOf(
                                    loaded.run.runId,
                                    loaded.baselineSelectionId,
                                    loaded.contextSelectionId,
                                    loaded.aggregate,
                                    loaded.history
                                )
                            )
                    }
                ProvideAutomaticFigureLibraryContext(
                    collection = "model-comparison",
                    analysisId = loaded.run.runId,
                    executionId = executionId
                ) {
                    ComparisonMetricChanges(loaded)
                    TrainingComparisonCharts(loaded)
                }
            }
        }
    }
}

@Composable
private fun RunChoice(
    run: TrainingRunSummary,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label =
        "${run.runName} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(run.startedAtEpochMillis))}\n" +
            "Baseline F1 ${number(run.baselineTestMacroF1)} · Context F1 ${number(run.contextTestMacroF1)} · Δ ${signed(run.macroF1Delta)}"
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ComparisonWorkspaceControls(
    session: ModelComparisonSession,
    filter: ComparisonPointFilter,
    onFilterChange: (ComparisonPointFilter) -> Unit,
    filteredPoints: List<ModelComparisonPoint>,
    selectedPoint: ModelComparisonPoint?,
    onPointSelected: (ModelComparisonPoint) -> Unit,
    poseMode: ComparisonPoseMode,
    onPoseModeChange: (ComparisonPoseMode) -> Unit
) {
    TrainingDisclosureSection(
        title = "2 · Held-out point browser",
        subtitle = "Tap any point to render only that robot and its stored evidence. The complete set is never rendered simultaneously."
    ) {
        ComparisonPointFilter.entries.chunked(2).forEach { filters ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { item ->
                    ChoiceButton(
                        selected = filter == item,
                        label = item.displayName,
                        onClick = { onFilterChange(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        ChartMetricRow("Matching points", "${filteredPoints.size} / ${session.points.size}")
        if (filteredPoints.isEmpty()) {
            Text("No held-out point matches this filter.")
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(94.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPoints, key = ModelComparisonPoint::sourceRowIndex) { point ->
                    val label =
                        "#${point.globalRowIndex}\n${point.robot.name}\n" +
                            "B ${short(point.baselinePrediction.predictedLabel)} · C ${short(point.contextPrediction.predictedLabel)}"
                    ChoiceButton(
                        selected = point.sourceRowIndex == selectedPoint?.sourceRowIndex,
                        label = label,
                        onClick = { onPointSelected(point) },
                        modifier = Modifier.width(178.dp).testTag("comparison-point-${point.sourceRowIndex}")
                    )
                }
            }
            val currentIndex = selectedPoint?.let(filteredPoints::indexOf) ?: 0
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onPointSelected(filteredPoints[(currentIndex - 1).coerceAtLeast(0)]) },
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Previous point") }
                OutlinedButton(
                    onClick = { onPointSelected(filteredPoints[(currentIndex + 1).coerceAtMost(filteredPoints.lastIndex)]) },
                    enabled = currentIndex < filteredPoints.lastIndex,
                    modifier = Modifier.weight(1f)
                ) { Text("Next point") }
            }
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComparisonPoseMode.entries.forEach { mode ->
                ChoiceButton(
                    selected = poseMode == mode,
                    label = mode.displayName,
                    onClick = { onPoseModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PointComparisonWorkspace(
    point: ModelComparisonPoint,
    poseMode: ComparisonPoseMode,
    leftLabel: String,
    rightLabel: String
) {
    val state = if (poseMode == ComparisonPoseMode.INITIAL) point.seedState else point.solutionState
    val service = remember(point.robot) { KinematicsService() }
    val fk = remember(point.sourceRowIndex, poseMode) { service.computeFK(point.robot, state) }
    val workspaceRadius = remember(point.robot) { robotWorkspaceRadius(point.robot) }

    TrainingDisclosureSection(
        title = "3 · Point #${point.globalRowIndex} · ${point.robot.name}",
        subtitle =
            "Current models classify deterministic IK reliability; they do not generate separate joint trajectories. " +
                "Both panes therefore show the same physical evidence while their predictions and confidence differ."
    ) {
        ChartMetricRow("Robot / target class", "${point.robotId} · ${point.targetClass}")
        ChartMetricRow("Deterministic IK reference (oracle)", "${point.oracleLabel} · ${point.oracleStatus}")
        ChartMetricRow("Detail", point.oracleDetailCode)
        ChartMetricRow("Final Cartesian error", meters(point.finalErrorMeters))
        ChartMetricRow("Iterations / solve time", "${point.iterations} · ${duration(point.solveDurationNanos)}")
        ChartMetricRow("Target", vector(point.target.x, point.target.y, point.target.z))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val paneWidth = if (maxWidth >= 700.dp) (maxWidth - 12.dp) / 2 else 340.dp
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModelPane(
                    title = leftLabel,
                    prediction = point.baselinePrediction,
                    point = point,
                    jointState = state,
                    jointPositions = fk.jointPositions,
                    workspaceRadius = workspaceRadius,
                    accent = BaselineColor,
                    modifier = Modifier.width(paneWidth).testTag("baseline-comparison-pane")
                )
                ModelPane(
                    title = rightLabel,
                    prediction = point.contextPrediction,
                    point = point,
                    jointState = state,
                    jointPositions = fk.jointPositions,
                    workspaceRadius = workspaceRadius,
                    accent = ContextColor,
                    modifier = Modifier.width(paneWidth).testTag("context-comparison-pane")
                )
            }
        }
        RobotSceneLegend(workspaceRadius)
        Text(
            "Drag either 3D pane to inspect its camera. The selected pose and target remain identical, preserving a fair visual comparison.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModelPane(
    title: String,
    prediction: ModelPointPrediction,
    point: ModelComparisonPoint,
    jointState: RobotState,
    jointPositions: List<com.robotkinematicslab.mobile.math.utility.Vec3>,
    workspaceRadius: Double,
    accent: Color,
    modifier: Modifier
) {
    Column(
        modifier = modifier.background(accent.copy(alpha = 0.09f), RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        Text(
            "Prediction: ${prediction.predictedLabel}",
            style = MaterialTheme.typography.titleSmall,
            color = if (prediction.correct) Color(0xFF1B5E20) else Color(0xFFB71C1C),
            fontWeight = FontWeight.Bold
        )
        ChartMetricRow("Reference label", point.oracleLabel.name)
        ChartMetricRow("Correct", if (prediction.correct) "YES" else "NO")
        ChartMetricRow("Confidence", percent(prediction.confidence))
        ChartMetricRow("Probability assigned to correct label", percent(prediction.probabilityAssignedToTruth))
        TrainingLabel.entries.forEachIndexed { index, label ->
            val probability = prediction.probabilities.getOrElse(index) { 0.0 }.coerceIn(0.0, 1.0)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label.name, style = MaterialTheme.typography.labelSmall)
                    Text(percent(probability), style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(
                    progress = { probability.toFloat() },
                    color = accent,
                    modifier = Modifier.fillMaxWidth().height(7.dp)
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(270.dp).background(Color(0xFF101820), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            RobotScene3D(
                jointPositions = jointPositions,
                jointTypes = point.robot.joints.map { it.type },
                targetPoint = point.target,
                endEffector = jointPositions.lastOrNull(),
                workspaceRadius = workspaceRadius,
                enabled = false,
                onTargetSelected = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            "q = ${jointState.jointValues.joinToString(prefix = "[", postfix = "]") { number(it) }}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = true,
        onClick = onClick,
        modifier = modifier
    )
}

private fun List<TrainingRunSummary>.comparableOnly(): List<TrainingRunSummary> =
    filter { run -> run.modelPaths.size >= 2 }

private fun mapComparisonProgress(
    update: ModelComparisonLoadProgress,
    startedAtMillis: Long,
    sampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val elapsed = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L) / 1_000.0
    val rate = if (elapsed > 0.0) update.completedWorkUnits / elapsed else 0.0
    val remaining = (update.totalWorkUnits - update.completedWorkUnits).coerceAtLeast(0)
    return DiagnosticProgressState(
        isRunning = update.phase != ComparisonLoadPhase.COMPLETED,
        phase =
            when (update.phase) {
                ComparisonLoadPhase.LOADING_BASELINE,
                ComparisonLoadPhase.LOADING_CONTEXT -> DiagnosticProgressPhase.PLANNING
                ComparisonLoadPhase.REBUILDING_TEST_SPLIT -> DiagnosticProgressPhase.AUDITING_TOPOLOGY
                ComparisonLoadPhase.READING_VISUAL_EVIDENCE -> DiagnosticProgressPhase.GENERATING_TARGETS
                ComparisonLoadPhase.RUNNING_INFERENCE -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
                ComparisonLoadPhase.COMPLETED -> DiagnosticProgressPhase.COMPLETED
            },
        completedRuns = update.completedWorkUnits,
        totalRuns = update.totalWorkUnits,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsed,
        message = update.message,
        telemetry = sampler.sample()
    )
}

private fun comparisonTimeline(current: ComparisonLoadPhase?): List<DiagnosticTimelineStep> {
    val steps =
        listOf(
            ComparisonLoadPhase.LOADING_BASELINE to "Validate baseline features",
            ComparisonLoadPhase.LOADING_CONTEXT to "Validate context features",
            ComparisonLoadPhase.REBUILDING_TEST_SPLIT to "Rebuild held-out split",
            ComparisonLoadPhase.READING_VISUAL_EVIDENCE to "Read robot evidence",
            ComparisonLoadPhase.RUNNING_INFERENCE to "Run paired inference"
        )
    val currentIndex = steps.indexOfFirst { it.first == current }
    return steps.mapIndexed { index, (_, label) ->
        DiagnosticTimelineStep(
            label = label,
            status =
                when {
                    current == ComparisonLoadPhase.COMPLETED || (currentIndex >= 0 && index < currentIndex) -> DiagnosticTimelineStatus.COMPLETE
                    index == currentIndex -> DiagnosticTimelineStatus.RUNNING
                    else -> DiagnosticTimelineStatus.PENDING
                }
        )
    }
}

private fun short(label: TrainingLabel): String =
    when (label) {
        TrainingLabel.ACCEPTED -> "A"
        TrainingLabel.UNCERTAIN -> "U"
        TrainingLabel.REJECTED -> "R"
    }

private fun number(value: Double?): String =
    value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.4f", it) } ?: "N/A"

private fun signed(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%+.4f", value) else "N/A"

private fun percent(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100.0) else "N/A"

private fun meters(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.8f m", value) else "N/A"

private fun duration(nanos: Long): String = String.format(Locale.US, "%.3f ms", nanos / 1_000_000.0)

private fun vector(x: Double, y: Double, z: Double): String =
    String.format(Locale.US, "(%.4f, %.4f, %.4f) m", x, y, z)
