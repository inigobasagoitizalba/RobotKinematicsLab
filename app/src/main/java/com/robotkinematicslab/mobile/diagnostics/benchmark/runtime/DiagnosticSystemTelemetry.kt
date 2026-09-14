package com.robotkinematicslab.mobile.diagnostics.benchmark.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import com.robotkinematicslab.mobile.ui.shared.progress.SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS
import java.io.File

data class DiagnosticCpuFrequencySnapshot(
    val readableCoreCount: Int = 0,
    val minFrequencyMhz: Double = Double.NaN,
    val maxFrequencyMhz: Double = Double.NaN,
    val averageFrequencyMhz: Double = Double.NaN,
    val frequenciesMhz: List<Double> = emptyList()
)

data class DiagnosticThermalZoneSnapshot(
    val name: String,
    val type: String,
    val temperatureCelsius: Double
)



data class DiagnosticSystemTelemetry(
    val usedRuntimeMemoryMb: Double = Double.NaN,
    val freeRuntimeMemoryMb: Double = Double.NaN,
    val maxRuntimeMemoryMb: Double = Double.NaN,
    val totalRuntimeMemoryMb: Double = Double.NaN,

    val nativeHeapAllocatedMb: Double = Double.NaN,
    val nativeHeapFreeMb: Double = Double.NaN,
    val nativeHeapSizeMb: Double = Double.NaN,

    val availableSystemMemoryMb: Double = Double.NaN,
    val totalSystemMemoryMb: Double = Double.NaN,
    val lowMemory: Boolean? = null,

    val cpuCoreCount: Int = Runtime.getRuntime().availableProcessors(),
    val systemLoadAverage: Double = Double.NaN,
    val processCpuTimeMs: Long = -1L,
    val currentThreadCpuTimeMs: Long = -1L,
    val cpuFrequencySnapshot: DiagnosticCpuFrequencySnapshot = DiagnosticCpuFrequencySnapshot(),

    val gcCount: Long = -1L,
    val gcTimeMs: Long = -1L,
    val blockingGcCount: Long = -1L,
    val blockingGcTimeMs: Long = -1L,
    val allocationHotspots: List<DiagnosticAllocationHotspot> = emptyList(),

    val batteryLevelPercent: Double = Double.NaN,
    val batteryTemperatureCelsius: Double = Double.NaN,
    val isCharging: Boolean? = null,

    val thermalStatus: String = "N/A",
    val socTemperatureCelsius: Double = Double.NaN,
    val thermalZones: List<DiagnosticThermalZoneSnapshot> = emptyList(),

    val note: String = ""
)

private data class DiagnosticSlowSystemTelemetry(
    val systemLoadAverage: Double,
    val cpuFrequencySnapshot: DiagnosticCpuFrequencySnapshot,
    val batteryLevelPercent: Double,
    val batteryTemperatureCelsius: Double,
    val isCharging: Boolean?,
    val thermalStatus: String,
    val socTemperatureCelsius: Double,
    val thermalZones: List<DiagnosticThermalZoneSnapshot>
)

