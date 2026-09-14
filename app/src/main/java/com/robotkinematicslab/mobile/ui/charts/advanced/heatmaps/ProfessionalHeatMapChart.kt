package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.advanced.compactProfessionalLabels
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalHeatMapChart(
    title: String,
    subtitle: String,
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
    legendItems: List<ChartHeatMapLegendItem> = emptyList(),
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    var showCellNames by remember {
        mutableStateOf(false)
    }

    val cleanCells =
        cells.filter {
            it.value.isFinite()
        }

    val totalPossibleCells =
        rowLabels.size * columnLabels.size

    val missingCellCount =
        (totalPossibleCells - cleanCells.size).coerceAtLeast(0)

    val previewHeight =
        heatMapPreviewHeight(
            rowCount = rowLabels.size,
            columnCount = columnLabels.size
        )

    val guide =
        remember(title, subtitle, xAxisLabel, yAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HEAT_MAP,
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
        automaticExportKey =
            cleanCells.takeIf { it.isNotEmpty() }?.let { listOf(rowLabels, columnLabels, it).hashCode() }
    ) {
        if (rowLabels.isEmpty() || columnLabels.isEmpty() || cleanCells.isEmpty()) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(previewHeight.dp)
                .background(
                    color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
                    shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                )
                .clickable {
                    inspectorOpen = true
                }
        ) {
            HeatMapCanvas(
                rowLabels = rowLabels,
                columnLabels = columnLabels,
                cells = cleanCells,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                roundedCells = rowLabels.size * columnLabels.size <= 120,
                showAxisLabels = true,
                showCellValues = presentation.showValueLabels,
                showCellNames = showCellNames,
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

        if (!publicationMode && !com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress.current) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 8.dp
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
                        text = "Show row × column labels inside cells when there is enough space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6368),
                        maxLines = 3
                    )
                }

                Switch(
                    checked = showCellNames,
                    onCheckedChange = {
                        showCellNames = it
                    }
                )
            }
        }

        if (presentation.showStatistics) {
            ChartAxisInfoRow(
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel
            )

            ChartMetricRow(
                label = "Rows",
                value = "${rowLabels.size} · ${compactProfessionalLabels(rowLabels)}"
            )

            ChartMetricRow(
                label = "Columns",
                value = "${columnLabels.size} · ${compactProfessionalLabels(columnLabels)}"
            )

            ChartMetricRow(
                label = "Cells with data",
                value = "${cleanCells.size} / $totalPossibleCells"
            )

            ChartMetricRow(
                label = "Missing cells",
                value = missingCellCount.toString()
            )

            ChartMetricRow(
                label = "Value range",
                value =
                    "${formatHeatMapDouble(cleanCells.minOf { it.value })} → ${
                        formatHeatMapDouble(cleanCells.maxOf { it.value })
                    }"
            )

            ChartMetricRow(
                label = "Average value",
                value = formatHeatMapDouble(cleanCells.map { it.value }.average())
            )
        }

        if (presentation.showLegend) {
            HeatMapLegend(
                cells = cleanCells,
                legendItems = legendItems
            )
        }
    }

    if (inspectorOpen) {
        HeatMapInspectorDialog(
            title = title,
            subtitle = subtitle,
            rowLabels = rowLabels,
            columnLabels = columnLabels,
            cells = cleanCells,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            showCellNames = showCellNames,
            legendItems = legendItems,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
