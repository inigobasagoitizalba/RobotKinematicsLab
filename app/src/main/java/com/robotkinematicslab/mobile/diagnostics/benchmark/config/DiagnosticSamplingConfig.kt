package com.robotkinematicslab.mobile.diagnostics.benchmark.config

data class DiagnosticSamplingConfig(
    val reachableCount: Int = 4,
    val unreachableCount: Int = 4,
    val runCount: Int = 100,
    val samplesPerLinkCount: Int = 500,
    val unlimitedSamplesEnabled: Boolean = false,
    val unlimitedSampleCount: Int = 5000
)
