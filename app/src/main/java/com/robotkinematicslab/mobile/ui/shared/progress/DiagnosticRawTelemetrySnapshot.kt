package com.robotkinematicslab.mobile.ui.shared.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun DiagnosticRawTelemetrySnapshot(
    progressState: DiagnosticProgressState,
    performanceSamples: List<DiagnosticPerformanceSample>,
    config: DiagnosticLoadingCardConfig
) {
    val captured = latestTelemetrySample(performanceSamples)
    val telemetry = captured?.recordedTelemetrySnapshot() ?: progressState.telemetry
    val displayed = captured?.let { progressState.copy(completedRuns = it.completedRuns, totalRuns = it.totalRuns,
        runsPerSecond = it.calculationsPerSecond, estimatedSecondsRemaining = it.estimatedSecondsRemaining,
        elapsedSeconds = it.elapsedSeconds, phase = it.phase, currentSeed = it.currentSeed,
        currentLinkCount = it.currentLinkCount, currentJointMode = it.currentJointMode) } ?: progressState
    val gcRate = buildGcRateSnapshot(performanceSamples)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        JargonAwareText(
            text = if (progressState.isRunning) "Latest Raw Telemetry" else "Final Raw Telemetry",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF102A43)
        )
        JargonAwareText(
            captured?.let { "Recorded sample ${it.sampleIndex} at ${it.elapsedSeconds} s: the same capture used by charts and the final snapshot. Memory is MiB (1,048,576 bytes)." } ?: "Current sampler snapshot only; no recorded timeline sample exists. Memory is MiB (1,048,576 bytes).",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF52606D)
        )

        JargonAwareText(
            "Open only the section you need. Progress starts expanded; device and runtime sections stay compact until selected.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RawTelemetrySection(
            title = config.progressSectionTitle,
            initiallyExpanded = true
        ) {
            RawTelemetryRow("Progress", "${formatRawDouble(displayed.percent)}%")
            RawTelemetryRow(config.completedLabel, "${displayed.completedRuns} / ${displayed.totalRuns}")
            RawTelemetryRow(config.remainingLabel, displayed.remainingRuns.toString())
            RawTelemetryRow(config.rateLabel, formatRawDouble(displayed.runsPerSecond))
            RawTelemetryRow("Elapsed time", formatRawSeconds(displayed.elapsedSeconds))
            RawTelemetryRow("Estimated time left", formatRawSeconds(displayed.estimatedSecondsRemaining))
            RawTelemetryRow("Phase", displayed.phase.name)
            if (config.showBenchmarkCoordinates) {
                RawTelemetryRow("Current seed", displayed.currentSeed?.toString() ?: "N/A")
                RawTelemetryRow("Current link count", displayed.currentLinkCount?.toString() ?: "N/A")
                RawTelemetryRow("Current topology mode", displayed.currentJointMode?.name ?: "N/A")
            }
        }

        RawTelemetrySection("Java / Runtime Memory") {
            RawTelemetryRow("Runtime memory used", "${formatRawDouble(telemetry.usedRuntimeMemoryMb)} MiB")
            RawTelemetryRow("Runtime memory free", "${formatRawDouble(telemetry.freeRuntimeMemoryMb)} MiB")
            RawTelemetryRow("Runtime memory total", "${formatRawDouble(telemetry.totalRuntimeMemoryMb)} MiB")
            RawTelemetryRow("Runtime memory max", "${formatRawDouble(telemetry.maxRuntimeMemoryMb)} MiB")
        }

        RawTelemetrySection("Native Heap") {
            RawTelemetryRow("Native heap allocated", "${formatRawDouble(telemetry.nativeHeapAllocatedMb)} MiB")
            RawTelemetryRow("Native heap free", "${formatRawDouble(telemetry.nativeHeapFreeMb)} MiB")
            RawTelemetryRow("Native heap size", "${formatRawDouble(telemetry.nativeHeapSizeMb)} MiB")
        }

        RawTelemetrySection("System Memory") {
            RawTelemetryRow("System memory available", "${formatRawDouble(telemetry.availableSystemMemoryMb)} MiB")
            RawTelemetryRow("System memory total", "${formatRawDouble(telemetry.totalSystemMemoryMb)} MiB")
            RawTelemetryRow("Low memory", telemetry.lowMemory?.toString() ?: "N/A")
        }

        RawTelemetrySection("CPU / Scheduler") {
            RawTelemetryRow("CPU cores", telemetry.cpuCoreCount.toString())
            RawTelemetryRow("System load average", formatRawDouble(telemetry.systemLoadAverage))
            RawTelemetryRow("Process CPU time", formatRawMilliseconds(telemetry.processCpuTimeMs))
            RawTelemetryRow("Current thread CPU time", formatRawMilliseconds(telemetry.currentThreadCpuTimeMs))
            RawTelemetryRow("Readable CPU frequency cores", telemetry.cpuFrequencySnapshot.readableCoreCount.toString())
            RawTelemetryRow("CPU frequency min", "${formatRawDouble(telemetry.cpuFrequencySnapshot.minFrequencyMhz)} MHz")
            RawTelemetryRow("CPU frequency avg", "${formatRawDouble(telemetry.cpuFrequencySnapshot.averageFrequencyMhz)} MHz")
            RawTelemetryRow("CPU frequency max", "${formatRawDouble(telemetry.cpuFrequencySnapshot.maxFrequencyMhz)} MHz")
            if (telemetry.cpuFrequencySnapshot.frequenciesMhz.isNotEmpty()) {
                JargonAwareText(
                    "Per-core MHz: ${telemetry.cpuFrequencySnapshot.frequenciesMhz.joinToString { formatRawDouble(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF52606D)
                )
            }
        }

        RawTelemetrySection("Garbage Collection") {
            RawTelemetryRow("GC count", formatRawLong(telemetry.gcCount))
            RawTelemetryRow("GC time", formatRawMilliseconds(telemetry.gcTimeMs))
            RawTelemetryRow("Blocking GC count", formatRawLong(telemetry.blockingGcCount))
            RawTelemetryRow("Blocking GC time", formatRawMilliseconds(telemetry.blockingGcTimeMs))
        }

        RawTelemetrySection("GC Rate / Allocation Pressure") {
            RawTelemetryRow("GC count / sec", formatRawDouble(gcRate.gcCountPerSecond))
            RawTelemetryRow("GC time / sec", "${formatRawDouble(gcRate.gcTimeMsPerSecond)} ms/s")
            RawTelemetryRow("Blocking GC / sec", formatRawDouble(gcRate.blockingGcCountPerSecond))
            RawTelemetryRow("Blocking GC time / sec", "${formatRawDouble(gcRate.blockingGcTimeMsPerSecond)} ms/s")
            RawTelemetryRow("Runtime memory change / sec", "${formatRawSignedDouble(gcRate.runtimeMemoryUsedMbDeltaPerSecond)} MiB/s")
            RawTelemetryRow("Native heap change / sec", "${formatRawSignedDouble(gcRate.nativeHeapAllocatedMbDeltaPerSecond)} MiB/s")
            RawTelemetryRow("Samples used", gcRate.sampleDescription)
        }

        RawTelemetrySection("Allocation Hotspots") {
            if (telemetry.allocationHotspots.isEmpty()) {
                JargonAwareText("No allocation tracker samples yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                telemetry.allocationHotspots.take(12).forEach { hotspot ->
                    JargonAwareText(
                        hotspot.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF31558B)
                    )
                    RawTelemetryRow("Calls", hotspot.callCount.toString())
                    RawTelemetryRow("Total positive allocation", "${formatRawDouble(hotspot.totalPositiveDeltaMb)} MiB")
                    RawTelemetryRow("Average positive allocation", "${formatRawDouble(hotspot.averagePositiveDeltaKb)} KiB")
                    RawTelemetryRow("Worst positive call", "${formatRawDouble(hotspot.worstPositiveDeltaMb)} MiB")
                    RawTelemetryRow("Average elapsed", "${formatRawDouble(hotspot.averageElapsedMs)} ms")
                }
                if (telemetry.allocationHotspots.size > 12) {
                    JargonAwareText(
                        "${telemetry.allocationHotspots.size - 12} additional allocation sections omitted from this compact view.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        RawTelemetrySection("Battery / Thermal") {
            RawTelemetryRow("Battery level", "${formatRawDouble(telemetry.batteryLevelPercent)}%")
            RawTelemetryRow("Battery temperature", "${formatRawDouble(telemetry.batteryTemperatureCelsius)} °C")
            RawTelemetryRow("Charging", telemetry.isCharging?.toString() ?: "N/A")
            RawTelemetryRow("Android thermal status", telemetry.thermalStatus)
            RawTelemetryRow("Best-effort SoC temperature", "${formatRawDouble(telemetry.socTemperatureCelsius)} °C")
        }

        RawTelemetrySection("Raw Thermal Zones") {
            if (telemetry.thermalZones.isEmpty()) {
                JargonAwareText("No thermal zones readable on this device.", style = MaterialTheme.typography.bodySmall)
            } else {
                telemetry.thermalZones.take(16).forEach { zone ->
                    RawTelemetryRow("${zone.name}: ${zone.type}", "${formatRawDouble(zone.temperatureCelsius)} °C")
                }
            }
        }

        JargonAwareText(telemetry.note, style = MaterialTheme.typography.bodySmall, color = Color(0xFF52606D))
    }
}

