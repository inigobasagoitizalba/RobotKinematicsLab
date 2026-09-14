package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerThreadFactory
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import com.robotkinematicslab.mobile.performance.compute.NoOpComputeWorkCycleReporter
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal data class CandidateSpecification(
    val id: String,
    val kind: TrainingModelKind,
    val hiddenUnits: Int
)

internal data class CandidateTrainingResult(
    val specification: CandidateSpecification,
    val model: LocalClassifierModel,
    val bestEpoch: Int,
    val validationMetrics: ClassificationMetrics,
    val durationMillis: Long,
    val workerBatchCounts: Map<Int, Long> = emptyMap()
)

internal class LocalModelTrainer(
    private val workCycleReporterFactory: () -> ComputeWorkCycleReporter = {
        NoOpComputeWorkCycleReporter
    }
) {

    fun train(
        dataset: PreparedTrainingDataset,
        specification: CandidateSpecification,
        config: LocalTrainingConfig,
        nextGlobalIteration: () -> Int,
        cancellationRequested: () -> Boolean,
        runtimeWorkerLimit: () -> Int,
        onIteration: (TrainingIterationMetrics) -> Unit
    ): CandidateTrainingResult {
        require(specification.kind != TrainingModelKind.AUTOMATIC)

        val startedAt = System.currentTimeMillis()
        var model = initializeModel(dataset, specification, config.randomSeed)
        var bestModel = model.deepCopy()
        var bestEpoch = 0
        var bestValidation =
            ClassificationEvaluator.evaluate(
                model = model,
                dataset = dataset,
                indices = dataset.split.validationIndices
            )
        var epochsWithoutImprovement = 0

        val trainIndices = dataset.split.trainIndices.clone()
        val random =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(
                    config.randomSeed,
                    dataset.source.profile.name,
                    specification.id,
                    "training-order"
                )
            )

        val optimizer = AdamOptimizer(model)
        val workerBatchCounts = sortedMapOf<Int, Long>()
        val executor = createExecutor(config.workerCount)
        val workCycleReporter = workCycleReporterFactory()
        val parallelTrainer =
            executor?.let {
                ParallelMiniBatchTrainer(
                    executor = it,
                    runtimeWorkerLimit = runtimeWorkerLimit,
                    cancellationRequested = cancellationRequested,
                    workCycleReporter = workCycleReporter,
                    onBatchWorkers = { workers -> workerBatchCounts[workers] = (workerBatchCounts[workers] ?: 0L) + 1L }
                )
            }

        try {
            for (epoch in 1..config.epochs) {
                ensureNotCancelled(cancellationRequested)
                shuffle(trainIndices, random)
                if (parallelTrainer == null) {
                    val batches = (trainIndices.size.toLong() + config.batchSize - 1) / config.batchSize
                    workerBatchCounts[1] = (workerBatchCounts[1] ?: 0L) + batches
                }
                val trainingLoss =
                    when (specification.kind) {
                        TrainingModelKind.LINEAR_SOFTMAX ->
                            parallelTrainer?.trainLinearEpoch(model, optimizer, dataset, trainIndices, config)
                                ?: trainLinearEpoch(
                                    model,
                                    optimizer,
                                    dataset,
                                    trainIndices,
                                    config,
                                    cancellationRequested
                                )
                        TrainingModelKind.COMPACT_MLP ->
                            parallelTrainer?.trainMlpEpoch(model, optimizer, dataset, trainIndices, config)
                                ?: trainMlpEpoch(
                                    model,
                                    optimizer,
                                    dataset,
                                    trainIndices,
                                    config,
                                    cancellationRequested
                                )
                        TrainingModelKind.AUTOMATIC -> error("Automatic is a selection mode, not a model.")
                    }

                val validation =
                    ClassificationEvaluator.evaluate(
                        model = model,
                        dataset = dataset,
                        indices = dataset.split.validationIndices
                    )
                onIteration(
                    TrainingIterationMetrics(
                        globalIteration = nextGlobalIteration(),
                        profile = dataset.source.profile,
                        candidateId = specification.id,
                        modelKind = specification.kind,
                        hiddenUnits = specification.hiddenUnits,
                        epoch = epoch,
                        trainingLoss = trainingLoss,
                        validationMetrics = validation,
                        elapsedMillis = System.currentTimeMillis() - startedAt
                    )
                )

                if (isBetter(validation, bestValidation)) {
                    bestValidation = validation
                    bestModel = model.deepCopy()
                    bestEpoch = epoch
                    epochsWithoutImprovement = 0
                } else {
                    epochsWithoutImprovement++
                    if (epochsWithoutImprovement >= config.earlyStoppingPatience) break
                }
            }
        } finally {
            executor?.shutdownNow()
            workCycleReporter.close()
        }

        model = bestModel
        return CandidateTrainingResult(
            specification = specification,
            model = model,
            bestEpoch = bestEpoch,
            validationMetrics = bestValidation,
            durationMillis = System.currentTimeMillis() - startedAt,
            workerBatchCounts = workerBatchCounts.toMap()
        )
    }

    private fun initializeModel(
        dataset: PreparedTrainingDataset,
        specification: CandidateSpecification,
        seed: Int
    ): LocalClassifierModel {
        val inputCount = dataset.source.featureNames.size
        val classCount = dataset.classWeights.size
        if (specification.kind == TrainingModelKind.LINEAR_SOFTMAX) {
            return LocalClassifierModel(
                kind = specification.kind,
                inputFeatureCount = inputCount,
                classCount = classCount,
                hiddenUnitCount = 0,
                inputWeights = FloatArray(inputCount * classCount),
                hiddenBiases = FloatArray(0),
                outputWeights = FloatArray(0),
                outputBiases = FloatArray(classCount)
            )
        }

        val random =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(
                    seed,
                    dataset.source.profile.name,
                    specification.id,
                    "weight-initialization"
                )
            )
        val hidden = specification.hiddenUnits
        val inputLimit = sqrt(6.0 / (inputCount + hidden))
        val outputLimit = sqrt(6.0 / (hidden + classCount))
        return LocalClassifierModel(
            kind = specification.kind,
            inputFeatureCount = inputCount,
            classCount = classCount,
            hiddenUnitCount = hidden,
            inputWeights =
                FloatArray(inputCount * hidden) {
                    random.nextDouble(-inputLimit, inputLimit).toFloat()
                },
            hiddenBiases = FloatArray(hidden),
            outputWeights =
                FloatArray(hidden * classCount) {
                    random.nextDouble(-outputLimit, outputLimit).toFloat()
                },
            outputBiases = FloatArray(classCount)
        )
    }

    private fun trainLinearEpoch(
        model: LocalClassifierModel,
        optimizer: AdamOptimizer,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        config: LocalTrainingConfig,
        cancellationRequested: () -> Boolean
    ): Double {
        val classCount = model.classCount
        val featureCount = model.inputFeatureCount
        val gradientWeights = FloatArray(model.inputWeights.size)
        val gradientBiases = FloatArray(model.outputBiases.size)
        val logits = FloatArray(classCount)
        var weightedLoss = 0.0
        var lossWeight = 0.0

        var batchStart = 0
        while (batchStart < indices.size) {
            ensureNotCancelled(cancellationRequested)
            gradientWeights.fill(0f)
            gradientBiases.fill(0f)
            val batchEnd = (batchStart + config.batchSize).coerceAtMost(indices.size)
            var batchWeight = 0f

            for (position in batchStart until batchEnd) {
                if ((position - batchStart) % CANCELLATION_CHECK_INTERVAL == 0) {
                    ensureNotCancelled(cancellationRequested)
                }
                val sampleIndex = indices[position]
                val features = dataset.normalizedFeatures[sampleIndex]
                val truth = dataset.source.samples[sampleIndex].labelIndex
                val classWeight = dataset.classWeights[truth]
                batchWeight += classWeight

                for (classIndex in 0 until classCount) {
                    var value = model.outputBiases[classIndex]
                    val offset = classIndex * featureCount
                    for (featureIndex in 0 until featureCount) {
                        value += model.inputWeights[offset + featureIndex] * features[featureIndex]
                    }
                    logits[classIndex] = value
                }
                LocalClassifierModel.softmaxInPlace(logits)
                weightedLoss -= classWeight * ln(logits[truth].toDouble().coerceAtLeast(1e-12))
                lossWeight += classWeight

                for (classIndex in 0 until classCount) {
                    val delta = (logits[classIndex] - if (classIndex == truth) 1f else 0f) * classWeight
                    gradientBiases[classIndex] += delta
                    val offset = classIndex * featureCount
                    for (featureIndex in 0 until featureCount) {
                        gradientWeights[offset + featureIndex] += delta * features[featureIndex]
                    }
                }
            }

            val normalizer = batchWeight.coerceAtLeast(1e-8f)
            for (index in gradientWeights.indices) {
                gradientWeights[index] =
                    gradientWeights[index] / normalizer +
                        config.l2Regularization.toFloat() * model.inputWeights[index]
            }
            for (index in gradientBiases.indices) gradientBiases[index] /= normalizer
            requireFiniteUpdate(
                model.inputWeights,
                model.outputBiases,
                gradientWeights,
                gradientBiases
            )
            optimizer.updateLinear(
                model = model,
                inputGradient = gradientWeights,
                outputBiasGradient = gradientBiases,
                learningRate = config.learningRate
            )
            batchStart = batchEnd
        }

        return weightedLoss / lossWeight.coerceAtLeast(1e-12)
    }

    private fun trainMlpEpoch(
        model: LocalClassifierModel,
        optimizer: AdamOptimizer,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        config: LocalTrainingConfig,
        cancellationRequested: () -> Boolean
    ): Double {
        val classCount = model.classCount
        val featureCount = model.inputFeatureCount
        val hiddenCount = model.hiddenUnitCount
        val gradientInput = FloatArray(model.inputWeights.size)
        val gradientHiddenBias = FloatArray(model.hiddenBiases.size)
        val gradientOutput = FloatArray(model.outputWeights.size)
        val gradientOutputBias = FloatArray(model.outputBiases.size)
        val hiddenPreActivation = FloatArray(hiddenCount)
        val hidden = FloatArray(hiddenCount)
        val logits = FloatArray(classCount)
        var weightedLoss = 0.0
        var lossWeight = 0.0

        var batchStart = 0
        while (batchStart < indices.size) {
            ensureNotCancelled(cancellationRequested)
            gradientInput.fill(0f)
            gradientHiddenBias.fill(0f)
            gradientOutput.fill(0f)
            gradientOutputBias.fill(0f)
            val batchEnd = (batchStart + config.batchSize).coerceAtMost(indices.size)
            var batchWeight = 0f

            for (position in batchStart until batchEnd) {
                if ((position - batchStart) % CANCELLATION_CHECK_INTERVAL == 0) {
                    ensureNotCancelled(cancellationRequested)
                }
                val sampleIndex = indices[position]
                val features = dataset.normalizedFeatures[sampleIndex]
                val truth = dataset.source.samples[sampleIndex].labelIndex
                val classWeight = dataset.classWeights[truth]
                batchWeight += classWeight

                for (hiddenIndex in 0 until hiddenCount) {
                    var value = model.hiddenBiases[hiddenIndex]
                    val offset = hiddenIndex * featureCount
                    for (featureIndex in 0 until featureCount) {
                        value += model.inputWeights[offset + featureIndex] * features[featureIndex]
                    }
                    hiddenPreActivation[hiddenIndex] = value
                    hidden[hiddenIndex] = if (value > 0f) value else 0f
                }
                for (classIndex in 0 until classCount) {
                    var value = model.outputBiases[classIndex]
                    val offset = classIndex * hiddenCount
                    for (hiddenIndex in 0 until hiddenCount) {
                        value += model.outputWeights[offset + hiddenIndex] * hidden[hiddenIndex]
                    }
                    logits[classIndex] = value
                }
                LocalClassifierModel.softmaxInPlace(logits)
                weightedLoss -= classWeight * ln(logits[truth].toDouble().coerceAtLeast(1e-12))
                lossWeight += classWeight

                for (classIndex in 0 until classCount) {
                    val deltaOutput =
                        (logits[classIndex] - if (classIndex == truth) 1f else 0f) * classWeight
                    gradientOutputBias[classIndex] += deltaOutput
                    val outputOffset = classIndex * hiddenCount
                    for (hiddenIndex in 0 until hiddenCount) {
                        gradientOutput[outputOffset + hiddenIndex] += deltaOutput * hidden[hiddenIndex]
                    }
                }

                for (hiddenIndex in 0 until hiddenCount) {
                    var hiddenGradient = 0f
                    for (classIndex in 0 until classCount) {
                        val deltaOutput =
                            (logits[classIndex] - if (classIndex == truth) 1f else 0f) * classWeight
                        hiddenGradient +=
                            model.outputWeights[classIndex * hiddenCount + hiddenIndex] * deltaOutput
                    }
                    if (hiddenPreActivation[hiddenIndex] <= 0f) hiddenGradient = 0f
                    gradientHiddenBias[hiddenIndex] += hiddenGradient
                    val inputOffset = hiddenIndex * featureCount
                    for (featureIndex in 0 until featureCount) {
                        gradientInput[inputOffset + featureIndex] += hiddenGradient * features[featureIndex]
                    }
                }
            }

            val normalizer = batchWeight.coerceAtLeast(1e-8f)
            normalizeAndRegularize(gradientInput, model.inputWeights, normalizer, config.l2Regularization)
            normalizeAndRegularize(gradientOutput, model.outputWeights, normalizer, config.l2Regularization)
            for (index in gradientHiddenBias.indices) gradientHiddenBias[index] /= normalizer
            for (index in gradientOutputBias.indices) gradientOutputBias[index] /= normalizer

            requireFiniteUpdate(
                model.inputWeights,
                model.hiddenBiases,
                model.outputWeights,
                model.outputBiases,
                gradientInput,
                gradientHiddenBias,
                gradientOutput,
                gradientOutputBias
            )
            optimizer.updateMlp(
                model = model,
                inputGradient = gradientInput,
                hiddenBiasGradient = gradientHiddenBias,
                outputGradient = gradientOutput,
                outputBiasGradient = gradientOutputBias,
                learningRate = config.learningRate
            )
            batchStart = batchEnd
        }

        return weightedLoss / lossWeight.coerceAtLeast(1e-12)
    }

    private fun normalizeAndRegularize(
        gradient: FloatArray,
        parameters: FloatArray,
        normalizer: Float,
        l2: Double
    ) {
        for (index in gradient.indices) {
            gradient[index] = gradient[index] / normalizer + l2.toFloat() * parameters[index]
        }
    }

    private fun shuffle(indices: IntArray, random: ScientificRandom) {
        for (index in indices.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val temporary = indices[index]
            indices[index] = indices[swap]
            indices[swap] = temporary
        }
    }

    private fun isBetter(
        candidate: ClassificationMetrics,
        current: ClassificationMetrics
    ): Boolean {
        val f1Delta = candidate.macroF1 - current.macroF1
        return f1Delta > 1e-12 ||
            (kotlin.math.abs(f1Delta) <= 1e-12 && candidate.logLoss < current.logLoss)
    }

    private fun createExecutor(workerCount: Int): ExecutorService? {
        if (workerCount <= 1) return null
        return Executors.newFixedThreadPool(
            workerCount,
            ComputeWorkerThreadFactory("rkl-training-worker")
        )
    }

    private fun requireFiniteUpdate(vararg arrays: FloatArray) {
        require(arrays.all { values -> values.all(Float::isFinite) }) {
            "Training update contains a non-finite parameter or gradient; model mutation was blocked."
        }
    }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw LocalTrainingCancelledException()
        }
    }

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL = 64
    }
}

