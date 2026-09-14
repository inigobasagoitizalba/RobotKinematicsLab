package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NumericalSafetyStorageRepositoryTest {

    @Test
    fun savedSessionContainsReadableMethodAndBothPairedRows() {
        val report =
            NumericalSafetyExperiment().run(
                NumericalSafetyExperimentConfig(
                    linkCounts = listOf(3),
                    jointModes = listOf(DiagnosticJointMode.REVOLUTE_ONLY),
                    validTrialsPerTopology = 1,
                    includeAdversarialProbes = false,
                    randomSeed = 77,
                    ikConfig = IKConfig(maxIterations = 40, tolerance = 1e-4, damping = 0.02, maxStep = 0.03)
                )
            )
        val root = Files.createTempDirectory("rkl-numerical-safety").toFile()
        val saved = NumericalSafetyStorageRepository(root).save(report)
        val readable = java.io.File(saved.reportPath).readText()
        val csvLines = java.io.File(saved.trialCsvPath).readLines()

        assertEquals(1, saved.trialCount)
        assertTrue(readable.contains(NumericalSafetyExperiment.PROTOCOL_ID))
        assertTrue(readable.contains("SCIENTIFIC BOUNDARY"))
        assertTrue(readable.contains("not physical drift"))
        assertEquals(3, csvLines.size)
        assertTrue(csvLines[1].contains("GUARDED_PRODUCTION"))
        assertTrue(csvLines[2].contains("UNGUARDED_REFERENCE"))
    }

    @Test
    fun duplicateScientificIdentityIsRejectedWithoutChangingPublishedBytes() {
        val report =
            NumericalSafetyExperiment().run(
                NumericalSafetyExperimentConfig(
                    linkCounts = listOf(3),
                    jointModes = listOf(DiagnosticJointMode.REVOLUTE_ONLY),
                    validTrialsPerTopology = 1,
                    includeAdversarialProbes = false,
                    randomSeed = 77,
                    ikConfig = IKConfig(maxIterations = 40, tolerance = 1e-4, damping = 0.02, maxStep = 0.03)
                )
            )
        val root = Files.createTempDirectory("rkl-numerical-safety-collision").toFile()
        val repository = NumericalSafetyStorageRepository(root)
        val saved = repository.save(report)
        val reportBytes = java.io.File(saved.reportPath).readBytes()
        val csvBytes = java.io.File(saved.trialCsvPath).readBytes()
        val manifest = java.io.File(saved.directoryPath, "manifest.properties")
        val manifestBytes = manifest.readBytes()

        assertThrows(IllegalArgumentException::class.java) { repository.save(report) }

        assertTrue(reportBytes.contentEquals(java.io.File(saved.reportPath).readBytes()))
        assertTrue(csvBytes.contentEquals(java.io.File(saved.trialCsvPath).readBytes()))
        assertTrue(manifestBytes.contentEquals(manifest.readBytes()))
        assertEquals(1, root.listFiles(java.io.File::isDirectory)?.size)
    }
}
