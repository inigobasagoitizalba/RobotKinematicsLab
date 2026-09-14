package com.robotkinematicslab.mobile.math

import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.math.utility.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceReliabilityTest {

    @Test
    fun clampUsesFullThreeDimensionalDistance() {
        val clamped = Workspace.clampToReach(Vec3(3.0, 4.0, 0.0), 2.0)

        assertEquals(2.0, clamped.norm(), 1e-12)
        assertEquals(1.2, clamped.x, 1e-12)
        assertEquals(1.6, clamped.y, 1e-12)
    }

    @Test
    fun clampDoesNotMistakeFiniteOverflowForAnInvalidTarget() {
        val huge = Double.MAX_VALUE / 2.0
        val clamped = Workspace.clampToReach(Vec3(huge, huge, 0.0), 2.0)

        assertTrue(clamped.isFinite())
        assertEquals(2.0, clamped.norm(), 1e-12)
    }
}
