package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.logging.AppLog
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

enum class SingularityLevel {
    NORMAL,
    WARNING,
    CRITICAL
}

data class NearSingularityReport(
    val sigmaMin: Double,
    val sigmaMax: Double,
    val conditionNumber: Double,
    val level: SingularityLevel,
    val isNearSingular: Boolean,
    val expectedRank: Int,
    val observedRank: Int,
    val taskSpaceUnderactuated: Boolean
)

class NearSingularityAnalyzer(
    private val sigmaMinWarningThreshold: Double = 1e-3,
    private val sigmaMinCriticalThreshold: Double = 1e-5,
    private val conditionWarningThreshold: Double = 1e3,
    private val conditionCriticalThreshold: Double = 1e5
) {

    init {
        require(sigmaMinWarningThreshold.isFinite() && sigmaMinWarningThreshold > 0.0)
        require(sigmaMinCriticalThreshold.isFinite() && sigmaMinCriticalThreshold > 0.0)
        require(sigmaMinCriticalThreshold <= sigmaMinWarningThreshold)
        require(conditionWarningThreshold.isFinite() && conditionWarningThreshold > 1.0)
        require(conditionCriticalThreshold.isFinite() && conditionCriticalThreshold >= conditionWarningThreshold)
    }

    companion object {
        private const val TAG = "NearSingularityAnalyzer"
        private const val JACOBI_EPS = 1e-12
        private const val JACOBI_MAX_SWEEPS = 32
        private const val SAFE_EIGEN_FLOOR = 0.0
    }

    fun analyze(
        jacobianX: List<Double>,
        jacobianY: List<Double>,
        jacobianZ: List<Double>,
        columnScales: List<Double> = List(jacobianX.size) { 1.0 }
    ): NearSingularityReport {

        require(jacobianX.size == jacobianY.size && jacobianY.size == jacobianZ.size) {
            "Jacobian row lists must have the same size."
        }
        require(jacobianX.isNotEmpty()) {
            "Jacobian must contain at least one column."
        }
        require(
            jacobianX.all(Double::isFinite) &&
                jacobianY.all(Double::isFinite) &&
                jacobianZ.all(Double::isFinite)
        ) {
            "Jacobian entries must be finite."
        }
        require(columnScales.size == jacobianX.size && columnScales.all { it.isFinite() && it > 0.0 }) {
            "Jacobian column scales must be finite, positive and match the column count."
        }

        AppLog.d(TAG) {
            "🚀 analyze started | columns=${jacobianX.size}"
        }

        val jjT = buildJjt(
            jacobianX = jacobianX,
            jacobianY = jacobianY,
            jacobianZ = jacobianZ,
            columnScales = columnScales
        )
        require(jjT.all { row -> row.all(Double::isFinite) }) {
            "Scaled Jacobian energy overflowed; singularity classification is not trustworthy."
        }

        AppLog.d(TAG) {
            "🧮 JJT built | " +
                    "[[${jjT[0][0]}, ${jjT[0][1]}, ${jjT[0][2]}], " +
                    "[${jjT[1][0]}, ${jjT[1][1]}, ${jjT[1][2]}], " +
                    "[${jjT[2][0]}, ${jjT[2][1]}, ${jjT[2][2]}]]"
        }

        val singularValues = eigenvaluesSymmetric3x3(jjT)
            .map { max(SAFE_EIGEN_FLOOR, it) }
            .map(::sqrt)
            .sortedDescending()

        val expectedRank = jacobianX.size.coerceIn(1, 3)
        val sigmaMax = singularValues.first()
        val sigmaMin = singularValues[expectedRank - 1]
        val rankTolerance = max(JACOBI_EPS, sigmaMax * 1e-9)
        val observedRank = singularValues.count { it > rankTolerance }

        val conditionNumber = if (sigmaMin <= JACOBI_EPS) {
            Double.POSITIVE_INFINITY
        } else {
            sigmaMax / sigmaMin
        }

        val level = when {
            sigmaMin <= sigmaMinCriticalThreshold ||
                    conditionNumber >= conditionCriticalThreshold -> SingularityLevel.CRITICAL

            sigmaMin <= sigmaMinWarningThreshold ||
                    conditionNumber >= conditionWarningThreshold -> SingularityLevel.WARNING

            else -> SingularityLevel.NORMAL
        }

        val report = NearSingularityReport(
            sigmaMin = sigmaMin,
            sigmaMax = sigmaMax,
            conditionNumber = conditionNumber,
            level = level,
            isNearSingular = level != SingularityLevel.NORMAL,
            expectedRank = expectedRank,
            observedRank = observedRank,
            taskSpaceUnderactuated = expectedRank < 3
        )

        AppLog.d(TAG) {
            "✅ analyze finished | sigmaMin=$sigmaMin, sigmaMax=$sigmaMax, " +
                    "conditionNumber=$conditionNumber, expectedRank=$expectedRank, observedRank=$observedRank, " +
                    "taskSpaceUnderactuated=${expectedRank < 3}, level=$level"
        }

        return report
    }

    private fun buildJjt(
        jacobianX: List<Double>,
        jacobianY: List<Double>,
        jacobianZ: List<Double>,
        columnScales: List<Double>
    ): Array<DoubleArray> {

        var a11 = 0.0
        var a12 = 0.0
        var a13 = 0.0
        var a22 = 0.0
        var a23 = 0.0
        var a33 = 0.0
        var index = 0

        while (index < jacobianX.size) {
            val scale = columnScales[index]
            val x = jacobianX[index] * scale
            val y = jacobianY[index] * scale
            val z = jacobianZ[index] * scale

            a11 += x * x
            a12 += x * y
            a13 += x * z
            a22 += y * y
            a23 += y * z
            a33 += z * z

            index++
        }

        return arrayOf(
            doubleArrayOf(a11, a12, a13),
            doubleArrayOf(a12, a22, a23),
            doubleArrayOf(a13, a23, a33)
        )
    }

    /**
     * Jacobi diagonalization for a real symmetric 3x3 matrix.
     * Returns the diagonal values (eigenvalue estimates).
     */
    private fun eigenvaluesSymmetric3x3(input: Array<DoubleArray>): List<Double> {
        val a = Array(3) { row -> input[row].clone() }

        repeat(JACOBI_MAX_SWEEPS) {
            val (p, q, offDiagAbs) = largestOffDiagonal(a)

            if (offDiagAbs < JACOBI_EPS) {
                return listOf(a[0][0], a[1][1], a[2][2])
            }

            val app = a[p][p]
            val aqq = a[q][q]
            val apq = a[p][q]

            if (abs(apq) < JACOBI_EPS) {
                return@repeat
            }

            val tau = (aqq - app) / (2.0 * apq)
            val t = when {
                tau >= 0.0 -> 1.0 / (tau + hypot(1.0, tau))
                else -> -1.0 / (-tau + hypot(1.0, tau))
            }

            val c = 1.0 / sqrt(1.0 + t * t)
            val s = t * c

            for (k in 0..2) {
                if (k != p && k != q) {
                    val akp = a[k][p]
                    val akq = a[k][q]

                    a[k][p] = c * akp - s * akq
                    a[p][k] = a[k][p]

                    a[k][q] = s * akp + c * akq
                    a[q][k] = a[k][q]
                }
            }

            val newApp = c * c * app - 2.0 * s * c * apq + s * s * aqq
            val newAqq = s * s * app + 2.0 * s * c * apq + c * c * aqq

            a[p][p] = newApp
            a[q][q] = newAqq
            a[p][q] = 0.0
            a[q][p] = 0.0
        }

        return listOf(a[0][0], a[1][1], a[2][2])
    }

    private fun largestOffDiagonal(a: Array<DoubleArray>): Triple<Int, Int, Double> {
        var p = 0
        var q = 1
        var best = abs(a[0][1])

        val a02 = abs(a[0][2])
        if (a02 > best) {
            best = a02
            p = 0
            q = 2
        }

        val a12 = abs(a[1][2])
        if (a12 > best) {
            best = a12
            p = 1
            q = 2
        }

        return Triple(p, q, best)
    }
}
