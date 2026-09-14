package com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun TimelineCanvas(
    cells: List<TimelinePreparedCell>,
    xAxisLabel: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetX: Float = 0f,
    showAxisLabels: Boolean = true,
    showCellLabels: Boolean = false,
    onCellSelected: ((TimelineSelection) -> Unit)? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val cleanCells =
        remember(cells) {
            cells
        }
    val safeScale = safeChartScale(scale, 32f)

    val interactiveModifier =
        if (onCellSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanCells, safeScale, offsetX) {
                detectTapGestures { tap ->
                    findTimelineSelection(
                        cells = cleanCells,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = safeScale,
                        offsetX = offsetX,
                        leftPadding = 42.dp.toPx(),
                        rightPadding = 14.dp.toPx(),
                        topPadding = 18.dp.toPx(),
                        bottomPadding = 34.dp.toPx()
                    )?.let(onCellSelected)
                }
            }
        }

    Canvas(
        modifier = interactiveModifier
    ) {
        val leftPadding =
            if (showAxisLabels) {
                42.dp.toPx()
            } else {
                10.dp.toPx()
            }

        val rightPadding =
            if (showAxisLabels) {
                14.dp.toPx()
            } else {
                10.dp.toPx()
            }

        val topPadding =
            if (showAxisLabels) {
                18.dp.toPx()
            } else {
                10.dp.toPx()
            }

        val bottomPadding =
            if (showAxisLabels) {
                34.dp.toPx()
            } else {
                10.dp.toPx()
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

        val cellCount =
            cleanCells.size.coerceAtLeast(1)

        val zoomedWidth =
            chartWidth * safeScale

        val clampedOffsetX =
            hardClampTimelineOffset(
                offset = offsetX,
                viewportSize = chartWidth,
                contentSize = zoomedWidth
            )

        drawRoundTimelineBackground(
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

            val cellWidth =
                zoomedWidth / cellCount.toFloat()

            val gap =
                timelineCellGapPx(cellWidth)

            cleanCells.forEachIndexed { index, cell ->
                val left =
                    bodyLeft +
                            clampedOffsetX +
                            index.toFloat() * cellWidth

                val right =
                    bodyLeft +
                            clampedOffsetX +
                            (index + 1).toFloat() * cellWidth

                if (right >= bodyLeft && left <= bodyLeft + chartWidth) {
                    drawRect(
                        color = cell.color,
                        topLeft = Offset(
                            x = left + gap / 2f,
                            y = bodyTop
                        ),
                        size = Size(
                            width = (cellWidth - gap).coerceAtLeast(0.5f),
                            height = chartHeight
                        )
                    )

                    if (
                        (showCellLabels || presentation.showValueLabels) &&
                        (safeScale >= 3f || cleanCells.size <= 16) &&
                        cellWidth >= 24.dp.toPx()
                    ) {
                        drawTimelineCellLabel(
                            label = cell.label,
                            x = left + cellWidth / 2f,
                            y = bodyTop + chartHeight / 2f + 3.dp.toPx(),
                            color = Color(0xFF202124)
                        )
                    }
                }
            }

            canvas.nativeCanvas.restore()
        }

        if (showAxisLabels) {
            drawTimelineAxisLayer(
                xAxisLabel = xAxisLabel,
                left = bodyLeft,
                top = bodyTop,
                width = chartWidth,
                height = chartHeight,
                cellCount = cellCount,
                scale = safeScale,
                offsetX = clampedOffsetX
            )
        }
    }
}

private fun DrawScope.drawRoundTimelineBackground(
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

private fun DrawScope.drawTimelineCellLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color
) {
    if (label.isBlank()) {
        return
    }

    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        canvas.nativeCanvas.drawText(
            compactTimelineLabel(label),
            x,
            y,
            paint
        )
    }
}

private fun DrawScope.drawTimelineAxisLayer(
    xAxisLabel: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    cellCount: Int,
    scale: Float,
    offsetX: Float
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

        val zoomedWidth =
            width * scale

        val firstVisibleIndex =
            ((0f - offsetX) / zoomedWidth * cellCount.toFloat())
                .toInt()
                .coerceIn(
                    minimumValue = 0,
                    maximumValue = (cellCount - 1).coerceAtLeast(0)
                )

        val lastVisibleIndex =
            ((width - offsetX) / zoomedWidth * cellCount.toFloat())
                .toInt()
                .coerceIn(
                    minimumValue = 0,
                    maximumValue = (cellCount - 1).coerceAtLeast(0)
                )

        val visibleRange =
            (lastVisibleIndex - firstVisibleIndex)
                .coerceAtLeast(1)

        canvas.nativeCanvas.drawLine(
            left,
            top + height,
            left + width,
            top + height,
            linePaint
        )

        repeat(tickCount) { index ->
            val ratio =
                index.toFloat() / (tickCount - 1).toFloat()

            val x =
                left + width * ratio

            val value =
                firstVisibleIndex + (visibleRange * ratio).toInt() + 1

            canvas.nativeCanvas.drawLine(
                x,
                top + height,
                x,
                top + height + 4.dp.toPx(),
                linePaint
            )

            canvas.nativeCanvas.drawText(
                compactTimelineIndexValue(value),
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
