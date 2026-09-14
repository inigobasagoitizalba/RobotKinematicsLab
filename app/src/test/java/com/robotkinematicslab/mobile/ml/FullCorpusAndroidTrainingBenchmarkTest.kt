package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.ClassificationMetrics
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.TrainedProfileResult
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in benchmark that trains the exact on-device models against a full scientific corpus.
 * It is deliberately excluded from ordinary test runs unless RKL_FULL_TRAINING_DATASET is set.
 */
class FullCorpusAndroidTrainingBenchmarkTest {

    @Test
    fun trainExactAndroidEngineOnFullCorpus() {
        val datasetPath = System.getenv("RKL_FULL_TRAINING_DATASET").orEmpty()
        assumeTrue("Set RKL_FULL_TRAINING_DATASET to run the full benchmark.", datasetPath.isNotBlank())
        val dataset = resolveProjectFile(datasetPath)
        assertTrue("Dataset does not exist: $datasetPath", dataset.isFile)

        val outputDirectory = File(
            System.getenv("RKL_FULL_TRAINING_OUTPUT")
                ?: "build/reports/full-corpus-android-training"
        ).apply { mkdirs() }
        val seeds = com.robotkinematicslab.mobile.audit.OptInCampaignInputs.seeds(env("RKL_FULL_TRAINING_SEEDS", "2604"))
        val modelKind = TrainingModelKind.valueOf(env("RKL_FULL_TRAINING_MODEL", "AUTOMATIC"))
        val resourceMode = TrainingResourceMode.valueOf(env("RKL_FULL_TRAINING_RESOURCE", "BALANCED"))
        val splitStrategy = TrainingSplitStrategy.valueOf(env("RKL_FULL_TRAINING_SPLIT", "ROBOT_HELD_OUT"))
        val epochs = env("RKL_FULL_TRAINING_EPOCHS", "40").toInt()
        val batchSize = env("RKL_FULL_TRAINING_BATCH_SIZE", "512").toInt()
        val hiddenUnits = env("RKL_FULL_TRAINING_HIDDEN_UNITS", "24").toInt()
        val learningRate = env("RKL_FULL_TRAINING_LEARNING_RATE", "0.003").toDouble()
        val l2 = env("RKL_FULL_TRAINING_L2", "0.0001").toDouble()
        val patience = env("RKL_FULL_TRAINING_PATIENCE", "8").toInt()
        val maximumRows = env("RKL_FULL_TRAINING_MAXIMUM_ROWS", "100000").toInt()
        val compareProfiles = env("RKL_FULL_TRAINING_COMPARE_PROFILES", "true").toBooleanStrict()
        val singleProfile =
            TrainingFeatureProfile.valueOf(
                env("RKL_FULL_TRAINING_PROFILE", TrainingFeatureProfile.CONTEXT_EXPANDED.name)
            )
        val workers = env(
            "RKL_FULL_TRAINING_WORKERS",
            (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 8).toString()
        ).toInt()

        val summaryRows = mutableListOf(SUMMARY_HEADER)
        val iterationRows = mutableListOf(ITERATION_HEADER)
        seeds.forEach { seed ->
            System.gc()
            val monitor = PeakHeapMonitor()
            val wallStart = System.nanoTime()
            val result = monitor.use {
                LocalTrainingEngine().train(
                    config = LocalTrainingConfig(
                        runName = "full-corpus-android-seed-$seed",
                        datasetPath = dataset.absolutePath,
                        compareFeatureProfiles = compareProfiles,
                        singleFeatureProfile = singleProfile,
                        modelKind = modelKind,
                        resourceMode = resourceMode,
                        splitStrategy = splitStrategy,
                        maximumRows = maximumRows,
                        epochs = epochs,
                        batchSize = batchSize,
                        learningRate = learningRate,
                        l2Regularization = l2,
                        hiddenUnits = hiddenUnits,
                        randomSeed = seed,
                        earlyStoppingPatience = patience,
                        workerCount = workers
                    ),
                    onProgress = { progress ->
                        if (progress.epoch == null || progress.epoch == 1 || progress.epoch % 5 == 0) {
                            println("[full-corpus] seed=$seed ${progress.phase}: ${progress.message}")
                        }
                    }
                )
            }
            val wallMillis = (System.nanoTime() - wallStart) / 1_000_000
            result.comparison.variants.forEach { profile ->
                summaryRows += summaryRow(seed, profile, wallMillis, monitor.peakBytes.get(), workers)
            }
            result.iterations.forEach { iteration ->
                iterationRows += listOf(
                    seed, iteration.profile.name, iteration.candidateId, iteration.modelKind.name,
                    iteration.hiddenUnits, iteration.epoch, iteration.trainingLoss,
                    iteration.validationMetrics.accuracy, iteration.validationMetrics.balancedAccuracy,
                    iteration.validationMetrics.macroF1, iteration.validationMetrics.logLoss,
                    iteration.elapsedMillis
                ).joinToString(",")
            }
        }

        File(outputDirectory, "summary.csv").writeText(summaryRows.joinToString("\n", postfix = "\n"))
        File(outputDirectory, "iterations.csv").writeText(iterationRows.joinToString("\n", postfix = "\n"))
        File(outputDirectory, "configuration.txt").writeText(
            "dataset=${dataset.absolutePath}\nrows_limit=$maximumRows\nseeds=${seeds.joinToString(",")}\n" +
                "model=$modelKind\nresource_mode=$resourceMode\nsplit=$splitStrategy\n" +
                "epochs=$epochs\nbatch_size=$batchSize\nhidden_units=$hiddenUnits\n" +
                "learning_rate=$learningRate\nl2=$l2\npatience=$patience\n" +
                "compare_profiles=$compareProfiles\nsingle_profile=$singleProfile\nworkers=$workers\n"
        )
        println("[full-corpus] Results: ${outputDirectory.absolutePath}")
    }

