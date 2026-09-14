package com.robotkinematicslab.mobile.diagnostics.benchmark.export

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticCaseResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

class DiagnosticCaseResultCsvWriter(
    filePath: String
) : AutoCloseable {

    private val writer =
        BufferedWriter(
            FileWriter(
                File(filePath),
                false
            )
        )

    init {
        writer.appendCsvRow(
            arrayOf(
                "seed",
                "randomProtocol",
                "linkCount",
                "jointMode",
                "id",
                "expectedClass",
                "solverAccepted",
                "status",
                "detailCode",
                "finalError",
                "iterations",
                "targetX",
                "targetY",
                "targetZ",
                "sourceJointState",
                "seedJointState",
                "solutionJointValues",
                "initialError",
                "improvement",
                "improvementRatio",
                "progressClass",
                "seedDistanceBucket",
                "seedMinNormalizedLimitMargin",
                "seedLogConditionNumber",
                "iterationSaturationRatio",
                "jointDeltaNorm",
                "maxSingleJointMovement",
                "normalizedJointTravelRms",
                "finalMinNormalizedLimitMargin",
                "backtrackingRetryCount",
                "solveDurationNanos",
                "nearLimitJointCount",
                "nearLimitJointNames",
                "jointLimitPressureRatio",
                "note"
            )
        )
    }

    fun writeCaseResult(
        result: DiagnosticCaseResult
    ) {
        writeCaseResultValues(
            seed = result.seed,
            linkCount = result.linkCount,
            jointMode = result.jointMode,
            id = result.id,
            expectedClass = result.expectedClass,
            solverAccepted = result.solverAccepted,
            status = result.status,
            detailCode = result.detailCode,
            finalError = result.finalError,
            iterations = result.iterations,
            target = result.target,
            sourceJointState = result.sourceJointState,
            seedJointState = result.seedJointState,
            solutionJointValues = result.solutionJointValues,
            initialError = result.initialError,
            improvement = result.improvement,
            improvementRatio = result.improvementRatio,
            progressClass = result.progressClass,
            seedDistanceBucket = result.seedDistanceBucket,
            seedMinNormalizedLimitMargin = result.seedMinNormalizedLimitMargin,
            seedLogConditionNumber = result.seedLogConditionNumber,
            iterationSaturationRatio = result.iterationSaturationRatio,
            jointDeltaNorm = result.jointDeltaNorm,
            maxSingleJointMovement = result.maxSingleJointMovement,
            normalizedJointTravelRms = result.normalizedJointTravelRms,
            finalMinNormalizedLimitMargin = result.finalMinNormalizedLimitMargin,
            backtrackingRetryCount = result.backtrackingRetryCount,
            solveDurationNanos = result.solveDurationNanos,
            nearLimitJointCount = result.nearLimitJointCount,
            nearLimitJointNames = result.nearLimitJointNames,
            jointLimitPressureRatio = result.jointLimitPressureRatio,
            note = result.note
        )
    }

    fun writeCaseResultValues(
        seed: Int,
        linkCount: Int,
        jointMode: DiagnosticJointMode,
        id: String,
        expectedClass: DiagnosticExpectedClass,
        solverAccepted: Boolean,
        status: String,
        detailCode: String,
        finalError: Double,
        iterations: Int,
        target: Vec3,
        sourceJointState: RobotState?,
        seedJointState: RobotState,
        solutionJointValues: List<Double>,
        initialError: Double,
        improvement: Double,
        improvementRatio: Double,
        progressClass: DiagnosticProgressClass,
        seedDistanceBucket: DiagnosticSeedDistanceBucket,
        seedMinNormalizedLimitMargin: Double,
        seedLogConditionNumber: Double,
        iterationSaturationRatio: Double,
        jointDeltaNorm: Double,
        maxSingleJointMovement: Double,
        normalizedJointTravelRms: Double,
        finalMinNormalizedLimitMargin: Double,
        backtrackingRetryCount: Int,
        solveDurationNanos: Long,
        nearLimitJointCount: Int,
        nearLimitJointNames: List<String>,
        jointLimitPressureRatio: Double,
        note: String
    ) {
        appendValue(seed.toString())
        appendValue(ScientificRandomProtocol.ID)
        appendValue(linkCount.toString())
        appendValue(jointMode.name)
        appendValue(id)
        appendValue(expectedClass.name)
        appendValue(solverAccepted.toString())
        appendValue(status)
        appendValue(detailCode)
        appendValue(formatDouble(finalError))
        appendValue(iterations.toString())
        appendValue(formatDouble(target.x))
        appendValue(formatDouble(target.y))
        appendValue(formatDouble(target.z))

        appendJointValues(
            values = sourceJointState?.jointValues,
            closeRow = false
        )

        appendJointValues(
            values = seedJointState.jointValues,
            closeRow = false
        )

        appendJointValues(
            values = solutionJointValues,
            closeRow = false
        )

        appendValue(formatDouble(initialError))
        appendValue(formatDouble(improvement))
        appendValue(formatDouble(improvementRatio))
        appendValue(progressClass.name)
        appendValue(seedDistanceBucket.name)
        appendValue(formatDouble(seedMinNormalizedLimitMargin))
        appendValue(formatDouble(seedLogConditionNumber))
        appendValue(formatDouble(iterationSaturationRatio))
        appendValue(formatDouble(jointDeltaNorm))
        appendValue(formatDouble(maxSingleJointMovement))
        appendValue(formatDouble(normalizedJointTravelRms))
        appendValue(formatDouble(finalMinNormalizedLimitMargin))
        appendValue(backtrackingRetryCount.toString())
        appendValue(solveDurationNanos.toString())
        appendValue(nearLimitJointCount.toString())

        appendStringList(
            values = nearLimitJointNames,
            closeRow = false
        )

        appendValue(formatDouble(jointLimitPressureRatio))
        appendValue(note)
        writer.newLine()
    }

    fun flush() {
        writer.flush()
    }

    override fun close() {
        writer.flush()
        writer.close()
    }

    private fun BufferedWriter.appendCsvRow(
        values: Array<String>
    ) {
        var index = 0

        while (index < values.size) {
            if (index > 0) {
                append(',')
            }

            appendEscapedCsvValue(values[index])

            index++
        }

        newLine()
    }

    private fun appendValue(
        value: String
    ) {
        writer.appendEscapedCsvValue(value)
        writer.append(',')
    }

    private fun appendJointValues(
        values: List<Double>?,
        closeRow: Boolean
    ) {
        if (values == null) {
            writer.append(',')
            return
        }

        writer.append('"')
        writer.append('[')

        var index = 0

        while (index < values.size) {
            if (index > 0) {
                writer.append(", ")
            }

            writer.append(formatDouble(values[index]))

            index++
        }

        writer.append(']')
        writer.append('"')

        if (closeRow) {
            writer.newLine()
        } else {
            writer.append(',')
        }
    }

    private fun appendStringList(
        values: List<String>,
        closeRow: Boolean
    ) {
        writer.append('"')
        writer.append('[')

        var index = 0

        while (index < values.size) {
            if (index > 0) {
                writer.append(", ")
            }

            writer.appendEscapedInsideQuotedField(values[index])

            index++
        }

        writer.append(']')
        writer.append('"')

        if (closeRow) {
            writer.newLine()
        } else {
            writer.append(',')
        }
    }

    private fun BufferedWriter.appendEscapedCsvValue(
        value: String
    ) {
        val mustQuote =
            value.contains(",") ||
                    value.contains("\"") ||
                    value.contains("\n") ||
                    value.contains("\r")

        if (!mustQuote) {
            append(value)
            return
        }

        append('"')

        var index = 0

        while (index < value.length) {
            val char =
                value[index]

            if (char == '"') {
                append("\"\"")
            } else {
                append(char)
            }

            index++
        }

        append('"')
    }

    private fun BufferedWriter.appendEscapedInsideQuotedField(
        value: String
    ) {
        var index = 0

        while (index < value.length) {
            val char =
                value[index]

            if (char == '"') {
                append("\"\"")
            } else {
                append(char)
            }

            index++
        }
    }

    private fun formatDouble(
        value: Double
    ): String {
        return if (value.isFinite()) {
            String.format(Locale.US, "%.10f", value)
        } else {
            "NA"
        }
    }
}
