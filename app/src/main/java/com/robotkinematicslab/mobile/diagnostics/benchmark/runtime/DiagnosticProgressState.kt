package com.robotkinematicslab.mobile.diagnostics.benchmark.runtime

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

enum class DiagnosticProgressPhase {
    IDLE,
    PLANNING,
    AUDITING_TOPOLOGY,
    GENERATING_TARGETS,
    ORACLE_CHECKS,
    SEQUENTIAL_RUNS,
    AGGREGATING,
    COMPLETED,
    FAILED
}

data class DiagnosticProgressState(
    val isRunning: Boolean = false,
    val phase: DiagnosticProgressPhase = DiagnosticProgressPhase.IDLE,
    val completedRuns: Int = 0,
    val totalRuns: Int = 0,
    val currentSeed: Int? = null,
    val currentLinkCount: Int? = null,
    val currentJointMode: DiagnosticJointMode? = null,
    val runsPerSecond: Double = 0.0,
    val estimatedSecondsRemaining: Double = Double.NaN,
    val elapsedSeconds: Double = 0.0,
    val message: String = "Idle",
    val telemetry: DiagnosticSystemTelemetry = DiagnosticSystemTelemetry()
) {
    val remainingRuns: Int
        get() = (totalRuns - completedRuns).coerceAtLeast(0)

    val percent: Double
        get() =
            if (totalRuns > 0) {
                (completedRuns.toDouble() / totalRuns.toDouble())
                    .coerceIn(0.0, 1.0) * 100.0
            } else {
                0.0
            }
}
