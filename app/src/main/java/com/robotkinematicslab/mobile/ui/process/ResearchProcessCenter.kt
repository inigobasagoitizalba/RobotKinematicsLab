package com.robotkinematicslab.mobile.ui.process

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessSnapshot
import com.robotkinematicslab.mobile.process.ResearchProcessStatus
import com.robotkinematicslab.mobile.process.ResearchProcessSummary
import com.robotkinematicslab.mobile.process.ResearchResultReference
import kotlinx.coroutines.delay

@Composable
fun ResearchProcessCenterOverlay(
    modifier: Modifier = Modifier,
    onOpenResult: (ResearchResultReference) -> Unit = {}
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val coordinator = remember(applicationContext) { ResearchProcessCoordinator.get(applicationContext) }
    val processes by coordinator.processes.collectAsState()
    val summary = remember(processes) { ResearchProcessSummary.from(processes) }
    var centerVisible by remember { mutableStateOf(false) }
    var permissionExplanationVisible by remember { mutableStateOf(false) }
    var completionVisible by remember { mutableStateOf(false) }
    var completionProcess by remember { mutableStateOf<ResearchProcessSnapshot?>(null) }
    var lastTerminalEvent by remember {
        mutableStateOf(summary.recent.firstOrNull()?.let { it.jobId to it.updatedAtEpochMillis })
    }
    val preferences = remember(applicationContext) {
        applicationContext.getSharedPreferences(NOTIFICATION_PROMPT_PREFERENCES, 0)
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionExplanationVisible = false
        }

    LaunchedEffect(summary.active.isNotEmpty()) {
        if (
            summary.active.isNotEmpty() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean(NOTIFICATION_PROMPT_SHOWN, false)
        ) {
            permissionExplanationVisible = true
        }
    }

    val newestTerminal = summary.recent.firstOrNull()
    LaunchedEffect(newestTerminal?.id, newestTerminal?.updatedAtEpochMillis) {
        val terminal = newestTerminal ?: return@LaunchedEffect
        val event = terminal.jobId to terminal.updatedAtEpochMillis
        if (lastTerminalEvent != event) {
            lastTerminalEvent = event
            completionProcess = terminal
            completionVisible = true
            delay(COMPLETION_CARD_DURATION_MILLIS)
            completionVisible = false
        }
    }

    Box(modifier = modifier) {
        when {
            summary.active.isNotEmpty() ->
                ActiveProcessDock(
                    summary = summary,
                    onOpen = { centerVisible = true },
                    modifier = Modifier.fillMaxWidth()
                )

            completionVisible && completionProcess != null ->
                CompletionDock(
                    process = requireNotNull(completionProcess),
                    onOpen = { centerVisible = true },
                    onDismiss = { completionVisible = false },
                    modifier = Modifier.fillMaxWidth()
                )
        }
    }

    if (centerVisible) {
        ResearchProcessCenterDialog(
            summary = summary,
            onDismiss = { centerVisible = false },
            onCancel = coordinator::requestCancel,
            onClearFinished = coordinator::clearFinished,
            onOpenResult = { reference ->
                centerVisible = false
                onOpenResult(reference)
            }
        )
    }

    if (permissionExplanationVisible) {
        AlertDialog(
            onDismissRequest = {
                preferences.edit { putBoolean(NOTIFICATION_PROMPT_SHOWN, true) }
                permissionExplanationVisible = false
            },
            title = { Text("Keep an eye on long research runs") },
            text = {
                Text(
                    "Allow notifications to see live progress and completion alerts while you move between screens or use another app. " +
                        "The app never starts research work by itself."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        preferences.edit { putBoolean(NOTIFICATION_PROMPT_SHOWN, true) }
                        permissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
                    }
                ) { Text("Enable notifications") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        preferences.edit { putBoolean(NOTIFICATION_PROMPT_SHOWN, true) }
                        permissionExplanationVisible = false
                    }
                ) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun ActiveProcessDock(
    summary: ResearchProcessSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lead = summary.active.first()
    Surface(
        modifier = modifier.testTag("research-process-dock").clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        shadowElevation = 9.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (summary.active.size == 1) lead.title else "${summary.active.size} research processes active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (summary.active.size == 1) lead.stage else "Tap to inspect every process",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("OPEN  ›", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
            val progress = summary.overallProgressFraction
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompletionDock(
    process: ResearchProcessSnapshot,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = statusTone(process.status)
    Surface(
        modifier = modifier.testTag("research-process-result").clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = tone.copy(alpha = 0.96f),
        contentColor = Color.White,
        shadowElevation = 9.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(statusSymbol(process.status), style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(process.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(process.stage, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White) }
        }
    }
}

@Composable
private fun ResearchProcessCenterDialog(
    summary: ResearchProcessSummary,
    onDismiss: () -> Unit,
    onCancel: (String) -> Boolean,
    onClearFinished: () -> Unit,
    onOpenResult: (ResearchResultReference) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Research activity")
                Text(
                    "${summary.active.size} active · ${summary.recent.size} recent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (summary.active.isEmpty() && summary.recent.isEmpty()) {
                Text("No research processes have run in this app session.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (summary.active.isNotEmpty()) {
                        item { SectionLabel("ACTIVE NOW") }
                        items(summary.active, key = ResearchProcessSnapshot::jobId) { process ->
                            ProcessCard(process = process, onCancel = onCancel, onOpenResult = onOpenResult)
                        }
                    }
                    if (summary.recent.isNotEmpty()) {
                        item { SectionLabel("RECENT RESULTS") }
                        items(summary.recent.take(10), key = ResearchProcessSnapshot::jobId) { process ->
                            ProcessCard(process = process, onCancel = onCancel, onOpenResult = onOpenResult)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (summary.recent.isNotEmpty()) {
                TextButton(onClick = onClearFinished) { Text("Clear finished") }
            }
        }
    )
}

@Composable
private fun ProcessCard(
    process: ResearchProcessSnapshot,
    onCancel: (String) -> Boolean,
    onOpenResult: (ResearchResultReference) -> Unit
) {
    val tone = statusTone(process.status)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("research-process:${process.jobId}"),
        shape = RoundedCornerShape(14.dp),
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(statusSymbol(process.status))
                Column(modifier = Modifier.weight(1f)) {
                    Text(process.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${process.kind.displayName} · ${process.status.displayName()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tone
                    )
                }
                process.safeProgressFraction?.let {
                    Text("${(it * 100.0).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }
            Text(process.stage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(process.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (process.status.isActive) {
                    "Started ${formatProcessMoment(process.startedAtEpochMillis)} · Job ${process.jobId}"
                } else {
                    "Finished ${formatProcessMoment(process.updatedAtEpochMillis)} · Job ${process.jobId}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            process.safeProgressFraction?.let {
                LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.fillMaxWidth(), color = tone)
            }
            if (process.status.isActive && process.canCancel) {
                Text(
                    if (process.id == com.robotkinematicslab.mobile.process.ResearchProcessIds.LOCAL_TRAINING) {
                        "Stops after the current mini-batch. A run becomes reopenable only after models and evidence are stored; this training engine does not persist a resume checkpoint."
                    } else {
                        "Requests this operation's documented safe boundary. Evidence already committed remains available."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onCancel(process.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Stop safely") }
            }
            process.actionableResult?.let { reference ->
                Button(
                    onClick = { onOpenResult(reference) },
                    modifier = Modifier.fillMaxWidth().testTag("research-process-open-result:${process.jobId}")
                ) { Text("Open this saved result") }
            }
        }
    }
}

private fun formatProcessMoment(epochMillis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(epochMillis))

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
}

@Composable
private fun statusTone(status: ResearchProcessStatus): Color =
    when (status) {
        ResearchProcessStatus.SUCCEEDED -> Color(0xFF2E7D32)
        ResearchProcessStatus.FAILED -> MaterialTheme.colorScheme.error
        ResearchProcessStatus.PAUSED,
        ResearchProcessStatus.CANCELLED,
        ResearchProcessStatus.PAUSING -> Color(0xFFEF6C00)
        ResearchProcessStatus.PREPARING,
        ResearchProcessStatus.RUNNING -> MaterialTheme.colorScheme.primary
    }

private fun statusSymbol(status: ResearchProcessStatus): String =
    when (status) {
        ResearchProcessStatus.SUCCEEDED -> "✓"
        ResearchProcessStatus.FAILED -> "!"
        ResearchProcessStatus.PAUSED -> "Ⅱ"
        ResearchProcessStatus.CANCELLED -> "×"
        ResearchProcessStatus.PAUSING -> "…"
        ResearchProcessStatus.PREPARING -> "◇"
        ResearchProcessStatus.RUNNING -> "●"
    }

private fun ResearchProcessStatus.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private const val NOTIFICATION_PROMPT_PREFERENCES = "research_process_notifications"
private const val NOTIFICATION_PROMPT_SHOWN = "permission_prompt_shown"
private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val COMPLETION_CARD_DURATION_MILLIS = 7_000L
