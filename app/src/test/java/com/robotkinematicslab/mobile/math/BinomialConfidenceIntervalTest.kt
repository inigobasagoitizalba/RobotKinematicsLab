package com.robotkinematicslab.mobile.math

import com.robotkinematicslab.mobile.math.statistics.WilsonScoreInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinomialConfidenceIntervalTest {

    @Test
    fun zeroSuccesses_keepsNonZeroUpperUncertainty() {
        val interval = WilsonScoreInterval.at95Percent(successes = 0, trials = 10)

        assertEquals(0.0, interval.estimate, 0.0)
        assertEquals(0.0, interval.lower, 1e-15)
        assertTrue(interval.upper > 0.25)
        assertTrue(interval.upper < 0.30)
    }

    @Test
    fun allSuccesses_keepsNonZeroLowerUncertainty() {
        val interval = WilsonScoreInterval.at95Percent(successes = 10, trials = 10)

        assertEquals(1.0, interval.estimate, 0.0)
        assertEquals(1.0, interval.upper, 1e-15)
        assertTrue(interval.lower > 0.70)
        assertTrue(interval.lower < 0.75)
    }

    @Test
    fun noTrials_isExplicitlyUnavailable() {
        val interval = WilsonScoreInterval.at95Percent(successes = 0, trials = 0)

        assertTrue(interval.estimate.isNaN())
        assertTrue(interval.lower.isNaN())
        assertTrue(interval.upper.isNaN())
    }
}
