package com.robotkinematicslab.mobile.ui.charts.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPresentationPreferencesTest {

    @Test
    fun publicationPreset_keepsScientificEssentialsAndRemovesDashboardDecoration() {
        val preferences =
            ChartPresentationPreferences.explorationDefaults()
                .withPreset(ChartPresentationPreset.PUBLICATION)

        assertEquals(ChartPresentationPreset.PUBLICATION, preferences.preset)
        assertFalse(preferences.showGrid)
        assertTrue(preferences.showLines)
        assertTrue(preferences.showMarkers)
        assertTrue(preferences.showLegend)
        assertFalse(preferences.showStatistics)
        assertFalse(preferences.showGuidance)
        assertTrue(preferences.autoSaveFigures)
    }

    @Test
    fun customPreset_neverAllowsAnInvisibleLineSeries() {
        val preferences =
            ChartPresentationPreferences.explorationDefaults()
                .customized(showLines = false, showMarkers = false)

        assertEquals(ChartPresentationPreset.CUSTOM, preferences.preset)
        assertFalse(preferences.showLines)
        assertTrue(preferences.showMarkers)
    }

    @Test
    fun codec_recoversUnknownPresetWithoutDroppingLayerChoices() {
        val preferences =
            ChartPresentationPreferenceCodec.decode(
                presetId = "future-preset",
                showGrid = false,
                showLines = true,
                showMarkers = false,
                showValueLabels = true,
                showLegend = false,
                showStatistics = true,
                showGuidance = false,
                autoSaveFigures = true
            )

        assertEquals(ChartPresentationPreset.EXPLORATION, preferences.preset)
        assertFalse(preferences.showGrid)
        assertTrue(preferences.showLines)
        assertFalse(preferences.showMarkers)
        assertTrue(preferences.showValueLabels)
        assertFalse(preferences.showLegend)
        assertTrue(preferences.showStatistics)
        assertFalse(preferences.showGuidance)
        assertTrue(preferences.autoSaveFigures)
    }

    @Test
    fun displayPresetChange_preservesAutomaticLibraryChoice() {
        val preferences =
            ChartPresentationPreferences(autoSaveFigures = false)
                .withPreset(ChartPresentationPreset.PUBLICATION)

        assertFalse(preferences.autoSaveFigures)
    }

    @Test
    fun automaticLibraryClassification_groupsCommonResearchCharts() {
        assertEquals("training", inferAutomaticFigureCollection("Context · normalized confusion matrix"))
        assertEquals("dataset-quality", inferAutomaticFigureCollection("OOD rate by robot"))
        assertEquals("diagnostics", inferAutomaticFigureCollection("Mean solver iterations"))
    }
}
