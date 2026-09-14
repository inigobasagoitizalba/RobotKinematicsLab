package com.robotkinematicslab.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisualThemePreferencesTest {

    @Test
    fun missingOrUnknownPaletteFallsBackToReproducibleOceanTheme() {
        val missing = AppVisualThemePreferenceCodec.decode(null, false)
        val corrupt = AppVisualThemePreferenceCodec.decode("not-a-real-palette", false)

        assertEquals(AppVisualPalette.OCEAN, missing.palette)
        assertEquals(AppVisualPalette.OCEAN, corrupt.palette)
        assertFalse(corrupt.useDeviceDynamicColors)
    }

    @Test
    fun everyHistoricalPaletteMigratesToUniversityResearchTheme() {
        AppVisualPalette.entries.forEach { palette ->
            val decoded = AppVisualThemePreferenceCodec.decode(palette.persistedId, true)
            assertEquals(AppVisualPalette.OCEAN, decoded.palette)
            assertFalse(decoded.useDeviceDynamicColors)
        }
    }

    @Test
    fun legacyIdentifiersAreAcceptedButCannotReenableDecorativeThemes() {
        AppVisualPalette.entries.forEach { palette ->
            val decoded = AppVisualThemePreferenceCodec.decode(palette.persistedId, true)
            assertEquals(AppVisualThemePreferences(), decoded)
        }
    }

    @Test
    fun paletteIdentifiersAreUniqueForUnambiguousPersistence() {
        val persistedIds = AppVisualPalette.entries.map(AppVisualPalette::persistedId)

        assertEquals(persistedIds.size, persistedIds.distinct().size)
    }

}
