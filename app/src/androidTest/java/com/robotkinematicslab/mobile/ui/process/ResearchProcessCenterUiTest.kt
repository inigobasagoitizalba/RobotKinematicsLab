package com.robotkinematicslab.mobile.ui.process

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.process.ResearchResultDestination
import com.robotkinematicslab.mobile.process.ResearchResultReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ResearchProcessCenterUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val coordinator by lazy { ResearchProcessCoordinator.get(context) }

    @Before
    fun prepare() {
        coordinator.cancelAll("UI test reset")
        coordinator.clearFinished()
        context.getSharedPreferences("research_process_notifications", 0)
            .edit().putBoolean("permission_prompt_shown", true).commit()
    }

    @After
    fun cleanUp() {
        coordinator.cancelAll("UI test complete")
        coordinator.clearFinished()
    }

    @Test
    fun succeededCardOpensTheExactArtifactAndFailedCardCannotExposeOne() {
        val successful =
            coordinator.beginExternal(
                id = "training-success-ui",
                title = "Training success",
                kind = ResearchProcessKind.TRAINING,
                stage = "Finalising evidence",
                detail = "Saving the exact run."
            )
        var opened: ResearchResultReference? = null
        composeRule.setContent {
            MaterialTheme {
                ResearchProcessCenterOverlay(onOpenResult = { opened = it })
            }
        }
        composeRule.onNodeWithTag("research-process-dock").performClick()
        successful.completed(
            "Stored run-42.",
            ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "run-42")
        )
        val successfulJobId = requireNotNull(coordinator.snapshot("training-success-ui")).jobId

        composeRule.onNodeWithTag("research-process-open-result:$successfulJobId")
            .assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("run-42", opened?.artifactId)
        }

        val failed =
            coordinator.beginExternal(
                id = "training-failed-ui",
                title = "Training failed",
                kind = ResearchProcessKind.TRAINING,
                stage = "Persist models and evidence",
                detail = "Writing."
            )
        failed.failed("Injected storage failure")
        composeRule.onNodeWithTag("research-process-result").performClick()
        val failedJobId = requireNotNull(coordinator.snapshot("training-failed-ui")).jobId
        composeRule.onNodeWithTag("research-process-open-result:$failedJobId").assertDoesNotExist()
    }
}
