package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathematicalReliabilityMonteCarloTest {

    @Test
    fun fkProvenReachableTargetsNeverProduceFalseSuccessAndMeetRecoveryFloor() {
        val factory = DiagnosticRobotFactory()
        val fk = ForwardKinematicsSolver()
        val tolerance = 1e-5
        var total = 0
        var accepted = 0

        listOf(3, 5, 10).forEach { linkCount ->
            DiagnosticJointMode.entries.forEach { mode ->
                val robot = factory.buildSeedRobot(linkCount, mode, 0.5)
                val service =
                    KinematicsService(
                        IKConfig(
                            maxIterations = 800,
                            tolerance = tolerance,
                            damping = 0.05,
                            maxStep = 0.02
                        )
                    )
                val home = RobotState(robot.joints.map { it.homeValue })
                val random =
                    ScientificRandom(
                        ScientificRandomProtocol.deriveSeed(
                            20260904,
                            linkCount.toString(),
                            mode.name,
                            "reliability-monte-carlo"
                        )
                    )
                var groupAccepted = 0

                repeat(SAMPLES_PER_GROUP) {
                    val source =
                        robot.joints.map { joint ->
                            random.nextDouble(joint.minValue, joint.maxValue)
                        }
                    val target = fk.solvePositionOnly(robot, source).position
                    val result = service.computeIK(robot, home, target)
                    val reproduced = fk.solvePositionOnly(robot, result.state.jointValues).position
                    val independentlyRecomputedResidual = reproduced.minus(target).norm()
                    val isAccepted =
                        result.status == IKStatus.SUCCESS ||
                            result.status == IKStatus.SUCCESS_WITH_WARNING

                    assertTrue(result.state.jointValues.all(Double::isFinite))
                    result.state.jointValues.forEachIndexed { index, value ->
                        assertTrue(value in robot.joints[index].minValue..robot.joints[index].maxValue)
                    }
                    assertEquals(independentlyRecomputedResidual, result.finalError, 1e-12)
                    if (isAccepted) {
                        assertTrue(
                            "Accepted residual $independentlyRecomputedResidual exceeds $tolerance",
                            independentlyRecomputedResidual <= tolerance * 1.000_001
                        )
                        groupAccepted++
                        accepted++
                    }
                    total++
                }

                println(
                    "MATH_RELIABILITY|links=$linkCount|mode=$mode|accepted=$groupAccepted|" +
                        "total=$SAMPLES_PER_GROUP|rate=${groupAccepted.toDouble() / SAMPLES_PER_GROUP}"
                )
            }
        }

        val recoveryRate = accepted.toDouble() / total
        println("MATH_RELIABILITY|overall_accepted=$accepted|total=$total|rate=$recoveryRate")
        assertTrue("Reachable recovery $recoveryRate is below the declared 80% floor.", recoveryRate >= 0.80)
    }

    companion object {
        private const val SAMPLES_PER_GROUP = 50
    }
}
