package com.robotkinematicslab.mobile.diagnostics.benchmark.generation

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.validation.DiagnosticTopologyAuditCode
import com.robotkinematicslab.mobile.diagnostics.benchmark.validation.DiagnosticTopologyAuditor

import com.robotkinematicslab.mobile.domain.JointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRobotFactoryTest {

    private val factory =
        DiagnosticRobotFactory()

    private val auditor =
        DiagnosticTopologyAuditor()

    @Test
    fun autoMode_twoLinkRobot_staysRevoluteOnlyAndPassesAudit() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 2,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(2, robot.joints.size)
        assertEquals(2, robot.dhParameters.size)
        assertTrue(robot.joints.all { it.type == JointType.REVOLUTE })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun autoMode_threeLinkRobot_staysRevoluteOnlyAndPassesAudit() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 3,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(3, robot.joints.size)
        assertEquals(3, robot.dhParameters.size)
        assertTrue(robot.joints.all { it.type == JointType.REVOLUTE })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun autoMode_sixLinkRobot_startsWithRevoluteBaseAndIncludesPrismaticJoints() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 6,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(6, robot.joints.size)
        assertEquals(6, robot.dhParameters.size)
        assertEquals(JointType.REVOLUTE, robot.joints.first().type)
        assertTrue(robot.joints.any { it.type == JointType.PRISMATIC })
        assertTrue(robot.joints.count { it.type == JointType.REVOLUTE } >
                robot.joints.count { it.type == JointType.PRISMATIC })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun mixedMode_startsWithRevoluteBaseAndIncludesBothJointTypes() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 6,
                jointMode = DiagnosticJointMode.MIXED,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(6, robot.joints.size)
        assertEquals(6, robot.dhParameters.size)
        assertEquals(JointType.REVOLUTE, robot.joints.first().type)
        assertTrue(robot.joints.any { it.type == JointType.REVOLUTE })
        assertTrue(robot.joints.any { it.type == JointType.PRISMATIC })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun revoluteOnlyMode_createsOnlyRevoluteJointsAndPassesAudit() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 8,
                jointMode = DiagnosticJointMode.REVOLUTE_ONLY,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(8, robot.joints.size)
        assertEquals(8, robot.dhParameters.size)
        assertTrue(robot.joints.all { it.type == JointType.REVOLUTE })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun prismaticOnlyMode_createsOnlyPrismaticJointsAndPassesAuditWithWarningsAllowed() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 5,
                jointMode = DiagnosticJointMode.PRISMATIC_ONLY,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(5, robot.joints.size)
        assertEquals(5, robot.dhParameters.size)
        assertTrue(robot.joints.all { it.type == JointType.PRISMATIC })
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
        assertFalse(audit.hasErrors)
    }

    @Test
    fun safeLinkCountBelowTwo_isClampedToTwo() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 1,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(2, robot.joints.size)
        assertEquals(2, robot.dhParameters.size)
        assertTrue(audit.acceptedForBenchmark)
        assertTrue(audit.safeModeReliabilityClaimAllowed)
    }

    @Test
    fun experimentalLinkCountAboveTen_isGeneratedAndMarkedExperimentalByAudit() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 12,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(12, robot.joints.size)
        assertEquals(12, robot.dhParameters.size)
        assertTrue(audit.acceptedForBenchmark)
        assertFalse(audit.safeModeReliabilityClaimAllowed)
        assertTrue(
            audit.issues.any {
                it.code == DiagnosticTopologyAuditCode.LINK_COUNT_EXPERIMENTAL
            }
        )
    }

    @Test
    fun extremeExperimentalLinkCount_isClampedToOneHundred() {
        val robot =
            factory.buildSeedRobot(
                linkCount = 500,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 0.5
            )

        val audit =
            auditor.audit(robot)

        assertEquals(100, robot.joints.size)
        assertEquals(100, robot.dhParameters.size)
        assertTrue(audit.acceptedForBenchmark)
        assertFalse(audit.safeModeReliabilityClaimAllowed)
    }

    @Test
    fun stressLevelOutsideRange_isClampedAndStillProducesAuditValidRobot() {
        val lowStressRobot =
            factory.buildSeedRobot(
                linkCount = 6,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = -10.0
            )

        val highStressRobot =
            factory.buildSeedRobot(
                linkCount = 6,
                jointMode = DiagnosticJointMode.AUTO,
                stressLevel = 10.0
            )

        val lowAudit =
            auditor.audit(lowStressRobot)

        val highAudit =
            auditor.audit(highStressRobot)

        assertTrue(lowAudit.acceptedForBenchmark)
        assertTrue(highAudit.acceptedForBenchmark)
        assertFalse(lowAudit.hasErrors)
        assertFalse(highAudit.hasErrors)
    }
}