internal class AdamOptimizer(model: LocalClassifierModel) {
    private val input = AdamArray(model.inputWeights.size)
    private val hiddenBias = AdamArray(model.hiddenBiases.size)
    private val output = AdamArray(model.outputWeights.size)
    private val outputBias = AdamArray(model.outputBiases.size)

    fun updateInputWeights(parameters: FloatArray, gradient: FloatArray, learningRate: Double) =
        input.update(parameters, gradient, learningRate)

    fun updateHiddenBiases(parameters: FloatArray, gradient: FloatArray, learningRate: Double) =
        hiddenBias.update(parameters, gradient, learningRate)

    fun updateOutputWeights(parameters: FloatArray, gradient: FloatArray, learningRate: Double) =
        output.update(parameters, gradient, learningRate)

    fun updateOutputBiases(parameters: FloatArray, gradient: FloatArray, learningRate: Double) =
        outputBias.update(parameters, gradient, learningRate)

    fun updateLinear(
        model: LocalClassifierModel,
        inputGradient: FloatArray,
        outputBiasGradient: FloatArray,
        learningRate: Double
    ) {
        input.validate(model.inputWeights, inputGradient, learningRate)
        outputBias.validate(model.outputBiases, outputBiasGradient, learningRate)
        input.update(model.inputWeights, inputGradient, learningRate)
        outputBias.update(model.outputBiases, outputBiasGradient, learningRate)
    }

