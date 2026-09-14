package com.robotkinematicslab.mobile.ui.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.storage.evidence.ProjectArtifact
import com.robotkinematicslab.mobile.storage.evidence.ProjectArtifactFormat
import com.robotkinematicslab.mobile.storage.evidence.ProjectEvidenceSnapshot
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectEvidencePackContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun summaryShowsProjectCountsSizeRootAndStatus() {
        val snapshot = evidenceSnapshot()

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProjectEvidencePackContent(
                        snapshot = snapshot,
                        busy = false,
                        status = "Verified 3 files.",
                        onRefresh = {},
                        onExportProject = {},
                        onOpenArtifact = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PROJECT_EVIDENCE_PACK_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("One project, one complete context").assertIsDisplayed()
        composeRule.onNodeWithText("FILES").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("FOLDERS").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("SIZE").assertIsDisplayed()
        composeRule.onNodeWithText("3.0 KiB").assertIsDisplayed()
        composeRule.onNodeWithText(snapshot.rootPath).assertIsDisplayed()
        composeRule.onNodeWithText("Verified 3 files.").assertIsDisplayed()
    }

    @Test
    fun foldersAreCollapsedByDefaultAndFileTapDispatchesSelectedArtifact() {
        val snapshot = evidenceSnapshot()
        val dataset = snapshot.artifacts.first { it.relativePath == "datasets/factory.csv" }
        var openedArtifact: ProjectArtifact? = null

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProjectEvidencePackContent(
                        snapshot = snapshot,
                        busy = false,
                        status = "Ready.",
                        onRefresh = {},
                        onExportProject = {},
                        onOpenArtifact = { openedArtifact = it }
                    )
                }
            }
        }

        val datasetFileTag = "EvidenceFile:${dataset.relativePath}"
        composeRule.onNodeWithTag(datasetFileTag).assertDoesNotExist()
        composeRule.onNodeWithTag("EvidenceFolder:datasets")
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag(datasetFileTag).performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(dataset, openedArtifact) }

        composeRule.onNodeWithTag("EvidenceFolder:datasets").performScrollTo().performClick()
        composeRule.onNodeWithTag(datasetFileTag).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(dataset, openedArtifact) }
    }

    @Test
    fun busyStateDisablesRefreshAndProjectExport() {
        var refreshRequests = 0
        var exportRequests = 0

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProjectEvidencePackContent(
                        snapshot = evidenceSnapshot(),
                        busy = true,
                        status = "Creating and re-verifying the portable project package…",
                        onRefresh = { refreshRequests += 1 },
                        onExportProject = { exportRequests += 1 },
                        onOpenArtifact = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Creating and re-verifying the portable project package…")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PROJECT_EXPORT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(PROJECT_REFRESH_TAG).assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(0, refreshRequests)
            assertEquals(0, exportRequests)
        }
    }

    @Test
    fun readyStateEnablesRefreshAndProjectExportCallbacks() {
        var refreshRequests = 0
        var exportRequests = 0
        var openedArtifact: ProjectArtifact? = null

        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProjectEvidencePackContent(
                        snapshot = evidenceSnapshot(),
                        busy = false,
                        status = "Verified and ready to export.",
                        onRefresh = { refreshRequests += 1 },
                        onExportProject = { exportRequests += 1 },
                        onOpenArtifact = { openedArtifact = it }
                    )
                }
            }
        }

        composeRule.onNodeWithTag(PROJECT_EXPORT_TAG)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(PROJECT_REFRESH_TAG)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, exportRequests)
            assertEquals(1, refreshRequests)
            assertNull(openedArtifact)
        }
    }

    private fun evidenceSnapshot(): ProjectEvidenceSnapshot =
        ProjectEvidenceSnapshot(
            project =
                ResearchProject(
                    id = "ui-evidence-study",
                    name = "UI Evidence Study",
                    objective = "Verify the portable evidence browser.",
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 2_000L,
                    usesLegacyWorkspace = false
                ),
            rootPath = "/test/RobotKinematicsLab/projects/ui-evidence-study",
            generatedAtEpochMillis = 3_000L,
            artifacts =
                listOf(
                    ProjectArtifact(
                        relativePath = "datasets/factory.csv",
                        category = "datasets",
                        format = ProjectArtifactFormat.CSV,
                        byteCount = 1_024L,
                        modifiedAtEpochMillis = 2_100L,
                        sha256 = "a".repeat(64)
                    ),
                    ProjectArtifact(
                        relativePath = "models/classifier.rklm",
                        category = "models",
                        format = ProjectArtifactFormat.CLASSIFIER_MODEL,
                        byteCount = 1_024L,
                        modifiedAtEpochMillis = 2_200L,
                        sha256 = "b".repeat(64)
                    ),
                    ProjectArtifact(
                        relativePath = "models/model-card.json",
                        category = "models",
                        format = ProjectArtifactFormat.JSON,
                        byteCount = 1_024L,
                        modifiedAtEpochMillis = 2_300L,
                        sha256 = "c".repeat(64)
                    )
                )
        )
}
