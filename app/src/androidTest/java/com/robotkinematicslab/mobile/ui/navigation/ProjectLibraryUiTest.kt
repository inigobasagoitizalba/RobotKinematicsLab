package com.robotkinematicslab.mobile.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectLibraryUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun projectCreationDialog_requiresNameAndPreservesLibraryWhenCancelled() {
        composeRule.dismissFirstRunTutorialIfPresent()
        val projectName = "UI isolation study ${System.currentTimeMillis()}"
        composeRule.onNodeWithTag("create-project-button").performClick()
        composeRule.onNodeWithTag("project-editor-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Create project").assertIsNotEnabled()

        composeRule.onNodeWithTag("project-name-field").performTextInput(projectName)
        composeRule.onNodeWithTag("project-objective-field").performTextInput("Verify isolated navigation and evidence.")
        composeRule.onNodeWithText("Create project").assertIsEnabled()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your research projects").assertIsDisplayed()
    }
}
