package com.robotkinematicslab.mobile.ml.closedloop

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class ClosedLoopTrainingCoordinator(
    private val cycleRunner: ClosedLoopTrainingCycleRunner,
    private val datasetGrower: ClosedLoopDatasetGrower,
    private val sessionStore: ClosedLoopSessionStore,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun run(
        config: ClosedLoopTrainingConfig,
        initialDatasetRows: Long,
        resumeFrom: ClosedLoopSessionSnapshot? = null,
        cancellationRequested: AtomicBoolean = AtomicBoolean(false),
        onProgress: (ClosedLoopProgress) -> Unit = {}
    ): ClosedLoopSessionSnapshot {
        validate(config, initialDatasetRows)
        resumeFrom?.let { validateResume(config, initialDatasetRows, it, verifyCorpus = true) }
        val startedAt = resumeFrom?.startedAtEpochMillis ?: now()
        val invocationStartedAt = now()
        var snapshot =
            resumeFrom?.copy(
                config = config,
                phase = ClosedLoopPhase.IDLE,
                outcome = ClosedLoopOutcome.RUNNING,
                updatedAtEpochMillis = now(),
                currentDatasetRows = maxOf(initialDatasetRows, resumeFrom.currentDatasetRows),
                statusMessage = "Closed-loop session resumed."
            ) ?: ClosedLoopSessionSnapshot(
                sessionId = buildSessionId(startedAt, config.sessionName),
                config = config,
                phase = ClosedLoopPhase.IDLE,
                outcome = ClosedLoopOutcome.RUNNING,
                startedAtEpochMillis = startedAt,
                updatedAtEpochMillis = startedAt,
                currentDatasetRows = initialDatasetRows,
                completedCycles = 0,
                currentTrainingAttempt = 0,
                bestRunId = null,
                bestModelPath = null,
                bestMacroF1 = null,
                bestOracleDisagreementRate = null,
                latestEvidence = null,
                statusMessage = "Closed-loop session created.",
                events = emptyList(),
                currentCorpusSha256 = java.io.File(config.localTrainingConfig.datasetPath).takeIf { it.isFile }?.let {
                    com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract.corpusDigest(it)
                }
            )

        fun persist() {
            try { sessionStore.save(snapshot) } catch (error: Exception) { throw CheckpointFailure(error) }
        }

        fun publishProgress(progress: ClosedLoopProgress) {
            try {
                onProgress(progress)
            } catch (_: Exception) {
                // Progress is an observer boundary. A detached UI or monitoring adapter must not
                // turn already-checkpointed scientific work into a retry or failed session.
            }
        }

        fun transition(
            phase: ClosedLoopPhase,
            cycle: Int,
            attempt: Int,
            message: String,
            outcome: ClosedLoopOutcome = ClosedLoopOutcome.RUNNING,
            evidence: ClosedLoopCycleEvidence? = snapshot.latestEvidence,
            fraction: Double = cycleFraction(cycle, config.maximumCycles)
        ) {
            val eventEvidence = evidence.takeUnless { phase == ClosedLoopPhase.TRAINING_MODEL || phase == ClosedLoopPhase.VALIDATING_DATASET }
            val event =
                ClosedLoopEvent(
                    eventIndex = snapshot.events.size + 1,
                    timestampEpochMillis = now(),
                    phase = phase,
                    cycle = cycle,
                    datasetRows = snapshot.currentDatasetRows,
                    trainingAttempt = attempt,
                    message = message,
                    runId = eventEvidence?.runId,
                    macroF1 = eventEvidence?.macroF1,
                    oracleDisagreementRate = eventEvidence?.oracleDisagreementRate,
                    modelPath = eventEvidence?.modelPath,
                    datasetPath = config.localTrainingConfig.datasetPath,
                    evaluationProfile = config.evaluationProfile,
                    effectiveRows = if(phase == ClosedLoopPhase.TRAINING_MODEL) minOf(snapshot.currentDatasetRows, config.localTrainingConfig.maximumRows.toLong(), config.maximumDatasetRows).toInt() else eventEvidence?.datasetRowsUsed,
                    corpusSha256 = eventEvidence?.corpusSha256
                )
            snapshot =
                snapshot.copy(
                    phase = phase,
                    outcome = outcome,
                    updatedAtEpochMillis = event.timestampEpochMillis,
                    currentTrainingAttempt = attempt,
                    latestEvidence = evidence,
                    statusMessage = message,
                    events = snapshot.events + event
                )
            persist()
            publishProgress(
                ClosedLoopProgress(
                    phase = phase,
                    cycle = cycle,
                    maximumCycles = config.maximumCycles,
                    datasetRows = snapshot.currentDatasetRows,
                    trainingAttempt = attempt,
                    fraction = fraction.coerceIn(0.0, 1.0),
                    message = message
                )
            )
        }

        fun terminal(
            phase: ClosedLoopPhase,
            outcome: ClosedLoopOutcome,
            cycle: Int,
            attempt: Int,
            message: String
        ): ClosedLoopSessionSnapshot {
            transition(
                phase = phase,
                cycle = cycle,
                attempt = attempt,
                message = message,
                outcome = outcome,
                fraction = if (outcome == ClosedLoopOutcome.ACCEPTED) 1.0 else snapshot.completedCycles.toDouble() / config.maximumCycles.toDouble()
            )
            return snapshot
        }

        var cycle = snapshot.completedCycles + 1
        var previousMacroF1 = snapshot.latestEvidence?.macroF1
        var stagnantCycles = 0

        try {
            if (resumeFrom != null) transition(ClosedLoopPhase.RETRYING, cycle, 0,
                "Resume checkpoint; training and gate unchanged. Policy before=${resumeFrom.config.copy(localTrainingConfig = config.localTrainingConfig)}; policy after=$config")
            while (cycle <= config.maximumCycles) {
                if (cancellationRequested.get()) {
                    return terminal(
                        ClosedLoopPhase.CANCELLED,
                        ClosedLoopOutcome.CANCELLED,
                        cycle,
                        0,
                        "Closed-loop session cancelled safely before cycle $cycle."
                    )
                }
                if (timedOut(config, invocationStartedAt)) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        0,
                        "The configured time budget was reached. The best stored model was preserved."
                    )
                }

                transition(
                    ClosedLoopPhase.VALIDATING_DATASET,
                    cycle,
                    0,
                    "Cycle $cycle: validating ${snapshot.currentDatasetRows} available dataset rows."
                )

                val rowsForTraining =
                    minOf(
                        snapshot.currentDatasetRows,
                        config.maximumDatasetRows,
                        config.localTrainingConfig.maximumRows.toLong()
                    ).toInt()
                if (rowsForTraining < MINIMUM_TRAINING_ROWS) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        0,
                        "At least $MINIMUM_TRAINING_ROWS valid rows are required before training."
                    )
                }

                var evidence: ClosedLoopCycleEvidence? = null
                var trainingFailureCount = 0
                var inferenceFailureCount = 0
                var attempt = 0
                while (evidence == null) {
                    if (cancellationRequested.get()) {
                        return terminal(
                            ClosedLoopPhase.CANCELLED,
                            ClosedLoopOutcome.CANCELLED,
                            cycle,
                            attempt,
                            "Closed-loop session cancelled safely."
                        )
                    }
                    attempt++
                    transition(
                        ClosedLoopPhase.TRAINING_MODEL,
                        cycle,
                        attempt,
                        "Cycle $cycle, attempt $attempt: training ${config.localTrainingConfig.resolvedFeatureSelections.size} feature set(s), judging ${config.evaluationProfile.displayName}; $rowsForTraining rows, seed ${config.localTrainingConfig.randomSeed}, ${snapshot.untrainedAppendedRows} mandatory newest rows. Same model/sampling parameters on retries; test metrics are reporting-only for this training run."
                    )
                    val candidate =
                        try {
                            cycleRunner.train(
                                request =
                                    ClosedLoopTrainingRequest(
                                        config =
                                            config.localTrainingConfig.copy(
                                                runName = "${config.sessionName}-cycle-$cycle-attempt-$attempt",
                                                maximumRows = rowsForTraining,
                                                sampleAcrossEntireDataset = true,
                                                expectedDatasetRows = snapshot.currentDatasetRows,
                                                requiredNewestRows = snapshot.untrainedAppendedRows
                                            ),
                                        evaluationProfile = config.evaluationProfile
                                ),
                                cancellationRequested = cancellationRequested,
                                onProgress = { localProgress ->
                                    publishProgress(
                                        ClosedLoopProgress(
                                            phase = ClosedLoopPhase.TRAINING_MODEL,
                                            cycle = cycle,
                                            maximumCycles = config.maximumCycles,
                                            datasetRows = snapshot.currentDatasetRows,
                                            trainingAttempt = attempt,
                                            fraction =
                                                ((cycle - 1) + localProgress.fraction * TRAINING_CYCLE_WEIGHT) /
                                                    config.maximumCycles.toDouble(),
                                            message = localProgress.message,
                                            localTrainingProgress = localProgress
                                        )
                                    )
                                }
                            )
                        } catch (error: Exception) {
                            if (cancellationRequested.get()) throw ClosedLoopCancellation()
                            trainingFailureCount++
                            if (trainingFailureCount > config.maximumTrainingRetries) {
                                return terminal(
                                    ClosedLoopPhase.FAILED,
                                    ClosedLoopOutcome.FAILED,
                                    cycle,
                                    attempt,
                                    "Training failed after $attempt attempt(s): ${error.message ?: error::class.java.simpleName}"
                                )
                            }
                            transition(
                                ClosedLoopPhase.RETRYING,
                                cycle,
                                attempt,
                                "Training error: ${error.message ?: error::class.java.simpleName}. Retrying with unchanged model, seed, split and dataset; no tuning or growth is implied."
                            )
                            null
                        }
                    if (candidate == null) continue
                    require(candidate.profile == config.evaluationProfile) { "Cycle returned a different judged profile." }
                    require(listOf(candidate.macroF1, candidate.accuracy, candidate.balancedAccuracy).all { it.isFinite() && it in 0.0..1.0 }) {
                        "Cycle returned invalid validation metrics; no gate decision or best-model update is permitted."
                    }
                    if (cancellationRequested.get()) {
                        return terminal(
                            ClosedLoopPhase.CANCELLED,
                            ClosedLoopOutcome.CANCELLED,
                            cycle,
                            attempt,
                            "Closed-loop session cancelled after the active training boundary."
                        )
                    }

                    transition(
                        ClosedLoopPhase.VERIFYING_INFERENCE,
                        cycle,
                        attempt,
                        candidate.inferenceMessage,
                        evidence = candidate
                    )
                    if (!candidate.inferenceValid) {
                        inferenceFailureCount++
                        if (inferenceFailureCount > config.maximumInferenceRetries) {
                            return terminal(
                                ClosedLoopPhase.MANUAL_INTERVENTION,
                                ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                                cycle,
                                attempt,
                                "Inference remained invalid after $attempt attempt(s). Manual intervention is required."
                            )
                        }
                        transition(
                            ClosedLoopPhase.RETRYING,
                            cycle,
                            attempt,
                            "Inference verification failed. Rebuilding the model (${inferenceFailureCount}/${config.maximumInferenceRetries}).",
                            evidence = candidate
                        )
                    } else {
                        evidence = candidate
                    }
                }

                val completedEvidence = requireNotNull(evidence)
                snapshot = updateBest(snapshot, completedEvidence)
                snapshot = snapshot.copy(completedCycles = cycle, untrainedAppendedRows = 0, currentCorpusSha256 = completedEvidence.corpusSha256 ?: snapshot.currentCorpusSha256)
                persist()
                transition(
                    ClosedLoopPhase.COMPARING_WITH_ORACLE,
                    cycle,
                    attempt,
                    "Validation oracle gate: macro-F1 ${format(completedEvidence.macroF1)}, " +
                        "solver disagreement ${formatPercent(completedEvidence.oracleDisagreementRate)}. " +
                        "${ClosedLoopAcceptanceGate.evaluate(config, completedEvidence).explanation}. " +
                        "Validation rows ${completedEvidence.validationRows ?: "legacy unknown"}; reporting test rows ${completedEvidence.reportingTestRows ?: "legacy unknown"}. " +
                        "Test is reporting-only per training run; growth may change partitions, so this is not a fixed independent final session holdout.",
                    evidence = completedEvidence,
                    fraction = (cycle - 0.10) / config.maximumCycles.toDouble()
                )

                if (ClosedLoopAcceptanceGate.evaluate(config, completedEvidence).accepted) {
                    return terminal(
                        ClosedLoopPhase.COMPLETED,
                        ClosedLoopOutcome.ACCEPTED,
                        cycle,
                        attempt,
                        "Accepted: the stored ${config.evaluationProfile.displayName} model meets both validation-label thresholds. No new IK solve or session-level independent-test claim is implied."
                    )
                }

                val improvement = previousMacroF1?.let { completedEvidence.macroF1 - it }
                stagnantCycles =
                    if (improvement != null && improvement < config.minimumMacroF1Improvement) {
                        stagnantCycles + 1
                    } else {
                        0
                    }
                previousMacroF1 = completedEvidence.macroF1

                if (stagnantCycles >= config.maximumStagnantCycles) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        attempt,
                        "Training stopped after $stagnantCycles stagnant cycle(s). The best model remains stored."
                    )
                }
                if (!config.automaticallyGrowDataset) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        attempt,
                        "The model is outside tolerance and automatic dataset growth is disabled."
                    )
                }
                if (cycle >= config.maximumCycles || snapshot.currentDatasetRows >= config.maximumDatasetRows) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        attempt,
                        "The acceptance gate was not reached within the configured cycle/data budget."
                    )
                }

                transition(
                    ClosedLoopPhase.GROWING_DATASET,
                    cycle,
                    attempt,
                    "Requesting ${config.samplesPerRobotIncrement} additional rows per robot from Data Factory.",
                    evidence = completedEvidence
                )
                val growth =
                    datasetGrower.grow(
                        request =
                            ClosedLoopDatasetGrowthRequest(
                                datasetName = config.datasetName,
                                samplesPerRobot = config.samplesPerRobotIncrement,
                                expectedCurrentRows = snapshot.currentDatasetRows,
                                maximumTotalRows = config.maximumDatasetRows,
                                maximumRowsEligibleForNextTraining = config.localTrainingConfig.maximumRows
                        ),
                        cancellationRequested = cancellationRequested,
                        onProgress = { growthProgress ->
                            publishProgress(
                                ClosedLoopProgress(
                                    phase = ClosedLoopPhase.GROWING_DATASET,
                                    cycle = cycle,
                                    maximumCycles = config.maximumCycles,
                                    datasetRows = snapshot.currentDatasetRows + growthProgress.addedRows,
                                    trainingAttempt = attempt,
                                    fraction =
                                        ((cycle - 1) + TRAINING_CYCLE_WEIGHT +
                                            growthProgress.fraction * (1.0 - TRAINING_CYCLE_WEIGHT)) /
                                            config.maximumCycles.toDouble(),
                                    message =
                                        "${growthProgress.message} " +
                                            "Robot ${growthProgress.currentRobotIndex + 1}/${growthProgress.totalRobots}; " +
                                            "${growthProgress.attempts} solver attempts."
                                )
                            )
                        }
                    )
                require(growth.addedRows >= 0 && growth.totalRows == snapshot.currentDatasetRows + growth.addedRows &&
                    growth.totalRows <= config.maximumDatasetRows && growth.addedRows <= config.localTrainingConfig.maximumRows) {
                    "Dataset growth returned an inconsistent row checkpoint or exceeded the next training cap."
                }
                snapshot =
                    snapshot.copy(
                        currentDatasetRows = growth.totalRows,
                        untrainedAppendedRows = growth.addedRows,
                        currentCorpusSha256 = java.io.File(config.localTrainingConfig.datasetPath).takeIf { it.isFile }?.let {
                            com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract.corpusDigest(it)
                        }
                    )
                if (cancellationRequested.get()) {
                    return terminal(
                        ClosedLoopPhase.CANCELLED,
                        ClosedLoopOutcome.CANCELLED,
                        cycle,
                        attempt,
                        "Closed-loop session cancelled after the dataset checkpoint; committed rows were preserved."
                    )
                }
                if (!growth.completed || growth.addedRows <= 0) {
                    return terminal(
                        ClosedLoopPhase.MANUAL_INTERVENTION,
                        ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
                        cycle,
                        attempt,
                        "Data Factory could not complete the requested growth: ${growth.message}"
                    )
                }
                transition(
                    ClosedLoopPhase.GROWING_DATASET,
                    cycle,
                    attempt,
                    "Data Factory added ${growth.addedRows} rows; ${growth.totalRows} rows are now available.",
                    evidence = completedEvidence,
                    fraction = cycle.toDouble() / config.maximumCycles.toDouble()
                )
                cycle++
            }
        } catch (error: CheckpointFailure) {
            return snapshot.copy(phase = ClosedLoopPhase.FAILED, outcome = ClosedLoopOutcome.FAILED,
                statusMessage = "Checkpoint publication failed; this result is not committed. The previous stored checkpoint remains available: ${error.cause?.message}")
        } catch (_: ClosedLoopCancellation) {
            return terminal(
                ClosedLoopPhase.CANCELLED,
                ClosedLoopOutcome.CANCELLED,
                cycle,
                snapshot.currentTrainingAttempt,
                "Closed-loop session cancelled safely."
            )
        } catch (error: Exception) {
            if (cancellationRequested.get()) {
                return terminal(
                    ClosedLoopPhase.CANCELLED,
                    ClosedLoopOutcome.CANCELLED,
                    cycle,
                    snapshot.currentTrainingAttempt,
                    "Closed-loop session cancelled safely at the failing work boundary."
                )
            }
            return terminal(
                ClosedLoopPhase.FAILED,
                ClosedLoopOutcome.FAILED,
                cycle,
                snapshot.currentTrainingAttempt,
                "Closed-loop failure: ${error.message ?: error::class.java.simpleName}"
            )
        }

        return terminal(
            ClosedLoopPhase.MANUAL_INTERVENTION,
            ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION,
            cycle,
            snapshot.currentTrainingAttempt,
            "The configured closed-loop budget ended before acceptance."
        )
    }

    private fun validate(config: ClosedLoopTrainingConfig, initialDatasetRows: Long) {
        require(config.localTrainingConfig.resolvedFeatureSelections.count { it.sourceProfile == config.evaluationProfile } == 1) {
            "Select exactly one trained feature set for the judged profile."
        }
        require(config.sessionName.isNotBlank()) { "Session name must not be blank." }
        require(config.datasetName.isNotBlank()) { "Dataset name must not be blank." }
        require(initialDatasetRows >= 0L) { "Dataset row count cannot be negative." }
        require(config.maximumCycles in 1..100) { "Maximum cycles must be between 1 and 100." }
        require(config.samplesPerRobotIncrement in 1..1_000_000) { "Dataset increment is outside its safe range." }
        require(config.maximumDatasetRows in MINIMUM_TRAINING_ROWS.toLong()..10_000_000L) {
            "Maximum dataset rows must be between $MINIMUM_TRAINING_ROWS and 10,000,000."
        }
        require(config.maximumInferenceRetries in 0..10) { "Inference retries must be between 0 and 10." }
        require(config.maximumTrainingRetries in 0..10) { "Training retries must be between 0 and 10." }
        require(config.minimumMacroF1.isFinite() && config.minimumMacroF1 in 0.0..1.0)
        require(
            config.maximumOracleDisagreementRate.isFinite() &&
                config.maximumOracleDisagreementRate in 0.0..1.0
        )
        require(config.minimumMacroF1Improvement.isFinite() && config.minimumMacroF1Improvement in 0.0..1.0)
        require(config.maximumStagnantCycles in 1..100)
        require(config.maximumElapsedMinutes in 0..100_000)
        require(config.localTrainingConfig.maximumRows >= MINIMUM_TRAINING_ROWS) {
            "The training row cap is below the minimum scientific training size."
        }
    }

    internal fun validateResume(config: ClosedLoopTrainingConfig, rows: Long, saved: ClosedLoopSessionSnapshot, verifyCorpus: Boolean = false) {
        require(saved.checkpointProtocolVersion == 1) { "This legacy checkpoint is review-only; its recovery contract is not supported. Start a new session." }
        saved.currentCorpusSha256?.takeIf { verifyCorpus }?.let { digest ->
            require(com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract.corpusDigest(java.io.File(config.localTrainingConfig.datasetPath)) == digest) {
                "Dataset bytes changed after the recovery checkpoint; start a new session after review."
            }
        }
        require(saved.outcome != ClosedLoopOutcome.ACCEPTED) { "An accepted session is complete; start a new session to change its experiment." }
        require(rows == saved.currentDatasetRows) { "Dataset row count changed outside this session; review it before starting a new session." }
        require(config.datasetName == saved.config.datasetName && config.evaluationProfile == saved.config.evaluationProfile) {
            "Resuming must preserve the dataset and judged profile. Start a new session for a different experiment."
        }
        require(config.localTrainingConfig == saved.config.localTrainingConfig) {
            "Resuming must preserve the training, split, feature and sampling contract. Only loop budgets may change."
        }
        require(config.minimumMacroF1 == saved.config.minimumMacroF1 && config.maximumOracleDisagreementRate == saved.config.maximumOracleDisagreementRate) {
            "Resuming cannot redefine the historical acceptance gate."
        }
        require(config.maximumCycles > saved.completedCycles) { "Increase the cycle budget before continuing this checkpoint." }
        require(saved.untrainedAppendedRows <= config.localTrainingConfig.maximumRows)
    }

    private fun updateBest(
        snapshot: ClosedLoopSessionSnapshot,
        candidate: ClosedLoopCycleEvidence
    ): ClosedLoopSessionSnapshot {
        val bestF1 = snapshot.bestMacroF1
        val bestDisagreement = snapshot.bestOracleDisagreementRate
        val better =
            bestF1 == null ||
                candidate.macroF1 > bestF1 + 1e-12 ||
                (abs(candidate.macroF1 - bestF1) <= 1e-12 &&
                    (bestDisagreement == null || candidate.oracleDisagreementRate < bestDisagreement))
        return if (better) {
            snapshot.copy(
                bestRunId = candidate.runId,
                bestModelPath = candidate.modelPath,
                bestMacroF1 = candidate.macroF1,
                bestOracleDisagreementRate = candidate.oracleDisagreementRate
            )
        } else {
            snapshot
        }
    }

    private fun timedOut(config: ClosedLoopTrainingConfig, startedAt: Long): Boolean =
        config.maximumElapsedMinutes > 0 &&
            now() - startedAt >= config.maximumElapsedMinutes * 60_000L

    private fun cycleFraction(cycle: Int, maximumCycles: Int): Double =
        (cycle - 1).coerceAtLeast(0).toDouble() / maximumCycles.toDouble()

    private fun buildSessionId(startedAt: Long, sessionName: String): String =
        "closed-loop-$startedAt-" +
            sessionName.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-', '.', '_')
                .take(40)
                .ifBlank { "experiment" }

    private fun format(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)

    private class CheckpointFailure(cause: Exception) : RuntimeException(cause)

    private class ClosedLoopCancellation : RuntimeException()

    companion object {
        private const val MINIMUM_TRAINING_ROWS = 30
        private const val TRAINING_CYCLE_WEIGHT = 0.80
    }
}
