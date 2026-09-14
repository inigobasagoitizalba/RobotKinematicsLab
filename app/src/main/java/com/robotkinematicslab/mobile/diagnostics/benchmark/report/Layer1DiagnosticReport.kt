package com.robotkinematicslab.mobile.diagnostics.benchmark.report

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticTargetCase
import com.robotkinematicslab.mobile.diagnostics.benchmark.planning.DiagnosticBenchmarkPlan
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticCaseResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticVerdict
import com.robotkinematicslab.mobile.diagnostics.benchmark.validation.DiagnosticTopologyAuditRecord

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.util.Locale

data class Layer1DiagnosticReport(
    val experimentName: String,
    val config: DiagnosticBenchmarkConfig,
    val benchmarkPlan: DiagnosticBenchmarkPlan,
    val finalVerdict: DiagnosticVerdict,
    val robot: RobotDefinition,
    val initialState: RobotState,
    val targetCases: List<DiagnosticTargetCase>,
    val cases: List<DiagnosticCaseResult>,
    val runResults: List<DiagnosticRunResult>,
    val perCaseAggregates: List<DiagnosticPerCaseAggregate>,
    val transitionAggregates: List<DiagnosticTransitionAggregate>,
    val extremeRuns: List<DiagnosticExtremeRunSummary>,
    val linkCountAggregates: List<LinkCountAggregate>,
    val seedAggregates: List<SeedAggregate>,
    val seedComparisonSummary: SeedComparisonSummary,
    val topologyAggregates: List<TopologyAggregate>,
    val topologyAuditRecords: List<DiagnosticTopologyAuditRecord> = emptyList(),
    val statusDistribution: List<DiagnosticStatusDistributionItem>,
    val detailCodeDistribution: List<DiagnosticDetailCodeDistributionItem>,
    val datasetAcceptanceSummary: DiagnosticDatasetAcceptanceSummary,
    val fallbackSummary: DiagnosticFallbackSummary,
    val summary: DiagnosticSummary
) {

    fun toHumanReadableText(): String {
        return buildString {
            appendLine("=== Layer 1 Diagnostic Experiment ===")
            appendLine("Experiment: $experimentName")
            appendLine("Seed: ${config.seeds.seeds.joinToString()}")
            appendLine("Deterministic random protocol: ${ScientificRandomProtocol.ID}")
            appendLine("Requested reachable target pool size: ${config.sampling.reachableCount}")
            appendLine("Requested unreachable target pool size: ${config.sampling.unreachableCount}")
            appendLine("Legacy requested sequential solver runs: ${config.sampling.runCount}")
            appendLine("Planned samples per link count: ${benchmarkPlan.samplesPerLinkCount}")
            appendLine("Planned total sequential solver runs: ${benchmarkPlan.totalPlannedSequentialRuns}")
            appendLine("Robot: ${robot.name}")
            appendLine("Joint count: ${robot.joints.size}")
            appendLine("Initial state: ${initialState.jointValues}")
            appendLine()

            appendLine("--- Scientific Metric Policy ---")
            appendLine("Near-success error threshold (m): ${config.metricPolicy.nearSuccessErrorMeters}")
            appendLine("Close-miss error threshold (m): ${config.metricPolicy.closeMissErrorMeters}")
            appendLine("Stalled improvement-ratio epsilon: ${config.metricPolicy.stalledImprovementRatioEpsilon}")
            appendLine("Joint-limit margin ratio: ${config.metricPolicy.jointLimitMarginRatio}")
            appendLine(
                "Seed-distance boundaries (m): ${config.metricPolicy.easySeedDistanceUpperMeters}, " +
                    "${config.metricPolicy.mediumSeedDistanceUpperMeters}, " +
                    config.metricPolicy.hardSeedDistanceUpperMeters
            )
            appendLine("Log condition-number cap: ${config.metricPolicy.logConditionNumberCap}")
            appendLine()

            appendLine("--- Final Diagnostic Verdict ---")
            appendLine("Verdict: $finalVerdict")
            appendLine()

            appendLine("--- Benchmark Plan ---")
            appendLine("Reliability claim: ${benchmarkPlan.reliabilityClaim}")
            appendLine("Experimental mode: ${benchmarkPlan.isExperimental}")
            appendLine("Unlimited sample mode: ${benchmarkPlan.isUnlimited}")
            appendLine("Link counts: ${benchmarkPlan.linkCounts.joinToString()}")
            appendLine(
                "Safe link counts: ${
                    if (benchmarkPlan.safeLinkCounts.isEmpty()) {
                        "none"
                    } else {
                        benchmarkPlan.safeLinkCounts.joinToString()
                    }
                }"
            )

            appendLine(
                "Experimental link counts: ${
                    if (benchmarkPlan.experimentalLinkCounts.isEmpty()) {
                        "none"
                    } else {
                        benchmarkPlan.experimentalLinkCounts.joinToString()
                    }
                }"
            )

            appendLine("Strong reliability claim allowed: ${benchmarkPlan.strongReliabilityClaimAllowed}")
            appendLine("Samples per link count: ${benchmarkPlan.samplesPerLinkCount}")
            appendLine("Total planned sequential runs: ${benchmarkPlan.totalPlannedSequentialRuns}")
            appendLine(
                "Warnings: ${
                    if (benchmarkPlan.warnings.isEmpty()) {
                        "none"
                    } else {
                        benchmarkPlan.warnings.joinToString(separator = " | ")
                    }
                }"
            )
            appendLine()

            appendLine("--- Topology Audit Records ---")
            if (topologyAuditRecords.isEmpty()) {
                appendLine("No topology audit records were attached to this report.")
            } else {
                appendLine("Audited topologies: ${topologyAuditRecords.size}")
                appendLine("Accepted topologies: ${topologyAuditRecords.count { it.acceptedForBenchmark }}")
                appendLine("Rejected topologies: ${topologyAuditRecords.count { !it.acceptedForBenchmark }}")
                appendLine("Safe reliability-claim topologies: ${topologyAuditRecords.count { it.safeModeReliabilityClaimAllowed }}")
                appendLine()

                topologyAuditRecords.forEach { record ->
                    appendLine(
                        "Seed=${record.seed}, " +
                                "links=${record.linkCount}, " +
                                "mode=${record.jointMode}, " +
                                "label=${record.summaryLabel}, " +
                                "accepted=${record.acceptedForBenchmark}, " +
                                "safeClaim=${record.safeModeReliabilityClaimAllowed}"
                    )
                    appendLine("  Estimated reach: ${formatDouble(record.estimatedReachMeters)} m")
                    appendLine("  Revolute joints: ${record.revoluteCount}")
                    appendLine("  Prismatic joints: ${record.prismaticCount}")
                    appendLine("  Prismatic ratio: ${formatDouble(record.prismaticRatio)}")
                    appendLine("  Issues: total=${record.issueCount}, errors=${record.errorCount}, warnings=${record.warningCount}, info=${record.infoCount}")

                    record.issues.forEach { issue ->
                        appendLine("    - ${issue.severity}: ${issue.code} — ${issue.message}")
                    }

                    appendLine()
                }
            }
            appendLine()

            appendLine("--- IK Config Used ---")
            appendLine("Max iterations: ${config.solver.ikMaxIterations}")
            appendLine("Tolerance: ${formatDouble(config.solver.ikTolerance)}")
            appendLine("Damping: ${formatDouble(config.solver.ikDamping)}")
            appendLine("Max step: ${formatDouble(config.solver.ikMaxStep)}")
            appendLine()

            appendLine("--- Summary ---")
            appendLine("Target cases: ${summary.totalCases}")
            appendLine("Total runs: ${summary.totalRuns}")
            appendLine("Expected reachable target cases: ${summary.expectedReachableCount}")
            appendLine("Expected unreachable target cases: ${summary.expectedUnreachableCount}")
            appendLine()

            appendLine("--- FK-Proven Reachable Oracle Check ---")
            appendLine("Oracle reachable runs: ${summary.oracleReachableRuns}")
            appendLine("Oracle reachable accepted: ${summary.oracleReachableAccepted}")
            appendLine("Oracle reachable rejected: ${summary.oracleReachableRejected}")
            appendLine()

            appendLine("--- Sequential Stability Check ---")
            appendLine("Sequential runs: ${summary.sequentialRuns}")
            appendLine("Sequential accepted: ${summary.sequentialAcceptedCount}")
            appendLine("Sequential rejected: ${summary.sequentialRejectedCount}")
            appendLine("Sequential reachable runs: ${summary.sequentialReachableRuns}")
            appendLine("Sequential reachable accepted: ${summary.sequentialReachableAccepted}")
            appendLine("Sequential reachable rejected: ${summary.sequentialReachableRejected}")
            appendLine("Sequential unreachable runs: ${summary.sequentialUnreachableRuns}")
            appendLine("Sequential unreachable accepted: ${summary.sequentialUnreachableAccepted}")
            appendLine("Sequential unreachable rejected: ${summary.sequentialUnreachableRejected}")
            appendLine("Average initial error: ${formatDouble(summary.averageSequentialInitialError)} m")
            appendLine("Average final error: ${formatDouble(summary.averageSequentialError)} m")
            appendLine("Max final error: ${formatDouble(summary.maxSequentialError)} m")
            appendLine("Average improvement: ${formatDouble(summary.averageSequentialImprovement)} m")
            appendLine("Average improvement ratio: ${formatDouble(summary.averageSequentialImprovementRatio)}")
            appendLine("Average iterations: ${formatDouble(summary.averageSequentialIterations)}")
            appendLine("Average iteration saturation: ${formatDouble(summary.averageIterationSaturationRatio)}")
            appendLine()

            appendLine("--- Dataset Acceptance Summary ---")
            appendLine("Reachable runs: ${datasetAcceptanceSummary.reachableRunCount}")
            appendLine("Reachable accepted: ${datasetAcceptanceSummary.reachableAcceptedCount}")
            appendLine("Reachable rejected / false rejects: ${datasetAcceptanceSummary.reachableRejectedCount}")
            appendLine("Reachable acceptance rate: ${formatDouble(datasetAcceptanceSummary.reachableAcceptanceRate)}")
            appendLine(
                "Reachable acceptance Wilson 95% CI: " +
                    "[${formatDouble(datasetAcceptanceSummary.reachableAcceptanceConfidence95.lower)}, " +
                    "${formatDouble(datasetAcceptanceSummary.reachableAcceptanceConfidence95.upper)}]"
            )
            appendLine("Unreachable runs: ${datasetAcceptanceSummary.unreachableRunCount}")
            appendLine("Unreachable accepted / false accepts: ${datasetAcceptanceSummary.unreachableAcceptedCount}")
            appendLine("Unreachable rejected: ${datasetAcceptanceSummary.unreachableRejectedCount}")
            appendLine("Unreachable rejection rate: ${formatDouble(datasetAcceptanceSummary.unreachableRejectionRate)}")
            appendLine(
                "Unreachable rejection Wilson 95% CI: " +
                    "[${formatDouble(datasetAcceptanceSummary.unreachableRejectionConfidence95.lower)}, " +
                    "${formatDouble(datasetAcceptanceSummary.unreachableRejectionConfidence95.upper)}]"
            )
            appendLine("False accepts: ${datasetAcceptanceSummary.falseAcceptCount}")
            appendLine("False rejects: ${datasetAcceptanceSummary.falseRejectCount}")
            appendLine()

            appendLine("--- Status Distribution ---")
            if (statusDistribution.isEmpty()) {
                appendLine("No sequential status data.")
            } else {
                statusDistribution.forEach { item ->
                    appendLine(
                        "${item.status}: count=${item.count}, ratio=${formatDouble(item.ratio)}"
                    )
                }
            }
            appendLine()

            appendLine("--- Detail Code Distribution ---")
            if (detailCodeDistribution.isEmpty()) {
                appendLine("No sequential detail-code data.")
            } else {
                detailCodeDistribution.forEach { item ->
                    appendLine(
                        "${item.detailCode}: count=${item.count}, ratio=${formatDouble(item.ratio)}"
                    )
                }
            }
            appendLine()

            appendLine("--- Progress Class Distribution ---")
            appendLine("Solved: ${summary.solvedCount}")
            appendLine("Near solved: ${summary.nearSolvedCount}")
            appendLine("Improved but not enough: ${summary.improvedButNotEnoughCount}")
            appendLine("Stalled: ${summary.stalledCount}")
            appendLine("Worsened: ${summary.worsenedCount}")
            appendLine("Invalid numerical: ${summary.invalidNumericalCount}")
            appendLine()

            appendLine("--- Seed Distance Buckets ---")
            appendLine("Easy: ${summary.easySeedRuns}")
            appendLine("Medium: ${summary.mediumSeedRuns}")
            appendLine("Hard: ${summary.hardSeedRuns}")
            appendLine("Extreme: ${summary.extremeSeedRuns}")
            appendLine("Unknown: ${summary.unknownSeedRuns}")
            appendLine()

            appendLine("--- Joint Motion / Joint Limit Diagnostics ---")
            appendLine("Average joint delta norm: ${formatDouble(summary.averageJointDeltaNorm)}")
            appendLine("Average max single-joint movement: ${formatDouble(summary.averageMaxSingleJointMovement)}")
            appendLine("Average joint limit pressure ratio: ${formatDouble(summary.averageJointLimitPressureRatio)}")
            appendLine("Runs with near joint limit: ${summary.runsWithNearJointLimit}")
            appendLine()

            appendLine("--- Fallback / Clamp Diagnostics ---")
            appendLine("Sequential runs: ${fallbackSummary.sequentialRunCount}")
            appendLine("Accepted runs: ${fallbackSummary.acceptedRunCount}")
            appendLine("Rejected runs: ${fallbackSummary.rejectedRunCount}")
            appendLine("Rejected with near joint limit: ${fallbackSummary.rejectedWithNearLimitCount}")
            appendLine("Rejected at full joint-limit pressure: ${fallbackSummary.rejectedAtFullJointLimitPressureCount}")
            appendLine("Rejected with max iterations: ${fallbackSummary.rejectedWithMaxIterationsCount}")
            appendLine("Rejected with no convergence: ${fallbackSummary.rejectedWithNoConvergenceCount}")
            appendLine("Average rejected final error: ${formatDouble(fallbackSummary.averageRejectedFinalError)} m")
            appendLine("Average rejected joint-limit pressure: ${formatDouble(fallbackSummary.averageRejectedJointLimitPressureRatio)}")
            appendLine("Fallback pressure label: ${fallbackSummary.fallbackPressureLabel}")
            appendLine()

            appendLine("--- Multi-Seed Comparison ---")
            appendLine("Seed count: ${seedComparisonSummary.seedCount}")
            appendLine("Best seed: ${seedComparisonSummary.bestSeed ?: "N/A"}")
            appendLine("Worst seed: ${seedComparisonSummary.worstSeed ?: "N/A"}")
            appendLine("Average strict acceptance rate: ${formatDouble(seedComparisonSummary.averageStrictAcceptanceRate)}")
            appendLine("Strict acceptance rate variance: ${formatDouble(seedComparisonSummary.strictAcceptanceRateVariance)}")
            appendLine("Strict acceptance rate spread: ${formatDouble(seedComparisonSummary.strictAcceptanceRateSpread)}")
            appendLine("Seed sensitivity: ${seedComparisonSummary.seedSensitivityLabel}")
            appendLine()

            appendLine("--- Link Count Aggregates ---")
            linkCountAggregates.forEach { aggregate ->
                appendLine("Link count: ${aggregate.linkCount}")
                appendLine("  Runs: ${aggregate.runCount}")
                appendLine("  Strict acceptance rate: ${formatDouble(aggregate.strictAcceptanceRate)}")
                appendLine("  Strict accepted: ${aggregate.strictAcceptedCount}")
                appendLine("  Near solved: ${aggregate.nearSolvedCount}")
                appendLine("  Close miss: ${aggregate.closeMissCount}")
                appendLine("  Far failure: ${aggregate.farFailureCount}")
                appendLine("  Average final error: ${formatDouble(aggregate.averageFinalError)}")
                appendLine("  Max final error: ${formatDouble(aggregate.maxFinalError)}")
                appendLine("  Average iterations: ${formatDouble(aggregate.averageIterations)}")
                appendLine("  Average improvement ratio: ${formatDouble(aggregate.averageImprovementRatio)}")
                appendLine()
            }

            appendLine("--- Seed Aggregates ---")
            seedAggregates.forEach { aggregate ->
                appendLine("Seed: ${aggregate.seed}")
                appendLine("  Runs: ${aggregate.runCount}")
                appendLine("  Strict acceptance rate: ${formatDouble(aggregate.strictAcceptanceRate)}")
                appendLine("  Strict accepted: ${aggregate.strictAcceptedCount}")
                appendLine("  Near solved: ${aggregate.nearSolvedCount}")
                appendLine("  Close miss: ${aggregate.closeMissCount}")
                appendLine("  Far failure: ${aggregate.farFailureCount}")
                appendLine("  Average final error: ${formatDouble(aggregate.averageFinalError)}")
                appendLine("  Max final error: ${formatDouble(aggregate.maxFinalError)}")
                appendLine("  Average iterations: ${formatDouble(aggregate.averageIterations)}")
                appendLine("  Average improvement ratio: ${formatDouble(aggregate.averageImprovementRatio)}")
                appendLine()
            }

            appendLine("--- Topology Aggregates ---")
            topologyAggregates.forEach { aggregate ->
                appendLine("Topology: ${aggregate.jointMode}")
                appendLine("  Runs: ${aggregate.runCount}")
                appendLine("  Strict acceptance rate: ${formatDouble(aggregate.strictAcceptanceRate)}")
                appendLine("  Strict accepted: ${aggregate.strictAcceptedCount}")
                appendLine("  Near solved: ${aggregate.nearSolvedCount}")
                appendLine("  Close miss: ${aggregate.closeMissCount}")
                appendLine("  Far failure: ${aggregate.farFailureCount}")
                appendLine("  Average final error: ${formatDouble(aggregate.averageFinalError)}")
                appendLine("  Max final error: ${formatDouble(aggregate.maxFinalError)}")
                appendLine("  Average iterations: ${formatDouble(aggregate.averageIterations)}")
                appendLine("  Average improvement ratio: ${formatDouble(aggregate.averageImprovementRatio)}")
                appendLine()
            }

            appendLine("--- Per Case Aggregates ---")
            perCaseAggregates.forEach { aggregate ->
                appendLine("Case: ${aggregate.caseId}")
                appendLine("  Expected class: ${aggregate.expectedClass}")
                appendLine("  Oracle runs: ${aggregate.oracleRunCount}")
                appendLine("  Oracle accepted: ${aggregate.oracleAcceptedCount}")
                appendLine("  Oracle rejected: ${aggregate.oracleRejectedCount}")
                appendLine("  Sequential runs selected: ${aggregate.sequentialRunCount}")
                appendLine("  Sequential accepted: ${aggregate.sequentialAcceptedCount}")
                appendLine("  Sequential rejected: ${aggregate.sequentialRejectedCount}")
                appendLine("  Average initial error: ${formatDouble(aggregate.averageSequentialInitialError)} m")
                appendLine("  Average final error: ${formatDouble(aggregate.averageSequentialError)} m")
                appendLine("  Max final error: ${formatDouble(aggregate.maxSequentialError)} m")
                appendLine("  Average improvement ratio: ${formatDouble(aggregate.averageSequentialImprovementRatio)}")
                appendLine("  Average iterations: ${formatDouble(aggregate.averageSequentialIterations)}")
                appendLine("  Average iteration saturation: ${formatDouble(aggregate.averageIterationSaturationRatio)}")
                appendLine("  Average joint delta norm: ${formatDouble(aggregate.averageJointDeltaNorm)}")
                appendLine("  Average max single-joint movement: ${formatDouble(aggregate.averageMaxSingleJointMovement)}")
                appendLine("  Average joint limit pressure ratio: ${formatDouble(aggregate.averageJointLimitPressureRatio)}")
                appendLine("  Most common status: ${aggregate.mostCommonSequentialStatus}")
                appendLine("  Most common progress class: ${aggregate.mostCommonProgressClass ?: "N/A"}")
                appendLine("  Most common seed distance bucket: ${aggregate.mostCommonSeedDistanceBucket ?: "N/A"}")
                appendLine(
                    "  Target: (${formatDouble(aggregate.target.x)}, " +
                            "${formatDouble(aggregate.target.y)}, " +
                            "${formatDouble(aggregate.target.z)})"
                )
                appendLine(
                    "  Source q from FK: ${
                        aggregate.sourceJointState?.jointValues?.map {
                            formatDouble(it)
                        } ?: "N/A"
                    }"
                )
                appendLine("  Note: ${aggregate.note}")
                appendLine()
            }

            appendLine("--- Transition Aggregates Preview ---")
            transitionAggregates.take(20).forEach { transition ->
                appendLine(
                    "${transition.fromCaseId} -> ${transition.toCaseId}: " +
                            "runs=${transition.runCount}, " +
                            "accepted=${transition.acceptedCount}, " +
                            "rejected=${transition.rejectedCount}, " +
                            "near=${transition.nearSolvedCount}, " +
                            "close=${transition.closeMissCount}, " +
                            "far=${transition.farFailureCount}, " +
                            "avgError=${formatDouble(transition.averageFinalError)}, " +
                            "avgImprovementRatio=${formatDouble(transition.averageImprovementRatio)}, " +
                            "status=${transition.mostCommonStatus}"
                )
            }

            if (transitionAggregates.size > 20) {
                appendLine("... ${transitionAggregates.size - 20} more transitions not shown")
            }

            appendLine()

            appendLine("--- Best / Worst Runs ---")
            extremeRuns.forEach { run ->
                appendLine(
                    "${run.label}: run=${run.runIndex}, case=${run.caseId}, " +
                            "transition=${run.transitionFromCaseId ?: "START"} -> ${run.transitionToCaseId}, " +
                            "expected=${run.expectedClass}, status=${run.status}, detail=${run.detailCode}, " +
                            "initialError=${formatDouble(run.initialError)}, " +
                            "finalError=${formatDouble(run.finalError)}, " +
                            "improvementRatio=${formatDouble(run.improvementRatio)}, " +
                            "iterations=${run.iterations}, progress=${run.progressClass}"
                )
            }

            appendLine()

            appendLine("--- Target Pool One-Shot Results ---")
            cases.forEach { case ->
                appendLine("Case: ${case.id}")
                appendLine("  Expected class: ${case.expectedClass}")
                appendLine("  Solver accepted: ${case.solverAccepted}")
                appendLine("  Status: ${case.status}")
                appendLine("  Detail code: ${case.detailCode}")
                appendLine(
                    "  Target: (${formatDouble(case.target.x)}, " +
                            "${formatDouble(case.target.y)}, " +
                            "${formatDouble(case.target.z)})"
                )
                appendLine(
                    "  Source q from FK: ${
                        case.sourceJointState?.jointValues?.map {
                            formatDouble(it)
                        } ?: "N/A"
                    }"
                )
                appendLine("  Seed q used by IK: ${case.seedJointState.jointValues.map { formatDouble(it) }}")
                appendLine("  Initial error: ${formatDouble(case.initialError)} m")
                appendLine("  Final error: ${formatDouble(case.finalError)} m")
                appendLine("  Improvement ratio: ${formatDouble(case.improvementRatio)}")
                appendLine("  Progress class: ${case.progressClass}")
                appendLine("  Seed distance bucket: ${case.seedDistanceBucket}")
                appendLine("  Seed minimum normalized limit margin: ${formatDouble(case.seedMinNormalizedLimitMargin)}")
                appendLine("  Seed log10 condition number: ${formatDouble(case.seedLogConditionNumber)}")
                appendLine("  Iteration saturation: ${formatDouble(case.iterationSaturationRatio)}")
                appendLine("  Joint delta norm: ${formatDouble(case.jointDeltaNorm)}")
                appendLine("  Max single-joint movement: ${formatDouble(case.maxSingleJointMovement)}")
                appendLine("  Normalized joint travel RMS: ${formatDouble(case.normalizedJointTravelRms)}")
                appendLine("  Final minimum normalized limit margin: ${formatDouble(case.finalMinNormalizedLimitMargin)}")
                appendLine("  Backtracking retries: ${case.backtrackingRetryCount}")
                appendLine("  Solve duration: ${case.solveDurationNanos} ns")
                appendLine("  Near limit joints: ${case.nearLimitJointNames}")
                appendLine("  Joint limit pressure ratio: ${formatDouble(case.jointLimitPressureRatio)}")
                appendLine("  Iterations: ${case.iterations}")
                appendLine("  Solution q: ${case.solutionJointValues.map { formatDouble(it) }}")
                appendLine("  Note: ${case.note}")
                appendLine()
            }

            appendLine("--- First Sequential Runs Preview ---")
            runResults
                .asSequence()
                .filter {
                    it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
                }
                .take(20)
                .forEach { run ->
                    appendLine(
                        "Run ${run.runIndex}: " +
                                "transition=${run.transitionFromCaseId ?: "START"} -> ${run.transitionToCaseId}, " +
                                "case=${run.selectedCaseId}, " +
                                "expected=${run.expectedClass}, " +
                                "accepted=${run.solverAccepted}, " +
                                "status=${run.status}, " +
                                "detail=${run.detailCode}, " +
                                "initialError=${formatDouble(run.initialError)}, " +
                                "finalError=${formatDouble(run.finalError)}, " +
                                "improvementRatio=${formatDouble(run.improvementRatio)}, " +
                                "progress=${run.progressClass}, " +
                                "seedBucket=${run.seedDistanceBucket}, " +
                                "seedMinLimitMargin=${formatDouble(run.seedMinNormalizedLimitMargin)}, " +
                                "seedLogCondition=${formatDouble(run.seedLogConditionNumber)}, " +
                                "normalizedTravelRms=${formatDouble(run.normalizedJointTravelRms)}, " +
                                "finalMinLimitMargin=${formatDouble(run.finalMinNormalizedLimitMargin)}, " +
                                "backtrackingRetries=${run.backtrackingRetryCount}, " +
                                "solveDurationNanos=${run.solveDurationNanos}, " +
                                "iterations=${run.iterations}"
                    )
                }

            appendLine("=====================================")
        }
    }

    private fun formatDouble(value: Double): String {
        return if (value.isFinite()) {
            String.format(Locale.US, "%.6f", value)
        } else {
            "NA"
        }
    }
}
