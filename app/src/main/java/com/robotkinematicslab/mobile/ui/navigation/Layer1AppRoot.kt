package com.robotkinematicslab.mobile.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetGenerationCoordinator
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetState
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetStatus
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.accessibility.LocalAppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.accessibility.LocalReadAloudController
import com.robotkinematicslab.mobile.ui.accessibility.ReadAloudResult
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.process.ResearchResultDestination
import com.robotkinematicslab.mobile.process.ResearchResultReference
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.StorageCategory
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import com.robotkinematicslab.mobile.ui.dataset.DatasetBuilderPanel
import com.robotkinematicslab.mobile.ui.dataset.DatasetBuilderSection
import com.robotkinematicslab.mobile.ui.diagnostic.Layer1DiagnosticPanel
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.projects.ProjectDestination
import com.robotkinematicslab.mobile.ui.projects.ProjectHomeScreen
import com.robotkinematicslab.mobile.ui.projects.ProjectSectionHub
import com.robotkinematicslab.mobile.ui.projects.ProjectWorkspaceSummary
import com.robotkinematicslab.mobile.ui.projects.ResearchProjectLibrary
import com.robotkinematicslab.mobile.ui.process.ResearchProcessCenterOverlay
import com.robotkinematicslab.mobile.ui.settings.ComputeSettingsPanel
import com.robotkinematicslab.mobile.ui.shared.GlobalHeaderActions
import com.robotkinematicslab.mobile.ui.storage.StorageCenterPanel
import com.robotkinematicslab.mobile.ui.theme.AppVisualThemePreferences
import com.robotkinematicslab.mobile.ui.training.LocalTrainingPanel
import com.robotkinematicslab.mobile.ui.training.TrainingLabMode
import com.robotkinematicslab.mobile.ui.workspace.RobotWorkspacePanel

enum class ProjectRoute {
    HOME,
    PREPARE,
    EXPERIMENT,
    LIBRARY,
    ROBOT_LAB,
    WORKSPACE_3D,
    DATASET_BUILDER,
    DIAGNOSTICS,
    TRAINING,
    STORAGE,
    SETTINGS
}

internal const val PROJECT_BOTTOM_NAV_COMPACT_WIDTH_DP = 360

internal fun useCompactProjectBottomNavigation(
    availableWidthDp: Float,
    fontScale: Float
): Boolean {
    require(availableWidthDp.isFinite() && availableWidthDp >= 0f) {
        "Bottom navigation width must be finite and non-negative."
    }
    require(fontScale.isFinite() && fontScale > 0f) {
        "Bottom navigation font scale must be finite and positive."
    }
    val effectiveWidthDp = availableWidthDp / fontScale.coerceAtLeast(1f)
    return effectiveWidthDp <= PROJECT_BOTTOM_NAV_COMPACT_WIDTH_DP
}

internal enum class ProjectSection(
    val label: String,
    val compactLabel: String,
    val symbol: String,
    val rootRoute: ProjectRoute,
    val tutorialTarget: TutorialTargetId
) {
    HOME("Home", "Home", "⌂", ProjectRoute.HOME, TutorialTargets.SectionHome),
    PREPARE("Prepare", "Prep", "＋", ProjectRoute.PREPARE, TutorialTargets.SectionPrepare),
    EXPERIMENT("Experiment", "Exp.", "△", ProjectRoute.EXPERIMENT, TutorialTargets.SectionExperiment),
    LIBRARY("Library", "Lib.", "▣", ProjectRoute.LIBRARY, TutorialTargets.SectionLibrary)
}

