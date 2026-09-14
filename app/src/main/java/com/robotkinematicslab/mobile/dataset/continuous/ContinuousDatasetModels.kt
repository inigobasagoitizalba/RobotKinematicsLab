package com.robotkinematicslab.mobile.dataset.continuous

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationProgress
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeResourcePreset
import com.robotkinematicslab.mobile.performance.compute.ComputeResourceSettings
import com.robotkinematicslab.mobile.performance.compute.ComputeThermalLevel
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import com.robotkinematicslab.mobile.performance.compute.SafeComputePolicyResolver
import kotlin.math.ceil

enum class ContinuousDatasetStatus {
    IDLE,
    PREPARING,
    RUNNING,
    THROTTLED,
    PAUSING,
    PAUSED,
    INTERRUPTED,
    ERROR
}

data class ContinuousDatasetPlan(
    val datasetName: String,
    val robotIds: List<String>,
    val randomSeed: Int,
    val targetMode: DatasetTargetMode,
    val reachableFraction: Double,
    val filterMode: DatasetFilterMode,
    val ikConfig: IKConfig,
    val metricPolicy: DiagnosticMetricPolicy = DiagnosticMetricPolicy(),
    val maxAttemptsMultiplier: Int = 30,
    val cpuBudgetPercent: Int = DEFAULT_CPU_PERCENT,
    val memoryBudgetPercent: Int = DEFAULT_MEMORY_PERCENT
) {
    fun validated(): ContinuousDatasetPlan {
        require(datasetName.isNotBlank()) { "Enter a dataset name." }
        require(datasetName.length <= MAXIMUM_DATASET_NAME_LENGTH) {
            "Dataset name must contain at most $MAXIMUM_DATASET_NAME_LENGTH characters."
        }
        require(datasetName.none(Char::isISOControl)) {
            "Dataset name must not contain line breaks or control characters."
        }
        require(robotIds.isNotEmpty()) { "Select at least one robot." }
        require(robotIds.all(String::isNotBlank) && robotIds.distinct().size == robotIds.size) {
            "Selected robot identifiers must be non-blank and unique."
        }
        require(reachableFraction.isFinite() && reachableFraction in 0.0..1.0) {
            "Reachable target percentage must be between 0 and 100."
        }
        require(maxAttemptsMultiplier > 0) { "Attempt multiplier must be positive." }
        require(ikConfig.maxIterations in 1..10_000) { "IK max iterations must be between 1 and 10,000." }
        require(ikConfig.tolerance.isFinite() && ikConfig.tolerance in 1e-9..1.0) {
            "IK tolerance must be finite and between 1e-9 and 1."
        }
        require(ikConfig.damping.isFinite() && ikConfig.damping in 1e-9..10.0) {
            "IK damping must be finite and between 1e-9 and 10."
        }
        require(ikConfig.maxStep.isFinite() && ikConfig.maxStep in 1e-6..10.0) {
            "IK max step must be finite and between 1e-6 and 10."
        }
        require(cpuBudgetPercent in MIN_CPU_PERCENT..MAX_CPU_PERCENT) {
            "Continuous CPU budget must be between $MIN_CPU_PERCENT% and $MAX_CPU_PERCENT%."
        }
        require(memoryBudgetPercent in MIN_MEMORY_PERCENT..MAX_MEMORY_PERCENT) {
            "Continuous memory ceiling must be between $MIN_MEMORY_PERCENT% and $MAX_MEMORY_PERCENT%."
        }
        return copy(
            datasetName = datasetName.trim(),
            robotIds = robotIds.distinct()
        )
    }

    companion object {
        const val MIN_CPU_PERCENT = 5
        const val MAX_CPU_PERCENT = 100
        const val DEFAULT_CPU_PERCENT = 10
        const val MIN_MEMORY_PERCENT = 10
        const val MAX_MEMORY_PERCENT = 50
        const val DEFAULT_MEMORY_PERCENT = 20
        const val MAXIMUM_DATASET_NAME_LENGTH = 160
    }
}

data class ContinuousDatasetPendingBatch(
    val existingRowCount: Long,
    val generationIndex: Int,
    val samplesPerRobot: Int,
    val startedAtEpochMillis: Long,
    val batchFingerprint: String? = null
) {
    init {
        require(batchFingerprint == null || com.robotkinematicslab.mobile.dataset.DatasetScientificContract.isValidFingerprint(batchFingerprint))
        require(existingRowCount >= 0L)
        require(generationIndex >= 0)
        require(samplesPerRobot > 0)
        require(startedAtEpochMillis >= 0L)
    }
}

data class ContinuousDatasetRecovery(
    val plan: ContinuousDatasetPlan,
    val wasRunning: Boolean,
    val pendingBatch: ContinuousDatasetPendingBatch? = null
)

