package com.robotkinematicslab.mobile.ui.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessibilityPreferencesTest {

    @Test
    fun missingOrCorruptValuesFallBackToSafeAccessibleDefaults() {
        val decoded =
            AppAccessibilityPreferenceCodec.decode(
                contentScaleId = "unknown-scale",
                highContrast = false,
                boldText = false,
                reduceMotion = false,
                readAloudEnabled = false,
                speechRate = Float.NaN
            )

        assertEquals(AppContentScale.STANDARD, decoded.contentScale)
        assertEquals(AppAccessibilityPreferences.DEFAULT_SPEECH_RATE, decoded.speechRate)
        assertFalse(decoded.highContrast)
        assertFalse(decoded.readAloudEnabled)
    }

    @Test
    fun speechRateIsClampedAndEveryContentScaleHasAStableIdentifier() {
        val tooSlow = decodeRate(-100f)
        val tooFast = decodeRate(100f)

        assertEquals(AppAccessibilityPreferences.MINIMUM_SPEECH_RATE, tooSlow)
        assertEquals(AppAccessibilityPreferences.MAXIMUM_SPEECH_RATE, tooFast)
        AppContentScale.entries.forEach { scale ->
            val decoded =
                AppAccessibilityPreferenceCodec.decode(
                    contentScaleId = scale.persistedId,
                    highContrast = true,
                    boldText = true,
                    reduceMotion = true,
                    readAloudEnabled = true,
                    speechRate = 1f
                )
            assertEquals(scale, decoded.contentScale)
            assertTrue(decoded.highContrast)
            assertTrue(decoded.boldText)
            assertTrue(decoded.reduceMotion)
            assertTrue(decoded.readAloudEnabled)
        }
    }

    private fun decodeRate(value: Float): Float =
        AppAccessibilityPreferenceCodec.decode(
            contentScaleId = AppContentScale.STANDARD.persistedId,
            highContrast = false,
            boldText = false,
            reduceMotion = false,
            readAloudEnabled = false,
            speechRate = value
        ).speechRate
}
