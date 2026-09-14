package com.robotkinematicslab.mobile.math.utility

import com.robotkinematicslab.mobile.logging.AppLog
import kotlin.math.PI

class AngleNormalizer {

    companion object {
        private const val TAG = "AngleNormalizer"
    }

    fun normalizeRadians(angle: Double): Double {
        AppLog.d(TAG) {
            "🔍 normalizeRadians called | inputAngle=$angle"
        }

        if (!angle.isFinite()) {
            AppLog.w(TAG) {
                "⚠️ Non-finite angle received, returning input unchanged | inputAngle=$angle"
            }
            return angle
        }

        val twoPi = 2.0 * PI

        AppLog.d(TAG) {
            "🧮 Normalization constants prepared | pi=$PI, twoPi=$twoPi"
        }

        var result = angle % twoPi
        if (result <= -PI) result += twoPi
        if (result > PI) result -= twoPi

        AppLog.d(TAG) {
            "✅ normalizeRadians finished | inputAngle=$angle, normalizedAngle=$result, algorithm=constant_time_modulo"
        }

        return result
    }
}
