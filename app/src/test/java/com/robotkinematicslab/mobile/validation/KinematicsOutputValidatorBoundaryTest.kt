package com.robotkinematicslab.mobile.validation

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKResult
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKDiagnostics
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.domain.result.SolverMetadata
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.validation.output.KinematicsOutputValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KinematicsOutputValidatorBoundaryTest {

    private val validator = KinematicsOutputValidator()

    @Test
    fun canonicalFkOutputIsAccepted() {
        assertTrue(validator.validateFkOutput(fk()).isValid)
    }

    @Test
    fun malformedTransformIsRejectedWithoutThrowing() {
        val malformed = Matrix4(arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0)))

        val validation = validator.validateFkOutput(fk(transform = malformed))

        assertFalse(validation.isValid)
        assertTrue(validation.hasCode(ValidationCode.OUTPUT_TRANSFORM_SHAPE_INVALID))
    }

    @Test
    fun reflectionAndNonFiniteTransformAreRejected() {
        val reflection =
            Matrix4(
                arrayOf(
                    doubleArrayOf(-1.0, 0.0, 0.0, 0.0),
                    doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                    doubleArrayOf(0.0, 0.0, 1.0, 0.0),
                    doubleArrayOf(0.0, 0.0, 0.0, 1.0)
                )
            )
        val reflected = validator.validateFkOutput(fk(transform = reflection))
        assertTrue(reflected.hasCode(ValidationCode.OUTPUT_ROTATION_DETERMINANT_INVALID))

        val nonFiniteRows = Matrix4.identity().m.map { row -> row.clone() }.toTypedArray()
        nonFiniteRows[1][2] = Double.NaN
        val nonFinite = validator.validateFkOutput(fk(transform = Matrix4(nonFiniteRows)))
        assertTrue(nonFinite.hasCode(ValidationCode.OUTPUT_TRANSFORM_NON_FINITE))
    }

    @Test
    fun positionMustMatchTransformTranslation() {
        val validation = validator.validateFkOutput(fk(position = Vec3(0.01, 0.0, 0.0)))

        assertFalse(validation.isValid)
        assertTrue(validation.hasCode(ValidationCode.OUTPUT_POSITION_TRANSFORM_MISMATCH))
    }

    @Test
    fun ikAcceptsConsistentSuccessAtJointLimits() {
        val validation =
            validator.validateIkOutput(
                robot(),
                ik(
                    state = RobotState(listOf(1.0)),
                    status = IKStatus.SUCCESS,
                    converged = true,
                    finalError = 1e-6
                )
            )

        assertTrue(validation.isValid)
    }

    @Test
    fun ikRejectsContradictoryStatusResidualAndState() {
        val contradictory =
            validator.validateIkOutput(
                robot(),
                ik(
                    state = RobotState(listOf(Double.NaN)),
                    status = IKStatus.SUCCESS,
                    converged = false,
                    finalError = 1e-2,
                    detailCode = IKDetailCode.MAX_ITERATIONS_LIMIT_REACHED
                )
            )

        assertFalse(contradictory.isValid)
        assertTrue(contradictory.hasCode(ValidationCode.OUTPUT_IK_STATUS_INCONSISTENT))
        assertTrue(contradictory.hasCode(ValidationCode.OUTPUT_IK_RESIDUAL_EXCEEDS_TOLERANCE))
        assertTrue(contradictory.hasCode(ValidationCode.OUTPUT_IK_FINAL_STATE_NON_FINITE))
    }

    @Test
    fun ikRejectsSuccessWithFailureDetailAndWarningWithoutWarningDetail() {
        val successWithFailureDetail =
            validator.validateIkOutput(
                robot(),
                ik(
                    state = RobotState(listOf(0.0)),
                    status = IKStatus.SUCCESS,
                    converged = true,
                    finalError = 1e-6,
                    detailCode = IKDetailCode.INVALID_TARGET
                )
            )
        val warningWithoutWarningDetail =
            validator.validateIkOutput(
                robot(),
                ik(
                    state = RobotState(listOf(0.0)),
                    status = IKStatus.SUCCESS_WITH_WARNING,
                    converged = true,
                    finalError = 1e-6,
                    detailCode = IKDetailCode.NONE
                )
            )

        assertTrue(successWithFailureDetail.hasCode(ValidationCode.OUTPUT_IK_STATUS_INCONSISTENT))
        assertTrue(warningWithoutWarningDetail.hasCode(ValidationCode.OUTPUT_IK_STATUS_INCONSISTENT))
    }

    @Test
    fun ikRejectsNonFiniteErrorWrongStateSizeAndOutOfLimits() {
        val nonFinite =
            validator.validateIkOutput(
                robot(),
                ik(RobotState(listOf(0.0)), IKStatus.NO_CONVERGENCE, false, Double.NaN)
            )
        assertTrue(nonFinite.hasCode(ValidationCode.OUTPUT_IK_FINAL_ERROR_NON_FINITE))

        val wrongSize =
            validator.validateIkOutput(
                robot(),
                ik(RobotState(emptyList()), IKStatus.NO_CONVERGENCE, false, 0.1)
            )
        assertTrue(wrongSize.hasCode(ValidationCode.OUTPUT_IK_FINAL_STATE_SIZE_MISMATCH))

        val outOfLimits =
            validator.validateIkOutput(
                robot(),
                ik(RobotState(listOf(1.1)), IKStatus.NO_CONVERGENCE, false, 0.1)
            )
        assertTrue(outOfLimits.hasCode(ValidationCode.OUTPUT_IK_FINAL_STATE_OUT_OF_LIMITS))
    }

    @Test
    fun ikSuccessCannotBypassResidualContractByOmittingMetadata() {
        val withoutMetadata =
            IKResult(
                state = RobotState(listOf(0.0)),
                status = IKStatus.SUCCESS,
                converged = true,
                iterations = 1,
                finalError = 100.0,
                metadata = null
            )

        val validation = validator.validateIkOutput(robot(), withoutMetadata)

        assertFalse(validation.isValid)
        assertTrue(validation.hasCode(ValidationCode.OUTPUT_IK_METADATA_INVALID))
    }

    @Test
    fun ikRejectsImpossibleNegativeErrorAndIterationAccounting() {
        val negativeError = validator.validateIkOutput(
            robot(),
            ik(
                state = RobotState(listOf(0.0)),
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                finalError = -1e-9
            )
        )
        val negativeIterations = validator.validateIkOutput(
            robot(),
            ik(
                state = RobotState(listOf(0.0)),
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                finalError = 1e-3
            ).copy(iterations = -1)
        )
        val beyondDeclaredLimit = validator.validateIkOutput(
            robot(),
            ik(
                state = RobotState(listOf(0.0)),
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                finalError = 1e-3
            ).copy(iterations = 101)
        )

        assertTrue(negativeError.hasCode(ValidationCode.OUTPUT_IK_FINAL_ERROR_NEGATIVE))
        assertTrue(negativeIterations.hasCode(ValidationCode.OUTPUT_IK_ITERATIONS_INVALID))
        assertTrue(beyondDeclaredLimit.hasCode(ValidationCode.OUTPUT_IK_ITERATIONS_INVALID))
    }

    @Test
    fun ikDiagnosticsAcceptRealSingularityButRejectImpossibleAccounting() {
        val singular = validator.validateIkOutput(
            robot(),
            ik(
                state = RobotState(listOf(0.0)),
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                finalError = 1e-3
            ).copy(
                diagnostics = IKDiagnostics(
                    seedConditionNumber = Double.POSITIVE_INFINITY,
                    backtrackingRetryCount = 0,
                    solveDurationNanos = 1L
                )
            )
        )
        val corrupt = validator.validateIkOutput(
            robot(),
            ik(
                state = RobotState(listOf(0.0)),
                status = IKStatus.NO_CONVERGENCE,
                converged = false,
                finalError = 1e-3
            ).copy(
                diagnostics = IKDiagnostics(
                    seedConditionNumber = Double.NEGATIVE_INFINITY,
                    backtrackingRetryCount = -1,
                    solveDurationNanos = -1L
                )
            )
        )

        assertTrue(singular.isValid)
        assertTrue(corrupt.hasCode(ValidationCode.OUTPUT_IK_DIAGNOSTICS_INVALID))
    }

    private fun fk(
        transform: Matrix4 = Matrix4.identity(),
        position: Vec3 = Vec3.ZERO
    ): FKResult {
        return FKResult(
            status = FKStatus.SUCCESS,
            endEffectorTransform = transform,
            endEffectorPosition = position,
            jointPositions = listOf(Vec3.ZERO),
            detailCode = FKDetailCode.NONE
        )
    }

    private fun ik(
        state: RobotState,
        status: IKStatus,
        converged: Boolean,
        finalError: Double,
        detailCode: IKDetailCode = IKDetailCode.NONE
    ): IKResult {
        return IKResult(
            state = state,
            status = status,
            converged = converged,
            iterations = 10,
            finalError = finalError,
            detailCode = detailCode,
            metadata =
                SolverMetadata(
                    solverName = "test",
                    maxIterations = 100,
                    tolerance = 1e-5,
                    damping = 0.05,
                    maxStep = 0.1
                )
        )
    }

    private fun robot(): RobotDefinition {
        return RobotDefinition(
            name = "Output robot",
            dhParameters = listOf(DHParameter(0.0, 0.0, 1.0, 0.0)),
            joints = listOf(JointDefinition("J1", JointType.REVOLUTE, -1.0, 1.0, 0.0))
        )
    }

    private fun com.robotkinematicslab.mobile.domain.result.ValidationResult.hasCode(
        code: ValidationCode
    ): Boolean = issues.any { issue -> issue.code == code }
}
