package com.robotkinematicslab.mobile.ui.charts.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ChartPresentationPreset(
    val persistedId: String,
    val displayName: String,
    val description: String
) {
    EXPLORATION(
        persistedId = "exploration",
        displayName = "Interactive exploration",
        description = "Keeps guides, gridlines and supporting statistics visible while results are inspected."
    ),
    PUBLICATION(
        persistedId = "publication",
        displayName = "Publication figure",
        description = "Uses a restrained white figure, labelled axes and less non-data decoration for dissertation-ready captures."
    ),
    CUSTOM(
        persistedId = "custom",
        displayName = "Custom",
        description = "Uses the exact visual layers selected below."
    )
}

@Immutable
data class ChartPresentationPreferences(
    val preset: ChartPresentationPreset = ChartPresentationPreset.EXPLORATION,
    val autoSaveFigures: Boolean = true,
    val showGrid: Boolean = true,
    val showLines: Boolean = true,
    val showMarkers: Boolean = true,
    val showValueLabels: Boolean = false,
    val showLegend: Boolean = true,
    val showStatistics: Boolean = true,
    val showGuidance: Boolean = true
) {
    fun normalized(): ChartPresentationPreferences =
        if (showLines || showMarkers) {
            this
        } else {
            copy(showMarkers = true)
        }

    fun withPreset(selected: ChartPresentationPreset): ChartPresentationPreferences =
        when (selected) {
            ChartPresentationPreset.EXPLORATION -> explorationDefaults().copy(autoSaveFigures = autoSaveFigures)
            ChartPresentationPreset.PUBLICATION -> publicationDefaults().copy(autoSaveFigures = autoSaveFigures)
            ChartPresentationPreset.CUSTOM -> copy(preset = ChartPresentationPreset.CUSTOM).normalized()
        }

    fun customized(
        showGrid: Boolean = this.showGrid,
        showLines: Boolean = this.showLines,
        showMarkers: Boolean = this.showMarkers,
        showValueLabels: Boolean = this.showValueLabels,
        showLegend: Boolean = this.showLegend,
        showStatistics: Boolean = this.showStatistics,
        showGuidance: Boolean = this.showGuidance
    ): ChartPresentationPreferences =
        copy(
            preset = ChartPresentationPreset.CUSTOM,
            showGrid = showGrid,
            showLines = showLines,
            showMarkers = showMarkers,
            showValueLabels = showValueLabels,
            showLegend = showLegend,
            showStatistics = showStatistics,
            showGuidance = showGuidance
        ).normalized()

    companion object {
        fun explorationDefaults(): ChartPresentationPreferences = ChartPresentationPreferences()

        fun publicationDefaults(): ChartPresentationPreferences =
            ChartPresentationPreferences(
                preset = ChartPresentationPreset.PUBLICATION,
                showGrid = false,
                showLines = true,
                showMarkers = true,
                showValueLabels = false,
                showLegend = true,
                showStatistics = false,
                showGuidance = false
            )
    }
}

internal object ChartPresentationPreferenceCodec {
    fun decode(
        presetId: String?,
        autoSaveFigures: Boolean,
        showGrid: Boolean,
        showLines: Boolean,
        showMarkers: Boolean,
        showValueLabels: Boolean,
        showLegend: Boolean,
        showStatistics: Boolean,
        showGuidance: Boolean
    ): ChartPresentationPreferences =
        ChartPresentationPreferences(
            preset = ChartPresentationPreset.entries.firstOrNull { it.persistedId == presetId }
                ?: ChartPresentationPreset.EXPLORATION,
            autoSaveFigures = autoSaveFigures,
            showGrid = showGrid,
            showLines = showLines,
            showMarkers = showMarkers,
            showValueLabels = showValueLabels,
            showLegend = showLegend,
            showStatistics = showStatistics,
            showGuidance = showGuidance
        ).normalized()
}

class ChartPresentationRepository(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    fun load(): ChartPresentationPreferences =
        ChartPresentationPreferenceCodec.decode(
            presetId = preferences.getString(KEY_PRESET, null),
            autoSaveFigures = preferences.getBoolean(KEY_AUTO_SAVE_FIGURES, true),
            showGrid = preferences.getBoolean(KEY_GRID, true),
            showLines = preferences.getBoolean(KEY_LINES, true),
            showMarkers = preferences.getBoolean(KEY_MARKERS, true),
            showValueLabels = preferences.getBoolean(KEY_VALUE_LABELS, false),
            showLegend = preferences.getBoolean(KEY_LEGEND, true),
            showStatistics = preferences.getBoolean(KEY_STATISTICS, true),
            showGuidance = preferences.getBoolean(KEY_GUIDANCE, true)
        )

    fun save(value: ChartPresentationPreferences): ChartPresentationPreferences {
        val safeValue = value.normalized()
        preferences.edit()
            .putString(KEY_PRESET, safeValue.preset.persistedId)
            .putBoolean(KEY_AUTO_SAVE_FIGURES, safeValue.autoSaveFigures)
            .putBoolean(KEY_GRID, safeValue.showGrid)
            .putBoolean(KEY_LINES, safeValue.showLines)
            .putBoolean(KEY_MARKERS, safeValue.showMarkers)
            .putBoolean(KEY_VALUE_LABELS, safeValue.showValueLabels)
            .putBoolean(KEY_LEGEND, safeValue.showLegend)
            .putBoolean(KEY_STATISTICS, safeValue.showStatistics)
            .putBoolean(KEY_GUIDANCE, safeValue.showGuidance)
            .apply()
        return safeValue
    }

    private companion object {
        const val PREFERENCES_FILE = "chart-presentation-v1"
        const val KEY_PRESET = "preset"
        const val KEY_AUTO_SAVE_FIGURES = "auto-save-figures"
        const val KEY_GRID = "grid"
        const val KEY_LINES = "lines"
        const val KEY_MARKERS = "markers"
        const val KEY_VALUE_LABELS = "value-labels"
        const val KEY_LEGEND = "legend"
        const val KEY_STATISTICS = "statistics"
        const val KEY_GUIDANCE = "guidance"
    }
}

@Immutable
data class ChartPresentationController(
    val preferences: ChartPresentationPreferences,
    val update: (ChartPresentationPreferences) -> Unit
)

val LocalChartPresentationController =
    staticCompositionLocalOf {
        ChartPresentationController(
            preferences = ChartPresentationPreferences(),
            update = {}
        )
    }

@Composable
fun ProvideChartPresentation(
    controller: ChartPresentationController,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalChartPresentationController provides controller,
        content = content
    )
}
