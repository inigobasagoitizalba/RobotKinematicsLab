package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.pdf.PdfDocument
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotReferencePdfViewerUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longDocumentControlsAllowBoundedPageJumpAndAccessibleZoom() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fileName = "viewer-navigation-test.pdf"
        val cachedFile = File(File(context.cacheDir, "robot-reference-pdfs"), fileName)
        writeTwoPagePdf(cachedFile)
        val document =
            cachedTestDocument(
                fileName = fileName,
                file = cachedFile,
                title = "Viewer navigation test"
            )
        composeRule.setContent {
            MaterialTheme {
                RobotReferencePdfDialog(document = document, onDismiss = {})
            }
        }

        composeRule.waitForTag("RobotReferencePdfPage")
        composeRule.onNodeWithText(document.title).assertIsDisplayed()
        composeRule.onNodeWithText("Relevant pages: ${document.relevantPages}").assertIsDisplayed()

        composeRule.onNodeWithTag("RobotReferencePdfPageInput").performTextClearance()
        composeRule.onNodeWithTag("RobotReferencePdfPageInput").performTextInput("999999")
        composeRule.onNodeWithTag("RobotReferencePdfPageGo").performClick()

        composeRule.waitUntil(timeoutMillis = PDF_VIEWER_TEST_TIMEOUT_MS) {
            composeRule
                .onAllNodesWithContentDescription("${document.title}, page 2 of 2")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag("RobotReferencePdfZoomIn").performClick()
        composeRule.onNodeWithText("150%").assertIsDisplayed()

        composeRule.onNodeWithTag("RobotReferencePdfZoomReset").performClick()
        composeRule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun corruptResourceFailsSafelyAndOffersRetryWithoutClosing() {
        val reference = IndustrialRobotReferenceCatalog.entries.first()
        val invalidDocument =
            reference.documents.first().copy(
                resourceId = reference.artworkResId,
                localFileName = "invalid-but-contained.pdf"
            )
        composeRule.setContent {
            MaterialTheme {
                RobotReferencePdfDialog(document = invalidDocument, onDismiss = {})
            }
        }

        composeRule.waitForTag("RobotReferencePdfRetry")
        composeRule.onNodeWithTag("RobotReferencePdfRetry").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This document could not be verified or rendered. Tap Retry; the robot data remains unchanged."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("RobotReferencePdfRetry").performClick()
        composeRule.waitForTag("RobotReferencePdfRetry")
    }

    @Test
    fun remoteDocumentWaitsForExplicitDownloadConsent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reference = IndustrialRobotReferenceCatalog.entries.first()
        val document =
            reference.documents.first().copy(
                resourceId = null,
                localFileName = "explicit-download-consent-test.pdf",
                officialDownloadUrl = "https://127.0.0.1/this-must-not-be-contacted.pdf"
            )
        File(File(context.cacheDir, "robot-reference-pdfs"), document.localFileName).delete()
        composeRule.setContent {
            MaterialTheme {
                RobotReferencePdfDialog(document = document, onDismiss = {})
            }
        }

        composeRule.waitForTag("RobotReferencePdfDownload")
        composeRule.onNodeWithTag("RobotReferencePdfDownload").assertIsDisplayed()
        composeRule.onNodeWithText("Download official PDF").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForTag(tag: String) {
        waitUntil(timeoutMillis = PDF_VIEWER_TEST_TIMEOUT_MS) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun cachedTestDocument(fileName: String, file: File, title: String) =
        RobotReferenceDocument(
            resourceId = null,
            localFileName = fileName,
            title = title,
            revision = "Test fixture",
            relevantPages = "All pages",
            sourceLabel = "Instrumented test fixture",
            sourceUrl = "https://example.invalid/viewer-test-fixture",
            provenanceNote = "Generated only as a deterministic viewer test fixture.",
            officialDownloadUrl = "https://example.invalid/viewer-test-fixture.pdf",
            expectedSha256 = sha256(file),
            expectedByteCount = file.length(),
            expectedPageCount = 2
        )

    private fun writeTwoPagePdf(destination: File) {
        destination.parentFile?.mkdirs()
        val pdf = PdfDocument()
        try {
            repeat(2) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(320, 480, index + 1).create())
                page.canvas.drawText("Viewer test page ${index + 1}", 24f, 48f, android.graphics.Paint())
                pdf.finishPage(page)
            }
            destination.outputStream().use(pdf::writeTo)
        } finally {
            pdf.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private const val PDF_VIEWER_TEST_TIMEOUT_MS = 30_000L
