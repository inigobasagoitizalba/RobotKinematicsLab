package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.report.SeedAggregate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeedComparisonSummaryTest {

    private val aggregator =
        DiagnosticStatsAggregator()

    @Test
    fun emptySeedAggregates_returnsEmptySummary() {
        val summary =
            aggregator.buildSeedComparisonSummary(
                seedAggregates = emptyList()
            )

        assertEquals(0, summary.seedCount)
        assertNull(summary.bestSeed)
        assertNull(summary.worstSeed)
        assertEquals(0.0, summary.averageStrictAcceptanceRate, EPSILON)
        assertEquals(0.0, summary.strictAcceptanceRateVariance, EPSILON)
        assertEquals(0.0, summary.strictAcceptanceRateSpread, EPSILON)
        assertEquals("N/A", summary.seedSensitivityLabel)
    }

    @Test
    fun singleSeed_returnsSingleSeedOnlyLabel() {
        val summary =
            aggregator.buildSeedComparisonSummary(
                seedAggregates =
                    listOf(
                        seedAggregate(
                            seed = 42,
                            strictAcceptanceRate = 0.80
                        )
                    )
            )

        assertEquals(1, summary.seedCount)
        assertEquals(42, summary.bestSeed)
        assertEquals(42, summary.worstSeed)
        assertEquals(0.80, summary.averageStrictAcceptanceRate, EPSILON)
        assertEquals(0.0, summary.strictAcceptanceRateVariance, EPSILON)
        assertEquals(0.0, summary.strictAcceptanceRateSpread, EPSILON)
        assertEquals("Single seed only", summary.seedSensitivityLabel)
    }

    @Test
    fun lowSeedSensitivity_isClassifiedLowWhenSpreadBelowFivePercent() {
        val summary =
            aggregator.buildSeedComparisonSummary(
                seedAggregates =
                    listOf(
                        seedAggregate(seed = 1, strictAcceptanceRate = 0.90),
                        seedAggregate(seed = 2, strictAcceptanceRate = 0.92),
                        seedAggregate(seed = 3, strictAcceptanceRate = 0.94)
                    )
            )

        assertEquals(3, summary.seedCount)
        assertEquals(3, summary.bestSeed)
        assertEquals(1, summary.worstSeed)
        assertEquals(0.92, summary.averageStrictAcceptanceRate, EPSILON)
        assertEquals(0.0002666666666666663, summary.strictAcceptanceRateVariance, EPSILON)
        assertEquals(0.04, summary.strictAcceptanceRateSpread, EPSILON)
        assertEquals("LOW", summary.seedSensitivityLabel)
    }

    @Test
    fun mediumSeedSensitivity_isClassifiedMediumWhenSpreadBelowFifteenPercent() {
        val summary =
            aggregator.buildSeedComparisonSummary(
                seedAggregates =
                    listOf(
                        seedAggregate(seed = 10, strictAcceptanceRate = 0.80),
                        seedAggregate(seed = 20, strictAcceptanceRate = 0.86),
                        seedAggregate(seed = 30, strictAcceptanceRate = 0.92)
                    )
            )

        assertEquals(3, summary.seedCount)
        assertEquals(30, summary.bestSeed)
        assertEquals(10, summary.worstSeed)
        assertEquals(0.86, summary.averageStrictAcceptanceRate, EPSILON)
        assertEquals(0.0024, summary.strictAcceptanceRateVariance, EPSILON)
        assertEquals(0.12, summary.strictAcceptanceRateSpread, EPSILON)
        assertEquals("MEDIUM", summary.seedSensitivityLabel)
    }

    @Test
    fun highSeedSensitivity_isClassifiedHighWhenSpreadIsFifteenPercentOrHigher() {
        val summary =
            aggregator.buildSeedComparisonSummary(
                seedAggregates =
                    listOf(
                        seedAggregate(seed = 100, strictAcceptanceRate = 0.60),
                        seedAggregate(seed = 200, strictAcceptanceRate = 0.75),
                        seedAggregate(seed = 300, strictAcceptanceRate = 0.90)
                    )
            )

        assertEquals(3, summary.seedCount)
        assertEquals(300, summary.bestSeed)
        assertEquals(100, summary.worstSeed)
        assertEquals(0.75, summary.averageStrictAcceptanceRate, EPSILON)
        assertEquals(0.015, summary.strictAcceptanceRateVariance, EPSILON)
        assertEquals(0.30, summary.strictAcceptanceRateSpread, EPSILON)
        assertEquals("HIGH", summary.seedSensitivityLabel)
    }

    private fun seedAggregate(
        seed: Int,
        strictAcceptanceRate: Double
    ): SeedAggregate {
        return SeedAggregate(
            seed = seed,
            runCount = 100,
            strictAcceptedCount = (strictAcceptanceRate * 100.0).toInt(),
            nearSolvedCount = 0,
            closeMissCount = 0,
            farFailureCount = 0,
            strictAcceptanceRate = strictAcceptanceRate,
            averageFinalError = 0.0,
            maxFinalError = 0.0,
            averageIterations = 0.0,
            averageImprovementRatio = 0.0
        )
    }

    companion object {
        private const val EPSILON = 1e-9
    }
}
