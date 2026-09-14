package com.robotkinematicslab.mobile.ml.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetCoverageAnalyzerTest {

    @Test
    fun `near rows are in distribution and remote rows are flagged`() {
        val reference =
            listOf(
                coverage(1, 0.0, 0.0),
                coverage(2, 1.0, 1.0),
                coverage(3, 2.0, 2.0)
            )
        val evaluation =
            listOf(
                coverage(4, 1.0, 1.0),
                coverage(5, 20.0, 20.0)
            )

        val result = DatasetCoverageAnalyzer.analyze(reference, evaluation)

        assertFalse(result.points.first().outOfDistribution)
        assertTrue(result.points.last().outOfDistribution)
        assertEquals(0.5, result.outOfDistributionRate, 1e-12)
        assertTrue(result.calibratedDistanceThreshold.isFinite())
        assertEquals(1, result.referenceGroupCount)
        assertEquals(1, result.evaluationGroupCount)
        assertEquals(0, result.constantFeatureCount)
    }

    @Test
    fun `constant dimensions stay finite and are reported`() {
        val result =
            DatasetCoverageAnalyzer.analyze(
                reference = listOf(coverage(1, 1.0, 0.0), coverage(2, 1.0, 1.0)),
                evaluation = listOf(coverage(3, 1.0, 0.5))
            )

        assertEquals(1, result.constantFeatureCount)
        assertTrue(result.meanNearestDistance.isFinite())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched feature contracts are rejected`() {
        DatasetCoverageAnalyzer.analyze(
            reference = listOf(coverage(1, 0.0, 1.0)),
            evaluation = listOf(CoverageObservation(2, "robot", doubleArrayOf(0.0)))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reference and evaluation leakage is rejected`() {
        DatasetCoverageAnalyzer.analyze(
            reference = listOf(coverage(1, 0.0), coverage(2, 1.0)),
            evaluation = listOf(coverage(2, 1.0))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate reference rows cannot bias coverage calibration`() {
        DatasetCoverageAnalyzer.analyze(
            reference = listOf(coverage(1, 0.0), coverage(1, 0.0)),
            evaluation = listOf(coverage(2, 1.0))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite coverage rows are rejected`() {
        DatasetCoverageAnalyzer.analyze(
            reference = listOf(CoverageObservation(1, "train", doubleArrayOf(Double.NaN))),
            evaluation = listOf(CoverageObservation(2, "test", doubleArrayOf(0.0)))
        )
    }

    @Test
    fun `single reference cap remains deterministic`() {
        val result = DatasetCoverageAnalyzer.analyze(
            reference = listOf(
                CoverageObservation(1, "train", doubleArrayOf(0.0)),
                CoverageObservation(2, "train", doubleArrayOf(1.0))
            ),
            evaluation = listOf(CoverageObservation(3, "test", doubleArrayOf(0.5))),
            maximumReferencePoints = 1
        )

        assertEquals(1, result.points.size)
        assertEquals(2, result.referenceCount)
        assertEquals(1, result.comparedReferenceCount)
        assertTrue(result.points.single().normalizedNearestReferenceDistance.isFinite())
    }

    @Test
    fun `bounded reference sample preserves exact nearest match at sampled endpoints`() {
        val result = DatasetCoverageAnalyzer.analyze(
            reference = (0L..9L).map { id ->
                CoverageObservation(id, "train", doubleArrayOf(id.toDouble(), id.toDouble()))
            },
            evaluation = listOf(CoverageObservation(10, "test", doubleArrayOf(9.0, 9.0))),
            maximumReferencePoints = 3
        )

        assertEquals(10, result.referenceCount)
        assertEquals(3, result.comparedReferenceCount)
        assertEquals(0.0, result.points.single().normalizedNearestReferenceDistance, 0.0)
    }

    @Test
    fun `one marginal range escape is evidence but not an automatic multivariate OOD decision`() {
        val result = DatasetCoverageAnalyzer.analyze(
            reference = listOf(
                coverage(1, 0.0, 0.0),
                coverage(2, 1.0, 1.0),
                coverage(3, 2.0, 2.0)
            ),
            evaluation = listOf(coverage(4, 2.01, 2.0))
        )

        assertTrue(result.points.single().outsideTrainingRangeFraction > 0.0)
        assertEquals(1.0, result.rangeEscapeRate, 0.0)
        assertFalse(result.points.single().outOfDistribution)
    }

    @Test
    fun `explicit scientific threshold override is honoured`() {
        val result = DatasetCoverageAnalyzer.analyze(
            reference = listOf(coverage(1, 0.0), coverage(2, 1.0), coverage(3, 2.0)),
            evaluation = listOf(coverage(4, 1.1)),
            outOfDistributionDistance = 0.01
        )

        assertEquals(0.01, result.calibratedDistanceThreshold, 0.0)
        assertTrue(result.points.single().outOfDistribution)
    }

    private fun coverage(id: Long, vararg values: Double) =
        CoverageObservation(id, "robot", values)
}
