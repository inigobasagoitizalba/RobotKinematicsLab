package com.robotkinematicslab.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotLabDisclosureContractTest {

    @Test
    fun `disclosures keep the exact requested titles and begin collapsed`() {
        assertEquals(
            listOf("Viewport info", "Layer 1 debug"),
            RobotLabDisclosureSpec.entries.map { it.title }
        )
        assertTrue(RobotLabDisclosureSpec.entries.all { !it.initiallyExpanded })
        assertEquals(2, RobotLabDisclosureSpec.entries.map { it.testTag }.distinct().size)
    }

    @Test
    fun `legacy title variants cannot satisfy the disclosure contract`() {
        val acceptedTitles = RobotLabDisclosureSpec.entries.map { it.title }.toSet()

        assertFalse("Viewport Info" in acceptedTitles)
        assertFalse("Layer 1 Debug" in acceptedTitles)
        assertFalse("Debug" in acceptedTitles)
    }

    @Test
    fun `toggle and accessibility state remain reversible and synchronized`() {
        val opened = nextRobotLabDisclosureState(expanded = false)
        val closedAgain = nextRobotLabDisclosureState(expanded = opened)

        assertTrue(opened)
        assertFalse(closedAgain)
        assertEquals("Expanded", robotLabDisclosureStateDescription(opened))
        assertEquals("Collapsed", robotLabDisclosureStateDescription(closedAgain))
    }

    @Test
    fun `accessible label contains only the title while state has its own channel`() {
        RobotLabDisclosureSpec.entries.forEach { spec ->
            val label = robotLabDisclosureContentDescription(spec)

            assertEquals(spec.title, label)
            assertFalse(label.contains("Collapsed", ignoreCase = true))
            assertFalse(label.contains("Expanded", ignoreCase = true))
        }
        assertTrue(ROBOT_LAB_DISCLOSURE_MIN_TOUCH_TARGET_DP >= 48)
    }
}
