package com.robotkinematicslab.mobile.diagnostics.metrics

import com.robotkinematicslab.mobile.dataset.DatasetRunMetricsCalculator
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKDiagnostics
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.service.KinematicsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRunMetricsCalculatorTest {

    private val robot =
        RobotDefinition(
            name = "Single revolute link",
            dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = 0.0)),
            joints =
                listOf(
                    JointDefinition(
                        name = "J1",
                        type = JointType.REVOLUTE,
                        minValue = -1.0,
                        maxValue = 1.0,
                        homeValue = 0.0
                    )
                )
        )

    @Test
    fun customPolicy_controlsClassificationAndLimitPressure() {
        val calculator =
            DiagnosticRunMetricsCalculator(
                policy =
                    DiagnosticMetricPolicy(
                        nearSuccessErrorMeters = 0.02,
                        closeMissErrorMeters = 0.03,
                        jointLimitMarginRatio = 0.10
                    )
            )

        val metrics =
            calculator.calculate(
                robot = robot,
                seedState = RobotState(listOf(0.0)),
                solutionState = RobotState(listOf(0.85)),
                initialError = 0.5,
                finalError = 0.01,
                iterations = 5,
                maxIterations = 10,
                solverAccepted = false,
                solverDiagnostics = IKDiagnostics(seedConditionNumber = 10.0)
            )

        assertEquals(DiagnosticProgressClass.NEAR_SOLVED, metrics.progressClass)
        assertEquals(1, metrics.nearLimitJointCount)
        assertEquals(listOf("J1"), metrics.nearLimitJointNames)
        assertEquals(1.0, metrics.jointLimitPressureRatio, 1e-12)
    }

    @Test
    fun mismatchedJointState_doesNotInventMovementMetrics() {
        val metrics =
            DiagnosticRunMetricsCalculator().calculate(
                robot = robot,
                seedState = RobotState(listOf(0.0)),
                solutionState = RobotState(emptyList()),
                initialError = 0.5,
                finalError = 0.4,
                iterations = 1,
                maxIterations = 10,
                solverAccepted = false,
                solverDiagnostics = IKDiagnostics()
            )

        assertTrue(metrics.jointDeltaNorm.isNaN())
        assertTrue(metrics.maxSingleJointMovement.isNaN())
        assertTrue(metrics.jointLimitPressureRatio.isNaN())
    }

    @Test
    fun largeFiniteCoordinates_keepFiniteEuclideanMetrics() {
        val initialError =
            DiagnosticRunMetricsCalculator().calculateInitialError(
                seedFk =
                    com.robotkinematicslab.mobile.domain.result.FKResult(
                        status = com.robotkinematicslab.mobile.domain.result.FKStatus.SUCCESS,
                        endEffectorTransform = com.robotkinematicslab.mobile.math.utility.Matrix4.identity(),
                        endEffectorPosition = Vec3(1e200, 1e200, 1e200),
                        jointPositions = emptyList()
                    ),
                target = Vec3.ZERO
            )

        assertTrue(initialError.isFinite())
        assertEquals(kotlin.math.sqrt(3.0) * 1e200, initialError, 1e185)
    }

    @Test
    fun datasetAdapter_matchesSharedScientificCalculator() {
        val service = KinematicsService()
        val seed = RobotState(listOf(0.0))
        val solution = RobotState(listOf(0.4))
        val target = Vec3(0.9, 0.1, 0.0)
        val result =
            IKResult(
                state = solution,
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                iterations = 4,
                finalError = 0.2,
                detailCode = IKDetailCode.STAGNATION_WINDOW_EXCEEDED,
                diagnostics =
                    IKDiagnostics(
                        seedConditionNumber = 100.0,
                        backtrackingRetryCount = 2,
                        solveDurationNanos = 1234L
                    )
            )
        val shared = DiagnosticRunMetricsCalculator()
        val seedFk = service.computeFK(robot, seed)
        val expected =
            shared.calculate(
                robot = robot,
                seedState = seed,
                solutionState = solution,
                initialError = shared.calculateInitialError(seedFk, target),
                finalError = result.finalError,
                iterations = result.iterations,
                maxIterations = 10,
                solverAccepted = false,
                solverDiagnostics = result.diagnostics
            )
        val actual =
            DatasetRunMetricsCalculator().calculate(
                kinematicsService = service,
                robot = robot,
                seedState = seed,
                target = target,
                result = result,
                maxIterations = 10
            )

        assertEquals(expected, actual)
    }
}
