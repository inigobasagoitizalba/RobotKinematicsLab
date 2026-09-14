package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousDatasetNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun generationRemainsVisibleAfterNavigatingAwayAndCanBePausedSafely() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("Dataset Factory").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val datasetName = AppStorageRepository(context).loadDatasetBuilderDraft().datasetName
        val storage = DatasetStorageRepository(context)
        val initialRows = storage.loadManifest(datasetName)?.rowCount ?: 0L
        composeRule.onNodeWithTag("dataset-section-continuous").performClick()
        composeRule.onNodeWithTag("start-continuous-dataset").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            runCatching {
                composeRule.onNodeWithTag("continuous-dataset-global-status").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("continuous-dataset-global-status").assertIsDisplayed()

        // The first real long-running process intentionally explains Android's
        // notification permission. Dismiss that one-time product prompt so this
        // navigation/cancellation test exercises the controls underneath it.
        runCatching {
            composeRule.onAllNodesWithText("Not now")[0].performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithText("Experiment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Scientific experiments").assertIsDisplayed()
        composeRule.onNodeWithTag("continuous-dataset-global-status").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            (storage.loadManifest(datasetName)?.rowCount ?: 0L) > initialRows
        }

        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("Dataset Factory").performClick()
        composeRule.onNodeWithTag("dataset-section-continuous").performClick()
        composeRule.onNodeWithTag("pause-continuous-dataset").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            runCatching {
                composeRule.onNodeWithTag("resume-continuous-dataset").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("continuous-dataset-status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Paused").assertIsDisplayed()
    }
}
