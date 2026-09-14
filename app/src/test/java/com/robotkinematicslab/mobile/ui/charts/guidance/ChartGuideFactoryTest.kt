package com.robotkinematicslab.mobile.ui.charts.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGuideFactoryTest {

    @Test
    fun residualLineUsesLowerIsNormallyBetterWithoutMakingAnAbsoluteClaim() {
        val guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = "Final residual by iteration",
                subtitle = "Independent Cartesian residual for every solver step.",
                xAxisLabel = "Iteration",
                yAxisLabel = "Residual (m)",
                supportsDataInspector = true
            )

        assertEquals(ChartReadingDirection.LOWER_TENDS_BETTER, guide.direction)
        assertTrue(guide.howToRead.contains("Iteration"))
        assertTrue(guide.howToRead.contains("Residual (m)"))
        assertTrue(guide.supportsDataInspector)
    }

    @Test
    fun successHeatMapUsesHigherDirectionButFailureCodeMatrixUsesPatternDirection() {
        val success =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HEAT_MAP,
                title = "Success rate heat map"
            )
        val failureCode =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HEAT_MAP,
                title = "Failure code by topology"
            )

        assertEquals(ChartReadingDirection.HIGHER_TENDS_BETTER, success.direction)
        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, failureCode.direction)
        assertTrue(failureCode.caution.contains("Missing", ignoreCase = true).not())
        assertTrue(failureCode.howToRead.contains("Missing cells"))
    }

    @Test
    fun toleranceAndParetoGuidesDoNotPretendOneExtremeIsAlwaysBest() {
        val tolerance =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.GAUGE,
                title = "Tolerance compliance"
            )
        val pareto =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.SCATTER,
                title = "Pareto cost / quality comparison"
            )

        assertEquals(ChartReadingDirection.TARGET_DEPENDENT, tolerance.direction)
        assertEquals(ChartReadingDirection.TRADE_OFF, pareto.direction)
    }

    @Test
    fun automaticGuidanceLeavesOrdinarySettingsCardsUntouched() {
        assertNull(
            ChartGuideFactory.inferForCard(
                title = "Appearance",
                subtitle = "Choose a fixed application palette."
            )
        )

        val inferred =
            ChartGuideFactory.inferForCard(
                title = "Error distribution summary",
                subtitle = "Held-out residuals."
            )
        assertEquals(ChartGuideKind.HISTOGRAM, inferred?.kind)
        assertFalse(requireNotNull(inferred).supportsDataInspector)
    }

    @Test
    fun knownFailureAndUncertaintyMetricsCannotBeMisclassifiedByPositiveWords() {
        val lowerBetterTitles =
            listOf(
                "Unreachable Accepted",
                "Perturbation magnitude versus accuracy drop",
                "Calibration gap by robot",
                "Seed Acceptance Spread",
                "Runtime Risk by Link Count",
                "Masked validation MSE",
                "Numerical safety events"
            )

        lowerBetterTitles.forEach { title ->
            val guide = ChartGuideFactory.forChart(ChartGuideKind.LINE, title)
            assertEquals(title, ChartReadingDirection.LOWER_TENDS_BETTER, guide.direction)
        }
    }

    @Test
    fun reliabilityAndRiskCoverageUseTheirScientificReadingContracts() {
        val reliability = ChartGuideFactory.forChart(ChartGuideKind.LINE, "Reliability curve")
        val riskCoverage = ChartGuideFactory.forChart(ChartGuideKind.SCATTER, "Risk-coverage curve")

        assertEquals(ChartReadingDirection.TARGET_DEPENDENT, reliability.direction)
        assertEquals(ChartReadingDirection.TRADE_OFF, riskCoverage.direction)
        assertTrue(reliability.whyItMatters.isNotBlank())
    }

    @Test
    fun yAxisUnitOrScaleTravelsWithTheGuide() {
        val guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = "Residual history",
                yAxisLabel = "Residual (mm)"
            )

        assertEquals("mm", guide.unitOrScale)
    }
}
