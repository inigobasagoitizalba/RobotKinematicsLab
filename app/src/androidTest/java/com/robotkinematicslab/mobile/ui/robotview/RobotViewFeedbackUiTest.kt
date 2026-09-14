package com.robotkinematicslab.mobile.ui.robotview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.InteractiveFkDemo
import com.robotkinematicslab.mobile.domain.*
import com.robotkinematicslab.mobile.domain.result.*
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.RobotLabSavedState
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RobotViewFeedbackUiTest {
    @get:Rule val composeRule = createComposeRule()
    private val robot = RobotDefinition("Vertical stage", listOf(DHParameter(0.0, 0.0, 0.0, 0.0)),
        listOf(JointDefinition("J1", JointType.PRISMATIC, 0.0, 1.0, 0.2)))
    private val target = Vec3(0.0, 0.0, 0.7)

    @Test fun currentResultStatesHaveAccessibleTextAndCenteredStableAiControlWithCaptures() {
        val started = RobotViewIkState().begin(RobotViewIkContext(robot, target), RobotState(listOf(0.2)))
        val actual = KinematicsService().computeIK(robot, started.request!!.initialState, target)
        val verified = started.complete(started.request, RobotViewIkResponse(actual, 1.0))
        val hybrid = RobotViewIkState().begin(RobotViewIkContext(robot, target, "fixture-model:sha"), RobotState(listOf(0.2)))
        val fallback = hybrid.complete(hybrid.request!!, RobotViewIkResponse(
            IKResult(RobotState(listOf(0.7)), IKStatus.SUCCESS, true, 3, 0.0), 1.0,
            VerifiedIkPath.DETERMINISTIC_FALLBACK, "Neural proposal failed the tolerance check.", 0.2))
        val failed = started.complete(started.request, RobotViewIkResponse(
            actual.copy(state = RobotState(listOf(Double.NaN))), 1.0))
        var current by mutableStateOf(RobotViewIkState())
        var availability by mutableStateOf(robotViewAiAvailability(false, 10, 1))
        var enabled by mutableStateOf(false)
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()).testTag("robot-view-feedback-capture"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RobotViewStatusCard(true, robot.name, current.request?.context?.target, current, FKStatus.SUCCESS)
                    RobotViewAiControls(enabled, availability, false, { enabled = !enabled }, {})
                }
            }
        }
        val states = listOf("inactive" to RobotViewIkState(), "calculating" to started,
            "verified" to verified, "fallback" to fallback, "failed" to failed)
        for ((name, state) in states) {
            composeRule.runOnIdle { current = state }
            val status = composeRule.onNodeWithTag("robot-view-result-status")
            status.assertTextContains("Robot: Vertical stage", substring = true)
            if (state.request != null) status.assertTextContains("z=0.700000 m", substring = true)
            capture(name)
        }
        composeRule.runOnIdle { current = RobotViewIkState() }
        composeRule.onNodeWithTag("robot-view-ai-toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("robot-view-ai-toggle").assertIsSelected()
        val enabledBounds = composeRule.onNodeWithTag("robot-view-ai-toggle").fetchSemanticsNode().boundsInRoot
        val sectionBounds = composeRule.onNodeWithTag("robot-view-ai-section").fetchSemanticsNode().boundsInRoot
        assertEquals(sectionBounds.center.x, enabledBounds.center.x, 1f)
        composeRule.runOnIdle { availability = robotViewAiAvailability(false, null, 1) }
        composeRule.onNodeWithTag("robot-view-ai-toggle").assertIsNotEnabled().assertIsNotSelected()
        composeRule.onNodeWithTag("robot-view-ai-toggle").assertTextContains("OFF", substring = true)
        val disabledBounds = composeRule.onNodeWithTag("robot-view-ai-toggle").fetchSemanticsNode().boundsInRoot
        assertEquals(enabledBounds.width, disabledBounds.width, 1f)
        assertEquals(enabledBounds.center.x, disabledBounds.center.x, 1f)
        capture("unavailable-ai")
    }

    @Test fun actualRobotViewReturnFromSetupKeepsCurrentContextAndApplyingRobotClearsOldSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projects = ResearchProjectRepository(context)
        val previous = projects.activeProject()
        val project = projects.createProject("Robot View feedback ${System.nanoTime()}", "Isolated group 10 UI fixture")
        projects.activateProject(project.id)
        AppStorageRepository(context).saveRobotLabState(RobotLabSavedState(robot, listOf(0.2), target, "IK", System.currentTimeMillis()))
        var visible by mutableStateOf(true)
        try {
            composeRule.setContent { RobotKinematicsLabTheme { if (visible) InteractiveFkDemo() } }
            composeRule.onNodeWithTag("robot-lab-entry-view").performClick()
            waitForVerification()
            composeRule.onNodeWithTag("robot-view-result-status").assertTextContains("z=0.700000 m", substring = true)
            composeRule.onNodeWithTag("robot-lab-setup-tab").performClick()
            composeRule.onNodeWithTag("robot-view-result-status").assertDoesNotExist()
            composeRule.onNodeWithTag("robot-lab-view-tab").performClick()
            waitForVerification()
            composeRule.onNodeWithTag("robot-view-result-status").assertTextContains("Robot: Vertical stage", substring = true)
            composeRule.onNodeWithTag("robot-lab-setup-tab").performClick()
            composeRule.onNodeWithTag("robot-name").performScrollTo().performTextReplacement("Changed vertical stage")
            composeRule.onNodeWithTag("robot-apply").performScrollTo().performClick()
            composeRule.onNodeWithTag("robot-view-result-status").assertTextContains("Target: Not selected", substring = true)
            composeRule.onNodeWithTag("robot-view-result-status").assertTextContains("Robot: Changed vertical stage", substring = true)
            composeRule.onNodeWithTag("robot-view-result-status").assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "○ Inactive"))
            composeRule.onNodeWithTag("robot-lab-fk-mode").performClick()
            composeRule.onNodeWithTag("robot-view-result-status").assertTextContains("Forward kinematics output accepted", substring = true)
            composeRule.onNodeWithTag("robot-lab-joint-sliders").performScrollTo().assertExists()
            composeRule.runOnIdle { visible = false }
        } finally { projects.activateProject(previous.id) }
    }

    private fun waitForVerification() {
        composeRule.waitUntil(15000) {
            composeRule.onAllNodesWithTag("robot-view-result-status").fetchSemanticsNodes().any {
                it.config.getOrElse(SemanticsProperties.StateDescription) { "" }.contains("Verified")
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "test-evidence/group10").apply { mkdirs() }
        composeRule.onNodeWithTag("robot-view-feedback-capture").captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
