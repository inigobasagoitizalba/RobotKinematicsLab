package com.robotkinematicslab.mobile.ui.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectEditorValidationTest {

    @Test
    fun `valid boundary values pass the project editor gate`() {
        assertTrue(isProjectEditorInputValid("A", ""))
        assertTrue(isProjectEditorInputValid("R".repeat(80), "O".repeat(280)))
        assertTrue(isProjectEditorInputValid("  Valid study  ", "  Reproducible objective  "))
        assertTrue(isProjectEditorInputValid("Valid study", "Line one\nLine two\twith detail"))
    }

    @Test
    fun `blank oversized and control-character values fail the project editor gate`() {
        assertFalse(isProjectEditorInputValid("", "Valid objective"))
        assertFalse(isProjectEditorInputValid("   ", "Valid objective"))
        assertFalse(isProjectEditorInputValid("R".repeat(81), "Valid objective"))
        assertFalse(isProjectEditorInputValid("Valid", "O".repeat(281)))
        assertFalse(isProjectEditorInputValid("Invalid\nname", "Valid objective"))
        assertFalse(isProjectEditorInputValid("Valid", "Invalid\u0000objective"))
    }
}
