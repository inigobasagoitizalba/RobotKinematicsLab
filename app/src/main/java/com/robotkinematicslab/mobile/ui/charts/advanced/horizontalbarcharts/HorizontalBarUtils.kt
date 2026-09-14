package com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts

import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

internal data class HorizontalBarPreparedItem(
    val label: String,
    val value: Int,
    val color: androidx.compose.ui.graphics.Color
)

internal data class HorizontalBarStats(
    val itemCount: Int,
    val totalValue: Int,
    val maxValue: Int,
    val minValue: Int,
    val averageValue: Double
)

internal data class HorizontalBarSelection(
    val item: HorizontalBarPreparedItem,
    val index: Int
)

internal fun findHorizontalBarSelection(
    items: List<HorizontalBarPreparedItem>,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offsetY: Float,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float,
    baseRowHeight: Float
): HorizontalBarSelection? {
    if (items.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftPadding, rightPadding, topPadding, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f || !baseRowHeight.isFinite() || baseRowHeight <= 0f) return null

    val chartWidth = viewportWidth - leftPadding - rightPadding
    val chartHeight = viewportHeight - topPadding - bottomPadding
    if (tapX !in 0f..(leftPadding + chartWidth)) return null
    if (tapY !in topPadding..(topPadding + chartHeight)) return null

    val fillRowHeight = chartHeight / items.size.toFloat()
    val rowHeight = if (scale <= 1.001f) fillRowHeight else baseRowHeight
    val contentHeight = rowHeight * items.size.toFloat()
    val effectiveOffset =
        if (contentHeight <= chartHeight) {
            (chartHeight - contentHeight) / 2f
        } else {
            hardClampHorizontalBarOffset(offsetY, chartHeight, contentHeight)
        }
    val index = floor((tapY - topPadding - effectiveOffset) / rowHeight).toInt()
    if (index !in items.indices) return null
    return HorizontalBarSelection(item = items[index], index = index)
}

internal fun prepareHorizontalBarItems(
    items: List<ChartBarItem>
): List<HorizontalBarPreparedItem> {
    return items
        .filter {
            it.value >= 0
        }
        .sortedByDescending {
            it.value
        }
        .map {
            HorizontalBarPreparedItem(
                label = it.label,
                value = it.value,
                color = it.color
            )
        }
}

internal fun computeHorizontalBarStats(
    items: List<HorizontalBarPreparedItem>
): HorizontalBarStats? {
    if (items.isEmpty()) {
        return null
    }

    val total =
        items.sumOf {
            it.value
        }

    val max =
        items.maxOf {
            it.value
        }.coerceAtLeast(1)

    val min =
        items.minOf {
            it.value
        }

    return HorizontalBarStats(
        itemCount = items.size,
        totalValue = total,
        maxValue = max,
        minValue = min,
        averageValue = total.toDouble() / items.size.toDouble()
    )
}

internal fun horizontalBarPreviewHeight(
    itemCount: Int
): Int {
    return when {
        itemCount <= 3 -> 170
        itemCount <= 6 -> 220
        itemCount <= 10 -> 280
        else -> 320
    }
}

internal fun horizontalBarRowHeightDp(
    itemCount: Int,
    scale: Float = 1f
): Float {
    val base =
        when {
            itemCount <= 5 -> 42f
            itemCount <= 12 -> 34f
            itemCount <= 30 -> 28f
            else -> 24f
        }

    return (base * scale.coerceIn(0.9f, 2.4f))
        .coerceIn(
            minimumValue = 20f,
            maximumValue = 72f
        )
}

internal fun hardClampHorizontalBarOffset(
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

internal fun formatHorizontalBarDouble(
    value: Double,
    digits: Int = 2
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun compactHorizontalBarValue(
    value: Int
): String {
    val absValue =
        abs(value)

    return when {
        absValue >= 1_000_000 ->
            "%.1fM".format(Locale.US, value.toDouble() / 1_000_000.0)

        absValue >= 1_000 ->
            "%.1fk".format(Locale.US, value.toDouble() / 1_000.0)

        else ->
            value.toString()
    }
}

internal fun compactHorizontalBarLabel(
    value: String
): String {
    return when {
        value.length <= 13 ->
            value

        value.contains("-") ->
            value
                .split("-")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(13)

        value.contains("_") ->
            value
                .split("_")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(13)

        else ->
            value.take(13)
    }
}

internal fun cleanHorizontalBarTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
