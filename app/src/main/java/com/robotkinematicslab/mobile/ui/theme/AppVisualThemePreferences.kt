package com.robotkinematicslab.mobile.ui.theme

import android.content.Context

enum class AppVisualPalette(
    val persistedId: String,
    val displayName: String,
    val description: String
) {
    OCEAN("ocean", "Ocean Lab", "Cobalt, cyan and teal. Clear and technical."),
    AURORA("aurora", "Aurora", "Violet, turquoise and magenta. More expressive."),
    SOLAR("solar", "Solar Lab", "Amber, lime and warm red. High visual energy."),
    FOREST("forest", "Forest", "Emerald and moss. Comfortable for long sessions."),
    LAGOON("lagoon", "Lagoon", "Deep teal, marine blue and berry. Calm but vivid."),
    EMBER("ember", "Ember", "Terracotta, ochre and plum. Warm and grounded."),
    NEON_CIRCUIT(
        "neon-circuit",
        "Neon Circuit",
        "Dark graphite with focused cyan, green and magenta signal accents."
    )
}

data class AppVisualThemePreferences(
    val palette: AppVisualPalette = AppVisualPalette.OCEAN,
    val useDeviceDynamicColors: Boolean = false
)

internal object AppVisualThemePreferenceCodec {
    fun decode(
        paletteId: String?,
        useDeviceDynamicColors: Boolean
    ): AppVisualThemePreferences =
        AppVisualThemePreferences(
            palette = AppVisualPalette.OCEAN,
            useDeviceDynamicColors = false
        )
}

class AppVisualThemeRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun load(): AppVisualThemePreferences =
        AppVisualThemePreferenceCodec.decode(
            paletteId = preferences.getString(KEY_PALETTE, null),
            useDeviceDynamicColors = preferences.getBoolean(KEY_DYNAMIC_COLORS, false)
        )

    fun save(value: AppVisualThemePreferences): AppVisualThemePreferences {
        val universityProfile = AppVisualThemePreferences()
        preferences.edit()
            .putString(KEY_PALETTE, universityProfile.palette.persistedId)
            .putBoolean(KEY_DYNAMIC_COLORS, universityProfile.useDeviceDynamicColors)
            .apply()
        return universityProfile
    }

    private companion object {
        const val PREFERENCES_FILE = "visual-theme-v1"
        const val KEY_PALETTE = "palette"
        const val KEY_DYNAMIC_COLORS = "dynamic-colors"
    }
}
