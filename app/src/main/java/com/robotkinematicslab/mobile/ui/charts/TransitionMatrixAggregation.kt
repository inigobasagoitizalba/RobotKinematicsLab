package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTransitionAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerCaseAggregate
import java.util.Locale

/**
 * Removes benchmark scenario prefixes while preserving the scientific target role.
 * Example: S101-10L-MIXED-R3 becomes R3. The source report is never modified.
 */
internal fun compactDiagnosticCaseId(caseId: String?): String {
    if (caseId.isNullOrBlank() || caseId.equals("START", ignoreCase = true)) {
        return "START"
    }

    val normalized = caseId.trim()
    val role = PREFIXED_CASE_ROLE.find(normalized)?.groupValues?.getOrNull(1)
    return role?.uppercase(Locale.US) ?: normalized
}

internal fun compactTransitionAggregates(
    transitions: List<DiagnosticTransitionAggregate>
): List<DiagnosticTransitionAggregate> {
    return transitions
        .groupBy { transition ->
            compactDiagnosticCaseId(transition.fromCaseId) to
                    compactDiagnosticCaseId(transition.toCaseId)
        }
        .map { (pair, group) ->
            val totalRuns = group.sumOf { it.runCount }

            DiagnosticTransitionAggregate(
                fromCaseId = pair.first,
                toCaseId = pair.second,
                runCount = totalRuns,
                acceptedCount = group.sumOf { it.acceptedCount },
                rejectedCount = group.sumOf { it.rejectedCount },
                nearSolvedCount = group.sumOf { it.nearSolvedCount },
                closeMissCount = group.sumOf { it.closeMissCount },
                farFailureCount = group.sumOf { it.farFailureCount },
                averageFinalError = group.weightedAverageByRunCount { it.averageFinalError },
                maxFinalError =
                    group.mapNotNull { it.maxFinalError.takeIf(Double::isFinite) }
                        .maxOrNull()
                        ?: Double.NaN,
                averageInitialError = group.weightedAverageByRunCount { it.averageInitialError },
                averageImprovementRatio =
                    group.weightedAverageByRunCount { it.averageImprovementRatio },
                averageIterations = group.weightedAverageByRunCount { it.averageIterations },
                mostCommonStatus = group.maxByOrNull { it.runCount }?.mostCommonStatus ?: "UNKNOWN"
            )
        }
        .sortedWith(
            compareBy<DiagnosticTransitionAggregate> {
                diagnosticCaseSortKey(it.fromCaseId)
            }.thenBy {
                diagnosticCaseSortKey(it.toCaseId)
            }
        )
}

internal fun compactTransitionCaseLabels(
    transitions: List<DiagnosticTransitionAggregate>
): List<String> {
    return transitions
        .flatMap { listOf(it.fromCaseId, it.toCaseId) }
        .distinct()
        .sortedBy(::diagnosticCaseSortKey)
}

internal data class DiagnosticCaseMatrixAggregate(
    val caseId: String,
    val runCount: Int,
    val acceptedCount: Int,
    val averageError: Double,
    val maxError: Double,
    val averageIterations: Double,
    val averageJointPressure: Double
)

internal fun compactCaseMatrixAggregates(
    cases: List<DiagnosticPerCaseAggregate>
): List<DiagnosticCaseMatrixAggregate> {
    return cases
        .groupBy { compactDiagnosticCaseId(it.caseId) }
        .map { (caseId, group) ->
            DiagnosticCaseMatrixAggregate(
                caseId = caseId,
                runCount = group.sumOf { it.sequentialRunCount },
                acceptedCount = group.sumOf { it.sequentialAcceptedCount },
                averageError = group.weightedCaseAverage { it.averageSequentialError },
                maxError =
                    group.mapNotNull { it.maxSequentialError.takeIf(Double::isFinite) }
                        .maxOrNull()
                        ?: Double.NaN,
                averageIterations = group.weightedCaseAverage { it.averageSequentialIterations },
                averageJointPressure =
                    group.weightedCaseAverage { it.averageJointLimitPressureRatio }
            )
        }
        .sortedBy { diagnosticCaseSortKey(it.caseId) }
}

private fun List<DiagnosticTransitionAggregate>.weightedAverageByRunCount(
    selector: (DiagnosticTransitionAggregate) -> Double
): Double {
    var weightedSum = 0.0
    var totalWeight = 0L

    forEach { transition ->
        val value = selector(transition)
        if (value.isFinite() && transition.runCount > 0) {
            weightedSum += value * transition.runCount.toDouble()
            totalWeight += transition.runCount.toLong()
        }
    }

    return if (totalWeight > 0L) weightedSum / totalWeight.toDouble() else Double.NaN
}

private fun List<DiagnosticPerCaseAggregate>.weightedCaseAverage(
    selector: (DiagnosticPerCaseAggregate) -> Double
): Double {
    var weightedSum = 0.0
    var totalWeight = 0L

    forEach { aggregate ->
        val value = selector(aggregate)
        if (value.isFinite() && aggregate.sequentialRunCount > 0) {
            weightedSum += value * aggregate.sequentialRunCount.toDouble()
            totalWeight += aggregate.sequentialRunCount.toLong()
        }
    }

    return if (totalWeight > 0L) weightedSum / totalWeight.toDouble() else Double.NaN
}

private fun diagnosticCaseSortKey(caseId: String): String {
    if (caseId == "START") return "0-000000"

    val role = SIMPLE_CASE_ROLE.matchEntire(caseId)
    if (role != null) {
        val family = if (role.groupValues[1].equals("R", true)) "1" else "2"
        val index = role.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
        return "$family-${index.toString().padStart(6, '0')}"
    }

    return "3-${caseId.lowercase(Locale.US)}"
}

private val PREFIXED_CASE_ROLE = Regex("(?:^|-)([RU]\\d+)$", RegexOption.IGNORE_CASE)
private val SIMPLE_CASE_ROLE = Regex("([RU])(\\d+)", RegexOption.IGNORE_CASE)
