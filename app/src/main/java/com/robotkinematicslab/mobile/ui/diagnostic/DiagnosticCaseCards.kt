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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import kotlin.math.max

@Composable
fun DiagnosticPerCaseCard(
    report: Layer1DiagnosticReport
) {
    val sequentialByCase =
        remember(report) {
            report.sequentialRuns().groupBy { it.selectedCaseId }
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFDFDFD)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Per-Target Case Diagnostics",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${report.perCaseAggregates.size} cases · open one case to inspect its full evidence.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF52606D)
            )

            report.perCaseAggregates.forEach { item ->

                val sequentialRunsForCase =
                    sequentialByCase[item.caseId].orEmpty()

                val nearCount =
                    sequentialRunsForCase.count {
                        it.isNearConverged()
                    }

                val closeCount =
                    sequentialRunsForCase.count {
                        it.isCloseMiss()
                    }

                val farCount =
                    sequentialRunsForCase.count {
                        it.isFarFailure()
                    }

                val strictOrNearCount =
                    sequentialRunsForCase.count {
                        it.solverAccepted || it.isNearConverged()
                    }

                val background =
                    when (item.expectedClass) {
                        DiagnosticExpectedClass.REACHABLE ->
                            Color(0xFFEAF3FF)

                        DiagnosticExpectedClass.UNREACHABLE ->
                            Color(0xFFFFF3E0)
                    }

                DiagnosticDisclosureCard(
                    title = "${item.caseId} — ${item.expectedClass}",
                    subtitle =
                        "${item.sequentialRunCount} sequential runs · " +
                            "${formatPercent(item.averageSequentialImprovementRatio)} average improvement",
                    accentColor = background
                ) {
                    SummaryRow(
                        label = "Oracle runs",
                        value = item.oracleRunCount.toString()
                    )

                    SummaryRow(
                        label = "Oracle accepted",
                        value = item.oracleAcceptedCount.toString()
                    )

                    SummaryRow(
                        label = "Oracle rejected",
                        value = item.oracleRejectedCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential selected",
                        value = item.sequentialRunCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential strict accepted",
                        value = item.sequentialAcceptedCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential near-converged",
                        value = nearCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential close miss",
                        value = closeCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential far failure",
                        value = farCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential strict-or-near",
                        value = strictOrNearCount.toString()
                    )

                    SummaryRow(
                        label = "Sequential rejected",
                        value = item.sequentialRejectedCount.toString()
                    )

                    SummaryRow(
                        label = "Most common status",
                        value = item.mostCommonSequentialStatus
                    )

                    SummaryRow(
                        label = "Most common progress",
                        value = item.mostCommonProgressClass?.toString() ?: "N/A"
                    )

                    SummaryRow(
                        label = "Most common seed bucket",
                        value = item.mostCommonSeedDistanceBucket?.toString() ?: "N/A"
                    )

                    SummaryRow(
                        label = "Avg initial error",
                        value = "${formatDouble(item.averageSequentialInitialError)} m"
                    )

                    SummaryRow(
                        label = "Avg final error",
                        value = "${formatDouble(item.averageSequentialError)} m"
                    )

                    SummaryRow(
                        label = "Max final error",
                        value = "${formatDouble(item.maxSequentialError)} m"
                    )

                    SummaryRow(
                        label = "Avg improvement ratio",
                        value = formatPercent(item.averageSequentialImprovementRatio)
                    )

                    SummaryRow(
                        label = "Avg iterations",
                        value = formatDouble(item.averageSequentialIterations)
                    )

                    SummaryRow(
                        label = "Avg iteration saturation",
                        value = formatPercent(item.averageIterationSaturationRatio)
                    )

                    SummaryRow(
                        label = "Avg joint delta norm",
                        value = formatDouble(item.averageJointDeltaNorm)
                    )

                    SummaryRow(
                        label = "Avg max joint movement",
                        value = formatDouble(item.averageMaxSingleJointMovement)
                    )

                    SummaryRow(
                        label = "Avg joint-limit pressure",
                        value = formatPercent(item.averageJointLimitPressureRatio)
                    )

                    Text(
                        text =
                            "Target: x=${formatDouble(item.target.x)}, " +
                                    "y=${formatDouble(item.target.y)}, " +
                                    "z=${formatDouble(item.target.z)}"
                    )

                    if (item.sourceJointState != null) {
                        Text(
                            text =
                                "FK source q: ${
                                    item.sourceJointState
                                        .jointValues
                                        .joinToString(
                                            prefix = "[",
                                            postfix = "]"
                                        ) {
                                            formatDouble(it)
                                        }
                                }"
                        )
                    }

                    Text(
                        text = "Note: ${item.note}"
                    )

                    DiagnosticBar(
                        label = "Case strict-or-near",
                        numerator = strictOrNearCount,
                        denominator = max(1, item.sequentialRunCount)
                    )

                    DiagnosticBar(
                        label = "Case close-or-better",
                        numerator =
                            item.sequentialAcceptedCount +
                                    nearCount +
                                    closeCount,
                        denominator = max(1, item.sequentialRunCount)
                    )
                }
            }
        }
    }
}
