package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.project
import com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import com.robotkinematicslab.mobile.performance.compute.NoOpComputeWorkCycleReporter
import java.io.File
import java.util.Locale

class LocalTrainingCancelledException : RuntimeException("Training was cancelled.")

class LocalTrainingEngine(
    private val reader: ScientificDatasetTrainingReader = ScientificDatasetTrainingReader(),
    private val preparer: TrainingDatasetPreparer = TrainingDatasetPreparer(),
    /** Supplies a live thermal/memory-aware cap. Pure JVM callers keep the configured cap. */
    private val runtimeWorkerLimit: () -> Int = { Int.MAX_VALUE },
    private val workCycleReporterFactory: () -> ComputeWorkCycleReporter = {
        NoOpComputeWorkCycleReporter
    }
) {

    private val trainer = LocalModelTrainer(workCycleReporterFactory)

    fun train(
        config: LocalTrainingConfig,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (LocalTrainingProgress) -> Unit = {},
        onIteration: (TrainingIterationMetrics) -> Unit = {}
    ): LocalTrainingRunResult {
        validate(config)
        val selections = config.resolvedFeatureSelections
        val candidates = candidateSpecifications(config)
        val workerBatchCounts = sortedMapOf<Int, Long>()
        val totalWorkUnits = selections.size * (2 + candidates.size * config.epochs + 1) + 1
        var completedWorkUnits = 0
        var globalIteration = 0
        val iterations = mutableListOf<TrainingIterationMetrics>()
        val results = mutableListOf<TrainedProfileResult>()
        val startedAt = System.currentTimeMillis()
        val originalCorpusDigest = TrainingInferenceContract.corpusDigest(File(config.datasetPath)) { ensureNotCancelled(cancellationRequested) }
        config.expectedCorpusSha256?.let { require(it == originalCorpusDigest) { "The selected dataset changed after compatibility validation. Refresh before training." } }
        config.managedDatasetManifest?.let { manifest ->
            require(manifest.csvPath == config.datasetPath && manifest.rowCount == config.expectedDatasetRows)
            val requirements = requireNotNull(config.datasetRequirements)
            require(com.robotkinematicslab.mobile.ml.data.TrainingDatasetCompatibilityEvaluator.evaluate(manifest, requirements).level !=
                com.robotkinematicslab.mobile.ml.data.DatasetCompatibilityLevel.INCOMPATIBLE) { "Dataset no longer satisfies the requested experiment." }
        }

        selections.forEach { selection ->
            val profile = selection.sourceProfile
            ensureNotCancelled(cancellationRequested)
            onProgress(
                LocalTrainingProgress(
                    phase = LocalTrainingPhase.READING_DATASET,
                    completedWorkUnits = completedWorkUnits,
                    totalWorkUnits = totalWorkUnits,
                    profile = profile,
                    candidateId = null,
                    epoch = null,
                    message = "Reading ${profile.displayName} rows and enforcing the feature contract."
                )
            )
            val sampleAcrossEntireFile =
                config.sampleAcrossEntireDataset ||
                    config.splitStrategy == com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy.ROBOT_HELD_OUT
            val loadResult =
                reader.load(
                    file = File(config.datasetPath),
                    profile = profile,
                    maximumRows = config.maximumRows,
                    cancellationRequested = cancellationRequested,
                    sampleAcrossEntireFile = sampleAcrossEntireFile,
                    samplingSeed = config.randomSeed,
                    expectedDataRowCount = config.expectedDatasetRows,
                    requiredNewestRows = config.requiredNewestRows,
                    requireEveryRowValid = config.managedDatasetManifest != null,
                    expectedManifest = config.managedDatasetManifest,
                    requirements = config.datasetRequirements
                )
            ensureNotCancelled(cancellationRequested)
            val completeDataset = requireNotNull(loadResult.dataset) {
                loadResult.errorMessage ?: "Dataset could not be loaded."
            }
            if (config.managedDatasetManifest != null) {
                require(completeDataset.samples.size == config.maximumRows) { "Validated effective row count differs from the requested execution plan." }
            }
            val dataset = completeDataset.project(selection)
            completedWorkUnits++

            ensureNotCancelled(cancellationRequested)
            onProgress(
                LocalTrainingProgress(
                    phase = LocalTrainingPhase.PREPARING_SPLITS,
                    completedWorkUnits = completedWorkUnits,
                    totalWorkUnits = totalWorkUnits,
                    profile = profile,
                    candidateId = null,
                    epoch = null,
                    message = "Grouping duplicate scenarios or complete robot definitions into train, validation and test partitions."
                )
            )
            val prepared = preparer.prepare(dataset, config.randomSeed, config.splitStrategy)
            completedWorkUnits++

            val candidateResults = mutableListOf<CandidateTrainingResult>()
            candidates.forEach { specification ->
                ensureNotCancelled(cancellationRequested)
                val phase =
                    if (profile == TrainingFeatureProfile.BASELINE_KINEMATICS) {
                        LocalTrainingPhase.TRAINING_BASELINE
                    } else {
                        LocalTrainingPhase.TRAINING_CONTEXT
                    }
                onProgress(
                    LocalTrainingProgress(
                        phase = phase,
                        completedWorkUnits = completedWorkUnits,
                        totalWorkUnits = totalWorkUnits,
                        profile = profile,
                        candidateId = specification.id,
                        epoch = 0,
                        message = "Training ${specification.id} on ${profile.displayName}. " + preparer.splitWarnings(prepared.split).joinToString(" ")
                    )
                )

                val candidateResult =
                    trainer.train(
                        dataset = prepared,
                        specification = specification,
                        config = config,
                        nextGlobalIteration = { ++globalIteration },
                        cancellationRequested = cancellationRequested,
                        runtimeWorkerLimit = runtimeWorkerLimit,
                        onIteration = { iteration ->
                            val selectedIteration =
                                iteration.copy(
                                    featureSelectionId = selection.id,
                                    featureSelectionName = selection.displayName
                                )
                            iterations += selectedIteration
                            completedWorkUnits++
                            onIteration(selectedIteration)
                            onProgress(
                                LocalTrainingProgress(
                                    phase = phase,
                                    completedWorkUnits = completedWorkUnits,
                                    totalWorkUnits = totalWorkUnits,
                                    profile = profile,
                                    candidateId = specification.id,
                                    epoch = selectedIteration.epoch,
                                    message =
                                        "${specification.id}: epoch ${selectedIteration.epoch}, " +
                                            "validation macro-F1 ${format(selectedIteration.validationMetrics.macroF1)}."
                                )
                            )
                        }
                    )
                candidateResults += candidateResult
                candidateResult.workerBatchCounts.forEach { (workers, count) -> workerBatchCounts[workers] = (workerBatchCounts[workers] ?: 0L) + count }
            }

            ensureNotCancelled(cancellationRequested)
            val best = candidateResults.reduce { current, candidate ->
                if (isBetter(candidate.validationMetrics, current.validationMetrics)) candidate else current
            }
            onProgress(
                LocalTrainingProgress(
                    phase = LocalTrainingPhase.EVALUATING,
                    completedWorkUnits = completedWorkUnits,
                    totalWorkUnits = totalWorkUnits,
                    profile = profile,
                    candidateId = best.specification.id,
                    epoch = best.bestEpoch,
                    message = "Evaluating the selected candidate on the untouched test partition."
                )
            )
            val testMetrics =
                ClassificationEvaluator.evaluate(
                    model = best.model,
                    dataset = prepared,
                    indices = prepared.split.testIndices,
                    measureInference = true
                )
            completedWorkUnits++

            results +=
                TrainedProfileResult(
                    profile = profile,
                    candidateId = best.specification.id,
                    model = best.model,
                    normalization = prepared.normalization,
                    featureNames = dataset.featureNames,
                    trainRowCount = prepared.split.trainIndices.size,
                    validationRowCount = prepared.split.validationIndices.size,
                    testRowCount = prepared.split.testIndices.size,
                    skippedRowCount = dataset.skippedRowCount,
                    duplicateFingerprintCount = dataset.duplicateFingerprintCount,
                    duplicateFingerprintsKeptTogether = prepared.split.duplicateFingerprintsKeptTogether,
                    bestEpoch = best.bestEpoch,
                    validationMetrics = best.validationMetrics,
                    testMetrics = testMetrics,
                    trainingDurationMillis = best.durationMillis,
                    parameterCount = parameterCount(best.model),
                    testSlices = ClassificationEvaluator.evaluateScientificSlices(best.model, prepared, prepared.split.testIndices),
                    datasetWarnings = prepared.shortcutWarnings,
                    splitEvidence = com.robotkinematicslab.mobile.ml.data.TrainingSplitEvidence.from(dataset.samples,prepared.split),
                    featureSelectionId = selection.id,
                    featureSelectionName = selection.displayName,
                    inferenceContract = TrainingInferenceContract.capture(originalCorpusDigest, prepared, best.model,
                        config.maximumRows, config.randomSeed, sampleAcrossEntireFile,
                        expectedDataRowCount = config.expectedDatasetRows,
                        requiredNewestRows = config.requiredNewestRows,
                        checkCancellation = { ensureNotCancelled(cancellationRequested) })
                )
        }

        require(originalCorpusDigest == TrainingInferenceContract.corpusDigest(File(config.datasetPath)) { ensureNotCancelled(cancellationRequested) }) {
            "The dataset changed during training; the run cannot publish replayable inference evidence."
        }
        val baseline = results.firstOrNull { it.profile == TrainingFeatureProfile.BASELINE_KINEMATICS }
        val context = results.firstOrNull { it.profile == TrainingFeatureProfile.CONTEXT_ENHANCED }
        val expanded = results.firstOrNull { it.profile == TrainingFeatureProfile.CONTEXT_EXPANDED }
        val comparison =
            TrainingComparison(
                baseline = baseline,
                contextEnhanced = context,
                contextExpanded = expanded,
                macroF1Delta = metricDelta(context, baseline) { it.testMetrics.macroF1 },
                balancedAccuracyDelta = metricDelta(context, baseline) { it.testMetrics.balancedAccuracy },
                logLossDelta = metricDelta(context, baseline) { it.testMetrics.logLoss },
                inferenceNanosDelta = metricDelta(context, baseline) { it.testMetrics.inferenceNanosPerSample },
                expandedMacroF1DeltaVsBaseline = metricDelta(expanded, baseline) { it.testMetrics.macroF1 },
                expandedMacroF1DeltaVsContext = metricDelta(expanded, context) { it.testMetrics.macroF1 },
                expandedBalancedAccuracyDeltaVsBaseline = metricDelta(expanded, baseline) { it.testMetrics.balancedAccuracy },
                expandedLogLossDeltaVsBaseline = metricDelta(expanded, baseline) { it.testMetrics.logLoss },
                expandedInferenceNanosDeltaVsBaseline = metricDelta(expanded, baseline) { it.testMetrics.inferenceNanosPerSample },
                variants = results.toList()
            )
        completedWorkUnits++
        val finishedAt = System.currentTimeMillis()
        onProgress(
            LocalTrainingProgress(
                phase = LocalTrainingPhase.COMPLETED,
                completedWorkUnits = totalWorkUnits,
                totalWorkUnits = totalWorkUnits,
                profile = null,
                candidateId = null,
                epoch = null,
                message = "Training and independent test evaluation completed."
            )
        )

        return LocalTrainingRunResult(
            runId = "training-${startedAt}-${safeStem(config.runName)}",
            config = config,
            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt,
            iterations = iterations,
            comparison = comparison,
            workerBatchCounts = workerBatchCounts.toMap()
        )
    }

    private fun candidateSpecifications(config: LocalTrainingConfig): List<CandidateSpecification> =
        TrainingControlContract.candidates(config)

    private fun validate(config: LocalTrainingConfig) {
        require(config.runName.isNotBlank()) { "Training run name must not be blank." }
        require(config.datasetPath.isNotBlank()) { "Select a dataset before training." }
        require(config.maximumRows in 30..1_000_000) { "Maximum rows must be between 30 and 1,000,000." }
        require(config.resolvedFeatureSelections.isNotEmpty()) { "Select at least one feature set." }
        require(config.resolvedFeatureSelections.map { it.id }.distinct().size == config.resolvedFeatureSelections.size) {
            "Every selected feature set needs a unique id."
        }
        val controlIssues = TrainingControlContract.issues(config)
        require(controlIssues.isEmpty()) { controlIssues.values.joinToString(" ") }
        require(config.requiredNewestRows in 0..config.maximumRows) {
            "Required newest rows must fit inside the selected training row cap."
        }
        require(config.expectedDatasetRows == null || config.expectedDatasetRows > 0L) {
            "Expected dataset row count must be positive when supplied."
        }
        require(config.requiredNewestRows == 0 || (config.sampleAcrossEntireDataset && config.expectedDatasetRows != null)) {
            "Guaranteed newest-row participation requires full-corpus sampling and a verified row count."
        }
    }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw LocalTrainingCancelledException()
        }
    }

    private fun isBetter(candidate: ClassificationMetrics, current: ClassificationMetrics): Boolean {
        val macroF1Difference = candidate.macroF1 - current.macroF1
        return macroF1Difference > 1e-12 ||
            (kotlin.math.abs(macroF1Difference) <= 1e-12 && candidate.logLoss < current.logLoss)
    }

    private fun parameterCount(model: com.robotkinematicslab.mobile.ml.model.LocalClassifierModel): Int =
        model.inputWeights.size + model.hiddenBiases.size +
            model.outputWeights.size + model.outputBiases.size

    private fun metricDelta(
        context: TrainedProfileResult?,
        baseline: TrainedProfileResult?,
        selector: (TrainedProfileResult) -> Double
    ): Double =
        if (context != null && baseline != null) selector(context) - selector(baseline) else Double.NaN

    private fun safeStem(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(48)
            .ifBlank { "experiment" }

    private fun format(value: Double): String = String.format(Locale.US, "%.4f", value)
}
