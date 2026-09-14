package com.robotkinematicslab.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKStatus
import org.junit.Rule
import org.junit.Test

class RobotLabDisclosureUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun viewportStatusPayloadStaysBehindTheCollapsedAdvancedDisclosure() {
        composeRule.setContent {
            MaterialTheme {
                ViewportStatusPanel(
                    mode = ControlMode.FK,
                    hasIkRun = false,
                    fkStatus = FKStatus.SUCCESS,
                    ikStatus = IKStatus.MAX_ITERATIONS_REACHED,
                    ikDetailCode = IKDetailCode.NONE,
                    fkStatusText = "Forward solution ready",
                    ikStatusText = "No inverse solution requested",
                    endEffector = null,
                    targetPoint = null,
                    ikFinalError = Double.NaN,
                    ikIterations = 0,
                    ikElapsedMs = Double.NaN,
                    ikIterationsPerSecond = Double.NaN,
                    fkMetadata = null,
                    ikMetadata = null
                )
            }
        }

        composeRule.onNodeWithText("Viewport info").assertIsDisplayed()
        composeRule.onAllNodesWithText("Forward solution ready").assertCountEquals(0)
        composeRule.onNodeWithTag("viewport-info-disclosure")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Collapsed"
                )
            )
            .performClick()

        composeRule.onNodeWithText("Forward solution ready").assertIsDisplayed()
        composeRule.onNodeWithText("FK: SUCCESS").assertIsDisplayed()
    }

    @Test
    fun debugPayloadIsAbsentUntilItsExactTitleIsOpenedAndIsRemovedWhenClosed() {
        composeRule.setContent {
            MaterialTheme {
                DebugPanel(debugText = "Sensitive debug payload")
            }
        }

        composeRule.onNodeWithText("Layer 1 debug").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sensitive debug payload").assertCountEquals(0)
        composeRule.onNodeWithTag("layer-1-debug-disclosure")
            .assertHeightIsAtLeast(48.dp)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Layer 1 debug")
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Collapsed"
                )
            )

        composeRule.onNodeWithTag("layer-1-debug-disclosure").performClick()
        composeRule.onNodeWithText("Sensitive debug payload").assertIsDisplayed()
        composeRule.onNodeWithTag("layer-1-debug-disclosure")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Expanded"
                )
            )

        composeRule.onNodeWithTag("layer-1-debug-disclosure").performClick()
        composeRule.onAllNodesWithText("Sensitive debug payload").assertCountEquals(0)
    }

    @Test
    fun openStateSurvivesRestorationAndLatestPayloadWinsAfterRecomposition() {
        val restorationTester = StateRestorationTester(composeRule)
        val payload = mutableStateOf("First payload")
        restorationTester.setContent {
            MaterialTheme {
                DebugPanel(debugText = payload.value)
            }
        }

        composeRule.onNodeWithTag("layer-1-debug-disclosure").performClick()
        composeRule.onNodeWithText("First payload").assertIsDisplayed()

        composeRule.runOnIdle { payload.value = "Updated payload" }
        composeRule.onNodeWithText("Updated payload").assertIsDisplayed()
        composeRule.onAllNodesWithText("First payload").assertCountEquals(0)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Updated payload").assertIsDisplayed()
        composeRule.onNodeWithTag("layer-1-debug-disclosure")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Expanded"
                )
            )
    }
}
