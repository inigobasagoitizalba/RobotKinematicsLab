package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

private enum class ResultFolder(
    val label: String,
    val icon: String,
    val description: String
) {
    OVERVIEW("Overview", "◎", "Verdict and executive result"),
    QUICK_GRAPHS("Quick charts", "▥", "Five compact visual summaries"),
    VALIDATION("Validation", "✓", "Method, configuration and topology"),
    METRICS("Metrics", "Σ", "Distributions and grouped analytics"),
    RECOVERY("Recovery", "↻", "Limits, fallback and transitions"),
    EVIDENCE("Evidence", "≡", "Per-case records and full report")
}

private data class NestedFolder(
    val id: String,
    val title: String,
    val subtitle: String
)

@Composable
fun DiagnosticResultsExplorer(
    report: Layer1DiagnosticReport,
    onOpenCharts: () -> Unit
) {
    val tutorialReporter = LocalTutorialActionReporter.current
    var selectedFolder by remember(report) { mutableStateOf(ResultFolder.OVERVIEW) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("diagnostics-results")
                .tutorialAnchor(TutorialTargets.DiagnosticsResults),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Diagnostic Results", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Open one folder at a time. Heavy report sections are not rendered until you request them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics-result-folders")
                        .tutorialAnchor(TutorialTargets.DiagnosticsResultFolders),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultFolder.entries.chunked(2).forEach { rowFolders ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowFolders.forEach { folder ->
                            FolderButton(
                                selected = selectedFolder == folder,
                                label = "${folder.icon} ${folder.label}",
                                onClick = {
                                    if (selectedFolder != folder) {
                                        selectedFolder = folder
                                        tutorialReporter.report(
                                            target = TutorialTargets.DiagnosticsResultFolders,
                                            interaction = TutorialInteraction.CHOOSE,
                                            detail = "Diagnostic result folder selected: ${folder.name}."
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowFolders.size == 1) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${selectedFolder.icon} ${selectedFolder.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedFolder.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = {
                    onOpenCharts()
                    tutorialReporter.report(
                        target = TutorialTargets.DiagnosticsCharts,
                        interaction = TutorialInteraction.TAP,
                        detail = "Interactive charts workspace opened."
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics-charts")
                        .tutorialAnchor(
                            targetId = TutorialTargets.DiagnosticsCharts,
                            actionEnabled = false
                        )
            ) {
                Text("Open interactive charts")
            }
        }
    }

    when (selectedFolder) {
        ResultFolder.OVERVIEW ->
            OverviewFolder(report = report)
        ResultFolder.QUICK_GRAPHS -> QuickGraphsFolder(report)
        ResultFolder.VALIDATION -> ValidationFolder(report)
        ResultFolder.METRICS -> MetricsFolder(report)
        ResultFolder.RECOVERY -> RecoveryFolder(report)
        ResultFolder.EVIDENCE -> EvidenceFolder(report)
    }
}

@Composable
private fun OverviewFolder(
    report: Layer1DiagnosticReport
) {
    DiagnosticFinalVerdictCard(report)
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("summary", "Executive metrics", "Acceptance, error and improvement summary"),
                NestedFolder("progress", "Progress and seed", "Progress classes and seed-distance buckets"),
                NestedFolder("motion", "Motion and saturation", "Travel, movement and iteration pressure")
            )
    ) { selected ->
        when (selected) {
            "summary" -> DiagnosticSummaryCard(report)
            "progress" -> ProgressAndSeedBucketCard(report)
            "motion" -> ImprovementAndMotionCard(report)
        }
    }
}

@Composable
private fun QuickGraphsFolder(report: Layer1DiagnosticReport) {
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("links", "Success by link count", "Strict acceptance across robot sizes"),
                NestedFolder("failures", "Failure codes", "Which solver outcomes dominate"),
                NestedFolder("error", "Final-error buckets", "Distribution of terminal Cartesian error"),
                NestedFolder("iterations", "Iteration buckets", "Solver effort distribution"),
                NestedFolder("seeds", "Seed sensitivity", "Variation between deterministic seeds")
            )
    ) { selected ->
        when (selected) {
            "links" -> DiagnosticSuccessByLinkCountGraphCard(report)
            "failures" -> DiagnosticFailureCodeGraphCard(report)
            "error" -> DiagnosticFinalErrorBucketGraphCard(report)
            "iterations" -> DiagnosticIterationBucketGraphCard(report)
            "seeds" -> DiagnosticSeedSensitivityGraphCard(report)
        }
    }
}

@Composable
private fun ValidationFolder(report: Layer1DiagnosticReport) {
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("warnings", "Warnings and runtime risk", "Safe-mode claim restrictions and warnings"),
                NestedFolder("solver", "IK configuration", "Exact deterministic solver parameters"),
                NestedFolder("policy", "Benchmark policy", "Sampling and reporting rules"),
                NestedFolder("auto", "Automatic benchmark check", "Preset-compliance evidence"),
                NestedFolder("topology", "Topology audit", "Robot-definition validity by topology")
            )
    ) { selected ->
        when (selected) {
            "warnings" -> BenchmarkWarningsCard(report)
            "solver" -> IkConfigUsedCard(report)
            "policy" -> BenchmarkPolicyCard(report)
            "auto" -> AutoBenchmarkCheckCard(report)
            "topology" -> DiagnosticTopologyAuditCard(report)
        }
    }
}

