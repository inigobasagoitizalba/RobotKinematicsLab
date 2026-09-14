package com.robotkinematicslab.mobile.ui.shared.progress

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.diagnostic.DiagnosticDisclosureCard
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiagnosticLoadingCardConfig(
    val runningTitle: String = "Diagnostic Benchmark Running",
    val finishedTitle: String = "Diagnostic Benchmark Progress",
    val progressSectionTitle: String = "Benchmark Progress",
    val completedLabel: String = "Completed calculations",
    val remainingLabel: String = "Calculations left",
    val rateLabel: String = "Calculations per second",
    val showBenchmarkCoordinates: Boolean = true,
    val telemetryProjectName: String = "Robot Kinematics Research",
    val telemetrySessionType: String = "Diagnostic"
)

enum class DiagnosticTimelineStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    CANCELLED
}

data class DiagnosticTimelineStep(
    val label: String,
    val status: DiagnosticTimelineStatus,
    val description: String = "",
    val inputs: String = "",
    val outputs: String = "",
    val reason: String = ""
)

@Composable
fun DiagnosticLoadingProgressCard(
    progressState: DiagnosticProgressState,
    performanceSamples: List<DiagnosticPerformanceSample> = emptyList(),
    config: DiagnosticLoadingCardConfig = DiagnosticLoadingCardConfig(),
    timeline: List<DiagnosticTimelineStep> = emptyList(),
    onOpenChartLibrary: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current.applicationContext
    val activeProject = remember(context) { ResearchProjectRepository(context).activeProject() }
    val telemetryRepository = remember(context) { TelemetrySessionRepository(context) }
    val finalFigureArchiver = remember(context) { FinalTelemetryFigureArchiver(context) }
    var showRawTelemetry by remember {
        mutableStateOf(false)
    }
    var comparisonSessions by remember {
        mutableStateOf<List<StoredTelemetrySession>>(emptyList())
    }

    LaunchedEffect(progressState.isRunning) {
        if (progressState.isRunning) showRawTelemetry = false
    }

    val capturedSamples = performanceSamples.toList()
    val chartSamples =
        remember(capturedSamples, progressState) {
            appendCurrentSampleIfNeeded(capturedSamples, progressState)
        }

    val successfulCompletion =
        !progressState.isRunning &&
            progressState.phase == DiagnosticProgressPhase.COMPLETED &&
            chartSamples.isNotEmpty()
    val completedArchiveKey =
        remember(successfulCompletion, chartSamples) {
            if (successfulCompletion) finalTelemetryArchiveKey(chartSamples) else null
        }
    var archiveAttempt by remember(completedArchiveKey) { mutableIntStateOf(0) }
    var archiveFeedback by remember(completedArchiveKey) {
        mutableStateOf<TelemetryArchiveFeedback>(TelemetryArchiveFeedback.NotStarted)
    }

    // This effect lives above every disclosure. Final evidence is therefore archived even when the
    // user never expands the telemetry UI, and it is based only on the frozen completed sample set.
    LaunchedEffect(
        completedArchiveKey,
        config.telemetrySessionType,
        config.finishedTitle,
        activeProject.id,
        archiveAttempt
    ) {
        if (completedArchiveKey == null) return@LaunchedEffect
        archiveFeedback = TelemetryArchiveFeedback.Saving
        runCatching {
            withContext(Dispatchers.IO) {
                val projectName =
                    config.telemetryProjectName
                        .takeUnless { it == DEFAULT_TELEMETRY_PROJECT_NAME }
                        ?: activeProject.name
                val session =
                    telemetryRepository.save(
                        projectName = projectName,
                        sessionType = config.telemetrySessionType,
                        title = config.finishedTitle,
                        samples = chartSamples
                    )
                val archive =
                    finalFigureArchiver.archive(
                        projectId = activeProject.id,
                        session = session,
                        samples = chartSamples
                    )
                require(archive.complete) {
                    "Final telemetry figure archive is incomplete: ${archive.failures.joinToString()}"
                }
                TelemetryArchiveFeedback.Saved(
                    sessionSampleCount = session.sampleCount,
                    figureCount = archive.generatedFigureCount + archive.existingFigureCount
                )
            }
        }.onSuccess { saved ->
            archiveFeedback = saved
        }.onFailure { error ->
            // Evidence export must be diagnosable without invalidating a completed scientific run.
            Log.e("TelemetryFigureArchive", "Final telemetry figures could not be archived.", error)
            archiveFeedback =
                TelemetryArchiveFeedback.Failed(
                    reason = error.message?.take(180) ?: "unknown storage error"
                )
        }
    }

    val visibleTimeline =
        remember(timeline, progressState.phase) {
            if (timeline.isNotEmpty()) timeline else defaultDiagnosticTimeline(progressState.phase)
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("telemetry-loading")
                .tutorialAnchor(TutorialTargets.TelemetryLoading),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.34f)),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            JargonAwareText(
                text = if (progressState.isRunning) config.runningTitle else config.finishedTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            JargonAwareText(
                text = progressState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { (progressState.percent / 100.0).toFloat() },
                modifier = Modifier.fillMaxWidth().height(11.dp)
            )

            EssentialProgressSnapshot(
                progressState = progressState,
                config = config
            )

            if (successfulCompletion) {
                TelemetryArchiveFeedbackCard(
                    feedback = archiveFeedback,
                    onRetry = { archiveAttempt += 1 },
                    onOpenChartLibrary = onOpenChartLibrary
                )
            }

            LoadingDataDisclosure(
                title = "Process timeline",
                summary = timelineSummary(visibleTimeline),
                testTag = "telemetry-timeline-disclosure"
            ) {
                DiagnosticProcessTimeline(visibleTimeline)
            }

            LoadingDataDisclosure(
                title = "Performance and device telemetry",
                summary = if (chartSamples.isEmpty()) "Waiting for the first sample" else "${chartSamples.size} sample(s) collected",
                testTag = "telemetry-charts-disclosure"
            ) {
                DiagnosticTelemetryDashboard(
                    samples = chartSamples,
                    finished = !progressState.isRunning,
                    comparisonSessions = comparisonSessions
                )
            }

            LoadingDataDisclosure(
                title = "Save or compare this session",
                summary = if (comparisonSessions.isEmpty()) "Optional research record" else "${comparisonSessions.size} comparison session(s)",
                testTag = "telemetry-sessions-disclosure"
            ) {
                DiagnosticTelemetrySessionControls(
                    samples = chartSamples,
                    progressState = progressState,
                    config = config,
                    onComparisonsChanged = { comparisonSessions = it }
                )
            }

            OutlinedButton(
                onClick = { showRawTelemetry = !showRawTelemetry },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TELEMETRY_RAW_BUTTON_TAG)
                    .tutorialAnchor(TutorialTargets.TelemetryRaw)
            ) {
                Text(
                    if (showRawTelemetry) {
                        "Hide raw telemetry"
                    } else if (progressState.isRunning) {
                        "View latest raw telemetry"
                    } else {
                        "View final raw telemetry"
                    }
                )
            }

            if (showRawTelemetry) {
                DiagnosticRawTelemetrySnapshot(
                    progressState = progressState,
                    performanceSamples = chartSamples,
                    config = config
                )
            }
        }
    }
}

