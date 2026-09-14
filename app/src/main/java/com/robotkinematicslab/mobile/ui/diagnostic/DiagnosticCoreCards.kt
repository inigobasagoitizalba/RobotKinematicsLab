package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticVerdict
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

@Composable
fun DiagnosticFinalVerdictCard(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    val fallback =
        report.fallbackSummary

    val dataset =
        report.datasetAcceptanceSummary

    val sequentialAcceptanceRate =
        if (summary.sequentialRuns > 0) {
            summary.sequentialAcceptedCount.toDouble() /
                    summary.sequentialRuns.toDouble()
        } else {
            Double.NaN
        }

    val oraclePassed =
        summary.oracleReachableRuns > 0 &&
                summary.oracleReachableAccepted == summary.oracleReachableRuns

    val unreachablePassed =
        summary.sequentialUnreachableRuns > 0 &&
                summary.sequentialUnreachableAccepted == 0

    val hasFalseRejects =
        dataset.falseRejectCount > 0

    val hasFalseAccepts =
        dataset.falseAcceptCount > 0

    val highJointLimitPressure =
        fallback.fallbackPressureLabel.contains(
            other = "HIGH",
            ignoreCase = true
        )

    val verdictMeaning =
        when (report.finalVerdict) {
            DiagnosticVerdict.PASS ->
                "Core diagnostic checks passed with no major warnings."

            DiagnosticVerdict.PASS_WITH_WARNINGS ->
                "Core safety checks passed, but sequential recovery or solver stability has warnings."

            DiagnosticVerdict.FAIL ->
                "One or more core diagnostic checks failed."

            DiagnosticVerdict.EXPERIMENTAL_RESULT_ONLY ->
                "This run includes experimental settings and should not be used as a strong reliability claim."
        }

    val cardColor =
        when (report.finalVerdict) {
            DiagnosticVerdict.PASS ->
                Color(0xFFEAF8EA)

            DiagnosticVerdict.PASS_WITH_WARNINGS ->
                Color(0xFFFFF8E1)

            DiagnosticVerdict.FAIL ->
                Color(0xFFFFEEEE)

            DiagnosticVerdict.EXPERIMENTAL_RESULT_ONLY ->
                Color(0xFFEAF3FF)
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
                text = "Final Verdict Explanation",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Verdict",
                value = report.finalVerdict.toString()
            )

            SummaryRow(
                label = "Strong reliability claim allowed",
                value = report.benchmarkPlan.strongReliabilityClaimAllowed.toString()
            )

            SummaryRow(
                label = "Sequential strict acceptance",
                value = formatPercent(sequentialAcceptanceRate)
            )

            SummaryRow(
                label = "False accepts",
                value = dataset.falseAcceptCount.toString()
            )

            SummaryRow(
                label = "False rejects",
                value = dataset.falseRejectCount.toString()
            )

            SummaryRow(
                label = "Fallback pressure",
                value = fallback.fallbackPressureLabel
            )

            Text(
                text = verdictMeaning,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Reason checklist",
                style = MaterialTheme.typography.titleSmall
            )

            VerdictReasonRow(
                passed = oraclePassed,
                text = "FK-proven reachable oracle accepted ${summary.oracleReachableAccepted} / ${summary.oracleReachableRuns}."
            )

            VerdictReasonRow(
                passed = unreachablePassed,
                text = "Unreachable sequential targets produced ${summary.sequentialUnreachableAccepted} false accepts."
            )

            VerdictReasonRow(
                passed = !hasFalseAccepts,
                text = "False accepts: ${dataset.falseAcceptCount}."
            )

            VerdictReasonRow(
                passed = !hasFalseRejects,
                text = "False rejects: ${dataset.falseRejectCount}."
            )

            VerdictReasonRow(
                passed = !highJointLimitPressure,
                text = "Joint-limit fallback pressure: ${fallback.fallbackPressureLabel}."
            )

            if (report.benchmarkPlan.warnings.isNotEmpty()) {
                Text(
                    text = "Benchmark warnings",
                    style = MaterialTheme.typography.titleSmall
                )

                report.benchmarkPlan.warnings.forEach { warning ->
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
fun DiagnosticSummaryCard(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    val sequentialRuns =
        report.sequentialRuns()

    val reachableRuns =
        sequentialRuns.filter {
            it.expectedClass == DiagnosticExpectedClass.REACHABLE
        }

    val unreachableRuns =
        sequentialRuns.filter {
            it.expectedClass == DiagnosticExpectedClass.UNREACHABLE
        }

    val reachableErrorStats =
        reachableRuns.finalErrorStats()

    val unreachableErrorStats =
        unreachableRuns.finalErrorStats()

    val nearSequential =
        sequentialRuns.count {
            it.isNearConverged()
        }

    val closeSequential =
        sequentialRuns.count {
            it.isCloseMiss()
        }

    val strictOrNearSequential =
        sequentialRuns.count {
            it.solverAccepted || it.isNearConverged()
        }

    val strictOrNearReachable =
        reachableRuns.count {
            it.solverAccepted || it.isNearConverged()
        }

    val strictOrNearUnreachable =
        unreachableRuns.count {
            it.solverAccepted || it.isNearConverged()
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F7F7)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow("Target pool size", summary.totalCases.toString())
            SummaryRow("Total runs", summary.totalRuns.toString())
            SummaryRow("Reachable targets in pool", summary.expectedReachableCount.toString())
            SummaryRow("Unreachable targets in pool", summary.expectedUnreachableCount.toString())

            Spacer(modifier = Modifier.height(6.dp))

            JargonAwareText(
                text = "FK-Proven Reachable Oracle Check",
                style = MaterialTheme.typography.titleSmall
            )

            SummaryRow("Oracle reachable checks", summary.oracleReachableRuns.toString())
            SummaryRow("Oracle strict accepted", summary.oracleReachableAccepted.toString())
            SummaryRow("Oracle rejected", summary.oracleReachableRejected.toString())

            Spacer(modifier = Modifier.height(6.dp))

            JargonAwareText(
                text = "Sequential IK Stability Check",
                style = MaterialTheme.typography.titleSmall
            )

            SummaryRow("Sequential runs", summary.sequentialRuns.toString())
            SummaryRow("Sequential strict accepted", summary.sequentialAcceptedCount.toString())
            SummaryRow("Sequential near-converged", nearSequential.toString())
            SummaryRow("Sequential close miss", closeSequential.toString())
            SummaryRow("Sequential strict-or-near", strictOrNearSequential.toString())
            SummaryRow("Sequential rejected", summary.sequentialRejectedCount.toString())

            SummaryRow("Sequential reachable runs", summary.sequentialReachableRuns.toString())
            SummaryRow("Reachable strict accepted", summary.sequentialReachableAccepted.toString())
            SummaryRow(
                "Reachable acceptance (Wilson 95% CI)",
                report.datasetAcceptanceSummary.reachableAcceptanceConfidence95.let { interval ->
                    "${formatPercent(interval.estimate)} [${formatPercent(interval.lower)}, ${formatPercent(interval.upper)}]"
                }
            )
            SummaryRow("Reachable strict-or-near", strictOrNearReachable.toString())
            SummaryRow("Reachable rejected", summary.sequentialReachableRejected.toString())

            SummaryRow("Sequential unreachable runs", summary.sequentialUnreachableRuns.toString())
            SummaryRow("Unreachable strict accepted", summary.sequentialUnreachableAccepted.toString())
            SummaryRow(
                "Unreachable rejection (Wilson 95% CI)",
                report.datasetAcceptanceSummary.unreachableRejectionConfidence95.let { interval ->
                    "${formatPercent(interval.estimate)} [${formatPercent(interval.lower)}, ${formatPercent(interval.upper)}]"
                }
            )
            SummaryRow("Unreachable strict-or-near", strictOrNearUnreachable.toString())
            SummaryRow("Unreachable rejected", summary.sequentialUnreachableRejected.toString())

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Combined Error Metrics",
                style = MaterialTheme.typography.titleSmall
            )

            SummaryRow("Average initial error", "${formatDouble(summary.averageSequentialInitialError)} m")
            SummaryRow("Average final error", "${formatDouble(summary.averageSequentialError)} m")
            SummaryRow("Max final error", "${formatDouble(summary.maxSequentialError)} m")

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Final Error by Expected Class",
                style = MaterialTheme.typography.titleSmall
            )

            SummaryRow("Reachable finite error samples", reachableErrorStats.count.toString())
            SummaryRow("Reachable average final error", "${formatDouble(reachableErrorStats.average)} m")
            SummaryRow("Reachable min final error", "${formatDouble(reachableErrorStats.min)} m")
            SummaryRow("Reachable max final error", "${formatDouble(reachableErrorStats.max)} m")

            SummaryRow("Unreachable finite error samples", unreachableErrorStats.count.toString())
            SummaryRow("Unreachable average final error", "${formatDouble(unreachableErrorStats.average)} m")
            SummaryRow("Unreachable min final error", "${formatDouble(unreachableErrorStats.min)} m")
            SummaryRow("Unreachable max final error", "${formatDouble(unreachableErrorStats.max)} m")

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Improvement Metrics",
                style = MaterialTheme.typography.titleSmall
            )

            SummaryRow("Average improvement", "${formatDouble(summary.averageSequentialImprovement)} m")
            SummaryRow("Average improvement ratio", formatDouble(summary.averageSequentialImprovementRatio))
            SummaryRow("Average iterations", formatDouble(summary.averageSequentialIterations))
            SummaryRow("Average iteration saturation", formatPercent(summary.averageIterationSaturationRatio))
        }
    }
}

