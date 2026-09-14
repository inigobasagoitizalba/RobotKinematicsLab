package com.robotkinematicslab.mobile.ui.dataset

import org.junit.Assert.assertEquals
import org.junit.Test

class RobotNameDialogLogicTest {

    @Test
    fun normalizationTrimsAndCollapsesWhitespace() {
        assertEquals("My research robot", normalizeRobotName("  My   research\nrobot  "))
    }

    @Test
    fun generatedNameUsesFirstAvailableStableNumber() {
        assertEquals(
            "Custom Robot 2",
            nextCustomRobotName(listOf("Sawyer", " custom   robot 1 ", "Custom Robot 3"))
        )
    }
}
