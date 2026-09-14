package com.robotkinematicslab.mobile.diagnostics.benchmark.report

data class LinkCountAggregate(
    val linkCount: Int,
    val runCount: Int,
    val strictAcceptedCount: Int,
    val nearSolvedCount: Int,
    val closeMissCount: Int,
    val farFailureCount: Int,
    val strictAcceptanceRate: Double,
    val averageFinalError: Double,
    val maxFinalError: Double,
    val averageIterations: Double,
    val averageImprovementRatio: Double
)
