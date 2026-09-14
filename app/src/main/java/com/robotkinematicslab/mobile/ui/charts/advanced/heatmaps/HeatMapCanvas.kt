package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import kotlin.math.ceil
import kotlin.math.sqrt

@Composable
internal fun HeatMapCanvas(
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    xAxisLabel: String,
    yAxisLabel: String,
    roundedCells: Boolean,
    showAxisLabels: Boolean,
    showCellValues: Boolean,
    showCellNames: Boolean,
    modifier: Modifier = Modifier
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

    Canvas(
        modifier = modifier
    ) {
        val leftPadding =
            if (showAxisLabels) {
                62.dp.toPx()
            } else {
                10.dp.toPx()
            }

        val rightPadding =
            10.dp.toPx()

        val topPadding =
            if (showAxisLabels) {
                34.dp.toPx()
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
            size.width - leftPadding - rightPadding

        val chartHeight =
            size.height - topPadding - bottomPadding

        val rowCount =
            rowLabels.size.coerceAtLeast(1)

        val columnCount =
            columnLabels.size.coerceAtLeast(1)

        val totalCells =
            rowCount * columnCount

        if (showAxisLabels) {
            drawHeatMapAxisLabels(
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                rowLabels = rowLabels,
                columnLabels = columnLabels,
                leftPadding = leftPadding,
                topPadding = topPadding,
                chartWidth = chartWidth,
                chartHeight = chartHeight
            )
        }

        val maxDrawableCells =
            if (roundedCells) {
                6_000
            } else {
                20_000
            }

        if (totalCells > maxDrawableCells) {
            val step =
                ceil(
                    sqrt(totalCells.toDouble() / maxDrawableCells.toDouble())
                ).toInt().coerceAtLeast(1)

            rowLabels.forEachIndexed { rowIndex, row ->
                if (rowIndex % step == 0) {
                    columnLabels.forEachIndexed { columnIndex, column ->
                        if (columnIndex % step == 0) {
                            val cell =
                                lookup[row to column]

                            val left =
                                leftPadding + chartWidth *
                                        (columnIndex.toFloat() / columnCount.toFloat())

                            val right =
                                leftPadding + chartWidth *
                                        (((columnIndex + step).coerceAtMost(columnCount)).toFloat() /
                                                columnCount.toFloat())

                            val top =
                                topPadding + chartHeight *
                                        (rowIndex.toFloat() / rowCount.toFloat())

                            val bottom =
                                topPadding + chartHeight *
                                        (((rowIndex + step).coerceAtMost(rowCount)).toFloat() /
                                                rowCount.toFloat())

                            drawRect(
                                color = cell?.color ?: Color(0xFFE0E0E0),
                                topLeft = Offset(
                                    x = left,
                                    y = top
                                ),
                                size = Size(
                                    width = (right - left).coerceAtLeast(0.5f),
                                    height = (bottom - top).coerceAtLeast(0.5f)
                                )
                            )
                        }
                    }
                }
            }

            return@Canvas
        }

        val gap =
            if (totalCells <= 500) {
                1.dp.toPx()
            } else {
                0.25.dp.toPx()
            }

        rowLabels.forEachIndexed { rowIndex, row ->
            columnLabels.forEachIndexed { columnIndex, column ->
                val cell =
                    lookup[row to column]

                val left =
                    leftPadding + chartWidth *
                            (columnIndex.toFloat() / columnCount.toFloat())

                val right =
                    leftPadding + chartWidth *
                            ((columnIndex + 1).toFloat() / columnCount.toFloat())

                val top =
                    topPadding + chartHeight *
                            (rowIndex.toFloat() / rowCount.toFloat())

                val bottom =
                    topPadding + chartHeight *
                            ((rowIndex + 1).toFloat() / rowCount.toFloat())

                val drawSize =
                    Size(
                        width = (right - left - gap).coerceAtLeast(0.5f),
                        height = (bottom - top - gap).coerceAtLeast(0.5f)
                    )

                val cellColor =
                    cell?.color ?: Color(0xFFE0E0E0)

                if (roundedCells) {
                    drawRoundRect(
                        color = cellColor,
                        topLeft = Offset(
                            x = left,
                            y = top
                        ),
                        size = drawSize,
                        cornerRadius = CornerRadius(
                            x = 3.dp.toPx(),
                            y = 3.dp.toPx()
                        )
                    )
                } else {
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(
                            x = left,
                            y = top
                        ),
                        size = drawSize
                    )
                }

                if (
                    cell != null &&
                    totalCells <= 500 &&
                    drawSize.width >= 18.dp.toPx() &&
                    drawSize.height >= 12.dp.toPx() &&
                    (showCellValues || showCellNames)
                ) {
                    drawIntoCanvas { canvas ->
                        val textColor =
                            if (cellColor.luminance() < 0.45f) {
                                Color.White.toArgb()
                            } else {
                                Color(0xFF202124).toArgb()
                            }

                        val paint =
                            Paint().apply {
                                isAntiAlias = true
                                textAlign = Paint.Align.CENTER
                                textSize = 9.dp.toPx()
                                color = textColor
                            }

                        val centerX =
                            left + drawSize.width / 2f

                        val centerY =
                            top + drawSize.height / 2f

                        when {
                            showCellNames && showCellValues && drawSize.height >= 28.dp.toPx() -> {
                                canvas.nativeCanvas.drawText(
                                    compactCellName(row, column),
                                    centerX,
                                    centerY - 2.dp.toPx(),
                                    paint
                                )

                                canvas.nativeCanvas.drawText(
                                    compactCellText(cell.displayValue),
                                    centerX,
                                    centerY + 10.dp.toPx(),
                                    paint
                                )
                            }

                            showCellNames -> {
                                canvas.nativeCanvas.drawText(
                                    compactCellName(row, column),
                                    centerX,
                                    centerY + 3.dp.toPx(),
                                    paint
                                )
                            }

                            showCellValues -> {
                                canvas.nativeCanvas.drawText(
                                    compactCellText(cell.displayValue),
                                    centerX,
                                    centerY + 3.dp.toPx(),
                                    paint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHeatMapAxisLabels(
    xAxisLabel: String,
    yAxisLabel: String,
    rowLabels: List<String>,
    columnLabels: List<String>,
    leftPadding: Float,
    topPadding: Float,
    chartWidth: Float,
    chartHeight: Float
) {
    drawIntoCanvas { canvas ->
        val safeColumnCount =
            columnLabels.size.coerceAtLeast(1)

        val safeRowCount =
            rowLabels.size.coerceAtLeast(1)

        val columnCellWidth =
            chartWidth / safeColumnCount.toFloat()

        val rowCellHeight =
            chartHeight / safeRowCount.toFloat()

        val axisPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF202124).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 10.dp.toPx()
            }

        val columnPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF5F6368).toArgb()
                textAlign = Paint.Align.CENTER
                textSize =
                    adaptiveDenseAxisTextSize(
                        cellSizePx = columnCellWidth,
                        minSizePx = 1.8.dp.toPx(),
                        maxSizePx = 5.5.dp.toPx()
                    )
            }

        val rowPaint =
            Paint().apply {
                isAntiAlias = true
                color = Color(0xFF5F6368).toArgb()
                textAlign = Paint.Align.RIGHT
                textSize =
                    adaptiveDenseAxisTextSize(
                        cellSizePx = rowCellHeight,
                        minSizePx = 2.2.dp.toPx(),
                        maxSizePx = 7.dp.toPx()
                    )
            }

        canvas.nativeCanvas.drawText(
            xAxisLabel,
            leftPadding + chartWidth / 2f,
            size.height - 7.dp.toPx(),
            axisPaint
        )

        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(
            -90f,
            14.dp.toPx(),
            topPadding + chartHeight / 2f
        )
        canvas.nativeCanvas.drawText(
            yAxisLabel,
            14.dp.toPx(),
            topPadding + chartHeight / 2f,
            axisPaint
        )
        canvas.nativeCanvas.restore()

        columnLabels.forEachIndexed { index, label ->
            val centerX =
                leftPadding + chartWidth *
                        ((index.toFloat() + 0.5f) / safeColumnCount.toFloat())

            canvas.nativeCanvas.drawText(
                compactAxisLabel(label),
                centerX,
                topPadding - 9.dp.toPx(),
                columnPaint
            )
        }

        rowLabels.forEachIndexed { index, label ->
            val centerY =
                topPadding + chartHeight *
                        ((index.toFloat() + 0.5f) / safeRowCount.toFloat())

            canvas.nativeCanvas.drawText(
                compactAxisLabel(label),
                leftPadding - 6.dp.toPx(),
                centerY + rowPaint.textSize * 0.35f,
                rowPaint
            )
        }
    }
}