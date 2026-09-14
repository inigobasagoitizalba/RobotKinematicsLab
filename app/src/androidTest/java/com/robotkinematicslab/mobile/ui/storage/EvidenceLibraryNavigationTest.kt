package com.robotkinematicslab.mobile.ui.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.robotkinematicslab.mobile.storage.evidence.*
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import org.junit.Rule
import org.junit.Test

class EvidenceLibraryNavigationTest {
    @get:Rule val rule=createComposeRule()
    @Test fun search167OpenReturnRestoreAndProjectChangeKeepExactIdentity() {
        var project by mutableStateOf("one")
        var opened by mutableStateOf<ProjectArtifact?>(null)
        val restoration=StateRestorationTester(rule)
        restoration.setContent { MaterialTheme {
            val artifacts=List(if(project=="one") 167 else 1) { ProjectArtifact("figures/$it.png","figures",ProjectArtifactFormat.PNG,10,1700000000000,"a".repeat(64),displayTitle="Plot $it") }
            Column(Modifier.verticalScroll(rememberScrollState())) { ProjectEvidencePackContent(ProjectEvidenceSnapshot(ResearchProject(project,"Project $project","",1,1,false),"/project/$project",1,artifacts),false,"Indexed",{},{},{opened=it}) }
            opened?.let { ProjectArtifactPreviewDialog(ProjectArtifactPreview(it,emptyList(),false,null,explanation="Exact selected artifact"),{opened=null}) }
        } }
        rule.onNodeWithTag("evidence-library-search").performScrollTo().performTextReplacement("Plot 166")
        rule.onNodeWithTag("evidence-library-count").assertTextContains("Dates use UTC. 1 matching artifacts / 167 indexed.")
        rule.onNodeWithTag("EvidenceFile:figures/166.png").performScrollTo().performClick()
        rule.onNodeWithText("Exact selected artifact").assertExists()
        rule.onNodeWithText("Close").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("evidence-library-search").assertTextContains("Plot 166")
        rule.runOnIdle { project="two" }
        rule.onNodeWithTag("evidence-library-count").assertTextContains("Dates use UTC. 1 matching artifacts / 1 indexed.")
        rule.onNodeWithTag("EvidenceFile:figures/166.png").assertDoesNotExist()
    }
}
