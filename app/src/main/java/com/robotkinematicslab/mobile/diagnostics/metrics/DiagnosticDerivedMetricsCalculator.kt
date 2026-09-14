package com.robotkinematicslab.mobile.diagnostics.metrics

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.JointType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class DiagnosticDerivedMetrics(
    val seedMinNormalizedLimitMargin: Double,
    val seedLogConditionNumber: Double,
    val normalizedJointTravelRms: Double,
    val finalMinNormalizedLimitMargin: Double
)

class DiagnosticDerivedMetricsCalculator(
    private val policy: DiagnosticMetricPolicy = DiagnosticMetricPolicy()
) {

    companion object {
        const val LOG_CONDITION_NUMBER_CAP = 12.0
        private const val RANGE_EPS = 1e-12
    }

    fun calculate(
        robot: RobotDefinition,
        seedState: RobotState,
        solutionState: RobotState,
        seedConditionNumber: Double
    ): DiagnosticDerivedMetrics {
        return DiagnosticDerivedMetrics(
            seedMinNormalizedLimitMargin =
                minNormalizedLimitMargin(
                    robot = robot,
                    state = seedState
                ),
            seedLogConditionNumber =
                logConditionNumber(seedConditionNumber),
            normalizedJointTravelRms =
                normalizedJointTravelRms(
                    robot = robot,
                    seedState = seedState,
                    solutionState = solutionState
                ),
            finalMinNormalizedLimitMargin =
                minNormalizedLimitMargin(
                    robot = robot,
                    state = solutionState
                )
        )
    }

    fun minNormalizedLimitMargin(
        robot: RobotDefinition,
        state: RobotState
    ): Double {
        if (
            robot.joints.isEmpty() ||
            state.jointValues.size != robot.joints.size
        ) {
            return Double.NaN
        }

        var minimumMargin = 0.5

        robot.joints.forEachIndexed { index, joint ->
            val value = state.jointValues[index]
            val range = joint.maxValue - joint.minValue

            if (
                !value.isFinite() ||
                !joint.minValue.isFinite() ||
                !joint.maxValue.isFinite() ||
                !range.isFinite() ||
                range <= RANGE_EPS
            ) {
                return Double.NaN
            }

            val lowerMargin = (value - joint.minValue) / range
            val upperMargin = (joint.maxValue - value) / range
            val normalizedMargin =
                min(lowerMargin, upperMargin)
                    .coerceIn(0.0, 0.5)

            minimumMargin = min(minimumMargin, normalizedMargin)
        }

        return minimumMargin
    }

    fun normalizedJointTravelRms(
        robot: RobotDefinition,
        seedState: RobotState,
        solutionState: RobotState
    ): Double {
        if (
            robot.joints.isEmpty() ||
            seedState.jointValues.size != robot.joints.size ||
            solutionState.jointValues.size != robot.joints.size
        ) {
            return Double.NaN
        }

        var normalizedTravelSquaredSum = 0.0

        robot.joints.forEachIndexed { index, joint ->
            val seedValue = seedState.jointValues[index]
            val solutionValue = solutionState.jointValues[index]
            val range = joint.maxValue - joint.minValue

            if (
                !seedValue.isFinite() ||
                !solutionValue.isFinite() ||
                !range.isFinite() ||
                range <= RANGE_EPS
            ) {
                return Double.NaN
            }

            val rawTravel =
                solutionValue - seedValue

            val comparableTravel =
                if (
                    joint.type == JointType.REVOLUTE &&
                    range >= 2.0 * PI - RANGE_EPS
                ) {
                    atan2(
                        sin(rawTravel),
                        cos(rawTravel)
                    )
                } else {
                    rawTravel
                }

            val normalizedTravel =
                comparableTravel / range

            normalizedTravelSquaredSum +=
                normalizedTravel * normalizedTravel
        }

        return sqrt(
            normalizedTravelSquaredSum /
                    robot.joints.size.toDouble()
        )
    }

    fun logConditionNumber(
        conditionNumber: Double
    ): Double {
        return when {
            conditionNumber.isNaN() || conditionNumber <= 0.0 ->
                Double.NaN

            conditionNumber == Double.POSITIVE_INFINITY ->
                policy.logConditionNumberCap

            !conditionNumber.isFinite() ->
                Double.NaN

            else ->
                log10(conditionNumber)
                    .coerceIn(0.0, policy.logConditionNumberCap)
        }
    }
}
