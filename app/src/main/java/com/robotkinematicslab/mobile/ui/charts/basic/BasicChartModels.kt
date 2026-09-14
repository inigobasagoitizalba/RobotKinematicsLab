package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.ui.graphics.Color

data class ChartSlice(
    val label: String,
    val value: Int,
    val color: Color
)

data class ChartBarItem(
    val label: String,
    val value: Int,
    val color: Color
)

data class ChartDoubleBarItem(
    val label: String,
    val value: Double,
    val displayValue: String,
    val color: Color
)

data class ChartRatioItem(
    val label: String,
    val ratio: Double,
    val displayValue: String,
    val color: Color
)