package com.robotkinematicslab.mobile.ui.dataset.robotstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RobotReferencePdfViewerLogicTest {

    @Test
    fun standardPortraitPageUsesReadableWidthWithinMemoryBudget() {
        val dimensions = calculatePdfRenderDimensions(pageWidth = 612, pageHeight = 792)

        assertEquals(1_200, dimensions.width)
        assertTrue(dimensions.height > dimensions.width)
        assertTrue(dimensions.width.toLong() * dimensions.height <= 2_500_000L)
    }

    @Test
    fun extremeTallPageCannotCreateAnUnboundedBitmap() {
        val dimensions =
            calculatePdfRenderDimensions(
                pageWidth = 100,
                pageHeight = 100_000
            )

        assertTrue(dimensions.width in 1..4_096)
        assertTrue(dimensions.height in 1..4_096)
        assertTrue(dimensions.width.toLong() * dimensions.height <= 2_500_000L)
    }

    @Test
    fun pixelBudgetWinsWhenPreferredWidthWouldAllocateTooMuch() {
        val dimensions =
            calculatePdfRenderDimensions(
                pageWidth = 1_000,
                pageHeight = 1_000,
                preferredWidth = 10_000
            )

        assertTrue(dimensions.width < 4_096)
        assertTrue(dimensions.height < 4_096)
        assertTrue(dimensions.width.toLong() * dimensions.height <= 2_500_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSizedPdfPageIsRejectedBeforeBitmapAllocation() {
        calculatePdfRenderDimensions(pageWidth = 0, pageHeight = 792)
    }

    @Test
    fun safePdfFileNameIsAccepted() {
        validateBundledPdfFileName("kinova_gen3_user_guide_r07.pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun parentDirectoryTraversalIsRejected() {
        validateBundledPdfFileName("../manual.pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPdfExtensionIsRejected() {
        validateBundledPdfFileName("manual.txt")
    }

    @Test
    fun standardHttpsOfficialUrlIsAccepted() {
        val url = validateOfficialPdfUrl("https://manufacturer.example/manuals/robot.pdf")

        assertEquals("manufacturer.example", url.host)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextOfficialUrlIsRejected() {
        validateOfficialPdfUrl("http://manufacturer.example/manuals/robot.pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun credentialBearingOfficialUrlIsRejected() {
        validateOfficialPdfUrl("https://user:password@manufacturer.example/robot.pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonStandardHttpsPortIsRejected() {
        validateOfficialPdfUrl("https://manufacturer.example:8443/robot.pdf")
    }

    @Test
    fun pinnedRemotePdfPolicyAcceptsCompleteIntegrityContract() {
        validateRemotePdfIntegrityPolicy(remoteDocument())
    }

    @Test
    fun remotePdfWithoutDigestIsRejectedBeforeAnyNetworkAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePdfIntegrityPolicy(remoteDocument().copy(expectedSha256 = null))
        }
    }

    @Test
    fun remotePdfWithoutByteOrPageBoundsIsRejectedBeforeAnyNetworkAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePdfIntegrityPolicy(remoteDocument().copy(expectedByteCount = null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRemotePdfIntegrityPolicy(remoteDocument().copy(expectedPageCount = null))
        }
    }

    @Test
    fun controlCharactersCannotBeSmuggledIntoOfficialUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            validateOfficialPdfUrl("https://manufacturer.example/manual.pdf\nignored")
        }
    }

    private fun remoteDocument() =
        RobotReferenceDocument(
            localFileName = "manufacturer_manual.pdf",
            title = "Manufacturer manual",
            revision = "1",
            relevantPages = "All",
            sourceLabel = "Manufacturer",
            sourceUrl = "https://manufacturer.example/manual.pdf",
            provenanceNote = "Test fixture",
            officialDownloadUrl = "https://manufacturer.example/manual.pdf",
            expectedSha256 = "a".repeat(64),
            expectedByteCount = 1_024,
            expectedPageCount = 10
        )
}
