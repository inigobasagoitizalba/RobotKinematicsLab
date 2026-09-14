package com.robotkinematicslab.mobile.diagnostics.benchmark.validation

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import kotlin.math.PI
import kotlin.math.abs

enum class DiagnosticTopologyAuditSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class DiagnosticTopologyAuditCode {
    LINK_COUNT_BELOW_SAFE_MIN,
    LINK_COUNT_EXPERIMENTAL,
    JOINT_DH_COUNT_MISMATCH,

    NON_FINITE_DH_VALUE,
    NON_FINITE_JOINT_LIMIT,
    NON_FINITE_HOME_VALUE,

    INVALID_JOINT_LIMIT_RANGE,
    HOME_VALUE_OUTSIDE_LIMITS,

    LINK_GEOMETRY_TOO_SMALL,
    LINK_GEOMETRY_TOO_LARGE,
    TOTAL_REACH_TOO_SMALL,
    TOTAL_REACH_TOO_LARGE,

    REVOLUTE_RANGE_TOO_SMALL,
    REVOLUTE_RANGE_UNUSUALLY_LARGE,

    PRISMATIC_RANGE_TOO_SMALL,
    PRISMATIC_RANGE_UNUSUALLY_LARGE,

    ALL_LINKS_ZERO_OFFSET,
    ALL_JOINTS_PRISMATIC,
    HIGH_PRISMATIC_RATIO,

    TOPOLOGY_ACCEPTED
}

data class DiagnosticTopologyAuditIssue(
    val severity: DiagnosticTopologyAuditSeverity,
    val code: DiagnosticTopologyAuditCode,
    val message: String
)

data class DiagnosticTopologyAuditMetrics(
    val linkCount: Int,
    val jointCount: Int,
    val dhCount: Int,
    val revoluteCount: Int,
    val prismaticCount: Int,
    val prismaticRatio: Double,
    val estimatedReachMeters: Double,
    val minLinkExtentMeters: Double,
    val maxLinkExtentMeters: Double
)

data class DiagnosticTopologyAuditResult(
    val acceptedForBenchmark: Boolean,
    val safeModeReliabilityClaimAllowed: Boolean,
    val metrics: DiagnosticTopologyAuditMetrics,
    val issues: List<DiagnosticTopologyAuditIssue>
) {
    val hasErrors: Boolean
        get() = issues.any { it.severity == DiagnosticTopologyAuditSeverity.ERROR }

    val hasWarnings: Boolean
        get() = issues.any { it.severity == DiagnosticTopologyAuditSeverity.WARNING }

    val summaryLabel: String
        get() = when {
            hasErrors -> "TOPOLOGY_REJECTED"
            hasWarnings -> "TOPOLOGY_ACCEPTED_WITH_WARNINGS"
            else -> "TOPOLOGY_ACCEPTED"
        }
}

class DiagnosticTopologyAuditor {

    companion object {
        private const val SAFE_MIN_LINK_COUNT = 2
        private const val SAFE_MAX_LINK_COUNT = 10

        private const val MIN_LINK_EXTENT_METERS = 0.01
        private const val MAX_LINK_EXTENT_METERS = 2.50

        private const val MIN_TOTAL_REACH_METERS = 0.05
        private const val MAX_TOTAL_REACH_SAFE_METERS = 8.0

        private const val MIN_REVOLUTE_RANGE_RAD = 0.05
        private const val MAX_REASONABLE_REVOLUTE_RANGE_RAD = 2.0 * PI

        private const val MIN_PRISMATIC_RANGE_METERS = 0.005
        private const val MAX_REASONABLE_PRISMATIC_RANGE_METERS = 2.0

        private const val HIGH_PRISMATIC_RATIO = 0.70
    }

