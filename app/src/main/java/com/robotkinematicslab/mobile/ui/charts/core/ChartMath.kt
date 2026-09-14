package com.robotkinematicslab.mobile.ui.charts.core

import java.util.Locale

fun safeRatio(
    numerator: Int,
    denominator: Int
): Double {
    return if (denominator <= 0) {
        0.0
    } else {
        numerator.toDouble() / denominator.toDouble()
    }
}

fun formatChartDouble(
    value: Double,
    digits: Int = 4
): String {
    return if (value.isFinite()) {
        "%.${digits}f".format(Locale.US, value)
    } else {
        "NA"
    }
}

fun formatChartPercent(
    value: Double
): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.2f%%", value * 100.0)
    } else {
        "NA"
    }
}
