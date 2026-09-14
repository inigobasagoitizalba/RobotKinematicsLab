package com.robotkinematicslab.mobile.ui.charts

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the shared chart routes so a local optimisation cannot silently degrade every figure. */
class ChartGlobalFidelityContractTest {

    @Test
    fun lineAndScatterUseCompleteSourceForInspectorAndEveryPngCapture() {
        listOf(
            "linecharts/ProfessionalLineChart.kt" to "LineChartInspectorDialog",
            "scattercharts/ProfessionalScatterChart.kt" to "ScatterInspectorDialog"
        ).forEach { (relativePath, inspector) ->
            val source = normalizedSource("ui/charts/advanced/$relativePath")

            assertContains(
                source,
                "val renderedPoints = if (figureCaptureInProgress) cleanPoints else displayPoints",
                relativePath
            )
            val isLine = inspector == "LineChartInspectorDialog"
            // The calibration reference is a rendering option, not a replacement for observations.
            val inspectorArguments = if (isLine) "referenceDiagonal = referenceDiagonal, " else ""
            assertContains(source, "$inspector( ${inspectorArguments}title = title, subtitle = subtitle, points = cleanPoints", relativePath)
            assertContains(
                source,
                if (isLine) {
                    "automaticExportKey = cleanPoints.takeIf { it.isNotEmpty() }?.let { listOf(it,referenceDiagonal).hashCode() }"
                } else {
                    "automaticExportKey = cleanPoints.takeIf { it.isNotEmpty() }?.hashCode()"
                },
                relativePath
            )
        }
    }

    @Test
    fun everyOtherProfessionalFamilySharesItsCompletePreparedSourceWithCanvasAndInspector() {
        val routes =
            listOf(
                FamilyRoute("histogramcharts/ProfessionalHistogramChart.kt", "bins = bins"),
                FamilyRoute("horizontalbarcharts/ProfessionalHorizontalBarChart.kt", "items = preparedItems"),
                FamilyRoute("boxplotcharts/ProfessionalBoxPlotChart.kt", "items = preparedItems"),
                FamilyRoute("heatmaps/ProfessionalHeatMapChart.kt", "cells = cleanCells"),
                FamilyRoute("timelinecharts/ProfessionalTimelineChart.kt", "cells = preparedCells")
            )

        routes.forEach { route ->
            val source = normalizedSource("ui/charts/advanced/${route.relativePath}")
            val occurrences = Regex(Regex.escape(route.fullSourceArgument)).findAll(source).count()

            assertTrue(
                "${route.relativePath} must pass the same complete source to canvas and inspector",
                occurrences >= 2
            )
            assertFalse(
                "${route.relativePath} must not introduce generic point-count truncation",
                source.contains("sampleForResponsiveChartDisplay(") ||
                    source.contains("downsampleTelemetryPoints(")
            )
        }
    }

    @Test
    fun basicChartFamiliesFingerprintAndIterateTheirCompleteInputs() {
        val routes =
            listOf(
                BasicRoute("DonutChart.kt", "automaticExportKey = listOf(centerLabel, slices)", "slices.forEach"),
                BasicRoute("GroupedRatioBarChart.kt", "automaticExportKey = items", "items.forEach"),
                BasicRoute("HorizontalBarChart.kt", "automaticExportKey = items", "items.forEach"),
                BasicRoute("HorizontalDoubleBarChart.kt", "automaticExportKey = items", "items.forEach"),
                BasicRoute("StackedBarChart.kt", "automaticExportKey = listOf(totalLabel, slices)", "slices.forEach")
            )

        routes.forEach { route ->
            val source = normalizedSource("ui/charts/basic/${route.relativePath}")
            assertContains(source, route.exportKey, route.relativePath)
            assertContains(source, route.completeIteration, route.relativePath)
            assertFalse("${route.relativePath} must not cap categories", source.contains(".take("))
        }

        val gauge = normalizedSource("ui/charts/basic/GaugeChart.kt")
        assertContains(gauge, "automaticExportKey = listOf(ratio, valueLabel, color)", "GaugeChart.kt")
    }

    @Test
    fun telemetryDashboardAndFinalRendererUseTheSameUncappedPointContract() {
        val dashboard = normalizedSource("ui/shared/progress/DiagnosticTelemetryDashboard.kt")
        val finalRenderer = normalizedSource("ui/shared/progress/FinalTelemetryFigureArchiver.kt")

        assertContains(
            dashboard,
            "val displayPoints = telemetryPointsForRendering(series.points)",
            "DiagnosticTelemetryDashboard.kt"
        )
        assertContains(
            finalRenderer,
            "val points = telemetryPointsForRendering(series.points)",
            "FinalTelemetryFigureArchiver.kt"
        )
        assertFalse("Dashboard must not restore the former 240-point cap", dashboard.contains("MAX_DRAWN_POINTS"))
        assertFalse(
            "Final PNG renderer must not restore the former 600-point cap",
            finalRenderer.contains("downsampleTelemetryPoints(")
        )
    }

    private fun normalizedSource(relativePath: String): String =
        locateSource(relativePath).readText().replace(Regex("\\s+"), " ").trim()

    private fun locateSource(relativePath: String): File {
        val suffix = "app/src/main/java/com/robotkinematicslab/mobile/$relativePath"
        val start = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { root -> File(root, suffix) }
            .firstOrNull(File::isFile)
            ?: error("Cannot locate production source $suffix from ${start.absolutePath}")
    }

    private fun assertContains(source: String, expected: String, route: String) {
        assertTrue("$route no longer satisfies the full-source contract: $expected", source.contains(expected))
    }

    private data class FamilyRoute(
        val relativePath: String,
        val fullSourceArgument: String
    )

    private data class BasicRoute(
        val relativePath: String,
        val exportKey: String,
        val completeIteration: String
    )
}
