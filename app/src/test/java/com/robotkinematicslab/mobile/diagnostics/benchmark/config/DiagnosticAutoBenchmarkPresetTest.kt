package com.robotkinematicslab.mobile.diagnostics.benchmark.config

import com.robotkinematicslab.mobile.diagnostics.benchmark.planning.DiagnosticBenchmarkPlanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticAutoBenchmarkPresetTest {

    private val planner =
        DiagnosticBenchmarkPlanner()

    @Test
    fun autoBenchmarkPreset_usesCanonicalSafeDefaults() {
        val config =
            DiagnosticAutoBenchmarkPreset.buildConfig()

        assertEquals(4, config.sampling.reachableCount)
        assertEquals(4, config.sampling.unreachableCount)
        assertEquals(500, config.sampling.runCount)
        assertEquals(500, config.sampling.samplesPerLinkCount)
        assertFalse(config.sampling.unlimitedSamplesEnabled)
        assertEquals(5000, config.sampling.unlimitedSampleCount)

        assertEquals(
            listOf(42, 101, 202, 303, 404),
            config.seeds.seeds
        )

        assertEquals(3, config.topology.robotLinkCount)
        assertEquals(2, config.topology.minLinkCount)
        assertEquals(10, config.topology.maxLinkCount)
        assertEquals(DiagnosticJointMode.MIXED, config.topology.jointMode)
        assertFalse(config.topology.runAllTopologies)
        assertEquals(0.5, config.topology.stressLevel, EPSILON)
        assertFalse(config.topology.experimentalModeEnabled)

        assertEquals(800, config.solver.ikMaxIterations)
        assertEquals(0.00001, config.solver.ikTolerance, EPSILON)
        assertEquals(0.05, config.solver.ikDamping, EPSILON)
        assertEquals(0.02, config.solver.ikMaxStep, EPSILON)
    }

    @Test
    fun autoBenchmarkPreset_buildsSafePlannerClaim() {
        val config =
            DiagnosticAutoBenchmarkPreset.buildConfig()

        val plan =
            planner.buildPlan(config)

        assertEquals((2..10).toList(), plan.linkCounts)
        assertEquals((2..10).toList(), plan.safeLinkCounts)
        assertEquals(emptyList<Int>(), plan.experimentalLinkCounts)

        assertEquals(500, plan.samplesPerLinkCount)

        val expectedRuns =
            9L * 500L * 5L * 1L

        assertEquals(expectedRuns, plan.totalPlannedSequentialRuns)
        assertFalse(plan.isExperimental)
        assertFalse(plan.isUnlimited)
        assertTrue(plan.strongReliabilityClaimAllowed)
        assertEquals(
            "SAFE-MODE RESULT. Link counts are within the supported 2–10 range.",
            plan.reliabilityClaim
        )
    }

    @Test
    fun autoBenchmarkPreset_summaryTextDescribesCanonicalPreset() {
        val summary =
            DiagnosticAutoBenchmarkPreset.buildSummaryText()

        assertTrue(summary.contains("safe links 2–10", ignoreCase = true))
        assertTrue(summary.contains("500 samples per link", ignoreCase = true))
        assertTrue(summary.contains("mixed topology", ignoreCase = true))
        assertTrue(summary.contains("balanced solver", ignoreCase = true))
        assertTrue(summary.contains("deterministic seeds", ignoreCase = true))
    }

    companion object {
        private const val EPSILON = 1e-12
    }
}
