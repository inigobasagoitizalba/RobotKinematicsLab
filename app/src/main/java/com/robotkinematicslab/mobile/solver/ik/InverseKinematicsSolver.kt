package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKDiagnostics
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.domain.result.SolverMetadata
import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.fk.MutableFKPositionOnlyResult
import com.robotkinematicslab.mobile.validation.input.DHParameterValidator
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.input.TargetPositionValidator
import com.robotkinematicslab.mobile.validation.output.KinematicsOutputValidator
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

class InverseKinematicsSolver(
    private val fkSolver: ForwardKinematicsSolver,
    private val config: IKConfig = IKConfig()
) {

    companion object {
        private const val TAG = "InverseKinematics"

        private const val STAGNATION_IMPROVEMENT_EPS = 1e-10
        private const val STAGNATION_WINDOW_COARSE = 12
        private const val STAGNATION_WINDOW_PRECISION = 40

        private const val JACOBIAN_DELTA = 1e-6
        private const val CHOLESKY_RELATIVE_FLOOR = 1e-15

        private const val BACKTRACK_ATTEMPTS = 12
        private const val BACKTRACK_SHRINK = 0.5

        private const val PRECISION_MODE_THRESHOLD_METERS = 0.001
        private const val PRECISION_MAX_STEP_SCALE = 0.20
        private const val PRECISION_DAMPING_SCALE = 0.35

        private const val COARSE_EXPLORATORY_ERROR_GROWTH_ABSOLUTE = 0.002
        private const val COARSE_EXPLORATORY_ERROR_GROWTH_RATIO = 0.02

        private const val PRECISION_EXPLORATORY_ERROR_GROWTH_ABSOLUTE = 1e-7
        private const val PRECISION_EXPLORATORY_ERROR_GROWTH_RATIO = 0.001
    }

    private val robotValidator = RobotDefinitionValidator()
    private val stateValidator = RobotStateValidator()
    private val targetValidator = TargetPositionValidator()
    private val dhValidator = DHParameterValidator()
    private val outputValidator = KinematicsOutputValidator()
    private val nearSingularityAnalyzer = NearSingularityAnalyzer()

    fun solve(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): IKResult {
        val startedAtNanos = System.nanoTime()
        val result =
            solveInternal(
                robot = robot,
                initialState = initialState,
                target = target
            )

        return result.copy(
            diagnostics =
                result.diagnostics.copy(
                    solveDurationNanos =
                        (System.nanoTime() - startedAtNanos)
                            .coerceAtLeast(0L)
                )
        )
    }

    private fun solveInternal(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3
    ): IKResult {

        // Failure results are observable API objects too. Never echo NaN/Infinity
        // from an untrusted input into a result that could later be logged, stored or
        // accidentally reused by a caller that correctly sees the failure status.
        val quarantinedInitialState = quarantineFailureState(robot, initialState)

        AppLog.d(TAG) {
            "🚀 IK solve started | robot=${robot.name}, initialState=${initialState.jointValues}, target=(${target.x}, ${target.y}, ${target.z}), config={maxIterations=${config.maxIterations}, tolerance=${config.tolerance}, damping=${config.damping}, maxStep=${config.maxStep}}"
        }

        val metadata = SolverMetadata.fromIKConfig(config)

        if (
            config.maxIterations <= 0 ||
            !config.tolerance.isFinite() || config.tolerance <= 0.0 ||
            !config.damping.isFinite() || config.damping <= 0.0 ||
            !config.maxStep.isFinite() || config.maxStep <= 0.0
        ) {
            AppLog.e(TAG) { "❌ IK configuration is invalid | config=$config" }
            return IKResult(
                state = quarantinedInitialState,
                status = IKStatus.INVALID_INPUT,
                converged = false,
                iterations = 0,
                finalError = Double.NaN,
                detailCode = IKDetailCode.INVALID_CONFIGURATION,
                metadata = metadata
            )
        }

        AppLog.d(TAG) {
            "🧾 Metadata created | solverName=${metadata.solverName}, maxIterations=${metadata.maxIterations}, tolerance=${metadata.tolerance}, damping=${metadata.damping}, maxStep=${metadata.maxStep}"
        }

        AppLog.d(TAG) { "🔎 Validating robot definition" }
        val robotValidation = robotValidator.validate(robot)
        if (!robotValidation.isValid) {
            AppLog.e(TAG) {
                "❌ Robot definition validation failed | issues=${robotValidation.issues}"
            }
            return IKResult(
                state = quarantinedInitialState,
                status = IKStatus.INVALID_INPUT,
                converged = false,
                iterations = 0,
                finalError = Double.NaN,
                detailCode = IKDetailCode.INVALID_ROBOT_DEFINITION,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ Robot definition validation passed" }

        AppLog.d(TAG) { "🔎 Validating DH parameters | count=${robot.dhParameters.size}" }
        if (!dhValidator.validateAll(robot.dhParameters)) {
            AppLog.e(TAG) {
                "❌ DH parameter validation failed | dhParameters=${robot.dhParameters}"
            }
            return IKResult(
                state = quarantinedInitialState,
                status = IKStatus.INVALID_INPUT,
                converged = false,
                iterations = 0,
                finalError = Double.NaN,
                detailCode = IKDetailCode.INVALID_DH_PARAMETERS,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ DH parameter validation passed" }

        AppLog.d(TAG) { "🔎 Validating initial robot state | state=${initialState.jointValues}" }
        val stateValidation = stateValidator.validate(robot, initialState)
        if (!stateValidation.isValid) {
            AppLog.e(TAG) {
                "❌ Robot state validation failed | issues=${stateValidation.issues}, state=${initialState.jointValues}"
            }
            return IKResult(
                state = quarantinedInitialState,
                status = IKStatus.INVALID_INPUT,
                converged = false,
                iterations = 0,
                finalError = Double.NaN,
                detailCode = IKDetailCode.INVALID_INITIAL_STATE,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ Initial robot state validation passed" }

        AppLog.d(TAG) { "🔎 Validating target position | target=(${target.x}, ${target.y}, ${target.z})" }
        if (!targetValidator.isValid(target)) {
            AppLog.e(TAG) {
                "❌ Target validation failed | target=(${target.x}, ${target.y}, ${target.z})"
            }
            return IKResult(
                state = quarantinedInitialState,
                status = IKStatus.INVALID_INPUT,
                converged = false,
                iterations = 0,
                finalError = Double.NaN,
                detailCode = IKDetailCode.INVALID_TARGET,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ Target validation passed" }

        val joints = sanitizeJointVector(
            robot = robot,
            values = initialState.jointValues
        ).toMutableList()

        val jointCount = joints.size
        val jointCoordinateScales = JointSpaceMetric.coordinateScales(robot)

        val jacobianX = MutableList(jointCount) { 0.0 }
        val jacobianY = MutableList(jointCount) { 0.0 }
        val jacobianZ = MutableList(jointCount) { 0.0 }
        val deltaQ = MutableList(jointCount) { 0.0 }
        val perturbedJoints = MutableList(jointCount) { index -> joints[index] }
        val candidateJoints = MutableList(jointCount) { index -> joints[index] }
        val acceptedJoints = MutableList(jointCount) { index -> joints[index] }
        val bestJoints = MutableList(jointCount) { index -> joints[index] }

        val currentFkResult = MutableFKPositionOnlyResult()
        val perturbedFkResult = MutableFKPositionOnlyResult()
        val candidateFkResult = MutableFKPositionOnlyResult()

        AppLog.d(TAG) { "🦾 Working joint buffer initialized | joints=$joints" }

        var converged = false
        var status = IKStatus.MAX_ITERATIONS_REACHED
        var finalError = Double.MAX_VALUE
        var iterations = 0
        var detailCode = IKDetailCode.NONE

        var previousError = Double.POSITIVE_INFINITY
        var stagnationCounter = 0
        var warningEncountered = false
        var precisionModeEntered = false
        var seedConditionNumber = Double.NaN
        var backtrackingRetryCount = 0
        var currentFkIsCached = false

        AppLog.d(TAG) { "🔁 Entering IK iteration loop | maxIterations=${config.maxIterations}" }

        for (iter in 0 until config.maxIterations) {
            iterations = iter + 1

            AppLog.d(TAG) {
                "🔄 Iteration ${iter + 1}/${config.maxIterations} started | joints=${joints.toList()}"
            }

            if (currentFkIsCached) {
                currentFkIsCached = false
            } else {
                fkSolver.solvePositionOnlyInto(
                    robot = robot,
                    jointValues = joints,
                    output = currentFkResult
                )
            }

            AppLog.d(TAG) {
                "📍 FK result received | status=${currentFkResult.status}, endEffector=(${currentFkResult.x}, ${currentFkResult.y}, ${currentFkResult.z})"
            }

            if (!isUsableFk(currentFkResult.status)) {
                AppLog.e(TAG) {
                    "💥 FK failed at current IK state | iteration=${iter + 1}, fkStatus=${currentFkResult.status}, joints=${joints.toList()}"
                }
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.FK_FAILED_DURING_IK
                break
            }

            val currentX = currentFkResult.x
            val currentY = currentFkResult.y
            val currentZ = currentFkResult.z

            val errorX = target.x - currentX
            val errorY = target.y - currentY
            val errorZ = target.z - currentZ
            val error = hypot(hypot(errorX, errorY), errorZ)

            finalError = error

            AppLog.d(TAG) {
                "📏 Error computed | errorX=$errorX, errorY=$errorY, errorZ=$errorZ, magnitude=$error"
            }

            if (!error.isFinite()) {
                AppLog.e(TAG) {
                    "💥 Non-finite error detected | iteration=${iter + 1}, error=$error"
                }
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.NON_FINITE_ERROR
                break
            }

            if (error <= config.tolerance) {
                AppLog.d(TAG) {
                    "🎯 Strict convergence reached | iteration=${iter + 1}, error=$error, tolerance=${config.tolerance}, warningEncountered=$warningEncountered, precisionModeEntered=$precisionModeEntered"
                }
                converged = true
                if (warningEncountered) {
                    status = IKStatus.SUCCESS_WITH_WARNING
                    if (detailCode == IKDetailCode.NONE) {
                        detailCode = IKDetailCode.NEAR_SINGULARITY_WARNING
                    }
                } else {
                    status = IKStatus.SUCCESS
                    detailCode = IKDetailCode.NONE
                }
                break
            }

            val precisionMode = error <= PRECISION_MODE_THRESHOLD_METERS
            if (precisionMode && !precisionModeEntered) {
                precisionModeEntered = true
                AppLog.d(TAG) {
                    "🔬 Precision mode entered | iteration=${iter + 1}, error=$error, tolerance=${config.tolerance}, precisionThreshold=$PRECISION_MODE_THRESHOLD_METERS"
                }
            }

            val stagnationWindow =
                if (precisionMode) {
                    STAGNATION_WINDOW_PRECISION
                } else {
                    STAGNATION_WINDOW_COARSE
                }

            if (previousError.isFinite()) {
                val improvement = previousError - error

                AppLog.d(TAG) {
                    "📉 Improvement check | iteration=${iter + 1}, previousError=$previousError, currentError=$error, improvement=$improvement, stagnationCounter=$stagnationCounter, precisionMode=$precisionMode"
                }

                if (improvement < STAGNATION_IMPROVEMENT_EPS) {
                    stagnationCounter += 1

                    AppLog.w(TAG) {
                        "⚠️ Stagnation incremented | iteration=${iter + 1}, improvement=$improvement, stagnationCounter=$stagnationCounter, threshold=$stagnationWindow, precisionMode=$precisionMode"
                    }

                    if (stagnationCounter >= stagnationWindow) {
                        AppLog.w(TAG) {
                            "🛑 IK stagnation detected | iteration=${iter + 1}, currentError=$error, previousError=$previousError, stagnationCounter=$stagnationCounter, precisionMode=$precisionMode"
                        }
                        status = IKStatus.NO_CONVERGENCE
                        detailCode = IKDetailCode.STAGNATION_WINDOW_EXCEEDED
                        break
                    }
                } else {
                    if (stagnationCounter > 0) {
                        AppLog.d(TAG) {
                            "✅ Stagnation counter reset | iteration=${iter + 1}, improvement=$improvement, previousCounter=$stagnationCounter"
                        }
                    }
                    stagnationCounter = 0
                }
            }

            previousError = error

            val jacobianOk =
                estimateJacobianSafely(
                    robot = robot,
                    joints = joints,
                    currentX = currentX,
                    currentY = currentY,
                    currentZ = currentZ,
                    iteration = iter + 1,
                    jacobianX = jacobianX,
                    jacobianY = jacobianY,
                    jacobianZ = jacobianZ,
                    perturbedJoints = perturbedJoints,
                    perturbedFkResult = perturbedFkResult
                )

            if (!jacobianOk) {
                AppLog.e(TAG) {
                    "🛑 Jacobian estimation failed safely | iteration=${iter + 1}"
                }
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.FK_FAILED_DURING_IK
                break
            }

            val singularityReport = nearSingularityAnalyzer.analyze(
                jacobianX = jacobianX,
                jacobianY = jacobianY,
                jacobianZ = jacobianZ,
                columnScales = jointCoordinateScales
            )

            if (seedConditionNumber.isNaN()) {
                seedConditionNumber = singularityReport.conditionNumber
            }

            AppLog.d(TAG) {
                "📉 Singularity analysis | iteration=${iter + 1}, sigmaMin=${singularityReport.sigmaMin}, sigmaMax=${singularityReport.sigmaMax}, conditionNumber=${singularityReport.conditionNumber}, level=${singularityReport.level}"
            }

            if (singularityReport.level == SingularityLevel.WARNING) {
                warningEncountered = true
                detailCode = IKDetailCode.NEAR_SINGULARITY_WARNING
                AppLog.w(TAG) {
                    "⚠️ Near singularity warning | iteration=${iter + 1}, sigmaMin=${singularityReport.sigmaMin}, conditionNumber=${singularityReport.conditionNumber}"
                }
            }

            if (singularityReport.level == SingularityLevel.CRITICAL) {
                warningEncountered = true
                detailCode = IKDetailCode.CRITICAL_SINGULARITY_DAMPED
                AppLog.w(TAG) {
                    "⚠️ Critical singularity region detected, attempting stabilized step with very high damping | iteration=${iter + 1}, sigmaMin=${singularityReport.sigmaMin}, conditionNumber=${singularityReport.conditionNumber}"
                }
            }

            val baseLambda =
                when (singularityReport.level) {
                    SingularityLevel.NORMAL -> max(config.damping, error * 0.5)
                    SingularityLevel.WARNING -> max(config.damping * 3.0, error * 0.5)
                    SingularityLevel.CRITICAL -> max(config.damping * 10.0, error * 0.5)
                }

            val precisionLambda =
                max(config.damping * PRECISION_DAMPING_SCALE, config.tolerance * 10.0)
            val lambda =
                when {
                    !precisionMode -> baseLambda
                    singularityReport.level == SingularityLevel.NORMAL -> precisionLambda
                    // Precision mode may reduce ordinary damping for final convergence, but
                    // must never cancel the stronger guard selected for a singular Jacobian.
                    else -> max(baseLambda, precisionLambda)
                }

            val dlsOk =
                computeDlsStep(
                    jacobianX = jacobianX,
                    jacobianY = jacobianY,
                    jacobianZ = jacobianZ,
                    errorX = errorX,
                    errorY = errorY,
                    errorZ = errorZ,
                    lambda = lambda,
                    jointCoordinateScales = jointCoordinateScales,
                    iteration = iter + 1,
                    outputDeltaQ = deltaQ
                )

            if (!dlsOk) {
                AppLog.e(TAG) {
                    "💥 DLS step computation failed | iteration=${iter + 1}"
                }
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.DLS_SYSTEM_NOT_POSITIVE_DEFINITE
                break
            }

            val rawStepNorm = JointSpaceMetric.normalizedNorm(deltaQ, jointCoordinateScales)

            AppLog.d(TAG) {
                "📊 Raw deltaQ computed | iteration=${iter + 1}, deltaQ=$deltaQ, rawStepNorm=$rawStepNorm"
            }

            if (!rawStepNorm.isFinite()) {
                AppLog.e(TAG) {
                    "💥 Non-finite deltaQ norm detected | iteration=${iter + 1}, rawStepNorm=$rawStepNorm"
                }
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.NON_FINITE_STEP_NORM
                break
            }

            val effectiveMaxStep =
                if (precisionMode) {
                    config.maxStep * PRECISION_MAX_STEP_SCALE
                } else {
                    config.maxStep
                }

            val scaling =
                if (rawStepNorm > effectiveMaxStep && rawStepNorm > 0.0) {
                    effectiveMaxStep / rawStepNorm
                } else {
                    1.0
                }

            AppLog.d(TAG) {
                "🎛️ Step scaling computed | iteration=${iter + 1}, scaling=$scaling, maxStep=${config.maxStep}, effectiveMaxStep=$effectiveMaxStep, precisionMode=$precisionMode"
            }

            val backtrackingOutcome =
                applyBacktrackedSafeStep(
                    robot = robot,
                    baseJoints = joints,
                    deltaQ = deltaQ,
                    candidateJoints = candidateJoints,
                    acceptedJoints = acceptedJoints,
                    bestJoints = bestJoints,
                    candidateFkResult = candidateFkResult,
                    baseScaling = scaling,
                    target = target,
                    currentError = error,
                    iteration = iter + 1,
                    precisionMode = precisionMode
                )

            backtrackingRetryCount +=
                backtrackingOutcome.retryCount

            val acceptedStep =
                backtrackingOutcome.acceptedStep

            if (acceptedStep == null) {
                AppLog.w(TAG) {
                    "🛑 No FK-safe candidate step accepted after backtracking | iteration=${iter + 1}, currentError=$error, precisionMode=$precisionMode"
                }

                status = IKStatus.NO_CONVERGENCE
                detailCode = IKDetailCode.STAGNATION_WINDOW_EXCEEDED
                break
            }

            var acceptedCopyIndex = 0

            while (acceptedCopyIndex < joints.size) {
                joints[acceptedCopyIndex] = acceptedJoints[acceptedCopyIndex]
                acceptedCopyIndex++
            }

            currentFkResult.status = acceptedStep.fkStatus
            currentFkResult.x = acceptedStep.x
            currentFkResult.y = acceptedStep.y
            currentFkResult.z = acceptedStep.z
            currentFkResult.detailCode = acceptedStep.fkDetailCode
            currentFkIsCached = true

            finalError = acceptedStep.error

            val stepImproved = acceptedStep.error < error
            if (!stepImproved) {
                stagnationCounter += 1
                AppLog.w(TAG) {
                    "⚠️ Exploratory non-improving step accepted | iteration=${iter + 1}, oldError=$error, newError=${acceptedStep.error}, stagnationCounter=$stagnationCounter, precisionMode=$precisionMode"
                }
            }

            AppLog.d(TAG) {
                "✅ Backtracked safe step accepted | iteration=${iter + 1}, acceptedScale=${acceptedStep.scale}, oldError=$error, newError=${acceptedStep.error}, improved=$stepImproved, exploratory=${acceptedStep.exploratory}, precisionMode=$precisionMode, joints=${joints.toList()}"
            }

            AppLog.d(TAG) {
                "📘 Iteration ${iter + 1} completed | currentStatus=$status, currentError=$finalError, joints=${joints.toList()}, stagnationCounter=$stagnationCounter, warningEncountered=$warningEncountered, detailCode=$detailCode"
            }
        }

        if (!converged &&
            status == IKStatus.MAX_ITERATIONS_REACHED &&
            detailCode == IKDetailCode.NONE
        ) {
            detailCode = IKDetailCode.MAX_ITERATIONS_LIMIT_REACHED
        }

        AppLog.d(TAG) { "🧼 Creating safe final joint state from working buffer | joints=${joints.toList()}" }

        val safeFinal = sanitizeJointVector(
            robot = robot,
            values = joints
        )

        fkSolver.solvePositionOnlyInto(
            robot = robot,
            jointValues = safeFinal,
            output = currentFkResult
        )

        if (isUsableFk(currentFkResult.status)) {
            finalError = distanceValues(
                ax = currentFkResult.x,
                ay = currentFkResult.y,
                az = currentFkResult.z,
                b = target
            )

            if (converged && finalError > config.tolerance) {
                AppLog.e(TAG) {
                    "❌ Independently recomputed final residual violates tolerance | finalError=$finalError, tolerance=${config.tolerance}"
                }
                converged = false
                status = IKStatus.NUMERICAL_FAILURE
                detailCode = IKDetailCode.OUTPUT_VALIDATION_FAILED
            }
        } else {
            converged = false
            status = IKStatus.NUMERICAL_FAILURE
            detailCode = IKDetailCode.FK_FAILED_DURING_IK
            finalError = Double.NaN
        }

        val candidateResult = IKResult(
            state = RobotState(safeFinal),
            status = status,
            converged = converged,
            iterations = iterations,
            finalError = finalError,
            detailCode = detailCode,
            metadata = metadata,
            diagnostics =
                IKDiagnostics(
                    seedConditionNumber = seedConditionNumber,
                    backtrackingRetryCount = backtrackingRetryCount
                )
        )

        val outputValidation = outputValidator.validateIkOutput(
            robot = robot,
            result = candidateResult
        )

        if (!outputValidation.isValid) {
            AppLog.e(TAG) {
                "❌ IK output validation failed | issues=${outputValidation.issues}"
            }

            return IKResult(
                state = RobotState(safeFinal),
                status = IKStatus.NUMERICAL_FAILURE,
                converged = false,
                iterations = iterations,
                finalError = finalError,
                detailCode = IKDetailCode.OUTPUT_VALIDATION_FAILED,
                metadata = metadata,
                diagnostics = candidateResult.diagnostics
            )
        }

        AppLog.d(TAG) {
            "🏁 IK solve finished | status=$status, converged=$converged, iterations=$iterations, finalError=$finalError, detailCode=$detailCode, initialState=${initialState.jointValues}, finalState=$safeFinal, target=(${target.x}, ${target.y}, ${target.z}), warningEncountered=$warningEncountered, precisionModeEntered=$precisionModeEntered"
        }

        return candidateResult
    }

    private data class AcceptedStep(
        val error: Double,
        val scale: Double,
        val exploratory: Boolean,
        val fkStatus: FKStatus,
        val x: Double,
        val y: Double,
        val z: Double,
        val fkDetailCode: FKDetailCode
    )

    private data class BacktrackingOutcome(
        val acceptedStep: AcceptedStep?,
        val retryCount: Int
    )

    private fun estimateJacobianSafely(
        robot: RobotDefinition,
        joints: List<Double>,
        currentX: Double,
        currentY: Double,
        currentZ: Double,
        iteration: Int,
        jacobianX: MutableList<Double>,
        jacobianY: MutableList<Double>,
        jacobianZ: MutableList<Double>,
        perturbedJoints: MutableList<Double>,
        perturbedFkResult: MutableFKPositionOnlyResult
    ): Boolean {
        var clearIndex = 0

        while (clearIndex < joints.size) {
            jacobianX[clearIndex] = 0.0
            jacobianY[clearIndex] = 0.0
            jacobianZ[clearIndex] = 0.0
            clearIndex++
        }

        var syncIndex = 0

        while (syncIndex < joints.size) {
            perturbedJoints[syncIndex] = joints[syncIndex]
            syncIndex++
        }

        AppLog.d(TAG) {
            "🧮 Starting boundary-aware 3D Jacobian estimation | iteration=$iteration, delta=$JACOBIAN_DELTA, jointCount=${joints.size}"
        }

        var i = 0

        while (i < joints.size) {
            val jointDef = robot.joints[i]
            val original = joints[i]

            val forwardCandidate =
                (original + JACOBIAN_DELTA).coerceAtMost(jointDef.maxValue)

            val backwardCandidate =
                (original - JACOBIAN_DELTA).coerceAtLeast(jointDef.minValue)

            val useForward = abs(forwardCandidate - original) > 1e-12
            val useBackward = abs(backwardCandidate - original) > 1e-12

            if (!useForward && !useBackward) {
                AppLog.w(TAG) {
                    "⚠️ Joint cannot be perturbed for Jacobian because it is pinned at limits | iteration=$iteration, jointIndex=$i, joint=${jointDef.name}, value=$original"
                }
                jacobianX[i] = 0.0
                jacobianY[i] = 0.0
                jacobianZ[i] = 0.0
                i++
                continue
            }

            var forwardX = currentX
            var forwardY = currentY
            var forwardZ = currentZ
            if (useForward) {
                perturbedJoints[i] = forwardCandidate
                fkSolver.solvePositionOnlyInto(robot, perturbedJoints, perturbedFkResult)
                if (!isUsableFk(perturbedFkResult.status)) {
                    perturbedJoints[i] = original
                    return false
                }
                forwardX = perturbedFkResult.x
                forwardY = perturbedFkResult.y
                forwardZ = perturbedFkResult.z
            }

            var backwardX = currentX
            var backwardY = currentY
            var backwardZ = currentZ
            if (useBackward) {
                perturbedJoints[i] = backwardCandidate
                fkSolver.solvePositionOnlyInto(robot, perturbedJoints, perturbedFkResult)
                if (!isUsableFk(perturbedFkResult.status)) {
                    perturbedJoints[i] = original
                    return false
                }
                backwardX = perturbedFkResult.x
                backwardY = perturbedFkResult.y
                backwardZ = perturbedFkResult.z
            }

            val denominator =
                when {
                    useForward && useBackward -> forwardCandidate - backwardCandidate
                    useForward -> forwardCandidate - original
                    else -> original - backwardCandidate
                }

            when {
                useForward && useBackward -> {
                    jacobianX[i] = (forwardX - backwardX) / denominator
                    jacobianY[i] = (forwardY - backwardY) / denominator
                    jacobianZ[i] = (forwardZ - backwardZ) / denominator
                }

                useForward -> {
                    jacobianX[i] = (forwardX - currentX) / denominator
                    jacobianY[i] = (forwardY - currentY) / denominator
                    jacobianZ[i] = (forwardZ - currentZ) / denominator
                }

                else -> {
                    jacobianX[i] = (currentX - backwardX) / denominator
                    jacobianY[i] = (currentY - backwardY) / denominator
                    jacobianZ[i] = (currentZ - backwardZ) / denominator
                }
            }

            perturbedJoints[i] = original

            AppLog.d(TAG) {
                "📐 Boundary-aware Jacobian column computed | iteration=$iteration, jointIndex=$i, central=${useForward && useBackward}, denominator=$denominator, dX=${jacobianX[i]}, dY=${jacobianY[i]}, dZ=${jacobianZ[i]}"
            }

            i++
        }

        var checkIndex = 0

        while (checkIndex < joints.size) {
            if (
                !jacobianX[checkIndex].isFinite() ||
                !jacobianY[checkIndex].isFinite() ||
                !jacobianZ[checkIndex].isFinite()
            ) {
                AppLog.e(TAG) {
                    "💥 Non-finite Jacobian value detected | iteration=$iteration"
                }
                return false
            }

            checkIndex++
        }

        return true
    }

    private fun computeDlsStep(
        jacobianX: List<Double>,
        jacobianY: List<Double>,
        jacobianZ: List<Double>,
        errorX: Double,
        errorY: Double,
        errorZ: Double,
        lambda: Double,
        jointCoordinateScales: List<Double>,
        iteration: Int,
        outputDeltaQ: MutableList<Double>
    ): Boolean {
        val lambdaSq = lambda * lambda

        var a11 = lambdaSq
        var a12 = 0.0
        var a13 = 0.0
        var a22 = lambdaSq
        var a23 = 0.0
        var a33 = lambdaSq

        var index = 0

        while (index < jacobianX.size) {
            val scale = jointCoordinateScales[index]
            val x = jacobianX[index] * scale
            val y = jacobianY[index] * scale
            val z = jacobianZ[index] * scale

            a11 += x * x
            a12 += x * y
            a13 += x * z
            a22 += y * y
            a23 += y * z
            a33 += z * z

            index++
        }

        val diagonalScale = max(a11, max(a22, a33))
        val positiveFloor = diagonalScale * CHOLESKY_RELATIVE_FLOOR

        // A = J J^T + lambda^2 I is symmetric positive definite for lambda > 0.
        // Solve A y = e using Cholesky rather than explicitly forming A^-1.
        val l11Squared = a11
        if (!l11Squared.isFinite() || l11Squared <= positiveFloor) return false
        val l11 = sqrt(l11Squared)
        val l21 = a12 / l11
        val l31 = a13 / l11

        val l22Squared = a22 - l21 * l21
        if (!l22Squared.isFinite() || l22Squared <= positiveFloor) return false
        val l22 = sqrt(l22Squared)
        val l32 = (a23 - l31 * l21) / l22

        val l33Squared = a33 - l31 * l31 - l32 * l32
        if (!l33Squared.isFinite() || l33Squared <= positiveFloor) return false
        val l33 = sqrt(l33Squared)

        val z1 = errorX / l11
        val z2 = (errorY - l21 * z1) / l22
        val z3 = (errorZ - l31 * z1 - l32 * z2) / l33

        val y3 = z3 / l33
        val y2 = (z2 - l32 * y3) / l22
        val y1 = (z1 - l21 * y2 - l31 * y3) / l11

        AppLog.d(TAG) {
            "🧠 3D DLS system solved with Cholesky | iteration=$iteration, lambda=$lambda, diagonalScale=$diagonalScale"
        }

        AppLog.d(TAG) {
            "🧮 3D DLS intermediate solution | iteration=$iteration, y1=$y1, y2=$y2, y3=$y3"
        }

        if (!y1.isFinite() || !y2.isFinite() || !y3.isFinite()) {
            AppLog.e(TAG) {
                "💥 Non-finite DLS intermediate solution | iteration=$iteration, y1=$y1, y2=$y2, y3=$y3"
            }
            return false
        }

        var outputIndex = 0

        while (outputIndex < jacobianX.size) {
            val scale = jointCoordinateScales[outputIndex]
            val value =
                scale * (
                    jacobianX[outputIndex] * scale * y1 +
                        jacobianY[outputIndex] * scale * y2 +
                        jacobianZ[outputIndex] * scale * y3
                )

            if (!value.isFinite()) {
                AppLog.e(TAG) {
                    "💥 Non-finite deltaQ component detected | iteration=$iteration, index=$outputIndex, value=$value"
                }
                return false
            }

            outputDeltaQ[outputIndex] = value

            outputIndex++
        }

        return true
    }

    private fun applyBacktrackedSafeStep(
        robot: RobotDefinition,
        baseJoints: List<Double>,
        deltaQ: List<Double>,
        candidateJoints: MutableList<Double>,
        acceptedJoints: MutableList<Double>,
        bestJoints: MutableList<Double>,
        candidateFkResult: MutableFKPositionOnlyResult,
        baseScaling: Double,
        target: Vec3,
        currentError: Double,
        iteration: Int,
        precisionMode: Boolean
    ): BacktrackingOutcome {
        var hasBest = false
        var bestError = Double.POSITIVE_INFINITY
        var bestScale = baseScaling
        var bestExploratory = false
        var bestFkStatus = FKStatus.SUCCESS
        var bestX = 0.0
        var bestY = 0.0
        var bestZ = 0.0
        var bestFkDetailCode = FKDetailCode.NONE

        var hasAccepted = false
        var acceptedError = Double.NaN
        var acceptedScale = baseScaling
        var acceptedExploratory = false
        var acceptedFkStatus = FKStatus.SUCCESS
        var acceptedX = 0.0
        var acceptedY = 0.0
        var acceptedZ = 0.0
        var acceptedFkDetailCode = FKDetailCode.NONE

        var scale = baseScaling

        val allowedGrowth =
            if (precisionMode) {
                max(
                    PRECISION_EXPLORATORY_ERROR_GROWTH_ABSOLUTE,
                    currentError * PRECISION_EXPLORATORY_ERROR_GROWTH_RATIO
                )
            } else {
                max(
                    COARSE_EXPLORATORY_ERROR_GROWTH_ABSOLUTE,
                    currentError * COARSE_EXPLORATORY_ERROR_GROWTH_RATIO
                )
            }

        var attempt = 0

        while (attempt < BACKTRACK_ATTEMPTS) {
            var validCandidate = true
            var index = 0

            while (index < baseJoints.size) {
                val step = deltaQ[index] * scale

                if (!step.isFinite()) {
                    validCandidate = false
                    break
                }

                candidateJoints[index] =
                    sanitizeJointValue(
                        robot = robot,
                        jointIndex = index,
                        value = baseJoints[index] + step
                    )

                index++
            }

            if (!validCandidate) {
                scale *= BACKTRACK_SHRINK
                attempt++
                continue
            }

            var finiteIndex = 0

            while (finiteIndex < candidateJoints.size) {
                if (!candidateJoints[finiteIndex].isFinite()) {
                    validCandidate = false
                    break
                }

                finiteIndex++
            }

            if (!validCandidate) {
                AppLog.w(TAG) {
                    "⚠️ Backtracking candidate contains non-finite value | iteration=$iteration, attempt=${attempt + 1}, scale=$scale, candidate=$candidateJoints"
                }

                scale *= BACKTRACK_SHRINK
                attempt++
                continue
            }

            fkSolver.solvePositionOnlyInto(
                robot = robot,
                jointValues = candidateJoints,
                output = candidateFkResult
            )

            if (!isUsableFk(candidateFkResult.status)) {
                AppLog.w(TAG) {
                    "⚠️ Backtracking candidate FK failed | iteration=$iteration, attempt=${attempt + 1}, scale=$scale, fkStatus=${candidateFkResult.status}, candidate=$candidateJoints"
                }

                scale *= BACKTRACK_SHRINK
                attempt++
                continue
            }

            val candidateError =
                distanceValues(
                    ax = candidateFkResult.x,
                    ay = candidateFkResult.y,
                    az = candidateFkResult.z,
                    b = target
                )

            if (!candidateError.isFinite()) {
                AppLog.w(TAG) {
                    "⚠️ Backtracking candidate produced non-finite error | iteration=$iteration, attempt=${attempt + 1}, scale=$scale"
                }

                scale *= BACKTRACK_SHRINK
                attempt++
                continue
            }

            val exploratory =
                candidateError > currentError

            if (candidateError < bestError) {
                bestError = candidateError
                bestScale = scale
                bestExploratory = exploratory
                bestFkStatus = candidateFkResult.status
                bestX = candidateFkResult.x
                bestY = candidateFkResult.y
                bestZ = candidateFkResult.z
                bestFkDetailCode = candidateFkResult.detailCode
                hasBest = true

                var bestCopyIndex = 0

                while (bestCopyIndex < candidateJoints.size) {
                    bestJoints[bestCopyIndex] = candidateJoints[bestCopyIndex]
                    bestCopyIndex++
                }
            }

            if (candidateError <= currentError) {
                acceptedError = candidateError
                acceptedScale = scale
                acceptedExploratory = exploratory
                acceptedFkStatus = candidateFkResult.status
                acceptedX = candidateFkResult.x
                acceptedY = candidateFkResult.y
                acceptedZ = candidateFkResult.z
                acceptedFkDetailCode = candidateFkResult.detailCode
                hasAccepted = true

                var acceptedCopyIndex = 0

                while (acceptedCopyIndex < candidateJoints.size) {
                    acceptedJoints[acceptedCopyIndex] = candidateJoints[acceptedCopyIndex]
                    acceptedCopyIndex++
                }

                break
            }

            if (!precisionMode && candidateError <= currentError + allowedGrowth) {
                AppLog.w(TAG) {
                    "⚠️ Accepting exploratory FK-safe coarse step | iteration=$iteration, attempt=${attempt + 1}, scale=$scale, currentError=$currentError, candidateError=$candidateError, allowedGrowth=$allowedGrowth"
                }

                acceptedError = candidateError
                acceptedScale = scale
                acceptedExploratory = exploratory
                acceptedFkStatus = candidateFkResult.status
                acceptedX = candidateFkResult.x
                acceptedY = candidateFkResult.y
                acceptedZ = candidateFkResult.z
                acceptedFkDetailCode = candidateFkResult.detailCode
                hasAccepted = true

                var acceptedCopyIndex = 0

                while (acceptedCopyIndex < candidateJoints.size) {
                    acceptedJoints[acceptedCopyIndex] = candidateJoints[acceptedCopyIndex]
                    acceptedCopyIndex++
                }

                break
            }

            if (precisionMode && candidateError <= currentError + allowedGrowth) {
                AppLog.w(TAG) {
                    "⚠️ Accepting tiny exploratory FK-safe precision step | iteration=$iteration, attempt=${attempt + 1}, scale=$scale, currentError=$currentError, candidateError=$candidateError, allowedGrowth=$allowedGrowth"
                }

                acceptedError = candidateError
                acceptedScale = scale
                acceptedExploratory = exploratory
                acceptedFkStatus = candidateFkResult.status
                acceptedX = candidateFkResult.x
                acceptedY = candidateFkResult.y
                acceptedZ = candidateFkResult.z
                acceptedFkDetailCode = candidateFkResult.detailCode
                hasAccepted = true

                var acceptedCopyIndex = 0

                while (acceptedCopyIndex < candidateJoints.size) {
                    acceptedJoints[acceptedCopyIndex] = candidateJoints[acceptedCopyIndex]
                    acceptedCopyIndex++
                }

                break
            }

            AppLog.d(TAG) {
                "↩️ Backtracking step too costly | iteration=$iteration, attempt=${attempt + 1}, scale=$scale, currentError=$currentError, candidateError=$candidateError, allowedGrowth=$allowedGrowth, precisionMode=$precisionMode"
            }

            scale *= BACKTRACK_SHRINK
            attempt++
        }

        if (hasAccepted) {
            return BacktrackingOutcome(
                acceptedStep =
                    AcceptedStep(
                        error = acceptedError,
                        scale = acceptedScale,
                        exploratory = acceptedExploratory,
                        fkStatus = acceptedFkStatus,
                        x = acceptedX,
                        y = acceptedY,
                        z = acceptedZ,
                        fkDetailCode = acceptedFkDetailCode
                    ),
                retryCount = attempt
            )
        }

        val bestAcceptedStep =
            if (hasBest && bestError <= currentError + allowedGrowth) {
                AppLog.w(TAG) {
                    "⚠️ Using best available FK-safe step | iteration=$iteration, currentError=$currentError, bestError=$bestError, bestScale=$bestScale, allowedGrowth=$allowedGrowth, precisionMode=$precisionMode"
                }

                var bestCopyIndex = 0

                while (bestCopyIndex < bestJoints.size) {
                    acceptedJoints[bestCopyIndex] = bestJoints[bestCopyIndex]
                    bestCopyIndex++
                }

                AcceptedStep(
                    error = bestError,
                    scale = bestScale,
                    exploratory = bestExploratory,
                    fkStatus = bestFkStatus,
                    x = bestX,
                    y = bestY,
                    z = bestZ,
                    fkDetailCode = bestFkDetailCode
                )
            } else {
                null
            }

        return BacktrackingOutcome(
            acceptedStep = bestAcceptedStep,
            retryCount = attempt
        )
    }

    private fun sanitizeJointVector(
        robot: RobotDefinition,
        values: List<Double>
    ): List<Double> {
        val output =
            MutableList(robot.joints.size) {
                0.0
            }

        sanitizeJointVectorInto(
            robot = robot,
            values = values,
            output = output
        )

        return output
    }

    private fun quarantineFailureState(
        robot: RobotDefinition,
        state: RobotState
    ): RobotState {
        val jointsAreTrustworthy =
            robot.joints.isNotEmpty() && robot.joints.all { joint ->
                joint.minValue.isFinite() &&
                    joint.maxValue.isFinite() &&
                    joint.minValue <= joint.maxValue &&
                    joint.homeValue.isFinite() &&
                    joint.homeValue in joint.minValue..joint.maxValue
            }
        if (!jointsAreTrustworthy) {
            return RobotState(state.jointValues.map { value -> value.takeIf(Double::isFinite) ?: 0.0 })
        }
        return RobotState(
            robot.joints.mapIndexed { index, joint ->
                state.jointValues.getOrNull(index)
                    ?.takeIf(Double::isFinite)
                    ?.coerceIn(joint.minValue, joint.maxValue)
                    ?: joint.homeValue
            }
        )
    }

    private fun sanitizeJointVectorInto(
        robot: RobotDefinition,
        values: List<Double>,
        output: MutableList<Double>
    ) {
        var index = 0

        while (index < robot.joints.size) {
            val joint =
                robot.joints[index]

            val raw =
                values.getOrElse(index) {
                    joint.homeValue
                }

            output[index] =
                sanitizeJointValue(
                    robot = robot,
                    jointIndex = index,
                    value = raw
                )

            index++
        }
    }

    private fun sanitizeJointValue(
        robot: RobotDefinition,
        jointIndex: Int,
        value: Double
    ): Double {
        val joint = robot.joints[jointIndex]

        if (!value.isFinite()) {
            return joint.homeValue.coerceIn(joint.minValue, joint.maxValue)
        }

        // Joint limits define the authoritative coordinate interval. Applying a
        // global [-pi, pi] wrap here would corrupt legitimate shifted intervals
        // such as [3, 4] rad and introduce a discontinuity in the Jacobian.
        return value.coerceIn(joint.minValue, joint.maxValue)
    }

    private fun isUsableFk(status: FKStatus): Boolean {
        return status == FKStatus.SUCCESS ||
                status == FKStatus.SUCCESS_WITH_WARNING
    }

    private fun distanceValues(
        ax: Double,
        ay: Double,
        az: Double,
        b: Vec3
    ): Double {
        val dx = ax - b.x
        val dy = ay - b.y
        val dz = az - b.z

        return hypot(hypot(dx, dy), dz)
    }
}
