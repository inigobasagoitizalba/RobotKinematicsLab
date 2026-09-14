package com.robotkinematicslab.mobile.solver.ik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearSingularityAnalyzerReliabilityTest {

    private val analyzer = NearSingularityAnalyzer()

    @Test
    fun oneDegreeOfFreedomDoesNotUseTwoStructuralZeroSingularValues() {
        val report = analyzer.analyze(listOf(1.0), listOf(0.0), listOf(0.0))

        assertEquals(1, report.expectedRank)
        assertEquals(1, report.observedRank)
        assertEquals(1.0, report.sigmaMin, 1e-12)
        assertEquals(1.0, report.conditionNumber, 1e-12)
        assertFalse(report.isNearSingular)
        assertTrue(report.taskSpaceUnderactuated)
    }

    @Test
    fun twoIndependentColumnsUseSmallestRelevantSingularValue() {
        val report = analyzer.analyze(
            jacobianX = listOf(1.0, 0.0),
            jacobianY = listOf(0.0, 0.5),
            jacobianZ = listOf(0.0, 0.0)
        )

        assertEquals(2, report.expectedRank)
        assertEquals(2, report.observedRank)
        assertEquals(0.5, report.sigmaMin, 1e-12)
        assertEquals(2.0, report.conditionNumber, 1e-12)
        assertFalse(report.isNearSingular)
    }

    @Test
    fun rankLossRelativeToExpectedRankIsCritical() {
        val report = analyzer.analyze(
            jacobianX = listOf(1.0, 2.0),
            jacobianY = listOf(0.0, 0.0),
            jacobianZ = listOf(0.0, 0.0)
        )

        assertEquals(1, report.observedRank)
        assertEquals(SingularityLevel.CRITICAL, report.level)
    }

    @Test
    fun columnScalingMakesConditioningIndependentOfJointCoordinateUnits() {
        val report =
            analyzer.analyze(
                jacobianX = listOf(1.0, 0.0),
                jacobianY = listOf(0.0, 0.01),
                jacobianZ = listOf(0.0, 0.0),
                columnScales = listOf(1.0, 100.0)
            )

        assertEquals(1.0, report.conditionNumber, 1e-12)
        assertEquals(SingularityLevel.NORMAL, report.level)
    }

    @Test
    fun emptyOrNonFiniteJacobiansAreRejectedBeforeClassification() {
        assertRejected {
            analyzer.analyze(emptyList(), emptyList(), emptyList())
        }
        assertRejected {
            analyzer.analyze(listOf(Double.NaN), listOf(0.0), listOf(0.0))
        }
        assertRejected {
            analyzer.analyze(listOf(1.0), listOf(Double.POSITIVE_INFINITY), listOf(0.0))
        }
    }

    @Test
    fun invalidThresholdOrderingAndOverflowedJacobianEnergyAreRejected() {
        assertRejected {
            NearSingularityAnalyzer(
                sigmaMinWarningThreshold = 1e-5,
                sigmaMinCriticalThreshold = 1e-3
            )
        }
        assertRejected {
            analyzer.analyze(
                jacobianX = listOf(Double.MAX_VALUE),
                jacobianY = listOf(Double.MAX_VALUE),
                jacobianZ = listOf(Double.MAX_VALUE)
            )
        }
    }

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
