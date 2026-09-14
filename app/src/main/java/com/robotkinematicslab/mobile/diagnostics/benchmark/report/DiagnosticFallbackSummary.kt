package com.robotkinematicslab.mobile.diagnostics.benchmark.report

data class DiagnosticFallbackSummary(
    val sequentialRunCount: Int,
    val acceptedRunCount: Int,
    val rejectedRunCount: Int,
    val rejectedWithNearLimitCount: Int,
    val rejectedAtFullJointLimitPressureCount: Int,
    val rejectedWithMaxIterationsCount: Int,
    val rejectedWithNoConvergenceCount: Int,
    val averageRejectedFinalError: Double,
    val averageRejectedJointLimitPressureRatio: Double,
    val fallbackPressureLabel: String
)
