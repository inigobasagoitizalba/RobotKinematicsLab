package com.robotkinematicslab.mobile.ui.shared.progress

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTelemetryDashboardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun memoryTimelineWith137CapturesUsesFullSharedZoomInspector() {
        val samples = (1..137).map { sample(it, it.toDouble(), 12.0, 1.0).copy(runtimeMemoryUsedMb = it.toDouble()) }
        val group = buildTelemetryChartGroups(samples).getValue(TelemetryDashboardCategory.MEMORY).first()
        composeRule.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { InteractiveTelemetryChart(group) } }
        }
        composeRule.onNodeWithTag("telemetry-open-chart-inspector").performScrollTo().performClick()
        composeRule.onNodeWithTag("chart-inspector-zoom-in").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("chart-inspector-zoom-out").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("chart-inspector-reset").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("chart-inspector-plot").assertIsDisplayed()
    }

    @Test
    fun telemetryCategorySelectorKeepsEveryCategoryVisible() {
        composeRule.setContent {
            MaterialTheme {
                TelemetryCategorySelector(
                    selected = TelemetryDashboardCategory.FLOW,
                    onSelected = {}
                )
            }
        }

        TelemetryDashboardCategory.entries.forEach { category ->
            composeRule.onNodeWithText("${category.icon} ${category.label}").assertIsDisplayed()
        }
    }

    @Test
    fun finishedTelemetryShowsChartsTimelineAndRawDataOnDemand() {
        val first = sample(1, 1.0, 8.0, 4.0)
        val second = sample(2, 2.0, 12.0, 0.0)
        val finished =
            DiagnosticProgressState(
                phase = DiagnosticProgressPhase.COMPLETED,
                completedRuns = 0,
                totalRuns = 0,
                message = "Complete",
                elapsedSeconds = 2.0
            )

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiagnosticLoadingProgressCard(
                        progressState = finished,
                        performanceSamples = listOf(first, second),
                        timeline =
                            listOf(
                                DiagnosticTimelineStep("Prepare", DiagnosticTimelineStatus.COMPLETE, "Prepare the evidence."),
                                DiagnosticTimelineStep("Finish", DiagnosticTimelineStatus.COMPLETE, "Finish and freeze the evidence.")
                            )
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Process timeline").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Performance and device telemetry").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TELEMETRY_DASHBOARD_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Final Performance Dashboard").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Calculation rate").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Current: Finish").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("timeline_checkpoint_Prepare").performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithText("Prepare the evidence.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("timeline_checkpoint_Finish").performTouchInput { doubleClick() }
        composeRule.onNodeWithText("Status: COMPLETE").assertIsDisplayed()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithTag(TELEMETRY_RAW_BUTTON_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Final Raw Telemetry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun finishedOptionalMetricExplainsAbsenceInsteadOfPretendingToCollect() {
        val group =
            TelemetryChartGroup(
                id = "system_load",
                title = "System load",
                subtitle = "Optional Android metric.",
                unit = "cores",
                series = listOf(TelemetryChartSeries("load", "Load", emptyList()))
            )
        val evidence =
            assessTelemetryChartEvidence(
                group = group,
                capturedSampleCount = 5,
                capturedDurationSeconds = 2.0,
                finished = true
            )

        composeRule.setContent {
            MaterialTheme {
                InteractiveTelemetryChart(group = group, evidence = evidence)
            }
        }

        composeRule.onNodeWithText("Metric unavailable for this run").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Collecting compatible telemetry samples…")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun selectedTimelineStageSurvivesProgressUpdatesAndRemovedStagesAreNotInvented() {
        val steps =
            mutableStateOf(
                listOf(
                    DiagnosticTimelineStep("Encode", DiagnosticTimelineStatus.RUNNING, "Encode exact inputs."),
                    DiagnosticTimelineStep("Persist", DiagnosticTimelineStatus.PENDING, "Persist exact outputs.")
                )
            )
        composeRule.setContent {
            MaterialTheme { DiagnosticProcessTimeline(steps.value) }
        }

        composeRule.onNodeWithTag("timeline_checkpoint_Encode").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Encode exact inputs.").assertIsDisplayed()

        composeRule.runOnIdle {
            steps.value =
                listOf(
                    DiagnosticTimelineStep("Encode", DiagnosticTimelineStatus.COMPLETE, "Encode exact inputs."),
                    DiagnosticTimelineStep("Persist", DiagnosticTimelineStatus.RUNNING, "Persist exact outputs.")
                )
        }
        composeRule.onNodeWithText("Encode exact inputs.").assertIsDisplayed()
        composeRule.onNodeWithText("Current: Persist").assertIsDisplayed()

        composeRule.runOnIdle {
            steps.value = listOf(DiagnosticTimelineStep("Persist", DiagnosticTimelineStatus.COMPLETE, "Persist exact outputs."))
        }
        composeRule.onNodeWithText("Encode exact inputs.").assertDoesNotExist()
    }

    @Test
    fun rawTelemetryUsesSharedExpandableSections() {
        val finished =
            DiagnosticProgressState(
                phase = DiagnosticProgressPhase.COMPLETED,
                completedRuns = 2,
                totalRuns = 2,
                message = "Complete",
                elapsedSeconds = 2.0
            )

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiagnosticRawTelemetrySnapshot(
                        progressState = finished,
                        performanceSamples = emptyList(),
                        config = DiagnosticLoadingCardConfig()
                    )
                }
            }
        }

        composeRule.onNodeWithText("Elapsed time").assertIsDisplayed()
        composeRule.onNodeWithText("CPU cores").assertDoesNotExist()

        val cpuSection = composeRule.onNodeWithTag(rawTelemetrySectionTag("CPU / Scheduler"))
        cpuSection.performScrollTo().performClick()
        composeRule.onNodeWithText("CPU cores").performScrollTo().assertIsDisplayed()

        cpuSection.performScrollTo().performClick()
        composeRule.onNodeWithText("CPU cores").assertDoesNotExist()
        composeRule.onNodeWithTag(rawTelemetrySectionTag("Battery / Thermal"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun sample(
        index: Int,
        elapsed: Double,
        rate: Double,
        eta: Double
    ): DiagnosticPerformanceSample =
        DiagnosticPerformanceSample.fromProgressState(
            sampleIndex = index,
            progressState =
                DiagnosticProgressState(
                    isRunning = index == 1,
                    phase = if (index == 1) DiagnosticProgressPhase.SEQUENTIAL_RUNS else DiagnosticProgressPhase.COMPLETED,
                    completedRuns = index,
                    totalRuns = 2,
                    runsPerSecond = rate,
                    estimatedSecondsRemaining = eta,
                    elapsedSeconds = elapsed
                )
        ).copy(timestampMs = index * 1_000L)
}
