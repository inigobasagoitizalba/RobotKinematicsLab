package com.robotkinematicslab.mobile.ui.charts.advanced.timelinecharts

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartTimelineCell
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import java.util.Locale
import kotlin.math.floor

internal data class TimelinePreparedCell(
    val index: Int,
    val label: String,
    val color: Color
)

internal data class TimelineStats(
    val cellCount: Int,
    val legendItemCount: Int,
    val dominantLabel: String,
    val dominantCount: Int
)

internal data class TimelineSelection(
    val cell: TimelinePreparedCell,
    val index: Int
)

internal fun findTimelineSelection(
    cells: List<TimelinePreparedCell>,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offsetX: Float,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float
): TimelineSelection? {
    if (cells.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftPadding, rightPadding, topPadding, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f) return null

    val chartWidth = viewportWidth - leftPadding - rightPadding
    val chartHeight = viewportHeight - topPadding - bottomPadding
    if (tapX !in leftPadding..(leftPadding + chartWidth)) return null
    if (tapY !in topPadding..(topPadding + chartHeight)) return null

    val zoomedWidth = chartWidth * scale.coerceAtLeast(1f)
    if (!zoomedWidth.isFinite()) return null
    val clampedX = hardClampTimelineOffset(offsetX, chartWidth, zoomedWidth)
    val cellWidth = zoomedWidth / cells.size.toFloat()
    val index = floor((tapX - leftPadding - clampedX) / cellWidth).toInt()
    if (index !in cells.indices) return null
    return TimelineSelection(cell = cells[index], index = index)
}

internal fun prepareTimelineCells(
    cells: List<ChartTimelineCell>
): List<TimelinePreparedCell> {
    return cells.mapIndexed { index, cell ->
        TimelinePreparedCell(
            index = index,
            label = inferTimelineCellLabel(
                cell = cell,
                index = index
            ),
            color = cell.color
        )
    }
}

internal fun computeTimelineStats(
    cells: List<TimelinePreparedCell>,
    legendItems: List<ChartSlice>
): TimelineStats? {
    if (cells.isEmpty()) {
        return null
    }

    val dominantEntry =
        cells
            .groupingBy {
                it.label
            }
            .eachCount()
            .maxByOrNull {
                it.value
            }

    return TimelineStats(
        cellCount = cells.size,
        legendItemCount = legendItems.size,
        dominantLabel = dominantEntry?.key ?: "Unknown",
        dominantCount = dominantEntry?.value ?: 0
    )
}

internal fun inferTimelineCellLabel(
    cell: ChartTimelineCell,
    index: Int
): String {
    return when {
        cell.label.isNotBlank() ->
            cell.label

        else ->
            "Run ${index + 1}"
    }
}

internal fun timelinePreviewHeight(
    cellCount: Int
): Int {
    return when {
        cellCount <= 20 -> 120
        cellCount <= 100 -> 110
        else -> 100
    }
}

internal fun timelineCellGapPx(
    cellWidthPx: Float
): Float {
    return when {
        cellWidthPx >= 16f -> 1.5f
        cellWidthPx >= 8f -> 1f
        cellWidthPx >= 4f -> 0.5f
        else -> 0f
    }
}

internal fun compactTimelineLabel(
    value: String
): String {
    return when {
        value.length <= 14 ->
            value

        value.contains("-") ->
            value
                .split("-")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(14)

        value.contains("_") ->
            value
                .split("_")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(14)

        else ->
            value.take(14)
    }
}

internal fun compactTimelineIndexValue(
    value: Int
): String {
    return when {
        value >= 1_000_000 ->
            "%.1fM".format(Locale.US, value.toDouble() / 1_000_000.0)

        value >= 1_000 ->
            "%.1fk".format(Locale.US, value.toDouble() / 1_000.0)

        else ->
            value.toString()
    }
}

internal fun formatTimelineDouble(
    value: Double,
    digits: Int = 2
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun hardClampTimelineOffset(
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

internal fun cleanTimelineTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
