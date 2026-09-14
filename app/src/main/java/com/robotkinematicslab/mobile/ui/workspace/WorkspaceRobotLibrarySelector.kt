package com.robotkinematicslab.mobile.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.workspace.analysis.robotWorkspaceFingerprint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun WorkspaceRobotLibrarySelector(
    choices: List<WorkspaceRobotChoice>,
    selectedRobotId: String?,
    enabled: Boolean,
    onSelect: (WorkspaceRobotChoice) -> Unit
) {
    if (choices.isEmpty()) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("workspace-robot-library-empty")
                    .tutorialAnchor(TutorialTargets.WorkspaceRobotSelector),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "No validated DH robots are available. Add one in Dataset > Robots first.",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        return
    }

    val selectedChoice = choices.firstOrNull { it.id == selectedRobotId } ?: choices.first()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("workspace-robot-selector")
                .tutorialAnchor(TutorialTargets.WorkspaceRobotSelector),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Robot library", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Choose from the list · the preview is calculated from the stored DH model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = choices.count { it.source == WorkspaceRobotSource.LIBRARY }.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CompactSelectionMenu(
            options = choices,
            selected = selectedChoice,
            label = { choice -> "${choice.label} · ${choice.robot.joints.size} joints" },
            onSelected = onSelect,
            enabled = enabled,
            optionTestTag = { choice -> "workspace-robot:${choice.id}" }
        )

        WorkspaceRobotChoiceCard(
            choice = selectedChoice,
            selected = true
        )

        Text(
            if (enabled) {
                "Selecting another robot clears any incompatible workspace view; generate a new study to animate its own geometry."
            } else {
                "Robot selection is locked while this study is running."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkspaceRobotChoiceCard(
    choice: WorkspaceRobotChoice,
    selected: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val topology = choice.robot.joints.joinToString("-") { joint ->
        if (joint.type == JointType.REVOLUTE) "R" else "P"
    }
    val preview = remember(choice.fingerprint) { buildWorkspaceRobotPreview(choice.robot) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("workspace-robot-preview:${choice.id}"),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) colors.primaryContainer.copy(alpha = 0.62f) else colors.surface
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 5.dp else 1.dp)
    ) {
        WorkspaceRobotDhPreview(
            robot = choice.robot,
            points = preview,
            selected = selected
        )
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${choice.robot.joints.size} joints · ${topology.ifBlank { "No topology" }}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = if (choice.source == WorkspaceRobotSource.LIBRARY) "VALIDATED LOCAL MODEL" else "CURRENT ROBOT LAB",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WorkspaceRobotDhPreview(
    robot: RobotDefinition,
    points: List<Vec3>,
    selected: Boolean
) {
    val topology = robot.joints.joinToString("-") { if (it.type == JointType.REVOLUTE) "R" else "P" }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF061426),
                            if (selected) Color(0xFF164D83) else Color(0xFF17243E),
                            Color(0xFF35133B)
                        )
                    )
                )
                .semantics {
                    contentDescription =
                        "DH-derived home pose preview for ${robot.name}, topology ${topology.ifBlank { "none" }}"
                }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.07f)
            repeat(6) { index ->
                val x = size.width * index / 5f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            repeat(4) { index ->
                val y = size.height * index / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }

            if (points.size < 2) {
                drawCircle(Color(0xFFFFB44C), radius = 7f, center = center)
                return@Canvas
            }

            val projected = projectWorkspaceRobotPreview(points, size.width, size.height)

            projected.zipWithNext().forEachIndexed { index, (start, end) ->
                val jointType = robot.joints.getOrNull(index)?.type ?: JointType.REVOLUTE
                val linkColor = if (jointType == JointType.REVOLUTE) Color(0xFF54D7FF) else Color(0xFFFFB44C)
                drawLine(
                    color = linkColor.copy(alpha = 0.20f),
                    start = Offset(start.x, start.y),
                    end = Offset(end.x, end.y),
                    strokeWidth = 15f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.92f),
                    start = Offset(start.x, start.y),
                    end = Offset(end.x, end.y),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }

            projected.dropLast(1).forEachIndexed { index, point ->
                val jointType = robot.joints.getOrNull(index)?.type ?: JointType.REVOLUTE
                val color = if (jointType == JointType.REVOLUTE) Color(0xFF54D7FF) else Color(0xFFFFB44C)
                if (jointType == JointType.REVOLUTE) {
                    drawCircle(color.copy(alpha = 0.22f), radius = 12f, center = Offset(point.x, point.y))
                    drawCircle(color, radius = 6f, center = Offset(point.x, point.y))
                    drawCircle(Color(0xFF061426), radius = 2f, center = Offset(point.x, point.y))
                } else {
                    drawRect(color, topLeft = Offset(point.x - 6f, point.y - 6f), size = androidx.compose.ui.geometry.Size(12f, 12f))
                }
            }

            val end = projected.last()
            drawCircle(Color(0xFFFF4FD8).copy(alpha = 0.25f), radius = 13f, center = Offset(end.x, end.y))
            drawCircle(Color(0xFFFF4FD8), radius = 6f, center = Offset(end.x, end.y), style = Stroke(width = 3f))
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            color = Color.Black.copy(alpha = 0.46f),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Text(
                text = "DH HOME · $topology",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun projectWorkspaceRobotPreview(
    points: List<Vec3>,
    width: Float,
    height: Float
): List<Offset> {
    if (points.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val yaw = -0.72
    val pitch = 0.38
    val projected =
        points.map { point ->
            val rotatedX = point.x * cos(yaw) - point.z * sin(yaw)
            val rotatedZ = point.x * sin(yaw) + point.z * cos(yaw)
            val rotatedY = point.y * cos(pitch) - rotatedZ * sin(pitch)
            rotatedX to -rotatedY
        }
    val minimumX = projected.minOf { it.first }
    val maximumX = projected.maxOf { it.first }
    val minimumY = projected.minOf { it.second }
    val maximumY = projected.maxOf { it.second }
    val xRange = maximumX - minimumX
    val yRange = maximumY - minimumY
    val scaleX = if (xRange > 1e-12) width * 0.76 / xRange else Double.POSITIVE_INFINITY
    val scaleY = if (yRange > 1e-12) height * 0.60 / yRange else Double.POSITIVE_INFINITY
    val scale =
        min(scaleX, scaleY)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: min(width, height).toDouble() * 0.25
    val midpointX = (minimumX + maximumX) * 0.5
    val midpointY = (minimumY + maximumY) * 0.5

    return projected.map { (x, y) ->
        Offset(
            x = (width * 0.5 + (x - midpointX) * scale).toFloat(),
            y = (height * 0.49 + (y - midpointY) * scale).toFloat()
        )
    }
}

internal fun buildWorkspaceRobotPreview(robot: RobotDefinition): List<Vec3> {
    val state =
        RobotState(
            robot.joints.map { joint ->
                joint.homeValue.coerceIn(joint.minValue, joint.maxValue)
            }
        )
    val result = ForwardKinematicsSolver().solve(robot, state)
    return if (result.status == FKStatus.SUCCESS || result.status == FKStatus.SUCCESS_WITH_WARNING) {
        result.jointPositions
    } else {
        emptyList()
    }
}

internal fun buildRobotChoices(
    currentRobot: RobotDefinition?,
    libraryRobots: List<SavedRobot>
): List<WorkspaceRobotChoice> {
    val libraryChoices =
        libraryRobots.map { saved ->
            val fingerprint = robotWorkspaceFingerprint(saved.robot)
            WorkspaceRobotChoice(
                id = "library-${saved.id}",
                label = saved.robot.name,
                robot = saved.robot,
                source = WorkspaceRobotSource.LIBRARY,
                fingerprint = fingerprint
            )
        }
    val knownFingerprints = libraryChoices.mapTo(mutableSetOf(), WorkspaceRobotChoice::fingerprint)
    val currentChoice =
        currentRobot
            ?.takeIf { robotWorkspaceFingerprint(it) !in knownFingerprints }
            ?.let { robot ->
                val fingerprint = robotWorkspaceFingerprint(robot)
                WorkspaceRobotChoice(
                    id = "current-$fingerprint",
                    label = robot.name,
                    robot = robot,
                    source = WorkspaceRobotSource.CURRENT_ROBOT_LAB,
                    fingerprint = fingerprint
                )
            }
    return if (currentChoice == null) libraryChoices else listOf(currentChoice) + libraryChoices
}

internal data class WorkspaceRobotChoice(
    val id: String,
    val label: String,
    val robot: RobotDefinition,
    val source: WorkspaceRobotSource,
    val fingerprint: String
)

internal enum class WorkspaceRobotSource {
    LIBRARY,
    CURRENT_ROBOT_LAB
}
