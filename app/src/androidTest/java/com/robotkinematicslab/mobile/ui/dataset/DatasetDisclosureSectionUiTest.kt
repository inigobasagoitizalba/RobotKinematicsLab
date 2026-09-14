package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatasetDisclosureSectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedSectionKeepsSummaryVisibleAndRevealsContentOnTap() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                DatasetDisclosureSection(
                    title = "Dataset configuration",
                    summary = "2 robots · 20k rows",
                    testTag = "dataset-disclosure-test"
                ) {
                    Text("Advanced dataset controls")
                }
            }
        }

        composeRule.onNodeWithText("2 robots · 20k rows").assertIsDisplayed()
        composeRule.onNodeWithText("Advanced dataset controls").assertDoesNotExist()

        composeRule.onNodeWithTag("dataset-disclosure-test").performClick()

        // combinedClickable defers the single tap while it rules out a double tap.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("Advanced dataset controls").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText("Advanced dataset controls").assertIsDisplayed()
        composeRule.onNodeWithTag("dataset-disclosure-test").assertTextContains("Dataset configuration")
    }
}
