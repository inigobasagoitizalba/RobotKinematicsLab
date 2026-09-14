package com.robotkinematicslab.mobile.ui.charts.advanced.linecharts

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawLineChartAxisLayer(
    xAxisLabel: String,
    yAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    visibleWindow: LineChartVisibleWindow
) {
    drawIntoCanvas { canvas ->
        val axisPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF202124).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 10.dp.toPx()
            }

        val tickPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF5F6368).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 7.dp.toPx()
            }

        val linePaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFFDADCE0).toArgb()
                strokeWidth = 1.dp.toPx()
            }

        val xTickCount =
            when {
                width >= 340.dp.toPx() -> 6
                width >= 240.dp.toPx() -> 5
                else -> 4
            }.coerceAtLeast(2)

        val yTickCount =
            when {
                height >= 260.dp.toPx() -> 6
                height >= 180.dp.toPx() -> 5
                else -> 4
            }.coerceAtLeast(2)

        val xSpan =
            visibleWindow.maxX - visibleWindow.minX

        val ySpan =
            visibleWindow.maxY - visibleWindow.minY

        canvas.nativeCanvas.drawLine(
            left,
            top + height,
            left + width,
            top + height,
            linePaint
        )

        canvas.nativeCanvas.drawLine(
            left,
            top,
            left,
            top + height,
            linePaint
        )

        repeat(xTickCount) { index ->
            val ratio =
                index.toFloat() / (xTickCount - 1).toFloat()

            val x =
                left + width * ratio

            val value =
                visibleWindow.minX + xSpan * ratio.toDouble()

            canvas.nativeCanvas.drawLine(
                x,
                top + height,
                x,
                top + height + 4.dp.toPx(),
                linePaint
            )

            canvas.nativeCanvas.drawText(
                compactLineChartAxisValue(
                    value = value,
                    visibleSpan = xSpan
                ),
                x,
                top + height + 13.dp.toPx(),
                tickPaint
            )
        }

        tickPaint.textAlign = Paint.Align.RIGHT

        repeat(yTickCount) { index ->
            val ratio =
                index.toFloat() / (yTickCount - 1).toFloat()

            val y =
                top + height - height * ratio

            val value =
                visibleWindow.minY + ySpan * ratio.toDouble()

            canvas.nativeCanvas.drawLine(
                left - 4.dp.toPx(),
                y,
                left,
                y,
                linePaint
            )

            canvas.nativeCanvas.drawText(
                compactLineChartAxisValue(
                    value = value,
                    visibleSpan = ySpan
                ),
                left - 6.dp.toPx(),
                y + 3.dp.toPx(),
                tickPaint
            )
        }

        axisPaint.textAlign = Paint.Align.CENTER

        canvas.nativeCanvas.drawText(
            xAxisLabel,
            left + width / 2f,
            top + height + 31.dp.toPx(),
            axisPaint
        )

        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(
            -90f,
            13.dp.toPx(),
            top + height / 2f
        )

        canvas.nativeCanvas.drawText(
            yAxisLabel,
            13.dp.toPx(),
            top + height / 2f,
            axisPaint
        )

        canvas.nativeCanvas.restore()
    }
}