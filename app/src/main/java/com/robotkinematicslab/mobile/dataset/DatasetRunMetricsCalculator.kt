package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticRunMetrics
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticRunMetricsCalculator
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.service.KinematicsService

typealias DatasetRunMetrics = DiagnosticRunMetrics

class DatasetRunMetricsCalculator(
    val policy: DiagnosticMetricPolicy = DiagnosticMetricPolicy(),
    private val delegate: DiagnosticRunMetricsCalculator =
        DiagnosticRunMetricsCalculator(policy)
) {

    fun calculate(
        kinematicsService: KinematicsService,
        robot: RobotDefinition,
        seedState: RobotState,
        target: Vec3,
        result: IKResult,
        maxIterations: Int
    ): DatasetRunMetrics {
        val seedFk = kinematicsService.computeFK(robot, seedState)

        return delegate.calculate(
            robot = robot,
            seedState = seedState,
            solutionState = result.state,
            initialError = delegate.calculateInitialError(seedFk, target),
            finalError = result.finalError,
            iterations = result.iterations,
            maxIterations = maxIterations,
            solverAccepted =
                result.status == IKStatus.SUCCESS ||
                    result.status == IKStatus.SUCCESS_WITH_WARNING,
            solverDiagnostics = result.diagnostics
        )
    }
}
