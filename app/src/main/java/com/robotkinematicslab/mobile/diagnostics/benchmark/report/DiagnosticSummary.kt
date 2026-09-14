package com.robotkinematicslab.mobile.diagnostics.benchmark.report

data class DiagnosticSummary(
    val totalCases: Int,
    val totalRuns: Int,
    val expectedReachableCount: Int,
    val expectedUnreachableCount: Int,

    val oracleReachableRuns: Int,
    val oracleReachableAccepted: Int,
    val oracleReachableRejected: Int,

    val sequentialRuns: Int,
    val sequentialAcceptedCount: Int,
    val sequentialRejectedCount: Int,

    val sequentialReachableRuns: Int,
    val sequentialReachableAccepted: Int,
    val sequentialReachableRejected: Int,

    val sequentialUnreachableRuns: Int,
    val sequentialUnreachableAccepted: Int,
    val sequentialUnreachableRejected: Int,

    val averageSequentialInitialError: Double,
    val averageSequentialError: Double,
    val maxSequentialError: Double,
    val averageSequentialImprovement: Double,
    val averageSequentialImprovementRatio: Double,
    val averageSequentialIterations: Double,
    val averageIterationSaturationRatio: Double,

    val averageJointDeltaNorm: Double,
    val averageMaxSingleJointMovement: Double,
    val averageJointLimitPressureRatio: Double,
    val runsWithNearJointLimit: Int,

    val solvedCount: Int,
    val nearSolvedCount: Int,
    val improvedButNotEnoughCount: Int,
    val stalledCount: Int,
    val worsenedCount: Int,
    val invalidNumericalCount: Int,

    val easySeedRuns: Int,
    val mediumSeedRuns: Int,
    val hardSeedRuns: Int,
    val extremeSeedRuns: Int,
    val unknownSeedRuns: Int
)
