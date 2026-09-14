package com.robotkinematicslab.mobile.ui.diagnostic

import com.robotkinematicslab.mobile.storage.DiagnosticDraft
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.execution.Layer1DiagnosticExperiment
import org.junit.Assert.*
import org.junit.Test

class DiagnosticSetupContractTest {
    private fun evaluate(draft: DiagnosticDraft = DiagnosticDraft()) = DiagnosticSetupContract.evaluate(draft)

    @Test fun fifteenThousandShowsActualMultipliersAndSeparateOracleWork() {
        val result = evaluate(DiagnosticDraft(samplesPerLinkCountText = "5000", seedText = "42,101,202"))
        assertTrue(result.canRun)
        assertEquals(15000L, result.plan!!.totalPlannedSequentialRuns)
        assertEquals(12L, result.oracleRuns)
        assertEquals(15012L, result.totalSolverRuns)
        assertTrue(result.breakdown.contains("1 link counts × 5000 sequential samples × 3 distinct seeds × 1 topology modes"))
    }

    @Test fun fullAutoDefaultsHaveExplicitWorkInsteadOfDurationGuess() {
        val result = evaluate(DiagnosticDraft(manualRangeMode = true, samplesPerLinkCountText = "500", seedText = "42,101,202,303,404"))
        assertEquals(22500L, result.plan!!.totalPlannedSequentialRuns)
        assertEquals(180L, result.oracleRuns)
        assertEquals("MEDIUM", estimateRuntimeRisk(15000, false, false))
        assertEquals("LOW", estimateRuntimeRisk(10000, false, false))
        assertEquals("HIGH", estimateRuntimeRisk(50001, false, false))
        assertEquals("EXTREME", estimateRuntimeRisk(100001, false, false))
    }

    @Test fun invalidSeedsCannotSilentlyBecomeAnotherExperiment() {
        listOf("", "42,bad", "42,", "2147483648").forEach { assertFalse(it, evaluate(DiagnosticDraft(seedText = it)).canRun) }
        val result = evaluate(DiagnosticDraft(seedText = "42,42,-1", samplesPerLinkCountText = "2", runAllTopologies = true))
        assertEquals(listOf(42,-1), result.config!!.seeds.seeds)
        assertEquals(16L, result.plan!!.totalPlannedSequentialRuns)
    }

    @Test fun modeBoundariesAndActiveSampleLimitAreEnforcedBeforeExecution() {
        assertTrue(evaluate(DiagnosticDraft(robotLinkCountText = "10")).canRun)
        assertFalse(evaluate(DiagnosticDraft(robotLinkCountText = "11")).canRun)
        assertTrue(evaluate(DiagnosticDraft(robotLinkCountText = "11", experimentalMode = true)).canRun)
        assertTrue(evaluate(DiagnosticDraft(robotLinkCountText = "100", experimentalMode = true)).canRun)
        assertFalse(evaluate(DiagnosticDraft(robotLinkCountText = "101", experimentalMode = true)).canRun)
        assertFalse(evaluate(DiagnosticDraft(samplesPerLinkCountText = "5001")).canRun)
        assertFalse(evaluate(DiagnosticDraft(unlimitedSamplesEnabled = true)).canRun)
        assertTrue(evaluate(DiagnosticDraft(unlimitedSamplesEnabled = true, experimentalMode = true, unlimitedSampleCountText = "1000000")).canRun)
        assertFalse(evaluate(DiagnosticDraft(unlimitedSamplesEnabled = true, experimentalMode = true, unlimitedSampleCountText = "1000001")).canRun)
    }

    @Test fun equalRangeUsesVisibleLinkCountAndInactiveFieldsCannotChangePlan() {
        val result = evaluate(DiagnosticDraft(manualRangeMode = true, minLinkCountText = "4", maxLinkCountText = "4", robotLinkCountText = "bad", unlimitedSampleCountText = "bad"))
        assertTrue(result.canRun)
        assertEquals(listOf(4), result.plan!!.linkCounts)
        assertEquals(4, result.config!!.topology.robotLinkCount)
        assertFalse(evaluate(DiagnosticDraft(manualRangeMode = true, minLinkCountText = "5", maxLinkCountText = "4")).canRun)
    }

    @Test fun invalidNumericValuesAreRejectedWithoutDigitSanitization() {
        listOf(DiagnosticDraft(reachableCountText = "-1"), DiagnosticDraft(reachableCountText = "0", unreachableCountText = "0"),
            DiagnosticDraft(reachableCountText = "200", unreachableCountText = "1"), DiagnosticDraft(stressLevel = Float.NaN),
            DiagnosticDraft(stressLevel = 1.01f), DiagnosticDraft(ikMaxIterationsText = "10001"),
            DiagnosticDraft(ikToleranceText = "1e-10"), DiagnosticDraft(ikDampingText = "NaN"),
            DiagnosticDraft(ikMaxStepText = "1e-7")).forEach { assertFalse(it.toString(), evaluate(it).canRun) }
        assertTrue(evaluate(DiagnosticDraft(ikToleranceText = "1e-5", stressLevel = 0f)).canRun)
        assertTrue(evaluate(DiagnosticDraft(stressLevel = 1f)).canRun)
    }

    @Test fun tinyRealExperimentUsesTheSameConfigAndPlanAsPreview() {
        val prepared = evaluate(DiagnosticDraft(robotLinkCountText = "2", reachableCountText = "1", unreachableCountText = "0",
            samplesPerLinkCountText = "2", ikMaxIterationsText = "1", jointMode = DiagnosticJointMode.MIXED))
        val report = Layer1DiagnosticExperiment().runExperiment(requireNotNull(prepared.config))
        assertEquals(prepared.config, report.config)
        assertEquals(prepared.plan, report.benchmarkPlan)
        assertEquals(3, report.runResults.size)
    }
}
