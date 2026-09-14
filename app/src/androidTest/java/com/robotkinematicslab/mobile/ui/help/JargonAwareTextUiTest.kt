package com.robotkinematicslab.mobile.ui.help

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JargonAwareTextUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleTapShowsPlainDefinitionAndVisibleDetailedHelpPath() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("MSE")
            }
        }

        composeRule
            .onNodeWithTag("jargon-text:mean-squared-error-mse")
            .assertHasClickAction()
            .performTouchInput { click(center) }

        // A real single tap is confirmed only after Android's double-tap window expires.
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()

        // Popup containers can report clipped bounds on some API levels; verify the tagged popup
        // exists and its user-facing content is actually visible.
        composeRule.onNodeWithTag(JARGON_QUICK_INFO_TAG).assertExists()
        composeRule.onNodeWithText("TECHNICAL TERM").assertIsDisplayed()
        composeRule.onNodeWithText("More information").assertIsDisplayed()
    }

    @Test
    fun doubleTapOpensDetailedMeaningAndScientificConsequence() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("MSE")
            }
        }

        composeRule
            .onNodeWithTag("jargon-text:mean-squared-error-mse")
            .performTouchInput { doubleClick(center) }

        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("In plain language").assertIsDisplayed()
        composeRule.onNodeWithText("Why it matters here").assertIsDisplayed()
    }

    @Test
    fun longPressOpensDetailedMeaningAndTheDialogDocumentsThatGesture() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("MSE")
            }
        }

        composeRule
            .onNodeWithTag("jargon-text:mean-squared-error-mse")
            .performTouchInput { longClick(center) }

        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Mean squared error (MSE)").assertIsDisplayed()
        composeRule
            .onNodeWithText("press and hold", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}
