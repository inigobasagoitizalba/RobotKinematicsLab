package com.robotkinematicslab.mobile.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialActionReport
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

@Composable
fun RobotEditorPanel(
    editorState: RobotEditorState,
    validationErrors: List<String>,
    isApplyEnabled: Boolean,
    onRobotNameChange: (String) -> Unit,
    onDhRowChange: (Int, DhInputRow) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onApplyRobot: () -> Unit,
    onLoadPresetRobot: () -> Unit,
    applyButtonLabel: String = "Apply Robot",
    presetButtonLabel: String = "Load Demo Robot",
    collapseJointDetails: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tutorialReporter = LocalTutorialActionReporter.current
    val colors = MaterialTheme.colorScheme

    fun submitValidDhEdit(index: Int, updatedRow: DhInputRow, fieldName: String) {
        val updatedRows = editorState.dhRows.toMutableList()
        updatedRows[index] = updatedRow
        val updatedState = editorState.copy(dhRows = updatedRows)
        onDhRowChange(index, updatedRow)
        validRobotEditorEditTutorialAction(
            previous = editorState,
            updated = updatedState,
            target = TutorialTargets.RobotDhParameters,
            detail = "Joint ${index + 1} $fieldName edit is valid."
        )?.let(tutorialReporter::report)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = editorState.robotName,
            onValueChange = { updatedName ->
                val updatedState = editorState.copy(robotName = updatedName)
                onRobotNameChange(updatedName)
                validRobotEditorEditTutorialAction(
                    previous = editorState,
                    updated = updatedState,
                    target = TutorialTargets.RobotName,
                    detail = "Robot name edit is valid."
                )?.let(tutorialReporter::report)
            },
            label = { Text("Robot Name") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("robot-name")
                    .tutorialAnchor(TutorialTargets.RobotName),
            singleLine = true
        )

        Text(
            text = "Joint type: R = Revolute, P = Prismatic",
            color = colors.onSurfaceVariant
        )

        editorState.dhRows.forEachIndexed { index, row ->
            var jointDetailsExpanded by rememberSaveable(index, collapseJointDetails) {
                mutableStateOf(!collapseJointDetails || index == 0)
            }
            val primaryJointModifier =
                if (index == 0) Modifier.tutorialAnchor(TutorialTargets.RobotJointCard) else Modifier
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("robot-joint-card:$index")
                        .then(primaryJointModifier),
                shape = RoundedCornerShape(12.dp),
                color = robotEditorJointContainerColor(colors),
                contentColor = robotEditorJointContentColor(colors)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("robot-joint-toggle:$index")
                                .then(
                                    if (collapseJointDetails) {
                                        Modifier
                                            .clickable {
                                                jointDetailsExpanded = !jointDetailsExpanded
                                            }
                                            .semantics(mergeDescendants = true) {
                                                role = Role.Button
                                                stateDescription =
                                                    if (jointDetailsExpanded) "Expanded" else "Collapsed"
                                            }
                                    } else {
                                        Modifier
                                    }
                                ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Joint ${index + 1}",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (collapseJointDetails) {
                                val jointType =
                                    if (row.jointTypeText == "PRISMATIC") "Prismatic" else "Revolute"
                                val unit = if (row.jointTypeText == "PRISMATIC") "mm" else "deg"
                                Text(
                                    text = "$jointType · ${row.minText} to ${row.maxText} $unit · home ${row.homeText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        if (collapseJointDetails) {
                            Text(
                                text = if (jointDetailsExpanded) "▾" else "▸",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.primary
                            )
                        }
                    }

                if (!collapseJointDetails || jointDetailsExpanded) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("robot-joint-type:$index")
                            .then(
                                if (index == 0) {
                                    Modifier.tutorialAnchor(TutorialTargets.RobotJointType)
                                } else {
                                    Modifier
                                }
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isRevolute = row.jointTypeText == "REVOLUTE"
                    val isPrismatic = row.jointTypeText == "PRISMATIC"

                    if (isRevolute) {
                        Button(
                            onClick = { onDhRowChange(index, row.copy(jointTypeText = "REVOLUTE")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("R")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onDhRowChange(index, row.copy(jointTypeText = "REVOLUTE"))
                                robotJointTypeTutorialAction(
                                    previous = row.jointTypeText,
                                    selected = "REVOLUTE",
                                    index = index
                                )?.let(tutorialReporter::report)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("R")
                        }
                    }

                    if (isPrismatic) {
                        Button(
                            onClick = { onDhRowChange(index, row.copy(jointTypeText = "PRISMATIC")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("P")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onDhRowChange(index, row.copy(jointTypeText = "PRISMATIC"))
                                robotJointTypeTutorialAction(
                                    previous = row.jointTypeText,
                                    selected = "PRISMATIC",
                                    index = index
                                )?.let(tutorialReporter::report)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("P")
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("robot-dh-parameters:$index")
                            .then(
                                if (index == 0) {
                                    Modifier.tutorialAnchor(TutorialTargets.RobotDhParameters)
                                } else {
                                    Modifier
                                }
                            ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = row.thetaText,
                            onValueChange = {
                                submitValidDhEdit(index, row.copy(thetaText = it), "theta")
                            },
                            label = {
                                if (row.jointTypeText == "REVOLUTE") {
                                    Text("θ variable (deg)")
                                } else {
                                    Text("θ fixed (deg)")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )

                        OutlinedTextField(
                            value = row.dText,
                            onValueChange = {
                                submitValidDhEdit(index, row.copy(dText = it), "d")
                            },
                            label = {
                                if (row.jointTypeText == "PRISMATIC") {
                                    Text("d variable (mm)")
                                } else {
                                    Text("d fixed (mm)")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = row.aText,
                            onValueChange = {
                                submitValidDhEdit(index, row.copy(aText = it), "a")
                            },
                            label = { Text("a (mm)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )

                        OutlinedTextField(
                            value = row.alphaText,
                            onValueChange = {
                                submitValidDhEdit(index, row.copy(alphaText = it), "alpha")
                            },
                            label = { Text("α (deg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("robot-joint-limits:$index")
                            .then(
                                if (index == 0) {
                                    Modifier.tutorialAnchor(TutorialTargets.RobotJointLimits)
                                } else {
                                    Modifier
                                }
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val minLabel = if (row.jointTypeText == "PRISMATIC") {
                        "Min (mm)"
                    } else {
                        "Min (deg)"
                    }

                    val maxLabel = if (row.jointTypeText == "PRISMATIC") {
                        "Max (mm)"
                    } else {
                        "Max (deg)"
                    }

                    val homeLabel = if (row.jointTypeText == "PRISMATIC") {
                        "Home (mm)"
                    } else {
                        "Home (deg)"
                    }

                    OutlinedTextField(
                        value = row.minText,
                        onValueChange = {
                            submitValidDhEdit(index, row.copy(minText = it), "minimum")
                        },
                        label = { Text(minLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )

                    OutlinedTextField(
                        value = row.maxText,
                        onValueChange = {
                            submitValidDhEdit(index, row.copy(maxText = it), "maximum")
                        },
                        label = { Text(maxLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )

                    OutlinedTextField(
                        value = row.homeText,
                        onValueChange = {
                            submitValidDhEdit(index, row.copy(homeText = it), "home")
                        },
                        label = { Text(homeLabel) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )
                }

                    if (editorState.dhRows.size > 1) {
                        TextButton(
                            onClick = { onRemoveRow(index) },
                            modifier =
                                Modifier
                                    .testTag("robot-remove-joint:$index")
                                    .then(
                                        if (index == 0) {
                                            Modifier.tutorialAnchor(TutorialTargets.RobotRemoveJoint)
                                        } else {
                                            Modifier
                                        }
                                    )
                        ) {
                            Text("Remove Joint ${index + 1}")
                        }
                    }
                }
                }
            }
        }

        Text(
            text = if (validationErrors.isEmpty()) {
                "Current robot input is valid."
            } else {
                "Current robot input has ${validationErrors.size} issue(s)."
            },
            color = if (validationErrors.isEmpty()) colors.secondary else colors.error
        )

        if (validationErrors.isNotEmpty()) {
            validationErrors.forEach { error ->
                Text(
                    text = "• $error",
                    color = colors.error
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddRow,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("robot-add-joint")
                        .tutorialAnchor(TutorialTargets.RobotAddJoint)
            ) {
                Text("Add Joint")
            }

            Button(
                onClick = onApplyRobot,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("robot-apply")
                        .tutorialAnchor(TutorialTargets.RobotApply),
                enabled = isApplyEnabled
            ) {
                Text(applyButtonLabel)
            }
        }

        Button(
            onClick = onLoadPresetRobot,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("robot-load-demo")
                    .tutorialAnchor(TutorialTargets.RobotDemo)
        ) {
            Text(presetButtonLabel)
        }
    }
}

internal fun robotEditorJointContainerColor(colors: ColorScheme) = colors.surfaceVariant

internal fun robotEditorJointContentColor(colors: ColorScheme) = colors.onSurfaceVariant

internal fun robotJointTypeTutorialAction(
    previous: String,
    selected: String,
    index: Int
): TutorialActionReport? {
    if (previous == selected) return null
    return TutorialActionReport(
        target = TutorialTargets.RobotJointType,
        interaction = TutorialInteraction.CHOOSE,
        detail = "Joint ${index + 1} changed to ${selected.lowercase()}."
    )
}

internal fun validRobotEditorEditTutorialAction(
    previous: RobotEditorState,
    updated: RobotEditorState,
    target: com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId,
    detail: String
): TutorialActionReport? {
    if (previous == updated) return null
    if (!RobotEditorMapper().buildRobotDefinition(updated).isSuccess) return null
    return TutorialActionReport(
        target = target,
        interaction = TutorialInteraction.TYPE,
        detail = detail
    )
}
