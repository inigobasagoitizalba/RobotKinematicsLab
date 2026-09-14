package com.robotkinematicslab.mobile.ui.training.explainability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainabilityFigureExportContractTest {

    @Test
    fun requiredFigures_containsTheFiveExplainableAiFiguresExactlyOnce() {
        assertEquals(5, ExplainabilityFigureExportContract.requiredFigures.size)
        assertEquals(
            setOf(
                ExplainabilityFigureId.GLOBAL_FEATURE_IMPORTANCE,
                ExplainabilityFigureId.SCIENTIFIC_FAMILY_IMPORTANCE,
                ExplainabilityFigureId.FAMILY_CO_ACTIVATION,
                ExplainabilityFigureId.FEATURE_IMPACT_DISTRIBUTION,
                ExplainabilityFigureId.LOCAL_CONTRIBUTION_WATERFALL
            ),
            ExplainabilityFigureExportContract.requiredFigures
        )
        assertEquals(
            5,
            ExplainabilityFigureExportContract.requiredFigures.map { it.title }.toSet().size
        )
    }

    @Test
    fun createSession_repeatedExecutionCannotOverwriteThePreviousExecution() {
        val first = session(completedAt = 1_789_000_000_000L)
        val second = session(completedAt = 1_789_000_000_001L)

        assertEquals("explainable-ai", first.collection)
        assertEquals(first.analysisId, second.analysisId)
        assertNotEquals(first.executionId, second.executionId)
        assertTrue(first.metadata.contains("run-42"))
        assertTrue(first.metadata.contains("held-out n=512"))
        assertTrue(first.metadata.contains("IG steps=32"))
        assertTrue(first.metadata.contains("completed epoch ms=1789000000000"))
    }

    @Test
    fun waterfallTitle_keepsDifferentHeldOutCasesInSeparateFiles() {
        val first = ExplainabilityFigureExportContract.waterfallTitle(1, 100L)
        val second = ExplainabilityFigureExportContract.waterfallTitle(2, 101L)

        assertNotEquals(first, second)
        assertTrue(first.contains("case 1"))
        assertTrue(second.contains("source row 101"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun createSession_rejectsMissingRunIdentity() {
        ExplainabilityFigureExportContract.createSession(
            runId = "",
            candidateId = "mlp-64",
            featureSelectionName = "Expanded context",
            explainedSampleCount = 512,
            integratedGradientSteps = 32,
            completedAtEpochMillis = 1_789_000_000_000L
        )
    }

    private fun session(completedAt: Long): ExplainabilityFigureExportSession =
        ExplainabilityFigureExportContract.createSession(
            runId = "run-42",
            candidateId = "mlp-64",
            featureSelectionName = "Expanded context · 383 variables",
            explainedSampleCount = 512,
            integratedGradientSteps = 32,
            completedAtEpochMillis = completedAt
        )
}
