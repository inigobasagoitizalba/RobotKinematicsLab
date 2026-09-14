package com.robotkinematicslab.mobile.validation.output

import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Matrix4
import kotlin.math.abs
import kotlin.math.max

data class RotationRepairResult(
    val repairedTransform: Matrix4,
    val repairApplied: Boolean
)

class RotationRepairer {

    companion object {
        private const val TAG = "RotationRepairer"
        private const val DETERMINANT_EPS = 1e-15
        private const val CONVERGENCE_EPS = 1e-14
        private const val MAX_POLAR_ITERATIONS = 12
    }

    fun repair(transform: Matrix4): RotationRepairResult {
        AppLog.d(TAG) { "🚀 repair started" }

        if (transform.m.size != 4 || transform.m.any { row -> row.size != 4 }) {
            return failedRepair(transform, "transform must be exactly 4x4")
        }

        val m = Array(4) { row -> transform.m[row].clone() }

        var rotation = Array(3) { row -> DoubleArray(3) { col -> m[row][col] } }

        for (iteration in 0 until MAX_POLAR_ITERATIONS) {
            val inverse = inverse3x3(rotation)
                ?: return failedRepair(transform, "rotation block is singular")
            val next = Array(3) { DoubleArray(3) }
            var maxDelta = 0.0

            for (row in 0..2) {
                for (col in 0..2) {
                    val value = 0.5 * (rotation[row][col] + inverse[col][row])
                    if (!value.isFinite()) {
                        return failedRepair(transform, "polar iteration became non-finite")
                    }
                    next[row][col] = value
                    maxDelta = max(maxDelta, abs(value - rotation[row][col]))
                }
            }

            rotation = next
            if (maxDelta <= CONVERGENCE_EPS) break
        }

        val determinant = determinant3x3(rotation)
        if (!determinant.isFinite() || determinant <= 0.0) {
            return failedRepair(transform, "polar factor is not a proper rotation")
        }

        for (row in 0..2) {
            for (col in 0..2) {
                m[row][col] = rotation[row][col]
            }
        }

        val repaired = Matrix4(m)

        AppLog.d(TAG) { "✅ repair finished | repairApplied=true" }

        return RotationRepairResult(
            repairedTransform = repaired,
            repairApplied = true
        )
    }

    private fun failedRepair(transform: Matrix4, reason: String): RotationRepairResult {
        AppLog.w(TAG) { "⚠️ repair failed | reason=$reason" }
        return RotationRepairResult(transform, false)
    }

    private fun inverse3x3(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val determinant = determinant3x3(matrix)
        val scale = matrix.maxOf { row -> row.maxOf(::abs) }.coerceAtLeast(1.0)
        if (!determinant.isFinite() || abs(determinant) <= DETERMINANT_EPS * scale * scale * scale) {
            return null
        }

        val a = matrix
        return arrayOf(
            doubleArrayOf(
                (a[1][1] * a[2][2] - a[1][2] * a[2][1]) / determinant,
                (a[0][2] * a[2][1] - a[0][1] * a[2][2]) / determinant,
                (a[0][1] * a[1][2] - a[0][2] * a[1][1]) / determinant
            ),
            doubleArrayOf(
                (a[1][2] * a[2][0] - a[1][0] * a[2][2]) / determinant,
                (a[0][0] * a[2][2] - a[0][2] * a[2][0]) / determinant,
                (a[0][2] * a[1][0] - a[0][0] * a[1][2]) / determinant
            ),
            doubleArrayOf(
                (a[1][0] * a[2][1] - a[1][1] * a[2][0]) / determinant,
                (a[0][1] * a[2][0] - a[0][0] * a[2][1]) / determinant,
                (a[0][0] * a[1][1] - a[0][1] * a[1][0]) / determinant
            )
        )
    }

    private fun determinant3x3(a: Array<DoubleArray>): Double {
        return a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) -
                a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) +
                a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
    }
}
