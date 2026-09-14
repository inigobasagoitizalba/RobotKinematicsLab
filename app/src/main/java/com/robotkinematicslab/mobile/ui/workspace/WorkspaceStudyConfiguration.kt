package com.robotkinematicslab.mobile.ui.workspace

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import java.util.Locale

internal enum class WorkspaceQualityPreset(
    val label: String, val samples: Int, val resolution: Int, val replications: Int,
    val explanation: String
) {
    QUICK("Quick", 2_048, 16, 2, "Fewer joint states and a coarser Cartesian grid for initial exploration."),
    BALANCED("Balanced", 8_192, 20, 4, "More joint states and replications with an intermediate grid."),
    RESEARCH("Research", 32_768, 28, 8, "The largest preset sample budget and finest preset grid; more computation, without a quality guarantee.");

    fun configuration(seed: Int) = RobotWorkspaceAnalysisConfig(samples, seed, resolution, replications)
    val exactSummary: String get() = "$samples samples · $resolution³ grid · $replications replications"
}

internal fun workspaceConfiguration(samples: String, resolution: String, replications: String, seed: String) =
    RobotWorkspaceAnalysisConfig(
        sampleCount = requireNotNull(samples.toIntOrNull()) { "Samples must be a whole number." },
        voxelResolution = requireNotNull(resolution.toIntOrNull()) { "Resolution must be a whole number." },
        replicationCount = requireNotNull(replications.toIntOrNull()) { "Replications must be a whole number." },
        randomSeed = requireNotNull(seed.toIntOrNull()) { "Seed must be a signed 32-bit whole number." }
    )

internal fun workspaceWorkSummary(robot: RobotDefinition, config: RobotWorkspaceAnalysisConfig, requestedWorkers: Int): String {
    val radius = RobotReachEnvelope.conservativeRadialUpperBound(robot)
    val n = config.voxelResolution
    val edge = String.format(Locale.US, "%.5g", 2.0 * radius / n)
    val effective = RobotWorkspaceAnalyzer.effectiveWorkerCount(requestedWorkers, config.sampleCount)
    return "${robot.name} · ${robot.joints.size} joints · ${config.sampleCount} FK evaluations total (replications share this budget) · " +
        "$n × $n × $n = ${n * n * n} Cartesian grid cells before envelope filtering · cell edge $edge m · " +
        "${config.replicationCount} replications · seed ${config.randomSeed} · workers requested by device policy $requestedWorkers / effective $effective. " +
        "These are work quantities, not a duration estimate."
}
