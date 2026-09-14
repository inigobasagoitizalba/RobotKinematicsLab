package com.robotkinematicslab.mobile.diagnostics.benchmark.report

import com.robotkinematicslab.mobile.math.statistics.BinomialConfidenceInterval
import com.robotkinematicslab.mobile.math.statistics.WilsonScoreInterval

data class DiagnosticStatusDistributionItem(
    val status: String,
    val count: Int,
    val ratio: Double
)

data class DiagnosticDetailCodeDistributionItem(
    val detailCode: String,
    val count: Int,
    val ratio: Double
)

data class DiagnosticDatasetAcceptanceSummary(
    val reachableRunCount: Int,
    val reachableAcceptedCount: Int,
    val reachableRejectedCount: Int,
    val reachableAcceptanceRate: Double,
    val unreachableRunCount: Int,
    val unreachableAcceptedCount: Int,
    val unreachableRejectedCount: Int,
    val falseAcceptCount: Int,
    val falseRejectCount: Int,
    val unreachableRejectionRate: Double,
    val reachableAcceptanceConfidence95: BinomialConfidenceInterval =
        WilsonScoreInterval.at95Percent(reachableAcceptedCount, reachableRunCount),
    val unreachableRejectionConfidence95: BinomialConfidenceInterval =
        WilsonScoreInterval.at95Percent(unreachableRejectedCount, unreachableRunCount)
)
