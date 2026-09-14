package com.robotkinematicslab.mobile.ui.help

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps novice help discoverable on technical diagnostic surfaces without hijacking controls. */
class DiagnosticJargonSurfaceContractTest {

    @Test
    fun eachTechnicalEntrySurfaceShowsOneHelpNotice() {
        listOf(
            "ui/diagnostic/Layer1DiagnosticPanel.kt",
            "ui/diagnostic/numericalsafety/NumericalSafetyAuditPanel.kt",
            "ui/charts/DiagnosticChartsScreen.kt",
            "ui/shared/progress/DiagnosticTelemetryDashboard.kt"
        ).forEach { relativePath ->
            val source = source(relativePath)
            assertEquals(
                "$relativePath must show exactly one jargon-help notice",
                1,
                Regex("JargonHelpNotice\\s*\\(\\s*\\)").findAll(source).count()
            )
        }
    }

    @Test
    fun diagnosticExplanationsResolveThePriorityVocabulary() {
        val explanatoryCopy =
            listOf(
                "A seed repeats choices across each topology.",
                "DLS reduces the residual until tolerance or maximum iterations; maximum step limits each move.",
                "Telemetry reports heap, GC and thermal throttling.",
                "A logarithmic scale plus percentiles and outliers reveal tail behaviour in residual error.",
                "The 3D view groups samples into voxels."
            ).joinToString(" ")

        val resolvedTerms = JargonCatalog.findMatches(explanatoryCopy).map { it.definition.term }.toSet()
        val required =
            setOf(
                "Seed",
                "Topology",
                "Damped least squares (DLS)",
                "Residual / final error",
                "Tolerance",
                "Solver iteration",
                "Maximum step",
                "Telemetry",
                "App heap",
                "Garbage collection (GC)",
                "Thermal throttling",
                "Logarithmic scale",
                "Percentile",
                "Outlier",
                "Voxel"
            )

        assertTrue("Unresolved diagnostic help terms: ${required - resolvedTerms}", resolvedTerms.containsAll(required))
    }

    @Test
    fun actionLabelsAndClickableSectionHeadersRemainPlainText() {
        val input = source("ui/diagnostic/DiagnosticInputCards.kt")
        val charts = source("ui/charts/DiagnosticChartsScreen.kt")
        val rawTelemetry = source("ui/shared/progress/DiagnosticRawTelemetrySnapshot.kt")
        val disclosure = source("ui/shared/AppDisclosureSection.kt")
        val selectors = source("ui/shared/AppUiComponents.kt")

        listOf(
            "Run Diagnostic Experiment",
            "Cancel Diagnostic Experiment",
            "Balanced solver",
            "Precision solver",
            "Exploration solver",
            "Back to Diagnostics"
        ).forEach { label ->
            assertFalse("Interactive label '$label' must not gain nested jargon gestures", input.contains("JargonAwareText(\"$label\"") || charts.contains("JargonAwareText(\"$label\""))
        }
        // Diagnostics delegates clickable headers and preset labels to shared owners.
        assertTrue(input.contains("AppDisclosureSection(title = title, summary = subtitle,"))
        assertTrue(disclosure.contains("Text( text = title,"))
        assertFalse(disclosure.contains("JargonAwareText("))
        assertTrue(input.contains("CompactSelectionMenu(options = DiagnosticSolverPreset.entries,"))
        assertTrue(selectors.contains("Text( text = label,"))
        assertFalse(selectors.contains("JargonAwareText("))
        assertTrue(rawTelemetry.contains("Text( title,"))
    }

    private fun source(relativePath: String): String =
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
}
