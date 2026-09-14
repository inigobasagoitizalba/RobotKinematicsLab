package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.ik.InverseKinematicsSolver
import kotlin.math.hypot

/**
 * Neural warm start with an independently verified deterministic safety envelope.
 * A neural prediction is never reported as a one-micron result until fresh FK proves it.
 */
class VerifiedOneMicronIkEngine(
    private val storedModel: StoredOneMicronIkModel,
    private val fkSolver: ForwardKinematicsSolver = ForwardKinematicsSolver()
) {
    private val preparer = OneMicronIkDatasetPreparer()

    fun solve(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): VerifiedOneMicronIkResult {
        val started = System.nanoTime()
        val neural = predictState(robot, initialState, target)
        val inferenceNanos = System.nanoTime() - started
        val neuralError = cartesianError(robot, neural, target)
        if (OneMicronVerificationCriterion.acceptsPositionResidual(neuralError)) {
            return VerifiedOneMicronIkResult(
                neural,
                VerifiedIkPath.NEURAL_DIRECT,
                true,
                neuralError,
                neuralError,
                0,
                0,
                inferenceNanos,
                "Neural proposal independently verified at or below 1 µm."
            )
        }

        val solver = InverseKinematicsSolver(fkSolver, storedModel.solverConfig.copy(tolerance = ONE_MICRON_METERS))
        val refinement = solver.solve(robot, neural, target)
        val refinedError = cartesianError(robot, refinement.state, target)
        if (refinement.isSuccessful() && OneMicronVerificationCriterion.acceptsPositionResidual(refinedError)) {
            return VerifiedOneMicronIkResult(
                refinement.state,
                VerifiedIkPath.NEURAL_REFINED,
                true,
                refinedError,
                neuralError,
                refinement.iterations,
                0,
                inferenceNanos,
                "Neural proposal refined and independently verified at or below 1 µm."
            )
        }

        val fallback = solver.solve(robot, initialState, target)
        val fallbackError = cartesianError(robot, fallback.state, target)
        if (fallback.isSuccessful() && OneMicronVerificationCriterion.acceptsPositionResidual(fallbackError)) {
            return VerifiedOneMicronIkResult(
                fallback.state,
                VerifiedIkPath.DETERMINISTIC_FALLBACK,
                true,
                fallbackError,
                neuralError,
                refinement.iterations,
                fallback.iterations,
                inferenceNanos,
                "Neural basin did not certify; deterministic fallback produced a verified 1 µm result."
            )
        }

        val candidates = listOf(neural to neuralError, refinement.state to refinedError, fallback.state to fallbackError)
        val best = candidates.filter { it.second.isFinite() }.minByOrNull { it.second } ?: (initialState to Double.NaN)
        return VerifiedOneMicronIkResult(
            best.first,
            VerifiedIkPath.FAILED,
            false,
            best.second,
            neuralError,
            refinement.iterations,
            fallback.iterations,
            inferenceNanos,
            "No candidate satisfied the independently checked 1 µm contract."
        )
    }

    fun predictState(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): RobotState {
        require(robot.joints.size <= storedModel.model.outputCount)
        val complete =
            OneMicronIkFeatureEncoder.encode(
                robot,
                initialState,
                target,
                storedModel.solverConfig,
                storedModel.profile
            )
        val completeNames = OneMicronIkFeatureEncoder.featureNames(storedModel.profile)
        val indices = storedModel.featureNames.map { name ->
            completeNames.indexOf(name).takeIf { it >= 0 }
                ?: error("Stored neural-IK variable '$name' is absent from the source encoder.")
        }
        val raw = FloatArray(indices.size) { index -> complete[indices[index]] }
        require(raw.size == storedModel.featureNames.size)
        val normalized = preparer.normalize(raw, storedModel.normalization)
        val delta = storedModel.model.predict(normalized)
        return RobotState(
            robot.joints.mapIndexed { index, joint ->
                val span = joint.maxValue - joint.minValue
                (initialState.jointValues[index] + delta[index] * span).coerceIn(joint.minValue, joint.maxValue)
            }
        )
    }

    fun cartesianError(robot: RobotDefinition, state: RobotState, target: Vec3): Double {
        val fk = fkSolver.solve(robot, state)
        if (fk.status != FKStatus.SUCCESS && fk.status != FKStatus.SUCCESS_WITH_WARNING) return Double.POSITIVE_INFINITY
        return hypot(
            hypot(fk.endEffectorPosition.x - target.x, fk.endEffectorPosition.y - target.y),
            fk.endEffectorPosition.z - target.z
        )
    }

    private fun com.robotkinematicslab.mobile.domain.result.IKResult.isSuccessful(): Boolean =
        converged && (status == IKStatus.SUCCESS || status == IKStatus.SUCCESS_WITH_WARNING) &&
            finalError.isFinite() && finalError <= ONE_MICRON_METERS
}
