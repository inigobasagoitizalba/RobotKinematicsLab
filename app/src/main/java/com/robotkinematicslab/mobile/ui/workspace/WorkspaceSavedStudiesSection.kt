package com.robotkinematicslab.mobile.ui.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureHeader
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import kotlinx.coroutines.launch

/** Scope lives alongside expansion, so closing/reopening never resets newest/all. The route's
 * project-keyed SaveableStateProvider isolates both settings between projects. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceSavedStudiesSection(
    summaries: List<RobotWorkspaceStudySummary>,
    selectedStudyId: String?,
    onLoad: (RobotWorkspaceStudySummary) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val requester = remember { BringIntoViewRequester() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppDisclosureHeader(
            title = "Saved workspace studies",
            summary = if (summaries.isEmpty()) "No saved studies" else "${summaries.size} saved studies",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.bringIntoViewRequester(requester).focusRequester(focus).focusable()
                .testTag("workspace-saved-studies").tutorialAnchor(TutorialTargets.WorkspaceSavedStudies)
        )
        if (expanded) {
            JargonAwareText("Samples are evaluated joint states; n³ is the Cartesian grid before filtering. Observed % = occupied cell volume / all classified envelope cell volume. This includes a conservative margin of cells around the radial bound; it is not the fraction of all physically reachable positions. Unobserved cells are candidates, not proof of impossibility.", style = MaterialTheme.typography.bodySmall)
            if (summaries.isEmpty()) Text("No workspace studies have been saved yet.")
            else ExpandableSelectionCollection(
                items = summaries,
                initialVisibleCount = 8,
                itemName = "workspace studies",
                isSelected = { it.studyId == selectedStudyId },
                toggleTestTag = "workspace-studies-show-all",
                showAllOverride = showAll,
                onShowAllChange = { showAll = it },
                itemKey = { it.studyId },
                collapseLabel = "Collapse saved workspace studies",
                onCollapseParent = {
                    expanded = false
                    scope.launch {
                        withFrameNanos { }
                        focus.requestFocus()
                        requester.bringIntoView()
                    }
                }
            ) { summary -> SavedStudyRow(summary) { onLoad(summary) } }
        }
    }
}
