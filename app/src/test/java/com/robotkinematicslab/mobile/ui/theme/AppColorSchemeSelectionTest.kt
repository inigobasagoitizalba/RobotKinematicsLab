package com.robotkinematicslab.mobile.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorSchemeSelectionTest {

    @Test
    fun explicitHighContrastTakesPriorityOverTutorialPaletteOverride() {
        val source =
            resolveAppColorSchemeSource(
                highContrast = true,
                temporaryPaletteOverride = AppVisualPalette.NEON_CIRCUIT,
                useDeviceDynamicColors = true,
                supportsDynamicColors = true
            )

        assertEquals(AppColorSchemeSource.HIGH_CONTRAST, source)
    }

    @Test
    fun tutorialPaletteStillOverridesOrdinaryAndDynamicThemes() {
        val source =
            resolveAppColorSchemeSource(
                highContrast = false,
                temporaryPaletteOverride = AppVisualPalette.NEON_CIRCUIT,
                useDeviceDynamicColors = true,
                supportsDynamicColors = true
            )

        assertEquals(AppColorSchemeSource.TEMPORARY_PALETTE, source)
    }
}
