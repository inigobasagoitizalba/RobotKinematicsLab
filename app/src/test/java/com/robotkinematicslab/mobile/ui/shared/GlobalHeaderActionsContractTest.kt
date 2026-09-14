package com.robotkinematicslab.mobile.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalHeaderActionsContractTest {

    @Test
    fun `complete global header is always local then settings`() {
        assertEquals(
            listOf(
                GlobalHeaderAction.LOCAL,
                GlobalHeaderAction.SETTINGS
            ),
            globalHeaderActionOrder(hasSettings = true)
        )
    }

    @Test
    fun `missing callbacks cannot create dead header actions`() {
        assertEquals(
            listOf(GlobalHeaderAction.LOCAL, GlobalHeaderAction.SETTINGS),
            globalHeaderActionOrder(hasSettings = true)
        )
        assertEquals(
            listOf(GlobalHeaderAction.LOCAL),
            globalHeaderActionOrder(hasSettings = false)
        )
    }

    @Test
    fun `header contract cannot contain duplicate actions`() {
        val actual = globalHeaderActionOrder(hasSettings = true)
        assertEquals(actual.size, actual.distinct().size)
    }

    @Test
    fun `settings shortcut is actionable only away from the current settings screen`() {
        assertTrue(globalSettingsShortcutEnabled(selected = false))
        assertFalse(globalSettingsShortcutEnabled(selected = true))
    }
}
