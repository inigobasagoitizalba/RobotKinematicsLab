package com.robotkinematicslab.mobile.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

data class ProjectWorkspaceSummary(
    val robotReady: Boolean,
    val robotCount: Int,
    val diagnosticSessionCount: Int,
    val datasetCount: Int,
    val trainingRunCount: Int,
    val modelCount: Int
)

data class ProjectDestination(
    val eyebrow: String,
    val title: String,
    val description: String,
    val status: String,
    val onClick: () -> Unit,
    val tutorialTarget: TutorialTargetId? = null,
    val actionLabel: String = "Open",
    val enabled: Boolean = true
)

@Composable
fun ProjectHomeScreen(
    project: ResearchProject,
    summary: ProjectWorkspaceSummary,
    onRobotLab: () -> Unit,
    onWorkspace: () -> Unit,
    onDataset: () -> Unit,
    onDiagnostics: () -> Unit,
    onTraining: () -> Unit,
    onStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nextStep = recommendedNextStep(summary, onRobotLab, onDataset, onTraining, onDiagnostics)
    var workflowGuideVisible by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("project-home")
                .tutorialAnchor(TutorialTargets.ProjectHome),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            JargonHelpNotice()
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.26f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("PROJECT OVERVIEW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(
                        project.objective.ifBlank { "Add an objective from the project library to keep this study focused." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = nextStep.onClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("project-continue")
                                .tutorialAnchor(TutorialTargets.ProjectContinue)
                    ) {
                        Text("Continue · ${nextStep.title}")
                    }
                    JargonAwareText(
                        nextStep.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    OutlinedButton(
                        onClick = { workflowGuideVisible = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("How this study workflow fits together")
                    }
                }
            }
        }
        item {
            Text("At a glance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectMetric("ROBOTS", summary.robotCount.toString(), Modifier.weight(1f))
                ProjectMetric("DATASETS", summary.datasetCount.toString(), Modifier.weight(1f))
                ProjectMetric("RUNS", summary.trainingRunCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("Scientific workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            ProjectDestinationCard(
                ProjectDestination(
                    "01 · PREPARE",
                    if (summary.robotCount > 0) "Choose and apply a robot" else "Define the robot",
                    if (summary.robotCount > 0) {
                        "Choose one of ${summary.robotCount} saved definitions or create one, then apply it to Robot Lab."
                    } else {
                        "Create the DH definition and joint limits used by the study."
                    },
                    if (summary.robotReady) "READY" else if (summary.robotCount > 0) "APPLY ONE" else "START HERE",
                    onRobotLab,
                    TutorialTargets.RobotLab
                )
            )
        }
        item {
            ProjectDestinationCard(
                ProjectDestination("02 · VERIFY", "Explore the 3D workspace", "Check reach, boundaries and dead space before generating evidence.", "OPTIONAL CHECK", onWorkspace, TutorialTargets.Workspace)
            )
        }
        item {
            ProjectDestinationCard(
                ProjectDestination("03 · BUILD", "Generate the dataset", "Choose robots, samples, seed protocol and validation policy.", "${summary.datasetCount} SAVED", onDataset, TutorialTargets.Dataset)
            )
        }
        item {
            ProjectDestinationCard(
                ProjectDestination("04 · EXPERIMENT", "Train and compare models", "Run selected feature profiles and inspect their evaluation evidence.", "${summary.trainingRunCount} RUNS", onTraining, TutorialTargets.Training)
            )
        }
        item {
            ProjectDestinationCard(
                ProjectDestination("05 · VALIDATE", "Run diagnostics", "Stress the deterministic pipeline, recovery behaviour and numerical safety.", "${summary.diagnosticSessionCount} SESSIONS", onDiagnostics, TutorialTargets.Diagnostics)
            )
        }
        item {
            ProjectDestinationCard(
                ProjectDestination("06 · REVIEW", "Open project library", "Review datasets, models, sessions and reproducibility artifacts.", "${summary.modelCount} MODELS", onStorage, TutorialTargets.Storage)
            )
        }
    }

    if (workflowGuideVisible) {
        AlertDialog(
            onDismissRequest = { workflowGuideVisible = false },
            title = { Text("From robot to evidence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JargonAwareText("1. Choose or define a robot, then apply it to Robot Lab.")
                    JargonAwareText("2. Optionally inspect its 3D workspace and dead space.")
                    JargonAwareText("3. Generate a reproducible, validated dataset.")
                    JargonAwareText("4. Train and compare selected feature profiles.")
                    JargonAwareText("5. Run diagnostics and inspect numerical reliability.")
                    JargonAwareText("6. Review saved evidence in the project library.")
                    JargonAwareText(
                        "Each workflow card opens the real tool. Technical terms can be inspected from their information affordances.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        workflowGuideVisible = false
                        nextStep.onClick()
                    }
                ) { Text("Start with ${nextStep.title}") }
            },
            dismissButton = {
                TextButton(onClick = { workflowGuideVisible = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ProjectSectionHub(
    eyebrow: String,
    title: String,
    description: String,
    destinations: List<ProjectDestination>,
    showJargonHelp: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("project-section:$title"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showJargonHelp) {
            item { JargonHelpNotice() }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (showJargonHelp) {
                    JargonAwareText(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        destinations.forEach { destination ->
            item(key = destination.title) { ProjectDestinationCard(destination) }
        }
    }
}

@Composable
fun ProjectDestinationCard(destination: ProjectDestination) {
    val colors = MaterialTheme.colorScheme
    val tutorialModifier =
        destination.tutorialTarget?.let { target ->
            Modifier.tutorialAnchor(
                targetId = target,
                actionEnabled = destination.enabled
            )
        } ?: Modifier
    val stableIdModifier =
        destination.tutorialTarget?.let { target ->
            Modifier.testTag("destination-id:${target.value}")
        } ?: Modifier
    Surface(
        onClick = destination.onClick,
        enabled = destination.enabled,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("destination:${destination.title}")
                .then(tutorialModifier),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp).then(stableIdModifier),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(destination.eyebrow, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                Surface(shape = RoundedCornerShape(999.dp), color = colors.secondaryContainer.copy(alpha = 0.72f)) {
                    Text(
                        destination.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSecondaryContainer
                    )
                }
            }
            Text(destination.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(destination.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            ProjectDestinationAction(
                label = destination.actionLabel,
                destinationTitle = destination.title,
                enabled = destination.enabled,
                onClick = destination.onClick,
                modifier = Modifier.testTag("destination-action:${destination.title}")
            )
        }
    }
}

@Composable
private fun ProjectDestinationAction(
    label: String,
    destinationTitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = "$label $destinationTitle"
                }
    ) {
        Text("$label  →", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProjectMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class RecommendedNextStep(
    val title: String,
    val reason: String,
    val onClick: () -> Unit
)

internal fun recommendedNextStep(
    summary: ProjectWorkspaceSummary,
    onRobotLab: () -> Unit,
    onDataset: () -> Unit,
    onTraining: () -> Unit,
    onDiagnostics: () -> Unit
): RecommendedNextStep =
    when {
        !summary.robotReady && summary.robotCount > 0 -> RecommendedNextStep(
            "Apply a robot in Robot Lab",
            "${summary.robotCount} saved robot definitions are available, but none is currently applied to Robot Lab.",
            onRobotLab
        )
        !summary.robotReady -> RecommendedNextStep("Define the robot", "Create and apply a valid robot definition before continuing.", onRobotLab)
        summary.datasetCount == 0 -> RecommendedNextStep("Generate the dataset", "The robot is ready; the next reproducible artifact is a dataset.", onDataset)
        summary.trainingRunCount == 0 -> RecommendedNextStep("Train and compare", "A dataset exists, but this project has no stored training run yet.", onTraining)
        else -> RecommendedNextStep("Run diagnostics", "Core artifacts exist; validate reliability and numerical behaviour next.", onDiagnostics)
    }
