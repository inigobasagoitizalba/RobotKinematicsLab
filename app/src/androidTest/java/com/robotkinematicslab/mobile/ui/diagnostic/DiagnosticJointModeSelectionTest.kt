package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticJointModeSelectionTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun narrowMobileSelectorHasExactlyOneSelectionAndEqualTapGeometry() {
        val selection = mutableStateOf(DiagnosticJointMode.MIXED)
        composeRule.setContent { RobotKinematicsLabTheme { Box(Modifier.width(320.dp)) {
            DiagnosticJointModeSelector(selection.value, { selection.value = it })
        } } }
        DiagnosticJointMode.entries.forEach { mode ->
            val node = composeRule.onNodeWithTag("diagnostic-mode-${mode.name}")
            if(mode == DiagnosticJointMode.MIXED) node.assertIsSelected() else node.assertIsNotSelected()
        }
        val bounds = DiagnosticJointMode.entries.map { composeRule.onNodeWithTag("diagnostic-mode-${it.name}").fetchSemanticsNode().boundsInRoot }
        bounds.forEach { assertEquals(bounds.first().width, it.width, 1f); assertEquals(bounds.first().height, it.height, 1f) }
        composeRule.onNodeWithTag("diagnostic-mode-AUTO").performClick().assertIsSelected()
        composeRule.onNodeWithTag("diagnostic-mode-MIXED").assertIsNotSelected()
    }
}
