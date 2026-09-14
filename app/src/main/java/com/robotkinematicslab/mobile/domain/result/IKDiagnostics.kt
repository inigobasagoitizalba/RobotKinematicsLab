package com.robotkinematicslab.mobile.domain.result

data class IKDiagnostics(
    val seedConditionNumber: Double = Double.NaN,
    val backtrackingRetryCount: Int = 0,
    val solveDurationNanos: Long = 0L
)
