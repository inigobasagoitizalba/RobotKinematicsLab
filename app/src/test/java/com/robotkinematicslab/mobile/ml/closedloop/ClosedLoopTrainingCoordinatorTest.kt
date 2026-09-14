package com.robotkinematicslab.mobile.ml.closedloop

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedLoopTrainingCoordinatorTest {

    @Test
    fun acceptsValidInferenceInsideBothOracleThresholdsWithoutGrowingData() {
        val runner = QueueRunner(listOf(evidence("accepted", macroF1 = 0.91, accuracy = 0.94)))
        val grower = RecordingGrower()
        val store = RecordingStore()

        val result = coordinator(runner, grower, store).run(config(), initialDatasetRows = 100)

        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(1, result.completedCycles)
        assertEquals("accepted", result.bestRunId)
        assertEquals(0, grower.requests.size)
        assertEquals(ClosedLoopPhase.COMPLETED, store.snapshots.last().phase)
    }

    @Test
    fun rejectedModelGrowsManagedDatasetThenRetrainsAndAccepts() {
        val runner =
            QueueRunner(
                listOf(
                    evidence("cycle-1", macroF1 = 0.60, accuracy = 0.72),
                    evidence("cycle-2", macroF1 = 0.90, accuracy = 0.93)
                )
            )
        val grower = RecordingGrower(addedRows = 40)

        val result = coordinator(runner, grower, RecordingStore()).run(
            config = config().copy(maximumCycles = 3, samplesPerRobotIncrement = 10),
            initialDatasetRows = 100
        )

        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(2, result.completedCycles)
        assertEquals(140L, result.currentDatasetRows)
        assertEquals(1, grower.requests.size)
        assertEquals(10, grower.requests.single().samplesPerRobot)
        assertTrue(result.events.any { it.phase == ClosedLoopPhase.GROWING_DATASET })
    }

    @Test
    fun boundedCapBelowDatasetCeilingForcesEveryAppendIntoTheNextFullCorpusSample() {
        val runner =
            QueueRunner(
                listOf(
                    evidence("cycle-1", macroF1 = 0.60, accuracy = 0.72),
                    evidence("cycle-2", macroF1 = 0.90, accuracy = 0.93)
                )
            )
        val grower = RecordingGrower(addedRows = 40)
        val bounded =
            config().copy(
                localTrainingConfig = config().localTrainingConfig.copy(maximumRows = 100),
                maximumDatasetRows = 1_000,
                maximumCycles = 3
            )

        val result = coordinator(runner, grower, RecordingStore()).run(bounded, initialDatasetRows = 500)

        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(100, grower.requests.single().maximumRowsEligibleForNextTraining)
        val secondCycle = runner.requests[1].config
        assertEquals(100, secondCycle.maximumRows)
        assertEquals(540L, secondCycle.expectedDatasetRows)
        assertEquals(40, secondCycle.requiredNewestRows)
        assertTrue(secondCycle.sampleAcrossEntireDataset)
        assertEquals(0, result.untrainedAppendedRows)
    }

    @Test
    fun invalidStoredInferenceIsRebuiltThreeTimesThenEscalated() {
        val invalid = (1..4).map { evidence("invalid-$it", 0.95, 0.98, inferenceValid = false) }
        val runner = QueueRunner(invalid)

        val result = coordinator(runner, RecordingGrower(), RecordingStore()).run(
            config = config().copy(maximumInferenceRetries = 3),
            initialDatasetRows = 100
        )

        assertEquals(ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION, result.outcome)
        assertEquals(4, runner.calls)
        assertEquals(0, result.completedCycles)
        assertTrue(result.statusMessage.contains("Inference remained invalid"))
    }

    @Test
    fun transientTrainingFailureUsesConfiguredRetryAndThenSucceeds() {
        val runner = QueueRunner(listOf(IllegalStateException("numeric overflow"), evidence("recovered", 0.90, 0.95)))

        val result = coordinator(runner, RecordingGrower(), RecordingStore()).run(
            config = config().copy(maximumTrainingRetries = 1),
            initialDatasetRows = 100
        )

        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(2, runner.calls)
        assertTrue(result.events.any { it.phase == ClosedLoopPhase.RETRYING && it.message.contains("numeric overflow") })
    }

    @Test
    fun laterRegressionCannotReplaceBestStoredModel() {
        val runner =
            QueueRunner(
                listOf(
                    evidence("best", macroF1 = 0.79, accuracy = 0.82),
                    evidence("worse", macroF1 = 0.70, accuracy = 0.75)
                )
            )
        val result = coordinator(runner, RecordingGrower(30), RecordingStore()).run(
            config =
                config().copy(
                    minimumMacroF1 = 0.95,
                    maximumOracleDisagreementRate = 0.01,
                    maximumCycles = 2,
                    maximumStagnantCycles = 10
                ),
            initialDatasetRows = 100
        )

        assertEquals(ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION, result.outcome)
        assertEquals("best", result.bestRunId)
        assertEquals(0.79, requireNotNull(result.bestMacroF1), 0.0)
    }

    @Test
    fun cancellationBeforeWorkIsTerminalAndDoesNotTrain() {
        val runner = QueueRunner(listOf(evidence("unused", 1.0, 1.0)))
        val cancellation = AtomicBoolean(true)

        val result = coordinator(runner, RecordingGrower(), RecordingStore()).run(
            config = config(),
            initialDatasetRows = 100,
            cancellationRequested = cancellation
        )

        assertEquals(ClosedLoopOutcome.CANCELLED, result.outcome)
        assertEquals(0, runner.calls)
    }

    @Test
    fun cancellationRequestedByTrainerWinsAtTheTrainingBoundary() {
        val cancellation = AtomicBoolean(false)
        val runner =
            object : ClosedLoopTrainingCycleRunner {
                override fun train(
                    request: ClosedLoopTrainingRequest,
                    cancellationRequested: AtomicBoolean,
                    onProgress: (LocalTrainingProgress) -> Unit
                ): ClosedLoopCycleEvidence {
                    cancellationRequested.set(true)
                    return evidence("must-not-be-accepted", macroF1 = 1.0, accuracy = 1.0)
                }
            }

        val result = coordinator(runner, RecordingGrower(), RecordingStore()).run(
            config = config(),
            initialDatasetRows = 100,
            cancellationRequested = cancellation
        )

        assertEquals(ClosedLoopOutcome.CANCELLED, result.outcome)
        assertEquals(ClosedLoopPhase.CANCELLED, result.phase)
        assertTrue(result.bestRunId == null)
    }

    @Test
    fun cancellationDuringFailingDatasetGrowthIsNotMisreportedAsFailure() {
        val cancellation = AtomicBoolean(false)
        val grower =
            object : ClosedLoopDatasetGrower {
                override fun grow(
                    request: ClosedLoopDatasetGrowthRequest,
                    cancellationRequested: AtomicBoolean,
                    onProgress: (ClosedLoopDatasetGrowthProgress) -> Unit
                ): ClosedLoopDatasetGrowthResult {
                    cancellationRequested.set(true)
                    error("Injected engine shutdown while growing the dataset")
                }
            }

        val result = coordinator(
            QueueRunner(listOf(evidence("below-gate", macroF1 = 0.5, accuracy = 0.6))),
            grower,
            RecordingStore()
        ).run(
            config = config(),
            initialDatasetRows = 100,
            cancellationRequested = cancellation
        )

        assertEquals(ClosedLoopOutcome.CANCELLED, result.outcome)
        assertEquals(ClosedLoopPhase.CANCELLED, result.phase)
        assertTrue(result.statusMessage.contains("cancelled safely"))
    }

    @Test
    fun failingProgressObserverCannotAbortOrRetryScientificWork() {
        val runner = QueueRunner(listOf(evidence("observer-isolated", macroF1 = 0.91, accuracy = 0.94)))

        val result = coordinator(runner, RecordingGrower(), RecordingStore()).run(
            config = config(),
            initialDatasetRows = 100,
            onProgress = { error("Injected detached observer") }
        )

        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(1, runner.calls)
        assertEquals("observer-isolated", result.bestRunId)
    }

    @Test
    fun gateBoundaryMatrixChecksProfileInferenceAndBothValidationCriteria() {
        val boundary = config().copy(minimumMacroF1 = 0.8, maximumOracleDisagreementRate = 0.3)
        val exact = evidence("exact", 0.8, 0.7)
        assertTrue(ClosedLoopAcceptanceGate.evaluate(boundary, exact).accepted)
        listOf(exact.copy(macroF1 = 0.799999), exact.copy(accuracy = 0.699999),
            exact.copy(macroF1 = 0.79, accuracy = 0.69), exact.copy(inferenceValid = false),
            exact.copy(profile = TrainingFeatureProfile.BASELINE_KINEMATICS), exact.copy(macroF1 = Double.NaN))
            .forEach { assertTrue(!ClosedLoopAcceptanceGate.evaluate(boundary, it).accepted) }
        assertTrue(ClosedLoopAcceptanceGate.evaluate(boundary, exact.copy(independentTestMacroF1 = 0.0, independentTestAccuracy = 0.0)).accepted)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ClosedLoopAcceptanceGate.evaluate(boundary.copy(minimumMacroF1 = Double.NaN), exact)
        }
    }

    @Test
    fun absentProfileFailsBeforeTrainingAndAdditionalProfilesDoNotChangeJudgedProfile() {
        val runner = QueueRunner(listOf(evidence("all-profiles", 0.9, 0.95)))
        val coordinator = coordinator(runner, RecordingGrower(), RecordingStore())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.run(config().copy(evaluationProfile = TrainingFeatureProfile.BASELINE_KINEMATICS), 100)
        }
        val all = config().copy(localTrainingConfig = config().localTrainingConfig.copy(compareFeatureProfiles = true))
        val result = coordinator.run(all, 100)
        assertEquals(ClosedLoopOutcome.ACCEPTED, result.outcome)
        assertEquals(TrainingFeatureProfile.entries.size, runner.requests.single().config.resolvedFeatureSelections.size)
        assertEquals(all.evaluationProfile, runner.requests.single().evaluationProfile)
        assertTrue(result.events.first { it.phase == ClosedLoopPhase.COMPARING_WITH_ORACLE }.message.contains("Validation macro-F1: passes"))
    }

    @Test
    fun failedAcceptanceCheckpointCannotReturnAcceptedAndPriorCheckpointSurvives() {
        val stored = mutableListOf<ClosedLoopSessionSnapshot>()
        val store = ClosedLoopSessionStore { snapshot ->
            if(snapshot.outcome == ClosedLoopOutcome.ACCEPTED) throw java.io.IOException("disk full")
            stored += snapshot
        }
        val result = coordinator(QueueRunner(listOf(evidence("completed-model", 0.9, 0.95))), RecordingGrower(), store).run(config(), 100)
        assertEquals(ClosedLoopOutcome.FAILED, result.outcome)
        assertTrue(result.statusMessage.contains("not committed"))
        assertTrue(stored.none { it.outcome == ClosedLoopOutcome.ACCEPTED })
        assertEquals("completed-model", stored.last().latestEvidence?.runId)
    }

    @Test
    fun resumeKeepsIdentityAndRejectsChangedScientificContracts() {
        val cancelled = coordinator(QueueRunner(emptyList()), RecordingGrower(), RecordingStore())
            .run(config(), 100, cancellationRequested = AtomicBoolean(true))
        val runner = QueueRunner(listOf(evidence("resumed", 0.9, 0.95)))
        val coordinator = coordinator(runner, RecordingGrower(), RecordingStore())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.run(config().copy(minimumMacroF1 = 0.7), 100, resumeFrom = cancelled)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            coordinator.run(config(), 101, resumeFrom = cancelled)
        }
        val resumed = coordinator.run(config().copy(maximumCycles = 6), 100, resumeFrom = cancelled)
        assertEquals(cancelled.sessionId, resumed.sessionId)
        assertEquals(ClosedLoopOutcome.ACCEPTED, resumed.outcome)
        assertTrue(resumed.events.any { it.message.contains("Policy before=") && it.message.contains("policy after=") })
        assertEquals("1 completed cycle", closedLoopCycleCount(1))
        assertEquals("2 completed cycles", closedLoopCycleCount(2))
    }

    @Test
    fun earlyCancellationDoesNotInventCompletedTrainingStages() {
        val stopped = coordinator(QueueRunner(emptyList()), RecordingGrower(), RecordingStore())
            .run(config(), 100, cancellationRequested = AtomicBoolean(true))
        val timeline = com.robotkinematicslab.mobile.ui.training.closedLoopTimeline(stopped.phase, stopped)
        assertTrue(timeline.none { it.status == com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus.RUNNING })
        assertTrue(timeline.none { it.status == com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus.COMPLETE })
    }

    private fun coordinator(
        runner: ClosedLoopTrainingCycleRunner,
        grower: ClosedLoopDatasetGrower,
        store: ClosedLoopSessionStore
    ) = ClosedLoopTrainingCoordinator(runner, grower, store)

    private fun config(): ClosedLoopTrainingConfig =
        ClosedLoopTrainingConfig(
            sessionName = "test-loop",
            datasetName = "managed-dataset",
            localTrainingConfig =
                LocalTrainingConfig(
                    runName = "test",
                    datasetPath = "/tmp/test.csv",
                    compareFeatureProfiles = false,
                    singleFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                    modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                    maximumRows = 1_000,
                    epochs = 1
                ),
            maximumCycles = 3,
            samplesPerRobotIncrement = 10,
            maximumDatasetRows = 1_000,
            minimumMacroF1 = 0.80,
            maximumOracleDisagreementRate = 0.10,
            maximumStagnantCycles = 3
        )

    private fun evidence(
        id: String,
        macroF1: Double,
        accuracy: Double,
        inferenceValid: Boolean = true
    ) = ClosedLoopCycleEvidence(
        runId = id,
        profile = TrainingFeatureProfile.CONTEXT_ENHANCED,
        datasetRowsUsed = 100,
        macroF1 = macroF1,
        balancedAccuracy = macroF1,
        accuracy = accuracy,
        logLoss = 0.2,
        independentTestMacroF1 = macroF1,
        independentTestBalancedAccuracy = macroF1,
        independentTestAccuracy = accuracy,
        independentTestLogLoss = 0.2,
        inferenceNanosPerSample = 1_000.0,
        inferenceValid = inferenceValid,
        inferenceMessage = if (inferenceValid) "verified" else "invalid",
        modelPath = "/models/$id.rklm",
        trainingDurationMillis = 10,
        parameterCount = 10
    )

    private class QueueRunner(values: List<Any>) : ClosedLoopTrainingCycleRunner {
        private val queue = ArrayDeque(values)
        var calls = 0
        val requests = mutableListOf<ClosedLoopTrainingRequest>()

        override fun train(
            request: ClosedLoopTrainingRequest,
            cancellationRequested: AtomicBoolean,
            onProgress: (LocalTrainingProgress) -> Unit
        ): ClosedLoopCycleEvidence {
            calls++
            requests += request
            return when (val value = queue.removeFirst()) {
                is Throwable -> throw value
                else -> value as ClosedLoopCycleEvidence
            }
        }
    }

    private class RecordingGrower(private val addedRows: Int = 20) : ClosedLoopDatasetGrower {
        val requests = mutableListOf<ClosedLoopDatasetGrowthRequest>()

        override fun grow(
            request: ClosedLoopDatasetGrowthRequest,
            cancellationRequested: AtomicBoolean,
            onProgress: (ClosedLoopDatasetGrowthProgress) -> Unit
        ): ClosedLoopDatasetGrowthResult {
            requests += request
            return ClosedLoopDatasetGrowthResult(
                addedRows = addedRows,
                totalRows = request.expectedCurrentRows + addedRows,
                completed = true,
                message = "grown"
            )
        }
    }

    private class RecordingStore : ClosedLoopSessionStore {
        val snapshots = mutableListOf<ClosedLoopSessionSnapshot>()
        override fun save(snapshot: ClosedLoopSessionSnapshot) {
            snapshots += snapshot
        }
    }
}
