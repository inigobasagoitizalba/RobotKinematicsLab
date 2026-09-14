package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

class FeatureSetExplorerUiTest {
    @get:Rule val rule = createComposeRule()
    private fun explainFirst() {
        rule.onNodeWithTag("feature-catalog-open").performClick()
        rule.onAllNodes(hasText("Explain classification.", substring = true))[0].performClick()
    }
    @Test fun confirmedCsvIsBoundedAndReturnKeepsOriginDraftAfterRestoration() {
        val csv = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "feature-preview-ui.csv")
        csv.writeText("x,y\n" + (1..25).joinToString("\n") { "0.123456789012345678,$it" })
        val restoration = StateRestorationTester(rule)
        restoration.setContent { RobotKinematicsLabTheme { Column {
            var draft by rememberSaveable { mutableStateOf("Original experiment") }
            OutlinedTextField(draft, { draft = it })
            FeatureSetExplorer("Single Run / Experiment design", csv.absolutePath)
        } } }
        rule.onNodeWithText("Original experiment").performTextReplacement("Retained experiment")
        explainFirst()
        rule.onNodeWithText("View source CSV").performScrollTo().performClick()
        rule.onNodeWithText("Open source CSV?").assertExists()
        rule.onNodeWithTag("csv-cell-0").assertDoesNotExist()
        rule.onNodeWithTag("confirm-source-csv").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("20 preview rows; 2 columns; additional rows omitted.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("feature-inspector-list").performScrollToNode(hasTestTag("csv-cell-0"))
        rule.onNodeWithText("0.123456789012345678").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("feature-catalog-return").performClick()
        rule.onNodeWithText("Retained experiment").assertExists()
        csv.delete()
    }
    @Test fun missingFileReportsFailureAfterConfirmationAndCanReturn() {
        val missing = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "absent-${System.nanoTime()}.csv")
        rule.setContent { RobotKinematicsLabTheme { FeatureSetExplorer("Verified IK / Experiment contract", missing.absolutePath) } }
        explainFirst()
        rule.onNodeWithText("View source CSV").performScrollTo().performClick()
        rule.onNodeWithTag("confirm-source-csv").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("csv-preview-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("feature-catalog-return").performClick()
        rule.onNodeWithTag("feature-catalog-open").assertExists()
    }
    @Test fun noDedicatedCsvDoesNotInventSourceAndAllOriginsReturn() {
        var origin by mutableStateOf("Dataset Factory / Planned AI comparisons")
        rule.setContent { RobotKinematicsLabTheme { FeatureSetExplorer(origin, null) } }
        listOf("Dataset Factory / Planned AI comparisons", "Single Run / Experiment design", "Verified IK / Experiment contract", "Closed Loop / Scientific acceptance gate", "Explainable AI / Model evidence").forEach { label ->
            rule.runOnIdle { origin = label }
            explainFirst()
            rule.onNodeWithText("View source CSV").performScrollTo().assertIsNotEnabled()
            rule.onNodeWithText("Back to $label").performClick()
            rule.onNodeWithTag("feature-catalog-open").assertExists()
        }
    }
}
