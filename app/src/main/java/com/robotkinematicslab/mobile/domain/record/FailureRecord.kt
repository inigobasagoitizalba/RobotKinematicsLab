package com.robotkinematicslab.mobile.domain.record

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3

data class FailureRecord(
    val operationType: OperationType,
    val robotName: String,
    val inputState: RobotState,
    val targetPosition: Vec3? = null,
    val failureType: String,
    val message: String
)


