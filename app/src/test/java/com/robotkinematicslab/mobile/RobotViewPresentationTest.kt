package com.robotkinematicslab.mobile

import com.robotkinematicslab.mobile.domain.JointType
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class RobotViewPresentationTest {

    @Test
    fun revoluteJoint_isPresentedInDegrees() {
        assertEquals(
            "J1 (R) = 90.0°",
            formatJointValueForDisplay("J1", PI / 2.0, JointType.REVOLUTE)
        )
    }

    @Test
    fun prismaticJoint_isPresentedInMillimetres() {
        assertEquals(
            "J2 (P) = 125.0 mm",
            formatJointValueForDisplay("J2", 0.125, JointType.PRISMATIC)
        )
    }
}
