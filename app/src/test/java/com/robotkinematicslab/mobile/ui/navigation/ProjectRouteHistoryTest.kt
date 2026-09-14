package com.robotkinematicslab.mobile.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectRouteHistoryTest {

    @Test
    fun validHistoryReturnsToTheExactOriginInsteadOfTheSectionParent() {
        var history = appendProjectRoute(emptyList(), ProjectRoute.HOME)
        history = appendProjectRoute(history, ProjectRoute.WORKSPACE_3D)

        val fromSettings = popProjectRoute(history)
        assertEquals(ProjectRoute.WORKSPACE_3D, fromSettings.destination)

        val fromWorkspace = popProjectRoute(fromSettings.remainingRouteNames)
        assertEquals(ProjectRoute.HOME, fromWorkspace.destination)
        assertEquals(emptyList<String>(), fromWorkspace.remainingRouteNames)
    }

    @Test
    fun corruptSavedRoutesAreRemovedWithoutCreatingALoopOrInventingADestination() {
        val recovered =
            popProjectRoute(
                listOf(
                    ProjectRoute.HOME.name,
                    "MISSING_SCREEN",
                    ProjectRoute.DATASET_BUILDER.name,
                    ""
                )
            )

        assertEquals(ProjectRoute.DATASET_BUILDER, recovered.destination)
        assertEquals(arrayListOf(ProjectRoute.HOME.name), recovered.remainingRouteNames)
        assertEquals(2, recovered.discardedInvalidEntries)

        val exhausted = popProjectRoute(listOf("UNKNOWN", ""))
        assertNull(exhausted.destination)
        assertEquals(emptyList<String>(), exhausted.remainingRouteNames)
        assertEquals(2, exhausted.discardedInvalidEntries)
    }

    @Test
    fun routeTransitionsReturnNewValuesAndNeverMutateTheSavedInputCollection() {
        val persisted = arrayListOf(ProjectRoute.HOME.name)

        val appended = appendProjectRoute(persisted, ProjectRoute.ROBOT_LAB)
        val popped = popProjectRoute(persisted)

        assertEquals(listOf(ProjectRoute.HOME.name), persisted)
        assertEquals(listOf(ProjectRoute.HOME.name, ProjectRoute.ROBOT_LAB.name), appended)
        assertEquals(ProjectRoute.HOME, popped.destination)
        assertEquals(emptyList<String>(), popped.remainingRouteNames)
        assertNotSame(persisted, appended)
        assertNotSame(persisted, popped.remainingRouteNames)
    }
}
