package com.robotkinematicslab.mobile.ui.robotview

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.*
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import kotlin.math.abs
import kotlin.math.hypot

internal enum class RobotViewIkPhase { INACTIVE, CALCULATING, VERIFIED, WARNING, FAILED }

/** Model identity is a loaded-file digest, not only its human run name. */
internal data class RobotViewIkContext(
    val robot: RobotDefinition,
    val target: Vec3,
    val aiModelKey: String? = null
) {
    val toleranceMeters: Double get() = if (aiModelKey == null) IKConfig().tolerance else 1e-6
}

internal data class RobotViewIkRequest(
    val generation: Long,
    val context: RobotViewIkContext,
    val initialState: RobotState
)

internal data class RobotViewIkResponse(
    val result: IKResult,
    val elapsedMillis: Double,
    val aiPath: VerifiedIkPath? = null,
    val explanation: String = "",
    val neuralProposalErrorMeters: Double? = null
)

/** The only owner of visible IK result state. Invalidation also revokes outstanding requests. */
internal data class RobotViewIkState(
    val generation: Long = 0,
    val request: RobotViewIkRequest? = null,
    val phase: RobotViewIkPhase = RobotViewIkPhase.INACTIVE,
    val response: RobotViewIkResponse? = null,
    val checkedErrorMeters: Double? = null,
    val message: String = "Choose an IK target in Robot View. No solution has been verified."
) {
    val hasResult: Boolean get() = request != null && phase in setOf(RobotViewIkPhase.VERIFIED, RobotViewIkPhase.WARNING, RobotViewIkPhase.FAILED)
    val poseToApply: RobotState? get() = response?.result?.state

    fun invalidate(reason: String = "Inputs changed. The previous IK result no longer applies.") =
        RobotViewIkState(generation = generation + 1, message = reason)

    fun begin(context: RobotViewIkContext, initialState: RobotState): RobotViewIkState {
        val target = context.target
        val next = invalidate()
        if (!RobotDefinitionValidator().validate(context.robot).isValid ||
            !RobotStateValidator().validate(context.robot, initialState).isValid ||
            !target.x.isFinite() || !target.y.isFinite() || !target.z.isFinite()) {
            return next.copy(phase = RobotViewIkPhase.FAILED, message = "IK input rejected: robot, joint state and target must be valid and finite.")
        }
        // Snapshot list ownership so a caller cannot mutate a submitted seed or mechanism in place.
        val snapshot = context.copy(robot = context.robot.copy(
            joints = context.robot.joints.toList(), dhParameters = context.robot.dhParameters.toList()))
        return next.copy(request = RobotViewIkRequest(next.generation, snapshot, RobotState(initialState.jointValues.toList())),
            phase = RobotViewIkPhase.CALCULATING, message = "Calculating IK for the current robot and target. No new solution is verified yet.")
    }

    fun fail(completedRequest: RobotViewIkRequest, reason: String): RobotViewIkState =
        if (request != completedRequest || phase != RobotViewIkPhase.CALCULATING) this
        else copy(phase = RobotViewIkPhase.FAILED, response = null, checkedErrorMeters = null, message = reason)

    fun complete(completedRequest: RobotViewIkRequest, result: RobotViewIkResponse): RobotViewIkState {
        if (request != completedRequest || phase != RobotViewIkPhase.CALCULATING) return this
        val context = completedRequest.context
        val output = result.result
        fun rejected(reason: String) = fail(completedRequest, "IK output rejected: $reason. The previous pose is retained; no solution is certified.")
        if (output.status == IKStatus.INVALID_INPUT || output.status == IKStatus.NUMERICAL_FAILURE)
            return rejected(output.status.name.lowercase().replace('_', ' '))
        if (!result.elapsedMillis.isFinite() || result.elapsedMillis < 0 || output.iterations < 0)
            return rejected("invalid solver measurements")
        if (!output.finalError.isFinite() || output.finalError < 0 ||
            !RobotStateValidator().validate(context.robot, output.state).isValid)
            return rejected("non-finite error or invalid joint state")
        val successfulStatus = output.status == IKStatus.SUCCESS || output.status == IKStatus.SUCCESS_WITH_WARNING
        if (successfulStatus != output.converged) return rejected("inconsistent convergence status")
        if ((context.aiModelKey == null) != (result.aiPath == null)) return rejected("response belongs to a different AI mode")
        if (result.aiPath != null && (result.aiPath != VerifiedIkPath.FAILED) != output.converged)
            return rejected("AI verification and execution path disagree")
        val fk = ForwardKinematicsSolver().solve(context.robot, output.state)
        if (fk.status != FKStatus.SUCCESS && fk.status != FKStatus.SUCCESS_WITH_WARNING)
            return rejected("independent FK validation failed")
        val actualError = hypot(hypot(fk.endEffectorPosition.x - context.target.x,
            fk.endEffectorPosition.y - context.target.y), fk.endEffectorPosition.z - context.target.z)
        if (!actualError.isFinite() || abs(actualError - output.finalError) > maxOf(1e-9, actualError * 1e-6))
            return rejected("reported error does not match independent FK at this target")
        if (output.converged && actualError > context.toleranceMeters)
            return rejected("the independently measured error exceeds the requested tolerance")
        if (context.aiModelKey == null && output.metadata != null &&
            output.metadata.tolerance != context.toleranceMeters)
            return rejected("solver tolerance does not match the submitted request")
        val warning = output.status == IKStatus.SUCCESS_WITH_WARNING ||
            result.aiPath == VerifiedIkPath.NEURAL_REFINED || result.aiPath == VerifiedIkPath.DETERMINISTIC_FALLBACK
        return copy(
            phase = if (!output.converged) RobotViewIkPhase.FAILED else if (warning) RobotViewIkPhase.WARNING else RobotViewIkPhase.VERIFIED,
            response = result.copy(result = output.copy(state = RobotState(output.state.jointValues.toList()))),
            checkedErrorMeters = actualError,
            message = when {
                !output.converged -> "IK did not meet the requested tolerance. The displayed pose is a checked candidate, not a verified solution. ${result.explanation}"
                result.aiPath == VerifiedIkPath.DETERMINISTIC_FALLBACK -> "AI proposal did not certify. Deterministic fallback reached the target and independent FK verified the final position. ${result.explanation}"
                result.aiPath == VerifiedIkPath.NEURAL_REFINED -> "AI proposal required deterministic refinement. Independent FK verified the final position. ${result.explanation}"
                result.aiPath == VerifiedIkPath.NEURAL_DIRECT -> "The AI proposal was independently verified by FK. Tool orientation is not certified."
                warning -> "IK reached the target with a solver warning. Independent FK verified the position."
                else -> "Deterministic IK reached the target. Independent FK verified the position."
            }
        )
    }
}

internal data class RobotViewAiAvailability(val available: Boolean, val reason: String)

internal fun robotViewAiAvailability(loading: Boolean, modelOutputCount: Int?, jointCount: Int?): RobotViewAiAvailability = when {
    loading -> RobotViewAiAvailability(false, "Checking installed position-only AI models…")
    modelOutputCount == null -> RobotViewAiAvailability(false, "No readable position-only AI model is installed. Train one in AI Training → Verified 1 µm IK, then refresh.")
    jointCount == null || jointCount <= 0 -> RobotViewAiAvailability(false, "Apply a valid robot in Robot Setup before enabling AI.")
    jointCount > modelOutputCount -> RobotViewAiAvailability(false, "This robot has more joints than the installed model supports. Deterministic IK remains available.")
    else -> RobotViewAiAvailability(true, "The AI proposes joint values; independent FK verifies the final position. Deterministic refinement or fallback may be required.")
}
