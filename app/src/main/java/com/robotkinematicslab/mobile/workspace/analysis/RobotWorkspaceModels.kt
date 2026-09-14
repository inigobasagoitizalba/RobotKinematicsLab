package com.robotkinematicslab.mobile.workspace.analysis

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Vec3

enum class WorkspaceSamplingProtocol(val protocolId: String) {
    SHIFTED_HALTON_V1("rkl-workspace-shifted-halton-v1")
}

enum class WorkspaceSampleKind {
    HOME,
    SINGLE_JOINT_LIMIT,
    JOINT_LIMIT_CORNER,
    QUASI_RANDOM
}

enum class WorkspaceVoxelClass {
    OBSERVED_REACHABLE_CORE,
    OBSERVED_REACHABLE_BOUNDARY,
    UNOBSERVED_CANDIDATE
}

enum class WorkspaceAnalysisPhase {
    VALIDATING_ROBOT,
    BUILDING_SAMPLE_SEQUENCE,
    RUNNING_FORWARD_KINEMATICS,
    BUILDING_VOXELS,
    CLASSIFYING_SPACE,
    FINALIZING,
    COMPLETED
}

data class RobotWorkspaceAnalysisConfig(
    val sampleCount: Int = 8_192,
    val randomSeed: Int = 42,
    val voxelResolution: Int = 20,
    val replicationCount: Int = 4,
    val samplingProtocol: WorkspaceSamplingProtocol = WorkspaceSamplingProtocol.SHIFTED_HALTON_V1
) {
    init {
        require(sampleCount in MINIMUM_SAMPLE_COUNT..MAXIMUM_SAMPLE_COUNT)
        require(voxelResolution in MINIMUM_VOXEL_RESOLUTION..MAXIMUM_VOXEL_RESOLUTION)
        require(replicationCount in 1..MAXIMUM_REPLICATION_COUNT)
        require(replicationCount <= sampleCount)
    }

    companion object {
        const val MINIMUM_SAMPLE_COUNT = 256
        const val MAXIMUM_SAMPLE_COUNT = 50_000
        const val MINIMUM_VOXEL_RESOLUTION = 10
        const val MAXIMUM_VOXEL_RESOLUTION = 32
        const val MAXIMUM_REPLICATION_COUNT = 8
    }
}

data class RobotWorkspaceAnalysisProgress(
    val phase: WorkspaceAnalysisPhase,
    val completedWorkUnits: Int,
    val totalWorkUnits: Int,
    val message: String
) {
    val fraction: Double
        get() =
            if (totalWorkUnits > 0) {
                completedWorkUnits.toDouble().div(totalWorkUnits).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
}

data class RobotWorkspaceSample(
    val sequenceIndex: Int,
    val replicationIndex: Int,
    val kind: WorkspaceSampleKind,
    val jointValues: List<Double>,
    val endEffector: Vec3
) {
    init {
        require(sequenceIndex >= 0)
        require(replicationIndex >= -1)
        require(jointValues.isNotEmpty() && jointValues.all(Double::isFinite))
        require(endEffector.x.isFinite() && endEffector.y.isFinite() && endEffector.z.isFinite())
    }
}

data class RobotWorkspaceVoxel(
    val xIndex: Int,
    val yIndex: Int,
    val zIndex: Int,
    val center: Vec3,
    val classification: WorkspaceVoxelClass,
    val sampleHitCount: Int,
    val firstObservedSampleIndex: Int?
) {
    init {
        require(xIndex >= 0 && yIndex >= 0 && zIndex >= 0)
        require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite())
        require(sampleHitCount >= 0)
        require(firstObservedSampleIndex == null || firstObservedSampleIndex >= 0)
        require(
            (classification == WorkspaceVoxelClass.UNOBSERVED_CANDIDATE) ==
                (sampleHitCount == 0 && firstObservedSampleIndex == null)
        )
    }

    val id: String
        get() = "$xIndex:$yIndex:$zIndex"
}

data class WorkspaceConvergenceCheckpoint(
    val sampleCount: Int,
    val occupiedVoxelCount: Int,
    val observedVoxelVolumeCubicMeters: Double
) {
    init {
        require(sampleCount > 0)
        require(occupiedVoxelCount >= 0)
        require(observedVoxelVolumeCubicMeters.isFinite() && observedVoxelVolumeCubicMeters >= 0.0)
    }
}

data class RobotWorkspaceStudy(
    val studyId: String,
    val studyName: String,
    val createdAtEpochMillis: Long,
    val robotFingerprint: String,
    val robot: RobotDefinition,
    val config: RobotWorkspaceAnalysisConfig,
    val workerCount: Int,
    val durationMillis: Long,
    val conservativeRadiusMeters: Double,
    val voxelCellSizeMeters: Double,
    val conservativeSphereVolumeCubicMeters: Double,
    val classifiedEnvelopeVolumeCubicMeters: Double,
    val observedVoxelVolumeCubicMeters: Double,
    val unobservedCandidateVolumeCubicMeters: Double,
    val observedEnvelopeFraction: Double,
    val lastQuarterRelativeVolumeGain: Double,
    val invalidSampleCount: Int,
    val samples: List<RobotWorkspaceSample>,
    val voxels: List<RobotWorkspaceVoxel>,
    val convergence: List<WorkspaceConvergenceCheckpoint>
) {
    init {
        require(studyId.isNotBlank() && studyName.isNotBlank() && robotFingerprint.isNotBlank())
        require(createdAtEpochMillis >= 0L)
        require(workerCount > 0 && durationMillis >= 0L)
        require(conservativeRadiusMeters.isFinite() && conservativeRadiusMeters > 0.0)
        require(voxelCellSizeMeters.isFinite() && voxelCellSizeMeters > 0.0)
        require(conservativeSphereVolumeCubicMeters.isFinite() && conservativeSphereVolumeCubicMeters > 0.0)
        require(classifiedEnvelopeVolumeCubicMeters.isFinite() && classifiedEnvelopeVolumeCubicMeters > 0.0)
        require(observedVoxelVolumeCubicMeters.isFinite() && observedVoxelVolumeCubicMeters >= 0.0)
        require(unobservedCandidateVolumeCubicMeters.isFinite() && unobservedCandidateVolumeCubicMeters >= 0.0)
        require(observedEnvelopeFraction.isFinite() && observedEnvelopeFraction in 0.0..1.0)
        require(lastQuarterRelativeVolumeGain.isFinite() && lastQuarterRelativeVolumeGain >= 0.0)
        require(invalidSampleCount >= 0)
        require(samples.size + invalidSampleCount == config.sampleCount)
        require(samples.map(RobotWorkspaceSample::sequenceIndex).distinct().size == samples.size)
        require(voxels.isNotEmpty())
        require(convergence.isNotEmpty())
    }

    val reachableCoreVoxelCount: Int
        get() = voxels.count { it.classification == WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE }

    val reachableBoundaryVoxelCount: Int
        get() = voxels.count { it.classification == WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY }

    val unobservedCandidateVoxelCount: Int
        get() = voxels.count { it.classification == WorkspaceVoxelClass.UNOBSERVED_CANDIDATE }
}

data class RobotWorkspaceStudySummary(
    val studyId: String,
    val studyName: String,
    val robotName: String,
    val robotFingerprint: String,
    val createdAtEpochMillis: Long,
    val sampleCount: Int,
    val voxelResolution: Int,
    val observedEnvelopeFraction: Double,
    val directoryPath: String,
    val dataPath: String
)

