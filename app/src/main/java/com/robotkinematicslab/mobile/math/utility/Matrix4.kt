package com.robotkinematicslab.mobile.math.utility

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.logging.AppLog
import kotlin.math.cos
import kotlin.math.sin

data class Matrix4(
    val m: Array<DoubleArray>
) {

    companion object {
        private const val TAG = "Matrix4"

        fun identity(): Matrix4 {
            AppLog.d(TAG) { "🧱 identity() called" }

            val matrix = Matrix4(
                arrayOf(
                    doubleArrayOf(1.0, 0.0, 0.0, 0.0),
                    doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                    doubleArrayOf(0.0, 0.0, 1.0, 0.0),
                    doubleArrayOf(0.0, 0.0, 0.0, 1.0)
                )
            )

            AppLog.d(TAG) { "✅ identity() finished | matrix=${matrix.prettyString()}" }

            return matrix
        }

        fun fromDH(parameter: DHParameter): Matrix4 {
            AppLog.d(TAG) {
                "🧩 fromDH() called | parameter={theta=${parameter.theta}, d=${parameter.d}, a=${parameter.a}, alpha=${parameter.alpha}}"
            }

            val theta = parameter.theta
            val d = parameter.d
            val a = parameter.a
            val alpha = parameter.alpha

            val ct = cos(theta)
            val st = sin(theta)
            val ca = cos(alpha)
            val sa = sin(alpha)

            AppLog.d(TAG) {
                "🧮 DH trig computed | ct=$ct, st=$st, ca=$ca, sa=$sa"
            }

            val matrix = Matrix4(
                arrayOf(
                    doubleArrayOf(ct, -st * ca, st * sa, a * ct),
                    doubleArrayOf(st, ct * ca, -ct * sa, a * st),
                    doubleArrayOf(0.0, sa, ca, d),
                    doubleArrayOf(0.0, 0.0, 0.0, 1.0)
                )
            )

            AppLog.d(TAG) {
                "✅ fromDH() finished | translation=${matrix.translation()}, matrix=${matrix.prettyString()}"
            }

            return matrix
        }
    }

    operator fun times(other: Matrix4): Matrix4 {
        AppLog.d(TAG) {
            "✖️ Matrix multiplication started | left=${prettyString()}, right=${other.prettyString()}"
        }

        val result = Array(4) { DoubleArray(4) }

        for (i in 0 until 4) {
            for (j in 0 until 4) {
                for (k in 0 until 4) {
                    val contribution = m[i][k] * other.m[k][j]
                    result[i][j] += contribution

                    AppLog.d(TAG) {
                        "➕ Multiply-accumulate | cell=[$i][$j], k=$k, left=${m[i][k]}, right=${other.m[k][j]}, contribution=$contribution, partial=${result[i][j]}"
                    }
                }
            }
        }

        val output = Matrix4(result)

        AppLog.d(TAG) {
            "✅ Matrix multiplication finished | result=${output.prettyString()}, translation=${output.translation()}"
        }

        return output
    }

    fun transform(v: Vec3): Vec3 {
        AppLog.d(TAG) {
            "🔄 transform() called | vector=(${v.x}, ${v.y}, ${v.z}), matrix=${prettyString()}"
        }

        val x = m[0][0] * v.x + m[0][1] * v.y + m[0][2] * v.z + m[0][3]
        val y = m[1][0] * v.x + m[1][1] * v.y + m[1][2] * v.z + m[1][3]
        val z = m[2][0] * v.x + m[2][1] * v.y + m[2][2] * v.z + m[2][3]

        AppLog.d(TAG) {
            "🧮 transform() components computed | x=$x, y=$y, z=$z"
        }

        val transformed = Vec3(x, y, z)

        AppLog.d(TAG) {
            "✅ transform() finished | result=$transformed"
        }

        return transformed
    }

    fun translation(): Vec3 {
        val translation = Vec3(
            x = m[0][3],
            y = m[1][3],
            z = m[2][3]
        )

        AppLog.d(TAG) {
            "📍 translation() called | translation=$translation"
        }

        return translation
    }

    private fun prettyString(): String {
        return buildString {
            append("[")
            m.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) append(", ")
                append(row.joinToString(prefix = "[", postfix = "]"))
            }
            append("]")
        }
    }
}