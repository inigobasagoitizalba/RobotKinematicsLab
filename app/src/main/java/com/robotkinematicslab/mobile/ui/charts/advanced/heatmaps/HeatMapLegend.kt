package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

data class ChartHeatMapLegendItem(
    val label: String,
    val value: String,
    val color: Color
)

@Composable
internal fun HeatMapLegend(
    cells: List<ChartHeatMapCell>,
    legendItems: List<ChartHeatMapLegendItem>
) {
    if (legendItems.isNotEmpty()) {
        HeatMapSemanticLegend(
            items = legendItems
        )
    } else {
        HeatMapValueLegend(
            cells = cells
        )
    }
}

@Composable
internal fun HeatMapInlineLegend(
    cells: List<ChartHeatMapCell>,
    legendItems: List<ChartHeatMapLegendItem>
) {
    val inlineItems =
        if (legendItems.isNotEmpty()) {
            legendItems
        } else {
            buildValueLegendItems(cells)
        }

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
        inlineItems
            .take(4)
            .forEach { item ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(
                                color = item.color,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        JargonAwareText(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF202124),
                            maxLines = 3
                        )

                        JargonAwareText(
                            text = item.value,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5F6368),
                            maxLines = 2
                        )
                    }
                }
            }
    }
}

@Composable
private fun HeatMapSemanticLegend(
    items: List<ChartHeatMapLegendItem>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        JargonAwareText(
            text = "Legend",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF202124)
        )

        items
            .chunked(2)
            .forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        HeatMapLegendPill(
                            label = item.label,
                            value = item.value,
                            color = item.color,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (rowItems.size == 1) {
                        Box(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
    }
}

@Composable
private fun HeatMapValueLegend(
    cells: List<ChartHeatMapCell>
) {
    val items =
        buildValueLegendItems(cells)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFF5F5F5),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        JargonAwareText(
            text = "Legend",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF202124)
        )

        items
            .chunked(2)
            .forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        HeatMapLegendPill(
                            label = item.label,
                            value = item.value,
                            color = item.color,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (rowItems.size == 1) {
                        Box(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
    }
}

@Composable
private fun HeatMapLegendPill(
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
                .height(10.dp)
                .widthIn(
                    min = 10.dp,
                    max = 10.dp
                )
                .background(
                    color = color,
                    shape = RoundedCornerShape(3.dp)
                )
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            JargonAwareText(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF5F6368),
                maxLines = 3
            )

            JargonAwareText(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF202124),
                maxLines = 2
            )
        }
    }
}

private fun buildValueLegendItems(
    cells: List<ChartHeatMapCell>
): List<ChartHeatMapLegendItem> {
    val sortedCells =
        cells
            .filter {
                it.value.isFinite()
            }
            .sortedBy {
                it.value
            }

    val minCell =
        sortedCells.firstOrNull()

    val midCell =
        if (sortedCells.isNotEmpty()) {
            sortedCells[sortedCells.lastIndex / 2]
        } else {
            null
        }

    val maxCell =
        sortedCells.lastOrNull()

    return listOf(
        ChartHeatMapLegendItem(
            label = "Lowest",
            value = minCell?.displayValue ?: "NA",
            color = minCell?.color ?: Color(0xFFDADCE0)
        ),
        ChartHeatMapLegendItem(
            label = "Middle",
            value = midCell?.displayValue ?: "NA",
            color = midCell?.color ?: Color(0xFFDADCE0)
        ),
        ChartHeatMapLegendItem(
            label = "Highest",
            value = maxCell?.displayValue ?: "NA",
            color = maxCell?.color ?: Color(0xFFDADCE0)
        ),
        ChartHeatMapLegendItem(
            label = "Missing",
            value = "No data",
            color = Color(0xFFDADCE0)
        )
    )
}
