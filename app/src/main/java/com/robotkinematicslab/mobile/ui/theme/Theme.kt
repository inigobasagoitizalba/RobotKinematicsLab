package com.robotkinematicslab.mobile.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityPreferences

private fun labLightScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    primaryContainer: Color,
    secondaryContainer: Color,
    tertiaryContainer: Color
) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primaryContainer,
    onPrimaryContainer = Color(0xFF072335),
    secondary = secondary,
    onSecondary = Color.White,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = Color(0xFF09251F),
    tertiary = tertiary,
    onTertiary = Color.White,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = Color(0xFF331024),
    background = background,
    onBackground = Color(0xFF162027),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF162027),
    surfaceVariant = Color(0xFFDCE4EA),
    onSurfaceVariant = Color(0xFF40484D),
    outline = Color(0xFF70787E),
    outlineVariant = Color(0xFFC0C8CE),
    error = LabError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val OceanColorScheme =
    labLightScheme(
        primary = OceanPrimary,
        secondary = OceanSecondary,
        tertiary = OceanTertiary,
        background = Color(0xFFF3F9FD),
        primaryContainer = Color(0xFFC9EAFF),
        secondaryContainer = Color(0xFF9CF2DB),
        tertiaryContainer = Color(0xFFFFD7EB)
    )

private val AuroraColorScheme =
    labLightScheme(
        primary = AuroraPrimary,
        secondary = AuroraSecondary,
        tertiary = AuroraTertiary,
        background = Color(0xFFFAF7FF),
        primaryContainer = Color(0xFFEADDFF),
        secondaryContainer = Color(0xFF9CF2ED),
        tertiaryContainer = Color(0xFFFFD9E3)
    )

private val SolarColorScheme =
    labLightScheme(
        primary = SolarPrimary,
        secondary = SolarSecondary,
        tertiary = SolarTertiary,
        background = Color(0xFFFFF9F1),
        primaryContainer = Color(0xFFFFDDB6),
        secondaryContainer = Color(0xFFD8EC91),
        tertiaryContainer = Color(0xFFFFDBCF)
    )

internal val ForestColorScheme =
    labLightScheme(
        primary = ForestPrimary,
        secondary = ForestSecondary,
        tertiary = ForestTertiary,
        background = Color(0xFFF4FAF6),
        primaryContainer = Color(0xFFB8F1D8),
        secondaryContainer = Color(0xFFD6ED9A),
        tertiaryContainer = Color(0xFFE9DDFF)
    )

private val LagoonColorScheme =
    labLightScheme(
        primary = LagoonPrimary,
        secondary = LagoonSecondary,
        tertiary = LagoonTertiary,
        background = Color(0xFFF2FAFA),
        primaryContainer = Color(0xFF9CF2ED),
        secondaryContainer = Color(0xFFD9E2FF),
        tertiaryContainer = Color(0xFFFFD8E7)
    )

private val EmberColorScheme =
    labLightScheme(
        primary = EmberPrimary,
        secondary = EmberSecondary,
        tertiary = EmberTertiary,
        background = Color(0xFFFFF8F4),
        primaryContainer = Color(0xFFFFDBCC),
        secondaryContainer = Color(0xFFF4E18A),
        tertiaryContainer = Color(0xFFFFD8EA)
    )

internal val NeonCircuitColorScheme =
    darkColorScheme(
        primary = NeonCircuitPrimary,
        onPrimary = Color(0xFF001F24),
        primaryContainer = Color(0xFF004D5C),
        onPrimaryContainer = Color(0xFFB6EFFF),
        secondary = NeonCircuitSecondary,
        onSecondary = Color(0xFF00210B),
        secondaryContainer = Color(0xFF0B4F2B),
        onSecondaryContainer = Color(0xFFA7F5BF),
        tertiary = NeonCircuitTertiary,
        onTertiary = Color(0xFF3B0030),
        tertiaryContainer = Color(0xFF641452),
        onTertiaryContainer = Color(0xFFFFD7F1),
        background = NeonCircuitBackground,
        onBackground = Color(0xFFE6EAF2),
        surface = NeonCircuitSurface,
        onSurface = Color(0xFFE6EAF2),
        surfaceVariant = NeonCircuitSurfaceVariant,
        onSurfaceVariant = Color(0xFFC2C9D6),
        outline = Color(0xFF8B94A5),
        outlineVariant = Color(0xFF3A4251),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )

private val HighContrastLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF002B5C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color.Black,
        secondary = Color(0xFF004B3C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC1F1DF),
        onSecondaryContainer = Color.Black,
        tertiary = Color(0xFF6C163F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E5),
        onTertiaryContainer = Color.Black,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE3E3E3),
        onSurfaceVariant = Color(0xFF202020),
        outline = Color.Black,
        outlineVariant = Color(0xFF5E5E5E),
        error = Color(0xFF8C0009),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color.Black
    )

