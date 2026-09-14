package com.robotkinematicslab.mobile.dataset.continuous

import android.content.Context
import android.os.storage.StorageManager
import com.robotkinematicslab.mobile.dataset.DatasetScientificContract
import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetGenerationProgress
import com.robotkinematicslab.mobile.dataset.DatasetGenerationResult
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.ParallelScientificDatasetGenerator
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.buildDatasetManifest
import com.robotkinematicslab.mobile.performance.compute.AndroidDeviceComputeProfiler
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContinuousDatasetBatchRequest(
    val config: DatasetGenerationConfig,
    val csvFile: File,
    val existingRowCount: Long,
    val generationIndex: Int,
    val workerCount: Int,
    val cancellationRequested: AtomicBoolean,
    val onProgress: (DatasetGenerationProgress) -> Unit,
    val onCheckpoint: (addedRows: Int, totalRows: Long) -> Unit,
    val lockedPreflight: () -> Unit = {}
)

fun interface ContinuousDatasetBatchRunner {
    fun run(request: ContinuousDatasetBatchRequest): DatasetGenerationResult
}

class ContinuousDatasetGenerationCoordinator(
    private val storageRepository: DatasetStorageRepository,
    private val settingsRepository: ContinuousDatasetSettingsRepository,
    private val deviceProfileProvider: () -> DeviceComputeProfile,
    private val batchRunner: ContinuousDatasetBatchRunner,
    private val freeStorageBytes: () -> Long,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Closeable {

    constructor(context: Context) : this(
        storageRepository = DatasetStorageRepository(context),
        settingsRepository = ContinuousDatasetSettingsRepository(context),
        deviceProfileProvider = { AndroidDeviceComputeProfiler(context).read() },
        batchRunner =
            ContinuousDatasetBatchRunner { request ->
                ParallelScientificDatasetGenerator().generate(
                    config = request.config,
                    csvFile = request.csvFile,
                    existingRowCount = request.existingRowCount,
                    generationIndex = request.generationIndex,
                    workerCount = request.workerCount,
                    cancellationRequested = request.cancellationRequested,
                    onProgress = request.onProgress,
                    onCheckpoint = request.onCheckpoint,
                    lockedPreflight = request.lockedPreflight
                )
            },
        freeStorageBytes = {
            val outputDirectory = DatasetStorageRepository(context).outputDirectory()
            kotlin.runCatching {
                val storage = context.getSystemService(StorageManager::class.java)
                storage.getAllocatableBytes(storage.getUuidForPath(outputDirectory))
            }.getOrElse { outputDirectory.usableSpace }
        }
    )

    private val lifecycleMutex = Mutex()
    private val cancellationRequested = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val nextRunToken = AtomicLong(0L)
    private val activeRunToken = AtomicLong(NO_ACTIVE_RUN_TOKEN)
    @Volatile
    private var activeJob: Job? = null
    private val restored = settingsRepository.load()
    @Volatile
    private var pendingBatch: ContinuousDatasetPendingBatch? = restored?.pendingBatch
    private val _state =
        MutableStateFlow(
            restored?.let { saved ->
                ContinuousDatasetState(
                    status =
                        if (saved.wasRunning) {
                            ContinuousDatasetStatus.INTERRUPTED
                        } else {
                            ContinuousDatasetStatus.PAUSED
                        },
                    plan = saved.plan,
                    committedDatasetRows =
                        storageRepository.loadManifest(saved.plan.datasetName)?.rowCount ?: 0L,
                    message =
                        if (saved.wasRunning) {
                            "The previous app process ended during continuous growth. Committed batches are safe; press Resume to reconcile the final checkpoint."
                        } else {
                            "The previous continuous plan is ready to resume."
                        }
                )
            } ?: ContinuousDatasetState()
        )

    val state: StateFlow<ContinuousDatasetState> = _state.asStateFlow()

    @Synchronized
    fun start(
        plan: ContinuousDatasetPlan,
        availableRobots: List<SavedRobot>
    ): Result<Unit> {
        if (closed.get()) {
            return Result.failure(IllegalStateException("Continuous dataset coordinator is already closed."))
        }
        if (activeJob?.isActive == true) {
            return Result.failure(IllegalStateException("Continuous dataset growth is already active."))
        }
        val result = runCatching {
            val safePlan = plan.validated()
            val savedRecovery = settingsRepository.load()
            check(
                savedRecovery?.pendingBatch == null ||
                    savedRecovery.plan.datasetName == safePlan.datasetName
            ) {
                "Resume the interrupted dataset '${savedRecovery?.plan?.datasetName}' before starting a different one."
            }
            val robotsById = availableRobots.associateBy(SavedRobot::id)
            val frozenRobots = safePlan.robotIds.map { id ->
                requireNotNull(robotsById[id]) { "Selected robot '$id' is no longer available." }
            }
            check(frozenRobots.map(SavedRobot::id).distinct().size == frozenRobots.size)
            savedRecovery?.pendingBatch?.let { pending ->
                requirePendingIdentity(pending, safePlan, frozenRobots)
            }
            if (savedRecovery?.pendingBatch == null) {
                DatasetScientificContract.requireCompatible(
                    storageRepository.loadManifest(safePlan.datasetName),
                    generationConfig(safePlan, frozenRobots, 1)
                )
            }

            cancellationRequested.set(false)
            settingsRepository.save(
                ContinuousDatasetRecovery(
                    plan = safePlan,
                    wasRunning = true,
                    pendingBatch = restoredPendingFor(safePlan)
                )
            )
            _state.value =
                _state.value.copy(
                    status = ContinuousDatasetStatus.PREPARING,
                    plan = safePlan,
                    currentBatchProgress = null,
                    message = "Reconciling the saved checkpoint before continuous generation starts."
                )
            val runToken = nextRunToken.incrementAndGet()
            activeRunToken.set(runToken)
            activeJob = scope.launch { runLoop(safePlan, frozenRobots, runToken) }
        }
        result.exceptionOrNull()?.let { error ->
            _state.value =
                _state.value.copy(
                    status = ContinuousDatasetStatus.ERROR,
                    message = "Continuous growth could not start: ${error.message ?: error::class.simpleName}."
                )
        }
        return result
    }

    @Synchronized
    fun pause() {
        if (activeJob?.isActive != true) {
            val plan = _state.value.plan ?: return
            settingsRepository.save(
                ContinuousDatasetRecovery(plan, wasRunning = false, pendingBatch = pendingBatch)
            )
            _state.value = _state.value.copy(status = ContinuousDatasetStatus.PAUSED, message = "Continuous growth is paused.")
            return
        }
        cancellationRequested.set(true)
        _state.value =
            _state.value.copy(
                status = ContinuousDatasetStatus.PAUSING,
                message = "Pause requested. The active calculation is being discarded or committed atomically."
            )
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancellationRequested.set(true)
        _state.value.plan?.let { plan ->
            runCatching {
                settingsRepository.save(
                    ContinuousDatasetRecovery(
                        plan = plan,
                        wasRunning = false,
                        pendingBatch = pendingBatch
                    )
                )
            }
        }
        scope.cancel()
    }

    private suspend fun runLoop(
        plan: ContinuousDatasetPlan,
        robots: List<SavedRobot>,
        runToken: Long
    ) {
        lifecycleMutex.withLock {
            var rowsAddedThisSession = 0L
            var committedBatches = 0
            try {
                reconcilePendingCommit(plan, robots)
                verifyStoredDataset(plan)

                while (currentCoroutineContext().isActive && !cancellationRequested.get()) {
                    val policy = ContinuousDatasetResourceResolver.resolve(plan, deviceProfileProvider())
                    if (cancellationRequested.get()) break
                    publishPolicy(policy)
                    if (policy.waitForDevice) {
                        _state.value =
                            _state.value.copy(
                                status = ContinuousDatasetStatus.THROTTLED,
                                message = policy.safetyMessage
                            )
                        cooperativeDelay(DEVICE_RECHECK_MILLIS)
                        continue
                    }

                    val existingManifest = storageRepository.loadManifest(plan.datasetName)
                    val csvFile = existingManifest?.let { File(it.csvPath) } ?: storageRepository.resolveCsvFile(plan.datasetName)
                    if (existingManifest == null && csvFile.isFile && csvFile.length() > 0L) {
                        error("A CSV with this name exists without a valid manifest. Rename it or repair it before continuous append.")
                    }
                    val existingRows = existingManifest?.rowCount ?: 0L
                    val generationIndex = existingManifest?.generationCount ?: 0
                    check(generationIndex < MAXIMUM_GENERATIONS) {
                        "This dataset reached the supported 10,000-batch provenance limit. Start a new continuous dataset to continue."
                    }
                    val requestedRows = policy.rowsPerRobotPerBatch.toLong() * robots.size.toLong()
                    val requiredFreeBytes = MINIMUM_FREE_STORAGE_BYTES + requestedRows * ESTIMATED_OUTPUT_BYTES_PER_ROW
                    check(freeStorageBytes() >= requiredFreeBytes) {
                        "Continuous growth paused before the storage safety reserve could be crossed."
                    }
                    if (cancellationRequested.get()) break

                    val config =
                        DatasetGenerationConfig(
                            datasetName = plan.datasetName,
                            robots = robots,
                            samplesPerRobot = policy.rowsPerRobotPerBatch,
                            randomSeed = plan.randomSeed,
                            targetMode = plan.targetMode,
                            reachableFraction = plan.reachableFraction,
                            filterMode = plan.filterMode,
                            append = existingManifest != null,
                            maxAttemptsMultiplier = plan.maxAttemptsMultiplier,
                            ikConfig = plan.ikConfig,
                            metricPolicy = plan.metricPolicy
                        )
                    val batchMayStart =
                        synchronized(this) {
                            if (cancellationRequested.get()) {
                                false
                            } else {
                                pendingBatch =
                                    ContinuousDatasetPendingBatch(
                                        existingRowCount = existingRows,
                                        generationIndex = generationIndex,
                                        samplesPerRobot = policy.rowsPerRobotPerBatch,
                                        startedAtEpochMillis = clockMillis(),
                                        batchFingerprint = DatasetScientificContract.batchFingerprint(config, existingRows, generationIndex)
                                    )
                                settingsRepository.save(
                                    ContinuousDatasetRecovery(plan, wasRunning = true, pendingBatch = pendingBatch)
                                )
                                _state.value =
                                    _state.value.copy(
                                        status = ContinuousDatasetStatus.RUNNING,
                                        committedDatasetRows = existingRows,
                                        currentBatchProgress = null,
                                        message = "Generating deterministic batch ${generationIndex + 1}."
                                    )
                                true
                            }
                        }
                    if (!batchMayStart) break

                    val batchStartedAt = clockMillis()
                    var checkpointManifestSaved = false
                    val result =
                        batchRunner.run(
                            ContinuousDatasetBatchRequest(
                                config = config,
                                csvFile = csvFile,
                                existingRowCount = existingRows,
                                generationIndex = generationIndex,
                                workerCount = policy.workerCount,
                                lockedPreflight = {
                                    val currentManifest = storageRepository.loadManifest(plan.datasetName)
                                    check(currentManifest == existingManifest) {
                                        "The dataset changed before this batch acquired its write lock. Resume using the latest checkpoint."
                                    }
                                    DatasetScientificContract.requireCompatible(currentManifest, config)
                                },
                                cancellationRequested = cancellationRequested,
                                onProgress = { progress ->
                                    if (isCurrentRun(runToken) && !cancellationRequested.get()) {
                                        _state.value =
                                            _state.value.copy(
                                                status = ContinuousDatasetStatus.RUNNING,
                                                currentBatchProgress = progress,
                                                message = progress.message
                                        )
                                    }
                                },
                                onCheckpoint = checkpoint@ { addedRows, totalRows ->
                                    // Batch runners are synchronous by contract, but a faulty or
                                    // adapted runner can retain callbacks. Never let an earlier
                                    // lifecycle publish data after a new run owns this coordinator.
                                    if (!isCurrentRun(runToken)) return@checkpoint
                                    storageRepository.saveManifest(
                                        buildDatasetManifest(
                                            existingManifest = existingManifest,
                                            config = config,
                                            csvPath = csvFile.absolutePath,
                                            totalRows = totalRows,
                                            addedRows = addedRows.toLong(),
                                            generationIndex = generationIndex,
                                            updatedAtEpochMillis = clockMillis()
                                        )
                                    )
                                    checkpointManifestSaved = true
                                }
                            )
                        )

                    if (result.cancelled) {
                        pendingBatch = null
                        break
                    }
                    check(result.completed && result.addedRows > 0) { result.message }
                    if (!checkpointManifestSaved) {
                        storageRepository.saveManifest(
                            buildDatasetManifest(
                                existingManifest = existingManifest,
                                config = config,
                                csvPath = result.csvPath,
                                totalRows = result.totalRows,
                                addedRows = result.addedRows.toLong(),
                                generationIndex = generationIndex,
                                updatedAtEpochMillis = clockMillis()
                            )
                        )
                    }
                    pendingBatch = null
                    rowsAddedThisSession += result.addedRows.toLong()
                    committedBatches += 1
                    settingsRepository.save(ContinuousDatasetRecovery(plan, wasRunning = true))
                    _state.value =
                        _state.value.copy(
                            status = ContinuousDatasetStatus.RUNNING,
                            committedDatasetRows = result.totalRows,
                            rowsAddedThisSession = rowsAddedThisSession,
                            committedBatchesThisSession = committedBatches,
                            currentBatchProgress = null,
                            lastCheckpointEpochMillis = clockMillis(),
                            message = "Batch ${generationIndex + 1} committed safely; preparing the next batch."
                        )

                    val activeMillis = (clockMillis() - batchStartedAt).coerceAtLeast(0L)
                    val cooldown = ContinuousDatasetResourceResolver.idleDelayMillis(activeMillis, policy.dutyCycle)
                    if (cooldown > 0L) {
                        _state.value =
                            _state.value.copy(
                                status = ContinuousDatasetStatus.THROTTLED,
                                message = "Checkpoint saved. Cooling down to respect the ${plan.cpuBudgetPercent}% CPU budget."
                            )
                        cooperativeDelay(cooldown)
                    }
                }

                settingsRepository.save(ContinuousDatasetRecovery(plan, wasRunning = false))
                _state.value =
                    _state.value.copy(
                        status = ContinuousDatasetStatus.PAUSED,
                        currentBatchProgress = null,
                        message = "Continuous growth paused. Every completed batch remains available."
                    )
            } catch (_: CancellationException) {
                settingsRepository.save(
                    ContinuousDatasetRecovery(plan, wasRunning = false, pendingBatch = pendingBatch)
                )
            } catch (error: Exception) {
                settingsRepository.save(
                    ContinuousDatasetRecovery(plan, wasRunning = false, pendingBatch = pendingBatch)
                )
                _state.value =
                    _state.value.copy(
                        status = ContinuousDatasetStatus.ERROR,
                        currentBatchProgress = null,
                        message = "Continuous growth stopped safely: ${error.message ?: error::class.simpleName}."
                    )
            } finally {
                activeRunToken.compareAndSet(runToken, NO_ACTIVE_RUN_TOKEN)
                activeJob = null
            }
        }
    }

    private fun generationConfig(plan: ContinuousDatasetPlan, robots: List<SavedRobot>, samples: Int) =
        DatasetGenerationConfig(
            datasetName = plan.datasetName, robots = robots, samplesPerRobot = samples,
            randomSeed = plan.randomSeed, targetMode = plan.targetMode,
            reachableFraction = plan.reachableFraction, filterMode = plan.filterMode,
            append = true, maxAttemptsMultiplier = plan.maxAttemptsMultiplier,
            ikConfig = plan.ikConfig, metricPolicy = plan.metricPolicy
        )

    private fun requirePendingIdentity(
        pending: ContinuousDatasetPendingBatch,
        plan: ContinuousDatasetPlan,
        robots: List<SavedRobot>
    ) {
        check(pending.batchFingerprint != null) {
            "This legacy pending batch has no verifiable scientific fingerprint. Recovery was blocked; its CSV and checkpoint were preserved."
        }
        val expected = DatasetScientificContract.batchFingerprint(
            generationConfig(plan, robots, pending.samplesPerRobot),
            pending.existingRowCount, pending.generationIndex
        )
        check(pending.batchFingerprint == expected) {
            "The pending batch belongs to different ordered robots or scientific settings. Restore its original configuration before resuming."
        }
    }

    private fun isCurrentRun(runToken: Long): Boolean = activeRunToken.get() == runToken

    private fun restoredPendingFor(plan: ContinuousDatasetPlan): ContinuousDatasetPendingBatch? {
        val saved = settingsRepository.load() ?: return null
        return saved.pendingBatch.takeIf { saved.plan.datasetName == plan.datasetName }
    }

    private fun reconcilePendingCommit(
        plan: ContinuousDatasetPlan,
        robots: List<SavedRobot>
    ) {
        val saved = settingsRepository.load() ?: return
        val pending = saved.pendingBatch ?: return
        if (saved.plan.datasetName != plan.datasetName) return
        requirePendingIdentity(pending, plan, robots)
        val manifest = storageRepository.loadManifest(plan.datasetName)
        val csvFile = manifest?.let { File(it.csvPath) } ?: storageRepository.resolveCsvFile(plan.datasetName)
        if (csvFile.exists()) {
            check(csvFile.bufferedReader().use { it.readLine() } == ScientificDatasetCsvWriter.HEADER.joinToString(",")) {
                "Pending dataset schema changed. Recovery was blocked without modifying the CSV."
            }
        }
        val actualRows = storageRepository.countCsvDataRows(csvFile)
        val committedRows = manifest?.rowCount ?: pending.existingRowCount

        when {
            actualRows == committedRows -> Unit
            (manifest?.rowCount ?: pending.existingRowCount) == pending.existingRowCount &&
                actualRows == pending.existingRowCount + pending.samplesPerRobot.toLong() * robots.size -> {
                val recoveredConfig =
                    DatasetGenerationConfig(
                        datasetName = plan.datasetName,
                        robots = robots,
                        samplesPerRobot = pending.samplesPerRobot,
                        randomSeed = plan.randomSeed,
                        targetMode = plan.targetMode,
                        reachableFraction = plan.reachableFraction,
                        filterMode = plan.filterMode,
                        append = manifest != null,
                        maxAttemptsMultiplier = plan.maxAttemptsMultiplier,
                        ikConfig = plan.ikConfig,
                        metricPolicy = plan.metricPolicy
                    )
                storageRepository.saveManifest(
                    buildDatasetManifest(
                        existingManifest = manifest,
                        config = recoveredConfig,
                        csvPath = csvFile.absolutePath,
                        totalRows = actualRows,
                        addedRows = actualRows - pending.existingRowCount,
                        generationIndex = pending.generationIndex,
                        updatedAtEpochMillis = clockMillis()
                    )
                )
            }
            else -> error(
                "The CSV and its saved checkpoint disagree. Continuous append was blocked to protect scientific provenance."
            )
        }
        pendingBatch = null
        settingsRepository.save(ContinuousDatasetRecovery(plan, wasRunning = true))
        _state.value =
            _state.value.copy(
                committedDatasetRows = actualRows,
                lastCheckpointEpochMillis = clockMillis(),
                message = "Saved checkpoint reconciled successfully."
            )
    }

    /**
     * Count once when a session starts. Counting before every append would turn a long-running
     * dataset into an O(n²) process, while skipping the startup check could append to a missing or
     * externally truncated CSV using a stale manifest row count.
     */
    private fun verifyStoredDataset(plan: ContinuousDatasetPlan) {
        storageRepository.resolveAppendOrCreateTarget(plan.datasetName)
    }

    private fun publishPolicy(policy: ContinuousDatasetResourcePolicy) {
        _state.value =
            _state.value.copy(
                workerCount = policy.workerCount,
                rowsPerRobotPerBatch = policy.rowsPerRobotPerBatch,
                workingMemoryBudgetBytes = policy.workingMemoryBudgetBytes
            )
    }

    private suspend fun cooperativeDelay(totalMillis: Long) {
        var remaining = totalMillis.coerceAtLeast(0L)
        while (remaining > 0L && !cancellationRequested.get()) {
            val step = minOf(remaining, 1_000L)
            delayMillis(step)
            remaining -= step
        }
    }

    companion object {
        private const val DEVICE_RECHECK_MILLIS = 5_000L
        private const val MINIMUM_FREE_STORAGE_BYTES = 256L * 1_024L * 1_024L
        private const val ESTIMATED_OUTPUT_BYTES_PER_ROW = 4_096L
        private const val MAXIMUM_GENERATIONS = 10_000
        private const val NO_ACTIVE_RUN_TOKEN = 0L
    }
}
