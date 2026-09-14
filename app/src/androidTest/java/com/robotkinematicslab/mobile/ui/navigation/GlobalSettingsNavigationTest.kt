package com.robotkinematicslab.mobile.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import com.robotkinematicslab.mobile.ui.shared.GLOBAL_SETTINGS_SHORTCUT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSettingsNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsAreAvailableBeforeAProjectIsOpened() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("project-library").assertIsDisplayed()

        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).performClick()
        composeRule.onNodeWithTag("standalone-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-back-to-projects").performClick()
        composeRule.onNodeWithTag("project-library").assertIsDisplayed()
    }

    @Test
    fun standaloneSettingsKeepTheActivityResumedAndReturnToProjects() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("project-library").assertIsDisplayed()

        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).performClick()
        composeRule.onNodeWithTag("standalone-settings").assertIsDisplayed()
        assertActivityIsResumed()

        composeRule.onNodeWithTag("settings-back-to-projects").performClick()
        composeRule.onNodeWithTag("project-library").assertIsDisplayed()
        assertActivityIsResumed()
    }

    @Test
    fun projectSettingsReturnToTheExactCallingScreen() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Experiment").performClick()
        composeRule.onNodeWithText("Diagnostics").performClick()
        composeRule.onNodeWithText("Layer 1 Diagnostic Experiment").assertIsDisplayed()

        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).performClick()
        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
        composeRule.onNodeWithText("← Back to Experiment / Diagnostics").assertIsDisplayed()

        composeRule.onNodeWithTag("project-context-section-back").performClick()
        composeRule.onNodeWithText("Layer 1 Diagnostic Experiment").assertIsDisplayed()
        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).performClick()
        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("Layer 1 Diagnostic Experiment").assertIsDisplayed()
    }

    private fun assertActivityIsResumed() {
        composeRule.runOnIdle {
            check(!composeRule.activity.isFinishing) { "Settings must not finish MainActivity" }
            check(!composeRule.activity.isDestroyed) { "Settings must not destroy MainActivity" }
            check(composeRule.activity.lifecycle.currentState == Lifecycle.State.RESUMED) {
                "Settings must leave MainActivity resumed, but it was " +
                    composeRule.activity.lifecycle.currentState
            }
        }
    }
}
