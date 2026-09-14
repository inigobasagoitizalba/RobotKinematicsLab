package com.robotkinematicslab.mobile.ui.navigation

import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.ui.projects.projectRecencyLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNavigationPresentationContractTest {

    @Test
    fun `compact bottom navigation keeps four short unique identifiers`() {
        assertTrue(useCompactProjectBottomNavigation(320f, fontScale = 1f))
        assertTrue(useCompactProjectBottomNavigation(360f, fontScale = 1f))
        assertTrue(useCompactProjectBottomNavigation(411f, fontScale = 2f))
        assertFalse(useCompactProjectBottomNavigation(411f, fontScale = 1f))
        assertFalse(useCompactProjectBottomNavigation(840f, fontScale = 1f))
        assertEquals(4, ProjectSection.entries.size)
        assertEquals(
            listOf("Home", "Prep", "Exp.", "Lib."),
            ProjectSection.entries.map(ProjectSection::compactLabel)
        )
        assertEquals(
            ProjectSection.entries.size,
            ProjectSection.entries.map(ProjectSection::compactLabel).distinct().size
        )
        assertTrue(ProjectSection.entries.all { it.compactLabel.length <= 4 })
        assertTrue(ProjectSection.entries.all { it.compactLabel.isNotBlank() })
    }

    @Test
    fun `invalid widths cannot corrupt responsive navigation selection`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCompactProjectBottomNavigation(Float.NaN, fontScale = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            useCompactProjectBottomNavigation(-1f, fontScale = 1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            useCompactProjectBottomNavigation(360f, fontScale = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            useCompactProjectBottomNavigation(360f, fontScale = Float.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `compact identifiers cannot silently replace full accessible names`() {
        assertEquals(
            listOf("Home", "Prepare", "Experiment", "Library"),
            ProjectSection.entries.map(ProjectSection::label)
        )
        assertFalse(ProjectSection.entries.any { it.label == "Prep" })
        assertFalse(ProjectSection.entries.any { it.label == "Exp." })
        assertFalse(ProjectSection.entries.any { it.label == "Lib." })
    }

    @Test
    fun `project timestamp reports updates and rejects the misleading opened label`() {
        val project =
            ResearchProject(
                id = "presentation-contract",
                name = "Presentation contract",
                objective = "Verify truthful timestamp semantics.",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
                usesLegacyWorkspace = false
            )

        val label = projectRecencyLabel(project)

        assertTrue(label.startsWith("Last updated "))
        assertTrue(label.endsWith(" · Isolated workspace"))
        assertFalse(label.contains("Last opened"))
    }
}
