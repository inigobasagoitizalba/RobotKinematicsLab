package com.robotkinematicslab.mobile.diagnostics.benchmark.report

data class DiagnosticTransitionAggregate(
    val fromCaseId: String,
    val toCaseId: String,
    val runCount: Int,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val nearSolvedCount: Int,
    val closeMissCount: Int,
    val farFailureCount: Int,
    val averageFinalError: Double,
    val maxFinalError: Double,
    val averageInitialError: Double,
    val averageImprovementRatio: Double,
    val averageIterations: Double,
    val mostCommonStatus: String
)
