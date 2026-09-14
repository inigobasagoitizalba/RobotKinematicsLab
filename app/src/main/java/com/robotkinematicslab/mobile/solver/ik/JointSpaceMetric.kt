package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.domain.RobotDefinition
import kotlin.math.hypot

/**
 * Dimensionless joint-space coordinates for mixed revolute/prismatic inverse kinematics.
 * A unit coordinate represents one half of a joint's valid interval.
 */
object JointSpaceMetric {

    fun coordinateScales(robot: RobotDefinition): List<Double> =
        robot.joints.map { joint ->
            ((joint.maxValue - joint.minValue) * 0.5).coerceAtLeast(MINIMUM_SCALE)
        }

    fun normalizedNorm(
        delta: List<Double>,
        coordinateScales: List<Double>
    ): Double {
        require(delta.size == coordinateScales.size)
        require(coordinateScales.all { it.isFinite() && it > 0.0 })
        var result = 0.0
        for (index in delta.indices) {
            val normalized = delta[index] / coordinateScales[index]
            result = hypot(result, normalized)
        }
        return result
    }

    private const val MINIMUM_SCALE = 1e-12
}
