package com.robotkinematicslab.mobile.ml.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DeterministicCsvRowSamplerTest {

    @Test
    fun `sample has exact size unique in-range indices and is deterministic`() {
        val first = DeterministicCsvRowSampler.indices(totalRows = 100_000, maximumRows = 5_000, seed = 2604)
        val repeated = DeterministicCsvRowSampler.indices(totalRows = 100_000, maximumRows = 5_000, seed = 2604)

        assertEquals(5_000, first.size)
        assertEquals(first, repeated)
        assertTrue(first.all { it in 0 until 100_000 })
    }

    @Test
    fun `different seeds select a different corpus subset`() {
        val first = DeterministicCsvRowSampler.indices(1_000, 100, 1)
        val second = DeterministicCsvRowSampler.indices(1_000, 100, 2)

        assertNotEquals(first, second)
    }

    @Test
    fun `request larger than corpus returns every row once`() {
        assertEquals((0 until 7).toSet(), DeterministicCsvRowSampler.indices(7, 100, 9))
    }

    @Test
    fun `bounded closed-loop sample includes every newly appended row and remains deterministic`() {
        val first = DeterministicCsvRowSampler.indicesIncludingTail(1_400, 100, 42, 40)
        val repeated = DeterministicCsvRowSampler.indicesIncludingTail(1_400, 100, 42, 40)

        assertEquals(100, first.size)
        assertEquals(first, repeated)
        assertTrue((1_360 until 1_400).all(first::contains))
        assertEquals(60, first.count { it < 1_360 })
    }

    @Test
    fun `new rows that exceed the cap are rejected instead of being silently omitted`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeterministicCsvRowSampler.indicesIncludingTail(1_400, 30, 42, 31)
        }
    }
}
