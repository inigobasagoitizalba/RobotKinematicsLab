package com.robotkinematicslab.mobile.domain.record

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.SolverMetadata
import com.robotkinematicslab.mobile.math.utility.Vec3

enum class OperationType {
    FK,
    IK
}

data class ExecutionRecord(
    val operationType: OperationType,
    val robotName: String,
    val inputState: RobotState,
    val targetPosition: Vec3? = null,
    val status: String,
    val finalError: Double? = null,
    val iterations: Int? = null,
    val metadata: SolverMetadata? = null
)
