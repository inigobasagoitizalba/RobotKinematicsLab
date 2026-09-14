package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale

@Composable
internal fun StickyHeatMapCanvas(
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    showCellValues: Boolean,
    showCellNames: Boolean,
    modifier: Modifier = Modifier,
    onCellSelected: ((HeatMapSelection) -> Unit)? = null
) {
    val lookup =
        remember(
            rowLabels,
            columnLabels,
            cells
        ) {
            cells.associateBy {
                it.row to it.column
            }
    }
    val safeScale = safeChartScale(scale, 18f)
    val currentScale by rememberUpdatedState(safeScale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnCellSelected by rememberUpdatedState(onCellSelected)

    val interactiveModifier =
        if (onCellSelected == null) {
            modifier
        } else {
            // Pan/zoom recomposes this canvas continuously. Keep the tap detector stable instead
            // of cancelling it for every offset update, while reading the current viewport here.
            modifier.pointerInput(rowLabels, columnLabels, cells) {
                detectTapGestures { tap ->
                    findHeatMapSelection(
                        rowLabels = rowLabels,
                        columnLabels = columnLabels,
                        cells = cells,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = currentScale,
                        offsetX = currentOffsetX,
                        offsetY = currentOffsetY,
                        leftHeaderWidth = 72.dp.toPx(),
                        topHeaderHeight = 46.dp.toPx(),
                        rightPadding = 10.dp.toPx(),
                        bottomPadding = 10.dp.toPx()
                    )?.let { currentOnCellSelected?.invoke(it) }
                }
            }
        }

    Canvas(
        modifier = interactiveModifier
    ) {
        val leftHeaderWidth =
            72.dp.toPx()

        val topHeaderHeight =
            46.dp.toPx()

        val rightPadding =
            10.dp.toPx()

        val bottomPadding =
            10.dp.toPx()

        val bodyLeft =
            leftHeaderWidth

        val bodyTop =
            topHeaderHeight

        val bodyWidth =
            (size.width - leftHeaderWidth - rightPadding)
                .coerceAtLeast(1f)

        val bodyHeight =
            (size.height - topHeaderHeight - bottomPadding)
                .coerceAtLeast(1f)

        val rowCount =
            rowLabels.size.coerceAtLeast(1)

        val columnCount =
            columnLabels.size.coerceAtLeast(1)

        val baseCellWidth =
            bodyWidth / columnCount.toFloat()

        val baseCellHeight =
            bodyHeight / rowCount.toFloat()

        val zoomedCellWidth =
            baseCellWidth * safeScale

        val zoomedCellHeight =
            baseCellHeight * safeScale

        val clampedOffsetX =
            softClampHeatMapOffset(
                offset = offsetX,
                viewportSize = bodyWidth,
                contentSize = zoomedCellWidth * columnCount.toFloat(),
                overscrollPx = bodyWidth * 0.35f
            )

        val clampedOffsetY =
            softClampHeatMapOffset(
                offset = offsetY,
                viewportSize = bodyHeight,
                contentSize = zoomedCellHeight * rowCount.toFloat(),
                overscrollPx = bodyHeight * 0.35f
            )

        drawRoundRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset.Zero,
            size = Size(
                width = size.width,
                height = size.height
            ),
            cornerRadius = CornerRadius(
                x = 14.dp.toPx(),
                y = 14.dp.toPx()
            )
        )

        drawRect(
            color = Color(0xFFF7F9FC),
            topLeft = Offset(
                x = bodyLeft,
                y = 0f
            ),
            size = Size(
                width = bodyWidth,
                height = topHeaderHeight
            )
        )

        drawRect(
            color = Color(0xFFF7F9FC),
            topLeft = Offset(
                x = 0f,
                y = bodyTop
            ),
            size = Size(
                width = leftHeaderWidth,
                height = bodyHeight
            )
        )

        drawRect(
            color = Color(0xFFE8EAED),
            topLeft = Offset.Zero,
            size = Size(
                width = leftHeaderWidth,
                height = topHeaderHeight
            )
        )

        drawHeatMapBodyCells(
            rowLabels = rowLabels,
            columnLabels = columnLabels,
            lookup = lookup,
            bodyLeft = bodyLeft,
            bodyTop = bodyTop,
            bodyWidth = bodyWidth,
            bodyHeight = bodyHeight,
            offsetX = clampedOffsetX,
            offsetY = clampedOffsetY,
            zoomedCellWidth = zoomedCellWidth,
            zoomedCellHeight = zoomedCellHeight,
            showCellValues = showCellValues,
            showCellNames = showCellNames
        )

        drawStickyColumnHeaders(
            columnLabels = columnLabels,
            xAxisLabel = xAxisLabel,
            bodyLeft = bodyLeft,
            bodyTop = bodyTop,
            bodyWidth = bodyWidth,
            topHeaderHeight = topHeaderHeight,
            offsetX = clampedOffsetX,
            zoomedCellWidth = zoomedCellWidth
        )

        drawStickyRowHeaders(
            rowLabels = rowLabels,
            yAxisLabel = yAxisLabel,
            leftHeaderWidth = leftHeaderWidth,
            bodyTop = bodyTop,
            bodyHeight = bodyHeight,
            offsetY = clampedOffsetY,
            zoomedCellHeight = zoomedCellHeight
        )

        drawRect(
            color = Color(0xFFDADCE0),
            topLeft = Offset(
                x = bodyLeft,
                y = bodyTop
            ),
            size = Size(
                width = 1.dp.toPx(),
                height = bodyHeight
            )
        )

        drawRect(
            color = Color(0xFFDADCE0),
            topLeft = Offset(
                x = bodyLeft,
                y = bodyTop
            ),
            size = Size(
                width = bodyWidth,
                height = 1.dp.toPx()
            )
        )
    }
}