internal sealed interface TelemetryArchiveFeedback {
    data object NotStarted : TelemetryArchiveFeedback
    data object Saving : TelemetryArchiveFeedback
    data class Saved(
        val sessionSampleCount: Int,
        val figureCount: Int
    ) : TelemetryArchiveFeedback
    data class Failed(val reason: String) : TelemetryArchiveFeedback
}

internal fun telemetryArchiveFeedbackMessage(feedback: TelemetryArchiveFeedback): String =
    when (feedback) {
        TelemetryArchiveFeedback.NotStarted -> "Preparing the automatic chart archive…"
        TelemetryArchiveFeedback.Saving -> "Saving the session and its complete chart archive…"
        is TelemetryArchiveFeedback.Saved ->
            "Saved ${feedback.sessionSampleCount} telemetry samples and ${feedback.figureCount} chart images in project evidence."
        is TelemetryArchiveFeedback.Failed ->
            "The scientific run completed, but its chart archive could not be saved: ${feedback.reason}"
    }

@Composable
private fun TelemetryArchiveFeedbackCard(
    feedback: TelemetryArchiveFeedback,
    onRetry: () -> Unit,
    onOpenChartLibrary: (() -> Unit)?
) {
    val failed = feedback is TelemetryArchiveFeedback.Failed
    val saved = feedback is TelemetryArchiveFeedback.Saved
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("telemetry-archive-feedback"),
        shape = RoundedCornerShape(12.dp),
        color =
            when {
                failed -> MaterialTheme.colorScheme.errorContainer
                saved -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = telemetryArchiveFeedbackMessage(feedback),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (failed) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface
            )
            if (failed) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().testTag("telemetry-archive-retry")
                ) {
                    Text("Retry chart archive")
                }
            }
            if (saved && onOpenChartLibrary != null) {
                OutlinedButton(
                    onClick = onOpenChartLibrary,
                    modifier = Modifier.fillMaxWidth().testTag("telemetry-open-chart-library")
                ) {
                    Text("Open saved charts")
                }
            }
        }
    }
}

private const val DEFAULT_TELEMETRY_PROJECT_NAME = "Robot Kinematics Research"

@Composable
private fun LoadingDataDisclosure(
    title: String,
    summary: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    DiagnosticDisclosureCard(
        title = title,
        subtitle = summary,
        modifier = Modifier.testTag(testTag)
    ) {
        content()
    }
}

