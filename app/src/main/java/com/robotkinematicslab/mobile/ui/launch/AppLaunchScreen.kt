package com.robotkinematicslab.mobile.ui.launch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ui.accessibility.LocalAppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceBounds3D
import com.robotkinematicslab.mobile.ui.charts.spatial.WorkspaceCamera
import com.robotkinematicslab.mobile.ui.charts.spatial.buildWorkspaceProjectionContext
import com.robotkinematicslab.mobile.ui.charts.spatial.projectWorkspacePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val APP_LAUNCH_SCREEN_TAG = "app-launch-screen"
internal const val APP_LAUNCH_ENTER_PROJECTS_TAG = "app-launch-enter-projects"
internal const val APP_LAUNCH_SCENE_TAG = "app-launch-3d-scene"

/** Branded, live 3D entry point shown before any project or tutorial surface is composed. */
@Composable
fun AppLaunchScreen(
    onEnterProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibility = LocalAppAccessibilityPreferences.current
    val colors = MaterialTheme.colorScheme
    val accentColors =
        remember(colors.primary, colors.secondary, colors.tertiary, accessibility.highContrast) {
            if (accessibility.highContrast) {
                listOf(Color.White, Color(0xFFFFFF00), Color(0xFF00FFFF))
            } else {
                listOf(colors.primary, colors.secondary, colors.tertiary)
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF05090F),
                            Color(0xFF08131D),
                            Color(0xFF101820)
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .semantics { paneTitle = "Robot Kinematics Lab welcome" }
                .testTag(APP_LAUNCH_SCREEN_TAG)
    ) {
        val landscape = maxWidth > maxHeight * 1.15f
        val availableHeight = maxHeight
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LaunchWireframeScene(
                    accents = accentColors,
                    reduceMotion = accessibility.reduceMotion,
                    modifier = Modifier.weight(1.15f).fillMaxHeight()
                )
                LaunchBrandContent(
                    onEnterProjects = onEnterProjects,
                    accents = accentColors,
                    compact = availableHeight < 430.dp,
                    modifier = Modifier.weight(0.85f).fillMaxHeight().verticalScroll(rememberScrollState())
                )
            }
        } else {
            val sceneHeight = (availableHeight * 0.54f).coerceIn(210.dp, 470.dp)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LaunchWireframeScene(
                    accents = accentColors,
                    reduceMotion = accessibility.reduceMotion,
                    modifier = Modifier.fillMaxWidth().height(sceneHeight)
                )
                LaunchBrandContent(
                    onEnterProjects = onEnterProjects,
                    accents = accentColors,
                    compact = availableHeight < 650.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LaunchBrandContent(
    onEnterProjects: () -> Unit,
    accents: List<Color>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonContentColor = launchReadableContentColor(accents[0])
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(if (compact) 38.dp else 46.dp),
                shape = CircleShape,
                color = accents[0].copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, accents[0])
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "RK",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = "ROBOT KINEMATICS LAB",
                modifier = Modifier.weight(1f).semantics { heading() },
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2
            )
        }
        Text(
            text = "Build, validate and explain robotic motion.",
            color = Color(0xFFD8E7F2),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (!compact) {
            Text(
                text = "Local scientific workspaces · deterministic evidence · explainable training",
                color = Color(0xFFAFC3D1),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        LaunchSignalChips(
            accents = accents,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onEnterProjects,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .semantics { contentDescription = "Enter projects" }
                    .testTag(APP_LAUNCH_ENTER_PROJECTS_TAG),
            shape = RoundedCornerShape(18.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = accents[0],
                    contentColor = buttonContentColor
                )
        ) {
            Text(
                text = "Enter projects  →",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(if (compact) 0.dp else 5.dp))
    }
}

internal fun launchReadableContentColor(background: Color): Color =
    if (background.luminance() > 0.40f) Color.Black else Color.White

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LaunchSignalChips(
    accents: List<Color>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        LaunchSignalChip("KINEMATICS", accents[0])
        LaunchSignalChip("VALIDATION", accents[1])
        LaunchSignalChip("LOCAL AI", accents[2])
    }
}

@Composable
private fun LaunchSignalChip(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LaunchWireframeScene(
    accents: List<Color>,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    var manualYaw by remember { mutableFloatStateOf(0f) }
    var manualPitch by remember { mutableFloatStateOf(0f) }
    val progress =
        if (reduceMotion) {
            0.18f
        } else {
            val transition = rememberInfiniteTransition(label = "launch-kinematic-choreography")
            val animated by
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 18_000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                    label = "launch-scene-progress"
                )
            animated
        }
    val precomputedFrames =
        remember {
            List(LAUNCH_FRAME_COUNT) { index ->
                buildLaunchSceneFrame(index.toDouble() / LAUNCH_FRAME_COUNT.toDouble())
            }
        }
    val frame =
        precomputedFrames[
            (normalizedLaunchProgress(progress.toDouble()) * LAUNCH_FRAME_COUNT.toDouble())
                .toInt()
                .coerceIn(0, LAUNCH_FRAME_COUNT - 1)
        ]

    Canvas(
        modifier =
            modifier
                .semantics {
                    contentDescription =
                        if (reduceMotion) {
                            "Static 3D wireframe scene with three articulated robot arms."
                        } else {
                            "Animated 3D wireframe scene with three articulated robot arms exchanging a data core."
                        }
                }
                .testTag(APP_LAUNCH_SCENE_TAG)
                .pointerInput(reduceMotion) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        manualYaw = normalizedLaunchYaw(manualYaw + dragAmount.x * 0.008f)
                        manualPitch = (manualPitch - dragAmount.y * 0.006f).coerceIn(-0.55f, 0.55f)
                    }
                }
    ) {
        val bounds =
            WorkspaceBounds3D(
                minimum = Vec3(-1.45, -0.05, -1.45),
                maximum = Vec3(1.45, 1.25, 1.45),
                center = Vec3(0.0, 0.48, 0.0),
                radius = 1.52
            )
        val camera =
            WorkspaceCamera(
                yaw = -0.72f + frame.orbitRadians.toFloat() + manualYaw,
                pitch = 0.35f + manualPitch,
                zoom = 1.18f
            )
        val projection = buildWorkspaceProjectionContext(bounds, size.width, size.height, camera)
        fun project(point: Vec3): Offset {
            val projected = projectWorkspacePoint(point, projection)
            return Offset(projected.x, projected.y)
        }

        drawLaunchStars(accents, frame.orbitRadians.toFloat())
        drawLaunchGrid(::project, accents[0])
        drawLaunchOrbit(::project, accents[1])

        frame.robots
            .sortedBy { robot ->
                projectWorkspacePoint(robot.jointPositions.first(), projection).depth
            }
            .forEach { robot ->
                drawLaunchRobot(robot, accents[robot.colorIndex.mod(accents.size)], ::project)
            }

        val core = project(frame.dataCore)
        val coreRadius = 9f + frame.dataCorePulse.toFloat() * 5f
        drawCircle(accents[2].copy(alpha = 0.12f), radius = coreRadius * 2.7f, center = core)
        drawCircle(accents[2].copy(alpha = 0.30f), radius = coreRadius * 1.7f, center = core)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, accents[2]), center = core, radius = coreRadius),
            radius = coreRadius,
            center = core
        )
        drawCircle(Color.White.copy(alpha = 0.82f), coreRadius + 5f, core, style = Stroke(1.5f))
    }
}

