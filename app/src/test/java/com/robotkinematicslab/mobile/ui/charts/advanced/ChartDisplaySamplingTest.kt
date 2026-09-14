package com.robotkinematicslab.mobile.ui.charts.advanced

import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.cleanLineChartPoints
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.cleanScatterPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDisplaySamplingTest {

    @Test
    fun smallInput_isReturnedUnchanged() {
        val values = listOf("a", "b", "c")

        assertSame(values, sampleForResponsiveChartDisplay(values, maximumSize = 5))
    }

    @Test
    fun compactPreview_preservesIntermediateSpikeInsteadOfFlatteningIt() {
        val spikeIndex = 10_003
        val values =
            (0 until 20_000).map { index ->
                ChartLinePoint(
                    x = index.toDouble(),
                    y = if (index == spikeIndex) 1_000_000.0 else index.toDouble()
                )
            }

        val preview =
            selectPeakPreservingChartPreview(
                values = values,
                maximumSize = 1_000,
                valueSelector = ChartLinePoint::y
            )

        assertTrue(preview.size <= 1_000)
        assertEquals(values.first(), preview.first())
        assertEquals(values.last(), preview.last())
        assertEquals(values[spikeIndex], preview.single { it.y == 1_000_000.0 })
    }

    @Test
    fun lineAndScatterModelPreparation_preservesTheCompleteInspectorSource() {
        val linePoints =
            (0 until 8_001).map { index ->
                ChartLinePoint(index.toDouble(), if (index == 4_003) -77.0 else index.toDouble())
            }
        val scatterPoints =
            (0 until 8_001).map { index ->
                ChartPoint(
                    x = index.toDouble(),
                    y = if (index == 4_003) -77.0 else index.toDouble(),
                    color = androidx.compose.ui.graphics.Color.Blue,
                    label = "point-$index"
                )
            }

        val preparedLine = cleanLineChartPoints(linePoints)
        val preparedScatter = cleanScatterPoints(scatterPoints)

        assertEquals(linePoints, preparedLine)
        assertEquals(scatterPoints, preparedScatter)
        assertEquals(-77.0, preparedLine.single { it.x == 4_003.0 }.y, 0.0)
        assertEquals("point-4003", preparedScatter.single { it.x == 4_003.0 }.label)
    }
}
