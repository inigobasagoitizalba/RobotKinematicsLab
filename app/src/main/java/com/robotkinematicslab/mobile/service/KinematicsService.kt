package com.robotkinematicslab.mobile.service

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.record.ExecutionRecord
import com.robotkinematicslab.mobile.domain.record.FailureRecord
import com.robotkinematicslab.mobile.domain.record.MLRecord
import com.robotkinematicslab.mobile.domain.record.OperationType
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.ik.InverseKinematicsSolver

class KinematicsService(
    private val ikConfig: IKConfig = IKConfig()
) {

    companion object {
        private const val TAG = "KinematicsService"
    }

    private val fkSolver = ForwardKinematicsSolver()
    private val ikSolver = InverseKinematicsSolver(fkSolver, ikConfig)

    init {
        AppLog.d(TAG) {
            "🚀 KinematicsService initialized | ikConfig={maxIterations=${ikConfig.maxIterations}, tolerance=${ikConfig.tolerance}, damping=${ikConfig.damping}, maxStep=${ikConfig.maxStep}}"
        }
    }

    /**
     * Service calls deliberately preserve the caller's state. Validation belongs
     * to the solver and must happen before indexing, clamping or angle wrapping;
     * silently mutating invalid scientific input would destroy provenance.
     */
    private fun preserveInputState(robot: RobotDefinition, state: RobotState): RobotState {
        AppLog.d(TAG) {
            "🧾 Preserving input state for solver validation | robot=${robot.name}, inputState=${state.jointValues}"
        }
        return state
    }

    fun computeFK(
        robot: RobotDefinition,
        state: RobotState
    ): FKResult {
        AppLog.d(TAG) {
            "📥 computeFK called | robot=${robot.name}, inputState=${state.jointValues}"
        }

        val preservedState = preserveInputState(robot, state)

        AppLog.d(TAG) {
            "🧩 computeFK preserving state for validation | state=${preservedState.jointValues}"
        }

        val result = fkSolver.solve(robot, preservedState)

        AppLog.d(TAG) {
            "📤 computeFK finished | status=${result.status}, endEffector=(${result.endEffectorPosition.x}, ${result.endEffectorPosition.y}, ${result.endEffectorPosition.z}), jointPositionsCount=${result.jointPositions.size}"
        }

        return result
    }

    fun computeIK(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): IKResult {
        AppLog.d(TAG) {
            "📥 computeIK called | robot=${robot.name}, inputState=${initialState.jointValues}, target=(${target.x}, ${target.y}, ${target.z})"
        }

        val preservedState = preserveInputState(robot, initialState)

        AppLog.d(TAG) {
            "🧩 computeIK preserving state for validation | state=${preservedState.jointValues}"
        }

        val result = ikSolver.solve(robot, preservedState, target)

        AppLog.d(TAG) {
            "📤 computeIK finished | status=${result.status}, converged=${result.converged}, iterations=${result.iterations}, finalError=${result.finalError}, resultState=${result.state.jointValues}"
        }

        return result
    }

    fun computeFKRecord(
        robot: RobotDefinition,
        state: RobotState
    ): ExecutionRecord {

        AppLog.d(TAG) {
            "📝 computeFKRecord called | robot=${robot.name}, inputState=${state.jointValues}"
        }

        val preservedState = preserveInputState(robot, state)
        val result = fkSolver.solve(robot, preservedState)

        val record = ExecutionRecord(
            operationType = OperationType.FK,
            robotName = robot.name,
            inputState = preservedState,
            status = result.status.name,
            metadata = result.metadata
        )

        AppLog.d(TAG) {
            "✅ computeFKRecord finished | status=${record.status}, operationType=${record.operationType}, robot=${record.robotName}"
        }

        return record
    }

    fun computeIKRecord(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): ExecutionRecord {

        AppLog.d(TAG) {
            "📝 computeIKRecord called | robot=${robot.name}, inputState=${initialState.jointValues}, target=(${target.x}, ${target.y}, ${target.z})"
        }

        val preservedState = preserveInputState(robot, initialState)
        val result = ikSolver.solve(robot, preservedState, target)

        val record = ExecutionRecord(
            operationType = OperationType.IK,
            robotName = robot.name,
            inputState = preservedState,
            targetPosition = target,
            status = result.status.name,
            finalError = result.finalError.takeIf { it.isFinite() },
            iterations = result.iterations,
            metadata = result.metadata
        )

        AppLog.d(TAG) {
            "✅ computeIKRecord finished | status=${record.status}, iterations=${record.iterations}, finalError=${record.finalError}"
        }

        return record
    }

    fun computeFKMLRecord(
        robot: RobotDefinition,
        state: RobotState
    ): MLRecord {
        AppLog.d(TAG) {
            "🤖 computeFKMLRecord called | robot=${robot.name}, inputState=${state.jointValues}"
        }

        val preservedState = preserveInputState(robot, state)
        AppLog.d(TAG) {
            "🧩 computeFKMLRecord preserving state for validation | state=${preservedState.jointValues}"
        }

        val result = fkSolver.solve(robot, preservedState)

        AppLog.d(TAG) {
            "📍 FK result for ML record | status=${result.status}, endEffector=(${result.endEffectorPosition.x}, ${result.endEffectorPosition.y}, ${result.endEffectorPosition.z})"
        }

        val input = preservedState.jointValues

        val output = if (
            result.status == FKStatus.SUCCESS ||
            result.status == FKStatus.SUCCESS_WITH_WARNING
        ) {
            listOf(
                result.endEffectorPosition.x,
                result.endEffectorPosition.y,
                result.endEffectorPosition.z
            )
        } else {
            listOf(0.0, 0.0, 0.0)
        }

        AppLog.d(TAG) {
            "🧮 FK ML vectors built | input=$input, output=$output"
        }

        val encodedStatus = encodeFKStatus(result.status)

        val record = MLRecord(
            input = input,
            output = output,
            status = encodedStatus,
            error = if (
                result.status == FKStatus.SUCCESS ||
                result.status == FKStatus.SUCCESS_WITH_WARNING
            ) 0.0 else Double.NaN,
            iterations = 0
        )

        AppLog.d(TAG) {
            "✅ computeFKMLRecord finished | encodedStatus=${record.status}, error=${record.error}, iterations=${record.iterations}"
        }

        return record
    }

    fun computeIKMLRecord(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): MLRecord {
        AppLog.d(TAG) {
            "🤖 computeIKMLRecord called | robot=${robot.name}, inputState=${initialState.jointValues}, target=(${target.x}, ${target.y}, ${target.z})"
        }

        val preservedState = preserveInputState(robot, initialState)

        AppLog.d(TAG) {
            "🧩 computeIKMLRecord preserving state for validation | state=${preservedState.jointValues}"
        }

        val result = ikSolver.solve(robot, preservedState, target)

        AppLog.d(TAG) {
            "📍 IK result for ML record | status=${result.status}, resultState=${result.state.jointValues}, finalError=${result.finalError}, iterations=${result.iterations}"
        }

        val input = preservedState.jointValues + listOf(
            target.x,
            target.y,
            target.z
        )

        val output = if (
            result.status == IKStatus.SUCCESS ||
            result.status == IKStatus.SUCCESS_WITH_WARNING ||
            result.status == IKStatus.MAX_ITERATIONS_REACHED ||
            result.status == IKStatus.NO_CONVERGENCE
        ) {
            result.state.jointValues
        } else {
            List(robot.joints.size) { 0.0 }
        }

        AppLog.d(TAG) {
            "🧮 IK ML vectors built | input=$input, output=$output"
        }

        val encodedStatus = encodeIKStatus(result.status)

        val record = MLRecord(
            input = input,
            output = output,
            status = encodedStatus,
            error = result.finalError.takeIf { it.isFinite() } ?: Double.NaN,
            iterations = result.iterations
        )

        AppLog.d(TAG) {
            "✅ computeIKMLRecord finished | encodedStatus=${record.status}, error=${record.error}, iterations=${record.iterations}"
        }

        return record
    }

    fun computeFKFailure(
        robot: RobotDefinition,
        state: RobotState
    ): FailureRecord? {

        AppLog.d(TAG) {
            "⚠️ computeFKFailure called | robot=${robot.name}, inputState=${state.jointValues}"
        }

        val preservedState = preserveInputState(robot, state)
        val result = fkSolver.solve(robot, preservedState)

        if (result.status == FKStatus.SUCCESS || result.status == FKStatus.SUCCESS_WITH_WARNING) {
            AppLog.d(TAG) {
                "✅ computeFKFailure returning null because FK succeeded"
            }
            return null
        }

        val failure = FailureRecord(
            operationType = OperationType.FK,
            robotName = robot.name,
            inputState = preservedState,
            failureType = result.status.name,
            message = buildFkFailureMessage(result.status)
        )

        AppLog.w(TAG) {
            "❌ computeFKFailure created failure record | failureType=${failure.failureType}, message=${failure.message}"
        }

        return failure
    }

    fun computeIKFailure(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): FailureRecord? {

        AppLog.d(TAG) {
            "⚠️ computeIKFailure called | robot=${robot.name}, inputState=${initialState.jointValues}, target=(${target.x}, ${target.y}, ${target.z})"
        }

        val preservedState = preserveInputState(robot, initialState)
        val result = ikSolver.solve(robot, preservedState, target)

        if (result.status == IKStatus.SUCCESS || result.status == IKStatus.SUCCESS_WITH_WARNING) {
            AppLog.d(TAG) {
                "✅ computeIKFailure returning null because IK succeeded"
            }
            return null
        }

        val failure = FailureRecord(
            operationType = OperationType.IK,
            robotName = robot.name,
            inputState = preservedState,
            targetPosition = target,
            failureType = result.status.name,
            message = buildIkFailureMessage(result)
        )

        AppLog.w(TAG) {
            "❌ computeIKFailure created failure record | failureType=${failure.failureType}, message=${failure.message}, finalError=${result.finalError}, iterations=${result.iterations}"
        }

        return failure
    }

    private fun buildFkFailureMessage(status: FKStatus): String {
        return when (status) {
            FKStatus.INVALID_INPUT -> "FK computation was blocked because the robot or joint state input was invalid."
            FKStatus.NUMERICAL_FAILURE -> "FK computation produced an output that failed numerical validation."
            FKStatus.SUCCESS -> "FK computation succeeded."
            FKStatus.SUCCESS_WITH_WARNING -> "FK computation succeeded with a repair warning."
        }
    }

    private fun buildIkFailureMessage(result: IKResult): String {
        val detail = buildIkDetailSuffix(
            finalError = result.finalError,
            iterations = result.iterations
        )

        return when (result.status) {
            IKStatus.INVALID_INPUT ->
                "IK computation was blocked because the robot, state, or target input was invalid."
            IKStatus.NO_CONVERGENCE ->
                "IK computation stalled without meaningful convergence$detail."
            IKStatus.MAX_ITERATIONS_REACHED ->
                "IK computation reached the iteration limit without full convergence$detail."
            IKStatus.NUMERICAL_FAILURE ->
                "IK computation failed because of a numerical or output-validation issue$detail."
            IKStatus.SUCCESS ->
                "IK computation succeeded."
            IKStatus.SUCCESS_WITH_WARNING ->

                "IK computation succeeded with a near-singularity warning$detail."

        }

    }

    private fun buildIkDetailSuffix(
        finalError: Double,
        iterations: Int
    ): String {
        val parts = mutableListOf<String>()

        if (finalError.isFinite()) {
            parts.add("finalError=$finalError")
        }

        parts.add("iterations=$iterations")

        return if (parts.isEmpty()) {
            ""
        } else {
            " (${parts.joinToString(", ")})"
        }
    }

    private fun encodeFKStatus(status: FKStatus): Int {
        AppLog.d(TAG) { "🔢 encodeFKStatus called | status=$status" }

        val encoded = when (status) {
            FKStatus.SUCCESS -> 1
            FKStatus.SUCCESS_WITH_WARNING -> 2
            FKStatus.INVALID_INPUT -> 0
            FKStatus.NUMERICAL_FAILURE -> -1
        }

        AppLog.d(TAG) { "✅ encodeFKStatus finished | status=$status, encoded=$encoded" }

        return encoded
    }

    private fun encodeIKStatus(status: IKStatus): Int {
        AppLog.d(TAG) { "🔢 encodeIKStatus called | status=$status" }

        val encoded = when (status) {
            IKStatus.SUCCESS -> 1
            IKStatus.SUCCESS_WITH_WARNING -> 2
            IKStatus.MAX_ITERATIONS_REACHED -> 0
            IKStatus.NO_CONVERGENCE -> -1
            IKStatus.INVALID_INPUT -> -2
            IKStatus.NUMERICAL_FAILURE -> -3
        }

        AppLog.d(TAG) { "✅ encodeIKStatus finished | status=$status, encoded=$encoded" }

        return encoded
    }
}
