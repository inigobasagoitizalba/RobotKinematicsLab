package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetPlan
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetResourcePolicy
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetResourceResolver
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetState
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetStatus
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadClassifier
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLegend
import com.robotkinematicslab.mobile.ui.shared.resources.resourceLoadColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ContinuousDatasetPanel(
    state: ContinuousDatasetState,
    draftPlan: ContinuousDatasetPlan?,
    deviceProfile: DeviceComputeProfile,
    onStartCurrentPlan: (cpuPercent: Int, memoryPercent: Int) -> Unit,
    onResumeSavedPlan: (cpuPercent: Int, memoryPercent: Int) -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialPlan = state.plan ?: draftPlan
    var cpuPercent by remember(initialPlan?.datasetName, state.isActive) {
        mutableIntStateOf(initialPlan?.cpuBudgetPercent ?: ContinuousDatasetPlan.DEFAULT_CPU_PERCENT)
    }
    var memoryPercent by remember(initialPlan?.datasetName, state.isActive) {
        mutableIntStateOf(initialPlan?.memoryBudgetPercent ?: ContinuousDatasetPlan.DEFAULT_MEMORY_PERCENT)
    }
    val previewPlan = (draftPlan ?: state.plan)?.copy(
        cpuBudgetPercent = cpuPercent,
        memoryBudgetPercent = memoryPercent
    )
    val policy = previewPlan?.let { runCatching { ContinuousDatasetResourceResolver.resolve(it, deviceProfile) }.getOrNull() }
    val canResume = !state.isActive && state.plan != null && state.status != ContinuousDatasetStatus.IDLE
    val currentMatchesSaved = state.plan?.let { saved ->
        draftPlan?.copy(cpuBudgetPercent = saved.cpuBudgetPercent, memoryBudgetPercent = saved.memoryBudgetPercent) == saved
    } == true

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .tutorialAnchor(TutorialTargets.DatasetContinuous),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                JargonAwareText("Continuous dataset growth", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                JargonAwareText(
                    "Grow one reproducible dataset in small, atomic batches while you use other parts of the app.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                JargonAwareText(
                    "Completed batches survive navigation and restart. Android may stop the app after it is closed, so this is not an always-on hidden service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        ContinuousStatusCard(state, draftPlan)

        DatasetDisclosureSection(
            title = "Resource budgets",
            summary = "$cpuPercent% CPU · $memoryPercent% memory ceiling",
            testTag = "continuous-resource-budgets"
        ) {
            ResourceLoadLegend(modifier = Modifier.fillMaxWidth())
            ResourceSliderCard(
                title = "Average CPU budget",
                explanation = "Approximate share over time. At 100%, every safe worker stays busy while one logical core remains available to Android and the interface.",
                value = cpuPercent,
                range = ContinuousDatasetPlan.MIN_CPU_PERCENT..ContinuousDatasetPlan.MAX_CPU_PERCENT,
                enabled = !state.isActive,
                onValueChange = { cpuPercent = it },
                testTag = "continuous-cpu-slider",
                modifier = Modifier.tutorialAnchor(TutorialTargets.ContinuousCpu)
            )
            ResourceSliderCard(
                title = "Working-memory ceiling",
                explanation = "A ceiling inside this app's Android heap allowance. It does not reserve or force the phone to allocate that RAM.",
                value = memoryPercent,
                range = ContinuousDatasetPlan.MIN_MEMORY_PERCENT..ContinuousDatasetPlan.MAX_MEMORY_PERCENT,
                enabled = !state.isActive,
                onValueChange = { memoryPercent = it },
                testTag = "continuous-memory-slider",
                modifier = Modifier.tutorialAnchor(TutorialTargets.ContinuousMemory)
            )

            if (policy != null) ResourcePolicyCard(policy, deviceProfile)
        }

        DatasetDisclosureSection(
            title = "Scientific safeguards",
            summary = "Atomic batches, thermal limits and deterministic append indexes",
            modifier = Modifier.tutorialAnchor(TutorialTargets.ContinuousSafeguards),
            testTag = "continuous-scientific-safeguards"
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    JargonAwareText("• Robot definitions are frozen when Start is pressed.")
                    JargonAwareText("• A complete batch is merged and recorded together, or neither is accepted.")
                    JargonAwareText("• Low memory, severe heat and the storage reserve pause generation automatically.")
                    JargonAwareText("• Forecast thermal pressure reduces workers before Android reaches severe throttling.")
                    JargonAwareText("• Each append receives a deterministic, non-overlapping generation index.")
                }
            }
        }

        if (state.isActive) {
            Button(
                onClick = onPause,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("pause-continuous-dataset")
                        .tutorialAnchor(TutorialTargets.ContinuousPause)
            ) {
                Text(if (state.status == ContinuousDatasetStatus.PAUSING) "Pausing safely…" else "Pause after current safe boundary")
            }
        } else {
            if (canResume) {
                Button(
                    onClick = { onResumeSavedPlan(cpuPercent, memoryPercent) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("resume-continuous-dataset")
                            .tutorialAnchor(TutorialTargets.ContinuousResume)
                ) {
                    Text("Resume saved plan: ${state.plan?.datasetName}")
                }
            }
            OutlinedButton(
                onClick = { onStartCurrentPlan(cpuPercent, memoryPercent) },
                enabled = draftPlan != null && (!canResume || currentMatchesSaved),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("start-continuous-dataset")
                        .tutorialAnchor(TutorialTargets.ContinuousStart)
            ) {
                Text(if (canResume && !currentMatchesSaved) "Resume the pending dataset first" else "Start current Dataset setup")
            }
        }
    }
}

