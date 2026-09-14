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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport

@Composable
fun DiagnosticChartsCard(
    report: Layer1DiagnosticReport
) {
    val colors = MaterialTheme.colorScheme
    val summary =
        report.summary

    val sequentialRuns =
        report.sequentialRuns()

    val reachableRuns =
        sequentialRuns.filter {
            it.expectedClass == DiagnosticExpectedClass.REACHABLE
        }

    val nearSequential =
        sequentialRuns.count {
            it.isNearConverged()
        }

    val closeSequential =
        sequentialRuns.count {
            it.isCloseMiss()
        }

    val nearReachable =
        reachableRuns.count {
            it.isNearConverged()
        }

    val closeReachable =
        reachableRuns.count {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = diagnosticDistributionContainerColor(colors),
            contentColor = diagnosticDistributionContentColor(colors)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Diagnostic Ratios",
                style = MaterialTheme.typography.titleMedium
            )

            DiagnosticBar(
                label = "Oracle reachable strict recovery",
                numerator = summary.oracleReachableAccepted,
                denominator = summary.oracleReachableRuns
            )

            DiagnosticBar(
                label = "Sequential strict accepted",
                numerator = summary.sequentialAcceptedCount,
                denominator = summary.sequentialRuns
            )

            DiagnosticBar(
                label = "Sequential near-converged",
                numerator = nearSequential,
                denominator = summary.sequentialRuns
            )

            DiagnosticBar(
                label = "Sequential close miss",
                numerator = closeSequential,
                denominator = summary.sequentialRuns
            )

            DiagnosticBar(
                label = "Sequential strict-or-near",
                numerator = strictOrNearSequential,
                denominator = summary.sequentialRuns
            )

            DiagnosticBar(
                label = "Reachable strict-or-near",
                numerator = strictOrNearReachable,
                denominator = summary.sequentialReachableRuns
            )

            DiagnosticBar(
                label = "Reachable near-converged",
                numerator = nearReachable,
                denominator = summary.sequentialReachableRuns
            )

            DiagnosticBar(
                label = "Reachable close miss",
                numerator = closeReachable,
                denominator = summary.sequentialReachableRuns
            )

            DiagnosticBar(
                label = "Unreachable rejected",
                numerator = summary.sequentialUnreachableRejected,
                denominator = summary.sequentialUnreachableRuns
            )
        }
    }
}

