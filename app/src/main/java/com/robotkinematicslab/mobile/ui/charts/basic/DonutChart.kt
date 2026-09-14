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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.core.formatChartPercent
import com.robotkinematicslab.mobile.ui.charts.core.safeRatio
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun DonutChart(
    title: String,
    centerLabel: String,
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val total =
        slices.sumOf {
            it.value
        }.coerceAtLeast(1)

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        automaticExportKey = listOf(centerLabel, slices),
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.STACKED_SHARE,
                title = title,
                subtitle = subtitle,
                directionOverride = directionOverride
            )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(160.dp)
            ) {
                val strokeWidth =
                    28.dp.toPx()

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
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Butt
                    )
                )

                var startAngle =
                    -90f

                slices.forEach { slice ->
                    val sweep =
                        360f * (slice.value.toFloat() / total.toFloat())

                    if (slice.value > 0) {
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                    }

                    startAngle += sweep
                }
            }

            Text(
                text = centerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = ChartTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
        }

        if (presentation.showLegend) {
            slices.forEach { slice ->
                val ratio =
                    safeRatio(
                        numerator = slice.value,
                        denominator = total
                    )

                ChartLegendMetricRow(
                    label = slice.label,
                    value = "${slice.value} (${formatChartPercent(ratio)})",
                    color = slice.color
                )
            }
        }
    }
}
