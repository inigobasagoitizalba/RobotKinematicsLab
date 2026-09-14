package com.robotkinematicslab.mobile.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RobotEditorStateSaverTest {

    @Test
    fun validDraftRestoresEveryDhFieldIncludingInvalidTextForUserCorrection() {
        val state =
            RobotEditorState(
                robotName = "Pending arm",
                dhRows =
                    listOf(
                        DhInputRow(
                            jointTypeText = "PRISMATIC",
                            thetaText = "90",
                            dText = "invalid pending value",
                            aText = "125.5",
                            alphaText = "-90",
                            minText = "0",
                            maxText = "800",
                            homeText = "5"
                        ),
                        DhInputRow(jointTypeText = "REVOLUTE", aText = "300")
                    )
            )

        assertEquals(state, decodeRobotEditorState(encodeRobotEditorState(state)))
    }

    @Test
    fun corruptDraftShapeOrUnboundedRowCountIsRejectedDuringRestoration() {
        assertNull(decodeRobotEditorState(arrayListOf("name", "2", "REVOLUTE")))
        assertNull(decodeRobotEditorState(arrayListOf("name", "-1")))
        assertNull(decodeRobotEditorState(arrayListOf("name", "1001")))
        assertNull(decodeRobotEditorState(arrayListOf("name", "not-a-count")))
    }
}
