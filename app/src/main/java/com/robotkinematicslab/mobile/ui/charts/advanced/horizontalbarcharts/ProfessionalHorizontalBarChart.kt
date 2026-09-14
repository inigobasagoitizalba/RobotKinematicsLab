package com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalHorizontalBarChart(
    title: String,
    subtitle: String,
    items: List<ChartBarItem>,
    xAxisLabel: String,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null,
    metricFormat: com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.COUNT
) {
    androidx.compose.runtime.CompositionLocalProvider(com.robotkinematicslab.mobile.ui.charts.presentation.LocalScientificMetricFormat provides metricFormat) {
        HorizontalBarChartContent(title, subtitle, items, xAxisLabel, modifier, directionOverride)
    }
}

@Composable
private fun HorizontalBarChartContent(
    title: String,
    subtitle: String,
    items: List<ChartBarItem>,
    xAxisLabel: String,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.LocalScientificMetricFormat.current
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val preparedItems =
        remember(items) {
            prepareHorizontalBarItems(items)
        }

    val stats =
        remember(preparedItems) {
            computeHorizontalBarStats(preparedItems)
        }

    val guide =
        remember(title, subtitle, xAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = title,
                subtitle = subtitle,
                xAxisLabel = xAxisLabel,
                yAxisLabel = "Categories",
                supportsDataInspector = true,
                directionOverride = directionOverride
            )
        }

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        guide = guide,
        automaticExportKey = preparedItems.takeIf { it.isNotEmpty() }?.let { listOf(it, metricFormat) }
    ) {
        if (preparedItems.isEmpty() || stats == null) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    horizontalBarPreviewHeight(
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
            HorizontalBarCanvas(
                items = preparedItems,
                stats = stats,
                xAxisLabel = xAxisLabel,
                scale = 1f,
                offsetY = 0f,
                showAxisLabels = true,
                showValueLabels = false,
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
            ChartAxisInfoRow(xAxisLabel = xAxisLabel, yAxisLabel = "Categories")
            ChartMetricRow(label = "Bars", value = stats.itemCount.toString())
            if (metricFormat == com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.COUNT) ChartMetricRow(label = "Total", value = metricFormat.format(stats.totalValue.toDouble()))
            ChartMetricRow(label = "Max value", value = metricFormat.format(stats.maxValue.toDouble()))
            ChartMetricRow(label = "Min value", value = metricFormat.format(stats.minValue.toDouble()))
            ChartMetricRow(label = "Average", value = metricFormat.format(stats.averageValue))
        }

        if (presentation.showLegend) {
            preparedItems.take(8).forEach { item ->
                ChartLegendMetricRow(
                    label = item.label,
                    value = metricFormat.format(item.value.toDouble()),
                    color = item.color
                )
            }

            if (preparedItems.size > 8) {
                ChartMetricRow(
                    label = "More bars",
                    value = "${preparedItems.size - 8} more shown in inspector"
                )
            }
        }
    }

    if (inspectorOpen && stats != null) {
        HorizontalBarInspectorDialog(
            title = title,
            subtitle = subtitle,
            items = preparedItems,
            stats = stats,
            xAxisLabel = xAxisLabel,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
