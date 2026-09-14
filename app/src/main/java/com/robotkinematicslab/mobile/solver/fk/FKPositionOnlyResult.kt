package com.robotkinematicslab.mobile.solver.fk

import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3

data class FKPositionOnlyResult(
    val status: FKStatus,
    val position: Vec3,
    val detailCode: FKDetailCode
)