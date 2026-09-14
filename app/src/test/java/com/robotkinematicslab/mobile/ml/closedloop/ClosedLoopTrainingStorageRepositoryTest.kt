package com.robotkinematicslab.mobile.ml.closedloop

import java.io.File
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ClosedLoopTrainingStorageRepositoryTest {

    @Test
    fun failedSummaryPublicationLeavesPreviousHistoryAndSnapshotReadable() {
        val root = Files.createTempDirectory("closed-loop-publication-failure").toFile()
        var fail = false
        val repository = ClosedLoopTrainingStorageRepository(root) { if(fail) throw java.io.IOException("injected publication failure") }
        val initial = ClosedLoopSessionSnapshot("publication-test", ClosedLoopTrainingConfig("test", "dataset", LocalTrainingConfig("test", "/dataset.csv")),
            ClosedLoopPhase.IDLE, ClosedLoopOutcome.RUNNING, 1L, 1L, 100L, 0, 0, null, null, null, null, null, "Initial", emptyList())
        repository.save(initial)
        val summaryFile = File(File(root, initial.sessionId), "session.properties")
        val original = summaryFile.readBytes()
        val props = java.util.Properties().apply { summaryFile.inputStream().use(::load) }
        val history = File(summaryFile.parentFile, props.getProperty("historyFileName"))
        val originalHistory = history.readBytes()
        fail = true
        org.junit.Assert.assertThrows(java.io.IOException::class.java) {
            repository.save(initial.copy(updatedAtEpochMillis = 2L, statusMessage = "Not committed"))
        }
        assertEquals(initial, repository.load(initial.sessionId))
        assertTrue(original.contentEquals(summaryFile.readBytes()))
        assertTrue(originalHistory.contentEquals(history.readBytes()))
    }

    @Test
    fun staleLegacyTemporaryDirectoriesCannotBlockCheckpointOrTelemetryPublication() {
        val root = Files.createTempDirectory("closed-loop-stale-temp").toFile()
        val repository = ClosedLoopTrainingStorageRepository(root)
        val snapshot =
            ClosedLoopSessionSnapshot(
                sessionId = "closed-loop-stale",
                config =
                    ClosedLoopTrainingConfig(
                        sessionName = "stale temporary recovery",
                        datasetName = "dataset",
                        localTrainingConfig = LocalTrainingConfig("run", "/dataset.csv")
                    ),
                phase = ClosedLoopPhase.IDLE,
                outcome = ClosedLoopOutcome.RUNNING,
                startedAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                currentDatasetRows = 0L,
                completedCycles = 0,
                currentTrainingAttempt = 0,
                bestRunId = null,
                bestModelPath = null,
                bestMacroF1 = null,
                bestOracleDisagreementRate = null,
                latestEvidence = null,
                statusMessage = "Initial",
                events = emptyList()
            )
        repository.save(snapshot)
        val sessionDirectory = File(root, snapshot.sessionId)
        val staleHistoryTemporary = File(sessionDirectory, "closed-loop-history.csv.tmp")
        val staleSummaryTemporary = File(sessionDirectory, "session.properties.tmp")
        val staleTelemetryTemporary = File(sessionDirectory, "resource-telemetry.csv.tmp")
        assertTrue(staleHistoryTemporary.mkdir())
        assertTrue(staleSummaryTemporary.mkdir())
        assertTrue(staleTelemetryTemporary.mkdir())

        val updated = snapshot.copy(updatedAtEpochMillis = 2L, statusMessage = "Recovered")
        repository.save(updated)
        repository.saveTelemetry(
            snapshot.sessionId,
            listOf(
                DiagnosticPerformanceSample.fromProgressState(
                    sampleIndex = 1,
                    progressState = DiagnosticProgressState(totalRuns = 1, completedRuns = 1)
                )
            )
        )

        assertEquals(updated, repository.load(snapshot.sessionId))
        assertTrue(staleHistoryTemporary.isDirectory)
        assertTrue(staleSummaryTemporary.isDirectory)
        assertTrue(staleTelemetryTemporary.isDirectory)
    }

    @Test
    fun sessionConfigurationEvidenceAndDecisionTraceRoundTrip() {
        val repository =
            ClosedLoopTrainingStorageRepository(
                Files.createTempDirectory("closed-loop-storage").toFile()
            )
        val config =
            ClosedLoopTrainingConfig(
                sessionName = "thesis loop",
                datasetName = "robots-10k",
                localTrainingConfig =
                    LocalTrainingConfig(
                        runName = "cycle",
                        datasetPath = "/datasets/robots.csv",
                        maximumRows = 10_000
                    ),
                maximumCycles = 7,
                maximumDatasetRows = 70_000,
                minimumMacroF1 = 0.88,
                maximumOracleDisagreementRate = 0.06
            )
        val evidence =
            ClosedLoopCycleEvidence(
                runId = "run-2",
                profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                datasetRowsUsed = 20_000,
                macroF1 = 0.91,
                balancedAccuracy = 0.90,
                accuracy = 0.94,
                logLoss = 0.18,
                independentTestMacroF1 = 0.89,
                independentTestBalancedAccuracy = 0.88,
                independentTestAccuracy = 0.92,
                independentTestLogLoss = 0.20,
                inferenceNanosPerSample = 720.0,
                inferenceValid = true,
                inferenceMessage = "round-trip valid",
                modelPath = "/models/run-2.rklm",
                trainingDurationMillis = 1_200,
                parameterCount = 2_048,
                validationRows = 3_000, reportingTestRows = 3_000,
                corpusSha256 = "a".repeat(64), modelSha256 = "b".repeat(64), featureSelectionId = "context_enhanced"
            )
        val event =
            ClosedLoopEvent(
                eventIndex = 1,
                timestampEpochMillis = 20,
                phase = ClosedLoopPhase.COMPARING_WITH_ORACLE,
                cycle = 2,
                datasetRows = 20_000,
                trainingAttempt = 1,
                message = "Oracle comparison, commas and ütf-8 survive.",
                runId = evidence.runId,
                macroF1 = evidence.macroF1,
                oracleDisagreementRate = evidence.oracleDisagreementRate,
                modelPath = evidence.modelPath, datasetPath = config.localTrainingConfig.datasetPath,
                evaluationProfile = evidence.profile, effectiveRows = evidence.datasetRowsUsed, corpusSha256 = evidence.corpusSha256
            )
        val snapshot =
            ClosedLoopSessionSnapshot(
                sessionId = "closed-loop-1-thesis",
                config = config,
                phase = ClosedLoopPhase.MANUAL_INTERVENTION,
                outcome = ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                startedAtEpochMillis = 10,
                updatedAtEpochMillis = 20,
                currentDatasetRows = 20_000,
                completedCycles = 2,
                currentTrainingAttempt = 1,
                bestRunId = evidence.runId,
                bestModelPath = evidence.modelPath,
                bestMacroF1 = evidence.macroF1,
                bestOracleDisagreementRate = evidence.oracleDisagreementRate,
                latestEvidence = evidence,
                statusMessage = "Increase the data budget.",
                events = listOf(event)
            )

        assertThrows(IllegalArgumentException::class.java) {
            repository.save(snapshot.copy(sessionId = "../outside"))
        }

        repository.save(snapshot)
        val telemetryFile =
            repository.saveTelemetry(
                snapshot.sessionId,
                listOf(
                    DiagnosticPerformanceSample.fromProgressState(
                        sampleIndex = 1,
                        progressState = DiagnosticProgressState(totalRuns = 10, completedRuns = 4)
                    )
                )
            )
        val restored = requireNotNull(repository.load(snapshot.sessionId))
        val summary = repository.listSessions().single()

        assertEquals(snapshot, restored)
        assertEquals(snapshot.sessionId, summary.sessionId)
        assertEquals(2, summary.completedCycles)
        assertTrue(summary.directoryPath.isNotBlank())
        assertTrue(telemetryFile.exists())
        assertTrue(telemetryFile.readText().contains("runtimeMemoryUsedMb"))

        val summaryFile = java.io.File(summary.directoryPath, "session.properties")
        val originalSummaryBytes = summaryFile.readBytes()
        val properties = java.util.Properties().apply { summaryFile.inputStream().use(::load) }
        properties.setProperty("bestMacroF1", "NaN")
        summaryFile.outputStream().use { properties.store(it, "corrupt metric") }
        val corruptSummaryBytes = summaryFile.readBytes()
        assertNull(repository.load(snapshot.sessionId))
        assertTrue(corruptSummaryBytes.contentEquals(summaryFile.readBytes()))
        summaryFile.writeBytes(originalSummaryBytes)

        val historyFile = java.io.File(summary.directoryPath, properties.getProperty("historyFileName"))
        historyFile.appendText("corrupt,row\n")
        val corruptBytes = historyFile.readBytes()
        assertNull(repository.load(snapshot.sessionId))
        assertTrue(corruptBytes.contentEquals(historyFile.readBytes()))
    }
}
