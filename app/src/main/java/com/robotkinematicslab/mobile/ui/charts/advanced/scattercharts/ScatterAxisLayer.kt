package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

internal data class ScatterVisibleWindow(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

internal fun computeVisibleScatterWindow(
    stats: ScatterStats,
    chartWidth: Float,
    chartHeight: Float,
    zoomedWidth: Float,
    zoomedHeight: Float,
    offsetX: Float,
    offsetY: Float
): ScatterVisibleWindow {
    if (
        !chartWidth.isFinite() || chartWidth <= 0f ||
        !chartHeight.isFinite() || chartHeight <= 0f ||
        !zoomedWidth.isFinite() || zoomedWidth <= 0f ||
        !zoomedHeight.isFinite() || zoomedHeight <= 0f ||
        !offsetX.isFinite() || !offsetY.isFinite()
    ) {
        return ScatterVisibleWindow(stats.minX, stats.maxX, stats.minY, stats.maxY)
    }
    val visibleMinX =
        stats.minX +
                ((0f - offsetX) / zoomedWidth).toDouble() * stats.xRange

    val visibleMaxX =
        stats.minX +
                ((chartWidth - offsetX) / zoomedWidth).toDouble() * stats.xRange

    val visibleMaxY =
        stats.minY +
                ((zoomedHeight + offsetY) / zoomedHeight).toDouble() * stats.yRange

    val visibleMinY =
        stats.minY +
                ((offsetY + zoomedHeight - chartHeight) / zoomedHeight).toDouble() * stats.yRange

    return ScatterVisibleWindow(
        minX = visibleMinX.coerceIn(stats.minX, stats.maxX),
        maxX = visibleMaxX.coerceIn(stats.minX, stats.maxX),
        minY = visibleMinY.coerceIn(stats.minY, stats.maxY),
        maxY = visibleMaxY.coerceIn(stats.minY, stats.maxY)
    )
}

internal fun DrawScope.drawScatterAxisLayer(
    xAxisLabel: String,
    yAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    visibleWindow: ScatterVisibleWindow
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

        repeat(tickCount) { index ->
            val ratio =
                index.toFloat() / (tickCount - 1).toFloat()

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
                compactScatterAxisValue(
                    value = value,
                    visibleSpan = xSpan
                ),
                x,
                top + height + 13.dp.toPx(),
                tickPaint
            )
        }

        tickPaint.textAlign = Paint.Align.RIGHT

        repeat(tickCount) { index ->
            val ratio =
                index.toFloat() / (tickCount - 1).toFloat()

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
                compactScatterAxisValue(
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

private fun compactScatterAxisValue(
    value: Double,
    visibleSpan: Double
): String {
    if (!value.isFinite()) {
        return "NA"
    }

    val absValue =
        abs(value)

    val absSpan =
        abs(visibleSpan)

    return when {
        absValue >= 1000.0 ->
            "%.1fk".format(Locale.US, value / 1000.0)

        absSpan >= 100.0 ->
            "%.0f".format(Locale.US, value)

        absSpan >= 10.0 ->
            "%.1f".format(Locale.US, value)

        absSpan >= 1.0 ->
            "%.2f".format(Locale.US, value)

        absSpan >= 0.01 ->
            "%.3f".format(Locale.US, value)

        else ->
            "%.4f".format(Locale.US, value)
    }
}
