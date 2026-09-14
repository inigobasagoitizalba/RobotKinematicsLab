package com.robotkinematicslab.mobile.diagnostics.benchmark.export

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

class DiagnosticRunHistoryCsvWriter(
    private val filePath: String
) {
    private var writer: BufferedWriter? = null
    private var opened = false

    fun open() {
        if (opened) {
            return
        }

        val file =
            File(filePath)

        file.parentFile?.mkdirs()

        writer =
            BufferedWriter(
                FileWriter(
                    file,
                    false
                )
            )

        opened = true

        writeHeader()
    }

    fun writeRun(
        run: DiagnosticRunResult
    ) {
        writeRunValues(
            seed = run.seed,
            linkCount = run.linkCount,
            jointMode = run.jointMode,
            runIndex = run.runIndex,
            runKind = run.runKind,
            selectedCaseId = run.selectedCaseId,
            transitionFromCaseId = run.transitionFromCaseId,
            transitionToCaseId = run.transitionToCaseId,
            expectedClass = run.expectedClass,
            solverAccepted = run.solverAccepted,
            status = run.status,
            detailCode = run.detailCode,
            finalError = run.finalError,
            iterations = run.iterations,
            target = run.target,
            sourceJointState = run.sourceJointState,
            seedJointState = run.seedJointState,
            solutionJointValues = run.solutionJointValues,
            initialError = run.initialError,
            improvement = run.improvement,
            improvementRatio = run.improvementRatio,
            progressClass = run.progressClass,
            seedDistanceBucket = run.seedDistanceBucket,
            seedMinNormalizedLimitMargin = run.seedMinNormalizedLimitMargin,
            seedLogConditionNumber = run.seedLogConditionNumber,
            iterationSaturationRatio = run.iterationSaturationRatio,
            jointDeltaNorm = run.jointDeltaNorm,
            maxSingleJointMovement = run.maxSingleJointMovement,
            normalizedJointTravelRms = run.normalizedJointTravelRms,
            finalMinNormalizedLimitMargin = run.finalMinNormalizedLimitMargin,
            backtrackingRetryCount = run.backtrackingRetryCount,
            solveDurationNanos = run.solveDurationNanos,
            nearLimitJointCount = run.nearLimitJointCount,
            nearLimitJointNames = run.nearLimitJointNames,
            jointLimitPressureRatio = run.jointLimitPressureRatio,
            note = run.note
        )
    }

    fun writeRunValues(
        seed: Int,
        linkCount: Int,
        jointMode: DiagnosticJointMode,
        runIndex: Int,
        runKind: DiagnosticRunKind,
        selectedCaseId: String,
        transitionFromCaseId: String?,
        transitionToCaseId: String?,
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
        val activeWriter =
            writer ?: return

        activeWriter.appendCsvRow(
            listOf(
                seed.toString(),
                ScientificRandomProtocol.ID,
                linkCount.toString(),
                jointMode.name,
                runIndex.toString(),
                runKind.name,
                selectedCaseId,
                transitionFromCaseId ?: "",
                transitionToCaseId ?: "",
                expectedClass.name,
                solverAccepted.toString(),
                status,
                detailCode,
                formatDouble(finalError),
                iterations.toString(),
                formatDouble(target.x),
                formatDouble(target.y),
                formatDouble(target.z),
                sourceJointState?.jointValues?.joinToString(
                    separator = ";"
                ) {
                    formatDouble(it)
                } ?: "",
                seedJointState.jointValues.joinToString(
                    separator = ";"
                ) {
                    formatDouble(it)
                },
                solutionJointValues.joinToString(
                    separator = ";"
                ) {
                    formatDouble(it)
                },
                formatDouble(initialError),
                formatDouble(improvement),
                formatDouble(improvementRatio),
                progressClass.name,
                seedDistanceBucket.name,
                formatDouble(seedMinNormalizedLimitMargin),
                formatDouble(seedLogConditionNumber),
                formatDouble(iterationSaturationRatio),
                formatDouble(jointDeltaNorm),
                formatDouble(maxSingleJointMovement),
                formatDouble(normalizedJointTravelRms),
                formatDouble(finalMinNormalizedLimitMargin),
                backtrackingRetryCount.toString(),
                solveDurationNanos.toString(),
                nearLimitJointCount.toString(),
                nearLimitJointNames.joinToString(
                    separator = ";"
                ),
                formatDouble(jointLimitPressureRatio),
                note
            )
        )
    }

    fun flush() {
        writer?.flush()
    }

    fun close() {
        writer?.flush()
        writer?.close()
        writer = null
        opened = false
    }

    private fun writeHeader() {
        writer?.appendCsvRow(
            listOf(
                "seed",
                "randomProtocol",
                "linkCount",
                "jointMode",
                "runIndex",
                "runKind",
                "selectedCaseId",
                "transitionFromCaseId",
                "transitionToCaseId",
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

    private fun BufferedWriter.appendCsvRow(
        values: List<String>
    ) {
        var index =
            0

        while (index < values.size) {
            if (index > 0) {
                append(',')
            }

            append(
                escapeCsv(
                    value = values[index]
                )
            )

            index++
        }

        newLine()
    }

    private fun escapeCsv(
        value: String
    ): String {
        val mustQuote =
            value.contains(",") ||
                    value.contains("\"") ||
                    value.contains("\n") ||
                    value.contains("\r")

        if (!mustQuote) {
            return value
        }

        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun formatDouble(
        value: Double
    ): String {
        return if (value.isFinite()) {
            String.format(
                Locale.US,
                "%.10f",
                value
            )
        } else {
            "NA"
        }
    }
}
