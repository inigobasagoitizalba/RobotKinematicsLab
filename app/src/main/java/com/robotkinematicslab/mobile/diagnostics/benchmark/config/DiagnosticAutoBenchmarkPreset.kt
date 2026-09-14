package com.robotkinematicslab.mobile.diagnostics.benchmark.config

object DiagnosticAutoBenchmarkPreset {

    const val DEFAULT_REACHABLE_COUNT = 4
    const val DEFAULT_UNREACHABLE_COUNT = 4
    const val DEFAULT_ROBOT_LINK_COUNT = 3
    const val DEFAULT_MIN_LINK_COUNT = 2
    const val DEFAULT_MAX_LINK_COUNT = 10
    const val DEFAULT_SAMPLES_PER_LINK_COUNT = 500
    const val DEFAULT_UNLIMITED_SAMPLE_COUNT = 5000
    const val DEFAULT_STRESS_LEVEL = 0.5

    const val DEFAULT_IK_MAX_ITERATIONS = 800
    const val DEFAULT_IK_TOLERANCE = 0.00001
    const val DEFAULT_IK_DAMPING = 0.05
    const val DEFAULT_IK_MAX_STEP = 0.02

    val DEFAULT_SEEDS =
        listOf(
            42,
            101,
            202,
            303,
            404
        )

    fun buildConfig(): DiagnosticBenchmarkConfig {
        return DiagnosticBenchmarkConfig(
            sampling =
                DiagnosticSamplingConfig(
                    reachableCount = DEFAULT_REACHABLE_COUNT,
                    unreachableCount = DEFAULT_UNREACHABLE_COUNT,
                    runCount = DEFAULT_SAMPLES_PER_LINK_COUNT,
                    samplesPerLinkCount = DEFAULT_SAMPLES_PER_LINK_COUNT,
                    unlimitedSamplesEnabled = false,
                    unlimitedSampleCount = DEFAULT_UNLIMITED_SAMPLE_COUNT
                ),

            seeds =
                DiagnosticSeedConfig(
                    seeds = DEFAULT_SEEDS
                ),

            topology =
                DiagnosticTopologyConfig(
                    robotLinkCount = DEFAULT_ROBOT_LINK_COUNT,
                    minLinkCount = DEFAULT_MIN_LINK_COUNT,
                    maxLinkCount = DEFAULT_MAX_LINK_COUNT,
                    jointMode = DiagnosticJointMode.MIXED,
                    runAllTopologies = false,
                    stressLevel = DEFAULT_STRESS_LEVEL,
                    experimentalModeEnabled = false
                ),

            solver =
                DiagnosticSolverConfig(
                    ikMaxIterations = DEFAULT_IK_MAX_ITERATIONS,
                    ikTolerance = DEFAULT_IK_TOLERANCE,
                    ikDamping = DEFAULT_IK_DAMPING,
                    ikMaxStep = DEFAULT_IK_MAX_STEP
                )
        )
    }

    fun buildSummaryText(): String {
        return "Auto benchmark preset applied: safe links 2–10, " +
                "500 samples per link, mixed topology, balanced solver, deterministic seeds."
    }
}
