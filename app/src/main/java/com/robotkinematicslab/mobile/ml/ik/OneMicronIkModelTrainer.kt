package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerAllocation
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerThreadFactory
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkCycleReporter
import com.robotkinematicslab.mobile.performance.compute.NoOpComputeWorkCycleReporter
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

internal data class OneMicronModelTrainingResult(
    val model: LocalIkRegressionModel,
    val bestEpoch: Int,
    val epochs: List<OneMicronIkEpochMetrics>
)

internal class OneMicronIkModelTrainer(
    private val runtimeWorkerLimit: () -> Int = { Int.MAX_VALUE },
    private val workCycleReporterFactory: () -> ComputeWorkCycleReporter = {
        NoOpComputeWorkCycleReporter
    }
) {

    fun train(
        dataset: PreparedOneMicronIkDataset,
        config: OneMicronIkTrainingConfig,
        cancellationRequested: () -> Boolean,
        onEpoch: (OneMicronIkEpochMetrics) -> Unit
    ): OneMicronModelTrainingResult {
        require(config.epochs > 0 && config.batchSize > 0 && config.hiddenUnits > 0)
        require(config.learningRate.isFinite() && config.learningRate > 0.0)
        val started = System.currentTimeMillis()
        var model = initialize(dataset.source.featureNames.size, config.hiddenUnits, config.randomSeed)
        var bestModel = model.deepCopy()
        var bestEpoch = 0
        var bestLoss = evaluateLoss(model, dataset, dataset.split.validationIndices, cancellationRequested)
        var staleEpochs = 0
        val history = mutableListOf<OneMicronIkEpochMetrics>()
        val indices = dataset.split.trainIndices.clone()
        val random =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(config.randomSeed, config.profile.name, "one-micron-regression")
            )
        val optimizer = RegressionAdam(model)
        val workCycleReporter = workCycleReporterFactory()
        var gradientBuffers: List<RegressionGradient> = emptyList()
        val combinedGradient = RegressionGradient(model)
        val executor =
            if (config.workerCount > 1) {
                Executors.newFixedThreadPool(
                    config.workerCount,
                    ComputeWorkerThreadFactory("rkl-micron-training")
                )
            } else null

        try {
            for (epoch in 1..config.epochs) {
                ensureNotCancelled(cancellationRequested)
                shuffle(indices, random)
                var lossSum = 0.0
                var rowCount = 0
                var start = 0
                while (start < indices.size) {
                    ensureNotCancelled(cancellationRequested)
                    val end = (start + config.batchSize).coerceAtMost(indices.size)
                    val workers =
                        ComputeWorkerAllocation.resolve(
                            configuredWorkerCount = config.workerCount,
                            runtimeWorkerLimit = runtimeWorkerLimit(),
                            availableWorkItems = end - start
                        )
                    gradientBuffers = resizeGradientBuffers(gradientBuffers, model, workers)
                    val gradients =
                        if (executor == null || workers == 1) {
                            listOf(
                                gradient(
                                    model,
                                    dataset,
                                    indices,
                                    start,
                                    end,
                                    gradientBuffers[0],
                                    cancellationRequested
                                )
                            )
                        } else {
                            val chunk = ((end - start) + workers - 1) / workers
                            val tasks =
                                (0 until workers).mapNotNull { worker ->
                                    val from = start + worker * chunk
                                    val to = minOf(end, from + chunk)
                                    if (from >= to) {
                                        null
                                    } else {
                                        Callable {
                                            val cycleStarted = workCycleReporter.startCycle()
                                            try {
                                                gradient(
                                                    model,
                                                    dataset,
                                                    indices,
                                                    from,
                                                    to,
                                                    gradientBuffers[worker],
                                                    cancellationRequested
                                                )
                                            } finally {
                                                workCycleReporter.finishCycle(cycleStarted)
                                            }
                                        }
                                    }
                                }
                            try {
                                executor.invokeAll(tasks).map { it.get() }
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw OneMicronIkTrainingCancelledException()
                            } catch (error: ExecutionException) {
                                val cause = error.cause
                                if (cause is RuntimeException) throw cause
                                throw IllegalStateException("One-micron training worker failed.", cause)
                            }
                        }
                    val combined = combinedGradient.apply(RegressionGradient::reset)
                    gradients.forEach(combined::add)
                    update(model, optimizer, combined, config)
                    lossSum += combined.lossSum
                    rowCount += combined.rows
                    start = end
                }
                ensureNotCancelled(cancellationRequested)
                val validationLoss =
                    evaluateLoss(model, dataset, dataset.split.validationIndices, cancellationRequested)
                val epochMetrics =
                    OneMicronIkEpochMetrics(
                        epoch,
                        lossSum / rowCount.coerceAtLeast(1),
                        validationLoss,
                        System.currentTimeMillis() - started
                    )
                history += epochMetrics
                onEpoch(epochMetrics)
                if (validationLoss + 1e-12 < bestLoss) {
                    bestLoss = validationLoss
                    bestModel = model.deepCopy()
                    bestEpoch = epoch
                    staleEpochs = 0
                } else {
                    staleEpochs++
                    if (staleEpochs >= config.earlyStoppingPatience) break
                }
            }
        } finally {
            executor?.shutdownNow()
            workCycleReporter.close()
        }
        model = bestModel
        return OneMicronModelTrainingResult(model, bestEpoch, history)
    }

    private fun initialize(inputCount: Int, hiddenCount: Int, seed: Int): LocalIkRegressionModel {
        val random = ScientificRandom(ScientificRandomProtocol.deriveSeed(seed, "micron-weight-initialization"))
        val inputLimit = sqrt(6.0 / (inputCount + hiddenCount))
        val outputLimit = sqrt(6.0 / (hiddenCount + ONE_MICRON_MAX_JOINTS))
        return LocalIkRegressionModel(
            inputFeatureCount = inputCount,
            hiddenUnitCount = hiddenCount,
            inputWeights = FloatArray(inputCount * hiddenCount) { random.nextDouble(-inputLimit, inputLimit).toFloat() },
            hiddenBiases = FloatArray(hiddenCount),
            outputWeights = FloatArray(hiddenCount * ONE_MICRON_MAX_JOINTS) { random.nextDouble(-outputLimit, outputLimit).toFloat() },
            outputBiases = FloatArray(ONE_MICRON_MAX_JOINTS)
        )
    }

    private fun gradient(
        model: LocalIkRegressionModel,
        dataset: PreparedOneMicronIkDataset,
        indices: IntArray,
        from: Int,
        to: Int,
        result: RegressionGradient,
        cancellationRequested: () -> Boolean
    ): RegressionGradient {
        result.reset()
        val hiddenPre = result.hiddenPre
        val hidden = result.hidden
        val output = result.output
        val outputDelta = result.outputDelta
        for (position in from until to) {
            if ((position - from) % CANCELLATION_CHECK_INTERVAL == 0) {
                ensureNotCancelled(cancellationRequested)
            }
            val sampleIndex = indices[position]
            val features = dataset.normalizedFeatures[sampleIndex]
            val sample = dataset.source.samples[sampleIndex]
            for (h in 0 until model.hiddenUnitCount) {
                var value = model.hiddenBiases[h]
                val offset = h * model.inputFeatureCount
                for (f in 0 until model.inputFeatureCount) value += model.inputWeights[offset + f] * features[f]
                hiddenPre[h] = value
                hidden[h] = if (value > 0f) value else 0f
            }
            var active = 0
            for (o in 0 until model.outputCount) if (sample.outputMask[o] > 0f) active++
            val activeScale = 1f / active.coerceAtLeast(1)
            for (o in 0 until model.outputCount) {
                var value = model.outputBiases[o]
                val offset = o * model.hiddenUnitCount
                for (h in 0 until model.hiddenUnitCount) value += model.outputWeights[offset + h] * hidden[h]
                output[o] = kotlin.math.tanh(value.toDouble()).toFloat()
                val difference = (output[o] - sample.normalizedJointDelta[o]) * sample.outputMask[o]
                result.lossSum += difference * difference * activeScale
                outputDelta[o] = 2f * difference * activeScale * (1f - output[o] * output[o])
                result.outputBias[o] += outputDelta[o]
                for (h in 0 until model.hiddenUnitCount) result.outputWeights[offset + h] += outputDelta[o] * hidden[h]
            }
            for (h in 0 until model.hiddenUnitCount) {
                var delta = 0f
                for (o in 0 until model.outputCount) delta += model.outputWeights[o * model.hiddenUnitCount + h] * outputDelta[o]
                if (hiddenPre[h] <= 0f) delta = 0f
                result.hiddenBias[h] += delta
                val offset = h * model.inputFeatureCount
                for (f in 0 until model.inputFeatureCount) result.inputWeights[offset + f] += delta * features[f]
            }
            result.rows++
        }
        return result
    }

    private fun update(
        model: LocalIkRegressionModel,
        optimizer: RegressionAdam,
        gradient: RegressionGradient,
        config: OneMicronIkTrainingConfig
    ) {
        val divisor = gradient.rows.coerceAtLeast(1).toFloat()
        fun normalize(values: FloatArray, parameters: FloatArray, regularize: Boolean) {
            for (index in values.indices) {
                values[index] /= divisor
                if (regularize) values[index] += config.l2Regularization.toFloat() * parameters[index]
            }
        }
        normalize(gradient.inputWeights, model.inputWeights, true)
        normalize(gradient.hiddenBias, model.hiddenBiases, false)
        normalize(gradient.outputWeights, model.outputWeights, true)
        normalize(gradient.outputBias, model.outputBiases, false)
        requireFiniteUpdate(
            model.inputWeights,
            model.hiddenBiases,
            model.outputWeights,
            model.outputBiases,
            gradient.inputWeights,
            gradient.hiddenBias,
            gradient.outputWeights,
            gradient.outputBias
        )
        optimizer.updateAll(model, gradient, config.learningRate)
    }

    private fun requireFiniteUpdate(vararg arrays: FloatArray) {
        require(arrays.all { values -> values.all(Float::isFinite) }) {
            "One-micron training update contains a non-finite parameter or gradient; model mutation was blocked."
        }
    }

    private fun evaluateLoss(
        model: LocalIkRegressionModel,
        dataset: PreparedOneMicronIkDataset,
        indices: IntArray,
        cancellationRequested: () -> Boolean
    ): Double {
        if (indices.isEmpty()) return Double.POSITIVE_INFINITY
        var sum = 0.0
        val predicted = FloatArray(model.outputCount)
        val hidden = FloatArray(model.hiddenUnitCount)
        indices.forEachIndexed { position, index ->
            if (position % CANCELLATION_CHECK_INTERVAL == 0) {
                ensureNotCancelled(cancellationRequested)
            }
            val sample = dataset.source.samples[index]
            model.predictInto(dataset.normalizedFeatures[index], predicted, hidden)
            var active = 0
            var rowSum = 0.0
            for (output in predicted.indices) {
                if (sample.outputMask[output] > 0f) {
                    val difference = predicted[output] - sample.normalizedJointDelta[output]
                    rowSum += difference * difference
                    active++
                }
            }
            sum += rowSum / active.coerceAtLeast(1)
        }
        return sum / indices.size
    }

    private fun resizeGradientBuffers(
        previous: List<RegressionGradient>,
        model: LocalIkRegressionModel,
        workerCount: Int
    ): List<RegressionGradient> =
        if (previous.size == workerCount) {
            previous
        } else {
            List(workerCount) { index -> previous.getOrNull(index) ?: RegressionGradient(model) }
        }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw OneMicronIkTrainingCancelledException()
        }
    }

    private fun shuffle(indices: IntArray, random: ScientificRandom) {
        for (index in indices.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val value = indices[index]
            indices[index] = indices[swap]
            indices[swap] = value
        }
    }

    private companion object {
        const val CANCELLATION_CHECK_INTERVAL = 64
    }
}

