package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticAllocationTracker
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.statistics.WilsonScoreInterval
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.ik.InverseKinematicsSolver
import kotlin.math.hypot

class NumericalSafetyExperiment(
    private val isCancellationRequested: () -> Boolean = { false },
    private val robotFactory: DiagnosticRobotFactory = DiagnosticRobotFactory()
) {
    fun run(
        config: NumericalSafetyExperimentConfig,
        onProgress: ((NumericalSafetyProgress) -> Unit)? = null
    ): NumericalSafetyReport {
        val startedAtNanos = System.nanoTime()
        DiagnosticAllocationTracker.reset()
        val trials = ArrayList<NumericalSafetyTrial>(config.totalTrialCount)
        var completed = 0

        publishProgress(
            onProgress,
            startedAtNanos,
            completed,
            config.totalTrialCount,
            "PLANNING",
            "Building deterministic paired safety-ablation cases."
        )

        for (linkCount in config.linkCounts) {
            for (jointMode in config.jointModes) {
                check(!isCancellationRequested()) { "Numerical-safety experiment cancelled." }
                val robot = robotFactory.buildSeedRobot(linkCount, jointMode, config.stressLevel)
                val topologySeed =
                    ScientificRandomProtocol.deriveSeed(
                        config.randomSeed,
                        PROTOCOL_ID,
                        "links=$linkCount",
                        "mode=${jointMode.name}"
                    )
                val random = ScientificRandom(topologySeed)
                var guardedState = homeState(robot)
                var unguardedState = homeState(robot)
                var precedingTargetState = homeState(robot)

                repeat(config.validTrialsPerTopology) { localIndex ->
                    check(!isCancellationRequested()) { "Numerical-safety experiment cancelled." }
                    val scenario = VALID_SCENARIOS[localIndex % VALID_SCENARIOS.size]
                    val sourceState =
                        buildValidTargetState(
                            robot = robot,
                            scenario = scenario,
                            random = random,
                            preceding = precedingTargetState
                        )
                    precedingTargetState = sourceState
                    val target = RawForwardKinematics.position(robot, sourceState.jointValues)
                    val trialSeed =
                        ScientificRandomProtocol.deriveSeed(
                            config.randomSeed,
                            PROTOCOL_ID,
                            "links=$linkCount",
                            "mode=${jointMode.name}",
                            "valid=$localIndex"
                        )
                    val trial =
                        runPair(
                            index = trials.size,
                            cohort = NumericalSafetyCohort.VALID_CAMPAIGN,
                            scenario = scenario,
                            seed = trialSeed,
                            robot = robot,
                            jointMode = jointMode,
                            guardedInitial = if (config.carryStateBetweenTargets) guardedState else homeState(robot),
                            unguardedInitial = if (config.carryStateBetweenTargets) unguardedState else homeState(robot),
                            target = target,
                            config = config
                        )
                    trials += trial
                    if (config.carryStateBetweenTargets) {
                        guardedState = trial.guarded.state
                        trial.unguarded?.let { unguardedState = it.state }
                    }
                    completed++
                    publishProgress(
                        onProgress,
                        startedAtNanos,
                        completed,
                        config.totalTrialCount,
                        "VALID_CAMPAIGN",
                        "Running matched valid targets with both numerical paths.",
                        linkCount,
                        jointMode
                    )
                }

                if (config.includeAdversarialProbes) {
                    ADVERSARIAL_SCENARIOS.forEachIndexed { localIndex, scenario ->
                        check(!isCancellationRequested()) { "Numerical-safety experiment cancelled." }
                        val probe = buildAdversarialProbe(robot, scenario)
                        val trialSeed =
                            ScientificRandomProtocol.deriveSeed(
                                config.randomSeed,
                                PROTOCOL_ID,
                                "links=$linkCount",
                                "mode=${jointMode.name}",
                                "fault=${scenario.name}"
                            )
                        trials +=
                            runPair(
                                index = trials.size,
                                cohort = NumericalSafetyCohort.ADVERSARIAL_PROBES,
                                scenario = scenario,
                                seed = trialSeed,
                                robot = probe.robot,
                                jointMode = jointMode,
                                guardedInitial = probe.initialState,
                                unguardedInitial = probe.initialState,
                                target = probe.target,
                                config = config
                            )
                        completed++
                        publishProgress(
                            onProgress,
                            startedAtNanos,
                            completed,
                            config.totalTrialCount,
                            "ADVERSARIAL_PROBES",
                            "Checking that injected numerical faults are rejected or contained.",
                            linkCount,
                            jointMode
                        )
                    }
                }
            }
        }

        publishProgress(
            onProgress,
            startedAtNanos,
            completed,
            config.totalTrialCount,
            "AGGREGATING",
            "Computing confidence intervals and paired impact metrics."
        )

        val validTrials = trials.filter { it.cohort == NumericalSafetyCohort.VALID_CAMPAIGN }
        val adversarialTrials = trials.filter { it.cohort == NumericalSafetyCohort.ADVERSARIAL_PROBES }
        val validGuarded = summarize(validTrials.map(NumericalSafetyTrial::guarded), NumericalSafetyBranch.GUARDED_PRODUCTION)
        val validUnguarded =
            validTrials.mapNotNull(NumericalSafetyTrial::unguarded)
                .takeIf(List<NumericalSafetyBranchObservation>::isNotEmpty)
                ?.let { summarize(it, NumericalSafetyBranch.UNGUARDED_REFERENCE) }
        val adversarialGuarded =
            adversarialTrials.map(NumericalSafetyTrial::guarded)
                .takeIf(List<NumericalSafetyBranchObservation>::isNotEmpty)
                ?.let { summarize(it, NumericalSafetyBranch.GUARDED_PRODUCTION) }
        val adversarialUnguarded =
            adversarialTrials.mapNotNull(NumericalSafetyTrial::unguarded)
                .takeIf(List<NumericalSafetyBranchObservation>::isNotEmpty)
                ?.let { summarize(it, NumericalSafetyBranch.UNGUARDED_REFERENCE) }
        val validImpact = validUnguarded?.let { impact(validTrials, validGuarded, it) }
        val adversarialImpact =
            if (adversarialGuarded != null && adversarialUnguarded != null) {
                impact(adversarialTrials, adversarialGuarded, adversarialUnguarded)
            } else {
                null
            }
        val cumulative = cumulativeResiduals(validTrials)
        val report =
            NumericalSafetyReport(
                protocolId = PROTOCOL_ID,
                createdAtEpochMillis = System.currentTimeMillis(),
                config = config,
                trials = trials,
                validGuardedSummary = validGuarded,
                validUnguardedSummary = validUnguarded,
                adversarialGuardedSummary = adversarialGuarded,
                adversarialUnguardedSummary = adversarialUnguarded,
                validImpact = validImpact,
                adversarialImpact = adversarialImpact,
                cumulativeValidResiduals = cumulative,
                interpretation = buildInterpretation(validGuarded, validUnguarded, validImpact, adversarialImpact)
            )

        publishProgress(
            onProgress,
            startedAtNanos,
            completed,
            config.totalTrialCount,
            "COMPLETED",
            "Safety-ablation report completed. Natural and injected-fault evidence remain separate."
        )
        return report
    }

    private fun runPair(
        index: Int,
        cohort: NumericalSafetyCohort,
        scenario: NumericalSafetyScenario,
        seed: Long,
        robot: RobotDefinition,
        jointMode: DiagnosticJointMode,
        guardedInitial: RobotState,
        unguardedInitial: RobotState,
        target: Vec3,
        config: NumericalSafetyExperimentConfig
    ): NumericalSafetyTrial {
        val guarded =
            DiagnosticAllocationTracker.measure("Protected IK branch") {
                runGuarded(robot, guardedInitial, target, config)
            }
        val unguarded =
            if (config.includeUnguardedReference) {
                DiagnosticAllocationTracker.measure("Unguarded reference branch") {
                    runUnguarded(robot, unguardedInitial, target, config)
                }
            } else {
                null
            }
        return NumericalSafetyTrial(
            index = index,
            cohort = cohort,
            scenario = scenario,
            seed = seed,
            linkCount = robot.joints.size,
            jointMode = jointMode,
            target = target,
            guarded = guarded,
            unguarded = unguarded
        )
    }

    private fun runGuarded(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3,
        config: NumericalSafetyExperimentConfig
    ): NumericalSafetyBranchObservation {
        val result =
            InverseKinematicsSolver(ForwardKinematicsSolver(), config.ikConfig)
                .solve(robot, initialState, target)
        val stateFinite = result.state.jointValues.size == robot.joints.size && result.state.jointValues.all(Double::isFinite)
        val limitsValid = stateWithinLimits(robot, result.state)
        val residual = independentResidual(robot, result.state, target)
        val reported = result.converged && result.status.isSuccess()
        val validated = reported && stateFinite && limitsValid && residual.isFinite() && residual <= config.ikConfig.tolerance
        val nonFinite =
            result.detailCode == IKDetailCode.NON_FINITE_ERROR ||
                result.detailCode == IKDetailCode.NON_FINITE_STEP ||
                result.detailCode == IKDetailCode.NON_FINITE_STEP_NORM ||
                (reported && (!stateFinite || !residual.isFinite()))
        return NumericalSafetyBranchObservation(
            branch = NumericalSafetyBranch.GUARDED_PRODUCTION,
            reportedConverged = reported,
            status = result.status.name,
            detailCode = result.detailCode.name,
            iterations = result.iterations,
            state = result.state,
            stateFinite = stateFinite,
            jointLimitsValid = limitsValid,
            independentResidualMeters = residual,
            validatedSuccess = validated,
            nonFiniteEncountered = nonFinite,
            unsafeSuccess = reported && !validated,
            exceptionContained = false,
            backtrackingRetryCount = result.diagnostics.backtrackingRetryCount,
            seedConditionNumber = result.diagnostics.seedConditionNumber,
            durationNanos = result.diagnostics.solveDurationNanos
        )
    }

    private fun runUnguarded(
        robot: RobotDefinition,
        initialState: RobotState,
        target: Vec3,
        config: NumericalSafetyExperimentConfig
    ): NumericalSafetyBranchObservation {
        val started = System.nanoTime()
        return try {
            val result = UnguardedDlsReferenceSolver(config.ikConfig).solve(robot, initialState, target)
            val stateFinite = result.state.jointValues.size == robot.joints.size && result.state.jointValues.all(Double::isFinite)
            val limitsValid = stateWithinLimits(robot, result.state)
            val residual = independentResidual(robot, result.state, target)
            val validated =
                result.converged && stateFinite && limitsValid &&
                    residual.isFinite() && residual <= config.ikConfig.tolerance
            NumericalSafetyBranchObservation(
                branch = NumericalSafetyBranch.UNGUARDED_REFERENCE,
                reportedConverged = result.converged,
                status = if (result.converged) "CONVERGED_UNCERTIFIED" else "MAX_ITERATIONS_UNCERTIFIED",
                detailCode = "NO_GUARDS_OR_FINAL_CERTIFICATION",
                iterations = result.iterations,
                state = result.state,
                stateFinite = stateFinite,
                jointLimitsValid = limitsValid,
                independentResidualMeters = residual,
                validatedSuccess = validated,
                nonFiniteEncountered = result.nonFiniteEncountered || !stateFinite || !residual.isFinite(),
                unsafeSuccess = result.converged && !validated,
                exceptionContained = false,
                backtrackingRetryCount = 0,
                seedConditionNumber = Double.NaN,
                durationNanos = (System.nanoTime() - started).coerceAtLeast(0L)
            )
        } catch (_: Exception) {
            NumericalSafetyBranchObservation(
                branch = NumericalSafetyBranch.UNGUARDED_REFERENCE,
                reportedConverged = false,
                status = "EXCEPTION_CONTAINED_BY_LAB",
                detailCode = "UNGUARDED_REFERENCE_EXCEPTION",
                iterations = 0,
                state = initialState,
                stateFinite = initialState.jointValues.all(Double::isFinite),
                jointLimitsValid = stateWithinLimits(robot, initialState),
                independentResidualMeters = Double.NaN,
                validatedSuccess = false,
                nonFiniteEncountered = true,
                unsafeSuccess = false,
                exceptionContained = true,
                backtrackingRetryCount = 0,
                seedConditionNumber = Double.NaN,
                durationNanos = (System.nanoTime() - started).coerceAtLeast(0L)
            )
        }
    }

    private fun summarize(
        observations: List<NumericalSafetyBranchObservation>,
        branch: NumericalSafetyBranch
    ): NumericalSafetyBranchSummary {
        val finiteResiduals =
            observations.map(NumericalSafetyBranchObservation::independentResidualMeters)
                .filter { it.isFinite() && it >= 0.0 }
                .sorted()
        val successes = observations.count(NumericalSafetyBranchObservation::validatedSuccess)
        return NumericalSafetyBranchSummary(
            branch = branch,
            trialCount = observations.size,
            validatedSuccessCount = successes,
            validatedSuccessInterval95 = WilsonScoreInterval.at95Percent(successes, observations.size),
            nonFiniteEncounterCount = observations.count(NumericalSafetyBranchObservation::nonFiniteEncountered),
            jointLimitViolationCount = observations.count { it.stateFinite && !it.jointLimitsValid },
            unsafeSuccessCount = observations.count(NumericalSafetyBranchObservation::unsafeSuccess),
            exceptionCount = observations.count(NumericalSafetyBranchObservation::exceptionContained),
            contaminatingTrainingCandidateCount = observations.count(NumericalSafetyBranchObservation::contaminatingTrainingCandidate),
            finiteResidualCount = finiteResiduals.size,
            residualSumMeters = finiteResiduals.sum(),
            medianResidualMeters = quantile(finiteResiduals, 0.50),
            p95ResidualMeters = quantile(finiteResiduals, 0.95),
            maximumResidualMeters = finiteResiduals.lastOrNull() ?: Double.NaN,
            meanIterations = observations.map { it.iterations.toDouble() }.averageOrNaN(),
            meanDurationMillis = observations.map { it.durationNanos / 1_000_000.0 }.averageOrNaN()
        )
    }

    private fun impact(
        trials: List<NumericalSafetyTrial>,
        guarded: NumericalSafetyBranchSummary,
        unguarded: NumericalSafetyBranchSummary
    ): NumericalSafetyImpact {
        var pairedRaw = 0.0
        var pairedGuarded = 0.0
        var pairedFinite = 0
        trials.forEach { trial ->
            val raw = trial.unguarded ?: return@forEach
            val guardedResidual = trial.guarded.independentResidualMeters
            val rawResidual = raw.independentResidualMeters
            if (
                trial.guarded.stateFinite && trial.guarded.jointLimitsValid &&
                raw.stateFinite && raw.jointLimitsValid &&
                guardedResidual.isFinite() && guardedResidual >= 0.0 &&
                rawResidual.isFinite() && rawResidual >= 0.0
            ) {
                pairedGuarded += guardedResidual
                pairedRaw += rawResidual
                pairedFinite++
            }
        }
        val reduction =
            if (pairedRaw > 0.0) {
                (pairedRaw - pairedGuarded) / pairedRaw * 100.0
            } else {
                Double.NaN
            }
        val timeCost =
            if (unguarded.meanDurationMillis > 0.0) {
                (guarded.meanDurationMillis / unguarded.meanDurationMillis - 1.0) * 100.0
            } else {
                Double.NaN
            }
        return NumericalSafetyImpact(
            pairedTrialCount = trials.size,
            pairedFiniteResidualCount = pairedFinite,
            guardedSuccessRate = guarded.validatedSuccessRate,
            unguardedSuccessRate = unguarded.validatedSuccessRate,
            successRateDeltaPercentagePoints = (guarded.validatedSuccessRate - unguarded.validatedSuccessRate) * 100.0,
            guardedContaminationRate = guarded.contaminationCandidateRate,
            unguardedContaminationRate = unguarded.contaminationCandidateRate,
            contaminationReductionPercentagePoints =
                (unguarded.contaminationCandidateRate - guarded.contaminationCandidateRate) * 100.0,
            rawNonFiniteEventsPrevented = trials.count { trial ->
                val raw = trial.unguarded
                raw != null && raw.nonFiniteEncountered && !trial.guarded.nonFiniteEncountered
            },
            rawLimitViolationsPrevented = trials.count { trial ->
                val raw = trial.unguarded
                raw != null && raw.stateFinite && !raw.jointLimitsValid && trial.guarded.jointLimitsValid
            },
            unsafeSuccessesPrevented = trials.count { trial ->
                trial.unguarded?.unsafeSuccess == true && !trial.guarded.unsafeSuccess
            },
            pairedRawResidualSumMeters = pairedRaw,
            pairedGuardedResidualSumMeters = pairedGuarded,
            pairedResidualReductionPercent = reduction,
            meanTimeCostPercent = timeCost,
            guardedBacktrackingRetries = trials.sumOf { it.guarded.backtrackingRetryCount },
            guardedSingularityInterventions = trials.count {
                it.guarded.detailCode == IKDetailCode.NEAR_SINGULARITY_WARNING.name ||
                    it.guarded.detailCode == IKDetailCode.CRITICAL_SINGULARITY_DAMPED.name
            }
        )
    }

    private fun cumulativeResiduals(trials: List<NumericalSafetyTrial>): List<NumericalSafetyCumulativePoint> {
        var guardedSum = 0.0
        var rawSum = 0.0
        var pairedFinite = 0
        return trials.mapIndexed { index, trial ->
            val raw = trial.unguarded
            if (
                raw != null &&
                trial.guarded.stateFinite && trial.guarded.jointLimitsValid &&
                raw.stateFinite && raw.jointLimitsValid &&
                trial.guarded.independentResidualMeters.isFinite() &&
                raw.independentResidualMeters.isFinite() &&
                trial.guarded.independentResidualMeters >= 0.0 &&
                raw.independentResidualMeters >= 0.0
            ) {
                guardedSum += trial.guarded.independentResidualMeters
                rawSum += raw.independentResidualMeters
                pairedFinite++
            }
            NumericalSafetyCumulativePoint(index + 1, guardedSum, rawSum, pairedFinite)
        }
    }

    private fun buildValidTargetState(
        robot: RobotDefinition,
        scenario: NumericalSafetyScenario,
        random: ScientificRandom,
        preceding: RobotState
    ): RobotState {
        val values =
            robot.joints.mapIndexed { index, joint ->
                val span = joint.maxValue - joint.minValue
                when (scenario) {
                    NumericalSafetyScenario.UNIFORM ->
                        random.nextDouble(joint.minValue + span * 0.05, joint.maxValue - span * 0.05)

                    NumericalSafetyScenario.NEAR_JOINT_LIMIT ->
                        if ((index + random.nextInt(2)) % 2 == 0) {
                            joint.minValue + span * 1e-8
                        } else {
                            joint.maxValue - span * 1e-8
                        }

                    NumericalSafetyScenario.NEAR_SINGULARITY ->
                        (if (0.0 in joint.minValue..joint.maxValue) 0.0 else joint.homeValue) +
                            (if (index % 2 == 0) 1.0 else -1.0) * span * 1e-8

                    NumericalSafetyScenario.MICRO_MOTION ->
                        (preceding.jointValues[index] +
                            (if (index % 2 == 0) 1.0 else -1.0) * span * 1e-7)
                            .coerceIn(joint.minValue, joint.maxValue)

                    else -> error("Adversarial scenario requested from valid target generator.")
                }.coerceIn(joint.minValue, joint.maxValue)
            }
        return RobotState(values)
    }

    private data class AdversarialProbe(
        val robot: RobotDefinition,
        val initialState: RobotState,
        val target: Vec3
    )

    private fun buildAdversarialProbe(
        robot: RobotDefinition,
        scenario: NumericalSafetyScenario
    ): AdversarialProbe {
        val home = homeState(robot)
        val validTarget = RawForwardKinematics.position(robot, home.jointValues)
        return when (scenario) {
            NumericalSafetyScenario.NON_FINITE_TARGET ->
                AdversarialProbe(robot, home, validTarget.copy(x = Double.NaN))

            NumericalSafetyScenario.INFINITE_TARGET ->
                AdversarialProbe(robot, home, validTarget.copy(z = Double.POSITIVE_INFINITY))

            NumericalSafetyScenario.NON_FINITE_INITIAL_STATE ->
                AdversarialProbe(
                    robot,
                    RobotState(home.jointValues.toMutableList().also { it[0] = Double.NaN }),
                    validTarget
                )

            NumericalSafetyScenario.OVERFLOWING_GEOMETRY ->
                AdversarialProbe(
                    robot.copy(
                        name = "${robot.name} · overflow probe",
                        dhParameters = robot.dhParameters.map { it.copy(a = Double.MAX_VALUE) }
                    ),
                    home,
                    Vec3.ZERO
                )

            else -> error("Valid scenario requested from adversarial probe generator.")
        }
    }

    private fun independentResidual(robot: RobotDefinition, state: RobotState, target: Vec3): Double =
        runCatching {
            val position = RawForwardKinematics.position(robot, state.jointValues)
            hypot(hypot(position.x - target.x, position.y - target.y), position.z - target.z)
        }.getOrDefault(Double.NaN)

    private fun stateWithinLimits(robot: RobotDefinition, state: RobotState): Boolean =
        state.jointValues.size == robot.joints.size &&
            state.jointValues.indices.all { index ->
                val value = state.jointValues[index]
                value.isFinite() && value in robot.joints[index].minValue..robot.joints[index].maxValue
            }

    private fun homeState(robot: RobotDefinition): RobotState = RobotState(robot.joints.map { it.homeValue })

    private fun IKStatus.isSuccess(): Boolean = this == IKStatus.SUCCESS || this == IKStatus.SUCCESS_WITH_WARNING

    private fun quantile(sorted: List<Double>, probability: Double): Double {
        if (sorted.isEmpty()) return Double.NaN
        val position = (sorted.size - 1) * probability.coerceIn(0.0, 1.0)
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction
    }

    private fun List<Double>.averageOrNaN(): Double = if (isEmpty()) Double.NaN else average()

    private fun buildInterpretation(
        guarded: NumericalSafetyBranchSummary,
        unguarded: NumericalSafetyBranchSummary?,
        validImpact: NumericalSafetyImpact?,
        adversarialImpact: NumericalSafetyImpact?
    ): String {
        if (unguarded == null || validImpact == null) {
            return "Only the guarded production path was executed. This certifies its observed behaviour but cannot estimate causal safety impact."
        }
        val natural =
            "On valid matched inputs, guarded success was ${percent(guarded.validatedSuccessRate)} " +
                "versus ${percent(unguarded.validatedSuccessRate)} for the isolated unguarded reference " +
                "(${signed(validImpact.successRateDeltaPercentagePoints)} percentage points). " +
                "The paired admissible-residual comparison covers ${validImpact.pairedFiniteResidualCount}/${validImpact.pairedTrialCount} trials."
        val faults =
            adversarialImpact?.let {
                " In injected-fault probes, ${it.rawNonFiniteEventsPrevented} unguarded non-finite events were contained by the guarded path."
            }.orEmpty()
        return natural + faults +
            " Aggregate residual is an error burden across commands, not measured physical trajectory drift; injected faults are never mixed into the natural-rate claim."
    }

    private fun percent(value: Double): String =
        if (value.isFinite()) java.lang.String.format(java.util.Locale.US, "%.2f%%", value * 100.0) else "N/A"

    private fun signed(value: Double): String =
        if (value.isFinite()) java.lang.String.format(java.util.Locale.US, "%+.2f", value) else "N/A"

    private fun publishProgress(
        callback: ((NumericalSafetyProgress) -> Unit)?,
        startedAtNanos: Long,
        completed: Int,
        total: Int,
        phase: String,
        message: String,
        linkCount: Int? = null,
        jointMode: DiagnosticJointMode? = null
    ) {
        if (callback == null) return
        val elapsed = (System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / 1_000_000_000.0
        val rate = if (elapsed > 0.0) completed / elapsed else 0.0
        val remaining = if (rate > 0.0) (total - completed).coerceAtLeast(0) / rate else Double.NaN
        callback(
            NumericalSafetyProgress(
                completedTrials = completed,
                totalTrials = total,
                phase = phase,
                message = message,
                currentLinkCount = linkCount,
                currentJointMode = jointMode,
                elapsedSeconds = elapsed,
                trialsPerSecond = rate,
                estimatedSecondsRemaining = remaining
            )
        )
    }

    companion object {
        const val PROTOCOL_ID = "rkl-numerical-safety-ablation-v1"

        private val VALID_SCENARIOS =
            listOf(
                NumericalSafetyScenario.UNIFORM,
                NumericalSafetyScenario.NEAR_JOINT_LIMIT,
                NumericalSafetyScenario.NEAR_SINGULARITY,
                NumericalSafetyScenario.MICRO_MOTION
            )

        private val ADVERSARIAL_SCENARIOS =
            listOf(
                NumericalSafetyScenario.NON_FINITE_TARGET,
                NumericalSafetyScenario.INFINITE_TARGET,
                NumericalSafetyScenario.NON_FINITE_INITIAL_STATE,
                NumericalSafetyScenario.OVERFLOWING_GEOMETRY
            )
    }
}
