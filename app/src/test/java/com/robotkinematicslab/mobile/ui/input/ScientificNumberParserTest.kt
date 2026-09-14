package com.robotkinematicslab.mobile.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScientificNumberParserTest {
    @Test
    fun acceptsSignedDotAndCommaDecimals() {
        assertEquals(-90.5, ScientificNumberParser.parseDouble("-90.5")!!, 0.0)
        assertEquals(-90.5, ScientificNumberParser.parseDouble(" -90,5 ")!!, 0.0)
        assertEquals(1.0e-6, ScientificNumberParser.parseDouble("1e-6")!!, 0.0)
    }

    @Test
    fun rejectsAmbiguousNonFiniteAndMalformedValues() {
        assertNull(ScientificNumberParser.parseDouble("1,234.5"))
        listOf("NaN", "Infinity", "-Infinity", "1e309").forEach { raw ->
            assertNull("Non-finite scientific input must not reach a numeric control: $raw", ScientificNumberParser.parseDouble(raw))
        }
        assertNull(ScientificNumberParser.parseDouble("--2"))
        assertNull(ScientificNumberParser.parseDouble(""))
    }
}
