package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

class DiagnosticDisclosureCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heavyContentIsOnlyComposedWhileFolderIsOpen() {
        composeRule.setContent {
            MaterialTheme {
                DiagnosticDisclosureCard(
                    title = "Case S101",
                    subtitle = "20 sequential runs"
                ) {
                    Text("Expensive detailed evidence")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Case S101").assertIsDisplayed()
        composeRule.onAllNodesWithText("Expensive detailed evidence").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Case S101").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Case S101")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        composeRule.onNodeWithText("Expensive detailed evidence").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Case S101").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Expensive detailed evidence").assertCountEquals(0)
    }

    @Test
    fun openFolderSurvivesActivityStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                DiagnosticDisclosureCard(
                    title = "Runtime memory",
                    subtitle = "Used, free and total"
                ) {
                    Text("Memory time series")
                }
            }
        }

        composeRule.onNodeWithContentDescription("Runtime memory").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Memory time series").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Memory time series").assertIsDisplayed()
    }

    @Test
    fun rapidDoubleTapProducesOneDeterministicToggle() {
        composeRule.setContent {
            MaterialTheme {
                DiagnosticDisclosureCard(
                    title = "CPU scheduler",
                    subtitle = "Live scheduler evidence"
                ) {
                    Text("Scheduler time series")
                }
            }
        }

        composeRule.onNodeWithContentDescription("CPU scheduler")
            .performTouchInput { doubleClick() }

        composeRule.onNodeWithText("Scheduler time series").assertIsDisplayed()
    }
}
