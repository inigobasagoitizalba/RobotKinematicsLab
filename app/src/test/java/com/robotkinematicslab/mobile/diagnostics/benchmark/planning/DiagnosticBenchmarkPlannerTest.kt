package com.robotkinematicslab.mobile.diagnostics.benchmark.planning

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticTopologyConfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBenchmarkPlannerTest {

    private val planner =
        DiagnosticBenchmarkPlanner()

    @Test
    fun safeRangeMode_twoToTen_allowsStrongReliabilityClaim() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 500
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 2,
                        maxLinkCount = 10,
                        runAllTopologies = false,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertEquals((2..10).toList(), plan.linkCounts)
        assertEquals((2..10).toList(), plan.safeLinkCounts)
        assertEquals(emptyList<Int>(), plan.experimentalLinkCounts)
        assertEquals(500, plan.samplesPerLinkCount)
        assertEquals(9L * 500L * 1L * 1L, plan.totalPlannedSequentialRuns)
        assertFalse(plan.isExperimental)
        assertFalse(plan.isUnlimited)
        assertTrue(plan.strongReliabilityClaimAllowed)
        assertEquals(
            "SAFE-MODE RESULT. Link counts are within the supported 2–10 range.",
            plan.reliabilityClaim
        )
    }

    @Test
    fun safeModeClampsLinkRangeAboveTen_whenExperimentalModeIsDisabled() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 100
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 2,
                        maxLinkCount = 20,
                        runAllTopologies = false,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertEquals((2..20).toList(), plan.linkCounts)
        assertEquals((2..10).toList(), plan.safeLinkCounts)
        assertEquals((11..20).toList(), plan.experimentalLinkCounts)
        assertTrue(plan.isExperimental)
        assertFalse(plan.strongReliabilityClaimAllowed)
        assertTrue(
            plan.reliabilityClaim.contains(
                other = "EXPERIMENTAL RESULT ONLY",
                ignoreCase = true
            )
        )
    }

    @Test
    fun explicitExperimentalMode_allowsLinksAboveTenButBlocksStrongReliabilityClaim() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 50
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 12,
                        minLinkCount = 12,
                        maxLinkCount = 12,
                        runAllTopologies = false,
                        experimentalModeEnabled = true
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertEquals(listOf(12), plan.linkCounts)
        assertEquals(emptyList<Int>(), plan.safeLinkCounts)
        assertEquals(listOf(12), plan.experimentalLinkCounts)
        assertTrue(plan.isExperimental)
        assertFalse(plan.strongReliabilityClaimAllowed)
        assertTrue(
            plan.warnings.any {
                it.contains("Experimental", ignoreCase = true)
            }
        )
    }

    @Test
    fun normalSampleCount_isClampedToFiveThousand() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 50_000,
                        unlimitedSamplesEnabled = false
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 3,
                        maxLinkCount = 3,
                        runAllTopologies = false,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertEquals(5_000, plan.samplesPerLinkCount)
        assertEquals(5_000L, plan.totalPlannedSequentialRuns)
        assertTrue(plan.strongReliabilityClaimAllowed)
    }

    @Test
    fun unlimitedMode_usesManualUnlimitedSampleCountAndBlocksStrongReliabilityClaim() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 500,
                        unlimitedSamplesEnabled = true,
                        unlimitedSampleCount = 20_000
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 3,
                        maxLinkCount = 3,
                        runAllTopologies = false,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertEquals(listOf(3), plan.linkCounts)
        assertEquals(20_000, plan.samplesPerLinkCount)
        assertEquals(20_000L, plan.totalPlannedSequentialRuns)
        assertTrue(plan.isUnlimited)
        assertFalse(plan.strongReliabilityClaimAllowed)
        assertTrue(
            plan.reliabilityClaim.contains(
                other = "unlimited sample mode",
                ignoreCase = true
            )
        )
        assertTrue(
            plan.warnings.any {
                it.contains("Unlimited sample mode", ignoreCase = true)
            }
        )
    }

    @Test
    fun runAllTopologies_multipliesTotalRunsByJointModeCount() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 10
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(42, 101)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 2,
                        maxLinkCount = 4,
                        runAllTopologies = true,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        val expectedLinkCountCount =
            3

        val expectedSeedCount =
            2

        val expectedTopologyModeCount =
            DiagnosticJointMode.entries.size

        assertEquals((2..4).toList(), plan.linkCounts)
        assertEquals(
            expectedLinkCountCount.toLong() * 10L * expectedSeedCount.toLong() * expectedTopologyModeCount.toLong(),
            plan.totalPlannedSequentialRuns
        )
    }

    @Test
    fun largeBenchmark_addsRuntimeWarning() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        samplesPerLinkCount = 5_000
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(1, 2, 3)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        robotLinkCount = 3,
                        minLinkCount = 2,
                        maxLinkCount = 10,
                        runAllTopologies = true,
                        experimentalModeEnabled = false
                    )
            )

        val plan =
            planner.buildPlan(config)

        assertTrue(plan.totalPlannedSequentialRuns > 50_000)
        assertTrue(
            plan.warnings.any {
                it.contains("Large benchmark planned", ignoreCase = true)
            }
        )
    }

    @Test
    fun veryLargeBenchmark_keepsExactPositiveCountBeyondIntRange() {
        val config =
            DiagnosticBenchmarkConfig(
                sampling =
                    DiagnosticSamplingConfig(
                        unlimitedSamplesEnabled = true,
                        unlimitedSampleCount = 1_000_000
                    ),
                seeds =
                    DiagnosticSeedConfig(
                        seeds = listOf(1, 2, 3, 4, 5, 6)
                    ),
                topology =
                    DiagnosticTopologyConfig(
                        minLinkCount = 2,
                        maxLinkCount = 100,
                        runAllTopologies = true,
                        experimentalModeEnabled = true
                    )
            )

        val plan = planner.buildPlan(config)

        assertEquals(2_376_000_000L, plan.totalPlannedSequentialRuns)
        assertTrue(plan.totalPlannedSequentialRuns > Int.MAX_VALUE.toLong())
    }
}