    private fun summaryRow(
        seed: Int,
        result: TrainedProfileResult,
        wallMillis: Long,
        peakHeapBytes: Long,
        workers: Int
    ): String {
        val metrics = result.testMetrics
        return listOf(
            seed, result.profile.name, result.featureNames.size, result.candidateId,
            result.model.kind.name, result.model.hiddenUnitCount, result.parameterCount,
            result.trainRowCount, result.validationRowCount, result.testRowCount,
            result.skippedRowCount, result.duplicateFingerprintCount,
            result.duplicateFingerprintsKeptTogether, result.bestEpoch,
            metrics.accuracy, metrics.balancedAccuracy, metrics.macroF1, metrics.logLoss,
            recall(metrics, TrainingLabel.ACCEPTED.ordinal),
            recall(metrics, TrainingLabel.UNCERTAIN.ordinal),
            recall(metrics, TrainingLabel.REJECTED.ordinal),
            metrics.classSupport.getOrElse(TrainingLabel.ACCEPTED.ordinal) { 0 },
            metrics.classSupport.getOrElse(TrainingLabel.UNCERTAIN.ordinal) { 0 },
            metrics.classSupport.getOrElse(TrainingLabel.REJECTED.ordinal) { 0 },
            metrics.hasCompleteClassCoverage,
            metrics.inferenceNanosPerSample, result.trainingDurationMillis,
            wallMillis, peakHeapBytes, workers
        ).joinToString(",")
    }

    private fun recall(metrics: ClassificationMetrics, classIndex: Int): Double {
        val actualClass = metrics.confusionMatrix[classIndex]
        val count = actualClass.sum()
        return if (count == 0) Double.NaN else actualClass[classIndex].toDouble() / count
    }

    private fun env(name: String, default: String): String = System.getenv(name) ?: default

    private fun resolveProjectFile(path: String): File {
        val requested = File(path)
        if (requested.isAbsolute || requested.exists()) return requested.absoluteFile
        val repositoryRelative = File("..", path).canonicalFile
        return if (repositoryRelative.exists()) repositoryRelative else requested.absoluteFile
    }

    private class PeakHeapMonitor : Closeable {
        private val running = AtomicBoolean(true)
        val peakBytes = AtomicLong(usedHeap())
        private val sampler = Thread {
            while (running.get()) {
                peakBytes.accumulateAndGet(usedHeap(), ::maxOf)
                try {
                    Thread.sleep(20)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply {
            name = "full-corpus-heap-monitor"
            isDaemon = true
            start()
        }

        override fun close() {
            running.set(false)
            sampler.interrupt()
            sampler.join()
            peakBytes.accumulateAndGet(usedHeap(), ::maxOf)
        }

        companion object {
            private fun usedHeap(): Long {
                val runtime = Runtime.getRuntime()
                return runtime.totalMemory() - runtime.freeMemory()
            }
        }
    }

    companion object {
        private val SUMMARY_HEADER = listOf(
            "seed", "profile", "feature_count", "candidate", "model_kind", "hidden_units",
            "parameter_count", "train_rows", "validation_rows", "test_rows", "skipped_rows",
            "duplicate_fingerprints", "duplicates_kept_together", "best_epoch", "accuracy",
            "balanced_accuracy", "macro_f1", "log_loss", "accepted_recall", "uncertain_recall",
            "rejected_recall", "accepted_support", "uncertain_support", "rejected_support",
            "complete_class_coverage", "inference_ns_per_sample", "selected_candidate_training_ms",
            "whole_comparison_wall_ms", "peak_jvm_heap_bytes", "workers"
        ).joinToString(",")
        private val ITERATION_HEADER = listOf(
            "seed", "profile", "candidate", "model_kind", "hidden_units", "epoch",
            "training_loss", "validation_accuracy", "validation_balanced_accuracy",
            "validation_macro_f1", "validation_log_loss", "elapsed_ms"
        ).joinToString(",")
    }
}
