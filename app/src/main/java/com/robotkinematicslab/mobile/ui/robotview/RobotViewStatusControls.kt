package com.robotkinematicslab.mobile.ui.robotview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import java.util.Locale

@Composable
internal fun RobotViewStatusCard(
    isIk: Boolean,
    robotName: String?,
    target: Vec3?,
    ikState: RobotViewIkState,
    fkStatus: FKStatus,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val phase = if (isIk) ikState.phase else when (fkStatus) {
        FKStatus.SUCCESS -> RobotViewIkPhase.VERIFIED
        FKStatus.SUCCESS_WITH_WARNING -> RobotViewIkPhase.WARNING
        else -> RobotViewIkPhase.FAILED
    }
    val label = when (phase) {
        RobotViewIkPhase.INACTIVE -> "○ Inactive"
        RobotViewIkPhase.CALCULATING -> "◌ Calculating"
        RobotViewIkPhase.VERIFIED -> "✓ Verified result"
        RobotViewIkPhase.WARNING -> "△ Verified with warning"
        RobotViewIkPhase.FAILED -> "× Not verified"
    }
    val accent = when (phase) {
        RobotViewIkPhase.VERIFIED -> Color(0xFF216E39)
        RobotViewIkPhase.WARNING -> Color(0xFF875400)
        RobotViewIkPhase.FAILED -> colors.error
        else -> colors.primary
    }
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).testTag("robot-view-result-status").politeLiveRegion()
            .semantics(mergeDescendants = true) { stateDescription = label }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${if (isIk) "IK" else "FK"} · $label", color = accent, style = MaterialTheme.typography.titleMedium)
            Text("Robot: ${robotName ?: "No valid robot applied"}")
            if (isIk) {
                Text("Target: ${target?.let(::targetLabel) ?: "Not selected"}")
                Text(ikState.message)
                ikState.checkedErrorMeters?.let { error ->
                    Text("Independent FK position error: ${String.format(Locale.US, "%.6g m (%.3f µm)", error, error * 1e6)}")
                    Text("Required tolerance: ${String.format(Locale.US, "%.3g µm", ikState.request!!.context.toleranceMeters * 1e6)}")
                }
                ikState.response?.let { response ->
                    Text("Result source: ${response.aiPath?.name?.lowercase()?.replace('_', ' ') ?: "deterministic IK"}")
                    if (response.aiPath != null) Text("Unrefined AI proposal error: ${response.neuralProposalErrorMeters?.let { String.format(Locale.US, "%.6g m", it) } ?: "not available or rejected"}")
                }
            } else {
                Text(when (fkStatus) {
                    FKStatus.SUCCESS -> "Forward kinematics output accepted for the current joint values."
                    FKStatus.SUCCESS_WITH_WARNING -> "Forward kinematics accepted with a numerical repair warning."
                    FKStatus.INVALID_INPUT -> "Forward kinematics blocked by invalid robot or joint input."
                    FKStatus.NUMERICAL_FAILURE -> "Forward kinematics output failed numerical validation."
                })
            }
        }
    }
}

@Composable
internal fun RobotViewAiControls(
    enabled: Boolean,
    availability: RobotViewAiAvailability,
    refreshing: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().testTag("robot-view-ai-section"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AccessibleSelectionButton(
                selected = enabled && availability.available,
                label = "1 micrometre position AI · ${if (enabled && availability.available) "ON" else "OFF"}",
                enabled = availability.available,
                onClick = onToggle,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().testTag("robot-view-ai-toggle")
            )
        }
        Text(availability.reason, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("robot-view-ai-availability"),
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onRefresh, enabled = !refreshing,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().testTag("robot-view-ai-refresh")) {
            Text("Refresh 1 micrometre models")
        }
    }
}

private fun targetLabel(target: Vec3): String = String.format(Locale.US,
    "x=%.6g m · y=%.6g m · z=%.6g m", target.x, target.y, target.z)
