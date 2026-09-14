package com.robotkinematicslab.mobile.solver.ik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JointSpaceMetricReliabilityTest {

    @Test
    fun largeFiniteVector_keepsFiniteNormalizedNorm() {
        val result =
            JointSpaceMetric.normalizedNorm(
                delta = listOf(1e200, 1e200, 1e200),
                coordinateScales = listOf(1.0, 1.0, 1.0)
            )

        assertTrue(result.isFinite())
        assertEquals(kotlin.math.sqrt(3.0) * 1e200, result, 1e185)
    }

    @Test
    fun invalidCoordinateScale_isRejectedRatherThanHidingMovement() {
        assertThrows(IllegalArgumentException::class.java) {
            JointSpaceMetric.normalizedNorm(
                delta = listOf(1.0),
                coordinateScales = listOf(Double.POSITIVE_INFINITY)
            )
        }
    }
}
