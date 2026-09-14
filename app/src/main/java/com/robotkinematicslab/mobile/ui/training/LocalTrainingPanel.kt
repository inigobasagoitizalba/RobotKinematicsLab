package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.data.SingleRunDatasetPlanner
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.data.DatasetCompatibilityLevel
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetCompatibilityEvaluator
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingCancelledException
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.LocalTrainingPhase
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress
import com.robotkinematicslab.mobile.ml.training.LocalTrainingRunResult
import com.robotkinematicslab.mobile.ml.training.TrainedProfileResult
import com.robotkinematicslab.mobile.ml.training.TrainingIterationMetrics
import com.robotkinematicslab.mobile.ml.training.TrainingControlContract
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.performance.compute.ComputeRuntimeGuard
import com.robotkinematicslab.mobile.performance.compute.AndroidPerformanceHintReporter
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchResultReference
import com.robotkinematicslab.mobile.process.ResearchResultDestination
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLegend
import com.robotkinematicslab.mobile.ui.input.ScientificNumberParser
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.training.comparison.TrainingComparisonPanel
import com.robotkinematicslab.mobile.ui.training.explainability.ExplainabilityPanel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TrainingLabMode {
    CONTROLLED_SINGLE_RUN,
    CLOSED_LOOP_AUTOMATION,
    RESULT_COMPARISON,
    EXPLAINABILITY,
    ONE_MICRON_IK,
    SCIENTIFIC_EVIDENCE
}

/** Maps a live coach target to the existing training surface that owns it. */
internal fun trainingModeForTutorialTarget(target: TutorialTargetId?): TrainingLabMode? =
    when (target) {
        TutorialTargets.TrainingSingleRun,
        TutorialTargets.TrainingSingleExecution -> TrainingLabMode.CONTROLLED_SINGLE_RUN

        TutorialTargets.TrainingClosedLoop -> TrainingLabMode.CLOSED_LOOP_AUTOMATION
        TutorialTargets.TrainingComparison -> TrainingLabMode.RESULT_COMPARISON
        TutorialTargets.TrainingExplainability -> TrainingLabMode.EXPLAINABILITY
        TutorialTargets.TrainingOneMicron -> TrainingLabMode.ONE_MICRON_IK
        TutorialTargets.TrainingScientificEvidence -> TrainingLabMode.SCIENTIFIC_EVIDENCE
        else -> null
    }

