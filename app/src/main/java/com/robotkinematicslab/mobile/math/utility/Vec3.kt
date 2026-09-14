package com.robotkinematicslab.mobile.math.utility

import kotlin.math.hypot

data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
) {

    operator fun plus(other: Vec3): Vec3 {
        return Vec3(
            x + other.x,
            y + other.y,
            z + other.z
        )
    }

    operator fun minus(other: Vec3): Vec3 {
        return Vec3(
            x - other.x,
            y - other.y,
            z - other.z
        )
    }

    operator fun times(scalar: Double): Vec3 {
        return Vec3(
            x * scalar,
            y * scalar,
            z * scalar
        )
    }

    operator fun div(scalar: Double): Vec3 {
        require(scalar != 0.0) { "Cannot divide by zero." }
        return Vec3(
            x / scalar,
            y / scalar,
            z / scalar
        )
    }

    fun dot(other: Vec3): Double {
        return x * other.x + y * other.y + z * other.z
    }

    fun cross(other: Vec3): Vec3 {
        return Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    fun norm(): Double {
        return hypot(hypot(x, y), z)
    }

    fun normalized(): Vec3 {
        val n = norm()
        require(n > 0.0) { "Cannot normalize zero vector." }
        return this / n
    }

    fun isFinite(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
    }

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}
