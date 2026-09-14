package com.robotkinematicslab.mobile.diagnostics.benchmark.execution

import com.robotkinematicslab.mobile.diagnostics.benchmark.*
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.IKDiagnostics
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.service.KinematicsService
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

class Layer1DiagnosticExperiment(
    private val isCancellationRequested: () -> Boolean = { false }
) {

    private val benchmarkPlanner = DiagnosticBenchmarkPlanner()
    private val robotFactory = DiagnosticRobotFactory()
    private val targetFactory = DiagnosticTargetFactory()
    private val topologyAuditor = DiagnosticTopologyAuditor()
    private val fallbackSummaryBuilder = DiagnosticFallbackSummaryBuilder()
    private val verdictPolicy = DiagnosticVerdictPolicy()

    fun runExperiment(
        config: DiagnosticBenchmarkConfig,
        onProgress: ((DiagnosticProgressState) -> Unit)? = null
    ): Layer1DiagnosticReport {
        throwIfCancellationRequested()
        val statsAggregator = DiagnosticStatsAggregator(config.metricPolicy)
        val runMetricsCalculator = DiagnosticRunMetricsCalculator(config.metricPolicy)

        val storeFullRunHistory =
            config.performance.storeFullRunHistory

        val storePerCaseDetails =
            config.performance.storePerCaseDetails

        val storeTransitionDetails =
            config.performance.storeTransitionDetails

        val storeExtremeRunDetails =
            config.performance.storeExtremeRunDetails

        val exportRunHistoryToCsv =
            config.performance.exportRunHistoryToCsv &&
                    !config.performance.runHistoryCsvPath.isNullOrBlank()

        val exportCaseResultsToCsv =
            config.performance.exportCaseResultsToCsv &&
                    !config.performance.caseResultsCsvPath.isNullOrBlank()

        val keepRunHistoryInMemory =
            storeFullRunHistory &&
                    !exportRunHistoryToCsv

        DiagnosticAllocationTracker.reset()

        val safeSeedConfig =
            config.seeds.copy(
                seeds =
                    config.seeds.seeds
                        .distinct()
                        .ifEmpty { listOf(DEFAULT_SEED) }
            )

        val requestedReachableCount =
            config.sampling.reachableCount.coerceIn(0, MAX_TARGET_CASES)

        val requestedUnreachableCount =
            config.sampling.unreachableCount.coerceIn(
                0,
                MAX_TARGET_CASES - requestedReachableCount
            )

        val safeSamplingConfig =
            config.sampling.copy(
                reachableCount =
                    if (requestedReachableCount + requestedUnreachableCount == 0) {
                        1
                    } else {
                        requestedReachableCount
                    },

                unreachableCount =
                    requestedUnreachableCount,

                runCount =
                    config.sampling.runCount.coerceIn(1, 100_000),

                samplesPerLinkCount =
                    config.sampling.samplesPerLinkCount.coerceIn(1, 5000),

                unlimitedSampleCount =
                    config.sampling.unlimitedSampleCount.coerceIn(1, 1_000_000)
            )

        val defaultSolverConfig = DiagnosticSolverConfig()

        val safeSolverConfig =
            config.solver.copy(
                ikMaxIterations =
                    config.solver.ikMaxIterations.coerceIn(1, 10_000),

                ikTolerance =
                    config.solver.ikTolerance
                        .takeIf { it.isFinite() }
                        ?.coerceIn(1e-9, 1.0)
                        ?: defaultSolverConfig.ikTolerance,

                ikDamping =
                    config.solver.ikDamping
                        .takeIf { it.isFinite() }
                        ?.coerceIn(1e-9, 10.0)
                        ?: defaultSolverConfig.ikDamping,

                ikMaxStep =
                    config.solver.ikMaxStep
                        .takeIf { it.isFinite() }
                        ?.coerceIn(1e-6, 10.0)
                        ?: defaultSolverConfig.ikMaxStep
            )

        val safeTopologyConfig =
            config.topology.copy(
                stressLevel =
                    config.topology.stressLevel
                        .takeIf { it.isFinite() }
                        ?.coerceIn(0.0, 1.0)
                        ?: DiagnosticTopologyConfig().stressLevel
            )

        val effectiveConfig =
            config.copy(
                seeds = safeSeedConfig,
                sampling = safeSamplingConfig,
                solver = safeSolverConfig,
                topology = safeTopologyConfig
            )

        val benchmarkPlan =
            DiagnosticAllocationTracker.measure(
                label = "Layer1DiagnosticExperiment.build_plan"
            ) {
                benchmarkPlanner.buildPlan(
                    config = effectiveConfig
                )
            }

        val topologyModes =
            if (effectiveConfig.topology.runAllTopologies) {
                listOf(
                    DiagnosticJointMode.AUTO,
                    DiagnosticJointMode.REVOLUTE_ONLY,
                    DiagnosticJointMode.PRISMATIC_ONLY,
                    DiagnosticJointMode.MIXED
                )
            } else {
                listOf(effectiveConfig.topology.jointMode)
            }

        val estimatedOracleRuns =
            benchmarkPlan.linkCounts.size.toLong() *
                    topologyModes.size.toLong() *
                    safeSeedConfig.seeds.size.toLong() *
                    safeSamplingConfig.reachableCount.toLong()

        val estimatedTotalCalculations =
            benchmarkPlan.totalPlannedSequentialRuns +
                    estimatedOracleRuns

        val progressReporter =
            onProgress?.let {
                DiagnosticProgressReporter(
                    totalRuns = estimatedTotalCalculations
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    onProgress = it
                )
            }

        progressReporter?.emit(
            phase = DiagnosticProgressPhase.PLANNING,
            message =
                when {
                    keepRunHistoryInMemory ->
                        "Planning diagnostic benchmark with full in-memory run history."

                    exportRunHistoryToCsv ->
                        "Planning diagnostic benchmark with CSV run-history export."

                    else ->
                        "Planning lightweight diagnostic benchmark."
                }
        )

        val configuredKinematicsService =
            KinematicsService(
                ikConfig =
                    IKConfig(
                        maxIterations = safeSolverConfig.ikMaxIterations,
                        tolerance = safeSolverConfig.ikTolerance,
                        damping = safeSolverConfig.ikDamping,
                        maxStep = safeSolverConfig.ikMaxStep
                    )
            )

        val csvWriter =
            if (exportRunHistoryToCsv) {
                DiagnosticRunHistoryCsvWriter(
                    filePath = config.performance.runHistoryCsvPath!!
                )
            } else {
                null
            }

        val caseCsvWriter =
            if (exportCaseResultsToCsv) {
                DiagnosticCaseResultCsvWriter(
                    filePath = config.performance.caseResultsCsvPath!!
                )
            } else {
                null
            }

        csvWriter?.open()

        try {
            val allTargetCases =
                mutableListOf<DiagnosticTargetCase>()

            val allOneShotResults =
                mutableListOf<DiagnosticCaseResult>()

            val allRuns =
                mutableListOf<DiagnosticRunResult>()

            val allTopologyAuditRecords =
                mutableListOf<DiagnosticTopologyAuditRecord>()

            val sequentialRunsByLinkCount =
                linkedMapOf<Int, MutableList<DiagnosticRunResult>>()

            val sequentialRunsByTopology =
                linkedMapOf<DiagnosticJointMode, MutableList<DiagnosticRunResult>>()

            var finalRobot: RobotDefinition? =
                null

            var finalInitialState: RobotState? =
                null

            var finalExperimentName =
                "Layer 1 Diagnostic"

            val shouldPrefixCaseIds =
                safeSeedConfig.seeds.size > 1 ||
                        topologyModes.size > 1 ||
                        benchmarkPlan.linkCounts.size > 1

            safeSeedConfig.seeds.forEach { seed ->
                throwIfCancellationRequested()

                topologyModes.forEach { topologyMode ->
                    throwIfCancellationRequested()

                    benchmarkPlan.linkCounts.forEach { selectedLinkCount ->
                        throwIfCancellationRequested()

                        val targetRandom =
                            ScientificRandom(
                                ScientificRandomProtocol.deriveSeed(
                                    seed,
                                    TARGET_STREAM,
                                    "links=$selectedLinkCount",
                                    "topology=${topologyMode.name}"
                                )
                            )

                        val sequenceRandom =
                            ScientificRandom(
                                ScientificRandomProtocol.deriveSeed(
                                    seed,
                                    SEQUENCE_STREAM,
                                    "links=$selectedLinkCount",
                                    "topology=${topologyMode.name}"
                                )
                            )

                        val experimentName =
                            buildString {
                                append("Layer 1 ")

                                if (benchmarkPlan.isExperimental) {
                                    append("Experimental ")
                                }

                                append("${selectedLinkCount}R ")

                                append(
                                    when (topologyMode) {
                                        DiagnosticJointMode.AUTO -> "AUTO"
                                        DiagnosticJointMode.REVOLUTE_ONLY -> "REV"
                                        DiagnosticJointMode.PRISMATIC_ONLY -> "PRISM"
                                        DiagnosticJointMode.MIXED -> "MIXED"
                                    }
                                )

                                append(" Diagnostic")
                            }

                        finalExperimentName =
                            experimentName

                        progressReporter?.emit(
                            phase = DiagnosticProgressPhase.AUDITING_TOPOLOGY,
                            currentSeed = seed,
                            currentLinkCount = selectedLinkCount,
                            currentJointMode = topologyMode,
                            message = "Auditing topology for seed $seed, $selectedLinkCount links, $topologyMode."
                        )

                        val robot =
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.robot_factory"
                            ) {
                                robotFactory.buildSeedRobot(
                                    linkCount = selectedLinkCount,
                                    jointMode = topologyMode,
                                    stressLevel = effectiveConfig.topology.stressLevel
                                )
                            }

                        val topologyAudit =
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.topology_audit"
                            ) {
                                topologyAuditor.audit(robot)
                            }

                        val topologyAuditRecord =
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.topology_audit_record"
                            ) {
                                DiagnosticTopologyAuditRecord.fromAuditResult(
                                    seed = seed,
                                    linkCount = selectedLinkCount,
                                    jointMode = topologyMode,
                                    auditResult = topologyAudit
                                )
                            }

                        allTopologyAuditRecords +=
                            topologyAuditRecord

                        if (!topologyAudit.acceptedForBenchmark) {
                            return@forEach
                        }

                        finalRobot =
                            robot

                        val initialState =
                            RobotState(
                                jointValues =
                                    robot.joints.map {
                                        it.homeValue
                                    }
                            )

                        finalInitialState =
                            initialState

                        val maxReach =
                            RobotReachEnvelope.conservativeRadialUpperBound(robot)

                        val caseIdPrefix =
                            if (shouldPrefixCaseIds) {
                                buildString {
                                    append("S")
                                    append(seed)
                                    append("-")
                                    append(selectedLinkCount)
                                    append("L-")
                                    append(
                                        when (topologyMode) {
                                            DiagnosticJointMode.AUTO -> "AUTO"
                                            DiagnosticJointMode.REVOLUTE_ONLY -> "REV"
                                            DiagnosticJointMode.PRISMATIC_ONLY -> "PRISM"
                                            DiagnosticJointMode.MIXED -> "MIXED"
                                        }
                                    )
                                    append("-")
                                }
                            } else {
                                ""
                            }

                        progressReporter?.emit(
                            phase = DiagnosticProgressPhase.GENERATING_TARGETS,
                            currentSeed = seed,
                            currentLinkCount = selectedLinkCount,
                            currentJointMode = topologyMode,
                            message = "Generating target cases for seed $seed, $selectedLinkCount links, $topologyMode."
                        )

                        val targetCases =
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.target_generation"
                            ) {
                                targetFactory.buildTargetCases(
                                    kinematicsService = configuredKinematicsService,
                                    robot = robot,
                                    reachableCount = safeSamplingConfig.reachableCount,
                                    unreachableCount = safeSamplingConfig.unreachableCount,
                                    random = targetRandom,
                                    maxReach = maxReach
                                ).map { targetCase ->
                                    targetCase.copy(
                                        id = "$caseIdPrefix${targetCase.id}"
                                    )
                                }
                            }

                        if (storePerCaseDetails || exportCaseResultsToCsv) {
                            DiagnosticAllocationTracker.measure(
                                label =
                                    if (storePerCaseDetails) {
                                        "Layer1DiagnosticExperiment.one_shot_cases_store"
                                    } else {
                                        "Layer1DiagnosticExperiment.one_shot_cases_export_only"
                                    }
                            ) {
                                targetCases.forEach { targetCase ->
                                    throwIfCancellationRequested()
                                    if (storePerCaseDetails) {
                                        val caseResult =
                                            runSingleCase(
                                                experimentSeed = seed,
                                                selectedLinkCount = selectedLinkCount,
                                                topologyMode = topologyMode,
                                                kinematicsService = configuredKinematicsService,
                                                targetCase = targetCase,
                                                robot = robot,
                                                fallbackInitialState = initialState,
                                                runMetricsCalculator = runMetricsCalculator,
                                                config = effectiveConfig
                                            )

                                        caseCsvWriter?.writeCaseResult(
                                            result = caseResult
                                        )

                                        allOneShotResults +=
                                            caseResult
                                    } else {
                                        writeSingleCaseToCsvOnly(
                                            csvWriter = caseCsvWriter,
                                            experimentSeed = seed,
                                            selectedLinkCount = selectedLinkCount,
                                            topologyMode = topologyMode,
                                            kinematicsService = configuredKinematicsService,
                                            targetCase = targetCase,
                                            robot = robot,
                                            fallbackInitialState = initialState,
                                            runMetricsCalculator = runMetricsCalculator,
                                            config = effectiveConfig
                                        )
                                    }
                                }

                                caseCsvWriter?.flush()
                            }
                        } else {
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.one_shot_cases_lightweight"
                            ) {
                                runSingleCasesLightweight(
                                    kinematicsService = configuredKinematicsService,
                                    targetCases = targetCases,
                                    robot = robot,
                                    fallbackInitialState = initialState
                                )
                            }
                        }

                        if (keepRunHistoryInMemory) {
                            val oracleRuns =
                                DiagnosticAllocationTracker.measure(
                                    label = "Layer1DiagnosticExperiment.oracle_checks_memory"
                                ) {
                                    runOracleReachableChecks(
                                        experimentSeed = seed,
                                        selectedLinkCount = selectedLinkCount,
                                        topologyMode = topologyMode,
                                        progressReporter = progressReporter,
                                        kinematicsService = configuredKinematicsService,
                                        targetCases = targetCases,
                                        robot = robot,
                                        runMetricsCalculator = runMetricsCalculator,
                                        config = effectiveConfig
                                    )
                                }

                            val sequentialRuns =
                                DiagnosticAllocationTracker.measure(
                                    label = "Layer1DiagnosticExperiment.sequential_runs_memory"
                                ) {
                                    runSequentialStabilityExperiment(
                                        experimentSeed = seed,
                                        selectedLinkCount = selectedLinkCount,
                                        topologyMode = topologyMode,
                                        progressReporter = progressReporter,
                                        kinematicsService = configuredKinematicsService,
                                        targetCases = targetCases,
                                        robot = robot,
                                        initialState = initialState,
                                        runCount = benchmarkPlan.samplesPerLinkCount,
                                        random = sequenceRandom,
                                        runMetricsCalculator = runMetricsCalculator,
                                        config = effectiveConfig
                                    )
                                }

                            allRuns.addAll(oracleRuns)
                            allRuns.addAll(sequentialRuns)

                            sequentialRunsByLinkCount
                                .getOrPut(selectedLinkCount) {
                                    mutableListOf()
                                }
                                .addAll(sequentialRuns)

                            sequentialRunsByTopology
                                .getOrPut(topologyMode) {
                                    mutableListOf()
                                }
                                .addAll(sequentialRuns)
                        } else if (exportRunHistoryToCsv) {
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.oracle_checks_csv_only"
                            ) {
                                writeOracleReachableChecksToCsvOnly(
                                    csvWriter = csvWriter,
                                    experimentSeed = seed,
                                    selectedLinkCount = selectedLinkCount,
                                    topologyMode = topologyMode,
                                    progressReporter = progressReporter,
                                    kinematicsService = configuredKinematicsService,
                                    targetCases = targetCases,
                                    robot = robot,
                                    runMetricsCalculator = runMetricsCalculator,
                                    config = effectiveConfig
                                )
                            }

                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.sequential_runs_csv_only"
                            ) {
                                writeSequentialStabilityExperimentToCsvOnly(
                                    csvWriter = csvWriter,
                                    experimentSeed = seed,
                                    selectedLinkCount = selectedLinkCount,
                                    topologyMode = topologyMode,
                                    progressReporter = progressReporter,
                                    kinematicsService = configuredKinematicsService,
                                    targetCases = targetCases,
                                    robot = robot,
                                    initialState = initialState,
                                    runCount = benchmarkPlan.samplesPerLinkCount,
                                    random = sequenceRandom,
                                    runMetricsCalculator = runMetricsCalculator,
                                    config = effectiveConfig
                                )
                            }
                        } else {
                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.oracle_checks_lightweight"
                            ) {
                                runOracleReachableChecksLightweight(
                                    experimentSeed = seed,
                                    selectedLinkCount = selectedLinkCount,
                                    topologyMode = topologyMode,
                                    progressReporter = progressReporter,
                                    kinematicsService = configuredKinematicsService,
                                    targetCases = targetCases,
                                    robot = robot
                                )
                            }

                            DiagnosticAllocationTracker.measure(
                                label = "Layer1DiagnosticExperiment.sequential_runs_lightweight"
                            ) {
                                runSequentialStabilityExperimentLightweight(
                                    experimentSeed = seed,
                                    selectedLinkCount = selectedLinkCount,
                                    topologyMode = topologyMode,
                                    progressReporter = progressReporter,
                                    kinematicsService = configuredKinematicsService,
                                    targetCases = targetCases,
                                    robot = robot,
                                    initialState = initialState,
                                    runCount = benchmarkPlan.samplesPerLinkCount,
                                    random = sequenceRandom
                                )
                            }
                        }

                        allTargetCases +=
                            targetCases
                    }
                }
            }

            progressReporter?.emit(
                phase = DiagnosticProgressPhase.AGGREGATING,
                message =
                    when {
                        keepRunHistoryInMemory ->
                            "Aggregating diagnostic results."

                        exportRunHistoryToCsv ->
                            "Finalizing CSV-backed diagnostic benchmark."

                        else ->
                            "Finishing lightweight diagnostic benchmark."
                    }
            )

            val allSequentialRuns =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.filter_sequential_runs"
                ) {
                    if (keepRunHistoryInMemory) {
                        allRuns.filter {
                            it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
                        }
                    } else {
                        emptyList()
                    }
                }

            val perCaseAggregates =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.per_case_aggregation"
                ) {
                    if (storePerCaseDetails || keepRunHistoryInMemory) {
                        statsAggregator.buildPerCaseAggregates(
                            targetCases = allTargetCases,
                            runResults = allRuns
                        )
                    } else {
                        emptyList()
                    }
                }

            val transitionAggregates =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.transition_aggregation"
                ) {
                    if (storeTransitionDetails && keepRunHistoryInMemory) {
                        statsAggregator.buildTransitionAggregates(
                            runResults = allSequentialRuns
                        )
                    } else {
                        emptyList()
                    }
                }

            val extremeRuns =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.extreme_run_aggregation"
                ) {
                    if (storeExtremeRunDetails && keepRunHistoryInMemory) {
                        statsAggregator.buildExtremeRunSummaries(
                            runResults = allSequentialRuns
                        )
                    } else {
                        emptyList()
                    }
                }

            val linkCountAggregates =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.link_count_aggregation"
                ) {
                    if (keepRunHistoryInMemory) {
                        sequentialRunsByLinkCount.flatMap { entry ->
                            statsAggregator.buildLinkCountAggregates(
                                linkCount = entry.key,
                                runResults = entry.value
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val topologyAggregates =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.topology_aggregation"
                ) {
                    if (keepRunHistoryInMemory) {
                        sequentialRunsByTopology.flatMap { entry ->
                            statsAggregator.buildTopologyAggregates(
                                jointMode = entry.key,
                                runResults = entry.value
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val seedAggregates =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.seed_aggregation"
                ) {
                    if (keepRunHistoryInMemory) {
                        safeSeedConfig.seeds.flatMap { seed ->
                            statsAggregator.buildSeedAggregates(
                                seed = seed,
                                runResults =
                                    allRuns.filter {
                                        it.seed == seed
                                    }
                            )
                        }
                    } else {
                        emptyList()
                    }
                }

            val seedComparisonSummary =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.seed_comparison_summary"
                ) {
                    statsAggregator.buildSeedComparisonSummary(
                        seedAggregates = seedAggregates
                    )
                }

            val summary =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.summary_aggregation"
                ) {
                    statsAggregator.buildSummary(
                        targetCases = allTargetCases,
                        runResults = allRuns
                    )
                }

            val finalVerdict =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.final_verdict"
                ) {
                    verdictPolicy.evaluate(
                        benchmarkPlan = benchmarkPlan,
                        summary = summary
                    )
                }

            val statusDistribution =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.status_distribution"
                ) {
                    statsAggregator.buildStatusDistribution(
                        runResults = allRuns
                    )
                }

            val detailCodeDistribution =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.detail_code_distribution"
                ) {
                    statsAggregator.buildDetailCodeDistribution(
                        runResults = allRuns
                    )
                }

            val datasetAcceptanceSummary =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.dataset_acceptance_summary"
                ) {
                    statsAggregator.buildDatasetAcceptanceSummary(
                        runResults = allRuns
                    )
                }

            val fallbackSummary =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.fallback_summary"
                ) {
                    fallbackSummaryBuilder.build(allRuns)
                }

            val report =
                DiagnosticAllocationTracker.measure(
                    label = "Layer1DiagnosticExperiment.report_construction"
                ) {
                    Layer1DiagnosticReport(
                        experimentName =
                            when {
                                keepRunHistoryInMemory ->
                                    finalExperimentName

                                exportRunHistoryToCsv ->
                                    "$finalExperimentName CSV Run History Export"

                                else ->
                                    "$finalExperimentName Lightweight Performance Run"
                            },
                        config = effectiveConfig,
                        benchmarkPlan = benchmarkPlan,
                        finalVerdict = finalVerdict,
                        robot = finalRobot ?: robotFactory.buildSeedRobot(3),
                        initialState = finalInitialState ?: RobotState(emptyList()),
                        targetCases = allTargetCases,
                        cases = allOneShotResults,
                        runResults = allRuns,
                        perCaseAggregates = perCaseAggregates,
                        transitionAggregates = transitionAggregates,
                        extremeRuns = extremeRuns,
                        linkCountAggregates = linkCountAggregates,
                        seedAggregates = seedAggregates,
                        seedComparisonSummary = seedComparisonSummary,
                        topologyAggregates = topologyAggregates,
                        topologyAuditRecords = allTopologyAuditRecords,
                        statusDistribution = statusDistribution,
                        detailCodeDistribution = detailCodeDistribution,
                        datasetAcceptanceSummary = datasetAcceptanceSummary,
                        fallbackSummary = fallbackSummary,
                        summary = summary
                    )
                }

            progressReporter?.complete(
                message =
                    when {
                        keepRunHistoryInMemory ->
                            "Diagnostic benchmark completed."

                        exportRunHistoryToCsv ->
                            "Diagnostic benchmark completed. Full run history was exported to CSV."

                        else ->
                            "Lightweight diagnostic benchmark completed."
                    }
            )

            return report
        } finally {
            caseCsvWriter?.close()
            csvWriter?.close()
        }
    }

    private fun writeOracleReachableChecksToCsvOnly(
        csvWriter: DiagnosticRunHistoryCsvWriter?,
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ) {
        val writer =
            csvWriter ?: return

        val reachableCases =
            targetCases.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE &&
                        it.sourceJointState != null
            }

        reachableCases.forEachIndexed { index, targetCase ->
            throwIfCancellationRequested()

            val seedState = robot.homeState()

            val result =
                kinematicsService.computeIK(
                    robot = robot,
                    initialState = seedState,
                    target = targetCase.target
                )

            val solverAccepted =
                isAcceptedIkStatus(
                    status = result.status
                )

            val diagnostics =
                buildRunDiagnostics(
                    kinematicsService = kinematicsService,
                    robot = robot,
                    seedState = seedState,
                    solutionState = result.state,
                    target = targetCase.target,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    solverAccepted = solverAccepted,
                    solverDiagnostics = result.diagnostics,
                    runMetricsCalculator = runMetricsCalculator,
                    config = config
                )

            writer.writeRunValues(
                seed = experimentSeed,
                linkCount = selectedLinkCount,
                jointMode = topologyMode,
                runIndex = index + 1,
                runKind = DiagnosticRunKind.ORACLE_REACHABLE_CHECK,
                selectedCaseId = targetCase.id,
                transitionFromCaseId = null,
                transitionToCaseId = targetCase.id,
                expectedClass = targetCase.expectedClass,
                solverAccepted = solverAccepted,
                status = result.status.name,
                detailCode = result.detailCode.name,
                finalError = result.finalError,
                iterations = result.iterations,
                target = targetCase.target,
                sourceJointState = targetCase.sourceJointState,
                seedJointState = seedState,
                solutionJointValues = result.state.jointValues,
                initialError = diagnostics.initialError,
                improvement = diagnostics.improvement,
                improvementRatio = diagnostics.improvementRatio,
                progressClass = diagnostics.progressClass,
                seedDistanceBucket = diagnostics.seedDistanceBucket,
                seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
                seedLogConditionNumber = diagnostics.seedLogConditionNumber,
                iterationSaturationRatio = diagnostics.iterationSaturationRatio,
                jointDeltaNorm = diagnostics.jointDeltaNorm,
                maxSingleJointMovement = diagnostics.maxSingleJointMovement,
                normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
                finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
                backtrackingRetryCount = diagnostics.backtrackingRetryCount,
                solveDurationNanos = diagnostics.solveDurationNanos,
                nearLimitJointCount = diagnostics.nearLimitJointCount,
                nearLimitJointNames = diagnostics.nearLimitJointNames,
                jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
                note = "Reachability oracle: target is FK-proven, while IK starts independently from robot home."
            )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.ORACLE_CHECKS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Oracle check ${index + 1} / ${reachableCases.size}."
            )
        }

        writer.flush()
    }

    private fun writeSequentialStabilityExperimentToCsvOnly(
        csvWriter: DiagnosticRunHistoryCsvWriter?,
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        initialState: RobotState,
        runCount: Int,
        random: ScientificRandom,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ) {
        val writer =
            csvWriter ?: return

        if (targetCases.isEmpty()) {
            return
        }

        var currentState =
            initialState

        var currentCaseId: String? =
            null

        repeat(runCount) { index ->
            throwIfCancellationRequested()

            val selectedCase =
                targetCases[
                    random.nextInt(targetCases.size)
                ]

            val seedState =
                currentState

            val fromCaseId =
                currentCaseId

            val result =
                kinematicsService.computeIK(
                    robot = robot,
                    initialState = seedState,
                    target = selectedCase.target
                )

            val solverAccepted =
                isAcceptedIkStatus(
                    status = result.status
                )

            val diagnostics =
                buildRunDiagnostics(
                    kinematicsService = kinematicsService,
                    robot = robot,
                    seedState = seedState,
                    solutionState = result.state,
                    target = selectedCase.target,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    solverAccepted = solverAccepted,
                    solverDiagnostics = result.diagnostics,
                    runMetricsCalculator = runMetricsCalculator,
                    config = config
                )

            writer.writeRunValues(
                seed = experimentSeed,
                linkCount = selectedLinkCount,
                jointMode = topologyMode,
                runIndex = index + 1,
                runKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
                selectedCaseId = selectedCase.id,
                transitionFromCaseId = fromCaseId,
                transitionToCaseId = selectedCase.id,
                expectedClass = selectedCase.expectedClass,
                solverAccepted = solverAccepted,
                status = result.status.name,
                detailCode = result.detailCode.name,
                finalError = result.finalError,
                iterations = result.iterations,
                target = selectedCase.target,
                sourceJointState = selectedCase.sourceJointState,
                seedJointState = seedState,
                solutionJointValues = result.state.jointValues,
                initialError = diagnostics.initialError,
                improvement = diagnostics.improvement,
                improvementRatio = diagnostics.improvementRatio,
                progressClass = diagnostics.progressClass,
                seedDistanceBucket = diagnostics.seedDistanceBucket,
                seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
                seedLogConditionNumber = diagnostics.seedLogConditionNumber,
                iterationSaturationRatio = diagnostics.iterationSaturationRatio,
                jointDeltaNorm = diagnostics.jointDeltaNorm,
                maxSingleJointMovement = diagnostics.maxSingleJointMovement,
                normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
                finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
                backtrackingRetryCount = diagnostics.backtrackingRetryCount,
                solveDurationNanos = diagnostics.solveDurationNanos,
                nearLimitJointCount = diagnostics.nearLimitJointCount,
                nearLimitJointNames = diagnostics.nearLimitJointNames,
                jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
                note = selectedCase.note
            )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.SEQUENTIAL_RUNS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Sequential run ${index + 1} / $runCount."
            )

            if (solverAccepted) {
                currentState =
                    result.state

                currentCaseId =
                    selectedCase.id
            }
        }

        writer.flush()
    }

    private fun runSingleCasesLightweight(
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        fallbackInitialState: RobotState
    ) {
        targetCases.forEach { targetCase ->
            throwIfCancellationRequested()
            val seedState =
                if (targetCase.expectedClass == DiagnosticExpectedClass.REACHABLE) {
                    targetCase.sourceJointState ?: fallbackInitialState
                } else {
                    fallbackInitialState
                }

            kinematicsService.computeIK(
                robot = robot,
                initialState = seedState,
                target = targetCase.target
            )
        }
    }

    private fun runOracleReachableChecksLightweight(
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition
    ) {
        val reachableCases =
            targetCases.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE &&
                        it.sourceJointState != null
            }

        reachableCases.forEachIndexed { index, targetCase ->
            throwIfCancellationRequested()
            val seedState = robot.homeState()

            kinematicsService.computeIK(
                robot = robot,
                initialState = seedState,
                target = targetCase.target
            )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.ORACLE_CHECKS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Oracle check ${index + 1} / ${reachableCases.size}."
            )
        }
    }

    private fun runSequentialStabilityExperimentLightweight(
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        initialState: RobotState,
        runCount: Int,
        random: ScientificRandom
    ) {
        if (targetCases.isEmpty()) {
            return
        }

        var currentState =
            initialState

        repeat(runCount) { index ->
            throwIfCancellationRequested()
            val selectedCase =
                targetCases[
                    random.nextInt(targetCases.size)
                ]

            val result =
                kinematicsService.computeIK(
                    robot = robot,
                    initialState = currentState,
                    target = selectedCase.target
                )

            val solverAccepted =
                isAcceptedIkStatus(
                    status = result.status
                )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.SEQUENTIAL_RUNS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Sequential run ${index + 1} / $runCount."
            )

            if (solverAccepted) {
                currentState =
                    result.state
            }
        }
    }

    private fun runSingleCase(
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        kinematicsService: KinematicsService,
        targetCase: DiagnosticTargetCase,
        robot: RobotDefinition,
        fallbackInitialState: RobotState,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ): DiagnosticCaseResult {
        val seedState =
            if (targetCase.expectedClass == DiagnosticExpectedClass.REACHABLE) {
                targetCase.sourceJointState ?: fallbackInitialState
            } else {
                fallbackInitialState
            }

        val result =
            kinematicsService.computeIK(
                robot = robot,
                initialState = seedState,
                target = targetCase.target
            )

        val solverAccepted =
            isAcceptedIkStatus(
                status = result.status
            )

        val diagnostics =
            buildRunDiagnostics(
                kinematicsService = kinematicsService,
                robot = robot,
                seedState = seedState,
                solutionState = result.state,
                target = targetCase.target,
                finalError = result.finalError,
                iterations = result.iterations,
                solverAccepted = solverAccepted,
                solverDiagnostics = result.diagnostics,
                runMetricsCalculator = runMetricsCalculator,
                config = config
            )

        return DiagnosticCaseResult(
            seed = experimentSeed,
            linkCount = selectedLinkCount,
            jointMode = topologyMode,
            id = targetCase.id,
            expectedClass = targetCase.expectedClass,
            solverAccepted = solverAccepted,
            status = result.status.name,
            detailCode = result.detailCode.name,
            finalError = result.finalError,
            iterations = result.iterations,
            target = targetCase.target,
            sourceJointState = targetCase.sourceJointState,
            seedJointState = seedState,
            solutionJointValues = result.state.jointValues,
            initialError = diagnostics.initialError,
            improvement = diagnostics.improvement,
            improvementRatio = diagnostics.improvementRatio,
            progressClass = diagnostics.progressClass,
            seedDistanceBucket = diagnostics.seedDistanceBucket,
            seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
            seedLogConditionNumber = diagnostics.seedLogConditionNumber,
            iterationSaturationRatio = diagnostics.iterationSaturationRatio,
            jointDeltaNorm = diagnostics.jointDeltaNorm,
            maxSingleJointMovement = diagnostics.maxSingleJointMovement,
            normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
            finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
            backtrackingRetryCount = diagnostics.backtrackingRetryCount,
            solveDurationNanos = diagnostics.solveDurationNanos,
            nearLimitJointCount = diagnostics.nearLimitJointCount,
            nearLimitJointNames = diagnostics.nearLimitJointNames,
            jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
            note = targetCase.note
        )
    }

    private fun writeSingleCaseToCsvOnly(
        csvWriter: DiagnosticCaseResultCsvWriter?,
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        kinematicsService: KinematicsService,
        targetCase: DiagnosticTargetCase,
        robot: RobotDefinition,
        fallbackInitialState: RobotState,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ) {
        val writer =
            csvWriter ?: return

        val seedState =
            if (targetCase.expectedClass == DiagnosticExpectedClass.REACHABLE) {
                targetCase.sourceJointState ?: fallbackInitialState
            } else {
                fallbackInitialState
            }

        val result =
            kinematicsService.computeIK(
                robot = robot,
                initialState = seedState,
                target = targetCase.target
            )

        val solverAccepted =
            isAcceptedIkStatus(
                status = result.status
            )

        val diagnostics =
            buildRunDiagnostics(
                kinematicsService = kinematicsService,
                robot = robot,
                seedState = seedState,
                solutionState = result.state,
                target = targetCase.target,
                finalError = result.finalError,
                iterations = result.iterations,
                solverAccepted = solverAccepted,
                solverDiagnostics = result.diagnostics,
                runMetricsCalculator = runMetricsCalculator,
                config = config
            )

        writer.writeCaseResultValues(
            seed = experimentSeed,
            linkCount = selectedLinkCount,
            jointMode = topologyMode,
            id = targetCase.id,
            expectedClass = targetCase.expectedClass,
            solverAccepted = solverAccepted,
            status = result.status.name,
            detailCode = result.detailCode.name,
            finalError = result.finalError,
            iterations = result.iterations,
            target = targetCase.target,
            sourceJointState = targetCase.sourceJointState,
            seedJointState = seedState,
            solutionJointValues = result.state.jointValues,
            initialError = diagnostics.initialError,
            improvement = diagnostics.improvement,
            improvementRatio = diagnostics.improvementRatio,
            progressClass = diagnostics.progressClass,
            seedDistanceBucket = diagnostics.seedDistanceBucket,
            seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
            seedLogConditionNumber = diagnostics.seedLogConditionNumber,
            iterationSaturationRatio = diagnostics.iterationSaturationRatio,
            jointDeltaNorm = diagnostics.jointDeltaNorm,
            maxSingleJointMovement = diagnostics.maxSingleJointMovement,
            normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
            finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
            backtrackingRetryCount = diagnostics.backtrackingRetryCount,
            solveDurationNanos = diagnostics.solveDurationNanos,
            nearLimitJointCount = diagnostics.nearLimitJointCount,
            nearLimitJointNames = diagnostics.nearLimitJointNames,
            jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
            note = targetCase.note
        )
    }

    private fun runOracleReachableChecks(
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ): List<DiagnosticRunResult> {
        val reachableCases =
            targetCases.filter {
                it.expectedClass == DiagnosticExpectedClass.REACHABLE &&
                        it.sourceJointState != null
            }

        return reachableCases.mapIndexed { index, targetCase ->

            val seedState = robot.homeState()

            val result =
                kinematicsService.computeIK(
                    robot = robot,
                    initialState = seedState,
                    target = targetCase.target
                )

            val solverAccepted =
                isAcceptedIkStatus(
                    status = result.status
                )

            val diagnostics =
                buildRunDiagnostics(
                    kinematicsService = kinematicsService,
                    robot = robot,
                    seedState = seedState,
                    solutionState = result.state,
                    target = targetCase.target,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    solverAccepted = solverAccepted,
                    solverDiagnostics = result.diagnostics,
                    runMetricsCalculator = runMetricsCalculator,
                    config = config
                )

            val runResult =
                DiagnosticRunResult(
                    seed = experimentSeed,
                    linkCount = selectedLinkCount,
                    jointMode = topologyMode,
                    runIndex = index + 1,
                    runKind = DiagnosticRunKind.ORACLE_REACHABLE_CHECK,
                    selectedCaseId = targetCase.id,
                    transitionFromCaseId = null,
                    transitionToCaseId = targetCase.id,
                    expectedClass = targetCase.expectedClass,
                    solverAccepted = solverAccepted,
                    status = result.status.name,
                    detailCode = result.detailCode.name,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    target = targetCase.target,
                    sourceJointState = targetCase.sourceJointState,
                    seedJointState = seedState,
                    solutionJointValues = result.state.jointValues,
                    initialError = diagnostics.initialError,
                    improvement = diagnostics.improvement,
                    improvementRatio = diagnostics.improvementRatio,
                    progressClass = diagnostics.progressClass,
                    seedDistanceBucket = diagnostics.seedDistanceBucket,
                    seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
                    seedLogConditionNumber = diagnostics.seedLogConditionNumber,
                    iterationSaturationRatio = diagnostics.iterationSaturationRatio,
                    jointDeltaNorm = diagnostics.jointDeltaNorm,
                    maxSingleJointMovement = diagnostics.maxSingleJointMovement,
                    normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
                    finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
                    backtrackingRetryCount = diagnostics.backtrackingRetryCount,
                    solveDurationNanos = diagnostics.solveDurationNanos,
                    nearLimitJointCount = diagnostics.nearLimitJointCount,
                    nearLimitJointNames = diagnostics.nearLimitJointNames,
                    jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
                    note = "Reachability oracle: target is FK-proven, while IK starts independently from robot home."
                )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.ORACLE_CHECKS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Oracle check ${index + 1} / ${reachableCases.size}."
            )

            runResult
        }
    }

    private fun runSequentialStabilityExperiment(
        experimentSeed: Int,
        selectedLinkCount: Int,
        topologyMode: DiagnosticJointMode,
        progressReporter: DiagnosticProgressReporter?,
        kinematicsService: KinematicsService,
        targetCases: List<DiagnosticTargetCase>,
        robot: RobotDefinition,
        initialState: RobotState,
        runCount: Int,
        random: ScientificRandom,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ): List<DiagnosticRunResult> {
        if (targetCases.isEmpty()) {
            return emptyList()
        }

        val results =
            ArrayList<DiagnosticRunResult>(
                runCount
            )

        var currentState =
            initialState

        var currentCaseId: String? =
            null

        repeat(runCount) { index ->
            throwIfCancellationRequested()

            val selectedCase =
                targetCases[
                    random.nextInt(targetCases.size)
                ]

            val seedState =
                currentState

            val fromCaseId =
                currentCaseId

            val result =
                kinematicsService.computeIK(
                    robot = robot,
                    initialState = seedState,
                    target = selectedCase.target
                )

            val solverAccepted =
                isAcceptedIkStatus(
                    status = result.status
                )

            val diagnostics =
                buildRunDiagnostics(
                    kinematicsService = kinematicsService,
                    robot = robot,
                    seedState = seedState,
                    solutionState = result.state,
                    target = selectedCase.target,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    solverAccepted = solverAccepted,
                    solverDiagnostics = result.diagnostics,
                    runMetricsCalculator = runMetricsCalculator,
                    config = config
                )

            results +=
                DiagnosticRunResult(
                    seed = experimentSeed,
                    linkCount = selectedLinkCount,
                    jointMode = topologyMode,
                    runIndex = index + 1,
                    runKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
                    selectedCaseId = selectedCase.id,
                    transitionFromCaseId = fromCaseId,
                    transitionToCaseId = selectedCase.id,
                    expectedClass = selectedCase.expectedClass,
                    solverAccepted = solverAccepted,
                    status = result.status.name,
                    detailCode = result.detailCode.name,
                    finalError = result.finalError,
                    iterations = result.iterations,
                    target = selectedCase.target,
                    sourceJointState = selectedCase.sourceJointState,
                    seedJointState = seedState,
                    solutionJointValues = result.state.jointValues,
                    initialError = diagnostics.initialError,
                    improvement = diagnostics.improvement,
                    improvementRatio = diagnostics.improvementRatio,
                    progressClass = diagnostics.progressClass,
                    seedDistanceBucket = diagnostics.seedDistanceBucket,
                    seedMinNormalizedLimitMargin = diagnostics.seedMinNormalizedLimitMargin,
                    seedLogConditionNumber = diagnostics.seedLogConditionNumber,
                    iterationSaturationRatio = diagnostics.iterationSaturationRatio,
                    jointDeltaNorm = diagnostics.jointDeltaNorm,
                    maxSingleJointMovement = diagnostics.maxSingleJointMovement,
                    normalizedJointTravelRms = diagnostics.normalizedJointTravelRms,
                    finalMinNormalizedLimitMargin = diagnostics.finalMinNormalizedLimitMargin,
                    backtrackingRetryCount = diagnostics.backtrackingRetryCount,
                    solveDurationNanos = diagnostics.solveDurationNanos,
                    nearLimitJointCount = diagnostics.nearLimitJointCount,
                    nearLimitJointNames = diagnostics.nearLimitJointNames,
                    jointLimitPressureRatio = diagnostics.jointLimitPressureRatio,
                    note = selectedCase.note
                )

            progressReporter?.incrementAndEmit(
                phase = DiagnosticProgressPhase.SEQUENTIAL_RUNS,
                currentSeed = experimentSeed,
                currentLinkCount = selectedLinkCount,
                currentJointMode = topologyMode,
                message = "Sequential run ${index + 1} / $runCount."
            )

            if (solverAccepted) {
                currentState =
                    result.state

                currentCaseId =
                    selectedCase.id
            }
        }

        return results
    }

    private fun buildRunDiagnostics(
        kinematicsService: KinematicsService,
        robot: RobotDefinition,
        seedState: RobotState,
        solutionState: RobotState,
        target: Vec3,
        finalError: Double,
        iterations: Int,
        solverAccepted: Boolean,
        solverDiagnostics: IKDiagnostics,
        runMetricsCalculator: DiagnosticRunMetricsCalculator,
        config: DiagnosticBenchmarkConfig
    ): DiagnosticRunMetrics {
        val seedFk =
            kinematicsService.computeFK(
                robot = robot,
                state = seedState
            )

        return runMetricsCalculator.calculate(
            robot = robot,
            seedState = seedState,
            solutionState = solutionState,
            initialError = runMetricsCalculator.calculateInitialError(seedFk, target),
            finalError = finalError,
            iterations = iterations,
            maxIterations = config.solver.ikMaxIterations,
            solverAccepted = solverAccepted,
            solverDiagnostics = solverDiagnostics,
            metricPolicy = config.metricPolicy
        )
    }

    private fun isAcceptedIkStatus(
        status: IKStatus
    ): Boolean {
        return status == IKStatus.SUCCESS ||
                status == IKStatus.SUCCESS_WITH_WARNING
    }

    private fun RobotDefinition.homeState(): RobotState {
        return RobotState(joints.map { it.homeValue })
    }

    private fun throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw CancellationException("Diagnostic experiment cancelled by the user.")
        }
    }

    companion object {
        private const val DEFAULT_SEED = 42
        private const val MAX_TARGET_CASES = 200
        private const val TARGET_STREAM = "diagnostic-targets"
        private const val SEQUENCE_STREAM = "diagnostic-sequence"
    }
}
