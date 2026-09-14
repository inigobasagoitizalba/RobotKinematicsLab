package com.robotkinematicslab.mobile.math

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.math.utility.AngleNormalizer
import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathUtilityReliabilityTest {

    @Test
    fun angleNormalizationPreservesBoundaryContract() {
        val normalizer = AngleNormalizer()

        assertEquals(0.0, normalizer.normalizeRadians(0.0), 0.0)
        assertEquals(PI, normalizer.normalizeRadians(-PI), 1e-15)
        assertEquals(PI, normalizer.normalizeRadians(PI), 1e-15)
        assertEquals(PI / 2.0, normalizer.normalizeRadians(5.0 * PI / 2.0), 1e-15)
        assertEquals(-PI / 2.0, normalizer.normalizeRadians(-5.0 * PI / 2.0), 1e-15)
    }

    @Test
    fun hugeFiniteAngleNormalizesInConstantWorkAndStaysInRange() {
        val started = System.nanoTime()
        val result = AngleNormalizer().normalizeRadians(Double.MAX_VALUE)
        val durationMillis = (System.nanoTime() - started) / 1_000_000.0

        assertTrue(result.isFinite())
        assertTrue(result > -PI && result <= PI)
        assertTrue("Normalization took $durationMillis ms", durationMillis < 500.0)
    }

    @Test
    fun nonFiniteAngleIsPreservedForExplicitDownstreamValidation() {
        val normalizer = AngleNormalizer()

        assertTrue(normalizer.normalizeRadians(Double.NaN).isNaN())
        assertEquals(Double.POSITIVE_INFINITY, normalizer.normalizeRadians(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun dhMatrixMatchesKnownQuarterTurnAndIdentityComposition() {
        val matrix = Matrix4.fromDH(DHParameter(theta = PI / 2.0, d = 0.3, a = 2.0, alpha = 0.0))
        val translation = matrix.translation()

        assertEquals(0.0, translation.x, 1e-12)
        assertEquals(2.0, translation.y, 1e-12)
        assertEquals(0.3, translation.z, 1e-12)
        assertMatrixEquals(matrix, Matrix4.identity() * matrix)
        assertMatrixEquals(matrix, matrix * Matrix4.identity())
    }

    @Test
    fun vec3OperationsRespectEuclideanIdentitiesAndRejectZeroNormalization() {
        val x = Vec3.UNIT_X
        val y = Vec3.UNIT_Y
        assertEquals(Vec3.UNIT_Z, x.cross(y))
        assertEquals(0.0, x.dot(y), 0.0)
        assertEquals(1.0, Vec3(3.0, 4.0, 0.0).normalized().norm(), 1e-12)

        var rejected = false
        try {
            Vec3.ZERO.normalized()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun vec3NormAndNormalizationRemainStableForHugeFiniteComponents() {
        val huge = Double.MAX_VALUE / 2.0
        val vector = Vec3(huge, huge, 0.0)
        val norm = vector.norm()
        val normalized = vector.normalized()

        assertTrue(norm.isFinite())
        assertTrue(normalized.isFinite())
        assertEquals(1.0, normalized.norm(), 1e-12)
    }

    private fun assertMatrixEquals(expected: Matrix4, actual: Matrix4) {
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                assertEquals(expected.m[row][column], actual.m[row][column], 1e-12)
            }
        }
    }
}
