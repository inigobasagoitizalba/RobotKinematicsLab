package com.robotkinematicslab.mobile.workspace.analysis

import kotlin.math.floor

data class WorkspaceReplicationCoverage(
    val replicationIndex: Int,
    val evaluatedSampleCount: Int,
    val occupiedVoxelCount: Int,
    val observedVoxelVolumeCubicMeters: Double
)

/**
 * Rebuilds one comparable coverage estimate per randomized QMC replication.
 * Deterministic probes are shared by every replication; shifted-Halton samples remain independent.
 */
fun workspaceReplicationCoverage(study: RobotWorkspaceStudy): List<WorkspaceReplicationCoverage> {
    val fixedSamples = study.samples.filter { it.replicationIndex < 0 }
    return List(study.config.replicationCount) { replication ->
        val replicationSamples = study.samples.filter { it.replicationIndex == replication }
        val occupied = hashSetOf<String>()
        (fixedSamples + replicationSamples).forEach { sample ->
            val x = workspaceEvidenceBin(sample.endEffector.x, study)
            val y = workspaceEvidenceBin(sample.endEffector.y, study)
            val z = workspaceEvidenceBin(sample.endEffector.z, study)
            occupied += "$x:$y:$z"
        }
        WorkspaceReplicationCoverage(
            replicationIndex = replication,
            evaluatedSampleCount = fixedSamples.size + replicationSamples.size,
            occupiedVoxelCount = occupied.size,
            observedVoxelVolumeCubicMeters = occupied.size * cube(study.voxelCellSizeMeters)
        )
    }
}

fun workspaceReplicationRelativeRange(study: RobotWorkspaceStudy): Double? {
    val values = workspaceReplicationCoverage(study).map { it.observedVoxelVolumeCubicMeters }
    if (values.size < 2) return null
    val mean = values.average()
    if (!mean.isFinite() || mean <= 0.0) return null
    return ((values.maxOrNull() ?: return null) - (values.minOrNull() ?: return null)) / mean
}

private fun workspaceEvidenceBin(value: Double, study: RobotWorkspaceStudy): Int =
    floor((value + study.conservativeRadiusMeters) / study.voxelCellSizeMeters)
        .toInt()
        .coerceIn(0, study.config.voxelResolution - 1)

private fun cube(value: Double): Double = value * value * value
