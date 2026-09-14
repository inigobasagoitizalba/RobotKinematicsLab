package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ExplainabilitySessionRepositoryTest {
    private fun directory() = Files.createTempDirectory("xai-session-test").toFile()
    private fun request() = ExplainabilityRequest(UUID.randomUUID().toString(), "run-a", "/project/models/model.bin", "a".repeat(64), 16, 8)
    private fun report(request: ExplainabilityRequest = request()) = StoredExplainabilityReport(request,
        ExplainabilityResult(request.runId, TrainingFeatureProfile.BASELINE_KINEMATICS, "candidate-a", 1, 8,
            listOf(GlobalFeatureImportance("target_x", 0.25, -0.25)),
            listOf(LocalPredictionExplanation(729, TrainingLabel.ACCEPTED, TrainingLabel.REJECTED, TrainingLabel.UNCERTAIN,
                0.7, -0.5, 1.0, 0.00001, listOf(FeatureAttribution("target_x", 0.25, -1.5)))),
            0.5, 0.9, 0.00001, 0.00002, 1234, "Custom profile"), 1234567)

    @Test fun completedEvidenceRestoresAllNumbersIdentityAndExportTimestampWithoutRecomputation() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val report = report()
        repository.saveReport(report)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.COMPLETED, report.request, report))
        val restored = ExplainabilitySessionRepository(dir).restore()
        assertEquals(report, restored.report)
        assertEquals(ExplainabilitySessionStatus.COMPLETED, restored.status)
        assertFalse(restored.loading); assertFalse(restored.running)
    }

    @Test fun killedRunningSessionRestoresInterruptedAndRetainsPreviousCompletedEvidence() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val prior = report()
        repository.saveReport(prior)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.RUNNING, request(), prior))
        val restored = ExplainabilitySessionRepository(dir).restore()
        assertEquals(ExplainabilitySessionStatus.INTERRUPTED, restored.status)
        assertEquals(prior, restored.report); assertFalse(restored.running); assertFalse(restored.loading)
    }

    @Test fun crashBetweenReportCommitAndSessionPointerStillRecoversCompletedExecution() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val report = report()
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.RUNNING, report.request))
        repository.saveReport(report)
        val restored = ExplainabilitySessionRepository(dir).restore()
        assertEquals(ExplainabilitySessionStatus.COMPLETED, restored.status)
        assertEquals(report, restored.report)
    }

    @Test fun sameRunAndModelNeverMixEvidenceAcrossProjectsAndRetriesRemainImmutable() {
        val first = directory(); val second = directory(); val original = report()
        val repository = ExplainabilitySessionRepository(first)
        repository.saveReport(original)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.COMPLETED, original.request, original))
        assertNull(ExplainabilitySessionRepository(second).restore().report)
        val snapshot = File(first, "${original.request.executionId}.xai").readBytes()
        assertTrue(runCatching { repository.saveReport(original.copy(completedAtEpochMillis = 9999)) }.isFailure)
        assertArrayEquals(snapshot, File(first, "${original.request.executionId}.xai").readBytes())
        val retry = report(request())
        repository.saveReport(retry)
        assertTrue(File(first, "${original.request.executionId}.xai").isFile)
        assertTrue(File(first, "${retry.request.executionId}.xai").isFile)
    }

    @Test fun corruptSavedReportReportsFailureWithoutPretendingToRunOrRemovingFiles() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val report = report()
        repository.saveReport(report)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.COMPLETED, report.request, report))
        val file = File(dir, "${report.request.executionId}.xai"); file.writeBytes(byteArrayOf(1, 2, 3))
        val restored = ExplainabilitySessionRepository(dir).restore()
        assertEquals(ExplainabilitySessionStatus.FAILED, restored.status)
        assertFalse(restored.running); assertFalse(restored.loading); assertTrue(file.isFile)
    }

    @Test fun incompleteTemporaryWriteDoesNotReplacePreviouslyCommittedEvidence() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val report = report()
        repository.saveReport(report)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.COMPLETED, report.request, report))
        File(dir, ".abandoned.tmp").writeText("partial next journal")
        assertEquals(report, ExplainabilitySessionRepository(dir).restore().report)
    }
    @Test fun sameSizeMutationAndMissingCompletedFileAreRejected() {
        val dir = directory(); val repository = ExplainabilitySessionRepository(dir); val report = report()
        repository.saveReport(report)
        repository.saveState(ExplainabilitySessionState(false, ExplainabilitySessionStatus.COMPLETED, report.request, report))
        val file = File(dir, "${report.request.executionId}.xai")
        val bytes = file.readBytes(); bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertEquals(ExplainabilitySessionStatus.FAILED, repository.restore().status)
        file.renameTo(File(dir, "preserved-corrupt-report.xai"))
        assertEquals(ExplainabilitySessionStatus.FAILED, repository.restore().status)
    }

    @Test fun nonFiniteAndMismatchedEvidenceCannotBeCommitted() {
        val repository = ExplainabilitySessionRepository(directory()); val report = report()
        assertTrue(runCatching { repository.saveReport(report.copy(result = report.result.copy(explainedSampleAccuracy = Double.NaN))) }.isFailure)
        assertTrue(runCatching { repository.saveReport(report.copy(result = report.result.copy(explainedSampleCount = 2))) }.isFailure)
        val local = report.result.localExplanations.single()
        assertTrue(runCatching { repository.saveReport(report.copy(result = report.result.copy(localExplanations = listOf(local.copy(sourceRowIndex = -1))))) }.isFailure)
    }

}
