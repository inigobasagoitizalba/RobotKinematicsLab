package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.math.statistics.WilsonScoreInterval
import kotlin.math.roundToInt

object OneMicronReliabilityCalculator {

    fun assess(metrics: OneMicronIkVerificationMetrics): OneMicronReliabilityAssessment {
        require(metrics.samples > 0)
        val successes = (metrics.verifiedPipelineSuccessRate * metrics.samples).roundToInt().coerceIn(0, metrics.samples)
        val interval = WilsonScoreInterval.at95Percent(successes, metrics.samples)
        val band =
            when {
                interval.lower >= 0.999 -> ReliabilityBand.RESEARCH_GRADE
                interval.lower >= 0.99 -> ReliabilityBand.STRONG
                interval.lower >= 0.95 -> ReliabilityBand.MODERATE
                else -> ReliabilityBand.LIMITED
            }
        val avoidedDistance =
            (metrics.rawCumulativeErrorMeters - metrics.protectedCumulativeErrorMeters)
                .coerceAtLeast(0.0)
        val meanRawResidual = metrics.rawCumulativeErrorMeters / metrics.samples
        val meanProtectedResidual = metrics.protectedCumulativeErrorMeters / metrics.samples
        val interpretation =
            "Across ${metrics.samples} untouched cases, the protected pipeline certified $successes results at ≤ 1 µm. " +
                "Its observed rate is ${format(interval.estimate * 100.0)}% and the 95% Wilson interval is " +
                "${format(interval.lower * 100.0)}–${format(interval.upper * 100.0)}%. " +
                "The error-savings percentage is an aggregate across independent test commands: it compares raw neural FK residual with the final independently verified/fallback residual. " +
                "It is not measured trajectory drift, physical wear or energy."
        return OneMicronReliabilityAssessment(
            observedCertifiedRate = interval.estimate,
            wilson95LowerBound = interval.lower,
            wilson95UpperBound = interval.upper,
            band = band,
            cumulativeErrorAvoidedPercent = metrics.cumulativeErrorAvoidedPercent,
            cumulativeDistanceAvoidedMeters = avoidedDistance,
            meanRawResidualMeters = meanRawResidual,
            meanProtectedResidualMeters = meanProtectedResidual,
            protectedResidualBudgetPerThousandCommandsMeters = meanProtectedResidual * 1_000.0,
            iterationSavingsPercent = metrics.iterationSavingsPercent,
            interpretation = interpretation
        )
    }

    private fun format(value: Double): String = java.lang.String.format(java.util.Locale.US, "%.3f", value)
}
