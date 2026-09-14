package com.robotkinematicslab.mobile.validation.robot

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.domain.result.ValidationIssue
import com.robotkinematicslab.mobile.domain.result.ValidationResult
import com.robotkinematicslab.mobile.logging.AppLog

class RobotDefinitionValidator {

    companion object {
        private const val TAG = "RobotDefinitionValidator"
        private const val ACTIVE_DH_ZERO_EPS = 1e-12
        const val MAX_NAME_LENGTH = 160
    }

    fun validate(robot: RobotDefinition): ValidationResult {
        AppLog.d(TAG) {
            "🚀 Robot definition validation started | robot=${robot.name}, dhCount=${robot.dhParameters.size}, jointCount=${robot.joints.size}"
        }

        val issues = mutableListOf<ValidationIssue>()

        if (robot.name.isBlank()) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_NAME_BLANK,
                message = "Robot name must not be blank."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else if (robot.name.length > MAX_NAME_LENGTH) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_NAME_TOO_LONG,
                message = "Robot name must contain at most $MAX_NAME_LENGTH characters."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else if (robot.name.any(Char::isISOControl)) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_NAME_CONTROL_CHARACTER,
                message = "Robot name must not contain line breaks or control characters."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else {
            AppLog.d(TAG) { "✅ Robot name is valid | name=${robot.name}" }
        }

        if (robot.dhParameters.isEmpty()) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_EMPTY_DH_PARAMETERS,
                message = "Robot must contain at least one DH parameter."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else {
            AppLog.d(TAG) { "✅ Robot has DH parameters | count=${robot.dhParameters.size}" }
        }

        if (robot.joints.isEmpty()) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_EMPTY_JOINTS,
                message = "Robot must contain at least one joint definition."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else {
            AppLog.d(TAG) { "✅ Robot has joints | count=${robot.joints.size}" }
        }

        if (robot.dhParameters.size != robot.joints.size) {
            val issue = ValidationIssue(
                code = ValidationCode.ROBOT_DH_JOINT_COUNT_MISMATCH,
                message = "DH parameter count must match joint definition count."
            )
            issues.add(issue)
            AppLog.w(TAG) {
                "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, dhCount=${robot.dhParameters.size}, jointCount=${robot.joints.size}"
            }
        } else {
            AppLog.d(TAG) {
                "✅ DH/joint counts match | dhCount=${robot.dhParameters.size}, jointCount=${robot.joints.size}"
            }
        }

        robot.dhParameters.forEachIndexed { index, parameter ->
            if (
                !parameter.theta.isFinite() ||
                !parameter.d.isFinite() ||
                !parameter.a.isFinite() ||
                !parameter.alpha.isFinite()
            ) {
                val issue = ValidationIssue(
                    code = ValidationCode.ROBOT_DH_PARAMETER_NON_FINITE,
                    message = "DH parameter ${index + 1} must contain only finite values."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, parameter=$parameter"
                }
            }
        }

        val normalizedJointNames = mutableSetOf<String>()

        robot.joints.forEachIndexed { index, joint ->
            AppLog.d(TAG) {
                "🔍 Validating joint | index=$index, name=${joint.name}, type=${joint.type}, min=${joint.minValue}, max=${joint.maxValue}, home=${joint.homeValue}"
            }

            if (joint.name.isBlank()) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_NAME_BLANK,
                    message = "Joint ${index + 1} must have a name."
                )
                issues.add(issue)
                AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
            } else if (joint.name.length > MAX_NAME_LENGTH) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_NAME_TOO_LONG,
                    message = "Joint ${index + 1} name must contain at most $MAX_NAME_LENGTH characters."
                )
                issues.add(issue)
                AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
            } else if (joint.name.any(Char::isISOControl)) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_NAME_CONTROL_CHARACTER,
                    message = "Joint ${index + 1} name must not contain line breaks or control characters."
                )
                issues.add(issue)
                AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
            } else {
                AppLog.d(TAG) { "✅ Joint name is valid | index=$index, name=${joint.name}" }
                if (!normalizedJointNames.add(joint.name.trim().lowercase())) {
                    val issue = ValidationIssue(
                        code = ValidationCode.JOINT_NAME_DUPLICATE,
                        message = "Joint name ${joint.name} is duplicated; joint names must be unique."
                    )
                    issues.add(issue)
                    AppLog.w(TAG) {
                        "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}"
                    }
                }
            }

            if (!joint.minValue.isFinite() || !joint.maxValue.isFinite()) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_LIMIT_NON_FINITE,
                    message = "Joint ${joint.name} limits must be finite."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, min=${joint.minValue}, max=${joint.maxValue}"
                }
            } else if (joint.minValue >= joint.maxValue) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_LIMITS_INVALID,
                    message = "Joint ${joint.name} has invalid limits: minValue must be strictly less than maxValue."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, min=${joint.minValue}, max=${joint.maxValue}"
                }
            } else if (!(joint.maxValue - joint.minValue).isFinite()) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_RANGE_NON_FINITE,
                    message = "Joint ${joint.name} has a non-finite limit span."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, min=${joint.minValue}, max=${joint.maxValue}"
                }
            } else {
                AppLog.d(TAG) {
                    "✅ Joint limits are valid | index=$index, name=${joint.name}, min=${joint.minValue}, max=${joint.maxValue}"
                }
            }

            if (!joint.homeValue.isFinite()) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_HOME_NON_FINITE,
                    message = "Joint ${joint.name} home value must be finite."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, home=${joint.homeValue}"
                }
            } else if (
                joint.minValue.isFinite() &&
                joint.maxValue.isFinite() &&
                (joint.homeValue < joint.minValue || joint.homeValue > joint.maxValue)
            ) {
                val issue = ValidationIssue(
                    code = ValidationCode.JOINT_HOME_OUT_OF_LIMITS,
                    message = "Joint ${joint.name} has a home value outside its limits."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, home=${joint.homeValue}, min=${joint.minValue}, max=${joint.maxValue}"
                }
            } else {
                AppLog.d(TAG) {
                    "✅ Joint home value is within limits | index=$index, name=${joint.name}, home=${joint.homeValue}"
                }
            }

            val dh = robot.dhParameters.getOrNull(index)
            if (dh != null) {
                val activeDhValue =
                    when (joint.type) {
                        JointType.REVOLUTE -> dh.theta
                        JointType.PRISMATIC -> dh.d
                    }

                if (activeDhValue.isFinite() && kotlin.math.abs(activeDhValue) > ACTIVE_DH_ZERO_EPS) {
                    val fieldName =
                        when (joint.type) {
                            JointType.REVOLUTE -> "theta"
                            JointType.PRISMATIC -> "d"
                        }
                    val issue = ValidationIssue(
                        code = ValidationCode.JOINT_ACTIVE_DH_PARAMETER_NON_ZERO,
                        message = "Joint ${joint.name} uses RobotState as its active DH $fieldName; stored $fieldName must be zero, but was $activeDhValue."
                    )
                    issues.add(issue)
                    AppLog.w(TAG) {
                        "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}"
                    }
                }
            }
        }

        return if (issues.isEmpty()) {
            AppLog.d(TAG) { "🏁 Robot definition validation finished | result=SUCCESS, issues=0" }
            ValidationResult.success()
        } else {
            AppLog.w(TAG) {
                "🏁 Robot definition validation finished | result=FAILURE, issues=${issues.size}, issues=$issues"
            }
            ValidationResult.failure(issues)
        }
    }
}
