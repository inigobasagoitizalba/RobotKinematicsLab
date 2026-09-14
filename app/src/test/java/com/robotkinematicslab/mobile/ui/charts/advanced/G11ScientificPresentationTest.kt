package com.robotkinematicslab.mobile.ui.charts.advanced

import com.robotkinematicslab.mobile.ui.charts.presentation.*
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.*
import com.robotkinematicslab.mobile.ui.charts.guidance.*
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class G11ScientificPresentationTest {
    @Test fun percentagesAndDifferencesHaveOneExplicitConversion() {
        assertEquals("94.85%", ScientificMetricFormat.RATIO.format(0.9485))
        assertEquals("94.85%", ScientificMetricFormat.BASIS_POINTS.format(9485.0))
        assertEquals("108", ScientificMetricFormat.COUNT.format(108.0))
        assertEquals("Invalid count", ScientificMetricFormat.COUNT.format(108.1))
        assertEquals("Unavailable", ScientificMetricFormat.RATIO.format(Double.NaN))
        val difference = requireNotNull(ratioDifference(0.8456,0.8745))
        assertEquals(0.0289,difference.absolute,1e-12); assertEquals(2.89,difference.percentagePoints,1e-12)
        assertEquals(3.4177,difference.relativePercent!!,1e-4)
        assertNull(ratioDifference(0.0,0.1)!!.relativePercent)
        assertNull(ratioDifference(-1.0,0.1))
        assertEquals("12.85 µs",ScientificMetricFormat.NANOSECONDS.format(12846.2509))
        assertEquals("1.000 µm",ScientificMetricFormat.METRES.format(1e-6))
        assertEquals("1.000 MiB",ScientificMetricFormat.BYTES.format(1048576.0))
    }
    @Test fun labelsNeverOverlapOrLeaveBoundsAndRejectCorruptGeometry() {
        val bounds=ChartLabelRect(0f,0f,320f,180f); val placed=mutableListOf<ChartLabelRect>()
        repeat(83) { index -> placeChartLabel(120f+index%3,90f,80f,14f,bounds,placed)?.let { placed += it } }
        assertTrue(placed.isNotEmpty()); assertTrue(placed.size < 83)
        placed.forEachIndexed { index, rect -> assertTrue(placed.drop(index+1).none(rect::overlaps)); assertTrue(rect.left>=0 && rect.right<=320 && rect.top>=0 && rect.bottom<=180) }
        assertNull(placeChartLabel(Float.NaN,0f,20f,12f,bounds,emptyList()))
        assertNull(placeChartLabel(0f,0f,500f,12f,bounds,emptyList()))
    }
    @Test fun scatterFitPadsDisplayButKeepsOriginalCoordinatesAndEmptyMeaning() {
        val points=listOf(ChartPoint(1000.0,-0.2,Color.Blue,"Baseline"),ChartPoint(11000.0,0.9,Color.Red,"Expanded long name"))
        val stats=requireNotNull(computeScatterStats(points))
        assertTrue(stats.minX < 1000);assertTrue(stats.maxX>11000)
        assertEquals(1000.0,stats.observedMinX,0.0);assertEquals(11000.0,stats.observedMaxX,0.0)
        assertEquals(points,cleanScatterPoints(points));assertNull(computeScatterStats(emptyList()))
        val single=requireNotNull(computeScatterStats(points.take(1)));assertTrue(single.xRange>0);assertTrue(single.yRange>0)
        assertEquals(1,cleanScatterPoints(points.take(1)+ChartPoint(Double.NaN,0.0,Color.Black)).size)
    }
    @Test fun guidesSeparateValidationCalibrationAndTimingLimits() {
        val validation=ChartGuideFactory.forChart(ChartGuideKind.LINE,"Validation macro-F1",xAxisLabel="Epoch")
        assertTrue(validation.howToRead.contains("convergence"));assertTrue(validation.caution.contains("independent-test"))
        val calibration=ChartGuideFactory.forChart(ChartGuideKind.LINE,"Reliability curve")
        assertTrue(calibration.howToRead.contains("y=x"));assertTrue(calibration.caution.contains("physical"))
        val time=ChartGuideFactory.forChart(ChartGuideKind.BAR,"Training time")
        assertTrue(time.caution.contains("Throughput"))
    }
}
