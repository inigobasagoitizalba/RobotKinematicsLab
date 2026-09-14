package com.robotkinematicslab.mobile.ui.launch

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.accessibility.AppContentScale
import com.robotkinematicslab.mobile.ui.accessibility.LocalAppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.theme.AppVisualPalette
import com.robotkinematicslab.mobile.ui.theme.AppVisualThemePreferences
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppLaunchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launchScreenExposesOneClearEntryAction() {
        var entryRequests = 0
        composeRule.setContent {
            RobotKinematicsLabTheme(
                preferences = AppVisualThemePreferences(AppVisualPalette.FOREST)
            ) {
                AppLaunchScreen(onEnterProjects = { entryRequests += 1 })
            }
        }

        composeRule.onNodeWithTag(APP_LAUNCH_SCREEN_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(APP_LAUNCH_ENTER_PROJECTS_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(APP_LAUNCH_ENTER_PROJECTS_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, entryRequests) }
    }

    @Test
    fun reducedMotionPublishesAStableSceneDescription() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                CompositionLocalProvider(
                    LocalAppAccessibilityPreferences provides
                        AppAccessibilityPreferences(reduceMotion = true)
                ) {
                    AppLaunchScreen(onEnterProjects = {})
                }
            }
        }

        composeRule.onNode(
            hasContentDescription(
                "Static 3D wireframe scene with three articulated robot arms."
            )
        ).assertIsDisplayed()
    }

    @Test
    fun maximumTextScaleAndHighContrastKeepEntryReachableOnACompactScreen() {
        val accessibility =
            AppAccessibilityPreferences(
                contentScale = AppContentScale.MAXIMUM,
                highContrast = true,
                reduceMotion = true
            )
        composeRule.setContent {
            RobotKinematicsLabTheme(accessibilityPreferences = accessibility) {
                CompositionLocalProvider(LocalAppAccessibilityPreferences provides accessibility) {
                    Box(Modifier.width(320.dp).height(480.dp)) {
                        AppLaunchScreen(onEnterProjects = {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag(APP_LAUNCH_ENTER_PROJECTS_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
