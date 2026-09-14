package com.robotkinematicslab.mobile.solver.fk

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.SolverMetadata
import com.robotkinematicslab.mobile.logging.AppLog
import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.validation.input.DHParameterValidator
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.output.KinematicsOutputValidator
import com.robotkinematicslab.mobile.validation.output.RotationDriftAssessor
import com.robotkinematicslab.mobile.validation.output.RotationRepairDecision
import com.robotkinematicslab.mobile.validation.output.RotationRepairer
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator

class ForwardKinematicsSolver {

    companion object {
        private const val TAG = "ForwardKinematics"
        private const val ACTIVE_DH_ZERO_EPS = 1e-12
    }

    private val robotValidator = RobotDefinitionValidator()
    private val stateValidator = RobotStateValidator()
    private val dhValidator = DHParameterValidator()
    private val outputValidator = KinematicsOutputValidator()
    private val rotationDriftAssessor = RotationDriftAssessor()
    private val rotationRepairer = RotationRepairer()

    fun solve(robot: RobotDefinition, state: RobotState): FKResult {
        AppLog.d(TAG) {
            "🚀 FK solve started | robot=${robot.name}, state=${state.jointValues}, jointCount=${robot.joints.size}, dhCount=${robot.dhParameters.size}"
        }

        val metadata = SolverMetadata(
            solverName = "ForwardKinematicsSolver",
            maxIterations = 0,
            tolerance = 0.0,
            damping = 0.0,
            maxStep = 0.0
        )

        AppLog.d(TAG) {
            "🧾 Metadata created | solverName=${metadata.solverName}, maxIterations=${metadata.maxIterations}, tolerance=${metadata.tolerance}, damping=${metadata.damping}, maxStep=${metadata.maxStep}"
        }

        AppLog.d(TAG) { "🔎 Validating robot definition" }
        val robotValidation = robotValidator.validate(robot)
        if (!robotValidation.isValid) {
            AppLog.e(TAG) {
                "❌ Robot definition validation failed | issues=${robotValidation.issues}"
            }
            return FKResult(
                status = FKStatus.INVALID_INPUT,
                endEffectorTransform = Matrix4.identity(),
                endEffectorPosition = Vec3.ZERO,
                jointPositions = emptyList(),
                detailCode = FKDetailCode.INVALID_ROBOT_DEFINITION,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ Robot definition validation passed" }

        if (robot.dhParameters.size != robot.joints.size) {
            AppLog.e(TAG) {
                "❌ Robot definition invalid | dhCount=${robot.dhParameters.size}, jointCount=${robot.joints.size}"
            }
            return FKResult(
                status = FKStatus.INVALID_INPUT,
                endEffectorTransform = Matrix4.identity(),
                endEffectorPosition = Vec3.ZERO,
                jointPositions = emptyList(),
                detailCode = FKDetailCode.INVALID_ROBOT_DEFINITION,
                metadata = metadata
            )
        }

        AppLog.d(TAG) { "🔎 Validating DH parameters | count=${robot.dhParameters.size}" }
        if (!dhValidator.validateAll(robot.dhParameters)) {
            AppLog.e(TAG) {
                "❌ DH parameter validation failed | dhParameters=${robot.dhParameters}"
            }
            return FKResult(
                status = FKStatus.INVALID_INPUT,
                endEffectorTransform = Matrix4.identity(),
                endEffectorPosition = Vec3.ZERO,
                jointPositions = emptyList(),
                detailCode = FKDetailCode.INVALID_DH_PARAMETERS,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ DH parameter validation passed" }

        AppLog.d(TAG) { "🔎 Validating robot state | state=${state.jointValues}" }
        val stateValidation = stateValidator.validate(robot, state)
        if (!stateValidation.isValid) {
            AppLog.e(TAG) {
                "❌ Robot state validation failed | issues=${stateValidation.issues}, state=${state.jointValues}"
            }
            return FKResult(
                status = FKStatus.INVALID_INPUT,
                endEffectorTransform = Matrix4.identity(),
                endEffectorPosition = Vec3.ZERO,
                jointPositions = emptyList(),
                detailCode = FKDetailCode.INVALID_STATE,
                metadata = metadata
            )
        }
        AppLog.d(TAG) { "✅ Robot state validation passed" }

        var transform = Matrix4.identity()
        val jointPositions = ArrayList<Vec3>(robot.dhParameters.size + 1)

        AppLog.d(TAG) { "🧱 Initial transform set to identity" }
        jointPositions.add(Vec3.ZERO)
        AppLog.d(TAG) { "📍 Initial base joint position added | position=${Vec3.ZERO}" }

        robot.dhParameters.forEachIndexed { index, baseParameter ->
            val joint = robot.joints[index]
            val jointValue = state.jointValues[index]

            AppLog.d(TAG) {
                "🔄 Processing joint ${index + 1}/${robot.dhParameters.size} | jointName=${joint.name}, jointType=${joint.type}, stateValue=$jointValue, baseDh={theta=${baseParameter.theta}, d=${baseParameter.d}, a=${baseParameter.a}, alpha=${baseParameter.alpha}}"
            }

            val updatedParameter = buildActiveDhParameter(
                baseParameter = baseParameter,
                jointType = joint.type,
                jointValue = jointValue
            )

            AppLog.d(TAG) {
                "🧩 Active DH parameter created | jointIndex=$index, jointType=${joint.type}, activeDh={theta=${updatedParameter.theta}, d=${updatedParameter.d}, a=${updatedParameter.a}, alpha=${updatedParameter.alpha}}"
            }

            val dhTransform = Matrix4.fromDH(updatedParameter)
            AppLog.d(TAG) {
                "🧮 DH transform computed | jointIndex=$index, translation=${dhTransform.translation()}"
            }

            transform = transform * dhTransform
            AppLog.d(TAG) {
                "🔗 Transform accumulated | jointIndex=$index, accumulatedTranslation=${transform.translation()}"
            }

            val pos = transform.translation()
            AppLog.d(TAG) {
                "📍 Joint position computed | jointIndex=$index, position=(${pos.x}, ${pos.y}, ${pos.z})"
            }

            if (!pos.x.isFinite() || !pos.y.isFinite() || !pos.z.isFinite()) {
                AppLog.e(TAG) {
                    "💥 Non-finite joint position detected | jointIndex=$index, position=(${pos.x}, ${pos.y}, ${pos.z})"
                }
                return FKResult(
                    status = FKStatus.NUMERICAL_FAILURE,
                    endEffectorTransform = transform,
                    endEffectorPosition = pos,
                    jointPositions = jointPositions,
                    detailCode = FKDetailCode.NON_FINITE_JOINT_POSITION,
                    metadata = metadata
                )
            }

            jointPositions.add(pos)
            AppLog.d(TAG) {
                "✅ Joint position stored | jointIndex=$index, totalStoredPositions=${jointPositions.size}"
            }
        }

        val eePos = jointPositions.last()
        AppLog.d(TAG) {
            "🎯 End effector position computed | position=(${eePos.x}, ${eePos.y}, ${eePos.z})"
        }

        if (!eePos.x.isFinite() || !eePos.y.isFinite() || !eePos.z.isFinite()) {
            AppLog.e(TAG) {
                "💥 Non-finite end effector position detected | position=(${eePos.x}, ${eePos.y}, ${eePos.z})"
            }
            return FKResult(
                status = FKStatus.NUMERICAL_FAILURE,
                endEffectorTransform = transform,
                endEffectorPosition = eePos,
                jointPositions = jointPositions,
                detailCode = FKDetailCode.NON_FINITE_END_EFFECTOR_POSITION,
                metadata = metadata
            )
        }

        val driftAssessment = rotationDriftAssessor.assess(transform.m)

        AppLog.d(TAG) {
            "🧪 Rotation drift assessment | decision=${driftAssessment.decision}, maxNormError=${driftAssessment.maxNormError}, maxOrthogonalityError=${driftAssessment.maxOrthogonalityError}, determinantError=${driftAssessment.determinantError}"
        }

        val finalTransform: Matrix4
        val finalStatus: FKStatus
        val finalDetailCode: FKDetailCode

        when (driftAssessment.decision) {
            RotationRepairDecision.NO_REPAIR_NEEDED -> {
                AppLog.d(TAG) {
                    "✅ Rotation drift assessment says no repair is needed"
                }
                finalTransform = transform
                finalStatus = FKStatus.SUCCESS
                finalDetailCode = FKDetailCode.NONE
            }

            RotationRepairDecision.REPAIR_ALLOWED -> {
                AppLog.w(TAG) {
                    "⚠️ Minor FK rotation drift detected | applying repair | maxNormError=${driftAssessment.maxNormError}, maxOrthogonalityError=${driftAssessment.maxOrthogonalityError}, determinantError=${driftAssessment.determinantError}"
                }

                val repairResult = rotationRepairer.repair(transform)

                if (!repairResult.repairApplied) {
                    AppLog.e(TAG) {
                        "❌ FK repair was allowed but repairer could not apply a stable correction"
                    }
                    return FKResult(
                        status = FKStatus.NUMERICAL_FAILURE,
                        endEffectorTransform = transform,
                        endEffectorPosition = eePos,
                        jointPositions = jointPositions,
                        detailCode = FKDetailCode.ROTATION_REPAIR_FAILED,
                        metadata = metadata
                    )
                }

                AppLog.d(TAG) {
                    "✅ FK rotation repair applied successfully"
                }

                finalTransform = repairResult.repairedTransform
                finalStatus = FKStatus.SUCCESS_WITH_WARNING
                finalDetailCode = FKDetailCode.ROTATION_REPAIR_APPLIED
            }

            RotationRepairDecision.REJECT -> {
                AppLog.e(TAG) {
                    "❌ Major FK rotation drift detected | rejecting output before repair stage | maxNormError=${driftAssessment.maxNormError}, maxOrthogonalityError=${driftAssessment.maxOrthogonalityError}, determinantError=${driftAssessment.determinantError}"
                }
                return FKResult(
                    status = FKStatus.NUMERICAL_FAILURE,
                    endEffectorTransform = transform,
                    endEffectorPosition = eePos,
                    jointPositions = jointPositions,
                    detailCode = FKDetailCode.ROTATION_DRIFT_REJECTED,
                    metadata = metadata
                )
            }
        }

        val candidateResult = FKResult(
            status = finalStatus,
            endEffectorTransform = finalTransform,
            endEffectorPosition = eePos,
            jointPositions = jointPositions,
            detailCode = finalDetailCode,
            metadata = metadata
        )

        val outputValidation = outputValidator.validateFkOutput(candidateResult)

        if (!outputValidation.isValid) {
            AppLog.e(TAG) {
                "❌ FK output validation failed | issues=${outputValidation.issues}"
            }

            return FKResult(
                status = FKStatus.NUMERICAL_FAILURE,
                endEffectorTransform = finalTransform,
                endEffectorPosition = eePos,
                jointPositions = jointPositions,
                detailCode = FKDetailCode.OUTPUT_VALIDATION_FAILED,
                metadata = metadata
            )
        }

        AppLog.d(TAG) {
            "🏁 FK solve finished | status=$finalStatus, detailCode=$finalDetailCode, endEffector=(${eePos.x}, ${eePos.y}, ${eePos.z}), jointPositionsCount=${jointPositions.size}, driftDecision=${driftAssessment.decision}"
        }

        return candidateResult
    }

    fun solvePositionOnlyInto(
        robot: RobotDefinition,
        jointValues: List<Double>,
        output: MutableFKPositionOnlyResult
    ): Boolean {
        if (!hasNumericallyValidRobotDefinition(robot)) {
            output.status = FKStatus.INVALID_INPUT
            output.x = 0.0
            output.y = 0.0
            output.z = 0.0
            output.detailCode = FKDetailCode.INVALID_ROBOT_DEFINITION
            return false
        }

        if (
            jointValues.size != robot.joints.size ||
            jointValues.indices.any { index ->
                val value = jointValues[index]
                val joint = robot.joints[index]
                !value.isFinite() || value < joint.minValue || value > joint.maxValue
            }
        ) {
            output.status = FKStatus.INVALID_INPUT
            output.x = 0.0
            output.y = 0.0
            output.z = 0.0
            output.detailCode = FKDetailCode.INVALID_STATE
            return false
        }

        var m00 = 1.0
        var m01 = 0.0
        var m02 = 0.0
        var m03 = 0.0

        var m10 = 0.0
        var m11 = 1.0
        var m12 = 0.0
        var m13 = 0.0

        var m20 = 0.0
        var m21 = 0.0
        var m22 = 1.0
        var m23 = 0.0

        var index = 0

        while (index < robot.dhParameters.size) {
            val baseParameter = robot.dhParameters[index]
            val joint = robot.joints[index]
            val jointValue = jointValues[index]

            val theta =
                if (joint.type == JointType.REVOLUTE) {
                    jointValue
                } else {
                    baseParameter.theta
                }

            val d =
                if (joint.type == JointType.PRISMATIC) {
                    jointValue
                } else {
                    baseParameter.d
                }

            val a = baseParameter.a
            val alpha = baseParameter.alpha

            val cosTheta = kotlin.math.cos(theta)
            val sinTheta = kotlin.math.sin(theta)
            val cosAlpha = kotlin.math.cos(alpha)
            val sinAlpha = kotlin.math.sin(alpha)

            val t00 = cosTheta
            val t01 = -sinTheta * cosAlpha
            val t02 = sinTheta * sinAlpha
            val t03 = a * cosTheta

            val t10 = sinTheta
            val t11 = cosTheta * cosAlpha
            val t12 = -cosTheta * sinAlpha
            val t13 = a * sinTheta

            val t20 = 0.0
            val t21 = sinAlpha
            val t22 = cosAlpha
            val t23 = d

            val n00 = m00 * t00 + m01 * t10 + m02 * t20
            val n01 = m00 * t01 + m01 * t11 + m02 * t21
            val n02 = m00 * t02 + m01 * t12 + m02 * t22
            val n03 = m00 * t03 + m01 * t13 + m02 * t23 + m03

            val n10 = m10 * t00 + m11 * t10 + m12 * t20
            val n11 = m10 * t01 + m11 * t11 + m12 * t21
            val n12 = m10 * t02 + m11 * t12 + m12 * t22
            val n13 = m10 * t03 + m11 * t13 + m12 * t23 + m13

            val n20 = m20 * t00 + m21 * t10 + m22 * t20
            val n21 = m20 * t01 + m21 * t11 + m22 * t21
            val n22 = m20 * t02 + m21 * t12 + m22 * t22
            val n23 = m20 * t03 + m21 * t13 + m22 * t23 + m23

            m00 = n00
            m01 = n01
            m02 = n02
            m03 = n03

            m10 = n10
            m11 = n11
            m12 = n12
            m13 = n13

            m20 = n20
            m21 = n21
            m22 = n22
            m23 = n23

            if (
                !m03.isFinite() ||
                !m13.isFinite() ||
                !m23.isFinite()
            ) {
                output.status = FKStatus.NUMERICAL_FAILURE
                output.x = m03
                output.y = m13
                output.z = m23
                output.detailCode = FKDetailCode.NON_FINITE_JOINT_POSITION
                return false
            }

            index++
        }

        if (
            !m03.isFinite() ||
            !m13.isFinite() ||
            !m23.isFinite()
        ) {
            output.status = FKStatus.NUMERICAL_FAILURE
            output.x = m03
            output.y = m13
            output.z = m23
            output.detailCode = FKDetailCode.NON_FINITE_END_EFFECTOR_POSITION
            return false
        }

        output.status = FKStatus.SUCCESS
        output.x = m03
        output.y = m13
        output.z = m23
        output.detailCode = FKDetailCode.NONE

        return true
    }

    /**
     * Allocation-free numerical contract for the high-frequency IK FK path.
     * The full solver still supplies rich validation issues; this path must at
     * least reject every malformed value before it enters matrix arithmetic.
     */
    private fun hasNumericallyValidRobotDefinition(robot: RobotDefinition): Boolean {
        if (robot.dhParameters.isEmpty() || robot.dhParameters.size != robot.joints.size) return false
        return robot.dhParameters.indices.all { index ->
            val parameter = robot.dhParameters[index]
            val joint = robot.joints[index]
            parameter.theta.isFinite() &&
                parameter.d.isFinite() &&
                parameter.a.isFinite() &&
                parameter.alpha.isFinite() &&
                joint.minValue.isFinite() &&
                joint.maxValue.isFinite() &&
                joint.homeValue.isFinite() &&
                joint.minValue < joint.maxValue &&
                (joint.maxValue - joint.minValue).isFinite() &&
                joint.homeValue in joint.minValue..joint.maxValue &&
                when (joint.type) {
                    JointType.REVOLUTE -> kotlin.math.abs(parameter.theta) <= ACTIVE_DH_ZERO_EPS
                    JointType.PRISMATIC -> kotlin.math.abs(parameter.d) <= ACTIVE_DH_ZERO_EPS
                }
        }
    }

    fun solvePositionOnly(
        robot: RobotDefinition,
        jointValues: List<Double>
    ): FKPositionOnlyResult {
        val mutableResult = MutableFKPositionOnlyResult()

        solvePositionOnlyInto(
            robot = robot,
            jointValues = jointValues,
            output = mutableResult
        )

        return FKPositionOnlyResult(
            status = mutableResult.status,
            position = Vec3(
                x = mutableResult.x,
                y = mutableResult.y,
                z = mutableResult.z
            ),
            detailCode = mutableResult.detailCode
        )
    }

    private fun buildActiveDhParameter(
        baseParameter: DHParameter,
        jointType: JointType,
        jointValue: Double
    ): DHParameter {
        return when (jointType) {
            JointType.REVOLUTE -> {
                DHParameter(
                    theta = jointValue,
                    d = baseParameter.d,
                    a = baseParameter.a,
                    alpha = baseParameter.alpha
                )
            }

            JointType.PRISMATIC -> {
                DHParameter(
                    theta = baseParameter.theta,
                    d = jointValue,
                    a = baseParameter.a,
                    alpha = baseParameter.alpha
                )
            }
        }
    }

}
