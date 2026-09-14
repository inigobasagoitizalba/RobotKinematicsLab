package com.robotkinematicslab.mobile.diagnostics.benchmark.report

data class SeedComparisonSummary(
    val seedCount: Int,
    val bestSeed: Int?,
    val worstSeed: Int?,
    val averageStrictAcceptanceRate: Double,
    val strictAcceptanceRateVariance: Double,
    val strictAcceptanceRateSpread: Double,
    val seedSensitivityLabel: String
)
