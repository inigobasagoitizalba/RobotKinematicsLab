package com.robotkinematicslab.mobile.ui.charts.presentation

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

enum class ScientificMetricFormat(val unit: String) {
    COUNT("count"), RATIO("%"), BASIS_POINTS("%"), NANOSECONDS("µs"), METRES("µm"), BYTES("MiB"), DECIMAL("raw");
    fun format(raw: Double): String {
        if(!raw.isFinite()) return "Unavailable"
        if(this == RATIO && raw !in 0.0..1.0 || this == BASIS_POINTS && raw !in 0.0..10000.0) return "Invalid ratio"
        val value = when(this) { RATIO -> raw * 100; BASIS_POINTS -> raw / 100; NANOSECONDS -> raw / 1000; METRES -> raw * 1e6; BYTES -> raw / 1048576; else -> raw }
        return when(this) {
            COUNT -> if(raw % 1.0 == 0.0) String.format(Locale.US,"%.0f",raw) else "Invalid count"
            RATIO, BASIS_POINTS -> String.format(Locale.US,"%.2f%%",value)
            DECIMAL -> String.format(Locale.US,"%.4g",value)
            else -> String.format(Locale.US,"%.4g %s",value,unit)
        }
    }
    fun rawDetails(raw: Double) = if(raw.isFinite()) raw.toString() else "Unavailable"
}
val LocalScientificMetricFormat = compositionLocalOf { ScientificMetricFormat.COUNT }
internal data class MetricDifference(val absolute: Double, val percentagePoints: Double, val relativePercent: Double?)
internal fun ratioDifference(reference: Double, current: Double): MetricDifference? {
    if(!reference.isFinite() || !current.isFinite() || reference !in 0.0..1.0 || current !in 0.0..1.0) return null
    val delta = current - reference
    return MetricDifference(delta, delta * 100, if(reference == 0.0) null else delta / reference * 100)
}
