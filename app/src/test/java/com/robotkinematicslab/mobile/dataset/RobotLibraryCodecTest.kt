package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RobotLibraryCodecTest {

    @Test
    fun roundTripPreservesRobotDefinitions() {
        val expected = DatasetRobotPresets().buildDefaults().take(3)
        val bytes = ByteArrayOutputStream()

        RobotLibraryCodec().write(expected, bytes)
        val actual = RobotLibraryCodec().read(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(expected, actual)
    }

    @Test
    fun aggregateJointBudgetRejectsPathologicalLibraryBeforeWriting() {
        val joints = List(1_000) { index ->
            JointDefinition("J$index", JointType.REVOLUTE, -1.0, 1.0, 0.0)
        }
        val robot =
            RobotDefinition(
                name = "Pathological",
                dhParameters = List(1_000) { DHParameter(0.0, 0.0, 0.1, 0.0) },
                joints = joints
            )
        val library = List(101) { index -> SavedRobot("robot-$index", robot) }

        assertThrows(IllegalArgumentException::class.java) {
            RobotLibraryCodec().write(library, ByteArrayOutputStream())
        }
    }
}