@Composable
fun DiagnosticStatusDistributionCard(
    report: Layer1DiagnosticReport
) {
    val colors = MaterialTheme.colorScheme
    val sequentialRuns =
        report.sequentialRuns()

    val statusCounts =
        sequentialRuns
            .groupBy {
                it.status
            }
            .mapValues {
                it.value.size
            }
            .toList()
            .sortedByDescending {
                it.second
            }

    val detailCounts =
        sequentialRuns
            .groupBy {
                it.detailCode
            }
            .mapValues {
                it.value.size
            }
            .toList()
            .sortedByDescending {
                it.second
            }

    val reachableStatusCounts =
        sequentialRuns
            .filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }
            .groupBy {
                it.status
            }
            .mapValues {
                it.value.size
            }
            .toList()
            .sortedByDescending {
                it.second
            }

    val unreachableStatusCounts =
        sequentialRuns
            .filter {
                it.expectedClass == DiagnosticExpectedClass.UNREACHABLE
            }
            .groupBy {
                it.status
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
            containerColor = diagnosticDistributionContainerColor(colors),
            contentColor = diagnosticDistributionContentColor(colors)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Status and Detail Code Distribution",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "All sequential status values",
                style = MaterialTheme.typography.titleSmall
            )

            if (statusCounts.isEmpty()) {
                Text("No sequential runs available.")
            } else {
                statusCounts.forEach { item ->
                    DiagnosticBar(
                        label = item.first,
                        numerator = item.second,
                        denominator = sequentialRuns.size
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "All sequential detail codes",
                style = MaterialTheme.typography.titleSmall
            )

            if (detailCounts.isEmpty()) {
                Text("No detail codes available.")
            } else {
                detailCounts.forEach { item ->
                    DiagnosticBar(
                        label = item.first,
                        numerator = item.second,
                        denominator = sequentialRuns.size
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Reachable sequential status",
                style = MaterialTheme.typography.titleSmall
            )

            reachableStatusCounts.forEach { item ->
                DiagnosticBar(
                    label = item.first,
                    numerator = item.second,
                    denominator = report.summary.sequentialReachableRuns
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Unreachable sequential status",
                style = MaterialTheme.typography.titleSmall
            )

            unreachableStatusCounts.forEach { item ->
                DiagnosticBar(
                    label = item.first,
                    numerator = item.second,
                    denominator = report.summary.sequentialUnreachableRuns
                )
            }
        }
    }
}

@Composable
fun DiagnosticErrorDistributionCard(
    report: Layer1DiagnosticReport
) {
    val colors = MaterialTheme.colorScheme
    val sequentialRuns =
        report.sequentialRuns()

    val strictSuccessCount =
        sequentialRuns.count {
            it.solverAccepted
        }

    val nearMissCount =
        sequentialRuns.count {
            it.isNearConverged()
        }

    val closeMissCount =
        sequentialRuns.count {
            it.isCloseMiss()
        }

    val farFailureCount =
        sequentialRuns.count {
            it.isFarFailure()
        }

    val nonFiniteCount =
        sequentialRuns.count {
            !it.finalError.isFinite()
        }

    val reachableRuns =
        sequentialRuns.filter {
            it.expectedClass == DiagnosticExpectedClass.REACHABLE
        }

    val reachableStrictSuccessCount =
        reachableRuns.count {
            it.solverAccepted
        }

    val reachableNearMissCount =
        reachableRuns.count {
            it.isNearConverged()
        }

    val reachableCloseMissCount =
        reachableRuns.count {
            it.isCloseMiss()
        }

    val reachableFarFailureCount =
        reachableRuns.count {
            it.isFarFailure()
        }

    val reachableNonFiniteCount =
        reachableRuns.count {
            !it.finalError.isFinite()
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = diagnosticDistributionContainerColor(colors),
            contentColor = diagnosticDistributionContentColor(colors)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Error Distribution",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "All sequential runs",
                style = MaterialTheme.typography.titleSmall
            )

            DiagnosticBar(
                label = "Strict success",
                numerator = strictSuccessCount,
                denominator = sequentialRuns.size
            )

            DiagnosticBar(
                label = "Near-converged ≤ ${formatDouble(NEAR_SUCCESS_ERROR_METERS)} m",
                numerator = nearMissCount,
                denominator = sequentialRuns.size
            )

            DiagnosticBar(
                label = "Close miss ≤ ${formatDouble(CLOSE_ERROR_METERS)} m",
                numerator = closeMissCount,
                denominator = sequentialRuns.size
            )

            DiagnosticBar(
                label = "Far failure > ${formatDouble(CLOSE_ERROR_METERS)} m",
                numerator = farFailureCount,
                denominator = sequentialRuns.size
            )

            DiagnosticBar(
                label = "Non-finite error",
                numerator = nonFiniteCount,
                denominator = sequentialRuns.size
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Reachable sequential runs",
                style = MaterialTheme.typography.titleSmall
            )

            DiagnosticBar(
                label = "Reachable strict success",
                numerator = reachableStrictSuccessCount,
                denominator = reachableRuns.size
            )

            DiagnosticBar(
                label = "Reachable near-converged",
                numerator = reachableNearMissCount,
                denominator = reachableRuns.size
            )

            DiagnosticBar(
                label = "Reachable close miss",
                numerator = reachableCloseMissCount,
                denominator = reachableRuns.size
            )

            DiagnosticBar(
                label = "Reachable far failure",
                numerator = reachableFarFailureCount,
                denominator = reachableRuns.size
            )

            DiagnosticBar(
                label = "Reachable non-finite error",
                numerator = reachableNonFiniteCount,
                denominator = reachableRuns.size
            )
        }
    }
}

internal fun diagnosticDistributionContainerColor(colors: ColorScheme) = colors.surface

internal fun diagnosticDistributionContentColor(colors: ColorScheme) = colors.onSurface
