package com.robotkinematicslab.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.diagnostic.diagnosticDistributionContainerColor
import com.robotkinematicslab.mobile.ui.diagnostic.diagnosticDistributionContentColor
import com.robotkinematicslab.mobile.ui.editor.robotEditorJointContainerColor
import com.robotkinematicslab.mobile.ui.editor.robotEditorJointContentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeonCircuitThemeTest {

    @Test
    fun semanticForegroundsMeetAccessibleContrastOnTheirBackgrounds() {
        val scheme = NeonCircuitColorScheme
        val pairs =
            listOf(
                "primary" to (scheme.onPrimary to scheme.primary),
                "primary container" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "secondary" to (scheme.onSecondary to scheme.secondary),
                "secondary container" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "tertiary" to (scheme.onTertiary to scheme.tertiary),
                "tertiary container" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "background" to (scheme.onBackground to scheme.background),
                "surface" to (scheme.onSurface to scheme.surface),
                "surface variant" to (scheme.onSurfaceVariant to scheme.surfaceVariant),
                "error" to (scheme.onError to scheme.error),
                "error container" to (scheme.onErrorContainer to scheme.errorContainer)
            )

        pairs.forEach { (role, colors) ->
            val ratio = contrastRatio(foreground = colors.first, background = colors.second)
            assertTrue("$role contrast was $ratio; expected at least 4.5:1", ratio >= 4.5)
        }
    }

    @Test
    fun canvasAndSurfacesRemainDarkInsteadOfTurningIntoAccentColour() {
        assertTrue(relativeLuminance(NeonCircuitColorScheme.background) < 0.02)
        assertTrue(relativeLuminance(NeonCircuitColorScheme.surface) < 0.02)
    }

    @Test
    fun editorAndDiagnosticCardsUseContrastingSemanticPairsInsteadOfLightConstants() {
        val scheme = NeonCircuitColorScheme
        val semanticPairs =
            listOf(
                robotEditorJointContentColor(scheme) to robotEditorJointContainerColor(scheme),
                diagnosticDistributionContentColor(scheme) to
                    diagnosticDistributionContainerColor(scheme)
            )

        assertEquals(scheme.surfaceVariant, robotEditorJointContainerColor(scheme))
        assertEquals(scheme.onSurfaceVariant, robotEditorJointContentColor(scheme))
        assertEquals(scheme.surface, diagnosticDistributionContainerColor(scheme))
        assertEquals(scheme.onSurface, diagnosticDistributionContentColor(scheme))
        semanticPairs.forEach { (foreground, background) ->
            assertTrue(contrastRatio(foreground, background) >= 4.5)
        }

        val corruptLightBackgrounds = listOf(Color.White, Color(0xFFF4F7FA), Color(0xFFF7F7F7))
        corruptLightBackgrounds.forEach { background ->
            assertTrue(contrastRatio(scheme.onSurface, background) < 4.5)
        }
    }

    @Test
    fun legacyUiTextLiteralsFailWhereSemanticForestAndNeonRolesRemainReadable() {
        val legacyMutedText = Color(0xFF455A64)
        val legacyDarkText = Color(0xFF263238)
        val legacyWarningText = Color(0xFFB26A00)

        assertTrue(contrastRatio(legacyMutedText, NeonCircuitColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(legacyDarkText, NeonCircuitColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(legacyWarningText, NeonCircuitColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(legacyWarningText, ForestColorScheme.surface) < 4.5)
        assertTrue(contrastRatio(Color.Red, ForestColorScheme.surface) < 4.5)

        listOf(ForestColorScheme, NeonCircuitColorScheme).forEach { scheme ->
            val semanticForegrounds =
                listOf(
                    scheme.onSurface,
                    scheme.onSurfaceVariant,
                    scheme.primary,
                    scheme.tertiary,
                    scheme.error
                )
            semanticForegrounds.forEach { foreground ->
                assertTrue(contrastRatio(foreground, scheme.surface) >= 4.5)
            }
        }
    }

    private fun contrastRatio(
        foreground: Color,
        background: Color
    ): Double {
        val foregroundLuminance = relativeLuminance(foreground)
        val backgroundLuminance = relativeLuminance(background)
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(component: Float): Double {
            val value = component.toDouble()
            return if (value <= 0.04045) {
                value / 12.92
            } else {
                Math.pow((value + 0.055) / 1.055, 2.4)
            }
        }

        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
