package com.robotkinematicslab.mobile.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFigureArchivePlanTest {
    @Test
    fun `archive covers selected and unselected analysis families`() {
        val plannedCategories =
            diagnosticFigureArchiveBatches
                .filterIsInstance<DiagnosticFigureArchiveBatch.AnalysisFamily>()
                .map { it.category }
                .toSet()

        assertTrue(diagnosticArchiveContainsEveryAnalysisFamily())
        diagnosticAnalysisCategories.forEach { selectedByUser ->
            assertTrue(plannedCategories.contains(selectedByUser))
            assertTrue(
                plannedCategories.containsAll(
                    diagnosticAnalysisCategories.filterNot { it == selectedByUser }
                )
            )
        }
    }

    @Test
    fun `archive batches are stable unique and bounded`() {
        val ids = diagnosticFigureArchiveBatches.map { it.stableId }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(
            diagnosticAnalysisCategories.size + 1,
            diagnosticFigureArchiveBatches.size
        )
        assertEquals(
            1,
            diagnosticFigureArchiveBatches.count {
                it is DiagnosticFigureArchiveBatch.UniqueTwoDimensionalMatrices
            }
        )
    }

    @Test
    fun `canonical matrix inventory has no duplicates and excludes interactive 3d`() {
        val ids = diagnosticTwoDimensionalMatrixFigures.map { it.stableId }

        assertEquals(13, diagnosticTwoDimensionalMatrixFigures.size)
        assertEquals(ids.size, ids.distinct().size)
        assertFalse(diagnosticArchiveContainsInteractive3D())
        assertFalse(ids.any { it.contains("3d", ignoreCase = true) })
        assertFalse(ids.any { it.contains("workspace", ignoreCase = true) })
    }

    @Test
    fun `heat map selector alias is not archived as a duplicate analysis family`() {
        val plannedCategories =
            diagnosticFigureArchiveBatches
                .filterIsInstance<DiagnosticFigureArchiveBatch.AnalysisFamily>()
                .map { it.category }

        assertFalse(plannedCategories.contains(DiagnosticChartCategory.HEAT_MAPS))
        assertEquals(plannedCategories.size, plannedCategories.distinct().size)
    }
}
