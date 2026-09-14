package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.ui.charts.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSlice
import com.robotkinematicslab.mobile.ui.charts.basic.EmptyChartDataCard
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.StackedBarChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection

@Composable
fun ProgressClassChartsPage(
    report: Layer1DiagnosticReport,
    sequentialRuns: List<DiagnosticRunResult>
) {
    if (sequentialRuns.isEmpty()) {
        EmptyChartDataCard(
            title = "📈 Progress Classes",
            subtitle = "No sequential run data is available for progress-class charts."
        )

        return
    }

    val progressOrder =
        listOf(
            "SOLVED",
            "NEAR_SOLVED",
            "CLOSE_MISS",
            "IMPROVED_BUT_NOT_ENOUGH",
            "STALLED",
            "WORSENED",
            "INVALID_NUMERICAL",
            "UNKNOWN"
        )

    val runsByProgress =
        sequentialRuns.groupBy {
            normalizeProgressClass(it.progressClass.toString())
        }

    HorizontalBarChart(
        title = "📈 Progress Class Distribution",
        items =
            progressOrder.map { progressClass ->
                ChartBarItem(
                    label = progressClass,
                    value = runsByProgress[progressClass]?.size ?: 0,
                    color = progressClassColor(progressClass)
                )
            },
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )

    ProgressClassByExpectedClassChart(
        runs = sequentialRuns,
        progressOrder = progressOrder
    )

    ProgressClassByLinkCountChart(
        runs = sequentialRuns,
        progressOrder = progressOrder
    )

    ProgressClassByTopologyChart(
        runs = sequentialRuns,
        progressOrder = progressOrder
    )

    ProgressClassBySeedBucketChart(
        runs = sequentialRuns,
        progressOrder = progressOrder
    )

    HorizontalDoubleBarChart(
        title = "📈 Improvement Ratio by Progress Class",
        subtitle = "Checks whether progress labels match the numeric improvement behavior.",
        items =
            progressOrder.map { progressClass ->
                val averageImprovementRatio =
                    runsByProgress[progressClass]
                        .orEmpty()
                        .map {
                            it.improvementRatio.coerceIn(0.0, 1.0)
                        }
                        .averageSafe()

                ChartDoubleBarItem(
                    label = progressClass,
                    value = averageImprovementRatio,
                    displayValue = formatChartPercent(averageImprovementRatio),
                    color = progressClassColor(progressClass)
                )
            }
    )

    ChartSectionCard(
        title = "📈 Progress-Class Safety Summary",
        subtitle = "Useful failures should reduce error. Bad failures stall, worsen, or go numerically invalid."
    ) {
        ChartMetricRow(
            label = "Solved",
            value = countProgress(runsByProgress, "SOLVED").toString()
        )

        ChartMetricRow(
            label = "Near solved",
            value = countProgress(runsByProgress, "NEAR_SOLVED").toString()
        )

        ChartMetricRow(
            label = "Close miss",
            value = countProgress(runsByProgress, "CLOSE_MISS").toString()
        )

        ChartMetricRow(
            label = "Improved but not enough",
            value = countProgress(runsByProgress, "IMPROVED_BUT_NOT_ENOUGH").toString()
        )

        ChartMetricRow(
            label = "Stalled",
            value = countProgress(runsByProgress, "STALLED").toString()
        )

        ChartMetricRow(
            label = "Worsened",
            value = countProgress(runsByProgress, "WORSENED").toString()
        )

        ChartMetricRow(
            label = "Invalid numerical",
            value = countProgress(runsByProgress, "INVALID_NUMERICAL").toString()
        )
    }
}

@Composable
private fun ProgressClassByExpectedClassChart(
    runs: List<DiagnosticRunResult>,
    progressOrder: List<String>
) {
    val expectedClasses =
        runs
            .map {
                it.expectedClass.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "📈 Progress Class by Expected Class",
        subtitle = "Reachable vs unreachable behavior."
    ) {
        if (expectedClasses.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No data"
            )

            return@ChartSectionCard
        }

        expectedClasses.forEach { expectedClass ->
            val classRuns =
                runs.filter {
                    it.expectedClass.toString() == expectedClass
                }

            StackedBarChart(
                title = "📈 $expectedClass",
                totalLabel = "Progress-class distribution for this expected class.",
                slices =
                    progressOrder.map { progressClass ->
                        ChartSlice(
                            label = progressClass,
                            value =
                                classRuns.count {
                                    normalizeProgressClass(it.progressClass.toString()) == progressClass
                                },
                            color = progressClassColor(progressClass)
                        )
                    }
            )
        }
    }
}