private const val LAUNCH_FRAME_COUNT = 540

private fun DrawScope.drawLaunchStars(accents: List<Color>, phase: Float) {
    repeat(30) { index ->
        val x = ((index * 83) % 997) / 997f * size.width
        val y = ((index * 137) % 991) / 991f * size.height
        val twinkle = 0.24f + 0.22f * sin(phase + index * 0.8f)
        drawCircle(
            color = accents[index.mod(accents.size)].copy(alpha = twinkle.coerceIn(0.08f, 0.48f)),
            radius = 0.9f + (index % 3) * 0.55f,
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawLaunchGrid(project: (Vec3) -> Offset, accent: Color) {
    val extent = 1.35
    val divisions = 8
    repeat(divisions + 1) { index ->
        val coordinate = -extent + extent * 2.0 * index.toDouble() / divisions.toDouble()
        drawLine(
            accent.copy(alpha = 0.14f),
            project(Vec3(coordinate, 0.0, -extent)),
            project(Vec3(coordinate, 0.0, extent)),
            1.1f
        )
        drawLine(
            accent.copy(alpha = 0.14f),
            project(Vec3(-extent, 0.0, coordinate)),
            project(Vec3(extent, 0.0, coordinate)),
            1.1f
        )
    }
}

private fun DrawScope.drawLaunchOrbit(project: (Vec3) -> Offset, accent: Color) {
    val path = Path()
    repeat(49) { index ->
        val angle = index.toDouble() / 48.0 * 2.0 * PI
        val point = project(Vec3(cos(angle) * 0.34, 0.58, sin(angle) * 0.34))
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(path, accent.copy(alpha = 0.10f), style = Stroke(width = 9f))
    drawPath(path, accent.copy(alpha = 0.60f), style = Stroke(width = 1.7f))
}

private fun DrawScope.drawLaunchRobot(
    robot: LaunchRobotPose,
    accent: Color,
    project: (Vec3) -> Offset
) {
    robot.jointPositions.zipWithNext().forEach { (start, end) ->
        val p0 = project(start)
        val p1 = project(end)
        drawLine(accent.copy(alpha = 0.13f), p0, p1, 18f, cap = StrokeCap.Round)
        drawLine(accent.copy(alpha = 0.40f), p0, p1, 9f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.88f), p0, p1, 2.2f, cap = StrokeCap.Round)
    }
    robot.jointPositions.forEachIndexed { index, point ->
        val projected = project(point)
        val radius = if (index == 0) 10f else 7.5f
        drawCircle(accent.copy(alpha = 0.18f), radius * 2.0f, projected)
        drawCircle(accent, radius, projected, style = Stroke(width = 2.4f))
        drawCircle(Color.White.copy(alpha = 0.88f), 2.3f, projected)
    }
}
