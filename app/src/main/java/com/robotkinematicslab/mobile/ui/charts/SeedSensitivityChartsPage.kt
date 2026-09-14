package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GaugeChart
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import kotlin.math.sqrt

@Composable
fun SeedSensitivityChartsPage(
    report: Layer1DiagnosticReport
) {
    val aggregates =
        report.seedAggregates.sortedBy {
            it.seed
        }

    if (aggregates.isEmpty()) {
        EmptyChartDataCard(
            title = "🌱 Seed Sensitivity",
            subtitle = "No seed aggregate data is available for this diagnostic report."
        )

        return
    }

    val acceptanceRates =
        aggregates.map {
            it.strictAcceptanceRate
        }

    val bestSeed =
        aggregates.maxByOrNull {
            it.strictAcceptanceRate
        }

    val worstSeed =
        aggregates.minByOrNull {
            it.strictAcceptanceRate
        }

    val averageAcceptanceRate =
        acceptanceRates.averageSafe()

    val acceptanceSpread =
        if (acceptanceRates.isNotEmpty()) {
            val maxRate =
                acceptanceRates.maxOrNull() ?: 0.0

            val minRate =
                acceptanceRates.minOrNull() ?: 0.0

            maxRate - minRate
        } else {
            0.0
        }

    val acceptanceVariance =
        varianceOf(acceptanceRates)

    val acceptanceStandardDeviation =
        sqrt(acceptanceVariance)

    val seedSensitivityLabel =
        seedSensitivityLabel(
            seedCount = aggregates.size,
            spread = acceptanceSpread,
            standardDeviation = acceptanceStandardDeviation
        )

    ChartSectionCard(
        title = "🌱 Seed Sensitivity Summary",
        subtitle = "Shows whether the diagnostic result is stable across random seeds."
    ) {
        ChartMetricRow(
            label = "Seed count",
            value = aggregates.size.toString()
        )

        ChartMetricRow(
            label = "Best seed",
            value =
                if (bestSeed != null) {
                    "${bestSeed.seed} (${formatChartPercent(bestSeed.strictAcceptanceRate)})"
                } else {
                    "NA"
                }
        )

        ChartMetricRow(
            label = "Worst seed",
            value =
                if (worstSeed != null) {
                    "${worstSeed.seed} (${formatChartPercent(worstSeed.strictAcceptanceRate)})"
                } else {
                    "NA"
                }
        )

        ChartMetricRow(
            label = "Average strict acceptance",
            value = formatChartPercent(averageAcceptanceRate)
        )

        ChartMetricRow(
            label = "Strict acceptance spread",
            value = formatChartPercent(acceptanceSpread)
        )

        ChartMetricRow(
            label = "Strict acceptance variance",
            value = formatChartDouble(acceptanceVariance, digits = 6)
        )

        ChartMetricRow(
            label = "Strict acceptance std. dev.",
            value = formatChartPercent(acceptanceStandardDeviation)
        )

        ChartMetricRow(
            label = "Seed sensitivity",
            value = seedSensitivityLabel
        )
    }

    GroupedRatioBarChart(
        title = "🌱 Strict Acceptance Rate by Seed",
        subtitle = "Shows best and worst seeds for strict IK success.",
        items =
            aggregates.map { aggregate ->
                ChartRatioItem(
                    label = "Seed ${aggregate.seed}",
                    ratio = aggregate.strictAcceptanceRate,
                    displayValue = formatChartPercent(aggregate.strictAcceptanceRate),
                    color = seedSuccessColor(aggregate.strictAcceptanceRate)
                )
            }
    )

    GroupedRatioBarChart(
        title = "🌱 Close-or-Better Rate by Seed",
        subtitle = "Shows whether low strict success is a hard failure or mostly near/close misses.",
        items =
            aggregates.map { aggregate ->
                val closeOrBetterCount =
                    aggregate.strictAcceptedCount +
                            aggregate.nearSolvedCount +
                            aggregate.closeMissCount

                val closeOrBetterRate =
                    safeRatio(
                        numerator = closeOrBetterCount,
                        denominator = aggregate.runCount
                    )

                ChartRatioItem(
                    label = "Seed ${aggregate.seed}",
                    ratio = closeOrBetterRate,
                    displayValue = formatChartPercent(closeOrBetterRate),
                    color = seedSuccessColor(closeOrBetterRate)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🌱 Average Final Error by Seed",
        subtitle = "Shows seed-specific residual error severity.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "Seed ${aggregate.seed}",
                    value = aggregate.averageFinalError,
                    displayValue = "${formatChartDouble(aggregate.averageFinalError)} m",
                    color = seedErrorColor(aggregate.averageFinalError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🌱 Max Final Error by Seed",
        subtitle = "Shows worst-case seed behavior.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "Seed ${aggregate.seed}",
                    value = aggregate.maxFinalError,
                    displayValue = "${formatChartDouble(aggregate.maxFinalError)} m",
                    color = seedErrorColor(aggregate.maxFinalError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "🌱 Average Iterations by Seed",
        subtitle = "Shows seed-dependent computational cost.",
        items =
            aggregates.map { aggregate ->
                ChartDoubleBarItem(
                    label = "Seed ${aggregate.seed}",
                    value = aggregate.averageIterations,
                    displayValue = formatChartDouble(aggregate.averageIterations),
                    color = Color(0xFF1565C0)
                )
            }
    )

    GaugeChart(
        title = "🌱 Seed Acceptance Spread",
        subtitle = "Visualizes how far apart the best and worst seeds are.",
        ratio = acceptanceSpread.coerceIn(0.0, 1.0),
        valueLabel = formatChartPercent(acceptanceSpread),
        color = seedSpreadColor(acceptanceSpread)
    )

    GroupedRatioBarChart(
        title = "🌱 Seed Variance Chart",
        subtitle = "Small stability view. Lower variance means the multi-seed result is more stable.",
        items = listOf(
            ChartRatioItem(
                label = "Acceptance std. dev.",
                ratio = acceptanceStandardDeviation.coerceIn(0.0, 1.0),
                displayValue = formatChartPercent(acceptanceStandardDeviation),
                color = seedSpreadColor(acceptanceStandardDeviation)
            ),
            ChartRatioItem(
                label = "Acceptance spread",
                ratio = acceptanceSpread.coerceIn(0.0, 1.0),
                displayValue = formatChartPercent(acceptanceSpread),
                color = seedSpreadColor(acceptanceSpread)
            )
        )
    )

    ChartSectionCard(
        title = "🌱 Seed × link-count heat map",
        subtitle = "Reserved for multi-link, multi-seed matrix diagnostics."
    ) {
        ChartMetricRow(
            label = "Status",
            value = "Coming soon"
        )

        ChartMetricRow(
            label = "Rows",
            value = "Seeds"
        )

        ChartMetricRow(
            label = "Columns",
            value = "Link counts"
        )

        ChartMetricRow(
            label = "Cell value",
            value = "Acceptance rate"
        )
    }

    ChartSectionCard(
        title = "🌱 Seed × topology heat map",
        subtitle = "Reserved for runAllTopologies + multi-seed diagnostics."
    ) {
        ChartMetricRow(
            label = "Status",
            value = "Coming soon"
        )

        ChartMetricRow(
            label = "Rows",
            value = "Seeds"
        )

        ChartMetricRow(
            label = "Columns",
            value = "Topology modes"
        )

        ChartMetricRow(
            label = "Cell value",
            value = "Acceptance rate"
        )
    }
}

private fun List<Double>.averageSafe(): Double {
    val finiteValues =
        filter {
            it.isFinite()
        }

    return if (finiteValues.isEmpty()) {
        0.0
    } else {
        finiteValues.average()
    }
}

private fun varianceOf(
    values: List<Double>
): Double {
    val finiteValues =
        values.filter {
            it.isFinite()
        }

    if (finiteValues.isEmpty()) {
        return 0.0
    }

    val average =
        finiteValues.average()

    return finiteValues
        .map { value ->
            val delta =
                value - average

            delta * delta
        }
        .average()
}

private fun seedSensitivityLabel(
    seedCount: Int,
    spread: Double,
    standardDeviation: Double
): String {
    return when {
        seedCount <= 1 ->
            "Single seed only"

        spread < 0.05 && standardDeviation < 0.025 ->
            "LOW_SEED_SENSITIVITY"

        spread < 0.15 && standardDeviation < 0.075 ->
            "MEDIUM_SEED_SENSITIVITY"

        else ->
            "HIGH_SEED_SENSITIVITY"
    }
}

private fun seedSuccessColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.70 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun seedErrorColor(
    error: Double
): Color {
    return when {
        !error.isFinite() ->
            Color(0xFFC62828)

        error <= 0.01 ->
            Color(0xFF2E7D32)

        error <= 0.10 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun seedSpreadColor(
    spread: Double
): Color {
    return when {
        spread < 0.05 ->
            Color(0xFF2E7D32)

        spread < 0.15 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}
