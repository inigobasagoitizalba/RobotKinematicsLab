package com.robotkinematicslab.mobile.math.statistics

import kotlin.math.sqrt

/**
 * Confidence interval for a binomial proportion.
 *
 * Wilson's score interval is used because, unlike the elementary Wald interval,
 * it remains meaningful for small samples and for observed rates of exactly 0 or 1.
 */
data class BinomialConfidenceInterval(
    val successes: Int,
    val trials: Int,
    val confidenceLevel: Double,
    val estimate: Double,
    val lower: Double,
    val upper: Double
)

object WilsonScoreInterval {

    private const val Z_95 = 1.959963984540054

    fun at95Percent(
        successes: Int,
        trials: Int
    ): BinomialConfidenceInterval {
        require(trials >= 0) { "Trials must be non-negative." }
        require(successes in 0..trials) { "Successes must be between zero and trials." }

        if (trials == 0) {
            return BinomialConfidenceInterval(
                successes = successes,
                trials = trials,
                confidenceLevel = 0.95,
                estimate = Double.NaN,
                lower = Double.NaN,
                upper = Double.NaN
            )
        }

        val n = trials.toDouble()
        val estimate = successes.toDouble() / n
        val zSquared = Z_95 * Z_95
        val denominator = 1.0 + zSquared / n
        val center = (estimate + zSquared / (2.0 * n)) / denominator
        val halfWidth =
            Z_95 * sqrt(
                estimate * (1.0 - estimate) / n +
                    zSquared / (4.0 * n * n)
            ) / denominator

        return BinomialConfidenceInterval(
            successes = successes,
            trials = trials,
            confidenceLevel = 0.95,
            estimate = estimate,
            lower = (center - halfWidth).coerceIn(0.0, 1.0),
            upper = (center + halfWidth).coerceIn(0.0, 1.0)
        )
    }
}
