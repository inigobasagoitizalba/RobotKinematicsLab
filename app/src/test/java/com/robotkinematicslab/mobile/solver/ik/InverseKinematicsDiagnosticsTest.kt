package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InverseKinematicsDiagnosticsTest {

    @Test
    fun validSolve_capturesSolverDiagnostics() {
        val robot =
            DiagnosticRobotFactory().buildSeedRobot(
                linkCount = 3,
                jointMode = DiagnosticJointMode.REVOLUTE_ONLY,
                stressLevel = 0.5
            )

        val solver =
            InverseKinematicsSolver(
                fkSolver = ForwardKinematicsSolver(),
                config =
                    IKConfig(
                        maxIterations = 2,
                        tolerance = 1e-9,
                        damping = 0.05,
                        maxStep = 0.02
                    )
            )

        val result =
            solver.solve(
                robot = robot,
                initialState =
                    RobotState(
                        jointValues = robot.joints.map { it.homeValue }
                    ),
                target = Vec3.ZERO
            )

        assertFalse(result.diagnostics.seedConditionNumber.isNaN())
        assertTrue(result.diagnostics.backtrackingRetryCount >= 0)
        assertTrue(result.diagnostics.solveDurationNanos > 0L)
    }
}