private class RegressionGradient(model: LocalIkRegressionModel) {
    val inputWeights = FloatArray(model.inputWeights.size)
    val hiddenBias = FloatArray(model.hiddenBiases.size)
    val outputWeights = FloatArray(model.outputWeights.size)
    val outputBias = FloatArray(model.outputBiases.size)
    val hiddenPre = FloatArray(model.hiddenUnitCount)
    val hidden = FloatArray(model.hiddenUnitCount)
    val output = FloatArray(model.outputCount)
    val outputDelta = FloatArray(model.outputCount)
    var lossSum = 0.0
    var rows = 0

    fun reset() {
        inputWeights.fill(0f)
        hiddenBias.fill(0f)
        outputWeights.fill(0f)
        outputBias.fill(0f)
        lossSum = 0.0
        rows = 0
    }

    fun add(other: RegressionGradient) {
        inputWeights.add(other.inputWeights)
        hiddenBias.add(other.hiddenBias)
        outputWeights.add(other.outputWeights)
        outputBias.add(other.outputBias)
        lossSum += other.lossSum
        rows += other.rows
    }

    private fun FloatArray.add(other: FloatArray) {
        for (index in indices) this[index] += other[index]
    }
}

private class RegressionAdam(model: LocalIkRegressionModel) {
    val input = RegressionAdamArray(model.inputWeights.size)
    val hidden = RegressionAdamArray(model.hiddenBiases.size)
    val output = RegressionAdamArray(model.outputWeights.size)
    val outputBias = RegressionAdamArray(model.outputBiases.size)

