package com.robotkinematicslab.mobile.solver.fk

import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKStatus

class MutableFKPositionOnlyResult {
    var status: FKStatus = FKStatus.SUCCESS
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0
    var detailCode: FKDetailCode = FKDetailCode.NONE
}