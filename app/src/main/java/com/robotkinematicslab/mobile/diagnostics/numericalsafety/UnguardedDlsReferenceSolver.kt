package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.ik.JointSpaceMetric
import kotlin.math.hypot

internal data class UnguardedDlsResult(
    val state: RobotState,
    val converged: Boolean,
    val iterations: Int,
    val finalError: Double,
    val nonFiniteEncountered: Boolean
)

/**
 * Scientific negative control. It retains the same DH convention, finite-difference scale,
 * fixed DLS damping and mixed-joint coordinate scaling as the production solver, while
 * deliberately omitting validation, adaptive damping, limit clipping, step limiting,
 * backtracking, stagnation detection and final certification.
 */
internal class UnguardedDlsReferenceSolver(
    private val config: IKConfig
) {
    fun solve(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): UnguardedDlsResult {
        val joints = initialState.jointValues.toMutableList()
        val scales = JointSpaceMetric.coordinateScales(robot)
        var finalError = Double.POSITIVE_INFINITY
        var nonFiniteEncountered = false

        for (iteration in 1..config.maxIterations) {
            val current = RawForwardKinematics.position(robot, joints)
            val errorX = target.x - current.x
            val errorY = target.y - current.y
            val errorZ = target.z - current.z
            finalError = hypot(hypot(errorX, errorY), errorZ)
            if (!current.isFinite() || !finalError.isFinite()) nonFiniteEncountered = true
            if (finalError <= config.tolerance) {
                return UnguardedDlsResult(
                    state = RobotState(joints.toList()),
                    converged = true,
                    iterations = iteration,
                    finalError = finalError,
                    nonFiniteEncountered = nonFiniteEncountered
                )
            }

            val jacobianX = DoubleArray(joints.size)
            val jacobianY = DoubleArray(joints.size)
            val jacobianZ = DoubleArray(joints.size)
            for (jointIndex in joints.indices) {
                val original = joints[jointIndex]
                joints[jointIndex] = original + JACOBIAN_DELTA
                val perturbed = RawForwardKinematics.position(robot, joints)
                joints[jointIndex] = original
                jacobianX[jointIndex] = (perturbed.x - current.x) / JACOBIAN_DELTA
                jacobianY[jointIndex] = (perturbed.y - current.y) / JACOBIAN_DELTA
                jacobianZ[jointIndex] = (perturbed.z - current.z) / JACOBIAN_DELTA
            }

            val lambdaSquared = config.damping * config.damping
            var a11 = lambdaSquared
            var a12 = 0.0
            var a13 = 0.0
            var a22 = lambdaSquared
            var a23 = 0.0
            var a33 = lambdaSquared
            for (jointIndex in joints.indices) {
                val x = jacobianX[jointIndex] * scales[jointIndex]
                val y = jacobianY[jointIndex] * scales[jointIndex]
                val z = jacobianZ[jointIndex] * scales[jointIndex]
                a11 += x * x
                a12 += x * y
                a13 += x * z
                a22 += y * y
                a23 += y * z
                a33 += z * z
            }

            val inverse = inverseSymmetric3x3(a11, a12, a13, a22, a23, a33)
            val y1 = inverse[0] * errorX + inverse[1] * errorY + inverse[2] * errorZ
            val y2 = inverse[1] * errorX + inverse[3] * errorY + inverse[4] * errorZ
            val y3 = inverse[2] * errorX + inverse[4] * errorY + inverse[5] * errorZ
            for (jointIndex in joints.indices) {
                val scale = scales[jointIndex]
                val delta =
                    scale * (
                        jacobianX[jointIndex] * scale * y1 +
                            jacobianY[jointIndex] * scale * y2 +
                            jacobianZ[jointIndex] * scale * y3
                        )
                joints[jointIndex] += delta
                if (!delta.isFinite() || !joints[jointIndex].isFinite()) nonFiniteEncountered = true
            }
        }

        return UnguardedDlsResult(
            state = RobotState(joints.toList()),
            converged = false,
            iterations = config.maxIterations,
            finalError = finalError,
            nonFiniteEncountered = nonFiniteEncountered
        )
    }

    private fun inverseSymmetric3x3(
        a11: Double,
        a12: Double,
        a13: Double,
        a22: Double,
        a23: Double,
        a33: Double
    ): DoubleArray {
        val c11 = a22 * a33 - a23 * a23
        val c12 = a13 * a23 - a12 * a33
        val c13 = a12 * a23 - a13 * a22
        val c22 = a11 * a33 - a13 * a13
        val c23 = a12 * a13 - a11 * a23
        val c33 = a11 * a22 - a12 * a12
        val determinant = a11 * c11 + a12 * c12 + a13 * c13
        return doubleArrayOf(
            c11 / determinant,
            c12 / determinant,
            c13 / determinant,
            c22 / determinant,
            c23 / determinant,
            c33 / determinant
        )
    }

    companion object {
        private const val JACOBIAN_DELTA = 1e-6
    }
}