@Composable
private fun RawTelemetrySection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val colors = MaterialTheme.colorScheme
    val sectionTag = rawTelemetrySectionTag(title)

    Surface(
        onClick = { expanded = !expanded },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(sectionTag),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.82f))
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "Tap to collapse" else "Tap to inspect",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Text(
                    if (expanded) "−" else "+",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    textAlign = TextAlign.Center
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            .testTag("$sectionTag-content"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

internal fun rawTelemetrySectionTag(title: String): String =
    "raw-telemetry-section-" +
        title
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

@Composable
private fun RawTelemetryRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        JargonAwareText(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 6,
            softWrap = true
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 6,
            softWrap = true
        )
    }
}

internal data class RawGcRateSnapshot(
    val gcCountPerSecond: Double,
    val gcTimeMsPerSecond: Double,
    val blockingGcCountPerSecond: Double,
    val blockingGcTimeMsPerSecond: Double,
    val runtimeMemoryUsedMbDeltaPerSecond: Double,
    val nativeHeapAllocatedMbDeltaPerSecond: Double,
    val sampleDescription: String
)

internal fun buildGcRateSnapshot(samples: List<DiagnosticPerformanceSample>): RawGcRateSnapshot {
    if (samples.size < 2) return RawGcRateSnapshot(
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "Need at least 2 samples"
    )
    val ordered = samples.filter { it.elapsedSeconds.isFinite() }.sortedWith(compareBy<DiagnosticPerformanceSample> { it.elapsedSeconds }.thenBy { it.sampleIndex }).distinctBy { it.sampleIndex }
    if (ordered.size < 2) return RawGcRateSnapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "Need two timed samples")
    val latest = ordered.last()
    val previous = ordered[ordered.lastIndex - 1]
    val elapsed = latest.elapsedSeconds - previous.elapsedSeconds
    if (!elapsed.isFinite() || elapsed <= 0.0) return RawGcRateSnapshot(
        Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "Invalid sample window"
    )
    return RawGcRateSnapshot(
        counterRate(latest.gcCount, previous.gcCount, elapsed),
        counterRate(latest.gcTimeMs, previous.gcTimeMs, elapsed),
        counterRate(latest.blockingGcCount, previous.blockingGcCount, elapsed),
        counterRate(latest.blockingGcTimeMs, previous.blockingGcTimeMs, elapsed),
        valueRate(latest.runtimeMemoryUsedMb, previous.runtimeMemoryUsedMb, elapsed),
        valueRate(latest.nativeHeapAllocatedMb, previous.nativeHeapAllocatedMb, elapsed),
        "${previous.sampleIndex} → ${latest.sampleIndex}, ${formatRawDouble(elapsed)}s"
    )
}

private fun counterRate(latest: Long, previous: Long, elapsed: Double): Double =
    if (latest >= 0L && previous >= 0L) (latest - previous).toDouble() / elapsed else Double.NaN

private fun valueRate(latest: Double, previous: Double, elapsed: Double): Double =
    if (latest.isFinite() && previous.isFinite()) (latest - previous) / elapsed else Double.NaN

private fun formatRawDouble(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f", value) else "N/A"

private fun formatRawSignedDouble(value: Double): String =
    if (value.isFinite()) (if (value > 0.0) "+" else "") + String.format(Locale.US, "%.2f", value) else "N/A"

private fun formatRawSeconds(value: Double): String {
    if (!value.isFinite()) return "N/A"
    val seconds = value.roundToInt().coerceAtLeast(0)
    return if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}

private fun formatRawMilliseconds(value: Long): String = if (value >= 0L) "$value ms" else "N/A"
private fun formatRawLong(value: Long): String = if (value >= 0L) value.toString() else "N/A"
