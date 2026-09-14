package com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartTimelineCell
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalTimelineChart(
    title: String,
    subtitle: String,
    cells: List<ChartTimelineCell>,
    xAxisLabel: String,
    legendItems: List<ChartSlice>,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val preparedCells =
        remember(cells) {
            prepareTimelineCells(cells)
        }

    val stats =
        remember(
            preparedCells,
            legendItems
        ) {
            computeTimelineStats(
                cells = preparedCells,
                legendItems = legendItems
            )
        }

    val guide =
        remember(title, subtitle, xAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.TIMELINE,
                title = title,
                subtitle = subtitle,
                xAxisLabel = xAxisLabel,
                supportsDataInspector = true,
                directionOverride = directionOverride
            )
        }

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        automaticExportKey = listOf(preparedCells, legendItems, xAxisLabel),
        guide = guide
    ) {
        if (preparedCells.isEmpty() || stats == null) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    timelinePreviewHeight(
                        cellCount = preparedCells.size
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
            TimelineCanvas(
                cells = preparedCells,
                xAxisLabel = xAxisLabel,
                scale = 1f,
                offsetX = 0f,
                showAxisLabels = true,
                showCellLabels = false,
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
            ChartMetricRow(label = "X axis", value = xAxisLabel)
            ChartMetricRow(label = "Runs shown", value = stats.cellCount.toString())
            ChartMetricRow(label = "Legend items", value = stats.legendItemCount.toString())
            ChartMetricRow(
                label = "Dominant state",
                value = "${stats.dominantLabel} · ${stats.dominantCount}"
            )
        }

        if (presentation.showLegend) {
            legendItems.forEach { item ->
                ChartLegendMetricRow(
                    label = item.label,
                    value = item.value.toString(),
                    color = item.color
                )
            }
        }
    }

    if (inspectorOpen && stats != null) {
        TimelineInspectorDialog(
            title = title,
            subtitle = subtitle,
            cells = preparedCells,
            stats = stats,
            xAxisLabel = xAxisLabel,
            legendItems = legendItems,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
