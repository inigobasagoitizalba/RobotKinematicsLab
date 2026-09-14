package com.robotkinematicslab.mobile.ui.charts.advanced

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun RoundedRatioBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(
                color = Color(0xFFDADCE0),
                shape = RoundedCornerShape(7.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val safeRatio =
                ratio.coerceIn(0f, 1f)

            val width =
                size.width * safeRatio

            if (width > 0f) {
                drawRoundRect(
                    color = color,
                    size = Size(
                        width = width,
                        height = size.height
                    ),
                    cornerRadius = CornerRadius(
                        x = size.height / 2f,
                        y = size.height / 2f
                    )
                )
            }
        }
    }
}

@Composable
fun EmptyProfessionalChartMessage() {
    Text(
        text = "No chart data available.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF5F6368)
    )
}

fun DrawScope.drawChartGrid(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    showGridLines: Boolean = true
) {
    val gridColor =
        Color(0xFFDADCE0)

    if (showGridLines) {
        repeat(5) { index ->
            val ratio =
                index / 4f

            val y =
                top + height * ratio

            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(left + width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        repeat(5) { index ->
            val ratio =
                index / 4f

            val x =
                left + width * ratio

            drawLine(
                color = gridColor,
                start = Offset(x, top),
                end = Offset(x, top + height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }

    drawLine(
        color = Color(0xFF9AA0A6),
        start = Offset(left, top + height),
        end = Offset(left + width, top + height),
        strokeWidth = 1.4.dp.toPx()
    )

    drawLine(
        color = Color(0xFF9AA0A6),
        start = Offset(left, top),
        end = Offset(left, top + height),
        strokeWidth = 1.4.dp.toPx()
    )
}
