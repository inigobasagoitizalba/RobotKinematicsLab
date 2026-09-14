package com.robotkinematicslab.mobile.ui.dataset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetGenerationProgress
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.ParallelScientificDatasetGenerator
import com.robotkinematicslab.mobile.dataset.buildDatasetManifest
import com.robotkinematicslab.mobile.dataset.plannedReachableTargetCount
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetGenerationCoordinator
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetPlan
import com.robotkinematicslab.mobile.performance.compute.AndroidDeviceComputeProfiler
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeRuntimeGuard
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.DatasetBuilderDraft
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ui.training.FeatureExperimentSelector
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.editor.DhInputRow
import com.robotkinematicslab.mobile.ui.editor.RobotEditorMapper
import com.robotkinematicslab.mobile.ui.editor.RobotEditorPanel
import com.robotkinematicslab.mobile.ui.editor.RobotEditorState
import com.robotkinematicslab.mobile.ui.input.ScientificNumberParser
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.dataset.robotstore.DatasetReadyModelsHeader
import com.robotkinematicslab.mobile.ui.dataset.robotstore.DatasetReadyRobotCard
import com.robotkinematicslab.mobile.ui.dataset.robotstore.IndustrialRobotReferenceCatalog
import com.robotkinematicslab.mobile.ui.dataset.robotstore.IndustrialRobotReferenceGallery
import com.robotkinematicslab.mobile.ui.dataset.robotstore.RobotStoreHero
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import com.robotkinematicslab.mobile.workspace.analysis.robotWorkspaceFingerprint
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceAutoProvisioner
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceStudyRepository
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DatasetBuilderSection {
    CREATE,
    CONTINUOUS,
    ROBOTS,
    SAVED,
    QUALITY
}

internal enum class DatasetCreationSection {
    IDENTITY,
    ROBOTS,
    SAMPLING,
    IK
}

internal data class DatasetCreationValidationIssue(
    val section: DatasetCreationSection,
    val fieldId: String,
    val message: String
)

internal data class DatasetRowPlan(
    val requestedSavedRows: Long,
    val maximumSolverAttempts: Long
)

internal fun datasetRowPlan(
    samplesPerRobotText: String,
    selectedRobotCount: Int,
    maxAttemptsPerRow: Int = 30
): DatasetRowPlan? {
    val samplesPerRobot = ScientificNumberParser.parseInt(samplesPerRobotText) ?: return null
    if (samplesPerRobot !in 1..1_000_000 || selectedRobotCount <= 0 || maxAttemptsPerRow <= 0) {
        return null
    }
    val requested =
        runCatching {
            Math.multiplyExact(samplesPerRobot.toLong(), selectedRobotCount.toLong())
        }.getOrNull() ?: return null
    if (requested > Int.MAX_VALUE) return null
    val attempts =
        runCatching { Math.multiplyExact(requested, maxAttemptsPerRow.toLong()) }
            .getOrNull() ?: return null
    return DatasetRowPlan(requestedSavedRows = requested, maximumSolverAttempts = attempts)
}

internal fun datasetGenerationConfigOrNull(
    datasetName: String,
    selectedRobots: List<SavedRobot>,
    samplesPerRobotText: String,
    randomSeedText: String,
    reachablePercentText: String,
    targetMode: DatasetTargetMode,
    filterMode: DatasetFilterMode,
    appendToExisting: Boolean,
    maxIterationsText: String,
    toleranceText: String,
    dampingText: String,
    maxStepText: String
): DatasetGenerationConfig? {
    if (
        validateDatasetCreationDraft(
            datasetName = datasetName,
            selectedRobotCount = selectedRobots.size,
            samplesPerRobotText = samplesPerRobotText,
            randomSeedText = randomSeedText,
            reachablePercentText = reachablePercentText,
            maxIterationsText = maxIterationsText,
            toleranceText = toleranceText,
            dampingText = dampingText,
            maxStepText = maxStepText
        ) != null
    ) {
        return null
    }
    return DatasetGenerationConfig(
        datasetName = datasetName.trim(),
        robots = selectedRobots,
        samplesPerRobot = requireNotNull(ScientificNumberParser.parseInt(samplesPerRobotText)),
        randomSeed = requireNotNull(ScientificNumberParser.parseInt(randomSeedText)),
        targetMode = targetMode,
        reachableFraction = requireNotNull(ScientificNumberParser.parseDouble(reachablePercentText)) / 100.0,
        filterMode = filterMode,
        append = appendToExisting,
        ikConfig =
            IKConfig(
                maxIterations = requireNotNull(ScientificNumberParser.parseInt(maxIterationsText)),
                tolerance = requireNotNull(ScientificNumberParser.parseDouble(toleranceText)),
                damping = requireNotNull(ScientificNumberParser.parseDouble(dampingText)),
                maxStep = requireNotNull(ScientificNumberParser.parseDouble(maxStepText))
            )
    )
}

internal fun validateDatasetCreationDraft(
    datasetName: String,
    selectedRobotCount: Int,
    samplesPerRobotText: String,
    randomSeedText: String,
    reachablePercentText: String,
    maxIterationsText: String,
    toleranceText: String,
    dampingText: String,
    maxStepText: String
): DatasetCreationValidationIssue? {
    return validateDatasetCreationDraftAll(
        datasetName = datasetName,
        selectedRobotCount = selectedRobotCount,
        samplesPerRobotText = samplesPerRobotText,
        randomSeedText = randomSeedText,
        reachablePercentText = reachablePercentText,
        maxIterationsText = maxIterationsText,
        toleranceText = toleranceText,
        dampingText = dampingText,
        maxStepText = maxStepText
    ).firstOrNull()
}

internal fun validateDatasetCreationDraftAll(
    datasetName: String,
    selectedRobotCount: Int,
    samplesPerRobotText: String,
    randomSeedText: String,
    reachablePercentText: String,
    maxIterationsText: String,
    toleranceText: String,
    dampingText: String,
    maxStepText: String
): List<DatasetCreationValidationIssue> {
    val samplesPerRobot = ScientificNumberParser.parseInt(samplesPerRobotText)
    val randomSeed = ScientificNumberParser.parseInt(randomSeedText)
    val reachablePercent = ScientificNumberParser.parseDouble(reachablePercentText)
    val maxIterations = ScientificNumberParser.parseInt(maxIterationsText)
    val tolerance = ScientificNumberParser.parseDouble(toleranceText)
    val damping = ScientificNumberParser.parseDouble(dampingText)
    val maxStep = ScientificNumberParser.parseDouble(maxStepText)
    val rowPlan = datasetRowPlan(samplesPerRobotText, selectedRobotCount)

    return buildList {
        when {
            datasetName.isBlank() -> add(DatasetCreationValidationIssue(DatasetCreationSection.IDENTITY, "dataset-name", "Enter a dataset name."))
            datasetName.length > 160 -> add(DatasetCreationValidationIssue(DatasetCreationSection.IDENTITY, "dataset-name", "Dataset name must contain at most 160 characters."))
            datasetName.any(Char::isISOControl) -> add(DatasetCreationValidationIssue(DatasetCreationSection.IDENTITY, "dataset-name", "Dataset name must not contain line breaks or control characters."))
        }
        if (selectedRobotCount == 0) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.ROBOTS, "selected-robots", "Select at least one robot."))
        }
        if (samplesPerRobot == null || samplesPerRobot !in 1..1_000_000) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.SAMPLING, "samples-per-robot", "Samples per robot must be between 1 and 1,000,000."))
        } else if (selectedRobotCount > 0 && rowPlan == null) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.SAMPLING, "planned-row-total", "This batch requests too many rows. Reduce rows per robot or split the work into append batches."))
        }
        if (randomSeed == null) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.SAMPLING, "random-seed", "Random seed must be a whole number."))
        }
        if (reachablePercent == null || reachablePercent !in 0.0..100.0) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.SAMPLING, "reachable-percent", "Reachable target percentage must be between 0 and 100."))
        }
        if (maxIterations == null || maxIterations !in 1..10_000) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.IK, "ik-max-iterations", "IK max iterations must be between 1 and 10,000."))
        }
        if (tolerance == null || !tolerance.isFinite() || tolerance !in 1e-9..1.0) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.IK, "ik-tolerance", "IK tolerance must be finite and between 1e-9 and 1."))
        }
        if (damping == null || !damping.isFinite() || damping !in 1e-9..10.0) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.IK, "ik-damping", "IK damping must be finite and between 1e-9 and 10."))
        }
        if (maxStep == null || !maxStep.isFinite() || maxStep !in 1e-6..10.0) {
            add(DatasetCreationValidationIssue(DatasetCreationSection.IK, "ik-max-step", "IK max step must be finite and between 1e-6 and 10."))
        }
    }
}

