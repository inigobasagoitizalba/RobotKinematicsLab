package com.robotkinematicslab.mobile.ui.training.explainability

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.MainActivity
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

class ExplainabilityActivityRecreationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun realActivityDestructionReattachesToRunningAndCompletedExplanation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projects = ResearchProjectRepository(context)
        val previous = projects.activeProject()
        val project = projects.createProject("XAI Android recreation ${System.nanoTime()}", "Isolated activity lifecycle fixture")
        projects.activateProject(project.id)
        val root = AppStoragePaths(context).rootDirectory
        val model = File(root, "models/recreation-model.bin").apply { parentFile!!.mkdirs(); writeText("stable recreation model") }
        val gate = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val owner = ExplainabilitySessionCoordinator(context, root) { run, _, _, _, cancelled, progress ->
            calls.incrementAndGet(); entered.countDown()
            progress(ExplainabilityProgress(ExplainabilityPhase.EXPLAINING_SAMPLES, 1, 5, "Controlled Android explanation is running."))
            check(gate.await(60, TimeUnit.SECONDS))
            if (cancelled()) throw ExplainabilityCancelledException()
            result(run.runId)
        }
        fun mountObserver() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent { RobotKinematicsLabTheme { ExplainabilitySessionPanel(owner) } }
            }
            composeRule.waitForIdle()
        }
        fun recreateAndMountObserver() {
            lateinit var oldActivity: MainActivity
            composeRule.activityRule.scenario.onActivity { oldActivity = it }
            composeRule.activityRule.scenario.recreate()
            composeRule.activityRule.scenario.onActivity {
                assertNotSame("Android must construct a new Activity", oldActivity, it)
                assertTrue("The old Activity must actually be destroyed", oldActivity.isDestroyed)
            }
            mountObserver()
        }
        try {
            composeRule.waitUntil(10000) { !owner.state.value.loading }
            mountObserver()
            val run = TrainingRunSummary("recreation-run", "Android fixture", "unused.csv", 1, 2,
                null, null, 0.0, 42, TrainingSplitStrategy.entries.first(), 100,
                root.path, "unused-history.csv", listOf(model.path))
            composeRule.runOnIdle { owner.start(run, model.path, 16, 8).getOrThrow() }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            recreateAndMountObserver()
            composeRule.onNodeWithTag("explainability-cancel").assertExists()
            assertTrue(owner.state.value.running)
            // Navigate away: dispose the full report panel while the process continues.
            composeRule.activityRule.scenario.onActivity { it.setContent { Text("Another destination") } }
            composeRule.waitForIdle()
            gate.countDown()
            composeRule.waitUntil(10000) { owner.state.value.status == ExplainabilitySessionStatus.COMPLETED }
            recreateAndMountObserver()
            composeRule.onNodeWithTag("explainability-saved-report").assertExists()
            composeRule.onNodeWithTag("explainability-cancel").assertDoesNotExist()
            assertFalse(owner.state.value.running)
            assertEquals(1, calls.get())
            val committed = ExplainabilitySessionRepository(File(root, "training/explainability")).restore()
            assertEquals(owner.state.value.report, committed.report)
            composeRule.activityRule.scenario.onActivity { it.setContent { Text("Fixture complete") } }
            composeRule.waitForIdle()
        } finally {
            gate.countDown(); owner.cancel()
            projects.activateProject(previous.id)
        }
    }

    private fun result(runId: String) = ExplainabilityResult(runId, TrainingFeatureProfile.BASELINE_KINEMATICS,
        "recreation-candidate", 1, 8, listOf(GlobalFeatureImportance("target_x", 1.0, 1.0)),
        listOf(LocalPredictionExplanation(0, TrainingLabel.ACCEPTED, TrainingLabel.ACCEPTED, TrainingLabel.REJECTED,
            0.8, 0.0, 1.0, 0.0, listOf(FeatureAttribution("target_x", 1.0, 1.0)))),
        1.0, 1.0, 0.0, 0.0, 1)
}
