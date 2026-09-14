package com.robotkinematicslab.mobile.ui.charts.advanced

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.findBoxPlotSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.prepareBoxPlotItems
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.findHeatMapSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.buildHistogramBins
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.findHistogramSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.findHorizontalBarSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.prepareHorizontalBarItems
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.computeLineChartStats
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.findLineChartSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.computeScatterStats
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.findScatterSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts.findTimelineSelection
import com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts.prepareTimelineCells
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChartInspectorHitTestingTest {

    @Test
    fun zeroSizedViewportsCannotInventSelections() {
        val scatterPoints = listOf(ChartPoint(1.0, 1.0, Color.Blue, "only"))
        val scatterStats = requireNotNull(computeScatterStats(scatterPoints))
        assertNull(
            findScatterSelection(
                scatterPoints, scatterStats, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0f, 2f
            )
        )

        val linePoints = listOf(ChartLinePoint(1.0, 1.0))
        val lineStats = requireNotNull(computeLineChartStats(linePoints))
        assertNull(
            findLineChartSelection(
                linePoints, lineStats, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, 2f
            )
        )

        val bins = buildHistogramBins(listOf(1.0), 1)
        assertNull(findHistogramSelection(bins, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f))

        val bars = prepareHorizontalBarItems(listOf(ChartBarItem("only", 1, Color.Blue)))
        assertNull(findHorizontalBarSelection(bars, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f))

        val boxes = prepareBoxPlotItems(listOf(ChartBoxPlotItem("only", listOf(1.0), Color.Blue)))
        assertNull(findBoxPlotSelection(boxes, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f))

        val timeline = prepareTimelineCells(listOf(ChartTimelineCell("only", Color.Blue)))
        assertNull(findTimelineSelection(timeline, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f))

        assertNull(
            findHeatMapSelection(
                listOf("row"), listOf("column"), listOf(ChartHeatMapCell("row", "column", 1.0, "one", Color.Blue)),
                0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f
            )
        )
    }

    @Test
    fun singleLinePointRemainsVisibleAndSelectableAtThePlotCentre() {
        val points = listOf(ChartLinePoint(7.0, 11.0))
        val stats = requireNotNull(computeLineChartStats(points))

        assertEquals(1, stats.pointCount)
        assertEquals(0.5f, com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.lineChartNormalizedPosition(7.0, 7.0, 7.0), 0f)
        assertEquals(
            0,
            findLineChartSelection(
                points, stats, 50f, 50f, 100f, 100f, 1f, 0f, 0f,
                10f, 10f, 10f, 10f, 0f, 1f
            )?.index
        )
    }

    @Test
    fun nonFiniteOrOutsideInteractionsAreRejected() {
        val points = listOf(ChartPoint(0.0, 0.0, Color.Blue, "point"))
        val stats = requireNotNull(computeScatterStats(points))

        assertNull(
            findScatterSelection(
                points, stats, Float.NaN, 50f, 100f, 100f, 1f, 0f, 0f,
                10f, 10f, 10f, 10f, 12f
            )
        )
        assertNull(
            findScatterSelection(
                points, stats, 50f, 50f, 100f, 100f, Float.MAX_VALUE, 0f, 0f,
                10f, 10f, 10f, 10f, 12f
            )
        )
        assertNull(
            findScatterSelection(
                points, stats, -1f, -1f, 100f, 100f, 1f, 0f, 0f,
                10f, 10f, 10f, 10f, 12f
            )
        )
    }

    @Test
    fun heatMapTap_resolvesVisibleCell() {
        val cells =
            listOf(
                ChartHeatMapCell("A", "X", 1.0, "one", Color.Green),
                ChartHeatMapCell("A", "Y", 2.0, "two", Color.Red)
            )

        val selected =
            findHeatMapSelection(
                rowLabels = listOf("A"),
                columnLabels = listOf("X", "Y"),
                cells = cells,
                tapX = 310f,
                tapY = 120f,
                viewportWidth = 400f,
                viewportHeight = 300f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                leftHeaderWidth = 70f,
                topHeaderHeight = 50f,
                rightPadding = 10f,
                bottomPadding = 10f
            )

        assertEquals("Y", selected?.column)
        assertEquals("two", selected?.cell?.displayValue)
    }

    @Test
    fun scatterAndLineTaps_pickNearestRenderedPoint() {
        val scatter =
            listOf(
                ChartPoint(0.0, 0.0, Color.Blue, "low"),
                ChartPoint(10.0, 10.0, Color.Red, "high")
            )
        val scatterStats = requireNotNull(computeScatterStats(scatter))
        val scatterSelection =
            findScatterSelection(
                scatter,
                scatterStats,
                tapX = 362f,
                tapY = 33f,
                viewportWidth = 400f,
                viewportHeight = 300f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                leftPadding = 40f,
                rightPadding = 20f,
                topPadding = 20f,
                bottomPadding = 40f,
                maximumDistance = 12f
            )
        assertEquals("high", scatterSelection?.point?.label)

        val line = listOf(ChartLinePoint(0.0, 0.0), ChartLinePoint(10.0, 10.0))
        val lineStats = requireNotNull(computeLineChartStats(line))
        val lineSelection =
            findLineChartSelection(
                line,
                lineStats,
                tapX = 370f,
                tapY = 30f,
                viewportWidth = 400f,
                viewportHeight = 300f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                leftPadding = 40f,
                rightPadding = 20f,
                topPadding = 20f,
                bottomPadding = 40f,
                plotInset = 10f,
                maximumDistance = 12f
            )
        assertEquals(1, lineSelection?.index)
    }

    @Test
    fun barsBinsBoxesAndTimeline_resolveSecondItem() {
        val bins = buildHistogramBins(listOf(0.0, 0.2, 0.8, 1.0), 2)
        assertEquals(
            1,
            findHistogramSelection(
                bins, 300f, 120f, 400f, 300f, 1f, 0f,
                40f, 20f, 20f, 40f
            )?.index
        )

        val bars =
            prepareHorizontalBarItems(
                listOf(ChartBarItem("A", 20, Color.Blue), ChartBarItem("B", 10, Color.Red))
            )
        assertEquals(
            1,
            findHorizontalBarSelection(
                bars, 200f, 200f, 400f, 300f, 1f, 0f,
                80f, 20f, 20f, 40f, 40f
            )?.index
        )

        val boxes =
            prepareBoxPlotItems(
                listOf(
                    ChartBoxPlotItem("A", listOf(1.0, 2.0), Color.Blue),
                    ChartBoxPlotItem("B", listOf(3.0, 4.0), Color.Red)
                )
            )
        assertEquals(
            1,
            findBoxPlotSelection(
                boxes, 200f, 200f, 400f, 300f, 1f, 0f,
                80f, 20f, 20f, 40f, 40f
            )?.index
        )

        val timeline =
            prepareTimelineCells(
                listOf(
                    ChartTimelineCell("accepted", Color.Green),
                    ChartTimelineCell("rejected", Color.Red)
                )
            )
        val selectedTimeline =
            findTimelineSelection(
                timeline, 300f, 120f, 400f, 300f, 1f, 0f,
                40f, 20f, 20f, 40f
            )
        assertNotNull(selectedTimeline)
        assertEquals("rejected", selectedTimeline?.cell?.label)
    }
}