    fun updateAll(model: LocalIkRegressionModel, gradient: RegressionGradient, rate: Double) {
        input.validate(model.inputWeights, gradient.inputWeights, rate)
        hidden.validate(model.hiddenBiases, gradient.hiddenBias, rate)
        output.validate(model.outputWeights, gradient.outputWeights, rate)
        outputBias.validate(model.outputBiases, gradient.outputBias, rate)
        input.update(model.inputWeights, gradient.inputWeights, rate)
        hidden.update(model.hiddenBiases, gradient.hiddenBias, rate)
        output.update(model.outputWeights, gradient.outputWeights, rate)
        outputBias.update(model.outputBiases, gradient.outputBias, rate)
    }
}

private class RegressionAdamArray(size: Int) {
    private val first = FloatArray(size)
    private val second = FloatArray(size)
    private var step = 0

    fun update(parameters: FloatArray, gradient: FloatArray, rate: Double) {
        validate(parameters, gradient, rate)
        if (parameters.isEmpty()) return
        val nextStep = step + 1
        val correction1 = 1.0 - BETA_1.pow(nextStep)
        val correction2 = 1.0 - BETA_2.pow(nextStep)

        step = nextStep
        for (index in parameters.indices) {
            val update = checkedUpdate(
                parameters[index], gradient[index], first[index], second[index], rate, correction1, correction2
            )
            first[index] = update.firstMoment.toFloat()
            second[index] = update.secondMoment.toFloat()
            parameters[index] = update.parameter.toFloat()
        }
    }

