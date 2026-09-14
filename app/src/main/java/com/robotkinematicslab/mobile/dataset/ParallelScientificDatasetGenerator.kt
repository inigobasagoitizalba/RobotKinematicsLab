package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerThreadFactory
import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerAllocation
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Deterministically shards both robots and their samples. Shards are merged by robot and sample
 * index, so one robot can occupy every selected CPU worker without scheduling changing any row.
 */
class ParallelScientificDatasetGenerator(
    private val delegateFactory: () -> ScientificDatasetGenerator = ::ScientificDatasetGenerator
) {
    fun generate(
        config: DatasetGenerationConfig,
        csvFile: File,
        existingRowCount: Long,
        generationIndex: Int,
        workerCount: Int,
        cancellationRequested: AtomicBoolean = AtomicBoolean(false),
        onProgress: (DatasetGenerationProgress) -> Unit = {},
        onCheckpoint: (addedRows: Int, totalRows: Long) -> Unit = { _, _ -> },
        lockedPreflight: () -> Unit = {}
    ): DatasetGenerationResult {
        val destinationKey = csvFile.canonicalFile.absolutePath
        val destinationLock = DESTINATION_LOCKS.computeIfAbsent(destinationKey) { ReentrantLock() }
        check(destinationLock.tryLock()) {
            "Dataset generation is already writing to ${csvFile.name}. Wait for it to finish or cancel it first."
        }
        return try {
            // Revalidate only after owning the destination lock. A second producer may have
            // committed between the caller's initial inspection and this critical section.
            lockedPreflight()
            generateTransaction(
                config = config,
                csvFile = csvFile,
                existingRowCount = existingRowCount,
                generationIndex = generationIndex,
                workerCount = workerCount,
                cancellationRequested = cancellationRequested,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint
            )
        } finally {
            destinationLock.unlock()
        }
    }

    private fun generateTransaction(
        config: DatasetGenerationConfig,
        csvFile: File,
        existingRowCount: Long,
        generationIndex: Int,
        workerCount: Int,
        cancellationRequested: AtomicBoolean,
        onProgress: (DatasetGenerationProgress) -> Unit,
        onCheckpoint: (addedRows: Int, totalRows: Long) -> Unit
    ): DatasetGenerationResult {
        require(config.datasetName.isNotBlank()) { "Dataset name must not be blank." }
        require(config.datasetName.length <= MAXIMUM_STORED_NAME_LENGTH) {
            "Dataset name must contain at most $MAXIMUM_STORED_NAME_LENGTH characters."
        }
        require(config.datasetName.none(Char::isISOControl)) {
            "Dataset name must not contain line breaks or control characters."
        }
        require(config.robots.isNotEmpty())
        require(
            config.robots.all {
                it.id.isNotBlank() && it.id.length <= MAXIMUM_STORED_NAME_LENGTH && it.id.none(Char::isISOControl)
            }
        ) {
            "Every dataset robot needs a bounded, control-character-free stable identifier."
        }
        require(config.robots.map(SavedRobot::id).distinct().size == config.robots.size) {
            "Dataset robot identifiers must be unique."
        }
        require(config.samplesPerRobot > 0) { "Samples per robot must be positive." }
        require(existingRowCount >= 0L) { "Existing dataset row count cannot be negative." }
        require(generationIndex >= 0) { "Dataset generation index cannot be negative." }
        val requestedRowsLong = config.samplesPerRobot.toLong() * config.robots.size.toLong()
        require(requestedRowsLong <= Int.MAX_VALUE) {
            "This run requests too many rows. Split it into append batches."
        }
        require(existingRowCount <= Long.MAX_VALUE - requestedRowsLong) {
            "Appending this batch would overflow the dataset row index."
        }
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            return DatasetGenerationResult(
                csvPath = csvFile.absolutePath,
                addedRows = 0,
                totalRows = existingRowCount,
                attempts = 0,
                completed = false,
                cancelled = true,
                message = "Parallel dataset generation cancelled before workers started."
            )
        }

        val workers =
            ComputeWorkerAllocation.resolve(
                configuredWorkerCount = workerCount.coerceAtLeast(1),
                runtimeWorkerLimit = MAXIMUM_WORKERS,
                availableWorkItems = requestedRowsLong.toInt()
            )
        val outputDirectory = requireNotNull(csvFile.absoluteFile.parentFile) {
            "Dataset output must have a parent directory."
        }
        outputDirectory.mkdirs()
        val shardDirectory = Files.createTempDirectory(outputDirectory.toPath(), "dataset-shards-").toFile()
        val executor =
            Executors.newFixedThreadPool(workers, ComputeWorkerThreadFactory("dataset-worker"))
        val added = AtomicInteger(0)
        val attempts = AtomicInteger(0)
        val requested = requestedRowsLong.toInt()
        return try {
            val shardPlans = buildShardPlans(config, workers)
            val futures = shardPlans.map { plan ->
                executor.submit(Callable {
                    val robot = config.robots[plan.robotIndex]
                    val shard =
                        File(
                            shardDirectory,
                            "robot-${plan.robotIndex.toString().padStart(4, '0')}-sample-${plan.sampleStart.toString().padStart(9, '0')}.csv"
                        )
                    var previousAdded = 0
                    var previousAttempts = 0
                    val result = delegateFactory().generate(
                        config = config.copy(robots = listOf(robot), append = false),
                        csvFile = shard,
                        existingRowCount =
                            existingRowCount +
                                plan.robotIndex.toLong() * config.samplesPerRobot.toLong() +
                                plan.sampleStart.toLong(),
                        generationIndex = generationIndex,
                        cancellationRequested = cancellationRequested,
                        onProgress = { progress ->
                            val totalAdded = added.addAndGet(progress.addedRows - previousAdded)
                            val totalAttempts = attempts.addAndGet(progress.attempts - previousAttempts)
                            previousAdded = progress.addedRows
                            previousAttempts = progress.attempts
                            onProgress(
                                progress.copy(
                                    requestedRows = requested,
                                    addedRows = totalAdded,
                                    attempts = totalAttempts,
                                    currentRobotIndex = plan.robotIndex,
                                    totalRobots = config.robots.size
                                )
                            )
                        },
                        onCheckpoint = { _, _ -> },
                        sampleIndexOffset = plan.sampleStart,
                        sampleCountPerRobot = plan.sampleCount
                    )
                    ShardResult(plan, shard, result)
                })
            }
            val shards =
                try {
                    futures.map { it.get() }
                } catch (_: InterruptedException) {
                    cancellationRequested.set(true)
                    futures.forEach { it.cancel(true) }
                    Thread.currentThread().interrupt()
                    return DatasetGenerationResult(
                        csvPath = csvFile.absolutePath,
                        addedRows = 0,
                        totalRows = existingRowCount,
                        attempts = attempts.get(),
                        completed = false,
                        cancelled = true,
                        message = "Parallel dataset generation interrupted safely."
                    )
                }.sortedWith(compareBy<ShardResult> { it.plan.robotIndex }.thenBy { it.plan.sampleStart })
            val failed = shards.firstOrNull { !it.result.completed }
            if (failed != null || cancellationRequested.get()) {
                DatasetGenerationResult(
                    csvPath = csvFile.absolutePath,
                    addedRows = 0,
                    totalRows = existingRowCount,
                    attempts = attempts.get(),
                    completed = false,
                    cancelled = cancellationRequested.get() || failed?.result?.cancelled == true,
                    message = failed?.result?.message ?: "Parallel dataset generation cancelled."
                )
            } else {
                val shouldAppend = config.append && csvFile.isFile && csvFile.length() > 0L
                if (shouldAppend) {
                    val existingHeader = csvFile.bufferedReader().use { it.readLine() }
                    require(existingHeader == ScientificDatasetCsvWriter.HEADER.joinToString(",")) {
                        "Cannot append: the existing CSV uses a different dataset schema."
                    }
                }
                val partial =
                    Files.createTempFile(
                        outputDirectory.toPath(),
                        ".${csvFile.name}.",
                        ".dataset-partial"
                    ).toFile()
                try {
                    partial.bufferedWriter().use { writer ->
                        if (shouldAppend) {
                            csvFile.useLines { lines -> lines.forEach(writer::appendLine) }
                        }
                        shards.forEachIndexed { shardIndex, shardResult ->
                            shardResult.file.useLines { lines ->
                                lines.forEachIndexed { lineIndex, line ->
                                    val includeHeader = !shouldAppend && shardIndex == 0 && lineIndex == 0
                                    if (includeHeader || lineIndex > 0) writer.appendLine(line)
                                }
                            }
                        }
                    }
                    moveReplacing(partial, csvFile)
                } finally {
                    partial.delete()
                }
                val addedRows = shards.sumOf { it.result.addedRows }
                val totalRows = existingRowCount + addedRows
                onCheckpoint(addedRows, totalRows)
                DatasetGenerationResult(
                    csvPath = csvFile.absolutePath,
                    addedRows = addedRows,
                    totalRows = totalRows,
                    attempts = attempts.get(),
                    completed = true,
                    cancelled = false,
                    message = "Parallel dataset generation completed with $workers worker(s)."
                )
            }
        } finally {
            val restoreInterrupt = Thread.interrupted()
            executor.shutdownNow()
            var interruptedWhileWaiting = false
            try {
                executor.awaitTermination(WORKER_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                interruptedWhileWaiting = true
            }
            if (executor.isTerminated) shardDirectory.deleteRecursively()
            if (restoreInterrupt || interruptedWhileWaiting) Thread.currentThread().interrupt()
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun buildShardPlans(
        config: DatasetGenerationConfig,
        workers: Int
    ): List<ShardPlan> {
        val targetShardCount =
            (workers * SHARDS_PER_WORKER)
                .coerceAtMost(config.samplesPerRobot * config.robots.size)
                .coerceAtLeast(config.robots.size)
        val shardsPerRobot =
            ((targetShardCount + config.robots.size - 1) / config.robots.size)
                .coerceIn(1, config.samplesPerRobot)
        val samplesPerShard =
            (config.samplesPerRobot + shardsPerRobot - 1) / shardsPerRobot
        return buildList {
            config.robots.indices.forEach { robotIndex ->
                var sampleStart = 0
                while (sampleStart < config.samplesPerRobot) {
                    val sampleCount = minOf(samplesPerShard, config.samplesPerRobot - sampleStart)
                    add(ShardPlan(robotIndex, sampleStart, sampleCount))
                    sampleStart += sampleCount
                }
            }
        }
    }

    private data class ShardPlan(
        val robotIndex: Int,
        val sampleStart: Int,
        val sampleCount: Int
    )

    private data class ShardResult(
        val plan: ShardPlan,
        val file: File,
        val result: DatasetGenerationResult
    )

    private companion object {
        val DESTINATION_LOCKS = ConcurrentHashMap<String, ReentrantLock>()
        const val SHARDS_PER_WORKER = 4
        const val MAXIMUM_WORKERS = 64
        const val MAXIMUM_STORED_NAME_LENGTH = 160
        const val WORKER_SHUTDOWN_TIMEOUT_SECONDS = 5L
    }
}