@Composable
private fun ProgressClassByLinkCountChart(
    runs: List<DiagnosticRunResult>,
    progressOrder: List<String>
) {
    val linkCounts =
        runs
            .map {
                it.linkCount
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "📈 Progress Class by Link Count",
        subtitle = "Shows how progress quality changes as robot complexity increases."
    ) {
        if (linkCounts.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No link-count data"
            )

            return@ChartSectionCard
        }

        linkCounts.forEach { linkCount ->
            val linkRuns =
                runs.filter {
                    it.linkCount == linkCount
                }

            StackedBarChart(
                title = "📈 $linkCount Links",
                totalLabel = "Progress-class distribution for this link count.",
                slices =
                    progressOrder.map { progressClass ->
                        ChartSlice(
                            label = progressClass,
                            value =
                                linkRuns.count {
                                    normalizeProgressClass(it.progressClass.toString()) == progressClass
                                },
                            color = progressClassColor(progressClass)
                        )
                    }
            )
        }
    }
}

@Composable
private fun ProgressClassByTopologyChart(
    runs: List<DiagnosticRunResult>,
    progressOrder: List<String>
) {
    val topologyModes =
        runs
            .map {
                it.jointMode.toString()
            }
            .distinct()
            .sorted()

    ChartSectionCard(
        title = "📈 Progress Class by Topology",
        subtitle = "Shows which topology modes fail usefully or uselessly."
    ) {
        if (topologyModes.isEmpty()) {
            ChartMetricRow(
                label = "Status",
                value = "No topology data"
            )

            return@ChartSectionCard
        }

        topologyModes.forEach { topologyMode ->
            val topologyRuns =
                runs.filter {
                    it.jointMode.toString() == topologyMode
                }

            StackedBarChart(
                title = "📈 $topologyMode",
                totalLabel = "Progress-class distribution for this topology mode.",
                slices =
                    progressOrder.map { progressClass ->
                        ChartSlice(
                            label = progressClass,
                            value =
                                topologyRuns.count {
                                    normalizeProgressClass(it.progressClass.toString()) == progressClass
                                },
                            color = progressClassColor(progressClass)
                        )
                    }
            )
        }
    }
}

@Composable
private fun ProgressClassBySeedBucketChart(
    runs: List<DiagnosticRunResult>,
    progressOrder: List<String>
) {
    val bucketOrder =
        listOf(
            "EASY",
            "MEDIUM",
            "HARD",
            "EXTREME",
            "UNKNOWN"
        )

    ChartSectionCard(
        title = "📈 Progress Class by Seed Bucket",
        subtitle = "Shows whether hard initial seeds still improve or mostly fail badly."
    ) {
        bucketOrder.forEach { bucket ->
            val bucketRuns =
                runs.filter {
                    normalizeSeedBucketForProgress(it.seedDistanceBucket.toString()) == bucket
                }

            StackedBarChart(
                title = "📈 $bucket Seeds",
                totalLabel = "Progress-class distribution for this seed-distance bucket.",
                slices =
                    progressOrder.map { progressClass ->
                        ChartSlice(
                            label = progressClass,
                            value =
                                bucketRuns.count {
                                    normalizeProgressClass(it.progressClass.toString()) == progressClass
                                },
                            color = progressClassColor(progressClass)
                        )
                    }
            )
        }
    }
}

private fun normalizeProgressClass(
    progressClass: String
): String {
    return when {
        progressClass.contains("SOLVED", ignoreCase = true) &&
                progressClass.contains("NEAR", ignoreCase = true) ->
            "NEAR_SOLVED"

        progressClass.contains("CLOSE", ignoreCase = true) ->
            "CLOSE_MISS"

        progressClass == "SOLVED" ||
                progressClass.endsWith(".SOLVED") ||
                progressClass.contains("SOLVED", ignoreCase = true) ->
            "SOLVED"

        progressClass.contains("IMPROVED", ignoreCase = true) ->
            "IMPROVED_BUT_NOT_ENOUGH"

        progressClass.contains("STALLED", ignoreCase = true) ->
            "STALLED"

        progressClass.contains("WORSENED", ignoreCase = true) ->
            "WORSENED"

        progressClass.contains("INVALID", ignoreCase = true) ||
                progressClass.contains("NUMERICAL", ignoreCase = true) ->
            "INVALID_NUMERICAL"

        else ->
            "UNKNOWN"
    }
}

private fun normalizeSeedBucketForProgress(
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

private fun progressClassColor(
    progressClass: String
): Color {
    return when (normalizeProgressClass(progressClass)) {
        "SOLVED" ->
            Color(0xFF2E7D32)

        "NEAR_SOLVED" ->
            Color(0xFF8BC34A)

        "CLOSE_MISS" ->
            Color(0xFFF9A825)

        "IMPROVED_BUT_NOT_ENOUGH" ->
            Color(0xFFEF6C00)

        "STALLED" ->
            Color(0xFF6D4C41)

        "WORSENED" ->
            Color(0xFFC62828)

        "INVALID_NUMERICAL" ->
            Color(0xFFAD1457)

        else ->
            Color(0xFF546E7A)
    }
}

private fun countProgress(
    runsByProgress: Map<String, List<DiagnosticRunResult>>,
    progressClass: String
): Int {
    return runsByProgress[progressClass]?.size ?: 0
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