class DiagnosticSystemTelemetrySampler(
    private val context: Context
) {
    private val slowTelemetryLock = Any()

    @Volatile
    private var cachedSlowTelemetry: DiagnosticSlowSystemTelemetry? = null

    @Volatile
    private var slowTelemetryValidUntilNanos: Long = Long.MIN_VALUE

    fun sample(): DiagnosticSystemTelemetry {
        val runtime =
            Runtime.getRuntime()

        val totalRuntimeBytes =
            runtime.totalMemory()

        val freeRuntimeBytes =
            runtime.freeMemory()

        val maxRuntimeBytes =
            runtime.maxMemory()

        val usedRuntimeBytes =
            totalRuntimeBytes - freeRuntimeBytes

        val memoryInfo =
            ActivityManager.MemoryInfo()

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

        activityManager?.getMemoryInfo(memoryInfo)

        val slowTelemetry = slowTelemetrySnapshot()

        return DiagnosticSystemTelemetry(
            usedRuntimeMemoryMb = bytesToMb(usedRuntimeBytes),
            freeRuntimeMemoryMb = bytesToMb(freeRuntimeBytes),
            maxRuntimeMemoryMb = bytesToMb(maxRuntimeBytes),
            totalRuntimeMemoryMb = bytesToMb(totalRuntimeBytes),

            nativeHeapAllocatedMb = bytesToMb(Debug.getNativeHeapAllocatedSize()),
            nativeHeapFreeMb = bytesToMb(Debug.getNativeHeapFreeSize()),
            nativeHeapSizeMb = bytesToMb(Debug.getNativeHeapSize()),

            availableSystemMemoryMb =
                if (activityManager != null) {
                    bytesToMb(memoryInfo.availMem)
                } else {
                    Double.NaN
                },
            totalSystemMemoryMb =
                if (activityManager != null) {
                    bytesToMb(memoryInfo.totalMem)
                } else {
                    Double.NaN
                },
            lowMemory =
                if (activityManager != null) {
                    memoryInfo.lowMemory
                } else {
                    null
                },

            cpuCoreCount = runtime.availableProcessors(),
            systemLoadAverage = slowTelemetry.systemLoadAverage,
            processCpuTimeMs = Process.getElapsedCpuTime(),
            currentThreadCpuTimeMs = Debug.threadCpuTimeNanos() / 1_000_000L,
            cpuFrequencySnapshot = slowTelemetry.cpuFrequencySnapshot,

            gcCount = readRuntimeStatLong("art.gc.gc-count"),
            gcTimeMs = readRuntimeStatLong("art.gc.gc-time"),
            blockingGcCount = readRuntimeStatLong("art.gc.blocking-gc-count"),
            blockingGcTimeMs = readRuntimeStatLong("art.gc.blocking-gc-time"),
            allocationHotspots = DiagnosticAllocationTracker.snapshot(),

            batteryLevelPercent = slowTelemetry.batteryLevelPercent,
            batteryTemperatureCelsius = slowTelemetry.batteryTemperatureCelsius,
            isCharging = slowTelemetry.isCharging,

            thermalStatus = slowTelemetry.thermalStatus,
            socTemperatureCelsius = slowTelemetry.socTemperatureCelsius,
            thermalZones = slowTelemetry.thermalZones,

            note = "High-resolution runtime, process, GC, allocation, and memory counters are sampled on every timeline point. Slower vendor/system sensors are refreshed once per second to avoid distorting the measured workload. Android may hide some sensors depending on device/vendor/API level."
        )
    }

    private fun slowTelemetrySnapshot(): DiagnosticSlowSystemTelemetry {
        val nowNanos = System.nanoTime()
        cachedSlowTelemetry?.let { cached ->
            if (nowNanos < slowTelemetryValidUntilNanos) {
                return cached
            }
        }

        return synchronized(slowTelemetryLock) {
            val refreshedAtNanos = System.nanoTime()
            cachedSlowTelemetry?.let { cached ->
                if (refreshedAtNanos < slowTelemetryValidUntilNanos) {
                    return@synchronized cached
                }
            }

            val batteryIntent =
                context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
            val thermalZones = readThermalZones()
            val refreshed =
                DiagnosticSlowSystemTelemetry(
                    systemLoadAverage = readSystemLoadAverage(),
                    cpuFrequencySnapshot = readCpuFrequencies(),
                    batteryLevelPercent = batteryLevelPercent(batteryIntent),
                    batteryTemperatureCelsius = batteryTemperatureCelsius(batteryIntent),
                    isCharging = isCharging(batteryIntent),
                    thermalStatus = readThermalStatus(),
                    socTemperatureCelsius = selectBestSocTemperature(thermalZones),
                    thermalZones = thermalZones
                )
            cachedSlowTelemetry = refreshed
            slowTelemetryValidUntilNanos =
                refreshedAtNanos + SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS
            refreshed
        }
    }

    private fun batteryLevelPercent(
        batteryIntent: Intent?
    ): Double {
        if (batteryIntent == null) {
            return Double.NaN
        }

        val level =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                -1
            )

        val scale =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                -1
            )

        return if (level >= 0 && scale > 0) {
            level.toDouble() / scale.toDouble() * 100.0
        } else {
            Double.NaN
        }
    }

    private fun batteryTemperatureCelsius(
        batteryIntent: Intent?
    ): Double {
        if (batteryIntent == null) {
            return Double.NaN
        }

        val rawTenthsCelsius =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                Int.MIN_VALUE
            )

        return if (rawTenthsCelsius != Int.MIN_VALUE) {
            rawTenthsCelsius.toDouble() / 10.0
        } else {
            Double.NaN
        }
    }

    private fun isCharging(
        batteryIntent: Intent?
    ): Boolean? {
        if (batteryIntent == null) {
            return null
        }

        val status =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                -1
            )

        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL ->
                true

            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
                false

            else ->
                null
        }
    }

    private fun readThermalStatus(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager =
                context.getSystemService(Context.POWER_SERVICE) as? PowerManager

            when (powerManager?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE ->
                    "NONE"

                PowerManager.THERMAL_STATUS_LIGHT ->
                    "LIGHT"

                PowerManager.THERMAL_STATUS_MODERATE ->
                    "MODERATE"

                PowerManager.THERMAL_STATUS_SEVERE ->
                    "SEVERE"

                PowerManager.THERMAL_STATUS_CRITICAL ->
                    "CRITICAL"

                PowerManager.THERMAL_STATUS_EMERGENCY ->
                    "EMERGENCY"

                PowerManager.THERMAL_STATUS_SHUTDOWN ->
                    "SHUTDOWN"

                else ->
                    "N/A"
            }
        } else {
            "N/A"
        }
    }

    private fun readSystemLoadAverage(): Double {
        return try {
            File("/proc/loadavg")
                .readText()
                .trim()
                .split(" ")
                .firstOrNull()
                ?.toDoubleOrNull()
                ?: Double.NaN
        } catch (_: Exception) {
            Double.NaN
        }
    }

    private fun readRuntimeStatLong(
        key: String
    ): Long {
        return try {
            Debug.getRuntimeStat(key)
                ?.toLongOrNull()
                ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun readCpuFrequencies(): DiagnosticCpuFrequencySnapshot {
        val frequencies =
            mutableListOf<Double>()

        try {
            val cpuRoot =
                File("/sys/devices/system/cpu")

            val cpuFolders =
                cpuRoot
                    .listFiles()
                    ?.filter {
                        it.name.matches(Regex("cpu\\d+"))
                    }
                    ?: emptyList()

            cpuFolders.forEach { cpuFolder ->
                val frequencyFile =
                    File(cpuFolder, "cpufreq/scaling_cur_freq")

                val rawKhz =
                    if (frequencyFile.exists()) {
                        frequencyFile
                            .readText()
                            .trim()
                            .toDoubleOrNull()
                    } else {
                        null
                    }

                if (rawKhz != null && rawKhz > 0.0) {
                    frequencies += rawKhz / 1000.0
                }
            }
        } catch (_: Exception) {
            // Keep best-effort behavior.
        }

        return if (frequencies.isEmpty()) {
            DiagnosticCpuFrequencySnapshot()
        } else {
            DiagnosticCpuFrequencySnapshot(
                readableCoreCount = frequencies.size,
                minFrequencyMhz = frequencies.minOrNull() ?: Double.NaN,
                maxFrequencyMhz = frequencies.maxOrNull() ?: Double.NaN,
                averageFrequencyMhz = frequencies.average(),
                frequenciesMhz = frequencies
            )
        }
    }

    private fun readThermalZones(): List<DiagnosticThermalZoneSnapshot> {
        val thermalRoot =
            File("/sys/class/thermal")

        return try {
            thermalRoot
                .listFiles()
                ?.filter {
                    it.name.startsWith("thermal_zone")
                }
                ?.mapNotNull { zone ->
                    val type =
                        File(zone, "type")
                            .takeIf {
                                it.exists()
                            }
                            ?.readText()
                            ?.trim()
                            ?: "unknown"

                    val rawTemp =
                        File(zone, "temp")
                            .takeIf {
                                it.exists()
                            }
                            ?.readText()
                            ?.trim()
                            ?.toDoubleOrNull()
                            ?: return@mapNotNull null

                    val tempCelsius =
                        when {
                            rawTemp > 1000.0 ->
                                rawTemp / 1000.0

                            else ->
                                rawTemp
                        }

                    if (tempCelsius.isFinite()) {
                        DiagnosticThermalZoneSnapshot(
                            name = zone.name,
                            type = type,
                            temperatureCelsius = tempCelsius
                        )
                    } else {
                        null
                    }
                }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun selectBestSocTemperature(
        thermalZones: List<DiagnosticThermalZoneSnapshot>
    ): Double {
        val preferred =
            thermalZones.firstOrNull { zone ->
                val type =
                    zone.type.lowercase()

                type.contains("soc") ||
                        type.contains("cpu") ||
                        type.contains("ap") ||
                        type.contains("tsens") ||
                        type.contains("cluster") ||
                        type.contains("big") ||
                        type.contains("little")
            }

        return preferred?.temperatureCelsius
            ?: thermalZones.firstOrNull()?.temperatureCelsius
            ?: Double.NaN
    }

    private fun bytesToMb(
        bytes: Long
    ): Double {
        return bytes.toDouble() / 1024.0 / 1024.0
    }
}
