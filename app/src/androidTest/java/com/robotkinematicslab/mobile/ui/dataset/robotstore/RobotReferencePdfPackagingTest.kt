package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotReferencePdfPackagingTest {

    @Test
    fun allAdmittedTechnicalDocumentsAreEitherBundledOrStrictlyPinnedToOfficialDownloads() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val references = IndustrialRobotReferenceCatalog.entries

        assertTrue("The admitted robot catalog must not be empty", references.isNotEmpty())
        references.forEach { reference ->
            assertTrue("${reference.displayName} must cite at least one manual", reference.documents.isNotEmpty())
            reference.documents.forEach documentLoop@{ document ->
                val resourceId = document.resourceId
                if (resourceId == null) {
                    assertTrue(
                        "${reference.displayName} remote document must use an official HTTPS address",
                        document.officialDownloadUrl?.startsWith("https://") == true
                    )
                    assertTrue(
                        "${reference.displayName} remote document must pin a SHA-256 digest",
                        document.expectedSha256?.matches(Regex("[A-Fa-f0-9]{64}")) == true
                    )
                    assertTrue(
                        "${reference.displayName} remote document must pin its byte count",
                        (document.expectedByteCount ?: 0L) > 0L
                    )
                    assertTrue(
                        "${reference.displayName} remote document must pin its page count",
                        (document.expectedPageCount ?: 0) > 0
                    )
                    return@documentLoop
                }
                val packagedBytes =
                    context.resources.openRawResource(resourceId).use { input ->
                        input.readBytes()
                    }
                assertTrue(
                    "${reference.displayName} must contain a PDF signature",
                    packagedBytes.size > 5 &&
                        packagedBytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())
                )

                val localCopy =
                    File(context.cacheDir, "pdf-package-test-${reference.id}-${document.localFileName}")
                try {
                    localCopy.outputStream().use { output -> output.write(packagedBytes) }
                    ParcelFileDescriptor.open(localCopy, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            assertTrue(
                                "${reference.displayName} must contain at least one readable page",
                                renderer.pageCount > 0
                            )
                        }
                    }
                } finally {
                    localCopy.delete()
                }
            }
        }
    }
}