internal fun appThemePreviewColors(palette: AppVisualPalette): List<Color> =
    when (palette) {
        AppVisualPalette.OCEAN -> listOf(OceanPrimary, OceanSecondary, OceanTertiary, Color(0xFFC9EAFF))
        AppVisualPalette.AURORA -> listOf(AuroraPrimary, AuroraSecondary, AuroraTertiary, Color(0xFFEADDFF))
        AppVisualPalette.SOLAR -> listOf(SolarPrimary, SolarSecondary, SolarTertiary, Color(0xFFFFDDB6))
        AppVisualPalette.FOREST -> listOf(ForestPrimary, ForestSecondary, ForestTertiary, Color(0xFFB8F1D8))
        AppVisualPalette.LAGOON -> listOf(LagoonPrimary, LagoonSecondary, LagoonTertiary, Color(0xFF9CF2ED))
        AppVisualPalette.EMBER -> listOf(EmberPrimary, EmberSecondary, EmberTertiary, Color(0xFFFFDBCC))
        AppVisualPalette.NEON_CIRCUIT ->
            listOf(
                NeonCircuitPrimary,
                NeonCircuitSecondary,
                NeonCircuitTertiary,
                NeonCircuitSurfaceVariant
            )
    }

private fun staticColorSchemeFor(palette: AppVisualPalette) =
    when (palette) {
        AppVisualPalette.OCEAN -> OceanColorScheme
        AppVisualPalette.AURORA -> AuroraColorScheme
        AppVisualPalette.SOLAR -> SolarColorScheme
        AppVisualPalette.FOREST -> ForestColorScheme
        AppVisualPalette.LAGOON -> LagoonColorScheme
        AppVisualPalette.EMBER -> EmberColorScheme
        AppVisualPalette.NEON_CIRCUIT -> NeonCircuitColorScheme
    }

internal enum class AppColorSchemeSource {
    HIGH_CONTRAST,
    TEMPORARY_PALETTE,
    DEVICE_DYNAMIC,
    STORED_PALETTE
}

internal fun resolveAppColorSchemeSource(
    highContrast: Boolean,
    temporaryPaletteOverride: AppVisualPalette?,
    useDeviceDynamicColors: Boolean,
    supportsDynamicColors: Boolean
): AppColorSchemeSource =
    when {
        highContrast -> AppColorSchemeSource.HIGH_CONTRAST
        temporaryPaletteOverride != null -> AppColorSchemeSource.TEMPORARY_PALETTE
        useDeviceDynamicColors && supportsDynamicColors -> AppColorSchemeSource.DEVICE_DYNAMIC
        else -> AppColorSchemeSource.STORED_PALETTE
    }

@Composable
fun RobotKinematicsLabTheme(
    preferences: AppVisualThemePreferences = AppVisualThemePreferences(),
    accessibilityPreferences: AppAccessibilityPreferences = AppAccessibilityPreferences(),
    temporaryPaletteOverride: AppVisualPalette? = null,
    content: @Composable () -> Unit
) {
    val colorSchemeSource =
        resolveAppColorSchemeSource(
            highContrast = accessibilityPreferences.highContrast,
            temporaryPaletteOverride = temporaryPaletteOverride,
            useDeviceDynamicColors = preferences.useDeviceDynamicColors,
            supportsDynamicColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
    val colorScheme =
        when (colorSchemeSource) {
            AppColorSchemeSource.HIGH_CONTRAST -> HighContrastLightColorScheme
            AppColorSchemeSource.TEMPORARY_PALETTE ->
                staticColorSchemeFor(checkNotNull(temporaryPaletteOverride))
            AppColorSchemeSource.DEVICE_DYNAMIC ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(LocalContext.current)
                } else {
                    // Defensive fallback: the resolver already excludes this branch below S, but
                    // keeping the platform guard beside the API call also makes that contract
                    // explicit to lint and resilient to future resolver changes.
                    staticColorSchemeFor(preferences.palette)
                }
            AppColorSchemeSource.STORED_PALETTE -> staticColorSchemeFor(preferences.palette)
        }

    val systemDensity = LocalDensity.current
    val scaledDensity =
        remember(systemDensity, accessibilityPreferences.contentScale) {
            Density(
                density = systemDensity.density * accessibilityPreferences.contentScale.multiplier,
                fontScale = systemDensity.fontScale
            )
        }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(accessibilityPreferences.boldText),
            shapes = LabShapes,
            content = content
        )
    }
}
