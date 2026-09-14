package com.robotkinematicslab.mobile.ui.shared.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun DiagnosticProcessTimeline(
    steps: List<DiagnosticTimelineStep>
) {
    if (steps.isEmpty()) return

    var selectedStepLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var detailedStepLabel by rememberSaveable { mutableStateOf<String?>(null) }
    val quickStep = selectedStepLabel?.let { label -> steps.firstOrNull { it.label == label } }
    val detailedStep = detailedStepLabel?.let { label -> steps.firstOrNull { it.label == label } }

    val activeStep =
        steps.firstOrNull { it.status == DiagnosticTimelineStatus.RUNNING }
            ?: steps.lastOrNull { it.status == DiagnosticTimelineStatus.FAILED }
            ?: steps.lastOrNull { it.status == DiagnosticTimelineStatus.CANCELLED }
            ?: steps.lastOrNull { it.status == DiagnosticTimelineStatus.COMPLETE }
            ?: steps.first()

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("telemetry-timeline")
                .tutorialAnchor(TutorialTargets.TelemetryTimeline),
        shape = RoundedCornerShape(14.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "Process Timeline",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF102A43)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, step ->
                    TimelineCheckpoint(
                        step = step,
                        hasPrevious = index > 0,
                        hasNext = index < steps.lastIndex,
                        onTap = { selectedStepLabel = step.label },
                        onLongPress = { selectedStepLabel = step.label },
                        onDoubleTap = { detailedStepLabel = step.label },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = timelineStatusColor(activeStep.status).copy(alpha = 0.13f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Current: ${activeStep.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF102A43)
                    )
                    Text(
                        "Tap a stage for its explanation · double-tap for full details",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF52606D)
                    )
                }
            }

            quickStep?.let { step ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEEF4FC)
                ) {
                    Column(
                        modifier = Modifier.padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(step.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            step.effectiveDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF52606D)
                        )
                    }
                }
            }
        }
    }

    detailedStep?.let { step ->
        AlertDialog(
            onDismissRequest = { detailedStepLabel = null },
            confirmButton = {
                TextButton(onClick = { detailedStepLabel = null }) { Text("Close") }
            },
            title = { Text(step.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Status: ${step.status.name}", fontWeight = FontWeight.SemiBold)
                    Text(step.effectiveDescription())
                    if (step.inputs.isNotBlank()) Text("Inputs: ${step.inputs}")
                    if (step.outputs.isNotBlank()) Text("Outputs: ${step.outputs}")
                    if (step.reason.isNotBlank()) Text("Why this stage exists: ${step.reason}")
                    Text(
                        timelineStatusMeaning(step.status),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF52606D)
                    )
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TimelineCheckpoint(
    step: DiagnosticTimelineStep,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier
) {
    val color = timelineStatusColor(step.status)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val centerY = size.height / 2f
            if (hasPrevious) {
                drawLine(
                    color = if (step.status == DiagnosticTimelineStatus.PENDING) Color(0xFFD5DCE5) else color,
                    start = Offset(0f, centerY),
                    end = Offset(size.width / 2f, centerY),
                    strokeWidth = 3.dp.toPx()
                )
            }
            if (hasNext) {
                drawLine(
                    color = if (step.status == DiagnosticTimelineStatus.COMPLETE) color else Color(0xFFD5DCE5),
                    start = Offset(size.width / 2f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
        Surface(
            modifier = Modifier
                .size(48.dp)
                .testTag("timeline_checkpoint_${step.label}")
                .semantics {
                    role = Role.Button
                    contentDescription = "${step.label} process stage"
                    stateDescription = step.status.name.lowercase().replace('_', ' ')
                }
                .combinedClickable(
                    role = Role.Button,
                    onClick = onTap,
                    onLongClick = onLongPress,
                    onDoubleClick = onDoubleTap
                ),
            shape = CircleShape,
            color = color,
            shadowElevation = if (step.status == DiagnosticTimelineStatus.RUNNING) 5.dp else 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = timelineStatusSymbol(step.status),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun DiagnosticTimelineStep.effectiveDescription(): String =
    description.ifBlank {
        "This checkpoint represents the ‘$label’ stage of the current operation. Its status is updated from the real process state."
    }

private fun timelineStatusColor(status: DiagnosticTimelineStatus): Color =
    when (status) {
        DiagnosticTimelineStatus.PENDING -> Color(0xFF9AA5B1)
        DiagnosticTimelineStatus.RUNNING -> Color(0xFF1565C0)
        DiagnosticTimelineStatus.COMPLETE -> Color(0xFF2E7D32)
        DiagnosticTimelineStatus.FAILED -> Color(0xFFC62828)
        DiagnosticTimelineStatus.CANCELLED -> Color(0xFFEF6C00)
    }

private fun timelineStatusSymbol(status: DiagnosticTimelineStatus): String =
    when (status) {
        DiagnosticTimelineStatus.PENDING -> "○"
        DiagnosticTimelineStatus.RUNNING -> "●"
        DiagnosticTimelineStatus.COMPLETE -> "✓"
        DiagnosticTimelineStatus.FAILED -> "!"
        DiagnosticTimelineStatus.CANCELLED -> "×"
    }

private fun timelineStatusMeaning(status: DiagnosticTimelineStatus): String =
    when (status) {
        DiagnosticTimelineStatus.PENDING -> "This stage has not started yet."
        DiagnosticTimelineStatus.RUNNING -> "This is the stage currently doing work."
        DiagnosticTimelineStatus.COMPLETE -> "This stage completed successfully."
        DiagnosticTimelineStatus.FAILED -> "The operation stopped or reported a failure at this stage."
        DiagnosticTimelineStatus.CANCELLED -> "The user requested a safe stop during this stage."
    }
