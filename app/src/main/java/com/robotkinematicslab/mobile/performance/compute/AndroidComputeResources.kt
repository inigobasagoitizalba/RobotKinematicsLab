package com.robotkinematicslab.mobile.performance.compute

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.PerformanceHintManager
import android.os.PowerManager
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import java.io.File
import java.util.Properties

class AndroidDeviceComputeProfiler(
    private val context: Context
) {
    fun read(): DeviceComputeProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val runtimeHeapLimit = Runtime.getRuntime().maxMemory()
        val largeHeapRequested = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        val declaredMemoryClass =
            if (largeHeapRequested) {
                activityManager?.largeMemoryClass ?: 0
            } else {
                activityManager?.memoryClass ?: 0
            }
        val memoryClassLimit = declaredMemoryClass.toLong() * BYTES_PER_MIB
        val appHeapLimit =
            listOf(runtimeHeapLimit, memoryClassLimit)
                .filter { it > 0L }
                .minOrNull()
                ?: runtimeHeapLimit

        return DeviceComputeProfile(
            logicalCpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalSystemMemoryBytes = memoryInfo.totalMem.coerceAtLeast(0L),
            availableSystemMemoryBytes = memoryInfo.availMem.coerceAtLeast(0L),
            lowMemoryThresholdBytes = memoryInfo.threshold.coerceAtLeast(0L),
            appHeapLimitBytes = appHeapLimit.coerceAtLeast(BYTES_PER_MIB),
            lowMemory = memoryInfo.lowMemory,
            thermalLevel = readThermalLevel(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            thermalHeadroom = readThermalHeadroom(),
            performanceHintsSupported = supportsPerformanceHints()
        )
    }

    private fun readThermalLevel(): ComputeThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ComputeThermalLevel.UNKNOWN
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ComputeThermalLevel.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ComputeThermalLevel.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ComputeThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ComputeThermalLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ComputeThermalLevel.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ComputeThermalLevel.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ComputeThermalLevel.SHUTDOWN
            else -> ComputeThermalLevel.UNKNOWN
        }
    }

    private fun readThermalHeadroom(): Double {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Double.NaN
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return runCatching { powerManager?.getThermalHeadroom(THERMAL_FORECAST_SECONDS)?.toDouble() }
            .getOrNull()
            ?.takeIf(Double::isFinite)
            ?: Double.NaN
    }

    private fun supportsPerformanceHints(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(PerformanceHintManager::class.java) != null

    companion object {
        private const val BYTES_PER_MIB = 1_048_576L
        private const val THERMAL_FORECAST_SECONDS = 10
    }
}

class ComputeSettingsRepository(
    private val preferencesDirectory: File
) {
    constructor(context: Context) : this(
        AppStoragePaths(context)
            .also(AppStoragePaths::ensureStructureAndMigrateLegacyData)
            .preferencesDirectory
    )

    private val settingsFile = File(preferencesDirectory, SETTINGS_FILE_NAME)

    @Synchronized
    fun load(): ComputeResourceSettings {
        if (!settingsFile.exists()) return ComputeResourceSettings()
        return runCatching {
            val properties = Properties().apply { settingsFile.inputStream().buffered().use(::load) }
            ComputeResourceSettings(
                preset =
                    runCatching {
                        ComputeResourcePreset.valueOf(properties.getProperty("preset"))
                    }.getOrDefault(ComputeResourcePreset.BALANCED)
                        .takeIf { it == ComputeResourcePreset.ECO || it == ComputeResourcePreset.BALANCED }
                        ?: ComputeResourcePreset.BALANCED,
                customWorkerCount = properties.getProperty("customWorkerCount")?.toIntOrNull()?.coerceIn(1, 2) ?: 1,
                requestedWorkingMemoryPercent =
                    properties.getProperty("requestedWorkingMemoryPercent")
                        ?.toIntOrNull()
                        ?.coerceIn(10, SafeComputePolicyResolver.MAXIMUM_MEMORY_PERCENT)
                        ?: 35
            )
        }.getOrDefault(ComputeResourceSettings())
    }

    @Synchronized
    fun save(settings: ComputeResourceSettings): ComputeResourceSettings {
        val safe =
            settings.copy(
                preset =
                    settings.preset.takeIf {
                        it == ComputeResourcePreset.ECO || it == ComputeResourcePreset.BALANCED
                    } ?: ComputeResourcePreset.BALANCED,
                customWorkerCount = settings.customWorkerCount.coerceIn(1, SafeComputePolicyResolver.MAXIMUM_APP_WORKERS),
                requestedWorkingMemoryPercent =
                    settings.requestedWorkingMemoryPercent.coerceIn(
                        10,
                        SafeComputePolicyResolver.MAXIMUM_MEMORY_PERCENT
                    )
            )
        preferencesDirectory.mkdirs()
        val properties =
            Properties().apply {
                setProperty("schemaVersion", "1")
                setProperty("preset", safe.preset.name)
                setProperty("customWorkerCount", safe.customWorkerCount.toString())
                setProperty("requestedWorkingMemoryPercent", safe.requestedWorkingMemoryPercent.toString())
            }
        AtomicFilePublisher.write(settingsFile) { temporary ->
            temporary.outputStream().buffered().use { properties.store(it, "Safe compute settings") }
        }
        return safe
    }

    companion object {
        private const val SETTINGS_FILE_NAME = "compute-settings.properties"
    }
}

class ComputeRuntimeGuard(
    private val repository: ComputeSettingsRepository,
    private val profiler: AndroidDeviceComputeProfiler,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        repository = ComputeSettingsRepository(context),
        profiler = AndroidDeviceComputeProfiler(context)
    )

    @Volatile
    private var cachedAtMillis: Long = Long.MIN_VALUE

    @Volatile
    private var cachedPolicy: ResolvedComputePolicy? = null

    @Synchronized
    fun currentPolicy(forceRefresh: Boolean = false): ResolvedComputePolicy {
        val now = clockMillis()
        val cached = cachedPolicy
        if (!forceRefresh && cached != null && computePolicyCacheIsFresh(now, cachedAtMillis)) return cached
        return SafeComputePolicyResolver.resolve(repository.load(), profiler.read()).also {
            cachedPolicy = it
            cachedAtMillis = now
        }
    }

    fun currentWorkerLimit(): Int = currentPolicy().effectiveWorkerCount

}

internal fun computePolicyCacheIsFresh(nowMillis: Long, cachedAtMillis: Long): Boolean =
    cachedAtMillis >= 0L &&
        nowMillis >= cachedAtMillis &&
        nowMillis - cachedAtMillis < COMPUTE_POLICY_CACHE_MILLIS

private const val COMPUTE_POLICY_CACHE_MILLIS = 1_000L
