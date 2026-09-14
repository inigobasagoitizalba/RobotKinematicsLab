package com.robotkinematicslab.mobile.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchProcessModelsTest {

    @Test
    fun failureStageNamesTheOperationAndNeverFallsBackToNeedsAttention() {
        ResearchProcessKind.entries.forEach { kind ->
            val stage = researchProcessFailureStage(kind)
            assertTrue(stage.contains(kind.displayName))
            assertTrue(stage.endsWith("failed"))
            assertFalse(stage.contains("Needs attention", ignoreCase = true))
        }
        assertEquals("Scientific process failed", researchProcessFailureStage(null))
    }
    @Test
    fun `progress rejects non finite values and clamps corrupt boundaries`() {
        assertNull(snapshot("nan", ResearchProcessStatus.RUNNING, Double.NaN).safeProgressFraction)
        assertNull(snapshot("infinite", ResearchProcessStatus.RUNNING, Double.POSITIVE_INFINITY).safeProgressFraction)
        assertEquals(0.0, snapshot("low", ResearchProcessStatus.RUNNING, -4.0).safeProgressFraction!!, 0.0)
        assertEquals(1.0, snapshot("high", ResearchProcessStatus.RUNNING, 7.0).safeProgressFraction!!, 0.0)
    }

    @Test
    fun `summary separates active work from terminal evidence and sorts it`() {
        val processes =
            listOf(
                snapshot("old-result", ResearchProcessStatus.SUCCEEDED, 1.0, started = 1L, updated = 5L),
                snapshot("second-active", ResearchProcessStatus.PAUSING, 0.8, started = 20L, updated = 30L),
                snapshot("first-active", ResearchProcessStatus.RUNNING, 0.2, started = 10L, updated = 40L),
                snapshot("new-result", ResearchProcessStatus.FAILED, 0.7, started = 2L, updated = 50L)
            )

        val summary = ResearchProcessSummary.from(processes)

        assertEquals(listOf("first-active", "second-active"), summary.active.map(ResearchProcessSnapshot::id))
        assertEquals(listOf("new-result", "old-result"), summary.recent.map(ResearchProcessSnapshot::id))
        assertEquals(0.5, summary.overallProgressFraction!!, 1e-12)
    }

    @Test
    fun `unknown active progress keeps aggregate indeterminate instead of overstating completion`() {
        val summary =
            ResearchProcessSummary.from(
                listOf(
                    snapshot("unknown", ResearchProcessStatus.PREPARING, null),
                    snapshot("known", ResearchProcessStatus.RUNNING, 0.6)
                )
            )

        assertNull(summary.overallProgressFraction)
    }

    @Test
    fun `status contract is exhaustive and continuous project ids remain isolated`() {
        ResearchProcessStatus.entries.forEach { status ->
            assertEquals(!status.isActive, status.isTerminal)
        }
        assertTrue(ResearchProcessStatus.RUNNING.isActive)
        assertFalse(ResearchProcessStatus.SUCCEEDED.isActive)
        assertEquals("continuous_dataset:project-a", ResearchProcessIds.continuousDataset("project-a"))
        assertFalse(
            ResearchProcessIds.continuousDataset("project-a") ==
                ResearchProcessIds.continuousDataset("project-b")
        )
    }

    @Test
    fun `only a successful process exposes its exact persisted artifact`() {
        val reference = ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "run-42")
        val successful = snapshot("success", ResearchProcessStatus.SUCCEEDED, 1.0).copy(resultReference = reference)
        val failed = snapshot("failed", ResearchProcessStatus.FAILED, 0.9).copy(resultReference = reference)

        assertEquals(reference, successful.actionableResult)
        assertNull(failed.actionableResult)
        assertThrows(IllegalArgumentException::class.java) {
            ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "bad\nrun")
        }
    }

    @Test
    fun `successive jobs in one operation slot remain separate recent results`() {
        val first =
            snapshot("local-training", ResearchProcessStatus.SUCCEEDED, 1.0, updated = 10L)
                .copy(jobId = "local-training:1", lifecycleSequence = 1L)
        val second =
            snapshot("local-training", ResearchProcessStatus.SUCCEEDED, 1.0, updated = 20L)
                .copy(jobId = "local-training:2", lifecycleSequence = 2L)

        val recent = ResearchProcessSummary.from(listOf(first, second)).recent

        assertEquals(listOf("local-training:2", "local-training:1"), recent.map { it.jobId })
    }

    private fun snapshot(
        id: String,
        status: ResearchProcessStatus,
        progress: Double?,
        started: Long = 1L,
        updated: Long = 1L
    ): ResearchProcessSnapshot =
        ResearchProcessSnapshot(
            id = id,
            title = id,
            kind = ResearchProcessKind.ANALYSIS,
            status = status,
            progressFraction = progress,
            stage = "stage",
            detail = "detail",
            startedAtEpochMillis = started,
            updatedAtEpochMillis = updated,
            canCancel = status.isActive
        )
}
