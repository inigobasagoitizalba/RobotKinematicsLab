package com.robotkinematicslab.mobile.ui.theme

import androidx.compose.ui.test.assertIsDisplayed
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
class VisualThemeSettingsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun deliveryAppearanceIsFixedToTheSoberScientificTheme() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ocean Lab").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic device colours").assertDoesNotExist()
        composeRule.onNodeWithText(AppVisualPalette.NEON_CIRCUIT.displayName).assertDoesNotExist()
    }
}
