package com.robotkinematicslab.mobile.ml.storage

import com.robotkinematicslab.mobile.ml.training.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrainingResultEvidenceArchiveTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun snapshot()=HistoricalRunEvidence("run-one","/original/dataset.csv","a".repeat(64),listOf(HistoricalProfileEvidence("features","Two original variables","candidate-A",listOf("z","x"),7,
        ClassificationMetrics(2,1.0,1.0,1.0,.1,listOf(listOf(1,0,0),listOf(0,0,0),listOf(0,0,1)),classSupport=listOf(1,0,1)),emptyList(),listOf("Original warning"))))
    @Test fun reopenPreservesExactOriginalEvidenceAndFeatureOrder() {
        val file=File(temporary.root,"result.bin");val original=snapshot()
        TrainingResultEvidenceArchive.saveSnapshot(file,original)
        val restored=TrainingResultEvidenceArchive.readSnapshot(file)
        assertEquals(original.runId,restored.runId);assertEquals(original.profiles.first().featureNames,restored.profiles.first().featureNames)
        assertEquals(original.profiles.first().test,restored.profiles.first().test);assertEquals(7,restored.profiles.first().bestEpoch)
    }
    @Test fun invalidSaveAndCorruptReopenNeverReturnCurrentResults() {
        val file=File(temporary.root,"result.bin");val original=snapshot();TrainingResultEvidenceArchive.saveSnapshot(file,original);val bytes=file.readBytes()
        val profile=original.profiles.first()
        assertTrue(runCatching { TrainingResultEvidenceArchive.saveSnapshot(file,original.copy(profiles=listOf(profile.copy(test=profile.test.copy(classSupport=listOf(2,0,0)))))) }.isFailure)
        assertArrayEquals(bytes,file.readBytes())
        val changed=bytes.copyOf();changed[30]=(changed[30].toInt() xor 1).toByte();file.writeBytes(changed)
        assertTrue(runCatching { TrainingResultEvidenceArchive.readSnapshot(file) }.isFailure)
        file.writeBytes(bytes.copyOf(bytes.size-5));assertTrue(runCatching { TrainingResultEvidenceArchive.readSnapshot(file) }.isFailure)
    }
    @Test fun exactRunAndSummaryMustMatchAndLegacyAbsenceIsExplicit() {
        assertTrue(runCatching { TrainingResultEvidenceArchive.load(temporary.root,"legacy") }.isFailure)
        val summary=File(temporary.root,"summary.properties").apply { writeText("run=one") }
        val sha=java.security.MessageDigest.getInstance("SHA-256").digest(summary.readBytes()).joinToString("") { "%02x".format(it) }
        TrainingResultEvidenceArchive.saveSnapshot(File(temporary.root,TrainingResultEvidenceArchive.FILE_NAME),snapshot().copy(summarySha256=sha))
        assertEquals("run-one",TrainingResultEvidenceArchive.load(temporary.root,"run-one").runId)
        assertTrue(runCatching { TrainingResultEvidenceArchive.load(temporary.root,"run-two") }.isFailure)
        summary.writeText("run=two");assertTrue(runCatching { TrainingResultEvidenceArchive.load(temporary.root,"run-one") }.isFailure)
    }
}
