package com.robotkinematicslab.mobile.workspace.storage

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RobotWorkspaceStudyRepositoryTest {

    @Test
    fun completeStudyRoundTripsWithoutLosingScientificEvidence() {
        val directory = Files.createTempDirectory("workspace-study-roundtrip").toFile()
        val repository = RobotWorkspaceStudyRepository(directory)
        val study = study()

        val saved = repository.save(study)
        val indexed = repository.listStudies().single()
        val loaded = repository.load(indexed)

        assertEquals(saved, indexed)
        assertEquals(study, loaded)
        assertTrue(File(indexed.dataPath).isFile)
    }

    @Test
    fun corruptDataCannotMasqueradeAsAValidSavedStudy() {
        val directory = Files.createTempDirectory("workspace-study-corrupt").toFile()
        val repository = RobotWorkspaceStudyRepository(directory)
        val summary = repository.save(study())
        File(summary.dataPath).writeBytes(byteArrayOf(1, 2, 3, 4))

        var rejected = false
        try {
            repository.load(summary)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun pathTraversalSummaryIsRejectedBeforeReading() {
        val directory = Files.createTempDirectory("workspace-study-path").toFile()
        val repository = RobotWorkspaceStudyRepository(directory)
        val summary = repository.save(study())
        val outside = Files.createTempDirectory("workspace-study-outside").toFile()

        var rejected = false
        try {
            repository.load(summary.copy(directoryPath = outside.absolutePath))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun duplicateStudyCannotOverwritePublishedEvidence() {
        val directory = Files.createTempDirectory("workspace-study-collision").toFile()
        val repository = RobotWorkspaceStudyRepository(directory)
        val study = study()
        val summary = repository.save(study)
        val data = File(summary.dataPath).readBytes()
        val manifest = File(summary.directoryPath, "manifest.properties")
        val manifestBytes = manifest.readBytes()

        assertThrows(IllegalArgumentException::class.java) { repository.save(study) }

        assertTrue(data.contentEquals(File(summary.dataPath).readBytes()))
        assertTrue(manifestBytes.contentEquals(manifest.readBytes()))
        assertEquals(study, repository.load(summary))
    }

    @Test
    fun manifestCannotClaimDifferentScientificEvidence() {
        val directory = Files.createTempDirectory("workspace-study-manifest-mismatch").toFile()
        val repository = RobotWorkspaceStudyRepository(directory)
        val summary = repository.save(study())
        val manifest = File(summary.directoryPath, "manifest.properties")
        val originalBytes = manifest.readBytes()
        val properties = java.util.Properties().apply { manifest.inputStream().use(::load) }
        properties.setProperty("robotFingerprint", "different-fingerprint")
        manifest.outputStream().use { properties.store(it, "tampered") }
        val tamperedBytes = manifest.readBytes()

        assertThrows(IllegalArgumentException::class.java) { repository.load(summary) }

        assertTrue(tamperedBytes.contentEquals(manifest.readBytes()))
        assertTrue(!originalBytes.contentEquals(tamperedBytes))
    }

    private fun study() =
        RobotWorkspaceAnalyzer(clockMillis = { 123_456L }).analyze(
            robot =
                RobotDefinition(
                    name = "Stored 2R",
                    dhParameters =
                        listOf(
                            DHParameter(0.0, 0.1, 0.5, 0.0),
                            DHParameter(0.0, 0.0, 0.4, 0.0)
                        ),
                    joints =
                        listOf(
                            JointDefinition("J1", JointType.REVOLUTE, -PI, PI, 0.0),
                            JointDefinition("J2", JointType.REVOLUTE, -PI / 2.0, PI / 2.0, 0.0)
                        )
                ),
            config = RobotWorkspaceAnalysisConfig(sampleCount = 256, randomSeed = 71, voxelResolution = 10, replicationCount = 2),
            requestedWorkerCount = 2
        )
}