@Composable
private fun ContinuousStatusCard(
    state: ContinuousDatasetState,
    draftPlan: ContinuousDatasetPlan?
) {
    val visiblePlan = state.plan ?: draftPlan
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("continuous-dataset-status")
                .tutorialAnchor(TutorialTargets.ContinuousStatus),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, statusColor(state.status).copy(alpha = 0.5f)),
        color = statusColor(state.status).copy(alpha = 0.10f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                JargonAwareText(state.status.readableName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                JargonAwareText(
                    visiblePlan?.datasetName ?: "Setup incomplete",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            state.currentBatchProgress?.let { progress ->
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                JargonAwareText("Current batch: ${progress.addedRows}/${progress.requestedRows} rows · ${progress.currentRobotName}")
            }
            MetricLine("Dataset total", "${state.committedDatasetRows} committed rows")
            MetricLine("This session", "+${state.rowsAddedThisSession} rows · ${state.committedBatchesThisSession} batches")
            visiblePlan?.let { plan ->
                MetricLine("Selected robots", plan.robotIds.size.toString())
            }
            if (state.workerCount > 0) {
                MetricLine("Active policy", "${state.workerCount} worker(s) · ${state.rowsPerRobotPerBatch} rows/robot/batch")
            }
            state.lastCheckpointEpochMillis?.let { checkpoint ->
                MetricLine("Last checkpoint", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(checkpoint)))
            }
            JargonAwareText(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResourceSliderCard(
    title: String,
    explanation: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val loadLevel = ResourceLoadClassifier.classify(value, range.first, range.last)
    val loadColors = resourceLoadColors(loadLevel)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = loadColors.container,
        border = BorderStroke(1.dp, loadColors.accent.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                JargonAwareText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                JargonAwareText(
                    "$value% · ${loadLevel.displayName}",
                    color = loadColors.accent,
                    fontWeight = FontWeight.Bold
                )
            }
            JargonAwareText(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value.toFloat(),
                onValueChange = { raw -> onValueChange((raw / 5f).roundToInt() * 5) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = ((range.last - range.first) / 5 - 1).coerceAtLeast(0),
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        thumbColor = loadColors.accent,
                        activeTrackColor = loadColors.accent,
                        inactiveTrackColor = loadColors.accent.copy(alpha = 0.24f)
                    ),
                modifier = Modifier.testTag(testTag)
            )
            JargonAwareText(
                loadLevel.explanation,
                style = MaterialTheme.typography.labelSmall,
                color = loadColors.accent
            )
        }
    }
}

@Composable
private fun ResourcePolicyCard(policy: ContinuousDatasetResourcePolicy, device: DeviceComputeProfile) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            JargonAwareText("Effective safe plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            MetricLine("Device", "${device.logicalCpuCores} logical cores · ${formatMiB(device.appHeapLimitBytes)} app heap")
            if (device.thermalHeadroom.isFinite()) {
                MetricLine("10 s thermal forecast", String.format(Locale.US, "%.0f%%", device.thermalHeadroom * 100.0))
            }
            MetricLine("Generator", "${policy.workerCount} worker(s) · ${policy.rowsPerRobotPerBatch} rows per robot and batch")
            MetricLine("Memory ceiling", formatMiB(policy.workingMemoryBudgetBytes))
            JargonAwareText(policy.safetyMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        JargonAwareText(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        JargonAwareText(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun ContinuousDatasetStatus.readableName(): String =
    when (this) {
        ContinuousDatasetStatus.IDLE -> "Ready"
        ContinuousDatasetStatus.PREPARING -> "Preparing"
        ContinuousDatasetStatus.RUNNING -> "Generating"
        ContinuousDatasetStatus.THROTTLED -> "Cooling / waiting"
        ContinuousDatasetStatus.PAUSING -> "Pausing"
        ContinuousDatasetStatus.PAUSED -> "Paused"
        ContinuousDatasetStatus.INTERRUPTED -> "Recovery available"
        ContinuousDatasetStatus.ERROR -> "Stopped safely"
    }

@Composable
private fun statusColor(status: ContinuousDatasetStatus) =
    when (status) {
        ContinuousDatasetStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ContinuousDatasetStatus.THROTTLED, ContinuousDatasetStatus.PAUSING -> MaterialTheme.colorScheme.tertiary
        ContinuousDatasetStatus.ERROR, ContinuousDatasetStatus.INTERRUPTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

private fun formatMiB(bytes: Long): String =
    String.format(Locale.US, "%.0f MiB", bytes.toDouble() / 1_048_576.0)

@Composable
internal fun ContinuousDatasetQualitySummary(
    state: ContinuousDatasetState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Continuous growth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (state.isActive) {
                    "${state.status.readableName()} · ${state.committedDatasetRows} committed rows · +${state.rowsAddedThisSession} this session"
                } else {
                    "${state.status.readableName()} · ${state.plan?.datasetName ?: "no saved plan"}"
                }
            )
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Open continuous dataset controls") }
        }
    }
}
