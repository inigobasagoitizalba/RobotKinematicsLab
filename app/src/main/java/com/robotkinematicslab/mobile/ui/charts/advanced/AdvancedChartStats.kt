package com.robotkinematicslab.mobile.ui.charts.advanced

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

fun buildProfessionalHistogramBins(
    values: List<Double>,
    binCount: Int
): List<ProfessionalHistogramBin> {
    val cleanValues =
        values.filter {
            it.isFinite()
        }

    if (cleanValues.isEmpty()) {
        return emptyList()
    }

    val minValue =
        cleanValues.minOrNull() ?: return emptyList()

    val maxValue =
        cleanValues.maxOrNull() ?: return emptyList()

    if (minValue == maxValue) {
        return listOf(
            ProfessionalHistogramBin(
                start = minValue,
                end = maxValue,
                count = cleanValues.size
            )
        )
    }

    val safeBinCount = binCount.coerceAtLeast(1)

    val width =
        (maxValue - minValue) / safeBinCount.toDouble()

    return (0 until safeBinCount).map { index ->
        val start =
            minValue + width * index

        val end =
            if (index == safeBinCount - 1) {
                maxValue
            } else {
                start + width
            }

        val count =
            cleanValues.count { value ->
                if (index == safeBinCount - 1) {
                    value >= start && value <= end
                } else {
                    value >= start && value < end
                }
            }

        ProfessionalHistogramBin(
            start = start,
            end = end,
            count = count
        )
    }
}

fun boxStats(
    values: List<Double>
): ProfessionalBoxStats {
    val sorted =
        values
            .filter {
                it.isFinite()
            }
            .sorted()

    if (sorted.isEmpty()) {
        return ProfessionalBoxStats(
            min = 0.0,
            q1 = 0.0,
            median = 0.0,
            q3 = 0.0,
            max = 0.0
        )
    }

    return ProfessionalBoxStats(
        min = sorted.first(),
        q1 = percentile(sorted, 0.25),
        median = percentile(sorted, 0.50),
        q3 = percentile(sorted, 0.75),
        max = sorted.last()
    )
}

fun percentile(
    sortedValues: List<Double>,
    ratio: Double
): Double {
    if (sortedValues.isEmpty()) {
        return 0.0
    }

    if (sortedValues.size == 1) {
        return sortedValues.first()
    }

    val safeRatio = ratio.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.5
    val index =
        safeRatio * (sortedValues.size - 1).toDouble()

    val lowerIndex =
        floor(index).toInt()

    val upperIndex =
        ceil(index).toInt()

    if (lowerIndex == upperIndex) {
        return sortedValues[lowerIndex]
    }

    val weight =
        index - lowerIndex.toDouble()

    return sortedValues[lowerIndex] * (1.0 - weight) +
            sortedValues[upperIndex] * weight
}

fun safeRange(
    minValue: Double,
    maxValue: Double
): Double {
    val range =
        maxValue - minValue

    return if (range.isFinite() && range > 1e-9) {
        range
    } else {
        1.0
    }
}

fun safeLogValue(
    value: Double
): Double {
    return if (value.isFinite() && value > 0.0) {
        ln(value)
    } else {
        ln(1e-12)
    }
}

fun compactProfessionalLabels(
    labels: List<String>
): String {
    return if (labels.size <= 5) {
        labels.joinToString()
    } else {
        labels.take(5).joinToString() + " +${labels.size - 5} more"
    }
}
