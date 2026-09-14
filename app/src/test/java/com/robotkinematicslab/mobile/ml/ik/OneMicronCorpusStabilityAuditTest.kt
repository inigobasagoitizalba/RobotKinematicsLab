package com.robotkinematicslab.mobile.ml.ik

import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in full-corpus certification used by the mathematical stability audit. */
class OneMicronCorpusStabilityAuditTest {

    @Test
    fun certifyEveryRequestedCorpusRowWithFreshForwardKinematics() {
        val path = System.getenv("RKL_MATH_STABILITY_CORPUS")
        assumeTrue(!path.isNullOrBlank())
        val expectedRows = System.getenv("RKL_MATH_STABILITY_ROWS")?.toIntOrNull() ?: 100_000
        val started = System.nanoTime()
        val corpusFile = resolveCorpusFile(requireNotNull(path))
        val dataset = OneMicronIkDatasetReader().load(
            file = corpusFile,
            profile = OneMicronIkFeatureProfile.KINEMATICS_108,
            maximumRows = expectedRows
        )

        assertEquals(expectedRows, dataset.rowsRead)
        assertEquals(expectedRows, dataset.samples.size)
        assertEquals(0, dataset.skippedRows)
        assertEquals(0, dataset.rejectedByMicronContract)
        assertTrue(dataset.robots.size >= 3)
        assertTrue(dataset.samples.all { it.certifiedCartesianErrorMeters in 0.0..ONE_MICRON_METERS })

        val maximumResidual = dataset.samples.maxOf { it.certifiedCartesianErrorMeters }
        val maximumAgreementDelta = dataset.samples.maxOf {
            abs(it.recordedCartesianErrorMeters - it.certifiedCartesianErrorMeters)
        }
        val naiveCumulativeResidual = dataset.samples.sumOf { it.certifiedCartesianErrorMeters }
        val compensatedCumulativeResidual = compensatedSum(
            dataset.samples.map { it.certifiedCartesianErrorMeters }
        )
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue(maximumAgreementDelta <= 5e-11)
        assertTrue(compensatedCumulativeResidual.isFinite())
        println(
            "MATH_STABILITY_CORPUS|rows=${dataset.samples.size}|robots=${dataset.robots.size}|" +
                "max_residual_m=$maximumResidual|max_recorded_delta_m=$maximumAgreementDelta|" +
                "naive_cumulative_m=$naiveCumulativeResidual|compensated_cumulative_m=$compensatedCumulativeResidual|" +
                "sum_delta_m=${abs(naiveCumulativeResidual - compensatedCumulativeResidual)}|elapsed_ms=$elapsedMillis"
        )
    }

    private fun resolveCorpusFile(path: String): File {
        val requested = File(path)
        if (requested.isAbsolute || requested.exists()) return requested

        // Gradle executes this module's tests with app/ as the working directory,
        // while audit commands are normally issued from the repository root.
        val repositoryRelative = File("..", path).canonicalFile
        return if (repositoryRelative.exists()) repositoryRelative else requested
    }

    private fun compensatedSum(values: List<Double>): Double {
        var sum = 0.0
        var correction = 0.0
        values.forEach { value ->
            val next = sum + value
            correction += if (abs(sum) >= abs(value)) (sum - next) + value else (value - next) + sum
            sum = next
        }
        return sum + correction
    }
}
