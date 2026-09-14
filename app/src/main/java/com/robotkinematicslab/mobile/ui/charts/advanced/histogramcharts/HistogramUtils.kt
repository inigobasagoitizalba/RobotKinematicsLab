package com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts

import com.robotkinematicslab.mobile.ui.charts.advanced.hasUsableChartViewport
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal data class HistogramBin(
    val index: Int,
    val start: Double,
    val end: Double,
    val count: Int
) {
    val center: Double
        get() = (start + end) / 2.0

    val label: String
        get() = "${formatHistogramDouble(start)} → ${formatHistogramDouble(end)}"
}

internal data class HistogramStats(
    val min: Double,
    val max: Double,
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val sampleCount: Int,
    val binCount: Int,
    val maxBinCount: Int,
    val peakBin: HistogramBin
)

internal data class HistogramSelection(
    val bin: HistogramBin,
    val index: Int
)

internal fun findHistogramSelection(
    bins: List<HistogramBin>,
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
): HistogramSelection? {
    if (bins.isEmpty()) return null
    if (!hasUsableChartViewport(viewportWidth, viewportHeight, leftPadding, rightPadding, topPadding, bottomPadding)) return null
    if (!scale.isFinite() || scale <= 0f) return null

    val chartWidth = viewportWidth - leftPadding - rightPadding
    val chartHeight = viewportHeight - topPadding - bottomPadding
    if (tapX !in leftPadding..(leftPadding + chartWidth)) return null
    if (tapY !in topPadding..(topPadding + chartHeight)) return null

    val zoomedWidth = chartWidth * scale.coerceAtLeast(1f)
    if (!zoomedWidth.isFinite()) return null
    val clampedX = hardClampHistogramOffset(offsetX, chartWidth, zoomedWidth)
    val rawIndex = ((tapX - leftPadding - clampedX) / (zoomedWidth / bins.size)).toInt()
    if (rawIndex !in bins.indices) return null

    return HistogramSelection(bin = bins[rawIndex], index = rawIndex)
}

internal fun cleanHistogramValues(
    values: List<Double>,
    logScale: Boolean
): List<Double> {
    return values
        .filter {
            it.isFinite()
        }
        .map {
            if (logScale) {
                safeHistogramLogValue(it)
            } else {
                it
            }
        }
        .filter {
            it.isFinite()
        }
}

internal fun buildHistogramBins(
    values: List<Double>,
    requestedBinCount: Int
): List<HistogramBin> {
    val cleanValues = values.filter(Double::isFinite)
    if (cleanValues.isEmpty()) {
        return emptyList()
    }

    val binCount =
        requestedBinCount.coerceAtLeast(1)

    val min =
        cleanValues.minOrNull() ?: 0.0

    val max =
        cleanValues.maxOrNull() ?: min

    val range =
        safeHistogramRange(
            min = min,
            max = max
        )

    val step =
        range / binCount.toDouble()

    val counts =
        IntArray(binCount)

    cleanValues.forEach { value ->
        val rawIndex =
            ((value - min) / step).toInt()

        val index =
            rawIndex.coerceIn(
                minimumValue = 0,
                maximumValue = binCount - 1
            )

        counts[index] += 1
    }

    return List(binCount) { index ->
        val start =
            min + step * index.toDouble()

        val end =
            if (index == binCount - 1) {
                max
            } else {
                min + step * (index + 1).toDouble()
            }

        HistogramBin(
            index = index,
            start = start,
            end = end,
            count = counts[index]
        )
    }
}

internal fun computeHistogramStats(
    values: List<Double>,
    bins: List<HistogramBin>
): HistogramStats? {
    val sortedValues = values.filter(Double::isFinite).sorted()
    if (sortedValues.isEmpty() || bins.isEmpty()) {
        return null
    }

    val min =
        sortedValues.first()

    val max =
        sortedValues.last()

    val mean =
        sortedValues.average()

    val median =
        if (sortedValues.size % 2 == 0) {
            val rightIndex =
                sortedValues.size / 2

            val leftIndex =
                rightIndex - 1

            (sortedValues[leftIndex] + sortedValues[rightIndex]) / 2.0
        } else {
            sortedValues[sortedValues.size / 2]
        }

    val variance =
        sortedValues
            .map {
                (it - mean).pow(2.0)
            }
            .average()

    val standardDeviation =
        sqrt(variance)

    val peakBin =
        bins.maxByOrNull {
            it.count
        } ?: bins.first()

    return HistogramStats(
        min = min,
        max = max,
        mean = mean,
        median = median,
        standardDeviation = standardDeviation,
        sampleCount = sortedValues.size,
        binCount = bins.size,
        maxBinCount = peakBin.count.coerceAtLeast(1),
        peakBin = peakBin
    )
}

internal fun safeHistogramRange(
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

internal fun safeHistogramLogValue(
    value: Double
): Double {
    return if (value > 0.0 && value.isFinite()) {
        ln(value)
    } else {
        Double.NaN
    }
}

internal fun histogramPreviewHeight(
    sampleCount: Int,
    binCount: Int
): Int {
    return when {
        binCount <= 8 && sampleCount <= 100 -> 210
        binCount <= 16 -> 230
        else -> 250
    }
}

internal fun histogramBarCornerDp(
    visibleBinCount: Int
): Float {
    return when {
        visibleBinCount <= 10 -> 4f
        visibleBinCount <= 25 -> 3f
        else -> 1.5f
    }
}

internal fun formatHistogramDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

internal fun compactHistogramAxisValue(
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

internal fun cleanHistogramTitle(
    title: String
): String {
    return title
        .replace(Regex("^\\p{So}+\\s*"), "")
        .trim()
}
