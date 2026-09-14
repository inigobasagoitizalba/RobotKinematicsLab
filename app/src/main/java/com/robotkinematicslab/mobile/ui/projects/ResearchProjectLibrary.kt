package com.robotkinematicslab.mobile.ui.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.ui.shared.GlobalHeaderActions
import java.text.DateFormat
import java.util.Date

@Composable
fun ResearchProjectLibrary(
    projects: List<ResearchProject>,
    onOpenProject: (ResearchProject) -> Unit,
    onCreateProject: (name: String, objective: String) -> Unit,
    onUpdateProject: (project: ResearchProject, name: String, objective: String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var createDialogOpen by rememberSaveable { mutableStateOf(false) }
    var projectBeingEditedId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("project-library"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ProjectLibraryHero(
                onCreate = { createDialogOpen = true },
                onOpenSettings = onOpenSettings
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Your research projects",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose one workspace before opening robots, experiments or results.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(projects, key = { _, project -> project.id }) { _, project ->
            ResearchProjectCard(
                project = project,
                onOpen = { onOpenProject(project) },
                onEdit = { projectBeingEditedId = project.id }
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "Each new project has isolated robots, sessions, datasets, models and training evidence. The original project keeps all existing files in place.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (createDialogOpen) {
        ProjectEditorDialog(
            title = "Create research project",
            confirmLabel = "Create project",
            initialName = "",
            initialObjective = "",
            onDismiss = { createDialogOpen = false },
            onConfirm = { name, objective ->
                onCreateProject(name, objective)
                createDialogOpen = false
            }
        )
    }

    projects.firstOrNull { it.id == projectBeingEditedId }?.let { project ->
        ProjectEditorDialog(
            title = "Edit project details",
            confirmLabel = "Save changes",
            initialName = project.name,
            initialObjective = project.objective,
            onDismiss = { projectBeingEditedId = null },
            onConfirm = { name, objective ->
                onUpdateProject(project, name, objective)
                projectBeingEditedId = null
            }
        )
    }
}

@Composable
private fun ProjectLibraryHero(
    onCreate: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = colors.primaryContainer.copy(alpha = 0.72f),
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ROBOT KINEMATICS LAB",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary
                    )
                    Text(
                        text = "Research project library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                GlobalHeaderActions(
                    onOpenSettings = onOpenSettings
                )
            }
            Text(
                text = "Keep each hypothesis, robot family and evidence trail in a clear workspace.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onCreate,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("create-project-button")
            ) {
                Text("＋  New project")
            }
        }
    }
}

@Composable
private fun ResearchProjectCard(
    project: ResearchProject,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("project-card:${project.id}"),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = project.objective.ifBlank { "No research objective has been added yet." },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = projectRecencyLabel(project),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(0.38f)) {
                    Text("Edit")
                }
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(0.62f).testTag("open-project:${project.id}")
                ) {
                    Text("Open project")
                }
            }
        }
    }
}

internal fun projectRecencyLabel(project: ResearchProject): String =
    "Last updated ${formatProjectDate(project.updatedAtEpochMillis)} · " +
        if (project.usesLegacyWorkspace) "Existing workspace" else "Isolated workspace"

@Composable
internal fun ProjectEditorDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialObjective: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var objective by rememberSaveable(initialObjective) { mutableStateOf(initialObjective) }
    var completionRequested by rememberSaveable(initialName, initialObjective) {
        mutableStateOf(false)
    }
    val valid = isProjectEditorInputValid(name, objective)

    AlertDialog(
        onDismissRequest = {
            if (!completionRequested) {
                completionRequested = true
                onDismiss()
            }
        },
        title = { Text(title) },
        text = {
            Column(
                modifier =
                    Modifier
                        .testTag("project-editor-dialog")
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Give the workspace a short name and state the scientific question it is meant to answer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    modifier = Modifier.fillMaxWidth().testTag("project-name-field"),
                    label = { Text("Project name") },
                    supportingText = { Text("${name.length}/80") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = objective,
                    onValueChange = { if (it.length <= 280) objective = it },
                    modifier = Modifier.fillMaxWidth().testTag("project-objective-field"),
                    label = { Text("Research objective (optional)") },
                    supportingText = { Text("${objective.length}/280") },
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!completionRequested) {
                        completionRequested = true
                        onDismiss()
                    }
                },
                enabled = !completionRequested,
                modifier = Modifier.testTag("project-editor-cancel")
            ) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!completionRequested) {
                        completionRequested = true
                        onConfirm(name.trim(), objective.trim())
                    }
                },
                enabled = valid && !completionRequested,
                modifier = Modifier.testTag("project-editor-confirm")
            ) {
                Text(confirmLabel)
            }
        }
    )
}

internal fun isProjectEditorInputValid(name: String, objective: String): Boolean {
    val normalizedName = name.trim()
    val normalizedObjective = objective.trim()
    return normalizedName.isNotEmpty() &&
        normalizedName.length <= 80 &&
        normalizedObjective.length <= 280 &&
        !normalizedName.any(Char::isISOControl) &&
        !normalizedObjective.any { character ->
            character.isISOControl() && character != '\n' && character != '\t'
        }
}

private fun formatProjectDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
