package com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

internal data class HistogramVisibleWindow(
    val minX: Double,
    val maxX: Double,
    val maxY: Int
)

internal fun computeVisibleHistogramWindow(
    bins: List<HistogramBin>,
    chartWidth: Float,
    zoomedWidth: Float,
    offsetX: Float,
    maxY: Int
): HistogramVisibleWindow {
    if (
        bins.isEmpty() ||
        !chartWidth.isFinite() || chartWidth <= 0f ||
        !zoomedWidth.isFinite() || zoomedWidth <= 0f ||
        !offsetX.isFinite()
    ) {
        return HistogramVisibleWindow(
            minX = 0.0,
            maxX = 1.0,
            maxY = maxY.coerceAtLeast(1)
        )
    }

    val firstStart =
        bins.first().start

    val lastEnd =
        bins.last().end

    val fullRange =
        safeHistogramRange(
            min = firstStart,
            max = lastEnd
        )

    val visibleMinX =
        firstStart +
                ((0f - offsetX) / zoomedWidth).toDouble() * fullRange

    val visibleMaxX =
        firstStart +
                ((chartWidth - offsetX) / zoomedWidth).toDouble() * fullRange

    return HistogramVisibleWindow(
        minX = visibleMinX.coerceIn(firstStart, lastEnd),
        maxX = visibleMaxX.coerceIn(firstStart, lastEnd),
        maxY = maxY.coerceAtLeast(1)
    )
}

internal fun DrawScope.drawHistogramAxisLayer(
    xAxisLabel: String,
    yAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    visibleWindow: HistogramVisibleWindow
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
            5

        val xSpan =
            visibleWindow.maxX - visibleWindow.minX

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
                compactHistogramAxisValue(
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
                visibleWindow.maxY * ratio

            canvas.nativeCanvas.drawLine(
                left - 4.dp.toPx(),
                y,
                left,
                y,
                linePaint
            )

            canvas.nativeCanvas.drawText(
                value.toInt().toString(),
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

internal fun hardClampHistogramOffset(
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
