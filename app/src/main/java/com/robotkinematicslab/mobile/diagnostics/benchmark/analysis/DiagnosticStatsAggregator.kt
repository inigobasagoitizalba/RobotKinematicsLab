package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticTargetCase
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticDatasetAcceptanceSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticDetailCodeDistributionItem
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticExtremeRunSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticPerCaseAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticStatusDistributionItem
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.DiagnosticTransitionAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.LinkCountAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.SeedAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.SeedComparisonSummary
import com.robotkinematicslab.mobile.diagnostics.benchmark.report.TopologyAggregate
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticRunResult

class DiagnosticStatsAggregator(
    private val metricPolicy: DiagnosticMetricPolicy = DiagnosticMetricPolicy()
) {

    fun buildStatusDistribution(
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticStatusDistributionItem> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        val total =
            sequentialRuns.size

        if (total == 0) {
            return emptyList()
        }

        return sequentialRuns
            .groupBy {
                it.status
            }
            .map { (status, runs) ->
                DiagnosticStatusDistributionItem(
                    status = status,
                    count = runs.size,
                    ratio = runs.size.toDouble() / total.toDouble()
                )
            }
            .sortedByDescending {
                it.count
            }
    }

    fun buildDetailCodeDistribution(
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticDetailCodeDistributionItem> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        val total =
            sequentialRuns.size

        if (total == 0) {
            return emptyList()
        }

        return sequentialRuns
            .groupBy {
                it.detailCode
            }
            .map { (detailCode, runs) ->
                DiagnosticDetailCodeDistributionItem(
                    detailCode = detailCode,
                    count = runs.size,
                    ratio = runs.size.toDouble() / total.toDouble()
                )
            }
            .sortedByDescending {
                it.count
            }
    }

    fun buildDatasetAcceptanceSummary(
        runResults: List<DiagnosticRunResult>
    ): DiagnosticDatasetAcceptanceSummary {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        val reachableRuns =
            sequentialRuns.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }

        val unreachableRuns =
            sequentialRuns.filter {
                it.expectedClass == DiagnosticExpectedClass.UNREACHABLE
            }

        val reachableAccepted =
            reachableRuns.count {
                it.solverAccepted
            }

        val reachableRejected =
            reachableRuns.count {
                !it.solverAccepted
            }

        val unreachableAccepted =
            unreachableRuns.count {
                it.solverAccepted
            }

        val unreachableRejected =
            unreachableRuns.count {
                !it.solverAccepted
            }

        return DiagnosticDatasetAcceptanceSummary(
            reachableRunCount = reachableRuns.size,
            reachableAcceptedCount = reachableAccepted,
            reachableRejectedCount = reachableRejected,
            reachableAcceptanceRate =
                if (reachableRuns.isNotEmpty()) {
                    reachableAccepted.toDouble() / reachableRuns.size.toDouble()
                } else {
                    0.0
                },
            unreachableRunCount = unreachableRuns.size,
            unreachableAcceptedCount = unreachableAccepted,
            unreachableRejectedCount = unreachableRejected,
            falseAcceptCount = unreachableAccepted,
            falseRejectCount = reachableRejected,
            unreachableRejectionRate =
                if (unreachableRuns.isNotEmpty()) {
                    unreachableRejected.toDouble() / unreachableRuns.size.toDouble()
                } else {
                    0.0
                }
        )
    }

    fun buildPerCaseAggregates(
        targetCases: List<DiagnosticTargetCase>,
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticPerCaseAggregate> {
        val runsByCaseId =
            LinkedHashMap<String, PerCaseRunBuckets>()

        runResults.forEach { run ->
            val bucket =
                runsByCaseId.getOrPut(run.selectedCaseId) {
                    PerCaseRunBuckets()
                }

            when (run.runKind) {
                DiagnosticRunKind.ORACLE_REACHABLE_CHECK ->
                    bucket.oracleRuns += run

                DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK ->
                    bucket.sequentialRuns += run
            }
        }

        return targetCases.map { targetCase ->
            val bucket =
                runsByCaseId[targetCase.id]

            val oracleRunsForCase =
                bucket?.oracleRuns.orEmpty()

            val sequentialRunsForCase =
                bucket?.sequentialRuns.orEmpty()

            val finiteSequentialErrors =
                sequentialRunsForCase.mapNotNull {
                    it.finalError.takeIf { value ->
                        value.isFinite()
                    }
                }

            val finiteInitialErrors =
                sequentialRunsForCase.mapNotNull {
                    it.initialError.takeIf { value ->
                        value.isFinite()
                    }
                }

            val finiteImprovementRatios =
                sequentialRunsForCase.mapNotNull {
                    it.improvementRatio.takeIf { value ->
                        value.isFinite()
                    }
                }

            val averageSequentialIterations =
                if (sequentialRunsForCase.isNotEmpty()) {
                    sequentialRunsForCase.map {
                        it.iterations
                    }.average()
                } else {
                    0.0
                }

            val strictAcceptedCount =
                sequentialRunsForCase.count {
                    it.solverAccepted
                }

            val nearSolvedCount =
                countNearSolved(sequentialRunsForCase)

            val closeMissCount =
                countCloseMiss(sequentialRunsForCase)

            val farFailureCount =
                countFarFailure(sequentialRunsForCase)

            DiagnosticPerCaseAggregate(
                caseId = targetCase.id,
                expectedClass = targetCase.expectedClass,
                sequentialRunCount = sequentialRunsForCase.size,
                sequentialAcceptedCount = strictAcceptedCount,
                sequentialRejectedCount = sequentialRunsForCase.count { !it.solverAccepted },
                strictAcceptedCount = strictAcceptedCount,
                nearSolvedCount = nearSolvedCount,
                closeMissCount = closeMissCount,
                farFailureCount = farFailureCount,
                oracleRunCount = oracleRunsForCase.size,
                oracleAcceptedCount = oracleRunsForCase.count { it.solverAccepted },
                oracleRejectedCount = oracleRunsForCase.count { !it.solverAccepted },
                averageSequentialError = finiteSequentialErrors.averageOrZero(),
                maxSequentialError = finiteSequentialErrors.maxOrNull() ?: Double.NaN,
                averageSequentialInitialError = finiteInitialErrors.averageOrZero(),
                averageSequentialImprovementRatio = finiteImprovementRatios.averageOrZero(),
                averageSequentialIterations = averageSequentialIterations,
                averageIterationSaturationRatio =
                    sequentialRunsForCase.mapNotNull {
                        it.iterationSaturationRatio.takeIf { value ->
                            value.isFinite()
                        }
                    }.averageOrZero(),
                averageJointDeltaNorm =
                    sequentialRunsForCase.mapNotNull {
                        it.jointDeltaNorm.takeIf { value ->
                            value.isFinite()
                        }
                    }.averageOrZero(),
                averageMaxSingleJointMovement =
                    sequentialRunsForCase.mapNotNull {
                        it.maxSingleJointMovement.takeIf { value ->
                            value.isFinite()
                        }
                    }.averageOrZero(),
                averageJointLimitPressureRatio =
                    sequentialRunsForCase.mapNotNull {
                        it.jointLimitPressureRatio.takeIf { value ->
                            value.isFinite()
                        }
                    }.averageOrZero(),
                mostCommonSequentialStatus =
                    mostCommonString(
                        sequentialRunsForCase.map {
                            it.status
                        }
                    ),
                mostCommonProgressClass =
                    mostCommonEnum(
                        sequentialRunsForCase.map {
                            it.progressClass
                        }
                    ),
                mostCommonSeedDistanceBucket =
                    mostCommonEnum(
                        sequentialRunsForCase.map {
                            it.seedDistanceBucket
                        }
                    ),
                target = targetCase.target,
                sourceJointState = targetCase.sourceJointState,
                note = targetCase.note
            )
        }
    }

    private data class PerCaseRunBuckets(
        val oracleRuns: MutableList<DiagnosticRunResult> = mutableListOf(),
        val sequentialRuns: MutableList<DiagnosticRunResult> = mutableListOf()
    )

    fun buildTransitionAggregates(
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticTransitionAggregate> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        return sequentialRuns
            .filter {
                it.transitionFromCaseId != null
            }
            .groupBy {
                "${it.transitionFromCaseId} -> ${it.transitionToCaseId}"
            }
            .map { (_, runs) ->
                val first =
                    runs.first()

                val finiteFinalErrors =
                    runs.mapNotNull {
                        it.finalError.takeIf { value ->
                            value.isFinite()
                        }
                    }

                val finiteInitialErrors =
                    runs.mapNotNull {
                        it.initialError.takeIf { value ->
                            value.isFinite()
                        }
                    }

                val finiteImprovementRatios =
                    runs.mapNotNull {
                        it.improvementRatio.takeIf { value ->
                            value.isFinite()
                        }
                    }

                DiagnosticTransitionAggregate(
                    fromCaseId = first.transitionFromCaseId ?: "START",
                    toCaseId = first.transitionToCaseId,
                    runCount = runs.size,
                    acceptedCount = runs.count { it.solverAccepted },
                    rejectedCount = runs.count { !it.solverAccepted },
                    nearSolvedCount = countNearSolved(runs),
                    closeMissCount = countCloseMiss(runs),
                    farFailureCount = countFarFailure(runs),
                    averageFinalError = finiteFinalErrors.averageOrZero(),
                    maxFinalError = finiteFinalErrors.maxOrNull() ?: Double.NaN,
                    averageInitialError = finiteInitialErrors.averageOrZero(),
                    averageImprovementRatio = finiteImprovementRatios.averageOrZero(),
                    averageIterations =
                        if (runs.isNotEmpty()) {
                            runs.map {
                                it.iterations
                            }.average()
                        } else {
                            0.0
                        },
                    mostCommonStatus =
                        mostCommonString(
                            runs.map {
                                it.status
                            }
                        )
                )
            }
            .sortedWith(
                compareByDescending<DiagnosticTransitionAggregate> {
                    it.farFailureCount
                }
                    .thenByDescending {
                        it.maxFinalError
                    }
                    .thenByDescending {
                        it.runCount
                    }
            )
    }

    fun buildExtremeRunSummaries(
        runResults: List<DiagnosticRunResult>
    ): List<DiagnosticExtremeRunSummary> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        if (sequentialRuns.isEmpty()) {
            return emptyList()
        }

        val reachableRuns =
            sequentialRuns.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }

        val items =
            mutableListOf<DiagnosticExtremeRunSummary>()

        reachableRuns
            .filter {
                it.finalError.isFinite()
            }
            .minByOrNull {
                it.finalError
            }
            ?.let {
                items += it.toExtremeSummary("Best reachable final error")
            }

        reachableRuns
            .filter {
                it.finalError.isFinite()
            }
            .maxByOrNull {
                it.finalError
            }
            ?.let {
                items += it.toExtremeSummary("Worst reachable final error")
            }

        sequentialRuns
            .filter {
                it.finalError.isFinite()
            }
            .maxByOrNull {
                it.finalError
            }
            ?.let {
                items += it.toExtremeSummary("Largest final error overall")
            }

        sequentialRuns
            .maxByOrNull {
                it.iterations
            }
            ?.let {
                items += it.toExtremeSummary("Highest iteration count")
            }

        sequentialRuns
            .filter {
                it.improvementRatio.isFinite()
            }
            .minByOrNull {
                it.improvementRatio
            }
            ?.let {
                items += it.toExtremeSummary("Worst improvement ratio")
            }

        sequentialRuns
            .filter {
                it.jointLimitPressureRatio.isFinite()
            }
            .maxByOrNull {
                it.jointLimitPressureRatio
            }
            ?.let {
                items += it.toExtremeSummary("Highest joint-limit pressure")
            }

        return items.distinctBy {
            "${it.label}-${it.runIndex}-${it.caseId}"
        }
    }

    fun buildLinkCountAggregates(
        linkCount: Int,
        runResults: List<DiagnosticRunResult>
    ): List<LinkCountAggregate> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        if (sequentialRuns.isEmpty()) {
            return emptyList()
        }

        val finiteErrors =
            sequentialRuns.mapNotNull {
                it.finalError.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteImprovementRatios =
            sequentialRuns.mapNotNull {
                it.improvementRatio.takeIf { value ->
                    value.isFinite()
                }
            }

        val strictAccepted =
            sequentialRuns.count {
                it.solverAccepted
            }

        return listOf(
            LinkCountAggregate(
                linkCount = linkCount,
                runCount = sequentialRuns.size,
                strictAcceptedCount = strictAccepted,
                nearSolvedCount = countNearSolved(sequentialRuns),
                closeMissCount = countCloseMiss(sequentialRuns),
                farFailureCount = countFarFailure(sequentialRuns),
                strictAcceptanceRate =
                    strictAccepted.toDouble() /
                            sequentialRuns.size.toDouble(),
                averageFinalError = finiteErrors.averageOrZero(),
                maxFinalError = finiteErrors.maxOrNull() ?: Double.NaN,
                averageIterations =
                    sequentialRuns.map {
                        it.iterations
                    }.average(),
                averageImprovementRatio =
                    finiteImprovementRatios.averageOrZero()
            )
        )
    }

    fun buildSeedAggregates(
        seed: Int,
        runResults: List<DiagnosticRunResult>
    ): List<SeedAggregate> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        val finiteErrors =
            sequentialRuns.mapNotNull {
                it.finalError.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteImprovementRatios =
            sequentialRuns.mapNotNull {
                it.improvementRatio.takeIf { value ->
                    value.isFinite()
                }
            }

        val strictAccepted =
            sequentialRuns.count {
                it.solverAccepted
            }

        return listOf(
            SeedAggregate(
                seed = seed,
                runCount = sequentialRuns.size,
                strictAcceptedCount = strictAccepted,
                nearSolvedCount = countNearSolved(sequentialRuns),
                closeMissCount = countCloseMiss(sequentialRuns),
                farFailureCount = countFarFailure(sequentialRuns),
                strictAcceptanceRate =
                    if (sequentialRuns.isNotEmpty()) {
                        strictAccepted.toDouble() /
                                sequentialRuns.size.toDouble()
                    } else {
                        0.0
                    },
                averageFinalError = finiteErrors.averageOrZero(),
                maxFinalError = finiteErrors.maxOrNull() ?: 0.0,
                averageIterations =
                    if (sequentialRuns.isNotEmpty()) {
                        sequentialRuns.map {
                            it.iterations
                        }.average()
                    } else {
                        0.0
                    },
                averageImprovementRatio =
                    finiteImprovementRatios.averageOrZero()
            )
        )
    }

    fun buildSeedComparisonSummary(
        seedAggregates: List<SeedAggregate>
    ): SeedComparisonSummary {
        if (seedAggregates.isEmpty()) {
            return SeedComparisonSummary(
                seedCount = 0,
                bestSeed = null,
                worstSeed = null,
                averageStrictAcceptanceRate = 0.0,
                strictAcceptanceRateVariance = 0.0,
                strictAcceptanceRateSpread = 0.0,
                seedSensitivityLabel = "N/A"
            )
        }

        val best =
            seedAggregates.maxByOrNull {
                it.strictAcceptanceRate
            }

        val worst =
            seedAggregates.minByOrNull {
                it.strictAcceptanceRate
            }

        val average =
            seedAggregates.map {
                it.strictAcceptanceRate
            }.average()

        val variance =
            seedAggregates.map {
                val delta =
                    it.strictAcceptanceRate - average

                delta * delta
            }.average()

        val spread =
            (best?.strictAcceptanceRate ?: 0.0) -
                    (worst?.strictAcceptanceRate ?: 0.0)

        val label =
            when {
                seedAggregates.size <= 1 ->
                    "Single seed only"

                spread < 0.05 ->
                    "LOW"

                spread < 0.15 ->
                    "MEDIUM"

                else ->
                    "HIGH"
            }

        return SeedComparisonSummary(
            seedCount = seedAggregates.size,
            bestSeed = best?.seed,
            worstSeed = worst?.seed,
            averageStrictAcceptanceRate = average,
            strictAcceptanceRateVariance = variance,
            strictAcceptanceRateSpread = spread,
            seedSensitivityLabel = label
        )
    }

    fun buildTopologyAggregates(
        jointMode: DiagnosticJointMode,
        runResults: List<DiagnosticRunResult>
    ): List<TopologyAggregate> {
        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        if (sequentialRuns.isEmpty()) {
            return emptyList()
        }

        val finiteErrors =
            sequentialRuns.mapNotNull {
                it.finalError.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteImprovementRatios =
            sequentialRuns.mapNotNull {
                it.improvementRatio.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteJointLimitPressureRatios =
            sequentialRuns.mapNotNull {
                it.jointLimitPressureRatio.takeIf { value ->
                    value.isFinite()
                }
            }

        val strictAccepted =
            sequentialRuns.count {
                it.solverAccepted
            }

        return listOf(
            TopologyAggregate(
                jointMode = jointMode,
                runCount = sequentialRuns.size,
                strictAcceptedCount = strictAccepted,
                nearSolvedCount = countNearSolved(sequentialRuns),
                closeMissCount = countCloseMiss(sequentialRuns),
                farFailureCount = countFarFailure(sequentialRuns),
                strictAcceptanceRate =
                    if (sequentialRuns.isNotEmpty()) {
                        strictAccepted.toDouble() /
                                sequentialRuns.size.toDouble()
                    } else {
                        0.0
                    },
                averageFinalError = finiteErrors.averageOrZero(),
                maxFinalError = finiteErrors.maxOrNull() ?: 0.0,
                averageIterations =
                    if (sequentialRuns.isNotEmpty()) {
                        sequentialRuns.map {
                            it.iterations
                        }.average()
                    } else {
                        0.0
                    },
                averageImprovementRatio =
                    finiteImprovementRatios.averageOrZero(),
                averageJointLimitPressureRatio =
                    finiteJointLimitPressureRatios.averageOrZero(),
                runsWithNearJointLimit =
                    sequentialRuns.count {
                        it.nearLimitJointCount > 0
                    },
                fullJointLimitPressureCount =
                    sequentialRuns.count {
                        it.jointLimitPressureRatio.isFinite() &&
                                it.jointLimitPressureRatio >= 0.999
                    }
            )
        )
    }

    fun buildSummary(
        targetCases: List<DiagnosticTargetCase>,
        runResults: List<DiagnosticRunResult>
    ): DiagnosticSummary {
        val totalCases =
            targetCases.size

        val totalRuns =
            runResults.size

        val expectedReachableCount =
            targetCases.count {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }

        val expectedUnreachableCount =
            targetCases.count {
                it.expectedClass == DiagnosticExpectedClass.UNREACHABLE
            }

        val oracleRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.ORACLE_REACHABLE_CHECK
            }

        val sequentialRuns =
            runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        val sequentialReachableRuns =
            sequentialRuns.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE
            }

        val sequentialUnreachableRuns =
            sequentialRuns.filter {
                it.expectedClass == DiagnosticExpectedClass.UNREACHABLE
            }

        val finiteSequentialInitialErrors =
            sequentialRuns.mapNotNull {
                it.initialError.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteSequentialErrors =
            sequentialRuns.mapNotNull {
                it.finalError.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteSequentialImprovements =
            sequentialRuns.mapNotNull {
                it.improvement.takeIf { value ->
                    value.isFinite()
                }
            }

        val finiteSequentialImprovementRatios =
            sequentialRuns.mapNotNull {
                it.improvementRatio.takeIf { value ->
                    value.isFinite()
                }
            }

        val averageSequentialIterations =
            if (sequentialRuns.isNotEmpty()) {
                sequentialRuns.map {
                    it.iterations
                }.average()
            } else {
                0.0
            }

        return DiagnosticSummary(
            totalCases = totalCases,
            totalRuns = totalRuns,
            expectedReachableCount = expectedReachableCount,
            expectedUnreachableCount = expectedUnreachableCount,
            oracleReachableRuns = oracleRuns.size,
            oracleReachableAccepted = oracleRuns.count { it.solverAccepted },
            oracleReachableRejected = oracleRuns.count { !it.solverAccepted },
            sequentialRuns = sequentialRuns.size,
            sequentialAcceptedCount = sequentialRuns.count { it.solverAccepted },
            sequentialRejectedCount = sequentialRuns.count { !it.solverAccepted },
            sequentialReachableRuns = sequentialReachableRuns.size,
            sequentialReachableAccepted = sequentialReachableRuns.count { it.solverAccepted },
            sequentialReachableRejected = sequentialReachableRuns.count { !it.solverAccepted },
            sequentialUnreachableRuns = sequentialUnreachableRuns.size,
            sequentialUnreachableAccepted = sequentialUnreachableRuns.count { it.solverAccepted },
            sequentialUnreachableRejected = sequentialUnreachableRuns.count { !it.solverAccepted },
            averageSequentialInitialError = finiteSequentialInitialErrors.averageOrZero(),
            averageSequentialError = finiteSequentialErrors.averageOrZero(),
            maxSequentialError = finiteSequentialErrors.maxOrNull() ?: Double.NaN,
            averageSequentialImprovement = finiteSequentialImprovements.averageOrZero(),
            averageSequentialImprovementRatio = finiteSequentialImprovementRatios.averageOrZero(),
            averageSequentialIterations = averageSequentialIterations,
            averageIterationSaturationRatio =
                sequentialRuns.mapNotNull {
                    it.iterationSaturationRatio.takeIf { value ->
                        value.isFinite()
                    }
                }.averageOrZero(),
            averageJointDeltaNorm =
                sequentialRuns.mapNotNull {
                    it.jointDeltaNorm.takeIf { value ->
                        value.isFinite()
                    }
                }.averageOrZero(),
            averageMaxSingleJointMovement =
                sequentialRuns.mapNotNull {
                    it.maxSingleJointMovement.takeIf { value ->
                        value.isFinite()
                    }
                }.averageOrZero(),
            averageJointLimitPressureRatio =
                sequentialRuns.mapNotNull {
                    it.jointLimitPressureRatio.takeIf { value ->
                        value.isFinite()
                    }
                }.averageOrZero(),
            runsWithNearJointLimit =
                sequentialRuns.count {
                    it.nearLimitJointCount > 0
                },
            solvedCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.SOLVED
                },
            nearSolvedCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.NEAR_SOLVED
                },
            improvedButNotEnoughCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.IMPROVED_BUT_NOT_ENOUGH
                },
            stalledCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.STALLED
                },
            worsenedCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.WORSENED
                },
            invalidNumericalCount =
                sequentialRuns.count {
                    it.progressClass == DiagnosticProgressClass.INVALID_NUMERICAL
                },
            easySeedRuns =
                sequentialRuns.count {
                    it.seedDistanceBucket == DiagnosticSeedDistanceBucket.EASY
                },
            mediumSeedRuns =
                sequentialRuns.count {
                    it.seedDistanceBucket == DiagnosticSeedDistanceBucket.MEDIUM
                },
            hardSeedRuns =
                sequentialRuns.count {
                    it.seedDistanceBucket == DiagnosticSeedDistanceBucket.HARD
                },
            extremeSeedRuns =
                sequentialRuns.count {
                    it.seedDistanceBucket == DiagnosticSeedDistanceBucket.EXTREME
                },
            unknownSeedRuns =
                sequentialRuns.count {
                    it.seedDistanceBucket == DiagnosticSeedDistanceBucket.UNKNOWN
                }
        )
    }

    private fun countNearSolved(
        runs: List<DiagnosticRunResult>
    ): Int {
        return runs.count {
            !it.solverAccepted &&
                    it.finalError.isFinite() &&
                    it.finalError <= metricPolicy.nearSuccessErrorMeters
        }
    }

    private fun countCloseMiss(
        runs: List<DiagnosticRunResult>
    ): Int {
        return runs.count {
            !it.solverAccepted &&
                    it.finalError.isFinite() &&
                    it.finalError > metricPolicy.nearSuccessErrorMeters &&
                    it.finalError <= metricPolicy.closeMissErrorMeters
        }
    }

    private fun countFarFailure(
        runs: List<DiagnosticRunResult>
    ): Int {
        return runs.count {
            !it.solverAccepted &&
                    it.finalError.isFinite() &&
                    it.finalError > metricPolicy.closeMissErrorMeters
        }
    }

    private fun DiagnosticRunResult.toExtremeSummary(
        label: String
    ): DiagnosticExtremeRunSummary {
        return DiagnosticExtremeRunSummary(
            label = label,
            runIndex = runIndex,
            caseId = selectedCaseId,
            status = status,
            detailCode = detailCode,
            expectedClass = expectedClass,
            finalError = finalError,
            initialError = initialError,
            improvementRatio = improvementRatio,
            iterations = iterations,
            progressClass = progressClass,
            transitionFromCaseId = transitionFromCaseId,
            transitionToCaseId = transitionToCaseId
        )
    }

    private fun <T : Enum<T>> mostCommonEnum(
        values: List<T>
    ): T? {
        return values
            .groupBy {
                it
            }
            .maxByOrNull {
                it.value.size
            }
            ?.key
    }

    private fun mostCommonString(
        values: List<String>
    ): String {
        return values
            .groupBy {
                it
            }
            .maxByOrNull {
                it.value.size
            }
            ?.let {
                "${it.key} (${it.value.size})"
            }
            ?: "N/A"
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isEmpty()) {
            0.0
        } else {
            average()
        }
    }
}
