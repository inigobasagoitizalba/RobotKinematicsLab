package com.robotkinematicslab.mobile.diagnostics.metrics

/**
 * Scientific thresholds shared by diagnostic benchmarks and dataset generation.
 *
 * Keeping these values in one immutable policy ensures that an exported feature has
 * exactly the same meaning regardless of which application workflow produced it.
 */
data class DiagnosticMetricPolicy(
    val numericalEpsilon: Double = 1e-12,
    val nearSuccessErrorMeters: Double = 1e-3,
    val closeMissErrorMeters: Double = 1e-2,
    val stalledImprovementRatioEpsilon: Double = 1e-2,
    val jointLimitMarginRatio: Double = 5e-2,
    val easySeedDistanceUpperMeters: Double = 0.10,
    val mediumSeedDistanceUpperMeters: Double = 0.50,
    val hardSeedDistanceUpperMeters: Double = 1.00,
    val logConditionNumberCap: Double = 12.0
) {

    init {
        require(numericalEpsilon > 0.0 && numericalEpsilon.isFinite())
        require(nearSuccessErrorMeters >= 0.0 && nearSuccessErrorMeters.isFinite())
        require(closeMissErrorMeters >= nearSuccessErrorMeters && closeMissErrorMeters.isFinite())
        require(stalledImprovementRatioEpsilon >= 0.0 && stalledImprovementRatioEpsilon.isFinite())
        require(jointLimitMarginRatio in 0.0..0.5)
        require(easySeedDistanceUpperMeters >= 0.0 && easySeedDistanceUpperMeters.isFinite())
        require(mediumSeedDistanceUpperMeters > easySeedDistanceUpperMeters && mediumSeedDistanceUpperMeters.isFinite())
        require(hardSeedDistanceUpperMeters > mediumSeedDistanceUpperMeters && hardSeedDistanceUpperMeters.isFinite())
        require(logConditionNumberCap > 0.0 && logConditionNumberCap.isFinite())
    }
}
