package com.robotkinematicslab.mobile.ml.closedloop

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress
import java.util.concurrent.atomic.AtomicBoolean

enum class ClosedLoopPhase {
    IDLE,
    VALIDATING_DATASET,
    TRAINING_MODEL,
    VERIFYING_INFERENCE,
    COMPARING_WITH_ORACLE,
    GROWING_DATASET,
    RETRYING,
    COMPLETED,
    MANUAL_INTERVENTION,
    CANCELLED,
    FAILED
}

enum class ClosedLoopOutcome {
    RUNNING,
    ACCEPTED,
    NEEDS_MANUAL_INTERVENTION,
    CANCELLED,
    FAILED
}

data class ClosedLoopTrainingConfig(
    val sessionName: String,
    val datasetName: String,
    val localTrainingConfig: LocalTrainingConfig,
    val evaluationProfile: TrainingFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
    val maximumCycles: Int = 5,
    val samplesPerRobotIncrement: Int = 1_000,
    val maximumDatasetRows: Long = 100_000,
    val maximumInferenceRetries: Int = 3,
    val maximumTrainingRetries: Int = 1,
    val minimumMacroF1: Double = 0.80,
    val maximumOracleDisagreementRate: Double = 0.10,
    val minimumMacroF1Improvement: Double = 0.001,
    val maximumStagnantCycles: Int = 2,
    val maximumElapsedMinutes: Int = 0,
    val automaticallyGrowDataset: Boolean = true
)

data class ClosedLoopCycleEvidence(
    val runId: String,
    val profile: TrainingFeatureProfile,
    val datasetRowsUsed: Int,
    val macroF1: Double,
    val balancedAccuracy: Double,
    val accuracy: Double,
    val logLoss: Double,
    val independentTestMacroF1: Double,
    val independentTestBalancedAccuracy: Double,
    val independentTestAccuracy: Double,
    val independentTestLogLoss: Double,
    val inferenceNanosPerSample: Double,
    val inferenceValid: Boolean,
    val inferenceMessage: String,
    val modelPath: String,
    val trainingDurationMillis: Long,
    val parameterCount: Int,
    val validationRows: Int? = null,
    val reportingTestRows: Int? = null,
    val corpusSha256: String? = null,
    val modelSha256: String? = null,
    val featureSelectionId: String? = null
) {
    val oracleDisagreementRate: Double
        get() = 1.0 - accuracy
}

data class ClosedLoopDatasetGrowthResult(
    val addedRows: Int,
    val totalRows: Long,
    val completed: Boolean,
    val message: String
)

data class ClosedLoopDatasetGrowthProgress(
    val requestedRows: Int,
    val addedRows: Int,
    val attempts: Int,
    val currentRobotIndex: Int,
    val totalRobots: Int,
    val currentRobotName: String,
    val message: String
) {
    val fraction: Double
        get() = if (requestedRows > 0) addedRows.toDouble() / requestedRows.toDouble() else 0.0
}

data class ClosedLoopEvent(
    val eventIndex: Int,
    val timestampEpochMillis: Long,
    val phase: ClosedLoopPhase,
    val cycle: Int,
    val datasetRows: Long,
    val trainingAttempt: Int,
    val message: String,
    val runId: String? = null,
    val macroF1: Double? = null,
    val oracleDisagreementRate: Double? = null,
    val modelPath: String? = null,
    val datasetPath: String? = null,
    val evaluationProfile: TrainingFeatureProfile? = null,
    val effectiveRows: Int? = null,
    val corpusSha256: String? = null
)

data class ClosedLoopSessionSnapshot(
    val sessionId: String,
    val config: ClosedLoopTrainingConfig,
    val phase: ClosedLoopPhase,
    val outcome: ClosedLoopOutcome,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val currentDatasetRows: Long,
    val completedCycles: Int,
    val currentTrainingAttempt: Int,
    val bestRunId: String?,
    val bestModelPath: String?,
    val bestMacroF1: Double?,
    val bestOracleDisagreementRate: Double?,
    val latestEvidence: ClosedLoopCycleEvidence?,
    val statusMessage: String,
    val events: List<ClosedLoopEvent>,
    /** Rows committed by the latest growth checkpoint that have not yet entered a training sample. */
    val untrainedAppendedRows: Int = 0,
    val checkpointProtocolVersion: Int = 1,
    val currentCorpusSha256: String? = null
) {
    val isTerminal: Boolean
        get() = outcome != ClosedLoopOutcome.RUNNING
}

