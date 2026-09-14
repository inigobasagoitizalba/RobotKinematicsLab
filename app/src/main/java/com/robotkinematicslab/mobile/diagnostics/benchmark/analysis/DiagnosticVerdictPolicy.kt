package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.planning.DiagnosticBenchmarkPlan
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticVerdict

data class DiagnosticReliabilityThresholds(
    val minimumOracleReachableRuns: Int = 10,
    val minimumSequentialReachableRuns: Int = 30,
    val minimumSequentialUnreachableRuns: Int = 30,
    val minimumOracleReachableAcceptanceRate: Double = 0.95,
    val minimumSequentialReachableAcceptanceRate: Double = 0.80
)

/** Explicit, rate-based policy that converts benchmark evidence into a scientific verdict. */
class DiagnosticVerdictPolicy(
    private val thresholds: DiagnosticReliabilityThresholds = DiagnosticReliabilityThresholds()
) {

    fun evaluate(
        benchmarkPlan: DiagnosticBenchmarkPlan,
        summary: DiagnosticSummary
    ): DiagnosticVerdict {
        if (benchmarkPlan.isExperimental) {
            return DiagnosticVerdict.EXPERIMENTAL_RESULT_ONLY
        }

        val oracleAcceptanceRate = summary.oracleReachableAccepted.rateOf(summary.oracleReachableRuns)
        val sequentialReachableAcceptanceRate =
            summary.sequentialReachableAccepted.rateOf(summary.sequentialReachableRuns)

        if (
            oracleAcceptanceRate.isFinite() &&
            oracleAcceptanceRate < thresholds.minimumOracleReachableAcceptanceRate
        ) {
            return DiagnosticVerdict.FAIL
        }

        if (summary.sequentialUnreachableAccepted > 0) {
            return DiagnosticVerdict.FAIL
        }

        if (
            sequentialReachableAcceptanceRate.isFinite() &&
            sequentialReachableAcceptanceRate < thresholds.minimumSequentialReachableAcceptanceRate
        ) {
            return DiagnosticVerdict.FAIL
        }

        if (
            summary.oracleReachableRuns < thresholds.minimumOracleReachableRuns ||
            summary.sequentialReachableRuns < thresholds.minimumSequentialReachableRuns ||
            summary.sequentialUnreachableRuns < thresholds.minimumSequentialUnreachableRuns ||
            summary.sequentialReachableRejected > 0 ||
            summary.runsWithNearJointLimit > 0 ||
            summary.invalidNumericalCount > 0
        ) {
            return DiagnosticVerdict.PASS_WITH_WARNINGS
        }

        return DiagnosticVerdict.PASS
    }

    private fun Int.rateOf(total: Int): Double {
        return if (total > 0) toDouble() / total.toDouble() else Double.NaN
    }
}
