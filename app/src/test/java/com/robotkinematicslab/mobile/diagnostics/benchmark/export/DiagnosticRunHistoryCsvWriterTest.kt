package com.robotkinematicslab.mobile.diagnostics.benchmark.export

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiagnosticRunHistoryCsvWriterTest {

    @Test
    fun writeRunValues_exportsAllSixNewMetricsInMatchingColumns() {
        val outputFile =
            File.createTempFile(
                "diagnostic-run-history-",
                ".csv"
            )

        try {
            val writer =
                DiagnosticRunHistoryCsvWriter(
                    filePath = outputFile.absolutePath
                )

            writer.open()
            writer.writeRunValues(
                seed = 42,
                linkCount = 3,
                jointMode = DiagnosticJointMode.MIXED,
                runIndex = 1,
                runKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
                selectedCaseId = "case-1",
                transitionFromCaseId = null,
                transitionToCaseId = "case-1",
                expectedClass = DiagnosticExpectedClass.REACHABLE,
                solverAccepted = true,
                status = "SUCCESS",
                detailCode = "NONE",
                finalError = 0.001,
                iterations = 7,
                target = Vec3(0.1, 0.2, 0.3),
                sourceJointState = null,
                seedJointState = RobotState(listOf(0.0)),
                solutionJointValues = listOf(0.1),
                initialError = 0.2,
                improvement = 0.199,
                improvementRatio = 0.995,
                progressClass = DiagnosticProgressClass.SOLVED,
                seedDistanceBucket = DiagnosticSeedDistanceBucket.MEDIUM,
                seedMinNormalizedLimitMargin = 0.40,
                seedLogConditionNumber = 2.50,
                iterationSaturationRatio = 0.07,
                jointDeltaNorm = 0.10,
                maxSingleJointMovement = 0.10,
                normalizedJointTravelRms = 0.05,
                finalMinNormalizedLimitMargin = 0.35,
                backtrackingRetryCount = 3,
                solveDurationNanos = 125_000L,
                nearLimitJointCount = 0,
                nearLimitJointNames = emptyList(),
                jointLimitPressureRatio = 0.0,
                note = "test"
            )
            writer.close()

            val lines = outputFile.readLines()
            val header = lines[0].split(',')
            val row = lines[1].split(',')

            assertEquals(header.size, row.size)
            assertEquals("42", row[header.indexOf("seed")])
            assertEquals("3", row[header.indexOf("linkCount")])
            assertEquals("MIXED", row[header.indexOf("jointMode")])
            assertTrue(row[header.indexOf("randomProtocol")].isNotBlank())
            assertEquals("0.4000000000", row[header.indexOf("seedMinNormalizedLimitMargin")])
            assertEquals("2.5000000000", row[header.indexOf("seedLogConditionNumber")])
            assertEquals("0.0500000000", row[header.indexOf("normalizedJointTravelRms")])
            assertEquals("0.3500000000", row[header.indexOf("finalMinNormalizedLimitMargin")])
            assertEquals("3", row[header.indexOf("backtrackingRetryCount")])
            assertEquals("125000", row[header.indexOf("solveDurationNanos")])
            assertTrue(header.size >= 39)
        } finally {
            outputFile.delete()
        }
    }
}
