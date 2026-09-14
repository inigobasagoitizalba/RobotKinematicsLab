package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotReferencePdfCacheIntegrityTest {

    @Test
    fun sameLengthCorruptionInvalidatesPinnedOfficialCache() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cachedFile = File(File(context.filesDir, "robot-reference-pdfs"), "cache-integrity-test.pdf")
        writeOnePagePdf(cachedFile)
        val expectedSha256 = sha256(cachedFile)
        val document =
            RobotReferenceDocument(
                resourceId = null,
                localFileName = "cache-integrity-test.pdf",
                title = "Cache integrity test",
                revision = "Test fixture",
                relevantPages = "All pages",
                sourceLabel = "Instrumented test fixture",
                sourceUrl = "https://example.invalid/cache-test-fixture",
                provenanceNote = "Generated only as a deterministic cache-integrity fixture.",
                officialDownloadUrl = "https://example.invalid/cache-test-fixture.pdf",
                expectedSha256 = expectedSha256,
                expectedByteCount = cachedFile.length(),
                expectedPageCount = 1
            )
        assertNotNull(verifiedOfficialCacheOrNull(context, document))

        val corruptedBytes = cachedFile.readBytes()
        val mutationIndex = (corruptedBytes.size / 2).coerceAtLeast(5)
        corruptedBytes[mutationIndex] = (corruptedBytes[mutationIndex].toInt() xor 0x01).toByte()
        val previousTimestamp = cachedFile.lastModified()
        cachedFile.writeBytes(corruptedBytes)
        assertTrue(cachedFile.setLastModified(previousTimestamp))
        val corruptedOnDisk = cachedFile.readBytes()

        assertNull(verifiedOfficialCacheOrNull(context, document))
        assertTrue(corruptedOnDisk.contentEquals(cachedFile.readBytes()))
    }

    @Test
    fun oversizedCorruptCacheIsRejectedWithoutReadingOrDeletingIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cachedFile = File(File(context.filesDir, "robot-reference-pdfs"), "oversized-cache-test.pdf")
        cachedFile.parentFile?.mkdirs()
        java.io.RandomAccessFile(cachedFile, "rw").use { file ->
            file.setLength(40L * 1024L * 1024L + 1L)
        }
        val originalLength = cachedFile.length()
        val document =
            RobotReferenceDocument(
                localFileName = cachedFile.name,
                title = "Oversized cache fixture",
                revision = "Test",
                relevantPages = "All",
                sourceLabel = "Test",
                sourceUrl = "https://example.invalid/manual.pdf",
                provenanceNote = "Test fixture",
                officialDownloadUrl = "https://example.invalid/manual.pdf",
                expectedSha256 = "a".repeat(64),
                expectedByteCount = 1_024L,
                expectedPageCount = 1
            )

        assertNull(verifiedOfficialCacheOrNull(context, document))
        assertTrue(cachedFile.exists())
        assertTrue(cachedFile.length() == originalLength)
    }

    @Test
    fun verifiedManualRemainsAvailableAfterItsLegacyCacheIsRemoved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cached = File(File(context.cacheDir, "robot-reference-pdfs"), "offline-retention-test.pdf")
        writeOnePagePdf(cached)
        val document = RobotReferenceDocument(
            localFileName = cached.name, title = "Offline fixture", revision = "Test",
            relevantPages = "All", sourceLabel = "Test fixture",
            sourceUrl = "https://example.invalid/manual.pdf", provenanceNote = "Generated test PDF",
            officialDownloadUrl = "https://example.invalid/manual.pdf",
            expectedSha256 = sha256(cached), expectedByteCount = cached.length(), expectedPageCount = 1
        )
        val local = requireNotNull(verifiedOfficialCacheOrNull(context, document))
        assertTrue("Verified manuals must use durable storage", local.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertTrue("Migration must preserve the old copy", cached.isFile)
        cached.delete()
        assertNotNull("Opening offline must not require a cache or network", verifiedOfficialCacheOrNull(context, document))
    }

    @Test
    fun invalidLocalImportPreservesAnExistingVerifiedManual() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = File(context.cacheDir, "import-fixture.pdf")
        writeOnePagePdf(fixture)
        val expected = fixture.readBytes()
        val document = RobotReferenceDocument(
            localFileName = "local-import-test.pdf", title = "Local import", revision = "Test",
            relevantPages = "All", sourceLabel = "Test fixture",
            sourceUrl = "https://example.invalid/manual.pdf", provenanceNote = "Generated test PDF",
            officialDownloadUrl = "https://example.invalid/manual.pdf",
            expectedSha256 = sha256(fixture), expectedByteCount = fixture.length(), expectedPageCount = 1
        )
        val local = expected.inputStream().use { importVerifiedOfficialPdf(context, document, it) }
        assertTrue(expected.contentEquals(local.readBytes()))
        for (invalid in listOf(byteArrayOf(), expected.copyOf(expected.size - 1), expected + 0, expected.clone().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() })) {
            assertTrue(runCatching { invalid.inputStream().use { importVerifiedOfficialPdf(context, document, it) } }.isFailure)
            assertTrue(expected.contentEquals(local.readBytes()))
        }
        assertNotNull(verifiedOfficialCacheOrNull(context, document))
        assertTrue(runCatching { expected.inputStream().use { importVerifiedOfficialPdf(context, document.copy(expectedPageCount = 2), it) } }.isFailure)
        assertTrue(expected.contentEquals(local.readBytes()))
    }

    @Test
    fun corruptDurableManualRecoversVerifiedLegacyAndPreservesBothHistoricalCopies() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "legacy-recovery-${java.util.UUID.randomUUID()}.pdf"
        val legacy = File(File(context.cacheDir, "robot-reference-pdfs"), name)
        writeOnePagePdf(legacy)
        val expected = legacy.readBytes()
        val document = testDocument(name, legacy)
        val durable = File(File(context.filesDir, "robot-reference-pdfs"), name)
        durable.parentFile?.mkdirs()
        val corrupted = expected.clone().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        durable.writeBytes(corrupted)
        val previousPaths = durable.parentFile!!.listFiles().orEmpty().map { it.name }.toSet()
        val recovered = requireNotNull(verifiedOfficialCacheOrNull(context, document))
        assertEquals(durable.canonicalFile, recovered.canonicalFile)
        assertTrue(expected.contentEquals(recovered.readBytes()))
        assertTrue(expected.contentEquals(legacy.readBytes()))
        val preserved = durable.parentFile!!.listFiles().orEmpty().filter { it.name !in previousPaths }
        assertTrue(preserved.any { it.name.startsWith("unverified-manual-") && corrupted.contentEquals(it.readBytes()) })
    }

    @Test
    fun invalidLegacyAndCancelledVerificationPreserveAllHistoricalBytes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "legacy-rejection-${java.util.UUID.randomUUID()}.pdf"
        val legacy = File(File(context.cacheDir, "robot-reference-pdfs"), name)
        writeOnePagePdf(legacy)
        val document = testDocument(name, legacy)
        val originalLegacy = legacy.readBytes()
        val durable = File(File(context.filesDir, "robot-reference-pdfs"), name)
        durable.parentFile?.mkdirs()
        val historical = byteArrayOf(1, 2, 3, 4)
        durable.writeBytes(historical)
        assertThrows(CancellationException::class.java) {
            verifiedOfficialCacheOrNull(context, document) { throw CancellationException("cancelled") }
        }
        assertTrue(originalLegacy.contentEquals(legacy.readBytes()))
        assertTrue(historical.contentEquals(durable.readBytes()))
        legacy.writeBytes(originalLegacy.copyOf(originalLegacy.size - 1))
        val invalidLegacy = legacy.readBytes()
        assertNull(verifiedOfficialCacheOrNull(context, document))
        assertTrue(invalidLegacy.contentEquals(legacy.readBytes()))
        assertTrue(historical.contentEquals(durable.readBytes()))
    }

    @Test
    fun cancellationDuringCopyAndAtPublicationPreservesExistingManualBytes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fixture = File(context.cacheDir, "cancel-fixture-${java.util.UUID.randomUUID()}.pdf")
        writeOnePagePdf(fixture)
        val expected = fixture.readBytes()
        val document = testDocument("cancel-import-${java.util.UUID.randomUUID()}.pdf", fixture)
        val local = expected.inputStream().use { importVerifiedOfficialPdf(context, document, it) }
        var cancel = false
        val slowInput = object : java.io.ByteArrayInputStream(expected) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, minOf(length, 32))
                cancel = true
                return count
            }
        }
        assertThrows(CancellationException::class.java) {
            slowInput.use { input -> importVerifiedOfficialPdf(context, document, input) {
                if (cancel) throw CancellationException("cancelled during copy")
            } }
        }
        assertTrue(expected.contentEquals(local.readBytes()))
        var successfulChecks = 0
        expected.inputStream().use { input -> importVerifiedOfficialPdf(context, document, input) { successfulChecks++ } }
        assertTrue(local.setLastModified(2_000L))
        val previousTimestamp = local.lastModified()
        var checks = 0
        assertThrows(CancellationException::class.java) {
            expected.inputStream().use { input -> importVerifiedOfficialPdf(context, document, input) {
                if (++checks == successfulChecks) throw CancellationException("cancelled at publication")
            } }
        }
        assertEquals(successfulChecks, checks)
        assertEquals(previousTimestamp, local.lastModified())
        assertTrue(expected.contentEquals(local.readBytes()))
        assertTrue(local.parentFile!!.listFiles().orEmpty().none { it.name.startsWith(".${local.name}-") })
    }

    private fun testDocument(name: String, source: File) = RobotReferenceDocument(
        localFileName = name, title = "Offline integrity fixture", revision = "Generated test revision",
        relevantPages = "All", sourceLabel = "Instrumented fixture", sourceUrl = "https://example.invalid/manual.pdf",
        provenanceNote = "Generated locally for integrity testing; not manufacturer evidence.",
        officialDownloadUrl = "https://example.invalid/manual.pdf", expectedSha256 = sha256(source),
        expectedByteCount = source.length(), expectedPageCount = 1)

    private fun writeOnePagePdf(destination: File) {
        destination.parentFile?.mkdirs()
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(320, 480, 1).create())
            page.canvas.drawText("Cache integrity test", 24f, 48f, android.graphics.Paint())
            pdf.finishPage(page)
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
