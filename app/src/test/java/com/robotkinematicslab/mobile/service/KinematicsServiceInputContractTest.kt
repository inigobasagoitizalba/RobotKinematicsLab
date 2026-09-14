package com.robotkinematicslab.mobile.service

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class KinematicsServiceInputContractTest {

    @Test
    fun wrongStateSizeIsRejectedWithoutIndexingCrash() {
        val result = KinematicsService().computeFK(robot(-1.0, 1.0), RobotState(listOf(0.0, 0.0)))
        assertEquals(FKStatus.INVALID_INPUT, result.status)
    }

    @Test
    fun shiftedRevoluteIntervalIsPreservedWithoutGlobalAngleWrapping() {
        val q = 3.5
        val result = KinematicsService().computeFK(robot(3.0, 4.0), RobotState(listOf(q)))

        assertEquals(FKStatus.SUCCESS, result.status)
        assertEquals(cos(q), result.endEffectorPosition.x, 1e-12)
        assertEquals(sin(q), result.endEffectorPosition.y, 1e-12)
    }

    @Test
    fun outOfRangeInputIsRejectedRatherThanSilentlyClamped() {
        val result = KinematicsService().computeFK(robot(-1.0, 1.0), RobotState(listOf(2.0)))
        assertEquals(FKStatus.INVALID_INPUT, result.status)
    }

    private fun robot(min: Double, max: Double): RobotDefinition {
        return RobotDefinition(
            name = "Shifted interval",
            dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = 0.0)),
            joints = listOf(JointDefinition("R1", JointType.REVOLUTE, min, max, (min + max) / 2.0))
        )
    }
}
