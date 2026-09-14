package com.robotkinematicslab.mobile.validation

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.result.ValidationCode
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotDefinitionValidatorReliabilityTest {

    private val validator = RobotDefinitionValidator()

    @Test
    fun rejectsNonFiniteAndZeroWidthJointDomains() {
        assertHasCode(robot(min = Double.NaN), ValidationCode.JOINT_LIMIT_NON_FINITE)
        assertHasCode(robot(home = Double.POSITIVE_INFINITY), ValidationCode.JOINT_HOME_NON_FINITE)
        assertHasCode(robot(min = 1.0, max = 1.0, home = 1.0), ValidationCode.JOINT_LIMITS_INVALID)
        assertHasCode(
            robot(min = -Double.MAX_VALUE, max = Double.MAX_VALUE),
            ValidationCode.JOINT_RANGE_NON_FINITE
        )
    }

    @Test
    fun rejectsCaseInsensitiveDuplicateJointNames() {
        val robot =
            RobotDefinition(
                name = "Ambiguous robot",
                dhParameters =
                    listOf(
                        DHParameter(0.0, 0.0, 1.0, 0.0),
                        DHParameter(0.0, 0.0, 1.0, 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition("Elbow", JointType.REVOLUTE, -1.0, 1.0, 0.0),
                        JointDefinition(" elbow ", JointType.REVOLUTE, -1.0, 1.0, 0.0)
                    )
            )

        assertHasCode(robot, ValidationCode.JOINT_NAME_DUPLICATE)
    }

    @Test
    fun rejectsNamesThatCannotBeSafelyPersisted() {
        assertHasCode(
            robot().copy(name = "R".repeat(RobotDefinitionValidator.MAX_NAME_LENGTH + 1)),
            ValidationCode.ROBOT_NAME_TOO_LONG
        )
        assertHasCode(
            robot().copy(
                joints =
                    listOf(
                        robot().joints.single().copy(
                            name = "J".repeat(RobotDefinitionValidator.MAX_NAME_LENGTH + 1)
                        )
                    )
            ),
            ValidationCode.JOINT_NAME_TOO_LONG
        )
        assertHasCode(
            robot().copy(name = "Robot\nInjected row"),
            ValidationCode.ROBOT_NAME_CONTROL_CHARACTER
        )
        assertHasCode(
            robot().copy(joints = listOf(robot().joints.single().copy(name = "Joint\rInjected row"))),
            ValidationCode.JOINT_NAME_CONTROL_CHARACTER
        )
    }

    @Test
    fun rejectsNonCanonicalActiveDhFields() {
        val revolute = robot(dh = DHParameter(theta = 0.25, d = 0.1, a = 0.2, alpha = 0.0))
        assertHasCode(revolute, ValidationCode.JOINT_ACTIVE_DH_PARAMETER_NON_ZERO)

        val prismatic =
            robot(
                type = JointType.PRISMATIC,
                min = 0.0,
                max = 1.0,
                home = 0.5,
                dh = DHParameter(theta = 0.2, d = 0.1, a = 0.2, alpha = 0.0)
            )
        assertHasCode(prismatic, ValidationCode.JOINT_ACTIVE_DH_PARAMETER_NON_ZERO)
    }

    @Test
    fun rejectsEveryNonFiniteDhComponentAtRobotBoundary() {
        assertHasCode(robot(dh = DHParameter(Double.NaN, 0.1, 0.2, 0.0)), ValidationCode.ROBOT_DH_PARAMETER_NON_FINITE)
        assertHasCode(robot(dh = DHParameter(0.0, Double.POSITIVE_INFINITY, 0.2, 0.0)), ValidationCode.ROBOT_DH_PARAMETER_NON_FINITE)
        assertHasCode(robot(dh = DHParameter(0.0, 0.1, Double.NEGATIVE_INFINITY, 0.0)), ValidationCode.ROBOT_DH_PARAMETER_NON_FINITE)
        assertHasCode(robot(dh = DHParameter(0.0, 0.1, 0.2, Double.NaN)), ValidationCode.ROBOT_DH_PARAMETER_NON_FINITE)
    }

    @Test
    fun acceptsCanonicalReplacementContract() {
        assertTrue(validator.validate(robot()).isValid)
        assertTrue(
            validator.validate(
                robot(
                    type = JointType.PRISMATIC,
                    min = 0.0,
                    max = 1.0,
                    home = 0.5,
                    dh = DHParameter(theta = 0.2, d = 0.0, a = 0.2, alpha = 0.0)
                )
            ).isValid
        )
    }

    private fun assertHasCode(robot: RobotDefinition, code: ValidationCode) {
        val result = validator.validate(robot)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == code })
    }

    private fun robot(
        type: JointType = JointType.REVOLUTE,
        min: Double = -1.0,
        max: Double = 1.0,
        home: Double = 0.0,
        dh: DHParameter = DHParameter(theta = 0.0, d = 0.1, a = 0.2, alpha = 0.0)
    ): RobotDefinition {
        return RobotDefinition(
            name = "Contract robot",
            dhParameters = listOf(dh),
            joints = listOf(JointDefinition("J1", type, min, max, home))
        )
    }
}
