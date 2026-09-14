package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.statistics.BinomialConfidenceInterval
import com.robotkinematicslab.mobile.math.utility.Vec3

enum class NumericalSafetyCohort(val displayName: String) {
    VALID_CAMPAIGN("Valid deterministic campaign"),
    ADVERSARIAL_PROBES("Injected-fault probes")
}

enum class NumericalSafetyScenario(val displayName: String) {
    UNIFORM("Uniform reachable target"),
    NEAR_JOINT_LIMIT("Target close to a joint limit"),
    NEAR_SINGULARITY("Near-singular posture"),
    MICRO_MOTION("Micrometre-scale joint perturbation"),
    NON_FINITE_TARGET("Injected NaN target"),
    INFINITE_TARGET("Injected infinite target"),
    NON_FINITE_INITIAL_STATE("Injected NaN joint state"),
    OVERFLOWING_GEOMETRY("Injected overflowing DH geometry")
}

enum class NumericalSafetyBranch(val displayName: String) {
    GUARDED_PRODUCTION("Guarded production solver"),
    UNGUARDED_REFERENCE("Isolated unguarded DLS reference")
}

data class NumericalSafetyExperimentConfig(
    val linkCounts: List<Int> = listOf(3, 5, 10),
    val jointModes: List<DiagnosticJointMode> = DiagnosticJointMode.entries,
    val validTrialsPerTopology: Int = 8,
    val includeAdversarialProbes: Boolean = true,
    val includeUnguardedReference: Boolean = true,
    val carryStateBetweenTargets: Boolean = true,
    val randomSeed: Int = 42,
    val stressLevel: Double = 0.9,
    val ikConfig: IKConfig = IKConfig(
        maxIterations = 800,
        tolerance = 1e-6,
        damping = 0.01,
        maxStep = 0.02
    )
) {
    init {
        require(linkCounts.isNotEmpty() && linkCounts.distinct().size == linkCounts.size)
        require(linkCounts.all { it in 2..100 })
        require(jointModes.isNotEmpty() && jointModes.distinct().size == jointModes.size)
        require(validTrialsPerTopology in 1..10_000)
        require(stressLevel.isFinite() && stressLevel in 0.0..1.0)
        require(ikConfig.maxIterations in 1..10_000)
        require(ikConfig.tolerance.isFinite() && ikConfig.tolerance > 0.0)
        require(ikConfig.damping.isFinite() && ikConfig.damping > 0.0)
        require(ikConfig.maxStep.isFinite() && ikConfig.maxStep > 0.0)
    }

    val validTrialCount: Int
        get() = linkCounts.size * jointModes.size * validTrialsPerTopology

    val adversarialTrialCount: Int
        get() = if (includeAdversarialProbes) {
            linkCounts.size * jointModes.size * ADVERSARIAL_SCENARIO_COUNT
        } else {
            0
        }

    val totalTrialCount: Int
        get() = validTrialCount + adversarialTrialCount

    companion object {
        const val ADVERSARIAL_SCENARIO_COUNT = 4
    }
}

data class NumericalSafetyBranchObservation(
    val branch: NumericalSafetyBranch,
    val reportedConverged: Boolean,
    val status: String,
    val detailCode: String,
    val iterations: Int,
    val state: RobotState,
    val stateFinite: Boolean,
    val jointLimitsValid: Boolean,
    val independentResidualMeters: Double,
    val validatedSuccess: Boolean,
    val nonFiniteEncountered: Boolean,
    val unsafeSuccess: Boolean,
    val exceptionContained: Boolean,
    val backtrackingRetryCount: Int,
    val seedConditionNumber: Double,
    val durationNanos: Long
) {
    val contaminatingTrainingCandidate: Boolean
        get() =
            nonFiniteEncountered ||
                !stateFinite ||
                !jointLimitsValid ||
                unsafeSuccess ||
                exceptionContained
}

data class NumericalSafetyTrial(
    val index: Int,
    val cohort: NumericalSafetyCohort,
    val scenario: NumericalSafetyScenario,
    val seed: Long,
    val linkCount: Int,
    val jointMode: DiagnosticJointMode,
    val target: Vec3,
    val guarded: NumericalSafetyBranchObservation,
    val unguarded: NumericalSafetyBranchObservation?
)

data class NumericalSafetyBranchSummary(
    val branch: NumericalSafetyBranch,
    val trialCount: Int,
    val validatedSuccessCount: Int,
    val validatedSuccessInterval95: BinomialConfidenceInterval,
    val nonFiniteEncounterCount: Int,
    val jointLimitViolationCount: Int,
    val unsafeSuccessCount: Int,
    val exceptionCount: Int,
    val contaminatingTrainingCandidateCount: Int,
    val finiteResidualCount: Int,
    val residualSumMeters: Double,
    val medianResidualMeters: Double,
    val p95ResidualMeters: Double,
    val maximumResidualMeters: Double,
    val meanIterations: Double,
    val meanDurationMillis: Double
) {
    val validatedSuccessRate: Double
        get() = ratio(validatedSuccessCount, trialCount)

    val nonFiniteEncounterRate: Double
        get() = ratio(nonFiniteEncounterCount, trialCount)

    val jointLimitViolationRate: Double
        get() = ratio(jointLimitViolationCount, trialCount)

    val contaminationCandidateRate: Double
        get() = ratio(contaminatingTrainingCandidateCount, trialCount)

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) Double.NaN else numerator.toDouble() / denominator.toDouble()
}

data class NumericalSafetyImpact(
    val pairedTrialCount: Int,
    val pairedFiniteResidualCount: Int,
    val guardedSuccessRate: Double,
    val unguardedSuccessRate: Double,
    val successRateDeltaPercentagePoints: Double,
    val guardedContaminationRate: Double,
    val unguardedContaminationRate: Double,
    val contaminationReductionPercentagePoints: Double,
    val rawNonFiniteEventsPrevented: Int,
    val rawLimitViolationsPrevented: Int,
    val unsafeSuccessesPrevented: Int,
    val pairedRawResidualSumMeters: Double,
    val pairedGuardedResidualSumMeters: Double,
    val pairedResidualReductionPercent: Double,
    val meanTimeCostPercent: Double,
    val guardedBacktrackingRetries: Int,
    val guardedSingularityInterventions: Int
)

data class NumericalSafetyCumulativePoint(
    val trialIndex: Int,
    val guardedResidualSumMeters: Double,
    val unguardedResidualSumMeters: Double,
    val pairedFiniteCount: Int
)

data class NumericalSafetyReport(
    val protocolId: String,
    val createdAtEpochMillis: Long,
    val config: NumericalSafetyExperimentConfig,
    val trials: List<NumericalSafetyTrial>,
    val validGuardedSummary: NumericalSafetyBranchSummary,
    val validUnguardedSummary: NumericalSafetyBranchSummary?,
    val adversarialGuardedSummary: NumericalSafetyBranchSummary?,
    val adversarialUnguardedSummary: NumericalSafetyBranchSummary?,
    val validImpact: NumericalSafetyImpact?,
    val adversarialImpact: NumericalSafetyImpact?,
    val cumulativeValidResiduals: List<NumericalSafetyCumulativePoint>,
    val interpretation: String
)

data class NumericalSafetyProgress(
    val completedTrials: Int,
    val totalTrials: Int,
    val phase: String,
    val message: String,
    val currentLinkCount: Int? = null,
    val currentJointMode: DiagnosticJointMode? = null,
    val elapsedSeconds: Double = 0.0,
    val trialsPerSecond: Double = 0.0,
    val estimatedSecondsRemaining: Double = Double.NaN
)
