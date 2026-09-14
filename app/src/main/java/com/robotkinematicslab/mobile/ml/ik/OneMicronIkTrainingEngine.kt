package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.ik.InverseKinematicsSolver
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import com.robotkinematicslab.mobile.performance.compute.NoOpComputeWorkCycleReporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OneMicronIkTrainingEngine(
    private val reader: OneMicronIkDatasetReader = OneMicronIkDatasetReader(),
    private val preparer: OneMicronIkDatasetPreparer = OneMicronIkDatasetPreparer(),
    private val runtimeWorkerLimit: () -> Int = { Int.MAX_VALUE },
    private val workCycleReporterFactory: () -> ComputeWorkCycleReporter = {
        NoOpComputeWorkCycleReporter
    }
) {

    fun run(
        config: OneMicronIkTrainingConfig,
        storage: OneMicronIkStorageRepository? = null,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (OneMicronIkTrainingProgress) -> Unit = {},
        onEpoch: (OneMicronIkEpochMetrics) -> Unit = {}
    ): OneMicronIkTrainingResult {
        validate(config)
        ensureNotCancelled(cancellationRequested)
        val started = System.currentTimeMillis()
        onProgress(
            OneMicronIkTrainingProgress(
                OneMicronIkTrainingPhase.READING_AND_CERTIFYING,
                0,
                config.maximumRows,
                "Reading rows and independently rechecking their 1 µm FK residual."
            )
        )
        val dataset =
            reader.load(
                file = File(config.datasetPath),
                profile = config.resolvedFeatureSelection.sourceProfile,
                maximumRows = config.maximumRows,
                cancellationRequested = cancellationRequested,
                onProgress = { load ->
                    onProgress(
                        OneMicronIkTrainingProgress(
                            OneMicronIkTrainingPhase.READING_AND_CERTIFYING,
                            load.rowsAccepted,
                            config.maximumRows,
                            load.message
                        )
                    )
                },
                sampleAcrossEntireFile = config.splitStrategy ==
                    com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy.ROBOT_HELD_OUT,
                samplingSeed = config.randomSeed
            ).project(config.resolvedFeatureSelection)
        ensureNotCancelled(cancellationRequested)
        onProgress(
            OneMicronIkTrainingProgress(
                OneMicronIkTrainingPhase.PREPARING_SPLITS,
                1,
                1,
                "Building leakage-safe train, validation and untouched test partitions."
            )
        )
        val prepared = preparer.prepare(dataset, config.randomSeed, config.splitStrategy)
        val trainer = OneMicronIkModelTrainer(runtimeWorkerLimit, workCycleReporterFactory)
        onProgress(
            OneMicronIkTrainingProgress(
                OneMicronIkTrainingPhase.TRAINING,
                0,
                config.epochs,
                "Training the neural warm-start regressor."
            )
        )
        val trained =
            trainer.train(prepared, config, cancellationRequested) { epoch ->
                onEpoch(epoch)
                onProgress(
                    OneMicronIkTrainingProgress(
                        OneMicronIkTrainingPhase.TRAINING,
                        epoch.epoch,
                        config.epochs,
                        "Epoch ${epoch.epoch}: validation masked MSE ${format(epoch.validationLoss)}"
                    )
                )
            }
        ensureNotCancelled(cancellationRequested)
        val storedModel =
            StoredOneMicronIkModel(
                runId = "pending",
                profile = config.resolvedFeatureSelection.sourceProfile,
                solverConfig = config.solverConfig.copy(tolerance = ONE_MICRON_METERS),
                featureNames = dataset.featureNames,
                normalization = prepared.normalization,
                model = trained.model
            )
        val verifyIndices =
            prepared.split.testIndices
                .asSequence()
                .take(config.verificationSampleLimit)
                .toList()
                .toIntArray()
        onProgress(
            OneMicronIkTrainingProgress(
                OneMicronIkTrainingPhase.VERIFYING_CARTESIAN_ERROR,
                0,
                verifyIndices.size,
                "Comparing raw neural, neural-refined and deterministic paths on untouched targets."
            )
        )
        val verification =
            verify(storedModel, prepared, verifyIndices, cancellationRequested) { completed ->
                onProgress(
                    OneMicronIkTrainingProgress(
                        OneMicronIkTrainingPhase.VERIFYING_CARTESIAN_ERROR,
                        completed,
                        verifyIndices.size,
                        "Independent FK verification at 1 µm."
                    )
                )
            }
        ensureNotCancelled(cancellationRequested)
        val runId = buildRunId(config.runName, started)
        var result =
            OneMicronIkTrainingResult(
                runId = runId,
                config = config,
                model = trained.model,
                normalization = prepared.normalization,
                featureNames = dataset.featureNames,
                epochs = trained.epochs,
                bestEpoch = trained.bestEpoch,
                trainRows = prepared.split.trainIndices.size,
                validationRows = prepared.split.validationIndices.size,
                testRows = prepared.split.testIndices.size,
                skippedRows = dataset.skippedRows + dataset.rejectedByMicronContract,
                verification = verification,
                startedAtEpochMillis = started,
                finishedAtEpochMillis = System.currentTimeMillis()
            )
        if (storage != null) {
            onProgress(OneMicronIkTrainingProgress(OneMicronIkTrainingPhase.SAVING, 0, 1, "Saving model and scientific report."))
            result = storage.save(result)
        }
        onProgress(OneMicronIkTrainingProgress(OneMicronIkTrainingPhase.COMPLETED, 1, 1, "Training and verification completed."))
        return result
    }

    private fun verify(
        stored: StoredOneMicronIkModel,
        dataset: PreparedOneMicronIkDataset,
        indices: IntArray,
        cancelled: () -> Boolean,
        progress: (Int) -> Unit
    ): OneMicronIkVerificationMetrics {
        require(indices.isNotEmpty())
        val fk = ForwardKinematicsSolver()
        val engine = VerifiedOneMicronIkEngine(stored, fk)
        val solver = InverseKinematicsSolver(fk, stored.solverConfig)
        val rawErrors = mutableListOf<Double>()
        val refinedErrors = mutableListOf<Double>()
        val baselineErrors = mutableListOf<Double>()
        val observations = mutableListOf<OneMicronVerificationObservation>()
        var rawSuccess = 0
        var refinedSuccess = 0
        var baselineSuccess = 0
        var pipelineSuccess = 0
        var refinedIterations = 0L
        var baselineIterations = 0L
        var inferenceNanos = 0L
        var pipelineIterations = 0L
        val rawCumulativeError = CompensatedSum()
        val protectedCumulativeError = CompensatedSum()

        for ((position, sampleIndex) in indices.withIndex()) {
            if (cancelled()) break
            val sample = dataset.source.samples[sampleIndex]
            val robot = dataset.source.robots[sample.robotIndex]
            val inferenceStart = System.nanoTime()
            val proposal = engine.predictState(robot, sample.seedState, sample.target)
            inferenceNanos += System.nanoTime() - inferenceStart
            val rawError = engine.cartesianError(robot, proposal, sample.target)
            rawErrors += rawError
            rawCumulativeError.add(rawError.finiteOrPenalty(robot))
            if (OneMicronVerificationCriterion.acceptsPositionResidual(rawError)) rawSuccess++

            var sampleRefinementIterations = 0
            var refinedCertified = false
            var refinedErrorForPipeline = rawError
            if (OneMicronVerificationCriterion.acceptsPositionResidual(rawError)) {
                refinedSuccess++
                refinedCertified = true
                refinedErrors += rawError
            } else {
                val refined = solver.solve(robot, proposal, sample.target)
                val verifiedError = engine.cartesianError(robot, refined.state, sample.target)
                sampleRefinementIterations = refined.iterations
                refinedIterations += refined.iterations
                pipelineIterations += refined.iterations
                refinedErrors += verifiedError
                refinedErrorForPipeline = verifiedError
                if (refined.isCertified(verifiedError)) {
                    refinedSuccess++
                    refinedCertified = true
                }
            }

            val baseline = solver.solve(robot, sample.seedState, sample.target)
            val baselineError = engine.cartesianError(robot, baseline.state, sample.target)
            baselineIterations += baseline.iterations
            baselineErrors += baselineError
            val baselineCertified = baseline.isCertified(baselineError)
            if (baselineCertified) baselineSuccess++
            val fallbackIterations = if (!refinedCertified) baseline.iterations else 0
            pipelineIterations += fallbackIterations
            val path = when {
                OneMicronVerificationCriterion.acceptsPositionResidual(rawError) -> VerifiedIkPath.NEURAL_DIRECT
                refinedCertified -> VerifiedIkPath.NEURAL_REFINED
                baselineCertified -> VerifiedIkPath.DETERMINISTIC_FALLBACK
                else -> VerifiedIkPath.FAILED
            }
            val finalResidual = when (path) {
                VerifiedIkPath.NEURAL_DIRECT -> rawError
                VerifiedIkPath.NEURAL_REFINED -> refinedErrorForPipeline
                VerifiedIkPath.DETERMINISTIC_FALLBACK -> baselineError
                VerifiedIkPath.FAILED -> listOf(rawError, refinedErrorForPipeline, baselineError)
                    .filter(Double::isFinite).minOrNull() ?: Double.POSITIVE_INFINITY
            }
            if (path != VerifiedIkPath.FAILED) pipelineSuccess++
            protectedCumulativeError.add(finalResidual.finiteOrPenalty(robot))
            observations += OneMicronVerificationObservation(sample.sourceRowIndex, sample.robotFingerprint, path,
                rawError, if (sampleRefinementIterations > 0 || rawError > ONE_MICRON_METERS) refinedErrorForPipeline else Double.NaN,
                baselineError, finalResidual, baselineCertified, sampleRefinementIterations,
                fallbackIterations, baseline.iterations)
            if (position == 0 || (position + 1) % 25 == 0 || position == indices.lastIndex) progress(position + 1)
        }
        val count = rawErrors.size.coerceAtLeast(1)
        val rawCumulative = rawCumulativeError.value
        val protectedCumulative = protectedCumulativeError.value
        val avoidedPercent =
            if (rawCumulative > 0.0) {
                ((rawCumulative - protectedCumulative) / rawCumulative * 100.0).coerceAtMost(100.0)
            } else 0.0
        val baselineMeanIterations = baselineIterations.toDouble() / count
        val pipelineMeanIterations = pipelineIterations.toDouble() / count
        return OneMicronIkVerificationMetrics(
            samples = rawErrors.size,
            observations = observations,
            rawNeuralSuccessRate = rawSuccess.toDouble() / count,
            neuralThenRefineSuccessRate = refinedSuccess.toDouble() / count,
            deterministicBaselineSuccessRate = baselineSuccess.toDouble() / count,
            rawMedianErrorMeters = rawErrors.finiteQuantile(0.50),
            rawP95ErrorMeters = rawErrors.finiteQuantile(0.95),
            refinedMedianErrorMeters = refinedErrors.finiteQuantile(0.50),
            baselineMedianErrorMeters = baselineErrors.finiteQuantile(0.50),
            meanRefinedIterations = refinedIterations.toDouble() / count,
            meanBaselineIterations = baselineMeanIterations,
            meanNeuralInferenceNanos = inferenceNanos.toDouble() / count,
            verifiedPipelineSuccessRate = pipelineSuccess.toDouble() / count,
            rawCumulativeErrorMeters = rawCumulative,
            protectedCumulativeErrorMeters = protectedCumulative,
            cumulativeErrorAvoidedPercent = avoidedPercent,
            meanPipelineIterations = pipelineMeanIterations,
            iterationSavingsPercent =
                if (baselineMeanIterations > 0.0) {
                    (baselineMeanIterations - pipelineMeanIterations) / baselineMeanIterations * 100.0
                } else 0.0
        )
    }

    private fun com.robotkinematicslab.mobile.domain.result.IKResult.isCertified(independentError: Double): Boolean =
        converged && (status == IKStatus.SUCCESS || status == IKStatus.SUCCESS_WITH_WARNING) &&
            OneMicronVerificationCriterion.acceptsPositionResidual(independentError)

    private fun List<Double>.finiteQuantile(fraction: Double): Double {
        val sorted = filter(Double::isFinite).sorted()
        if (sorted.isEmpty()) return Double.NaN
        return sorted[((sorted.size - 1) * fraction).toInt().coerceIn(sorted.indices)]
    }

    private fun Double.finiteOrPenalty(robot: com.robotkinematicslab.mobile.domain.RobotDefinition?): Double =
        if (isFinite() && this >= 0.0) this else {
            robot?.dhParameters?.sumOf { kotlin.math.hypot(it.a, it.d) }?.coerceAtLeast(1.0) ?: 1.0
        }

    private fun minimumFiniteOrPenalty(
        first: Double,
        second: Double,
        robot: com.robotkinematicslab.mobile.domain.RobotDefinition
    ): Double {
        val finite = listOf(first, second).filter { it.isFinite() && it >= 0.0 }
        return finite.minOrNull() ?: Double.NaN.finiteOrPenalty(robot)
    }

    /** Neumaier compensated accumulation keeps 100k+ residual summaries reproducible. */
    private class CompensatedSum {
        private var sum = 0.0
        private var correction = 0.0

        val value: Double
            get() = sum + correction

        fun add(value: Double) {
            require(value.isFinite() && value >= 0.0)
            val next = sum + value
            correction += if (kotlin.math.abs(sum) >= kotlin.math.abs(value)) {
                (sum - next) + value
            } else {
                (value - next) + sum
            }
            sum = next
        }
    }

    private fun validate(config: OneMicronIkTrainingConfig) {
        require(config.runName.isNotBlank())
        require(config.maximumRows >= 30)
        require(config.requestedMaximumRows >= config.maximumRows)
        require(config.sourceDatasetRows == -1L || config.sourceDatasetRows >= config.maximumRows.toLong())
        config.datasetScientificFingerprint?.let { fingerprint ->
            require(com.robotkinematicslab.mobile.dataset.DatasetScientificContract.isValidFingerprint(fingerprint))
        }
        require(config.datasetRobotIds.distinct().size == config.datasetRobotIds.size)
        require(config.epochs in 1..1_000)
        require(config.batchSize in 1..100_000)
        require(config.hiddenUnits in 4..1_024)
        require(config.workerCount in 1..256)
        require(config.earlyStoppingPatience > 0)
        require(config.verificationSampleLimit > 0)
        require(config.learningRate.isFinite() && config.learningRate in 1e-6..1.0) {
            "Learning rate must be finite and between 1e-6 and 1."
        }
        require(config.l2Regularization.isFinite() && config.l2Regularization in 0.0..1.0) {
            "L2 regularization must be finite and between 0 and 1."
        }
        require(config.maximumWorkingMemoryBytes > 0L) {
            "The training working-memory budget must be positive."
        }
        require(
            OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                featureCount = config.resolvedFeatureSelection.includedFeatureNames.size,
                rows = config.maximumRows,
                hiddenUnits = config.hiddenUnits,
                workerCount = config.workerCount,
                batchSize = config.batchSize
            ) <=
                config.maximumWorkingMemoryBytes
        ) {
            "The requested rows, model and worker buffers exceed the safe working-memory budget. " +
                "Reduce the row count, hidden units or batch workers, or raise the app budget in Settings."
        }
        require(config.solverConfig.maxIterations > 0) {
            "The verification solver iteration limit must be positive."
        }
        require(
            config.solverConfig.tolerance.isFinite() &&
                config.solverConfig.tolerance > 0.0 &&
                config.solverConfig.tolerance <= ONE_MICRON_METERS
        ) {
            "The verification solver tolerance must be 1e-6 metres or stricter."
        }
        require(config.solverConfig.damping.isFinite() && config.solverConfig.damping > 0.0) {
            "The verification solver damping must be finite and positive."
        }
        require(config.solverConfig.maxStep.isFinite() && config.solverConfig.maxStep > 0.0) {
            "The verification solver maximum step must be finite and positive."
        }
    }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw OneMicronIkTrainingCancelledException()
        }
    }

    private fun buildRunId(name: String, started: Long): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(started))
        val safe = name.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "one-micron" }
        return "$timestamp-$safe"
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.6g", value)
}
