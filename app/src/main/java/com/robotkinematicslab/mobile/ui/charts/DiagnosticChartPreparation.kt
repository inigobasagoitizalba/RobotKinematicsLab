package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult

data class DiagnosticChartRunPartition(
    val sequentialRuns: List<DiagnosticRunResult>,
    val oracleRuns: List<DiagnosticRunResult>
)

data class DiagnosticChartPreparedData(
    val sequentialRuns: List<DiagnosticRunResult>,
    val oracleRuns: List<DiagnosticRunResult>,
    val displayRuns: List<DiagnosticRunResult>,
    val finiteMetricCount: Long,
    val nonFiniteMetricCount: Long,
    val categoryGroupCount: Int,
    val heatMapIndex: DiagnosticHeatMapIndex? = null
)

internal fun <T> deterministicDisplaySample(
    values: List<T>,
    maximumSize: Int = MAX_DISPLAY_RUNS
): List<T> {
    require(maximumSize > 0) { "maximumSize must be positive." }
    return values
}

data class DiagnosticHeatMapCount(
    val total: Int,
    val accepted: Int
)

data class DiagnosticHeatMapIndex(
    val seeds: List<Int>,
    val linkCounts: List<Int>,
    val topologyModes: List<String>,
    val failureCodes: List<String>,
    val statuses: List<String>,
    val expectedClasses: List<String>,
    val jointNames: List<String>,
    val caseIds: List<String>,
    val transitions: List<String>,
    val seedLink: Map<Pair<Int, Int>, DiagnosticHeatMapCount>,
    val topologyLink: Map<Pair<String, Int>, DiagnosticHeatMapCount>,
    val seedTopology: Map<Pair<Int, String>, DiagnosticHeatMapCount>,
    val failureLink: Map<Pair<String, Int>, Int>,
    val statusExpectedClass: Map<Pair<String, String>, Int>,
    val progressSeedBucket: Map<Pair<String, String>, Int>,
    val jointCase: Map<Pair<String, String>, Int>,
    val jointTransition: Map<Pair<String, String>, Int>
)

internal fun partitionDiagnosticRuns(
    runs: List<DiagnosticRunResult>
): DiagnosticChartRunPartition {
    val sequentialRuns = ArrayList<DiagnosticRunResult>()
    val oracleRuns = ArrayList<DiagnosticRunResult>()

    runs.forEach { run ->
        if (run.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK) {
            sequentialRuns += run
        } else {
            oracleRuns += run
        }
    }

    return DiagnosticChartRunPartition(
        sequentialRuns = sequentialRuns,
        oracleRuns = oracleRuns
    )
}

internal fun sortDiagnosticRuns(
    partition: DiagnosticChartRunPartition
): DiagnosticChartRunPartition {
    return DiagnosticChartRunPartition(
        sequentialRuns = partition.sequentialRuns.sortedBy { it.runIndex },
        oracleRuns = partition.oracleRuns.sortedBy { it.runIndex }
    )
}

internal fun countDiagnosticMetricFiniteness(
    runs: List<DiagnosticRunResult>
): Pair<Long, Long> {
    var finite = 0L
    var nonFinite = 0L

    runs.forEach { run ->
        val values =
            doubleArrayOf(
                run.finalError,
                run.initialError,
                run.improvement,
                run.improvementRatio,
                run.seedMinNormalizedLimitMargin,
                run.seedLogConditionNumber,
                run.iterationSaturationRatio,
                run.jointDeltaNorm,
                run.maxSingleJointMovement,
                run.normalizedJointTravelRms,
                run.finalMinNormalizedLimitMargin,
                run.jointLimitPressureRatio
            )

        values.forEach { value ->
            if (value.isFinite()) {
                finite += 1L
            } else {
                nonFinite += 1L
            }
        }
    }

    return finite to nonFinite
}

internal fun countDiagnosticCategoryGroups(
    category: DiagnosticChartCategory,
    runs: List<DiagnosticRunResult>
): Int {
    if (runs.isEmpty()) {
        return 0
    }

    return when (category) {
        DiagnosticChartCategory.LINK_COUNT_SCALING -> runs.map { it.linkCount }.distinct().size
        DiagnosticChartCategory.SEED_SENSITIVITY -> runs.map { it.seed }.distinct().size
        DiagnosticChartCategory.TOPOLOGY -> runs.map { it.jointMode }.distinct().size
        DiagnosticChartCategory.STATUS_FAILURE_CODES ->
            runs.map { it.status }.distinct().size + runs.map { it.detailCode }.distinct().size
        DiagnosticChartCategory.TRANSITIONS ->
            runs.map { it.transitionFromCaseId to it.transitionToCaseId }.distinct().size
        DiagnosticChartCategory.JOINT_LIMITS -> runs.flatMap { it.nearLimitJointNames }.distinct().size
        DiagnosticChartCategory.SEED_DISTANCE -> runs.map { it.seedDistanceBucket }.distinct().size
        DiagnosticChartCategory.PROGRESS_CLASSES -> runs.map { it.progressClass }.distinct().size
        DiagnosticChartCategory.PER_CASE -> runs.map { it.selectedCaseId }.distinct().size
        DiagnosticChartCategory.HEAT_MAPS ->
            runs.map { Triple(it.seed, it.linkCount, it.jointMode) }.distinct().size
        else -> 1
    }
}

