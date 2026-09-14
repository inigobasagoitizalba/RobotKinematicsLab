package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticProgressPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SustainedPerformanceAnalyzerTest {
    @Test
    fun comparesIndependentEarlyAndLateWindows() {
        val samples = (0 until 8).map { index ->
            sample(
                index = index,
                throughput = if (index < 2) 100.0 else if (index >= 6) 80.0 else 90.0,
                frequency = if (index < 2) 2_000.0 else if (index >= 6) 1_500.0 else 1_800.0,
                batteryTemperature = 30.0 + index,
                processCpuTimeMs = index * 500L,
                gcTimeMs = index * 10L
            )
        }

        val evidence = SustainedPerformanceAnalyzer.analyze(samples)

        assertTrue(evidence.hasSustainedWindow)
        assertEquals(7.0, evidence.observedDurationSeconds, 1e-12)
        assertEquals(80.0, evidence.throughputRetentionPercent, 1e-12)
        assertEquals(75.0, evidence.cpuFrequencyRetentionPercent, 1e-12)
        assertEquals(6.0, evidence.batteryTemperatureRiseCelsius, 1e-12)
        assertEquals(50.0, evidence.processCpuCoreEquivalentPercent, 1e-12)
        assertEquals(1.0, evidence.garbageCollectionDutyPercent, 1e-12)
    }

    @Test
    fun missingVendorSensorsRemainMissing() {
        val evidence = SustainedPerformanceAnalyzer.analyze(listOf(sample(0, 10.0, Double.NaN, Double.NaN, -1L, -1L)))

        assertFalse(evidence.hasSustainedWindow)
        assertTrue(evidence.cpuFrequencyRetentionPercent.isNaN())
        assertTrue(evidence.batteryTemperatureRiseCelsius.isNaN())
        assertTrue(evidence.processCpuCoreEquivalentPercent.isNaN())
    }

    @Test
    fun denseSubSecondSamplesDoNotMasqueradeAsSustainedEvidence() {
        val samples = (0 until 68).map { index ->
            sample(
                index = index,
                throughput = 100.0 - index / 10.0,
                frequency = 2_000.0,
                batteryTemperature = 30.0,
                processCpuTimeMs = index.toLong(),
                gcTimeMs = 0L,
                elapsedSeconds = index * 0.01
            )
        }

        val evidence = SustainedPerformanceAnalyzer.analyze(samples)

        assertFalse(evidence.hasSustainedWindow)
        assertEquals("Run too short", evidence.stabilityLabel)
        assertEquals(0.67, evidence.observedDurationSeconds, 1e-12)
    }

    @Test
    fun internalMissingAndResetCountersDoNotProduceAFalseWholeSessionPercentage() {
        val raw = (0 until 8).map { index -> sample(index, 100.0, 2000.0, 30.0, index * 500L, index * 10L) }
        val missing = raw.mapIndexed { i, item -> if(i == 3) item.copy(processCpuTimeMs = -1) else item }
        assertTrue(SustainedPerformanceAnalyzer.analyze(missing).processCpuCoreEquivalentPercent.isNaN())
        val reset = raw.mapIndexed { i, item -> if(i == 3) item.copy(gcTimeMs = 0) else item }
        assertTrue(SustainedPerformanceAnalyzer.analyze(reset).garbageCollectionDutyPercent.isNaN())
    }

    private fun sample(
        index: Int,
        throughput: Double,
        frequency: Double,
        batteryTemperature: Double,
        processCpuTimeMs: Long,
        gcTimeMs: Long,
        elapsedSeconds: Double = index.toDouble()
    ) = DiagnosticPerformanceSample(
        sampleIndex = index,
        timestampMs = index * 1_000L,
        elapsedSeconds = elapsedSeconds,
        completedRuns = index,
        totalRuns = 8,
        remainingRuns = 8 - index,
        progressPercent = index / 8.0 * 100.0,
        calculationsPerSecond = throughput,
        estimatedSecondsRemaining = (8 - index).toDouble(),
        phase = DiagnosticProgressPhase.SEQUENTIAL_RUNS,
        currentSeed = null,
        currentLinkCount = null,
        currentJointMode = null,
        runtimeMemoryUsedMb = 100.0 + index,
        runtimeMemoryFreeMb = 50.0,
        runtimeMemoryTotalMb = 200.0,
        runtimeMemoryMaxMb = 512.0,
        nativeHeapAllocatedMb = 10.0,
        nativeHeapFreeMb = 5.0,
        nativeHeapSizeMb = 20.0,
        availableSystemMemoryMb = 2_000.0,
        totalSystemMemoryMb = 8_000.0,
        lowMemory = false,
        cpuCoreCount = 8,
        systemLoadAverage = 1.0,
        processCpuTimeMs = processCpuTimeMs,
        currentThreadCpuTimeMs = processCpuTimeMs,
        readableCpuFrequencyCoreCount = if (frequency.isFinite()) 8 else 0,
        cpuFrequencyMinMhz = frequency,
        cpuFrequencyAverageMhz = frequency,
        cpuFrequencyMaxMhz = frequency,
        gcCount = index.toLong(),
        gcTimeMs = gcTimeMs,
        blockingGcCount = 0L,
        blockingGcTimeMs = 0L,
        batteryLevelPercent = 80.0,
        batteryTemperatureCelsius = batteryTemperature,
        isCharging = true,
        thermalStatus = "NONE",
        socTemperatureCelsius = Double.NaN,
        note = "test"
    )
}
