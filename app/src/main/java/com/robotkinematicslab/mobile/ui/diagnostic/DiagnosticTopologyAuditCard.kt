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
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyAuditSeverity
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import kotlin.math.max

@Composable
fun DiagnosticTopologyAuditCard(
    report: Layer1DiagnosticReport
) {
    val records =
        report.topologyAuditRecords

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3F8FF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Topology Audit",
                style = MaterialTheme.typography.titleMedium
            )

            if (records.isEmpty()) {
                Text(
                    text = "No topology audit records are attached to this report.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            val acceptedCount =
                records.count {
                    it.acceptedForBenchmark
                }

            val rejectedCount =
                records.count {
                    !it.acceptedForBenchmark
                }

            val safeClaimCount =
                records.count {
                    it.safeModeReliabilityClaimAllowed
                }

            val warningRecordCount =
                records.count {
                    it.warningCount > 0
                }

            val errorRecordCount =
                records.count {
                    it.errorCount > 0
                }

            SummaryRow(
                label = "Audited topologies",
                value = records.size.toString()
            )

            SummaryRow(
                label = "Accepted",
                value = acceptedCount.toString()
            )

            SummaryRow(
                label = "Rejected",
                value = rejectedCount.toString()
            )

            SummaryRow(
                label = "Safe reliability-claim topologies",
                value = safeClaimCount.toString()
            )

            SummaryRow(
                label = "Topologies with warnings",
                value = warningRecordCount.toString()
            )

            SummaryRow(
                label = "Topologies with errors",
                value = errorRecordCount.toString()
            )

            DiagnosticBar(
                label = "Accepted topology ratio",
                numerator = acceptedCount,
                denominator = max(1, records.size)
            )

            DiagnosticBar(
                label = "Safe-claim topology ratio",
                numerator = safeClaimCount,
                denominator = max(1, records.size)
            )

            records.forEach { record ->
                val cardColor =
                    when {
                        !record.acceptedForBenchmark ->
                            Color(0xFFFFEEEE)

                        record.warningCount > 0 ->
                            Color(0xFFFFF8E1)

                        else ->
                            Color.White
                    }

                DiagnosticDisclosureCard(
                    title = "Seed ${record.seed} — ${record.linkCount} links — ${record.jointMode}",
                    subtitle =
                        "${record.summaryLabel} · ${record.errorCount} errors · ${record.warningCount} warnings",
                    accentColor = cardColor
                ) {
                    Text(
                        text = "Detailed topology audit",
                        style = MaterialTheme.typography.titleSmall
                    )

                    SummaryRow(
                        label = "Audit label",
                        value = record.summaryLabel
                    )

                    SummaryRow(
                        label = "Accepted for benchmark",
                        value = record.acceptedForBenchmark.toString()
                    )

                    SummaryRow(
                        label = "Safe reliability claim allowed",
                        value = record.safeModeReliabilityClaimAllowed.toString()
                    )

                    SummaryRow(
                        label = "Estimated reach",
                        value = "${formatDouble(record.estimatedReachMeters)} m"
                    )

                    SummaryRow(
                        label = "Revolute joints",
                        value = record.revoluteCount.toString()
                    )

                    SummaryRow(
                        label = "Prismatic joints",
                        value = record.prismaticCount.toString()
                    )

                    SummaryRow(
                        label = "Prismatic ratio",
                        value = formatPercent(record.prismaticRatio)
                    )

                    SummaryRow(
                        label = "Issues",
                        value = "total=${record.issueCount}, errors=${record.errorCount}, warnings=${record.warningCount}, info=${record.infoCount}"
                    )

                    val importantIssues =
                        record.issues.filter {
                            it.severity == DiagnosticTopologyAuditSeverity.ERROR ||
                                    it.severity == DiagnosticTopologyAuditSeverity.WARNING
                        }

                    if (importantIssues.isEmpty()) {
                        Text(
                            text = "No warnings or errors.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        importantIssues.forEach { issue ->
                            Text(
                                text = "• ${issue.severity}: ${issue.code} — ${issue.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
