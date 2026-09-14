package com.robotkinematicslab.mobile.ui.charts.advanced.linecharts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.advanced.selectPeakPreservingChartPreview
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalLineChart(
    title: String,
    subtitle: String,
    points: List<ChartLinePoint>,
    xAxisLabel: String,
    yAxisLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null,
    referenceDiagonal: Boolean = false
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val cleanPoints =
        remember(points) {
            cleanLineChartPoints(points)
        }

    val stats =
        remember(cleanPoints, referenceDiagonal) {
            computeLineChartStats(cleanPoints)?.let { if(referenceDiagonal) it.copy(minX=0.0,maxX=1.0,minY=0.0,maxY=1.0,xRange=1.0,yRange=1.0) else it }
        }

    val displayPoints =
        remember(cleanPoints) {
            selectPeakPreservingChartPreview(cleanPoints) { it.y }
        }
    val guide =
        remember(title, subtitle, xAxisLabel, yAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = title,
                subtitle = subtitle,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                supportsDataInspector = true,
                directionOverride = directionOverride
            )
        }

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        guide = guide,
        automaticExportKey = cleanPoints.takeIf { it.isNotEmpty() }?.let { listOf(it,referenceDiagonal).hashCode() }
    ) {
        val figureCaptureInProgress = LocalChartFigureCaptureInProgress.current
        val renderedPoints = if (figureCaptureInProgress) cleanPoints else displayPoints
        if (cleanPoints.isEmpty() || stats == null) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    lineChartPreviewHeight(
                        pointCount = cleanPoints.size
                    ).dp
                )
                .background(
                    color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
                    shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                )
                .clickable {
                    inspectorOpen = true
                }
        ) {
            LineChartCanvas(
                referenceDiagonal = referenceDiagonal,
                points = renderedPoints,
                stats = stats,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                color = color,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                showAxisLabels = true,
                showPointLabels = presentation.showValueLabels && renderedPoints.size <= 24,
                showGridLines = presentation.showGrid,
                showLines = presentation.showLines,
                showMarkers = presentation.showMarkers,
                publicationMode = publicationMode,
                modifier = Modifier.fillMaxSize()
            )

            if (!publicationMode && !com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress.current) {
                Text(
                    text = "Open chart inspector",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF202124)
                )
            }
        }

        if (presentation.showStatistics) {
            ChartAxisInfoRow(
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel
            )
        }

        if (presentation.showLegend) {
            ChartLegendMetricRow(
                label = "Line",
                value = "${stats.pointCount} points",
                color = color
            )
        }

        if (presentation.showStatistics && !figureCaptureInProgress && displayPoints.size < cleanPoints.size) {
            ChartMetricRow(
                label = "Points drawn",
                value = "${displayPoints.size} deterministic samples"
            )
        }

        if (presentation.showStatistics) {
            ChartMetricRow(
                label = "X range",
                value = "${formatLineChartDouble(stats.minX)} → ${formatLineChartDouble(stats.maxX)}"
            )

            ChartMetricRow(
                label = "Y range",
                value = "${formatLineChartDouble(stats.minY)} → ${formatLineChartDouble(stats.maxY)}"
            )

            ChartMetricRow(
                label = "$yAxisLabel at first $xAxisLabel",
                value = formatLineChartDouble(stats.firstY)
            )

            ChartMetricRow(
                label = "$yAxisLabel at last $xAxisLabel",
                value = formatLineChartDouble(stats.lastY)
            )

            ChartMetricRow(
                label = "Absolute change in $yAxisLabel",
                value = formatLineChartDouble(stats.deltaY)
            )
        }
    }

    if (inspectorOpen && stats != null) {
        LineChartInspectorDialog(
            referenceDiagonal = referenceDiagonal,
            title = title,
            subtitle = subtitle,
            points = cleanPoints,
            stats = stats,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            color = color,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
