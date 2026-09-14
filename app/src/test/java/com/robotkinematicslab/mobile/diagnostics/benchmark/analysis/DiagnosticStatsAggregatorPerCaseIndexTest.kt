package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticTargetCase
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticPerCaseAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticStatsAggregatorPerCaseIndexTest {

    private val policy = DiagnosticMetricPolicy()

    @Test
    fun indexedAggregationMatchesLegacyFilteringForNonFiniteValuesTiesAndEmptyCase() {
        val targets =
            listOf(
                target("empty", DiagnosticExpectedClass.UNREACHABLE),
                target("case-b", DiagnosticExpectedClass.REACHABLE),
                target("case-a", DiagnosticExpectedClass.REACHABLE)
            )
        val runs =
            listOf(
                run("case-a", status = "FIRST", finalError = Double.NaN),
                run(
                    "case-b",
                    kind = DiagnosticRunKind.ORACLE_REACHABLE_CHECK,
                    accepted = true,
                    status = "ORACLE"
                ),
                run(
                    "case-a",
                    accepted = false,
                    status = "SECOND",
                    finalError = 5e-4,
                    initialError = Double.POSITIVE_INFINITY,
                    improvementRatio = Double.NEGATIVE_INFINITY,
                    progressClass = DiagnosticProgressClass.NEAR_SOLVED,
                    seedBucket = DiagnosticSeedDistanceBucket.HARD
                ),
                run(
                    "case-b",
                    accepted = false,
                    status = "FAILED",
                    finalError = 2e-2,
                    initialError = 0.4,
                    improvementRatio = 0.5,
                    progressClass = DiagnosticProgressClass.IMPROVED_BUT_NOT_ENOUGH,
                    seedBucket = DiagnosticSeedDistanceBucket.MEDIUM
                ),
                run(
                    "case-a",
                    accepted = true,
                    status = "FIRST",
                    finalError = 1e-5,
                    initialError = 0.2,
                    improvementRatio = 0.9,
                    progressClass = DiagnosticProgressClass.SOLVED,
                    seedBucket = DiagnosticSeedDistanceBucket.EASY
                ),
                run(
                    "orphan",
                    accepted = false,
                    status = "IGNORED",
                    finalError = Double.POSITIVE_INFINITY
                )
            )

        val expected = legacyBuildPerCaseAggregates(targets, runs)
        val actual = DiagnosticStatsAggregator(policy).buildPerCaseAggregates(targets, runs)

        assertEquals(expected, actual)
        assertEquals(listOf("empty", "case-b", "case-a"), actual.map { it.caseId })
        assertEquals("FIRST (2)", actual.last().mostCommonSequentialStatus)
    }

    @Test
    fun indexedAggregationPreservesFirstSeenWinnerForEqualFrequencyTie() {
        val targets = listOf(target("case-a", DiagnosticExpectedClass.REACHABLE))
        val runs =
            listOf(
                run("case-a", status = "BETA", progressClass = DiagnosticProgressClass.STALLED),
                run("case-a", status = "ALPHA", progressClass = DiagnosticProgressClass.WORSENED)
            )

        val expected = legacyBuildPerCaseAggregates(targets, runs)
        val actual = DiagnosticStatsAggregator(policy).buildPerCaseAggregates(targets, runs)

        assertEquals(expected, actual)
        assertEquals("BETA (1)", actual.single().mostCommonSequentialStatus)
        assertEquals(DiagnosticProgressClass.STALLED, actual.single().mostCommonProgressClass)
    }

    @Test
    fun indexedAggregationMatchesLegacyForEmptyInputs() {
        assertEquals(
            legacyBuildPerCaseAggregates(emptyList(), emptyList()),
            DiagnosticStatsAggregator(policy).buildPerCaseAggregates(emptyList(), emptyList())
        )
    }

    private fun legacyBuildPerCaseAggregates(
        targetCases: List<DiagnosticTargetCase>,
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticPerCaseAggregate> {
        return targetCases.map { targetCase ->
            val oracleRuns =
                runResults.filter {
                    it.selectedCaseId == targetCase.id &&
                        it.runKind == DiagnosticRunKind.ORACLE_REACHABLE_CHECK
                }
            val sequentialRuns =
                runResults.filter {
                    it.selectedCaseId == targetCase.id &&
                        it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
                }
            val finiteFinalErrors = sequentialRuns.mapNotNull { it.finalError.takeIf(Double::isFinite) }
            val finiteInitialErrors = sequentialRuns.mapNotNull { it.initialError.takeIf(Double::isFinite) }
            val finiteImprovementRatios =
                sequentialRuns.mapNotNull { it.improvementRatio.takeIf(Double::isFinite) }
            val strictAccepted = sequentialRuns.count(DiagnosticRunResult::solverAccepted)

            DiagnosticPerCaseAggregate(
                caseId = targetCase.id,
                expectedClass = targetCase.expectedClass,
                sequentialRunCount = sequentialRuns.size,
                sequentialAcceptedCount = strictAccepted,
                sequentialRejectedCount = sequentialRuns.count { !it.solverAccepted },
                strictAcceptedCount = strictAccepted,
                nearSolvedCount =
                    sequentialRuns.count {
                        !it.solverAccepted && it.finalError.isFinite() &&
                            it.finalError <= policy.nearSuccessErrorMeters
                    },
                closeMissCount =
                    sequentialRuns.count {
                        !it.solverAccepted && it.finalError.isFinite() &&
                            it.finalError > policy.nearSuccessErrorMeters &&
                            it.finalError <= policy.closeMissErrorMeters
                    },
                farFailureCount =
                    sequentialRuns.count {
                        !it.solverAccepted && it.finalError.isFinite() &&
                            it.finalError > policy.closeMissErrorMeters
                    },
                oracleRunCount = oracleRuns.size,
                oracleAcceptedCount = oracleRuns.count(DiagnosticRunResult::solverAccepted),
                oracleRejectedCount = oracleRuns.count { !it.solverAccepted },
                averageSequentialError = finiteFinalErrors.averageOrZero(),
                maxSequentialError = finiteFinalErrors.maxOrNull() ?: Double.NaN,
                averageSequentialInitialError = finiteInitialErrors.averageOrZero(),
                averageSequentialImprovementRatio = finiteImprovementRatios.averageOrZero(),
                averageSequentialIterations = sequentialRuns.map { it.iterations }.averageOrZero(),
                averageIterationSaturationRatio =
                    sequentialRuns.mapNotNull {
                        it.iterationSaturationRatio.takeIf(Double::isFinite)
                    }.averageOrZero(),
                averageJointDeltaNorm =
                    sequentialRuns.mapNotNull { it.jointDeltaNorm.takeIf(Double::isFinite) }
                        .averageOrZero(),
                averageMaxSingleJointMovement =
                    sequentialRuns.mapNotNull { it.maxSingleJointMovement.takeIf(Double::isFinite) }
                        .averageOrZero(),
                averageJointLimitPressureRatio =
                    sequentialRuns.mapNotNull { it.jointLimitPressureRatio.takeIf(Double::isFinite) }
                        .averageOrZero(),
                mostCommonSequentialStatus = mostCommonString(sequentialRuns.map { it.status }),
                mostCommonProgressClass = mostCommon(sequentialRuns.map { it.progressClass }),
                mostCommonSeedDistanceBucket = mostCommon(sequentialRuns.map { it.seedDistanceBucket }),
                target = targetCase.target,
                sourceJointState = targetCase.sourceJointState,
                note = targetCase.note
            )
        }
    }

    private fun target(
        id: String,
        expectedClass: DiagnosticExpectedClass
    ) = DiagnosticTargetCase(
        id = id,
        expectedClass = expectedClass,
        target = Vec3(id.length.toDouble(), 0.0, 1.0),
        sourceJointState = RobotState(listOf(0.0)),
        note = "target-$id"
    )

    private fun run(
        caseId: String,
        kind: DiagnosticRunKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
        accepted: Boolean = false,
        status: String = "STATUS",
        finalError: Double = 0.02,
        initialError: Double = 0.1,
        improvementRatio: Double = 0.25,
        progressClass: DiagnosticProgressClass = DiagnosticProgressClass.STALLED,
        seedBucket: DiagnosticSeedDistanceBucket = DiagnosticSeedDistanceBucket.MEDIUM
    ) = DiagnosticRunResult(
        seed = 42,
        linkCount = 3,
        jointMode = DiagnosticJointMode.REVOLUTE_ONLY,
        runIndex = caseId.length,
        runKind = kind,
        selectedCaseId = caseId,
        transitionFromCaseId = null,
        transitionToCaseId = caseId,
        expectedClass = DiagnosticExpectedClass.REACHABLE,
        solverAccepted = accepted,
        status = status,
        detailCode = "DETAIL",
        finalError = finalError,
        iterations = caseId.length + 1,
        target = Vec3(1.0, 2.0, 3.0),
        sourceJointState = RobotState(listOf(0.0)),
        seedJointState = RobotState(listOf(0.0)),
        solutionJointValues = listOf(0.1),
        initialError = initialError,
        improvement = initialError - finalError,
        improvementRatio = improvementRatio,
        progressClass = progressClass,
        seedDistanceBucket = seedBucket,
        seedMinNormalizedLimitMargin = 0.2,
        seedLogConditionNumber = 1.0,
        iterationSaturationRatio = if (caseId == "case-b") Double.NaN else 0.2,
        jointDeltaNorm = if (caseId == "case-b") Double.POSITIVE_INFINITY else 0.1,
        maxSingleJointMovement = 0.1,
        normalizedJointTravelRms = 0.05,
        finalMinNormalizedLimitMargin = 0.1,
        backtrackingRetryCount = 0,
        solveDurationNanos = 100,
        nearLimitJointCount = if (caseId == "case-b") 1 else 0,
        nearLimitJointNames = emptyList(),
        jointLimitPressureRatio = if (caseId == "case-b") Double.NEGATIVE_INFINITY else 0.0,
        note = "run-$caseId"
    )

    private fun List<Number>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else sumOf(Number::toDouble) / size.toDouble()

    private fun <T> mostCommon(values: List<T>): T? =
        values.groupBy { it }.maxByOrNull { it.value.size }?.key

    private fun mostCommonString(values: List<String>): String =
        values.groupBy { it }.maxByOrNull { it.value.size }
            ?.let { "${it.key} (${it.value.size})" }
            ?: "N/A"
}
