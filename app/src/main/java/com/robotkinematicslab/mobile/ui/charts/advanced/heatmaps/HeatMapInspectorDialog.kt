package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_PLOT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorSelectionCard
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorZoomControls
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartQuickGuide
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun HeatMapInspectorDialog(
    title: String,
    subtitle: String,
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String,
    showCellNames: Boolean,
    legendItems: List<ChartHeatMapLegendItem>,
    guide: ChartGuide,
    onDismiss: () -> Unit
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var scale by remember(title, cells, rowLabels, columnLabels) {
        mutableFloatStateOf(1f)
    }

    var offsetX by remember(title, cells, rowLabels, columnLabels) {
        mutableFloatStateOf(0f)
    }

    var offsetY by remember(title, cells, rowLabels, columnLabels) {
        mutableFloatStateOf(0f)
    }

    var inspectorShowCellNames by remember(title, cells, rowLabels, columnLabels) {
        mutableStateOf(showCellNames)
    }

    var selectedCell by remember(title, cells, rowLabels, columnLabels) {
        mutableStateOf<HeatMapSelection?>(null)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = if (publicationMode) Color.White else Color(0xFFFAFAFA)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = cleanHeatMapTitle(title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF202124),
                            maxLines = 2
                        )

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                            maxLines = 3
                        )
                    }

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Close")
                    }
                }

                ChartQuickGuide(
                    guide = guide,
                    expanded = false
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 6.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Cell names",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF202124)
                        )

                        Text(
                            text = "Show row × column labels inside visible cells.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = inspectorShowCellNames,
                        onCheckedChange = { enabled ->
                            inspectorShowCellNames = enabled

                            if (enabled && scale < 6.0f) {
                                scale = 6.0f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    )
                }

                ChartInspectorZoomControls(
                    scale = scale,
                    maximumScale = 18f,
                    onScaleChange = { nextScale ->
                        scale = nextScale
                        if (nextScale <= 1.001f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onFit = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        selectedCell = null
                    },
                    zoomText =
                        if (scale >= 2.4f) {
                            "Zoom ${formatHeatMapDouble(scale.toDouble(), digits = 2)}× · pan or tap a cell"
                        } else {
                            "Zoom ${formatHeatMapDouble(scale.toDouble(), digits = 2)}× · pinch, pan, or tap a cell"
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(CHART_INSPECTOR_PLOT_TAG)
                        .background(
                            color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
                            shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val oldScale =
                                    scale

                                val newScale =
                                    (oldScale * zoom).coerceIn(
                                        minimumValue = 1f,
                                        maximumValue = 18f
                                    )

                                scale = newScale

                                if (newScale <= 1.001f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        }
                ) {
                    StickyHeatMapCanvas(
                        rowLabels = rowLabels,
                        columnLabels = columnLabels,
                        cells = cells,
                        xAxisLabel = xAxisLabel,
                        yAxisLabel = yAxisLabel,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        showCellValues =
                            presentation.showValueLabels ||
                                (scale >= 2.4f && !inspectorShowCellNames),
                        showCellNames = inspectorShowCellNames,
                        onCellSelected = { selectedCell = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedCell?.let { selection ->
                        ChartInspectorSelectionCard(
                            title = "Selected cell",
                            values =
                                listOf(
                                    "Row" to selection.row,
                                    "Column" to selection.column,
                                    "Value" to (selection.cell?.displayValue ?: "No data"),
                                    "Raw" to
                                            selection.cell?.value?.let {
                                                formatHeatMapDouble(it, digits = 6)
                                            }.orEmpty().ifBlank { "NA" }
                                ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }

                if (presentation.showLegend) {
                    HeatMapInlineLegend(
                        cells = cells,
                        legendItems = legendItems
                    )
                }
            }
        }
    }
}
