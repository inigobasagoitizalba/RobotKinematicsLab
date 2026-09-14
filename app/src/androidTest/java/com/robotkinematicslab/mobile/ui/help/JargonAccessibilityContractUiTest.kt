package com.robotkinematicslab.mobile.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JargonAccessibilityContractUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun assistiveActivationOpensQuickHelpWithoutRequiringDoubleTap() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("The Jacobian may approach a singularity.")
            }
        }

        composeRule
            .onNodeWithTag("jargon-text:jacobian")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag(JARGON_QUICK_INFO_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Jacobian").assertIsDisplayed()
    }

    @Test
    fun detailedHelpIsExposedAsAnExplicitAccessibilityAction() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("MSE")
            }
        }

        val node =
            composeRule
                .onNodeWithTag("jargon-text:mean-squared-error-mse")
                .fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions]

        assertTrue(actions.any { action -> action.label == "More information about Mean squared error (MSE)" })
        composeRule.runOnUiThread {
            actions.first { it.label == "More information about Mean squared error (MSE)" }.action.invoke()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Why it matters here").assertIsDisplayed()
    }

    @Test
    fun everyTermInAParagraphExposesQuickAndDetailedAccessibilityActions() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                JargonAwareText("The Jacobian and MSE diagnose different failure modes.")
            }
        }

        val node = composeRule.onNodeWithTag("jargon-text:jacobian")
        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val expectedLabels =
            listOf(
                "Quick definition of Jacobian",
                "More information about Jacobian",
                "Quick definition of Mean squared error (MSE)",
                "More information about Mean squared error (MSE)"
            )

        assertEquals(expectedLabels, actions.map { it.label })

        composeRule.runOnUiThread {
            actions
                .first { it.label == "Quick definition of Mean squared error (MSE)" }
                .action
                .invoke()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(JARGON_QUICK_INFO_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Mean squared error (MSE)").assertIsDisplayed()

        composeRule.onNodeWithText("More information").performClick()
        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Got it").performClick()

        composeRule.runOnUiThread {
            actions
                .first { it.label == "More information about Mean squared error (MSE)" }
                .action
                .invoke()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Mean squared error (MSE)").assertIsDisplayed()
    }

    @Test
    fun publicationModeRemovesDecorationAndInteractionFromExportedText() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                WithoutJargonDecoration {
                    JargonAwareText("MSE")
                }
            }
        }

        composeRule.onNodeWithText("MSE").assertIsDisplayed()
        composeRule.onNodeWithTag("jargon-text:mean-squared-error-mse").assertDoesNotExist()
        composeRule.onNodeWithTag(JARGON_QUICK_INFO_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun explicitPlainTextScopeDoesNotDecorateDynamicNamesOrValues() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                WithoutJargonDecoration {
                    Column {
                        JargonAwareText("Robot name: Jacobian")
                        JargonAwareText("Measured value: MSE")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Robot name: Jacobian").assertIsDisplayed()
        composeRule.onNodeWithText("Measured value: MSE").assertIsDisplayed()
        composeRule.onNodeWithTag("jargon-text:jacobian").assertDoesNotExist()
        composeRule.onNodeWithTag("jargon-text:mean-squared-error-mse").assertDoesNotExist()
        composeRule.onNodeWithTag(JARGON_QUICK_INFO_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(JARGON_DETAIL_DIALOG_TAG).assertDoesNotExist()
    }
}
