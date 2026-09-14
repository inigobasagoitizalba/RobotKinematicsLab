package com.robotkinematicslab.mobile.performance.compute

import kotlin.math.ceil

enum class ComputeResourcePreset(
    val displayName: String,
    val description: String
) {
    ECO(
        displayName = "Eco",
        description = "Keeps the phone responsive and minimizes sustained heat."
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Uses several cores while preserving comfortable headroom for Android."
    ),
    PERFORMANCE(
        displayName = "Research maximum",
        description = "Uses the enlarged app heap and every training worker except one reserved for Android and the interface."
    ),
    CUSTOM(
        displayName = "Custom",
        description = "Choose a worker count inside the same non-negotiable safety boundary."
    )
}

data class ComputeResourceSettings(
    val preset: ComputeResourcePreset = ComputeResourcePreset.BALANCED,
    val customWorkerCount: Int = 1,
    /** Percentage of this app's heap allowance, not a percentage of the phone's physical RAM. */
    val requestedWorkingMemoryPercent: Int = 35
)

enum class ComputeThermalLevel {
    UNKNOWN,
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN
}

data class DeviceComputeProfile(
    val logicalCpuCores: Int,
    val totalSystemMemoryBytes: Long,
    val availableSystemMemoryBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val appHeapLimitBytes: Long,
    val lowMemory: Boolean,
    val thermalLevel: ComputeThermalLevel,
    val deviceName: String,
    /** Forecast thermal pressure: 1.0 means severe throttling; NaN means unsupported. */
    val thermalHeadroom: Double = Double.NaN,
    /** Whether Android exposes Performance Hint sessions to this app (Android 12+). */
    val performanceHintsSupported: Boolean = false
)

data class ResolvedComputePolicy(
    val preset: ComputeResourcePreset,
    val logicalCpuCores: Int,
    val reservedCpuCores: Int,
    val safeMaximumWorkers: Int,
    val effectiveWorkerCount: Int,
    val appHeapLimitBytes: Long,
    val workingMemoryBudgetBytes: Long,
    val estimatedSafeTrainingRows: Int,
    val thermalLevel: ComputeThermalLevel,
    val lowMemory: Boolean,
    val safetyMessage: String,
    /** Preset request before thermal and low-memory limits are applied. */
    val requestedWorkerCount: Int = effectiveWorkerCount
)

/** Pure policy so the safety boundary can be exhaustively unit tested without an Android device. */
object SafeComputePolicyResolver {
    private const val MINIMUM_MEMORY_PERCENT = 10
    const val MAXIMUM_MEMORY_PERCENT = 35
    const val MAXIMUM_APP_WORKERS = 2
    private const val ESTIMATED_BYTES_PER_TRAINING_ROW = 2_048L

