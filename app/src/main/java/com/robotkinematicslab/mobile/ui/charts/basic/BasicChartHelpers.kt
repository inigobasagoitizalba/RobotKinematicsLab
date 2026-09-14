package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

@Composable
fun ChartLegendMetricRow(
    label: String,
    value: String,
    color: Color
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendSquare(
                color = color
            )

            JargonAwareText(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 4,
                softWrap = true
            )
        }

        Text(
            text = value,
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            textAlign = TextAlign.End,
            maxLines = 4,
            softWrap = true
        )
    }
}

@Composable
private fun ChartLegendSquare(
    color: Color
) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = color,
                shape = RoundedCornerShape(3.dp)
            )
    )
}

@Composable
fun RoundedRatioBar(
    ratio: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val publicationMode =
        LocalChartPresentationController.current.preferences.preset ==
            ChartPresentationPreset.PUBLICATION
    val barShape =
        if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = ChartTrackColor,
                shape = barShape
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val width =
                size.width * ratio.coerceIn(0.0, 1.0).toFloat()

            if (width > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(
                        width = width,
                        height = size.height
                    ),
                    cornerRadius = CornerRadius(
                        x = if (publicationMode) 0f else size.height / 2f,
                        y = if (publicationMode) 0f else size.height / 2f
                    )
                )
            }
        }
    }
}
