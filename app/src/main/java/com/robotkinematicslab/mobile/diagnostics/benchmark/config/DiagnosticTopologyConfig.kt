package com.robotkinematicslab.mobile.diagnostics.benchmark.config

data class DiagnosticTopologyConfig(
    val robotLinkCount: Int = 3,
    val minLinkCount: Int = 2,
    val maxLinkCount: Int = 10,
    val jointMode: DiagnosticJointMode = DiagnosticJointMode.AUTO,
    val runAllTopologies: Boolean = false,
    val stressLevel: Double = 0.5,
    val experimentalModeEnabled: Boolean = false
)