    fun resolve(
        settings: ComputeResourceSettings,
        device: DeviceComputeProfile
    ): ResolvedComputePolicy {
        val logicalCores = device.logicalCpuCores.coerceAtLeast(1)
        val reservedCores =
            when {
                logicalCores >= 2 -> 1
                else -> 0
            }
        val safeMaximumWorkers =
            (logicalCores - reservedCores).coerceIn(1, MAXIMUM_APP_WORKERS)
        val effectivePreset =
            when (settings.preset) {
                ComputeResourcePreset.PERFORMANCE,
                ComputeResourcePreset.CUSTOM -> ComputeResourcePreset.BALANCED
                else -> settings.preset
            }
        val requestedWorkers =
            when (effectivePreset) {
                ComputeResourcePreset.ECO -> ceil(safeMaximumWorkers * 0.25).toInt()
                ComputeResourcePreset.BALANCED -> ceil(safeMaximumWorkers * 0.50).toInt()
                ComputeResourcePreset.PERFORMANCE,
                ComputeResourcePreset.CUSTOM -> error("Aggressive presets must be normalised")
            }.coerceIn(1, safeMaximumWorkers)

        val thermallyLimitedWorkers =
            when (device.thermalLevel) {
                ComputeThermalLevel.MODERATE -> minOf(requestedWorkers, ceil(safeMaximumWorkers * 0.50).toInt())
                ComputeThermalLevel.SEVERE,
                ComputeThermalLevel.CRITICAL,
                ComputeThermalLevel.EMERGENCY,
                ComputeThermalLevel.SHUTDOWN -> 1
                else -> requestedWorkers
            }
        val headroomLimitedWorkers =
            when {
                !device.thermalHeadroom.isFinite() -> thermallyLimitedWorkers
                device.thermalHeadroom >= 1.0 -> 1
                device.thermalHeadroom >= 0.85 ->
                    minOf(thermallyLimitedWorkers, ceil(safeMaximumWorkers * 0.50).toInt())
                else -> thermallyLimitedWorkers
            }
        val effectiveWorkers = if (device.lowMemory) 1 else headroomLimitedWorkers.coerceAtLeast(1)

        val selectedMemoryPercent =
            settings.requestedWorkingMemoryPercent.coerceIn(
                MINIMUM_MEMORY_PERCENT,
                MAXIMUM_MEMORY_PERCENT
            )
        val memoryPercent = selectedMemoryPercent
        // The profiler value is a hard ceiling. Inflating a small or synthetic value to a
        // convenient minimum would make the "safe" policy authorize memory Android did not grant.
        val heapLimit = device.appHeapLimitBytes.coerceAtLeast(1L)
        // Divide before multiplying so even a synthetic Long.MAX_VALUE device profile
        // cannot wrap the safety budget into a negative number.
        val heapBudget =
            heapLimit / 100L * memoryPercent +
                (heapLimit % 100L) * memoryPercent / 100L
        val availableMemory = device.availableSystemMemoryBytes.coerceAtLeast(0L)
        val lowMemoryThreshold = device.lowMemoryThresholdBytes.coerceAtLeast(0L)
        val availableAboveSystemThreshold =
            if (availableMemory > lowMemoryThreshold) {
                availableMemory - lowMemoryThreshold
            } else {
                0L
            }
        val systemPressureCap =
            if (availableAboveSystemThreshold > 0L) {
                availableAboveSystemThreshold / 4L
            } else {
                heapBudget
            }
        val normalBudget = minOf(heapBudget, systemPressureCap)
        val workingBudget = if (device.lowMemory) minOf(normalBudget, heapLimit / 10L) else normalBudget
        val estimatedRows =
            (workingBudget / ESTIMATED_BYTES_PER_TRAINING_ROW)
                .coerceAtMost(1_000_000L)
                .toInt()

        val safetyMessage =
            when {
                device.lowMemory -> "Android reports low memory: one worker is enforced."
                device.thermalHeadroom.isFinite() && device.thermalHeadroom >= 1.0 ->
                    "Forecast thermal headroom reached severe throttling: one worker is enforced until the device cools."
                device.thermalHeadroom.isFinite() && device.thermalHeadroom >= 0.85 ->
                    "Forecast thermal headroom is nearly exhausted: worker use is reduced before severe throttling."
                device.thermalLevel >= ComputeThermalLevel.SEVERE ->
                    "Thermal pressure is severe: one worker is enforced until the device cools."
                device.thermalLevel == ComputeThermalLevel.MODERATE ->
                    "Thermal pressure is moderate: worker use is temporarily reduced."
                effectiveWorkers == safeMaximumWorkers ->
                    "$reservedCores logical core(s) remain reserved for Android and the interface."
                else -> "Operating inside the safe CPU and app-heap envelope."
            }

        return ResolvedComputePolicy(
            preset = effectivePreset,
            logicalCpuCores = logicalCores,
            reservedCpuCores = reservedCores,
            safeMaximumWorkers = safeMaximumWorkers,
            effectiveWorkerCount = effectiveWorkers,
            appHeapLimitBytes = heapLimit,
            workingMemoryBudgetBytes = workingBudget,
            estimatedSafeTrainingRows = estimatedRows,
            thermalLevel = device.thermalLevel,
            lowMemory = device.lowMemory,
            safetyMessage = safetyMessage,
            requestedWorkerCount = requestedWorkers
        )
    }
}

/** Resolves the workers that can do useful work in one bounded batch. */
object ComputeWorkerAllocation {
    fun resolve(
        configuredWorkerCount: Int,
        runtimeWorkerLimit: Int,
        availableWorkItems: Int
    ): Int {
        require(configuredWorkerCount > 0) { "Configured worker count must be positive." }
        require(availableWorkItems > 0) { "A worker allocation requires at least one work item." }
        return minOf(
            configuredWorkerCount,
            runtimeWorkerLimit.coerceAtLeast(1),
            availableWorkItems
        )
    }
}
