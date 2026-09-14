package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import kotlin.math.max

@Composable
fun DiagnosticSuccessByLinkCountGraphCard(
    report: Layer1DiagnosticReport
) {
    if (report.linkCountAggregates.isEmpty()) {
        return
    }

    ChartSectionCard(
        title = "Success by Link Count",
        subtitle = "Compares strict and close-or-better solver outcomes for each robot link count.",
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Success by Link Count",
                subtitle = "Compares strict and close-or-better solver outcomes for each robot link count.",
                directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            report.linkCountAggregates
                .sortedBy { it.linkCount }
                .forEach { aggregate ->

                    val closeOrBetterCount =
                        aggregate.strictAcceptedCount +
                                aggregate.nearSolvedCount +
                                aggregate.closeMissCount

                    JargonAwareText(
                        text = "${aggregate.linkCount} links",
                        style = MaterialTheme.typography.titleSmall
                    )

                    DiagnosticBar(
                        label = "Strict success",
                        numerator = aggregate.strictAcceptedCount,
                        denominator = max(1, aggregate.runCount)
                    )

                    DiagnosticBar(
                        label = "Close-or-better",
                        numerator = closeOrBetterCount,
                        denominator = max(1, aggregate.runCount)
                    )

                    SummaryRow(
                        label = "Average final error",
                        value = "${formatDouble(aggregate.averageFinalError)} m"
                    )

                    SummaryRow(
                        label = "Average iterations",
                        value = formatDouble(aggregate.averageIterations)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
        }
    }
}

@Composable
fun DiagnosticFailureCodeGraphCard(
    report: Layer1DiagnosticReport
) {
    val sequentialRuns =
        report.sequentialRuns()

    val failedRuns =
        sequentialRuns.filter {
            !it.solverAccepted
        }

    val detailCodeCounts =
        failedRuns
            .groupBy { it.detailCode }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

    ChartSectionCard(
        title = "Failure-Code Distribution",
        subtitle = "Shows which recorded failure codes account for unsuccessful sequential runs.",
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Failure-Code Distribution",
                subtitle = "Shows which recorded failure codes account for unsuccessful sequential runs.",
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryRow(
                label = "Failed sequential runs",
                value = failedRuns.size.toString()
            )

            if (detailCodeCounts.isEmpty()) {
                JargonAwareText(
                    text = "No failed sequential runs.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                detailCodeCounts.forEach { item ->
                    val detailCode =
                        item.first

                    val count =
                        item.second

                    DiagnosticBar(
                        label = detailCode,
                        numerator = count,
                        denominator = max(1, failedRuns.size)
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticFinalErrorBucketGraphCard(
    report: Layer1DiagnosticReport
) {
    val sequentialRuns =
        report.sequentialRuns()

    val finiteRuns =
        sequentialRuns.filter {
            it.finalError.isFinite()
        }

    val exactOrStrict =
        finiteRuns.count {
            it.solverAccepted
        }

    val near =
        finiteRuns.count {
            !it.solverAccepted &&
                    it.finalError <= NEAR_SUCCESS_ERROR_METERS
        }

    val close =
        finiteRuns.count {
            !it.solverAccepted &&
                    it.finalError > NEAR_SUCCESS_ERROR_METERS &&
                    it.finalError <= CLOSE_ERROR_METERS
        }

    val medium =
        finiteRuns.count {
            !it.solverAccepted &&
                    it.finalError > CLOSE_ERROR_METERS &&
                    it.finalError <= 0.10
        }

    val large =
        finiteRuns.count {
            !it.solverAccepted &&
                    it.finalError > 0.10 &&
                    it.finalError <= 0.50
        }

    val extreme =
        finiteRuns.count {
            !it.solverAccepted &&
                    it.finalError > 0.50
        }

    val nonFinite =
        sequentialRuns.count {
            !it.finalError.isFinite()
        }

    ChartSectionCard(
        title = "Final-Error Distribution",
        subtitle = "Groups final Cartesian residuals into strict, near, close, large and invalid outcome bands.",
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HISTOGRAM,
                title = "Final-Error Distribution",
                subtitle = "Groups final Cartesian residuals into strict, near, close, large and invalid outcome bands.",
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryRow(
                label = "Finite error samples",
                value = finiteRuns.size.toString()
            )

            DiagnosticBar(
                label = "Strict accepted",
                numerator = exactOrStrict,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Near ≤ ${formatDouble(NEAR_SUCCESS_ERROR_METERS)} m",
                numerator = near,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Close ≤ ${formatDouble(CLOSE_ERROR_METERS)} m",
                numerator = close,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Medium ≤ 0.10 m",
                numerator = medium,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Large ≤ 0.50 m",
                numerator = large,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Extreme > 0.50 m",
                numerator = extreme,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Non-finite",
                numerator = nonFinite,
                denominator = max(1, sequentialRuns.size)
            )
        }
    }
}

@Composable
fun DiagnosticIterationBucketGraphCard(
    report: Layer1DiagnosticReport
) {
    val sequentialRuns =
        report.sequentialRuns()

    val low =
        sequentialRuns.count {
            it.iterationSaturationRatio.isFinite() &&
                    it.iterationSaturationRatio < 0.25
        }

    val medium =
        sequentialRuns.count {
            it.iterationSaturationRatio.isFinite() &&
                    it.iterationSaturationRatio >= 0.25 &&
                    it.iterationSaturationRatio < 0.50
        }

    val high =
        sequentialRuns.count {
            it.iterationSaturationRatio.isFinite() &&
                    it.iterationSaturationRatio >= 0.50 &&
                    it.iterationSaturationRatio < 0.90
        }

    val saturated =
        sequentialRuns.count {
            it.iterationSaturationRatio.isFinite() &&
                    it.iterationSaturationRatio >= 0.90
        }

    val unknown =
        sequentialRuns.count {
            !it.iterationSaturationRatio.isFinite()
        }

    ChartSectionCard(
        title = "Iteration Distribution",
        subtitle = "Shows how much of the configured iteration budget each sequential solve consumed.",
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HISTOGRAM,
                title = "Iteration Distribution",
                subtitle = "Shows how much of the configured iteration budget each sequential solve consumed.",
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryRow(
                label = "Average iterations",
                value = formatDouble(report.summary.averageSequentialIterations)
            )

            SummaryRow(
                label = "Average saturation",
                value = formatPercent(report.summary.averageIterationSaturationRatio)
            )

            DiagnosticBar(
                label = "Low saturation < 25%",
                numerator = low,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Medium saturation 25–50%",
                numerator = medium,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "High saturation 50–90%",
                numerator = high,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Saturated ≥ 90%",
                numerator = saturated,
                denominator = max(1, sequentialRuns.size)
            )

            DiagnosticBar(
                label = "Unknown",
                numerator = unknown,
                denominator = max(1, sequentialRuns.size)
            )
        }
    }
}

@Composable
fun DiagnosticSeedSensitivityGraphCard(
    report: Layer1DiagnosticReport
) {
    if (report.seedAggregates.isEmpty()) {
        return
    }

    val seedSummary =
        report.seedComparisonSummary

    ChartSectionCard(
        title = "Seed Sensitivity",
        subtitle = "Compares outcome stability across deterministic random seeds.",
        guide =
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Seed Sensitivity",
                subtitle = "Compares outcome stability across deterministic random seeds.",
                directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryRow(
                label = "Seed count",
                value = seedSummary.seedCount.toString()
            )

            SummaryRow(
                label = "Best seed",
                value = seedSummary.bestSeed?.toString() ?: "N/A"
            )

            SummaryRow(
                label = "Worst seed",
                value = seedSummary.worstSeed?.toString() ?: "N/A"
            )

            SummaryRow(
                label = "Average strict acceptance",
                value = formatPercent(seedSummary.averageStrictAcceptanceRate)
            )

            SummaryRow(
                label = "Acceptance spread",
                value = formatPercent(seedSummary.strictAcceptanceRateSpread)
            )

            SummaryRow(
                label = "Seed sensitivity label",
                value = seedSummary.seedSensitivityLabel
            )

            report.seedAggregates
                .sortedBy { it.seed }
                .forEach { aggregate ->

                    JargonAwareText(
                        text = "Seed ${aggregate.seed}",
                        style = MaterialTheme.typography.titleSmall
                    )

                    DiagnosticBar(
                        label = "Strict success",
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

                    SummaryRow(
                        label = "Average final error",
                        value = "${formatDouble(aggregate.averageFinalError)} m"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                }
        }
    }
}
