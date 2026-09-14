package com.robotkinematicslab.mobile.ml.ik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OneMicronReliabilityCalculatorTest {

    @Test
    fun pipelineRoutesAreExclusiveWhilePureSolverIsAnIndependentComparator() {
        val metrics = OneMicronIkVerificationMetrics(100, 0.2, 0.6, 0.75,
            0.1, 0.2, 1e-7, 1e-7, 3.0, 4.0, 100.0, verifiedPipelineSuccessRate = 0.9)
        assertEquals(0.2, metrics.directNeuralSuccessRate, 1e-12)
        assertEquals(0.4, metrics.refinedOnlySuccessRate, 1e-12)
        assertEquals(0.3, metrics.fallbackOnlySuccessRate, 1e-12)
        assertEquals(0.1, metrics.failedPipelineRate, 1e-12)
        assertEquals(1.0, metrics.directNeuralSuccessRate + metrics.refinedOnlySuccessRate +
            metrics.fallbackOnlySuccessRate + metrics.failedPipelineRate, 1e-12)
        assertEquals(0.75, metrics.pureSolverSuccessRate, 0.0)
        assertTrue(metrics.copy(verifiedPipelineSuccessRate = Double.NaN).fallbackOnlySuccessRate.isNaN())
        assertTrue(metrics.copy(verifiedPipelineSuccessRate = Double.NaN).failedPipelineRate.isNaN())
        // Contradictory cumulative rates are rejected at construction, before any consumer sees them.
        assertThrows(IllegalArgumentException::class.java) { metrics.copy(neuralThenRefineSuccessRate = 0.1) }
        assertThrows(IllegalArgumentException::class.java) { metrics.copy(verifiedPipelineSuccessRate = 0.5) }
    }

    @Test
    fun reliabilityUsesWilsonEvidenceAndMeasuredDistanceSavings() {
        val assessment =
            OneMicronReliabilityCalculator.assess(
                OneMicronIkVerificationMetrics(
                    samples = 10_000,
                    rawNeuralSuccessRate = 0.0,
                    neuralThenRefineSuccessRate = 0.999,
                    deterministicBaselineSuccessRate = 1.0,
                    rawMedianErrorMeters = 0.1,
                    rawP95ErrorMeters = 0.3,
                    refinedMedianErrorMeters = 1e-7,
                    baselineMedianErrorMeters = 1e-7,
                    meanRefinedIterations = 10.0,
                    meanBaselineIterations = 50.0,
                    meanNeuralInferenceNanos = 20_000.0,
                    verifiedPipelineSuccessRate = 1.0,
                    rawCumulativeErrorMeters = 1_000.0,
                    protectedCumulativeErrorMeters = 0.001,
                    cumulativeErrorAvoidedPercent = 99.9999,
                    meanPipelineIterations = 11.0,
                    iterationSavingsPercent = 78.0
                )
            )

        assertEquals(1.0, assessment.observedCertifiedRate, 0.0)
        assertTrue(assessment.wilson95LowerBound > 0.999)
        assertEquals(ReliabilityBand.RESEARCH_GRADE, assessment.band)
        assertEquals(999.999, assessment.cumulativeDistanceAvoidedMeters, 1e-9)
        assertEquals(0.1, assessment.meanRawResidualMeters, 1e-12)
        assertEquals(1e-7, assessment.meanProtectedResidualMeters, 1e-15)
        assertEquals(1e-4, assessment.protectedResidualBudgetPerThousandCommandsMeters, 1e-12)
        assertEquals(78.0, assessment.iterationSavingsPercent, 0.0)
    }
}
