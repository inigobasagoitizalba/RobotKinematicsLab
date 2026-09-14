package com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalBoxPlotChart(
    title: String,
    subtitle: String,
    items: List<ChartBoxPlotItem>,
    xAxisLabel: String,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val preparedItems =
        remember(items) {
            prepareBoxPlotItems(items)
        }

    val globalStats =
        remember(preparedItems) {
            computeBoxPlotGlobalStats(preparedItems)
        }

    val guide =
        remember(title, subtitle, xAxisLabel, yAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BOX_PLOT,
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
        automaticExportKey = preparedItems.takeIf { it.isNotEmpty() }?.hashCode()
    ) {
        if (preparedItems.isEmpty() || globalStats == null) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    boxPlotPreviewHeight(
                        itemCount = preparedItems.size
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
            BoxPlotCanvas(
                items = preparedItems,
                globalStats = globalStats,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                scale = 1f,
                offsetY = 0f,
                showAxisLabels = true,
                showStatLabels = false,
                modifier = Modifier.fillMaxSize()
            )

            if (!publicationMode && !com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress.current) Text(
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

        if (presentation.showStatistics) {
            ChartAxisInfoRow(xAxisLabel = xAxisLabel, yAxisLabel = yAxisLabel)
            ChartMetricRow(label = "Items", value = globalStats.itemCount.toString())
            ChartMetricRow(label = "Samples", value = globalStats.sampleCount.toString())
            ChartMetricRow(label = "Global min", value = formatBoxPlotDouble(globalStats.min))
            ChartMetricRow(label = "Global max", value = formatBoxPlotDouble(globalStats.max))
        }

        if (presentation.showLegend) {
            preparedItems.forEach { item ->
                ChartLegendMetricRow(
                    label = item.label,
                    value = "median=${formatBoxPlotDouble(item.stats.median)} · n=${item.stats.sampleCount}",
                    color = item.color
                )
            }
        }
    }

    if (inspectorOpen && globalStats != null) {
        BoxPlotInspectorDialog(
            title = title,
            subtitle = subtitle,
            items = preparedItems,
            globalStats = globalStats,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