@Composable
fun ProgressAndSeedBucketCard(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFFFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            JargonAwareText(
                text = "Progress Class and Seed Distance",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Progress class distribution",
                style = MaterialTheme.typography.titleSmall
            )

            DiagnosticBar("Solved", summary.solvedCount, summary.sequentialRuns)
            DiagnosticBar("Near solved", summary.nearSolvedCount, summary.sequentialRuns)
            DiagnosticBar("Improved but not enough", summary.improvedButNotEnoughCount, summary.sequentialRuns)
            DiagnosticBar("Stalled", summary.stalledCount, summary.sequentialRuns)
            DiagnosticBar("Worsened", summary.worsenedCount, summary.sequentialRuns)
            DiagnosticBar("Invalid numerical", summary.invalidNumericalCount, summary.sequentialRuns)

            Spacer(modifier = Modifier.height(4.dp))

            JargonAwareText(
                text = "Seed distance buckets",
                style = MaterialTheme.typography.titleSmall
            )

            DiagnosticBar("Easy < 0.10 m", summary.easySeedRuns, summary.sequentialRuns)
            DiagnosticBar("Medium 0.10–0.50 m", summary.mediumSeedRuns, summary.sequentialRuns)
            DiagnosticBar("Hard 0.50–1.00 m", summary.hardSeedRuns, summary.sequentialRuns)
            DiagnosticBar("Extreme > 1.00 m", summary.extremeSeedRuns, summary.sequentialRuns)
            DiagnosticBar("Unknown", summary.unknownSeedRuns, summary.sequentialRuns)
        }
    }
}

