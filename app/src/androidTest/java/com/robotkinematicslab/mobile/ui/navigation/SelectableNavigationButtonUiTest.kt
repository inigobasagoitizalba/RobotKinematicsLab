package com.robotkinematicslab.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SelectableNavigationButtonUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowLargeTextNavigationKeepsMinimumTargetAndDispatchesOnce() {
        var clicks = 0
        composeRule.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.width(92.dp)) {
                        SelectableNavigationButton(
                            selected = false,
                            label = "AI Training",
                            onClick = { clicks += 1 },
                            modifier = Modifier.testTag("narrow-navigation-button")
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("narrow-navigation-button")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotSelected()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun selectionStateIsExposedToAccessibilityServices() {
        composeRule.setContent {
            MaterialTheme {
                SelectableNavigationButton(
                    selected = true,
                    label = "Robot View",
                    onClick = {},
                    modifier = Modifier.testTag("selected-navigation-button")
                )
            }
        }

        composeRule.onNodeWithTag("selected-navigation-button")
            .assertIsDisplayed()
            .assertIsSelected()
    }
}
