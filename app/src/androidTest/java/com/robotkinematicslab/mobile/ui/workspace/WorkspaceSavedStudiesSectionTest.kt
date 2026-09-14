package com.robotkinematicslab.mobile.ui.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import org.junit.Rule
import org.junit.Test

class WorkspaceSavedStudiesSectionTest {
    @get:Rule val rule = createComposeRule()

    private fun studies(count: Int) = (1..count).map {
        RobotWorkspaceStudySummary("id-$it", "Study $it", "Robot $it", "fp-$it", it.toLong(),8192,20,.18721,"/unused/$it","/unused/$it/data")
    }

    @Test fun zeroOneEightNineThirteenAndManyKeepScopeAndCollapseIndependent() {
        val count = mutableStateOf(0)
        rule.setContent { RobotKinematicsLabTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WorkspaceSavedStudiesSection(studies(count.value),null,{})
            }
        } }
        rule.onNodeWithTag("workspace-saved-studies").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("No workspace studies have been saved yet.").assertExists()
        listOf(1,8,9,13,100).forEach { n ->
            rule.runOnIdle { count.value = n }
            rule.onNodeWithText("Study 1").assertExists()
            if (n <= 8) {
                rule.onNodeWithTag("workspace-studies-show-all").assertDoesNotExist()
                rule.onNodeWithText("Study $n").assertExists()
            } else {
                rule.onNodeWithText("Study 9").assertDoesNotExist()
                rule.onNodeWithTag("workspace-studies-show-all").performScrollTo().performClick()
                rule.onNodeWithText("Study $n").assertExists()
                rule.onNodeWithText("Show newest 8").performScrollTo().performClick()
                rule.onNodeWithText("Study 9").assertDoesNotExist()
                rule.onNodeWithText("Collapse saved workspace studies").performScrollTo().performClick()
                rule.onNodeWithTag("workspace-saved-studies").assertIsDisplayed()
                rule.onNodeWithText("Study 1").assertDoesNotExist()
                rule.onNodeWithTag("workspace-saved-studies").performSemanticsAction(SemanticsActions.OnClick)
                rule.onNodeWithText("Study 1").assertExists()
            }
        }
    }

    @Test fun projectSwitchRestorationAndNewResultsPreserveIndependentScopeAndSelection() {
        val project = mutableStateOf("a")
        val count = mutableStateOf(13)
        val selected = mutableStateOf<String?>(null)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { RobotKinematicsLabTheme {
            val holder = rememberSaveableStateHolder()
            holder.SaveableStateProvider(project.value) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WorkspaceSavedStudiesSection(studies(count.value),selected.value,{ selected.value = it.studyId })
                }
            }
        } }
        rule.onNodeWithTag("workspace-saved-studies").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("workspace-studies-show-all").performScrollTo().performClick()
        rule.runOnIdle { count.value = 14; selected.value = "id-13" }
        rule.onNodeWithText("Showing all 14 workspace studies.").assertExists()
        rule.onNodeWithText("Collapse saved workspace studies").performScrollTo().performClick()
        rule.onNodeWithTag("workspace-saved-studies").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Study 14").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Study 14").assertExists()
        rule.runOnIdle { project.value = "b" }
        rule.onNodeWithText("Study 1").assertDoesNotExist()
        rule.onNodeWithTag("workspace-saved-studies").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Study 14").assertDoesNotExist()
        rule.onNodeWithText("Study 13").assertExists()
        rule.runOnIdle { project.value = "a" }
        rule.onNodeWithText("Study 14").assertExists()
        rule.onNodeWithText("Show newest 8").performScrollTo().performClick()
        rule.onNodeWithText("Study 13").assertExists()
        rule.onNodeWithText("Study 14").assertDoesNotExist()
    }
}
