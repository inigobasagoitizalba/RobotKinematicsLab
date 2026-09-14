package com.robotkinematicslab.mobile.diagnostics.metrics

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKDiagnostics
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.abs
import kotlin.math.hypot

data class DiagnosticRunMetrics(
    val initialError: Double,
    val improvement: Double,
    val improvementRatio: Double,
    val progressClass: DiagnosticProgressClass,
    val seedDistanceBucket: DiagnosticSeedDistanceBucket,
    val iterationSaturationRatio: Double,
    val jointDeltaNorm: Double,
    val maxSingleJointMovement: Double,
    val seedMinNormalizedLimitMargin: Double,
    val seedLogConditionNumber: Double,
    val normalizedJointTravelRms: Double,
    val finalMinNormalizedLimitMargin: Double,
    val backtrackingRetryCount: Int,
    val solveDurationNanos: Long,
    val nearLimitJointCount: Int,
    val nearLimitJointNames: List<String>,
    val jointLimitPressureRatio: Double
)

/** Calculates the enriched run features used by both diagnostics and ML datasets. */
class DiagnosticRunMetricsCalculator(
    val policy: DiagnosticMetricPolicy = DiagnosticMetricPolicy(),
    private val derivedMetricsCalculator: DiagnosticDerivedMetricsCalculator =
        DiagnosticDerivedMetricsCalculator(policy)
) {

    fun calculateInitialError(
        seedFk: FKResult,
        target: Vec3
    ): Double {
        val accepted =
            seedFk.status == FKStatus.SUCCESS ||
                seedFk.status == FKStatus.SUCCESS_WITH_WARNING

        return if (accepted) {
            distance(seedFk.endEffectorPosition, target)
        } else {
            Double.NaN
        }
    }

    fun calculate(
        robot: RobotDefinition,
        seedState: RobotState,
        solutionState: RobotState,
        initialError: Double,
        finalError: Double,
        iterations: Int,
        maxIterations: Int,
        solverAccepted: Boolean,
        solverDiagnostics: IKDiagnostics,
        metricPolicy: DiagnosticMetricPolicy = policy
    ): DiagnosticRunMetrics {
        val safeInitialError = initialError.takeIf(Double::isFinite) ?: Double.NaN
        val safeFinalError = finalError.takeIf(Double::isFinite) ?: Double.NaN
        val improvement =
            if (safeInitialError.isFinite() && safeFinalError.isFinite()) {
                safeInitialError - safeFinalError
            } else {
                Double.NaN
            }
        val improvementRatio =
            when {
                safeInitialError > metricPolicy.numericalEpsilon && improvement.isFinite() ->
                    improvement / safeInitialError

                safeInitialError.isFinite() &&
                    safeInitialError <= metricPolicy.numericalEpsilon &&
                    safeFinalError.isFinite() &&
                    safeFinalError <= metricPolicy.nearSuccessErrorMeters -> 1.0

                else -> Double.NaN
            }
        val activeDerivedMetricsCalculator =
            if (metricPolicy == policy) {
                derivedMetricsCalculator
            } else {
                DiagnosticDerivedMetricsCalculator(metricPolicy)
            }
        val derived =
            activeDerivedMetricsCalculator.calculate(
                robot = robot,
                seedState = seedState,
                solutionState = solutionState,
                seedConditionNumber = solverDiagnostics.seedConditionNumber
            )
        val movement = calculateJointMovement(seedState, solutionState)
        val limitPressure = calculateJointLimitPressure(robot, solutionState, metricPolicy)

        return DiagnosticRunMetrics(
            initialError = safeInitialError,
            improvement = improvement,
            improvementRatio = improvementRatio,
            progressClass = classifyProgress(solverAccepted, safeFinalError, improvementRatio, metricPolicy),
            seedDistanceBucket = classifySeedDistance(safeInitialError, metricPolicy),
            iterationSaturationRatio =
                if (maxIterations > 0) {
                    (iterations.toDouble() / maxIterations.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    Double.NaN
                },
            jointDeltaNorm = movement.first,
            maxSingleJointMovement = movement.second,
            seedMinNormalizedLimitMargin = derived.seedMinNormalizedLimitMargin,
            seedLogConditionNumber = derived.seedLogConditionNumber,
            normalizedJointTravelRms = derived.normalizedJointTravelRms,
            finalMinNormalizedLimitMargin = derived.finalMinNormalizedLimitMargin,
            backtrackingRetryCount = solverDiagnostics.backtrackingRetryCount,
            solveDurationNanos = solverDiagnostics.solveDurationNanos,
            nearLimitJointCount = limitPressure.first,
            nearLimitJointNames = limitPressure.second,
            jointLimitPressureRatio = limitPressure.third
        )
    }

    private fun classifyProgress(
        solverAccepted: Boolean,
        finalError: Double,
        improvementRatio: Double,
        metricPolicy: DiagnosticMetricPolicy
    ): DiagnosticProgressClass {
        return when {
            !finalError.isFinite() -> DiagnosticProgressClass.INVALID_NUMERICAL
            solverAccepted -> DiagnosticProgressClass.SOLVED
            finalError <= metricPolicy.nearSuccessErrorMeters -> DiagnosticProgressClass.NEAR_SOLVED
            improvementRatio.isFinite() &&
                improvementRatio < -metricPolicy.stalledImprovementRatioEpsilon ->
                DiagnosticProgressClass.WORSENED
            improvementRatio.isFinite() &&
                improvementRatio < metricPolicy.stalledImprovementRatioEpsilon ->
                DiagnosticProgressClass.STALLED
            improvementRatio.isFinite() -> DiagnosticProgressClass.IMPROVED_BUT_NOT_ENOUGH
            else -> DiagnosticProgressClass.INVALID_NUMERICAL
        }
    }

    private fun classifySeedDistance(
        initialError: Double,
        metricPolicy: DiagnosticMetricPolicy
    ): DiagnosticSeedDistanceBucket {
        return when {
            !initialError.isFinite() -> DiagnosticSeedDistanceBucket.UNKNOWN
            initialError < metricPolicy.easySeedDistanceUpperMeters -> DiagnosticSeedDistanceBucket.EASY
            initialError < metricPolicy.mediumSeedDistanceUpperMeters -> DiagnosticSeedDistanceBucket.MEDIUM
            initialError < metricPolicy.hardSeedDistanceUpperMeters -> DiagnosticSeedDistanceBucket.HARD
            else -> DiagnosticSeedDistanceBucket.EXTREME
        }
    }

    private fun calculateJointMovement(
        seedState: RobotState,
        solutionState: RobotState
    ): Pair<Double, Double> {
        if (
            seedState.jointValues.isEmpty() ||
            seedState.jointValues.size != solutionState.jointValues.size
        ) {
            return Double.NaN to Double.NaN
        }

        var movementNorm = 0.0
        var maximum = 0.0

        seedState.jointValues.indices.forEach { index ->
            val delta = abs(solutionState.jointValues[index] - seedState.jointValues[index])
            if (!delta.isFinite()) {
                return Double.NaN to Double.NaN
            }
            movementNorm = hypot(movementNorm, delta)
            maximum = maxOf(maximum, delta)
        }

        return movementNorm to maximum
    }

    private fun calculateJointLimitPressure(
        robot: RobotDefinition,
        state: RobotState,
        metricPolicy: DiagnosticMetricPolicy
    ): Triple<Int, List<String>, Double> {
        if (robot.joints.isEmpty() || robot.joints.size != state.jointValues.size) {
            return Triple(0, emptyList(), Double.NaN)
        }

        val names = mutableListOf<String>()

        robot.joints.forEachIndexed { index, joint ->
            val value = state.jointValues[index]
            val range = joint.maxValue - joint.minValue

            if (value.isFinite() && range.isFinite() && range > 0.0) {
                val margin = range * metricPolicy.jointLimitMarginRatio
                if (
                    abs(value - joint.minValue) <= margin ||
                    abs(value - joint.maxValue) <= margin
                ) {
                    names += joint.name
                }
            }
        }

        return Triple(
            names.size,
            names,
            names.size.toDouble() / robot.joints.size.toDouble()
        )
    }

    private fun distance(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return hypot(hypot(dx, dy), dz)
    }
}
