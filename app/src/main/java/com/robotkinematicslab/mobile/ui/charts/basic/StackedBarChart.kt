package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.core.formatChartPercent
import com.robotkinematicslab.mobile.ui.charts.core.safeRatio
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun StackedBarChart(
    title: String,
    totalLabel: String,
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val total =
        slices.sumOf {
            it.value
        }.coerceAtLeast(1)

    ChartSectionCard(
        title = title,
        subtitle = totalLabel,
        automaticExportKey = listOf(totalLabel, slices),
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.STACKED_SHARE,
                title = title,
                subtitle = totalLabel,
                directionOverride = directionOverride
            )
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp))
                .background(
                    color = ChartTrackColor,
                    shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                var startX =
                    0f

                slices.forEach { slice ->
                    val widthFraction =
                        slice.value.toFloat() / total.toFloat()

                    val sliceWidth =
                        size.width * widthFraction

                    if (sliceWidth > 0f) {
                        drawRect(
                            color = slice.color,
                            topLeft = Offset(startX, 0f),
                            size = Size(
                                width = sliceWidth,
                                height = size.height
                            )
                        )
                    }

                    startX += sliceWidth
                }
            }
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
