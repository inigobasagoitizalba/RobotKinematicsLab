package com.robotkinematicslab.mobile.diagnostics.benchmark.runtime

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

data class DiagnosticPerformanceSample(
    val sampleIndex: Int,
    val timestampMs: Long,
    val elapsedSeconds: Double,

    val completedRuns: Int,
    val totalRuns: Int,
    val remainingRuns: Int,
    val progressPercent: Double,
    val calculationsPerSecond: Double,
    val estimatedSecondsRemaining: Double,

    val phase: DiagnosticProgressPhase,
    val currentSeed: Int?,
    val currentLinkCount: Int?,
    val currentJointMode: DiagnosticJointMode?,

    val runtimeMemoryUsedMb: Double,
    val runtimeMemoryFreeMb: Double,
    val runtimeMemoryTotalMb: Double,
    val runtimeMemoryMaxMb: Double,

    val nativeHeapAllocatedMb: Double,
    val nativeHeapFreeMb: Double,
    val nativeHeapSizeMb: Double,

    val availableSystemMemoryMb: Double,
    val totalSystemMemoryMb: Double,
    val lowMemory: Boolean?,

    val cpuCoreCount: Int,
    val systemLoadAverage: Double,
    val processCpuTimeMs: Long,
    val currentThreadCpuTimeMs: Long,
    val readableCpuFrequencyCoreCount: Int,
    val cpuFrequencyMinMhz: Double,
    val cpuFrequencyAverageMhz: Double,
    val cpuFrequencyMaxMhz: Double,

    val gcCount: Long,
    val gcTimeMs: Long,
    val blockingGcCount: Long,
    val blockingGcTimeMs: Long,

    val batteryLevelPercent: Double,
    val batteryTemperatureCelsius: Double,
    val isCharging: Boolean?,
    val thermalStatus: String,
    val socTemperatureCelsius: Double,

    val note: String,
    val allocationHotspots: List<DiagnosticAllocationHotspot> = emptyList()
) {
    companion object {
        fun fromProgressState(
            sampleIndex: Int,
            progressState: DiagnosticProgressState
        ): DiagnosticPerformanceSample {
            val telemetry =
                progressState.telemetry

            return DiagnosticPerformanceSample(
                sampleIndex = sampleIndex,
                timestampMs = System.currentTimeMillis(),
                elapsedSeconds = progressState.elapsedSeconds,

                completedRuns = progressState.completedRuns,
                totalRuns = progressState.totalRuns,
                remainingRuns = progressState.remainingRuns,
                progressPercent = progressState.percent,
                calculationsPerSecond = progressState.runsPerSecond,
                estimatedSecondsRemaining = progressState.estimatedSecondsRemaining,

                phase = progressState.phase,
                currentSeed = progressState.currentSeed,
                currentLinkCount = progressState.currentLinkCount,
                currentJointMode = progressState.currentJointMode,

                runtimeMemoryUsedMb = telemetry.usedRuntimeMemoryMb,
                runtimeMemoryFreeMb = telemetry.freeRuntimeMemoryMb,
                runtimeMemoryTotalMb = telemetry.totalRuntimeMemoryMb,
                runtimeMemoryMaxMb = telemetry.maxRuntimeMemoryMb,

                nativeHeapAllocatedMb = telemetry.nativeHeapAllocatedMb,
                nativeHeapFreeMb = telemetry.nativeHeapFreeMb,
                nativeHeapSizeMb = telemetry.nativeHeapSizeMb,

                availableSystemMemoryMb = telemetry.availableSystemMemoryMb,
                totalSystemMemoryMb = telemetry.totalSystemMemoryMb,
                lowMemory = telemetry.lowMemory,

                cpuCoreCount = telemetry.cpuCoreCount,
                systemLoadAverage = telemetry.systemLoadAverage,
                processCpuTimeMs = telemetry.processCpuTimeMs,
                currentThreadCpuTimeMs = telemetry.currentThreadCpuTimeMs,
                readableCpuFrequencyCoreCount = telemetry.cpuFrequencySnapshot.readableCoreCount,
                cpuFrequencyMinMhz = telemetry.cpuFrequencySnapshot.minFrequencyMhz,
                cpuFrequencyAverageMhz = telemetry.cpuFrequencySnapshot.averageFrequencyMhz,
                cpuFrequencyMaxMhz = telemetry.cpuFrequencySnapshot.maxFrequencyMhz,

                gcCount = telemetry.gcCount,
                gcTimeMs = telemetry.gcTimeMs,
                blockingGcCount = telemetry.blockingGcCount,
                blockingGcTimeMs = telemetry.blockingGcTimeMs,

                batteryLevelPercent = telemetry.batteryLevelPercent,
                batteryTemperatureCelsius = telemetry.batteryTemperatureCelsius,
                isCharging = telemetry.isCharging,
                thermalStatus = telemetry.thermalStatus,
                socTemperatureCelsius = telemetry.socTemperatureCelsius,

                note = telemetry.note,
                allocationHotspots = telemetry.allocationHotspots
            )
        }
    }
}
