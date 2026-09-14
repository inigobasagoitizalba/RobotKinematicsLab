package com.robotkinematicslab.mobile.ui.charts.guidance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartGuidanceUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guideSignalQuickQuestionAndExpandedScientificHelpAreReachable() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ChartSectionCard(
                    title = "Final error heat map",
                    subtitle = "Rows are source cases and columns are destination cases.",
                    automaticExportKey = null
                ) { }
            }
        }

        composeRule.onNodeWithText("HOW TO INTERPRET").assertIsDisplayed()
        composeRule.onNodeWithText("MEASURES").assertIsDisplayed()
        composeRule.onNodeWithText("WHY IT MATTERS").assertIsDisplayed()
        composeRule.onNodeWithText("↓ LOWER TENDS BETTER").assertIsDisplayed()
        composeRule.onNodeWithTag("chart-guide-button:Final error heat map").performClick()
        // combinedClickable waits for the double-tap window before confirming a single tap.
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()

        // DropdownMenu's tagged root can report clipped bounds although its children are visible.
        composeRule.onNodeWithTag(CHART_GUIDE_QUICK_POPUP_TAG).assertExists()
        composeRule.onNodeWithText("More information").performClick()

        composeRule.onNodeWithTag(CHART_GUIDE_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("How to read this").assertIsDisplayed()
        composeRule.onNodeWithText("What it measures").assertIsDisplayed()
        composeRule.onNodeWithText("Why it matters").assertIsDisplayed()
        composeRule.onNodeWithText("Scientific caution").assertIsDisplayed()
        composeRule.onNodeWithText("Got it").performClick()
    }

    @Test
    fun doubleTapGuideOpensDetailedHelpDirectly() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ChartSectionCard(title = "Final error heat map", automaticExportKey = null) { }
            }
        }

        composeRule
            .onNodeWithTag("chart-guide-button:Final error heat map")
            .performTouchInput { doubleClick(center) }

        composeRule.onNodeWithTag(CHART_GUIDE_DIALOG_TAG).assertIsDisplayed()
    }

    @Test
    fun chartHeaderActionsKeepAccessibleTargetsAtNarrowWidthAndLargeText() {
        val title = "Long residual convergence decision title"
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 2f)) {
                RobotKinematicsLabTheme {
                    Box(Modifier.width(320.dp)) {
                        ChartSectionCard(
                            title = title,
                            guide = ChartGuideFactory.forChart(ChartGuideKind.LINE, title),
                            automaticExportKey = null
                        ) { }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("chart-guide-button:$title")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("chart-figure-button:$title")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun captureKeepsTheInterpretationGuideButRemovesControlsAndJargonDecorationBeforeWriting() {
        val title = "Residual trend"
        val subtitle = "MSE tracks the residual across checkpoints."
        val guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = title,
                subtitle = subtitle
            )
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ChartSectionCard(
                    title = title,
                    subtitle = subtitle,
                    guide = guide,
                    automaticExportKey = null
                ) { }
            }
        }

        // ChartQuickGuide is scientific figure content, not an interactive overlay.
        composeRule.onNodeWithText("HOW TO INTERPRET").assertIsDisplayed()
        composeRule.onNodeWithText("MEASURES").assertIsDisplayed()
        composeRule.onNodeWithText("WHY IT MATTERS").assertIsDisplayed()
        composeRule
            .onAllNodesWithTag("jargon-text:mean-squared-error-mse")
            .assertCountEquals(2)

        composeRule.onNodeWithTag("chart-figure-button:$title").performClick()
        val startExport =
            composeRule
                .onNodeWithTag("chart-export-png")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            check(startExport?.invoke() == true)
        }

        // The exporter deliberately waits two frames. One frame exposes the exact hierarchy that
        // will be captured, while leaving the second frame suspended so this contract writes no PNG.
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("HOW TO INTERPRET").assertIsDisplayed()
        composeRule.onNodeWithText("MEASURES").assertIsDisplayed()
        composeRule.onNodeWithText("WHY IT MATTERS").assertIsDisplayed()
        composeRule.onNodeWithTag("chart-guide-button:$title").assertDoesNotExist()
        composeRule.onNodeWithTag("chart-figure-button:$title").assertDoesNotExist()
        composeRule
            .onAllNodesWithTag("jargon-text:mean-squared-error-mse")
            .assertCountEquals(0)
    }
}
