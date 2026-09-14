package com.robotkinematicslab.mobile.workspace.storage

import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import java.nio.file.Files
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotWorkspaceAutoProvisionerTest {

    @Test
    fun repeatedProvisioningReusesTheRobotOwnedBaseline() {
        val repository = repository()
        val provisioner = provisioner(repository)
        val robot = savedRobot()

        val first = provisioner.ensureBaseline(robot, requestedWorkerCount = 2)
        val repeated = provisioner.ensureBaseline(robot, requestedWorkerCount = 2)

        assertTrue(first.created)
        assertFalse(repeated.created)
        assertEquals(first.summary.studyId, repeated.summary.studyId)
        assertEquals(first.summary.robotFingerprint, repeated.summary.robotFingerprint)
        assertEquals(1, repository.listStudies().size)
    }

    @Test
    fun changedRobotDefinitionCreatesANewImmutableEvidenceVersion() {
        val repository = repository()
        val provisioner = provisioner(repository)
        val original = savedRobot()
        val originalResult = provisioner.ensureBaseline(original, requestedWorkerCount = 2)
        val edited =
            original.copy(
                robot =
                    original.robot.copy(
                        dhParameters =
                            original.robot.dhParameters.mapIndexed { index, parameter ->
                                if (index == 1) parameter.copy(a = parameter.a + 0.05) else parameter
                            }
                    )
            )

        val editedResult = provisioner.ensureBaseline(edited, requestedWorkerCount = 2)

        assertTrue(editedResult.created)
        assertNotEquals(originalResult.summary.studyId, editedResult.summary.studyId)
        assertNotEquals(originalResult.summary.robotFingerprint, editedResult.summary.robotFingerprint)
        assertEquals(2, repository.listStudies().size)
        assertEquals(
            setOf(originalResult.summary.robotFingerprint, editedResult.summary.robotFingerprint),
            repository.listStudies().map { it.robotFingerprint }.toSet()
        )
    }

    private fun repository() =
        RobotWorkspaceStudyRepository(
            Files.createTempDirectory("workspace-auto-provision").toFile()
        )

    private fun provisioner(repository: RobotWorkspaceStudyRepository) =
        RobotWorkspaceAutoProvisioner(
            repository = repository,
            analyzer = RobotWorkspaceAnalyzer(clockMillis = { 123_456L }),
            baselineConfig =
                RobotWorkspaceAnalysisConfig(
                    sampleCount = 256,
                    randomSeed = 42,
                    voxelResolution = 10,
                    replicationCount = 2
                )
        )

    private fun savedRobot() =
        SavedRobot(
            id = "owned-2r",
            robot =
                RobotDefinition(
                    name = "Owned 2R",
                    dhParameters =
                        listOf(
                            DHParameter(theta = 0.0, d = 0.1, a = 0.5, alpha = 0.0),
                            DHParameter(theta = 0.0, d = 0.0, a = 0.4, alpha = 0.0)
                        ),
                    joints =
                        listOf(
                            JointDefinition("J1", JointType.REVOLUTE, -PI, PI, 0.0),
                            JointDefinition("J2", JointType.REVOLUTE, -PI / 2.0, PI / 2.0, 0.0)
                        )
                )
        )
}
