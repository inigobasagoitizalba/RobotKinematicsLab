package com.robotkinematicslab.mobile.ui.charts.advanced

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.buildHistogramBins
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.computeHistogramStats
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.computeVisibleHistogramWindow
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.LineChartVisibleWindow
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.computeLineChartStats
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.computeVisibleLineChartWindow
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ScatterVisibleWindow
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.computeScatterStats
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.computeVisibleScatterWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedChartInputSafetyTest {

    @Test
    fun canvasScaleIsFiniteAndBoundedBeforeGeometryUsesIt() {
        assertEquals(1f, safeChartScale(Float.NaN, 24f), 0f)
        assertEquals(1f, safeChartScale(Float.POSITIVE_INFINITY, 24f), 0f)
        assertEquals(1f, safeChartScale(0f, 24f), 0f)
        assertEquals(4f, safeChartScale(4f, 24f), 0f)
        assertEquals(24f, safeChartScale(Float.MAX_VALUE, 24f), 0f)
        assertEquals(1f, safeChartScale(4f, Float.NaN), 0f)
    }

    @Test
    fun histogramBuildersKeepFiniteEvidenceWithInvalidBinRequest() {
        val values = listOf(Double.NaN, Double.NEGATIVE_INFINITY, 0.0, 10.0)

        val professionalBins = buildProfessionalHistogramBins(values, binCount = 0)
        assertEquals(1, professionalBins.size)
        assertEquals(2, professionalBins.single().count)
        assertTrue(professionalBins.single().start.isFinite())
        assertTrue(professionalBins.single().end.isFinite())

        val bins = buildHistogramBins(values, requestedBinCount = 0)
        assertEquals(1, bins.size)
        assertEquals(2, bins.single().count)
        val stats = requireNotNull(computeHistogramStats(values, bins))
        assertEquals(2, stats.sampleCount)
        assertTrue(stats.mean.isFinite())
    }

    @Test
    fun nonFinitePercentileAndReversedRangeHaveDeterministicSafeFallbacks() {
        assertEquals(5.0, percentile(listOf(0.0, 10.0), Double.NaN), 0.0)
        assertEquals(1.0, safeRange(10.0, 0.0), 0.0)
        assertEquals(1.0, safeRange(Double.NaN, 10.0), 0.0)
    }

    @Test
    fun statisticsDiscardNonFiniteSamplesBeforePublishingCounts() {
        val scatter =
            requireNotNull(
                computeScatterStats(
                    listOf(
                        ChartPoint(Double.NaN, 2.0, Color.Red, "invalid"),
                        ChartPoint(3.0, 4.0, Color.Blue, "valid")
                    )
                )
            )

        assertEquals(1, scatter.pointCount)
        assertEquals(3.0, scatter.observedMinX, 0.0)
        assertEquals(4.0, scatter.observedMinY, 0.0)
    }

    @Test
    fun invalidVisibleWindowGeometryFallsBackToFiniteFullBounds() {
        val lineStats = requireNotNull(computeLineChartStats(listOf(ChartLinePoint(2.0, 3.0))))
        assertEquals(
            LineChartVisibleWindow(lineStats.minX, lineStats.maxX, lineStats.minY, lineStats.maxY),
            computeVisibleLineChartWindow(lineStats, 0f, 0f, 0f, 0f, Float.NaN, Float.NaN)
        )

        val scatterStats = requireNotNull(computeScatterStats(listOf(ChartPoint(2.0, 3.0, Color.Blue))))
        assertEquals(
            ScatterVisibleWindow(scatterStats.minX, scatterStats.maxX, scatterStats.minY, scatterStats.maxY),
            computeVisibleScatterWindow(scatterStats, Float.NaN, 100f, 100f, 100f, 0f, 0f)
        )

        val histogramWindow =
            computeVisibleHistogramWindow(
                bins = buildHistogramBins(listOf(1.0, 2.0), 2),
                chartWidth = Float.POSITIVE_INFINITY,
                zoomedWidth = Float.POSITIVE_INFINITY,
                offsetX = 0f,
                maxY = 0
            )
        assertTrue(histogramWindow.minX.isFinite())
        assertTrue(histogramWindow.maxX.isFinite())
        assertEquals(1, histogramWindow.maxY)
    }
}
