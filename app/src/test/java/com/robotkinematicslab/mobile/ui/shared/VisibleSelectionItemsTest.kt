package com.robotkinematicslab.mobile.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VisibleSelectionItemsTest {

    @Test
    fun collapsedCollectionKeepsNewestWindowAndEveryOlderSelection() {
        val items = (1..15).toList()

        val visible =
            visibleSelectionItems(
                items = items,
                showAll = false,
                initialVisibleCount = 5,
                isSelected = { it == 12 || it == 15 }
            )

        assertEquals(listOf(1, 2, 3, 4, 5, 12, 15), visible)
    }

    @Test
    fun expandedCollectionReturnsEveryItemInOriginalOrder() {
        val items = (1..15).toList()

        val visible =
            visibleSelectionItems(
                items = items,
                showAll = true,
                initialVisibleCount = 5,
                isSelected = { it == 12 }
            )

        assertEquals(items, visible)
    }

    @Test
    fun collapsedCollectionRequiresAPositiveVisibleWindow() {
        assertThrows(IllegalArgumentException::class.java) {
            visibleSelectionItems(
                items = listOf(1),
                showAll = false,
                initialVisibleCount = 0,
                isSelected = { false }
            )
        }
    }
}
