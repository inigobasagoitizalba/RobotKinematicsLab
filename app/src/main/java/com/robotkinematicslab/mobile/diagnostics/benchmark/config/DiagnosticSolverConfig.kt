package com.robotkinematicslab.mobile.diagnostics.benchmark.config

data class DiagnosticSolverConfig(
    val ikMaxIterations: Int = 200,
    val ikTolerance: Double = 0.00001,
    val ikDamping: Double = 0.08,
    val ikMaxStep: Double = 0.01
)
