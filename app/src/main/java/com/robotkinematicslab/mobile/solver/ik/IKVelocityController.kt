package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.domain.JointDefinition

class IKVelocityController(
    private val maxVelocity: Double = 1.5,   // rad/sec
    private val dt: Double = 0.016           // ~60 FPS
) {

    fun step(
        current: List<Double>,
        target: List<Double>,
        joints: List<JointDefinition>
    ): List<Double> {

        return current.mapIndexed { i, value ->

            val error = target[i] - value

            val maxStep = maxVelocity * dt

            val step = when {
                error > maxStep -> maxStep
                error < -maxStep -> -maxStep
                else -> error
            }

            val updated = value + step

            updated.coerceIn(joints[i].minValue, joints[i].maxValue)
        }
    }
}