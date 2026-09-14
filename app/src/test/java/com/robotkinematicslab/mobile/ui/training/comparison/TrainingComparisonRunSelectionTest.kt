package com.robotkinematicslab.mobile.ui.training.comparison

import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingComparisonRunSelectionTest {
    @Test
    fun exactRequestedRunIsSelectedAndAnUnknownIdNeverFallsBackToLatest() {
        val older = summary("run-older")
        val latest = summary("run-latest")
        val runs = listOf(latest, older)

        assertEquals("run-older", selectedRunIdForRequest(runs, "run-older"))
        assertNull(selectedRunIdForRequest(runs, "missing-run"))
        assertEquals("run-latest", selectedRunIdForRequest(runs, null))
        assertNull(selectedRunIdForRequest(emptyList(), null))
    }

    private fun summary(id: String) =
        TrainingRunSummary(
            runId = id,
            runName = id,
            datasetPath = "/datasets/source.csv",
            startedAtEpochMillis = 1L,
            finishedAtEpochMillis = 2L,
            baselineTestMacroF1 = 0.5,
            contextTestMacroF1 = 0.6,
            macroF1Delta = 0.1,
            randomSeed = 42,
            splitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED,
            maximumRows = 100,
            directoryPath = "/runs/$id",
            historyCsvPath = "/runs/$id/history.csv",
            modelPaths = listOf("/models/$id-a.rklm", "/models/$id-b.rklm")
        )
}
