package com.robotkinematicslab.mobile.ui.charts.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

const val CHART_PRESENTATION_DIALOG_TAG = "chart-presentation-dialog"

@Composable
fun ChartPresentationSettingsContent(
    preferences: ChartPresentationPreferences,
    onPreferencesChange: (ChartPresentationPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Display preset", style = MaterialTheme.typography.titleSmall)

        PresetChoice(
            selected = preferences.preset == ChartPresentationPreset.EXPLORATION,
            preset = ChartPresentationPreset.EXPLORATION,
            onClick = { onPreferencesChange(preferences.withPreset(ChartPresentationPreset.EXPLORATION)) }
        )
        PresetChoice(
            selected = preferences.preset == ChartPresentationPreset.PUBLICATION,
            preset = ChartPresentationPreset.PUBLICATION,
            onClick = { onPreferencesChange(preferences.withPreset(ChartPresentationPreset.PUBLICATION)) }
        )

        Text("Scientific figure archive", style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        ) {
            Text(
                "Every completed 2D chart is archived automatically as an immutable PNG with a run id and SHA-256 index. Interactive 3D views are intentionally excluded.",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text("Visible layers", style = MaterialTheme.typography.titleSmall)
        ChartLayerCheck(
            label = "Gridlines",
            detail = "Reference grid behind Cartesian plots.",
            checked = preferences.showGrid,
            onCheckedChange = { onPreferencesChange(preferences.customized(showGrid = it)) }
        )
        ChartLayerCheck(
            label = "Connecting lines",
            detail = "Joins ordered observations; scatter plots remain unconnected.",
            checked = preferences.showLines,
            onCheckedChange = { onPreferencesChange(preferences.customized(showLines = it)) }
        )
        ChartLayerCheck(
            label = "Data markers",
            detail = "Shows observed points on line and telemetry plots.",
            checked = preferences.showMarkers,
            onCheckedChange = { onPreferencesChange(preferences.customized(showMarkers = it)) }
        )
        ChartLayerCheck(
            label = "Value labels",
            detail = "Adds values where the plot has enough room; exact values remain in the inspector.",
            checked = preferences.showValueLabels,
            onCheckedChange = { onPreferencesChange(preferences.customized(showValueLabels = it)) }
        )
        ChartLayerCheck(
            label = "Legend",
            detail = "Keeps series and category keys visible.",
            checked = preferences.showLegend,
            onCheckedChange = { onPreferencesChange(preferences.customized(showLegend = it)) }
        )
        ChartLayerCheck(
            label = "Supporting statistics",
            detail = "Shows ranges, counts and summary rows below the figure.",
            checked = preferences.showStatistics,
            onCheckedChange = { onPreferencesChange(preferences.customized(showStatistics = it)) }
        )
        ChartLayerCheck(
            label = "Reading guidance",
            detail = "Shows the research question and interpretation guidance above each figure.",
            checked = preferences.showGuidance,
            onCheckedChange = { onPreferencesChange(preferences.customized(showGuidance = it)) }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                "Axis labels, units and accessible descriptions remain enabled in every preset because removing them would weaken the scientific meaning of the figure.",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChartPresentationDialog(
    visible: Boolean,
    preferences: ChartPresentationPreferences,
    onPreferencesChange: (ChartPresentationPreferences) -> Unit,
    exportStatus: String? = null,
    exportInProgress: Boolean = false,
    onExportPng: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        modifier = Modifier.testTag(CHART_PRESENTATION_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = { Text("Chart appearance & save") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .verticalScroll(rememberScrollState())
                ) {
                    ChartPresentationSettingsContent(
                        preferences = preferences,
                        onPreferencesChange = onPreferencesChange
                    )
                }
                if (!exportStatus.isNullOrBlank()) {
                    Text(
                        exportStatus,
                        modifier = Modifier.testTag("chart-export-status"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        dismissButton = {
            if (onExportPng != null) {
                TextButton(
                    onClick = onExportPng,
                    enabled = !exportInProgress,
                    modifier = Modifier.testTag("chart-export-png")
                ) {
                    Text(if (exportInProgress) "Saving…" else "Save PNG")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PresetChoice(
    selected: Boolean,
    preset: ChartPresentationPreset,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag("chart-preset:${preset.persistedId}")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(preset.displayName)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag("chart-preset:${preset.persistedId}")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(preset.displayName)
                Text(preset.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChartLayerCheck(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("chart-layer:$label")
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange
                ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
