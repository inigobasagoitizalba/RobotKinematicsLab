package com.robotkinematicslab.mobile.diagnostics.benchmark.result

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3

data class DiagnosticCaseResult(
    val seed: Int,
    val linkCount: Int,
    val jointMode: DiagnosticJointMode,
    val id: String,
    val expectedClass: DiagnosticExpectedClass,
    val solverAccepted: Boolean,
    val status: String,
    val detailCode: String,
    val finalError: Double,
    val iterations: Int,
    val target: Vec3,
    val sourceJointState: RobotState?,
    val seedJointState: RobotState,
    val solutionJointValues: List<Double>,
    val initialError: Double,
    val improvement: Double,
    val improvementRatio: Double,
    val progressClass: DiagnosticProgressClass,
    val seedDistanceBucket: DiagnosticSeedDistanceBucket,
    val seedMinNormalizedLimitMargin: Double,
    val seedLogConditionNumber: Double,
    val iterationSaturationRatio: Double,
    val jointDeltaNorm: Double,
    val maxSingleJointMovement: Double,
    val normalizedJointTravelRms: Double,
    val finalMinNormalizedLimitMargin: Double,
    val backtrackingRetryCount: Int,
    val solveDurationNanos: Long,
    val nearLimitJointCount: Int,
    val nearLimitJointNames: List<String>,
    val jointLimitPressureRatio: Double,
    val note: String
)
