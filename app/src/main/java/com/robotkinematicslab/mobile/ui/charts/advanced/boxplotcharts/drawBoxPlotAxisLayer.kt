package com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawBoxPlotAxisLayer(
    xAxisLabel: String,
    yAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    globalStats: BoxPlotGlobalStats
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

        val tickCount =
            when {
                width >= 340.dp.toPx() -> 6
                width >= 240.dp.toPx() -> 5
                else -> 4
            }.coerceAtLeast(2)

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

        repeat(tickCount) { index ->
            val ratio =
                index.toFloat() / (tickCount - 1).toFloat()

            val x =
                left + width * ratio

            val value =
                globalStats.min + globalStats.range * ratio.toDouble()

            canvas.nativeCanvas.drawLine(
                x,
                top + height,
                x,
                top + height + 4.dp.toPx(),
                linePaint
            )

            canvas.nativeCanvas.drawText(
                compactBoxPlotAxisValue(
                    value = value,
                    visibleSpan = globalStats.range
                ),
                x,
                top + height + 13.dp.toPx(),
                tickPaint
            )
        }

        tickPaint.textAlign = Paint.Align.RIGHT

        canvas.nativeCanvas.drawText(
            compactBoxPlotAxisValue(globalStats.max, globalStats.range),
            left - 6.dp.toPx(),
            top + 8.dp.toPx(),
            tickPaint
        )

        canvas.nativeCanvas.drawText(
            compactBoxPlotAxisValue(globalStats.min, globalStats.range),
            left - 6.dp.toPx(),
            top + height,
            tickPaint
        )

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

internal fun hardClampBoxPlotOffset(
    offset: Float,
    viewportSize: Float,
    contentSize: Float
): Float {
    if (
        !offset.isFinite() ||
        !viewportSize.isFinite() ||
        !contentSize.isFinite() ||
        viewportSize <= 0f ||
        contentSize <= 0f
    ) {
        return 0f
    }

    if (contentSize <= viewportSize) {
        return 0f
    }

    val minOffset =
        viewportSize - contentSize

    val maxOffset =
        0f

    return offset.coerceIn(
        minimumValue = minOffset,
        maximumValue = maxOffset
    )
}