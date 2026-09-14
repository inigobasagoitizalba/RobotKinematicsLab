package com.robotkinematicslab.mobile.ui.editor

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotSetupLibraryPanelUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingValidatedRobotLoadsItsCompleteEditableCopyImmediately() {
        val robots = DatasetRobotPresets().buildDefaults().take(3)
        var loadedRobot: com.robotkinematicslab.mobile.dataset.SavedRobot? = null
        val selectedId = mutableStateOf(robots.first().id)

        composeRule.setContent {
            RobotKinematicsLabTheme {
                RobotSetupLibraryPanel(
                    robots = robots,
                    selectedRobotId = selectedId.value,
                    editorRobotId = selectedId.value,
                    activeRobotId = robots.first().id,
                    hasUnsavedEditorChanges = false,
                    onLoadRobotIntoEditor = { saved -> loadedRobot = saved; selectedId.value = saved.id },
                    onCreateNewRobotDraft = {},
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        openSection("robot-setup-dh-library-section")

        val selected = robots[1]
        composeRule
            .onNodeWithTag("robot-setup-library-option:${selected.id}")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNodeWithTag("robot-setup-library-summary:${selected.id}")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("robot-setup-load-selected").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(selected, loadedRobot) }

        composeRule.runOnIdle { loadedRobot = null }
        composeRule
            .onNodeWithTag("robot-setup-library-option:${selected.id}")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertNull(loadedRobot) }
    }

    @Test
    fun unsavedEditorFieldsRequireConfirmationBeforeReplacement() {
        val robots = DatasetRobotPresets().buildDefaults().take(2)
        val original = robots.first()
        val replacement = robots.last()
        var loadedRobotId: String? = null

        composeRule.setContent {
            RobotKinematicsLabTheme {
                RobotSetupLibraryPanel(
                    robots = robots,
                    selectedRobotId = original.id,
                    editorRobotId = original.id,
                    activeRobotId = original.id,
                    hasUnsavedEditorChanges = true,
                    onLoadRobotIntoEditor = { saved -> loadedRobotId = saved.id },
                    onCreateNewRobotDraft = {},
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        openSection("robot-setup-dh-library-section")
        composeRule
            .onNodeWithTag("robot-setup-library-option:${replacement.id}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("robot-setup-unsaved-confirmation").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(loadedRobotId) }

        composeRule.onNodeWithTag("robot-setup-cancel-load").performClick()
        composeRule.runOnIdle { assertNull(loadedRobotId) }

        composeRule
            .onNodeWithTag("robot-setup-library-option:${replacement.id}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("robot-setup-confirm-load").performClick()
        composeRule.runOnIdle { assertEquals(replacement.id, loadedRobotId) }
    }

    @Test
    fun setupLibrariesStartCompactAndOpenIndependently() {
        val robots = DatasetRobotPresets().buildDefaults().take(2)

        composeRule.setContent {
            RobotKinematicsLabTheme {
                RobotSetupLibraryPanel(
                    robots = robots,
                    selectedRobotId = robots.first().id,
                    editorRobotId = robots.first().id,
                    activeRobotId = null,
                    hasUnsavedEditorChanges = false,
                    onLoadRobotIntoEditor = {},
                    onCreateNewRobotDraft = {},
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        composeRule.onNodeWithTag("robot-setup-reference-section").assertIsDisplayed()
        composeRule.onNodeWithTag("robot-setup-dh-library-section").assertIsDisplayed()
        composeRule.onNodeWithTag("robot-setup-industrial-references").assertDoesNotExist()
        composeRule.onNodeWithTag("robot-setup-load-selected").assertDoesNotExist()

        openSection("robot-setup-reference-section")
        composeRule.onNodeWithTag("robot-setup-industrial-references").assertExists()
        composeRule.onNodeWithTag("robot-setup-load-selected").assertDoesNotExist()

        openSection("robot-setup-dh-library-section")
        composeRule.onNodeWithTag("robot-setup-load-selected").assertDoesNotExist()
        composeRule
            .onNodeWithTag("robot-setup-library-status:${robots.first().id}")
            .assertExists()
    }

    @Test
    fun compactJointEditorKeepsLaterJointsCollapsedUntilRequested() {
        val state = RobotEditorState(dhRows = List(3) { DhInputRow() })

        composeRule.setContent {
            RobotKinematicsLabTheme {
                RobotEditorPanel(
                    editorState = state,
                    validationErrors = emptyList(),
                    isApplyEnabled = true,
                    onRobotNameChange = {},
                    onDhRowChange = { _, _ -> },
                    onAddRow = {},
                    onRemoveRow = {},
                    onApplyRobot = {},
                    onLoadPresetRobot = {},
                    collapseJointDetails = true,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        composeRule.onNodeWithTag("robot-joint-type:0").assertExists()
        composeRule.onNodeWithTag("robot-joint-type:1").assertDoesNotExist()

        composeRule.onNodeWithTag("robot-joint-toggle:1").performScrollTo().performClick()
        composeRule.onNodeWithTag("robot-joint-type:1").assertExists()
    }
    private fun openSection(tag:String) {
        val header=composeRule.onNodeWithTag(tag).performScrollTo()
        if(header.fetchSemanticsNode().config[SemanticsProperties.StateDescription] != "Expanded") {
            header.performSemanticsAction(SemanticsActions.OnClick)
        }
        composeRule.onNodeWithTag("$tag-content").assertExists()
    }

}