data class ContinuousDatasetResourcePolicy(
    val workerCount: Int,
    val rowsPerRobotPerBatch: Int,
    val workingMemoryBudgetBytes: Long,
    val dutyCycle: Double,
    val waitForDevice: Boolean,
    val safetyMessage: String
)

data class ContinuousDatasetState(
    val status: ContinuousDatasetStatus = ContinuousDatasetStatus.IDLE,
    val plan: ContinuousDatasetPlan? = null,
    val committedDatasetRows: Long = 0L,
    val rowsAddedThisSession: Long = 0L,
    val committedBatchesThisSession: Int = 0,
    val currentBatchProgress: DatasetGenerationProgress? = null,
    val workerCount: Int = 0,
    val rowsPerRobotPerBatch: Int = 0,
    val workingMemoryBudgetBytes: Long = 0L,
    val lastCheckpointEpochMillis: Long? = null,
    val message: String = "Continuous dataset growth is ready."
) {
    val isActive: Boolean
        get() = status in setOf(
            ContinuousDatasetStatus.PREPARING,
            ContinuousDatasetStatus.RUNNING,
            ContinuousDatasetStatus.THROTTLED,
            ContinuousDatasetStatus.PAUSING
        )
}

object ContinuousDatasetResourceResolver {
    private const val ESTIMATED_TRANSIENT_BYTES_PER_ROW = 8_192L

    fun resolve(
        plan: ContinuousDatasetPlan,
        device: DeviceComputeProfile
    ): ContinuousDatasetResourcePolicy {
        val safePlan = plan.validated()
        val logicalCores = device.logicalCpuCores.coerceAtLeast(1)
        val safeMaximumWorkers = (logicalCores - if (logicalCores > 1) 1 else 0).coerceAtLeast(1)
        val requestedWorkers =
            ceil(safeMaximumWorkers * safePlan.cpuBudgetPercent / 100.0)
                .toInt()
                .coerceIn(1, safeMaximumWorkers)
        val resolved =
            SafeComputePolicyResolver.resolve(
                settings =
                    ComputeResourceSettings(
                        preset = ComputeResourcePreset.CUSTOM,
                        customWorkerCount = requestedWorkers,
                        requestedWorkingMemoryPercent = safePlan.memoryBudgetPercent
                    ),
                device = device
            )
        val activeCpuShare =
            (resolved.effectiveWorkerCount.toDouble() / logicalCores.toDouble())
                .coerceIn(1.0 / logicalCores.toDouble(), 1.0)
        val dutyCycle =
            (safePlan.cpuBudgetPercent / 100.0 / activeCpuShare)
                .coerceIn(0.05, 1.0)
        val memoryLimitedRows =
            (resolved.workingMemoryBudgetBytes /
                (safePlan.robotIds.size.coerceAtLeast(1).toLong() * ESTIMATED_TRANSIENT_BYTES_PER_ROW))
                .coerceIn(5L, 1_000L)
                .toInt()
        val responsivenessCap =
            when {
                safePlan.cpuBudgetPercent <= 10 -> 25
                safePlan.cpuBudgetPercent <= 20 -> 50
                safePlan.cpuBudgetPercent <= 35 -> 100
                safePlan.cpuBudgetPercent <= 50 -> 200
                safePlan.cpuBudgetPercent <= 75 -> 500
                else -> 1_000
            }
        val waitForDevice =
            resolved.lowMemory ||
                resolved.thermalLevel >= ComputeThermalLevel.SEVERE ||
                (device.thermalHeadroom.isFinite() && device.thermalHeadroom >= 1.0)

        return ContinuousDatasetResourcePolicy(
            workerCount = resolved.effectiveWorkerCount,
            rowsPerRobotPerBatch = minOf(memoryLimitedRows, responsivenessCap).coerceAtLeast(5),
            workingMemoryBudgetBytes = resolved.workingMemoryBudgetBytes,
            dutyCycle = dutyCycle,
            waitForDevice = waitForDevice,
            safetyMessage =
                if (waitForDevice) {
                    "Generation is waiting because Android reports memory or thermal pressure."
                } else {
                    "${resolved.safetyMessage} CPU use is an approximate average budget, not a hard Android quota."
                }
        )
    }

    fun idleDelayMillis(
        activeBatchMillis: Long,
        dutyCycle: Double
    ): Long {
        require(activeBatchMillis >= 0L)
        require(dutyCycle.isFinite() && dutyCycle in 0.0..1.0)
        if (activeBatchMillis == 0L || dutyCycle >= 0.999) return 0L
        return (activeBatchMillis * (1.0 - dutyCycle) / dutyCycle)
            .toLong()
            .coerceIn(0L, 60_000L)
    }
}
