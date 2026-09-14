package com.robotkinematicslab.mobile.ui.training

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TrainingDisclosureSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentStartsCollapsedAndCanBeExpanded() {
        composeRule.setContent {
            MaterialTheme {
                TrainingDisclosureSection(
                    title = "Advanced training controls",
                    subtitle = "Recorded with every run"
                ) {
                    Text("Hidden training field")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Advanced training controls")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        composeRule.onAllNodesWithText("Hidden training field").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Advanced training controls").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Hidden training field").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Advanced training controls")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    @Test
    fun expandedStateSurvivesRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                TrainingDisclosureSection(
                    title = "Stored evidence",
                    subtitle = "Scientific results"
                ) {
                    Text("Evidence details")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Stored evidence").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Evidence details").assertIsDisplayed()
    }
}
