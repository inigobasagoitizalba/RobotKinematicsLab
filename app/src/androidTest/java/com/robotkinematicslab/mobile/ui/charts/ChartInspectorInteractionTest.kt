package com.robotkinematicslab.mobile.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_PLOT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_RESET_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_SELECTION_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_ZOOM_IN_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_ZOOM_OUT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartTimelineCell
import com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts.ProfessionalBoxPlotChart
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts.ProfessionalHistogramChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts.ProfessionalTimelineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartInspectorInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyProfessionalChart_exposesZoomAndFitControls() {
        var chartType by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                InspectorFixture(chartType)
            }
        }

        repeat(7) { index ->
            composeRule.runOnUiThread { chartType = index }
            composeRule.waitForIdle()
            // Automatic PNG capture temporarily removes the interaction hint from semantics.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching { composeRule.onNodeWithText("Open chart inspector").assertIsDisplayed() }.isSuccess
            }
            composeRule.onNodeWithText("Open chart inspector").performClick()
            try {
                composeRule.onNodeWithTag(CHART_INSPECTOR_RESET_TAG).assertIsDisplayed()
                composeRule.onNodeWithTag(CHART_INSPECTOR_ZOOM_IN_TAG).assertIsDisplayed()
                composeRule.onNodeWithTag(CHART_INSPECTOR_ZOOM_OUT_TAG).assertIsDisplayed()
            } catch (error: AssertionError) {
                val controlBounds =
                    composeRule.onNodeWithTag(CHART_INSPECTOR_RESET_TAG).fetchSemanticsNode().boundsInRoot
                throw AssertionError(
                    "Zoom and fit controls are not visible for chart fixture $index; control=$controlBounds",
                    error
                )
            }
            val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "test-evidence/g11").apply { mkdirs() }
            composeRule.onNodeWithTag(CHART_INSPECTOR_PLOT_TAG).captureToImage().asAndroidBitmap().let { bitmap ->
                File(evidence, "professional-family-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            composeRule.onNodeWithText("Close").performClick()
        }
    }

    @Test
    fun scatterDatasetChangeRevokesSelectionAndZoomAndAllowsSingleObservation() {
        var dataset by mutableIntStateOf(0)
        composeRule.setContent { MaterialTheme {
            ProfessionalScatterChart(title = "Same chart identity", subtitle = "Dataset replacement fixture",
                points = if(dataset == 0) listOf(ChartPoint(1000.0,0.2,Color.Blue,"Baseline"),ChartPoint(11000.0,0.9,Color.Red,"Expanded")) else listOf(ChartPoint(7.0,0.5,Color.Blue,"Replacement")),
                xAxisLabel="Rows",yAxisLabel="Macro-F1")
        } }
        composeRule.onNodeWithText("Open chart inspector").performClick()
        composeRule.onNodeWithTag(CHART_INSPECTOR_ZOOM_IN_TAG).performClick()
        composeRule.runOnUiThread { dataset = 1 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHART_INSPECTOR_PLOT_TAG).performTouchInput { click(center) }
        composeRule.onNodeWithText("Replacement").assertIsDisplayed()
        composeRule.onNodeWithTag(CHART_INSPECTOR_RESET_TAG).performClick()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Open chart inspector").assertIsDisplayed()
    }

    @Test
    fun heatMapInspector_tappingCellShowsFloatingExactData() {
        composeRule.setContent {
            MaterialTheme {
                InspectorFixture(chartType = 0)
            }
        }

        composeRule.onNodeWithText("Open chart inspector").performClick()
        composeRule.onNodeWithTag(CHART_INSPECTOR_PLOT_TAG).performTouchInput {
            click(center + Offset(140f, 0f))
        }
        composeRule.onNodeWithTag(CHART_INSPECTOR_SELECTION_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Rejected").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun InspectorFixture(chartType: Int) {
    when (chartType) {
        0 ->
            ProfessionalHeatMapChart(
                title = "Outcome matrix",
                subtitle = "Interaction fixture",
                rowLabels = listOf("All runs"),
                columnLabels = listOf("Accepted", "Rejected"),
                cells =
                    listOf(
                        ChartHeatMapCell("All runs", "Accepted", 70.0, "70%", Color.Green),
                        ChartHeatMapCell("All runs", "Rejected", 30.0, "30%", Color.Red)
                    ),
                xAxisLabel = "Outcome",
                yAxisLabel = "Runs"
            )

        1 ->
            ProfessionalScatterChart(
                title = "Scatter",
                subtitle = "Interaction fixture",
                points =
                    listOf(
                        ChartPoint(0.0, 0.0, Color.Blue, "A"),
                        ChartPoint(1.0, 1.0, Color.Red, "B")
                    ),
                xAxisLabel = "X",
                yAxisLabel = "Y"
            )

        2 ->
            ProfessionalLineChart(
                title = "Line",
                subtitle = "Interaction fixture",
                points = List(83) { ChartLinePoint(1000.0 + it * 120.0, 0.8 + kotlin.math.sin(it.toDouble()) * 0.02) },
                xAxisLabel = "X",
                yAxisLabel = "Y",
                color = Color.Blue
            )

        3 ->
            ProfessionalHistogramChart(
                title = "Histogram",
                subtitle = "Interaction fixture",
                values = listOf(0.0, 0.2, 0.8, 1.0),
                xAxisLabel = "Value",
                binCount = 2,
                color = Color.Blue
            )

        4 ->
            ProfessionalHorizontalBarChart(
                title = "Bars",
                subtitle = "Interaction fixture",
                items =
                    listOf(
                        ChartBarItem("Accepted", 70, Color.Green),
                        ChartBarItem("Rejected", 30, Color.Red)
                    ),
                xAxisLabel = "Count"
            )

        5 ->
            ProfessionalBoxPlotChart(
                title = "Box plot",
                subtitle = "Interaction fixture",
                items =
                    listOf(
                        ChartBoxPlotItem("A", listOf(1.0, 2.0, 3.0), Color.Blue),
                        ChartBoxPlotItem("B", listOf(2.0, 3.0, 4.0), Color.Red)
                    ),
                xAxisLabel = "Value",
                yAxisLabel = "Group"
            )

        else ->
            ProfessionalTimelineChart(
                title = "Timeline",
                subtitle = "Interaction fixture",
                cells =
                    listOf(
                        ChartTimelineCell("Accepted", Color.Green),
                        ChartTimelineCell("Rejected", Color.Red)
                    ),
                xAxisLabel = "Run",
                legendItems =
                    listOf(
                        ChartSlice("Accepted", 1, Color.Green),
                        ChartSlice("Rejected", 1, Color.Red)
                    )
            )
    }
}
