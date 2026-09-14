package com.robotkinematicslab.mobile.ui.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificEntityNameResolverTest {

    @Test
    fun generatedModelKeepsExactIdButShowsReadableRealMetadata() {
        val label =
            ScientificEntityNameResolver.model(
                path = "/models/closed_loop_context_model-cycle-1-attempt-2-1789210000000.rklm"
            )

        assertEquals("Closed loop context", label.primary)
        assertEquals("cycle 1 · attempt 2", label.qualifier)
        assertEquals(
            "closed_loop_context_model-cycle-1-attempt-2-1789210000000.rklm",
            label.technicalId
        )
        assertFalse(label.primary.contains("178921"))
    }

    @Test
    fun suppliedHumanNameIncludingInternationalCharactersWinsWithoutChangingRunId() {
        val label = ScientificEntityNameResolver.run("training-178921", "  Brazo número α  ")

        assertEquals("Brazo número α", label.primary)
        assertEquals("training-178921", label.technicalId)
    }

    @Test
    fun missingIdentityIsRejectedInsteadOfFabricatingAReference() {
        assertThrows(IllegalArgumentException::class.java) {
            ScientificEntityNameResolver.model("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificEntityNameResolver.run("", "Friendly name")
        }
        assertTrue(
            ScientificEntityNameResolver.model("training-1789210000000.rklm").primary.isNotBlank()
        )
    }
}
