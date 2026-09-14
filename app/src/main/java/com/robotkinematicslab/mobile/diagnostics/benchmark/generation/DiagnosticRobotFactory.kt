package com.robotkinematicslab.mobile.diagnostics.benchmark.generation

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import kotlin.math.PI

class DiagnosticRobotFactory {

    fun buildSeedRobot(
        linkCount: Int,
        jointMode: DiagnosticJointMode = DiagnosticJointMode.AUTO,
        stressLevel: Double = 0.5
    ): RobotDefinition {

        val safeLinkCount =
            if (linkCount <= SAFE_MAX_LINK_COUNT) {
                linkCount.coerceIn(SAFE_MIN_LINK_COUNT, SAFE_MAX_LINK_COUNT)
            } else {
                linkCount.coerceIn(SAFE_MIN_LINK_COUNT, EXPERIMENTAL_MAX_LINK_COUNT)
            }

        val safeStressLevel =
            stressLevel.coerceIn(0.0, 1.0)

        val jointTypes =
            buildJointTypes(
                linkCount = safeLinkCount,
                jointMode = jointMode
            )

        val dhParameters =
            List(safeLinkCount) { index ->
                buildDhParameter(
                    index = index,
                    linkCount = safeLinkCount,
                    jointType = jointTypes[index],
                    jointMode = jointMode,
                    stressLevel = safeStressLevel
                )
            }

        val joints =
            List(safeLinkCount) { index ->
                buildJointDefinition(
                    index = index,
                    jointType = jointTypes[index],
                    stressLevel = safeStressLevel
                )
            }

        return RobotDefinition(
            name = buildRobotName(
                linkCount = safeLinkCount,
                jointMode = jointMode
            ),
            dhParameters = dhParameters,
            joints = joints
        )
    }

    private fun buildJointTypes(
        linkCount: Int,
        jointMode: DiagnosticJointMode
    ): List<JointType> {
        return when (jointMode) {
            DiagnosticJointMode.REVOLUTE_ONLY ->
                List(linkCount) {
                    JointType.REVOLUTE
                }

            DiagnosticJointMode.PRISMATIC_ONLY ->
                List(linkCount) {
                    JointType.PRISMATIC
                }

            DiagnosticJointMode.MIXED ->
                List(linkCount) { index ->
                    when {
                        index == 0 ->
                            JointType.REVOLUTE

                        index % 3 == 2 ->
                            JointType.PRISMATIC

                        else ->
                            JointType.REVOLUTE
                    }
                }

            DiagnosticJointMode.AUTO ->
                List(linkCount) { index ->
                    when {
                        index == 0 ->
                            JointType.REVOLUTE

                        linkCount <= 3 ->
                            JointType.REVOLUTE

                        index == linkCount - 1 && linkCount >= 5 ->
                            JointType.PRISMATIC

                        index % 4 == 3 ->
                            JointType.PRISMATIC

                        else ->
                            JointType.REVOLUTE
                    }
                }
        }
    }

    private fun buildDhParameter(
        index: Int,
        linkCount: Int,
        jointType: JointType,
        jointMode: DiagnosticJointMode,
        stressLevel: Double
    ): DHParameter {
        val baseLength =
            baseLinkLength(
                index = index,
                linkCount = linkCount
            )

        val stressScale =
            1.0 + stressLevel * 0.20

        val a =
            when (jointMode) {
                DiagnosticJointMode.PRISMATIC_ONLY ->
                    if (index == 0) {
                        0.10
                    } else {
                        0.04
                    }

                else ->
                    baseLength * stressScale
            }

        val d =
            when {
                jointType == JointType.PRISMATIC ->
                    0.0

                index == 0 ->
                    0.12

                stressLevel < 0.25 ->
                    0.0

                else ->
                    0.04 * (index % 3)
            }

        val alpha =
            when (jointMode) {
                DiagnosticJointMode.PRISMATIC_ONLY ->
                    when (index % 3) {
                        0 -> 0.0
                        1 -> PI / 2.0
                        else -> -PI / 2.0
                    }

                else ->
                    buildTwistAngle(
                        index = index,
                        stressLevel = stressLevel
                    )
            }

        return DHParameter(
            theta = 0.0,
            d = d,
            a = a,
            alpha = alpha
        )
    }

    private fun buildJointDefinition(
        index: Int,
        jointType: JointType,
        stressLevel: Double
    ): JointDefinition {
        return when (jointType) {
            JointType.REVOLUTE -> {
                val range =
                    revoluteRangeForIndex(
                        index = index,
                        stressLevel = stressLevel
                    )

                JointDefinition(
                    name = "J${index + 1}",
                    type = JointType.REVOLUTE,
                    minValue = -range,
                    maxValue = range,
                    homeValue = 0.0
                )
            }

            JointType.PRISMATIC -> {
                val minValue =
                    0.02

                val maxValue =
                    0.35 + stressLevel * 0.25

                val homeValue =
                    (minValue + maxValue) / 2.0

                JointDefinition(
                    name = "J${index + 1}",
                    type = JointType.PRISMATIC,
                    minValue = minValue,
                    maxValue = maxValue,
                    homeValue = homeValue
                )
            }
        }
    }

    private fun baseLinkLength(
        index: Int,
        linkCount: Int
    ): Double {
        val nominal =
            when (index) {
                0 -> 0.42
                1 -> 0.34
                2 -> 0.26
                3 -> 0.20
                else -> 0.16
            }

        val redundancyScale =
            when {
                linkCount <= 4 ->
                    1.0

                linkCount <= 7 ->
                    0.90

                else ->
                    0.80
            }

        return nominal * redundancyScale
    }

    private fun buildTwistAngle(
        index: Int,
        stressLevel: Double
    ): Double {
        return when {
            stressLevel < 0.20 ->
                0.0

            stressLevel < 0.50 ->
                if (index % 2 == 0) {
                    0.0
                } else {
                    PI / 2.0
                }

            else ->
                when (index % 4) {
                    0 -> 0.0
                    1 -> PI / 2.0
                    2 -> 0.0
                    else -> -PI / 2.0
                }
        }
    }

    private fun revoluteRangeForIndex(
        index: Int,
        stressLevel: Double
    ): Double {
        val baseRange =
            when (index) {
                0 -> PI
                1 -> 2.0 * PI / 3.0
                2 -> 3.0 * PI / 4.0
                else -> PI / 2.0
            }

        val stressExpansion =
            1.0 + stressLevel * 0.15

        return (baseRange * stressExpansion)
            .coerceAtMost(PI)
    }

    private fun buildRobotName(
        linkCount: Int,
        jointMode: DiagnosticJointMode
    ): String {
        val modeLabel =
            when (jointMode) {
                DiagnosticJointMode.AUTO -> "Auto"
                DiagnosticJointMode.REVOLUTE_ONLY -> "Revolute"
                DiagnosticJointMode.PRISMATIC_ONLY -> "Prismatic"
                DiagnosticJointMode.MIXED -> "Mixed"
            }

        return "Diagnostic $linkCount-Link $modeLabel Serial Robot"
    }

    companion object {
        private const val SAFE_MIN_LINK_COUNT = 2
        private const val SAFE_MAX_LINK_COUNT = 10
        private const val EXPERIMENTAL_MAX_LINK_COUNT = 100
    }
}
