package com.robotkinematicslab.mobile.ui.charts.spatial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorSelectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.progress.FinalTelemetryFigureArchiver
import com.robotkinematicslab.mobile.ui.shared.progress.TelemetrySessionRepository
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticWorkspace3DExplorer(
    report: Layer1DiagnosticReport,
    runs: List<DiagnosticRunResult>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val telemetryRepository = remember(context) { TelemetrySessionRepository(context) }
    val finalTelemetryFigureArchiver = remember(context) { FinalTelemetryFigureArchiver(context) }
    val activeProject = remember(context) { ResearchProjectRepository(context).activeProject() }
    val availableSeeds = remember(runs) { runs.map { it.seed }.distinct().sorted() }
    val availableLinkCounts = remember(runs) { runs.map { it.linkCount }.distinct().sorted() }
    val availableJointModes = remember(runs) { runs.map { it.jointMode }.distinct().sortedBy { it.name } }

    var filter by remember { mutableStateOf(DiagnosticWorkspaceFilter()) }
    var metric by remember { mutableStateOf(WorkspaceColorMetric.OUTCOME) }
    var camera by remember { mutableStateOf(WorkspaceCamera()) }
    var selectedPoint by remember { mutableStateOf<WorkspacePointAggregate?>(null) }
    var selectedPose by remember { mutableStateOf<WorkspaceRobotPose?>(null) }
    var preparedData by remember(runs, filter) { mutableStateOf<DiagnosticWorkspace3DData?>(null) }
    var preparationProgress by remember(runs, filter) {
        mutableStateOf(
            DiagnosticProgressState(
                isRunning = true,
                phase = DiagnosticProgressPhase.AGGREGATING,
                totalRuns = WORKSPACE_PREPARATION_STEPS.size,
                message = "Reading diagnostic workspace runs."
            )
        )
    }
    val performanceSamples = remember(runs, filter) {
        mutableStateListOf<DiagnosticPerformanceSample>()
    }

    LaunchedEffect(runs, filter) {
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
            val rate = if (elapsedSeconds > 0.0) completedSteps / elapsedSeconds else 0.0
            val remaining = (WORKSPACE_PREPARATION_STEPS.size - completedSteps).coerceAtLeast(0)

            preparationProgress =
                DiagnosticProgressState(
                    isRunning = running,
                    phase =
                        when {
                            failed -> DiagnosticProgressPhase.FAILED
                            !running -> DiagnosticProgressPhase.COMPLETED
                            else -> DiagnosticProgressPhase.AGGREGATING
                        },
                    completedRuns = completedSteps,
                    totalRuns = WORKSPACE_PREPARATION_STEPS.size,
                    runsPerSecond = rate,
                    estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
                    elapsedSeconds = elapsedSeconds,
                    message = message,
                    telemetry = telemetrySampler.sample()
                )
            performanceSamples +=
                DiagnosticPerformanceSample.fromProgressState(
                    sampleIndex = ++sampleIndex,
                    progressState = preparationProgress
                )
        }

        preparedData = null
        selectedPoint = null
        selectedPose = null
        performanceSamples.clear()
        publishProgress(0, "Reading diagnostic workspace runs.")

        val telemetryTicker =
            launch {
                while (isActive) {
                    delay(HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS)
                    val current = preparationProgress
                    if (current.isRunning) {
                        publishProgress(current.completedRuns, current.message)
                    }
                }
            }

        try {
            val sourceRuns = withContext(Dispatchers.Default) { runs.toList() }
            publishProgress(1, "Checking target coordinates for finite values.")

            val validRuns = withContext(Dispatchers.Default) { validWorkspaceRuns(sourceRuns) }
            publishProgress(2, "Applying seed, link-count and topology filters.")

            val filteredRuns =
                withContext(Dispatchers.Default) {
                    filterWorkspaceRuns(validRuns, filter)
                }
            publishProgress(3, "Calculating reproducible 3D workspace bounds.")

            val bounds =
                withContext(Dispatchers.Default) {
                    calculateWorkspaceBounds(filteredRuns)
                }
            publishProgress(4, "Aggregating dense regions into deterministic voxels.")

            val aggregation =
                withContext(Dispatchers.Default) {
                    aggregateWorkspaceRuns(
                        runs = filteredRuns,
                        bounds = bounds
                    )
                }
            publishProgress(5, "Building depth and point-selection structures.")

            val result =
                DiagnosticWorkspace3DData(
                    points = aggregation.first,
                    bounds = bounds,
                    totalRunCount = sourceRuns.size,
                    validPositionCount = validRuns.size,
                    filteredRunCount = filteredRuns.size,
                    invalidPositionCount = sourceRuns.size - validRuns.size,
                    voxelResolution = aggregation.second
                )

            publishProgress(
                completedSteps = WORKSPACE_PREPARATION_STEPS.size,
                message = "Interactive 3D workspace is ready.",
                running = false
            )
            val completedTelemetry = performanceSamples.toList()
            withContext(Dispatchers.IO) {
                runCatching {
                    val session =
                        telemetryRepository.save(
                            projectName = activeProject.name,
                            sessionType = "3D workspace preparation",
                            title = "3D workspace · ${workspaceFilterLabel(filter)}",
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            publishProgress(
                completedSteps = preparationProgress.completedRuns,
                message = "3D workspace preparation failed: ${error.message ?: error::class.simpleName}",
                running = false,
                failed = true
            )
        } finally {
            telemetryTicker.cancel()
        }
    }

    LaunchedEffect(selectedPoint, report.config.topology.stressLevel) {
        val point = selectedPoint
        selectedPose =
            if (point == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    buildSelectedRobotPose(
                        point = point,
                        stressLevel = report.config.topology.stressLevel
                    )
                }
            }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("diagnostics-workspace-3d")
                .tutorialAnchor(TutorialTargets.DiagnosticsWorkspace3D),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChartSectionCard(
            title = "🌐 Interactive 3D Workspace",
            subtitle =
                "Targets retain their physical X/Y/Z meaning. Color changes the diagnostic metric; it never changes position.",
            // An interactive camera state is not a deterministic scientific figure. Every 2D
            // chart is exported, but this live 3D workspace is deliberately excluded.
            automaticExportKey = null
        ) {
            Text(
                text = "Color metric",
                style = MaterialTheme.typography.labelLarge
            )
            CompactSelectionMenu(
                options = WorkspaceColorMetric.entries,
                selected = metric,
                label = { it.title },
                onSelected = { metric = it },
                modifier = Modifier.testTag("diagnostics-workspace-metric-options"),
                optionTestTag = { "diagnostics-workspace-metric-${it.name.lowercase()}" }
            )
            Text(
                text = metric.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Scientific filters",
                style = MaterialTheme.typography.labelLarge
            )
            Text("Seed", style = MaterialTheme.typography.labelMedium)
            CompactSelectionMenu(
                options = listOf<Int?>(null) + availableSeeds,
                selected = filter.seed,
                label = { it?.let { seed -> "Seed $seed" } ?: "All seeds" },
                onSelected = { filter = filter.copy(seed = it) },
                modifier = Modifier.testTag("diagnostics-workspace-seed-options"),
                optionTestTag = { seed -> "diagnostics-workspace-seed-${seed ?: "all"}" }
            )
            Text("Links", style = MaterialTheme.typography.labelMedium)
            CompactSelectionMenu(
                options = listOf<Int?>(null) + availableLinkCounts,
                selected = filter.linkCount,
                label = { it?.let { count -> "$count links" } ?: "All links" },
                onSelected = { filter = filter.copy(linkCount = it) },
                modifier = Modifier.testTag("diagnostics-workspace-link-options"),
                optionTestTag = { count -> "diagnostics-workspace-links-${count ?: "all"}" }
            )
            Text("Topology", style = MaterialTheme.typography.labelMedium)
            CompactSelectionMenu(
                options = listOf<DiagnosticJointMode?>(null) + availableJointModes,
                selected = filter.jointMode,
                label = { it?.displayName() ?: "All topologies" },
                onSelected = { filter = filter.copy(jointMode = it) },
                modifier = Modifier.testTag("diagnostics-workspace-topology-options"),
                optionTestTag = { mode ->
                    "diagnostics-workspace-topology-${mode?.name?.lowercase() ?: "all"}"
                }
            )
        }

        val data = preparedData
        if (data == null) {
            DiagnosticLoadingProgressCard(
                progressState = preparationProgress,
                performanceSamples = performanceSamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Building Interactive 3D Workspace",
                        finishedTitle = "3D Workspace Preparation",
                        progressSectionTitle = "Spatial Construction Progress",
                        completedLabel = "Completed preparation stages",
                        remainingLabel = "Stages left",
                        rateLabel = "Stages per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "3D workspace preparation"
                    ),
                timeline = workspacePreparationTimeline(preparationProgress)
            )
        } else if (data.points.isEmpty()) {
            ChartSectionCard(
                title = "No spatial runs match these filters",
                subtitle = "Change one or more scientific filters to restore points."
            ) {
                Text("The source report is unchanged; only this visualization is filtered.")
            }
        } else {
            WorkspaceCameraControls(
                camera = camera,
                onCameraChange = { camera = it }
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .testTag("diagnostics-workspace-3d-gestures")
                        .tutorialAnchor(TutorialTargets.DiagnosticsWorkspace3DGestures)
                        .background(
                            color = WorkspaceBackgroundColor,
                            shape = RoundedCornerShape(14.dp)
                        )
            ) {
                WorkspaceScene3D(
                    data = data,
                    metric = metric,
                    camera = camera,
                    selectedPointId = selectedPoint?.id,
                    selectedRobotPose = selectedPose,
                    onCameraChange = { camera = it },
                    onPointSelected = { selectedPoint = it },
                    modifier = Modifier.fillMaxWidth()
                )

                selectedPoint?.let { point ->
                    ChartInspectorSelectionCard(
                        title = if (point.isAggregated) "Selected 3D region" else "Selected diagnostic run",
                        values = workspaceSelectionValues(point),
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(0.78f)
                                .padding(8.dp)
                    )
                }
            }

            WorkspaceLegend(metric = metric, data = data)

            ChartSectionCard(
                title = "3D data provenance",
                subtitle = "Visual reduction never changes report statistics or exported records."
            ) {
                ChartMetricRow("Filtered runs", data.filteredRunCount.toString())
                ChartMetricRow("Displayed spheres", data.displayedPointCount.toString())
                ChartMetricRow("Invalid coordinates", data.invalidPositionCount.toString())
                ChartMetricRow(
                    "Display method",
                    data.voxelResolution?.let { "Deterministic ${it}×${it}×${it} voxel grid" }
                        ?: "One sphere per run"
                )
                ChartMetricRow(
                    "Workspace center",
                    "(${formatWorkspace(data.bounds.center.x)}, ${formatWorkspace(data.bounds.center.y)}, ${formatWorkspace(data.bounds.center.z)}) m"
                )
                Text(
                    text =
                        "Drag to orbit · pinch to zoom · tap a sphere to inspect it and reconstruct its robot pose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkspaceCameraControls(
    camera: WorkspaceCamera,
    onCameraChange: (WorkspaceCamera) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledTonalButton(
            onClick = { onCameraChange(WorkspaceCamera()) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Reset")
        }
        OutlinedButton(
            onClick = { onCameraChange(camera.copy(yaw = 0f, pitch = 0f, zoom = 1f)) },
            modifier = Modifier.weight(1f)
        ) {
            Text("XY")
        }
        OutlinedButton(
            onClick = { onCameraChange(camera.copy(yaw = 0f, pitch = 1.35f, zoom = 1f)) },
            modifier = Modifier.weight(1f)
        ) {
            Text("XZ")
        }
        OutlinedButton(
            onClick = { onCameraChange(camera.copy(yaw = -1.57f, pitch = 0f, zoom = 1f)) },
            modifier = Modifier.weight(1f)
        ) {
            Text("YZ")
        }
    }
}

@Composable
private fun WorkspaceLegend(
    metric: WorkspaceColorMetric,
    data: DiagnosticWorkspace3DData
) {
    val range = workspaceMetricRange(data.points, metric)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WorkspaceLegendItem(
            WorkspaceRejectedColor,
            if (metric == WorkspaceColorMetric.OUTCOME) "Rejected" else "Higher"
        )
        WorkspaceLegendItem(WorkspaceWarningColor, "Middle")
        WorkspaceLegendItem(
            WorkspaceAcceptedColor,
            if (metric == WorkspaceColorMetric.OUTCOME) "Accepted" else "Lower"
        )
    }
    Text(
        text =
            if (metric == WorkspaceColorMetric.OUTCOME) {
                "Color range: rejected → mixed region → accepted"
            } else {
                "${metric.title}: ${formatWorkspace(range.minimum)} → ${formatWorkspace(range.maximum)} ${metric.unit}"
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun WorkspaceLegendItem(
    color: Color,
    label: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun buildSelectedRobotPose(
    point: WorkspacePointAggregate,
    stressLevel: Double
): WorkspaceRobotPose? {
    val run = point.representativeRun
    val robot =
        DiagnosticRobotFactory().buildSeedRobot(
            linkCount = run.linkCount,
            jointMode = run.jointMode,
            stressLevel = stressLevel
        )
    if (run.solutionJointValues.size != robot.joints.size) return null

    val fk =
        runCatching {
            KinematicsService().computeFK(
                robot = robot,
                state = RobotState(run.solutionJointValues)
            )
        }.getOrNull() ?: return null

    if (fk.status != FKStatus.SUCCESS && fk.status != FKStatus.SUCCESS_WITH_WARNING) return null

    return WorkspaceRobotPose(
        jointPositions = fk.jointPositions,
        jointTypes = robot.joints.map { it.type },
        target = run.target,
        endEffector = fk.endEffectorPosition
    )
}

private fun workspaceSelectionValues(
    point: WorkspacePointAggregate
): List<Pair<String, String>> {
    val run = point.representativeRun
    return listOf(
        "Samples" to point.runCount.toString(),
        "Accepted" to "${formatWorkspace(point.acceptanceRate * 100.0)}%",
        "Position" to
                "(${formatWorkspace(point.position.x)}, ${formatWorkspace(point.position.y)}, ${formatWorkspace(point.position.z)}) m",
        "Final error" to "${formatWorkspace(point.averageFinalError)} m",
        "Iterations" to formatWorkspace(point.averageIterations),
        "Seed / links" to "${run.seed} / ${run.linkCount}",
        "Topology" to run.jointMode.displayName(),
        "Status" to run.status
    )
}

private fun DiagnosticJointMode.displayName(): String {
    return name.lowercase()
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
}

private fun workspaceFilterLabel(filter: DiagnosticWorkspaceFilter): String =
    listOf(
        filter.seed?.let { "seed $it" } ?: "all seeds",
        filter.linkCount?.let { "$it links" } ?: "all links",
        filter.jointMode?.displayName() ?: "all topologies"
    ).joinToString(" · ")

private fun workspacePreparationTimeline(
    progress: DiagnosticProgressState
): List<DiagnosticTimelineStep> {
    return WORKSPACE_PREPARATION_STEPS.mapIndexed { index, label ->
        val status =
            when {
                progress.phase == DiagnosticProgressPhase.FAILED && index == progress.completedRuns ->
                    DiagnosticTimelineStatus.FAILED
                index < progress.completedRuns -> DiagnosticTimelineStatus.COMPLETE
                progress.isRunning && index == progress.completedRuns -> DiagnosticTimelineStatus.RUNNING
                else -> DiagnosticTimelineStatus.PENDING
            }
        DiagnosticTimelineStep(label = label, status = status)
    }
}

private fun formatWorkspace(value: Double): String {
    if (!value.isFinite()) return "NA"
    return String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
}

private val WORKSPACE_PREPARATION_STEPS =
    listOf(
        "Read completed diagnostic runs",
        "Validate finite X/Y/Z target coordinates",
        "Apply scientific workspace filters",
        "Calculate spatial bounds and scale",
        "Build deterministic voxel aggregation",
        "Build depth and point-selection structures"
    )
