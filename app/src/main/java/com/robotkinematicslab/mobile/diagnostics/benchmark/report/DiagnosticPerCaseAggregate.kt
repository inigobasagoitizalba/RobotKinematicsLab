package com.robotkinematicslab.mobile.diagnostics.benchmark.report

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3

data class DiagnosticPerCaseAggregate(
    val caseId: String,
    val expectedClass: DiagnosticExpectedClass,
    val sequentialRunCount: Int,
    val sequentialAcceptedCount: Int,
    val sequentialRejectedCount: Int,
    val strictAcceptedCount: Int,
    val nearSolvedCount: Int,
    val closeMissCount: Int,
    val farFailureCount: Int,
    val oracleRunCount: Int,
    val oracleAcceptedCount: Int,
    val oracleRejectedCount: Int,
    val averageSequentialError: Double,
    val maxSequentialError: Double,
    val averageSequentialInitialError: Double,
    val averageSequentialImprovementRatio: Double,
    val averageSequentialIterations: Double,
    val averageIterationSaturationRatio: Double,
    val averageJointDeltaNorm: Double,
    val averageMaxSingleJointMovement: Double,
    val averageJointLimitPressureRatio: Double,
    val mostCommonSequentialStatus: String,
    val mostCommonProgressClass: DiagnosticProgressClass?,
    val mostCommonSeedDistanceBucket: DiagnosticSeedDistanceBucket?,
    val target: Vec3,
    val sourceJointState: RobotState?,
    val note: String
)
