package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.advanced.selectPeakPreservingChartPreview
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalScatterChart(
    title: String,
    subtitle: String,
    points: List<ChartPoint>,
    xAxisLabel: String,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
    legendItems: List<ScatterLegendItem> = emptyList(),
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val cleanPoints =
        remember(points) {
            cleanScatterPoints(points)
        }

    val stats =
        remember(cleanPoints) {
            computeScatterStats(cleanPoints)
        }

    val displayPoints =
        remember(cleanPoints) {
            selectPeakPreservingChartPreview(cleanPoints) { it.y }
        }
    val guide =
        remember(title, subtitle, xAxisLabel, yAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.SCATTER,
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
        automaticExportKey = cleanPoints.takeIf { it.isNotEmpty() }?.hashCode()
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
                .height(scatterPreviewHeight(cleanPoints.size).dp)
                .background(
                    color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
                    shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                )
                .clickable {
                    inspectorOpen = true
                }
        ) {
            ScatterCanvas(
                points = renderedPoints,
                stats = stats,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
                showAxisLabels = true,
                showPointLabels = false,
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
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF202124)
                )
            }
        }

        if (presentation.showStatistics) {
            ChartAxisInfoRow(xAxisLabel = xAxisLabel, yAxisLabel = yAxisLabel)
            ChartMetricRow(
                label = "X range",
                value = "${formatScatterDouble(stats.observedMinX)} → ${formatScatterDouble(stats.observedMaxX)}"
            )
            ChartMetricRow(
                label = "Y range",
                value = "${formatScatterDouble(stats.observedMinY)} → ${formatScatterDouble(stats.observedMaxY)}"
            )
            ChartMetricRow(label = "Points", value = stats.pointCount.toString())
        }

        if (presentation.showStatistics && !figureCaptureInProgress && displayPoints.size < cleanPoints.size) {
            ChartMetricRow(
                label = "Points drawn",
                value = "${displayPoints.size} deterministic samples"
            )
        }

        if (presentation.showStatistics) {
            ChartMetricRow(
                label = "Correlation coefficient",
                value = formatScatterCorrelation(stats.correlation)
            )
        }

        if (presentation.showLegend) {
            ScatterLegend(
                points = renderedPoints,
                items = legendItems,
                correlation = stats.correlation
            )
        }
    }

    if (inspectorOpen && stats != null) {
        ScatterInspectorDialog(
            title = title,
            subtitle = subtitle,
            points = cleanPoints,
            stats = stats,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            legendItems = legendItems,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
