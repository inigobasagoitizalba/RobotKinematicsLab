package com.robotkinematicslab.mobile.domain.result

import com.robotkinematicslab.mobile.domain.config.IKConfig

data class SolverMetadata(
    val solverName: String,
    val maxIterations: Int,
    val tolerance: Double,
    val damping: Double,
    val maxStep: Double
) {
    companion object {
        fun fromIKConfig(config: IKConfig): SolverMetadata {
            return SolverMetadata(
                solverName = "InverseKinematicsSolver",
                maxIterations = config.maxIterations,
                tolerance = config.tolerance,
                damping = config.damping,
                maxStep = config.maxStep
            )
        }
    }
}