@Composable
fun ImprovementAndMotionCard(
    report: Layer1DiagnosticReport
) {
    val summary =
        report.summary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F7F7)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Improvement, Motion, and Saturation",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow("Average initial error", "${formatDouble(summary.averageSequentialInitialError)} m")
            SummaryRow("Average final error", "${formatDouble(summary.averageSequentialError)} m")
            SummaryRow("Average improvement", "${formatDouble(summary.averageSequentialImprovement)} m")
            SummaryRow("Average improvement ratio", formatPercent(summary.averageSequentialImprovementRatio))
            SummaryRow("Average iteration saturation", formatPercent(summary.averageIterationSaturationRatio))
            SummaryRow("Average joint delta norm", formatDouble(summary.averageJointDeltaNorm))
            SummaryRow("Average max single-joint movement", formatDouble(summary.averageMaxSingleJointMovement))
            SummaryRow("Average joint-limit pressure", formatPercent(summary.averageJointLimitPressureRatio))
            SummaryRow("Runs with near joint limit", summary.runsWithNearJointLimit.toString())

            DiagnosticBar(
                label = "Average iteration saturation",
                numerator = percentageToInt(summary.averageIterationSaturationRatio),
                denominator = 100
            )

            DiagnosticBar(
                label = "Average joint-limit pressure",
                numerator = percentageToInt(summary.averageJointLimitPressureRatio),
                denominator = 100
            )
        }
    }
}
