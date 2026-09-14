package com.robotkinematicslab.mobile.ui.launch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBackUnconditionally
import com.robotkinematicslab.mobile.MainActivity
import org.junit.Rule
import org.junit.Test

class AppLaunchNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun projectsAreNotComposedUntilTheUserEnters() {
        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("project-library").assertDoesNotExist()
        composeRule.onNodeWithTag(APP_LAUNCH_ENTER_PROJECTS_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("project-library").assertExists()
    }

    @Test
    fun recreationPreservesTheEntryGateBeforeAndAfterEnteringProjects() {
        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("project-library").assertDoesNotExist()

        composeRule.onNodeWithTag(APP_LAUNCH_ENTER_PROJECTS_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("project-library").assertExists()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("project-library").assertExists()
    }

    @Test
    fun backFromLaunchFinishesTheActivityWithoutOpeningProjects() {
        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("project-library").assertDoesNotExist()

        pressBackUnconditionally()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
    }
}