private fun DrawScope.drawHeatMapBodyCells(
    rowLabels: List<String>,
    columnLabels: List<String>,
    lookup: Map<Pair<String, String>, ChartHeatMapCell>,
    bodyLeft: Float,
    bodyTop: Float,
    bodyWidth: Float,
    bodyHeight: Float,
    offsetX: Float,
    offsetY: Float,
    zoomedCellWidth: Float,
    zoomedCellHeight: Float,
    showCellValues: Boolean,
    showCellNames: Boolean
) {
    val gap =
        if (zoomedCellWidth >= 14.dp.toPx() && zoomedCellHeight >= 14.dp.toPx()) {
            1.dp.toPx()
        } else {
            0.25.dp.toPx()
        }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.clipRect(
            bodyLeft,
            bodyTop,
            bodyLeft + bodyWidth,
            bodyTop + bodyHeight
        )

        rowLabels.forEachIndexed { rowIndex, row ->
            val top =
                bodyTop + offsetY + rowIndex.toFloat() * zoomedCellHeight

            val bottom =
                top + zoomedCellHeight

            if (bottom >= bodyTop && top <= bodyTop + bodyHeight) {
                columnLabels.forEachIndexed { columnIndex, column ->
                    val left =
                        bodyLeft + offsetX + columnIndex.toFloat() * zoomedCellWidth

                    val right =
                        left + zoomedCellWidth

                    if (right >= bodyLeft && left <= bodyLeft + bodyWidth) {
                        val cell =
                            lookup[row to column]

                        val cellColor =
                            cell?.color ?: Color(0xFFE0E0E0)

                        drawRect(
                            color = cellColor,
                            topLeft = Offset(
                                x = left,
                                y = top
                            ),
                            size = Size(
                                width = (zoomedCellWidth - gap).coerceAtLeast(0.5f),
                                height = (zoomedCellHeight - gap).coerceAtLeast(0.5f)
                            )
                        )

                        if (
                            cell != null &&
                            (showCellValues || showCellNames) &&
                            zoomedCellWidth >= 5.dp.toPx() &&
                            zoomedCellHeight >= 4.dp.toPx()
                        ) {
                            val textColor =
                                if (cellColor.luminance() < 0.45f) {
                                    Color.White.toArgb()
                                } else {
                                    Color(0xFF202124).toArgb()
                                }

                            val textSize =
                                fittedTextSizeForCellText(
                                    boxWidthPx = zoomedCellWidth,
                                    boxHeightPx = zoomedCellHeight,
                                    lineCount =
                                        if (showCellNames && showCellValues) {
                                            2
                                        } else {
                                            1
                                        },
                                    minSizePx = 2.5.dp.toPx(),
                                    maxSizePx = 10.dp.toPx()
                                )

                            if (textSize >= 2.5.dp.toPx()) {
                                val paint =
                                    Paint().apply {
                                        isAntiAlias = true
                                        textAlign = Paint.Align.CENTER
                                        color = textColor
                                        this.textSize = textSize
                                    }

                                val centerX =
                                    left + zoomedCellWidth / 2f

                                val centerY =
                                    top + zoomedCellHeight / 2f

                                canvas.nativeCanvas.save()
                                canvas.nativeCanvas.clipRect(
                                    left,
                                    top,
                                    right,
                                    bottom
                                )

                                when {
                                    showCellNames && showCellValues && zoomedCellHeight >= textSize * 2.4f -> {
                                        canvas.nativeCanvas.drawText(
                                            compactCellName(row, column),
                                            centerX,
                                            centerY - textSize * 0.20f,
                                            paint
                                        )

                                        canvas.nativeCanvas.drawText(
                                            compactCellText(cell.displayValue),
                                            centerX,
                                            centerY + textSize * 0.95f,
                                            paint
                                        )
                                    }

                                    showCellNames -> {
                                        canvas.nativeCanvas.drawText(
                                            compactCellName(row, column),
                                            centerX,
                                            centerY + textSize * 0.35f,
                                            paint
                                        )
                                    }

                                    showCellValues -> {
                                        canvas.nativeCanvas.drawText(
                                            compactCellText(cell.displayValue),
                                            centerX,
                                            centerY + textSize * 0.35f,
                                            paint
                                        )
                                    }
                                }

                                canvas.nativeCanvas.restore()
                            }
                        }
                    }
                }
            }
        }

        canvas.nativeCanvas.restore()
    }
}

