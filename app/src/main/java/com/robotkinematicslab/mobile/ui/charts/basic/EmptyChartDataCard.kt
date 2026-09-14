package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

@Composable
fun EmptyChartDataCard(
    title: String,
    subtitle: String
) {
    ChartSectionCard(
        title = title,
        subtitle = subtitle
    ) {
        JargonAwareText(
            text = "This visualization is available, but the current diagnostic report contains no compatible observations to plot.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChartTextSecondary
        )
    }
}
