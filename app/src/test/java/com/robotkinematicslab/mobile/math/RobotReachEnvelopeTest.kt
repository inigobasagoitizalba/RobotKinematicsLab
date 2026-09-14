package com.robotkinematicslab.mobile.math

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.service.KinematicsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotReachEnvelopeTest {

    @Test
    fun prismaticBoundUsesActiveJointLimitsRatherThanIgnoredDhD() {
        val robot =
            RobotDefinition(
                name = "Prismatic bound counterexample",
                dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 0.0, alpha = 0.0)),
                joints = listOf(JointDefinition("P1", JointType.PRISMATIC, 99.0, 100.0, 99.0))
            )

        assertEquals(100.0, RobotReachEnvelope.conservativeRadialUpperBound(robot), 0.0)
        val fk = KinematicsService().computeFK(robot, RobotState(listOf(100.0)))
        assertEquals(FKStatus.SUCCESS, fk.status)
        assertEquals(100.0, fk.endEffectorPosition.norm(), 1e-12)
    }

    @Test
    fun randomizedFkNeverEscapesConservativeEnvelope() {
        val factory = DiagnosticRobotFactory()
        val random = ScientificRandom(0x524b4cL)
        var checked = 0

        for (links in 2..10) {
            DiagnosticJointMode.entries.forEach { mode ->
                val robot = factory.buildSeedRobot(links, mode, stressLevel = 0.73)
                val bound = RobotReachEnvelope.conservativeRadialUpperBound(robot)
                repeat(25) {
                    val state =
                        RobotState(
                            robot.joints.map { joint ->
                                random.nextDouble(joint.minValue, joint.maxValue)
                            }
                        )
                    val fk = KinematicsService().computeFK(robot, state)
                    assertTrue("FK must be usable for $links/$mode", fk.status == FKStatus.SUCCESS || fk.status == FKStatus.SUCCESS_WITH_WARNING)
                    assertTrue("Reach proof violated for $links/$mode", fk.endEffectorPosition.norm() <= bound + 1e-12)
                    checked++
                }
            }
        }

        assertEquals(900, checked)
    }
}
