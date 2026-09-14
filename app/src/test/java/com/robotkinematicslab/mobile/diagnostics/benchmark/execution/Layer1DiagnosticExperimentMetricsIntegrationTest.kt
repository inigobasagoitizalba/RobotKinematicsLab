package com.robotkinematicslab.mobile.diagnostics.benchmark.execution

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CancellationException

class Layer1DiagnosticExperimentMetricsIntegrationTest {

    @Test(expected = CancellationException::class)
    fun cancellationRequestedBeforeStart_abortsExperiment() {
        Layer1DiagnosticExperiment(isCancellationRequested = { true })
            .runExperiment(DiagnosticBenchmarkConfig())
    }

    @Test
    fun minimalExperiment_populatesNewMetricsEndToEnd() {
        val report =
            Layer1DiagnosticExperiment().runExperiment(
                config =
                    DiagnosticBenchmarkConfig(
                        sampling =
                            DiagnosticSamplingConfig(
                                reachableCount = 1,
                                unreachableCount = 1,
                                runCount = 1,
                                samplesPerLinkCount = 1
                            ),
                        seeds =
                            DiagnosticSeedConfig(
                                seeds = listOf(42)
                            ),
                        topology =
                            DiagnosticTopologyConfig(
                                robotLinkCount = 3,
                                minLinkCount = 3,
                                maxLinkCount = 3
                            ),
                        solver =
                            DiagnosticSolverConfig(
                                ikMaxIterations = 5
                            ),
                        performance =
                            DiagnosticPerformanceConfig(
                                storeFullRunHistory = true,
                                storePerCaseDetails = true,
                                storeTransitionDetails = true,
                                storeExtremeRunDetails = true,
                                exportRunHistoryToCsv = false,
                                exportCaseResultsToCsv = false
                            )
                    )
            )

        assertTrue(report.runResults.isNotEmpty())
        assertTrue(report.cases.isNotEmpty())

        report.runResults.forEach { run ->
            assertTrue(run.seedMinNormalizedLimitMargin in 0.0..0.5)
            assertTrue(run.finalMinNormalizedLimitMargin in 0.0..0.5)
            assertTrue(run.normalizedJointTravelRms.isFinite())
            assertTrue(run.normalizedJointTravelRms >= 0.0)
            assertTrue(run.backtrackingRetryCount >= 0)
            assertTrue(run.solveDurationNanos > 0L)
        }
    }

    @Test
    fun unsafeProgrammaticConfig_isNormalizedAndReportedExactly() {
        val defaults = DiagnosticSolverConfig()
        val report =
            Layer1DiagnosticExperiment().runExperiment(
                DiagnosticBenchmarkConfig(
                    sampling =
                        DiagnosticSamplingConfig(
                            reachableCount = 0,
                            unreachableCount = 0,
                            samplesPerLinkCount = 1
                        ),
                    topology =
                        DiagnosticTopologyConfig(
                            robotLinkCount = 2,
                            minLinkCount = 2,
                            maxLinkCount = 2,
                            stressLevel = Double.NaN
                        ),
                    solver =
                        DiagnosticSolverConfig(
                            ikMaxIterations = 1,
                            ikTolerance = Double.NaN,
                            ikDamping = Double.POSITIVE_INFINITY,
                            ikMaxStep = Double.NaN
                        ),
                    performance =
                        DiagnosticPerformanceConfig(
                            storeFullRunHistory = false,
                            storePerCaseDetails = false,
                            storeTransitionDetails = false,
                            storeExtremeRunDetails = false
                        )
                )
            )

        assertEquals(1, report.config.sampling.reachableCount)
        assertEquals(0, report.config.sampling.unreachableCount)
        assertEquals(defaults.ikTolerance, report.config.solver.ikTolerance, 0.0)
        assertEquals(defaults.ikDamping, report.config.solver.ikDamping, 0.0)
        assertEquals(defaults.ikMaxStep, report.config.solver.ikMaxStep, 0.0)
        assertEquals(0.5, report.config.topology.stressLevel, 0.0)
        assertTrue(report.targetCases.isNotEmpty())
    }
}
