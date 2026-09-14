package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScatterConstantRangeTest {
    @Test
    fun `single point receives symmetric drawable bounds without changing observed values`() {
        val stats = requireNotNull(
            computeScatterStats(listOf(ChartPoint(383.0, 0.5508, Color.Blue, "one model")))
        )

        assertEquals(383.0, stats.observedMinX, 0.0)
        assertEquals(383.0, stats.observedMaxX, 0.0)
        assertEquals(0.5508, stats.observedMinY, 0.0)
        assertEquals(0.5508, stats.observedMaxY, 0.0)
        assertTrue(stats.minX < 383.0 && stats.maxX > 383.0)
        assertTrue(stats.minY < 0.5508 && stats.maxY > 0.5508)
        assertEquals(0.5, (383.0 - stats.minX) / stats.xRange, 1e-12)
        assertEquals(0.5, (0.5508 - stats.minY) / stats.yRange, 1e-12)
    }

    @Test
    fun `non constant evidence retains its exact scientific bounds`() {
        val stats = requireNotNull(
            computeScatterStats(
                listOf(
                    ChartPoint(108.0, 0.49, Color.Blue, "baseline"),
                    ChartPoint(383.0, 0.62, Color.Green, "context")
                )
            )
        )

        assertEquals(108.0, stats.observedMinX, 0.0)
        assertEquals(383.0, stats.observedMaxX, 0.0)
        assertEquals(0.49, stats.observedMinY, 0.0)
        assertEquals(0.62, stats.observedMaxY, 0.0)
        assertTrue(stats.minX < stats.observedMinX && stats.maxX > stats.observedMaxX)
        assertTrue(stats.minY < stats.observedMinY && stats.maxY > stats.observedMaxY)
    }
}