    fun audit(robot: RobotDefinition): DiagnosticTopologyAuditResult {
        val issues = mutableListOf<DiagnosticTopologyAuditIssue>()

        val linkCount = robot.joints.size
        val dhCount = robot.dhParameters.size

        if (linkCount < SAFE_MIN_LINK_COUNT) {
            issues += error(
                code = DiagnosticTopologyAuditCode.LINK_COUNT_BELOW_SAFE_MIN,
                message = "Robot has $linkCount link(s). Diagnostic safe mode requires at least $SAFE_MIN_LINK_COUNT links."
            )
        }

        if (linkCount > SAFE_MAX_LINK_COUNT) {
            issues += warning(
                code = DiagnosticTopologyAuditCode.LINK_COUNT_EXPERIMENTAL,
                message = "Robot has $linkCount links. Link counts above $SAFE_MAX_LINK_COUNT are experimental and must not be used for strong reliability claims."
            )
        }

        if (linkCount != dhCount) {
            issues += error(
                code = DiagnosticTopologyAuditCode.JOINT_DH_COUNT_MISMATCH,
                message = "Joint count ($linkCount) does not match DH parameter count ($dhCount)."
            )
        }

        robot.dhParameters.forEachIndexed { index, dh ->
            if (
                !dh.theta.isFinite() ||
                !dh.d.isFinite() ||
                !dh.a.isFinite() ||
                !dh.alpha.isFinite()
            ) {
                issues += error(
                    code = DiagnosticTopologyAuditCode.NON_FINITE_DH_VALUE,
                    message = "DH row ${index + 1} contains a non-finite value."
                )
            }
        }

        robot.joints.forEachIndexed { index, joint ->
            if (!joint.minValue.isFinite() || !joint.maxValue.isFinite()) {
                issues += error(
                    code = DiagnosticTopologyAuditCode.NON_FINITE_JOINT_LIMIT,
                    message = "Joint ${joint.name} has a non-finite limit."
                )
            }

            if (!joint.homeValue.isFinite()) {
                issues += error(
                    code = DiagnosticTopologyAuditCode.NON_FINITE_HOME_VALUE,
                    message = "Joint ${joint.name} has a non-finite home value."
                )
            }

            if (
                joint.minValue.isFinite() &&
                joint.maxValue.isFinite() &&
                joint.minValue >= joint.maxValue
            ) {
                issues += error(
                    code = DiagnosticTopologyAuditCode.INVALID_JOINT_LIMIT_RANGE,
                    message = "Joint ${joint.name} has invalid limits: min=${joint.minValue}, max=${joint.maxValue}."
                )
            }

            if (
                joint.homeValue.isFinite() &&
                joint.minValue.isFinite() &&
                joint.maxValue.isFinite() &&
                joint.homeValue !in joint.minValue..joint.maxValue
            ) {
                issues += error(
                    code = DiagnosticTopologyAuditCode.HOME_VALUE_OUTSIDE_LIMITS,
                    message = "Joint ${joint.name} home value ${joint.homeValue} is outside limits [${joint.minValue}, ${joint.maxValue}]."
                )
            }

            val range =
                if (joint.minValue.isFinite() && joint.maxValue.isFinite()) {
                    joint.maxValue - joint.minValue
                } else {
                    Double.NaN
                }

            if (range.isFinite()) {
                when (joint.type) {
                    JointType.REVOLUTE -> {
                        if (range < MIN_REVOLUTE_RANGE_RAD) {
                            issues += warning(
                                code = DiagnosticTopologyAuditCode.REVOLUTE_RANGE_TOO_SMALL,
                                message = "Joint ${joint.name} has a very small revolute range: $range rad."
                            )
                        }

                        if (range > MAX_REASONABLE_REVOLUTE_RANGE_RAD) {
                            issues += warning(
                                code = DiagnosticTopologyAuditCode.REVOLUTE_RANGE_UNUSUALLY_LARGE,
                                message = "Joint ${joint.name} has an unusually large revolute range: $range rad."
                            )
                        }
                    }

                    JointType.PRISMATIC -> {
                        if (range < MIN_PRISMATIC_RANGE_METERS) {
                            issues += warning(
                                code = DiagnosticTopologyAuditCode.PRISMATIC_RANGE_TOO_SMALL,
                                message = "Joint ${joint.name} has a very small prismatic range: $range m."
                            )
                        }

                        if (range > MAX_REASONABLE_PRISMATIC_RANGE_METERS) {
                            issues += warning(
                                code = DiagnosticTopologyAuditCode.PRISMATIC_RANGE_UNUSUALLY_LARGE,
                                message = "Joint ${joint.name} has an unusually large prismatic range: $range m."
                            )
                        }
                    }
                }
            }
        }

        val linkExtents =
            RobotReachEnvelope.perLinkRadialUpperBounds(robot)

        val finiteLinkExtents =
            linkExtents.filter { it.isFinite() }

        val estimatedReach =
            RobotReachEnvelope.conservativeRadialUpperBound(robot)

        val minLinkExtent =
            finiteLinkExtents.minOrNull() ?: Double.NaN

        val maxLinkExtent =
            finiteLinkExtents.maxOrNull() ?: Double.NaN

        finiteLinkExtents.forEachIndexed { index, extent ->
            if (extent < MIN_LINK_EXTENT_METERS) {
                issues += warning(
                    code = DiagnosticTopologyAuditCode.LINK_GEOMETRY_TOO_SMALL,
                    message = "Link ${index + 1} has very small effective DH extent: $extent m."
                )
            }

            if (extent > MAX_LINK_EXTENT_METERS) {
                issues += warning(
                    code = DiagnosticTopologyAuditCode.LINK_GEOMETRY_TOO_LARGE,
                    message = "Link ${index + 1} has unusually large effective DH extent: $extent m."
                )
            }
        }

        if (estimatedReach.isFinite() && estimatedReach < MIN_TOTAL_REACH_METERS) {
            issues += error(
                code = DiagnosticTopologyAuditCode.TOTAL_REACH_TOO_SMALL,
                message = "Estimated total reach is too small for useful diagnostics: $estimatedReach m."
            )
        }

        if (estimatedReach.isFinite() && estimatedReach > MAX_TOTAL_REACH_SAFE_METERS) {
            issues += warning(
                code = DiagnosticTopologyAuditCode.TOTAL_REACH_TOO_LARGE,
                message = "Estimated total reach is large for an in-app diagnostic benchmark: $estimatedReach m."
            )
        }

        if (finiteLinkExtents.isNotEmpty() && finiteLinkExtents.all { it < MIN_LINK_EXTENT_METERS }) {
            issues += error(
                code = DiagnosticTopologyAuditCode.ALL_LINKS_ZERO_OFFSET,
                message = "All links have near-zero DH extent. This topology is degenerate for benchmark generation."
            )
        }

        val revoluteCount =
            robot.joints.count { it.type == JointType.REVOLUTE }

        val prismaticCount =
            robot.joints.count { it.type == JointType.PRISMATIC }

        val prismaticRatio =
            if (linkCount > 0) {
                prismaticCount.toDouble() / linkCount.toDouble()
            } else {
                0.0
            }

        if (linkCount > 0 && prismaticCount == linkCount) {
            issues += warning(
                code = DiagnosticTopologyAuditCode.ALL_JOINTS_PRISMATIC,
                message = "All joints are prismatic. This is allowed, but it is less representative of common serial robot arms."
            )
        } else if (prismaticRatio >= HIGH_PRISMATIC_RATIO) {
            issues += warning(
                code = DiagnosticTopologyAuditCode.HIGH_PRISMATIC_RATIO,
                message = "Prismatic joint ratio is high: $prismaticCount / $linkCount."
            )
        }

        if (issues.isEmpty()) {
            issues += info(
                code = DiagnosticTopologyAuditCode.TOPOLOGY_ACCEPTED,
                message = "Topology passed all benchmark audit checks."
            )
        }

        val acceptedForBenchmark =
            issues.none {
                it.severity == DiagnosticTopologyAuditSeverity.ERROR
            }

        val safeModeReliabilityClaimAllowed =
            acceptedForBenchmark &&
                    linkCount in SAFE_MIN_LINK_COUNT..SAFE_MAX_LINK_COUNT &&
                    issues.none {
                        it.code == DiagnosticTopologyAuditCode.LINK_COUNT_EXPERIMENTAL
                    }

        return DiagnosticTopologyAuditResult(
            acceptedForBenchmark = acceptedForBenchmark,
            safeModeReliabilityClaimAllowed = safeModeReliabilityClaimAllowed,
            metrics = DiagnosticTopologyAuditMetrics(
                linkCount = linkCount,
                jointCount = robot.joints.size,
                dhCount = robot.dhParameters.size,
                revoluteCount = revoluteCount,
                prismaticCount = prismaticCount,
                prismaticRatio = prismaticRatio,
                estimatedReachMeters = estimatedReach,
                minLinkExtentMeters = minLinkExtent,
                maxLinkExtentMeters = maxLinkExtent
            ),
            issues = issues
        )
    }

    private fun info(
        code: DiagnosticTopologyAuditCode,
        message: String
    ): DiagnosticTopologyAuditIssue {
        return DiagnosticTopologyAuditIssue(
            severity = DiagnosticTopologyAuditSeverity.INFO,
            code = code,
            message = message
        )
    }

    private fun warning(
        code: DiagnosticTopologyAuditCode,
        message: String
    ): DiagnosticTopologyAuditIssue {
        return DiagnosticTopologyAuditIssue(
            severity = DiagnosticTopologyAuditSeverity.WARNING,
            code = code,
            message = message
        )
    }

    private fun error(
        code: DiagnosticTopologyAuditCode,
        message: String
    ): DiagnosticTopologyAuditIssue {
        return DiagnosticTopologyAuditIssue(
            severity = DiagnosticTopologyAuditSeverity.ERROR,
            code = code,
            message = message
        )
    }
}