@Composable
fun Layer1AppRoot(
    robotLabContent: @Composable (Modifier, TutorialTargetId?) -> Unit,
    visualTheme: AppVisualThemePreferences,
    onVisualThemeChange: (AppVisualThemePreferences) -> Unit,
    accessibilityPreferences: AppAccessibilityPreferences,
    onAccessibilityPreferencesChange: (AppAccessibilityPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val projectRepository = remember(context) { ResearchProjectRepository(context) }
    var projects by remember(projectRepository) { mutableStateOf(projectRepository.listProjects()) }
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectLibrarySettingsVisible by rememberSaveable { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf(ProjectRoute.HOME) }
    // Keep an immutable value inside MutableState. Mutating an ArrayList in place would not
    // invalidate Compose and could leave the visible destination out of sync with navigation.
    var routeBackStack by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var summaryRevision by rememberSaveable { mutableIntStateOf(0) }
    var requestedTrainingRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val openProject = projects.firstOrNull { it.id == openProjectId }
    val colors = MaterialTheme.colorScheme
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val projectStateHolder = rememberSaveableStateHolder()
    val trackedProcesses by processCoordinator.processes.collectAsState()
    val hasActiveResearchProcess = trackedProcesses.any { it.status.isActive }
    fun open(project: ResearchProject) {
        val activated = projectRepository.activateProject(project.id)
        projects = projectRepository.listProjects()
        openProjectId = activated.id
        projectLibrarySettingsVisible = false
        route = ProjectRoute.HOME
        routeBackStack = emptyList()
        summaryRevision += 1
    }

    fun closeProject() {
        openProjectId = null
        projectLibrarySettingsVisible = false
        route = ProjectRoute.HOME
        routeBackStack = emptyList()
        projects = projectRepository.listProjects()
    }

    fun navigate(destination: ProjectRoute) {
        if (destination == route) return
        routeBackStack = appendProjectRoute(routeBackStack, route)
        route = destination
    }

    fun openProjectLevel(destination: ProjectRoute) {
        route = destination
        routeBackStack = emptyList()
        summaryRevision += 1
    }

    fun navigateBack(): Boolean {
        val result = popProjectRoute(routeBackStack)
        val previous = result.destination ?: return false
        routeBackStack = result.remainingRouteNames
        route = previous
        return true
    }

    fun openResearchResult(reference: ResearchResultReference) {
        when (reference.destination) {
            ResearchResultDestination.TRAINING_RUN -> {
                requestedTrainingRunId = reference.artifactId
                if (openProject == null) {
                    open(projectRepository.activeProject())
                    route = ProjectRoute.TRAINING
                } else {
                    navigate(ProjectRoute.TRAINING)
                }
            }
        }
    }

    Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(colors.background, colors.primaryContainer.copy(alpha = 0.18f), colors.background)
                        )
                    )
        ) {
            if (openProject == null) {
                if (projectLibrarySettingsVisible) {
                    BackHandler { projectLibrarySettingsVisible = false }
                    StandaloneSettingsScreen(
                        visualTheme = visualTheme,
                        onVisualThemeChange = onVisualThemeChange,
                        accessibilityPreferences = accessibilityPreferences,
                        onAccessibilityPreferencesChange = onAccessibilityPreferencesChange,
                        onBackToProjects = { projectLibrarySettingsVisible = false },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(bottom = if (hasActiveResearchProcess) 78.dp else 0.dp)
                    )
                } else {
                    ResearchProjectLibrary(
                        projects = projects,
                        onOpenProject = ::open,
                        onCreateProject = { name, objective ->
                            projectRepository.createProject(name, objective)
                            projects = projectRepository.listProjects()
                        },
                        onUpdateProject = { project, name, objective ->
                            projectRepository.updateProject(project.id, name, objective)
                            projects = projectRepository.listProjects()
                        },
                        onOpenSettings = { projectLibrarySettingsVisible = true },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .padding(bottom = if (hasActiveResearchProcess) 78.dp else 0.dp)
                    )
                }
            } else {
                BackHandler {
                    if (!navigateBack()) {
                        closeProject()
                    }
                }
                key(openProject.id) {
                    val summary = remember(openProject.id, summaryRevision) { loadProjectSummary(context) }
                    projectStateHolder.SaveableStateProvider(openProject.id) {
                        ProjectWorkspaceShell(
                            project = openProject,
                            route = route,
                            summary = summary,
                            robotLabContent = robotLabContent,
                            visualTheme = visualTheme,
                            onVisualThemeChange = onVisualThemeChange,
                            accessibilityPreferences = accessibilityPreferences,
                            onAccessibilityPreferencesChange = onAccessibilityPreferencesChange,
                            hasActiveResearchProcess = hasActiveResearchProcess,
                            previousRoute = popProjectRoute(routeBackStack).destination,
                            onRouteChange = { destination ->
                                navigate(destination)
                                if (destination.isSectionRoot()) {
                                    summaryRevision += 1
                                }
                            },
                            onNavigateBack = {
                                if (!navigateBack()) closeProject()
                            },
                            onOpenProjectLevel = ::openProjectLevel,
                            onCloseProject = ::closeProject,
                            requestedTrainingRunId = requestedTrainingRunId,
                            onTrainingRunRequestConsumed = { requestedTrainingRunId = null }
                        )
                    }
                }
            }

            ResearchProcessCenterOverlay(
                onOpenResult = ::openResearchResult,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = if (openProject == null) 16.dp else 88.dp
                        )
            )
    }
}