@Composable
fun LocalTrainingPanel(
    modifier: Modifier = Modifier,
    initialMode: TrainingLabMode = TrainingLabMode.CLOSED_LOOP_AUTOMATION,
    onModeChanged: (TrainingLabMode) -> Unit = {},
    tutorialTarget: TutorialTargetId? = null,
    initialTrainingRunId: String? = null,
    onOpenDatasetQuality: (() -> Unit)? = null
) {
    val tutorialReporter = LocalTutorialActionReporter.current
    val modeStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    LaunchedEffect(initialMode) {
        // An owning route can request an exact destination (for example Models -> Compare or
        // Training results -> Single run). A restored child state must not override that request.
        mode = initialMode
    }
    LaunchedEffect(tutorialTarget) {
        trainingModeForTutorialTarget(tutorialTarget)?.let { requestedMode ->
            mode = requestedMode
            onModeChanged(requestedMode)
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("training-root")
                .tutorialAnchor(TutorialTargets.Training),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactSelectionMenu(
            options = TrainingLabMode.entries,
            selected = mode,
            label = { option ->
                when (option) {
                    TrainingLabMode.CLOSED_LOOP_AUTOMATION -> "Closed loop"
                    TrainingLabMode.CONTROLLED_SINGLE_RUN -> "Single run"
                    TrainingLabMode.RESULT_COMPARISON -> "Compare"
                    TrainingLabMode.EXPLAINABILITY -> "Explainable AI"
                    TrainingLabMode.ONE_MICRON_IK -> "Verified 1 µm IK"
                    TrainingLabMode.SCIENTIFIC_EVIDENCE -> "Scientific evidence"
                }
            },
            onSelected = { selectedMode ->
                if (selectedMode != mode) {
                    mode = selectedMode
                    onModeChanged(selectedMode)
                    tutorialReporter.report(
                        target = TutorialTargets.TrainingModeMenu,
                        interaction = TutorialInteraction.CHOOSE,
                        detail = "Training mode selected: ${selectedMode.name}."
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .testTag("training-mode-menu")
                    .tutorialAnchor(TutorialTargets.TrainingModeMenu),
            optionTestTag = { option -> "training-mode-${option.name.lowercase()}" },
        )
        modeStateHolder.SaveableStateProvider(mode.name) {
        when (mode) {
            TrainingLabMode.CLOSED_LOOP_AUTOMATION ->
                ClosedLoopTrainingPanel(
                    Modifier
                        .fillMaxSize()
                        .testTag("training-closed-loop")
                        .tutorialAnchor(TutorialTargets.TrainingClosedLoop)
                )
            TrainingLabMode.CONTROLLED_SINGLE_RUN ->
                ControlledSingleRunTrainingPanel(
                    Modifier
                        .fillMaxSize()
                        .testTag("training-single-run")
                        .tutorialAnchor(TutorialTargets.TrainingSingleRun)
                )
            TrainingLabMode.RESULT_COMPARISON ->
                TrainingComparisonPanel(
                    initialRunId = initialTrainingRunId,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("training-comparison")
                        .tutorialAnchor(TutorialTargets.TrainingComparison)
                )
            TrainingLabMode.EXPLAINABILITY ->
                ExplainabilityPanel(
                    Modifier
                        .fillMaxSize()
                        .testTag("training-explainability")
                        .tutorialAnchor(TutorialTargets.TrainingExplainability)
                )
            TrainingLabMode.ONE_MICRON_IK ->
                OneMicronIkTrainingPanel(
                    Modifier
                        .fillMaxSize()
                        .testTag("training-one-micron")
                        .tutorialAnchor(TutorialTargets.TrainingOneMicron)
                )
            TrainingLabMode.SCIENTIFIC_EVIDENCE ->
                ScientificEvidencePanel(
                    onOpenExperiment = { mode = it },
                    onOpenDatasetQuality = onOpenDatasetQuality,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("training-scientific-evidence")
                        .tutorialAnchor(TutorialTargets.TrainingScientificEvidence)
                )
        }
        }
    }
}

@Composable
private fun ControlledSingleRunTrainingPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val datasetRepository = remember(context) { DatasetStorageRepository(context) }
    val trainingRepository = remember(context) { TrainingStorageRepository(context) }
    val computeGuard = remember(context) { ComputeRuntimeGuard(context) }
    val engine = remember(context) {
        LocalTrainingEngine(
            runtimeWorkerLimit = computeGuard::currentWorkerLimit,
            workCycleReporterFactory = { AndroidPerformanceHintReporter(context) }
        )
    }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val cancellation = remember { AtomicBoolean(false) }

    var manifests by remember { mutableStateOf(datasetRepository.listManifests()) }
    var selectedManifest by remember { mutableStateOf(manifests.firstOrNull()) }
    var reviewManifest by remember { mutableStateOf<DatasetManifest?>(null) }
    var validationRefresh by remember { mutableStateOf(0) }
    var validatedDigest by remember { mutableStateOf<String?>(null) }
    var validatedPath by remember { mutableStateOf<String?>(null) }
    var validatedRequirements by remember { mutableStateOf<TrainingDatasetRequirements?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var validatingDataset by remember { mutableStateOf(false) }
    var safeRowLimit by remember { mutableStateOf(computeGuard.currentPolicy().estimatedSafeTrainingRows) }
    var savedRuns by remember { mutableStateOf(trainingRepository.listRuns()) }
    var runName by remember { mutableStateOf("baseline_vs_context") }
    var featureSelections by remember {
        mutableStateOf(
            listOf(
                FeatureSelectionSpec.complete(TrainingFeatureProfile.BASELINE_KINEMATICS),
                FeatureSelectionSpec.complete(TrainingFeatureProfile.CONTEXT_EXPANDED)
            )
        )
    }
    val controlDraftRepository = remember(context) { TrainingControlsDraftRepository(File(AppStoragePaths(context).preferencesDirectory,"single-run-controls.properties")) }
    val restoredControlsResult = remember(controlDraftRepository) { runCatching { controlDraftRepository.load() } }
    val restoredControls = restoredControlsResult.getOrNull() ?: TrainingControlsDraft()
    var modelKind by rememberSaveable { mutableStateOf(restoredControls.model) }
    var resourceMode by rememberSaveable { mutableStateOf(restoredControls.resource) }
    var splitStrategy by rememberSaveable { mutableStateOf(restoredControls.split) }
    var maximumRowsText by remember { mutableStateOf("50000") }
    var epochsText by rememberSaveable { mutableStateOf(restoredControls.epochs) }
    var batchSizeText by rememberSaveable { mutableStateOf(restoredControls.batch) }
    var learningRateText by rememberSaveable { mutableStateOf(restoredControls.rate) }
    var l2Text by rememberSaveable { mutableStateOf(restoredControls.l2) }
    var hiddenUnitsText by rememberSaveable { mutableStateOf(restoredControls.hidden) }
    var seedText by rememberSaveable { mutableStateOf(restoredControls.seed) }
    var patienceText by rememberSaveable { mutableStateOf(restoredControls.patience) }
    val controlsDraft = TrainingControlsDraft(modelKind,resourceMode,splitStrategy,epochsText,batchSizeText,learningRateText,l2Text,hiddenUnitsText,seedText,patienceText)
    val controlErrors = controlsDraft.errors()
    var controlSaveError by remember { mutableStateOf(restoredControlsResult.exceptionOrNull()?.let { "Stored control draft was rejected: ${it.message}. Historical bytes are retained; edit controls to save a replacement draft." }) }
    var failedTrainingStage by remember { mutableStateOf<Int?>(null) }
    var trainingWasCancelled by remember { mutableStateOf(false) }
    LaunchedEffect(controlsDraft) {
        if (restoredControlsResult.isFailure && controlsDraft == restoredControls) return@LaunchedEffect
        controlSaveError = withContext(Dispatchers.IO) { runCatching { controlDraftRepository.save(controlsDraft) }.exceptionOrNull()?.let { "Control draft could not be saved: ${it.message}" } }
    }
    var minimumRobotCountText by remember { mutableStateOf("1") }
    var exactDatasetContract by remember { mutableStateOf(false) }
    var requiredToleranceText by remember { mutableStateOf("0.0001") }
    var requiredIterationsText by remember { mutableStateOf("1000") }
    var requiredDampingText by remember { mutableStateOf("0.001") }
    var requiredMaxStepText by remember { mutableStateOf("0.2") }
    var requiredTargetMode by remember { mutableStateOf<DatasetTargetMode?>(null) }
    var requiredReachableFractionText by remember { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf("Select a scientific dataset and start a controlled local comparison.")
    }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<LocalTrainingProgress?>(null) }
    var telemetryProgress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    var startedAtMillis by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<LocalTrainingRunResult?>(null) }
    val iterations = remember { mutableStateListOf<TrainingIterationMetrics>() }
    val performanceSamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    val datasetRequirements =
        buildDatasetRequirements(
            requestedRowsText = maximumRowsText,
            minimumRobotCountText = minimumRobotCountText,
            exactDatasetContract = exactDatasetContract,
            toleranceText = requiredToleranceText,
            iterationsText = requiredIterationsText,
            dampingText = requiredDampingText,
            maxStepText = requiredMaxStepText,
            targetMode = requiredTargetMode,
            reachableFractionText = requiredReachableFractionText
        )?.let { requirements ->
            if (splitStrategy == TrainingSplitStrategy.ROBOT_HELD_OUT) requirements.copy(minimumRobotCount = maxOf(3, requirements.minimumRobotCount))
            else requirements
        }
    LaunchedEffect(manifests, datasetRequirements, activeJob == null) {
        if (activeJob == null) selectedManifest = SingleRunDatasetPlanner.preserveSelection(selectedManifest, manifests, datasetRequirements)
    }
    LaunchedEffect(selectedManifest, datasetRequirements, validationRefresh) {
        validatedDigest = null
        validatedPath = null
        validatedRequirements = null
        validationError = null
        val selected = selectedManifest
        val requirements = datasetRequirements
        if (selected != null && requirements != null &&
            TrainingDatasetCompatibilityEvaluator.evaluate(selected, requirements).level != DatasetCompatibilityLevel.INCOMPATIBLE) {
            validatingDataset = true
            try {
                kotlinx.coroutines.delay(200)
                val digest = withContext(Dispatchers.IO) {
                    val validationJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                    SingleRunDatasetPlanner.inspect(selected, requirements) { validationJob?.isActive == false }
                }
                validatedPath = selected.csvPath
                validatedRequirements = requirements
                validatedDigest = digest
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: LocalTrainingCancelledException) {
            } catch (failure: Exception) { validationError = failure.message ?: "Dataset verification failed. Review its source and append history."
            } finally { validatingDataset = false }
        }
    }
    val effectivePlan = selectedManifest?.let { selected -> datasetRequirements?.let { SingleRunDatasetPlanner.plan(selected, it, safeRowLimit) } }
    val verifiedSelection = validatedDigest != null && validatedPath == selectedManifest?.csvPath && validatedRequirements == datasetRequirements
    reviewManifest?.let { reviewed ->
        AlertDialog(onDismissRequest = { reviewManifest = null },
            title = { Text("Dataset provenance review") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ScientificEntityNameResolver.dataset(reviewed.csvPath, reviewed.datasetName).primary)
                Text("Source file: ${reviewed.csvPath}")
                Text("Recorded rows: ${reviewed.rowCount}; generations: ${reviewed.generationCount}; complete append history: ${reviewed.hasCompleteBatchProvenance}.")
                reviewed.batches.forEach { batch ->
                    Text("Batch ${batch.generationIndex + 1}: ${batch.rowCount} rows starting at ${batch.rowStart}; ${batch.robotIds.size} robot IDs; ${targetModeLabel(batch.targetMode)}.")
                    Text("Tolerance: ${batch.ikConfig.tolerance} m; maximum iterations: ${batch.ikConfig.maxIterations}. Random protocol: ${batch.randomProtocol}.")
                }
                Text("A row shortage is usable with a smaller run. An inconsistent history must be reviewed against the original Dataset Factory batches; this screen does not modify historical files.")
            } },
            confirmButton = { TextButton(onClick = { reviewManifest = null }) { Text("Close review") } })
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Local AI Training Lab", style = MaterialTheme.typography.headlineSmall)
        JargonHelpNotice()
        JargonAwareText(
            text =
                "Train directly on this phone and compare ordinary kinematic inputs against the additional pre-solve context. " +
                    "The held-out test set is never used for model selection, which prevents data leakage.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrainingDisclosureSection(
            title = "1 · Scientific dataset",
            subtitle = "State the experiment contract first. Every saved dataset remains visible with a compatibility verdict and an explicit reason."
        ) {
            JargonAwareText(
                "Choose the required rows and robots first. An exact IK contract must also match tolerance, iteration, damping and step settings, so incompatible datasets cannot be selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Compatibility requirements", style = MaterialTheme.typography.titleSmall)
            NumericField("Rows requested for training", maximumRowsText, activeJob == null,
                "30–1,000,000 rows across train, validation and test. Dataset Factory records availability; the CSV is checked below. Raising this requests more evidence; lowering it reduces work. A shortage permits a smaller run.") { maximumRowsText = it }
            NumericField("Minimum distinct robots", minimumRobotCountText, activeJob == null,
                "1–10,000 robot definitions in the source dataset. Raising this excludes datasets with fewer robots; lowering it admits narrower coverage. CSV morphology is checked, not just names. Whole-robot holdout needs at least 3.") { minimumRobotCountText = it }
            CheckboxRow(
                checked = exactDatasetContract,
                label = "Require an exact IK solver contract",
                enabled = activeJob == null,
                onCheckedChange = { exactDatasetContract = it }
            )
            JargonAwareText("Match the Dataset Factory solver settings that produced the labels: tolerance, maximum iterations, damping and maximum step, in every append batch. Turning this off admits different settings; it does not alter or rerun their solver and does not imply better prediction accuracy.", style = MaterialTheme.typography.bodySmall)
            if (exactDatasetContract) {
                NumericField("Required tolerance (metres)", requiredToleranceText, activeJob == null, "Finite and greater than zero. Smaller means stricter Cartesian convergence when labels were generated; changing it selects only datasets generated with that tolerance.") {
                    requiredToleranceText = it
                }
                NumericField("Required IK maximum iterations", requiredIterationsText, activeJob == null, "Positive integer iteration budget used by Dataset Factory. A larger matched budget allowed its solver more attempts; this filter does not change training epochs.") {
                    requiredIterationsText = it
                }
                NumericField("Required damping", requiredDampingText, activeJob == null, "Finite and greater than zero. This is the generator’s DLS damping. Changing it changes eligible solver configurations, not the learning rate.") {
                    requiredDampingText = it
                }
                NumericField("Required maximum step", requiredMaxStepText, activeJob == null, "Finite and greater than zero. Generator joint-update cap: radians for revolute coordinates and metres for prismatic coordinates. Match it to compare labels produced under the same solver constraint.") {
                    requiredMaxStepText = it
                }
            }
            Text("Target generation contract", style = MaterialTheme.typography.titleSmall)
            CompactSelectionMenu(
                options = listOf<DatasetTargetMode?>(null) + DatasetTargetMode.entries,
                selected = requiredTargetMode,
                label = { targetModeLabel(it) },
                onSelected = { requiredTargetMode = it }, enabled = activeJob == null)
            JargonAwareText("This is the population requested in Dataset Factory: any mode removes this filter; Mixed combines target types; FK proven reachable uses forward-generated targets; Guaranteed unreachable uses outside-reach targets. It describes sampling, not whether the solver succeeded.", style = MaterialTheme.typography.bodySmall)
            NumericField("Exact reachable fraction (blank = any)", requiredReachableFractionText, activeJob == null,
                "Blank ignores this setting; otherwise enter 0–1. Match the fraction requested when Mixed batches were generated. Raising it requires a greater planned reachable share. This is not an observed solver success rate; filtering accepted/rejected rows can change the saved population.") { requiredReachableFractionText = it }

            val requirements = datasetRequirements
            if (requirements == null) {
                Text(
                    "Complete the filter with finite positive values before selecting a dataset.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (manifests.isEmpty()) Text("No dataset is available. Create one in Dataset Factory first.")
            val eligible = if (requirements == null) emptyList() else manifests.filter {
                TrainingDatasetCompatibilityEvaluator.evaluate(it, requirements).level != DatasetCompatibilityLevel.INCOMPATIBLE }
            CompactSelectionMenu(options = eligible, selected = selectedManifest,
                label = { ScientificEntityNameResolver.dataset(it.csvPath, it.datasetName).primary },
                onSelected = { selectedManifest = it }, enabled = activeJob == null)
            manifests.forEach { manifest ->
                val verdict = requirements?.let { TrainingDatasetCompatibilityEvaluator.evaluate(manifest, it) }
                val name = ScientificEntityNameResolver.dataset(manifest.csvPath, manifest.datasetName).primary
                Text("$name · ${manifest.rowCount} recorded available / ${requirements?.requestedRows ?: "—"} requested rows · ${manifest.robotIds.size} robot IDs · ${verdict?.level?.displayName ?: "Complete requirements"}", style = MaterialTheme.typography.bodySmall)
                verdict?.let { Text(it.reasons.joinToString(". "), style = MaterialTheme.typography.bodySmall,
                    color = if (it.level == DatasetCompatibilityLevel.INCOMPATIBLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!manifest.hasCompleteBatchProvenance || verdict?.reasons?.any { "provenance" in it || "history" in it } == true) {
                    TextButton(onClick = { reviewManifest = manifest }) { Text("Review ${name} append history") }
                }
            }
            OutlinedButton(onClick = {
                manifests = datasetRepository.listManifests()
                selectedManifest = SingleRunDatasetPlanner.preserveSelection(selectedManifest, manifests, datasetRequirements)
                validatedDigest = null
                validationRefresh++
            }, enabled = activeJob == null, modifier = Modifier.fillMaxWidth()) { Text("Refresh dataset index") }
            selectedManifest?.let { manifest ->
                ChartMetricRow("Selected dataset", ScientificEntityNameResolver.dataset(manifest.csvPath, manifest.datasetName).primary)
                ChartMetricRow("Requested rows (all partitions)", datasetRequirements?.requestedRows?.toString() ?: "Complete requirements")
                ChartMetricRow(if (verifiedSelection) "Verified valid rows available" else "Recorded rows; verification required", manifest.rowCount.toString())
                ChartMetricRow("Device row limit", safeRowLimit.toString())
                ChartMetricRow("Effective rows for this run", effectivePlan?.effectiveRows?.toString() ?: "Unavailable")
                Text("The effective count is the smallest of requested rows, available rows and the device row limit. All partitions together use that count; a partial dataset needs no fabricated rows.", style = MaterialTheme.typography.bodySmall)
                ChartMetricRow("Target population", targetModeLabel(manifest.targetMode))
                ChartMetricRow("Requested reachable fraction at generation", percent(manifest.reachableFraction))
                ChartMetricRow("Generator tolerance (metres)", manifest.ikConfig.tolerance.toString())
                ChartMetricRow("Generator maximum IK iterations", manifest.ikConfig.maxIterations.toString())
                ChartMetricRow("Generator damping", manifest.ikConfig.damping.toString())
                ChartMetricRow("Generator maximum joint step", manifest.ikConfig.maxStep.toString())
                TextButton(onClick = { reviewManifest = manifest }) { Text("Review source and append history") }
            }
            if (validatingDataset) Text("Verifying CSV rows, robot definitions, solver metadata and provenance…")
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (effectivePlan != null && effectivePlan.effectiveRows < 30) Text("The device row limit cannot fit the minimum 30 rows. Free memory or choose a lighter resource policy.", color = MaterialTheme.colorScheme.error)
        }

        TrainingDisclosureSection(
            title = "2 · Experiment design",
            subtitle =
                "Default: a seed-locked comparison of baseline, frozen context and expanded research context with class balancing and early stopping."
        ) {
            JargonAwareText(
                "A comparison arm is one model variant. Keep the random seed and scientific split fixed so a Macro-F1 change reflects the selected feature profile rather than a different test set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = runName,
                onValueChange = { runName = it },
                label = { Text("Training run name") },
                enabled = activeJob == null,
                modifier = Modifier.fillMaxWidth()
            )
            FeatureExperimentSelector(
                originLabel = "Single Run / Experiment design",
                sourceCsvPath = selectedManifest?.csvPath,
                selections = featureSelections,
                enabled = activeJob == null,
                onSelectionsChange = { featureSelections = it }
            )

            AppDisclosureSection(title = "Scientific split", summary = splitStrategy.displayName, testTag = "training-scientific-split") {
                TrainingSplitStrategy.entries.forEach { strategy ->
                    SelectionButton(selected = splitStrategy == strategy, label = strategy.displayName, enabled = activeJob == null,
                        onClick = { splitStrategy = strategy })
                }
                JargonAwareText(splitStrategy.scientificDescription, style = MaterialTheme.typography.bodySmall)
                Text("Parameters, normalization and class weights use training rows only. Validation chooses the epoch and candidate. Test rows are evaluated after selection. Hash allocation targets 70/15/15% of groups; unequal groups and the deterministic small-group fallback change row proportions. Changing strategy changes the experiment: previous results retain their original split. Group separation does not remove every possible bias.", style = MaterialTheme.typography.bodySmall)
            }

            Text("Model search", style = MaterialTheme.typography.titleSmall)
            TrainingModelKind.entries.forEach { kind ->
                SelectionButton(
                    selected = modelKind == kind,
                    label = kind.displayName,
                    enabled = activeJob == null,
                    onClick = { modelKind = kind; if (!TrainingControlContract.hiddenUnitsApply(kind,resourceMode)) hiddenUnitsText = "24" }
                )
            }

            val previewHidden = ScientificNumberParser.parseInt(hiddenUnitsText)?.takeIf { it in 2..512 } ?: 24
            JargonAwareText(TrainingControlContract.searchDescription(modelKind,resourceMode,previewHidden), style = MaterialTheme.typography.bodySmall)
            Text("Automatic search budget", style = MaterialTheme.typography.titleSmall)
            Text("Conservative/Moderate/High are load badges for these same search choices. They change candidate widths only, not epochs, learning rate, rows or device workers. Maximum accuracy is a search label, not a promised result. Fixed model choices ignore this budget.", style = MaterialTheme.typography.bodySmall)
            ResourceLoadLegend(modifier = Modifier.fillMaxWidth())
            TrainingResourceMode.entries.forEach { mode ->
                TrainingResourceModeButton(
                    mode = mode,
                    selected = resourceMode == mode && modelKind == TrainingModelKind.AUTOMATIC,
                    enabled = activeJob == null && modelKind == TrainingModelKind.AUTOMATIC,
                    onClick = { resourceMode = mode; if (!TrainingControlContract.hiddenUnitsApply(modelKind,mode)) hiddenUnitsText = "24" }
                )
            }
        }

        TrainingDisclosureSection(
            title = "3 · Training controls",
            subtitle = if (controlsDraft.customized) "Custom training controls · values preserved with the run" else "Default training controls · independent of the search budget"
        ) {
            Text("An epoch visits every training row once; a candidate is one initialized model architecture. Validation is evaluated after each epoch. Patience counts epochs without improved validation macro-F1/log loss; the best validation epoch is restored, which can be epoch 0. A patience above maximum epochs has no stopping effect.", style = MaterialTheme.typography.bodySmall)
            NumericField("Maximum epochs per candidate",epochsText,activeJob == null,"Default 40; 1–1,000. Increasing the cap permits more complete training passes and more compute, but early stopping can finish earlier.",controlErrors["epochs"]) { epochsText = it.take(128) }
            NumericField("Mini-batch size",batchSizeText,activeJob == null,"Default 128; 1–8,192 training rows per optimizer update. Larger batches need more scratch work and change update noise; the final batch may be smaller.",controlErrors["batch"]) { batchSizeText = it.take(128) }
            NumericField("Learning rate",learningRateText,activeJob == null,"Default 0.003; finite 0.000001–1. Controls Adam update size. Larger steps may converge faster or destabilize; smaller steps may need more epochs.",controlErrors["learningRate"]) { learningRateText = it.take(128) }
            NumericField("L2 regularization",l2Text,activeJob == null,"Default 0.0001; finite 0–1. Adds a weight penalty (not biases). Larger values shrink weights more and may underfit; zero removes this penalty.",controlErrors["l2"]) { l2Text = it.take(128) }
            NumericField("Hidden units",hiddenUnitsText,activeJob == null && TrainingControlContract.hiddenUnitsApply(modelKind,resourceMode),
                if (TrainingControlContract.hiddenUnitsApply(modelKind,resourceMode)) "Default 24; 2–512. Width of the ReLU layer; automatic search applies the widths shown above. Larger networks require more parameters and memory, without guaranteed improvement." else "Not used by this model/search selection. The inactive value is reset to 24 when switching to this selection.",controlErrors["hidden"]) { hiddenUnitsText = it.take(128) }
            NumericField("Random seed",seedText,activeJob == null,"Default 42; signed 32-bit integer. Controls partitions, initialization and training order. Reproduction also requires the corpus, feature schema, controls and worker/reduction behavior; a different seed is a different experiment.",controlErrors["seed"]) { seedText = it.take(128) }
            NumericField("Early-stopping patience",patienceText,activeJob == null,"Default 8; 1–100 validation checks without improvement. Larger patience permits longer plateaus, bounded by maximum epochs; zero is not a disable option.",controlErrors["patience"]) { patienceText = it.take(128) }
            val policy = computeGuard.currentPolicy()
            Text("Device policy ${policy.preset.displayName}: ${policy.effectiveWorkerCount} requested worker(s), estimated row cap ${policy.estimatedSafeTrainingRows}, working-memory budget ${policy.workingMemoryBudgetBytes / (1024 * 1024)} MiB. Rows are capped before execution; runtime safety and the final mini-batch may use fewer workers. Completed runs record requested controls and actual gradient-shard counts. High search load does not imply all device resources.", style = MaterialTheme.typography.bodySmall)
            controlSaveError?.let { Text(it,color=MaterialTheme.colorScheme.error) }

        }

        if (activeJob == null) {
            Button(
                    onClick = startTraining@{
                        if (processCoordinator.isActive(ResearchProcessIds.LOCAL_TRAINING)) {
                            statusMessage = "A controlled training run is already active. Open Research activity to inspect it."
                            return@startTraining
                        }
                        val manifest = selectedManifest
                        val computePolicy = computeGuard.currentPolicy(forceRefresh = true)
                        safeRowLimit = computePolicy.estimatedSafeTrainingRows
                        if (!verifiedSelection || manifest == null || datasetRepository.loadManifest(manifest.datasetName) != manifest) {
                            statusMessage = "Dataset selection changed or still needs verification. Refresh the dataset index before training."
                            return@startTraining
                        }
                        val configOrError =
                            if (datasetRequirements == null) {
                                null to "Complete the dataset compatibility filter with valid values."
                            } else if (
                                TrainingDatasetCompatibilityEvaluator.evaluate(manifest, datasetRequirements).level ==
                                    DatasetCompatibilityLevel.INCOMPATIBLE
                            ) {
                                null to "The selected dataset no longer matches the active compatibility filter."
                            } else buildConfig(
                                manifest = manifest,
                                runName = runName,
                                featureSelections = featureSelections,
                                modelKind = modelKind,
                                resourceMode = resourceMode,
                                splitStrategy = splitStrategy,
                                maximumRowsText = maximumRowsText,
                                epochsText = epochsText,
                                batchSizeText = batchSizeText,
                                learningRateText = learningRateText,
                                l2Text = l2Text,
                                hiddenUnitsText = hiddenUnitsText,
                                seedText = seedText,
                                patienceText = patienceText,
                                workerCount = computePolicy.effectiveWorkerCount,
                                safeTrainingRows = computePolicy.estimatedSafeTrainingRows,
                                datasetRequirements = datasetRequirements,
                                expectedCorpusSha256 = requireNotNull(validatedDigest)
                            )
                        if (configOrError.second != null) {
                            statusMessage = requireNotNull(configOrError.second)
                        } else {
                            val config = requireNotNull(configOrError.first)
                            cancellation.set(false)
                            iterations.clear()
                            performanceSamples.clear()
                            telemetryCaptureGate.reset()
                            result = null
                            failedTrainingStage = null
                            trainingWasCancelled = false
                            startedAtMillis = System.currentTimeMillis()
                            statusMessage = "Starting ${config.maximumRows} verified rows from ${manifest.datasetName} across all partitions, with ${config.workerCount} worker(s)."
                            activeJob =
                                processCoordinator.launch(
                                    id = ResearchProcessIds.LOCAL_TRAINING,
                                    title = "AI training · ${config.runName}",
                                    kind = ResearchProcessKind.TRAINING,
                                    cancellationAction = { cancellation.set(true) }
                                ) { process ->
                                    val lifecycle = TrainingPublicationLifecycle()
                                    var appliedRevision = -1L
                                    fun publish(snapshot: TrainingPublicationSnapshot) {
                                        if (!lifecycle.isCurrent(snapshot) || snapshot.revision <= appliedRevision) return
                                        appliedRevision = snapshot.revision
                                        val update = snapshot.progress
                                        progress = update
                                        failedTrainingStage = snapshot.failedStage
                                        process.report(update.fraction,update.phase.name.lowercase().replace('_',' '),update.message)
                                        val mapped = mapProgress(update,startedAtMillis,telemetrySampler)
                                        telemetryProgress = mapped
                                        if (telemetryCaptureGate.shouldCapture(force = update.phase in setOf(LocalTrainingPhase.SAVING,LocalTrainingPhase.COMPLETED,LocalTrainingPhase.FAILED))) {
                                            performanceSamples += DiagnosticPerformanceSample.fromProgressState(performanceSamples.size+1,mapped)
                                        }
                                    }
                                    publish(lifecycle.current)
                                    try {
                                        val trained =
                                            withContext(Dispatchers.Default) {
                                                engine.train(
                                                    config = config,
                                                    cancellationRequested = cancellation::get,
                                                    onProgress = { update ->
                                                        val snapshot = lifecycle.engine(update)
                                                        scope.launch { publish(snapshot) }
                                                    },
                                                    onIteration = { iteration ->
                                                        scope.launch { iterations += iteration }
                                                    }
                                                )
                                            }
                                        publish(lifecycle.saving())
                                        val stored = withContext(Dispatchers.IO) { trainingRepository.save(trained) }
                                        result = stored
                                        savedRuns = withContext(Dispatchers.IO) { runCatching { trainingRepository.listRuns() }.getOrDefault(savedRuns) }
                                        statusMessage = "Training completed and stored as ${stored.runId}."
                                        publish(lifecycle.completed())
                                        process.completed(statusMessage, ResearchResultReference(ResearchResultDestination.TRAINING_RUN,stored.runId))
                                    } catch (_: LocalTrainingCancelledException) {
                                        statusMessage = "Training cancelled. No partial model was registered."
                                        trainingWasCancelled = true
                                        publish(lifecycle.failed(statusMessage))
                                        process.cancelled(statusMessage)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        trainingWasCancelled = true
                                        publish(lifecycle.failed("Training was cancelled before a model was published."))
                                        process.cancelled("Training was cancelled before a model was published.")
                                    } catch (error: Exception) {
                                        statusMessage = error.message ?: "Training failed."
                                        publish(lifecycle.failed(statusMessage))
                                        process.failed(statusMessage)
                                    } finally {
                                        activeJob = null
                                    }
                                }.getOrElse { error ->
                                    statusMessage = error.message ?: "Training could not start."
                                    null
                                }
                        }
                    },
                    enabled = verifiedSelection && effectivePlan?.canTrain == true && !validatingDataset && controlErrors.isEmpty(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("training-single-execution")
                            .tutorialAnchor(TutorialTargets.TrainingSingleExecution)
            ) {
                Text("Train and compare locally")
            }
        } else {
            OutlinedButton(
                    onClick = {
                        processCoordinator.requestCancel(ResearchProcessIds.LOCAL_TRAINING)
                        statusMessage = "Cancellation requested; finishing the current batch safely…"
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("training-single-execution")
                            .tutorialAnchor(TutorialTargets.TrainingSingleExecution)
            ) {
                Text("Cancel after current batch")
            }
        }
        Text(statusMessage, modifier = Modifier.politeLiveRegion().testTag("training-single-status"))

        telemetryProgress?.let { mappedProgress ->
            DiagnosticLoadingProgressCard(
                progressState = mappedProgress,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Local Model Training Running",
                        finishedTitle = "Local Model Training Progress",
                        progressSectionTitle = "Training Progress",
                        completedLabel = "Completed work units",
                        remainingLabel = "Work units left",
                        rateLabel = "Work units per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Local training"
                    ),
                timeline = trainingTimeline(
                    progress?.phase ?: LocalTrainingPhase.IDLE,
                    failedTrainingStage,
                    cancelled = trainingWasCancelled
                )
            )
        }

        result?.let { TrainingResultSection(it, iterations) }

        if (savedRuns.isNotEmpty()) {
            TrainingDisclosureSection(
                title = "Stored training runs",
                subtitle = "Every run keeps its configuration, models, independent test metrics and full iteration history."
            ) {
                ExpandableSelectionCollection(
                    items = savedRuns,
                    initialVisibleCount = 10,
                    itemName = "training runs",
                    isSelected = { false },
                    toggleTestTag = "training-runs-show-all"
                ) { run ->
                    com.robotkinematicslab.mobile.ui.training.results.HistoricalTrainingRunCard(run)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TrainingResultSection(
    result: LocalTrainingRunResult,
    iterations: List<TrainingIterationMetrics>
) {
    val comparison = result.comparison
    ProvideAutomaticFigureLibraryContext(
        collection = "training",
        analysisId = result.runId,
        executionId = "training-${result.runId}"
    ) {
        TrainingDisclosureSection(
            title = "Independent test result",
            subtitle = "Positive deltas mean the additional context improved the held-out test result.",
            initiallyExpanded = true
        ) {
        Text("Independent test evaluates rows held apart from fitting and validation-based selection under the stated split. Accuracy measures total correctness; balanced accuracy averages supported-class recall; macro-F1 balances precision and recall per supported class. Log loss, ECE and Brier evaluate probability quality; smaller is preferable. Timings and parameter count describe computational cost.")
        ChartMetricRow("Split strategy", result.config.splitStrategy.displayName)
        val sharedWarnings = comparison.variants.flatMap { it.datasetWarnings.distinct() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if(sharedWarnings.isNotEmpty()) TrainingDisclosureSection(title="Shared dataset warnings",subtitle="Exact repeated warnings from this run’s dataset, shown once; original wording is preserved.") {
            sharedWarnings.forEach { Text(com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.warningExplanation(it)) }
        }
        comparison.variants.forEach { ProfileResultRows(it, sharedWarnings) }
        if (comparison.baseline != null && comparison.contextEnhanced != null) {
            HorizontalDivider()
            ChartMetricRow("Context macro-F1 delta", signed(comparison.macroF1Delta))
            ChartMetricRow("Context balanced-accuracy delta", signed(comparison.balancedAccuracyDelta))
            ChartMetricRow("Context log-loss delta (lower is better)", signed(comparison.logLossDelta))
            ChartMetricRow("Context inference cost", "${signed(comparison.inferenceNanosDelta)} ns/sample")
        }
        if (comparison.baseline != null && comparison.contextExpanded != null) {
            HorizontalDivider()
            ChartMetricRow("Expanded macro-F1 vs baseline", signed(comparison.expandedMacroF1DeltaVsBaseline))
            ChartMetricRow("Expanded macro-F1 vs frozen context", signed(comparison.expandedMacroF1DeltaVsContext))
            ChartMetricRow("Expanded balanced accuracy vs baseline", signed(comparison.expandedBalancedAccuracyDeltaVsBaseline))
            ChartMetricRow("Expanded log loss vs baseline (lower is better)", signed(comparison.expandedLogLossDeltaVsBaseline))
            ChartMetricRow("Expanded inference cost vs baseline", "${signed(comparison.expandedInferenceNanosDeltaVsBaseline)} ns/sample")
        }
        Text("History: ${result.historyCsvPath}", style = MaterialTheme.typography.bodySmall)

        if (comparison.variants.size >= 2) {
            val ordered = comparison.variants.sortedBy { it.featureNames.size }
            val featureGrowthPoints =
                buildFeatureGrowthChartPoints(
                    comparison.variants.map { variant ->
                        FeatureGrowthObservation(
                            featureSelectionId = variant.featureSelectionId,
                            featureCount = variant.featureNames.size,
                            independentTestMacroF1 = variant.testMetrics.macroF1
                        )
                    }
                )
            ProfessionalLineChart(
                title = "Variables added · independent macro-F1",
                subtitle =
                    "Every point is the independent-test macro-F1 of that exact feature contract on the same " +
                        "deterministic split; points are never inferred from validation history.",
                points = featureGrowthPoints.map { it.copy(breakBefore=true) },
                xAxisLabel = "Input variables",
                yAxisLabel = "Test macro-F1",
                color = Color(0xFF6A1B9A)
            )
            ProfessionalLineChart(
                title = "Selected-model training time by feature contract",
                subtitle = "Measured time for each selected model, not total search cost. Observed timing differences do not establish a causal effect of variable count.",
                points = ordered.map { ChartLinePoint(it.featureNames.size.toDouble(), it.trainingDurationMillis / 1_000.0,breakBefore=true,label=it.featureSelectionName) },
                xAxisLabel = "Input variables",
                yAxisLabel = "Training seconds",
                color = Color(0xFFF57C00),
                directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
            )
            ChartSectionCard(
                title = "All selected models compared with all",
                subtitle = "Positive macro-F1 means the row model performed better than the comparison model."
            ) {
                ordered.forEachIndexed { leftIndex, left ->
                    ordered.drop(leftIndex + 1).forEach { right ->
                        ChartMetricRow(
                            "${left.featureSelectionName} ↔ ${right.featureSelectionName}",
                            "ΔF1 ${signed(right.testMetrics.macroF1 - left.testMetrics.macroF1)} · " +
                                "Δtime ${signed((right.trainingDurationMillis - left.trainingDurationMillis) / 1_000.0)} s"
                        )
                    }
                }
            }
        }

        iterations.distinctBy { it.featureSelectionId }.forEach { selected ->
            val profile = selected.profile
            val profileIterations = iterations.filter { it.featureSelectionId == selected.featureSelectionId }
            if (profileIterations.size >= 2) {
                ProfessionalLineChart(
                    title = "${selected.featureSelectionName} · validation macro-F1",
                    subtitle = "Each observation is a completed epoch event; the global index spans candidates. Gaps separate candidate changes. Inspect the recorded candidate and epoch; no cause is inferred from a dip.",
                    points = com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.iterations(profileIterations),
                    xAxisLabel = "Recorded epoch event (across candidates)",
                    yAxisLabel = "Validation macro-F1",
                    color =
                        when (profile) {
                            TrainingFeatureProfile.BASELINE_KINEMATICS -> Color(0xFF546E7A)
                            TrainingFeatureProfile.CONTEXT_ENHANCED -> Color(0xFF2E7D32)
                            TrainingFeatureProfile.CONTEXT_EXPANDED -> Color(0xFF6A1B9A)
                            TrainingFeatureProfile.CONTEXT_RESEARCH_V2 -> Color(0xFFAD1457)
                        }
                )
            }
        }
            comparison.variants.forEach { profile ->
                ProfileBehaviorCharts(profile)
            }
        }
    }
}

@Composable
private fun ProfileBehaviorCharts(profile: TrainedProfileResult) {
    com.robotkinematicslab.mobile.ui.training.results.ScientificClassificationFigures(profile.featureSelectionName,profile.testMetrics)
}

@Composable
private fun ProfileResultRows(profile: TrainedProfileResult, sharedWarnings:Set<String> = emptySet()) {
    Text("${profile.featureSelectionName} · ${profile.featureNames.size} variables", style = MaterialTheme.typography.titleSmall)
    ChartMetricRow("Selected model", profile.candidateId)
    ChartMetricRow("Train / validation / test", "${profile.trainRowCount} / ${profile.validationRowCount} / ${profile.testRowCount}")
    ChartMetricRow("Test accuracy", percent(profile.testMetrics.accuracy))
    ChartMetricRow("Test balanced accuracy", percent(profile.testMetrics.balancedAccuracy))
    ChartMetricRow("Test macro-F1", decimal(profile.testMetrics.macroF1))
    ChartMetricRow("Test log loss", decimal(profile.testMetrics.logLoss))
    ChartMetricRow("Probability calibration (ECE)", percent(profile.testMetrics.expectedCalibrationError))
    ChartMetricRow("Multiclass Brier score", decimal(profile.testMetrics.brierScore))
    ChartMetricRow("Measured inference per sample", com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.NANOSECONDS.format(profile.testMetrics.inferenceNanosPerSample))
    ChartMetricRow("Parameters", profile.parameterCount.toString())
    ChartMetricRow("Duplicate leakage guard", if (profile.duplicateFingerprintsKeptTogether) "Recorded duplicates kept in one split" else "Grouping check failed")
    Text("This check prevents a recorded duplicate fingerprint crossing train/validation/test boundaries. It does not prove representativeness, remove every possible dependency, or guarantee generalization.",style=MaterialTheme.typography.bodySmall)
    if (!profile.testMetrics.hasCompleteClassCoverage) {
        val missing =
            profile.testMetrics.missingTruthClassIndices
                .mapNotNull { index -> TrainingLabel.entries.getOrNull(index)?.name }
                .joinToString()
        Text(
            "Scientific warning: the test partition contains no true examples for: $missing. " +
                "Macro-F1 only covers the classes present and must not be compared as a complete three-class score.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    com.robotkinematicslab.mobile.ui.training.results.NamedClassSupport(profile.testMetrics)
    if(profile.datasetWarnings.any { it in sharedWarnings }) Text("Shared dataset warnings apply; see the common warning section above.")
    profile.datasetWarnings.filterNot { it in sharedWarnings }.forEach { warning ->
        Text(
            com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.warningExplanation(warning),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodySmall
        )
    }
    com.robotkinematicslab.mobile.ui.training.results.ScientificTestSlices(profile.testSlices,profile.featureSelectionId)
    listOf("robot:" to "Per robot", "topology:" to "Per topology").forEach { (prefix, title) ->
        val slices = profile.testSlices.filter { it.id.startsWith(prefix) }
        if (slices.isNotEmpty()) {
            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title = "${profile.featureSelectionName} · $title macro-F1",
                subtitle = "F1 is independently evaluated for each stored subset; blue identifies measurements, not approval thresholds. Open Scientific test slices for sample support and exact identities.",
                items = slices.map { slice ->
                    ChartBarItem(
                        label = "${com.robotkinematicslab.mobile.ui.training.results.ScientificResultEvidence.sliceName(slice)} · n=${slice.metrics.sampleCount} · ${percent(slice.metrics.macroF1)}",
                        value = (slice.metrics.macroF1 * 10_000).roundToInt(),
                        color = Color(0xFF1565C0)
                    )
                },
                xAxisLabel = "Macro-F1 (%)"
            )
        }
    }
}

@Composable
private fun StoredRunRow(run: TrainingRunSummary) {
    Text(run.runName, style = MaterialTheme.typography.titleSmall)
    Text(DateFormat.getDateTimeInstance().format(Date(run.startedAtEpochMillis)))
    Text(
        "Baseline F1 ${run.baselineTestMacroF1?.let(::decimal) ?: "N/A"} · " +
            "Context F1 ${run.contextTestMacroF1?.let(::decimal) ?: "N/A"} · " +
            "Expanded F1 ${run.expandedContextTestMacroF1?.let(::decimal) ?: "N/A"} · " +
            "Frozen Δ ${signed(run.macroF1Delta)} · Expanded Δ ${signed(run.expandedMacroF1DeltaVsBaseline)}"
    )
    Text(run.directoryPath, style = MaterialTheme.typography.bodySmall)
        val controls = run.trainingControls
        Text(if (controls == null) "Legacy run: complete training controls and applied workers were not recorded." else
            "Stored controls: ${controls.epochs} epochs · batch ${controls.batchSize} · learning rate ${controls.learningRate} · L2 ${controls.l2Regularization} · hidden ${controls.hiddenUnits} · patience ${controls.earlyStoppingPatience} · seed ${controls.randomSeed}. Search ${controls.resourceMode.displayName}; model ${controls.modelKind.displayName}. Requested workers ${controls.requestedWorkers}; actual completed batch shards ${controls.workerBatchCounts.ifEmpty { mapOf() }}.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SelectionButton(
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
private fun CheckboxRow(
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
private fun NumericField(
    label: String,
    value: String,
    enabled: Boolean,
    help: String? = null,
    error: String? = null,
    onValueChange: (String) -> Unit
) {
    val support: (@Composable () -> Unit)? = if (help == null && error == null) null else { { JargonAwareText(error ?: requireNotNull(help), style = MaterialTheme.typography.bodySmall) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        supportingText = support,
        isError = error != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun targetModeLabel(mode: DatasetTargetMode?): String = when (mode) {
    null -> "Any target mode"
    DatasetTargetMode.MIXED -> "Mixed"
    DatasetTargetMode.FK_PROVEN_REACHABLE -> "FK proven reachable"
    DatasetTargetMode.GUARANTEED_UNREACHABLE -> "Guaranteed unreachable"
}

internal fun buildDatasetRequirements(
    requestedRowsText: String,
    minimumRobotCountText: String,
    exactDatasetContract: Boolean,
    toleranceText: String,
    iterationsText: String,
    dampingText: String,
    maxStepText: String,
    targetMode: DatasetTargetMode?,
    reachableFractionText: String
): TrainingDatasetRequirements? = runCatching {
    val requestedRows = requireNotNull(ScientificNumberParser.parseInt(requestedRowsText))
    val minimumRobots = requireNotNull(ScientificNumberParser.parseInt(minimumRobotCountText))
    require(requestedRows in 30..1_000_000)
    require(minimumRobots in 1..10_000)
    val tolerance = if (exactDatasetContract) requireNotNull(ScientificNumberParser.parseDouble(toleranceText)) else null
    val iterations = if (exactDatasetContract) requireNotNull(ScientificNumberParser.parseInt(iterationsText)) else null
    val damping = if (exactDatasetContract) requireNotNull(ScientificNumberParser.parseDouble(dampingText)) else null
    val maxStep = if (exactDatasetContract) requireNotNull(ScientificNumberParser.parseDouble(maxStepText)) else null
    require(tolerance == null || tolerance.isFinite() && tolerance > 0.0)
    require(iterations == null || iterations > 0)
    require(damping == null || damping.isFinite() && damping > 0.0)
    require(maxStep == null || maxStep.isFinite() && maxStep > 0.0)
    val reachableFraction = reachableFractionText.takeIf(String::isNotBlank)?.let { requireNotNull(ScientificNumberParser.parseDouble(it)) }
    require(reachableFraction == null || reachableFraction.isFinite() && reachableFraction in 0.0..1.0)
    TrainingDatasetRequirements(
        requestedRows = requestedRows,
        minimumRobotCount = minimumRobots,
        exactTolerance = tolerance,
        exactMaxIterations = iterations,
        exactDamping = damping,
        exactMaxStep = maxStep,
        targetMode = targetMode,
        reachableFraction = reachableFraction
    )
}.getOrNull()

internal fun buildConfig(
    manifest: DatasetManifest?,
    runName: String,
    featureSelections: List<FeatureSelectionSpec>,
    modelKind: TrainingModelKind,
    resourceMode: TrainingResourceMode,
    splitStrategy: TrainingSplitStrategy,
    maximumRowsText: String,
    epochsText: String,
    batchSizeText: String,
    learningRateText: String,
    l2Text: String,
    hiddenUnitsText: String,
    seedText: String,
    patienceText: String,
    workerCount: Int,
    safeTrainingRows: Int,
    datasetRequirements: TrainingDatasetRequirements,
    expectedCorpusSha256: String
): Pair<LocalTrainingConfig?, String?> {
    if (manifest == null || !File(manifest.csvPath).exists()) return null to "Select an existing dataset CSV."
    if (runName.isBlank()) return null to "Training run name must not be blank."
    if (featureSelections.isEmpty()) return null to "Select at least one feature set."
    val maximumRows = ScientificNumberParser.parseInt(maximumRowsText) ?: return null to "Maximum rows must be an integer."
    val epochs = ScientificNumberParser.parseInt(epochsText) ?: return null to "Epochs must be an integer."
    val batch = ScientificNumberParser.parseInt(batchSizeText) ?: return null to "Batch size must be an integer."
    val learningRate = ScientificNumberParser.parseDouble(learningRateText) ?: return null to "Learning rate must be numeric."
    val l2 = ScientificNumberParser.parseDouble(l2Text) ?: return null to "L2 regularization must be numeric."
    val hidden = ScientificNumberParser.parseInt(hiddenUnitsText) ?: return null to "Hidden units must be an integer."
    val seed = ScientificNumberParser.parseInt(seedText) ?: return null to "Random seed must be an integer."
    val patience = ScientificNumberParser.parseInt(patienceText) ?: return null to "Patience must be an integer."
    val plan = SingleRunDatasetPlanner.plan(manifest, datasetRequirements, safeTrainingRows)
    if (!plan.canTrain || datasetRequirements.requestedRows != maximumRows) return null to "Dataset requirements or the effective row limit are not valid for this run."
    val config =
        LocalTrainingConfig(
            runName = runName.trim(),
            datasetPath = manifest.csvPath,
            compareFeatureProfiles = false,
            singleFeatureProfile = featureSelections.first().sourceProfile,
            featureSelections = featureSelections,
            modelKind = modelKind,
            resourceMode = resourceMode,
            splitStrategy = splitStrategy,
            maximumRows = plan.effectiveRows,
            sampleAcrossEntireDataset = true,
            expectedDatasetRows = manifest.rowCount,
            managedDatasetManifest = manifest,
            datasetRequirements = datasetRequirements,
            expectedCorpusSha256 = expectedCorpusSha256,
            epochs = epochs,
            batchSize = batch,
            learningRate = learningRate,
            l2Regularization = l2,
            hiddenUnits = hidden,
            randomSeed = seed,
            earlyStoppingPatience = patience,
            workerCount = workerCount
        )
    return runCatching {
        require(maximumRows in 30..1_000_000)
        val issues = TrainingControlContract.issues(config)
        require(issues.isEmpty()) { issues.values.joinToString(" ") }
        config to null
    }.getOrElse {
        null to (it.message ?: "Check the marked training controls.")
    }
}

private fun mapProgress(
    update: LocalTrainingProgress,
    startedAtMillis: Long,
    telemetrySampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val elapsed = ((System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)) / 1000.0
    val phase =
        when (update.phase) {
            LocalTrainingPhase.IDLE -> DiagnosticProgressPhase.IDLE
            LocalTrainingPhase.READING_DATASET,
            LocalTrainingPhase.PREPARING_SPLITS -> DiagnosticProgressPhase.PLANNING
            LocalTrainingPhase.TRAINING_BASELINE,
            LocalTrainingPhase.TRAINING_CONTEXT -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
            LocalTrainingPhase.EVALUATING,
            LocalTrainingPhase.SAVING -> DiagnosticProgressPhase.AGGREGATING
            LocalTrainingPhase.COMPLETED -> DiagnosticProgressPhase.COMPLETED
            LocalTrainingPhase.FAILED -> DiagnosticProgressPhase.FAILED
        }
    return DiagnosticProgressState(
        isRunning = update.phase !in setOf(LocalTrainingPhase.COMPLETED, LocalTrainingPhase.FAILED),
        phase = phase,
        completedRuns = update.completedWorkUnits,
        totalRuns = update.totalWorkUnits,
        runsPerSecond = Double.NaN,
        estimatedSecondsRemaining = Double.NaN,
        elapsedSeconds = elapsed,
        message = update.message,
        telemetry = telemetrySampler.sample()
    )
}

internal fun trainingTimeline(
    phase: LocalTrainingPhase,
    failedStage: Int? = null,
    cancelled: Boolean = false
): List<DiagnosticTimelineStep> {
    val ordered =
        listOf(
            TrainingTimelineDefinition(
                "Validate and encode dataset",
                "Checks the selected CSV and converts its exact ordered feature contract into finite model inputs.",
                "Pinned dataset path, manifest, SHA-256, ordered feature names and labels.",
                "Validated rows and encoded feature vectors.",
                "Training must never continue with a changed, malformed or differently ordered corpus."
            ),
            TrainingTimelineDefinition(
                "Group train / validation / test rows",
                "Builds the requested leakage-aware split with the recorded seed.",
                "Validated rows, grouping keys, split strategy and random seed.",
                "Disjoint training, validation and independent-test partitions with effective counts.",
                "Model fitting, candidate selection and final evaluation require distinct roles."
            ),
            TrainingTimelineDefinition(
                "Fit candidate parameters",
                "Optimises every candidate on training rows, one mini-batch at a time.",
                "Training partition, candidate plan, optimiser controls and effective workers.",
                "Candidate weights and recorded epoch metrics.",
                "This is the only stage that adjusts model parameters from examples."
            ),
            TrainingTimelineDefinition(
                "Select epoch and candidate using validation",
                "Applies early stopping and chooses among candidates using validation evidence.",
                "Validation partition and candidate checkpoints.",
                "Selected candidate and best epoch; independent-test rows remain unused.",
                "Selection must finish before the independent test can remain an untouched estimate."
            ),
            TrainingTimelineDefinition(
                "Evaluate independent test rows",
                "Measures the selected model once on its held-out test partition.",
                "Selected checkpoint and independent-test rows.",
                "Classification metrics, slices and inference timings.",
                "Test evidence reports final behaviour and is not fed back into candidate selection."
            ),
            TrainingTimelineDefinition(
                "Persist models and evidence",
                "Atomically stores models, metrics, split identity and effective controls.",
                "Selected model files, metrics, provenance and full control snapshot.",
                "A reopenable run ID and verified storage files.",
                "Training can be 100% complete while this separate publication stage is still running."
            )
        )
    val currentIndex = if (phase == LocalTrainingPhase.FAILED) failedStage ?: 0 else trainingStageIndex(phase)
    return ordered.mapIndexed { index, definition ->
        DiagnosticTimelineStep(
            label = definition.label,
            status =
                when {
                    phase == LocalTrainingPhase.FAILED && index == currentIndex && cancelled -> DiagnosticTimelineStatus.CANCELLED
                    phase == LocalTrainingPhase.FAILED && index == currentIndex -> DiagnosticTimelineStatus.FAILED
                    phase == LocalTrainingPhase.COMPLETED || (currentIndex >= 0 && index < currentIndex) -> DiagnosticTimelineStatus.COMPLETE
                    index == currentIndex -> DiagnosticTimelineStatus.RUNNING
                    else -> DiagnosticTimelineStatus.PENDING
                },
            description = definition.description,
            inputs = definition.inputs,
            outputs = definition.outputs,
            reason = definition.reason
        )
    }
}

private data class TrainingTimelineDefinition(
    val label: String,
    val description: String,
    val inputs: String,
    val outputs: String,
    val reason: String
)

private fun decimal(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.4f", value) else "N/A"

private fun percent(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100.0) else "N/A"

private fun signed(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%+.4f", value) else "N/A"
