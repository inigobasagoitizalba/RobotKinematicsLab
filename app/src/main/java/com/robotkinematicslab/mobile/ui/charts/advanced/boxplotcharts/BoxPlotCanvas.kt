package com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts

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
internal fun BoxPlotCanvas(
    items: List<BoxPlotPreparedItem>,
    globalStats: BoxPlotGlobalStats,
    xAxisLabel: String,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetY: Float = 0f,
    showAxisLabels: Boolean = true,
    showStatLabels: Boolean = false,
    onBoxSelected: ((BoxPlotSelection) -> Unit)? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val cleanItems =
        remember(items) {
            items.filter {
                it.values.isNotEmpty() &&
                        it.stats.min.isFinite() &&
                        it.stats.max.isFinite()
            }
        }
    val safeScale = safeChartScale(scale, 10f)

    val interactiveModifier =
        if (onBoxSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanItems, safeScale, offsetY) {
                detectTapGestures { tap ->
                    findBoxPlotSelection(
                        items = cleanItems,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = safeScale,
                        offsetY = offsetY,
                        leftPadding = 88.dp.toPx(),
                        rightPadding = 18.dp.toPx(),
                        topPadding = 18.dp.toPx(),
                        bottomPadding = 42.dp.toPx(),
                        baseRowHeight = boxPlotRowHeightDp(cleanItems.size, safeScale).dp.toPx()
                    )?.let(onBoxSelected)
                }
            }
        }

    Canvas(
        modifier = interactiveModifier
    ) {
        val leftPadding =
            if (showAxisLabels) {
                88.dp.toPx()
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
                42.dp.toPx()
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
            boxPlotRowHeightDp(
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
                hardClampBoxPlotOffset(
                    offset = offsetY,
                    viewportSize = chartHeight,
                    contentSize = contentHeight
                )
            }

        drawRoundBoxPlotBackground(
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
                    drawBoxPlotItem(
                        item = item,
                        globalStats = globalStats,
                        left = bodyLeft,
                        centerY = centerY,
                        width = chartWidth,
                        rowHeight = rowHeight,
                        chartTop = bodyTop,
                        chartBottom = bodyTop + chartHeight,
                        showStatLabels =
                            (showStatLabels && safeScale >= 1.6f) ||
                                (presentation.showValueLabels && cleanItems.size <= 8)
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
                    drawBoxPlotRowLabel(
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
            drawBoxPlotAxisLayer(
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                left = bodyLeft,
                top = bodyTop,
                width = chartWidth,
                height = chartHeight,
                globalStats = globalStats
            )
        }
    }
}

private fun DrawScope.drawBoxPlotItem(
    item: BoxPlotPreparedItem,
    globalStats: BoxPlotGlobalStats,
    left: Float,
    centerY: Float,
    width: Float,
    rowHeight: Float,
    chartTop: Float,
    chartBottom: Float,
    showStatLabels: Boolean
) {
    val stats =
        item.stats

    fun xForValue(value: Double): Float {
        return left + width * ((value - globalStats.min) / globalStats.range).toFloat()
    }

    val minX =
        xForValue(stats.min)

    val q1X =
        xForValue(stats.q1)

    val medianX =
        xForValue(stats.median)

    val q3X =
        xForValue(stats.q3)

    val maxX =
        xForValue(stats.max)

    val boxHeight =
        (rowHeight * 0.42f)
            .coerceIn(
                minimumValue = 10.dp.toPx(),
                maximumValue = 30.dp.toPx()
            )

    drawLine(
        color = item.color,
        start = Offset(
            x = minX,
            y = centerY
        ),
        end = Offset(
            x = maxX,
            y = centerY
        ),
        strokeWidth = 2.dp.toPx()
    )

    drawLine(
        color = item.color,
        start = Offset(
            x = minX,
            y = centerY - boxHeight * 0.35f
        ),
        end = Offset(
            x = minX,
            y = centerY + boxHeight * 0.35f
        ),
        strokeWidth = 2.dp.toPx()
    )

    drawLine(
        color = item.color,
        start = Offset(
            x = maxX,
            y = centerY - boxHeight * 0.35f
        ),
        end = Offset(
            x = maxX,
            y = centerY + boxHeight * 0.35f
        ),
        strokeWidth = 2.dp.toPx()
    )

    drawRoundRect(
        color = item.color.copy(alpha = 0.55f),
        topLeft = Offset(
            x = q1X,
            y = centerY - boxHeight / 2f
        ),
        size = Size(
            width = (q3X - q1X).coerceAtLeast(2.dp.toPx()),
            height = boxHeight
        ),
        cornerRadius = CornerRadius(
            x = 3.dp.toPx(),
            y = 3.dp.toPx()
        )
    )

    drawLine(
        color = item.color,
        start = Offset(
            x = medianX,
            y = centerY - boxHeight * 0.70f
        ),
        end = Offset(
            x = medianX,
            y = centerY + boxHeight * 0.70f
        ),
        strokeWidth = 3.dp.toPx()
    )

    if (showStatLabels) {
        val labelY =
            (centerY - boxHeight)
                .coerceIn(
                    minimumValue = chartTop + 10.dp.toPx(),
                    maximumValue = chartBottom - 4.dp.toPx()
                )

        drawBoxPlotStatLabel(
            label = "m=${formatBoxPlotDouble(stats.median, digits = 2)}",
            x = medianX,
            y = labelY,
            color = Color(0xFF202124)
        )
    }
}

private fun DrawScope.drawRoundBoxPlotBackground(
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

private fun DrawScope.drawBoxPlotRowLabel(
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
            compactBoxPlotLabel(label),
            x,
            y,
            paint
        )
    }
}

private fun DrawScope.drawBoxPlotStatLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color
) {
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        canvas.nativeCanvas.drawText(
            label.take(12),
            x,
            y,
            paint
        )
    }
}
