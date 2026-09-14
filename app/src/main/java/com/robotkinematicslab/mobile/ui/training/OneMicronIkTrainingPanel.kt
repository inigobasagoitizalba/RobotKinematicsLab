package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.ik.ONE_MICRON_METERS
import com.robotkinematicslab.mobile.ml.ik.OneMicronExperimentPlanner
import com.robotkinematicslab.mobile.ml.ik.OneMicronExperimentRequest
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureProfile
import com.robotkinematicslab.mobile.ml.ik.OneMicronFeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkStorageRepository
import com.robotkinematicslab.mobile.ml.ik.OneMicronReliabilityCalculator
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkTrainingEngine
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkTrainingCancelledException
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkTrainingProgress
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkTrainingResult
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkTrainingPhase
import com.robotkinematicslab.mobile.ml.ik.oneMicronSolverConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeRuntimeGuard
import com.robotkinematicslab.mobile.performance.compute.AndroidPerformanceHintReporter
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadBadge
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadClassifier
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OneMicronIkTrainingPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val datasetRepository = remember(context) { DatasetStorageRepository(context) }
    val storage = remember(context) { OneMicronIkStorageRepository(context) }
    val guard = remember(context) { ComputeRuntimeGuard(context) }
    val engine = remember(context) {
        OneMicronIkTrainingEngine(
            runtimeWorkerLimit = guard::currentWorkerLimit,
            workCycleReporterFactory = { AndroidPerformanceHintReporter(context) }
        )
    }
    val cancellation = remember { AtomicBoolean(false) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    val telemetryIndex = remember { AtomicInteger(0) }
    val telemetryCaptureGate = remember { HighResolutionTelemetryCaptureGate() }
    var manifests by remember { mutableStateOf(datasetRepository.listManifests()) }
    val compatible = manifests.filter(::isOneMicronCompatible)
    var selected by remember { mutableStateOf(compatible.firstOrNull()) }
    var featureSelections by remember {
        mutableStateOf(
            listOf(
                OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.KINEMATICS_108),
                OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361)
            )
        )
    }
    var split by remember { mutableStateOf(TrainingSplitStrategy.SAMPLE_GROUPED) }
    var runName by remember { mutableStateOf("verified_one_micron_ik") }
    var rowsText by remember { mutableStateOf("100000") }
    var epochsText by remember { mutableStateOf("50") }
    var batchText by remember { mutableStateOf("128") }
    var hiddenText by remember { mutableStateOf("64") }
    var learningRateText by remember { mutableStateOf("0.001") }
    var seedText by remember { mutableStateOf("42") }
    var verificationText by remember { mutableStateOf("1000") }
    var progress by remember { mutableStateOf<OneMicronIkTrainingProgress?>(null) }
    var telemetryProgress by remember { mutableStateOf<DiagnosticProgressState?>(null) }
    val telemetrySamples = remember { mutableStateListOf<DiagnosticPerformanceSample>() }
    var trainingStartedAt by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<OneMicronIkTrainingResult?>(null) }
    val comparisonResults = remember { mutableStateListOf<OneMicronIkTrainingResult>() }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val currentPolicy = guard.currentPolicy()
    val previewRequest =
        oneMicronExperimentRequest(
            selected, featureSelections, split, runName, rowsText, epochsText, batchText, hiddenText,
            learningRateText, seedText, verificationText, currentPolicy.effectiveWorkerCount,
            currentPolicy.workingMemoryBudgetBytes
        ).getOrNull()
    val previewPlanning = previewRequest?.let { runCatching { OneMicronExperimentPlanner.plan(it) } }
    val previewPlan = previewPlanning?.getOrNull()
    val previewFailure = previewPlanning?.exceptionOrNull()?.message
    val recommendedPlan = previewRequest?.let(OneMicronExperimentPlanner::recommendedSafePlan)
    val knownMemoryBlock =
        previewRequest != null && previewPlan == null && previewFailure.orEmpty().contains("buffers", ignoreCase = true)
    val safeRows = previewPlan?.effectiveRows ?: recommendedPlan?.effectiveRows ?: 0
    val peakPlannedBytes = (previewPlan ?: recommendedPlan)?.armMemoryPlans?.maxOfOrNull { it.estimatedBytes }
    val effectiveMemoryPercent =
        if (currentPolicy.appHeapLimitBytes > 0L) {
            (currentPolicy.workingMemoryBudgetBytes.toDouble() / currentPolicy.appHeapLimitBytes.toDouble() * 100.0)
                .roundToInt()
                .coerceIn(0, 80)
        } else {
            0
        }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        JargonAwareText("Verified 1 µm Position IK", style = MaterialTheme.typography.headlineSmall)
        JargonAwareText(
            "Scope: Cartesian tool position only (x, y, z). End-effector orientation is not a training target or certification claim.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        JargonAwareText(
            "The network proposes a joint state; fresh forward kinematics verifies it. If needed, the deterministic solver refines it. A result is labelled verified only at ≤ 0.000001 m.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrainingDisclosureSection(
            title = "1 · Certified dataset",
            subtitle = "Certified rows are retained solver successes whose Cartesian target was created by forward kinematics and independently recomputed at 1 µm or stricter. This experiment certifies tool position; it does not certify orientation."
        ) {
            if (compatible.isEmpty()) {
                JargonAwareText(
                    "No compatible dataset yet. Open Dataset and use the ‘Verified 1 µm IK preset’.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            compatible.forEach { manifest ->
                ChoiceButton(
                    selected = selected?.csvPath == manifest.csvPath,
                    label = "${manifest.datasetName} · ${manifest.rowCount} rows · ${manifest.robotIds.size} robots",
                    enabled = job == null
                ) { selected = manifest }
            }
            OutlinedButton(
                onClick = {
                    manifests = datasetRepository.listManifests()
                    selected = manifests.filter(::isOneMicronCompatible).firstOrNull()
                },
                enabled = job == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh compatible datasets") }
        }

        TrainingDisclosureSection(
            title = "2 · Experiment contract",
            subtitle = "Grouped samples keeps identical robot/target/seed scenarios together while allowing the same robot definitions across partitions. Robot-held-out excludes complete definitions from training and tests transfer only to those sampled definitions, not to every unseen robot or topology."
        ) {
            OutlinedTextField(runName, { runName = it }, label = { Text("Run name") }, enabled = job == null, modifier = Modifier.fillMaxWidth())
            OneMicronFeatureExperimentSelector(
                originLabel = "Verified IK / Experiment contract",
                sourceCsvPath = selected?.csvPath,
                selections = featureSelections,
                enabled = job == null,
                onSelectionsChange = { featureSelections = it }
            )
            TrainingSplitStrategy.entries.forEach { candidate ->
                ChoiceButton(split == candidate, candidate.displayName, job == null) { split = candidate }
                if (split == candidate) {
                    Text(
                        candidate.scientificDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            NumberField("Maximum certified rows", rowsText, job == null, "Requested cap before dataset availability and the shared memory cap are applied. Every compared feature configuration receives the same effective row cap.") { rowsText = it }
            NumberField("Epochs", epochsText, job == null, "1–1,000; default 50. Each epoch visits the training partition once. More epochs add cost; validation stopping can finish earlier.") { epochsText = it }
            NumberField("Batch size", batchText, job == null, "1–100,000; default 128. It changes update grouping, active worker buffers and memory; it does not change the untouched test partition.") { batchText = it }
            NumberField("Hidden units", hiddenText, job == null, "4–1,024; default 64. This changes model capacity, parameter memory and compute. A larger model is not assumed to be more accurate.") { hiddenText = it }
            NumberField("Learning rate", learningRateText, job == null, "0.000001–1; default 0.001. It controls Adam update size and interacts with epoch count; unstable or very small steps can both harm validation.") { learningRateText = it }
            NumberField("Deterministic seed", seedText, job == null, "Default 42. It reproduces the split and model initialization for the same ordered dataset and contract; it is not evidence of generality by itself.") { seedText = it }
            NumberField("Untouched cases to verify", verificationText, job == null, "Positive requested maximum from the final test partition. If that partition contains fewer cases, the report records the exact smaller denominator.") { verificationText = it }
            ChartMetricRow("Requested workers", currentPolicy.requestedWorkerCount.toString())
            ChartMetricRow("Effective workers", currentPolicy.effectiveWorkerCount.toString())
            ResourceLoadBadge(
                level =
                    ResourceLoadClassifier.classify(
                        currentPolicy.effectiveWorkerCount,
                        1,
                        currentPolicy.safeMaximumWorkers
                    ),
                prefix = "CPU ${currentPolicy.effectiveWorkerCount}/${currentPolicy.safeMaximumWorkers} workers"
            )
            selected?.let { manifest -> ChartMetricRow("Stored dataset rows", manifest.rowCount.toString()) }
            if (safeRows > 0) ChartMetricRow("Shared effective row cap", safeRows.toString())
            ChartMetricRow("Working-memory budget", formatBytes(currentPolicy.workingMemoryBudgetBytes))
            peakPlannedBytes?.let { ChartMetricRow("Effective peak estimate", formatBytes(it)) }
            ResourceLoadBadge(
                level = ResourceLoadClassifier.classify(effectiveMemoryPercent, 0, 80),
                prefix = "Memory $effectiveMemoryPercent% of app heap"
            )
            if (knownMemoryBlock && recommendedPlan == null) {
                JargonAwareText(
                    "The current model and active worker buffers cannot fit the minimum 30-row training contract. Training is disabled for this plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (previewPlan?.wasCapped == true) {
                JargonAwareText(
                    "Effective plan: $safeRows of ${previewPlan.request.requestedRows} requested rows. The shared cap includes dataset matrices, model checkpoints, optimizer state and active worker buffers. Other stored rows remain unchanged on disk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (recommendedPlan != null && (previewPlan == null || previewPlan.wasCapped)) {
                val recommendation = recommendedPlan
                OutlinedButton(
                    onClick = {
                        rowsText = recommendation.effectiveRows.toString()
                        hiddenText = recommendation.request.hiddenUnits.toString()
                        batchText = recommendation.request.batchSize.toString()
                        error = null
                    },
                    enabled = job == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Apply safe settings · ${recommendation.effectiveRows} rows, " +
                            "${recommendation.request.hiddenUnits} hidden, batch ${recommendation.request.batchSize}"
                    )
                }
            }
            JargonAwareText(
                currentPolicy.safetyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ChartSectionCard(
            title = "3 · Train and certify",
            subtitle = "Raw neural accuracy, hybrid accuracy and an equal deterministic baseline are measured separately.",
            modifier = Modifier
                .testTag("training-one-micron-controls")
                .tutorialAnchor(TutorialTargets.TrainingOneMicron)
        ) {
            progress?.let {
                LinearProgressIndicator(progress = { it.fraction }, modifier = Modifier.fillMaxWidth())
                Text("${it.phase.name.replace('_', ' ')} · ${it.message}")
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.politeLiveRegion().testTag("training-one-micron-status")
                )
            }
            if (job == null) {
                Button(
                    onClick = startMicronTraining@{
                        if (processCoordinator.isActive(ResearchProcessIds.ONE_MICRON_TRAINING)) {
                            error = "A verified 1 µm training run is already active. Open Research activity to inspect it."
                            return@startMicronTraining
                        }
                        val policy = guard.currentPolicy(true)
                        val request =
                            oneMicronExperimentRequest(
                                selected, featureSelections, split, runName, rowsText, epochsText, batchText,
                                hiddenText, learningRateText, seedText, verificationText,
                                policy.effectiveWorkerCount, policy.workingMemoryBudgetBytes
                            ).getOrElse { failure ->
                                error = failure.message ?: "The experiment contract is invalid."
                                return@startMicronTraining
                            }
                        val executionPlan = runCatching { OneMicronExperimentPlanner.plan(request) }.getOrElse { failure ->
                            error = failure.message ?: "No safe effective configuration is available."
                            return@startMicronTraining
                        }
                        val executionConfigs = executionPlan.trainingConfigs()
                        run {
                            cancellation.set(false)
                            error = null
                            result = null
                            comparisonResults.clear()
                            telemetrySamples.clear()
                            telemetryIndex.set(0)
                            telemetryCaptureGate.reset()
                            trainingStartedAt = System.currentTimeMillis()
                            job = processCoordinator.launch(
                                id = ResearchProcessIds.ONE_MICRON_TRAINING,
                                title = "Verified 1 µm training · ${executionPlan.request.runName.trim()}",
                                kind = ResearchProcessKind.TRAINING,
                                cancellationAction = { cancellation.set(true) }
                            ) { process ->
                                process.report(0.0, "Certifying training data", "Preparing the verified 1 µm experiment.")
                                try {
                                    executionConfigs.forEachIndexed { configurationIndex, config ->
                                        if (cancellation.get()) throw OneMicronIkTrainingCancelledException()
                                        val trained = withContext(Dispatchers.Default) {
                                            engine.run(config, storage, cancellation::get,
                                                onProgress = { update ->
                                                    val decorated = update.copy(
                                                        message =
                                                            "Configuration ${configurationIndex + 1}/${executionConfigs.size} · " +
                                                                "${config.resolvedFeatureSelection.displayName}: ${update.message}"
                                                    )
                                                    val overallFraction =
                                                        (configurationIndex.toDouble() + update.fraction) / executionConfigs.size.toDouble()
                                                    process.report(
                                                        progressFraction = overallFraction,
                                                        stage = decorated.phase.name.lowercase().replace('_', ' '),
                                                        detail = decorated.message
                                                    )
                                                    if (
                                                        telemetryCaptureGate.shouldCapture(
                                                            force = decorated.phase in setOf(
                                                                OneMicronIkTrainingPhase.COMPLETED,
                                                                OneMicronIkTrainingPhase.FAILED
                                                            )
                                                        )
                                                    ) {
                                                        val mapped = mapMicronProgress(decorated, trainingStartedAt, telemetrySampler)
                                                        val sample = DiagnosticPerformanceSample.fromProgressState(telemetryIndex.incrementAndGet(), mapped)
                                                        scope.launch { progress = decorated; telemetryProgress = mapped; telemetrySamples += sample }
                                                    }
                                                },
                                                onEpoch = { }
                                            )
                                        }
                                        comparisonResults += trained
                                        result = trained
                                    }
                                    process.completed(
                                        "Verified 1 µm training completed for ${executionConfigs.size} feature configuration(s) " +
                                            "using the shared ${executionPlan.effectiveRows}-row cap."
                                    )
                                } catch (cancelled: OneMicronIkTrainingCancelledException) {
                                    process.cancelled("Verified 1 µm training was cancelled before completion.")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    process.cancelled("Verified 1 µm training was cancelled before completion.")
                                } catch (throwable: Exception) {
                                    error = throwable.message ?: "Training failed."
                                    process.failed(requireNotNull(error))
                                } finally {
                                    job = null
                                }
                            }.getOrElse { throwable ->
                                error = throwable.message ?: "Training could not start."
                                null
                            }
                        }
                    },
                    enabled = compatible.isNotEmpty() && !knownMemoryBlock,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Train and verify at 1 µm") }
            } else {
                OutlinedButton(
                    onClick = { processCoordinator.requestCancel(ResearchProcessIds.ONE_MICRON_TRAINING) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel safely") }
            }
        }

        telemetryProgress?.let { mapped ->
            DiagnosticLoadingProgressCard(
                progressState = mapped,
                performanceSamples = telemetrySamples,
                config =
                    DiagnosticLoadingCardConfig(
                        runningTitle = "Verified 1 µm Training Running",
                        finishedTitle = "Verified 1 µm Training Telemetry",
                        progressSectionTitle = "Neural IK Progress",
                        completedLabel = "Completed work units",
                        remainingLabel = "Work units left",
                        rateLabel = "Work units per second",
                        showBenchmarkCoordinates = false,
                        telemetrySessionType = "Verified 1 µm neural IK"
                    ),
                timeline = micronTimeline(progress?.phase ?: OneMicronIkTrainingPhase.READING_AND_CERTIFYING)
            )
        }

        if (job == null) {
            result?.let { completed ->
                val completedArms = comparisonResults.ifEmpty { listOf(completed) }
                val comparisonExecutionId =
                    remember(completedArms) {
                        "comparison-" +
                            ChartFigureExporter.automaticDataFingerprint(
                                completedArms.map { arm -> listOf(arm.runId, arm.finishedAtEpochMillis) }
                            )
                    }
                TrainingDisclosureSection(
                    title = "Independent 1 µm verification",
                    subtitle = "The neural-only line is descriptive. Only independently checked FK residuals at or below 1 µm count as certified.",
                    initiallyExpanded = true
                ) {
                    completedArms.forEach { arm ->
                        ProvideAutomaticFigureLibraryContext(
                            collection = "one-micron-training",
                            analysisId = arm.runId,
                            executionId = "one-micron-${arm.runId}"
                        ) {
                            OneMicronResult(arm)
                        }
                    }
                    if (completedArms.size >= 2) {
                        ProvideAutomaticFigureLibraryContext(
                            collection = "one-micron-comparison",
                            analysisId = completedArms.joinToString("-") { it.config.runName },
                            executionId = comparisonExecutionId
                        ) {
                            OneMicronComparisonResults(completedArms)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OneMicronResult(result: OneMicronIkTrainingResult) {
    val metrics = result.verification
    val reliability = OneMicronReliabilityCalculator.assess(metrics)
    val directCount = metrics.observations.count { it.path == com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath.NEURAL_DIRECT }
    val refinedCount = metrics.observations.count { it.path == com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath.NEURAL_REFINED }
    val fallbackCount = metrics.observations.count { it.path == com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath.DETERMINISTIC_FALLBACK }
    val failedCount = metrics.observations.count { it.path == com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath.FAILED }
    val pureSolverCount = metrics.observations.count { it.pureSolverCertified }
    fun observedCount(count: Int) = if (metrics.observations.isEmpty()) "case count unavailable" else "$count/${metrics.samples}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartMetricRow("Verified criterion", "tool-position residual ≤ ${metrics.certifiedToleranceMeters} m · 1 µm")
        ChartMetricRow("Stored dataset rows", result.config.sourceDatasetRows.takeIf { it >= 0 }?.toString() ?: "legacy metadata unavailable")
        ChartMetricRow("Rows requested / effective cap", "${result.config.requestedMaximumRows} / ${result.config.maximumRows}")
        ChartMetricRow("Actual train / validation / untouched test", "${result.trainRows} / ${result.validationRows} / ${result.testRows}")
        ChartMetricRow("Untouched cases requested / evaluated", "${result.config.verificationSampleLimit} / ${metrics.samples}")
        ChartMetricRow("Split strategy", result.config.splitStrategy.displayName)
        ChartMetricRow("Robot definitions", result.config.datasetRobotIds.size.takeIf { it > 0 }?.toString() ?: "legacy metadata unavailable")
        ChartMetricRow("Feature configuration", "${result.config.resolvedFeatureSelection.displayName} · ${result.featureNames.size} inputs")
        ChartMetricRow("Direct neural success · no solver", "${observedCount(directCount)} · ${percent(metrics.directNeuralSuccessRate)}")
        ChartMetricRow("Refinement-only success · hybrid", "${observedCount(refinedCount)} · ${percent(metrics.refinedOnlySuccessRate)}")
        ChartMetricRow("Fallback-only success · deterministic", "${observedCount(fallbackCount)} · ${percent(metrics.fallbackOnlySuccessRate)}")
        ChartMetricRow("Unresolved by protected pipeline", "${observedCount(failedCount)} · ${percent(metrics.failedPipelineRate)}")
        ChartMetricRow("Pure deterministic solver · independent trial", "${observedCount(pureSolverCount)} · ${percent(metrics.pureSolverSuccessRate)}")
        ChartMetricRow("Protected total · includes solver", percent(metrics.verifiedPipelineSuccessRate))
        JargonAwareText(
            "Direct, refinement-only and fallback-only successes are mutually exclusive fractions of the same commands. The pure solver is a separate reference trial. Only direct neural success counts as AI-only accuracy. Classification explanations describe classifier predictions, never hybrid IK results.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChartMetricRow("Raw neural median error", distance(metrics.rawMedianErrorMeters))
        ChartMetricRow("Raw neural p95 error", distance(metrics.rawP95ErrorMeters))
        ChartMetricRow("Refinement iterations / all evaluated commands", decimal(metrics.meanRefinedIterations))
        ChartMetricRow("Pure solver iterations / all evaluated commands", decimal(metrics.meanBaselineIterations))
        ChartMetricRow("Mean protected-pipeline iterations", decimal(metrics.meanPipelineIterations))
        ChartMetricRow("Iteration saving", "${decimal(metrics.iterationSavingsPercent)}%")
        ChartMetricRow("Aggregate raw residual", distance(metrics.rawCumulativeErrorMeters))
        ChartMetricRow("Aggregate protected residual", distance(metrics.protectedCumulativeErrorMeters))
        ChartMetricRow("Residual avoided after protected recovery", "${decimal(metrics.cumulativeErrorAvoidedPercent)}%")
        ChartMetricRow("Mean raw residual per command", distance(reliability.meanRawResidualMeters))
        ChartMetricRow("Mean protected residual per command", distance(reliability.meanProtectedResidualMeters))
        ChartMetricRow("1,000-command protected residual budget", distance(reliability.protectedResidualBudgetPerThousandCommandsMeters))
        ChartMetricRow("Reliability band", reliability.band.displayName)
        ChartMetricRow("95% Wilson interval", "${percent(reliability.wilson95LowerBound)} – ${percent(reliability.wilson95UpperBound)}")
        ChartMetricRow("Neural inference", "${decimal(metrics.meanNeuralInferenceNanos / 1_000.0)} µs/sample")
        ChartMetricRow("Parameters", result.model.parameterCount.toString())
        Text("Model: ${result.modelPath}", style = MaterialTheme.typography.bodySmall)
        Text("Report: ${result.reportPath}", style = MaterialTheme.typography.bodySmall)
        JargonAwareText(
            if (failedCount > 0) {
                "Needs attention: $failedCount untouched command(s) had no candidate with both an accepted solver status and an independently checked position residual at or below 1 µm. ${reliability.interpretation}"
            } else {
                reliability.interpretation
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (metrics.samples < result.config.verificationSampleLimit) {
            Text(
                "The untouched partition supplied ${metrics.samples} of ${result.config.verificationSampleLimit} requested cases. The rates apply only to that recorded denominator.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Text(
            "Scope: these results apply to the recorded dataset fingerprint, ordered robot definitions, feature contract, split and solver configuration. They do not certify orientation or unlimited operation outside the evaluated targets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ProfessionalHorizontalBarChart(
        title = "One-micron certification path",
        subtitle =
            "Direct neural, refinement-only and fallback-only are disjoint outcomes. The pure solver is an independent reference; protected total is not AI-only success.",
        items =
            listOf(
                ChartBarItem("Direct neural · ${percent(metrics.directNeuralSuccessRate)}", rateUnits(metrics.directNeuralSuccessRate), Color(0xFF6A1B9A)),
                ChartBarItem("Refinement only · ${percent(metrics.refinedOnlySuccessRate)}", rateUnits(metrics.refinedOnlySuccessRate), Color(0xFF1565C0)),
                ChartBarItem("Fallback only · ${percent(metrics.fallbackOnlySuccessRate)}", rateUnits(metrics.fallbackOnlySuccessRate), Color(0xFFB26A00)),
                ChartBarItem("Pure solver · ${percent(metrics.pureSolverSuccessRate)}", rateUnits(metrics.pureSolverSuccessRate), Color(0xFF546E7A)),
                ChartBarItem("Protected pipeline · ${percent(metrics.verifiedPipelineSuccessRate)}", rateUnits(metrics.verifiedPipelineSuccessRate), Color(0xFF2E7D32))
            ),
        xAxisLabel = "Certified rate × 10,000"
    )
    ProfessionalHorizontalBarChart(
        title = "Cumulative Cartesian residual burden",
        subtitle =
            "Direct sum over the untouched verification commands. Lower is better; the protected value is never folded back into the raw-neural result.",
        items =
            listOf(
                ChartBarItem("Raw neural · ${distance(metrics.rawCumulativeErrorMeters)}", nanometreUnits(metrics.rawCumulativeErrorMeters), Color(0xFFC62828)),
                ChartBarItem("Protected pipeline · ${distance(metrics.protectedCumulativeErrorMeters)}", nanometreUnits(metrics.protectedCumulativeErrorMeters), Color(0xFF2E7D32))
            ),
        xAxisLabel = "Cumulative residual (nm, bounded for display)"
    )
    if (result.epochs.size >= 2) {
        ProfessionalLineChart(
            title = "${result.config.resolvedFeatureSelection.displayName} · regression learning curve",
            subtitle = "Validation data is used for early stopping; final Cartesian evidence uses the untouched test partition.",
            points = result.epochs.map { ChartLinePoint(it.epoch.toDouble(), it.validationLoss) },
            xAxisLabel = "Epoch",
            yAxisLabel = "Masked validation MSE",
            color = Color(0xFF6A1B9A),
            directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
        )
    }
}

@Composable
private fun OneMicronComparisonResults(results: List<OneMicronIkTrainingResult>) {
    val ordered = results.sortedBy { it.featureNames.size }
    ProfessionalLineChart(
        title = "Neural IK · variables versus verified success",
        subtitle = "All arms use the same dataset, seed, split and verification budget; only the selected inputs change.",
        points = ordered.map { ChartLinePoint(it.featureNames.size.toDouble(), it.verification.rawNeuralSuccessRate) },
        xAxisLabel = "Selected input variables",
        yAxisLabel = "Raw neural success rate",
        color = Color(0xFF2E7D32)
    )
    ProfessionalLineChart(
        title = "Neural IK · variables versus Cartesian error",
        subtitle = "Lower is better. Values are independently recomputed through forward kinematics.",
        points = ordered.map { ChartLinePoint(it.featureNames.size.toDouble(), it.verification.rawMedianErrorMeters * 1e6) },
        xAxisLabel = "Selected input variables",
        yAxisLabel = "Median error (µm)",
        color = Color(0xFFC62828)
    )
    ProfessionalLineChart(
        title = "Neural IK · variables versus training time",
        subtitle = "Wall-clock cost for every selected experimental arm on this device.",
        points = ordered.map { ChartLinePoint(it.featureNames.size.toDouble(), (it.finishedAtEpochMillis - it.startedAtEpochMillis) / 1_000.0) },
        xAxisLabel = "Selected input variables",
        yAxisLabel = "Seconds",
        color = Color(0xFFF57C00),
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )
    ChartSectionCard("Every selected neural-IK arm", "Direct results; no deterministic correction is applied to the raw-neural rows.") {
        ordered.forEach { arm ->
            ChartMetricRow(
                "${arm.config.resolvedFeatureSelection.displayName} · ${arm.featureNames.size}",
                "success ${percent(arm.verification.rawNeuralSuccessRate)} · median ${distance(arm.verification.rawMedianErrorMeters)}"
            )
        }
    }
}

private fun isOneMicronCompatible(manifest: DatasetManifest): Boolean =
    manifest.targetMode == DatasetTargetMode.FK_PROVEN_REACHABLE &&
        manifest.reachableFraction == 1.0 &&
        manifest.filterMode == DatasetFilterMode.ACCEPTED_ONLY &&
        manifest.ikConfig.tolerance <= ONE_MICRON_METERS &&
        manifest.robotIds.size >= 3 && manifest.rowCount >= 30 &&
        manifest.scientificFingerprint != null

private fun oneMicronExperimentRequest(
    manifest: DatasetManifest?,
    featureSelections: List<OneMicronFeatureSelectionSpec>,
    split: TrainingSplitStrategy,
    runName: String,
    rowsText: String,
    epochsText: String,
    batchText: String,
    hiddenText: String,
    learningRateText: String,
    seedText: String,
    verificationText: String,
    workerCount: Int,
    workingMemoryBudgetBytes: Long
): Result<OneMicronExperimentRequest> = runCatching {
    val source = requireNotNull(manifest) { "Select a compatible 1 µm dataset." }
    fun integer(text: String, label: String): Int =
        requireNotNull(text.trim().toIntOrNull()) { "$label must be a whole number." }
    val learningRate = requireNotNull(learningRateText.trim().toDoubleOrNull()) {
        "Learning rate must be a number."
    }
    OneMicronExperimentRequest(
        runName = runName,
        datasetPath = source.csvPath,
        datasetScientificFingerprint = source.scientificFingerprint,
        datasetRowCount = source.rowCount,
        datasetRobotIds = source.robotIds,
        featureSelections = featureSelections,
        splitStrategy = split,
        requestedRows = integer(rowsText, "Rows"),
        epochs = integer(epochsText, "Epochs"),
        batchSize = integer(batchText, "Batch size"),
        hiddenUnits = integer(hiddenText, "Hidden units"),
        learningRate = learningRate,
        randomSeed = integer(seedText, "Seed"),
        verificationSampleLimit = integer(verificationText, "Untouched cases to verify"),
        workerCount = workerCount,
        workingMemoryBudgetBytes = workingMemoryBudgetBytes,
        solverConfig = oneMicronSolverConfig()
    )
}

@Composable
private fun ChoiceButton(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    enabled: Boolean,
    help: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value,
        onChange,
        label = { JargonAwareText(label) },
        supportingText = { Text(help) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun percent(value: Double): String = if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100.0) else "n/a"
private fun decimal(value: Double): String = if (value.isFinite()) String.format(Locale.US, "%.3f", value) else "n/a"
private fun distance(metres: Double): String =
    when {
        !metres.isFinite() -> "n/a"
        metres < 1e-3 -> String.format(Locale.US, "%.3f µm", metres * 1e6)
        else -> String.format(Locale.US, "%.3f mm", metres * 1e3)
    }

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    }

private fun rateUnits(value: Double): Int =
    if (value.isFinite()) (value.coerceIn(0.0, 1.0) * 10_000.0).toInt() else 0

private fun nanometreUnits(metres: Double): Int =
    if (metres.isFinite() && metres >= 0.0) {
        (metres * 1e9).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    } else {
        0
    }

private fun mapMicronProgress(
    update: OneMicronIkTrainingProgress,
    startedAt: Long,
    sampler: DiagnosticSystemTelemetrySampler
): DiagnosticProgressState {
    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1000.0
    val rate = if (elapsed > 0.0) update.completed / elapsed else 0.0
    val remaining = (update.total - update.completed).coerceAtLeast(0)
    val phase =
        when (update.phase) {
            OneMicronIkTrainingPhase.READING_AND_CERTIFYING,
            OneMicronIkTrainingPhase.PREPARING_SPLITS -> DiagnosticProgressPhase.PLANNING
            OneMicronIkTrainingPhase.TRAINING -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
            OneMicronIkTrainingPhase.VERIFYING_CARTESIAN_ERROR,
            OneMicronIkTrainingPhase.SAVING -> DiagnosticProgressPhase.AGGREGATING
            OneMicronIkTrainingPhase.COMPLETED -> DiagnosticProgressPhase.COMPLETED
            OneMicronIkTrainingPhase.FAILED -> DiagnosticProgressPhase.FAILED
        }
    return DiagnosticProgressState(
        isRunning = update.phase !in setOf(OneMicronIkTrainingPhase.COMPLETED, OneMicronIkTrainingPhase.FAILED),
        phase = phase,
        completedRuns = update.completed,
        totalRuns = update.total,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0.0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsed,
        message = update.message,
        telemetry = sampler.sample()
    )
}

private fun micronTimeline(phase: OneMicronIkTrainingPhase): List<DiagnosticTimelineStep> {
    val steps =
        listOf(
            OneMicronIkTrainingPhase.READING_AND_CERTIFYING to "Certify dataset rows",
            OneMicronIkTrainingPhase.PREPARING_SPLITS to "Build leakage-safe partitions",
            OneMicronIkTrainingPhase.TRAINING to "Train neural warm start",
            OneMicronIkTrainingPhase.VERIFYING_CARTESIAN_ERROR to "Verify untouched Cartesian errors",
            OneMicronIkTrainingPhase.SAVING to "Save model and evidence"
        )
    val current = steps.indexOfFirst { it.first == phase }
    return steps.mapIndexed { index, (_, label) ->
        DiagnosticTimelineStep(
            label,
            when {
                phase == OneMicronIkTrainingPhase.FAILED && index == current -> DiagnosticTimelineStatus.FAILED
                phase == OneMicronIkTrainingPhase.COMPLETED || (current >= 0 && index < current) -> DiagnosticTimelineStatus.COMPLETE
                index == current -> DiagnosticTimelineStatus.RUNNING
                else -> DiagnosticTimelineStatus.PENDING
            }
        )
    }
}