private fun DrawScope.drawStickyColumnHeaders(
    columnLabels: List<String>,
    xAxisLabel: String,
    bodyLeft: Float,
    bodyTop: Float,
    bodyWidth: Float,
    topHeaderHeight: Float,
    offsetX: Float,
    zoomedCellWidth: Float
) {
    drawIntoCanvas { canvas ->
        val axisPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF202124).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 10.dp.toPx()
            }

        canvas.nativeCanvas.drawText(
            xAxisLabel,
            bodyLeft + bodyWidth / 2f,
            13.dp.toPx(),
            axisPaint
        )

        canvas.nativeCanvas.save()
        canvas.nativeCanvas.clipRect(
            bodyLeft,
            16.dp.toPx(),
            bodyLeft + bodyWidth,
            bodyTop
        )

        columnLabels.forEachIndexed { index, label ->
            val left =
                bodyLeft + offsetX + index.toFloat() * zoomedCellWidth

            val right =
                left + zoomedCellWidth

            if (right >= bodyLeft && left <= bodyLeft + bodyWidth) {
                val centerX =
                    left + zoomedCellWidth / 2f

                val textSize =
                    fittedTextSizeForBox(
                        boxWidthPx = zoomedCellWidth,
                        boxHeightPx = topHeaderHeight - 16.dp.toPx(),
                        minSizePx = 3.5.dp.toPx(),
                        maxSizePx = 9.dp.toPx()
                    )

                val paint =
                    Paint().apply {
                        isAntiAlias = true
                        color = Color(0xFF5F6368).toArgb()
                        textAlign = Paint.Align.CENTER
                        this.textSize = textSize
                    }

                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipRect(
                    left,
                    16.dp.toPx(),
                    right,
                    bodyTop
                )

                canvas.nativeCanvas.drawText(
                    compactAxisLabel(label),
                    centerX,
                    bodyTop - 9.dp.toPx(),
                    paint
                )

                canvas.nativeCanvas.restore()
            }
        }

        canvas.nativeCanvas.restore()
    }
}

private fun DrawScope.drawStickyRowHeaders(
    rowLabels: List<String>,
    yAxisLabel: String,
    leftHeaderWidth: Float,
    bodyTop: Float,
    bodyHeight: Float,
    offsetY: Float,
    zoomedCellHeight: Float
) {
    drawIntoCanvas { canvas ->
        val axisPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF202124).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 10.dp.toPx()
            }

        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(
            -90f,
            14.dp.toPx(),
            bodyTop + bodyHeight / 2f
        )

        canvas.nativeCanvas.drawText(
            yAxisLabel,
            14.dp.toPx(),
            bodyTop + bodyHeight / 2f,
            axisPaint
        )

        canvas.nativeCanvas.restore()

        canvas.nativeCanvas.save()
        canvas.nativeCanvas.clipRect(
            22.dp.toPx(),
            bodyTop,
            leftHeaderWidth,
            bodyTop + bodyHeight
        )

        rowLabels.forEachIndexed { index, label ->
            val top =
                bodyTop + offsetY + index.toFloat() * zoomedCellHeight

            val bottom =
                top + zoomedCellHeight

            if (bottom >= bodyTop && top <= bodyTop + bodyHeight) {
                val centerY =
                    top + zoomedCellHeight / 2f

                val textSize =
                    fittedTextSizeForBox(
                        boxWidthPx = leftHeaderWidth - 28.dp.toPx(),
                        boxHeightPx = zoomedCellHeight,
                        minSizePx = 3.5.dp.toPx(),
                        maxSizePx = 9.dp.toPx()
                    )

                val paint =
                    Paint().apply {
                        isAntiAlias = true
                        color = Color(0xFF5F6368).toArgb()
                        textAlign = Paint.Align.RIGHT
                        this.textSize = textSize
                    }

                canvas.nativeCanvas.save()
                canvas.nativeCanvas.clipRect(
                    22.dp.toPx(),
                    top,
                    leftHeaderWidth,
                    bottom
                )

                canvas.nativeCanvas.drawText(
                    compactAxisLabel(label),
                    leftHeaderWidth - 6.dp.toPx(),
                    centerY + textSize * 0.35f,
                    paint
                )

                canvas.nativeCanvas.restore()
            }
        }

        canvas.nativeCanvas.restore()
    }
}
