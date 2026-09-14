package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport

@Composable
fun BenchmarkPolicyCard(
    report: Layer1DiagnosticReport
) {
    val plan =
        report.benchmarkPlan

    val background =
        if (plan.strongReliabilityClaimAllowed) {
            Color(0xFFEAF8EA)
        } else {
            Color(0xFFFFF3E0)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = background
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Benchmark Policy",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Reliability claim",
                value = plan.reliabilityClaim
            )

            SummaryRow(
                label = "Strong reliability claim allowed",
                value = plan.strongReliabilityClaimAllowed.toString()
            )

            SummaryRow(
                label = "Experimental mode",
                value = plan.isExperimental.toString()
            )

            SummaryRow(
                label = "Unlimited sample mode",
                value = plan.isUnlimited.toString()
            )

            SummaryRow(
                label = "All link counts",
                value = formatIntList(plan.linkCounts)
            )

            SummaryRow(
                label = "Safe link counts",
                value = formatIntList(plan.safeLinkCounts)
            )

            SummaryRow(
                label = "Experimental link counts",
                value = formatIntList(plan.experimentalLinkCounts)
            )

            SummaryRow(
                label = "Samples per link count",
                value = plan.samplesPerLinkCount.toString()
            )

            SummaryRow(
                label = "Total planned sequential runs",
                value = plan.totalPlannedSequentialRuns.toString()
            )

            if (plan.warnings.isEmpty()) {
                Text(
                    text = "Warnings: none",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.titleSmall
                )

                plan.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun BenchmarkWarningsCard(
    report: Layer1DiagnosticReport
) {
    val plan =
        report.benchmarkPlan

    val seedCount =
        report.config.seeds.seeds.size.coerceAtLeast(1)

    val topologyCount =
        if (report.config.topology.runAllTopologies) {
            4
        } else {
            1
        }

    val linkCountText =
        if (plan.linkCounts.isEmpty()) {
            "N/A"
        } else {
            plan.linkCounts.joinToString()
        }

    val runtimeRisk =
        estimateRuntimeRisk(
            plannedRuns = plan.totalPlannedSequentialRuns,
            isExperimental = plan.isExperimental,
            isUnlimited = plan.isUnlimited
        )

    val cardColor =
        when (runtimeRisk) {
            "LOW" -> Color(0xFFEAF8EA)
            "MEDIUM" -> Color(0xFFFFF8E1)
            "HIGH" -> Color(0xFFFFF3E0)
            else -> Color(0xFFFFEEEE)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Benchmark Warnings / Runtime Risk",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Reliability claim",
                value = plan.reliabilityClaim
            )

            SummaryRow(
                label = "Runtime risk",
                value = runtimeRisk
            )

            SummaryRow(
                label = "Planned sequential runs",
                value = plan.totalPlannedSequentialRuns.toString()
            )

            SummaryRow(
                label = "Link counts",
                value = linkCountText
            )

            SummaryRow(
                label = "Samples per link count",
                value = plan.samplesPerLinkCount.toString()
            )

            SummaryRow(
                label = "Seed count",
                value = seedCount.toString()
            )

            SummaryRow(
                label = "Topology multiplier",
                value = topologyCount.toString()
            )

            SummaryRow(
                label = "Experimental mode",
                value = plan.isExperimental.toString()
            )

            SummaryRow(
                label = "Unlimited sample mode",
                value = plan.isUnlimited.toString()
            )

            if (plan.warnings.isEmpty()) {
                Text(
                    text = "Warnings: none",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Warnings",
                    style = MaterialTheme.typography.titleSmall
                )

                plan.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            DiagnosticBar(
                label = "Runtime scale",
                numerator = runtimeRiskScore(runtimeRisk),
                denominator = 4
            )
        }
    }
}

@Composable
fun IkConfigUsedCard(
    report: Layer1DiagnosticReport
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "IK Config Used",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Max iterations",
                value = report.config.solver.ikMaxIterations.toString()
            )

            SummaryRow(
                label = "Tolerance",
                value = formatDouble(report.config.solver.ikTolerance)
            )

            SummaryRow(
                label = "Damping",
                value = formatDouble(report.config.solver.ikDamping)
            )

            SummaryRow(
                label = "Max step",
                value = formatDouble(report.config.solver.ikMaxStep)
            )
        }
    }
}

@Composable
fun AutoBenchmarkCheckCard(
    report: Layer1DiagnosticReport
) {
    val config =
        report.config

    val plan =
        report.benchmarkPlan

    val expectedSeeds =
        listOf(42, 101, 202, 303, 404)

    val usesSafeAutoLinks =
        plan.linkCounts == (2..10).toList()

    val usesTargetMix =
        config.sampling.reachableCount == 4 &&
                config.sampling.unreachableCount == 4

    val usesSampleCount =
        plan.samplesPerLinkCount == 500

    val usesSeedList =
        config.seeds.seeds == expectedSeeds

    val usesMixedTopology =
        config.topology.jointMode == DiagnosticJointMode.MIXED &&
                !config.topology.runAllTopologies

    val usesSafeMode =
        !plan.isExperimental &&
                !plan.isUnlimited &&
                plan.strongReliabilityClaimAllowed

    val usesBalancedSolver =
        config.solver.ikMaxIterations == 800 &&
                config.solver.ikTolerance == 0.00001 &&
                config.solver.ikDamping == 0.05 &&
                config.solver.ikMaxStep == 0.02

    val allMatched =
        usesSafeAutoLinks &&
                usesTargetMix &&
                usesSampleCount &&
                usesSeedList &&
                usesMixedTopology &&
                usesSafeMode &&
                usesBalancedSolver

    val background =
        if (allMatched) {
            Color(0xFFEAF8EA)
        } else {
            Color(0xFFFFF8E1)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = background
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "True Auto Benchmark Check",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Matches auto benchmark defaults",
                value = allMatched.toString()
            )

            AutoCheckRow(
                label = "Safe links 2–10",
                passed = usesSafeAutoLinks,
                detail = formatIntList(plan.linkCounts)
            )

            AutoCheckRow(
                label = "Target mix 4 reachable / 4 unreachable",
                passed = usesTargetMix,
                detail = "${config.sampling.reachableCount} reachable, ${config.sampling.unreachableCount} unreachable"
            )

            AutoCheckRow(
                label = "500 samples per link count",
                passed = usesSampleCount,
                detail = plan.samplesPerLinkCount.toString()
            )

            AutoCheckRow(
                label = "Deterministic seed list",
                passed = usesSeedList,
                detail = config.seeds.seeds.joinToString()
            )

            AutoCheckRow(
                label = "Mixed topology only",
                passed = usesMixedTopology,
                detail = "${config.topology.jointMode}, runAllTopologies=${config.topology.runAllTopologies}"
            )

            AutoCheckRow(
                label = "Safe mode only",
                passed = usesSafeMode,
                detail = "experimental=${plan.isExperimental}, unlimited=${plan.isUnlimited}, strongClaim=${plan.strongReliabilityClaimAllowed}"
            )

            AutoCheckRow(
                label = "Balanced solver preset",
                passed = usesBalancedSolver,
                detail = "iterations=${config.solver.ikMaxIterations}, tolerance=${formatDouble(config.solver.ikTolerance)}, damping=${formatDouble(config.solver.ikDamping)}, maxStep=${formatDouble(config.solver.ikMaxStep)}"
            )

            Text(
                text =
                    if (allMatched) {
                        "This run qualifies as the standard True Auto Benchmark profile."
                    } else {
                        "This run is a valid manual/custom diagnostic, but it does not exactly match the standard True Auto Benchmark profile."
                    },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}