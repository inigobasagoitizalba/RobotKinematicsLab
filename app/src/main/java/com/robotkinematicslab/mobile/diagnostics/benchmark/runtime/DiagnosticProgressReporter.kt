package com.robotkinematicslab.mobile.diagnostics.benchmark.runtime

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

class DiagnosticProgressReporter(
    private val totalRuns: Int,
    private val onProgress: (DiagnosticProgressState) -> Unit
) {
    private val startNanoTime =
        System.nanoTime()

    private var completedRuns =
        0

    fun emit(
        phase: DiagnosticProgressPhase,
        currentSeed: Int? = null,
        currentLinkCount: Int? = null,
        currentJointMode: DiagnosticJointMode? = null,
        message: String
    ) {
        onProgress(
            buildState(
                phase = phase,
                currentSeed = currentSeed,
                currentLinkCount = currentLinkCount,
                currentJointMode = currentJointMode,
                message = message
            )
        )
    }

    fun incrementAndEmit(
        phase: DiagnosticProgressPhase,
        currentSeed: Int? = null,
        currentLinkCount: Int? = null,
        currentJointMode: DiagnosticJointMode? = null,
        message: String
    ) {
        completedRuns =
            (completedRuns + 1)
                .coerceAtMost(totalRuns.coerceAtLeast(0))

        emit(
            phase = phase,
            currentSeed = currentSeed,
            currentLinkCount = currentLinkCount,
            currentJointMode = currentJointMode,
            message = message
        )
    }

    fun complete(
        message: String = "Diagnostic benchmark completed."
    ) {
        completedRuns =
            totalRuns.coerceAtLeast(0)

        onProgress(
            buildState(
                phase = DiagnosticProgressPhase.COMPLETED,
                message = message
            ).copy(
                isRunning = false
            )
        )
    }

    fun fail(
        message: String
    ) {
        onProgress(
            buildState(
                phase = DiagnosticProgressPhase.FAILED,
                message = message
            ).copy(
                isRunning = false
            )
        )
    }

    private fun buildState(
        phase: DiagnosticProgressPhase,
        currentSeed: Int? = null,
        currentLinkCount: Int? = null,
        currentJointMode: DiagnosticJointMode? = null,
        message: String
    ): DiagnosticProgressState {
        val elapsedSeconds =
            elapsedSeconds()

        val runsPerSecond =
            if (elapsedSeconds > 0.0 && completedRuns > 0) {
                completedRuns.toDouble() / elapsedSeconds
            } else {
                0.0
            }

        val remainingRuns =
            (totalRuns - completedRuns)
                .coerceAtLeast(0)

        val etaSeconds =
            if (runsPerSecond > 0.0) {
                remainingRuns.toDouble() / runsPerSecond
            } else {
                Double.NaN
            }

        return DiagnosticProgressState(
            isRunning =
                phase != DiagnosticProgressPhase.COMPLETED &&
                        phase != DiagnosticProgressPhase.FAILED &&
                        phase != DiagnosticProgressPhase.IDLE,
            phase = phase,
            completedRuns = completedRuns,
            totalRuns = totalRuns,
            currentSeed = currentSeed,
            currentLinkCount = currentLinkCount,
            currentJointMode = currentJointMode,
            runsPerSecond = runsPerSecond,
            estimatedSecondsRemaining = etaSeconds,
            elapsedSeconds = elapsedSeconds,
            message = message
        )
    }

    private fun elapsedSeconds(): Double {
        return (System.nanoTime() - startNanoTime).toDouble() / 1_000_000_000.0
    }
}
