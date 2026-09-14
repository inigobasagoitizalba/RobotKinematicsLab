package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreOptimizationEquivalenceTest {

    @Test
    fun optimizedCore_isBitReproducibleAndPreservesMathematicalInvariants() {
        val factory = DiagnosticRobotFactory()
        val config = IKConfig(maxIterations = 90, tolerance = 1e-6, damping = 0.05, maxStep = 0.02)

        DiagnosticJointMode.entries.forEach { mode ->
            listOf(17, 101).forEach { seed ->
                val label = "$mode/$seed"
                val robot = factory.buildSeedRobot(5, mode, stressLevel = 0.65)
                val random = ScientificRandom(
                    ScientificRandomProtocol.deriveSeed(seed, "core-optimization-equivalence", mode.name)
                )
                val targetSource = randomState(robot, random)
                val initialState = randomState(robot, random)
                val fkSolver = ForwardKinematicsSolver()
                val targetFk = fkSolver.solve(robot, targetSource)
                assertTrue(
                    "$label FK must be usable",
                    targetFk.status == FKStatus.SUCCESS || targetFk.status == FKStatus.SUCCESS_WITH_WARNING
                )

                val first = InverseKinematicsSolver(ForwardKinematicsSolver(), config)
                    .solve(robot, initialState, targetFk.endEffectorPosition)
                val second = InverseKinematicsSolver(ForwardKinematicsSolver(), config)
                    .solve(robot, initialState, targetFk.endEffectorPosition)

                assertEquals("$label status reproducibility", first.status, second.status)
                assertEquals("$label detail reproducibility", first.detailCode, second.detailCode)
                assertEquals("$label iterations reproducibility", first.iterations, second.iterations)
                assertEquals("$label residual reproducibility", first.finalError.toRawBits(), second.finalError.toRawBits())
                assertEquals(
                    "$label joint reproducibility",
                    first.state.jointValues.map(Double::toRawBits),
                    second.state.jointValues.map(Double::toRawBits)
                )

                first.state.jointValues.forEachIndexed { index, value ->
                    assertTrue("$label joint $index is finite", value.isFinite())
                    assertTrue("$label joint $index respects limits", value in robot.joints[index].minValue..robot.joints[index].maxValue)
                }

                val verifiedFk = fkSolver.solve(robot, first.state)
                assertTrue(
                    "$label final FK must be usable",
                    verifiedFk.status == FKStatus.SUCCESS || verifiedFk.status == FKStatus.SUCCESS_WITH_WARNING
                )
                val verifiedResidual = (verifiedFk.endEffectorPosition - targetFk.endEffectorPosition).norm()
                assertEquals("$label final residual", verifiedResidual, first.finalError, 1e-12)

                val success = first.status == IKStatus.SUCCESS || first.status == IKStatus.SUCCESS_WITH_WARNING
                assertEquals("$label success/converged contract", success, first.converged)
                if (success) {
                    assertTrue("$label successful residual must meet tolerance", first.finalError <= config.tolerance)
                }
            }
        }
    }

    private fun randomState(robot: RobotDefinition, random: ScientificRandom): RobotState {
        return RobotState(
            robot.joints.map { joint -> random.nextDouble(joint.minValue, joint.maxValue) }
        )
    }
}