    fun validate(parameters: FloatArray, gradient: FloatArray, rate: Double) {
        require(parameters.size == gradient.size) { "Adam parameter and gradient sizes must match." }
        require(rate.isFinite() && rate > 0.0) { "Adam learning rate must be finite and positive." }
        if (parameters.isEmpty()) return
        val nextStep = step + 1
        val correction1 = 1.0 - BETA_1.pow(nextStep)
        val correction2 = 1.0 - BETA_2.pow(nextStep)
        for (index in parameters.indices) {
            checkedUpdate(parameters[index], gradient[index], first[index], second[index], rate, correction1, correction2)
        }
    }

    private fun checkedUpdate(
        parameter: Float,
        gradient: Float,
        previousFirst: Float,
        previousSecond: Float,
        rate: Double,
        correction1: Double,
        correction2: Double
    ): CheckedRegressionAdamUpdate {
        require(parameter.isFinite() && gradient.isFinite() && previousFirst.isFinite() && previousSecond.isFinite()) {
            "Adam received a non-finite parameter, gradient or optimizer moment."
        }
        val gradientD = gradient.toDouble()
        val nextFirst = BETA_1 * previousFirst.toDouble() + (1.0 - BETA_1) * gradientD
        val nextSecond = BETA_2 * previousSecond.toDouble() + (1.0 - BETA_2) * gradientD * gradientD
        val nextParameter = parameter.toDouble() - rate * (nextFirst / correction1) /
            (sqrt(nextSecond / correction2) + EPSILON)
        require(
            nextFirst.isFinite() && nextSecond.isFinite() && nextParameter.isFinite() &&
                nextFirst in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble() &&
                nextSecond in 0.0..Float.MAX_VALUE.toDouble() &&
                nextParameter in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble()
        ) {
            "Adam update exceeded the finite Float range; model mutation was blocked."
        }
        return CheckedRegressionAdamUpdate(nextFirst, nextSecond, nextParameter)
    }

    private data class CheckedRegressionAdamUpdate(
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
