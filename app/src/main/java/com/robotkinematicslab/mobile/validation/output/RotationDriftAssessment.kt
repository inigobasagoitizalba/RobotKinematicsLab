package com.robotkinematicslab.mobile.validation.output

import com.robotkinematicslab.mobile.logging.AppLog
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class RotationRepairDecision {
    NO_REPAIR_NEEDED,
    REPAIR_ALLOWED,
    REJECT
}

data class RotationDriftAssessment(
    val decision: RotationRepairDecision,
    val maxNormError: Double,
    val maxOrthogonalityError: Double,
    val determinantError: Double
)

class RotationDriftAssessor(
    private val cleanNormTolerance: Double = 1e-3,
    private val cleanOrthogonalityTolerance: Double = 1e-3,
    private val cleanDeterminantTolerance: Double = 1e-3,
    private val repairableNormTolerance: Double = 5e-2,
    private val repairableOrthogonalityTolerance: Double = 5e-2,
    private val repairableDeterminantTolerance: Double = 5e-2
) {

    init {
        requireTolerancePair("norm", cleanNormTolerance, repairableNormTolerance)
        requireTolerancePair(
            "orthogonality",
            cleanOrthogonalityTolerance,
            repairableOrthogonalityTolerance
        )
        requireTolerancePair(
            "determinant",
            cleanDeterminantTolerance,
            repairableDeterminantTolerance
        )
    }

    companion object {
        private const val TAG = "RotationDriftAssessor"

        private fun requireTolerancePair(name: String, clean: Double, repairable: Double) {
            require(clean.isFinite() && clean >= 0.0) {
                "Clean $name tolerance must be finite and non-negative."
            }
            require(repairable.isFinite() && repairable >= clean) {
                "Repairable $name tolerance must be finite and at least the clean tolerance."
            }
        }
    }

    fun assess(matrix: Array<DoubleArray>): RotationDriftAssessment {
        AppLog.d(TAG) {
            "🚀 assess started"
        }

        require(matrix.size >= 3 && matrix[0].size >= 3 && matrix[1].size >= 3 && matrix[2].size >= 3) {
            "RotationDriftAssessor requires at least a 3x3 matrix block."
        }

        val r00 = matrix[0][0]
        val r01 = matrix[0][1]
        val r02 = matrix[0][2]

        val r10 = matrix[1][0]
        val r11 = matrix[1][1]
        val r12 = matrix[1][2]

        val r20 = matrix[2][0]
        val r21 = matrix[2][1]
        val r22 = matrix[2][2]

        val c0Norm = hypot(hypot(r00, r10), r20)
        val c1Norm = hypot(hypot(r01, r11), r21)
        val c2Norm = hypot(hypot(r02, r12), r22)

        val normError0 = abs(c0Norm - 1.0)
        val normError1 = abs(c1Norm - 1.0)
        val normError2 = abs(c2Norm - 1.0)
        val maxNormError = max(normError0, max(normError1, normError2))

        val c01Dot = r00 * r01 + r10 * r11 + r20 * r21
        val c02Dot = r00 * r02 + r10 * r12 + r20 * r22
        val c12Dot = r01 * r02 + r11 * r12 + r21 * r22
        val maxOrthogonalityError = max(abs(c01Dot), max(abs(c02Dot), abs(c12Dot)))

        val determinant =
            r00 * (r11 * r22 - r12 * r21) -
                    r01 * (r10 * r22 - r12 * r20) +
                    r02 * (r10 * r21 - r11 * r20)

        val determinantError = abs(determinant - 1.0)

        val isClean =
            maxNormError <= cleanNormTolerance &&
                    maxOrthogonalityError <= cleanOrthogonalityTolerance &&
                    determinantError <= cleanDeterminantTolerance

        val isRepairable =
            maxNormError <= repairableNormTolerance &&
                    maxOrthogonalityError <= repairableOrthogonalityTolerance &&
                    determinantError <= repairableDeterminantTolerance

        val decision = when {
            isClean -> RotationRepairDecision.NO_REPAIR_NEEDED
            isRepairable -> RotationRepairDecision.REPAIR_ALLOWED
            else -> RotationRepairDecision.REJECT
        }

        AppLog.d(TAG) {
            "✅ assess finished | decision=$decision, maxNormError=$maxNormError, maxOrthogonalityError=$maxOrthogonalityError, determinantError=$determinantError"
        }

        return RotationDriftAssessment(
            decision = decision,
            maxNormError = maxNormError,
            maxOrthogonalityError = maxOrthogonalityError,
            determinantError = determinantError
        )
    }
}
