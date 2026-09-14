package com.robotkinematicslab.mobile.math.utility

object Workspace {

    fun clampToReach(
        target: Vec3,
        maxReach: Double
    ): Vec3 {

        require(maxReach.isFinite() && maxReach >= 0.0) {
            "Maximum reach must be finite and non-negative."
        }

        val distance = target.norm()

        if (!distance.isFinite() || distance <= maxReach) return target
        if (distance == 0.0) return Vec3.ZERO

        val scale = maxReach / distance

        return Vec3(
            x = target.x * scale,
            y = target.y * scale,
            z = target.z * scale
        )
    }

    fun computeMaxReach(linkLengths: List<Double>): Double {
        return linkLengths.sum()
    }
}
