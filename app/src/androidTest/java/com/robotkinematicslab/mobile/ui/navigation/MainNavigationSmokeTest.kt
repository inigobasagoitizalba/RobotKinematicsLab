package com.robotkinematicslab.mobile.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import com.robotkinematicslab.mobile.ui.shared.GLOBAL_SETTINGS_SHORTCUT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyTopLevelDestinationCanBeOpenedAndComposed() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithText("Your research projects").assertIsDisplayed()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Scientific workflow").assertIsDisplayed()
        assertSingleJargonHelpNotice()
        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Build a valid experiment").assertIsDisplayed()
        assertSingleJargonHelpNotice()
        composeRule.onNodeWithText("Robot Lab").performClick()
        composeRule.waitForIdle()
        assertSingleJargonHelpNotice()
        composeRule.onNodeWithText("Robot Setup").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("robot-lab-setup").assertIsDisplayed()
        composeRule.onAllNodesWithText("Screen: Robot Setup").assertCountEquals(0)
        composeRule.onNodeWithText("Robot View").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forward Kinematics").assertIsDisplayed()
        composeRule.onNodeWithTag("robot-lab-scene").assertIsDisplayed()
        composeRule.onNodeWithTag("viewport-info-disclosure").assertExists()
        composeRule.onNodeWithTag("layer-1-debug-disclosure").assertExists()
        composeRule.onAllNodesWithText("Screen: Robot View").assertCountEquals(0)
        composeRule.onAllNodesWithText("Applied robot valid: true").assertCountEquals(0)
        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Inverse Kinematics").assertIsDisplayed().performClick()
        composeRule
            .onNodeWithText("Refresh 1 micrometre models")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Prepare").performClick()
        openDestination("Dataset Factory", "Scientific Dataset Builder")
        assertSingleJargonHelpNotice()

        composeRule.onNodeWithText("Experiment").performClick()
        openDestination("Diagnostics", "Layer 1 Diagnostic Experiment")
        composeRule.onNodeWithText("Experiment").performClick()
        openDestination("AI Training & Comparison", "Closed-Loop Training")
        composeRule.onNodeWithTag("training-mode-controlled_single_run").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local AI Training Lab").assertIsDisplayed()
        composeRule.onNodeWithTag("training-mode-result_comparison").performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("Compare trained models")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Prepare").performClick()
        openDestination("3D Workspace", "3D Robot Workspace Laboratory")
        assertSingleJargonHelpNotice()

        composeRule.onNodeWithText("Library").performClick()
        openDestination("Storage & Evidence", "Project Storage")
        composeRule.onNodeWithText("Library").performClick()
        openDestination("Settings", "Settings")
        composeRule.onNodeWithText("‹ Projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your research projects").assertIsDisplayed()
    }

    private fun openDestination(destination: String, expectedHeading: String) {
        composeRule.onNodeWithText(destination).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(expectedHeading).assertIsDisplayed()
        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).assertIsDisplayed()
    }

    private fun assertSingleJargonHelpNotice() {
        composeRule.onAllNodesWithTag("jargon-help-notice").assertCountEquals(1)
    }
}
