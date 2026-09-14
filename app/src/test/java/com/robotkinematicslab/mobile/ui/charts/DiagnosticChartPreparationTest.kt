package com.robotkinematicslab.mobile.ui.charts

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DiagnosticChartPreparationTest {

    @Test
    fun partitionAndSort_preserveEveryRunAndOrderEachTimeline() {
        val sequentialLater = run(index = 9, kind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK)
        val oracle = run(index = 7, kind = DiagnosticRunKind.ORACLE_REACHABLE_CHECK)
        val sequentialEarlier = run(index = 2, kind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK)

        val sorted =
            sortDiagnosticRuns(
                partitionDiagnosticRuns(
                    listOf(sequentialLater, oracle, sequentialEarlier)
                )
            )

        assertEquals(listOf(2, 9), sorted.sequentialRuns.map { it.runIndex })
        assertEquals(listOf(7), sorted.oracleRuns.map { it.runIndex })
        assertSame(sequentialEarlier, sorted.sequentialRuns.first())
    }

    @Test
    fun heatMapIndex_usesExactCountsWithoutDroppingRuns() {
        val accepted = run(index = 1, accepted = true, nearLimitJointNames = listOf("J1"))
        val rejected = run(index = 2, accepted = false, nearLimitJointNames = listOf("J1", "J2"))

        val index = buildDiagnosticHeatMapIndex(listOf(accepted, rejected))

        assertEquals(DiagnosticHeatMapCount(total = 2, accepted = 1), index.seedLink[42 to 3])
        assertEquals(2, index.statusExpectedClass["SUCCESS" to "REACHABLE"])
        assertEquals(2, index.jointCase["J1" to "case-1"])
        assertEquals(1, index.jointCase["J2" to "case-1"])
    }

    @Test
    fun displayPreparation_keepsEveryRunBeyondTheFormerFourThousandLimit() {
        val values = (0 until 10_000).map { index -> run(index = index) }

        val first = deterministicDisplaySample(values, maximumSize = 400)
        val second = deterministicDisplaySample(values, maximumSize = 400)

        assertEquals(values, first)
        assertEquals(10_000, first.size)
        assertEquals((0 until 10_000).toList(), first.map { it.runIndex })
        assertEquals(first, second)
    }

    private fun run(
        index: Int,
        kind: DiagnosticRunKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
        accepted: Boolean = true,
        nearLimitJointNames: List<String> = emptyList()
    ): DiagnosticRunResult {
        return DiagnosticRunResult(
            seed = 42,
            linkCount = 3,
            jointMode = DiagnosticJointMode.AUTO,
            runIndex = index,
            runKind = kind,
            selectedCaseId = "case-1",
            transitionFromCaseId = null,
            transitionToCaseId = "case-1",
            expectedClass = DiagnosticExpectedClass.REACHABLE,
            solverAccepted = accepted,
            status = "SUCCESS",
            detailCode = "NONE",
            finalError = 0.001,
            iterations = 5,
            target = Vec3(0.1, 0.2, 0.3),
            sourceJointState = null,
            seedJointState = RobotState(listOf(0.0)),
            solutionJointValues = listOf(0.1),
            initialError = 0.2,
            improvement = 0.199,
            improvementRatio = 0.995,
            progressClass = DiagnosticProgressClass.SOLVED,
            seedDistanceBucket = DiagnosticSeedDistanceBucket.EASY,
            seedMinNormalizedLimitMargin = 0.5,
            seedLogConditionNumber = 1.0,
            iterationSaturationRatio = 0.1,
            jointDeltaNorm = 0.1,
            maxSingleJointMovement = 0.1,
            normalizedJointTravelRms = 0.1,
            finalMinNormalizedLimitMargin = 0.4,
            backtrackingRetryCount = 0,
            solveDurationNanos = 1_000L,
            nearLimitJointCount = nearLimitJointNames.size,
            nearLimitJointNames = nearLimitJointNames,
            jointLimitPressureRatio = if (nearLimitJointNames.isEmpty()) 0.0 else 0.5,
            note = "test"
        )
    }
}
