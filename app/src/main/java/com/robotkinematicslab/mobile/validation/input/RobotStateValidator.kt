package com.robotkinematicslab.mobile.validation.input

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.domain.result.ValidationIssue
import com.robotkinematicslab.mobile.domain.result.ValidationResult
import com.robotkinematicslab.mobile.logging.AppLog

class RobotStateValidator {

    companion object {
        private const val TAG = "RobotStateValidator"
    }

    fun validate(robot: RobotDefinition, state: RobotState): ValidationResult {
        AppLog.d(TAG) {
            "🚀 Robot state validation started | robot=${robot.name}, jointCount=${robot.joints.size}, stateSize=${state.jointValues.size}, state=${state.jointValues}"
        }

        val issues = mutableListOf<ValidationIssue>()

        if (state.jointValues.isEmpty()) {
            val issue = ValidationIssue(
                code = ValidationCode.STATE_EMPTY,
                message = "Robot state must contain at least one joint value."
            )
            issues.add(issue)
            AppLog.w(TAG) { "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}" }
        } else {
            AppLog.d(TAG) { "✅ Robot state is not empty | valueCount=${state.jointValues.size}" }
        }

        if (robot.joints.size != state.jointValues.size) {
            val issue = ValidationIssue(
                code = ValidationCode.STATE_SIZE_MISMATCH,
                message = "Robot state joint value count must match robot joint count."
            )
            issues.add(issue)
            AppLog.w(TAG) {
                "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, jointCount=${robot.joints.size}, stateSize=${state.jointValues.size}"
            }

            AppLog.w(TAG) {
                "🏁 Robot state validation finished early | result=FAILURE, issues=${issues.size}, issues=$issues"
            }
            return ValidationResult.failure(issues)
        } else {
            AppLog.d(TAG) {
                "✅ Robot state size matches robot joints | jointCount=${robot.joints.size}, stateSize=${state.jointValues.size}"
            }
        }

        state.jointValues.forEachIndexed { index, value ->
            val joint = robot.joints[index]

            AppLog.d(TAG) {
                "🔍 Validating joint state value | index=$index, jointName=${joint.name}, value=$value, limits=[${joint.minValue}, ${joint.maxValue}]"
            }

            if (!value.isFinite()) {
                val issue = ValidationIssue(
                    code = ValidationCode.STATE_VALUE_NON_FINITE,
                    message = "Joint ${joint.name} value is not finite."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}, value=$value"
                }
            } else if (value < joint.minValue || value > joint.maxValue) {
                val issue = ValidationIssue(
                    code = ValidationCode.STATE_VALUE_OUT_OF_LIMITS,
                    message = "Joint ${joint.name} value $value is outside limits [${joint.minValue}, ${joint.maxValue}]."
                )
                issues.add(issue)
                AppLog.w(TAG) {
                    "⚠️ Validation issue added | code=${issue.code}, message=${issue.message}"
                }
            } else {
                AppLog.d(TAG) {
                    "✅ Joint state value is valid | index=$index, jointName=${joint.name}, value=$value"
                }
            }
        }

        return if (issues.isEmpty()) {
            AppLog.d(TAG) { "🏁 Robot state validation finished | result=SUCCESS, issues=0" }
            ValidationResult.success()
        } else {
            AppLog.w(TAG) {
                "🏁 Robot state validation finished | result=FAILURE, issues=${issues.size}, issues=$issues"
            }
            ValidationResult.failure(issues)
        }
    }
}