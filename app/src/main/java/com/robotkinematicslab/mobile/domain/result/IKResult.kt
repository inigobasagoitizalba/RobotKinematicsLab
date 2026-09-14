package com.robotkinematicslab.mobile.domain.result

import com.robotkinematicslab.mobile.domain.RobotState

enum class IKStatus {
    SUCCESS,
    SUCCESS_WITH_WARNING,
    INVALID_INPUT,
    NO_CONVERGENCE,
    MAX_ITERATIONS_REACHED,
    NUMERICAL_FAILURE
}

data class IKResult(
    val state: RobotState,
    val status: IKStatus,
    val converged: Boolean,
    val iterations: Int,
    val finalError: Double,
    val detailCode: IKDetailCode = IKDetailCode.NONE,
    val metadata: SolverMetadata? = null,
    val diagnostics: IKDiagnostics = IKDiagnostics()
)
