package com.robotkinematicslab.mobile.dataset

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.util.Locale

class ScientificDatasetCsvWriter : Closeable {

    private var writer: BufferedWriter? = null

    fun open(
        file: File,
        append: Boolean
    ) {
        check(writer == null) {
            "Dataset writer is already open."
        }

        file.parentFile?.mkdirs()

        val shouldAppend = append && file.exists() && file.length() > 0L

        if (shouldAppend) {
            val existingHeader = file.bufferedReader().use { it.readLine() }
            require(existingHeader == HEADER.joinToString(",")) {
                "Cannot append: the existing CSV uses a different dataset schema."
            }
        }

        writer = BufferedWriter(FileWriter(file, shouldAppend), BUFFER_SIZE)

        if (!shouldAppend) {
            appendRow(HEADER)
        }
    }

    fun write(row: DatasetRow) {
        val robot = row.robot

        appendRow(
            listOf(
                SCHEMA_VERSION,
                row.globalRowIndex.toString(),
                row.batchId,
                row.baseRandomSeed.toString(),
                row.robotRandomSeed.toString(),
                row.randomProtocol,
                row.robotId,
                robot.name,
                robot.joints.size.toString(),
                robot.joints.joinToString(";") { it.type.name },
                robot.dhParameters.joinToString(";") { formatDouble(it.theta) },
                robot.dhParameters.joinToString(";") { formatDouble(it.d) },
                robot.dhParameters.joinToString(";") { formatDouble(it.a) },
                robot.dhParameters.joinToString(";") { formatDouble(it.alpha) },
                robot.joints.joinToString(";") { formatDouble(it.minValue) },
                robot.joints.joinToString(";") { formatDouble(it.maxValue) },
                robot.joints.joinToString(";") { formatDouble(it.homeValue) },
                row.ikConfig.maxIterations.toString(),
                formatDouble(row.ikConfig.tolerance),
                formatDouble(row.ikConfig.damping),
                formatDouble(row.ikConfig.maxStep),
                formatDouble(row.metricPolicy.numericalEpsilon),
                formatDouble(row.metricPolicy.nearSuccessErrorMeters),
                formatDouble(row.metricPolicy.closeMissErrorMeters),
                formatDouble(row.metricPolicy.stalledImprovementRatioEpsilon),
                formatDouble(row.metricPolicy.jointLimitMarginRatio),
                formatDouble(row.metricPolicy.easySeedDistanceUpperMeters),
                formatDouble(row.metricPolicy.mediumSeedDistanceUpperMeters),
                formatDouble(row.metricPolicy.hardSeedDistanceUpperMeters),
                formatDouble(row.metricPolicy.logConditionNumberCap),
                row.sampleIndex.toString(),
                row.targetClass,
                row.targetSamplingStrategy,
                formatList(row.targetSourceJointValues),
                formatList(row.seedJointValues),
                formatDouble(row.targetX),
                formatDouble(row.targetY),
                formatDouble(row.targetZ),
                formatList(row.solutionJointValues),
                row.solverAccepted.toString(),
                row.acceptanceClass,
                row.status,
                row.detailCode,
                row.converged.toString(),
                formatDouble(row.finalError),
                row.iterations.toString(),
                formatDouble(row.initialError),
                formatDouble(row.improvement),
                formatDouble(row.improvementRatio),
                row.progressClass,
                row.seedDistanceBucket,
                formatDouble(row.iterationSaturationRatio),
                formatDouble(row.jointDeltaNorm),
                formatDouble(row.maxSingleJointMovement),
                formatDouble(row.seedMinNormalizedLimitMargin),
                formatDouble(row.seedConditionNumber),
                formatDouble(row.seedLogConditionNumber),
                formatDouble(row.normalizedJointTravelRms),
                formatDouble(row.finalMinNormalizedLimitMargin),
                row.backtrackingRetryCount.toString(),
                row.solveDurationNanos.toString(),
                row.nearLimitJointCount.toString(),
                row.nearLimitJointNames.joinToString(";"),
                formatDouble(row.jointLimitPressureRatio)
            )
        )
    }

    fun flush() {
        writer?.flush()
    }

    override fun close() {
        writer?.flush()
        writer?.close()
        writer = null
    }

    private fun appendRow(values: List<String>) {
        val activeWriter = writer ?: error("Dataset writer is not open.")

        values.forEachIndexed { index, value ->
            if (index > 0) {
                activeWriter.append(',')
            }

            activeWriter.append(escapeCsv(value))
        }

        activeWriter.newLine()
    }

    private fun formatList(values: List<Double>?): String {
        return values?.joinToString(";") { formatDouble(it) }.orEmpty()
    }

    private fun escapeCsv(value: String): String {
        if (
            !value.contains(',') &&
            !value.contains('"') &&
            !value.contains('\n') &&
            !value.contains('\r')
        ) {
            return value
        }

        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun formatDouble(value: Double): String {
        return when {
            value.isNaN() -> "NaN"
            value == Double.POSITIVE_INFINITY -> "Infinity"
            value == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> String.format(Locale.US, "%.12g", value)
        }
    }

    companion object {
        const val SCHEMA_VERSION = "6"
        private const val BUFFER_SIZE = 64 * 1024

        val HEADER =
            listOf(
                "schemaVersion",
                "globalRowIndex",
                "batchId",
                "baseRandomSeed",
                "robotRandomSeed",
                "randomProtocol",
                "robotId",
                "robotName",
                "jointCount",
                "jointTypes",
                "dhThetaRad",
                "dhDMeters",
                "dhAMeters",
                "dhAlphaRad",
                "jointMinValues",
                "jointMaxValues",
                "jointHomeValues",
                "ikMaxIterations",
                "ikToleranceMeters",
                "ikDamping",
                "ikMaxStep",
                "metricNumericalEpsilon",
                "metricNearSuccessErrorMeters",
                "metricCloseMissErrorMeters",
                "metricStalledImprovementRatioEpsilon",
                "metricJointLimitMarginRatio",
                "metricEasySeedDistanceUpperMeters",
                "metricMediumSeedDistanceUpperMeters",
                "metricHardSeedDistanceUpperMeters",
                "metricLogConditionNumberCap",
                "sampleIndex",
                "targetClass",
                "targetSamplingStrategy",
                "targetSourceJointValues",
                "seedJointValues",
                "targetX",
                "targetY",
                "targetZ",
                "solutionJointValues",
                "solverAccepted",
                "acceptanceClass",
                "status",
                "detailCode",
                "converged",
                "finalError",
                "iterations",
                "initialError",
                "improvement",
                "improvementRatio",
                "progressClass",
                "seedDistanceBucket",
                "iterationSaturationRatio",
                "jointDeltaNorm",
                "maxSingleJointMovement",
                "seedMinNormalizedLimitMargin",
                "seedConditionNumber",
                "seedLogConditionNumber",
                "normalizedJointTravelRms",
                "finalMinNormalizedLimitMargin",
                "backtrackingRetryCount",
                "solveDurationNanos",
                "nearLimitJointCount",
                "nearLimitJointNames",
                "jointLimitPressureRatio"
            )
    }
}
