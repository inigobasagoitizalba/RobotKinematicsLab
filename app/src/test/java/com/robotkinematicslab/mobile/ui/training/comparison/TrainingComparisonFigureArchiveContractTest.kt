package com.robotkinematicslab.mobile.ui.training.comparison

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.StoredTrainingIteration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingComparisonFigureArchiveContractTest {

    @Test
    fun completeHistoryArchive_containsEveryMetricForBothComparedModels() {
        val series =
            buildCompleteTrainingHistorySeries(
                history = listOf(iteration(2, "left", 0.8), iteration(1, "left", 0.7), iteration(1, "right", 0.9)),
                selections = listOf("left" to "108 variables", "right" to "383 variables")
            )

        assertEquals(HistoryMetric.entries.size * 2, series.size)
        assertEquals(HistoryMetric.entries.toSet(), series.map { it.metric }.toSet())
        assertEquals(setOf("left", "right"), series.map { it.selectionId }.toSet())
        assertTrue(series.all { it.selectionLabel.isNotBlank() })
        assertEquals(
            listOf(1.0, 2.0),
            series.first { it.selectionId == "left" && it.metric == HistoryMetric.MACRO_F1 }.points.map { it.x }
        )
    }

    @Test
    fun corruptNonFiniteMetric_isNotAllowedIntoAnArchivedSeries() {
        val corrupt = iteration(1, "left", Double.NaN)
        val series =
            buildCompleteTrainingHistorySeries(
                history = listOf(corrupt),
                selections = listOf("left" to "Left")
            )

        assertTrue(series.first { it.metric == HistoryMetric.MACRO_F1 }.points.isEmpty())
        assertFalse(series.first { it.metric == HistoryMetric.LOSS }.points.isEmpty())
    }

    private fun iteration(index: Int, selectionId: String, macroF1: Double) =
        StoredTrainingIteration(
            globalIteration = index,
            profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
            candidateId = "candidate",
            modelKind = TrainingModelKind.LINEAR_SOFTMAX,
            hiddenUnits = 0,
            epoch = index,
            trainingLoss = 0.5,
            validationAccuracy = 0.8,
            validationBalancedAccuracy = 0.75,
            validationMacroF1 = macroF1,
            validationLogLoss = 0.4,
            elapsedMillis = index * 10L,
            featureSelectionId = selectionId,
            featureSelectionName = selectionId
        )
}
