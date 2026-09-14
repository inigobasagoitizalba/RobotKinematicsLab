package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.ui.dataset.robotstore.IndustrialRobotReferenceCatalog
import com.robotkinematicslab.mobile.ui.onboarding.dismissFirstRunTutorialIfPresent
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceStudyRepository
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotStoreUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun storeShowsPrimaryEvidenceAndRequiresConsentBeforeOfficialPdfDownload() {
        // This case owns the no-local-copy precondition. A manual retained by a previous
        // debug session is a valid offline state, but it would turn this consent test into
        // a PDF-rendering test and make its result depend on execution order/device history.
        val context = composeRule.activity.applicationContext
        val document =
            IndustrialRobotReferenceCatalog.entries
                .single { it.id == "ur5e" }
                .documents
                .first()
        listOf(context.filesDir, context.cacheDir).forEach { root ->
            val localCopy = File(File(root, "robot-reference-pdfs"), document.localFileName)
            check(!localCopy.exists() || localCopy.delete()) {
                "Could not establish the no-local-copy consent-test precondition: $localCopy"
            }
        }
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("Dataset Factory").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Robots", substring = true).performClick()

        openSection("dataset-robots-reference-gallery")
        composeRule.onNodeWithText("Commercial references · not simulation presets").waitAndScrollTo()
        composeRule
            .onNodeWithTag("IndustrialRobotReferenceGallery")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("RobotReferenceCard:ur5e").performClick()
        composeRule
            .onNodeWithText("Technical specifications")
            .assertIsDisplayed()
        composeRule.onNodeWithText("850 mm").assertIsDisplayed()
        composeRule.onNodeWithText("Manufacturer joint limits").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag("RobotReferencePdfButton:ur5e:0")
            .performScrollTo()
        composeRule.onNodeWithTag("RobotReferencePdfButton:ur5e:0").performClick()
        composeRule.onNodeWithTag("RobotReferencePdfViewer").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            runCatching {
                composeRule.onNodeWithTag("RobotReferencePdfDownload").fetchSemanticsNode()
            }.isSuccess || runCatching {
                composeRule.onNodeWithTag("RobotReferencePdfPage").fetchSemanticsNode()
            }.isSuccess || runCatching {
                composeRule.onNodeWithTag("RobotReferencePdfRetry").fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag("RobotReferencePdfRetry").assertDoesNotExist()
        if (runCatching { composeRule.onNodeWithTag("RobotReferencePdfDownload").fetchSemanticsNode() }.isSuccess) {
            composeRule.onNodeWithTag("RobotReferencePdfDownload").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("RobotReferencePdfImport").performScrollTo().assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag("RobotReferencePdfPage").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("RobotReferencePdfRetry").assertDoesNotExist()
        composeRule.onNodeWithTag("RobotReferencePdfClose").performClick()
        composeRule.onNodeWithTag("RobotReferenceDialogClose:ur5e").performClick()

        composeRule.onNodeWithText("Dataset-ready DH models").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("RobotStoreSearch").performScrollTo().performTextInput("mixed")
        composeRule.onNodeWithTag("RobotStoreSearch").assertTextContains("mixed")
    }

    @Test
    fun creatingCustomRobotStartsWithAnExplicitNameDialog() {
        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("Dataset Factory").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Robots", substring = true).performClick()

        composeRule.onNodeWithText("+ Create custom DH robot").waitAndScrollTo()
        composeRule.onNodeWithText("+ Create custom DH robot").performClick()

        composeRule.onNodeWithTag("robot-name-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("robot-name-dialog-content")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        composeRule.onNodeWithTag("confirm-robot-name").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Create robot").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save to library").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun robotOwnedWorkspaceQuickAccessLoadsEvidenceAndReturnsToTheCard() {
        val context = composeRule.activity.applicationContext
        val savedRobot = DatasetRobotPresets().buildDefaults().first()
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot = savedRobot.robot,
                config =
                    RobotWorkspaceAnalysisConfig(
                        sampleCount = 256,
                        randomSeed = 42,
                        voxelResolution = 10,
                        replicationCount = 2
                    ),
                requestedWorkerCount = 2
            )
        RobotWorkspaceStudyRepository(context).save(study)

        composeRule.dismissFirstRunTutorialIfPresent()
        composeRule.onNodeWithTag("open-project:layer-1-research").performClick()
        composeRule.onNodeWithText("Prepare").performClick()
        composeRule.onNodeWithText("Dataset Factory").performClick()
        composeRule.onNodeWithText("Robots", substring = true).performClick()

        openSection("dataset-robots-saved-models")
        composeRule.onNodeWithTag("DatasetRobotCard:${savedRobot.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("RobotWorkspaceOpen:${savedRobot.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("DatasetRobotDetails:${savedRobot.id}").performClick()
        composeRule
            .onNodeWithTag("RobotWorkspaceOpen:${savedRobot.id}")
            .waitAndScrollTo()
        composeRule.onNodeWithTag("RobotWorkspaceOpen:${savedRobot.id}").performClick()

        composeRule.onNodeWithText("3D Robot Workspace Laboratory").assertIsDisplayed()
        composeRule
            .onNodeWithTag("workspace-study-robot")
            .performScrollTo()
            .assertTextContains(savedRobot.robot.name)
        composeRule.onNodeWithTag("workspace-return-to-robot").performScrollTo().performClick()

        composeRule.onNodeWithText("Dataset-ready DH models").performScrollTo().assertIsDisplayed()
        openSection("dataset-robots-saved-models")
        composeRule.onNodeWithTag("DatasetRobotCard:${savedRobot.id}").performScrollTo().assertIsDisplayed()
    }

    private fun openSection(tag:String) {
        val header=composeRule.onNodeWithTag(tag).performScrollTo()
        if(header.fetchSemanticsNode().config[SemanticsProperties.StateDescription] != "Expanded") {
            header.performSemanticsAction(SemanticsActions.OnClick)
        }
        composeRule.onNodeWithTag("$tag-content").assertExists()
    }

    private fun SemanticsNodeInteraction.waitAndScrollTo() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { fetchSemanticsNode() }.isSuccess
        }
        performScrollTo()
        assertIsDisplayed()
    }

}
