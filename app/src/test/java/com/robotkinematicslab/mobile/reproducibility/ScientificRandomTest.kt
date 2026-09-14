package com.robotkinematicslab.mobile.reproducibility

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificRandomTest {

    @Test
    fun protocolGoldenVector_preventsSilentAlgorithmChanges() {
        val derivedSeed =
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-targets",
                "links=3",
                "topology=AUTO"
            )
        val random = ScientificRandom(derivedSeed)

        assertEquals(7722895386302910077L, derivedSeed)
        assertEquals(
            listOf(
                2065998904274544993L,
                4496111501173895536L,
                -4342124472426923091L,
                3159819444641844760L,
                4102535252379845476L
            ),
            List(5) { random.nextLong() }
        )
    }

    @Test
    fun sameCoordinates_produceSameSeedAndSequence() {
        val firstSeed =
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-targets",
                "links=3",
                "topology=AUTO"
            )
        val secondSeed =
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-targets",
                "links=3",
                "topology=AUTO"
            )

        val first = ScientificRandom(firstSeed)
        val second = ScientificRandom(secondSeed)

        assertEquals(firstSeed, secondSeed)
        repeat(100) {
            assertEquals(first.nextLong(), second.nextLong())
        }
    }

    @Test
    fun changingAnyExperimentalCoordinate_changesTheStream() {
        val reference =
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-targets",
                "links=3",
                "topology=AUTO"
            )

        assertNotEquals(
            reference,
            ScientificRandomProtocol.deriveSeed(
                43,
                "diagnostic-targets",
                "links=3",
                "topology=AUTO"
            )
        )
        assertNotEquals(
            reference,
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-sequence",
                "links=3",
                "topology=AUTO"
            )
        )
        assertNotEquals(
            reference,
            ScientificRandomProtocol.deriveSeed(
                42,
                "diagnostic-targets",
                "links=4",
                "topology=AUTO"
            )
        )
    }

    @Test
    fun generatedValues_respectExclusiveBounds() {
        val random = ScientificRandom(123L)

        repeat(10_000) {
            assertTrue(random.nextInt(7) in 0 until 7)
            assertTrue(random.nextDouble(-2.0, 5.0) in -2.0..<5.0)
        }
    }

    @Test
    fun statisticalSmokeTest_hasUniformBinsAndLowLagOneCorrelation() {
        val random =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(42, "statistical-smoke-test")
            )
        val sampleCount = 100_000
        val binCount = 16
        val bins = IntArray(binCount)
        val values = DoubleArray(sampleCount)

        repeat(sampleCount) { index ->
            val value = random.nextDouble(0.0, 1.0)
            values[index] = value
            bins[(value * binCount).toInt().coerceAtMost(binCount - 1)] += 1
        }

        val mean = values.average()
        val expectedPerBin = sampleCount.toDouble() / binCount.toDouble()
        val chiSquared =
            bins.sumOf { observed ->
                val difference = observed - expectedPerBin
                difference * difference / expectedPerBin
            }

        val firstMean = values.dropLast(1).average()
        val secondMean = values.drop(1).average()
        var covariance = 0.0
        var firstVariance = 0.0
        var secondVariance = 0.0
        for (index in 0 until sampleCount - 1) {
            val firstDelta = values[index] - firstMean
            val secondDelta = values[index + 1] - secondMean
            covariance += firstDelta * secondDelta
            firstVariance += firstDelta * firstDelta
            secondVariance += secondDelta * secondDelta
        }
        val lagOneCorrelation =
            covariance / sqrt(firstVariance * secondVariance)

        assertTrue(abs(mean - 0.5) < 0.005)
        assertTrue(chiSquared < 40.0)
        assertTrue(abs(lagOneCorrelation) < 0.02)
    }

    @Test
    fun derivedSeeds_haveNoCollisionsAcrossTenThousandCells() {
        val seeds =
            (0 until 10_000).map { index ->
                ScientificRandomProtocol.deriveSeed(
                    42,
                    "collision-smoke-test",
                    "cell=$index"
                )
            }

        assertEquals(seeds.size, seeds.toSet().size)
    }
}
