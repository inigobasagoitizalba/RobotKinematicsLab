package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import java.io.File
import java.util.Locale

object NumericalSafetyReportWriter {

    fun writeTrialCsv(report: NumericalSafetyReport, file: File) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.appendLine(CSV_HEADER)
            report.trials.forEach { trial ->
                writer.appendLine(csvRow(trial, trial.guarded))
                trial.unguarded?.let { writer.appendLine(csvRow(trial, it)) }
            }
        }
    }

    fun writeReadableReport(report: NumericalSafetyReport, file: File) {
        file.parentFile?.mkdirs()
        file.writeText(readableText(report))
    }

    fun readableText(report: NumericalSafetyReport): String = buildString {
        appendLine("Robot Kinematics Lab · Numerical Safety Ablation")
        appendLine("Protocol: ${report.protocolId}")
        appendLine("Created: ${report.createdAtEpochMillis}")
        appendLine("Seed: ${report.config.randomSeed}")
        appendLine("Link counts: ${report.config.linkCounts.joinToString()}")
        appendLine("Joint modes: ${report.config.jointModes.joinToString { it.name }}")
        appendLine("Valid trials per topology: ${report.config.validTrialsPerTopology}")
        appendLine("Tolerance: ${format(report.config.ikConfig.tolerance)} m")
        appendLine("Reference branch included: ${report.config.includeUnguardedReference}")
        appendLine("Injected faults included: ${report.config.includeAdversarialProbes}")
        appendLine()
        appendSummary("VALID · GUARDED", report.validGuardedSummary)
        report.validUnguardedSummary?.let { appendSummary("VALID · UNGUARDED REFERENCE", it) }
        report.validImpact?.let { appendImpact("VALID PAIRED IMPACT", it) }
        report.adversarialGuardedSummary?.let { appendSummary("FAULTS · GUARDED", it) }
        report.adversarialUnguardedSummary?.let { appendSummary("FAULTS · UNGUARDED REFERENCE", it) }
        report.adversarialImpact?.let { appendImpact("FAULT-CONTAINMENT IMPACT", it) }
        appendLine("INTERPRETATION")
        appendLine(report.interpretation)
        appendLine()
        appendLine("SCIENTIFIC BOUNDARY")
        appendLine(
            "The unguarded branch is an isolated negative control, not an alternative production solver. " +
                "It preserves the DH convention, fixed DLS damping, numerical-difference step and mixed-joint scaling, " +
                "while omitting validation, adaptive damping, clipping, step limits, backtracking, stagnation checks " +
                "and final certification. Aggregate residual is a sum across commands, not physical drift. " +
                "Injected-fault results are reported separately from valid-input incidence."
        )
    }

    private fun StringBuilder.appendSummary(title: String, summary: NumericalSafetyBranchSummary) {
        appendLine(title)
        appendLine("Trials: ${summary.trialCount}")
        appendLine("Certified successes: ${summary.validatedSuccessCount} (${percent(summary.validatedSuccessRate)})")
        appendLine(
            "Success Wilson 95% CI: ${percent(summary.validatedSuccessInterval95.lower)} to " +
                percent(summary.validatedSuccessInterval95.upper)
        )
        appendLine("Non-finite events: ${summary.nonFiniteEncounterCount}")
        appendLine("Joint-limit violations: ${summary.jointLimitViolationCount}")
        appendLine("Unsafe success claims: ${summary.unsafeSuccessCount}")
        appendLine("Contained exceptions: ${summary.exceptionCount}")
        appendLine("Potentially contaminating candidates: ${summary.contaminatingTrainingCandidateCount}")
        appendLine("Finite residual coverage: ${summary.finiteResidualCount}/${summary.trialCount}")
        appendLine("Residual sum: ${format(summary.residualSumMeters)} m")
        appendLine("Residual median: ${format(summary.medianResidualMeters)} m")
        appendLine("Residual p95: ${format(summary.p95ResidualMeters)} m")
        appendLine("Mean iterations: ${format(summary.meanIterations)}")
        appendLine("Mean duration: ${format(summary.meanDurationMillis)} ms")
        appendLine()
    }

    private fun StringBuilder.appendImpact(title: String, impact: NumericalSafetyImpact) {
        appendLine(title)
        appendLine("Success delta: ${signed(impact.successRateDeltaPercentagePoints)} percentage points")
        appendLine("Contamination-candidate reduction: ${signed(impact.contaminationReductionPercentagePoints)} percentage points")
        appendLine("Non-finite events prevented: ${impact.rawNonFiniteEventsPrevented}")
        appendLine("Limit violations prevented: ${impact.rawLimitViolationsPrevented}")
        appendLine("Unsafe successes prevented: ${impact.unsafeSuccessesPrevented}")
        appendLine("Paired finite in-limit residual coverage: ${impact.pairedFiniteResidualCount}/${impact.pairedTrialCount}")
        appendLine("Paired raw residual sum: ${format(impact.pairedRawResidualSumMeters)} m")
        appendLine("Paired guarded residual sum: ${format(impact.pairedGuardedResidualSumMeters)} m")
        appendLine("Paired residual reduction: ${percentPoints(impact.pairedResidualReductionPercent)}")
        appendLine("Mean time cost: ${signed(impact.meanTimeCostPercent)}%")
        appendLine("Guarded backtracking retries: ${impact.guardedBacktrackingRetries}")
        appendLine("Guarded singularity interventions: ${impact.guardedSingularityInterventions}")
        appendLine()
        appendLine("Timing note: short-run duration is indicative device telemetry, not a controlled performance benchmark.")
        appendLine()
    }

    private fun csvRow(trial: NumericalSafetyTrial, observation: NumericalSafetyBranchObservation): String =
        listOf(
            trial.index,
            trial.cohort.name,
            trial.scenario.name,
            trial.seed,
            trial.linkCount,
            trial.jointMode.name,
            format(trial.target.x),
            format(trial.target.y),
            format(trial.target.z),
            observation.branch.name,
            observation.reportedConverged,
            observation.validatedSuccess,
            observation.status,
            observation.detailCode,
            observation.iterations,
            observation.stateFinite,
            observation.jointLimitsValid,
            format(observation.independentResidualMeters),
            observation.nonFiniteEncountered,
            observation.unsafeSuccess,
            observation.exceptionContained,
            observation.contaminatingTrainingCandidate,
            observation.backtrackingRetryCount,
            format(observation.seedConditionNumber),
            observation.durationNanos,
            observation.state.jointValues.joinToString("|") { format(it) }
        ).joinToString(",") { escapeCsv(it.toString()) }

    private fun escapeCsv(value: String): String {
        if (',' !in value && '"' !in value && '\n' !in value && '\r' !in value) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun format(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value == Double.POSITIVE_INFINITY -> "Infinity"
            value == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> java.lang.String.format(Locale.US, "%.12g", value)
        }

    private fun percent(value: Double): String =
        if (value.isFinite()) java.lang.String.format(Locale.US, "%.3f%%", value * 100.0) else "N/A"

    private fun percentPoints(value: Double): String =
        if (value.isFinite()) java.lang.String.format(Locale.US, "%.3f%%", value) else "N/A"

    private fun signed(value: Double): String =
        if (value.isFinite()) java.lang.String.format(Locale.US, "%+.3f", value) else "N/A"

    private const val CSV_HEADER =
        "trialIndex,cohort,scenario,seed,linkCount,jointMode,targetX,targetY,targetZ,branch," +
            "reportedConverged,validatedSuccess,status,detailCode,iterations,stateFinite,jointLimitsValid," +
            "independentResidualMeters,nonFiniteEncountered,unsafeSuccess,exceptionContained," +
            "contaminatingTrainingCandidate,backtrackingRetryCount,seedConditionNumber,durationNanos,jointValues"
}
