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
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun GroupedRatioBarChart(
    title: String,
    items: List<ChartRatioItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    directionOverride: ChartReadingDirection? = null
) {
    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        automaticExportKey = items,
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = title,
                subtitle = subtitle,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ChartLegendMetricRow(
                    label = item.label,
                    value = item.displayValue,
                    color = item.color
                )

                RoundedRatioBar(
                    ratio = item.ratio,
                    color = item.color,
                    modifier = modifier.height(18.dp)
                )
            }
        }
    }
}
