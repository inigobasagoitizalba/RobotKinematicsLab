package com.robotkinematicslab.mobile.diagnostics.benchmark.planning

data class DiagnosticBenchmarkPlan(
    val linkCounts: List<Int>,
    val safeLinkCounts: List<Int>,
    val experimentalLinkCounts: List<Int>,
    val samplesPerLinkCount: Int,
    val totalPlannedSequentialRuns: Long,
    val isExperimental: Boolean,
    val isUnlimited: Boolean,
    val strongReliabilityClaimAllowed: Boolean,
    val warnings: List<String>,
    val reliabilityClaim: String
)