@Composable
fun DatasetBuilderPanel(
    continuousCoordinator: ContinuousDatasetGenerationCoordinator,
    onOpenRobotWorkspace: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    initialSection: DatasetBuilderSection = DatasetBuilderSection.CREATE,
    onSectionChanged: (DatasetBuilderSection) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val robotRepository = remember { RobotLibraryRepository(context) }
    val storageRepository = remember { DatasetStorageRepository(context) }
    val appStorageRepository = remember { AppStorageRepository(context) }
    val workspaceStudyRepository = remember(context) { RobotWorkspaceStudyRepository(context) }
    val workspaceProvisioner = remember(workspaceStudyRepository) {
        RobotWorkspaceAutoProvisioner(workspaceStudyRepository)
    }
    val restoredDraft = remember(appStorageRepository) {
        appStorageRepository.loadDatasetBuilderDraft()
    }
    val generator = remember { ParallelScientificDatasetGenerator() }
    val computeRuntimeGuard = remember(context) { ComputeRuntimeGuard(context) }
    val deviceComputeProfiler = remember(context) { AndroidDeviceComputeProfiler(context) }
    val deviceComputeProfile = remember { deviceComputeProfiler.read() }
    val continuousState by continuousCoordinator.state.collectAsState()
    val editorMapper = remember { RobotEditorMapper() }
    val cancellationRequested = remember { AtomicBoolean(false) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val lastDatasetTelemetryCaptureNanos = remember { AtomicLong(0L) }
    val datasetPerformanceSampleIndex = remember { AtomicInteger(0) }

    var section by remember(initialSection) { mutableStateOf(initialSection) }

    LaunchedEffect(section) {
        onSectionChanged(section)
    }
    var robots by remember { mutableStateOf(robotRepository.loadOrCreateDefaults()) }
    val robotLibraryRecoveryCopies = remember { robotRepository.listRecoveryCopies() }
    var selectedRobotIds by remember {
        val restoredSelection =
            restoredDraft.selectedRobotIds.filter { id -> robots.any { it.id == id } }.toSet()
        mutableStateOf(restoredSelection.ifEmpty { robots.take(1).map { it.id }.toSet() })
    }
    var manifests by remember { mutableStateOf(storageRepository.listManifests()) }
    var workspaceSummaries by remember {
        mutableStateOf(workspaceStudyRepository.listStudies())
    }
    var workspaceProvisioningRobotIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var workspaceProvisioningErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var datasetName by remember { mutableStateOf(restoredDraft.datasetName) }
    var samplesPerRobotText by remember { mutableStateOf(restoredDraft.samplesPerRobotText) }
    var randomSeedText by remember { mutableStateOf(restoredDraft.randomSeedText) }
    var reachablePercentText by remember { mutableStateOf(restoredDraft.reachablePercentText) }
    var targetMode by remember { mutableStateOf(restoredDraft.targetMode) }
    var filterMode by remember { mutableStateOf(restoredDraft.filterMode) }
    var appendToExisting by remember { mutableStateOf(restoredDraft.appendToExisting) }
    var maxIterationsText by remember { mutableStateOf(restoredDraft.maxIterationsText) }
    var toleranceText by remember { mutableStateOf(restoredDraft.toleranceText) }
    var dampingText by remember { mutableStateOf(restoredDraft.dampingText) }
    var maxStepText by remember { mutableStateOf(restoredDraft.maxStepText) }
    var plannedFeatureSelections by remember {
        mutableStateOf(
            listOf(
                FeatureSelectionSpec.complete(TrainingFeatureProfile.BASELINE_KINEMATICS),
                FeatureSelectionSpec.complete(TrainingFeatureProfile.CONTEXT_EXPANDED)
            )
        )
    }

    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<DatasetGenerationProgress?>(null) }
    var datasetTelemetryProgress by remember {
        mutableStateOf<DiagnosticProgressState?>(null)
    }
    val datasetPerformanceSamples = remember {
        mutableStateListOf<DiagnosticPerformanceSample>()
    }
    var datasetGenerationStartedAtMs by remember { mutableLongStateOf(0L) }
    var statusMessage by remember {
        mutableStateOf("Dataset draft restored. Review the scientific plan before generation.")
    }

    var editingRobotId by remember { mutableStateOf<String?>(null) }
    var showRobotEditor by remember { mutableStateOf(false) }
    var robotEditorState by remember {
        mutableStateOf(
            editorMapper.toEditorState(DatasetRobotPresets().buildDefaults()[1].robot)
                .copy(robotName = "Custom Robot")
        )
    }

    val robotBuildResult = remember(robotEditorState) {
        editorMapper.buildRobotDefinition(robotEditorState)
    }

    fun queueBaselineWorkspace(savedRobot: SavedRobot) {
        if (savedRobot.id in workspaceProvisioningRobotIds) return
        val fingerprint = robotWorkspaceFingerprint(savedRobot.robot)
        if (workspaceSummaries.any { it.robotFingerprint == fingerprint }) return

        workspaceProvisioningRobotIds = workspaceProvisioningRobotIds + savedRobot.id
        workspaceProvisioningErrors = workspaceProvisioningErrors - savedRobot.id
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    workspaceProvisioner.ensureBaseline(
                        savedRobot = savedRobot,
                        requestedWorkerCount = computeRuntimeGuard.currentWorkerLimit()
                    )
                }
            }.onSuccess {
                workspaceSummaries = withContext(Dispatchers.IO) {
                    workspaceStudyRepository.listStudies()
                }
            }.onFailure { error ->
                workspaceProvisioningErrors =
                    workspaceProvisioningErrors +
                        (savedRobot.id to (error.message ?: "Automatic workspace preparation failed."))
            }
            workspaceProvisioningRobotIds = workspaceProvisioningRobotIds - savedRobot.id
        }
    }

    val continuousDraftPlan =
        remember(
            datasetName,
            selectedRobotIds,
            robots,
            randomSeedText,
            reachablePercentText,
            targetMode,
            filterMode,
            maxIterationsText,
            toleranceText,
            dampingText,
            maxStepText
        ) {
            runCatching {
                ContinuousDatasetPlan(
                    datasetName = datasetName,
                    robotIds = robots.filter { it.id in selectedRobotIds }.map(SavedRobot::id),
                    randomSeed = requireNotNull(ScientificNumberParser.parseInt(randomSeedText)),
                    targetMode = targetMode,
                    reachableFraction =
                        requireNotNull(ScientificNumberParser.parseDouble(reachablePercentText)) / 100.0,
                    filterMode = filterMode,
                    ikConfig =
                        IKConfig(
                            maxIterations = requireNotNull(ScientificNumberParser.parseInt(maxIterationsText)),
                            tolerance = requireNotNull(ScientificNumberParser.parseDouble(toleranceText)),
                            damping = requireNotNull(ScientificNumberParser.parseDouble(dampingText)),
                            maxStep = requireNotNull(ScientificNumberParser.parseDouble(maxStepText))
                        )
                ).validated()
            }.getOrNull()
        }

    LaunchedEffect(
        datasetName,
        samplesPerRobotText,
        randomSeedText,
        reachablePercentText,
        targetMode,
        filterMode,
        appendToExisting,
        maxIterationsText,
        toleranceText,
        dampingText,
        maxStepText,
        selectedRobotIds
    ) {
        delay(300L)
        withContext(Dispatchers.IO) {
            appStorageRepository.saveDatasetBuilderDraft(
                DatasetBuilderDraft(
                    datasetName = datasetName,
                    samplesPerRobotText = samplesPerRobotText,
                    randomSeedText = randomSeedText,
                    reachablePercentText = reachablePercentText,
                    targetMode = targetMode,
                    filterMode = filterMode,
                    appendToExisting = appendToExisting,
                    maxIterationsText = maxIterationsText,
                    toleranceText = toleranceText,
                    dampingText = dampingText,
                    maxStepText = maxStepText,
                    selectedRobotIds = selectedRobotIds
                )
            )
        }
    }

    LaunchedEffect(section, robots) {
        if (section == DatasetBuilderSection.ROBOTS) {
            robots.forEach(::queueBaselineWorkspace)
        }
    }

    BackHandler(enabled = section == DatasetBuilderSection.ROBOTS) {
        if (showRobotEditor) {
            showRobotEditor = false
        } else {
            selectedRobotIds = retainedDatasetRobotSelection(selectedRobotIds, robots.map(SavedRobot::id).toSet())
            section = DatasetBuilderSection.CREATE
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Scientific Dataset Builder",
            style = MaterialTheme.typography.headlineSmall
        )

        JargonHelpNotice()

        JargonAwareText(
            text =
                "Generate reproducible IK rows from saved DH robots. Each CSV contains the raw kinematics variables and the enriched safety/diagnostic variables in the same record.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (robotLibraryRecoveryCopies.isNotEmpty()) {
            DatasetCard(title = "Recovered robot library") {
                Text(
                    "A corrupt library was replaced with validated defaults without deleting the original evidence. " +
                        "${robotLibraryRecoveryCopies.size} recovery copy/copies are preserved in Project Storage.",
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    robotLibraryRecoveryCopies.first().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        CompactSelectionMenu(
            options = DatasetBuilderSection.entries,
            selected = section,
            label = { destination ->
                when (destination) {
                    DatasetBuilderSection.CREATE -> "Create"
                    DatasetBuilderSection.ROBOTS ->
                        "Robots (${IndustrialRobotReferenceCatalog.entries.size})"
                    DatasetBuilderSection.SAVED -> "Saved (${manifests.size})"
                    DatasetBuilderSection.QUALITY -> "Quality"
                    DatasetBuilderSection.CONTINUOUS ->
                        if (continuousState.isActive) {
                            "Continuous · ${continuousState.committedDatasetRows} rows"
                        } else {
                            "Continuous growth"
                        }
                }
            },
            onSelected = { destination ->
                if (
                    destination == DatasetBuilderSection.SAVED ||
                    destination == DatasetBuilderSection.QUALITY
                ) {
                    manifests = storageRepository.listManifests()
                }
                section = destination
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.DatasetSectionSelector),
            optionTestTag = { destination ->
                "dataset-section-${destination.name.lowercase(Locale.ROOT)}"
            }
        )

        when (section) {
            DatasetBuilderSection.CREATE -> {
                val currentTelemetryProgress = datasetTelemetryProgress

                if (isGenerating && currentTelemetryProgress != null) {
                    DatasetGenerationLoadingPanel(
                        progressState = currentTelemetryProgress,
                        performanceSamples = datasetPerformanceSamples,
                        rowProgress = progress,
                        onCancel = {
                            processCoordinator.requestCancel(ResearchProcessIds.DATASET_GENERATION)
                            statusMessage = "Cancellation requested. Finishing the current calculation safely."
                        }
                    )
                } else {
                    DatasetCreationSection(
                    robots = robots,
                    selectedRobotIds = selectedRobotIds,
                    onSelectedRobotIdsChange = { selectedRobotIds = it },
                    datasetName = datasetName,
                    onDatasetNameChange = { datasetName = it },
                    samplesPerRobotText = samplesPerRobotText,
                    onSamplesPerRobotChange = { samplesPerRobotText = it },
                    randomSeedText = randomSeedText,
                    onRandomSeedChange = { randomSeedText = it },
                    reachablePercentText = reachablePercentText,
                    onReachablePercentChange = { reachablePercentText = it },
                    targetMode = targetMode,
                    onTargetModeChange = { targetMode = it },
                    filterMode = filterMode,
                    onFilterModeChange = { filterMode = it },
                    appendToExisting = appendToExisting,
                    onAppendToExistingChange = { appendToExisting = it },
                    maxIterationsText = maxIterationsText,
                    onMaxIterationsChange = { maxIterationsText = it },
                    toleranceText = toleranceText,
                    onToleranceChange = { toleranceText = it },
                    dampingText = dampingText,
                    onDampingChange = { dampingText = it },
                    maxStepText = maxStepText,
                    onMaxStepChange = { maxStepText = it },
                    plannedFeatureSelections = plannedFeatureSelections,
                    onPlannedFeatureSelectionsChange = { plannedFeatureSelections = it },
                    isGenerating = isGenerating,
                    continuousGenerationActive = continuousState.isActive,
                    progress = progress,
                    statusMessage = statusMessage,
                    outputDirectory = storageRepository.outputDirectory().absolutePath,
                    existingManifest = manifests.firstOrNull { it.datasetName == datasetName.trim() },
                    onLoadOneMicronPreset = {
                        datasetName = "verified_one_micron_ik"
                        samplesPerRobotText = "10000"
                        randomSeedText = "2604"
                        reachablePercentText = "100"
                        targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE
                        filterMode = DatasetFilterMode.ACCEPTED_ONLY
                        appendToExisting = false
                        maxIterationsText = "800"
                        toleranceText = "0.000001"
                        dampingText = "0.01"
                        maxStepText = "0.02"
                        selectedRobotIds = robots.map { it.id }.toSet()
                        statusMessage = "Verified 1 µm IK preset loaded. Review the 100,000-row plan, then generate."
                    },
                    onOpenRobotLibrary = { section = DatasetBuilderSection.ROBOTS },
                    onOpenContinuous = { section = DatasetBuilderSection.CONTINUOUS },
                    onCancel = {
                        processCoordinator.requestCancel(ResearchProcessIds.DATASET_GENERATION)
                        statusMessage = "Cancellation requested. Finishing the current calculation safely."
                    },
                    onGenerate = generateDataset@{
                        if (processCoordinator.isActive(ResearchProcessIds.DATASET_GENERATION)) {
                            statusMessage = "A dataset is already being generated. Open Research activity to inspect it."
                            return@generateDataset
                        }
                        val selectedRobots = robots.filter { it.id in selectedRobotIds }
                        val validationError =
                            validateDatasetCreationDraft(
                                datasetName = datasetName,
                                selectedRobotCount = selectedRobots.size,
                                samplesPerRobotText = samplesPerRobotText,
                                randomSeedText = randomSeedText,
                                reachablePercentText = reachablePercentText,
                                maxIterationsText = maxIterationsText,
                                toleranceText = toleranceText,
                                dampingText = dampingText,
                                maxStepText = maxStepText
                            )?.message
                        val config =
                            datasetGenerationConfigOrNull(
                                datasetName = datasetName,
                                selectedRobots = selectedRobots,
                                samplesPerRobotText = samplesPerRobotText,
                                randomSeedText = randomSeedText,
                                reachablePercentText = reachablePercentText,
                                targetMode = targetMode,
                                filterMode = filterMode,
                                appendToExisting = appendToExisting,
                                maxIterationsText = maxIterationsText,
                                toleranceText = toleranceText,
                                dampingText = dampingText,
                                maxStepText = maxStepText
                            )

                        if (validationError != null || config == null) {
                            statusMessage = validationError ?: "The dataset configuration is invalid."
                        } else {
                            cancellationRequested.set(false)
                            progress = null
                            isGenerating = true
                            statusMessage = "Starting dataset generation…"
                            datasetGenerationStartedAtMs = System.currentTimeMillis()
                            lastDatasetTelemetryCaptureNanos.set(0L)
                            datasetPerformanceSampleIndex.set(0)
                            datasetPerformanceSamples.clear()

                            val requestedRows = config.samplesPerRobot * selectedRobots.size
                            val initialTelemetryProgress =
                                DiagnosticProgressState(
                                    isRunning = true,
                                    phase = DiagnosticProgressPhase.PLANNING,
                                    completedRuns = 0,
                                    totalRuns = requestedRows,
                                    message = "Validating configuration and preparing the dataset output.",
                                    telemetry = telemetrySampler.sample()
                                )
                            datasetTelemetryProgress = initialTelemetryProgress
                            datasetPerformanceSamples +=
                                DiagnosticPerformanceSample.fromProgressState(
                                    sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                    progressState = initialTelemetryProgress
                                )

                            processCoordinator.launch(
                                id = ResearchProcessIds.DATASET_GENERATION,
                                title = "Dataset · ${config.datasetName}",
                                kind = ResearchProcessKind.DATASET,
                                cancellationAction = { cancellationRequested.set(true) }
                            ) { process ->
                                process.report(
                                    progressFraction = 0.0,
                                    stage = "Preparing dataset",
                                    detail = "Validating provenance and preparing $requestedRows requested rows."
                                )
                                try {
                                    val result =
                                        withContext(Dispatchers.IO) {
                                            val appendTarget =
                                                if (appendToExisting) {
                                                    storageRepository.resolveAppendOrCreateTarget(datasetName)
                                                } else {
                                                    null
                                                }
                                            val existingManifest = appendTarget?.manifest
                                            com.robotkinematicslab.mobile.dataset.DatasetScientificContract.requireCompatible(existingManifest, config)
                                            val csvFile =
                                                appendTarget?.csvFile
                                                    ?: storageRepository.resolveCsvFile(datasetName)
                                            val verifiedExistingRows = appendTarget?.existingRowCount ?: 0L
                                            val generationIndex = existingManifest?.generationCount ?: 0

                                            val generationResult = generator.generate(
                                                config = config,
                                                csvFile = csvFile,
                                                existingRowCount = verifiedExistingRows,
                                                generationIndex = generationIndex,
                                                workerCount = computeRuntimeGuard.currentWorkerLimit(),
                                                cancellationRequested = cancellationRequested,
                                                onProgress = { update ->
                                                    val now = System.currentTimeMillis()
                                                    val capturedAtNanos = System.nanoTime()
                                                    val previousCaptureNanos = lastDatasetTelemetryCaptureNanos.get()
                                                    val shouldPublish =
                                                        update.addedRows == 0 ||
                                                                update.addedRows >= update.requestedRows ||
                                                                capturedAtNanos - previousCaptureNanos >=
                                                                    HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS

                                                    if (
                                                        shouldPublish &&
                                                        lastDatasetTelemetryCaptureNanos.compareAndSet(
                                                            previousCaptureNanos,
                                                            capturedAtNanos
                                                        )
                                                    ) {
                                                        val elapsedSeconds =
                                                            ((now - datasetGenerationStartedAtMs).coerceAtLeast(0L)) / 1000.0
                                                        val rowsPerSecond =
                                                            if (elapsedSeconds > 0.0) update.addedRows / elapsedSeconds else 0.0
                                                        val remainingRows =
                                                            (update.requestedRows - update.addedRows).coerceAtLeast(0)
                                                        val estimatedSeconds =
                                                            if (rowsPerSecond > 0.0) remainingRows / rowsPerSecond else Double.NaN
                                                        val telemetry = telemetrySampler.sample()

                                                        process.report(
                                                            progressFraction = update.fraction.toDouble(),
                                                            stage = "Generating rows · ${update.addedRows}/${update.requestedRows}",
                                                            detail = update.message
                                                        )

                                                        coroutineScope.launch {
                                                            progress = update
                                                            val telemetryProgress =
                                                                DiagnosticProgressState(
                                                                    isRunning = true,
                                                                    phase = DiagnosticProgressPhase.SEQUENTIAL_RUNS,
                                                                    completedRuns = update.addedRows,
                                                                    totalRuns = update.requestedRows,
                                                                    currentLinkCount = null,
                                                                    runsPerSecond = rowsPerSecond,
                                                                    estimatedSecondsRemaining = estimatedSeconds,
                                                                    elapsedSeconds = elapsedSeconds,
                                                                    message = update.message,
                                                                    telemetry = telemetry
                                                                )
                                                            datasetTelemetryProgress = telemetryProgress
                                                            datasetPerformanceSamples +=
                                                                DiagnosticPerformanceSample.fromProgressState(
                                                                    sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                                                    progressState = telemetryProgress
                                                                )
                                                        }
                                                    }
                                                },
                                                onCheckpoint = { addedRows, totalRows ->
                                                    storageRepository.saveManifest(
                                                        buildDatasetManifest(
                                                            existingManifest = existingManifest.takeIf { appendToExisting },
                                                            config = config,
                                                            csvPath = csvFile.absolutePath,
                                                            totalRows = totalRows,
                                                            addedRows = addedRows.toLong(),
                                                            generationIndex = generationIndex,
                                                            updatedAtEpochMillis = System.currentTimeMillis()
                                                        )
                                                    )
                                                },
                                                lockedPreflight = {
                                                    if (appendTarget != null) {
                                                        val currentTarget =
                                                            storageRepository.resolveAppendOrCreateTarget(datasetName)
                                                        com.robotkinematicslab.mobile.dataset.DatasetScientificContract.requireCompatible(currentTarget.manifest, config)
                                                        check(currentTarget == appendTarget) {
                                                            "The dataset changed after provenance validation. Append was blocked; retry with the latest saved state."
                                                        }
                                                    }
                                                }
                                            )
                                            generationResult
                                        }

                                    manifests =
                                        withContext(Dispatchers.IO) {
                                            storageRepository.listManifests()
                                        }
                                    statusMessage =
                                        "${result.message} Added ${result.addedRows} rows; total ${result.totalRows}. File: ${result.csvPath}"

                                    val elapsedSeconds =
                                        ((System.currentTimeMillis() - datasetGenerationStartedAtMs).coerceAtLeast(0L)) / 1000.0
                                    val finalTelemetryProgress =
                                        DiagnosticProgressState(
                                            isRunning = false,
                                            phase =
                                                if (result.completed) {
                                                    DiagnosticProgressPhase.COMPLETED
                                                } else {
                                                    DiagnosticProgressPhase.FAILED
                                                },
                                            completedRuns = result.addedRows,
                                            totalRuns = config.samplesPerRobot * config.robots.size,
                                            runsPerSecond =
                                                if (elapsedSeconds > 0.0) result.addedRows / elapsedSeconds else 0.0,
                                            estimatedSecondsRemaining = 0.0,
                                            elapsedSeconds = elapsedSeconds,
                                            message = result.message,
                                            telemetry = telemetrySampler.sample()
                                        )
                                    datasetTelemetryProgress = finalTelemetryProgress
                                    datasetPerformanceSamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                            progressState = finalTelemetryProgress
                                        )
                                    when {
                                        result.completed -> process.completed(statusMessage)
                                        result.cancelled -> process.cancelled(statusMessage)
                                        else -> process.failed(statusMessage)
                                    }
                                } catch (cancelled: CancellationException) {
                                    val cancelledTelemetryProgress =
                                        datasetTelemetryProgress?.copy(
                                            isRunning = false,
                                            phase = DiagnosticProgressPhase.IDLE,
                                            message = "Dataset generation was cancelled before publication.",
                                            telemetry = telemetrySampler.sample()
                                        )
                                    datasetTelemetryProgress = cancelledTelemetryProgress
                                    if (cancelledTelemetryProgress != null) {
                                        datasetPerformanceSamples +=
                                            DiagnosticPerformanceSample.fromProgressState(
                                                sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                                progressState = cancelledTelemetryProgress
                                            )
                                    }
                                    process.cancelled("Dataset generation was cancelled before publication.")
                                } catch (error: Exception) {
                                    statusMessage = "Generation failed: ${error.message ?: error::class.simpleName}"
                                    val failedTelemetryProgress =
                                        datasetTelemetryProgress?.copy(
                                            isRunning = false,
                                            phase = DiagnosticProgressPhase.FAILED,
                                            message = statusMessage,
                                            telemetry = telemetrySampler.sample()
                                        )
                                    datasetTelemetryProgress = failedTelemetryProgress
                                    if (failedTelemetryProgress != null) {
                                        datasetPerformanceSamples +=
                                            DiagnosticPerformanceSample.fromProgressState(
                                                sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                                progressState = failedTelemetryProgress
                                            )
                                    }
                                    process.failed(statusMessage)
                                } finally {
                                    isGenerating = false
                                }
                            }.onFailure { error ->
                                isGenerating = false
                                statusMessage = error.message ?: "Dataset generation could not start."
                                val failedStartProgress =
                                    datasetTelemetryProgress?.copy(
                                        isRunning = false,
                                        phase = DiagnosticProgressPhase.FAILED,
                                        message = statusMessage,
                                        telemetry = telemetrySampler.sample()
                                    )
                                datasetTelemetryProgress = failedStartProgress
                                if (failedStartProgress != null) {
                                    datasetPerformanceSamples +=
                                        DiagnosticPerformanceSample.fromProgressState(
                                            sampleIndex = datasetPerformanceSampleIndex.incrementAndGet(),
                                            progressState = failedStartProgress
                                        )
                                }
                            }
                        }
                    }
                    )
                }
            }

            DatasetBuilderSection.CONTINUOUS -> {
                ContinuousDatasetPanel(
                    state = continuousState,
                    draftPlan = continuousDraftPlan,
                    deviceProfile = deviceComputeProfile,
                    onStartCurrentPlan = { cpuPercent, memoryPercent ->
                        continuousDraftPlan?.let { plan ->
                            continuousCoordinator.start(
                                plan =
                                    plan.copy(
                                        cpuBudgetPercent = cpuPercent,
                                        memoryBudgetPercent = memoryPercent
                                    ),
                                availableRobots = robots
                            )
                        }
                    },
                    onResumeSavedPlan = { cpuPercent, memoryPercent ->
                        continuousState.plan?.let { plan ->
                            continuousCoordinator.start(
                                plan =
                                    plan.copy(
                                        cpuBudgetPercent = cpuPercent,
                                        memoryBudgetPercent = memoryPercent
                                    ),
                                availableRobots = robots
                            )
                        }
                    },
                    onPause = continuousCoordinator::pause
                )
            }

            DatasetBuilderSection.ROBOTS -> {
                RobotLibrarySection(
                    robots = robots,
                    libraryEditingEnabled = !continuousState.isActive,
                    selectedRobotIds = selectedRobotIds,
                    showEditor = showRobotEditor,
                    editorState = robotEditorState,
                    editorErrors = robotBuildResult.errors,
                    editingRobotId = editingRobotId,
                    onToggleSelected = { id ->
                        selectedRobotIds =
                            if (id in selectedRobotIds) {
                                selectedRobotIds - id
                            } else {
                                selectedRobotIds + id
                            }
                    },
                    onSelectAll = {
                        selectedRobotIds = robots.map { it.id }.toSet()
                    },
                    onSelectNone = { selectedRobotIds = emptySet() },
                    onAddRobot = {
                        robotName ->
                        editingRobotId = null
                        robotEditorState =
                            editorMapper.toEditorState(DatasetRobotPresets().buildDefaults()[1].robot)
                                .copy(robotName = robotName)
                        showRobotEditor = true
                    },
                    onEditRobot = { savedRobot ->
                        editingRobotId = savedRobot.id
                        robotEditorState = editorMapper.toEditorState(savedRobot.robot)
                        showRobotEditor = true
                    },
                    onDeleteRobot = { savedRobot ->
                        robots = robots.filterNot { it.id == savedRobot.id }
                        selectedRobotIds = selectedRobotIds - savedRobot.id
                        robotRepository.save(robots)
                    },
                    onResetExamples = {
                        robots = robotRepository.resetToDefaults()
                        selectedRobotIds = robots.take(1).map { it.id }.toSet()
                        showRobotEditor = false
                        statusMessage = "The 10 example robots were restored."
                    },
                    onEditorStateChange = { robotEditorState = it },
                    onCloseEditor = { showRobotEditor = false },
                    onLoadTemplate = {
                        robotEditorState =
                            editorMapper.toEditorState(DatasetRobotPresets().buildDefaults()[1].robot)
                                .copy(robotName = robotEditorState.robotName)
                    },
                    workspaceSummaries = workspaceSummaries,
                    workspaceProvisioningRobotIds = workspaceProvisioningRobotIds,
                    workspaceProvisioningErrors = workspaceProvisioningErrors,
                    onOpenRobotWorkspace = onOpenRobotWorkspace,
                    onRetryRobotWorkspace = ::queueBaselineWorkspace,
                    onSaveSelectionAndReturn = {
                        selectedRobotIds = retainedDatasetRobotSelection(selectedRobotIds, robots.map(SavedRobot::id).toSet())
                        showRobotEditor = false
                        section = DatasetBuilderSection.CREATE
                    },
                    onSaveRobot = {
                        val robot = robotBuildResult.robot
                        if (robot != null) {
                            runCatching {
                                robotRepository.upsertRobot(
                                    existingId = editingRobotId,
                                    robot = robot
                                )
                            }.onSuccess { mutation ->
                                robots = mutation.robots
                                selectedRobotIds = selectedRobotIds + mutation.savedRobot.id
                                showRobotEditor = false
                                statusMessage = "Robot ${mutation.savedRobot.robot.name} saved and selected."
                            }.onFailure { failure ->
                                statusMessage =
                                    failure.message
                                        ?: "The robot could not be saved. The previous library was preserved."
                            }
                        }
                    }
                )
            }

            DatasetBuilderSection.SAVED -> {
                SavedDatasetsSection(
                    manifests = manifests,
                    robots = robots,
                    onAddMore = { manifest ->
                        datasetName = manifest.datasetName
                        samplesPerRobotText = manifest.samplesPerRobotLastRun.toString()
                        randomSeedText = manifest.randomSeed.toString()
                        targetMode = manifest.targetMode
                        reachablePercentText =
                            String.format(Locale.US, "%.1f", manifest.reachableFraction * 100.0)
                                .trimEnd('0')
                                .trimEnd('.')
                        filterMode = manifest.filterMode
                        selectedRobotIds =
                            manifest.robotIds.filter { id -> robots.any { it.id == id } }.toSet()
                        appendToExisting = true
                        statusMessage =
                            "Loaded ${manifest.datasetName}. Choose a new batch size and press Generate to append rows."
                        section = DatasetBuilderSection.CREATE
                    }
                )
            }

            DatasetBuilderSection.QUALITY -> {
                DatasetQualityPanel(
                    manifests = manifests,
                    continuousState = continuousState,
                    onOpenContinuous = { section = DatasetBuilderSection.CONTINUOUS }
                )
            }
        }
    }
}

@Composable
private fun DatasetGenerationLoadingPanel(
    progressState: DiagnosticProgressState,
    performanceSamples: List<DiagnosticPerformanceSample>,
    rowProgress: DatasetGenerationProgress?,
    onCancel: () -> Unit
) {
    val timeline =
        listOf(
            DiagnosticTimelineStep(
                label = "Validate robots and generation settings",
                status = DiagnosticTimelineStatus.COMPLETE
            ),
            DiagnosticTimelineStep(
                label = "Prepare or append the CSV output",
                status =
                    if (rowProgress == null) {
                        DiagnosticTimelineStatus.RUNNING
                    } else {
                        DiagnosticTimelineStatus.COMPLETE
                    }
            ),
            DiagnosticTimelineStep(
                label = "Generate targets, solve IK and build enriched rows",
                status =
                    when {
                        rowProgress == null -> DiagnosticTimelineStatus.PENDING
                        progressState.phase == DiagnosticProgressPhase.FAILED -> DiagnosticTimelineStatus.FAILED
                        progressState.isRunning -> DiagnosticTimelineStatus.RUNNING
                        else -> DiagnosticTimelineStatus.COMPLETE
                    }
            ),
            DiagnosticTimelineStep(
                label = "Write checkpoints and update the manifest",
                status =
                    when {
                        progressState.phase == DiagnosticProgressPhase.COMPLETED -> DiagnosticTimelineStatus.COMPLETE
                        progressState.isRunning -> DiagnosticTimelineStatus.RUNNING
                        else -> DiagnosticTimelineStatus.PENDING
                    }
            ),
            DiagnosticTimelineStep(
                label = "Finalize the reproducible dataset batch",
                status =
                    when (progressState.phase) {
                        DiagnosticProgressPhase.COMPLETED -> DiagnosticTimelineStatus.COMPLETE
                        DiagnosticProgressPhase.FAILED -> DiagnosticTimelineStatus.FAILED
                        else -> DiagnosticTimelineStatus.PENDING
                    }
            )
        )

    DiagnosticLoadingProgressCard(
        progressState = progressState,
        performanceSamples = performanceSamples,
        config =
            DiagnosticLoadingCardConfig(
                runningTitle = "Scientific Dataset Generation Running",
                finishedTitle = "Scientific Dataset Generation",
                progressSectionTitle = "Dataset Progress",
                completedLabel = "Rows generated",
                remainingLabel = "Rows left",
                rateLabel = "Rows per second",
                showBenchmarkCoordinates = false,
                telemetrySessionType = "Dataset generation"
            ),
        timeline = timeline
    )

    rowProgress?.let { current ->
        DatasetCard(title = "Current dataset batch") {
            Text("Robot ${current.currentRobotIndex + 1} / ${current.totalRobots}: ${current.currentRobotName}")
            Text("Solver attempts: ${current.attempts}")
        }
    }

    OutlinedButton(
        onClick = onCancel,
        modifier =
            Modifier
                .fillMaxWidth()
                .tutorialAnchor(TutorialTargets.DatasetCancel)
    ) {
        Text("Cancel safely")
    }
}

@Composable
private fun DatasetCreationSection(
    robots: List<SavedRobot>,
    selectedRobotIds: Set<String>,
    onSelectedRobotIdsChange: (Set<String>) -> Unit,
    datasetName: String,
    onDatasetNameChange: (String) -> Unit,
    samplesPerRobotText: String,
    onSamplesPerRobotChange: (String) -> Unit,
    randomSeedText: String,
    onRandomSeedChange: (String) -> Unit,
    reachablePercentText: String,
    onReachablePercentChange: (String) -> Unit,
    targetMode: DatasetTargetMode,
    onTargetModeChange: (DatasetTargetMode) -> Unit,
    filterMode: DatasetFilterMode,
    onFilterModeChange: (DatasetFilterMode) -> Unit,
    appendToExisting: Boolean,
    onAppendToExistingChange: (Boolean) -> Unit,
    maxIterationsText: String,
    onMaxIterationsChange: (String) -> Unit,
    toleranceText: String,
    onToleranceChange: (String) -> Unit,
    dampingText: String,
    onDampingChange: (String) -> Unit,
    maxStepText: String,
    onMaxStepChange: (String) -> Unit,
    plannedFeatureSelections: List<FeatureSelectionSpec>,
    onPlannedFeatureSelectionsChange: (List<FeatureSelectionSpec>) -> Unit,
    isGenerating: Boolean,
    continuousGenerationActive: Boolean,
    progress: DatasetGenerationProgress?,
    statusMessage: String,
    outputDirectory: String,
    existingManifest: DatasetManifest?,
    onLoadOneMicronPreset: () -> Unit,
    onOpenRobotLibrary: () -> Unit,
    onOpenContinuous: () -> Unit,
    onCancel: () -> Unit,
    onGenerate: () -> Unit
) {
    val tutorialReporter = LocalTutorialActionReporter.current
    var requestedSection by remember { mutableStateOf<DatasetCreationSection?>(null) }
    var expandRequest by remember { mutableStateOf(0) }
    var replaceConfirmationVisible by remember { mutableStateOf(false) }
    val validationIssues =
        validateDatasetCreationDraftAll(
            datasetName = datasetName,
            selectedRobotCount = selectedRobotIds.size,
            samplesPerRobotText = samplesPerRobotText,
            randomSeedText = randomSeedText,
            reachablePercentText = reachablePercentText,
            maxIterationsText = maxIterationsText,
            toleranceText = toleranceText,
            dampingText = dampingText,
            maxStepText = maxStepText
        )
    val validationIssue = validationIssues.firstOrNull()
    val selectedRobots = robots.filter { it.id in selectedRobotIds }
    val plannedConfig =
        datasetGenerationConfigOrNull(
            datasetName = datasetName,
            selectedRobots = selectedRobots,
            samplesPerRobotText = samplesPerRobotText,
            randomSeedText = randomSeedText,
            reachablePercentText = reachablePercentText,
            targetMode = targetMode,
            filterMode = filterMode,
            appendToExisting = appendToExisting,
            maxIterationsText = maxIterationsText,
            toleranceText = toleranceText,
            dampingText = dampingText,
            maxStepText = maxStepText
        )
    val appendCompatibilityIssue =
        if (appendToExisting && existingManifest != null && plannedConfig != null) {
            runCatching {
                com.robotkinematicslab.mobile.dataset.DatasetScientificContract.requireCompatible(
                    existingManifest,
                    plannedConfig
                )
            }.exceptionOrNull()?.message
        } else {
            null
        }
    val validationByField = validationIssues.associateBy(DatasetCreationValidationIssue::fieldId)

    fun requestSection(section: DatasetCreationSection) {
        requestedSection = section
        expandRequest += 1
    }

    DatasetDisclosureSection(
        title = "1. Dataset identity",
        summary = "$datasetName · ${if (appendToExisting) "append safely" else "replace existing file"}",
        initiallyExpanded = true,
        expandRequest = if (requestedSection == DatasetCreationSection.IDENTITY) expandRequest else 0,
        testTag = "dataset-create-identity"
    ) {
        OutlinedTextField(
            value = datasetName,
            onValueChange = onDatasetNameChange,
            label = { Text("Dataset name") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.DatasetCreate),
            enabled = !isGenerating,
            singleLine = true
        )

        JargonAwareText(
            text =
                "The name identifies this dataset inside the project. Append adds a new generation only when the saved ordered robot definitions, generator protocol, target population, retention rules and IK configuration match its scientific fingerprint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        existingManifest?.scientificFingerprint?.let { fingerprint ->
            Text(
                text = "Saved scientific fingerprint: $fingerprint",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.DatasetAppend),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = appendToExisting,
                onCheckedChange = onAppendToExistingChange,
                enabled = !isGenerating
            )
            Column {
                Text("Append if this dataset already exists")
                Text(
                    text = if (appendToExisting) {
                        "New batches use a distinct deterministic seed, so rows are not repeated."
                    } else {
                        "A file with the same name will be replaced."
                    },
                    color =
                        if (appendToExisting) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                )
            }
        }
    }

    DatasetDisclosureSection(
        title = "2. Robot selection",
        summary = "${selectedRobotIds.size} of ${robots.size} robots selected",
        expandRequest = if (requestedSection == DatasetCreationSection.ROBOTS) expandRequest else 0,
        modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetCreateRobots),
        testTag = "dataset-create-robots"
    ) {
        Text("${selectedRobotIds.size} of ${robots.size} robots selected")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onSelectedRobotIdsChange(robots.map(SavedRobot::id).toSet()) },
                modifier = Modifier.weight(1f),
                enabled = !isGenerating && selectedRobotIds.size < robots.size
            ) {
                Text("Select all")
            }
            OutlinedButton(
                onClick = { onSelectedRobotIdsChange(emptySet()) },
                modifier = Modifier.weight(1f),
                enabled = !isGenerating && selectedRobotIds.isNotEmpty()
            ) {
                Text("Clear all")
            }
        }

        robots.forEach { savedRobot ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = savedRobot.id in selectedRobotIds,
                    onCheckedChange = { checked ->
                        onSelectedRobotIdsChange(
                            if (checked) {
                                selectedRobotIds + savedRobot.id
                            } else {
                                selectedRobotIds - savedRobot.id
                            }
                        )
                        tutorialReporter.report(
                            target = TutorialTargets.DatasetCreateRobots,
                            interaction = TutorialInteraction.CHOOSE,
                            detail = "Robot selection updated."
                        )
                    },
                    enabled = !isGenerating
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(savedRobot.robot.name)
                    Text(
                        "${savedRobot.robot.joints.size} joints · ${savedRobot.robot.joints.joinToString("") { it.type.name.take(1) }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onOpenRobotLibrary,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            Text("Add or edit robots")
        }
    }

    DatasetDisclosureSection(
        title = "3. Sampling and row retention",
        summary = "$samplesPerRobotText rows per robot · ${targetMode.name.lowercase(Locale.ROOT).replace('_', ' ')}",
        expandRequest = if (requestedSection == DatasetCreationSection.SAMPLING) expandRequest else 0,
        testTag = "dataset-create-sampling"
    ) {
        OutlinedTextField(
            value = samplesPerRobotText,
            onValueChange = onSamplesPerRobotChange,
            label = { Text("Rows per selected robot") },
            supportingText = {
                val plan = datasetRowPlan(samplesPerRobotText, selectedRobotIds.size)
                Text(
                    plan?.let {
                        "Requested saved rows: ${it.requestedSavedRows} · up to ${it.maximumSolverAttempts} solver attempts if the retention filter is restrictive."
                    } ?: "Enter a valid positive integer whose total fits one generation batch."
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.DatasetCreateRows),
            enabled = !isGenerating,
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(20_000, 100_000, 200_000, 300_000).forEach { amount ->
                OutlinedButton(
                    onClick = { onSamplesPerRobotChange(amount.toString()) },
                    modifier = Modifier.weight(1f),
                    enabled = !isGenerating
                ) {
                    Text(if (amount >= 1000) "${amount / 1000}k" else amount.toString())
                }
            }
        }

        OutlinedTextField(
            value = randomSeedText,
            onValueChange = onRandomSeedChange,
            label = { Text("Deterministic random seed") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.DatasetCreateSeed),
            enabled = !isGenerating,
            singleLine = true
        )

        JargonAwareText(
            text = "Protocol: ${ScientificRandomProtocol.ID}. The same seed, robot, batch and settings reproduce the same generated values.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Target population", modifier = Modifier.fillMaxWidth())
        EnumChoiceRow(
            options = DatasetTargetMode.entries,
            selected = targetMode,
            label = {
                when (it) {
                    DatasetTargetMode.MIXED -> "Mixed"
                    DatasetTargetMode.FK_PROVEN_REACHABLE -> "Reachable"
                    DatasetTargetMode.GUARANTEED_UNREACHABLE -> "Unreachable"
                }
            },
            onSelect = { selectedMode ->
                if (selectedMode != targetMode) {
                    onTargetModeChange(selectedMode)
                    tutorialReporter.report(
                        target = TutorialTargets.DatasetCreatePopulation,
                        interaction = TutorialInteraction.CHOOSE,
                        detail = "Target population selected: ${selectedMode.name}."
                    )
                }
            },
            enabled = !isGenerating,
            modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetCreatePopulation)
        )

        if (targetMode == DatasetTargetMode.MIXED) {
            OutlinedTextField(
                value = reachablePercentText,
                onValueChange = onReachablePercentChange,
                label = { Text("FK-proven reachable targets (%)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGenerating,
                singleLine = true
            )
            val reachableFraction = ScientificNumberParser.parseDouble(reachablePercentText)?.div(100.0)
            val samples = ScientificNumberParser.parseInt(samplesPerRobotText)
            if (reachableFraction != null && reachableFraction in 0.0..1.0 && samples != null && samples > 0) {
                val reachableCount =
                    plannedReachableTargetCount(DatasetTargetMode.MIXED, reachableFraction, samples)
                Text(
                    "Per selected robot, the generator rounds to $reachableCount FK-proven reachable and ${samples - reachableCount} conservative-outside targets, then deterministically shuffles that exact class plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("Rows to retain")
        EnumChoiceRow(
            options = DatasetFilterMode.entries,
            selected = filterMode,
            label = {
                when (it) {
                    DatasetFilterMode.ALL -> "All"
                    DatasetFilterMode.ACCEPTED_ONLY -> "Accepted"
                    DatasetFilterMode.REJECTED_ONLY -> "Rejected"
                }
            },
            onSelect = onFilterModeChange,
            enabled = !isGenerating,
            modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetCreateRetention)
        )

        JargonAwareText(
            "Population controls what is attempted: Reachable targets come from forward kinematics of valid joint states; Unreachable targets are placed outside a conservative radial upper bound. Retention is evaluated later: All stores any solver status, Accepted keeps SUCCESS and SUCCESS_WITH_WARNING, and Rejected keeps every other status. A restrictive filter retries candidates up to the attempt cap, so requested rows can exceed rows retained and saved.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    DatasetDisclosureSection(
        title = "4. IK solver configuration",
        summary = "$maxIterationsText iterations · tolerance $toleranceText",
        expandRequest = if (requestedSection == DatasetCreationSection.IK) expandRequest else 0,
        modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetCreateIk),
        testTag = "dataset-create-ik"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallNumberField(
                label = "Max iterations",
                value = maxIterationsText,
                onValueChange = onMaxIterationsChange,
                enabled = !isGenerating,
                modifier = Modifier.weight(1f),
                error = validationByField["ik-max-iterations"]?.message
            )
            SmallNumberField(
                label = "Tolerance (m)",
                value = toleranceText,
                onValueChange = onToleranceChange,
                enabled = !isGenerating,
                modifier = Modifier.weight(1f),
                error = validationByField["ik-tolerance"]?.message
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallNumberField(
                label = "Damping",
                value = dampingText,
                onValueChange = onDampingChange,
                enabled = !isGenerating,
                modifier = Modifier.weight(1f),
                error = validationByField["ik-damping"]?.message
            )
            SmallNumberField(
                label = "Max normalized step",
                value = maxStepText,
                onValueChange = onMaxStepChange,
                enabled = !isGenerating,
                modifier = Modifier.weight(1f),
                error = validationByField["ik-max-step"]?.message
            )
        }
        JargonAwareText(
            "Max iterations (1–10,000) bounds search work. Tolerance (1e-9–1 m) is the independently recomputed Cartesian residual required for success. Damping (1e-9–10) regularizes the normalized DLS system and increases near singularities. Max normalized step (1e-6–10) caps each mixed revolute/prismatic update before backtracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    DatasetDisclosureSection(
        title = "Optional expert preset",
        summary = "Verified 1 µm neural IK configuration",
        testTag = "dataset-create-expert-preset"
    ) {
        JargonAwareText(
            "Loads a thesis-ready contract: all saved robots, FK-proven reachable targets, accepted solutions only, deterministic seed 2604 and a strict 1 µm solver tolerance.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onLoadOneMicronPreset,
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load verified 1 µm IK preset")
        }
    }

    DatasetDisclosureSection(
        title = "5. Planned AI comparisons",
        summary = "${plannedFeatureSelections.size} feature contract(s) selected",
        testTag = "dataset-create-ai-comparisons"
    ) {
        JargonAwareText(
            "The CSV always retains the complete scientific record. Choose which feature contracts this dataset is intended to compare; training can change them later without regenerating rows.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FeatureExperimentSelector(
                originLabel = "Dataset Factory / Planned AI comparisons",
                sourceCsvPath = existingManifest?.csvPath,
            selections = plannedFeatureSelections,
            enabled = !isGenerating,
            onSelectionsChange = { selections ->
                if (selections != plannedFeatureSelections) {
                    onPlannedFeatureSelectionsChange(selections)
                    tutorialReporter.report(
                        target = TutorialTargets.DatasetCreateFeatures,
                        interaction = TutorialInteraction.CHOOSE,
                        detail = "Feature comparison plan updated."
                    )
                }
            },
            modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetCreateFeatures)
        )
    }

    DatasetCard(title = "6. Generate and save") {
        val rowPlan = datasetRowPlan(samplesPerRobotText, selectedRobotIds.size)
        JargonAwareText(
            text =
                buildString {
                    append("Plan: ${selectedRobotIds.size} robot(s) × $samplesPerRobotText requested saved rows")
                    rowPlan?.let { append(" = ${it.requestedSavedRows} rows") }
                    append(" · ${targetMode.name.lowercase(Locale.ROOT).replace('_', ' ')} targets")
                    append(" · retain ${filterMode.name.lowercase(Locale.ROOT).replace('_', ' ')}")
                    append(" · ${if (appendToExisting) "append" else "replace"} mode.")
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${progress.addedRows}/${progress.requestedRows} rows · ${progress.attempts} attempts · ${progress.currentRobotName}"
            )
        }

        Text(
            text = statusMessage,
            color =
                if (statusMessage.contains("failed", ignoreCase = true)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
        )

        Text(
            "Saved in app Documents: $outputDirectory",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isGenerating && validationIssue != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Configuration needs attention",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    validationIssues.forEach { issue ->
                        Text(
                            "${issue.section.userFacingLabel()}: ${issue.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    OutlinedButton(
                        onClick = { requestSection(validationIssue.section) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review ${validationIssue.section.userFacingLabel()}")
                    }
                }
            }
        }

        if (!isGenerating && appendCompatibilityIssue != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("dataset-append-incompatible"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Existing dataset is incompatible", color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(appendCompatibilityIssue, color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(
                        onClick = { requestSection(DatasetCreationSection.IDENTITY) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Review dataset identity")
                    }
                }
            }
        }

        if (isGenerating) {
            OutlinedButton(
                onClick = onCancel,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tutorialAnchor(TutorialTargets.DatasetCancel)
            ) {
                Text("Cancel safely")
            }
        } else if (continuousGenerationActive) {
            OutlinedButton(
                onClick = onOpenContinuous,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continuous growth is running · open controls")
            }
        } else {
            Button(
                onClick = {
                    if (!appendToExisting && existingManifest != null) {
                        replaceConfirmationVisible = true
                    } else {
                        onGenerate()
                    }
                },
                enabled = validationIssue == null && appendCompatibilityIssue == null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .tutorialAnchor(TutorialTargets.DatasetGenerateReplace)
            ) {
                Text(if (appendToExisting) "Generate / append dataset" else "Generate and replace dataset")
            }
        }
    }

    if (replaceConfirmationVisible && existingManifest != null) {
        AlertDialog(
            onDismissRequest = { replaceConfirmationVisible = false },
            title = { Text("Replace saved dataset?") },
            text = {
                Text(
                    "${existingManifest.datasetName} currently contains ${existingManifest.rowCount} rows. " +
                        "Replacement publishes a new CSV atomically, but the current dataset will no longer be the active version."
                )
            },
            dismissButton = {
                TextButton(onClick = { replaceConfirmationVisible = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        replaceConfirmationVisible = false
                        onGenerate()
                    }
                ) { Text("Replace dataset") }
            }
        )
    }
}

private fun DatasetCreationSection.userFacingLabel(): String =
    when (this) {
        DatasetCreationSection.IDENTITY -> "dataset identity"
        DatasetCreationSection.ROBOTS -> "robot selection"
        DatasetCreationSection.SAMPLING -> "sampling settings"
        DatasetCreationSection.IK -> "IK solver settings"
    }

@Composable
private fun RobotLibrarySection(
    robots: List<SavedRobot>,
    libraryEditingEnabled: Boolean,
    selectedRobotIds: Set<String>,
    showEditor: Boolean,
    editorState: RobotEditorState,
    editorErrors: List<String>,
    editingRobotId: String?,
    onToggleSelected: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onAddRobot: (String) -> Unit,
    onEditRobot: (SavedRobot) -> Unit,
    onDeleteRobot: (SavedRobot) -> Unit,
    onResetExamples: () -> Unit,
    onEditorStateChange: (RobotEditorState) -> Unit,
    onCloseEditor: () -> Unit,
    onLoadTemplate: () -> Unit,
    workspaceSummaries: List<RobotWorkspaceStudySummary>,
    workspaceProvisioningRobotIds: Set<String>,
    workspaceProvisioningErrors: Map<String, String>,
    onOpenRobotWorkspace: (String) -> Unit,
    onRetryRobotWorkspace: (SavedRobot) -> Unit,
    onSaveSelectionAndReturn: () -> Unit,
    onSaveRobot: () -> Unit
) {
    var pendingDeleteRobotId by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var robotSearchQuery by remember { mutableStateOf("") }
    var nameRobotDialogOpen by remember { mutableStateOf(false) }
    var proposedRobotName by remember { mutableStateOf("") }

    val normalizedQuery = robotSearchQuery.trim().lowercase(Locale.ROOT)
    val visibleRobots =
        remember(robots, normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                robots
            } else {
                robots.filter { savedRobot ->
                    val topology =
                        savedRobot.robot.joints.joinToString("-") { joint ->
                            joint.type.name.take(1)
                        }
                    savedRobot.robot.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                            topology.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                            savedRobot.robot.joints.any { joint ->
                                joint.type.name.lowercase(Locale.ROOT).contains(normalizedQuery)
                            }
                }
            }
        }

    Column(
        modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetRobots),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    DatasetCard(title = "Manage dataset robots") {
        Text(
            "Edit shared robot definitions and choose which ones belong to the current Dataset Builder draft. The dataset name, rows, seed, solver and retention settings remain unchanged while you are here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onSaveSelectionAndReturn,
            modifier = Modifier.fillMaxWidth().testTag("dataset-robots-save-and-return")
        ) {
            Text("Save selection and return to Dataset creation")
        }
    }

    DatasetDisclosureSection(
        title = "Industrial robot reference gallery",
        summary = "${IndustrialRobotReferenceCatalog.entries.size} reference models and topology notes",
        testTag = "dataset-robots-reference-gallery"
    ) {
        IndustrialRobotReferenceGallery(
            modifier =
                Modifier.tutorialAnchor(
                    targetId = TutorialTargets.RobotLibraryGallery,
                    actionEnabled = false
                )
        )
    }

    if (!libraryEditingEnabled) {
        DatasetCard(title = "Robot definitions frozen") {
            Text(
                "Continuous growth is using a frozen copy of its selected robots. Pause it before editing, deleting or resetting the saved DH library. You may still inspect technical cards and prepare the next selection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    RobotStoreHero(
        availableCount = robots.size,
        selectedCount = selectedRobotIds.size,
        onAddRobot = {
            proposedRobotName = nextCustomRobotName(robots.map { it.robot.name })
            nameRobotDialogOpen = true
        },
        onSelectAll = onSelectAll,
        onSelectNone = onSelectNone,
        editingEnabled = libraryEditingEnabled
    )

    if (nameRobotDialogOpen) {
        NameRobotDialog(
            value = proposedRobotName,
            existingNames = robots.map { it.robot.name },
            onValueChange = { proposedRobotName = it.take(MAX_ROBOT_NAME_LENGTH) },
            onDismiss = { nameRobotDialogOpen = false },
            onConfirm = { name ->
                nameRobotDialogOpen = false
                onAddRobot(name)
            }
        )
    }

    if (showEditor && libraryEditingEnabled) {
        DatasetCard(title = if (editingRobotId == null) "Create robot" else "Edit robot") {
            RobotEditorPanel(
                editorState = editorState,
                validationErrors = editorErrors,
                isApplyEnabled = editorErrors.isEmpty(),
                onRobotNameChange = { onEditorStateChange(editorState.copy(robotName = it)) },
                onDhRowChange = { index, row ->
                    onEditorStateChange(
                        editorState.copy(
                            dhRows = editorState.dhRows.mapIndexed { rowIndex, existing ->
                                if (rowIndex == index) row else existing
                            }
                        )
                    )
                },
                onAddRow = {
                    onEditorStateChange(editorState.copy(dhRows = editorState.dhRows + DhInputRow()))
                },
                onRemoveRow = { index ->
                    onEditorStateChange(
                        editorState.copy(
                            dhRows = editorState.dhRows.filterIndexed { rowIndex, _ -> rowIndex != index }
                        )
                    )
                },
                onApplyRobot = onSaveRobot,
                onLoadPresetRobot = onLoadTemplate,
                applyButtonLabel = "Save to library",
                presetButtonLabel = "Load 3-link template"
            )

            TextButton(onClick = onCloseEditor) {
                Text("Close editor")
            }
        }
    }

    DatasetReadyModelsHeader(
        visibleCount = visibleRobots.size,
        totalCount = robots.size,
        query = robotSearchQuery,
        onQueryChange = { robotSearchQuery = it },
        modifier = Modifier.tutorialAnchor(TutorialTargets.RobotLibrarySearch)
    )

    if (visibleRobots.isEmpty()) {
        DatasetCard(title = "No matching DH models") {
            Text(
                "Try a robot name, link count, joint type or R/P topology.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { robotSearchQuery = "" }) {
                Text("Clear search")
            }
        }
    }

    if (visibleRobots.isNotEmpty()) {
        DatasetDisclosureSection(
            title = "Saved robot models",
            summary = "${visibleRobots.size} matching model(s) · ${selectedRobotIds.size} selected",
            testTag = "dataset-robots-saved-models"
        ) {
            visibleRobots.forEachIndexed { index, savedRobot ->
                val fingerprint = robotWorkspaceFingerprint(savedRobot.robot)
                val workspaceStudyCount =
                    workspaceSummaries.count { summary -> summary.robotFingerprint == fingerprint }
                DatasetReadyRobotCard(
                    savedRobot = savedRobot,
                    isSelected = savedRobot.id in selectedRobotIds,
                    deleteArmed = pendingDeleteRobotId == savedRobot.id,
                    workspaceStudyCount = workspaceStudyCount,
                    workspacePreparing = savedRobot.id in workspaceProvisioningRobotIds,
                    workspaceError = workspaceProvisioningErrors[savedRobot.id],
                    onToggleSelected = { onToggleSelected(savedRobot.id) },
                    onEdit = { onEditRobot(savedRobot) },
                    onOpenWorkspace = { onOpenRobotWorkspace(savedRobot.id) },
                    onRetryWorkspace = { onRetryRobotWorkspace(savedRobot) },
                    onDelete = {
                        if (pendingDeleteRobotId == savedRobot.id) {
                            onDeleteRobot(savedRobot)
                            pendingDeleteRobotId = null
                        } else {
                            pendingDeleteRobotId = savedRobot.id
                        }
                    },
                    editingEnabled = libraryEditingEnabled,
                    modifier =
                        if (index == 0) {
                            Modifier.tutorialAnchor(TutorialTargets.RobotLibraryModel)
                        } else {
                            Modifier
                        }
                )
            }
        }
    }

    DatasetDisclosureSection(
        title = "Library maintenance",
        summary = "Restore the validated example library",
        testTag = "dataset-robots-maintenance"
    ) {
        HorizontalDivider()
        OutlinedButton(
            onClick = {
                if (confirmReset) {
                    onResetExamples()
                    confirmReset = false
                } else {
                    confirmReset = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = libraryEditingEnabled
        ) {
            Text(
                if (confirmReset) {
                    "Confirm: replace library with examples"
                } else {
                    "Restore the 10 example robots"
                }
            )
        }
    }

    Button(
        onClick = onSaveSelectionAndReturn,
        modifier = Modifier.fillMaxWidth().testTag("dataset-robots-save-and-return-bottom")
    ) {
        Text("Save selection and return to Dataset creation")
    }
    }
}

internal fun retainedDatasetRobotSelection(
    selectedRobotIds: Set<String>,
    availableRobotIds: Set<String>
): Set<String> = selectedRobotIds.intersect(availableRobotIds)

@Composable
private fun NameRobotDialog(
    value: String,
    existingNames: List<String>,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val normalized = normalizeRobotName(value)
    val duplicate = existingNames.any { it.equals(normalized, ignoreCase = true) }
    val valid = normalized.isNotBlank() && normalized.length <= MAX_ROBOT_NAME_LENGTH && !duplicate

    AlertDialog(
        modifier = Modifier.testTag("robot-name-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Name your robot") },
        text = {
            Column(
                modifier =
                    Modifier
                        .testTag("robot-name-dialog-content")
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "This name identifies the DH definition, generated rows and saved evidence inside the current project.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().testTag("new-robot-name-field"),
                    label = { Text("Robot name") },
                    supportingText = {
                        Text(
                            when {
                                normalized.isBlank() -> "A name is required."
                                duplicate -> "A robot with this name already exists in this project."
                                else -> "${value.length}/$MAX_ROBOT_NAME_LENGTH"
                            }
                        )
                    },
                    isError = normalized.isBlank() || duplicate,
                    singleLine = true
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                onClick = { onConfirm(normalized) },
                enabled = valid,
                modifier = Modifier.testTag("confirm-robot-name")
            ) {
                Text("Continue to DH editor")
            }
        }
    )
}

internal fun normalizeRobotName(value: String): String =
    value.trim().replace(Regex("\\s+"), " ")

internal fun nextCustomRobotName(existingNames: List<String>): String {
    val normalizedExisting = existingNames.map { normalizeRobotName(it).lowercase(Locale.ROOT) }.toSet()
    var index = 1
    while ("custom robot $index" in normalizedExisting) index += 1
    return "Custom Robot $index"
}

private const val MAX_ROBOT_NAME_LENGTH = 80

@Composable
private fun SavedDatasetsSection(
    manifests: List<DatasetManifest>,
    robots: List<SavedRobot>,
    onAddMore: (DatasetManifest) -> Unit
) {
    Column(
        modifier = Modifier.tutorialAnchor(TutorialTargets.DatasetSaved),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (manifests.isEmpty()) {
            DatasetCard(title = "No saved datasets yet") {
                JargonAwareText("Generate the first dataset in the Create section. Its manifest and CSV will appear here.")
            }
        } else {
            manifests.forEach { manifest ->
                DatasetCard(title = manifest.datasetName) {
                    val availableRobots = manifest.robotIds.count { id -> robots.any { it.id == id } }
                    Text("${manifest.rowCount} rows · ${manifest.generationCount} generation batch(es)")
                    Text("$availableRobots/${manifest.robotIds.size} referenced robots currently available")
                    Text("Last mode: ${manifest.targetMode} · filter: ${manifest.filterMode}")
                    Text("Random protocol: ${manifest.randomProtocol}")
                    Text(
                        "Updated: ${DateFormat.getDateTimeInstance().format(Date(manifest.lastUpdatedEpochMillis))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        manifest.csvPath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onAddMore(manifest) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = availableRobots > 0
                    ) {
                        Text("Configure and append more rows")
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasetCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.95f), MaterialTheme.shapes.large)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        JargonAwareText(title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
        content()
    }
}

@Composable
private fun <T> EnumChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    CompactSelectionMenu(
        options = options,
        selected = selected,
        label = label,
        onSelected = onSelect,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled
    )
}

@Composable
private fun SmallNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { message -> ({ Text(message) }) },
        singleLine = true
    )
}
