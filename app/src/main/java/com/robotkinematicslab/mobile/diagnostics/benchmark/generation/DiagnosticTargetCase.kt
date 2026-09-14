package com.robotkinematicslab.mobile.diagnostics.benchmark.generation

import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3

data class DiagnosticTargetCase(
    val id: String,
    val expectedClass: DiagnosticExpectedClass,
    val target: Vec3,
    val sourceJointState: RobotState?,
    val note: String
)
