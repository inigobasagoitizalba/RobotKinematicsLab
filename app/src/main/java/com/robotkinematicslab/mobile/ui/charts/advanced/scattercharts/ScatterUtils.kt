package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import kotlin.math.pow
import kotlin.math.sqrt

internal data class ScatterStats(
    val observedMinX: Double,
    val observedMaxX: Double,
    val observedMinY: Double,
    val observedMaxY: Double,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val xRange: Double,
    val yRange: Double,
    val pointCount: Int,
    val correlation: Double?
)

internal data class ScatterSelection(
    val point: ChartPoint,
    val index: Int
)

internal fun findScatterSelection(
    points: List<ChartPoint>,
    stats: ScatterStats,
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
    maximumDistance: Float
): ScatterSelection? {
    if (points.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftPadding, rightPadding, topPadding, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f || !maximumDistance.isFinite() || maximumDistance < 0f) return null
    val chartWidth = viewportWidth - leftPadding - rightPadding
    val chartHeight = viewportHeight - topPadding - bottomPadding
    if (tapX !in leftPadding..(leftPadding + chartWidth)) return null
    if (tapY !in topPadding..(topPadding + chartHeight)) return null

    val zoomedWidth = chartWidth * scale.coerceAtLeast(1f)
    val zoomedHeight = chartHeight * scale.coerceAtLeast(1f)
    if (!zoomedWidth.isFinite() || !zoomedHeight.isFinite()) return null
    val clampedX = hardClampScatterOffset(offsetX, chartWidth, zoomedWidth)
    val clampedY = hardClampScatterOffset(offsetY, chartHeight, zoomedHeight)
    val maxDistanceSquared = maximumDistance * maximumDistance

    return points
        .mapIndexedNotNull { index, point ->
            if (!point.x.isFinite() || !point.y.isFinite()) return@mapIndexedNotNull null

            val x =
                leftPadding + clampedX +
                        zoomedWidth * ((point.x - stats.minX) / stats.xRange).toFloat()
            val y =
                topPadding + clampedY + zoomedHeight -
                        zoomedHeight * ((point.y - stats.minY) / stats.yRange).toFloat()
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
        ?.let { ScatterSelection(point = it.third, index = it.second) }
}

internal fun cleanScatterPoints(
    points: List<ChartPoint>
): List<ChartPoint> {
    return points.filter {
        it.x.isFinite() && it.y.isFinite()
    }
}

internal fun computeScatterStats(
    points: List<ChartPoint>
): ScatterStats? {
    val cleanPoints = cleanScatterPoints(points)
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

    val xBounds = paddedScatterBounds(minX, maxX)
    val yBounds = paddedScatterBounds(minY, maxY)
    return ScatterStats(
        observedMinX = minX,
        observedMaxX = maxX,
        observedMinY = minY,
        observedMaxY = maxY,
        minX = xBounds.first,
        maxX = xBounds.second,
        minY = yBounds.first,
        maxY = yBounds.second,
        xRange = safeScatterRange(xBounds.first, xBounds.second),
        yRange = safeScatterRange(yBounds.first, yBounds.second),
        pointCount = cleanPoints.size,
        correlation = pearsonCorrelation(cleanPoints)
    )
}

internal fun paddedScatterBounds(min: Double, max: Double): Pair<Double, Double> {
    val range = max - min
    if (range.isFinite() && range > 0.0) {
        val padding = range * 0.06
        val left = min - padding
        val right = max + padding
        return (if(left.isFinite()) left else min) to (if(right.isFinite()) right else max)
    }
    val centre = if (min.isFinite()) min else 0.0
    val padding = (kotlin.math.abs(centre) * 0.05).coerceAtLeast(1e-3)
    return (centre - padding) to (centre + padding)
}

internal fun safeScatterRange(
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

internal fun formatScatterDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(java.util.Locale.US, value)
    } else {
        "NA"
    }
}

internal fun formatScatterCorrelation(
    value: Double?
): String {
    return if (value != null && value.isFinite()) {
        formatScatterDouble(value, digits = 3)
    } else {
        "NA"
    }
}

internal fun pearsonCorrelation(
    points: List<ChartPoint>
): Double? {
    if (points.size < 2) {
        return null
    }

    val meanX =
        points.map {
            it.x
        }.average()

    val meanY =
        points.map {
            it.y
        }.average()

    var numerator =
        0.0

    var sumX =
        0.0

    var sumY =
        0.0

    points.forEach { point ->
        val dx =
            point.x - meanX

        val dy =
            point.y - meanY

        numerator += dx * dy
        sumX += dx.pow(2.0)
        sumY += dy.pow(2.0)
    }

    val denominator =
        sqrt(sumX * sumY)

    return if (denominator > 0.0 && denominator.isFinite()) {
        numerator / denominator
    } else {
        null
    }
}

internal fun scatterPreviewHeight(
    pointCount: Int
): Int {
    return when {
        pointCount <= 100 -> 220
        pointCount <= 1_000 -> 240
        else -> 260
    }
}

internal fun scatterPointRadiusDp(
    pointCount: Int,
    scale: Float = 1f
): Float {
    val baseRadius =
        when {
            pointCount <= 100 -> 3.0f
            pointCount <= 500 -> 2.2f
            pointCount <= 2_000 -> 1.5f
            pointCount <= 5_000 -> 1.0f
            else -> 0.65f
        }

    val zoomBoost =
        when {
            scale >= 8f -> 2.4f
            scale >= 5f -> 2.0f
            scale >= 3f -> 1.6f
            scale >= 2f -> 1.3f
            else -> 1.0f
        }

    return (baseRadius * zoomBoost)
        .coerceIn(
            minimumValue = baseRadius,
            maximumValue = 4.5f
        )
}

internal fun cleanScatterTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
