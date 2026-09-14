package com.robotkinematicslab.mobile.workspace.storage

import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import com.robotkinematicslab.mobile.workspace.analysis.robotWorkspaceFingerprint

data class RobotWorkspaceProvisionResult(
    val summary: RobotWorkspaceStudySummary,
    val created: Boolean
)

/**
 * Gives every saved robot one reproducible baseline workspace without duplicating the data used by
 * point-cloud, surface-shell and animated-construction views. The robot definition fingerprint is
 * the ownership key: changing geometry or limits creates a new immutable evidence version.
 */
class RobotWorkspaceAutoProvisioner(
    private val repository: RobotWorkspaceStudyRepository,
    private val analyzer: RobotWorkspaceAnalyzer = RobotWorkspaceAnalyzer(),
    private val baselineConfig: RobotWorkspaceAnalysisConfig = BASELINE_CONFIG
) {

    fun ensureBaseline(
        savedRobot: SavedRobot,
        requestedWorkerCount: Int
    ): RobotWorkspaceProvisionResult =
        synchronized(PROVISION_LOCK) {
            val fingerprint = robotWorkspaceFingerprint(savedRobot.robot)
            repository.listStudies()
                .firstOrNull { it.robotFingerprint == fingerprint }
                ?.let { existing ->
                    return@synchronized RobotWorkspaceProvisionResult(existing, created = false)
                }

            val study =
                analyzer.analyze(
                    robot = savedRobot.robot,
                    config = baselineConfig,
                    requestedWorkerCount = requestedWorkerCount.coerceAtLeast(1)
                )
            RobotWorkspaceProvisionResult(
                summary = repository.save(study),
                created = true
            )
        }

    companion object {
        /** Quick is deliberate: users can replace it with a Balanced or Research study later. */
        val BASELINE_CONFIG =
            RobotWorkspaceAnalysisConfig(
                sampleCount = 2_048,
                randomSeed = 42,
                voxelResolution = 16,
                replicationCount = 2
            )

        private val PROVISION_LOCK = Any()
    }
}
