package com.robotkinematicslab.mobile.ui.editor

import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotEditorTutorialActionsTest {

    private val validState =
        RobotEditorState(
            robotName = "Tutorial robot",
            dhRows =
                listOf(
                    DhInputRow(
                        jointTypeText = "REVOLUTE",
                        thetaText = "0",
                        dText = "300",
                        aText = "10",
                        alphaText = "90",
                        minText = "-180",
                        maxText = "180",
                        homeText = "0"
                    )
                )
        )

    @Test
    fun `joint type reports choose only for a real change`() {
        val action = robotJointTypeTutorialAction("REVOLUTE", "PRISMATIC", index = 1)

        assertEquals(TutorialTargets.RobotJointType, action?.target)
        assertEquals(TutorialInteraction.CHOOSE, action?.interaction)
        assertNull(robotJointTypeTutorialAction("REVOLUTE", "REVOLUTE", index = 0))
    }

    @Test
    fun `valid changed editor value reports type to dh target`() {
        val updated =
            validState.copy(
                dhRows = validState.dhRows.map { row -> row.copy(aText = "25") }
            )
        val action =
            validRobotEditorEditTutorialAction(
                previous = validState,
                updated = updated,
                target = TutorialTargets.RobotDhParameters,
                detail = "Valid edit."
            )

        assertEquals(TutorialTargets.RobotDhParameters, action?.target)
        assertEquals(TutorialInteraction.TYPE, action?.interaction)
    }

    @Test
    fun `invalid or unchanged editor value emits no completion`() {
        val invalid =
            validState.copy(
                dhRows = validState.dhRows.map { row -> row.copy(maxText = "not-a-number") }
            )

        assertNull(
            validRobotEditorEditTutorialAction(
                previous = validState,
                updated = invalid,
                target = TutorialTargets.RobotDhParameters,
                detail = "Invalid edit."
            )
        )
        assertNull(
            validRobotEditorEditTutorialAction(
                previous = validState,
                updated = validState,
                target = TutorialTargets.RobotDhParameters,
                detail = "Unchanged edit."
            )
        )
    }
}
