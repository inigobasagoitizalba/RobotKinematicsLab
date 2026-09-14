package com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps

import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import java.util.Locale
import kotlin.math.floor

internal data class HeatMapSelection(
    val row: String,
    val column: String,
    val cell: ChartHeatMapCell?
)

internal fun findHeatMapSelection(
    rowLabels: List<String>,
    columnLabels: List<String>,
    cells: List<ChartHeatMapCell>,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    leftHeaderWidth: Float,
    topHeaderHeight: Float,
    rightPadding: Float,
    bottomPadding: Float
): HeatMapSelection? {
    if (rowLabels.isEmpty() || columnLabels.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftHeaderWidth, rightPadding, topHeaderHeight, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f) return null

    val bodyWidth = viewportWidth - leftHeaderWidth - rightPadding
    val bodyHeight = viewportHeight - topHeaderHeight - bottomPadding
    if (tapX < leftHeaderWidth || tapX > leftHeaderWidth + bodyWidth) return null
    if (tapY < topHeaderHeight || tapY > topHeaderHeight + bodyHeight) return null

    val safeScale = scale.coerceAtLeast(1f)
    val cellWidth = bodyWidth / columnLabels.size.toFloat() * safeScale
    val cellHeight = bodyHeight / rowLabels.size.toFloat() * safeScale
    if (!cellWidth.isFinite() || !cellHeight.isFinite()) return null
    val clampedX =
        softClampHeatMapOffset(offsetX, bodyWidth, cellWidth * columnLabels.size, bodyWidth * 0.35f)
    val clampedY =
        softClampHeatMapOffset(offsetY, bodyHeight, cellHeight * rowLabels.size, bodyHeight * 0.35f)

    val columnIndex = floor((tapX - leftHeaderWidth - clampedX) / cellWidth).toInt()
    val rowIndex = floor((tapY - topHeaderHeight - clampedY) / cellHeight).toInt()
    if (rowIndex !in rowLabels.indices || columnIndex !in columnLabels.indices) return null

    val row = rowLabels[rowIndex]
    val column = columnLabels[columnIndex]
    return HeatMapSelection(
        row = row,
        column = column,
        cell = cells.firstOrNull { it.row == row && it.column == column }
    )
}

internal fun heatMapPreviewHeight(
    rowCount: Int,
    columnCount: Int
): Int {
    val baseHeight =
        when {
            rowCount <= 3 && columnCount <= 5 -> 190
            rowCount <= 8 && columnCount <= 8 -> 260
            rowCount <= 20 && columnCount <= 20 -> 320
            else -> 290
        }

    return baseHeight.coerceIn(
        minimumValue = 180,
        maximumValue = 360
    )
}

internal fun formatHeatMapDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun compactCellText(
    value: String
): String {
    return when {
        value.length <= 8 ->
            value

        value.endsWith("%") ->
            value.take(7) + "%"

        value.endsWith(" m") ->
            value.removeSuffix(" m").take(7) + "m"

        else ->
            value.take(8)
    }
}

internal fun compactCellName(
    row: String,
    column: String
): String {
    return "${compactAxisLabel(row)}×${compactAxisLabel(column)}"
}

internal fun compactAxisLabel(
    value: String
): String {
    return when {
        value.length <= 7 ->
            value

        value.contains("-") ->
            value
                .split("-")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(7)

        value.contains("_") ->
            value
                .split("_")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(7)

        else ->
            value.take(7)
    }
}

internal fun adaptiveDenseAxisTextSize(
    cellSizePx: Float,
    minSizePx: Float,
    maxSizePx: Float
): Float {
    if (!cellSizePx.isFinite() || cellSizePx <= 0f) {
        return minSizePx
    }

    return (cellSizePx * 0.72f)
        .coerceIn(
            minimumValue = minSizePx,
            maximumValue = maxSizePx
        )
}

internal fun fittedTextSizeForBox(
    boxWidthPx: Float,
    boxHeightPx: Float,
    minSizePx: Float,
    maxSizePx: Float
): Float {
    if (
        !boxWidthPx.isFinite() ||
        !boxHeightPx.isFinite() ||
        boxWidthPx <= 0f ||
        boxHeightPx <= 0f
    ) {
        return minSizePx
    }

    val widthBased =
        boxWidthPx * 0.34f

    val heightBased =
        boxHeightPx * 0.42f

    return minOf(
        widthBased,
        heightBased
    ).coerceIn(
        minimumValue = minSizePx,
        maximumValue = maxSizePx
    )
}

internal fun fittedTextSizeForCellText(
    boxWidthPx: Float,
    boxHeightPx: Float,
    lineCount: Int,
    minSizePx: Float,
    maxSizePx: Float
): Float {
    if (
        !boxWidthPx.isFinite() ||
        !boxHeightPx.isFinite() ||
        boxWidthPx <= 0f ||
        boxHeightPx <= 0f
    ) {
        return minSizePx
    }

    val safeLineCount =
        lineCount.coerceAtLeast(1)

    val widthBased =
        boxWidthPx * 0.38f

    val heightBased =
        boxHeightPx * 0.78f / safeLineCount.toFloat()

    return minOf(
        widthBased,
        heightBased
    ).coerceIn(
        minimumValue = minSizePx,
        maximumValue = maxSizePx
    )
}

internal fun softClampHeatMapOffset(
    offset: Float,
    viewportSize: Float,
    contentSize: Float,
    overscrollPx: Float
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
        return offset.coerceIn(
            minimumValue = -overscrollPx,
            maximumValue = overscrollPx
        )
    }

    val minOffset =
        viewportSize - contentSize - overscrollPx

    val maxOffset =
        overscrollPx

    return offset.coerceIn(
        minimumValue = minOffset,
        maximumValue = maxOffset
    )
}

internal fun cleanHeatMapTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
