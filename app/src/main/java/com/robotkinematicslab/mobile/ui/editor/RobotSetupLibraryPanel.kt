package com.robotkinematicslab.mobile.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.ui.dataset.robotstore.IndustrialRobotReferenceGallery
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu

/**
 * Shared Robot Setup entry point for the visual industrial reference library and the editable,
 * validated DH library. The two collections are intentionally kept distinct: a manufacturer
 * datasheet is useful evidence, but it is not automatically a standard-DH definition.
 */
@Composable
internal fun RobotSetupLibraryPanel(
    robots: List<SavedRobot>,
    selectedRobotId: String?,
    editorRobotId: String?,
    activeRobotId: String?,
    hasUnsavedEditorChanges: Boolean,
    onLoadRobotIntoEditor: (SavedRobot) -> Unit,
    onCreateNewRobotDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingReplacement by remember { mutableStateOf<SavedRobot?>(null) }
    var newDraftPending by remember { mutableStateOf(false) }
    val selectedRobot = robots.firstOrNull { it.id == selectedRobotId }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("robot-setup-library"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RobotSetupDisclosureSection(
            title = "Industrial robot references",
            subtitle = "Manufacturer evidence and technical reference cards",
            testTag = "robot-setup-reference-section"
        ) {
            IndustrialRobotReferenceGallery(
                modifier = Modifier.testTag("robot-setup-industrial-references"),
                eyebrow = "ROBOT SETUP REFERENCE LIBRARY",
                title = "Three commercial references",
                description =
                    "Commercial reference cards are separate from the ten synthetic mathematical presets. Manuals can be saved or imported for offline use; these cards are not validated simulation models.",
                boundaryText =
                    "Scientific boundary: seven former cards failed the primary-evidence gate and were removed. " +
                        "These admitted cards are technical references, not silently assumed DH models. " +
                        "Only the validated local DH definitions below can be loaded into Robot Setup."
            )
        }

        RobotSetupDisclosureSection(
            title = "Editable DH robot library",
            subtitle = "${robots.size} local definitions · synthetic presets and user models",
            testTag = "robot-setup-dh-library-section"
        ) {
            EditableDhRobotLibrary(
                robots = robots,
                selectedRobot = selectedRobot,
                editorRobotId = editorRobotId,
                activeRobotId = activeRobotId,
                hasUnsavedEditorChanges = hasUnsavedEditorChanges,
                onCreateNewRobot = {
                    if (hasUnsavedEditorChanges) {
                        newDraftPending = true
                    } else {
                        onCreateNewRobotDraft()
                    }
                },
                onSelectedRobotChange = { robot ->
                    if (hasUnsavedEditorChanges) {
                        pendingReplacement = robot
                    } else {
                        onLoadRobotIntoEditor(robot)
                    }
                }
            )
        }
    }

    pendingReplacement?.let { pending ->
        AlertDialog(
            modifier = Modifier.testTag("robot-setup-unsaved-confirmation"),
            onDismissRequest = { pendingReplacement = null },
            title = { Text("Replace unsaved Robot Setup edits?") },
            text = {
                Text(
                    "Loading ${pending.robot.name} will replace the fields currently in the editor. " +
                        "The active robot remains unchanged until you press Apply Robot."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLoadRobotIntoEditor(pending)
                        pendingReplacement = null
                    },
                    modifier = Modifier.testTag("robot-setup-confirm-load")
                ) {
                    Text("Replace editor fields")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingReplacement = null },
                    modifier = Modifier.testTag("robot-setup-cancel-load")
                ) {
                    Text("Keep my edits")
                }
            }
        )
    }

    if (newDraftPending) {
        AlertDialog(
            modifier = Modifier.testTag("robot-setup-new-unsaved-confirmation"),
            onDismissRequest = { newDraftPending = false },
            title = { Text("Replace unsaved Robot Setup edits?") },
            text = {
                Text(
                    "Creating a new robot will replace the fields currently in the editor. " +
                        "The active robot remains unchanged until you press Apply Robot."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewRobotDraft()
                        newDraftPending = false
                    },
                    modifier = Modifier.testTag("robot-setup-confirm-new")
                ) {
                    Text("Discard edits and create")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { newDraftPending = false },
                    modifier = Modifier.testTag("robot-setup-cancel-new")
                ) {
                    Text("Keep my edits")
                }
            }
        )
    }
}

