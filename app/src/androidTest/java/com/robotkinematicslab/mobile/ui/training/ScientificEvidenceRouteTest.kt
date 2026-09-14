package com.robotkinematicslab.mobile.ui.training

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScientificEvidenceRouteTest {
    @get:Rule val rule=createComposeRule()
    @Test fun numberedResearchStepsCallTheirRealDestinationWithoutClaimingCompletion() {
        var selected:TrainingLabMode?=null;var coverage=false
        rule.setContent { MaterialTheme { ScientificEvidencePanel(onOpenExperiment={ selected=it },onOpenDatasetQuality={coverage=true}) } }
        rule.onNodeWithText("Recommended experimental sequence").performScrollTo().performClick()
        rule.waitUntil(5000) {
            rule.onAllNodesWithText("Open experiment · 1 · Frozen growth").fetchSemanticsNodes().size == 1
        }
        listOf("1 · Frozen growth","2 · Candidate growth","3 · Family ablation","4 · Robot-held-out generalization").forEach { step ->
            rule.onNodeWithText("Open experiment · $step").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(5_000) { selected == TrainingLabMode.CONTROLLED_SINGLE_RUN }
            rule.runOnIdle { selected=null }
        }
        rule.onNodeWithText("Open experiment · 5 · Reliability").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { selected == TrainingLabMode.RESULT_COMPARISON }
        rule.onNodeWithText("Open experiment · Coverage / OOD").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { coverage }
        rule.onNodeWithText("Open experiment · 7 · Explanation").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { selected == TrainingLabMode.EXPLAINABILITY }
    }
}
