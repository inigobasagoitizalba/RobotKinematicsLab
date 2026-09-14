package com.robotkinematicslab.mobile.solver.ik

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InverseKinematicsFailureQuarantineTest {

    @Test
    fun nonFiniteInitialStateIsRejectedWithoutEchoingContaminatedCoordinates() {
        val robot = DatasetRobotPresets().buildDefaults().first().robot
        val contaminated = RobotState(List(robot.joints.size) { index -> if (index == 0) Double.NaN else 0.0 })

        val result = InverseKinematicsSolver(ForwardKinematicsSolver()).solve(robot, contaminated, Vec3.ZERO)

        assertEquals(IKStatus.INVALID_INPUT, result.status)
        assertEquals(IKDetailCode.INVALID_INITIAL_STATE, result.detailCode)
        assertFalse(result.converged)
        assertEquals(robot.joints.size, result.state.jointValues.size)
        assertTrue(result.state.jointValues.all(Double::isFinite))
        result.state.jointValues.forEachIndexed { index, value ->
            assertTrue(value in robot.joints[index].minValue..robot.joints[index].maxValue)
        }
    }
}