internal fun buildDiagnosticHeatMapIndex(
    runs: List<DiagnosticRunResult>
): DiagnosticHeatMapIndex {
    val seeds = sortedSetOf<Int>()
    val linkCounts = sortedSetOf<Int>()
    val topologyModes = sortedSetOf<String>()
    val failureCodes = sortedSetOf<String>()
    val statuses = sortedSetOf<String>()
    val expectedClasses = sortedSetOf<String>()
    val jointNames = sortedSetOf<String>()
    val caseIds = sortedSetOf<String>()
    val transitions = sortedSetOf<String>()

    val seedLink = mutableMapOf<Pair<Int, Int>, MutableHeatMapCount>()
    val topologyLink = mutableMapOf<Pair<String, Int>, MutableHeatMapCount>()
    val seedTopology = mutableMapOf<Pair<Int, String>, MutableHeatMapCount>()
    val failureLink = mutableMapOf<Pair<String, Int>, Int>()
    val statusExpectedClass = mutableMapOf<Pair<String, String>, Int>()
    val progressSeedBucket = mutableMapOf<Pair<String, String>, Int>()
    val jointCase = mutableMapOf<Pair<String, String>, Int>()
    val jointTransition = mutableMapOf<Pair<String, String>, Int>()

    runs.forEach { run ->
        val topology = run.jointMode.toString()
        val status = run.status.toString()
        val expectedClass = run.expectedClass.toString()
        val progressClass = normalizeProgressClass(run.progressClass.toString())
        val seedBucket = normalizeSeedBucket(run.seedDistanceBucket.toString())
        val compactCaseId = compactDiagnosticCaseId(run.selectedCaseId)
        val transition =
            "${compactDiagnosticCaseId(run.transitionFromCaseId)}→${compactDiagnosticCaseId(run.transitionToCaseId)}"

        seeds += run.seed
        linkCounts += run.linkCount
        topologyModes += topology
        failureCodes += run.detailCode
        statuses += status
        expectedClasses += expectedClass
        caseIds += compactCaseId
        transitions += transition

        seedLink.getOrPut(run.seed to run.linkCount) { MutableHeatMapCount() }
            .add(run.solverAccepted)
        topologyLink.getOrPut(topology to run.linkCount) { MutableHeatMapCount() }
            .add(run.solverAccepted)
        seedTopology.getOrPut(run.seed to topology) { MutableHeatMapCount() }
            .add(run.solverAccepted)
        failureLink.increment(run.detailCode to run.linkCount)
        statusExpectedClass.increment(status to expectedClass)
        progressSeedBucket.increment(progressClass to seedBucket)

        run.nearLimitJointNames.distinct().forEach { jointName ->
            jointNames += jointName
            jointCase.increment(jointName to compactCaseId)
            jointTransition.increment(jointName to transition)
        }
    }

    return DiagnosticHeatMapIndex(
        seeds = seeds.toList(),
        linkCounts = linkCounts.toList(),
        topologyModes = topologyModes.toList(),
        failureCodes = failureCodes.toList(),
        statuses = statuses.toList(),
        expectedClasses = expectedClasses.toList(),
        jointNames = jointNames.toList(),
        caseIds = caseIds.toList(),
        transitions = transitions.toList(),
        seedLink = seedLink.mapValues { it.value.freeze() },
        topologyLink = topologyLink.mapValues { it.value.freeze() },
        seedTopology = seedTopology.mapValues { it.value.freeze() },
        failureLink = failureLink,
        statusExpectedClass = statusExpectedClass,
        progressSeedBucket = progressSeedBucket,
        jointCase = jointCase,
        jointTransition = jointTransition
    )
}

private data class MutableHeatMapCount(
    var total: Int = 0,
    var accepted: Int = 0
) {
    fun add(isAccepted: Boolean) {
        total += 1
        if (isAccepted) accepted += 1
    }

    fun freeze(): DiagnosticHeatMapCount {
        return DiagnosticHeatMapCount(total = total, accepted = accepted)
    }
}

private fun <K> MutableMap<K, Int>.increment(key: K) {
    this[key] = (this[key] ?: 0) + 1
}

private fun normalizeProgressClass(value: String): String {
    return when {
        value.contains("SOLVED", true) && value.contains("NEAR", true) -> "NEAR_SOLVED"
        value.contains("CLOSE", true) -> "CLOSE_MISS"
        value.contains("SOLVED", true) -> "SOLVED"
        value.contains("IMPROVED", true) -> "IMPROVED_BUT_NOT_ENOUGH"
        value.contains("STALLED", true) -> "STALLED"
        value.contains("WORSENED", true) -> "WORSENED"
        value.contains("INVALID", true) || value.contains("NUMERICAL", true) -> "INVALID_NUMERICAL"
        else -> "UNKNOWN"
    }
}

private fun normalizeSeedBucket(value: String): String {
    return when {
        value.contains("EASY", true) -> "EASY"
        value.contains("MEDIUM", true) -> "MEDIUM"
        value.contains("HARD", true) -> "HARD"
        value.contains("EXTREME", true) -> "EXTREME"
        else -> "UNKNOWN"
    }
}

private const val MAX_DISPLAY_RUNS = 4_000