@Composable
private fun MetricsFolder(report: Layer1DiagnosticReport) {
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("ratios", "Diagnostic ratios", "Core acceptance and recovery ratios"),
                NestedFolder("status", "Status distributions", "Solver status and detail codes"),
                NestedFolder("errors", "Error distributions", "Error bands by expected class"),
                NestedFolder("links", "Link-count analytics", "Aggregates grouped by robot size"),
                NestedFolder("topologies", "Topology analytics", "Aggregates grouped by joint topology"),
                NestedFolder("seeds", "Seed analytics", "Aggregates grouped by random seed")
            )
    ) { selected ->
        when (selected) {
            "ratios" -> DiagnosticChartsCard(report)
            "status" -> DiagnosticStatusDistributionCard(report)
            "errors" -> DiagnosticErrorDistributionCard(report)
            "links" -> DiagnosticLinkCountAnalyticsCard(report)
            "topologies" -> DiagnosticTopologyAnalyticsCard(report)
            "seeds" -> DiagnosticSeedAnalyticsCard(report)
        }
    }
}

@Composable
private fun RecoveryFolder(report: Layer1DiagnosticReport) {
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("limits", "Joint-limit pressure", "Near-limit behavior and pressure ranking"),
                NestedFolder("fallback", "Fallback and clamp", "Recovery interventions and clamping"),
                NestedFolder("transitions", "Difficult transitions", "Source-to-destination recovery matrix"),
                NestedFolder("extremes", "Best and worst runs", "Extremal numerical cases"),
                NestedFolder("failures", "Reachable failures", "Why FK-proven targets failed IK")
            )
    ) { selected ->
        when (selected) {
            "limits" -> DiagnosticJointLimitCard(report)
            "fallback" -> DiagnosticFallbackSummaryCard(report)
            "transitions" -> DiagnosticTransitionCard(report)
            "extremes" -> DiagnosticExtremeRunsCard(report)
            "failures" -> DiagnosticReachableFailureInsightCard(report)
        }
    }
}

@Composable
private fun EvidenceFolder(report: Layer1DiagnosticReport) {
    DiagnosticNestedFolderMenu(
        options =
            listOf(
                NestedFolder("cases", "Per-target cases", "Aggregated evidence for each target"),
                NestedFolder("runs", "Run preview", "Inspectable oracle and sequential runs"),
                NestedFolder("report", "Full readable report", "Copyable scientific text export")
            )
    ) { selected ->
        when (selected) {
            "cases" -> DiagnosticPerCaseCard(report)
            "runs" -> DiagnosticRunPreviewCard(report)
            "report" -> DiagnosticReadableReportCard(report)
        }
    }
}

@Composable
private fun DiagnosticNestedFolderMenu(
    options: List<NestedFolder>,
    content: @Composable (String) -> Unit
) {
    var selectedId by remember(options) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = selectedId == option.id
            Surface(
                onClick = { selectedId = if (selected) null else option.id },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                border =
                    BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (selected) "▾" else "▸",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.title, fontWeight = FontWeight.SemiBold)
                        Text(
                            option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (selected) content(option.id)
        }
    }
}

@Composable
private fun FolderButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
