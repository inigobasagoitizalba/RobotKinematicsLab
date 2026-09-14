package com.robotkinematicslab.mobile.ui.dataset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetDisclosureContractTest {

    @Test
    fun toggleChangesExpansionStateInBothDirections() {
        val opened = nextDatasetDisclosureState(expanded = false)
        val closedAgain = nextDatasetDisclosureState(expanded = opened)

        assertTrue(opened)
        assertFalse(closedAgain)
    }

    @Test
    fun accessibleStateDescriptionMatchesVisibleState() {
        assertEquals("Collapsed", datasetDisclosureStateDescription(expanded = false))
        assertEquals("Expanded", datasetDisclosureStateDescription(expanded = true))
    }
}
