package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBitmapOwnershipTest {
    @Test fun renderFailureAndCancellationReleaseTheirAllocations() {
        var failedBitmap: Bitmap? = null
        assertThrows(IllegalStateException::class.java) {
            renderPdfBitmap(32, 32) { bitmap -> failedBitmap = bitmap; error("native render failed") }
        }
        assertTrue(requireNotNull(failedBitmap).isRecycled)
        var cancelledBitmap: Bitmap? = null
        var cancel = false
        assertThrows(CancellationException::class.java) {
            renderPdfBitmap(32, 32, { if (cancel) throw CancellationException("cancelled") }) { bitmap ->
                cancelledBitmap = bitmap
                cancel = true
            }
        }
        assertTrue(requireNotNull(cancelledBitmap).isRecycled)
    }

    @Test fun undisplayedPageIsReclaimedWhenProducerIsCancelled() {
        val bitmap = renderPdfBitmap(32, 32) { }
        val owner = PdfBitmapOwnership(bitmap)
        owner.releaseIfUnclaimed()
        assertTrue(bitmap.isRecycled)
        assertNull(owner.claimForDisplay())
        owner.releaseIfUnclaimed()
    }

    @Test fun displayedPageOutlivesProducerAndIsReleasedByDisplayExactlyOnce() {
        val bitmap = renderPdfBitmap(32, 32) { }
        val owner = PdfBitmapOwnership(bitmap)
        val display = PdfDisplayLease(owner)
        assertSame(bitmap, display.bitmap)
        display.onRemembered()
        owner.releaseIfUnclaimed()
        assertFalse(bitmap.isRecycled)
        display.onForgotten()
        assertTrue(bitmap.isRecycled)
        display.onForgotten()
    }

    @Test fun abandonedCompositionAlsoReleasesClaimedBitmap() {
        val bitmap = renderPdfBitmap(32, 32) { }
        val owner = PdfBitmapOwnership(bitmap)
        val display = PdfDisplayLease(owner)
        owner.releaseIfUnclaimed()
        assertFalse(bitmap.isRecycled)
        display.onAbandoned()
        assertTrue(bitmap.isRecycled)
    }
}
