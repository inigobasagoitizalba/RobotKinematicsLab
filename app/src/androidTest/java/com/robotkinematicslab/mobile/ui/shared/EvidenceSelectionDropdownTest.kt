package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test

class EvidenceSelectionDropdownTest {
    @get:Rule val rule=createComposeRule()
    @Test fun selectedModelAndDisclosureSurviveRecreationAndUnavailableSelectionIsExplicit() {
        val restoration=StateRestorationTester(rule)
        val available=mutableStateOf(listOf("Baseline","Expanded"))
        restoration.setContent { RobotKinematicsLabTheme {
            var selected by rememberSaveable { mutableStateOf("Baseline") }
            Column {
                AppDisclosureSection("Models","Selected: $selected",testTag="models",initiallyExpanded=true) {
                    EvidenceSelectionDropdown("Model",available.value,available.value.firstOrNull { it==selected },{ it },{ selected=it },testTag="model")
                }
                Text("Evidence identity: $selected")
            }
        } }
        rule.onNodeWithTag("model").performClick()
        rule.onNodeWithText("Expanded").performClick()
        rule.onNodeWithText("Evidence identity: Expanded").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Evidence identity: Expanded").assertExists()
        rule.onNodeWithTag("model").assertExists()
        rule.runOnIdle { available.value=listOf("Baseline") }
        rule.onNodeWithText("Choose Model").assertExists()
        rule.onNodeWithText("Evidence identity: Expanded").assertExists()
    }
}
