package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.RememberObserver

/** The producer owns an undisplayed page; a remembered display lease takes ownership exactly once. */
internal class PdfBitmapOwnership(private val bitmap: Bitmap) {
    private var claimed = false
    private var released = false

    @Synchronized fun claimForDisplay(): Bitmap? {
        if (released || claimed) return null
        claimed = true
        return bitmap
    }

    @Synchronized fun releaseIfUnclaimed() {
        if (!claimed) release()
    }

    @Synchronized fun release() {
        if (!released) {
            released = true
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

/** Abandoned compositions also release a claimed page; DisposableEffect alone cannot cover them. */
internal class PdfDisplayLease(private val ownership: PdfBitmapOwnership) : RememberObserver {
    val bitmap: Bitmap? = ownership.claimForDisplay()
    override fun onRemembered() = Unit
    override fun onForgotten() { if (bitmap != null) ownership.release() }
    override fun onAbandoned() { if (bitmap != null) ownership.release() }
}

/** Native rendering is synchronous; always reclaim allocation on failure or cancellation afterwards. */
internal fun renderPdfBitmap(
    width: Int,
    height: Int,
    checkCancellation: () -> Unit = {},
    render: (Bitmap) -> Unit
): Bitmap {
    checkCancellation()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(Color.WHITE)
        render(bitmap)
        checkCancellation()
        return bitmap
    } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
    }
}
