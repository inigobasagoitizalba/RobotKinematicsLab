package com.robotkinematicslab.mobile.ui.projects

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ResearchProjectLibraryCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun projectCardsHaveNoLegacyBadgeAndDescribeTheirTimestampTruthfully() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ResearchProjectLibrary(
                    projects =
                        listOf(
                            project("isolated", usesLegacyWorkspace = false),
                            project("legacy", usesLegacyWorkspace = true)
                        ),
                    onOpenProject = {},
                    onCreateProject = { _, _ -> },
                    onUpdateProject = { _, _, _ -> }
                )
            }
        }

        composeRule.onAllNodesWithText("LAB").assertCountEquals(0)
        composeRule.onAllNodesWithText("01").assertCountEquals(0)
        composeRule.onAllNodesWithText("Last updated", substring = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText("Last opened", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun projectEditorRemainsScrollableAtTwoHundredPercentText() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 2f)) {
                RobotKinematicsLabTheme {
                    ResearchProjectLibrary(
                        projects = emptyList(),
                        onOpenProject = {},
                        onCreateProject = { _, _ -> },
                        onUpdateProject = { _, _, _ -> }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("create-project-button").performClick()
        composeRule.onNodeWithTag("project-editor-dialog")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
    }

    @Test
    fun createProjectDialogAndDraftSurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            RobotKinematicsLabTheme {
                ResearchProjectLibrary(
                    projects = emptyList(),
                    onOpenProject = {},
                    onCreateProject = { _, _ -> },
                    onUpdateProject = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("create-project-button").performClick()
        composeRule.onNodeWithTag("project-name-field").performTextInput("Restored project")
        composeRule.onNodeWithTag("project-objective-field")
            .performTextInput("Keep this unsaved draft through rotation")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("project-editor-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("project-name-field").assertTextContains("Restored project")
        composeRule.onNodeWithTag("project-objective-field")
            .assertTextContains("Keep this unsaved draft through rotation")
    }

    @Test
    fun editProjectDialogAndDraftSurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            RobotKinematicsLabTheme {
                ResearchProjectLibrary(
                    projects = listOf(project("edit-restoration", usesLegacyWorkspace = false)),
                    onOpenProject = {},
                    onCreateProject = { _, _ -> },
                    onUpdateProject = { _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithTag("project-objective-field")
            .performTextInput(" Additional unsaved evidence")

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("project-editor-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("project-objective-field")
            .assertTextContains("Additional unsaved evidence", substring = true)
    }

    @Test
    fun rapidDoubleTapConfirmsProjectEditorOnlyOnce() {
        var confirmations = 0
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ProjectEditorDialog(
                    title = "Create research project",
                    confirmLabel = "Create project",
                    initialName = "One project",
                    initialObjective = "",
                    onDismiss = {},
                    onConfirm = { _, _ -> confirmations += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("project-editor-confirm")
            .performTouchInput { doubleClick() }

        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun rapidDoubleTapCancelsProjectEditorOnlyOnce() {
        var dismissals = 0
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ProjectEditorDialog(
                    title = "Create research project",
                    confirmLabel = "Create project",
                    initialName = "One project",
                    initialObjective = "",
                    onDismiss = { dismissals += 1 },
                    onConfirm = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("project-editor-cancel")
            .performTouchInput { doubleClick() }

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun project(id: String, usesLegacyWorkspace: Boolean): ResearchProject =
        ResearchProject(
            id = id,
            name = "$id project",
            objective = "Verify the project-card presentation contract.",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
            usesLegacyWorkspace = usesLegacyWorkspace
        )
}
