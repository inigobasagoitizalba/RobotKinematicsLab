package com.robotkinematicslab.mobile.validation.output

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.domain.result.ValidationIssue
import com.robotkinematicslab.mobile.domain.result.ValidationResult
import com.robotkinematicslab.mobile.logging.AppLog
import kotlin.math.abs
import kotlin.math.hypot

class KinematicsOutputValidator {

    companion object {
        private const val TAG = "KinematicsOutputValidator"

        private const val ORTHONORMAL_EPS = 1e-3
        private const val DETERMINANT_EPS = 1e-3
        private const val LAST_ROW_EPS = 1e-9
        private const val POSITION_CONSISTENCY_EPS = 1e-9
    }

    fun validateFkOutput(result: FKResult): ValidationResult {
        AppLog.d(TAG) {
            "🚀 validateFkOutput started | jointPositions=${result.jointPositions.size}, endEffector=${result.endEffectorPosition}"
        }

        val issues = mutableListOf<ValidationIssue>()

        val matrix = result.endEffectorTransform.m
        val matrixShapeValid = validateMatrixShape(matrix, issues)
        if (matrixShapeValid) {
            validateMatrixFinite(matrix, issues)
            validateLastRow(matrix, issues)
            validateRotationBlock(matrix, issues)
        }
        validateEndEffectorPosition(result, issues)
        if (matrixShapeValid) {
            validatePositionTransformConsistency(result, issues)
        }
        validateJointPositions(result, issues)

        return buildResult(issues, "FK")
    }

