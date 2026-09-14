package com.robotkinematicslab.mobile.ui.charts.advanced.linecharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.CHART_INSPECTOR_PLOT_TAG
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorSelectionCard
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartInspectorZoomControls
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartQuickGuide
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun LineChartInspectorDialog(
    title: String,
    subtitle: String,
    points: List<ChartLinePoint>,
    stats: LineChartStats,
    xAxisLabel: String,
    yAxisLabel: String,
    color: Color,
    guide: ChartGuide,
    onDismiss: () -> Unit,
    referenceDiagonal: Boolean = false
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
        mutableStateOf<LineChartSelection?>(null)
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
                            text = cleanLineChartTitle(title),
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
                            text = "Show labels when zoomed into the line.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6368),
                            maxLines = 2
                        )
                    }

                    Switch(
                        checked = showPointLabels,
                        onCheckedChange = { enabled ->
                            showPointLabels = enabled

                            if (enabled && scale < 2.0f) {
                                scale = 2.0f
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
                        if (scale >= 2.0f) {
                            "Zoom ${formatLineChartDouble(scale.toDouble(), digits = 2)}× · pan or tap a point"
                        } else {
                            "Zoom ${formatLineChartDouble(scale.toDouble(), digits = 2)}× · pinch, pan, or tap a point"
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
                    LineChartCanvas(
                        referenceDiagonal = referenceDiagonal,
                        points = points,
                        stats = stats,
                        xAxisLabel = xAxisLabel,
                        yAxisLabel = yAxisLabel,
                        color = color,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        showAxisLabels = true,
                        showPointLabels = showPointLabels,
                        showGridLines = presentation.showGrid,
                        showLines = presentation.showLines,
                        showMarkers = presentation.showMarkers,
                        publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION,
                        onPointSelected = { selectedPoint = it },
                        modifier = Modifier.fillMaxSize()
                    )

                    selectedPoint?.let { selection ->
                        ChartInspectorSelectionCard(
                            title = selection.point.label.ifBlank { "Point ${selection.index + 1}" },
                            values =
                                listOf(
                                    xAxisLabel to formatLineChartDouble(selection.point.x, digits = 6),
                                    yAxisLabel to formatLineChartDouble(selection.point.y, digits = 6)
                                ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }

                LineChartInspectorSummary(
                    stats = stats,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun LineChartInspectorSummary(
    stats: LineChartStats,
    color: Color
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
        LineChartSummaryPill(
            label = "Points",
            value = stats.pointCount.toString(),
            color = color,
            modifier = Modifier.weight(1f)
        )

        LineChartSummaryPill(
            label = "ΔY",
            value = formatLineChartDouble(stats.deltaY),
            color = color,
            modifier = Modifier.weight(1f)
        )

        LineChartSummaryPill(
            label = "Y range",
            value = "${formatLineChartDouble(stats.minY)} → ${formatLineChartDouble(stats.maxY)}",
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LineChartSummaryPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color.White,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = color,
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(5.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
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
}
