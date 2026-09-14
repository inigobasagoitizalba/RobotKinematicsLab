package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in desktop benchmark using the exact Android model and solver implementations. */
class OneMicronIkBenchmarkTest {

    @Test
    fun benchmarkBothFeatureProfilesWhenRequested() {
        val inputPath = System.getenv("RKL_ONE_MICRON_BENCHMARK_INPUT")
        assumeTrue(!inputPath.isNullOrBlank())
        val input = resolveProjectFile(requireNotNull(inputPath))
        val output =
            resolveProjectOutput(
                System.getenv("RKL_ONE_MICRON_BENCHMARK_OUTPUT")
                    ?: "audit-artifacts/one-micron-ik/benchmark"
            )
        val rows = System.getenv("RKL_ONE_MICRON_BENCHMARK_ROWS")?.toIntOrNull() ?: 10_000
        val epochs = System.getenv("RKL_ONE_MICRON_BENCHMARK_EPOCHS")?.toIntOrNull() ?: 30
        val verify = System.getenv("RKL_ONE_MICRON_VERIFY_ROWS")?.toIntOrNull() ?: 1_000
        val workers = System.getenv("RKL_ONE_MICRON_TRAINING_WORKERS")?.toIntOrNull() ?: 6
        val batchSize = System.getenv("RKL_ONE_MICRON_BATCH_SIZE")?.toIntOrNull() ?: 128
        val learningRate = System.getenv("RKL_ONE_MICRON_LEARNING_RATE")?.toDoubleOrNull() ?: 0.001
        val l2 = System.getenv("RKL_ONE_MICRON_L2")?.toDoubleOrNull() ?: 1e-5
        val hiddenUnits = System.getenv("RKL_ONE_MICRON_HIDDEN_UNITS")?.toIntOrNull() ?: 64
        val seed = System.getenv("RKL_ONE_MICRON_SEED")?.toIntOrNull() ?: 2604
        val patience = System.getenv("RKL_ONE_MICRON_PATIENCE")?.toIntOrNull() ?: 8
        val splitStrategy =
            System.getenv("RKL_ONE_MICRON_SPLIT")
                ?.let(TrainingSplitStrategy::valueOf)
                ?: TrainingSplitStrategy.ROBOT_HELD_OUT
        output.mkdirs()
        val storage = OneMicronIkStorageRepository(File(output, "runs"), File(output, "models"))
        val summary = mutableListOf(
            "profile,rows,best_epoch,parameters,raw_success,hybrid_success,baseline_success,pipeline_success,raw_median_m,raw_p95_m,refined_median_m,baseline_median_m,refined_iterations,pipeline_iterations,baseline_iterations,error_avoided_percent,iteration_savings_percent,inference_ns,training_ms,peak_heap_mib,model_path"
        )
        val profiles =
            System.getenv("RKL_ONE_MICRON_PROFILE")
                ?.takeIf(String::isNotBlank)
                ?.let { listOf(OneMicronIkFeatureProfile.valueOf(it)) }
                ?: OneMicronIkFeatureProfile.entries
        profiles.forEach { profile ->
            val started = System.currentTimeMillis()
            val heapSampler = PeakHeapSampler().also(PeakHeapSampler::start)
            val result =
                OneMicronIkTrainingEngine().run(
                    OneMicronIkTrainingConfig(
                        runName = "benchmark-${profile.name.lowercase()}",
                        datasetPath = input.absolutePath,
                        profile = profile,
                        maximumRows = rows,
                        epochs = epochs,
                        batchSize = batchSize,
                        learningRate = learningRate,
                        l2Regularization = l2,
                        hiddenUnits = hiddenUnits,
                        workerCount = workers,
                        randomSeed = seed,
                        earlyStoppingPatience = patience,
                        splitStrategy = splitStrategy,
                        verificationSampleLimit = verify
                    ),
                    storage
                )
            val peakHeapMib = heapSampler.stop().toDouble() / (1024.0 * 1024.0)
            val metrics = result.verification
            summary +=
                listOf(
                    profile.name,
                    rows,
                    result.bestEpoch,
                    result.model.parameterCount,
                    metrics.rawNeuralSuccessRate,
                    metrics.neuralThenRefineSuccessRate,
                    metrics.deterministicBaselineSuccessRate,
                    metrics.verifiedPipelineSuccessRate,
                    metrics.rawMedianErrorMeters,
                    metrics.rawP95ErrorMeters,
                    metrics.refinedMedianErrorMeters,
                    metrics.baselineMedianErrorMeters,
                    metrics.meanRefinedIterations,
                    metrics.meanPipelineIterations,
                    metrics.meanBaselineIterations,
                    metrics.cumulativeErrorAvoidedPercent,
                    metrics.iterationSavingsPercent,
                    metrics.meanNeuralInferenceNanos,
                    System.currentTimeMillis() - started,
                    peakHeapMib,
                    result.modelPath
                ).joinToString(",")
        }
        val summaryFile = File(output, "summary.csv")
        summaryFile.writeText(summary.joinToString("\n", postfix = "\n"))
        assertTrue(summaryFile.isFile)
        println("ONE_MICRON_BENCHMARK ${summaryFile.absolutePath}")
    }

    private class PeakHeapSampler {
        private val running = AtomicBoolean(false)
        private val peak = AtomicLong(0L)
        private var thread: Thread? = null

        fun start() {
            running.set(true)
            thread = Thread {
                val runtime = Runtime.getRuntime()
                while (running.get()) {
                    val used = runtime.totalMemory() - runtime.freeMemory()
                    peak.updateAndGet { current -> maxOf(current, used) }
                    Thread.sleep(5L)
                }
            }.apply { isDaemon = true; name = "rkl-peak-heap-sampler"; start() }
        }

        fun stop(): Long {
            running.set(false)
            thread?.join(1_000L)
            return peak.get()
        }
    }

    private fun resolveProjectFile(path: String): File {
        val requested = File(path)
        if (requested.isAbsolute || requested.exists()) return requested.absoluteFile
        val repositoryRelative = File("..", path).canonicalFile
        return if (repositoryRelative.exists()) repositoryRelative else requested.absoluteFile
    }

    private fun resolveProjectOutput(path: String): File {
        val requested = File(path)
        if (requested.isAbsolute) return requested
        return if (path.startsWith("app${File.separator}") || path.startsWith("app/")) {
            File("..", path).canonicalFile
        } else {
            requested.absoluteFile
        }
    }
}