    private fun validateMatrixShape(
        matrix: Array<DoubleArray>,
        issues: MutableList<ValidationIssue>
    ): Boolean {
        val valid = matrix.size == 4 && matrix.all { row -> row.size == 4 }
        if (!valid) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_TRANSFORM_SHAPE_INVALID,
                    message = "End-effector transform must be exactly 4x4."
                )
            )
        }
        return valid
    }

    fun validateIkOutput(
        robot: RobotDefinition,
        result: IKResult
    ): ValidationResult {
        AppLog.d(TAG) {
            "🚀 validateIkOutput started | robot=${robot.name}, state=${result.state.jointValues}, finalError=${result.finalError}"
        }

        val issues = mutableListOf<ValidationIssue>()

        if (!result.finalError.isFinite()) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_FINAL_ERROR_NON_FINITE,
                    message = "IK final error must be finite."
                )
            )
        } else if (result.finalError < 0.0) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_FINAL_ERROR_NEGATIVE,
                    message = "IK final error cannot be negative."
                )
            )
        }

        val declaredIterationLimit = result.metadata?.maxIterations
        if (
            result.iterations < 0 ||
            (declaredIterationLimit != null && result.iterations > declaredIterationLimit)
        ) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_ITERATIONS_INVALID,
                    message = "IK iteration count must be non-negative and cannot exceed its declared limit."
                )
            )
        }

        val diagnostics = result.diagnostics
        val conditionNumberValid =
            diagnostics.seedConditionNumber.isNaN() ||
                diagnostics.seedConditionNumber == Double.POSITIVE_INFINITY ||
                (diagnostics.seedConditionNumber.isFinite() && diagnostics.seedConditionNumber >= 1.0)
        if (
            !conditionNumberValid ||
            diagnostics.backtrackingRetryCount < 0 ||
            diagnostics.solveDurationNanos < 0L
        ) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_DIAGNOSTICS_INVALID,
                    message = "IK diagnostics contain an impossible condition number, retry count, or duration."
                )
            )
        }

        val successStatus =
            result.status == IKStatus.SUCCESS || result.status == IKStatus.SUCCESS_WITH_WARNING
        if (result.converged != successStatus) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_STATUS_INCONSISTENT,
                    message = "IK converged flag and status must describe the same outcome."
                )
            )
        }

        val successDetailConsistent =
            when (result.status) {
                IKStatus.SUCCESS -> result.detailCode == IKDetailCode.NONE
                IKStatus.SUCCESS_WITH_WARNING ->
                    result.detailCode == IKDetailCode.NEAR_SINGULARITY_WARNING ||
                        result.detailCode == IKDetailCode.CRITICAL_SINGULARITY_DAMPED
                else -> true
            }
        if (!successDetailConsistent) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_STATUS_INCONSISTENT,
                    message = "IK success status and detail code must describe the same outcome."
                )
            )
        }

        val tolerance = result.metadata?.tolerance
        val metadataValid =
            result.metadata?.let { metadata ->
                metadata.solverName.isNotBlank() &&
                    metadata.maxIterations > 0 &&
                    metadata.tolerance.isFinite() && metadata.tolerance > 0.0 &&
                    metadata.damping.isFinite() && metadata.damping > 0.0 &&
                    metadata.maxStep.isFinite() && metadata.maxStep > 0.0
            } == true
        if (successStatus && !metadataValid) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_METADATA_INVALID,
                    message = "A successful IK result must declare a finite, positive solver contract."
                )
            )
        }
        if (
            successStatus &&
            tolerance != null && tolerance.isFinite() && tolerance > 0.0 &&
            result.finalError.isFinite() && result.finalError > tolerance
        ) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_RESIDUAL_EXCEEDS_TOLERANCE,
                    message = "A successful IK result must satisfy its declared tolerance."
                )
            )
        }

        if (result.state.jointValues.size != robot.joints.size) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_IK_FINAL_STATE_SIZE_MISMATCH,
                    message = "IK final state size must match robot joint count."
                )
            )
        } else {
            result.state.jointValues.forEachIndexed { index, value ->
                val joint = robot.joints[index]

                if (!value.isFinite()) {
                    issues.add(
                        ValidationIssue(
                            code = ValidationCode.OUTPUT_IK_FINAL_STATE_NON_FINITE,
                            message = "IK final state joint ${joint.name} is non-finite."
                        )
                    )
                } else if (value < joint.minValue || value > joint.maxValue) {
                    issues.add(
                        ValidationIssue(
                            code = ValidationCode.OUTPUT_IK_FINAL_STATE_OUT_OF_LIMITS,
                            message = "IK final state joint ${joint.name} is outside limits."
                        )
                    )
                }
            }
        }

        return buildResult(issues, "IK")
    }

    private fun validateMatrixFinite(
        matrix: Array<DoubleArray>,
        issues: MutableList<ValidationIssue>
    ) {
        for (row in matrix.indices) {
            for (col in matrix[row].indices) {
                val value = matrix[row][col]
                if (!value.isFinite()) {
                    issues.add(
                        ValidationIssue(
                            code = ValidationCode.OUTPUT_TRANSFORM_NON_FINITE,
                            message = "Transform contains a non-finite value at [$row][$col]."
                        )
                    )
                    return
                }
            }
        }
    }

    private fun validateLastRow(
        matrix: Array<DoubleArray>,
        issues: MutableList<ValidationIssue>
    ) {
        val expected = doubleArrayOf(0.0, 0.0, 0.0, 1.0)

        for (col in 0..3) {
            val error = abs(matrix[3][col] - expected[col])
            if (error > LAST_ROW_EPS) {
                issues.add(
                    ValidationIssue(
                        code = ValidationCode.OUTPUT_TRANSFORM_LAST_ROW_INVALID,
                        message = "Transform last row must be [0, 0, 0, 1]."
                    )
                )
                return
            }
        }
    }

    private fun validateRotationBlock(
        matrix: Array<DoubleArray>,
        issues: MutableList<ValidationIssue>
    ) {
        val r00 = matrix[0][0]
        val r01 = matrix[0][1]
        val r02 = matrix[0][2]

        val r10 = matrix[1][0]
        val r11 = matrix[1][1]
        val r12 = matrix[1][2]

        val r20 = matrix[2][0]
        val r21 = matrix[2][1]
        val r22 = matrix[2][2]

        val c0Norm = hypot(hypot(r00, r10), r20)
        val c1Norm = hypot(hypot(r01, r11), r21)
        val c2Norm = hypot(hypot(r02, r12), r22)

        val c01Dot = r00 * r01 + r10 * r11 + r20 * r21
        val c02Dot = r00 * r02 + r10 * r12 + r20 * r22
        val c12Dot = r01 * r02 + r11 * r12 + r21 * r22

        val orthonormalOk =
            abs(c0Norm - 1.0) <= ORTHONORMAL_EPS &&
                    abs(c1Norm - 1.0) <= ORTHONORMAL_EPS &&
                    abs(c2Norm - 1.0) <= ORTHONORMAL_EPS &&
                    abs(c01Dot) <= ORTHONORMAL_EPS &&
                    abs(c02Dot) <= ORTHONORMAL_EPS &&
                    abs(c12Dot) <= ORTHONORMAL_EPS

        if (!orthonormalOk) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_ROTATION_NOT_ORTHONORMAL,
                    message = "Transform rotation block is not orthonormal."
                )
            )
        }

        val determinant =
            r00 * (r11 * r22 - r12 * r21) -
                    r01 * (r10 * r22 - r12 * r20) +
                    r02 * (r10 * r21 - r11 * r20)

        if (abs(determinant - 1.0) > DETERMINANT_EPS) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_ROTATION_DETERMINANT_INVALID,
                    message = "Transform rotation determinant is not close to 1."
                )
            )
        }
    }

    private fun validateEndEffectorPosition(
        result: FKResult,
        issues: MutableList<ValidationIssue>
    ) {
        val p = result.endEffectorPosition
        if (!p.x.isFinite() || !p.y.isFinite() || !p.z.isFinite()) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_POSITION_NON_FINITE,
                    message = "End-effector position must be finite."
                )
            )
        }
    }

    private fun validatePositionTransformConsistency(
        result: FKResult,
        issues: MutableList<ValidationIssue>
    ) {
        val position = result.endEffectorPosition
        val matrix = result.endEffectorTransform.m
        if (!position.isFinite()) return

        val inconsistent =
            abs(position.x - matrix[0][3]) > POSITION_CONSISTENCY_EPS ||
                    abs(position.y - matrix[1][3]) > POSITION_CONSISTENCY_EPS ||
                    abs(position.z - matrix[2][3]) > POSITION_CONSISTENCY_EPS
        if (inconsistent) {
            issues.add(
                ValidationIssue(
                    code = ValidationCode.OUTPUT_POSITION_TRANSFORM_MISMATCH,
                    message = "End-effector position must match the transform translation."
                )
            )
        }
    }

    private fun validateJointPositions(
        result: FKResult,
        issues: MutableList<ValidationIssue>
    ) {
        result.jointPositions.forEachIndexed { index, position ->
            if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) {
                issues.add(
                    ValidationIssue(
                        code = ValidationCode.OUTPUT_JOINT_POSITION_NON_FINITE,
                        message = "Joint position at index $index must be finite."
                    )
                )
                return
            }
        }
    }

    private fun buildResult(
        issues: List<ValidationIssue>,
        label: String
    ): ValidationResult {
        return if (issues.isEmpty()) {
            AppLog.d(TAG) {
                "✅ $label output validation passed"
            }
            ValidationResult.success()
        } else {
            AppLog.w(TAG) {
                "⚠️ $label output validation failed | issues=$issues"
            }
            ValidationResult.failure(issues)
        }
    }
}
