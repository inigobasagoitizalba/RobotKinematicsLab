package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartRatioItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.GroupedRatioBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun SeedDistanceChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>,
    displayRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        EmptyChartDataCard(
            title = "📍 Seed Distance / Target Difficulty",
            subtitle = "No sequential run data is available for seed-distance charts."
        )

        return
    }

    val bucketOrder =
        listOf(
            "EASY",
            "MEDIUM",
            "HARD",
            "EXTREME",
            "UNKNOWN"
        )

    val runsByBucket =
        sequentialRuns.groupBy {
            normalizeSeedBucket(it.seedDistanceBucket.toString())
        }

    HorizontalBarChart(
        title = "📍 Seed Distance Bucket Distribution",
        items =
            bucketOrder.map { bucket ->
                ChartBarItem(
                    label = bucket,
                    value = runsByBucket[bucket]?.size ?: 0,
                    color = seedBucketColor(bucket)
                )
            },
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    GroupedRatioBarChart(
        title = "📍 Acceptance by Seed-Distance Bucket",
        subtitle = "Shows success probability by seed difficulty.",
        items =
            bucketOrder.map { bucket ->
                val runs =
                    runsByBucket[bucket].orEmpty()

                val acceptanceRate =
                    safeRatio(
                        numerator = runs.count { it.solverAccepted },
                        denominator = runs.size
                    )

                ChartRatioItem(
                    label = bucket,
                    ratio = acceptanceRate,
                    displayValue = formatChartPercent(acceptanceRate),
                    color = acceptanceColor(acceptanceRate)
                )
            },
        directionOverride = ChartReadingDirection.HIGHER_TENDS_BETTER
    )

    HorizontalDoubleBarChart(
        title = "📍 Final Error by Seed-Distance Bucket",
        subtitle = "Shows how difficulty maps to residual error.",
        items =
            bucketOrder.map { bucket ->
                val averageFinalError =
                    runsByBucket[bucket]
                        .orEmpty()
                        .map {
                            it.finalError
                        }
                        .averageSafe()

                ChartDoubleBarItem(
                    label = bucket,
                    value = averageFinalError,
                    displayValue = "${formatChartDouble(averageFinalError)} m",
                    color = errorColor(averageFinalError)
                )
            }
    )

    HorizontalDoubleBarChart(
        title = "📍 Iterations by Seed-Distance Bucket",
        subtitle = "Shows computational cost of hard seeds.",
        items =
            bucketOrder.map { bucket ->
                val averageIterations =
                    runsByBucket[bucket]
                        .orEmpty()
                        .map {
                            it.iterations.toDouble()
                        }
                        .averageSafe()

                ChartDoubleBarItem(
                    label = bucket,
                    value = averageIterations,
                    displayValue = formatChartDouble(averageIterations),
                    color = Color(0xFF1565C0)
                )
            }
    )

    SeedDifficultyScatterChart(
        title = "📍 Seed Distance vs Final Error",
        subtitle = "Shows whether harder initial seeds produce larger residual error.",
        points =
            displayRuns.map { run ->
                SeedDifficultyPoint(
                    x = seedBucketNumeric(run.seedDistanceBucket.toString()),
                    y = run.finalError,
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Seed bucket difficulty",
        yLabel = "Final error"
    )

    SeedDifficultyScatterChart(
        title = "📍 Seed Distance vs Improvement Ratio",
        subtitle = "Shows whether far seeds still improve.",
        points =
            displayRuns.map { run ->
                SeedDifficultyPoint(
                    x = seedBucketNumeric(run.seedDistanceBucket.toString()),
                    y = run.improvementRatio.coerceIn(0.0, 1.0),
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Seed bucket difficulty",
        yLabel = "Improvement ratio"
    )

    SeedDifficultyScatterChart(
        title = "📍 Seed Distance vs Iterations",
        subtitle = "Shows computational difficulty as seed distance increases.",
        points =
            displayRuns.map { run ->
                SeedDifficultyPoint(
                    x = seedBucketNumeric(run.seedDistanceBucket.toString()),
                    y = run.iterations.toDouble(),
                    color =
                        if (run.solverAccepted) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                )
            },
        xLabel = "Seed bucket difficulty",
        yLabel = "Iterations"
    )

    SeedBucketByExpectedClassChart(
        runs = sequentialRuns,
        bucketOrder = bucketOrder
    )
}

@Composable
private fun SeedBucketByExpectedClassChart(
    runs: List<DiagnosticRunResult>,
    bucketOrder: List<String>
) {
    val expectedClasses =
        runs
            .map {
                it.expectedClass.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "📍 Seed Bucket by Expected Class",
        subtitle = "Shows whether unreachable cases dominate harder seed buckets."
    ) {
        if (expectedClasses.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No expected-class data available"
            )

            return@ChartSectionCard
        }

        expectedClasses.forEach { expectedClass ->
            val classRuns =
                runs.filter {
                    it.expectedClass.toString() == expectedClass
                }

            StackedBarChart(
                title = "📍 $expectedClass",
                totalLabel = "Seed-distance bucket distribution for this expected class.",
                slices =
                    bucketOrder.map { bucket ->
                        ChartSlice(
                            label = bucket,
                            value =
                                classRuns.count {
                                    normalizeSeedBucket(it.seedDistanceBucket.toString()) == bucket
                                },
                            color = seedBucketColor(bucket)
                        )
                    }
            )
        }
    }
}

@Composable
private fun SeedDifficultyScatterChart(
    title: String,
    subtitle: String,
    points: List<SeedDifficultyPoint>,
    xLabel: String,
    yLabel: String
) {
    val chartPoints =
        points
            .filter {
                it.x.isFinite() &&
                        it.y.isFinite() &&
                        it.x >= 0.0 &&
                        it.y >= 0.0
            }
            .map { point ->
                ChartPoint(
                    x = point.x,
                    y = point.y,
                    color = point.color
                )
            }

    ProfessionalScatterChart(
        title = title,
        subtitle = subtitle,
        points = chartPoints,
        xAxisLabel = xLabel,
        yAxisLabel = yLabel
    )

    ChartSectionCard(
        title = "📌 Seed Difficulty Axis",
        subtitle = "Numeric mapping used by the seed-distance scatter chart."
    ) {
        ChartMetricRow(
            label = "X labels",
            value = "0=Easy, 1=Medium, 2=Hard, 3=Extreme, 4=Unknown"
        )

        ChartMetricRow(
            label = "Green",
            value = "Accepted"
        )

        ChartMetricRow(
            label = "Red",
            value = "Rejected"
        )
    }
}

private data class SeedDifficultyPoint(
    val x: Double,
    val y: Double,
    val color: Color
)

private fun normalizeSeedBucket(
    bucket: String
): String {
    return when {
        bucket.contains("EASY", ignoreCase = true) ->
            "EASY"

        bucket.contains("MEDIUM", ignoreCase = true) ->
            "MEDIUM"

        bucket.contains("HARD", ignoreCase = true) ->
            "HARD"

        bucket.contains("EXTREME", ignoreCase = true) ->
            "EXTREME"

        else ->
            "UNKNOWN"
    }
}

private fun seedBucketNumeric(
    bucket: String
): Double {
    return when (normalizeSeedBucket(bucket)) {
        "EASY" ->
            0.0

        "MEDIUM" ->
            1.0

        "HARD" ->
            2.0

        "EXTREME" ->
            3.0

        else ->
            4.0
    }
}

private fun seedBucketColor(
    bucket: String
): Color {
    return when (normalizeSeedBucket(bucket)) {
        "EASY" ->
            Color(0xFF2E7D32)

        "MEDIUM" ->
            Color(0xFF1565C0)

        "HARD" ->
            Color(0xFFF9A825)

        "EXTREME" ->
            Color(0xFFC62828)

        else ->
            Color(0xFF546E7A)
    }
}

private fun acceptanceColor(
    ratio: Double
): Color {
    return when {
        ratio >= 0.90 ->
            Color(0xFF2E7D32)

        ratio >= 0.50 ->
            Color(0xFFF9A825)

        else ->
            Color(0xFFC62828)
    }
}

private fun errorColor(
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

private fun List<Double>.averageSafe(): Double {
    val cleanValues =
        filter {
            it.isFinite()
        }

    return if (cleanValues.isEmpty()) {
        0.0
    } else {
        cleanValues.average()
    }
}
