package com.robotkinematicslab.mobile

import android.os.Bundle
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.record.ExecutionRecord
import com.robotkinematicslab.mobile.domain.record.OperationType
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.ui.robotview.*
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.domain.result.SolverMetadata
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.math.utility.Workspace
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkStorageRepository
import com.robotkinematicslab.mobile.ml.ik.StoredOneMicronIkModel
import com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath
import com.robotkinematicslab.mobile.ml.ik.VerifiedOneMicronIkEngine
import com.robotkinematicslab.mobile.render.RobotScene3D
import com.robotkinematicslab.mobile.render.RobotSceneLegend
import com.robotkinematicslab.mobile.render.robotWorkspaceRadius
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.service.Layer1DebugFormatter
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.RobotLabSavedState
import com.robotkinematicslab.mobile.storage.bundled.BundledResearchPackInstaller
import com.robotkinematicslab.mobile.ui.editor.DhInputRow
import com.robotkinematicslab.mobile.ui.editor.RobotEditorMapper
import com.robotkinematicslab.mobile.ui.editor.RobotEditorPanel
import com.robotkinematicslab.mobile.ui.editor.RobotEditorState
import com.robotkinematicslab.mobile.ui.editor.RobotSetupLibraryPanel
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.accessibility.AndroidReadAloudController
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityRepository
import com.robotkinematicslab.mobile.ui.accessibility.ProvideAppAccessibility
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import com.robotkinematicslab.mobile.ui.theme.AppVisualThemeRepository
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationController
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationRepository
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideChartPresentation
import com.robotkinematicslab.mobile.ui.launch.AppLaunchScreen
import com.robotkinematicslab.mobile.ui.navigation.Layer1AppRoot
import com.robotkinematicslab.mobile.ui.navigation.SelectableNavigationButton
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialActionReport
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.d(TAG) { "🚀 MainActivity.onCreate started | savedInstanceStateExists=${savedInstanceState != null}" }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                BundledResearchPackInstaller(applicationContext).installIfNeeded()
            }.onSuccess { result ->
                AppLog.d(TAG) {
                    "📦 Bundled research pack ready | installed=${result.installedArtifacts}, reused=${result.reusedArtifacts}"
                }
            }.onFailure { error ->
                AppLog.e(TAG) { "Bundled research pack import failed safely | error=${error.message}" }
            }
        }

        enableEdgeToEdge()
        AppLog.d(TAG) { "🖼️ Edge-to-edge mode enabled" }

        // Compose's platform scroll-capture callback can race a rapidly detached layout node
        // while Android is taking a long screenshot. The app already owns explicit scientific
        // chart/data exports, so opting this dynamic hierarchy out is safer than allowing a
        // system capture to terminate the whole process (observed on API 35 during stress testing).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.decorView.scrollCaptureHint =
                View.SCROLL_CAPTURE_HINT_EXCLUDE or
                    View.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS
        }

        setContent {
            AppLog.d(TAG) { "🎨 setContent invoked" }
            val context = LocalContext.current.applicationContext
            val visualThemeRepository = remember(context) { AppVisualThemeRepository(context) }
            var visualTheme by remember { mutableStateOf(visualThemeRepository.load()) }
            // This is a launch surface, not onboarding: show it once per live task and preserve
            // the choice across configuration changes. A genuinely new cold start shows it again.
            var launchVisible by rememberSaveable { mutableStateOf(true) }
            val accessibilityRepository = remember(context) { AppAccessibilityRepository(context) }
            var accessibilityPreferences by remember {
                mutableStateOf(accessibilityRepository.load())
            }
            SideEffect {
                val darkSurfaceIsVisible = launchVisible
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkSurfaceIsVisible
                    isAppearanceLightNavigationBars = !darkSurfaceIsVisible
                }
            }
            val readAloudController = remember(context) { AndroidReadAloudController(context) }
            DisposableEffect(readAloudController) {
                onDispose(readAloudController::shutdown)
            }
            val chartPresentationRepository = remember(context) { ChartPresentationRepository(context) }
            var chartPresentation by remember { mutableStateOf(chartPresentationRepository.load()) }
            ProvideAppAccessibility(
                preferences = accessibilityPreferences,
                readAloudController = readAloudController
            ) {
                ProvideChartPresentation(
                    controller =
                        ChartPresentationController(
                            preferences = chartPresentation,
                            update = { selected ->
                                chartPresentation = chartPresentationRepository.save(selected)
                            }
                        )
                ) {
                    RobotKinematicsLabTheme(
                        preferences = visualTheme,
                        accessibilityPreferences = accessibilityPreferences
                    ) {
                        AppLog.d(TAG) { "🎭 RobotKinematicsLabTheme applied" }
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background
                        ) { innerPadding ->
                            AppLog.d(TAG) { "🧱 Scaffold content composed | innerPadding=$innerPadding" }
                            if (launchVisible) {
                                AppLaunchScreen(
                                    onEnterProjects = { launchVisible = false }
                                )
                            } else {
                                Layer1AppRoot(
                                    robotLabContent = { contentModifier, tutorialTarget ->
                                        InteractiveFkDemo(
                                            modifier = contentModifier,
                                            tutorialTarget = tutorialTarget
                                        )
                                    },
                                    visualTheme = visualTheme,
                                    onVisualThemeChange = { selected ->
                                        visualTheme = visualThemeRepository.save(selected)
                                    },
                                    accessibilityPreferences = accessibilityPreferences,
                                    onAccessibilityPreferencesChange = { selected ->
                                        accessibilityPreferences = accessibilityRepository.save(selected)
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
            }
        }

        AppLog.d(TAG) { "✅ MainActivity.onCreate finished" }
    }
}

enum class ControlMode { FK, IK }

enum class Layer1Screen {
    ROBOT_LAB_HOME,
    ROBOT_SETUP,
    ROBOT_VIEW
}

fun sanitizeJointValue(value: Double, joint: JointDefinition): Double {
    val tag = "MainActivity"

    AppLog.d(tag) {
        "🧼 sanitizeJointValue called | joint=${joint.name}, type=${joint.type}, rawValue=$value, limits=[${joint.minValue}, ${joint.maxValue}], homeValue=${joint.homeValue}"
    }

    if (!value.isFinite()) {
        AppLog.w(tag) {
            "⚠️ Non-finite joint value detected, using home value | joint=${joint.name}, rawValue=$value, fallback=${joint.homeValue}"
        }
        return joint.homeValue
    }

    val sanitized = value.coerceIn(joint.minValue, joint.maxValue)

    AppLog.d(tag) {
        "✅ sanitizeJointValue finished | joint=${joint.name}, sanitizedValue=$sanitized"
    }

    return sanitized
}

@Composable
fun InteractiveFkDemo(
    modifier: Modifier = Modifier,
    tutorialTarget: TutorialTargetId? = null
) {
    val tag = "MainActivity"
    val context = LocalContext.current.applicationContext
    val tutorialReporter = LocalTutorialActionReporter.current

    AppLog.d(tag) { "🎬 InteractiveFkDemo composition started" }

    val kinematicsService = remember {
        AppLog.d(tag) { "🧠 Creating KinematicsService" }
        KinematicsService()
    }

    val oneMicronRepository = remember(context) { OneMicronIkStorageRepository(context) }
    var selectedOneMicronModel by remember { mutableStateOf<StoredOneMicronIkModel?>(null) }
    var selectedOneMicronModelKey by remember { mutableStateOf<String?>(null) }
    var modelRefreshRevision by remember { mutableIntStateOf(0) }
    var modelLoading by remember { mutableStateOf(true) }
    var useVerifiedOneMicronAi by rememberSaveable { mutableStateOf(false) }
    var ikState by remember { mutableStateOf(RobotViewIkState()) }

    val debugFormatter = remember {
        AppLog.d(tag) { "🧠 Creating Layer1DebugFormatter" }
        Layer1DebugFormatter()
    }

    val robotEditorMapper = remember {
        AppLog.d(tag) { "🧠 Creating RobotEditorMapper" }
        RobotEditorMapper()
    }

    val appStorageRepository = remember(context) {
        AppStorageRepository(context)
    }

    val robotLibraryRepository = remember(context) {
        RobotLibraryRepository(context)
    }
    val robotLibraryRevision by robotLibraryRepository.observeChanges().collectAsState()
    val robotLibraryRobots = remember(robotLibraryRepository, robotLibraryRevision) {
        robotLibraryRepository.loadOrCreateDefaults()
    }

    val restoredRobotLabState = remember(appStorageRepository) {
        appStorageRepository.loadRobotLabState()
    }

    val demoRobotState = remember {
        RobotEditorState(
            robotName = "Demo Robot",
            dhRows = listOf(
                DhInputRow(
                    jointTypeText = "REVOLUTE",
                    thetaText = "0",
                    dText = "300",
                    aText = "0",
                    alphaText = "90",
                    minText = "-180",
                    maxText = "180",
                    homeText = "0"
                ),
                DhInputRow(
                    jointTypeText = "REVOLUTE",
                    thetaText = "0",
                    dText = "0",
                    aText = "500",
                    alphaText = "0",
                    minText = "-180",
                    maxText = "180",
                    homeText = "0"
                ),
                DhInputRow(
                    jointTypeText = "REVOLUTE",
                    thetaText = "0",
                    dText = "0",
                    aText = "400",
                    alphaText = "0",
                    minText = "-180",
                    maxText = "180",
                    homeText = "0"
                )
            )
        )
    }

    var currentScreen by rememberSaveable {
        mutableStateOf(Layer1Screen.ROBOT_LAB_HOME)
    }

    var editorState by rememberSaveable(stateSaver = RobotEditorState.Saver) {
        AppLog.d(tag) { "📝 Initializing editable RobotEditorState" }
        mutableStateOf(
            restoredRobotLabState?.robot
                ?.let(robotEditorMapper::toEditorState)
                ?: demoRobotState
        )
    }

    var editorBaselineState by rememberSaveable(stateSaver = RobotEditorState.Saver) {
        mutableStateOf(editorState)
    }

    var appliedEditorState by rememberSaveable(stateSaver = RobotEditorState.Saver) {
        AppLog.d(tag) { "📌 Initializing applied RobotEditorState" }
        mutableStateOf(
            restoredRobotLabState?.robot
                ?.let(robotEditorMapper::toEditorState)
                ?: demoRobotState
        )
    }

    val restoredLibraryRobotId =
        remember(robotLibraryRobots, restoredRobotLabState?.robot) {
            robotLibraryRobots.firstOrNull { saved ->
                saved.robot == restoredRobotLabState?.robot
            }?.id
        }
    var selectedLibraryRobotId by rememberSaveable {
        mutableStateOf<String?>(restoredLibraryRobotId)
    }
    var editorSourceRobotId by rememberSaveable {
        mutableStateOf<String?>(restoredLibraryRobotId)
    }
    var activeLibraryRobotId by rememberSaveable {
        mutableStateOf<String?>(restoredLibraryRobotId)
    }
    var robotLibraryMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var mode by remember {
        AppLog.d(tag) { "🎛️ Initializing mode state | initialMode=${ControlMode.IK}" }
        mutableStateOf(
            restoredRobotLabState?.controlMode
                ?.let { runCatching { ControlMode.valueOf(it) }.getOrNull() }
                ?: ControlMode.IK
        )
    }

    fun selectRobotLabScreen(screen: Layer1Screen) {
        if (screen != currentScreen) {
            ikState = ikState.invalidate("Robot View changed. No previous IK result certifies a new request.")
            currentScreen = screen
        }
    }
    fun selectControlMode(selected: ControlMode) {
        if (selected != mode) {
            ikState = ikState.invalidate("Kinematics mode changed. Previous IK verification was cleared.")
            mode = selected
        }
    }

    var tutorialModeRevealGeneration by remember { mutableLongStateOf(0L) }
    var tutorialModeRevealed by remember { mutableStateOf<ControlMode?>(null) }
    var saveRevealGenerationConsumed by remember { mutableLongStateOf(0L) }
    var ikRevealGenerationConsumed by remember { mutableLongStateOf(0L) }

    // A tutorial hint may reveal an existing surface, but it must never submit an editor,
    // run kinematics, or otherwise mutate the user's research data.
    LaunchedEffect(tutorialTarget) {
        val requestedScreen = robotLabScreenForTutorialTarget(tutorialTarget)
        val requestedMode = robotLabControlModeForTutorialTarget(tutorialTarget)
        if ((requestedScreen != null && requestedScreen != currentScreen) ||
            (requestedMode != null && requestedMode != mode)) {
            tutorialModeRevealGeneration += 1L
            tutorialModeRevealed = requestedMode ?: mode
            requestedScreen?.let(::selectRobotLabScreen)
            requestedMode?.let(::selectControlMode)
        }
    }

    val lastIkStatus = ikState.response?.result?.status
        ?: if (ikState.phase == RobotViewIkPhase.FAILED) IKStatus.NUMERICAL_FAILURE else IKStatus.MAX_ITERATIONS_REACHED
    val hasIkRun = ikState.hasResult
    val lastIkIterations = ikState.response?.result?.iterations ?: 0
    val lastIkFinalError = ikState.checkedErrorMeters ?: Double.NaN
    val lastIkDetailCode = ikState.response?.result?.detailCode ?: IKDetailCode.NONE
    val lastIkMetadata = ikState.response?.result?.metadata
    val lastIkElapsedMs = ikState.response?.elapsedMillis ?: Double.NaN

    val editorBuildResult = remember(editorState) {
        AppLog.d(tag) { "🧪 Building RobotDefinition from current editor state" }
        robotEditorMapper.buildRobotDefinition(editorState)
    }

    val robotBuildResult = remember(appliedEditorState) {
        AppLog.d(tag) { "🏗️ Building RobotDefinition from applied editor state" }
        robotEditorMapper.buildRobotDefinition(appliedEditorState)
    }

    val robot = robotBuildResult.robot
    LaunchedEffect(modelRefreshRevision, robot?.joints?.size) {
        modelLoading = true
        ikState = ikState.invalidate("Checking AI availability. Previous IK verification was cleared.")
        val loaded = withContext(Dispatchers.IO) {
            oneMicronRepository.listModelFiles().firstNotNullOfOrNull { file ->
                runCatching {
                    fun digest(): String {
                        val hash = java.security.MessageDigest.getInstance("SHA-256")
                        file.inputStream().buffered().use { input ->
                            val buffer = ByteArray(65536)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                hash.update(buffer, 0, count)
                            }
                        }
                        return hash.digest().joinToString("") { "%02x".format(it) }
                    }
                    val identity = digest()
                    val model = oneMicronRepository.loadModel(file)
                    check(identity == digest()) { "AI model changed while loading" }
                    model to "${model.runId}:$identity"
                }.getOrNull()
            }
        }
        selectedOneMicronModel = loaded?.first
        selectedOneMicronModelKey = loaded?.second
        modelLoading = false
        if (loaded == null) useVerifiedOneMicronAi = false
    }
    val aiAvailability = robotViewAiAvailability(modelLoading, selectedOneMicronModel?.model?.outputCount, robot?.joints?.size)
    val effectiveAiEnabled = useVerifiedOneMicronAi && aiAvailability.available
    val effectiveAiModelKey = selectedOneMicronModelKey.takeIf { effectiveAiEnabled }


    AppLog.d(tag) {
        "📦 Applied robot build result in composition | success=${robotBuildResult.isSuccess}, errors=${robotBuildResult.errors}"
    }

    val jointTypes: List<JointType> = remember(robot) {
        robot?.joints?.map { it.type } ?: emptyList()
    }
    val sceneWorkspaceRadius = remember(robot) {
        robotWorkspaceRadius(robot)
    }
    val maximumTargetReach = remember(robot) {
        robot?.let(RobotReachEnvelope::conservativeRadialUpperBound) ?: 0.0
    }

    var joints by remember {
        AppLog.d(tag) { "🦾 Initializing joints state | initialJoints=[0.0, 0.0, 0.0]" }
        mutableStateOf(
            restoredRobotLabState?.jointValues
                ?: listOf(0.0, 0.0, 0.0)
        )
    }

    var targetPoint by remember {
        AppLog.d(tag) { "🎯 Initializing targetPoint state | initialTarget=null" }
        mutableStateOf(restoredRobotLabState?.target)
    }
    var acceptedIkTargetUpdateDuringDrag by remember { mutableStateOf(false) }

    AppLog.d(tag) {
        "📥 Requesting FK result from UI | mode=$mode, joints=$joints, targetPoint=$targetPoint"
    }

    val fkResult = remember(robot, joints) {
        if (robot != null) {
            kinematicsService.computeFK(robot, RobotState(joints))
        } else {
            FKResult(
                status = FKStatus.INVALID_INPUT,
                endEffectorTransform = Matrix4.identity(),
                endEffectorPosition = Vec3.ZERO,
                jointPositions = emptyList(),
                metadata = null
            )
        }
    }

    AppLog.d(tag) {
        "📤 FK result received in UI | status=${fkResult.status}, endEffector=(${fkResult.endEffectorPosition.x}, ${fkResult.endEffectorPosition.y}, ${fkResult.endEffectorPosition.z}), jointPositionsCount=${fkResult.jointPositions.size}"
    }

    val safeJointPositions = if (fkResult.status == FKStatus.SUCCESS || fkResult.status == FKStatus.SUCCESS_WITH_WARNING) {
        AppLog.d(tag) { "✅ Using FK joint positions | count=${fkResult.jointPositions.size}" }
        fkResult.jointPositions
    } else {
        AppLog.w(tag) { "⚠️ FK failed, using empty joint positions | fkStatus=${fkResult.status}" }
        emptyList()
    }

    val safeEndEffector = if (fkResult.status == FKStatus.SUCCESS || fkResult.status == FKStatus.SUCCESS_WITH_WARNING) {
        AppLog.d(tag) {
            "✅ Using FK end effector | position=(${fkResult.endEffectorPosition.x}, ${fkResult.endEffectorPosition.y}, ${fkResult.endEffectorPosition.z})"
        }
        fkResult.endEffectorPosition
    } else {
        AppLog.w(tag) { "⚠️ FK failed, using zero end effector | fkStatus=${fkResult.status}" }
        Vec3.ZERO
    }

    val fkStatusText = remember(fkResult.status) {
        when (fkResult.status) {
            FKStatus.SUCCESS -> "FK output accepted"
            FKStatus.SUCCESS_WITH_WARNING -> "FK output accepted with repair warning"
            FKStatus.INVALID_INPUT -> "FK blocked: invalid robot or joint input"
            FKStatus.NUMERICAL_FAILURE -> "FK rejected: output failed numerical validation"
        }
    }

    val ikStatusText = ikState.message

    val fkStatusColor = remember(fkResult.status) {
        when (fkResult.status) {
            FKStatus.SUCCESS -> Color(0xFF2E7D32)
            FKStatus.SUCCESS_WITH_WARNING -> Color(0xFFFF8F00)
            FKStatus.INVALID_INPUT -> Color(0xFFC62828)
            FKStatus.NUMERICAL_FAILURE -> Color(0xFFFF8F00)
        }
    }

    val ikStatusColor = when (ikState.phase) {
        RobotViewIkPhase.VERIFIED -> Color(0xFF2E7D32)
        RobotViewIkPhase.WARNING -> Color(0xFFFF8F00)
        RobotViewIkPhase.FAILED -> Color(0xFFC62828)
        else -> Color(0xFF546E7A)
    }

    val lastIkIterationsPerSecond = remember(lastIkIterations, lastIkElapsedMs) {
        if (lastIkElapsedMs.isFinite() && lastIkElapsedMs > 0.0 && lastIkIterations > 0) {
            lastIkIterations / (lastIkElapsedMs / 1000.0)
        } else {
            Double.NaN
        }
    }

    val debugText = remember(
        ikState,
        robot?.name,
        joints,
        targetPoint,
        fkResult.status,
        fkResult.metadata,
        lastIkStatus,
        hasIkRun,
        lastIkIterations,
        lastIkFinalError,
        lastIkMetadata,
        mode
    ) {
        if (robot == null) {
            "No applied robot available."
        } else if (mode == ControlMode.IK && !hasIkRun) {
            ikState.message
        } else {
            val record = when (mode) {
                ControlMode.FK -> {
                    ExecutionRecord(
                        operationType = OperationType.FK,
                        robotName = robot.name,
                        inputState = RobotState(joints),
                        status = fkResult.status.name,
                        metadata = fkResult.metadata
                    )
                }

                ControlMode.IK -> {
                    ExecutionRecord(
                        operationType = OperationType.IK,
                        robotName = robot.name,
                        inputState = ikState.request?.initialState ?: RobotState(joints),
                        targetPosition = targetPoint,
                        status = lastIkStatus.name,
                        finalError = lastIkFinalError.takeIf { it.isFinite() },
                        iterations = lastIkIterations,
                        metadata = lastIkMetadata
                    )
                }
            }

            debugFormatter.formatExecution(record)
        }
    }

    LaunchedEffect(robot, joints, targetPoint, mode, tutorialModeRevealGeneration) {
        if (
            shouldSuppressRobotLabSideEffectForTutorialReveal(
                currentMode = mode,
                revealedMode = tutorialModeRevealed,
                revealGeneration = tutorialModeRevealGeneration,
                consumedGeneration = saveRevealGenerationConsumed
            )
        ) {
            saveRevealGenerationConsumed = tutorialModeRevealGeneration
            return@LaunchedEffect
        }
        val currentRobot = robot ?: return@LaunchedEffect
        if (joints.size != currentRobot.joints.size) return@LaunchedEffect

        delay(250L)
        try {
            withContext(Dispatchers.IO) {
                appStorageRepository.saveRobotLabState(
                    RobotLabSavedState(
                        robot = currentRobot,
                        jointValues = joints,
                        target = targetPoint,
                        controlMode = mode.name,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            AppLog.e(tag) {
                "Robot Lab autosave failed without interrupting the active session | " +
                    "type=${failure::class.java.simpleName}, message=${failure.message}"
            }
        }
    }

    LaunchedEffect(currentScreen, targetPoint, mode, robot, effectiveAiModelKey, modelLoading, tutorialModeRevealGeneration) {
        ikState = ikState.invalidate()
        if (shouldSuppressRobotLabSideEffectForTutorialReveal(mode, tutorialModeRevealed,
                tutorialModeRevealGeneration, ikRevealGenerationConsumed)) {
            ikRevealGenerationConsumed = tutorialModeRevealGeneration
            ikState = ikState.invalidate("Robot View was revealed without running IK. Select a target to calculate.")
            return@LaunchedEffect
        }
        if (currentScreen != Layer1Screen.ROBOT_VIEW || mode != ControlMode.IK || modelLoading) return@LaunchedEffect
        val safeRobot = robot ?: return@LaunchedEffect
        val target = targetPoint ?: return@LaunchedEffect
        val begun = ikState.begin(RobotViewIkContext(safeRobot, target, effectiveAiModelKey), RobotState(joints))
        ikState = begun
        val request = begun.request ?: return@LaunchedEffect
        val activeModel = selectedOneMicronModel.takeIf { effectiveAiEnabled }
        try {
            val response = withContext(Dispatchers.Default) {
                val started = System.nanoTime()
                if (activeModel != null) {
                    val verified = VerifiedOneMicronIkEngine(activeModel).solve(safeRobot, request.initialState, target)
                    RobotViewIkResponse(
                        result = IKResult(verified.state,
                            if (verified.verified) IKStatus.SUCCESS else IKStatus.NO_CONVERGENCE,
                            verified.verified, verified.refinementIterations + verified.fallbackIterations,
                            verified.finalErrorMeters),
                        elapsedMillis = (System.nanoTime() - started) / 1_000_000.0,
                        aiPath = verified.path,
                        explanation = verified.message,
                        neuralProposalErrorMeters = verified.neuralErrorMeters.takeIf { it.isFinite() && it >= 0 }
                    )
                } else {
                    val result = kinematicsService.computeIK(safeRobot, request.initialState, target)
                    RobotViewIkResponse(result, (System.nanoTime() - started) / 1_000_000.0)
                }
            }
            val completed = ikState.complete(request, response)
            ikState = completed
            if (completed.request == request) completed.poseToApply?.let { joints = it.jointValues }
        } catch (cancelled: CancellationException) {
            ikState = ikState.fail(request, "IK calculation cancelled. No new solution is verified.")
            throw cancelled
        } catch (error: Exception) {
            ikState = ikState.fail(request, "IK failed: ${error.message ?: error::class.simpleName}. No new solution is verified.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("robot-lab-root")
            .tutorialAnchor(TutorialTargets.RobotLab),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        JargonHelpNotice()

        if (currentScreen != Layer1Screen.ROBOT_LAB_HOME) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("robot-lab-screen-selector")
                        .tutorialAnchor(TutorialTargets.RobotLabScreenSelector),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableNavigationButton(
                    selected = currentScreen == Layer1Screen.ROBOT_SETUP,
                    label = "Robot Setup",
                    onClick = {
                        val action = robotLabScreenSelectionTutorialAction(
                            previous = currentScreen,
                            selected = Layer1Screen.ROBOT_SETUP
                        )
                        selectRobotLabScreen(Layer1Screen.ROBOT_SETUP)
                        action?.let(tutorialReporter::report)
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("robot-lab-setup-tab")
                            .tutorialAnchor(
                                targetId = TutorialTargets.RobotSetupTab,
                                actionEnabled = false
                            )
                )

                SelectableNavigationButton(
                    selected = currentScreen == Layer1Screen.ROBOT_VIEW,
                    label = "Robot View",
                    onClick = {
                        val action = robotLabScreenSelectionTutorialAction(
                            previous = currentScreen,
                            selected = Layer1Screen.ROBOT_VIEW
                        )
                        selectRobotLabScreen(Layer1Screen.ROBOT_VIEW)
                        action?.let(tutorialReporter::report)
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("robot-lab-view-tab")
                            .tutorialAnchor(
                                targetId = TutorialTargets.RobotViewTab,
                                actionEnabled = false
                            )
                )
            }
        }

        when (currentScreen) {
            Layer1Screen.ROBOT_LAB_HOME -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("robot-lab-entry"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose what you want to do in Robot Lab.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RobotLabEntryCard(
                            title = "Robot Setup",
                            description = "Build or edit a validated DH mechanism and its joint limits.",
                            onClick = { selectRobotLabScreen(Layer1Screen.ROBOT_SETUP) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag("robot-lab-entry-setup")
                        )
                        RobotLabEntryCard(
                            title = "Robot View",
                            description = "Inspect the active robot and interact with FK or IK.",
                            onClick = { selectRobotLabScreen(Layer1Screen.ROBOT_VIEW) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag("robot-lab-entry-view")
                        )
                    }
                }
            }

            Layer1Screen.ROBOT_SETUP -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("robot-lab-setup")
                        .tutorialAnchor(TutorialTargets.RobotSetup)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JargonAwareText(
                        text = "Define the robot with DH parameters and joint limits. Apply it before using forward kinematics (FK) or inverse kinematics (IK).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    RobotSetupLibraryPanel(
                        robots = robotLibraryRobots,
                        selectedRobotId = selectedLibraryRobotId,
                        editorRobotId = editorSourceRobotId,
                        activeRobotId = activeLibraryRobotId,
                        hasUnsavedEditorChanges = editorState != editorBaselineState,
                        onLoadRobotIntoEditor = { savedRobot ->
                            val loadedState = robotEditorMapper.toEditorState(savedRobot.robot)
                            editorState = loadedState
                            editorBaselineState = loadedState
                            selectedLibraryRobotId = savedRobot.id
                            editorSourceRobotId = savedRobot.id
                            robotLibraryMessage = null
                            AppLog.d(tag) {
                                "📚 Validated library robot loaded into editor | id=${savedRobot.id}, name=${savedRobot.robot.name}, joints=${savedRobot.robot.joints.size}"
                            }
                        },
                        onCreateNewRobotDraft = {
                            val newDraft = RobotEditorState(
                                robotName = "New Robot",
                                dhRows = listOf(DhInputRow())
                            )
                            editorState = newDraft
                            editorBaselineState = newDraft
                            selectedLibraryRobotId = null
                            editorSourceRobotId = null
                            robotLibraryMessage =
                                "New draft. Give it a unique name, validate its DH rows, then save it to the shared library."
                        }
                    )

                    RobotEditorPanel(
                        editorState = editorState,
                        validationErrors = editorBuildResult.errors,
                        isApplyEnabled = editorBuildResult.isSuccess,
                        onRobotNameChange = { newName ->
                            editorState = editorState.copy(robotName = newName)
                        },
                        onDhRowChange = { index, updatedRow ->
                            val updatedRows = editorState.dhRows.toMutableList()
                            updatedRows[index] = updatedRow
                            editorState = editorState.copy(dhRows = updatedRows)
                        },
                        onAddRow = {
                            editorState = editorState.copy(
                                dhRows = editorState.dhRows + DhInputRow()
                            )
                        },
                        onRemoveRow = { index ->
                            val updatedRows = editorState.dhRows.toMutableList()
                            if (index in updatedRows.indices && updatedRows.size > 1) {
                                updatedRows.removeAt(index)
                                editorState = editorState.copy(dhRows = updatedRows)
                            }
                        },
                        onApplyRobot = {
                            if (!editorBuildResult.isSuccess) {
                                AppLog.w(tag) {
                                    "⚠️ Apply Robot ignored because current editor state is invalid | errors=${editorBuildResult.errors}"
                                }
                                return@RobotEditorPanel
                            }

                            appliedEditorState = editorState
                            activeLibraryRobotId = editorSourceRobotId
                            val previewRobot = editorBuildResult.robot

                            if (previewRobot != null) {
                                joints = previewRobot.joints.map { it.homeValue }
                                targetPoint = null
                                ikState = ikState.invalidate("Robot applied from Setup. Choose a target for a new IK verification.")
                                selectRobotLabScreen(Layer1Screen.ROBOT_VIEW)
                            }
                        },
                        onLoadPresetRobot = {
                            editorState = demoRobotState
                            editorBaselineState = demoRobotState
                            appliedEditorState = demoRobotState
                            selectedLibraryRobotId = null
                            editorSourceRobotId = null
                            activeLibraryRobotId = null

                            val previewBuild = robotEditorMapper.buildRobotDefinition(demoRobotState)
                            val previewRobot = previewBuild.robot

                            if (previewRobot != null) {
                                joints = previewRobot.joints.map { it.homeValue }
                                targetPoint = null
                                ikState = ikState.invalidate("Robot applied from Setup. Choose a target for a new IK verification.")
                                selectRobotLabScreen(Layer1Screen.ROBOT_VIEW)
                            }
                        },
                        collapseJointDetails = true
                    )

                    Button(
                        onClick = {
                            val draft = editorBuildResult.robot ?: return@Button
                            runCatching {
                                robotLibraryRepository.upsertRobot(
                                    existingId = editorSourceRobotId,
                                    robot = draft
                                )
                            }.onSuccess { mutation ->
                                val savedState =
                                    robotEditorMapper.toEditorState(mutation.savedRobot.robot)
                                editorState = savedState
                                editorBaselineState = savedState
                                selectedLibraryRobotId = mutation.savedRobot.id
                                editorSourceRobotId = mutation.savedRobot.id
                                robotLibraryMessage =
                                    "${mutation.savedRobot.robot.name} saved to the shared robot library."
                            }.onFailure { failure ->
                                robotLibraryMessage =
                                    failure.message
                                        ?: "The robot could not be saved. The previous library was preserved."
                            }
                        },
                        enabled = editorBuildResult.isSuccess,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("robot-save-to-library")
                    ) {
                        Text(
                            if (editorSourceRobotId == null) {
                                "Save new robot to library"
                            } else {
                                "Save changes to library"
                            }
                        )
                    }

                    robotLibraryMessage?.let { message ->
                        Text(
                            text = message,
                            modifier = Modifier.testTag("robot-library-message"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (robotBuildResult.errors.isNotEmpty()) {
                        Text(
                            "The applied robot cannot be previewed until these issues are resolved:",
                            color = MaterialTheme.colorScheme.error
                        )
                        robotBuildResult.errors.forEach { error ->
                            Text("• $error", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Layer1Screen.ROBOT_VIEW -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("robot-lab-view")
                        .tutorialAnchor(TutorialTargets.RobotView)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    JargonAwareText(
                        text = "Forward kinematics (FK) calculates the end-effector position from the joint values. Inverse kinematics (IK) searches for joint values that reach a chosen target.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (robotBuildResult.errors.isNotEmpty()) {
                        Text(
                            "The applied robot cannot be displayed until these issues are resolved:",
                            color = MaterialTheme.colorScheme.error
                        )
                        robotBuildResult.errors.forEach { error ->
                            Text("• $error", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectableNavigationButton(
                            selected = mode == ControlMode.FK,
                            label = "Forward Kinematics",
                            onClick = {
                                AppLog.d(tag) { "👆 FK button clicked | previousMode=$mode, newMode=${ControlMode.FK}" }
                                val action = robotLabModeSelectionTutorialAction(
                                    previous = mode,
                                    selected = ControlMode.FK
                                )
                                selectControlMode(ControlMode.FK)
                                action?.let(tutorialReporter::report)
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag("robot-lab-fk-mode")
                                    .tutorialAnchor(TutorialTargets.RobotFkMode)
                        )

                        SelectableNavigationButton(
                            selected = mode == ControlMode.IK,
                            label = "Inverse Kinematics",
                            onClick = {
                                AppLog.d(tag) { "👆 IK button clicked | previousMode=$mode, newMode=${ControlMode.IK}" }
                                val action = robotLabModeSelectionTutorialAction(
                                    previous = mode,
                                    selected = ControlMode.IK
                                )
                                selectControlMode(ControlMode.IK)
                                action?.let(tutorialReporter::report)
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag("robot-lab-ik-mode")
                                    .tutorialAnchor(TutorialTargets.RobotIkMode)
                        )
                    }

                    RobotViewStatusCard(
                        isIk = mode == ControlMode.IK,
                        robotName = robot?.name,
                        target = targetPoint,
                        ikState = ikState,
                        fkStatus = fkResult.status
                    )
                    if (mode == ControlMode.IK) {
                        RobotViewAiControls(
                            enabled = effectiveAiEnabled,
                            availability = aiAvailability,
                            refreshing = modelLoading,
                            onToggle = {
                                if (aiAvailability.available) {
                                    ikState = ikState.invalidate("AI mode changed. Previous IK verification was cleared.")
                                    useVerifiedOneMicronAi = !effectiveAiEnabled
                                }
                            },
                            onRefresh = {
                                ikState = ikState.invalidate("Refreshing model availability. Previous IK verification was cleared.")
                                modelRefreshRevision += 1
                            }
                        )
                    }

                    RobotScene3D(
                        jointPositions = safeJointPositions,
                        jointTypes = jointTypes,
                        targetPoint = targetPoint,
                        endEffector = if (fkResult.status == FKStatus.SUCCESS || fkResult.status == FKStatus.SUCCESS_WITH_WARNING) safeEndEffector else null,
                        workspaceRadius = sceneWorkspaceRadius,
                        enabled = mode == ControlMode.IK,
                        onTargetSelected = { raw ->
                            AppLog.d(tag) {
                                "👆 onTargetSelected received raw target | raw=(${raw.x}, ${raw.y}, ${raw.z})"
                            }

                            val safeRobot = robot ?: run {
                                AppLog.w(tag) { "⚠️ Target selection ignored because robot is null" }
                                return@RobotScene3D
                            }

                            val maxReach = maximumTargetReach
                            AppLog.d(tag) { "📏 Workspace maxReach computed in UI | maxReach=$maxReach" }

                            val clampedTarget = Workspace.clampToReach(raw, maxReach)
                            AppLog.d(tag) {
                                "🧷 Target clamped in UI | raw=(${raw.x}, ${raw.y}, ${raw.z}), clamped=(${clampedTarget.x}, ${clampedTarget.y}, ${clampedTarget.z})"
                            }

                            if (targetPoint != clampedTarget) ikState = ikState.invalidate("Target changed. Calculating a new IK solution is required.")
                            targetPoint = clampedTarget
                            acceptedIkTargetUpdateDuringDrag = true
                            AppLog.d(tag) { "🎯 targetPoint updated | newTarget=$targetPoint" }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .testTag("robot-lab-scene")
                            .reportCompletedRobotLabDrag(
                                reporter = tutorialReporter,
                                target = TutorialTargets.RobotScene,
                                detail = "Robot scene drag completed.",
                                onGestureStarted = {
                                    acceptedIkTargetUpdateDuringDrag = false
                                },
                                isCompletedAction = {
                                    mode == ControlMode.FK || acceptedIkTargetUpdateDuringDrag
                                }
                            )
                            .tutorialAnchor(TutorialTargets.RobotScene)
                    )

                    RobotSceneLegend(
                        workspaceRadius = sceneWorkspaceRadius,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mode == ControlMode.FK && robot != null) {
                        AppLog.d(tag) { "🎚️ Rendering FK sliders | jointCount=${robot.joints.size}" }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("robot-lab-joint-sliders")
                                    .tutorialAnchor(TutorialTargets.RobotJointSliders),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            robot.joints.forEachIndexed { i, joint ->
                                JointSlider(
                                    label = "J${i + 1}",
                                    jointType = joint.type,
                                    value = joints.getOrElse(i) { joint.homeValue },
                                    min = joint.minValue,
                                    max = joint.maxValue,
                                    onValueChangeFinished = {
                                        tutorialReporter.report(
                                            robotLabGestureTutorialAction(
                                                TutorialTargets.RobotJointSliders
                                            )
                                        )
                                    }
                                ) { newValue ->
                                    AppLog.d(tag) {
                                        "👆 JointSlider callback received | jointIndex=$i, jointName=${joint.name}, rawNewValue=$newValue, previousJoints=$joints"
                                    }

                                    val updated = joints.toMutableList()

                                    while (updated.size < robot.joints.size) {
                                        updated.add(robot.joints[updated.size].homeValue)
                                    }

                                    val sanitized = sanitizeJointValue(newValue, joint)

                                    AppLog.d(tag) {
                                        "🧼 Slider value sanitized | jointIndex=$i, jointName=${joint.name}, sanitizedValue=$sanitized"
                                    }

                                    updated[i] = sanitized
                                    ikState = ikState.invalidate("Joint values changed in FK. Previous IK verification was cleared.")
                                    joints = updated

                                    AppLog.d(tag) {
                                        "🦾 Joints updated from slider | jointIndex=$i, updatedJoints=$joints"
                                    }
                                }
                            }
                        }
                    } else {
                        AppLog.d(tag) { "↩️ FK sliders not rendered because mode is $mode or robot is null" }
                    }


                    ViewportStatusPanel(
                        mode = mode,
                        hasIkRun = hasIkRun,
                        fkStatus = fkResult.status,
                        ikStatus = lastIkStatus,
                        ikDetailCode = lastIkDetailCode,
                        fkStatusText = fkStatusText,
                        ikStatusText = ikStatusText,
                        endEffector = if (fkResult.status == FKStatus.SUCCESS || fkResult.status == FKStatus.SUCCESS_WITH_WARNING) safeEndEffector else null,
                        targetPoint = targetPoint,
                        ikFinalError = lastIkFinalError,
                        ikIterations = lastIkIterations,
                        ikElapsedMs = lastIkElapsedMs,
                        ikIterationsPerSecond = lastIkIterationsPerSecond,
                        fkMetadata = fkResult.metadata,
                        ikMetadata = lastIkMetadata,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DebugPanel(
                        debugText = debugText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    AppLog.d(tag) { "✅ InteractiveFkDemo composition finished" }
}

@Composable
private fun RobotLabEntryCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 144.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun robotLabScreenForTutorialTarget(target: TutorialTargetId?): Layer1Screen? =
    when (target) {
        TutorialTargets.RobotSetup,
        TutorialTargets.RobotName,
        TutorialTargets.RobotJointCard,
        TutorialTargets.RobotJointType,
        TutorialTargets.RobotDhParameters,
        TutorialTargets.RobotJointLimits,
        TutorialTargets.RobotAddJoint,
        TutorialTargets.RobotRemoveJoint,
        TutorialTargets.RobotApply,
        TutorialTargets.RobotDemo -> Layer1Screen.ROBOT_SETUP

        TutorialTargets.RobotView,
        TutorialTargets.RobotFkMode,
        TutorialTargets.RobotIkMode,
        TutorialTargets.RobotScene,
        TutorialTargets.RobotJointSliders -> Layer1Screen.ROBOT_VIEW

        else -> null
    }

internal fun robotLabControlModeForTutorialTarget(target: TutorialTargetId?): ControlMode? =
    when (target) {
        TutorialTargets.RobotFkMode,
        TutorialTargets.RobotJointSliders -> ControlMode.FK

        TutorialTargets.RobotIkMode,
        TutorialTargets.RobotScene -> ControlMode.IK
        else -> null
    }

internal fun robotLabScreenSelectionTutorialAction(
    previous: Layer1Screen,
    selected: Layer1Screen
): TutorialActionReport? {
    // This step asks for a tap, not a value mutation. Re-tapping the already visible tab is still
    // a genuine, accessible product action and avoids trapping a fresh project that opens on Setup.
    val target =
        when (selected) {
            Layer1Screen.ROBOT_LAB_HOME -> return null
            Layer1Screen.ROBOT_SETUP -> TutorialTargets.RobotSetupTab
            Layer1Screen.ROBOT_VIEW -> TutorialTargets.RobotViewTab
        }
    return TutorialActionReport(
        target = target,
        interaction = TutorialInteraction.TAP,
        detail =
            if (previous == selected) {
                "Robot Lab $selected tab confirmed."
            } else {
                "Robot Lab screen changed to $selected."
            }
    )
}

internal fun robotLabModeSelectionTutorialAction(
    previous: ControlMode,
    selected: ControlMode
): TutorialActionReport? {
    if (previous == selected) return null
    return TutorialActionReport(
        target = selected.tutorialTarget(),
        interaction = TutorialInteraction.CHOOSE,
        detail = "Robot Lab kinematics mode changed to $selected."
    )
}

internal fun robotLabGestureTutorialAction(target: TutorialTargetId): TutorialActionReport =
    TutorialActionReport(
        target = target,
        interaction = TutorialInteraction.DRAG,
        detail = "Robot Lab gesture completed on ${target.value}."
    )

internal fun shouldSuppressRobotLabSideEffectForTutorialReveal(
    currentMode: ControlMode,
    revealedMode: ControlMode?,
    revealGeneration: Long,
    consumedGeneration: Long
): Boolean =
    revealGeneration > 0L &&
        revealGeneration > consumedGeneration &&
        revealedMode == currentMode

private fun ControlMode.tutorialTarget(): TutorialTargetId =
    when (this) {
        ControlMode.FK -> TutorialTargets.RobotFkMode
        ControlMode.IK -> TutorialTargets.RobotIkMode
    }

private fun Modifier.reportCompletedRobotLabDrag(
    reporter: com.robotkinematicslab.mobile.ui.onboarding.TutorialActionReporter,
    target: TutorialTargetId,
    detail: String,
    onGestureStarted: () -> Unit = {},
    isCompletedAction: () -> Boolean = { true }
): Modifier =
    pointerInput(reporter, target, detail) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            onGestureStarted()
            val pointerId = down.id
            val startPosition = down.position
            var latestPosition = startPosition
            var completed = false
            var pressed = true
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                latestPosition = change.position
                pressed = change.pressed
                if (!pressed && change.previousPressed) {
                    completed = true
                }
            } while (pressed)

            if (
                completed &&
                isCompletedAction() &&
                (latestPosition - startPosition).getDistance() > viewConfiguration.touchSlop
            ) {
                reporter.report(robotLabGestureTutorialAction(target).copy(detail = detail))
            }
        }
    }

@Composable
fun ViewportStatusPanel(
    mode: ControlMode,
    hasIkRun: Boolean,
    fkStatus: FKStatus,
    ikStatus: IKStatus,
    ikDetailCode: IKDetailCode,
    fkStatusText: String,
    ikStatusText: String,
    endEffector: Vec3?,
    targetPoint: Vec3?,
    ikFinalError: Double,
    ikIterations: Int,
    ikElapsedMs: Double,
    ikIterationsPerSecond: Double,
    fkMetadata: SolverMetadata?,
    ikMetadata: SolverMetadata?,
    modifier: Modifier = Modifier
) {
    val panelColor = Color(0xFF162535)
    val textColor = Color(0xFFE8EEF5)

    val interactionHint = if (mode == ControlMode.FK) {
        "Drag viewport: rotate camera"
    } else {
        "Drag viewport: move IK target"
    }

    RobotLabDisclosurePanel(
        spec = RobotLabDisclosureSpec.VIEWPORT_INFO,
        panelColor = panelColor,
        textColor = textColor,
        modifier = modifier
    ) {
        Text(
            text = "Mode: $mode",
            color = textColor
        )

        Text(
            text = "FK: $fkStatus",
            color = textColor
        )

        Text(
            text = fkStatusText,
            color = textColor
        )

        Text(
            text = "IK: ${if (hasIkRun) ikStatus else "NOT_RUN"}",
            color = textColor
        )

        Text(
            text = ikStatusText,
            color = textColor
        )

        Text(
            text = "IK detail: ${if (hasIkRun) ikDetailCode else "—"}",
            color = textColor
        )

        if (mode == ControlMode.IK && hasIkRun) {
            Text(
                text = "IK final error: ${formatNumberOrDash(ikFinalError)} m",
                color = textColor
            )

            Text(
                text = "IK iterations: $ikIterations",
                color = textColor
            )

            Text(
                text = "IK time: ${formatMillisecondsOrDash(ikElapsedMs)} ms",
                color = textColor
            )

            Text(
                text = "IK rate: ${formatRateOrDash(ikIterationsPerSecond)} iter/s",
                color = textColor
            )

            Text(
                text = "IK detail code: $ikDetailCode",
                color = textColor
            )

            Text(
                text = "IK solver: ${ikMetadata?.solverName ?: "—"}",
                color = textColor
            )

            Text(
                text = "IK configured max iterations: ${ikMetadata?.maxIterations?.toString() ?: "—"}",
                color = textColor
            )

            Text(
                text = "IK tolerance: ${ikMetadata?.tolerance?.let { formatNumberOrDash(it) } ?: "—"}",
                color = textColor
            )

            Text(
                text = "IK damping: ${ikMetadata?.damping?.let { formatNumberOrDash(it) } ?: "—"}",
                color = textColor
            )

            Text(
                text = "IK max step: ${ikMetadata?.maxStep?.let { formatNumberOrDash(it) } ?: "—"}",
                color = textColor
            )
        } else if (mode == ControlMode.FK) {
            Text(
                text = "FK solver: ${fkMetadata?.solverName ?: "—"}",
                color = textColor
            )
        } else {
            Text(
                text = "IK measurements will appear after a target is selected.",
                color = textColor
            )
        }

        Text(
            text = interactionHint,
            color = textColor
        )

        Text(
            text = "End effector: ${formatVec3OrDash(endEffector)}",
            color = textColor
        )

        Text(
            text = "Target: ${formatVec3OrDash(targetPoint)}",
            color = textColor
        )
    }
}

@Composable
fun DebugPanel(
    debugText: String,
    modifier: Modifier = Modifier
) {
    RobotLabDisclosurePanel(
        spec = RobotLabDisclosureSpec.LAYER_1_DEBUG,
        panelColor = Color(0xFF1E1E1E),
        textColor = Color(0xFFE8EEF5),
        modifier = modifier
    ) {
        Text(
            text = debugText,
            color = Color(0xFFE8EEF5)
        )
    }
}

internal enum class RobotLabDisclosureSpec(
    val title: String,
    val testTag: String,
    val initiallyExpanded: Boolean = false
) {
    VIEWPORT_INFO(
        title = "Viewport info",
        testTag = "viewport-info-disclosure"
    ),
    LAYER_1_DEBUG(
        title = "Layer 1 debug",
        testTag = "layer-1-debug-disclosure"
    )
}

internal fun nextRobotLabDisclosureState(expanded: Boolean): Boolean = !expanded

internal fun robotLabDisclosureStateDescription(expanded: Boolean): String =
    if (expanded) "Expanded" else "Collapsed"

internal fun robotLabDisclosureContentDescription(spec: RobotLabDisclosureSpec): String = spec.title

internal const val ROBOT_LAB_DISCLOSURE_MIN_TOUCH_TARGET_DP = 48

@Composable
private fun RobotLabDisclosurePanel(
    spec: RobotLabDisclosureSpec,
    panelColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(spec.name) { mutableStateOf(spec.initiallyExpanded) }
    val state = robotLabDisclosureStateDescription(expanded)

    Column(
        modifier = modifier.background(panelColor, RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = { expanded = nextRobotLabDisclosureState(expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ROBOT_LAB_DISCLOSURE_MIN_TOUCH_TARGET_DP.dp)
                    .testTag(spec.testTag)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        stateDescription = state
                        contentDescription = robotLabDisclosureContentDescription(spec)
                    },
            color = Color.Transparent,
            contentColor = textColor
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.title,
                    modifier = Modifier.weight(1f),
                    color = textColor
                )
                Text(
                    text = if (expanded) "▾" else "▸",
                    color = textColor
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

private fun formatVec3OrDash(value: Vec3?): String {
    if (value == null) return "—"

    return buildString {
        append("x=")
        append(formatNumber(value.x))
        append(", y=")
        append(formatNumber(value.y))
        append(", z=")
        append(formatNumber(value.z))
    }
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.US, "%.3f", value)
}

private fun formatNumberOrDash(value: Double): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.6f", value)
    } else {
        "—"
    }
}

private fun formatMillisecondsOrDash(value: Double): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.3f", value)
    } else {
        "—"
    }
}

private fun formatRateOrDash(value: Double): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.2f", value)
    } else {
        "—"
    }
}

@Composable
fun JointSlider(
    label: String,
    jointType: JointType,
    value: Double,
    min: Double,
    max: Double,
    onValueChangeFinished: () -> Unit = {},
    onValueChange: (Double) -> Unit
) {
    val tag = "MainActivity"
    var changedSinceGesture by remember { mutableStateOf(false) }

    AppLog.d(tag) {
        "🎚️ JointSlider composed | label=$label, type=$jointType, internalValue=$value, display=${formatJointValueForDisplay(label, value, jointType)}, range=[$min, $max]"
    }

    Column {
        Text(formatJointValueForDisplay(label, value, jointType))
        Slider(
            value = value.toFloat(),
            onValueChange = {
                AppLog.d(tag) {
                    "👆 Slider moved | label=$label, floatValue=$it, doubleValue=${it.toDouble()}"
                }
                changedSinceGesture = true
                onValueChange(it.toDouble())
            },
            onValueChangeFinished = {
                if (changedSinceGesture) {
                    changedSinceGesture = false
                    onValueChangeFinished()
                }
            },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}

fun formatJointValueForDisplay(
    label: String,
    value: Double,
    jointType: JointType
): String {
    return when (jointType) {
        JointType.REVOLUTE -> "$label (R) = %.1f°".format(Locale.US, Math.toDegrees(value))
        JointType.PRISMATIC -> "$label (P) = %.1f mm".format(Locale.US, value * 1000.0)
    }
}
