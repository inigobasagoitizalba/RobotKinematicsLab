package com.robotkinematicslab.mobile.domain.config

data class IKConfig(
    val maxIterations: Int = 800,
    val tolerance: Double = 1e-5,
    val damping: Double = 0.05,
    val maxStep: Double = 0.02
)