data class ClosedLoopProgress(
    val phase: ClosedLoopPhase,
    val cycle: Int,
    val maximumCycles: Int,
    val datasetRows: Long,
    val trainingAttempt: Int,
    val fraction: Double,
    val message: String,
    val localTrainingProgress: LocalTrainingProgress? = null
)

data class ClosedLoopTrainingRequest(
    val config: LocalTrainingConfig,
    val evaluationProfile: TrainingFeatureProfile
)

data class ClosedLoopDatasetGrowthRequest(
    val datasetName: String,
    val samplesPerRobot: Int,
    val expectedCurrentRows: Long,
    val maximumTotalRows: Long,
    /** Maximum complete append that the following bounded training sample can include. */
    val maximumRowsEligibleForNextTraining: Int = Int.MAX_VALUE
)

fun interface ClosedLoopSessionStore {
    fun save(snapshot: ClosedLoopSessionSnapshot)
}

interface ClosedLoopTrainingCycleRunner {
    fun train(
        request: ClosedLoopTrainingRequest,
        cancellationRequested: AtomicBoolean,
        onProgress: (LocalTrainingProgress) -> Unit
    ): ClosedLoopCycleEvidence
}

fun interface ClosedLoopDatasetGrower {
    fun grow(
        request: ClosedLoopDatasetGrowthRequest,
        cancellationRequested: AtomicBoolean,
        onProgress: (ClosedLoopDatasetGrowthProgress) -> Unit
    ): ClosedLoopDatasetGrowthResult
}


/** Validation-label feedback, never a fresh IK solve or a session-level independent test. */
data class ClosedLoopGateDecision(val profileMatches: Boolean, val evidenceValid: Boolean,
    val macroF1Passes: Boolean, val disagreementPasses: Boolean) {
    val accepted: Boolean get() = profileMatches && evidenceValid && macroF1Passes && disagreementPasses
    val explanation: String get() = listOf(
        "Judged profile: " + if(profileMatches) "matches" else "mismatch",
        "Inference/metrics: " + if(evidenceValid) "valid" else "invalid",
        "Validation macro-F1: " + if(macroF1Passes) "passes" else "fails",
        "Validation IK-label disagreement: " + if(disagreementPasses) "passes" else "fails").joinToString("; ")
}

object ClosedLoopAcceptanceGate {
    fun evaluate(config: ClosedLoopTrainingConfig, evidence: ClosedLoopCycleEvidence): ClosedLoopGateDecision {
        require(config.minimumMacroF1.isFinite() && config.minimumMacroF1 in 0.0..1.0)
        require(config.maximumOracleDisagreementRate.isFinite() && config.maximumOracleDisagreementRate in 0.0..1.0)
        val finite = listOf(evidence.macroF1, evidence.accuracy).all { it.isFinite() && it in 0.0..1.0 }
        return ClosedLoopGateDecision(evidence.profile == config.evaluationProfile, evidence.inferenceValid && finite,
            finite && evidence.macroF1 >= config.minimumMacroF1,
            finite && evidence.accuracy >= 1.0 - config.maximumOracleDisagreementRate)
    }
}

fun closedLoopCycleCount(count: Int): String = "$count " + if(count == 1) "completed cycle" else "completed cycles"
fun ClosedLoopOutcome.humanLabel(): String = when(this) {
    ClosedLoopOutcome.RUNNING -> "In progress"
    ClosedLoopOutcome.ACCEPTED -> "Validation gate met"
    ClosedLoopOutcome.NEEDS_MANUAL_INTERVENTION -> "Stopped for review"
    ClosedLoopOutcome.CANCELLED -> "Cancelled; saved evidence retained"
    ClosedLoopOutcome.FAILED -> "Failed; gate not completed"
}