private fun timelineSummary(timeline: List<DiagnosticTimelineStep>): String {
    val completed = timeline.count { it.status == DiagnosticTimelineStatus.COMPLETE }
    val running = timeline.firstOrNull { it.status == DiagnosticTimelineStatus.RUNNING }?.label
    return when {
        running != null -> "$running · $completed/${timeline.size} stages complete"
        timeline.any { it.status == DiagnosticTimelineStatus.FAILED } -> "Stopped with an error"
        timeline.isNotEmpty() && completed == timeline.size -> "All ${timeline.size} stages complete"
        else -> "$completed/${timeline.size} stages complete"
    }
}

@Composable
private fun EssentialProgressSnapshot(
    progressState: DiagnosticProgressState,
    config: DiagnosticLoadingCardConfig
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.94f), RoundedCornerShape(13.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        JargonAwareText(
            config.progressSectionTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF102A43)
        )
        CompactProgressRow("Progress", "${formatProgressDouble(progressState.percent)}%")
        CompactProgressRow(config.completedLabel, "${progressState.completedRuns} / ${progressState.totalRuns}")
        CompactProgressRow(config.remainingLabel, progressState.remainingRuns.toString())
        CompactProgressRow("Elapsed time", formatProgressSeconds(progressState.elapsedSeconds))
        if (config.showBenchmarkCoordinates) {
            CompactProgressRow("Current seed", progressState.currentSeed?.toString() ?: "N/A")
            CompactProgressRow("Current link count", progressState.currentLinkCount?.toString() ?: "N/A")
            CompactProgressRow("Current topology mode", progressState.currentJointMode?.name ?: "N/A")
        }
    }
}

@Composable
private fun CompactProgressRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JargonAwareText(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

private fun appendCurrentSampleIfNeeded(
    captured: List<DiagnosticPerformanceSample>,
    progressState: DiagnosticProgressState
): List<DiagnosticPerformanceSample> {
    val latest = captured.lastOrNull()
    val matchesLatest =
        latest != null &&
            abs(latest.elapsedSeconds - progressState.elapsedSeconds) < 1e-9 &&
            latest.completedRuns == progressState.completedRuns &&
            latest.phase == progressState.phase

    if (matchesLatest) return captured

    val current =
        DiagnosticPerformanceSample.fromProgressState(
            sampleIndex = (latest?.sampleIndex ?: 0) + 1,
            progressState = progressState
        )
    return captured + current
}

private fun defaultDiagnosticTimeline(
    currentPhase: DiagnosticProgressPhase
): List<DiagnosticTimelineStep> {
    val stages =
        listOf(
            PhaseDescription(DiagnosticProgressPhase.PLANNING, "Plan", "Validates the request and builds the deterministic execution plan."),
            PhaseDescription(DiagnosticProgressPhase.AUDITING_TOPOLOGY, "Topology", "Audits robot definitions, joint modes and link-count coverage before numerical work."),
            PhaseDescription(DiagnosticProgressPhase.GENERATING_TARGETS, "Targets", "Creates reproducible reachable and unreachable target cases from the configured seeds."),
            PhaseDescription(DiagnosticProgressPhase.ORACLE_CHECKS, "Oracle", "Checks FK-proven targets independently before sequential recovery is evaluated."),
            PhaseDescription(DiagnosticProgressPhase.SEQUENTIAL_RUNS, "Sequential", "Runs ordered IK recovery and captures its numerical evidence."),
            PhaseDescription(DiagnosticProgressPhase.AGGREGATING, "Aggregate", "Combines runs into diagnostics, distributions, verdicts and export-ready records.")
        )

    val currentIndex = stages.indexOfFirst { it.phase == currentPhase }
    return stages.mapIndexed { index, stage ->
        val status =
            when {
                currentPhase == DiagnosticProgressPhase.COMPLETED -> DiagnosticTimelineStatus.COMPLETE
                currentPhase == DiagnosticProgressPhase.FAILED && index == stages.lastIndex -> DiagnosticTimelineStatus.FAILED
                currentIndex < 0 -> DiagnosticTimelineStatus.PENDING
                index < currentIndex -> DiagnosticTimelineStatus.COMPLETE
                index == currentIndex -> DiagnosticTimelineStatus.RUNNING
                else -> DiagnosticTimelineStatus.PENDING
            }
        DiagnosticTimelineStep(stage.label, status, stage.description)
    }
}

private data class PhaseDescription(
    val phase: DiagnosticProgressPhase,
    val label: String,
    val description: String
)

private fun formatProgressDouble(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f", value) else "N/A"

private fun formatProgressSeconds(value: Double): String {
    if (!value.isFinite()) return "N/A"
    val totalSeconds = value.roundToInt().coerceAtLeast(0)
    return if (totalSeconds >= 60) "${totalSeconds / 60}m ${totalSeconds % 60}s" else "${totalSeconds}s"
}