@Composable
private fun RobotSetupDisclosureSection(
    title: String,
    subtitle: String,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AppDisclosureSection(
        title = title,
        summary = subtitle,
        testTag = testTag,
        content = content
    )
}

@Composable
private fun EditableDhRobotLibrary(
    robots: List<SavedRobot>,
    selectedRobot: SavedRobot?,
    editorRobotId: String?,
    activeRobotId: String?,
    hasUnsavedEditorChanges: Boolean,
    onCreateNewRobot: () -> Unit,
    onSelectedRobotChange: (SavedRobot) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editable DH robot library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selecting a validated definition loads its complete editable copy.",
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
                        text = robots.size.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onCreateNewRobot,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("robot-setup-new-robot")
            ) {
                Text("New robot")
            }

            if (selectedRobot == null) {
                Text(
                    text =
                        if (robots.isEmpty()) {
                            "No validated DH robots are currently available."
                        } else {
                            "No saved robot is selected. Choose one to edit or create a new draft."
                        },
                    modifier = Modifier.testTag("robot-setup-library-empty"),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                CompactSelectionMenu(
                    options = robots,
                    selected = selectedRobot,
                    label = { saved ->
                        buildString {
                            append("${saved.robot.name} · ${saved.robot.joints.size} joints")
                            if (saved.id == editorRobotId) append(" · EDITOR")
                            if (saved.id == activeRobotId) append(" · ACTIVE")
                        }
                    },
                    onSelected = onSelectedRobotChange,
                    optionTestTag = { saved -> "robot-setup-library-option:${saved.id}" }
                )

                RobotSetupLibrarySelectionSummary(
                    savedRobot = selectedRobot,
                    isEditorSource = selectedRobot.id == editorRobotId,
                    isActive = selectedRobot.id == activeRobotId,
                    hasUnsavedEditorChanges = hasUnsavedEditorChanges
                )

                Text(
                    text =
                        when {
                            selectedRobot.id == editorRobotId && hasUnsavedEditorChanges ->
                                "The editor contains pending changes based on ${selectedRobot.robot.name}. Apply Robot validates and transfers this draft to Robot View."
                            selectedRobot.id == editorRobotId && selectedRobot.id == activeRobotId ->
                                "${selectedRobot.robot.name} is selected, loaded in the editor, and active in Robot View."
                            selectedRobot.id == editorRobotId ->
                                "${selectedRobot.robot.name} is loaded in the editor. The active robot changes only after Apply Robot."
                            else ->
                                "Choose a definition to load its complete DH rows. The active robot changes only after Apply Robot."
                        },
                    modifier = Modifier.testTag("robot-setup-library-guidance"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RobotSetupLibrarySelectionSummary(
    savedRobot: SavedRobot,
    isEditorSource: Boolean,
    isActive: Boolean,
    hasUnsavedEditorChanges: Boolean
) {
    val topology =
        savedRobot.robot.joints.joinToString("-") { joint ->
            if (joint.type == JointType.REVOLUTE) "R" else "P"
        }.ifBlank { "No joints" }
    val revoluteCount = savedRobot.robot.joints.count { it.type == JointType.REVOLUTE }
    val prismaticCount = savedRobot.robot.joints.size - revoluteCount

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("robot-setup-library-summary:${savedRobot.id}"),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = savedRobot.robot.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Topology $topology · ${savedRobot.robot.joints.size} DH rows",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "$revoluteCount revolute · $prismaticCount prismatic · VALIDATED LOCAL MODEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text =
                    listOfNotNull(
                        "SELECTED",
                        if (isEditorSource) {
                            if (hasUnsavedEditorChanges) "EDITED DRAFT" else "IN EDITOR"
                        } else {
                            null
                        },
                        if (isActive) "ACTIVE IN VIEW" else null
                    ).joinToString(" · "),
                modifier = Modifier.testTag("robot-setup-library-status:${savedRobot.id}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
