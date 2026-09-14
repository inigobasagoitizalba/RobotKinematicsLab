package com.robotkinematicslab.mobile.ui.charts.advanced

import androidx.compose.ui.graphics.Color

data class ChartPoint(
    val x: Double,
    val y: Double,
    val color: Color,
    val label: String = ""
)

data class ChartLinePoint(
    val x: Double,
    val y: Double,
    val breakBefore: Boolean = false,
    val label: String = ""
)

data class ChartHeatMapCell(
    val row: String,
    val column: String,
    val value: Double,
    val displayValue: String,
    val color: Color
)

data class ChartTimelineCell(
    val label: String,
    val color: Color
)

data class ChartBoxPlotItem(
    val label: String,
    val values: List<Double>,
    val color: Color
)

data class ProfessionalHistogramBin(
    val start: Double,
    val end: Double,
    val count: Int
)

data class ProfessionalBoxStats(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double
)

internal fun hasUsableChartViewport(
    viewportWidth: Float,
    viewportHeight: Float,
    leftPadding: Float,
    rightPadding: Float,
    topPadding: Float,
    bottomPadding: Float
): Boolean {
    val dimensions =
        listOf(
            viewportWidth,
            viewportHeight,
            leftPadding,
            rightPadding,
            topPadding,
            bottomPadding
        )
    if (dimensions.any { !it.isFinite() } || dimensions.drop(2).any { it < 0f }) return false
    return viewportWidth > leftPadding + rightPadding &&
        viewportHeight > topPadding + bottomPadding
}

internal fun safeChartScale(
    scale: Float,
    maximumScale: Float
): Float {
    if (!maximumScale.isFinite() || maximumScale < 1f) return 1f
    return if (scale.isFinite()) scale.coerceIn(1f, maximumScale) else 1f
}
