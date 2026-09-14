package com.robotkinematicslab.mobile.domain

data class JointDefinition(
    val name: String,
    val type: JointType,
    val minValue: Double,
    val maxValue: Double,
    val homeValue: Double
)
