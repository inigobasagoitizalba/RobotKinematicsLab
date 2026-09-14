package com.robotkinematicslab.mobile.validation

import com.robotkinematicslab.mobile.validation.output.RotationDriftAssessor
import com.robotkinematicslab.mobile.validation.output.RotationRepairDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RotationDriftAssessorConfigurationTest {

    @Test
    fun rejectsNonFiniteNegativeAndInvertedTolerancePolicies() {
        assertThrows(IllegalArgumentException::class.java) {
            RotationDriftAssessor(cleanNormTolerance = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RotationDriftAssessor(cleanOrthogonalityTolerance = -1e-3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RotationDriftAssessor(
                cleanDeterminantTolerance = 0.1,
                repairableDeterminantTolerance = 0.01
            )
        }
    }

    @Test
    fun hugeFiniteRotationBlockIsRejectedWithoutNormOverflow() {
        val huge = 1e308
        val assessment =
            RotationDriftAssessor().assess(
                arrayOf(
                    doubleArrayOf(huge, 0.0, 0.0),
                    doubleArrayOf(0.0, huge, 0.0),
                    doubleArrayOf(0.0, 0.0, huge)
                )
            )

        assertEquals(RotationRepairDecision.REJECT, assessment.decision)
    }
}
