package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlobalHeaderActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrowHeaderKeepsLocalAndSettingsInsideTheAvailableWidth() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(132.dp).testTag("narrow-global-header-root")) {
                    GlobalHeaderActions(
                        onOpenSettings = {}
                    )
                }
            }
        }

        val local = composeRule.onNodeWithTag(GLOBAL_HEADER_LOCAL_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val settings = composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val root = composeRule.onNodeWithTag("narrow-global-header-root")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(local.top < settings.top || local.left < settings.left)
        assertTrue(settings.right <= root.right)
    }

    @Test
    fun currentSettingsGearIsVisibleButCannotDispatchANoopClick() {
        composeRule.setContent {
            MaterialTheme {
                GlobalHeaderActions(
                    onOpenSettings = {},
                    settingsSelected = true
                )
            }
        }

        composeRule.onNodeWithTag(GLOBAL_SETTINGS_SHORTCUT_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }
}
