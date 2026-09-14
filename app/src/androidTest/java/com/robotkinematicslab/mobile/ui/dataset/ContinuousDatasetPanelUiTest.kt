package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetPlan
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetState
import com.robotkinematicslab.mobile.dataset.continuous.ContinuousDatasetStatus
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeThermalLevel
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuousDatasetPanelUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyPanelExplainsBudgetsAndStartsCurrentPlan() {
        var started = false
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ContinuousDatasetPanel(
                        state = ContinuousDatasetState(),
                        draftPlan = plan(),
                        deviceProfile = device(),
                        onStartCurrentPlan = { _, _ -> started = true },
                        onResumeSavedPlan = { _, _ -> },
                        onPause = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Continuous dataset growth").assertIsDisplayed()
        composeRule.onNodeWithText("Average CPU budget").assertDoesNotExist()
        composeRule.onNodeWithTag("continuous-resource-budgets").performClick()
        // A header supports both single and double taps; wait for its single-tap decision.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithText("Average CPU budget").assertExists() }.isSuccess
        }
        composeRule.onNodeWithText("Average CPU budget").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(started) }
        composeRule.onNodeWithTag("start-continuous-dataset").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(started) }
    }

    @Test
    fun runningPanelShowsCommittedEvidenceAndPauseAction() {
        var paused = false
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ContinuousDatasetPanel(
                        state =
                            ContinuousDatasetState(
                                status = ContinuousDatasetStatus.RUNNING,
                                plan = plan(),
                                committedDatasetRows = 12_500,
                                rowsAddedThisSession = 500,
                                committedBatchesThisSession = 2,
                                workerCount = 1,
                                rowsPerRobotPerBatch = 25,
                                message = "Generating deterministic batch 3."
                            ),
                        draftPlan = plan(),
                        deviceProfile = device(),
                        onStartCurrentPlan = { _, _ -> },
                        onResumeSavedPlan = { _, _ -> },
                        onPause = { paused = true }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("continuous-dataset-status").assertIsDisplayed()
        composeRule.onNodeWithText("12500 committed rows").assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(paused) }
        composeRule.onNodeWithTag("pause-continuous-dataset").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(paused) }
    }

    @Test
    fun pausedPanelExposesResumeAnchorWithoutInvokingIt() {
        var resumed = false
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ContinuousDatasetPanel(
                        state =
                            ContinuousDatasetState(
                                status = ContinuousDatasetStatus.PAUSED,
                                plan = plan(),
                                message = "Paused safely."
                            ),
                        draftPlan = plan(),
                        deviceProfile = device(),
                        onStartCurrentPlan = { _, _ -> },
                        onResumeSavedPlan = { _, _ -> resumed = true },
                        onPause = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("resume-continuous-dataset").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(resumed) }
    }

    private fun plan() = ContinuousDatasetPlan(
        datasetName = "continuous-ui-study",
        robotIds = listOf("robot-a"),
        randomSeed = 2604,
        targetMode = DatasetTargetMode.MIXED,
        reachableFraction = 0.5,
        filterMode = DatasetFilterMode.ALL,
        ikConfig = IKConfig(maxIterations = 100, tolerance = 1e-6, damping = 0.01, maxStep = 0.02)
    )

    private fun device() = DeviceComputeProfile(
        logicalCpuCores = 8,
        totalSystemMemoryBytes = 8L * 1_024L * 1_024L * 1_024L,
        availableSystemMemoryBytes = 4L * 1_024L * 1_024L * 1_024L,
        lowMemoryThresholdBytes = 512L * 1_024L * 1_024L,
        appHeapLimitBytes = 512L * 1_024L * 1_024L,
        lowMemory = false,
        thermalLevel = ComputeThermalLevel.NONE,
        deviceName = "Test device"
    )

}
