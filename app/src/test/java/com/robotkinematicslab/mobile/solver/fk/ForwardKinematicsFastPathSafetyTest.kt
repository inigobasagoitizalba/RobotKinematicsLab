package com.robotkinematicslab.mobile.solver.fk

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.result.FKDetailCode
import com.robotkinematicslab.mobile.domain.result.FKStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ForwardKinematicsFastPathSafetyTest {

    @Test
    fun positionOnlyPathRejectsNonFiniteOrientationEvenWhenTranslationWouldLookFinite() {
        val robot =
            RobotDefinition(
                name = "Corrupt alpha",
                dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = Double.NaN)),
                joints =
                    listOf(
                        JointDefinition(
                            name = "Joint 1",
                            type = JointType.REVOLUTE,
                            minValue = -1.0,
                            maxValue = 1.0,
                            homeValue = 0.0
                        )
                    )
            )
        val output = MutableFKPositionOnlyResult()

        val accepted = ForwardKinematicsSolver().solvePositionOnlyInto(robot, listOf(0.0), output)

        assertFalse(accepted)
        assertEquals(FKStatus.INVALID_INPUT, output.status)
        assertEquals(FKDetailCode.INVALID_ROBOT_DEFINITION, output.detailCode)
    }

    @Test
    fun positionOnlyPathRejectsFiniteJointOutsideDeclaredLimits() {
        val robot =
            RobotDefinition(
                name = "Bounded",
                dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = 0.0)),
                joints = listOf(JointDefinition("Joint 1", JointType.REVOLUTE, -1.0, 1.0, 0.0))
            )
        val output = MutableFKPositionOnlyResult()

        val accepted = ForwardKinematicsSolver().solvePositionOnlyInto(robot, listOf(1.1), output)

        assertFalse(accepted)
        assertEquals(FKStatus.INVALID_INPUT, output.status)
        assertEquals(FKDetailCode.INVALID_STATE, output.detailCode)
    }
}
