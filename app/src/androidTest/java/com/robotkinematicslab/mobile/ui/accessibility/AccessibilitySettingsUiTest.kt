package com.robotkinematicslab.mobile.ui.accessibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySettingsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun accessibilityChoicesApplyImmediatelyAndPersist() {
        val repository = AppAccessibilityRepository(composeRule.activity)
        val previous = repository.load()

        try {
            composeRule.dismissFirstRunTutorialIfPresent()
            composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
            composeRule.onNodeWithText("Library").performClick()
            composeRule.onNodeWithText("Settings").performClick()
            composeRule
                .onNodeWithContentDescription("Accessibility")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeRule.mainClock.advanceTimeBy(500L)
            composeRule.waitForIdle()

            composeRule
                .onNodeWithTag("accessibility-scale:extra-large")
                .performScrollTo()
                .performClick()
            composeRule
                .onNodeWithTag("accessibility-high-contrast")
                .performScrollTo()
                .performClick()
            composeRule
                .onNodeWithTag("accessibility-reduce-motion")
                .performScrollTo()
                .performClick()
            composeRule
                .onNodeWithTag("accessibility-read-aloud")
                .performScrollTo()
                .performClick()

            composeRule.waitForIdle()
            val saved = repository.load()
            assertEquals(AppContentScale.EXTRA_LARGE, saved.contentScale)
            assertTrue(saved.highContrast)
            assertTrue(saved.reduceMotion)
            assertTrue(saved.readAloudEnabled)
            composeRule
                .onNodeWithTag("accessibility-preview-speech")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("Android accessibility services").performScrollTo().assertIsDisplayed()
        } finally {
            repository.save(previous)
        }
    }
}
