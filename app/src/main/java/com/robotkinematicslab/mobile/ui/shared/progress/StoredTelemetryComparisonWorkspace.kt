package com.robotkinematicslab.mobile.ui.shared.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun StoredTelemetryComparisonWorkspace() {
    val context = LocalContext.current.applicationContext
    val catalog = remember(context) { ProjectTelemetryCatalog(context) }
    var sessions by remember(catalog) { mutableStateOf(catalog.listSessions()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectedSessions = sessions.filter { it.selectionKey in selectedIds }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Project chart library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Every entry remains inside its original project. Select up to three compatible sessions; matching curves are placed on the same unit-safe axes without changing their source evidence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    sessions = catalog.listSessions()
                    selectedIds = selectedIds.intersect(sessions.map { it.selectionKey }.toSet())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh all project charts")
            }

            if (sessions.isEmpty()) {
                Text("No completed chart session has been saved in any project yet.")
            } else {
                sessions.groupBy { it.project }.forEach { (project, projectSessions) ->
                    Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${projectSessions.size} saved chart session(s) · ${if (project.usesLegacyWorkspace) "original workspace" else "isolated workspace"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    projectSessions.take(MAX_VISIBLE_SESSIONS_PER_PROJECT).forEach { entry ->
                        val session = entry.session
                        val selected = entry.selectionKey in selectedIds
                        Surface(
                            onClick = {
                                selectedIds =
                                    if (selected) {
                                        selectedIds - entry.selectionKey
                                    } else {
                                        (selectedIds + entry.selectionKey).toList().takeLast(MAX_STORED_COMPARISONS).toSet()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    if (selected) "●" else "○",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 3
                                    )
                                    Text(
                                        "${session.sessionType} · ${formatStoredSessionDate(session.createdAtEpochMillis)} · ${session.sampleCount} samples",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedSessions.isNotEmpty()) {
                StoredTelemetryOverlayDashboard(selectedSessions)
            }
        }
    }
}

@Composable
private fun StoredTelemetryOverlayDashboard(
    sessions: List<ProjectTelemetrySession>
) {
    var selectedCategory by remember { mutableStateOf(TelemetryDashboardCategory.FLOW) }
    val groups =
        remember(sessions, selectedCategory) {
            mergeStoredGroups(sessions, selectedCategory)
        }
    var selectedGroupId by remember(selectedCategory, groups.map { it.id }) {
        mutableStateOf(groups.firstOrNull()?.id)
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "Comparison workspace",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        TelemetryCategorySelector(
            selected = selectedCategory,
            onSelected = { selectedCategory = it }
        )
        TelemetryChartSelector(
            groups = groups,
            selectedId = selectedGroupId,
            onSelected = { selectedGroupId = it }
        )
        groups.firstOrNull { it.id == selectedGroupId }?.let { group ->
            InteractiveTelemetryChart(group)
        }
    }
}

private fun mergeStoredGroups(
    sessions: List<ProjectTelemetrySession>,
    category: TelemetryDashboardCategory
): List<TelemetryChartGroup> {
    val templates = sessions.flatMap { it.session.chartGroups[category].orEmpty() }.distinctBy { it.id }
    return templates.map { template ->
        val series =
            sessions.flatMapIndexed { sessionIndex, entry ->
                entry.session.chartGroups[category].orEmpty()
                    .firstOrNull { it.id == template.id }
                    ?.series
                    .orEmpty()
                    .map { source ->
                        source.copy(
                            id = "${entry.selectionKey}_${source.id}",
                            label = "S${sessionIndex + 1} ${entry.project.name} · ${source.label}"
                        )
                    }
            }
        template.copy(
            subtitle = "${sessions.size} saved session(s) aligned by elapsed time. ${template.subtitle}",
            series = series
        )
    }
}

private fun formatStoredSessionDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private const val MAX_STORED_COMPARISONS = 3
private const val MAX_VISIBLE_SESSIONS_PER_PROJECT = 12
