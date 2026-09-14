package com.robotkinematicslab.mobile.diagnostics.benchmark.execution

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticTopologyConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiagnosticSeedReproducibilityTest {

    @Test
    fun identicalExperimentSeed_reproducesTargetsAndNumericalResults() {
        val config = config(seed = 42, minLinks = 3, maxLinks = 3)

        val first = Layer1DiagnosticExperiment().runExperiment(config)
        val second = Layer1DiagnosticExperiment().runExperiment(config)

        assertEquals(
            first.targetCases.map { it.target to it.sourceJointState },
            second.targetCases.map { it.target to it.sourceJointState }
        )
        assertEquals(
            first.runResults.map(::withoutRuntimeNoise),
            second.runResults.map(::withoutRuntimeNoise)
        )
    }

    @Test
    fun differentExperimentSeed_changesGeneratedTargets() {
        val first =
            Layer1DiagnosticExperiment().runExperiment(
                config(seed = 42, minLinks = 3, maxLinks = 3)
            )
        val second =
            Layer1DiagnosticExperiment().runExperiment(
                config(seed = 43, minLinks = 3, maxLinks = 3)
            )

        assertNotEquals(
            first.targetCases.map { it.target },
            second.targetCases.map { it.target }
        )
    }

    @Test
    fun sameSeedAndThreeLinkCell_isIndependentOfOtherLinkCountsInSuite() {
        val isolated =
            Layer1DiagnosticExperiment().runExperiment(
                config(seed = 42, minLinks = 3, maxLinks = 3)
            )
        val suite =
            Layer1DiagnosticExperiment().runExperiment(
                config(seed = 42, minLinks = 2, maxLinks = 3)
            )

        val isolatedTargets = isolated.targetCases.map { it.target }
        val suiteThreeLinkTargets =
            suite.targetCases
                .filter { it.id.contains("-3L-AUTO-") }
                .map { it.target }

        val isolatedSequence =
            isolated.runResults
                .filter { it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK }
                .map { it.target }
        val suiteThreeLinkSequence =
            suite.runResults
                .filter {
                    it.linkCount == 3 &&
                        it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
                }
                .map { it.target }

        assertEquals(isolatedTargets, suiteThreeLinkTargets)
        assertEquals(isolatedSequence, suiteThreeLinkSequence)
    }

    @Test
    fun duplicateSeeds_areExecutedOnlyOnceAndReportedAsEffectiveConfig() {
        val report =
            Layer1DiagnosticExperiment().runExperiment(
                config(seed = 42, minLinks = 3, maxLinks = 3).copy(
                    seeds = DiagnosticSeedConfig(listOf(42, 42, 42))
                )
            )

        val sequentialRuns =
            report.runResults.count {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        assertEquals(listOf(42), report.config.seeds.seeds)
        assertEquals(SAMPLES_PER_LINK.toLong(), report.benchmarkPlan.totalPlannedSequentialRuns)
        assertEquals(SAMPLES_PER_LINK, sequentialRuns)
        assertEquals(1, report.seedAggregates.size)
    }

    @Test
    fun csvExports_recordCompleteSeedCoordinatesAndProtocol() {
        val runHistoryFile = Files.createTempFile("seed-run-history", ".csv").toFile()
        val caseFile = Files.createTempFile("seed-case-results", ".csv").toFile()
        val exportConfig =
            config(seed = 42, minLinks = 3, maxLinks = 3).copy(
                performance =
                    DiagnosticPerformanceConfig(
                        storeFullRunHistory = true,
                        storePerCaseDetails = true,
                        exportRunHistoryToCsv = true,
                        runHistoryCsvPath = runHistoryFile.absolutePath,
                        exportCaseResultsToCsv = true,
                        caseResultsCsvPath = caseFile.absolutePath
                    )
            )

        Layer1DiagnosticExperiment().runExperiment(exportConfig)

        listOf(runHistoryFile, caseFile).forEach { file ->
            val lines = file.readLines()
            val header = lines.first().split(',')
            val firstRow = lines[1].split(',')

            assertEquals("42", firstRow[header.indexOf("seed")])
            assertEquals("3", firstRow[header.indexOf("linkCount")])
            assertEquals("AUTO", firstRow[header.indexOf("jointMode")])
            assertEquals(
                ScientificRandomProtocol.ID,
                firstRow[header.indexOf("randomProtocol")]
            )
        }
    }

    private fun config(
        seed: Int,
        minLinks: Int,
        maxLinks: Int
    ) = DiagnosticBenchmarkConfig(
        sampling =
            DiagnosticSamplingConfig(
                reachableCount = 3,
                unreachableCount = 2,
                runCount = SAMPLES_PER_LINK,
                samplesPerLinkCount = SAMPLES_PER_LINK
            ),
        seeds = DiagnosticSeedConfig(listOf(seed)),
        topology =
            DiagnosticTopologyConfig(
                robotLinkCount = minLinks,
                minLinkCount = minLinks,
                maxLinkCount = maxLinks,
                jointMode = DiagnosticJointMode.AUTO,
                runAllTopologies = false,
                stressLevel = 0.5
            ),
        solver = DiagnosticSolverConfig(ikMaxIterations = 20),
        performance =
            DiagnosticPerformanceConfig(
                storeFullRunHistory = true,
                storePerCaseDetails = true,
                storeTransitionDetails = true,
                storeExtremeRunDetails = true
            )
    )

    private fun withoutRuntimeNoise(run: DiagnosticRunResult): DiagnosticRunResult {
        return run.copy(solveDurationNanos = 0L)
    }

    private companion object {
        const val SAMPLES_PER_LINK = 8
    }
}
