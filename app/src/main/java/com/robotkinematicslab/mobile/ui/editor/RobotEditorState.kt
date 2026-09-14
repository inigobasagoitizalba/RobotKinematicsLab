package com.robotkinematicslab.mobile.ui.editor

import androidx.compose.runtime.saveable.Saver

data class RobotEditorState(
    val robotName: String = "Robot Name",
    val dhRows: List<DhInputRow> = listOf(DhInputRow())
) {
    companion object {
        val Saver: Saver<RobotEditorState, ArrayList<String>> =
            Saver(
                save = { state -> encodeRobotEditorState(state) },
                restore = { encoded -> decodeRobotEditorState(encoded) }
            )
    }
}

internal fun encodeRobotEditorState(state: RobotEditorState): ArrayList<String> =
    ArrayList<String>(2 + state.dhRows.size * ROBOT_EDITOR_ROW_FIELD_COUNT).apply {
        add(state.robotName)
        add(state.dhRows.size.toString())
        state.dhRows.forEach { row ->
            add(row.jointTypeText)
            add(row.thetaText)
            add(row.dText)
            add(row.aText)
            add(row.alphaText)
            add(row.minText)
            add(row.maxText)
            add(row.homeText)
        }
    }

internal fun decodeRobotEditorState(encoded: ArrayList<String>): RobotEditorState? {
    val rowCount = encoded.getOrNull(1)?.toIntOrNull() ?: return null
    if (rowCount !in 1..MAXIMUM_RESTORED_EDITOR_ROWS) return null
    if (encoded.size != 2 + rowCount * ROBOT_EDITOR_ROW_FIELD_COUNT) return null
    val rows =
        (0 until rowCount).map { rowIndex ->
            val start = 2 + rowIndex * ROBOT_EDITOR_ROW_FIELD_COUNT
            DhInputRow(
                jointTypeText = encoded[start],
                thetaText = encoded[start + 1],
                dText = encoded[start + 2],
                aText = encoded[start + 3],
                alphaText = encoded[start + 4],
                minText = encoded[start + 5],
                maxText = encoded[start + 6],
                homeText = encoded[start + 7]
            )
        }
    return RobotEditorState(robotName = encoded.first(), dhRows = rows)
}

private const val ROBOT_EDITOR_ROW_FIELD_COUNT = 8
private const val MAXIMUM_RESTORED_EDITOR_ROWS = 1_000
