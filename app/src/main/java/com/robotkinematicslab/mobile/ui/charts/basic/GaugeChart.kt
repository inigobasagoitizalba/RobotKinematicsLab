package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun GaugeChart(
    title: String,
    ratio: Double,
    valueLabel: String,
    subtitle: String? = null,
    color: Color,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val clampedRatio =
        ratio.coerceIn(0.0, 1.0)

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        automaticExportKey = listOf(ratio, valueLabel, color),
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.GAUGE,
                title = title,
                subtitle = subtitle,
                directionOverride = directionOverride
            )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(155.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(150.dp)
            ) {
                val strokeWidth =
                    24.dp.toPx()

                val diameter =
                    size.minDimension - strokeWidth

                val topLeft =
                    Offset(
                        x = strokeWidth / 2f,
                        y = strokeWidth / 2f
                    )

                val arcSize =
                    Size(
                        width = diameter,
                        height = diameter
                    )

                drawArc(
                    color = ChartTrackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )

                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = (270f * clampedRatio).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }

            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleLarge,
                color = ChartTextPrimary,
                textAlign = TextAlign.Center
            )
        }

        if (presentation.showStatistics) {
            ChartLegendMetricRow(
                label = "Gauge value",
                value = valueLabel,
                color = color
            )
        }
    }
}
