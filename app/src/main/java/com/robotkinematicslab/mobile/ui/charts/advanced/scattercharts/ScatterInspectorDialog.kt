package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_PLOT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorSelectionCard
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorZoomControls
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartQuickGuide
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun ScatterInspectorDialog(
    title: String,
    subtitle: String,
    points: List<ChartPoint>,
    stats: ScatterStats,
    xAxisLabel: String,
    yAxisLabel: String,
    legendItems: List<ScatterLegendItem>,
    guide: ChartGuide,
    onDismiss: () -> Unit
) {
    val presentation = LocalChartPresentationController.current.preferences
    var scale by remember(title, points, xAxisLabel, yAxisLabel) {
        mutableFloatStateOf(1f)
    }

    var offsetX by remember(title, points, xAxisLabel, yAxisLabel) {
        mutableFloatStateOf(0f)
    }

    var offsetY by remember(title, points, xAxisLabel, yAxisLabel) {
        mutableFloatStateOf(0f)
    }

    var showPointLabels by remember(title, points, xAxisLabel, yAxisLabel, presentation.showValueLabels) {
        mutableStateOf(presentation.showValueLabels)
    }

    var selectedPoint by remember(title, points, xAxisLabel, yAxisLabel) {
        mutableStateOf<ScatterSelection?>(null)
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
                            text = cleanScatterTitle(title),
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
                            text = "Point labels",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF202124)
                        )

                        Text(
                            text = "Show point labels when zoomed enough.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = showPointLabels,
                        onCheckedChange = { enabled ->
                            showPointLabels = enabled

                            if (enabled && scale < 2.5f) {
                                scale = 2.5f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    )
                }

                ChartInspectorZoomControls(
                    scale = scale,
                    maximumScale = 24f,
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
                        selectedPoint = null
                    },
                    zoomText =
                        if (scale >= 2.5f) {
                            "Zoom ${formatScatterDouble(scale.toDouble(), digits = 2)}× · pan or tap a dot"
                        } else {
                            "Zoom ${formatScatterDouble(scale.toDouble(), digits = 2)}× · pinch, pan, or tap a dot"
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
                                        maximumValue = 24f
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
                    ScatterCanvas(
                        points = points,
                        stats = stats,
                        xAxisLabel = xAxisLabel,
                        yAxisLabel = yAxisLabel,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        showAxisLabels = true,
                        showPointLabels = showPointLabels,
                        onPointSelected = { selectedPoint = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedPoint?.let { selection ->
                        ChartInspectorSelectionCard(
                            title = selection.point.label.ifBlank { "Point ${selection.index + 1}" },
                            values =
                                listOf(
                                    xAxisLabel to selection.point.x.toString(),
                                    yAxisLabel to selection.point.y.toString(),
                                    "Rendered index" to (selection.index + 1).toString()
                                ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }

                selectedPoint?.let { current ->
                    val coincident = points.withIndex().filter { it.value.x == current.point.x && it.value.y == current.point.y }
                    if(coincident.size > 1) TextButton(onClick = {
                        val next = coincident[(coincident.indexOfFirst { it.index == current.index } + 1) % coincident.size]
                        selectedPoint = ScatterSelection(next.value, next.index)
                    }) { Text("${coincident.size} coincident observations · Next observation") }
                }

                if (presentation.showLegend) {
                    ScatterInlineLegend(
                        points = points,
                        items = legendItems
                    )
                }
            }
        }
    }
}
