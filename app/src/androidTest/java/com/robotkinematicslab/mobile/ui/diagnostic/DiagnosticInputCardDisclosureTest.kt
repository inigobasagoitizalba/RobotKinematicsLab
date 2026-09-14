package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticInputCardDisclosureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupKeepsPrimaryActionVisibleAndOpensAdvancedSectionsOnDemand() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                DiagnosticInputCard(
                    reachableCountText = "20",
                    onReachableCountChange = {},
                    unreachableCountText = "10",
                    onUnreachableCountChange = {},
                    robotLinkCountText = "6",
                    onRobotLinkCountChange = {},
                    minLinkCountText = "2",
                    onMinLinkCountChange = {},
                    maxLinkCountText = "10",
                    onMaxLinkCountChange = {},
                    samplesPerLinkCountText = "100",
                    onSamplesPerLinkCountChange = {},
                    unlimitedSampleCountText = "1000",
                    onUnlimitedSampleCountChange = {},
                    seedText = "42",
                    onSeedChange = {},
                    experimentalMode = false,
                    onExperimentalModeChange = {},
                    unlimitedSamplesEnabled = false,
                    onUnlimitedSamplesEnabledChange = {},
                    manualRangeMode = false,
                    onManualRangeModeChange = {},
                    selectedJointMode = DiagnosticJointMode.AUTO,
                    onJointModeChange = {},
                    runAllTopologies = true,
                    onRunAllTopologiesChange = {},
                    stressLevel = 0.5f,
                    onStressLevelChange = {},
                    ikMaxIterationsText = "800",
                    onIkMaxIterationsChange = {},
                    ikToleranceText = "0.00001",
                    onIkToleranceChange = {},
                    ikDampingText = "0.05",
                    onIkDampingChange = {},
                    ikMaxStepText = "0.02",
                    onIkMaxStepChange = {},
                    onUseAutoBenchmarkPreset = {},
                    onUseBalancedPreset = {},
                    onUsePrecisionPreset = {},
                    onUseExplorationPreset = {},
                    isRunning = false,
                    onRun = {},
                    onCancel = {},
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        composeRule.onNodeWithTag("diagnostics-section-sampling-content").assertDoesNotExist()
        composeRule.onNodeWithTag("diagnostics-section-solver-content").assertDoesNotExist()
        composeRule.onNodeWithTag("diagnostics-run").assertExists()

        composeRule.onNodeWithTag("diagnostics-section-sampling").performScrollTo().performClick()
        waitForContent("diagnostics-section-sampling-content", 1)

        composeRule.onNodeWithTag("diagnostics-section-sampling").performScrollTo().performClick()
        waitForContent("diagnostics-section-sampling-content", 0)
        composeRule.onNodeWithTag("diagnostics-section-solver").performScrollTo().performClick()
        waitForContent("diagnostics-section-solver-content", 1)
        composeRule.onNodeWithTag("diagnostics-run").assertExists()
    }
    private fun waitForContent(tag: String, count: Int) {
        // Shared headers also accept double-tap: wait for the single-tap decision before inspecting content.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size == count
        }
        composeRule.onAllNodesWithTag(tag).assertCountEquals(count)
    }

}
