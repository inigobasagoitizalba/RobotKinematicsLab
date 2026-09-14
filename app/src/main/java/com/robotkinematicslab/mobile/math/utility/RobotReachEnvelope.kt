package com.robotkinematicslab.mobile.math.utility

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Conservative radial reach bounds for the project's standard-DH joint contract.
 *
 * The active standard-DH coordinate is supplied by the robot state: theta for a
 * revolute joint and d for a prismatic joint. The corresponding stored DH field
 * is therefore canonical zero, while the other three DH fields are fixed.
 *
 * For one DH row the translation norm is sqrt(a^2 + d^2). The triangle
 * inequality makes the sum of those per-row maxima a guaranteed upper bound on
 * the end-effector distance from the base. It may overestimate the true
 * workspace, but it must never underestimate it for a valid robot.
 */
object RobotReachEnvelope {

    fun conservativeRadialUpperBound(robot: RobotDefinition): Double {
        if (robot.dhParameters.size != robot.joints.size || robot.joints.isEmpty()) {
            return Double.NaN
        }

        var upperBound = 0.0

        robot.joints.indices.forEach { index ->
            val joint = robot.joints[index]
            val dh = robot.dhParameters[index]
            val maximumAbsD =
                when (joint.type) {
                    JointType.REVOLUTE -> abs(dh.d)
                    JointType.PRISMATIC -> max(abs(joint.minValue), abs(joint.maxValue))
                }

            val linkBound = hypot(dh.a, maximumAbsD)
            if (!linkBound.isFinite()) {
                return Double.NaN
            }

            upperBound += linkBound
        }

        return upperBound.takeIf { it.isFinite() } ?: Double.NaN
    }

    fun perLinkRadialUpperBounds(robot: RobotDefinition): List<Double> {
        if (robot.dhParameters.size != robot.joints.size) {
            return emptyList()
        }

        return robot.joints.indices.map { index ->
            val joint = robot.joints[index]
            val dh = robot.dhParameters[index]
            val maximumAbsD =
                when (joint.type) {
                    JointType.REVOLUTE -> abs(dh.d)
                    JointType.PRISMATIC -> max(abs(joint.minValue), abs(joint.maxValue))
                }

            hypot(dh.a, maximumAbsD)
        }
    }
}
