package com.robotkinematicslab.mobile.ui.diagnostic

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.*
import com.robotkinematicslab.mobile.diagnostics.benchmark.planning.*
import com.robotkinematicslab.mobile.storage.DiagnosticDraft
import com.robotkinematicslab.mobile.ui.input.ScientificNumberParser

/** A validated form produces exactly one config and its actual planner result for preview and run. */
data class DiagnosticSetupEvaluation(val config: DiagnosticBenchmarkConfig?, val plan: DiagnosticBenchmarkPlan?,
    val errors: Map<String, String>, val oracleRuns: Long = 0) {
    val canRun get() = config != null && plan != null && errors.isEmpty()
    val totalSolverRuns: Long get() = (plan?.totalPlannedSequentialRuns ?: 0) + oracleRuns
    val breakdown: String get() = if(config == null || plan == null) "Complete the highlighted controls to calculate a plan." else
        "${plan.linkCounts.size} link counts × ${plan.samplesPerLinkCount} sequential samples × ${config.seeds.seeds.size} distinct seeds × ${if(config.topology.runAllTopologies) DiagnosticJointMode.entries.size else 1} topology modes = ${plan.totalPlannedSequentialRuns} sequential IK runs. Plus $oracleRuns reachable-target oracle checks = $totalSolverRuns planned solver runs. The target pool is reused, not another multiplier."
}

object DiagnosticSetupContract {
    fun evaluate(draft: DiagnosticDraft): DiagnosticSetupEvaluation {
        val errors = linkedMapOf<String, String>()
        fun integer(key: String, text: String, range: IntRange): Int {
            val value = ScientificNumberParser.parseInt(text)
            if(value == null || value !in range) errors[key] = "$key must be a whole number in ${range.first}–${range.last}."
            return value ?: range.first
        }
        fun decimal(key: String, text: String, minimum: Double, maximum: Double): Double {
            val value = ScientificNumberParser.parseDouble(text)
            if(value == null || !value.isFinite() || value !in minimum..maximum) errors[key] = "$key must be finite in $minimum–$maximum."
            return value ?: minimum
        }
        val reachable = integer("Reachable targets", draft.reachableCountText, 0..200)
        val unreachable = integer("Unreachable targets", draft.unreachableCountText, 0..200)
        if(reachable.toLong() + unreachable.toLong() !in 1L..200L) errors["Target pool"] = "The combined target pool must contain 1–200 cases."
        val maxLinks = if(draft.experimentalMode) 100 else 10
        val minimum = integer(if(draft.manualRangeMode) "Minimum links" else "Robot links",
            if(draft.manualRangeMode) draft.minLinkCountText else draft.robotLinkCountText, 2..maxLinks)
        val maximum = if(draft.manualRangeMode) integer("Maximum links", draft.maxLinkCountText, 2..maxLinks) else minimum
        if(maximum < minimum) errors["Maximum links"] = "Maximum links must be at least minimum links."
        val samples = if(draft.unlimitedSamplesEnabled) integer("Extended samples", draft.unlimitedSampleCountText, 1..1_000_000)
            else integer("Samples per link", draft.samplesPerLinkCountText, 1..5_000)
        if(draft.unlimitedSamplesEnabled && !draft.experimentalMode) errors["Extended samples"] = "Extended samples require Experimental mode. Normal sampling supports 1–5,000 samples per link."
        val tokens = draft.seedText.split(',')
        val parsed = tokens.map { ScientificNumberParser.parseInt(it.trim()) }
        if(parsed.any { it == null } || tokens.isEmpty()) errors["Seeds"] = "Enter signed integer seeds separated by commas; no blank or invalid token is ignored."
        val seeds = parsed.filterNotNull().distinct()
        val iterations = integer("IK iterations", draft.ikMaxIterationsText, 1..10_000)
        val tolerance = decimal("IK tolerance (m)", draft.ikToleranceText, 1e-9, 1.0)
        val damping = decimal("IK damping", draft.ikDampingText, 1e-9, 10.0)
        val step = decimal("IK maximum step", draft.ikMaxStepText, 1e-6, 10.0)
        if(!draft.stressLevel.isFinite() || draft.stressLevel !in 0f..1f) errors["Stress"] = "Stress must be in 0–1."
        if(errors.isNotEmpty()) return DiagnosticSetupEvaluation(null, null, errors)
        val config = DiagnosticBenchmarkConfig(
            sampling = DiagnosticSamplingConfig(reachableCount = reachable, unreachableCount = unreachable,
                runCount = samples.coerceAtMost(100_000), samplesPerLinkCount = if(draft.unlimitedSamplesEnabled) 500 else samples,
                unlimitedSamplesEnabled = draft.unlimitedSamplesEnabled, unlimitedSampleCount = if(draft.unlimitedSamplesEnabled) samples else 5000),
            seeds = DiagnosticSeedConfig(seeds),
            topology = DiagnosticTopologyConfig(robotLinkCount = minimum, minLinkCount = minimum, maxLinkCount = maximum,
                jointMode = draft.jointMode, runAllTopologies = draft.runAllTopologies, stressLevel = draft.stressLevel.toDouble(), experimentalModeEnabled = draft.experimentalMode),
            solver = DiagnosticSolverConfig(iterations, tolerance, damping, step),
            performance = DiagnosticPerformanceConfig(storeFullRunHistory = draft.storeFullRunHistory,
                storePerCaseDetails = true, storeTransitionDetails = true, storeExtremeRunDetails = true,
                exportRunHistoryToCsv = false, exportCaseResultsToCsv = false))
        val plan = DiagnosticBenchmarkPlanner().buildPlan(config)
        val oracle = plan.linkCounts.size.toLong() * seeds.size * (if(draft.runAllTopologies) DiagnosticJointMode.entries.size else 1) * reachable
        return DiagnosticSetupEvaluation(config, plan, emptyMap(), oracle)
    }
}

enum class DiagnosticSolverPreset(val label: String, val config: DiagnosticSolverConfig) {
    BALANCED("Balanced solver", DiagnosticSolverConfig(800, 1e-5, 0.05, 0.02)),
    PRECISION("Precision solver", DiagnosticSolverConfig(1200, 5e-6, 0.04, 0.015)),
    EXPLORATION("Exploration solver", DiagnosticSolverConfig(800, 1e-5, 0.04, 0.03))
}
