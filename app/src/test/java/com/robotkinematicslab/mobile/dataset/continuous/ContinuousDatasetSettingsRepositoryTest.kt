package com.robotkinematicslab.mobile.dataset.continuous

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousDatasetSettingsRepositoryTest {

    @Test
    fun recoveryPlanAndPendingBatchRoundTripExactly() {
        val directory = Files.createTempDirectory("continuous-settings").toFile()
        val repository = ContinuousDatasetSettingsRepository(directory)
        val expected = ContinuousDatasetRecovery(
            plan = plan(),
            wasRunning = true,
            pendingBatch = ContinuousDatasetPendingBatch(125L, 5, 25, 9_999L)
        )

        repository.save(expected)

        assertEquals(expected, repository.load())
        assertFalse(File(directory, "continuous-dataset.properties.tmp").exists())
    }

    @Test
    fun corruptOrFutureStateIsIgnoredInsteadOfStartingWithGuessedSettings() {
        val directory = Files.createTempDirectory("continuous-corrupt").toFile()
        val repository = ContinuousDatasetSettingsRepository(directory)
        File(directory, "continuous-dataset.properties").writeText("schemaVersion=999\ndatasetName=bad\n")

        assertNull(repository.load())
    }

    @Test
    fun staleLegacyTemporaryEntryCannotBlockARecoveryStateRetry() {
        val directory = Files.createTempDirectory("continuous-stale-temp").toFile()
        val repository = ContinuousDatasetSettingsRepository(directory)
        val staleTemporaryEntry = File(directory, "continuous-dataset.properties.tmp")
        assertTrue(staleTemporaryEntry.mkdir())

        val expected =
            ContinuousDatasetRecovery(
                plan = plan().copy(datasetName = "unicode-robot-漢字-🤖"),
                wasRunning = true,
                pendingBatch = ContinuousDatasetPendingBatch(0L, 0, 25, 123L)
            )

        repository.save(expected)

        assertEquals(expected, repository.load())
        assertTrue(staleTemporaryEntry.isDirectory)
        assertTrue(
            directory.listFiles().orEmpty().none {
                it.isFile && it.name.startsWith(".continuous-dataset.properties-") && it.name.endsWith(".tmp")
            }
        )
    }

    private fun plan() = ContinuousDatasetPlan(
        datasetName = "saved-continuous-study",
        robotIds = listOf("robot-a", "robot-b"),
        randomSeed = 2604,
        targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE,
        reachableFraction = 1.0,
        filterMode = DatasetFilterMode.ACCEPTED_ONLY,
        ikConfig = IKConfig(maxIterations = 800, tolerance = 1e-6, damping = 0.01, maxStep = 0.02),
        maxAttemptsMultiplier = 40,
        cpuBudgetPercent = 25,
        memoryBudgetPercent = 35
    )
}
