package com.robotkinematicslab.mobile.validation.input

import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Vec3

class TargetPositionValidator {

    companion object {
        private const val TAG = "TargetPositionValidator"
    }

    fun isValid(target: Vec3): Boolean {
        AppLog.d(TAG) {
            "🔍 isValid called | target=(${target.x}, ${target.y}, ${target.z})"
        }

        val xFinite = target.x.isFinite()
        val yFinite = target.y.isFinite()
        val zFinite = target.z.isFinite()

        AppLog.d(TAG) {
            "🧪 Target component finiteness checked | xFinite=$xFinite, yFinite=$yFinite, zFinite=$zFinite"
        }

        val isValid = xFinite &&
                yFinite &&
                zFinite

        if (isValid) {
            AppLog.d(TAG) {
                "✅ isValid finished | result=true"
            }
        } else {
            AppLog.w(TAG) {
                "⚠️ isValid finished | result=false, target=(${target.x}, ${target.y}, ${target.z})"
            }
        }

        return isValid
    }
}