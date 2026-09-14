package com.robotkinematicslab.mobile.workspace.analysis

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotWorkspaceAnalyzerTest {

    @Test
    fun sameSeedIsDeterministicAcrossWorkerCounts() {
        val config = RobotWorkspaceAnalysisConfig(sampleCount = 512, randomSeed = 917, voxelResolution = 14, replicationCount = 4)

        val serial = RobotWorkspaceAnalyzer(clockMillis = { 100L }).analyze(robot(), config, requestedWorkerCount = 1)
        val parallel = RobotWorkspaceAnalyzer(clockMillis = { 100L }).analyze(robot(), config, requestedWorkerCount = 4)

        assertEquals(serial.samples, parallel.samples)
        assertEquals(serial.voxels, parallel.voxels)
        assertEquals(serial.convergence, parallel.convergence)
        assertEquals(serial.observedVoxelVolumeCubicMeters, parallel.observedVoxelVolumeCubicMeters, 0.0)
    }

    @Test
    fun largerStudyPreservesTheCompleteSmallerSequencePrefix() {
        val small = RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 42, voxelResolution = 12, replicationCount = 2)
        )
        val large = RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 512, randomSeed = 42, voxelResolution = 12, replicationCount = 2)
        )

        assertEquals(small.samples, large.samples.take(small.samples.size))
    }

    @Test
    fun differentSeedChangesQuasiRandomStatesButNotFixedProbes() {
        val first = RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 1, voxelResolution = 12, replicationCount = 2)
        )
        val second = RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 2, voxelResolution = 12, replicationCount = 2)
        )

        val deterministicHomeAndSingleLimitProbeCount = 1 + robot().joints.size * 2
        val firstQmcIndex = first.samples.indexOfFirst { it.kind == WorkspaceSampleKind.QUASI_RANDOM }
        assertTrue(firstQmcIndex > deterministicHomeAndSingleLimitProbeCount)
        assertEquals(
            first.samples.take(deterministicHomeAndSingleLimitProbeCount),
            second.samples.take(deterministicHomeAndSingleLimitProbeCount)
        )
        assertNotEquals(first.samples[firstQmcIndex].jointValues, second.samples[firstQmcIndex].jointValues)
    }

    @Test
    fun everyStateRespectsJointLimitsAndMatchesCanonicalForwardKinematics() {
        val robot = robot()
        val study = RobotWorkspaceAnalyzer().analyze(
            robot,
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = -83, voxelResolution = 13, replicationCount = 3),
            requestedWorkerCount = 3
        )
        val solver = ForwardKinematicsSolver()

        study.samples.forEach { sample ->
            sample.jointValues.forEachIndexed { index, value ->
                assertTrue(value in robot.joints[index].minValue..robot.joints[index].maxValue)
            }
            assertTrue(sample.endEffector.norm() <= study.conservativeRadiusMeters + 1e-12)
        }
        study.samples.take(32).forEach { sample ->
            val result = solver.solve(robot, RobotState(sample.jointValues))
            assertTrue(result.status == FKStatus.SUCCESS || result.status == FKStatus.SUCCESS_WITH_WARNING)
            assertEquals(result.endEffectorPosition.x, sample.endEffector.x, 1e-12)
            assertEquals(result.endEffectorPosition.y, sample.endEffector.y, 1e-12)
            assertEquals(result.endEffectorPosition.z, sample.endEffector.z, 1e-12)
        }
    }

    @Test
    fun oneRevoluteLinkProducesTheAnalyticUnitCircleRadius() {
        val robot =
            RobotDefinition(
                name = "Analytic 1R",
                dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = 0.0)),
                joints = listOf(JointDefinition("J1", JointType.REVOLUTE, -PI, PI, 0.0))
            )
        val study = RobotWorkspaceAnalyzer().analyze(
            robot,
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 7, voxelResolution = 16, replicationCount = 2)
        )

        assertEquals(1.0, study.conservativeRadiusMeters, 0.0)
        study.samples.forEach { assertEquals(1.0, it.endEffector.norm(), 1e-12) }
    }

    @Test
    fun cancellationInsideParallelSweepSurfacesTheDomainException() {
        val checks = AtomicInteger(0)
        var cancelled = false
        try {
            RobotWorkspaceAnalyzer().analyze(
                robot(),
                RobotWorkspaceAnalysisConfig(sampleCount = 2_048, randomSeed = 3, voxelResolution = 12, replicationCount = 2),
                requestedWorkerCount = 4,
                cancellationRequested = { checks.incrementAndGet() > 12 }
            )
        } catch (_: RobotWorkspaceAnalysisCancelledException) {
            cancelled = true
        }
        assertTrue("Cancellation must not be hidden inside ExecutionException", cancelled)
    }

    @Test
    fun interruptedCallingThreadStopsAsDomainCancellationBeforeWorkersStart() {
        var cancelled = false
        Thread.currentThread().interrupt()
        try {
            RobotWorkspaceAnalyzer().analyze(
                robot(),
                RobotWorkspaceAnalysisConfig(sampleCount = 2_048, randomSeed = 33, voxelResolution = 12, replicationCount = 2),
                requestedWorkerCount = 4
            )
        } catch (_: RobotWorkspaceAnalysisCancelledException) {
            cancelled = true
        } finally {
            Thread.interrupted()
        }

        assertTrue("A lifecycle thread interrupt must surface as workspace cancellation.", cancelled)
    }

    @Test
    fun progressEndsAtACompleteHundredPercent() {
        val progress = mutableListOf<RobotWorkspaceAnalysisProgress>()
        RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 9, voxelResolution = 10, replicationCount = 2),
            onProgress = progress::add
        )

        assertTrue(progress.isNotEmpty())
        assertEquals(WorkspaceAnalysisPhase.COMPLETED, progress.last().phase)
        assertEquals(1.0, progress.last().fraction, 0.0)
        assertTrue(progress.all { it.completedWorkUnits in 0..it.totalWorkUnits })
    }

    @Test
    fun randomizedReplicationsExposeComparableCoverageEvidence() {
        val study = RobotWorkspaceAnalyzer().analyze(
            robot(),
            RobotWorkspaceAnalysisConfig(sampleCount = 512, randomSeed = 21, voxelResolution = 14, replicationCount = 4)
        )

        val replications = workspaceReplicationCoverage(study)

        assertEquals(4, replications.size)
        assertTrue(replications.all { it.evaluatedSampleCount > 0 })
        assertTrue(replications.all { it.occupiedVoxelCount > 0 })
        assertTrue(replications.all { it.observedVoxelVolumeCubicMeters > 0.0 })
        assertTrue(workspaceReplicationRelativeRange(study)!!.isFinite())
    }

    @Test
    fun everyBuiltInRobotTopologyProducesFiniteBoundedWorkspaceEvidence() {
        val robots = DatasetRobotPresets().buildDefaults()
        assertEquals(10, robots.size)

        robots.forEachIndexed { index, savedRobot ->
            val study =
                RobotWorkspaceAnalyzer().analyze(
                    savedRobot.robot,
                    RobotWorkspaceAnalysisConfig(
                        sampleCount = 256,
                        randomSeed = 1_000 + index,
                        voxelResolution = 10,
                        replicationCount = 2
                    ),
                    requestedWorkerCount = 4
                )
            assertEquals(256, study.samples.size)
            assertTrue(study.samples.all { it.endEffector.isFinite() })
            assertTrue(study.samples.all { it.endEffector.norm() <= study.conservativeRadiusMeters + 1e-12 })
            assertTrue(study.observedVoxelVolumeCubicMeters > 0.0)
            assertTrue(study.convergence.zipWithNext().all { (left, right) -> right.occupiedVoxelCount >= left.occupiedVoxelCount })
        }
    }

    @Test
    fun highDimensionalRobotUsesBoundedCornersAndGeneratedHaltonBases() {
        val jointCount = 64
        val robot =
            RobotDefinition(
                name = "Workspace 64R",
                dhParameters =
                    List(jointCount) {
                        DHParameter(theta = 0.0, d = 0.0, a = 0.01, alpha = 0.0)
                    },
                joints =
                    List(jointCount) { index ->
                        JointDefinition("J${index + 1}", JointType.REVOLUTE, -PI, PI, 0.0)
                    }
            )

        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(
                    sampleCount = 256,
                    randomSeed = 321,
                    voxelResolution = 10,
                    replicationCount = 2
                ),
                requestedWorkerCount = 4
            )

        assertEquals(256, study.samples.size)
        assertTrue(study.samples.any { it.kind == WorkspaceSampleKind.QUASI_RANDOM })
        assertTrue(study.samples.all { it.jointValues.size == jointCount })
        assertTrue(study.samples.all { it.endEffector.isFinite() })
        assertTrue(study.samples.all { it.endEffector.norm() <= study.conservativeRadiusMeters + 1e-12 })
    }

    private fun robot(): RobotDefinition =
        RobotDefinition(
            name = "Workspace 3R",
            dhParameters =
                listOf(
                    DHParameter(theta = 0.0, d = 0.2, a = 0.1, alpha = PI / 2.0),
                    DHParameter(theta = 0.0, d = 0.0, a = 0.6, alpha = 0.0),
                    DHParameter(theta = 0.0, d = 0.0, a = 0.4, alpha = 0.0)
                ),
            joints =
                listOf(
                    JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Shoulder", JointType.REVOLUTE, -2.2, 2.2, 0.0),
                    JointDefinition("Elbow", JointType.REVOLUTE, -2.6, 2.6, 0.0)
                )
        )
}
