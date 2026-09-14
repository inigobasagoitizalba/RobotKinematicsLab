package com.robotkinematicslab.mobile.domain.record

data class MLRecord(
    val input: List<Double>,
    val output: List<Double>,
    val status: Int,
    val error: Double,
    val iterations: Int
)
