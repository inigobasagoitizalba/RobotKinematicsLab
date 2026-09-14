package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.background
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
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import kotlin.math.max

@Composable
fun DiagnosticJointLimitCard(
    report: Layer1DiagnosticReport
) {
    val sequentialRuns =
        report.sequentialRuns()

    val nearLimitRuns =
        sequentialRuns.filter {
            it.nearLimitJointCount > 0
        }

    val jointNameCounts =
        nearLimitRuns
            .flatMap {
                it.nearLimitJointNames
            }
            .groupBy {
                it
            }
            .mapValues {
                it.value.size
            }
            .toList()
            .sortedByDescending {
                it.second
            }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Joint Limit Pressure",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Runs with near joint limit",
                value = nearLimitRuns.size.toString()
            )

            SummaryRow(
                label = "Average joint-limit pressure",
                value = formatPercent(report.summary.averageJointLimitPressureRatio)
            )

            SummaryRow(
                label = "Average joint delta norm",
                value = formatDouble(report.summary.averageJointDeltaNorm)
            )

            SummaryRow(
                label = "Average max single-joint movement",
                value = formatDouble(report.summary.averageMaxSingleJointMovement)
            )

            DiagnosticBar(
                label = "Near-limit run ratio",
                numerator = nearLimitRuns.size,
                denominator = sequentialRuns.size
            )

            if (jointNameCounts.isNotEmpty()) {
                Text(
                    text = "Most pressured joints",
                    style = MaterialTheme.typography.titleSmall
                )

                jointNameCounts.forEach { item ->
                    DiagnosticBar(
                        label = item.first,
                        numerator = item.second,
                        denominator = nearLimitRuns.size
                    )
                }
            } else {
                Text("No near-limit joints detected.")
            }
        }
    }
}

