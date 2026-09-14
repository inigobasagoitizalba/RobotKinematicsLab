package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.ExpandedContextFeatureCalculator
import com.robotkinematicslab.mobile.ml.data.ExpandedContextInput
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.input.TargetPositionValidator
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import kotlin.math.ln

object OneMicronIkFeatureEncoder {

    private val robotValidator = RobotDefinitionValidator()
    private val stateValidator = RobotStateValidator()
    private val targetValidator = TargetPositionValidator()

    fun featureNames(
        profile: OneMicronIkFeatureProfile,
        maximumJointCount: Int = ONE_MICRON_MAX_JOINTS
    ): List<String> = buildList {
        addAll(
            listOf(
                "joint_count_ratio",
                "target_x",
                "target_y",
                "target_z",
                "ik_iteration_budget_ratio",
                "log_ik_tolerance",
                "log_ik_damping",
                "log_ik_max_step"
            )
        )
        repeat(maximumJointCount) { index ->
            val joint = index + 1
            add("joint_${joint}_present")
            add("joint_${joint}_is_prismatic")
            add("joint_${joint}_dh_theta")
            add("joint_${joint}_dh_d")
            add("joint_${joint}_dh_a")
            add("joint_${joint}_dh_alpha")
            add("joint_${joint}_minimum")
            add("joint_${joint}_maximum")
            add("joint_${joint}_home")
            add("joint_${joint}_seed")
        }
        if (profile == OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361) {
            addAll(ExpandedContextFeatureCalculator.featureNames(maximumJointCount))
        }
    }

    fun encode(
        robot: RobotDefinition,
        seed: RobotState,
        target: Vec3,
        config: IKConfig,
        profile: OneMicronIkFeatureProfile,
        maximumJointCount: Int = ONE_MICRON_MAX_JOINTS
    ): FloatArray {
        require(robotValidator.validate(robot).isValid) { "Robot definition is invalid for neural IK." }
        require(stateValidator.validate(robot, seed).isValid) { "Seed state is invalid for neural IK." }
        require(targetValidator.isValid(target)) { "Target is invalid for neural IK." }
        require(robot.joints.size in 1..maximumJointCount)
        require(robot.dhParameters.size == robot.joints.size)
        require(seed.jointValues.size == robot.joints.size)
        require(target.x.isFinite() && target.y.isFinite() && target.z.isFinite())
        require(config.maxIterations > 0)
        require(config.tolerance.isFinite() && config.tolerance > 0.0)
        require(config.damping.isFinite() && config.damping > 0.0)
        require(config.maxStep.isFinite() && config.maxStep > 0.0)
        require(seed.jointValues.all(Double::isFinite))
        require(robot.dhParameters.all { dh ->
            dh.theta.isFinite() && dh.d.isFinite() && dh.a.isFinite() && dh.alpha.isFinite()
        })
        require(robot.joints.all { joint ->
            joint.minValue.isFinite() && joint.maxValue.isFinite() && joint.homeValue.isFinite() &&
                joint.minValue < joint.maxValue
        })

        val features = ArrayList<Float>(featureNames(profile, maximumJointCount).size)
        features += robot.joints.size.toFloat() / maximumJointCount
        features += target.x.toFloat()
        features += target.y.toFloat()
        features += target.z.toFloat()
        features += (config.maxIterations.toDouble() / 10_000.0).toFloat()
        features += ln(config.tolerance).toFloat()
        features += ln(config.damping).toFloat()
        features += ln(config.maxStep).toFloat()

        repeat(maximumJointCount) { index ->
            val present = index < robot.joints.size
            val joint = robot.joints.getOrNull(index)
            val dh = robot.dhParameters.getOrNull(index)
            features += if (present) 1f else 0f
            features += if (joint?.type == JointType.PRISMATIC) 1f else 0f
            features += (dh?.theta ?: 0.0).toFloat()
            features += (dh?.d ?: 0.0).toFloat()
            features += (dh?.a ?: 0.0).toFloat()
            features += (dh?.alpha ?: 0.0).toFloat()
            features += (joint?.minValue ?: 0.0).toFloat()
            features += (joint?.maxValue ?: 0.0).toFloat()
            features += (joint?.homeValue ?: 0.0).toFloat()
            features += (seed.jointValues.getOrNull(index) ?: 0.0).toFloat()
        }

        if (profile == OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361) {
            val expanded =
                ExpandedContextFeatureCalculator.calculate(
                    input =
                        ExpandedContextInput(
                            jointTypes = robot.joints.map { it.type.name },
                            theta = robot.dhParameters.map { it.theta },
                            d = robot.dhParameters.map { it.d },
                            a = robot.dhParameters.map { it.a },
                            alpha = robot.dhParameters.map { it.alpha },
                            minimums = robot.joints.map { it.minValue },
                            maximums = robot.joints.map { it.maxValue },
                            homes = robot.joints.map { it.homeValue },
                            seeds = seed.jointValues,
                            targetX = target.x,
                            targetY = target.y,
                            targetZ = target.z,
                            tolerance = config.tolerance,
                            damping = config.damping,
                            maxStep = config.maxStep
                        ),
                    maximumJointCount = maximumJointCount
                )
            expanded.forEach { features += it.toFloat() }
        }

        check(features.size == featureNames(profile, maximumJointCount).size)
        check(features.all(Float::isFinite))
        return features.toFloatArray()
    }
}