    fun updateMlp(
        model: LocalClassifierModel,
        inputGradient: FloatArray,
        hiddenBiasGradient: FloatArray,
        outputGradient: FloatArray,
        outputBiasGradient: FloatArray,
        learningRate: Double
    ) {
        input.validate(model.inputWeights, inputGradient, learningRate)
        hiddenBias.validate(model.hiddenBiases, hiddenBiasGradient, learningRate)
        output.validate(model.outputWeights, outputGradient, learningRate)
        outputBias.validate(model.outputBiases, outputBiasGradient, learningRate)
        input.update(model.inputWeights, inputGradient, learningRate)
        hiddenBias.update(model.hiddenBiases, hiddenBiasGradient, learningRate)
        output.update(model.outputWeights, outputGradient, learningRate)
        outputBias.update(model.outputBiases, outputBiasGradient, learningRate)
    }
}

private class AdamArray(size: Int) {
    private val firstMoment = FloatArray(size)
    private val secondMoment = FloatArray(size)
    private var step = 0

    fun update(
        parameters: FloatArray,
        gradient: FloatArray,
        learningRate: Double
    ) {
        validate(parameters, gradient, learningRate)
        if (parameters.isEmpty()) return
        val nextStep = step + 1
        val beta1Correction = 1.0 - BETA_1.pow(nextStep)
        val beta2Correction = 1.0 - BETA_2.pow(nextStep)

        step = nextStep
        for (index in parameters.indices) {
            val update = checkedUpdate(
                parameters[index], gradient[index], firstMoment[index], secondMoment[index],
                learningRate, beta1Correction, beta2Correction
            )
            firstMoment[index] = update.firstMoment.toFloat()
            secondMoment[index] = update.secondMoment.toFloat()
            parameters[index] = update.parameter.toFloat()
        }
    }

