package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Deliberately unvalidated position-only DH chain used exclusively by the numerical-safety
 * ablation. It must never be used by production kinematics, datasets, or model inference.
 */
internal object RawForwardKinematics {

    fun position(robot: RobotDefinition, jointValues: List<Double>): Vec3 {
        var m00 = 1.0
        var m01 = 0.0
        var m02 = 0.0
        var m03 = 0.0
        var m10 = 0.0
        var m11 = 1.0
        var m12 = 0.0
        var m13 = 0.0
        var m20 = 0.0
        var m21 = 0.0
        var m22 = 1.0
        var m23 = 0.0

        for (index in robot.dhParameters.indices) {
            val dh = robot.dhParameters[index]
            val joint = robot.joints[index]
            val value = jointValues[index]
            val theta = if (joint.type == JointType.REVOLUTE) value else dh.theta
            val d = if (joint.type == JointType.PRISMATIC) value else dh.d
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)
            val cosAlpha = cos(dh.alpha)
            val sinAlpha = sin(dh.alpha)

            val t00 = cosTheta
            val t01 = -sinTheta * cosAlpha
            val t02 = sinTheta * sinAlpha
            val t03 = dh.a * cosTheta
            val t10 = sinTheta
            val t11 = cosTheta * cosAlpha
            val t12 = -cosTheta * sinAlpha
            val t13 = dh.a * sinTheta
            val t21 = sinAlpha
            val t22 = cosAlpha
            val t23 = d

            val n00 = m00 * t00 + m01 * t10
            val n01 = m00 * t01 + m01 * t11 + m02 * t21
            val n02 = m00 * t02 + m01 * t12 + m02 * t22
            val n03 = m00 * t03 + m01 * t13 + m02 * t23 + m03
            val n10 = m10 * t00 + m11 * t10
            val n11 = m10 * t01 + m11 * t11 + m12 * t21
            val n12 = m10 * t02 + m11 * t12 + m12 * t22
            val n13 = m10 * t03 + m11 * t13 + m12 * t23 + m13
            val n20 = m20 * t00 + m21 * t10
            val n21 = m20 * t01 + m21 * t11 + m22 * t21
            val n22 = m20 * t02 + m21 * t12 + m22 * t22
            val n23 = m20 * t03 + m21 * t13 + m22 * t23 + m23

            m00 = n00
            m01 = n01
            m02 = n02
            m03 = n03
            m10 = n10
            m11 = n11
            m12 = n12
            m13 = n13
            m20 = n20
            m21 = n21
            m22 = n22
            m23 = n23
        }

        return Vec3(m03, m13, m23)
    }
}
