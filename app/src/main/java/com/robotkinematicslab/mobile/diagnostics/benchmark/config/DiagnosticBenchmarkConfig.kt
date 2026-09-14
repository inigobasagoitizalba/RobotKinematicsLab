package com.robotkinematicslab.mobile.diagnostics.benchmark.config

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy

data class DiagnosticBenchmarkConfig(
    val sampling: DiagnosticSamplingConfig = DiagnosticSamplingConfig(),
    val seeds: DiagnosticSeedConfig = DiagnosticSeedConfig(),
    val topology: DiagnosticTopologyConfig = DiagnosticTopologyConfig(),
    val solver: DiagnosticSolverConfig = DiagnosticSolverConfig(),
    val performance: DiagnosticPerformanceConfig = DiagnosticPerformanceConfig(),
    val metricPolicy: DiagnosticMetricPolicy = DiagnosticMetricPolicy()
)
