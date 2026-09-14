package com.robotkinematicslab.mobile.ui.training.explainability

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.ml.data.*
import com.robotkinematicslab.mobile.ml.explainability.*
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExplainabilityLifecycleUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun legacyRememberCompletionIsLostAfterLeavingAndReturning() {
        var visible by mutableStateOf(true)
        lateinit var finishCapturedJob: () -> Unit
        composeRule.setContent {
            if (visible) {
                var report by remember { mutableStateOf<String?>(null) }
                SideEffect { finishCapturedJob = { report = "completed evidence" } }
                Text(report ?: "No report")
            }
        }
        val oldCompletion = finishCapturedJob
        composeRule.runOnIdle { visible = false }
        composeRule.runOnIdle { oldCompletion() }
        composeRule.runOnIdle { visible = true }
        // Reproduces the original panel's state ownership: the completed job updates a discarded state.
        composeRule.onNodeWithText("No report").assertExists()
        composeRule.onNodeWithText("completed evidence").assertDoesNotExist()
    }

    @Test fun detachedCompletionReturnsSavedEvidenceAndSurvivesSavedStateAndNewOwnerRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projects = ResearchProjectRepository(context)
        val previous = projects.activeProject()
        val project = projects.createProject("XAI lifecycle test ${System.nanoTime()}", "Isolated lifecycle regression fixture")
        projects.activateProject(project.id)
        val root = AppStoragePaths(context).rootDirectory
        val model = File(root, "models/lifecycle-model.bin").apply { parentFile!!.mkdirs(); writeText("stable test model") }
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val owner = ExplainabilitySessionCoordinator(context, root) { run, _, _, _, cancelled, progress ->
            calls.incrementAndGet(); entered.countDown()
            progress(ExplainabilityProgress(ExplainabilityPhase.EXPLAINING_SAMPLES, 1, 5, "Controlled explanation is running."))
            check(gate.await(20, TimeUnit.SECONDS))
            if (cancelled()) throw ExplainabilityCancelledException()
            result(run.runId)
        }
        try {
            composeRule.waitUntil(10000) { !owner.state.value.loading }
            var visible by mutableStateOf(true)
            val restoration = StateRestorationTester(composeRule)
            restoration.setContent {
                RobotKinematicsLabTheme {
                    if (visible) ExplainabilitySessionPanel(owner)
                }
            }
            composeRule.runOnIdle { owner.start(run(model), model.path, 16, 8).getOrThrow() }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            composeRule.onNodeWithTag("explainability-cancel").assertExists()
            restoration.emulateSavedInstanceStateRestore()
            composeRule.onNodeWithTag("explainability-cancel").assertExists()
            composeRule.runOnIdle { visible = false }
            gate.countDown()
            composeRule.waitUntil(10000) { owner.state.value.status == ExplainabilitySessionStatus.COMPLETED }
            composeRule.runOnIdle { visible = true }
            composeRule.onNodeWithTag("explainability-saved-report").assertExists()
            composeRule.onNodeWithTag("explainability-cancel").assertDoesNotExist()
            restoration.emulateSavedInstanceStateRestore()
            composeRule.onNodeWithTag("explainability-saved-report").assertExists()
            val recovered = ExplainabilitySessionCoordinator(context, root) { _, _, _, _, _, _ -> error("Recovery must not recompute") }
            composeRule.waitUntil(10000) { !recovered.state.value.loading }
            assertEquals(owner.state.value.report, recovered.state.value.report)
            assertFalse(recovered.state.value.running)
            assertEquals(1, calls.get())
            composeRule.runOnIdle { visible = false }
        } finally {
            gate.countDown(); owner.cancel()
            projects.activateProject(previous.id)
        }
    }

    private fun run(model: File) = TrainingRunSummary("lifecycle-run", "Lifecycle fixture", "unused.csv", 1, 2,
        null, null, 0.0, 42, TrainingSplitStrategy.entries.first(), 100,
        model.parentFile!!.path, "unused-history.csv", listOf(model.path))

    private fun result(runId: String) = ExplainabilityResult(runId, TrainingFeatureProfile.BASELINE_KINEMATICS,
        "lifecycle-candidate", 1, 8, listOf(GlobalFeatureImportance("target_x", 1.0, 1.0)),
        listOf(LocalPredictionExplanation(0, TrainingLabel.ACCEPTED, TrainingLabel.ACCEPTED, TrainingLabel.REJECTED,
            0.8, 0.0, 1.0, 0.0, listOf(FeatureAttribution("target_x", 1.0, 1.0)))),
        1.0, 1.0, 0.0, 0.0, 1)
}
