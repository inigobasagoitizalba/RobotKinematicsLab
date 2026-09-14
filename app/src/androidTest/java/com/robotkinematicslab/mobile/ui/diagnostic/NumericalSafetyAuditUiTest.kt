package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumericalSafetyAuditUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun diagnosticSafetyLabIsReachableAndExplainsIsolation() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Experiment").performClick()
        composeRule.onNodeWithText("Diagnostics").performClick()
        composeRule.onNodeWithTag("OpenNumericalSafetyAudit").assertDoesNotExist()
        composeRule.onNodeWithTag("diagnostics-advanced-lab").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("OpenNumericalSafetyAudit").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("OpenNumericalSafetyAudit").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Mathematical Safety A/B Lab").assertIsDisplayed()
        composeRule.onNodeWithText("Scientific negative control").assertIsDisplayed()
        composeRule.onNodeWithText("Quick").performScrollTo().performClick()
        composeRule.onNodeWithTag("RunNumericalSafetyAudit").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("RunNumericalSafetyAudit").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Defensible result").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Defensible result").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Evidence export").performScrollTo().assertIsDisplayed()
    }
}
