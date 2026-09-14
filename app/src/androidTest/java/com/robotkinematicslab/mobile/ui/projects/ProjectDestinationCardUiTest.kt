package com.robotkinematicslab.mobile.ui.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.accessibility.AppContentScale
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargetId
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectDestinationCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openAndEnterActionsFillTheirCardAndInvokeOnlyTheAssignedDestination() {
        var robotLabRequests = 0
        var workspaceRequests = 0
        val workspaceTarget = TutorialTargetId("workspace-destination")
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Box(Modifier.width(360.dp)) {
                    Column {
                        ProjectDestinationCard(
                            ProjectDestination(
                                eyebrow = "ROBOT",
                                title = "Robot Lab",
                                description = "Define the robot.",
                                status = "READY",
                                onClick = { robotLabRequests += 1 }
                            )
                        )
                        ProjectDestinationCard(
                            ProjectDestination(
                                eyebrow = "02 · VERIFY",
                                title = "3D Workspace",
                                description = "Verify the workspace.",
                                status = "EXPLORE",
                                onClick = { workspaceRequests += 1 },
                                tutorialTarget = workspaceTarget,
                                actionLabel = "Enter"
                            )
                        )
                    }
                }
            }
        }

        val cardBounds =
            composeRule.onNodeWithTag("destination:Robot Lab").fetchSemanticsNode().boundsInRoot
        val actionBounds =
            composeRule.onNodeWithTag("destination-action:Robot Lab").fetchSemanticsNode().boundsInRoot
        val horizontalInset = with(composeRule.density) { 16.dp.toPx() }

        assertEquals(cardBounds.left + horizontalInset, actionBounds.left, 1f)
        assertEquals(cardBounds.right - horizontalInset, actionBounds.right, 1f)
        with(composeRule.density) {
            assertTrue(
                "Destination action must keep a 48 dp minimum touch target.",
                actionBounds.height >= 48.dp.toPx()
            )
        }
        composeRule.onNodeWithText("Open  →")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Enter  →")
            .assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Open Robot Lab"))
            .assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Enter 3D Workspace"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("destination-action:3D Workspace")
            .assertIsEnabled()
            .assertHasClickAction()
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(0, robotLabRequests)
            assertEquals(1, workspaceRequests)
        }

        composeRule.onNodeWithText("Robot Lab").performClick()
        composeRule.runOnIdle {
            assertEquals(1, robotLabRequests)
            assertEquals(1, workspaceRequests)
        }
    }

    @Test
    fun disabledDestinationActionRejectsInputWithoutInvokingItsCallback() {
        var requests = 0
        val datasetTarget = TutorialTargetId("disabled-dataset-destination")
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Box(Modifier.width(360.dp)) {
                    ProjectDestinationCard(
                        ProjectDestination(
                            eyebrow = "DATA",
                            title = "Dataset Factory",
                            description = "Generate a dataset.",
                            status = "NOT READY",
                            onClick = { requests += 1 },
                            tutorialTarget = datasetTarget,
                            enabled = false
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithTag("destination-action:Dataset Factory")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.onNodeWithTag("destination:Dataset Factory")
            .assertIsNotEnabled()
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(0, requests)
        }
    }

    @Test
    fun maximumContentScaleKeepsTheLongestDestinationActionInsideACompactCard() {
        val accessibility =
            AppAccessibilityPreferences(
                contentScale = AppContentScale.MAXIMUM,
                boldText = true
            )
        composeRule.setContent {
            RobotKinematicsLabTheme(accessibilityPreferences = accessibility) {
                Box(Modifier.width(280.dp)) {
                    ProjectDestinationCard(
                        ProjectDestination(
                            eyebrow = "MODELLING",
                            title = "AI Training & Comparison",
                            description = "Train feature profiles and compare their evidence.",
                            status = "2147483647 RUNS",
                            onClick = {}
                        )
                    )
                }
            }
        }

        val cardBounds =
            composeRule.onNodeWithTag("destination:AI Training & Comparison")
                .fetchSemanticsNode()
                .boundsInRoot
        val actionBounds =
            composeRule.onNodeWithTag("destination-action:AI Training & Comparison")
                .fetchSemanticsNode()
                .boundsInRoot
        val horizontalInset =
            with(composeRule.density) {
                16.dp.toPx() * accessibility.contentScale.multiplier
            }

        assertEquals(cardBounds.left + horizontalInset, actionBounds.left, 1f)
        assertEquals(cardBounds.right - horizontalInset, actionBounds.right, 1f)
        composeRule.onNode(hasContentDescription("Open AI Training & Comparison"))
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
