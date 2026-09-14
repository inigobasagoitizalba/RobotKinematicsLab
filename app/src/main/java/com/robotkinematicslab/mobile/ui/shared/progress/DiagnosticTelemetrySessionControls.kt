package com.robotkinematicslab.mobile.ui.shared.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DiagnosticTelemetrySessionControls(
    samples: List<DiagnosticPerformanceSample>,
    progressState: DiagnosticProgressState,
    config: DiagnosticLoadingCardConfig,
    onComparisonsChanged: (List<StoredTelemetrySession>) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { TelemetrySessionRepository(context) }
    val activeProject = remember(context) { ResearchProjectRepository(context).activeProject() }
    var evidenceGroup by remember(config.telemetryProjectName, activeProject.id) {
        mutableStateOf(
            config.telemetryProjectName
                .takeUnless { it == DEFAULT_TELEMETRY_PROJECT_NAME }
                ?: activeProject.name
        )
    }
    var sessions by remember(repository) { mutableStateOf(repository.listSessions()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSessions by remember { mutableStateOf(false) }
    var savedSessionId by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf("This timeline will be saved automatically after successful completion.") }

    val eligibleToSave =
        !progressState.isRunning &&
            progressState.phase != DiagnosticProgressPhase.FAILED &&
            samples.size >= 2 &&
            progressState.totalRuns > 0 &&
            progressState.completedRuns >= progressState.totalRuns

    LaunchedEffect(eligibleToSave, samples.lastOrNull()?.timestampMs) {
        if (!eligibleToSave) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                repository.save(
                    projectName = evidenceGroup,
                    sessionType = config.telemetrySessionType,
                    title = config.finishedTitle,
                    samples = samples
                )
            }
        }.onSuccess { saved ->
            savedSessionId = saved.id
            sessions = withContext(Dispatchers.IO) { repository.listSessions() }
            saveMessage =
                "Saved ${saved.sampleCount} samples inside ‘${activeProject.name}’" +
                    if (saved.projectName == activeProject.name) "." else " under group ‘${saved.projectName}’."
        }.onFailure { error ->
            saveMessage = "Telemetry could not be saved: ${error.message ?: "unknown storage error"}"
        }
    }

    val selectedSessions = sessions.filter { it.id in selectedIds }
    LaunchedEffect(selectedSessions.map { it.id }) {
        onComparisonsChanged(selectedSessions)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF1F5FB)
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Saved telemetry & comparison",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF102A43)
            )
            Text(
                "Project: ${activeProject.name}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF31558B)
            )
            OutlinedTextField(
                value = evidenceGroup,
                onValueChange = { evidenceGroup = it.take(80) },
                label = { Text("Diagram group label") },
                supportingText = { Text("Optional label inside this project; it never changes where evidence is stored.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(saveMessage, style = MaterialTheme.typography.bodySmall, color = Color(0xFF52606D))

            if (sessions.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showSessions = !showSessions },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showSessions) "Hide saved sessions" else "Compare saved sessions (${selectedIds.size}/2)"
                    )
                }
            }

            if (showSessions) {
                sessions.take(MAX_VISIBLE_SESSIONS).forEach { session ->
                    val selected = session.id in selectedIds
                    val isCurrent = session.id == savedSessionId
                    Surface(
                        onClick = {
                            if (isCurrent) return@Surface
                            selectedIds =
                                if (selected) {
                                    selectedIds - session.id
                                } else {
                                    (selectedIds + session.id).takeLastIds(MAX_COMPARISON_SESSIONS)
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(11.dp),
                        color =
                            when {
                                isCurrent -> Color(0xFFE8F5E9)
                                selected -> Color(0xFFDCE8FA)
                                else -> Color.White
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                when {
                                    isCurrent -> "✓"
                                    selected -> "●"
                                    else -> "○"
                                },
                                color = if (isCurrent) Color(0xFF2E7D32) else Color(0xFF31558B)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${session.projectName} · ${session.sessionType}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 3
                                )
                                Text(
                                    "${formatSessionDate(session.createdAtEpochMillis)} · ${session.sampleCount} samples" +
                                        if (isCurrent) " · current" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF52606D)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Set<String>.takeLastIds(maximum: Int): Set<String> =
    toList().takeLast(maximum).toSet()

private fun formatSessionDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private const val MAX_COMPARISON_SESSIONS = 2
private const val DEFAULT_TELEMETRY_PROJECT_NAME = "Robot Kinematics Research"
private const val MAX_VISIBLE_SESSIONS = 12