    fun validate(parameters: FloatArray, gradient: FloatArray, learningRate: Double) {
        require(parameters.size == gradient.size) { "Adam parameter and gradient sizes must match." }
        require(learningRate.isFinite() && learningRate > 0.0) {
            "Adam learning rate must be finite and positive."
        }
        if (parameters.isEmpty()) return
        val nextStep = step + 1
        val beta1Correction = 1.0 - BETA_1.pow(nextStep)
        val beta2Correction = 1.0 - BETA_2.pow(nextStep)
        for (index in parameters.indices) {
            checkedUpdate(
                parameters[index], gradient[index], firstMoment[index], secondMoment[index],
                learningRate, beta1Correction, beta2Correction
            )
        }
    }

    private fun checkedUpdate(
        parameter: Float,
        gradient: Float,
        previousFirst: Float,
        previousSecond: Float,
        learningRate: Double,
        beta1Correction: Double,
        beta2Correction: Double
    ): CheckedAdamUpdate {
        require(parameter.isFinite() && gradient.isFinite() && previousFirst.isFinite() && previousSecond.isFinite()) {
            "Adam received a non-finite parameter, gradient or optimizer moment."
        }
        val gradientD = gradient.toDouble()
        val first = BETA_1 * previousFirst.toDouble() + (1.0 - BETA_1) * gradientD
        val second = BETA_2 * previousSecond.toDouble() + (1.0 - BETA_2) * gradientD * gradientD
        val correctedFirst = first / beta1Correction
        val correctedSecond = second / beta2Correction
        val updated = parameter.toDouble() - learningRate * correctedFirst /
            (sqrt(correctedSecond) + EPSILON)
        require(
            first.isFinite() && second.isFinite() && updated.isFinite() &&
                first in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble() &&
                second in 0.0..Float.MAX_VALUE.toDouble() &&
                updated in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble()
        ) {
            "Adam update exceeded the finite Float range; model mutation was blocked."
        }
        return CheckedAdamUpdate(first, second, updated)
    }

    private data class CheckedAdamUpdate(
        val firstMoment: Double,
        val secondMoment: Double,
        val parameter: Double
    )

    companion object {
        private const val BETA_1 = 0.9
        private const val BETA_2 = 0.999
        private const val EPSILON = 1e-8
    }
}
