package com.robotkinematicslab.mobile.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.performance.compute.ComputeRuntimeGuard
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.ui.accessibility.LocalAppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.spatial.RobotWorkspaceDisplayOptions
import com.robotkinematicslab.mobile.ui.charts.spatial.RobotWorkspaceEnvelopeConstruction
import com.robotkinematicslab.mobile.ui.charts.spatial.RobotWorkspaceRenderMode
import com.robotkinematicslab.mobile.ui.charts.spatial.RobotWorkspaceScene3D
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceCamera
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceEnvelopeConstructionStage
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceEnvelopeConstructionStrategy
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceRobotPose
import com.robotkinematicslab.mobile.ui.shared.WeightedSelectionTile
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureHeader
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.charts.spatial.buildRobotWorkspaceEnvelopeConstruction
import com.robotkinematicslab.mobile.ui.charts.spatial.buildRobotJointLimitGuides
import com.robotkinematicslab.mobile.ui.charts.spatial.constructionJointValues
import com.robotkinematicslab.mobile.ui.charts.spatial.workspaceEnvelopeConstructionStage
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisCancelledException
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisProgress
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceAnalysisPhase
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass
import com.robotkinematicslab.mobile.workspace.analysis.robotWorkspaceFingerprint
import com.robotkinematicslab.mobile.workspace.analysis.workspaceReplicationCoverage
import com.robotkinematicslab.mobile.workspace.analysis.workspaceReplicationRelativeRange
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceStudyRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.PI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RobotWorkspacePanel(
    modifier: Modifier = Modifier,
    initialLibraryRobotId: String? = null,
    onReturnToRobot: (() -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val tutorialReporter = LocalTutorialActionReporter.current
    val accessibilityPreferences = LocalAppAccessibilityPreferences.current
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val trackedProcesses by processCoordinator.processes.collectAsState()
    val workspaceAnalysisActive =
        trackedProcesses.any {
            it.id == ResearchProcessIds.WORKSPACE_ANALYSIS && it.status.isActive
        }
    val analyzer = remember { RobotWorkspaceAnalyzer() }
    val studyRepository = remember(context) { RobotWorkspaceStudyRepository(context) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val computeGuard = remember(context) { ComputeRuntimeGuard(context) }
    val robotRepository = remember(context) { RobotLibraryRepository(context) }
    val robotLibraryRevision by robotRepository.observeChanges().collectAsState()
    var libraryRobots by remember(robotRepository) {
        mutableStateOf(robotRepository.loadOrCreateDefaults())
    }
    val currentRobot = remember(context) { AppStorageRepository(context).loadRobotLabState()?.robot }
    val robotChoices =
        remember(currentRobot, libraryRobots) {
            buildRobotChoices(
                currentRobot = currentRobot,
                libraryRobots = libraryRobots
            )
        }

    val initialRobotChoiceId = initialLibraryRobotId?.let { id -> "library-$id" }
    var selectedRobotId by rememberSaveable(initialLibraryRobotId) {
        mutableStateOf(initialRobotChoiceId ?: robotChoices.firstOrNull()?.id)
    }
    val selectedRobot = robotChoices.firstOrNull { it.id == selectedRobotId }
    var sampleCountText by rememberSaveable { mutableStateOf("8192") }
    var voxelResolutionText by rememberSaveable { mutableStateOf("20") }
    var replicationCountText by rememberSaveable { mutableStateOf("4") }
    var seedText by rememberSaveable { mutableStateOf("42") }
    var study by remember { mutableStateOf<RobotWorkspaceStudy?>(null) }
    var summaries by remember { mutableStateOf(studyRepository.listStudies()) }
    var selectedVoxel by remember { mutableStateOf<RobotWorkspaceVoxel?>(null) }
    var workspaceProgress by remember { mutableStateOf<RobotWorkspaceAnalysisProgress?>(null) }
    var progressState by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    var showCompletedTelemetry by remember { mutableStateOf(false) }
    val performanceSamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cancelSignal by remember { mutableStateOf<AtomicBoolean?>(null) }
    var quickAccessStudyLoaded by rememberSaveable(initialLibraryRobotId) {
        mutableStateOf(false)
    }
    var studySettingsExpanded by rememberSaveable { mutableStateOf(false) }
    var samplingSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val previewConfig = runCatching { workspaceConfiguration(sampleCountText, voxelResolutionText, replicationCountText, seedText) }.getOrNull()
    val devicePolicy = computeGuard.currentPolicy()
    var displayModeExpanded by rememberSaveable { mutableStateOf(false) }
    var visibleLayersExpanded by rememberSaveable { mutableStateOf(false) }
    var convergenceExpanded by rememberSaveable { mutableStateOf(false) }

    var camera by remember { mutableStateOf(WorkspaceCamera()) }
    var displayOptions by remember { mutableStateOf(RobotWorkspaceDisplayOptions()) }
    var renderMode by remember { mutableStateOf(RobotWorkspaceRenderMode.POINT_CLOUD) }
    var envelopeConstruction by remember { mutableStateOf<RobotWorkspaceEnvelopeConstruction?>(null) }
    var envelopeConstructionJob by remember { mutableStateOf<Job?>(null) }
    var constructionPreparing by remember { mutableStateOf(false) }
    var constructionProgress by remember { mutableDoubleStateOf(0.0) }
    var playbackPosition by remember { mutableDoubleStateOf(0.0) }
    var playbackRate by remember { mutableDoubleStateOf(60.0) }
    var playing by remember { mutableStateOf(false) }
    var robotPose by remember { mutableStateOf<WorkspaceRobotPose?>(null) }
    val trace = remember { mutableStateListOf<com.robotkinematicslab.mobile.math.utility.Vec3>() }
    // Canvas drawing can outlive the composition frame that supplied this state list.
    // Give the renderer an immutable frame snapshot so animation updates cannot mutate
    // the collection while the draw pass is iterating over it.
    val traceSnapshot = trace.toList()

    fun clearWorkspaceVisualization() {
        envelopeConstructionJob?.cancel()
        envelopeConstructionJob = null
        study = null
        selectedVoxel = null
        camera = WorkspaceCamera()
        displayOptions = RobotWorkspaceDisplayOptions()
        renderMode = RobotWorkspaceRenderMode.POINT_CLOUD
        envelopeConstruction = null
        constructionPreparing = false
        constructionProgress = 0.0
        playbackPosition = 0.0
        playing = false
        robotPose = null
        trace.clear()
        progressState = null
        showCompletedTelemetry = false
    }

    LaunchedEffect(robotRepository, robotLibraryRevision) {
        libraryRobots = withContext(Dispatchers.IO) { robotRepository.loadOrCreateDefaults() }
    }

    LaunchedEffect(robotChoices) {
        val resolved = robotChoices.firstOrNull { it.id == selectedRobotId } ?: robotChoices.firstOrNull()
        if (resolved?.id != selectedRobotId) selectedRobotId = resolved?.id
        val displayedStudy = study
        if (displayedStudy != null && resolved != null && robotWorkspaceFingerprint(displayedStudy.robot) != resolved.fingerprint) {
            clearWorkspaceVisualization()
        }
    }

    val activeStudy = study
    val revealedSamples =
        if (activeStudy == null) {
            0
        } else if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
            activeStudy.samples.size
        } else {
            (floor(playbackPosition).toInt() + 1).coerceIn(0, activeStudy.samples.size)
        }

    LaunchedEffect(activeStudy, playing, playbackRate, renderMode, envelopeConstruction) {
        val available = activeStudy?.samples?.size ?: return@LaunchedEffect
        if (available <= 0) {
            playing = false
            return@LaunchedEffect
        }
        var lastFrameNanos = System.nanoTime()
        while (isActive && playing) {
            delay(WORKSPACE_PLAYBACK_FRAME_INTERVAL_MILLIS)
            val now = System.nanoTime()
            val elapsedSeconds = (now - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0
            lastFrameNanos = now
            if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                if (envelopeConstruction == null) {
                    playing = false
                } else {
                    constructionProgress =
                        (constructionProgress +
                            elapsedSeconds * (playbackRate / NORMAL_PLAYBACK_RATE) / CONSTRUCTION_DURATION_SECONDS)
                            .coerceAtMost(1.0)
                    if (constructionProgress >= 1.0) playing = false
                }
            } else {
                playbackPosition =
                    (playbackPosition + playbackRate * elapsedSeconds)
                        .coerceAtMost((available - 1).toDouble())
                if (playbackPosition >= available - 1) playing = false
            }
        }
    }

    LaunchedEffect(activeStudy, renderMode, envelopeConstruction) {
        val currentStudy = activeStudy ?: return@LaunchedEffect
        if (currentStudy.samples.isEmpty()) {
            robotPose = null
            return@LaunchedEffect
        }
        snapshotFlow {
            if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                constructionProgress
            } else {
                playbackPosition
            }
        }
            .conflate()
            .collect { framePosition ->
                val nextPose = withContext(Dispatchers.Default) {
                    val interpolated =
                        if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                            envelopeConstruction?.let { construction ->
                                constructionJointValues(construction, currentStudy.robot, framePosition)
                            } ?: return@withContext null
                        } else {
                            val leftIndex =
                                floor(framePosition).toInt().coerceIn(0, currentStudy.samples.lastIndex)
                            val rightIndex = (leftIndex + 1).coerceAtMost(currentStudy.samples.lastIndex)
                            val fraction = (framePosition - leftIndex).coerceIn(0.0, 1.0)
                            val left = currentStudy.samples[leftIndex].jointValues
                            val right = currentStudy.samples[rightIndex].jointValues
                            left.indices.map { index ->
                                left[index] + (right[index] - left[index]) * fraction
                            }
                        }
                val result = ForwardKinematicsSolver().solve(currentStudy.robot, RobotState(interpolated))
                if (result.status == FKStatus.SUCCESS || result.status == FKStatus.SUCCESS_WITH_WARNING) {
                    WorkspaceRobotPose(
                        jointPositions = result.jointPositions,
                        jointTypes = currentStudy.robot.joints.map { it.type },
                        endEffector = result.endEffectorPosition,
                        jointLimitGuides =
                            buildRobotJointLimitGuides(
                                robot = currentStudy.robot,
                                jointValues = interpolated,
                                visualRadiusMeters =
                                    max(
                                        currentStudy.voxelCellSizeMeters * 1.8,
                                        currentStudy.conservativeRadiusMeters * 0.12
                                    )
                            )
                    )
                } else {
                    null
                }
            }
                robotPose = nextPose
                if (renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                    nextPose?.endEffector?.let { endEffector ->
                        if (
                            trace.isNotEmpty() &&
                            (endEffector - trace.last()).norm() >
                                currentStudy.voxelCellSizeMeters * MAXIMUM_TRACE_STEP_CELLS
                        ) {
                            trace.clear()
                        }
                        trace += endEffector
                        if (trace.size > MAXIMUM_TRACE_POINTS) trace.removeAt(0)
                    }
                }
            }
    }

    fun applyPreset(preset: WorkspaceQualityPreset) {
        sampleCountText = preset.samples.toString()
        voxelResolutionText = preset.resolution.toString()
        replicationCountText = preset.replications.toString()
    }

    fun showStudy(value: RobotWorkspaceStudy) {
        envelopeConstructionJob?.cancel()
        envelopeConstructionJob = null
        robotChoices.firstOrNull { it.fingerprint == robotWorkspaceFingerprint(value.robot) }?.let { matching ->
            selectedRobotId = matching.id
        }
        study = value
        selectedVoxel = null
        camera = WorkspaceCamera()
        displayOptions = RobotWorkspaceDisplayOptions()
        renderMode = RobotWorkspaceRenderMode.POINT_CLOUD
        envelopeConstruction = null
        constructionPreparing = false
        constructionProgress = 0.0
        playbackPosition = 0.0
        trace.clear()
        playing = !accessibilityPreferences.reduceMotion
    }

    LaunchedEffect(initialLibraryRobotId, summaries, robotChoices) {
        val robotId = initialLibraryRobotId ?: return@LaunchedEffect
        if (quickAccessStudyLoaded) return@LaunchedEffect

        val choice = robotChoices.firstOrNull { it.id == "library-$robotId" }
        if (choice == null) {
            errorMessage = "The robot that opened this workspace is no longer in the saved library."
            quickAccessStudyLoaded = true
            return@LaunchedEffect
        }
        selectedRobotId = choice.id

        val latestOwnedStudy =
            summaries.firstOrNull { summary -> summary.robotFingerprint == choice.fingerprint }
        if (latestOwnedStudy == null) {
            errorMessage = "This robot does not have a saved workspace study yet."
            quickAccessStudyLoaded = true
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) { studyRepository.load(latestOwnedStudy) }
        }.onSuccess { ownedStudy ->
            showStudy(ownedStudy)
        }.onFailure { error ->
            errorMessage =
                "The robot's saved workspace could not be loaded: " +
                    (error.message ?: error::class.simpleName)
        }
        quickAccessStudyLoaded = true
    }

    fun startEnvelopeConstruction() {
        val currentStudy = study ?: return
        renderMode = RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION
        displayOptions =
            displayOptions.copy(
                showUnobservedCandidates = true,
                showDeadSpaceXRay = true
            )
        camera = WorkspaceCamera(yaw = -0.72f, pitch = 0.32f, zoom = 1f)
        constructionProgress = 0.0
        trace.clear()
        playing = false
        envelopeConstruction?.let {
            playing = true
            return
        }
        if (envelopeConstructionJob?.isActive == true) {
            return
        }
        constructionPreparing = true
        envelopeConstructionJob =
            scope.launch {
                val ownJob = currentCoroutineContext()[Job]
                try {
                    val built =
                        withContext(Dispatchers.Default) {
                            buildRobotWorkspaceEnvelopeConstruction(currentStudy)
                        }
                    if (study?.studyId == currentStudy.studyId) {
                        envelopeConstruction = built
                        playing = renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (study?.studyId == currentStudy.studyId) {
                        errorMessage =
                            "Envelope construction failed: ${error.message ?: error::class.simpleName}"
                    }
                } finally {
                    if (envelopeConstructionJob == ownJob) {
                        constructionPreparing = false
                        envelopeConstructionJob = null
                    }
                }
            }
    }

    fun startAnalysis() {
        if (processCoordinator.isActive(ResearchProcessIds.WORKSPACE_ANALYSIS)) {
            errorMessage = "A workspace analysis is already running. Open the process centre to inspect it."
            return
        }
        val choice = selectedRobot
        if (choice == null) {
            errorMessage = "No valid robot is available. Save or create a robot first."
            return
        }
        val config =
            runCatching {
                workspaceConfiguration(sampleCountText, voxelResolutionText, replicationCountText, seedText)
            }.getOrElse {
                errorMessage =
                    "Use 256–50,000 samples, resolution 10–32, 1–8 replications and a whole-number seed."
                return
            }

        val requestedWorkers = computeGuard.currentWorkerLimit()
        val startedAt = System.currentTimeMillis()
        val rawProgress =
            AtomicReference(
                RobotWorkspaceAnalysisProgress(
                    WorkspaceAnalysisPhase.VALIDATING_ROBOT,
                    0,
                    config.sampleCount + 6,
                    "Preparing the robot workspace study."
                )
            )
        val cancelled = AtomicBoolean(false)
        cancelSignal = cancelled
        errorMessage = null
        envelopeConstructionJob?.cancel()
        envelopeConstructionJob = null
        constructionPreparing = false
        study = null
        selectedVoxel = null
        playing = false
        trace.clear()
        performanceSamples.clear()
        showCompletedTelemetry = true
        workspaceProgress = rawProgress.get()
        val telemetrySampleIndex = AtomicInteger(0)
        val initialProgress = workspaceProgressState(rawProgress.get(), startedAt, telemetrySampler)
        progressState = initialProgress
        performanceSamples +=
            DiagnosticPerformanceSample.fromProgressState(
                telemetrySampleIndex.incrementAndGet(),
                initialProgress
            )

        processCoordinator.launch(
            id = ResearchProcessIds.WORKSPACE_ANALYSIS,
            title = "3D workspace analysis",
            kind = ResearchProcessKind.VISUALIZATION,
            cancellationAction = { cancelled.set(true) }
        ) { reporter ->
                val telemetryTicker =
                    CoroutineScope(currentCoroutineContext()).launch {
                        while (isActive) {
                            delay(HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS)
                            val raw = rawProgress.get()
                            val current = workspaceProgressState(raw, startedAt, telemetrySampler)
                            workspaceProgress = raw
                            progressState = current
                            performanceSamples +=
                                DiagnosticPerformanceSample.fromProgressState(
                                    telemetrySampleIndex.incrementAndGet(),
                                    current
                                )
                            reporter.report(
                                progressFraction = raw.fraction,
                                stage = raw.phase.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                                detail = raw.message
                            )
                        }
                    }
                try {
                    val result =
                        withContext(Dispatchers.Default) {
                            analyzer.analyze(
                                robot = choice.robot,
                                config = config,
                                requestedWorkerCount = requestedWorkers,
                                cancellationRequested = cancelled::get,
                                onProgress = rawProgress::set
                            )
                        }
                    withContext(Dispatchers.IO) { studyRepository.save(result) }
                    rawProgress.set(
                        RobotWorkspaceAnalysisProgress(
                            WorkspaceAnalysisPhase.COMPLETED,
                            config.sampleCount + 6,
                            config.sampleCount + 6,
                            "Workspace study completed and saved."
                        )
                    )
                    telemetryTicker.cancel()
                    workspaceProgress = rawProgress.get()
                    val completedProgress =
                        workspaceProgressState(rawProgress.get(), startedAt, telemetrySampler)
                    progressState = completedProgress
                    performanceSamples +=
                        DiagnosticPerformanceSample.fromProgressState(
                            telemetrySampleIndex.incrementAndGet(),
                            completedProgress
                        )
                    summaries = withContext(Dispatchers.IO) { studyRepository.listStudies() }
                    showStudy(result)
                    showCompletedTelemetry = false
                    reporter.completed("Workspace study completed, verified and saved.")
                } catch (_: RobotWorkspaceAnalysisCancelledException) {
                    telemetryTicker.cancel()
                    errorMessage = "Workspace study cancelled safely; no partial study was saved."
                    val cancelledProgress = workspaceProgressState(rawProgress.get(), startedAt, telemetrySampler).copy(
                        isRunning = false,
                        phase = DiagnosticProgressPhase.FAILED,
                        message = "Workspace analysis cancelled safely."
                    )
                    progressState = cancelledProgress
                    performanceSamples +=
                        DiagnosticPerformanceSample.fromProgressState(
                            telemetrySampleIndex.incrementAndGet(),
                            cancelledProgress
                        )
                    reporter.cancelled("Workspace study cancelled safely; no partial study was saved.")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    telemetryTicker.cancel()
                    errorMessage = "Workspace study failed: ${error.message ?: error::class.simpleName}"
                    val failedProgress =
                        workspaceProgressState(rawProgress.get(), startedAt, telemetrySampler).copy(
                            isRunning = false,
                            phase = DiagnosticProgressPhase.FAILED,
                            message = errorMessage ?: "Workspace analysis failed unexpectedly."
                        )
                    progressState = failedProgress
                    performanceSamples +=
                        DiagnosticPerformanceSample.fromProgressState(
                            telemetrySampleIndex.incrementAndGet(),
                            failedProgress
                        )
                    reporter.failed(errorMessage ?: "Workspace analysis failed unexpectedly.")
                } finally {
                    telemetryTicker.cancel()
                    cancelSignal = null
                }
            }.onFailure { error ->
                cancelSignal = null
                errorMessage = error.message ?: "Workspace analysis could not be started."
                val failedStartProgress =
                    workspaceProgressState(rawProgress.get(), startedAt, telemetrySampler).copy(
                        isRunning = false,
                        phase = DiagnosticProgressPhase.FAILED,
                        message = errorMessage ?: "Workspace analysis could not be started."
                    )
                progressState = failedStartProgress
                performanceSamples +=
                    DiagnosticPerformanceSample.fromProgressState(
                        telemetrySampleIndex.incrementAndGet(),
                        failedStartProgress
                    )
            }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("workspace-root")
                .tutorialAnchor(TutorialTargets.Workspace)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onReturnToRobot != null) {
            OutlinedButton(
                onClick = onReturnToRobot,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("workspace-return-to-robot")
            ) {
                Text("← Back to robot card")
            }
        }
        Text(
            text = "3D Robot Workspace Laboratory",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        JargonHelpNotice()
        JargonAwareText(
            text =
                "Generate a reproducible joint-space sweep, watch the robot trace its observed workspace, and inspect possible dead-space regions without overstating sampled evidence.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WorkspaceCard(
            title = "Study setup",
            modifier =
                Modifier
                    .testTag("workspace-study-contract")
                    .tutorialAnchor(TutorialTargets.WorkspaceStudyContract)
        ) {
            Text("Quality preset", style = MaterialTheme.typography.labelLarge)
            CompactSelectionMenu(
                options = WorkspaceQualityPreset.entries,
                selected =
                    WorkspaceQualityPreset.entries.firstOrNull { preset ->
                        sampleCountText == preset.samples.toString() &&
                            voxelResolutionText == preset.resolution.toString() &&
                            replicationCountText == preset.replications.toString()
                    },
                label = WorkspaceQualityPreset::label,
                onSelected = ::applyPreset,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("workspace-quality-preset")
                        .tutorialAnchor(TutorialTargets.WorkspaceQualityPreset),
                enabled = !workspaceAnalysisActive
            )
            AppDisclosureSection(
                title = "Preset values and evidence scope",
                summary = "What Quick, Balanced and Research change",
                testTag = "workspace-preset-help"
            ) {
                WorkspaceQualityPreset.entries.forEach { preset ->
                    JargonAwareText("${preset.label} workspace preset: ${preset.explanation} ${preset.exactSummary}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Presets change samples, grid resolution and replications. Scientific seed and device resource policy stay at their selected values.", style = MaterialTheme.typography.bodySmall)
            }
            if (selectedRobot != null && previewConfig != null) {
                Text(workspaceWorkSummary(selectedRobot.robot, previewConfig, devicePolicy.effectiveWorkerCount), modifier = Modifier.testTag("workspace-work-summary"), style = MaterialTheme.typography.bodySmall)
            }
            WorkspaceDisclosureSection(
                title = "Robot settings",
                subtitle =
                    "${selectedRobot?.robot?.name ?: "No robot selected"} · " +
                        "$sampleCountText samples · $replicationCountText replication(s)",
                expanded = studySettingsExpanded,
                onExpandedChange = { studySettingsExpanded = it },
                modifier = Modifier.testTag("workspace-study-settings-disclosure")
            ) {
            WorkspaceRobotLibrarySelector(
                choices = robotChoices,
                selectedRobotId = selectedRobotId,
                enabled = !workspaceAnalysisActive,
                onSelect = { choice ->
                    if (choice.id != selectedRobotId) {
                        selectedRobotId = choice.id
                        clearWorkspaceVisualization()
                        tutorialReporter.report(
                            target = TutorialTargets.WorkspaceRobotSelector,
                            interaction = TutorialInteraction.CHOOSE,
                            detail = "Workspace robot selected: ${choice.robot.name}."
                        )
                    }
                }
            )

            }
            WorkspaceDisclosureSection(
                title = "Sampling settings",
                subtitle = previewConfig?.let { "${it.sampleCount} samples · ${it.voxelResolution}³ · ${it.replicationCount} replications" } ?: "Check numeric values",
                expanded = samplingSettingsExpanded,
                onExpandedChange = { samplingSettingsExpanded = it },
                modifier = Modifier.testTag("workspace-sampling-settings-disclosure")
            ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("workspace-numeric-contract")
                        .tutorialAnchor(TutorialTargets.WorkspaceNumericContract),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(
                    value = sampleCountText,
                    onValueChange = { sampleCountText = it },
                    label = { JargonAwareText("Joint-space samples (${RobotWorkspaceAnalysisConfig.MINIMUM_SAMPLE_COUNT}–${RobotWorkspaceAnalysisConfig.MAXIMUM_SAMPLE_COUNT})") },
                    supportingText = { Text("Total joint states, including limit probes. More samples can discover more occupied cells and require more FK work and storage.") },
                    enabled = !workspaceAnalysisActive,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = voxelResolutionText,
                        onValueChange = { voxelResolutionText = it },
                        label = { JargonAwareText("Voxel resolution") },
                        supportingText = { Text("${RobotWorkspaceAnalysisConfig.MINIMUM_VOXEL_RESOLUTION}–${RobotWorkspaceAnalysisConfig.MAXIMUM_VOXEL_RESOLUTION} cells per Cartesian axis. Higher values give smaller cells and cubic grid growth.") },
                        enabled = !workspaceAnalysisActive,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replicationCountText,
                        onValueChange = { replicationCountText = it },
                        label = { JargonAwareText("Replications") },
                        supportingText = { Text("1–${RobotWorkspaceAnalysisConfig.MAXIMUM_REPLICATION_COUNT} independently shifted sequences sharing the total sample budget. More replications leave fewer samples in each.") },
                        enabled = !workspaceAnalysisActive,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { seedText = it },
                    label = { JargonAwareText("Scientific seed") },
                    supportingText = { Text("Signed integer ${Int.MIN_VALUE} to ${Int.MAX_VALUE}; changes the shifts, not the amount of work. Reproduction requires the same robot, settings and protocol.") },
                    enabled = !workspaceAnalysisActive,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                JargonAwareText(
                    "Device policy: ${devicePolicy.preset.displayName}. ${devicePolicy.safetyMessage} Workspace caps parallel FK at ${RobotWorkspaceAnalyzer.MAXIMUM_WORKERS} workers. More workers use more concurrent scratch memory; they do not increase the sample budget. Ordered seeded sampling is independent of worker count. The saved study records actual workers, numeric configuration and protocol (${previewConfig?.samplingProtocol?.protocolId ?: "invalid configuration"}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            if (!workspaceAnalysisActive) {
                Button(
                    onClick = ::startAnalysis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("workspace-generate")
                            .tutorialAnchor(TutorialTargets.WorkspaceGenerate)
                ) {
                    Text("Generate, animate and save workspace")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        processCoordinator.requestCancel(ResearchProcessIds.WORKSPACE_ANALYSIS)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("workspace-cancel")
                            .tutorialAnchor(TutorialTargets.WorkspaceCancel)
                ) {
                    Text("Cancel safely")
                }
            }
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .politeLiveRegion()
                        .testTag("workspace-error-status")
            )
        }

        progressState?.takeIf { it.isRunning || showCompletedTelemetry }?.let { progress ->
            DiagnosticLoadingProgressCard(
                progressState = progress,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Workspace Analysis Running",
                        finishedTitle = "Workspace Analysis Telemetry",
                        progressSectionTitle = "Workspace progress",
                        completedLabel = "Completed calculations",
                        remainingLabel = "Calculations left",
                        rateLabel = "Workspace samples per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "3D robot workspace"
                    ),
                timeline = workspaceTimeline(progressState = progress, sourceProgress = workspaceProgress)
            )
            if (!progress.isRunning) {
                OutlinedButton(
                    onClick = { showCompletedTelemetry = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hide completed telemetry")
                }
            }
        }

        progressState?.takeIf { !it.isRunning && !showCompletedTelemetry && activeStudy != null }?.let { progress ->
            WorkspaceCard("Workspace analysis complete") {
                Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { showCompletedTelemetry = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View performance telemetry")
                }
            }
        }

        activeStudy?.let { current ->
            WorkspaceCard("Interactive workspace trace") {
                Text(
                    "Drag to orbit · pinch to zoom · tap a cell to inspect",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "The robot interpolates inside its joint limits between sampled anchors. Only the seeded anchor states count toward coverage statistics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WorkspaceDisclosureSection(
                    title = "Workspace display mode",
                    subtitle = renderMode.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                    expanded = displayModeExpanded,
                    onExpandedChange = { displayModeExpanded = it },
                    modifier = Modifier.testTag("workspace-display-mode-disclosure")
                ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("workspace-mode-selector")
                            .tutorialAnchor(TutorialTargets.WorkspaceModeSelector),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("Workspace view", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkspaceRenderModeButton(
                            label = "Point cloud",
                            selected = renderMode == RobotWorkspaceRenderMode.POINT_CLOUD,
                            testTag = "workspace-mode-points",
                            onClick = {
                                if (renderMode != RobotWorkspaceRenderMode.POINT_CLOUD) {
                                    renderMode = RobotWorkspaceRenderMode.POINT_CLOUD
                                    tutorialReporter.report(
                                        target = TutorialTargets.WorkspaceModeSelector,
                                        interaction = TutorialInteraction.CHOOSE,
                                        detail = "Point-cloud workspace mode selected."
                                    )
                                }
                            }
                        )
                        WorkspaceRenderModeButton(
                            label = "Surface shell",
                            selected = renderMode == RobotWorkspaceRenderMode.SURFACE_SHELL,
                            testTag = "workspace-mode-surface",
                            onClick = {
                                if (renderMode != RobotWorkspaceRenderMode.SURFACE_SHELL) {
                                    renderMode = RobotWorkspaceRenderMode.SURFACE_SHELL
                                    tutorialReporter.report(
                                        target = TutorialTargets.WorkspaceModeSelector,
                                        interaction = TutorialInteraction.CHOOSE,
                                        detail = "Surface-shell workspace mode selected."
                                    )
                                }
                            }
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        WorkspaceRenderModeButton(
                            label = "Animated construction",
                            selected = renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION,
                            testTag = "workspace-mode-construction",
                            onClick = {
                                if (renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                                    startEnvelopeConstruction()
                                    tutorialReporter.report(
                                        target = TutorialTargets.WorkspaceModeSelector,
                                        interaction = TutorialInteraction.CHOOSE,
                                        detail = "Animated workspace construction selected."
                                    )
                                }
                            }
                        )
                    }
                }
                }
                Text(
                    when (renderMode) {
                        RobotWorkspaceRenderMode.POINT_CLOUD ->
                            "Every visible bubble is a classified voxel."
                        RobotWorkspaceRenderMode.SURFACE_SHELL ->
                            "Only exposed voxel faces are joined into a transparent shell; interior faces are removed so sampled cavities remain visible."
                        RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION ->
                            "The robot follows a deterministic path calculated from canonical FK and sampled boundary cells; the same path reveals the reconstructed mesh. Red remains candidate internal dead space, not a collision proof."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                    WorkspaceEnvelopeConstructionTimeline(
                        construction = envelopeConstruction,
                        progress = constructionProgress,
                        preparing = constructionPreparing
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("● WORKSPACE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("● DEAD SPACE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(470.dp)
                        .background(Color(0xFF101820), RoundedCornerShape(14.dp))
                        .testTag("workspace-3d-scene")
                        .tutorialAnchor(TutorialTargets.WorkspaceScene)
                ) {
                    RobotWorkspaceScene3D(
                        study = current,
                        revealedSampleCount = revealedSamples,
                        camera = camera,
                        displayOptions = displayOptions,
                        renderMode = renderMode,
                        envelopeConstruction = envelopeConstruction,
                        constructionProgress = constructionProgress,
                        selectedVoxelId = selectedVoxel?.id,
                        robotPose = robotPose,
                        endEffectorTrace = traceSnapshot,
                        onCameraChange = { camera = it },
                        onVoxelSelected = { selectedVoxel = it }
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("workspace-playback")
                        .tutorialAnchor(TutorialTargets.WorkspacePlayback),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (playing) {
                                playing = false
                            } else {
                                if (
                                    renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION &&
                                    constructionProgress >= 1.0
                                ) {
                                    constructionProgress = 0.0
                                    trace.clear()
                                } else if (
                                    renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION &&
                                    playbackPosition >= current.samples.lastIndex.toDouble()
                                ) {
                                    playbackPosition = 0.0
                                    trace.clear()
                                }
                                playing = true
                            }
                        },
                        enabled =
                            !constructionPreparing &&
                                (renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION || envelopeConstruction != null),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (playing) "Pause" else "Play") }
                    OutlinedButton(
                        onClick = {
                            if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                                constructionProgress = 0.0
                            } else {
                                playbackPosition = 0.0
                            }
                            trace.clear()
                            playing =
                                renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION ||
                                    envelopeConstruction != null
                        },
                        enabled = !constructionPreparing,
                        modifier = Modifier.weight(1f)
                    ) { Text("Restart") }
                    OutlinedButton(
                        onClick = { camera = WorkspaceCamera() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset view") }
                }
                Text("Animation speed", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("workspace-speed")
                        .tutorialAnchor(TutorialTargets.WorkspaceSpeed),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(8.0 to "Detail", 60.0 to "Normal", 480.0 to "Fast").forEach { (rate, label) ->
                        val selected = playbackRate == rate
                        if (selected) {
                            FilledTonalButton(onClick = { playbackRate = rate }, modifier = Modifier.weight(1f)) { Text(label) }
                        } else {
                            OutlinedButton(onClick = { playbackRate = rate }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("workspace-reveal")
                            .tutorialAnchor(TutorialTargets.WorkspaceReveal),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                        Text("Construction ${(constructionProgress * 100.0).toInt()}%")
                        Slider(
                            value = constructionProgress.toFloat(),
                            onValueChange = {
                                playing = false
                                constructionProgress = it.toDouble()
                                trace.clear()
                            },
                            enabled = envelopeConstruction != null && !constructionPreparing,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().testTag("workspace-construction-slider")
                        )
                        OutlinedButton(
                            onClick = {
                                playing = false
                                constructionProgress = 1.0
                                trace.clear()
                            },
                            enabled = envelopeConstruction != null && !constructionPreparing,
                            modifier = Modifier.fillMaxWidth().testTag("workspace-complete-construction")
                        ) {
                            Text("Complete envelope construction")
                        }
                    } else {
                        Text("$revealedSamples / ${current.samples.size} sampled states revealed")
                        Slider(
                            value = playbackPosition.toFloat(),
                            onValueChange = {
                                playing = false
                                playbackPosition = it.toDouble()
                                trace.clear()
                            },
                            valueRange = 0f..current.samples.lastIndex.toFloat(),
                            modifier = Modifier.fillMaxWidth().testTag("workspace-reveal-slider")
                        )
                        OutlinedButton(
                            onClick = {
                                playing = false
                                playbackPosition = current.samples.lastIndex.toDouble()
                                trace.clear()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("workspace-reveal-complete")
                        ) {
                            Text("Reveal completed workspace map")
                        }
                    }
                }
                WorkspaceDisclosureSection(
                    title = "Visible layers and joint constraints",
                    subtitle = "Choose which evidence is drawn and inspect the limits used",
                    expanded = visibleLayersExpanded,
                    onExpandedChange = { visibleLayersExpanded = it },
                    modifier = Modifier.testTag("workspace-visible-layers-disclosure")
                ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("workspace-visibility-layers")
                            .tutorialAnchor(TutorialTargets.WorkspaceVisibilityLayers),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    WorkspaceVisibilityToggle(
                        label = "Observed reachable core",
                        color = Color(0xFF2E7D32),
                        checked = displayOptions.showReachableCore,
                        onCheckedChange = {
                            displayOptions = displayOptions.copy(showReachableCore = it)
                            tutorialReporter.report(
                                TutorialTargets.WorkspaceVisibilityLayers,
                                TutorialInteraction.CHOOSE,
                                detail = "Reachable-core visibility changed."
                            )
                        }
                    )
                    WorkspaceVisibilityToggle(
                        label = "Observed reachable boundary",
                        color = Color(0xFFF9A825),
                        checked = displayOptions.showReachableBoundary,
                        onCheckedChange = {
                            displayOptions = displayOptions.copy(showReachableBoundary = it)
                            tutorialReporter.report(
                                TutorialTargets.WorkspaceVisibilityLayers,
                                TutorialInteraction.CHOOSE,
                                detail = "Reachable-boundary visibility changed."
                            )
                        }
                    )
                    WorkspaceVisibilityToggle(
                        label =
                            if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                                "Candidate internal dead-space volume"
                            } else {
                                "Unobserved candidate space (final map)"
                            },
                        color = Color(0xFFC62828),
                        checked = displayOptions.showUnobservedCandidates,
                        onCheckedChange = {
                            displayOptions = displayOptions.copy(showUnobservedCandidates = it)
                            tutorialReporter.report(
                                TutorialTargets.WorkspaceVisibilityLayers,
                                TutorialInteraction.CHOOSE,
                                detail = "Candidate-space visibility changed."
                            )
                        }
                    )
                    if (renderMode == RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
                        WorkspaceVisibilityToggle(
                            label = "X-ray interior (draw dead space through shell)",
                            color = Color(0xFFAD1457),
                            checked = displayOptions.showDeadSpaceXRay,
                            onCheckedChange = {
                                displayOptions = displayOptions.copy(showDeadSpaceXRay = it)
                                tutorialReporter.report(
                                    TutorialTargets.WorkspaceVisibilityLayers,
                                    TutorialInteraction.CHOOSE,
                                    detail = "Dead-space X-ray visibility changed."
                                )
                            }
                        )
                    }
                    WorkspaceVisibilityToggle(
                        label = "Joint angle / travel limits",
                        color = Color(0xFFAB47BC),
                        checked = displayOptions.showJointLimits,
                        onCheckedChange = {
                            displayOptions = displayOptions.copy(showJointLimits = it)
                            tutorialReporter.report(
                                TutorialTargets.WorkspaceVisibilityLayers,
                                TutorialInteraction.CHOOSE,
                                detail = "Joint-limit visibility changed."
                            )
                        }
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Joint constraints used by this study", fontWeight = FontWeight.Bold)
                    current.robot.joints.forEachIndexed { index, joint ->
                        Text(
                            text = "${index + 1}. ${joint.name}: ${jointLimitLabel(joint.type, joint.minValue, joint.maxValue)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    JargonAwareText(
                        "Revolute sectors and prismatic rails are drawn in their actual standard-DH joint frames. Edit limits in Robot Lab, then generate a new study to change them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
                JargonAwareText(
                    "Scientific caution: red cells or red shell regions are not proof of unreachable space. They are regions inside a conservative reach envelope that this finite sample did not observe; denser convergence and repeated seeds strengthen the evidence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                selectedVoxel?.let { voxel -> WorkspaceVoxelInspector(voxel) }
            }

            WorkspaceStudyMetrics(current)
            WorkspaceDisclosureSection(
                title = "Convergence charts",
                subtitle =
                    "Volume stability across samples and independent replications",
                expanded = convergenceExpanded,
                onExpandedChange = { convergenceExpanded = it },
                modifier = Modifier.testTag("workspace-results-disclosure")
            ) {
                val replicationCoverage = remember(current) { workspaceReplicationCoverage(current) }
                if (replicationCoverage.size >= 2) {
                    ProfessionalLineChart(
                        title = "Randomized-replication stability",
                        subtitle = "Each point is an independent shifted-Halton replication plus the same deterministic limit probes.",
                        points = replicationCoverage.map { ChartLinePoint((it.replicationIndex + 1).toDouble(), it.observedVoxelVolumeCubicMeters) },
                        xAxisLabel = "Replication",
                        yAxisLabel = "Observed volume (m³)",
                        color = Color(0xFF6A1B9A)
                    )
                }
                ProfessionalLineChart(
                    title = "Empirical workspace convergence",
                    subtitle = "Observed voxel volume should flatten as more reproducible joint states are evaluated.",
                    points = current.convergence.map { ChartLinePoint(it.sampleCount.toDouble(), it.observedVoxelVolumeCubicMeters) },
                    xAxisLabel = "Samples",
                    yAxisLabel = "Observed volume (m³)",
                    color = Color(0xFF1565C0)
                )
            }
        }

        WorkspaceSavedStudiesSection(
            summaries = summaries,
            selectedStudyId = study?.studyId,
            onLoad = { summary ->
                scope.launch {
                    errorMessage = null
                    runCatching { withContext(Dispatchers.IO) { studyRepository.load(summary) } }
                        .onSuccess(::showStudy)
                        .onFailure { errorMessage = "Could not load study: ${it.message}" }
                }
            }
        )
    }
}

@Composable
private fun RowScope.WorkspaceRenderModeButton(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    WeightedSelectionTile(
        selected = selected,
        label = label,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun WorkspaceEnvelopeConstructionTimeline(
    construction: RobotWorkspaceEnvelopeConstruction?,
    progress: Double,
    preparing: Boolean
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("workspace-construction-timeline")
            .tutorialAnchor(TutorialTargets.WorkspaceConstructionTimeline),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Envelope construction sequence", fontWeight = FontWeight.Bold)
        if (preparing || construction == null) {
            JargonAwareText(
                "Preparing sampled boundaries and triangular mesh…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        val isAxial = construction.strategy == WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP
        Text(
            if (isAxial) {
                val degrees = (construction.sweepEndRadians - construction.sweepStartRadians) * 180.0 / PI
                "Standard-DH axial sweep · ${String.format(Locale.US, "%.1f", degrees)}° inside the real base-joint limits"
            } else {
                "General-DH reconstruction · smooth boundary route for any supported joint count, with no assumed rotational symmetry"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val currentStage = workspaceEnvelopeConstructionStage(progress)
        val stages =
            if (isAxial) {
                listOf(
                    WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS to "Freeze base reference axis",
                    WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY to "Trace maximum extension profile",
                    WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY to "Trace folded-arm / dead-space profile",
                    WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY to "Sweep profile through base angle",
                    WorkspaceEnvelopeConstructionStage.FILL_SURFACE to "Fill transparent triangular envelope"
                )
            } else {
                listOf(
                    WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS to "Move smoothly from home to the measured boundary",
                    WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY to "Trace spatially distributed outer boundary states",
                    WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY to "Trace the reachable rim beside candidate dead space",
                    WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY to "Replay the calculated route and reconstruct its mesh",
                    WorkspaceEnvelopeConstructionStage.FILL_SURFACE to "Fill transparent triangular envelope"
                )
            }
        stages.forEach { (stage, label) ->
            val completed = stage.ordinal < currentStage.ordinal || progress >= 1.0
            val current = stage == currentStage && progress < 1.0
            Row(
                Modifier.fillMaxWidth().testTag("workspace-construction-stage-${stage.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        completed -> "●"
                        current -> "◉"
                        else -> "○"
                    },
                    color =
                        when {
                            completed -> Color(0xFF2E7D32)
                            current -> Color(0xFF1565C0)
                            else -> Color(0xFF78909C)
                        },
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        Text(
            if (construction.hasInnerDeadSpace) {
                "Green = sampled reachable envelope · red = candidate internal dead-space region" +
                    if (construction.candidateDeadSpaceVoxelCount > 0) {
                        " (${construction.candidateDeadSpaceVoxelCount} classified cells)."
                    } else {
                        "."
                    }
            } else {
                "No resolved inner cavity was found at this study's sampling and voxel resolution."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Model scope: standard-DH links and joint limits. Physical body collisions or obstacles require geometry that is not part of the current robot definition.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6D4C41)
        )
    }
}

@Composable
private fun WorkspaceStudyMetrics(study: RobotWorkspaceStudy) {
    WorkspaceCard(
        title = "Scientific summary",
        modifier =
            Modifier
                .testTag("workspace-result")
                .tutorialAnchor(TutorialTargets.WorkspaceResult)
    ) {
        WorkspaceMetric(
            "Robot",
            study.robot.name,
            valueModifier = Modifier.testTag("workspace-study-robot")
        )
        WorkspaceMetric("Samples", "${study.samples.size} valid · ${study.invalidSampleCount} invalid")
        WorkspaceMetric("Stored configuration", "${study.config.sampleCount} requested samples · ${study.config.voxelResolution}³ grid · ${study.config.replicationCount} replications")
        WorkspaceMetric("Seed / protocol", "${study.config.randomSeed} · ${study.config.samplingProtocol.protocolId}")
        WorkspaceMetric("Workers / duration", "${study.workerCount} · ${study.durationMillis} ms")
        WorkspaceMetric("Conservative radial bound", "${formatWorkspaceNumber(study.conservativeRadiusMeters)} m")
        WorkspaceMetric("Voxel cell edge", "${formatWorkspaceNumber(study.voxelCellSizeMeters)} m")
        WorkspaceMetric("Observed volume", "${formatWorkspaceNumber(study.observedVoxelVolumeCubicMeters)} m³")
        WorkspaceMetric("Observed envelope", "${formatWorkspaceNumber(study.observedEnvelopeFraction * 100.0)}%")
        WorkspaceMetric("Last-quarter volume gain", "${formatWorkspaceNumber(study.lastQuarterRelativeVolumeGain * 100.0)}%")
        WorkspaceMetric(
            "Replication relative range",
            workspaceReplicationRelativeRange(study)?.let { "${formatWorkspaceNumber(it * 100.0)}%" } ?: "Needs ≥2 replications"
        )
        JargonAwareText(
            "Volume is a resolution-dependent voxel estimate, not a closed-form exact workspace volume. The convergence curve records how stable that estimate became.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkspaceVoxelInspector(voxel: RobotWorkspaceVoxel) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("workspace-selected-cell")
            .tutorialAnchor(TutorialTargets.WorkspaceSelectedCell)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Selected workspace cell", fontWeight = FontWeight.Bold)
        Text(voxel.classification.readableLabel())
        Text("Center: (${formatWorkspaceNumber(voxel.center.x)}, ${formatWorkspaceNumber(voxel.center.y)}, ${formatWorkspaceNumber(voxel.center.z)}) m")
        Text("Observed hits: ${voxel.sampleHitCount}")
        Text("First observation: ${voxel.firstObservedSampleIndex?.plus(1) ?: "not observed"}")
    }
}

@Composable
private fun WorkspaceVisibilityToggle(
    label: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 8.dp).background(color, RoundedCornerShape(99.dp)).size(12.dp))
        Text(label, modifier = Modifier.weight(0.78f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SavedStudyRow(summary: RobotWorkspaceStudySummary, onLoad: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(summary.studyName, fontWeight = FontWeight.Bold)
        Text(summary.robotName, style = MaterialTheme.typography.bodySmall)
        Text("${summary.sampleCount} samples · ${summary.voxelResolution}³ grid · ${formatWorkspaceNumber(summary.observedEnvelopeFraction * 100)}% observed")
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.createdAtEpochMillis)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onLoad, modifier = Modifier.fillMaxWidth()) { Text("Load and replay") }
    }
}

@Composable
private fun WorkspaceMetric(
    label: String,
    value: String,
    valueModifier: Modifier = Modifier
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        JargonAwareText(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.58f)
        )
        // A metric value can include a user-defined robot/study name or protocol identifier.
        Text(value, fontWeight = FontWeight.SemiBold, modifier = valueModifier.weight(0.42f))
    }
}

@Composable
private fun WorkspaceDisclosureSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppDisclosureHeader(
            title = title,
            summary = subtitle,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = modifier
        )
        if (expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FA))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            JargonAwareText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun workspaceProgressState(
    progress: RobotWorkspaceAnalysisProgress,
    startedAtMillis: Long,
    sampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val elapsedSeconds = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L) / 1000.0
    val rate = if (elapsedSeconds > 0.0) progress.completedWorkUnits / elapsedSeconds else 0.0
    val remaining = (progress.totalWorkUnits - progress.completedWorkUnits).coerceAtLeast(0)
    val completed = progress.phase == WorkspaceAnalysisPhase.COMPLETED
    return DiagnosticProgressState(
        isRunning = !completed,
        phase = if (completed) DiagnosticProgressPhase.COMPLETED else DiagnosticProgressPhase.AGGREGATING,
        completedRuns = progress.completedWorkUnits,
        totalRuns = progress.totalWorkUnits,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsedSeconds,
        message = progress.message,
        telemetry = sampler.sample()
    )
}

private fun workspaceTimeline(
    progressState: DiagnosticProgressState,
    sourceProgress: RobotWorkspaceAnalysisProgress?
): List<DiagnosticTimelineStep> {
    val current = sourceProgress?.phase
    val phases =
        listOf(
            WorkspaceAnalysisPhase.VALIDATING_ROBOT to ("Validate" to "Checks DH rows, joint limits and dimensional consistency."),
            WorkspaceAnalysisPhase.BUILDING_SAMPLE_SEQUENCE to ("Sequence" to "Builds the seeded probes and shifted-Halton sequence."),
            WorkspaceAnalysisPhase.RUNNING_FORWARD_KINEMATICS to ("FK sweep" to "Maps joint-space states into Cartesian space."),
            WorkspaceAnalysisPhase.BUILDING_VOXELS to ("Voxelize" to "Builds the resolution-controlled spatial estimate."),
            WorkspaceAnalysisPhase.CLASSIFYING_SPACE to ("Classify" to "Separates observed core, frontier and unobserved candidates."),
            WorkspaceAnalysisPhase.FINALIZING to ("Evidence" to "Calculates convergence and saves reproducibility metadata.")
        )
    val inferredIndex =
        if (progressState.phase == DiagnosticProgressPhase.COMPLETED) phases.lastIndex
        else current?.let { phase -> phases.indexOfFirst { it.first == phase } }?.coerceAtLeast(0) ?: 0
    return phases.mapIndexed { index, (_, labelAndDescription) ->
        DiagnosticTimelineStep(
            label = labelAndDescription.first,
            status =
                when {
                    progressState.phase == DiagnosticProgressPhase.FAILED && index == inferredIndex -> DiagnosticTimelineStatus.FAILED
                    progressState.phase == DiagnosticProgressPhase.COMPLETED || index < inferredIndex -> DiagnosticTimelineStatus.COMPLETE
                    index == inferredIndex -> DiagnosticTimelineStatus.RUNNING
                    else -> DiagnosticTimelineStatus.PENDING
                },
            description = labelAndDescription.second
        )
    }
}

private fun WorkspaceVoxelClass.readableLabel(): String =
    when (this) {
        WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE -> "Observed reachable core"
        WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY -> "Observed reachable boundary"
        WorkspaceVoxelClass.UNOBSERVED_CANDIDATE -> "Unobserved candidate space"
    }

private fun formatWorkspaceNumber(value: Double): String =
    if (!value.isFinite()) "n/a" else String.format(Locale.US, "%.5g", value)

private fun jointLimitLabel(type: JointType, minimum: Double, maximum: Double): String =
    when (type) {
        JointType.REVOLUTE ->
            "${String.format(Locale.US, "%.1f", minimum * 180.0 / PI)}° to " +
                "${String.format(Locale.US, "%.1f", maximum * 180.0 / PI)}°"
        JointType.PRISMATIC ->
            "${formatWorkspaceNumber(minimum)} m to ${formatWorkspaceNumber(maximum)} m"
    }

private const val MAXIMUM_TRACE_POINTS = 600
private const val MAXIMUM_TRACE_STEP_CELLS = 2.5
private const val WORKSPACE_PLAYBACK_FRAME_INTERVAL_MILLIS = 33L
private const val NORMAL_PLAYBACK_RATE = 60.0
private const val CONSTRUCTION_DURATION_SECONDS = 14.0
