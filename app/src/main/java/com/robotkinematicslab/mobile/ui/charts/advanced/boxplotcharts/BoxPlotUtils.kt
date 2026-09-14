package com.robotkinematicslab.mobile.ui.charts.advanced.boxplotcharts

import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartBoxPlotItem
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import java.util.Locale
import kotlin.math.max
import kotlin.math.floor

internal data class BoxPlotPreparedItem(
    val label: String,
    val values: List<Double>,
    val color: Color,
    val stats: BoxPlotStats
)

internal data class BoxPlotStats(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double,
    val mean: Double,
    val sampleCount: Int
)

internal data class BoxPlotGlobalStats(
    val min: Double,
    val max: Double,
    val range: Double,
    val itemCount: Int,
    val sampleCount: Int
)

internal data class BoxPlotSelection(
    val item: BoxPlotPreparedItem,
    val index: Int
)

internal fun findBoxPlotSelection(
    items: List<BoxPlotPreparedItem>,
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
): BoxPlotSelection? {
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
            hardClampBoxPlotOffset(offsetY, chartHeight, contentHeight)
        }
    val index = floor((tapY - topPadding - effectiveOffset) / rowHeight).toInt()
    if (index !in items.indices) return null
    return BoxPlotSelection(item = items[index], index = index)
}

internal fun prepareBoxPlotItems(
    items: List<ChartBoxPlotItem>
): List<BoxPlotPreparedItem> {
    return items
        .mapNotNull { item ->
            val cleanValues =
                item.values
                    .filter {
                        it.isFinite()
                    }
                    .sorted()

            if (cleanValues.isEmpty()) {
                null
            } else {
                BoxPlotPreparedItem(
                    label = item.label,
                    values = cleanValues,
                    color = item.color,
                    stats = computeBoxPlotStats(cleanValues)
                )
            }
        }
}

internal fun computeBoxPlotGlobalStats(
    items: List<BoxPlotPreparedItem>
): BoxPlotGlobalStats? {
    if (items.isEmpty()) {
        return null
    }

    val min =
        items.minOf {
            it.stats.min
        }

    val max =
        items.maxOf {
            it.stats.max
        }

    val sampleCount =
        items.sumOf {
            it.stats.sampleCount
        }

    return BoxPlotGlobalStats(
        min = min,
        max = max,
        range = safeBoxPlotRange(
            min = min,
            max = max
        ),
        itemCount = items.size,
        sampleCount = sampleCount
    )
}

internal fun computeBoxPlotStats(
    sortedValues: List<Double>
): BoxPlotStats {
    val min =
        sortedValues.firstOrNull() ?: 0.0

    val max =
        sortedValues.lastOrNull() ?: 0.0

    val mean =
        if (sortedValues.isNotEmpty()) {
            sortedValues.average()
        } else {
            0.0
        }

    return BoxPlotStats(
        min = min,
        q1 = percentileBoxPlotValue(
            sortedValues = sortedValues,
            percentile = 0.25
        ),
        median = percentileBoxPlotValue(
            sortedValues = sortedValues,
            percentile = 0.50
        ),
        q3 = percentileBoxPlotValue(
            sortedValues = sortedValues,
            percentile = 0.75
        ),
        max = max,
        mean = mean,
        sampleCount = sortedValues.size
    )
}

private fun percentileBoxPlotValue(
    sortedValues: List<Double>,
    percentile: Double
): Double {
    if (sortedValues.isEmpty()) {
        return 0.0
    }

    if (sortedValues.size == 1) {
        return sortedValues.first()
    }

    val clampedPercentile =
        percentile.coerceIn(
            minimumValue = 0.0,
            maximumValue = 1.0
        )

    val position =
        clampedPercentile * (sortedValues.size - 1).toDouble()

    val lowerIndex =
        position.toInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = sortedValues.lastIndex
            )

    val upperIndex =
        (lowerIndex + 1)
            .coerceIn(
                minimumValue = 0,
                maximumValue = sortedValues.lastIndex
            )

    val fraction =
        position - lowerIndex.toDouble()

    return sortedValues[lowerIndex] +
            (sortedValues[upperIndex] - sortedValues[lowerIndex]) * fraction
}

internal fun safeBoxPlotRange(
    min: Double,
    max: Double
): Double {
    val range =
        max - min

    return if (range.isFinite() && range > 0.0) {
        range
    } else {
        1.0
    }
}

internal fun boxPlotPreviewHeight(
    itemCount: Int
): Int {
    return max(
        220,
        itemCount * 38
    ).coerceIn(
        minimumValue = 220,
        maximumValue = 420
    )
}

internal fun boxPlotRowHeightDp(
    itemCount: Int,
    scale: Float
): Float {
    val base =
        when {
            itemCount <= 4 -> 48f
            itemCount <= 10 -> 40f
            itemCount <= 20 -> 34f
            else -> 28f
        }

    return (base * scale)
        .coerceIn(
            minimumValue = 24f,
            maximumValue = 90f
        )
}

internal fun formatBoxPlotDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun compactBoxPlotAxisValue(
    value: Double,
    visibleSpan: Double
): String {
    if (!value.isFinite()) {
        return "NA"
    }

    val absValue =
        kotlin.math.abs(value)

    val absSpan =
        kotlin.math.abs(visibleSpan)

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

internal fun compactBoxPlotLabel(
    value: String
): String {
    return when {
        value.length <= 12 ->
            value

        value.contains("_") ->
            value
                .split("_")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(12)

        value.contains("-") ->
            value
                .split("-")
                .mapNotNull {
                    it.firstOrNull()?.toString()
                }
                .joinToString("")
                .take(12)

        else ->
            value.take(12)
    }
}

internal fun cleanBoxPlotTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
