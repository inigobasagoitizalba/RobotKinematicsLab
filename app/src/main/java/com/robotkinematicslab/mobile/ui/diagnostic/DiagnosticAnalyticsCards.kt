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
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import kotlin.math.max

@Composable
fun DiagnosticLinkCountAnalyticsCard(
    report: Layer1DiagnosticReport
) {
    if (report.linkCountAggregates.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF9FBFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Link Count Analytics",
                style = MaterialTheme.typography.titleMedium
            )

            report.linkCountAggregates
                .sortedBy { it.linkCount }
                .forEach { aggregate ->

                    val successColor =
                        when {
                            aggregate.strictAcceptanceRate >= 0.90 ->
                                Color(0xFF2E7D32)

                            aggregate.strictAcceptanceRate >= 0.70 ->
                                Color(0xFFF9A825)

                            else ->
                                Color(0xFFC62828)
                        }

                    DiagnosticDisclosureCard(
                        title = "${aggregate.linkCount} links",
                        subtitle =
                            "${aggregate.runCount} runs · ${formatPercent(aggregate.strictAcceptanceRate)} strict acceptance",
                        accentColor = Color.White
                    ) {
                        Text(
                            text = "Detailed metrics",
                            style = MaterialTheme.typography.titleSmall,
                            color = successColor
                        )

                        SummaryRow("Runs", aggregate.runCount.toString())
                        SummaryRow("Strict accepted", aggregate.strictAcceptedCount.toString())
                        SummaryRow("Near solved", aggregate.nearSolvedCount.toString())
                        SummaryRow("Close miss", aggregate.closeMissCount.toString())
                        SummaryRow("Far failure", aggregate.farFailureCount.toString())
                        SummaryRow("Acceptance rate", formatPercent(aggregate.strictAcceptanceRate))
                        SummaryRow("Avg final error", "${formatDouble(aggregate.averageFinalError)} m")
                        SummaryRow("Max final error", "${formatDouble(aggregate.maxFinalError)} m")
                        SummaryRow("Avg iterations", formatDouble(aggregate.averageIterations))
                        SummaryRow("Avg improvement ratio", formatPercent(aggregate.averageImprovementRatio))

                        DiagnosticBar(
                            label = "Strict acceptance",
                            numerator = aggregate.strictAcceptedCount,
                            denominator = max(1, aggregate.runCount)
                        )

                        DiagnosticBar(
                            label = "Close-or-better",
                            numerator =
                                aggregate.strictAcceptedCount +
                                        aggregate.nearSolvedCount +
                                        aggregate.closeMissCount,
                            denominator = max(1, aggregate.runCount)
                        )
                    }
                }
        }
    }
}

@Composable
fun DiagnosticTopologyAnalyticsCard(
    report: Layer1DiagnosticReport
) {
    if (report.topologyAggregates.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3F8FF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Topology Analytics",
                style = MaterialTheme.typography.titleMedium
            )

            report.topologyAggregates.forEach { aggregate ->

                val successColor =
                    when {
                        aggregate.strictAcceptanceRate >= 0.90 ->
                            Color(0xFF2E7D32)

                        aggregate.strictAcceptanceRate >= 0.70 ->
                            Color(0xFFF9A825)

                        else ->
                            Color(0xFFC62828)
                    }

                DiagnosticDisclosureCard(
                    title = aggregate.jointMode.name,
                    subtitle =
                        "${aggregate.runCount} runs · ${formatPercent(aggregate.strictAcceptanceRate)} strict acceptance",
                    accentColor = Color.White
                ) {
                    Text(
                        text = "Detailed metrics",
                        style = MaterialTheme.typography.titleSmall,
                        color = successColor
                    )

                    SummaryRow("Runs", aggregate.runCount.toString())
                    SummaryRow("Strict accepted", aggregate.strictAcceptedCount.toString())
                    SummaryRow("Near solved", aggregate.nearSolvedCount.toString())
                    SummaryRow("Close miss", aggregate.closeMissCount.toString())
                    SummaryRow("Far failure", aggregate.farFailureCount.toString())
                    SummaryRow("Acceptance rate", formatPercent(aggregate.strictAcceptanceRate))
                    SummaryRow("Avg final error", "${formatDouble(aggregate.averageFinalError)} m")
                    SummaryRow("Max final error", "${formatDouble(aggregate.maxFinalError)} m")
                    SummaryRow("Avg iterations", formatDouble(aggregate.averageIterations))
                    SummaryRow("Avg improvement ratio", formatPercent(aggregate.averageImprovementRatio))

                    DiagnosticBar(
                        label = "Strict acceptance",
                        numerator = aggregate.strictAcceptedCount,
                        denominator = max(1, aggregate.runCount)
                    )

                    DiagnosticBar(
                        label = "Close-or-better",
                        numerator =
                            aggregate.strictAcceptedCount +
                                    aggregate.nearSolvedCount +
                                    aggregate.closeMissCount,
                        denominator = max(1, aggregate.runCount)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticSeedAnalyticsCard(
    report: Layer1DiagnosticReport
) {
    if (report.seedAggregates.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFBF2)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Seed Analytics",
                style = MaterialTheme.typography.titleMedium
            )

            report.seedAggregates
                .sortedBy { it.seed }
                .forEach { aggregate ->

                    val successColor =
                        when {
                            aggregate.strictAcceptanceRate >= 0.90 ->
                                Color(0xFF2E7D32)

                            aggregate.strictAcceptanceRate >= 0.70 ->
                                Color(0xFFF9A825)

                            else ->
                                Color(0xFFC62828)
                        }

                    DiagnosticDisclosureCard(
                        title = "Seed ${aggregate.seed}",
                        subtitle =
                            "${aggregate.runCount} runs · ${formatPercent(aggregate.strictAcceptanceRate)} strict acceptance",
                        accentColor = Color.White
                    ) {
                        Text(
                            text = "Detailed metrics",
                            style = MaterialTheme.typography.titleSmall,
                            color = successColor
                        )

                        SummaryRow("Runs", aggregate.runCount.toString())
                        SummaryRow("Strict accepted", aggregate.strictAcceptedCount.toString())
                        SummaryRow("Near solved", aggregate.nearSolvedCount.toString())
                        SummaryRow("Close miss", aggregate.closeMissCount.toString())
                        SummaryRow("Far failure", aggregate.farFailureCount.toString())
                        SummaryRow("Acceptance rate", formatPercent(aggregate.strictAcceptanceRate))
                        SummaryRow("Avg final error", "${formatDouble(aggregate.averageFinalError)} m")
                        SummaryRow("Max final error", "${formatDouble(aggregate.maxFinalError)} m")
                        SummaryRow("Avg iterations", formatDouble(aggregate.averageIterations))
                        SummaryRow("Avg improvement ratio", formatPercent(aggregate.averageImprovementRatio))

                        DiagnosticBar(
                            label = "Strict acceptance",
                            numerator = aggregate.strictAcceptedCount,
                            denominator = max(1, aggregate.runCount)
                        )

                        DiagnosticBar(
                            label = "Close-or-better",
                            numerator =
                                aggregate.strictAcceptedCount +
                                        aggregate.nearSolvedCount +
                                        aggregate.closeMissCount,
                            denominator = max(1, aggregate.runCount)
                        )
                    }
                }
        }
    }
}
