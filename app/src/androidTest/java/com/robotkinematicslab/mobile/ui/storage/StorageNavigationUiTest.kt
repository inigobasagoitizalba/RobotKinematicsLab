package com.robotkinematicslab.mobile.ui.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class StorageNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun storageExplainsBundledLibraryAndOpensEveryOwningScreen() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        openStorage()

        composeRule
            .onNodeWithText("A research pack is a versioned collection", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        openStorageDestination(
            category = "ROBOTS",
            access = "ROBOTS",
            expectedHeading = "Industrial robot reference gallery"
        )

        openStorage()
        openStorageCategory("DATASETS")
        composeRule.onNodeWithTag("StorageDirectAccess:DATASETS").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Scientific Dataset Builder").assertIsDisplayed()
        composeRule.onNodeWithText("Saved (", substring = true).assertIsSelected()

        openStorage()
        openStorageDestination(
            category = "MODELS",
            access = "MODELS",
            expectedHeading = "Compare trained models"
        )

        openStorage()
        openStorageDestination(
            category = "TRAINING",
            access = "TRAINING",
            expectedHeading = "Local AI Training Lab"
        )
        composeRule
            .onNodeWithTag("training-mode-controlled_single_run")
            .assertIsSelected()
            .assertTextContains("Single run", substring = true)

        openStorage()
        openStorageDestination(
            category = "SESSIONS",
            access = "SESSIONS",
            expectedHeading = "Layer 1 Diagnostic Experiment"
        )

        openStorage()
        openStorageDestination(
            category = "SESSIONS",
            access = "WORKSPACES",
            expectedHeading = "3D Robot Workspace Laboratory"
        )
    }

    private fun openStorage() {
        composeRule.onNodeWithText("Library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Storage & Evidence").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Project Storage").performScrollTo().assertIsDisplayed()
    }

    private fun openStorageCategory(category: String) {
        composeRule
            .onNodeWithTag("StorageCategoryToggle:$category")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun openStorageDestination(
        category: String,
        access: String,
        expectedHeading: String
    ) {
        openStorageCategory(category)
        composeRule
            .onNodeWithTag("StorageDirectAccess:$access")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(expectedHeading).performScrollTo().assertIsDisplayed()
    }
}
