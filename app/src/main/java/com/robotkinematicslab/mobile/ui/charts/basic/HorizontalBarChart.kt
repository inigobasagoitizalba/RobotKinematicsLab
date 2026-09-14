package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.max
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun HorizontalBarChart(
    title: String,
    items: List<ChartBarItem>,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val maxValue =
        max(
            1,
            items.maxOfOrNull {
                it.value
            } ?: 1
        )

    ChartSectionCard(
        title = title,
        automaticExportKey = items,
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = title,
                directionOverride = directionOverride
            )
    ) {
        if (items.isEmpty()) {
            Text(
                text = "No chart data available.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChartTextSecondary
            )
        }

        items.forEach { item ->
            val ratio =
                item.value.toFloat() / maxValue.toFloat()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ChartLegendMetricRow(
                    label = item.label,
                    value = item.value.toString(),
                    color = item.color
                )

                RoundedRatioBar(
                    ratio = ratio.toDouble(),
                    color = item.color,
                    modifier = modifier.height(16.dp)
                )
            }
        }
    }
}