@Composable
fun DiagnosticFallbackSummaryCard(
    report: Layer1DiagnosticReport
) {
    val fallback =
        report.fallbackSummary

    val pressureColor =
        when (fallback.fallbackPressureLabel) {
            "LOW_JOINT_LIMIT_PRESSURE" ->
                Color(0xFFEAF8EA)

            "MEDIUM_JOINT_LIMIT_PRESSURE" ->
                Color(0xFFFFF8E1)

            "HIGH_JOINT_LIMIT_PRESSURE" ->
                Color(0xFFFFEAEA)

            else ->
                Color(0xFFF7F7F7)
        }

    val interpretation =
        when (fallback.fallbackPressureLabel) {
            "HIGH_JOINT_LIMIT_PRESSURE" ->
                "Most rejected runs are ending near joint limits. This suggests clamp pressure or seed-state trapping, not random numerical failure."

            "MEDIUM_JOINT_LIMIT_PRESSURE" ->
                "Some rejected runs are influenced by joint limits. Compare rejected transitions and per-case joint-limit pressure."

            "LOW_JOINT_LIMIT_PRESSURE" ->
                "Rejected runs are not mainly explained by joint-limit pressure. Solver step size, damping, topology, or target difficulty may be stronger causes."

            else ->
                "Fallback pressure is not classified."
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = pressureColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Fallback / Clamp Diagnostics",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Sequential runs",
                value = fallback.sequentialRunCount.toString()
            )

            SummaryRow(
                label = "Accepted runs",
                value = fallback.acceptedRunCount.toString()
            )

            SummaryRow(
                label = "Rejected runs",
                value = fallback.rejectedRunCount.toString()
            )

            SummaryRow(
                label = "Rejected with near joint limit",
                value = fallback.rejectedWithNearLimitCount.toString()
            )

            SummaryRow(
                label = "Rejected at full joint-limit pressure",
                value = fallback.rejectedAtFullJointLimitPressureCount.toString()
            )

            SummaryRow(
                label = "Rejected with max iterations",
                value = fallback.rejectedWithMaxIterationsCount.toString()
            )

            SummaryRow(
                label = "Rejected with no convergence",
                value = fallback.rejectedWithNoConvergenceCount.toString()
            )

            SummaryRow(
                label = "Average rejected final error",
                value = "${formatDouble(fallback.averageRejectedFinalError)} m"
            )

            SummaryRow(
                label = "Average rejected joint-limit pressure",
                value = formatPercent(fallback.averageRejectedJointLimitPressureRatio)
            )

            SummaryRow(
                label = "Fallback pressure label",
                value = fallback.fallbackPressureLabel
            )

            DiagnosticBar(
                label = "Rejected near joint limit",
                numerator = fallback.rejectedWithNearLimitCount,
                denominator = max(1, fallback.rejectedRunCount)
            )

            DiagnosticBar(
                label = "Rejected at full joint-limit pressure",
                numerator = fallback.rejectedAtFullJointLimitPressureCount,
                denominator = max(1, fallback.rejectedRunCount)
            )

            DiagnosticBar(
                label = "Rejected with max iterations",
                numerator = fallback.rejectedWithMaxIterationsCount,
                denominator = max(1, fallback.rejectedRunCount)
            )

            Text(
                text = interpretation,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DiagnosticTransitionCard(
    report: Layer1DiagnosticReport
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F7F7)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Transition Matrix: Most Difficult Transitions",
                style = MaterialTheme.typography.titleMedium
            )

            if (report.transitionAggregates.isEmpty()) {
                Text("No transition aggregates available yet.")
            } else {
                report.transitionAggregates
                    .take(20)
                    .forEach { transition ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFFFFFFF),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${transition.fromCaseId} → ${transition.toCaseId}",
                                style = MaterialTheme.typography.titleSmall
                            )

                            SummaryRow(
                                label = "Runs",
                                value = transition.runCount.toString()
                            )

                            SummaryRow(
                                label = "Accepted",
                                value = transition.acceptedCount.toString()
                            )

                            SummaryRow(
                                label = "Rejected",
                                value = transition.rejectedCount.toString()
                            )

                            SummaryRow(
                                label = "Near solved",
                                value = transition.nearSolvedCount.toString()
                            )

                            SummaryRow(
                                label = "Close miss",
                                value = transition.closeMissCount.toString()
                            )

                            SummaryRow(
                                label = "Far failure",
                                value = transition.farFailureCount.toString()
                            )

                            SummaryRow(
                                label = "Average initial error",
                                value = "${formatDouble(transition.averageInitialError)} m"
                            )

                            SummaryRow(
                                label = "Average final error",
                                value = "${formatDouble(transition.averageFinalError)} m"
                            )

                            SummaryRow(
                                label = "Max final error",
                                value = "${formatDouble(transition.maxFinalError)} m"
                            )

                            SummaryRow(
                                label = "Average improvement ratio",
                                value = formatPercent(transition.averageImprovementRatio)
                            )

                            SummaryRow(
                                label = "Average iterations",
                                value = formatDouble(transition.averageIterations)
                            )

                            SummaryRow(
                                label = "Most common status",
                                value = transition.mostCommonStatus
                            )

                            DiagnosticBar(
                                label = "Accepted ratio",
                                numerator = transition.acceptedCount,
                                denominator = transition.runCount
                            )

                            DiagnosticBar(
                                label = "Far failure ratio",
                                numerator = transition.farFailureCount,
                                denominator = transition.runCount
                            )
                        }
                    }
            }
        }
    }
}

