package com.robotkinematicslab.mobile.diagnostics.benchmark.analysis

import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticPerformanceSample
import kotlin.math.max

data class SustainedPerformanceEvidence(
    val sampleCount: Int,
    val observedDurationSeconds: Double,
    val earlyThroughput: Double,
    val lateThroughput: Double,
    val throughputRetentionPercent: Double,
    val earlyCpuFrequencyMhz: Double,
    val lateCpuFrequencyMhz: Double,
    val cpuFrequencyRetentionPercent: Double,
    val batteryTemperatureRiseCelsius: Double,
    val socTemperatureRiseCelsius: Double,
    val processCpuCoreEquivalentPercent: Double,
    val garbageCollectionDutyPercent: Double,
    val peakRuntimeMemoryMb: Double,
    val observedThermalStates: Set<String>
) {
    val hasSustainedWindow: Boolean
        get() =
            sampleCount >= MINIMUM_SUSTAINED_SAMPLES &&
                observedDurationSeconds >= MINIMUM_SUSTAINED_DURATION_SECONDS &&
                throughputRetentionPercent.isFinite()

    val stabilityLabel: String
        get() = when {
            sampleCount < MINIMUM_SUSTAINED_SAMPLES -> "Too few samples"
            observedDurationSeconds < MINIMUM_SUSTAINED_DURATION_SECONDS -> "Run too short"
            !throughputRetentionPercent.isFinite() -> "Throughput unavailable"
            throughputRetentionPercent >= 90.0 -> "Stable throughput"
            throughputRetentionPercent >= 75.0 -> "Moderate throughput loss"
            else -> "Strong throughput loss"
        }

    private companion object {
        const val MINIMUM_SUSTAINED_SAMPLES = 4
        const val MINIMUM_SUSTAINED_DURATION_SECONDS = 5.0
    }
}

/**
 * Derives comparable, within-session performance evidence from measurements Android actually exposed.
 * It deliberately does not infer battery energy or power from CPU, temperature, memory, or elapsed time.
 */
object SustainedPerformanceAnalyzer {
    fun analyze(samples: List<DiagnosticPerformanceSample>): SustainedPerformanceEvidence {
        val clean = samples
            .filter { it.elapsedSeconds.isFinite() }
            .sortedWith(compareBy<DiagnosticPerformanceSample> { it.elapsedSeconds }.thenBy { it.sampleIndex })
            .distinctBy { it.sampleIndex }
        val windowSize = max(1, clean.size / 4)
        val early = clean.take(windowSize)
        val late = clean.takeLast(windowSize)

        val earlyThroughput = early.finiteMedian { it.calculationsPerSecond.takeIf { value -> value >= 0.0 } }
        val lateThroughput = late.finiteMedian { it.calculationsPerSecond.takeIf { value -> value >= 0.0 } }
        val earlyFrequency = early.finiteMedian { it.cpuFrequencyAverageMhz.takeIf { value -> value > 0.0 } }
        val lateFrequency = late.finiteMedian { it.cpuFrequencyAverageMhz.takeIf { value -> value > 0.0 } }
        val elapsedMillis = clean.elapsedMillis()
        val processCpuDelta = clean.counterDelta { it.processCpuTimeMs }
        val gcTimeDelta = clean.counterDelta { it.gcTimeMs }

        return SustainedPerformanceEvidence(
            sampleCount = clean.size,
            observedDurationSeconds = clean.observedDurationSeconds(),
            earlyThroughput = earlyThroughput,
            lateThroughput = lateThroughput,
            throughputRetentionPercent = retentionPercent(lateThroughput, earlyThroughput),
            earlyCpuFrequencyMhz = earlyFrequency,
            lateCpuFrequencyMhz = lateFrequency,
            cpuFrequencyRetentionPercent = retentionPercent(lateFrequency, earlyFrequency),
            batteryTemperatureRiseCelsius = temperatureRise(early, late) { it.batteryTemperatureCelsius },
            socTemperatureRiseCelsius = temperatureRise(early, late) { it.socTemperatureCelsius },
            processCpuCoreEquivalentPercent = percentageOfElapsed(processCpuDelta, elapsedMillis),
            garbageCollectionDutyPercent = percentageOfElapsed(gcTimeDelta, elapsedMillis),
            peakRuntimeMemoryMb = clean.map { it.runtimeMemoryUsedMb }.filter(Double::isFinite).maxOrNull() ?: Double.NaN,
            observedThermalStates = clean.map(DiagnosticPerformanceSample::thermalStatus)
                .filter { it.isNotBlank() && it != "N/A" }
                .toSet()
        )
    }

    private fun retentionPercent(late: Double, early: Double): Double =
        if (late.isFinite() && early.isFinite() && early > 0.0) late / early * 100.0 else Double.NaN

    private fun temperatureRise(
        early: List<DiagnosticPerformanceSample>,
        late: List<DiagnosticPerformanceSample>,
        value: (DiagnosticPerformanceSample) -> Double
    ): Double {
        val start = early.finiteMedian { value(it).takeIf { measured -> measured > -100.0 } }
        val end = late.finiteMedian { value(it).takeIf { measured -> measured > -100.0 } }
        return if (start.isFinite() && end.isFinite()) end - start else Double.NaN
    }

    private fun List<DiagnosticPerformanceSample>.elapsedMillis(): Double =
        if (size >= 2) ((last().elapsedSeconds - first().elapsedSeconds) * 1_000.0).takeIf { it > 0.0 }
            ?: Double.NaN else Double.NaN

    private fun List<DiagnosticPerformanceSample>.observedDurationSeconds(): Double =
        if (size >= 2) {
            (last().elapsedSeconds - first().elapsedSeconds).coerceAtLeast(0.0)
        } else {
            0.0
        }

    private fun List<DiagnosticPerformanceSample>.counterDelta(
        value: (DiagnosticPerformanceSample) -> Long
    ): Double {
        if (size < 2 || zipWithNext().any { (a, b) -> value(a) < 0L || value(b) < value(a) || b.elapsedSeconds <= a.elapsedSeconds }) return Double.NaN
        val start = value(first())
        val end = value(last())
        return if (start >= 0L && end >= start) (end - start).toDouble() else Double.NaN
    }

    private fun percentageOfElapsed(counterDeltaMs: Double, elapsedMs: Double): Double =
        if (counterDeltaMs.isFinite() && elapsedMs.isFinite() && elapsedMs > 0.0) {
            counterDeltaMs / elapsedMs * 100.0
        } else {
            Double.NaN
        }

    private fun List<DiagnosticPerformanceSample>.finiteMedian(
        selector: (DiagnosticPerformanceSample) -> Double?
    ): Double {
        val values = mapNotNull(selector).filter(Double::isFinite).sorted()
        if (values.isEmpty()) return Double.NaN
        val middle = values.size / 2
        return if (values.size % 2 == 0) (values[middle - 1] + values[middle]) / 2.0 else values[middle]
    }
}
