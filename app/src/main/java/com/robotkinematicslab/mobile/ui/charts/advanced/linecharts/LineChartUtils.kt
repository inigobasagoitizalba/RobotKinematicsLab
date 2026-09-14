package com.robotkinematicslab.mobile.ui.charts.advanced.linecharts

import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import java.util.Locale
import kotlin.math.abs

internal data class LineChartStats(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val xRange: Double,
    val yRange: Double,
    val pointCount: Int,
    val firstY: Double,
    val lastY: Double,
    val deltaY: Double
)

internal data class LineChartVisibleWindow(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

internal data class LineChartSelection(
    val point: ChartLinePoint,
    val index: Int
)

internal fun findLineChartSelection(
    points: List<ChartLinePoint>,
    stats: LineChartStats,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float,
    plotInset: Float,
    maximumDistance: Float
): LineChartSelection? {
    if (points.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftPadding, rightPadding, topPadding, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f || !maximumDistance.isFinite() || maximumDistance < 0f) return null
    val chartWidth = viewportWidth - leftPadding - rightPadding
    val chartHeight = viewportHeight - topPadding - bottomPadding
    val plotWidth = (chartWidth - plotInset * 2f).coerceAtLeast(1f)
    val plotHeight = (chartHeight - plotInset * 2f).coerceAtLeast(1f)
    if (tapX !in leftPadding..(leftPadding + chartWidth)) return null
    if (tapY !in topPadding..(topPadding + chartHeight)) return null

    val zoomedWidth = plotWidth * scale.coerceAtLeast(1f)
    val zoomedHeight = plotHeight * scale.coerceAtLeast(1f)
    if (!zoomedWidth.isFinite() || !zoomedHeight.isFinite()) return null
    val clampedX = hardClampLineChartOffset(offsetX, plotWidth, zoomedWidth)
    val clampedY = hardClampLineChartOffset(offsetY, plotHeight, zoomedHeight)
    val maxDistanceSquared = maximumDistance * maximumDistance

    return points
        .mapIndexedNotNull { index, point ->
            if (!point.x.isFinite() || !point.y.isFinite()) return@mapIndexedNotNull null

            val x = leftPadding + plotInset + clampedX +
                    zoomedWidth * lineChartNormalizedPosition(point.x, stats.minX, stats.maxX)
            val y = topPadding + plotInset + clampedY + zoomedHeight -
                    zoomedHeight * lineChartNormalizedPosition(point.y, stats.minY, stats.maxY)
            val dx = tapX - x
            val dy = tapY - y
            val distanceSquared = dx * dx + dy * dy

            if (distanceSquared <= maxDistanceSquared) {
                Triple(distanceSquared, index, point)
            } else {
                null
            }
        }
        .minByOrNull { it.first }
        ?.let { LineChartSelection(point = it.third, index = it.second) }
}

internal fun cleanLineChartPoints(
    points: List<ChartLinePoint>
): List<ChartLinePoint> {
    return points
        .filter {
            it.x.isFinite() && it.y.isFinite()
        }
        .sortedBy {
            it.x
        }
}

internal fun computeLineChartStats(
    points: List<ChartLinePoint>
): LineChartStats? {
    val cleanPoints =
        cleanLineChartPoints(points)

    if (cleanPoints.isEmpty()) {
        return null
    }

    val minX =
        cleanPoints.minOf {
            it.x
        }

    val maxX =
        cleanPoints.maxOf {
            it.x
        }

    val minY =
        cleanPoints.minOf {
            it.y
        }

    val maxY =
        cleanPoints.maxOf {
            it.y
        }

    val firstY =
        cleanPoints.first().y

    val lastY =
        cleanPoints.last().y

    return LineChartStats(
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        xRange = safeLineChartRange(
            min = minX,
            max = maxX
        ),
        yRange = safeLineChartRange(
            min = minY,
            max = maxY
        ),
        pointCount = cleanPoints.size,
        firstY = firstY,
        lastY = lastY,
        deltaY = lastY - firstY
    )
}

internal fun safeLineChartRange(
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

internal fun computeVisibleLineChartWindow(
    stats: LineChartStats,
    chartWidth: Float,
    chartHeight: Float,
    zoomedWidth: Float,
    zoomedHeight: Float,
    offsetX: Float,
    offsetY: Float
): LineChartVisibleWindow {
    if (
        !chartWidth.isFinite() || chartWidth <= 0f ||
        !chartHeight.isFinite() || chartHeight <= 0f ||
        !zoomedWidth.isFinite() || zoomedWidth <= 0f ||
        !zoomedHeight.isFinite() || zoomedHeight <= 0f ||
        !offsetX.isFinite() || !offsetY.isFinite()
    ) {
        return LineChartVisibleWindow(stats.minX, stats.maxX, stats.minY, stats.maxY)
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

    return LineChartVisibleWindow(
        minX = visibleMinX.coerceIn(stats.minX, stats.maxX),
        maxX = visibleMaxX.coerceIn(stats.minX, stats.maxX),
        minY = visibleMinY.coerceIn(stats.minY, stats.maxY),
        maxY = visibleMaxY.coerceIn(stats.minY, stats.maxY)
    )
}

internal fun lineChartNormalizedPosition(
    value: Double,
    min: Double,
    max: Double
): Float {
    if (!value.isFinite() || !min.isFinite() || !max.isFinite()) return 0.5f
    val range = max - min
    if (!range.isFinite() || range <= 1e-9) return 0.5f
    return ((value - min) / range).coerceIn(0.0, 1.0).toFloat()
}

internal fun hardClampLineChartOffset(
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

internal fun lineChartPreviewHeight(
    pointCount: Int
): Int {
    return when {
        pointCount <= 100 -> 220
        pointCount <= 1_000 -> 240
        else -> 260
    }
}

internal fun lineChartPointRadiusDp(
    pointCount: Int,
    scale: Float = 1f
): Float {
    val base =
        when {
            pointCount <= 100 -> 3.4f
            pointCount <= 1_000 -> 2.4f
            pointCount <= 5_000 -> 1.6f
            else -> 1.0f
        }

    return (base * scale.coerceIn(0.75f, 2.2f))
        .coerceIn(
            minimumValue = 0.8f,
            maximumValue = 5.0f
        )
}

internal fun lineChartStrokeWidthDp(
    pointCount: Int,
    scale: Float = 1f
): Float {
    val base =
        when {
            pointCount <= 24 -> 1.9f
            pointCount <= 100 -> 1.55f
            pointCount <= 1_000 -> 1.15f
            else -> 0.8f
        }

    return (base * scale.coerceIn(0.9f, 1.15f))
        .coerceIn(
            minimumValue = 0.65f,
            maximumValue = 2.15f
        )
}

internal fun formatLineChartDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun compactLineChartAxisValue(
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

internal fun cleanLineChartTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