@Composable
fun DiagnosticExtremeRunsCard(
    report: Layer1DiagnosticReport
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFFFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Best / Worst Case Runs",
                style = MaterialTheme.typography.titleMedium
            )

            if (report.extremeRuns.isEmpty()) {
                Text("No extreme run summaries available.")
            } else {
                report.extremeRuns.forEach { run ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFFF7F7F7),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = run.label,
                            style = MaterialTheme.typography.titleSmall
                        )

                        SummaryRow(
                            label = "Run",
                            value = run.runIndex.toString()
                        )

                        SummaryRow(
                            label = "Case",
                            value = run.caseId
                        )

                        SummaryRow(
                            label = "Transition",
                            value = "${run.transitionFromCaseId ?: "START"} → ${run.transitionToCaseId}"
                        )

                        SummaryRow(
                            label = "Expected class",
                            value = run.expectedClass.toString()
                        )

                        SummaryRow(
                            label = "Status",
                            value = run.status
                        )

                        SummaryRow(
                            label = "Detail code",
                            value = run.detailCode
                        )

                        SummaryRow(
                            label = "Progress",
                            value = run.progressClass.toString()
                        )

                        SummaryRow(
                            label = "Initial error",
                            value = "${formatDouble(run.initialError)} m"
                        )

                        SummaryRow(
                            label = "Final error",
                            value = "${formatDouble(run.finalError)} m"
                        )

                        SummaryRow(
                            label = "Improvement ratio",
                            value = formatPercent(run.improvementRatio)
                        )

                        SummaryRow(
                            label = "Iterations",
                            value = run.iterations.toString()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticReachableFailureInsightCard(
    report: Layer1DiagnosticReport
) {
    val reachableRuns =
        report
            .sequentialRuns()
            .filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }

    val rejectedReachableRuns =
        reachableRuns.filter {
            !it.solverAccepted
        }

    val rejectedNear =
        rejectedReachableRuns.count {
            it.isNearConverged()
        }

    val rejectedClose =
        rejectedReachableRuns.count {
            it.isCloseMiss()
        }

    val rejectedFar =
        rejectedReachableRuns.count {
            it.isFarFailure()
        }

    val mostCommonRejectedStatus =
        rejectedReachableRuns
            .groupBy {
                it.status
            }
            .maxByOrNull {
                it.value.size
            }
            ?.let {
                "${it.key} (${it.value.size})"
            }
            ?: "N/A"

    val mostCommonProgress =
        rejectedReachableRuns
            .groupBy {
                it.progressClass
            }
            .maxByOrNull {
                it.value.size
            }
            ?.let {
                "${it.key} (${it.value.size})"
            }
            ?: "N/A"

    val mostCommonSeedBucket =
        rejectedReachableRuns
            .groupBy {
                it.seedDistanceBucket
            }
            .maxByOrNull {
                it.value.size
            }
            ?.let {
                "${it.key} (${it.value.size})"
            }
            ?: "N/A"

    val interpretation =
        when {
            rejectedReachableRuns.isEmpty() ->
                "All reachable sequential targets were strictly accepted. Sequential IK recovery is stable for this configuration."

            rejectedNear > rejectedReachableRuns.size / 2 ->
                "Most rejected reachable runs are near-solved. The solver is close, but not reaching the configured strict tolerance."

            rejectedClose > rejectedReachableRuns.size / 2 ->
                "Most rejected reachable runs are close misses. More iterations, different damping, or larger safe steps may help."

            rejectedFar > rejectedReachableRuns.size / 2 ->
                "Most rejected reachable runs are far failures. This suggests seed sensitivity, difficult transitions, joint-limit effects, or local-minimum behavior."

            else ->
                "Rejected reachable runs are mixed. Compare transition aggregates, progress class, seed distance, and joint-limit pressure."
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEAF3FF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Reachable Failure Insight",
                style = MaterialTheme.typography.titleMedium
            )

            SummaryRow(
                label = "Reachable sequential runs",
                value = reachableRuns.size.toString()
            )

            SummaryRow(
                label = "Rejected reachable runs",
                value = rejectedReachableRuns.size.toString()
            )

            SummaryRow(
                label = "Rejected near-converged",
                value = rejectedNear.toString()
            )

            SummaryRow(
                label = "Rejected close miss",
                value = rejectedClose.toString()
            )

            SummaryRow(
                label = "Rejected far failure",
                value = rejectedFar.toString()
            )

            SummaryRow(
                label = "Most common rejected status",
                value = mostCommonRejectedStatus
            )

            SummaryRow(
                label = "Most common progress",
                value = mostCommonProgress
            )

            SummaryRow(
                label = "Most common seed bucket",
                value = mostCommonSeedBucket
            )

            Text(
                text = interpretation,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}