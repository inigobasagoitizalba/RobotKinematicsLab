package com.robotkinematicslab.mobile.ui.workspace

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceRobotLibrarySelectorTest {

    @Test
    fun `all dataset-ready robots keep their persistent library identities`() {
        val saved = DatasetRobotPresets().buildDefaults()

        val choices = buildRobotChoices(currentRobot = null, libraryRobots = saved)

        assertEquals(10, choices.size)
        assertEquals(saved.map { "library-${it.id}" }, choices.map { it.id })
        assertTrue(choices.all { it.source == WorkspaceRobotSource.LIBRARY })
    }

    @Test
    fun `current Robot Lab duplicate does not create a misleading eleventh model`() {
        val saved = DatasetRobotPresets().buildDefaults()

        val choices = buildRobotChoices(currentRobot = saved[2].robot.copy(), libraryRobots = saved)

        assertEquals(saved.size, choices.size)
        assertEquals(1, choices.count { it.fingerprint == choices[2].fingerprint })
    }

    @Test
    fun `DH preview is finite and includes base plus every link endpoint`() {
        DatasetRobotPresets().buildDefaults().forEach { saved ->
            val points = buildWorkspaceRobotPreview(saved.robot)

            assertEquals(saved.id, saved.robot.joints.size + 1, points.size)
            assertTrue(saved.id, points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        }
    }

    @Test
    fun `preview projection fits every valid robot inside its image card`() {
        DatasetRobotPresets().buildDefaults().forEach { saved ->
            val projected =
                projectWorkspaceRobotPreview(
                    points = buildWorkspaceRobotPreview(saved.robot),
                    width = 480f,
                    height = 240f
                )

            assertTrue(saved.id, projected.isNotEmpty())
            assertTrue(saved.id, projected.all { it.x in 0f..480f && it.y in 0f..240f })
        }
    }
}
