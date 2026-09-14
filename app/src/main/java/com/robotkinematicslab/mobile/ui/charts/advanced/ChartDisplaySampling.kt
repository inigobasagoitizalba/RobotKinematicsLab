package com.robotkinematicslab.mobile.ui.charts.advanced

internal fun <T> sampleForResponsiveChartDisplay(
    values: List<T>,
    maximumSize: Int = MAX_CHART_DISPLAY_ITEMS
): List<T> {
    require(maximumSize > 0) { "maximumSize must be positive." }
    return values
}

internal fun <T> selectPeakPreservingChartPreview(
    values: List<T>,
    maximumSize: Int = MAX_CHART_DISPLAY_ITEMS,
    valueSelector: (T) -> Double
): List<T> {
    require(maximumSize > 0) { "maximumSize must be positive." }
    if (values.size <= maximumSize) {
        return values
    }

    if (maximumSize == 1) {
        return listOf(values.first())
    }

    if (maximumSize == 2) {
        return listOf(values.first(), values.last())
    }

    // A compact preview has fewer horizontal pixels than many scientific series have samples. Preserve the
    // local low/high envelope instead of taking evenly spaced indexes: uniform sampling can erase a
    // short spike completely and make a materially different run look flat. Inspectors and figure
    // capture still receive the original list; this is only the compact-card level of detail.
    val interiorBudget = maximumSize - 2
    val pairBucketCount = interiorBudget / 2
    val selectedIndexes = ArrayList<Int>(maximumSize)
    selectedIndexes += 0

    if (pairBucketCount > 0) {
        repeat(pairBucketCount) { bucketIndex ->
            val start = 1 + bucketIndex * (values.size - 2) / pairBucketCount
            val endExclusive = 1 + (bucketIndex + 1) * (values.size - 2) / pairBucketCount
            if (start >= endExclusive) return@repeat

            var minimumIndex = start
            var maximumIndex = start
            var minimumValue = valueSelector(values[start])
            var maximumValue = minimumValue
            for (index in (start + 1) until endExclusive) {
                val currentValue = valueSelector(values[index])
                if (!currentValue.isFinite()) continue
                if (!minimumValue.isFinite() || currentValue < minimumValue) {
                    minimumValue = currentValue
                    minimumIndex = index
                }
                if (!maximumValue.isFinite() || currentValue > maximumValue) {
                    maximumValue = currentValue
                    maximumIndex = index
                }
            }
            if (minimumIndex <= maximumIndex) {
                selectedIndexes += minimumIndex
                if (maximumIndex != minimumIndex) selectedIndexes += maximumIndex
            } else {
                selectedIndexes += maximumIndex
                selectedIndexes += minimumIndex
            }
        }
    }

    if (interiorBudget % 2 != 0 && values.size > 2) {
        selectedIndexes += values.lastIndex / 2
    }
    selectedIndexes += values.lastIndex

    return selectedIndexes
        .distinct()
        .sorted()
        .take(maximumSize)
        .map(values::get)
}

private const val MAX_CHART_DISPLAY_ITEMS = 4_000
