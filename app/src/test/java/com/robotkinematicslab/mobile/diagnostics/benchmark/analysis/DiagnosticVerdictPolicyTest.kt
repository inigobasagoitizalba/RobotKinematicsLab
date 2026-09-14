package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.planning.DiagnosticBenchmarkPlan
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticVerdictPolicyTest {

    private val policy = DiagnosticVerdictPolicy()

    @Test
    fun experimentalPlan_neverMakesAProductionPassClaim() {
        val verdict = policy.evaluate(plan(isExperimental = true), emptySummary())

        assertEquals(DiagnosticVerdict.EXPERIMENTAL_RESULT_ONLY, verdict)
    }

    @Test
    fun falseAcceptanceOfUnreachableTarget_fails() {
        val verdict =
            policy.evaluate(
                plan(),
                emptySummary().copy(sequentialUnreachableAccepted = 1)
            )

        assertEquals(DiagnosticVerdict.FAIL, verdict)
    }

    @Test
    fun numericalOrBoundaryEvidence_producesWarning() {
        val verdict =
            policy.evaluate(
                plan(),
                emptySummary().copy(invalidNumericalCount = 1)
            )

        assertEquals(DiagnosticVerdict.PASS_WITH_WARNINGS, verdict)
    }

    @Test
    fun cleanEvidence_passes() {
        assertEquals(
            DiagnosticVerdict.PASS,
            policy.evaluate(
                plan(),
                emptySummary().copy(
                    oracleReachableRuns = 10,
                    oracleReachableAccepted = 10,
                    sequentialReachableRuns = 30,
                    sequentialReachableAccepted = 30,
                    sequentialUnreachableRuns = 30,
                    sequentialUnreachableRejected = 30
                )
            )
        )
    }

    @Test
    fun noEvidence_cannotProduceScientificPass() {
        assertEquals(
            DiagnosticVerdict.PASS_WITH_WARNINGS,
            policy.evaluate(plan(), emptySummary())
        )
    }

    @Test
    fun poorReachableRecovery_failsEvenWithoutFalseUnreachableAcceptance() {
        val summary =
            emptySummary().copy(
                oracleReachableRuns = 10,
                oracleReachableAccepted = 10,
                sequentialReachableRuns = 100,
                sequentialReachableAccepted = 79,
                sequentialReachableRejected = 21,
                sequentialUnreachableRuns = 100,
                sequentialUnreachableRejected = 100
            )

        assertEquals(DiagnosticVerdict.FAIL, policy.evaluate(plan(), summary))
    }

    private fun plan(
        isExperimental: Boolean = false
    ) = DiagnosticBenchmarkPlan(
        linkCounts = listOf(3),
        safeLinkCounts = listOf(3),
        experimentalLinkCounts = emptyList(),
        samplesPerLinkCount = 1,
        totalPlannedSequentialRuns = 1L,
        isExperimental = isExperimental,
        isUnlimited = false,
        strongReliabilityClaimAllowed = !isExperimental,
        warnings = emptyList(),
        reliabilityClaim = "test"
    )

    private fun emptySummary() = DiagnosticSummary(
        totalCases = 0,
        totalRuns = 0,
        expectedReachableCount = 0,
        expectedUnreachableCount = 0,
        oracleReachableRuns = 0,
        oracleReachableAccepted = 0,
        oracleReachableRejected = 0,
        sequentialRuns = 0,
        sequentialAcceptedCount = 0,
        sequentialRejectedCount = 0,
        sequentialReachableRuns = 0,
        sequentialReachableAccepted = 0,
        sequentialReachableRejected = 0,
        sequentialUnreachableRuns = 0,
        sequentialUnreachableAccepted = 0,
        sequentialUnreachableRejected = 0,
        averageSequentialInitialError = Double.NaN,
        averageSequentialError = Double.NaN,
        maxSequentialError = Double.NaN,
        averageSequentialImprovement = Double.NaN,
        averageSequentialImprovementRatio = Double.NaN,
        averageSequentialIterations = Double.NaN,
        averageIterationSaturationRatio = Double.NaN,
        averageJointDeltaNorm = Double.NaN,
        averageMaxSingleJointMovement = Double.NaN,
        averageJointLimitPressureRatio = Double.NaN,
        runsWithNearJointLimit = 0,
        solvedCount = 0,
        nearSolvedCount = 0,
        improvedButNotEnoughCount = 0,
        stalledCount = 0,
        worsenedCount = 0,
        invalidNumericalCount = 0,
        easySeedRuns = 0,
        mediumSeedRuns = 0,
        hardSeedRuns = 0,
        extremeSeedRuns = 0,
        unknownSeedRuns = 0
    )
}
