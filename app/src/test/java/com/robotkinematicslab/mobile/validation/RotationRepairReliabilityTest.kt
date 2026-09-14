package com.robotkinematicslab.mobile.validation

import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.validation.output.RotationDriftAssessor
import com.robotkinematicslab.mobile.validation.output.RotationRepairDecision
import com.robotkinematicslab.mobile.validation.output.RotationRepairer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationRepairReliabilityTest {

    @Test
    fun malformedTransformReturnsControlledFailure() {
        val transform = Matrix4(arrayOf(doubleArrayOf(1.0)))

        val result = RotationRepairer().repair(transform)

        assertFalse(result.repairApplied)
        assertEquals(transform, result.repairedTransform)
    }

    @Test
    fun polarRepairProducesProperRotationAndPreservesTranslation() {
        val transform =
            Matrix4(
                arrayOf(
                    doubleArrayOf(1.0, 0.012, 0.0, 0.4),
                    doubleArrayOf(-0.004, 0.999, 0.008, -0.2),
                    doubleArrayOf(0.003, -0.006, 1.002, 0.7),
                    doubleArrayOf(0.0, 0.0, 0.0, 1.0)
                )
            )

        val before = RotationDriftAssessor().assess(transform.m)
        assertEquals(RotationRepairDecision.REPAIR_ALLOWED, before.decision)

        val result = RotationRepairer().repair(transform)
        assertTrue(result.repairApplied)
        assertEquals(RotationRepairDecision.NO_REPAIR_NEEDED, RotationDriftAssessor().assess(result.repairedTransform.m).decision)
        assertEquals(0.4, result.repairedTransform.m[0][3], 0.0)
        assertEquals(-0.2, result.repairedTransform.m[1][3], 0.0)
        assertEquals(0.7, result.repairedTransform.m[2][3], 0.0)
    }
}
