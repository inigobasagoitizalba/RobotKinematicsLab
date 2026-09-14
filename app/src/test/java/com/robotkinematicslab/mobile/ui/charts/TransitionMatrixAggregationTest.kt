package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTransitionAggregate
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionMatrixAggregationTest {

    @Test
    fun heatMaps_areAViewFilterAndNotADuplicateAnalysisCategory() {
        assertEquals(false, DiagnosticChartCategory.HEAT_MAPS in diagnosticAnalysisCategories)
        assertEquals(true, DiagnosticChartCategory.TRANSITIONS.supportsMatrixExplorer())
        assertEquals(false, DiagnosticChartCategory.CORRELATIONS.supportsMatrixExplorer())
    }

    @Test
    fun compactCaseId_removesOnlyRecognizedScenarioPrefix() {
        assertEquals("R3", compactDiagnosticCaseId("S101-10L-MIXED-R3"))
        assertEquals("U2", compactDiagnosticCaseId("S42-3L-AUTO-U2"))
        assertEquals("R1", compactDiagnosticCaseId("R1"))
        assertEquals("custom-case", compactDiagnosticCaseId("custom-case"))
        assertEquals("START", compactDiagnosticCaseId(null))
    }

    @Test
    fun compactTransitions_usesRunCountWeightedMetricsAndExactCounts() {
        val first = transition("S101-10L-MIXED-R1", "S101-10L-MIXED-R2", 10, 8, 1.0)
        val second = transition("S202-3L-AUTO-R1", "S202-3L-AUTO-R2", 30, 15, 3.0)

        val compact = compactTransitionAggregates(listOf(first, second))

        assertEquals(1, compact.size)
        assertEquals("R1", compact.single().fromCaseId)
        assertEquals("R2", compact.single().toCaseId)
        assertEquals(40, compact.single().runCount)
        assertEquals(23, compact.single().acceptedCount)
        assertEquals(17, compact.single().rejectedCount)
        assertEquals(2.5, compact.single().averageFinalError, 1e-12)
        assertEquals(5.0, compact.single().maxFinalError, 1e-12)
    }

    private fun transition(
        from: String,
        to: String,
        runs: Int,
        accepted: Int,
        averageError: Double
    ): DiagnosticTransitionAggregate {
        return DiagnosticTransitionAggregate(
            fromCaseId = from,
            toCaseId = to,
            runCount = runs,
            acceptedCount = accepted,
            rejectedCount = runs - accepted,
            nearSolvedCount = 0,
            closeMissCount = 0,
            farFailureCount = runs - accepted,
            averageFinalError = averageError,
            maxFinalError = averageError + 2.0,
            averageInitialError = averageError + 1.0,
            averageImprovementRatio = 0.5,
            averageIterations = averageError * 10.0,
            mostCommonStatus = "TEST"
        )
    }
}
