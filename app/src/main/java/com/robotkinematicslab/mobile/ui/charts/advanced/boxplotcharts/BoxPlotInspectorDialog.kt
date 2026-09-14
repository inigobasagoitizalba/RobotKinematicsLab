package com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorZoomControls
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_PLOT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorSelectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartQuickGuide

@Composable
internal fun BoxPlotInspectorDialog(
    title: String,
    subtitle: String,
    items: List<BoxPlotPreparedItem>,
    globalStats: BoxPlotGlobalStats,
    xAxisLabel: String,
    yAxisLabel: String,
    guide: ChartGuide,
    onDismiss: () -> Unit
) {
    val presentation = LocalChartPresentationController.current.preferences
    var scale by remember {
        mutableFloatStateOf(1f)
    }

    var offsetY by remember {
        mutableFloatStateOf(0f)
    }

    var showStatLabels by remember(presentation.showValueLabels) {
        mutableStateOf(presentation.showValueLabels)
    }

    var selectedBox by remember {
        mutableStateOf<BoxPlotSelection?>(null)
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
            color = Color(0xFFFAFAFA)
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
                            text = cleanBoxPlotTitle(title),
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
                            text = "Stat labels",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF202124)
                        )

                        Text(
                            text = "Show median labels when rows are large enough.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = showStatLabels,
                        onCheckedChange = { enabled ->
                            showStatLabels = enabled

                            if (enabled && scale < 1.6f) {
                                scale = 1.6f
                                offsetY = 0f
                            }
                        }
                    )
                }

                ChartInspectorZoomControls(
                    scale = scale,
                    maximumScale = 10f,
                    onScaleChange = { nextScale ->
                        scale = nextScale
                        if (nextScale <= 1.001f) offsetY = 0f
                    },
                    onFit = {
                        scale = 1f
                        offsetY = 0f
                        selectedBox = null
                    },
                    zoomText =
                        if (scale >= 1.6f) {
                            "Zoom ${formatBoxPlotDouble(scale.toDouble(), digits = 2)}× · pan or tap a box"
                        } else {
                            "Zoom ${formatBoxPlotDouble(scale.toDouble(), digits = 2)}× · pinch, pan, or tap a box"
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(CHART_INSPECTOR_PLOT_TAG)
                        .background(
                            color = Color(0xFFF0F2F5),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale =
                                    (scale * zoom).coerceIn(
                                        minimumValue = 1f,
                                        maximumValue = 10f
                                    )

                                scale = newScale

                                if (newScale <= 1.001f) {
                                    offsetY = 0f
                                } else {
                                    offsetY += pan.y
                                }
                            }
                        }
                ) {
                    BoxPlotCanvas(
                        items = items,
                        globalStats = globalStats,
                        xAxisLabel = xAxisLabel,
                        yAxisLabel = yAxisLabel,
                        scale = scale,
                        offsetY = offsetY,
                        showAxisLabels = true,
                        showStatLabels = showStatLabels,
                        onBoxSelected = { selectedBox = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedBox?.let { selection ->
                        ChartInspectorSelectionCard(
                            title = selection.item.label,
                            values =
                                listOf(
                                    "n" to selection.item.stats.sampleCount.toString(),
                                    "Min / Q1" to
                                        "${formatBoxPlotDouble(selection.item.stats.min)} / ${formatBoxPlotDouble(selection.item.stats.q1)}",
                                    "Median / mean" to
                                        "${formatBoxPlotDouble(selection.item.stats.median)} / ${formatBoxPlotDouble(selection.item.stats.mean)}",
                                    "Q3 / max" to
                                        "${formatBoxPlotDouble(selection.item.stats.q3)} / ${formatBoxPlotDouble(selection.item.stats.max)}"
                                ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }

                BoxPlotInspectorSummary(
                    globalStats = globalStats
                )
            }
        }
    }
}

@Composable
private fun BoxPlotInspectorSummary(
    globalStats: BoxPlotGlobalStats
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxPlotSummaryPill(
            label = "Items",
            value = globalStats.itemCount.toString(),
            modifier = Modifier.weight(1f)
        )

        BoxPlotSummaryPill(
            label = "Samples",
            value = globalStats.sampleCount.toString(),
            modifier = Modifier.weight(1f)
        )

        BoxPlotSummaryPill(
            label = "Range",
            value = "${formatBoxPlotDouble(globalStats.min)} → ${formatBoxPlotDouble(globalStats.max)}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BoxPlotSummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = Color.White,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(
                horizontal = 8.dp,
                vertical = 5.dp
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF5F6368),
            maxLines = 3
        )

        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF202124),
            maxLines = 2
        )
    }
}
