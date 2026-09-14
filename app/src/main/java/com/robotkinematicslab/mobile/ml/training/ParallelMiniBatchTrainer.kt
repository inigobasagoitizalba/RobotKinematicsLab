package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerAllocation
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ExecutionException
import kotlin.math.ln

/**
 * Computes independent, read-only gradient shards and reduces them in submission order.
 * The fixed reduction order makes a given seed + worker count reproducible.
 */
internal class ParallelMiniBatchTrainer(
    private val executor: ExecutorService,
    private val runtimeWorkerLimit: () -> Int,
    private val cancellationRequested: () -> Boolean,
    private val workCycleReporter: ComputeWorkCycleReporter,
    private val onBatchWorkers: (Int) -> Unit = {}
) {
    private var linearBuffers: List<LinearGradient> = emptyList()
    private var mlpBuffers: List<MlpGradient> = emptyList()
    private var linearAggregate: LinearGradient? = null
    private var mlpAggregate: MlpGradient? = null

    fun trainLinearEpoch(
        model: LocalClassifierModel,
        optimizer: AdamOptimizer,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        config: LocalTrainingConfig
    ): Double {
        var weightedLoss = 0.0
        var lossWeight = 0.0
        var batchStart = 0
        while (batchStart < indices.size) {
            ensureNotCancelled()
            val batchEnd = (batchStart + config.batchSize).coerceAtMost(indices.size)
            val workers = resolveWorkers(config.workerCount, batchEnd - batchStart)
            ensureLinearBuffers(model, workers)
            val partials =
                computeOrderedShards(batchStart, batchEnd, workers) { worker, from, until ->
                    linearGradient(model, dataset, indices, from, until, linearBuffers[worker])
                }
            val aggregate = requireNotNull(linearAggregate)
            val gradientWeights = aggregate.weights.apply { fill(0f) }
            val gradientBiases = aggregate.biases.apply { fill(0f) }
            var batchWeight = 0f
            partials.forEach { partial ->
                addInPlace(gradientWeights, partial.weights)
                addInPlace(gradientBiases, partial.biases)
                batchWeight += partial.weight
                weightedLoss += partial.loss
                lossWeight += partial.weight
            }
            val normalizer = batchWeight.coerceAtLeast(1e-8f)
            for (index in gradientWeights.indices) {
                gradientWeights[index] =
                    gradientWeights[index] / normalizer +
                        config.l2Regularization.toFloat() * model.inputWeights[index]
            }
            for (index in gradientBiases.indices) gradientBiases[index] /= normalizer
            requireFiniteUpdate(model.inputWeights, model.outputBiases, gradientWeights, gradientBiases)
            optimizer.updateLinear(model, gradientWeights, gradientBiases, config.learningRate)
            batchStart = batchEnd
        }
        return weightedLoss / lossWeight.coerceAtLeast(1e-12)
    }

    fun trainMlpEpoch(
        model: LocalClassifierModel,
        optimizer: AdamOptimizer,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        config: LocalTrainingConfig
    ): Double {
        var weightedLoss = 0.0
        var lossWeight = 0.0
        var batchStart = 0
        while (batchStart < indices.size) {
            ensureNotCancelled()
            val batchEnd = (batchStart + config.batchSize).coerceAtMost(indices.size)
            val workers = resolveWorkers(config.workerCount, batchEnd - batchStart)
            ensureMlpBuffers(model, workers)
            val partials =
                computeOrderedShards(batchStart, batchEnd, workers) { worker, from, until ->
                    mlpGradient(model, dataset, indices, from, until, mlpBuffers[worker])
                }
            val aggregate = requireNotNull(mlpAggregate)
            val gradientInput = aggregate.input.apply { fill(0f) }
            val gradientHiddenBias = aggregate.hiddenBias.apply { fill(0f) }
            val gradientOutput = aggregate.output.apply { fill(0f) }
            val gradientOutputBias = aggregate.outputBias.apply { fill(0f) }
            var batchWeight = 0f
            partials.forEach { partial ->
                addInPlace(gradientInput, partial.input)
                addInPlace(gradientHiddenBias, partial.hiddenBias)
                addInPlace(gradientOutput, partial.output)
                addInPlace(gradientOutputBias, partial.outputBias)
                batchWeight += partial.weight
                weightedLoss += partial.loss
                lossWeight += partial.weight
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
                model,
                gradientInput,
                gradientHiddenBias,
                gradientOutput,
                gradientOutputBias,
                config.learningRate
            )
            batchStart = batchEnd
        }
        return weightedLoss / lossWeight.coerceAtLeast(1e-12)
    }

    private fun linearGradient(
        model: LocalClassifierModel,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        from: Int,
        until: Int,
        buffer: LinearGradient
    ): LinearGradient {
        val classCount = model.classCount
        val featureCount = model.inputFeatureCount
        val weights = buffer.weights.apply { fill(0f) }
        val biases = buffer.biases.apply { fill(0f) }
        val logits = buffer.logits
        var loss = 0.0
        var weight = 0f
        for (position in from until until) {
            if ((position - from) % CANCELLATION_CHECK_INTERVAL == 0) ensureNotCancelled()
            val sampleIndex = indices[position]
            val features = dataset.normalizedFeatures[sampleIndex]
            val truth = dataset.source.samples[sampleIndex].labelIndex
            val classWeight = dataset.classWeights[truth]
            weight += classWeight
            for (classIndex in 0 until classCount) {
                var value = model.outputBiases[classIndex]
                val offset = classIndex * featureCount
                for (featureIndex in 0 until featureCount) {
                    value += model.inputWeights[offset + featureIndex] * features[featureIndex]
                }
                logits[classIndex] = value
            }
            LocalClassifierModel.softmaxInPlace(logits)
            loss -= classWeight * ln(logits[truth].toDouble().coerceAtLeast(1e-12))
            for (classIndex in 0 until classCount) {
                val delta = (logits[classIndex] - if (classIndex == truth) 1f else 0f) * classWeight
                biases[classIndex] += delta
                val offset = classIndex * featureCount
                for (featureIndex in 0 until featureCount) {
                    weights[offset + featureIndex] += delta * features[featureIndex]
                }
            }
        }
        buffer.weight = weight
        buffer.loss = loss
        return buffer
    }

    private fun mlpGradient(
        model: LocalClassifierModel,
        dataset: PreparedTrainingDataset,
        indices: IntArray,
        from: Int,
        until: Int,
        buffer: MlpGradient
    ): MlpGradient {
        val classCount = model.classCount
        val featureCount = model.inputFeatureCount
        val hiddenCount = model.hiddenUnitCount
        val gradientInput = buffer.input.apply { fill(0f) }
        val gradientHiddenBias = buffer.hiddenBias.apply { fill(0f) }
        val gradientOutput = buffer.output.apply { fill(0f) }
        val gradientOutputBias = buffer.outputBias.apply { fill(0f) }
        val hiddenPreActivation = buffer.hiddenPreActivation
        val hidden = buffer.hidden
        val logits = buffer.logits
        val outputDeltas = buffer.outputDeltas
        var loss = 0.0
        var weight = 0f

        for (position in from until until) {
            if ((position - from) % CANCELLATION_CHECK_INTERVAL == 0) ensureNotCancelled()
            val sampleIndex = indices[position]
            val features = dataset.normalizedFeatures[sampleIndex]
            val truth = dataset.source.samples[sampleIndex].labelIndex
            val classWeight = dataset.classWeights[truth]
            weight += classWeight
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
            loss -= classWeight * ln(logits[truth].toDouble().coerceAtLeast(1e-12))
            for (classIndex in 0 until classCount) {
                val delta = (logits[classIndex] - if (classIndex == truth) 1f else 0f) * classWeight
                outputDeltas[classIndex] = delta
                gradientOutputBias[classIndex] += delta
                val outputOffset = classIndex * hiddenCount
                for (hiddenIndex in 0 until hiddenCount) {
                    gradientOutput[outputOffset + hiddenIndex] += delta * hidden[hiddenIndex]
                }
            }
            for (hiddenIndex in 0 until hiddenCount) {
                var hiddenGradient = 0f
                for (classIndex in 0 until classCount) {
                    hiddenGradient +=
                        model.outputWeights[classIndex * hiddenCount + hiddenIndex] * outputDeltas[classIndex]
                }
                if (hiddenPreActivation[hiddenIndex] <= 0f) hiddenGradient = 0f
                gradientHiddenBias[hiddenIndex] += hiddenGradient
                val inputOffset = hiddenIndex * featureCount
                for (featureIndex in 0 until featureCount) {
                    gradientInput[inputOffset + featureIndex] += hiddenGradient * features[featureIndex]
                }
            }
        }
        buffer.weight = weight
        buffer.loss = loss
        return buffer
    }

    private fun <T> computeOrderedShards(
        batchStart: Int,
        batchEnd: Int,
        workers: Int,
        task: (Int, Int, Int) -> T
    ): List<T> {
        val sampleCount = batchEnd - batchStart
        require(workers in 1..sampleCount)
        val chunk = (sampleCount + workers - 1) / workers
        onBatchWorkers((sampleCount + chunk - 1) / chunk)
        if (workers == 1) return listOf(task(0, batchStart, batchEnd))
        val chunkSize = (sampleCount + workers - 1) / workers
        val futures =
            (0 until workers).mapNotNull { worker ->
                val from = batchStart + worker * chunkSize
                val until = minOf(from + chunkSize, batchEnd)
                if (from >= until) {
                    null
                } else {
                    executor.submit(
                        Callable {
                            val started = workCycleReporter.startCycle()
                            try {
                                task(worker, from, until)
                            } finally {
                                workCycleReporter.finishCycle(started)
                            }
                        }
                    )
                }
            }
        return try {
            futures.map { it.get() }
        } catch (_: InterruptedException) {
            futures.forEach { it.cancel(true) }
            Thread.currentThread().interrupt()
            throw LocalTrainingCancelledException()
        } catch (error: ExecutionException) {
            futures.forEach { it.cancel(true) }
            val cause = error.cause
            if (cause is RuntimeException) throw cause
            throw IllegalStateException("Parallel training worker failed.", cause)
        }
    }

    private fun resolveWorkers(configuredWorkers: Int, sampleCount: Int): Int =
        ComputeWorkerAllocation.resolve(
            configuredWorkerCount = configuredWorkers,
            runtimeWorkerLimit = runtimeWorkerLimit(),
            availableWorkItems = sampleCount
        )

    private fun ensureNotCancelled() {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw LocalTrainingCancelledException()
        }
    }

    private fun addInPlace(destination: FloatArray, source: FloatArray) {
        for (index in destination.indices) destination[index] += source[index]
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

    private fun requireFiniteUpdate(vararg arrays: FloatArray) {
        require(arrays.all { values -> values.all(Float::isFinite) }) {
            "Parallel training update contains a non-finite parameter or gradient; model mutation was blocked."
        }
    }

    private fun ensureLinearBuffers(
        model: LocalClassifierModel,
        workerCount: Int
    ) {
        if (linearBuffers.size != workerCount || linearBuffers.firstOrNull()?.weights?.size != model.inputWeights.size) {
            val previous = linearBuffers
            linearBuffers =
                List(workerCount) { index ->
                    previous.getOrNull(index)
                        ?.takeIf {
                            it.weights.size == model.inputWeights.size &&
                                it.biases.size == model.outputBiases.size &&
                                it.logits.size == model.classCount
                        }
                        ?: LinearGradient(
                            weights = FloatArray(model.inputWeights.size),
                            biases = FloatArray(model.outputBiases.size),
                            logits = FloatArray(model.classCount)
                        )
                }
            if (
                linearAggregate?.weights?.size != model.inputWeights.size ||
                linearAggregate?.biases?.size != model.outputBiases.size
            ) {
                linearAggregate = LinearGradient(
                    weights = FloatArray(model.inputWeights.size),
                    biases = FloatArray(model.outputBiases.size),
                    logits = FloatArray(0)
                )
            }
        }
    }

    private fun ensureMlpBuffers(
        model: LocalClassifierModel,
        workerCount: Int
    ) {
        if (mlpBuffers.size != workerCount || mlpBuffers.firstOrNull()?.input?.size != model.inputWeights.size) {
            val previous = mlpBuffers
            mlpBuffers =
                List(workerCount) { index ->
                    previous.getOrNull(index)
                        ?.takeIf {
                            it.input.size == model.inputWeights.size &&
                                it.hiddenBias.size == model.hiddenBiases.size &&
                                it.output.size == model.outputWeights.size &&
                                it.outputBias.size == model.outputBiases.size
                        }
                        ?: MlpGradient(
                            input = FloatArray(model.inputWeights.size),
                            hiddenBias = FloatArray(model.hiddenBiases.size),
                            output = FloatArray(model.outputWeights.size),
                            outputBias = FloatArray(model.outputBiases.size),
                            hiddenPreActivation = FloatArray(model.hiddenUnitCount),
                            hidden = FloatArray(model.hiddenUnitCount),
                            logits = FloatArray(model.classCount),
                            outputDeltas = FloatArray(model.classCount)
                        )
                }
            if (
                mlpAggregate?.input?.size != model.inputWeights.size ||
                mlpAggregate?.hiddenBias?.size != model.hiddenBiases.size ||
                mlpAggregate?.output?.size != model.outputWeights.size ||
                mlpAggregate?.outputBias?.size != model.outputBiases.size
            ) {
                mlpAggregate = MlpGradient(
                    input = FloatArray(model.inputWeights.size),
                    hiddenBias = FloatArray(model.hiddenBiases.size),
                    output = FloatArray(model.outputWeights.size),
                    outputBias = FloatArray(model.outputBiases.size),
                    hiddenPreActivation = FloatArray(0),
                    hidden = FloatArray(0),
                    logits = FloatArray(0),
                    outputDeltas = FloatArray(0)
                )
            }
        }
    }

    private data class LinearGradient(
        val weights: FloatArray,
        val biases: FloatArray,
        val logits: FloatArray,
        var weight: Float = 0f,
        var loss: Double = 0.0
    )

    private data class MlpGradient(
        val input: FloatArray,
        val hiddenBias: FloatArray,
        val output: FloatArray,
        val outputBias: FloatArray,
        val hiddenPreActivation: FloatArray,
        val hidden: FloatArray,
        val logits: FloatArray,
        val outputDeltas: FloatArray,
        var weight: Float = 0f,
        var loss: Double = 0.0
    )

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL = 64
    }
}
