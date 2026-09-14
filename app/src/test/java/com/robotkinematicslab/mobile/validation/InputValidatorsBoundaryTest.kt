package com.robotkinematicslab.mobile.validation

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.validation.input.DHParameterValidator
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.input.TargetPositionValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsBoundaryTest {

    @Test
    fun dhValidatorAcceptsFiniteValuesAndRejectsEveryNonFiniteField() {
        val validator = DHParameterValidator()
        val valid = DHParameter(theta = -1.0, d = 0.0, a = Double.MAX_VALUE, alpha = 1.0)
        assertTrue(validator.isValid(valid))
        assertTrue(validator.validateAll(listOf(valid, valid.copy(a = 0.25))))

        val invalid =
            listOf(
                valid.copy(theta = Double.NaN),
                valid.copy(d = Double.POSITIVE_INFINITY),
                valid.copy(a = Double.NEGATIVE_INFINITY),
                valid.copy(alpha = Double.NaN)
            )
        assertTrue(invalid.all { parameter -> !validator.isValid(parameter) })
        assertFalse(validator.validateAll(listOf(valid, invalid.first())))
    }

    @Test
    fun targetValidatorChecksEveryCoordinate() {
        val validator = TargetPositionValidator()
        assertTrue(validator.isValid(Vec3(-1.0, 0.0, Double.MAX_VALUE)))
        assertFalse(validator.isValid(Vec3(Double.NaN, 0.0, 0.0)))
        assertFalse(validator.isValid(Vec3(0.0, Double.POSITIVE_INFINITY, 0.0)))
        assertFalse(validator.isValid(Vec3(0.0, 0.0, Double.NEGATIVE_INFINITY)))
    }

    @Test
    fun robotStateAcceptsInclusiveLimitsAndReportsAllValueDefects() {
        val robot = twoJointRobot()
        val validator = RobotStateValidator()

        assertTrue(validator.validate(robot, RobotState(listOf(-1.0, 2.0))).isValid)

        val invalid = validator.validate(robot, RobotState(listOf(Double.NaN, 2.1)))
        assertFalse(invalid.isValid)
        assertTrue(invalid.hasCode(ValidationCode.STATE_VALUE_NON_FINITE))
        assertTrue(invalid.hasCode(ValidationCode.STATE_VALUE_OUT_OF_LIMITS))
    }

    @Test
    fun robotStateRejectsEmptyAndWrongSizedInputWithoutIndexing() {
        val robot = twoJointRobot()
        val validator = RobotStateValidator()

        val empty = validator.validate(robot, RobotState(emptyList()))
        assertFalse(empty.isValid)
        assertTrue(empty.hasCode(ValidationCode.STATE_EMPTY))
        assertTrue(empty.hasCode(ValidationCode.STATE_SIZE_MISMATCH))

        val oversized = validator.validate(robot, RobotState(listOf(0.0, 0.0, 0.0)))
        assertFalse(oversized.isValid)
        assertTrue(oversized.hasCode(ValidationCode.STATE_SIZE_MISMATCH))
    }

    private fun twoJointRobot(): RobotDefinition {
        return RobotDefinition(
            name = "Boundary robot",
            dhParameters =
                listOf(
                    DHParameter(0.0, 0.0, 0.4, 0.0),
                    DHParameter(0.0, 0.0, 0.3, 0.0)
                ),
            joints =
                listOf(
                    JointDefinition("J1", JointType.REVOLUTE, -1.0, 1.0, 0.0),
                    JointDefinition("J2", JointType.REVOLUTE, -2.0, 2.0, 0.0)
                )
        )
    }

    private fun com.robotkinematicslab.mobile.domain.result.ValidationResult.hasCode(
        code: ValidationCode
    ): Boolean = issues.any { issue -> issue.code == code }
}
