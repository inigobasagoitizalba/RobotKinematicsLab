package com.robotkinematicslab.mobile.ui.training.results

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.ml.storage.*
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.training.*
import java.io.File
import org.junit.Rule
import org.junit.Test

class HistoricalResultUiTest {
    @get:Rule val rule=createComposeRule()
    private fun metrics()=ClassificationMetrics(2,1.0,1.0,1.0,.1,listOf(listOf(1,0,0),listOf(0,0,0),listOf(0,0,1)),classSupport=listOf(1,0,1))
    @Test fun exactArchivedProfileReopensAndReturnsToItsHistoryCard() {
        val directory=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"history-ui-${System.nanoTime()}").apply { mkdirs() }
        val summary=File(directory,"summary.properties").apply { writeText("original=true") }
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(summary.readBytes()).joinToString("") { "%02x".format(it) }
        TrainingResultEvidenceArchive.saveSnapshot(File(directory,TrainingResultEvidenceArchive.FILE_NAME),HistoricalRunEvidence("fixture-old","/original.csv",sha,listOf(HistoricalProfileEvidence("old-contract","Original contract","candidate-old",listOf("z","x"),7,metrics(),emptyList(),emptyList()))))
        val run=TrainingRunSummary(runId="fixture-old",runName="Original saved experiment",datasetPath="/original.csv",startedAtEpochMillis=1,finishedAtEpochMillis=2,baselineTestMacroF1=1.0,contextTestMacroF1=null,macroF1Delta=Double.NaN,randomSeed=42,splitStrategy=TrainingSplitStrategy.SAMPLE_GROUPED,maximumRows=2,directoryPath=directory.path,historyCsvPath=File(directory,"absent.csv").path,modelPaths=emptyList())
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HistoricalTrainingRunCard(run) } } }
        rule
            .onNodeWithTag("training-disclosure-original-saved-experiment")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("historical-open-fixture-old").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("historical-open-fixture-old").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("historical-profile-old-contract").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("historical-profile-old-contract").assertTextContains("candidate-old", substring = true)
        rule.onNodeWithTag("historical-return").performClick()
        rule.onNodeWithTag("historical-open-fixture-old").assertExists()
        directory.deleteRecursively()
    }
    @Test fun namedZeroSupportIsNotReportedAsZeroRecallSuccess() {
        rule.setContent { MaterialTheme { NamedClassSupport(metrics()) } }
        rule.onNodeWithText("Actual Uncertain").assertExists()
        rule.onNodeWithText("0 · no support").assertExists()
        rule.onNodeWithText("Actual Accepted").assertExists()
        rule.onNodeWithText("Actual Rejected").assertExists()
    }
}
