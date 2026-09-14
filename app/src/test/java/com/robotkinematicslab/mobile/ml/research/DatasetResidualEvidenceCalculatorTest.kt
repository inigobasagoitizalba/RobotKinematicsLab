package com.robotkinematicslab.mobile.ml.research

import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetResidualEvidenceCalculatorTest {

    @Test
    fun `nonfinite solver residuals remain explicit instead of contaminating quantiles`() {
        val evidence =
            DatasetResidualEvidenceCalculator.calculate(
                listOf(
                    DatasetResidualObservation(1, "A", 1.0, 0.1, true),
                    DatasetResidualObservation(2, "A", 2.0, null, false),
                    DatasetResidualObservation(3, "B", 3.0, 0.3, true)
                )
            )

        assertEquals(3, evidence.sampleCount)
        assertEquals(2, evidence.finiteResidualCount)
        assertEquals(1.0 / 3.0, evidence.nonFiniteResidualRate, 1e-12)
        assertEquals(0.2, evidence.medianFinalErrorMeters, 1e-12)
        assertTrue(evidence.finiteResidualReductionFraction > 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate residual evidence is rejected`() {
        DatasetResidualEvidenceCalculator.calculate(
            listOf(
                DatasetResidualObservation(1, "A", 1.0, 0.1, true),
                DatasetResidualObservation(1, "B", 1.0, 0.2, true)
            )
        )
    }

    @Test
    fun `bounded CSV audit samples the complete declared corpus deterministically`() {
        val csv = residualCsv(rowCount = 100)

        val first = DatasetResidualCsvReader.read(csv, maximumRows = 10, samplingSeed = 2604, expectedDataRowCount = 100)
        val repeated = DatasetResidualCsvReader.read(csv, maximumRows = 10, samplingSeed = 2604, expectedDataRowCount = 100)

        assertEquals(10, first.sampleCount)
        assertEquals(first.observations.map { it.sampleId }, repeated.observations.map { it.sampleId })
    }

    @Test
    fun `bounded CSV audit rejects a stale manifest row count`() {
        val csv = residualCsv(rowCount = 5)

        assertThrows(IllegalArgumentException::class.java) {
            DatasetResidualCsvReader.read(csv, maximumRows = 3, samplingSeed = 1, expectedDataRowCount = 6)
        }
    }

    @Test
    fun `bounded CSV audit stops cooperatively without publishing partial evidence`() {
        val csv = residualCsv(rowCount = 100)
        var cancellationChecks = 0

        val error = assertThrows(CancellationException::class.java) {
            DatasetResidualCsvReader.read(
                file = csv,
                maximumRows = 10,
                samplingSeed = 2604,
                expectedDataRowCount = 100,
                cancellationRequested = {
                    cancellationChecks += 1
                    cancellationChecks >= 4
                }
            )
        }

        assertEquals("Dataset residual audit was cancelled.", error.message)
    }

    @Test
    fun `cumulative evidence preserves small residuals next to a very large value`() {
        val evidence =
            DatasetResidualEvidenceCalculator.calculate(
                listOf(
                    DatasetResidualObservation(1, "A", 1e16, 1e16, true),
                    DatasetResidualObservation(2, "A", 1.0, 1.0, true),
                    DatasetResidualObservation(3, "A", 1.0, 1.0, true),
                    DatasetResidualObservation(4, "A", 1.0, 1.0, true)
                )
            )

        assertEquals(1.0000000000000004e16, evidence.cumulativeInitialErrorMeters, 0.0)
        assertEquals(1.0000000000000004e16, evidence.cumulativeFiniteFinalErrorMeters, 0.0)
    }

    private fun residualCsv(rowCount: Int) =
        Files.createTempFile("residual-evidence", ".csv").toFile().apply {
            writeText(
                buildString {
                    appendLine("robotId,initialError,finalError,solverAccepted")
                    repeat(rowCount) { index ->
                        appendLine("robot-${index % 3},${index + 1}.0,0.${index % 9 + 1},${index % 2 == 0}")
                    }
                }
            )
            deleteOnExit()
        }
}
