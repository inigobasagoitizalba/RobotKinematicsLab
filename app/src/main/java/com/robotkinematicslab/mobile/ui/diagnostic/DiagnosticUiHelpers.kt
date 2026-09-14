package com.robotkinematicslab.mobile.ui.diagnostic

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import java.util.Locale
import kotlin.math.max

const val NEAR_SUCCESS_ERROR_METERS = 0.001
const val CLOSE_ERROR_METERS = 0.01

data class ErrorStats(
    val count: Int,
    val average: Double,
    val min: Double,
    val max: Double
)

fun Layer1DiagnosticReport.sequentialRuns(): List<DiagnosticRunResult> {
    return runResults.filter {
        it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
    }
}

fun DiagnosticRunResult.isNearConverged(): Boolean {
    return !solverAccepted &&
            finalError.isFinite() &&
            finalError <= NEAR_SUCCESS_ERROR_METERS
}

fun DiagnosticRunResult.isCloseMiss(): Boolean {
    return !solverAccepted &&
            finalError.isFinite() &&
            finalError > NEAR_SUCCESS_ERROR_METERS &&
            finalError <= CLOSE_ERROR_METERS
}

fun DiagnosticRunResult.isFarFailure(): Boolean {
    return !solverAccepted &&
            finalError.isFinite() &&
            finalError > CLOSE_ERROR_METERS
}

fun List<DiagnosticRunResult>.finalErrorStats(): ErrorStats {
    val finiteErrors =
        mapNotNull { run ->
            run.finalError.takeIf { it.isFinite() }
        }

    return ErrorStats(
        count = finiteErrors.size,
        average =
            if (finiteErrors.isEmpty()) {
                Double.NaN
            } else {
                finiteErrors.average()
            },
        min = finiteErrors.minOrNull() ?: Double.NaN,
        max = finiteErrors.maxOrNull() ?: Double.NaN
    )
}

fun keepNumericText(value: String): String {
    return value.filter { it.isDigit() }
}

fun keepSignedNumericText(value: String): String {
    if (value.isEmpty()) {
        return value
    }

    val builder = StringBuilder()

    value.forEachIndexed { index, char ->
        when {
            char.isDigit() ->
                builder.append(char)

            char == '-' &&
                    (
                            index == 0 ||
                                    value.getOrNull(index - 1) == ','
                            ) ->
                builder.append(char)

            char == ',' ->
                builder.append(char)
        }
    }

    return builder.toString()
}

fun keepDecimalText(value: String): String {
    if (value.isEmpty()) {
        return value
    }

    val builder = StringBuilder()
    var dotSeen = false

    value.forEachIndexed { index, char ->
        when {
            char.isDigit() ->
                builder.append(char)

            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }

            char == '-' && index == 0 ->
                builder.append(char)
        }
    }

    return builder.toString()
}

fun percentageToInt(value: Double): Int {
    return if (value.isFinite()) {
        (value.coerceIn(0.0, 1.0) * 100.0).toInt()
    } else {
        0
    }
}

fun estimatePlannedRunsFromInputs(
    minLinkCount: Int?,
    maxLinkCount: Int?,
    singleLinkCount: Int?,
    manualRangeMode: Boolean,
    samplesPerLinkCount: Int?,
    seedCount: Int,
    runAllTopologies: Boolean
): Long {
    if (samplesPerLinkCount == null) {
        return 0L
    }

    val linkCountSize =
        if (manualRangeMode) {
            if (
                minLinkCount == null ||
                maxLinkCount == null ||
                maxLinkCount < minLinkCount
            ) {
                0
            } else {
                maxLinkCount - minLinkCount + 1
            }
        } else {
            if (singleLinkCount == null) {
                0
            } else {
                1
            }
        }

    val topologyCount =
        if (runAllTopologies) {
            4
        } else {
            1
        }

    return linkCountSize.toLong() *
            samplesPerLinkCount.toLong() *
            seedCount.coerceAtLeast(1).toLong() *
            topologyCount.toLong()
}

fun estimateRuntimeRisk(
    plannedRuns: Long,
    isExperimental: Boolean,
    isUnlimited: Boolean
): String {
    return when {
        isUnlimited || plannedRuns > 100_000 ->
            "EXTREME"

        isExperimental || plannedRuns > 50_000 ->
            "HIGH"

        plannedRuns > 10_000 ->
            "MEDIUM"

        else ->
            "LOW"
    }
}

fun runtimeRiskScore(
    runtimeRisk: String
): Int {
    return when (runtimeRisk) {
        "LOW" -> 1
        "MEDIUM" -> 2
        "HIGH" -> 3
        "EXTREME" -> 4
        else -> 0
    }
}

fun formatDouble(value: Double): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.6f", value)
    } else {
        "NA"
    }
}

fun formatPercent(value: Double): String {
    return if (value.isFinite()) {
        String.format(Locale.US, "%.2f%%", value * 100.0)
    } else {
        "NA"
    }
}

fun formatIntList(values: List<Int>): String {
    return if (values.isEmpty()) {
        "none"
    } else {
        values.joinToString()
    }
}
