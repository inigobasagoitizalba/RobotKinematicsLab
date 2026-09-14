package com.robotkinematicslab.mobile.ui.charts.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-corruption tests for wording whose scientific direction is easy to infer incorrectly.
 *
 * These labels are taken from real chart surfaces in the application. A misleading direction
 * badge is worse than no badge because a non-specialist may use it as an experimental conclusion.
 */
class ChartDirectionSafetyContractTest {

    @Test
    fun safetyAndOutcomeRatesKeepTheirDomainCorrectDirection() {
        val cases =
            listOf(
                DirectionCase("Oracle Pass Rate", ChartReadingDirection.HIGHER_TENDS_BETTER),
                DirectionCase("Close-or-Better Rate by Link Count", ChartReadingDirection.HIGHER_TENDS_BETTER),
                DirectionCase("Certified result rate", ChartReadingDirection.HIGHER_TENDS_BETTER),
                DirectionCase("Unreachable rejection rate", ChartReadingDirection.HIGHER_TENDS_BETTER),
                DirectionCase("False negative rate", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("False rejection rate", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("Unreachable acceptance rate", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("Numerical safety events", ChartReadingDirection.LOWER_TENDS_BETTER)
            )

        cases.forEach { case ->
            val guide = ChartGuideFactory.forChart(ChartGuideKind.LINE, case.title)
            assertEquals(case.title, case.expected, guide.direction)
        }
    }

    @Test
    fun confidenceWordingDoesNotOverrideTheMetricActuallyBeingRanked() {
        val successInterval =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = "Confidence Interval Success Chart",
                subtitle = "Strict success rate with a 95% confidence interval."
            )
        val reliability =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = "Reliability curve",
                subtitle = "Mean confidence versus observed accuracy."
            )
        val riskCoverage =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.SCATTER,
                title = "Risk versus retained coverage",
                subtitle = "Error rate among retained high-confidence predictions."
            )

        assertEquals(ChartReadingDirection.HIGHER_TENDS_BETTER, successInterval.direction)
        assertEquals(ChartReadingDirection.TARGET_DEPENDENT, reliability.direction)
        assertEquals(ChartReadingDirection.TRADE_OFF, riskCoverage.direction)
    }

    @Test
    fun distributionAndFailureParetoChartsCannotBeReducedToOneVerticalDirection() {
        val errorCdf =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = "Final Error CDF",
                yAxisLabel = "Cumulative probability"
            )
        val failurePareto =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Pareto Failure Chart",
                subtitle = "Sorted failure codes with cumulative contribution."
            )

        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, errorCdf.direction)
        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, failurePareto.direction)
    }

    @Test
    fun topologyGroupingDoesNotEraseTheMeasuredMetricDirection() {
        val cases =
            listOf(
                DirectionCase("Acceptance Rate by Topology", ChartReadingDirection.HIGHER_TENDS_BETTER),
                DirectionCase("Final Error by Topology", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("Iterations by Topology", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("Joint-Limit Pressure by Topology", ChartReadingDirection.LOWER_TENDS_BETTER),
                DirectionCase("Failure Code by Topology", ChartReadingDirection.PATTERN_NOT_RANK)
            )

        cases.forEach { case ->
            val guide = ChartGuideFactory.forChart(ChartGuideKind.BAR, case.title)
            assertEquals(case.title, case.expected, guide.direction)
        }
    }

    @Test
    fun mixedOutcomeCompositionsAndSampleVolumeRemainPatternEvidence() {
        val acceptedRejected =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.STACKED_SHARE,
                title = "Sequential Accepted vs Rejected"
            )
        val reachableComposition =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.STACKED_SHARE,
                title = "Reachable Accepted / Rejected"
            )
        val sampleVolume =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Sample Volume by Link Count"
            )

        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, acceptedRejected.direction)
        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, reachableComposition.direction)
        assertEquals(ChartReadingDirection.PATTERN_NOT_RANK, sampleVolume.direction)
    }

    @Test
    fun explicitDirectionAlwaysWinsOverHeuristicWording() {
        ChartReadingDirection.entries.forEach { expected ->
            val guide =
                ChartGuideFactory.forChart(
                    kind = ChartGuideKind.SUMMARY,
                    title = "Error success confidence trade-off",
                    directionOverride = expected
                )

            assertEquals(expected.name, expected, guide.direction)
        }
    }

    @Test
    fun everyChartGrammarProducesACompleteNonAbsoluteReadingContract() {
        ChartGuideKind.entries.forEach { kind ->
            val guide =
                ChartGuideFactory.forChart(
                    kind = kind,
                    title = "Neutral scientific result",
                    // Horizontal bars place the measured quantity on X and categories on Y.
                    xAxisLabel = if (kind == ChartGuideKind.BAR) "Observed value (mm)" else "Experiment arm",
                    yAxisLabel = if (kind == ChartGuideKind.BAR) "Experiment arm" else "Observed value (mm)"
                )

            assertTrue("$kind type", guide.typeLabel.isNotBlank())
            assertTrue("$kind measure", guide.whatItShows.isNotBlank())
            assertTrue("$kind reading", guide.howToRead.isNotBlank())
            assertTrue("$kind question", guide.researchQuestion.endsWith('?'))
            assertTrue("$kind relevance", guide.whyItMatters.isNotBlank())
            assertTrue("$kind caution", guide.caution.isNotBlank())
            assertEquals("$kind unit", "mm", guide.unitOrScale)
        }
    }

    private data class DirectionCase(
        val title: String,
        val expected: ChartReadingDirection
    )
}
