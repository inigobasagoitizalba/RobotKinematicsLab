package com.robotkinematicslab.mobile.diagnostics.benchmark.validation

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class DiagnosticTopologyAuditorTest {

    private val auditor =
        DiagnosticTopologyAuditor()

    @Test
    fun validSafeRevoluteRobot_isAcceptedForBenchmarkAndSafeClaim() {
        val robot =
            RobotDefinition(
                name = "Valid 3R Test Robot",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.10, a = 0.30, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.00, a = 0.25, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.00, a = 0.20, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition(
                            name = "J1",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        ),
                        JointDefinition(
                            name = "J2",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        ),
                        JointDefinition(
                            name = "J3",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    )
            )

        val result =
            auditor.audit(robot)

        assertTrue(result.acceptedForBenchmark)
        assertTrue(result.safeModeReliabilityClaimAllowed)
        assertEquals("TOPOLOGY_ACCEPTED", result.summaryLabel)
        assertFalse(result.hasErrors)
    }

    @Test
    fun mismatchedJointAndDhCounts_isRejected() {
        val robot =
            RobotDefinition(
                name = "Invalid Count Robot",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.10, a = 0.30, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition(
                            name = "J1",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        ),
                        JointDefinition(
                            name = "J2",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    )
            )

        val result =
            auditor.audit(robot)

        assertFalse(result.acceptedForBenchmark)
        assertFalse(result.safeModeReliabilityClaimAllowed)
        assertTrue(result.hasErrors)
        assertTrue(
            result.issues.any {
                it.code == DiagnosticTopologyAuditCode.JOINT_DH_COUNT_MISMATCH
            }
        )
    }

    @Test
    fun robotAboveTenLinks_isAcceptedButExperimentalOnly() {
        val linkCount =
            11

        val robot =
            RobotDefinition(
                name = "Experimental 11R Robot",
                dhParameters =
                    List(linkCount) {
                        DHParameter(theta = 0.0, d = 0.00, a = 0.15, alpha = 0.0)
                    },
                joints =
                    List(linkCount) { index ->
                        JointDefinition(
                            name = "J${index + 1}",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    }
            )

        val result =
            auditor.audit(robot)

        assertTrue(result.acceptedForBenchmark)
        assertFalse(result.safeModeReliabilityClaimAllowed)
        assertTrue(result.hasWarnings)
        assertTrue(
            result.issues.any {
                it.code == DiagnosticTopologyAuditCode.LINK_COUNT_EXPERIMENTAL
            }
        )
    }

    @Test
    fun degenerateNearZeroRobot_isRejected() {
        val robot =
            RobotDefinition(
                name = "Degenerate Robot",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.0, a = 0.0, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.0, a = 0.0, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition(
                            name = "J1",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        ),
                        JointDefinition(
                            name = "J2",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    )
            )

        val result =
            auditor.audit(robot)

        assertFalse(result.acceptedForBenchmark)
        assertFalse(result.safeModeReliabilityClaimAllowed)
        assertTrue(result.hasErrors)
        assertTrue(
            result.issues.any {
                it.code == DiagnosticTopologyAuditCode.ALL_LINKS_ZERO_OFFSET ||
                        it.code == DiagnosticTopologyAuditCode.TOTAL_REACH_TOO_SMALL
            }
        )
    }

    @Test
    fun homeValueOutsideLimits_isRejected() {
        val robot =
            RobotDefinition(
                name = "Home Outside Limits Robot",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.10, a = 0.30, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.00, a = 0.25, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition(
                            name = "J1",
                            type = JointType.REVOLUTE,
                            minValue = -1.0,
                            maxValue = 1.0,
                            homeValue = 2.0
                        ),
                        JointDefinition(
                            name = "J2",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    )
            )

        val result =
            auditor.audit(robot)

        assertFalse(result.acceptedForBenchmark)
        assertFalse(result.safeModeReliabilityClaimAllowed)
        assertTrue(result.hasErrors)
        assertTrue(
            result.issues.any {
                it.code == DiagnosticTopologyAuditCode.HOME_VALUE_OUTSIDE_LIMITS
            }
        )
    }
}
