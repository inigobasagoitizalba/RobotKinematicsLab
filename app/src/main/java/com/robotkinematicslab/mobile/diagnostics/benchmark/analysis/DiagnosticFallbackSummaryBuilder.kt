package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticFallbackSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult
import com.robotkinematicslab.mobile.domain.result.IKStatus

class DiagnosticFallbackSummaryBuilder {

    fun build(runResults: List<DiagnosticRunResult>): DiagnosticFallbackSummary {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }
        val rejectedRuns = sequentialRuns.filterNot(DiagnosticRunResult::solverAccepted)
        val acceptedRuns = sequentialRuns.filter(DiagnosticRunResult::solverAccepted)
        val finiteRejectedErrors =
            rejectedRuns.mapNotNull { run -> run.finalError.takeIf(Double::isFinite) }
        val finiteRejectedPressureRatios =
            rejectedRuns.mapNotNull { run -> run.jointLimitPressureRatio.takeIf(Double::isFinite) }
        val rejectedWithNearLimitCount =
            rejectedRuns.count { run -> run.nearLimitJointCount > 0 }
        val rejectedAtFullPressureCount =
            rejectedRuns.count { run ->
                run.jointLimitPressureRatio.isFinite() && run.jointLimitPressureRatio >= 0.999
            }
        val rejectedWithMaxIterationsCount =
            rejectedRuns.count { run ->
                run.status == IKStatus.MAX_ITERATIONS_REACHED.name ||
                    run.detailCode.contains("MAX_ITERATIONS", ignoreCase = true)
            }
        val rejectedWithNoConvergenceCount =
            rejectedRuns.count { run ->
                run.status == IKStatus.NO_CONVERGENCE.name ||
                    run.detailCode.contains("STAGNATION", ignoreCase = true)
            }
        val nearLimitRejectedRatio =
            rejectedWithNearLimitCount.toRatio(rejectedRuns.size)
        val fullLimitRejectedRatio =
            rejectedAtFullPressureCount.toRatio(rejectedRuns.size)

        return DiagnosticFallbackSummary(
            sequentialRunCount = sequentialRuns.size,
            acceptedRunCount = acceptedRuns.size,
            rejectedRunCount = rejectedRuns.size,
            rejectedWithNearLimitCount = rejectedWithNearLimitCount,
            rejectedAtFullJointLimitPressureCount = rejectedAtFullPressureCount,
            rejectedWithMaxIterationsCount = rejectedWithMaxIterationsCount,
            rejectedWithNoConvergenceCount = rejectedWithNoConvergenceCount,
            averageRejectedFinalError = finiteRejectedErrors.averageOrZero(),
            averageRejectedJointLimitPressureRatio = finiteRejectedPressureRatios.averageOrZero(),
            fallbackPressureLabel =
                when {
                    rejectedRuns.isEmpty() -> "NONE"
                    fullLimitRejectedRatio >= 0.50 -> "HIGH_FULL_LIMIT_CLAMPING"
                    nearLimitRejectedRatio >= 0.75 -> "HIGH_JOINT_LIMIT_PRESSURE"
                    nearLimitRejectedRatio >= 0.40 -> "MEDIUM_JOINT_LIMIT_PRESSURE"
                    else -> "LOW_JOINT_LIMIT_PRESSURE"
                }
        )
    }

    private fun Int.toRatio(total: Int): Double {
        return if (total == 0) 0.0 else toDouble() / total.toDouble()
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }
}
