package com.robotkinematicslab.mobile.domain.result

import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3

enum class FKStatus {
    SUCCESS,
    SUCCESS_WITH_WARNING,
    INVALID_INPUT,
    NUMERICAL_FAILURE
}

data class FKResult(
    val status: FKStatus,
    val endEffectorTransform: Matrix4,
    val endEffectorPosition: Vec3,
    val jointPositions: List<Vec3>,
    val detailCode: FKDetailCode = FKDetailCode.NONE,
    val metadata: SolverMetadata? = null
)