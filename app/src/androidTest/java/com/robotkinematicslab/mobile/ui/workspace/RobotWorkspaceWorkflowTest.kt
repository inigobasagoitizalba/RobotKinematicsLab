package com.robotkinematicslab.mobile.ui.workspace

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotWorkspaceWorkflowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickStudyGeneratesPersistsAndRevealsInteractiveScene() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("3D Workspace").performClick()
        composeRule.onNodeWithTag("workspace-root").assertIsDisplayed()
        if (composeRule.onAllNodesWithTag("workspace-robot-selector").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("workspace-study-settings-disclosure")
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        composeRule
            .onNodeWithTag("workspace-robot-selector")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-study-contract")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-quality-preset")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-sampling-settings-disclosure").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule
            .onNodeWithTag("workspace-numeric-contract")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-robot:library-preset-03-revolute")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Research").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-work-summary").assertTextContains("32768 FK evaluations total", substring = true)
        capture("workspace-research-selection")
        composeRule.onNodeWithText("Quick").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-work-summary").assertTextContains("2048 FK evaluations total", substring = true)
        capture("workspace-quick-selection")
        composeRule
            .onNodeWithTag("workspace-generate")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithText("Workspace analysis complete").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Workspace analysis complete").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-study-robot")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Synthetic 3-link Revolute mathematical preset")
        composeRule.onNodeWithTag("workspace-3d-scene").performScrollTo().assertIsDisplayed()
        if (composeRule.onAllNodesWithTag("workspace-mode-selector").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("workspace-display-mode-disclosure")
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        composeRule
            .onNodeWithTag("workspace-mode-selector")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-playback")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-speed")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-reveal")
            .performScrollTo()
            .assertIsDisplayed()
        if (composeRule.onAllNodesWithTag("workspace-visibility-layers").fetchSemanticsNodes().isEmpty()) {
            composeRule
                .onNodeWithTag("workspace-visible-layers-disclosure")
                .performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
                .performSemanticsAction(SemanticsActions.OnClick)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            composeRule.waitForIdle()
        }
        composeRule
            .onNodeWithTag("workspace-visibility-layers")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-result")
            .performScrollTo()
            .assertIsDisplayed()
        capture("workspace-saved-effective-configuration")
        composeRule
            .onNodeWithTag("workspace-saved-studies")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-mode-surface").performScrollTo().performClick()
        composeRule
            .onNodeWithText("Only exposed voxel faces are joined", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-mode-construction").performScrollTo().performClick()
        composeRule
            .onNodeWithTag("workspace-construction-timeline")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule
                .onAllNodesWithText("Preparing sampled boundaries", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeRule.onNodeWithText("● WORKSPACE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("● DEAD SPACE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("X-ray interior", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-complete-construction").performScrollTo().performClick()
        composeRule.onNodeWithText("Construction 100%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Joint constraints used by this study").performScrollTo().assertIsDisplayed()
        if (composeRule.onAllNodesWithText("Load and replay").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("workspace-saved-studies")
                .performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        composeRule.onAllNodesWithText("Load and replay")[0].performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "workspace-acceptance").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            composeRule.onNodeWithTag("workspace-root").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