@Composable
private fun StandaloneSettingsScreen(
    visualTheme: AppVisualThemePreferences,
    onVisualThemeChange: (AppVisualThemePreferences) -> Unit,
    accessibilityPreferences: AppAccessibilityPreferences,
    onAccessibilityPreferencesChange: (AppAccessibilityPreferences) -> Unit,
    onBackToProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag("standalone-settings"),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onBackToProjects,
                        modifier = Modifier.testTag("settings-back-to-projects")
                    ) {
                        Text("‹ Projects")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Device settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Available without opening a project",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlobalHeaderActions(
                        onOpenSettings = {},
                        settingsSelected = true
                    )
                }
            }
        }
    ) { innerPadding ->
        ComputeSettingsPanel(
            visualTheme = visualTheme,
            onVisualThemeChange = onVisualThemeChange,
            accessibilityPreferences = accessibilityPreferences,
            onAccessibilityPreferencesChange = onAccessibilityPreferencesChange,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ProjectWorkspaceShell(
    project: ResearchProject,
    route: ProjectRoute,
    summary: ProjectWorkspaceSummary,
    robotLabContent: @Composable (Modifier, TutorialTargetId?) -> Unit,
    visualTheme: AppVisualThemePreferences,
    onVisualThemeChange: (AppVisualThemePreferences) -> Unit,
    accessibilityPreferences: AppAccessibilityPreferences,
    onAccessibilityPreferencesChange: (AppAccessibilityPreferences) -> Unit,
    hasActiveResearchProcess: Boolean,
    previousRoute: ProjectRoute?,
    onRouteChange: (ProjectRoute) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenProjectLevel: (ProjectRoute) -> Unit,
    onCloseProject: () -> Unit,
    requestedTrainingRunId: String?,
    onTrainingRunRequestConsumed: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val continuousCoordinator = remember(context, project.id) {
        ContinuousDatasetGenerationCoordinator(context)
    }
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val routeStateHolder = rememberSaveableStateHolder()
    val continuousProcessId = remember(project.id) { ResearchProcessIds.continuousDataset(project.id) }
    val continuousState by continuousCoordinator.state.collectAsState()
    DisposableEffect(continuousCoordinator, continuousProcessId) {
        onDispose {
            continuousCoordinator.close()
            processCoordinator.snapshot(continuousProcessId)
                ?.takeIf { it.status.isActive }
                ?.let {
                    processCoordinator
                        .beginExternal(
                            id = continuousProcessId,
                            title = it.title,
                            kind = it.kind,
                            stage = it.stage,
                            detail = it.detail
                        )
                        .paused("Continuous dataset growth stopped safely when its project was closed.")
                }
        }
    }
    var datasetEntrySection by rememberSaveable(project.id) {
        mutableStateOf(DatasetBuilderSection.CREATE)
    }
    var trainingEntryMode by rememberSaveable(project.id) {
        mutableStateOf(TrainingLabMode.CLOSED_LOOP_AUTOMATION)
    }
    var trainingEntryRunId by rememberSaveable(project.id) { mutableStateOf<String?>(null) }
    var workspaceEntryRobotId by rememberSaveable(project.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(requestedTrainingRunId) {
        requestedTrainingRunId?.let { runId ->
            trainingEntryRunId = runId
            trainingEntryMode = TrainingLabMode.RESULT_COMPARISON
            if (route != ProjectRoute.TRAINING) onRouteChange(ProjectRoute.TRAINING)
            onTrainingRunRequestConsumed()
        }
    }

    LaunchedEffect(continuousState, continuousProcessId) {
        when (continuousState.status) {
            ContinuousDatasetStatus.PREPARING,
            ContinuousDatasetStatus.RUNNING,
            ContinuousDatasetStatus.THROTTLED,
            ContinuousDatasetStatus.PAUSING -> {
                val reporter =
                    processCoordinator.beginExternal(
                        id = continuousProcessId,
                        title = "Continuous dataset · ${continuousState.plan?.datasetName ?: project.name}",
                        kind = ResearchProcessKind.DATASET,
                        stage = "Preparing continuous generation",
                        detail = continuousState.message,
                        cancellationAction = continuousCoordinator::pause
                    )
                when (continuousState.status) {
                    ContinuousDatasetStatus.PAUSING -> reporter.pausing(continuousState.message)
                    ContinuousDatasetStatus.THROTTLED ->
                        reporter.report(
                            progressFraction = continuousState.currentBatchProgress?.fraction?.toDouble(),
                            stage = "Cooling safely",
                            detail = continuousState.message
                        )
                    else ->
                        reporter.report(
                            progressFraction = continuousState.currentBatchProgress?.fraction?.toDouble(),
                            stage =
                                continuousState.currentBatchProgress?.let {
                                    "Batch progress · ${it.addedRows}/${it.requestedRows} rows"
                                } ?: "Preparing next atomic batch",
                            detail = continuousState.message
                        )
                }
            }

            ContinuousDatasetStatus.PAUSED ->
                processCoordinator.snapshot(continuousProcessId)
                    ?.takeIf { it.status.isActive }
                    ?.let {
                        processCoordinator
                            .beginExternal(
                                id = continuousProcessId,
                                title = it.title,
                                kind = it.kind,
                                stage = it.stage,
                                detail = it.detail
                            )
                            .paused(continuousState.message)
                    }

            ContinuousDatasetStatus.ERROR ->
                processCoordinator.snapshot(continuousProcessId)
                    ?.takeIf { it.status.isActive }
                    ?.let {
                        processCoordinator
                            .beginExternal(
                                id = continuousProcessId,
                                title = it.title,
                                kind = it.kind,
                                stage = it.stage,
                                detail = it.detail
                            )
                            .failed(continuousState.message)
                    }

            ContinuousDatasetStatus.IDLE,
            ContinuousDatasetStatus.INTERRUPTED -> Unit
        }
    }

    fun navigate(destination: ProjectRoute) {
        if (destination != ProjectRoute.SETTINGS) {
            workspaceEntryRobotId = null
        }
        onRouteChange(destination)
    }

    fun returnFromSettings() {
        if (previousRoute != ProjectRoute.WORKSPACE_3D) {
            workspaceEntryRobotId = null
        }
        onNavigateBack()
    }

    fun openDataset(section: DatasetBuilderSection) {
        datasetEntrySection = section
        navigate(ProjectRoute.DATASET_BUILDER)
    }

    fun openTraining(mode: TrainingLabMode) {
        trainingEntryMode = mode
        trainingEntryRunId = null
        navigate(ProjectRoute.TRAINING)
    }

    fun openRobotWorkspace(robotId: String) {
        workspaceEntryRobotId = robotId
        onRouteChange(ProjectRoute.WORKSPACE_3D)
    }

    fun returnToRobotCard() {
        datasetEntrySection = DatasetBuilderSection.ROBOTS
        workspaceEntryRobotId = null
        onNavigateBack()
    }

    BackHandler(
        enabled = route == ProjectRoute.WORKSPACE_3D && workspaceEntryRobotId != null,
        onBack = ::returnToRobotCard
    )
    BackHandler(
        enabled = route == ProjectRoute.SETTINGS,
        onBack = ::returnFromSettings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("project-workspace"),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            ProjectContextBar(
                project = project,
                route = route,
                continuousState = continuousState,
                onProjects = onCloseProject,
                onProjectOverview = { onOpenProjectLevel(ProjectRoute.HOME) },
                onSectionRoot = { onOpenProjectLevel(route.section().rootRoute) },
                onSettings = { navigate(ProjectRoute.SETTINGS) },
                settingsSelected = route == ProjectRoute.SETTINGS,
                onSectionBack =
                    if (route == ProjectRoute.SETTINGS) {
                        ::returnFromSettings
                    } else if (route == ProjectRoute.WORKSPACE_3D && workspaceEntryRobotId != null) {
                        ::returnToRobotCard
                    } else {
                        previousRoute?.let { { onNavigateBack() } }
                    },
                sectionBackLabel =
                    if (route == ProjectRoute.WORKSPACE_3D && workspaceEntryRobotId != null) {
                        "robot card"
                    } else {
                        previousRoute?.displayName() ?: route.section().label
                    }
            )
        },
        bottomBar = {
            val navigationFontScale = LocalDensity.current.fontScale
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useCompactLabels =
                    useCompactProjectBottomNavigation(
                        availableWidthDp = maxWidth.value,
                        fontScale = navigationFontScale
                    )
                NavigationBar(
                    modifier = Modifier.fillMaxWidth(),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    ProjectSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = route.section() == section,
                            onClick = { onOpenProjectLevel(section.rootRoute) },
                            modifier =
                                Modifier
                                    .testTag("bottom-navigation:${section.rootRoute.name.lowercase()}")
                                    .semantics { contentDescription = section.label }
                                    .tutorialAnchor(section.tutorialTarget),
                            icon = { Text(section.symbol, fontWeight = FontWeight.Black) },
                            label = {
                                Text(
                                    text = if (useCompactLabels) section.compactLabel else section.label,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .padding(bottom = if (hasActiveResearchProcess) 78.dp else 0.dp)
                .then(
                    route.tutorialTarget()?.let { target ->
                        Modifier.tutorialAnchor(target)
                    } ?: Modifier
                )

        routeStateHolder.SaveableStateProvider(route.name) {
        when (route) {
            ProjectRoute.HOME ->
                ProjectHomeScreen(
                    project = project,
                    summary = summary,
                    onRobotLab = { navigate(ProjectRoute.ROBOT_LAB) },
                    onWorkspace = { navigate(ProjectRoute.WORKSPACE_3D) },
                    onDataset = { openDataset(DatasetBuilderSection.CREATE) },
                    onDiagnostics = { navigate(ProjectRoute.DIAGNOSTICS) },
                    onTraining = { openTraining(TrainingLabMode.CLOSED_LOOP_AUTOMATION) },
                    onStorage = { navigate(ProjectRoute.STORAGE) },
                    modifier = contentModifier
                )

            ProjectRoute.PREPARE ->
                ProjectSectionHub(
                    eyebrow = "PREPARE THE EVIDENCE",
                    title = "Build a valid experiment",
                    description = "Define the mechanism first, inspect its physical workspace, then create reproducible data.",
                    destinations =
                        prepareDestinations(summary) { destination ->
                            if (destination == ProjectRoute.DATASET_BUILDER) {
                                openDataset(DatasetBuilderSection.CREATE)
                            } else {
                                navigate(destination)
                            }
                        },
                    showJargonHelp = true,
                    modifier = contentModifier
                )

            ProjectRoute.EXPERIMENT ->
                ProjectSectionHub(
                    eyebrow = "RUN AND EVALUATE",
                    title = "Scientific experiments",
                    description = "Training and diagnostics are separated so model performance never hides numerical or solver failure.",
                    destinations =
                        experimentDestinations(summary) { destination ->
                            if (destination == ProjectRoute.TRAINING) {
                                openTraining(TrainingLabMode.CLOSED_LOOP_AUTOMATION)
                            } else {
                                navigate(destination)
                            }
                        },
                    modifier = contentModifier
                )

            ProjectRoute.LIBRARY ->
                ProjectSectionHub(
                    eyebrow = "REVIEW AND CONTROL",
                    title = "Project library",
                    description = "Find stored evidence or change device-level behaviour without crowding the experiment screens.",
                    destinations = libraryDestinations(summary, ::navigate),
                    modifier = contentModifier
                )

            ProjectRoute.ROBOT_LAB -> robotLabContent(contentModifier, null)
            ProjectRoute.WORKSPACE_3D ->
                RobotWorkspacePanel(
                    modifier = contentModifier,
                    initialLibraryRobotId = workspaceEntryRobotId,
                    onReturnToRobot = workspaceEntryRobotId?.let { { returnToRobotCard() } }
                )
            ProjectRoute.DATASET_BUILDER ->
                DatasetBuilderPanel(
                    continuousCoordinator = continuousCoordinator,
                    onOpenRobotWorkspace = ::openRobotWorkspace,
                    modifier = contentModifier,
                    initialSection = datasetEntrySection,
                    onSectionChanged = { datasetEntrySection = it }
                )
            ProjectRoute.DIAGNOSTICS ->
                Layer1DiagnosticPanel(
                    modifier = contentModifier,
                    tutorialTarget = null
                )
            ProjectRoute.TRAINING ->
                LocalTrainingPanel(
                    onOpenDatasetQuality = { openDataset(DatasetBuilderSection.QUALITY) },
                    modifier = contentModifier,
                    initialMode = trainingEntryMode,
                    onModeChanged = { trainingEntryMode = it },
                    initialTrainingRunId = trainingEntryRunId,
                    tutorialTarget = null
                )
            ProjectRoute.STORAGE ->
                StorageCenterPanel(
                    modifier = contentModifier,
                    onOpenRobots = { openDataset(DatasetBuilderSection.ROBOTS) },
                    onOpenDatasets = { openDataset(DatasetBuilderSection.SAVED) },
                    onOpenDiagnostics = { navigate(ProjectRoute.DIAGNOSTICS) },
                    onOpenWorkspace = { navigate(ProjectRoute.WORKSPACE_3D) },
                    onOpenModels = { openTraining(TrainingLabMode.RESULT_COMPARISON) },
                    onOpenTraining = { openTraining(TrainingLabMode.CONTROLLED_SINGLE_RUN) }
                )
            ProjectRoute.SETTINGS ->
                ComputeSettingsPanel(
                    visualTheme = visualTheme,
                    onVisualThemeChange = onVisualThemeChange,
                    accessibilityPreferences = accessibilityPreferences,
                    onAccessibilityPreferencesChange = onAccessibilityPreferencesChange,
                    modifier = contentModifier
                )
        }
        }
    }
}

@Composable
private fun ProjectContextBar(
    project: ResearchProject,
    route: ProjectRoute,
    continuousState: ContinuousDatasetState,
    onProjects: () -> Unit,
    onProjectOverview: () -> Unit,
    onSectionRoot: () -> Unit,
    onSettings: () -> Unit,
    settingsSelected: Boolean,
    onSectionBack: (() -> Unit)?,
    sectionBackLabel: String
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val accessibilityPreferences = LocalAppAccessibilityPreferences.current
    val readAloudController = LocalReadAloudController.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("project-context")
                .tutorialAnchor(TutorialTargets.ProjectContext),
        color = colors.surface.copy(alpha = 0.98f),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = onProjects, modifier = Modifier.testTag("back-to-projects")) {
                    Text("‹ Projects")
                }
                Column(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = onProjectOverview,
                        modifier = Modifier.testTag("project-context-overview")
                    ) {
                        Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onSectionRoot,
                        enabled = route != route.section().rootRoute,
                        modifier = Modifier.testTag("project-context-section")
                    ) {
                        Text(
                            text = route.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                GlobalHeaderActions(
                    onOpenSettings = onSettings,
                    settingsSelected = settingsSelected
                )
            }
            if (onSectionBack != null) {
                TextButton(
                    onClick = onSectionBack,
                    modifier = Modifier.testTag("project-context-section-back")
                ) {
                    Text("← Back to $sectionBackLabel")
                }
            }
            if (accessibilityPreferences.readAloudEnabled) {
                TextButton(
                    onClick = {
                        when (
                            readAloudController.speak(
                                text = route.readAloudGuide(project.name),
                                speechRate = accessibilityPreferences.speechRate
                            )
                        ) {
                            ReadAloudResult.STARTED -> Unit
                            ReadAloudResult.INITIALIZING ->
                                Toast.makeText(
                                    context,
                                    "The speech engine is starting. Try again in a moment.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            ReadAloudResult.UNAVAILABLE ->
                                Toast.makeText(
                                    context,
                                    "No compatible speech voice is available on this device.",
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("read-screen-aloud")
                ) {
                    Text("🔊 Read this screen guide aloud")
                }
            }
            if (continuousState.isActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("continuous-dataset-global-status"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text =
                            when (continuousState.status) {
                                ContinuousDatasetStatus.THROTTLED ->
                                    "DATASET COOLING · ${continuousState.committedDatasetRows} rows · +${continuousState.rowsAddedThisSession} this session"
                                ContinuousDatasetStatus.PAUSING ->
                                    "DATASET PAUSING · protecting the active batch"
                                else ->
                                    "DATASET RUNNING · ${continuousState.committedDatasetRows} rows · +${continuousState.rowsAddedThisSession} this session"
                            },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

private fun ProjectRoute.readAloudGuide(projectName: String): String =
    when (this) {
        ProjectRoute.HOME ->
            "Project home for $projectName. Choose Prepare to define robots and datasets, Experiment to run diagnostics and training, or Library to review saved evidence and settings."
        ProjectRoute.PREPARE ->
            "Prepare section for $projectName. Define the robot, inspect its three dimensional workspace, then create reproducible datasets."
        ProjectRoute.EXPERIMENT ->
            "Experiment section for $projectName. Run diagnostics, train local models, and compare their scientific results."
        ProjectRoute.LIBRARY ->
            "Library section for $projectName. Review stored robots, datasets, workspace studies, models, and application settings."
        ProjectRoute.ROBOT_LAB ->
            "Robot Lab. Define or select a robot, choose forward or inverse kinematics, and inspect the resulting pose and numerical evidence."
        ProjectRoute.WORKSPACE_3D ->
            "Three dimensional robot workspace. Select a saved robot and study its reachable workspace, boundary, sampled dead space, and animated construction."
        ProjectRoute.DATASET_BUILDER ->
            "Dataset Factory. Select validated robots, configure sampling and solver settings, generate or extend datasets, and inspect their quality."
        ProjectRoute.DIAGNOSTICS ->
            "Diagnostics. Configure a reproducible benchmark, watch live performance and numerical telemetry, then inspect the resulting scientific charts."
        ProjectRoute.TRAINING ->
            "AI Training. Select datasets, feature profiles, model candidates, tolerances, and comparison settings before starting local training."
        ProjectRoute.STORAGE ->
            "Storage. Review project-owned robots, datasets, diagnostic sessions, workspace evidence, models, and telemetry with direct access to each area."
        ProjectRoute.SETTINGS ->
            "Settings. Configure accessibility, appearance, chart presentation, the research glossary, processor workers, and safe working memory limits."
    }

private fun prepareDestinations(
    summary: ProjectWorkspaceSummary,
    navigate: (ProjectRoute) -> Unit
): List<ProjectDestination> =
    listOf(
        ProjectDestination("ROBOT", "Robot Lab", "Define DH parameters, joint types and limits, then inspect FK or IK behaviour.", if (summary.robotReady) "READY" else "SET UP", { navigate(ProjectRoute.ROBOT_LAB) }, TutorialTargets.RobotLab),
        ProjectDestination("SPACE", "3D Workspace", "Visualize reach, angular restrictions, boundaries and internal dead space.", "EXPLORE", { navigate(ProjectRoute.WORKSPACE_3D) }, TutorialTargets.Workspace),
        ProjectDestination("DATA", "Dataset Factory", "Generate, extend and validate reproducible datasets from selected robots.", "${summary.datasetCount} SAVED", { navigate(ProjectRoute.DATASET_BUILDER) }, TutorialTargets.Dataset)
    )

private fun experimentDestinations(
    summary: ProjectWorkspaceSummary,
    navigate: (ProjectRoute) -> Unit
): List<ProjectDestination> =
    listOf(
        ProjectDestination("RELIABILITY", "Diagnostics", "Benchmark solver recovery, seeds, topology, numerical safety and resource behaviour.", "${summary.diagnosticSessionCount} SESSIONS", { navigate(ProjectRoute.DIAGNOSTICS) }, TutorialTargets.Diagnostics),
        ProjectDestination("MODELLING", "AI Training & Comparison", "Train feature profiles, compare models and inspect explainable-AI evidence.", "${summary.trainingRunCount} RUNS", { navigate(ProjectRoute.TRAINING) }, TutorialTargets.Training)
    )

private fun libraryDestinations(
    summary: ProjectWorkspaceSummary,
    navigate: (ProjectRoute) -> Unit
): List<ProjectDestination> =
    listOf(
        ProjectDestination("ARTIFACTS", "Storage & Evidence", "Review saved robots, sessions, datasets, models and reproducibility files.", "${summary.modelCount} MODELS", { navigate(ProjectRoute.STORAGE) }, TutorialTargets.Storage),
        ProjectDestination("DEVICE", "Settings", "Control safe compute resources, memory policy and the visual theme.", "LOCAL", { navigate(ProjectRoute.SETTINGS) }, TutorialTargets.Settings)
    )

private fun ProjectRoute.tutorialTarget(): TutorialTargetId? =
    when (this) {
        ProjectRoute.ROBOT_LAB -> TutorialTargets.RobotLab
        ProjectRoute.WORKSPACE_3D -> TutorialTargets.Workspace
        ProjectRoute.DATASET_BUILDER -> TutorialTargets.Dataset
        ProjectRoute.DIAGNOSTICS -> TutorialTargets.Diagnostics
        ProjectRoute.TRAINING -> TutorialTargets.Training
        ProjectRoute.STORAGE -> TutorialTargets.Storage
        ProjectRoute.SETTINGS -> TutorialTargets.Settings
        ProjectRoute.HOME,
        ProjectRoute.PREPARE,
        ProjectRoute.EXPERIMENT,
        ProjectRoute.LIBRARY -> null
    }

private fun loadProjectSummary(context: android.content.Context): ProjectWorkspaceSummary {
    val appStorage = AppStorageRepository(context)
    val snapshot = appStorage.snapshot()
    val modelCount = snapshot.categories.firstOrNull { it.category == StorageCategory.MODELS }?.fileCount ?: 0
    return ProjectWorkspaceSummary(
        robotReady = appStorage.loadRobotLabState() != null,
        robotCount = RobotLibraryRepository(context).loadOrCreateDefaults().size,
        diagnosticSessionCount = snapshot.diagnosticSessions.size,
        datasetCount = DatasetStorageRepository(context).listManifests().size,
        trainingRunCount = TrainingStorageRepository(context).listRuns().size,
        modelCount = modelCount
    )
}

private fun ProjectRoute.section(): ProjectSection =
    when (this) {
        ProjectRoute.HOME -> ProjectSection.HOME
        ProjectRoute.PREPARE,
        ProjectRoute.ROBOT_LAB,
        ProjectRoute.WORKSPACE_3D,
        ProjectRoute.DATASET_BUILDER -> ProjectSection.PREPARE
        ProjectRoute.EXPERIMENT,
        ProjectRoute.DIAGNOSTICS,
        ProjectRoute.TRAINING -> ProjectSection.EXPERIMENT
        ProjectRoute.LIBRARY,
        ProjectRoute.STORAGE,
        ProjectRoute.SETTINGS -> ProjectSection.LIBRARY
    }

private fun ProjectRoute.parentRoute(): ProjectRoute = section().rootRoute

private fun ProjectRoute.isSectionRoot(): Boolean =
    this == ProjectRoute.HOME ||
        this == ProjectRoute.PREPARE ||
        this == ProjectRoute.EXPERIMENT ||
        this == ProjectRoute.LIBRARY

private fun ProjectRoute.displayName(): String =
    when (this) {
        ProjectRoute.HOME -> "Project overview"
        ProjectRoute.PREPARE -> "Prepare"
        ProjectRoute.EXPERIMENT -> "Experiment"
        ProjectRoute.LIBRARY -> "Library"
        ProjectRoute.ROBOT_LAB -> "Prepare / Robot Lab"
        ProjectRoute.WORKSPACE_3D -> "Prepare / 3D Workspace"
        ProjectRoute.DATASET_BUILDER -> "Prepare / Dataset Factory"
        ProjectRoute.DIAGNOSTICS -> "Experiment / Diagnostics"
        ProjectRoute.TRAINING -> "Experiment / AI Training"
        ProjectRoute.STORAGE -> "Library / Storage & Evidence"
        ProjectRoute.SETTINGS -> "Library / Settings"
    }

@Composable
fun SelectableNavigationButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
        shape = RoundedCornerShape(15.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true
            )
        }
    }
}
