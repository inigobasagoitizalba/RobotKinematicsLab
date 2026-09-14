package com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.drawChartGrid
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun HorizontalBarCanvas(
    items: List<HorizontalBarPreparedItem>,
    stats: HorizontalBarStats,
    xAxisLabel: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetY: Float = 0f,
    showAxisLabels: Boolean = true,
    showValueLabels: Boolean = false,
    onBarSelected: ((HorizontalBarSelection) -> Unit)? = null
) {
    val metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.LocalScientificMetricFormat.current
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val cleanItems =
        remember(items) {
            items.filter {
                it.value >= 0
            }
        }
    val safeScale = safeChartScale(scale, 10f)

    val interactiveModifier =
        if (onBarSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanItems, safeScale, offsetY) {
                detectTapGestures { tap ->
                    findHorizontalBarSelection(
                        items = cleanItems,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = safeScale,
                        offsetY = offsetY,
                        leftPadding = 96.dp.toPx(),
                        rightPadding = 18.dp.toPx(),
                        topPadding = 18.dp.toPx(),
                        bottomPadding = 40.dp.toPx(),
                        baseRowHeight = horizontalBarRowHeightDp(cleanItems.size, safeScale).dp.toPx()
                    )?.let(onBarSelected)
                }
            }
        }

    Canvas(
        modifier = interactiveModifier
    ) {
        val leftPadding =
            if (showAxisLabels) {
                96.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val rightPadding =
            if (showAxisLabels) {
                18.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val topPadding =
            if (showAxisLabels) {
                18.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val bottomPadding =
            if (showAxisLabels) {
                40.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val chartWidth =
            (size.width - leftPadding - rightPadding)
                .coerceAtLeast(1f)

        val chartHeight =
            (size.height - topPadding - bottomPadding)
                .coerceAtLeast(1f)

        val bodyLeft =
            leftPadding

        val bodyTop =
            topPadding

        val baseRowHeight =
            horizontalBarRowHeightDp(
                itemCount = cleanItems.size,
                scale = safeScale
            ).dp.toPx()

        val fillRowHeight =
            if (cleanItems.isNotEmpty()) {
                chartHeight / cleanItems.size.toFloat()
            } else {
                chartHeight
            }

        val rowHeight =
            if (safeScale <= 1.001f) {
                fillRowHeight
            } else {
                baseRowHeight
            }

        val contentHeight =
            rowHeight * cleanItems.size.toFloat()

        val centeredOffsetY =
            if (contentHeight < chartHeight) {
                (chartHeight - contentHeight) / 2f
            } else {
                0f
            }

        val clampedOffsetY =
            if (contentHeight <= chartHeight) {
                centeredOffsetY
            } else {
                hardClampHorizontalBarOffset(
                    offset = offsetY,
                    viewportSize = chartHeight,
                    contentSize = contentHeight
                )
            }

        drawRoundHorizontalBarBackground(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height,
            publicationMode = publicationMode
        )

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.clipRect(
                bodyLeft,
                bodyTop,
                bodyLeft + chartWidth,
                bodyTop + chartHeight
            )

            drawChartGrid(
                left = bodyLeft,
                top = bodyTop,
                width = chartWidth,
                height = chartHeight,
                showGridLines = presentation.showGrid
            )

            cleanItems.forEachIndexed { index, item ->
                val centerY =
                    bodyTop + clampedOffsetY + index.toFloat() * rowHeight + rowHeight / 2f

                if (
                    centerY >= bodyTop - rowHeight &&
                    centerY <= bodyTop + chartHeight + rowHeight
                ) {
                    drawHorizontalBarItem(
                        metricFormat = metricFormat,
                        item = item,
                        stats = stats,
                        left = bodyLeft,
                        centerY = centerY,
                        width = chartWidth,
                        rowHeight = rowHeight,
                        showValueLabels = showValueLabels || presentation.showValueLabels
                    )
                }
            }

            canvas.nativeCanvas.restore()
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.clipRect(
                0f,
                bodyTop,
                bodyLeft,
                bodyTop + chartHeight
            )

            cleanItems.forEachIndexed { index, item ->
                val centerY =
                    bodyTop + clampedOffsetY + index.toFloat() * rowHeight + rowHeight / 2f

                if (
                    centerY >= bodyTop - rowHeight &&
                    centerY <= bodyTop + chartHeight + rowHeight
                ) {
                    drawHorizontalBarRowLabel(
                        label = item.label,
                        x = bodyLeft - 8.dp.toPx(),
                        y = centerY + 3.dp.toPx(),
                        color = Color(0xFF5F6368)
                    )
                }
            }

            canvas.nativeCanvas.restore()
        }

        if (showAxisLabels) {
            drawHorizontalBarAxisLayer(
                metricFormat = metricFormat,
                xAxisLabel = xAxisLabel,
                left = bodyLeft,
                top = bodyTop,
                width = chartWidth,
                height = chartHeight,
                maxValue = stats.maxValue
            )
        }
    }
}

private fun DrawScope.drawHorizontalBarItem(
    metricFormat: com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat,
    item: HorizontalBarPreparedItem,
    stats: HorizontalBarStats,
    left: Float,
    centerY: Float,
    width: Float,
    rowHeight: Float,
    showValueLabels: Boolean
) {
    val maxValue =
        stats.maxValue.coerceAtLeast(1)

    val ratio =
        item.value.toFloat() / maxValue.toFloat()

    val barHeight =
        (rowHeight * 0.26f)
            .coerceIn(
                minimumValue = 0.75.dp.toPx(),
                maximumValue = 18.dp.toPx()
            )

    val barWidth =
        (width * ratio)
            .coerceAtLeast(
                if (item.value > 0) {
                    2.dp.toPx()
                } else {
                    0f
                }
            )

    drawRoundRect(
        color = item.color,
        topLeft = Offset(
            x = left,
            y = centerY - barHeight / 2f
        ),
        size = Size(
            width = barWidth,
            height = barHeight
        ),
        cornerRadius = CornerRadius(
            x = 5.dp.toPx(),
            y = 5.dp.toPx()
        )
    )

    if (showValueLabels) {
        val labelX =
            if (barWidth >= 42.dp.toPx()) {
                left + barWidth - 6.dp.toPx()
            } else {
                left + barWidth + 6.dp.toPx()
            }

        val align =
            if (barWidth >= 42.dp.toPx()) {
                Paint.Align.RIGHT
            } else {
                Paint.Align.LEFT
            }

        drawHorizontalBarValueLabel(
            label = metricFormat.format(item.value.toDouble()),
            x = labelX.coerceIn(
                minimumValue = left + 2.dp.toPx(),
                maximumValue = left + width - 2.dp.toPx()
            ),
            y = centerY + 3.dp.toPx(),
            color = if (barWidth >= 42.dp.toPx()) {
                Color.White
            } else {
                Color(0xFF202124)
            },
            align = align
        )
    }
}

private fun DrawScope.drawRoundHorizontalBarBackground(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    publicationMode: Boolean
) {
    drawRoundRect(
        color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
        topLeft = Offset(
            x = left,
            y = top
        ),
        size = Size(
            width = width,
            height = height
        ),
        cornerRadius = CornerRadius(
            x = 12.dp.toPx(),
            y = 12.dp.toPx()
        )
    )
}

private fun DrawScope.drawHorizontalBarRowLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color
) {
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        canvas.nativeCanvas.drawText(
            compactHorizontalBarLabel(label),
            x,
            y,
            paint
        )
    }
}

private fun DrawScope.drawHorizontalBarValueLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color,
    align: Paint.Align
) {
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = align
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        canvas.nativeCanvas.drawText(
            label,
            x,
            y,
            paint
        )
    }
}

private fun DrawScope.drawHorizontalBarAxisLayer(
    metricFormat: com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat,
    xAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    maxValue: Int
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
                (maxValue.toDouble() * ratio.toDouble()).toInt()

            canvas.nativeCanvas.drawLine(
                x,
                top + height,
                x,
                top + height + 4.dp.toPx(),
                linePaint
            )

            canvas.nativeCanvas.drawText(
                metricFormat.format(value.toDouble()),
                x,
                top + height + 13.dp.toPx(),
                tickPaint
            )
        }

        canvas.nativeCanvas.drawText(
            xAxisLabel,
            left + width / 2f,
            top + height + 31.dp.toPx(),
            axisPaint
        )
    }
}
