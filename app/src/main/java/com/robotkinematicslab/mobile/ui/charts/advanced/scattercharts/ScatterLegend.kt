package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

data class ScatterLegendItem(
    val label: String,
    val value: String,
    val color: Color
)

@Composable
internal fun ScatterLegend(
    points: List<ChartPoint>,
    items: List<ScatterLegendItem> = emptyList(),
    correlation: Double? = null
) {
    val legendItems =
        if (items.isNotEmpty()) {
            items
        } else {
            buildScatterLegendItems(points)
        }

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
            text = "Scatter Plot Statistics",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF202124)
        )

        JargonAwareText(
            text = "Legend and statistical summary for the scatter chart.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5F6368),
            maxLines = 3
        )

        legendItems
            .chunked(2)
            .forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        ScatterLegendPill(
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

        ScatterStatRow(
            label = "Correlation coefficient",
            value = formatScatterCorrelation(correlation)
        )

        ScatterStatRow(
            label = "Total points",
            value = points.size.toString()
        )
    }
}

@Composable
internal fun ScatterInlineLegend(
    points: List<ChartPoint>,
    items: List<ScatterLegendItem> = emptyList()
) {
    val legendItems =
        if (items.isNotEmpty()) {
            items
        } else {
            buildScatterLegendItems(points)
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
        legendItems
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
                                shape = RoundedCornerShape(999.dp)
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
private fun ScatterLegendPill(
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
                .size(10.dp)
                .background(
                    color = color,
                    shape = RoundedCornerShape(999.dp)
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

@Composable
private fun ScatterStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        JargonAwareText(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF202124),
            modifier = Modifier.weight(1f),
            maxLines = 3
        )

        JargonAwareText(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF202124),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            textAlign = TextAlign.End
        )
    }
}

private fun buildScatterLegendItems(
    points: List<ChartPoint>
): List<ScatterLegendItem> {
    val grouped =
        points.groupBy {
            it.color
        }

    if (grouped.isEmpty()) {
        return listOf(
            ScatterLegendItem(
                label = "Points",
                value = "0",
                color = Color(0xFFDADCE0)
            )
        )
    }

    return grouped
        .entries
        .sortedByDescending {
            it.value.size
        }
        .mapIndexed { index, entry ->
            ScatterLegendItem(
                label =
                    when (index) {
                        0 -> "Largest group"
                        1 -> "Second group"
                        2 -> "Third group"
                        else -> "Group ${index + 1}"
                    },
                value = entry.value.size.toString(),
                color = entry.key
            )
        }
}
