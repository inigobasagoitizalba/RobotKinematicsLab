package com.robotkinematicslab.mobile.ml.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConformalClassificationCalculatorTest {

    @Test
    fun `split conformal produces finite coverage and set size`() {
        val calibration = (0 until 20).map { index -> observation(index.toLong(), 0, 0.8, 0.1, 0.1) }
        val evaluation =
            listOf(
                observation(100, 0, 0.9, 0.05, 0.05),
                observation(101, 1, 0.2, 0.7, 0.1),
                observation(102, 2, 0.4, 0.3, 0.3)
            )

        val result = ConformalClassificationCalculator.calculate(calibration, evaluation, alpha = 0.10)

        assertEquals(0.8, result.probabilityThreshold, 1e-12)
        assertTrue(result.empiricalCoverage in 0.0..1.0)
        assertTrue(result.averageSetSize in 0.0..3.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calibration evidence cannot also be evaluation evidence`() {
        val row = observation(1, 0, 0.8, 0.1, 0.1)
        ConformalClassificationCalculator.calculate(listOf(row), listOf(row), 0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate calibration evidence cannot inflate conformal support`() {
        val row = observation(1, 0, 0.8, 0.1, 0.1)
        ConformalClassificationCalculator.calculate(
            calibration = listOf(row, row),
            evaluation = listOf(observation(2, 0, 0.8, 0.1, 0.1)),
            alpha = 0.1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate evaluation evidence cannot inflate empirical coverage`() {
        val row = observation(2, 0, 0.8, 0.1, 0.1)
        ConformalClassificationCalculator.calculate(
            calibration = listOf(observation(1, 0, 0.8, 0.1, 0.1)),
            evaluation = listOf(row, row),
            alpha = 0.1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid probability contract is rejected`() {
        observation(1, 0, 0.8, 0.8, 0.8)
    }

    private fun observation(id: Long, truth: Int, vararg probabilities: Double) =
        ConformalClassificationObservation(id, truth, probabilities)
}
