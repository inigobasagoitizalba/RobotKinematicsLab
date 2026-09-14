package com.robotkinematicslab.mobile.ui.shared.progress

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySamplingPolicyTest {

    @Test
    fun captureGateUsesSharedHundredMillisecondCadenceAndKeepsBoundaries() {
        val gate = HighResolutionTelemetryCaptureGate()
        val start = 10_000_000_000L

        assertTrue(gate.shouldCapture(capturedAtNanos = start))
        assertFalse(
            gate.shouldCapture(
                capturedAtNanos = start + HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS - 1L
            )
        )
        assertTrue(
            gate.shouldCapture(
                capturedAtNanos = start + HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
            )
        )
        assertTrue(
            gate.shouldCapture(
                capturedAtNanos = start + HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS + 1L,
                force = true
            )
        )
    }

    @Test
    fun resetMakesTheNextMeasurementARequiredFirstSample() {
        val gate = HighResolutionTelemetryCaptureGate()

        assertTrue(gate.shouldCapture(capturedAtNanos = 1_000L))
        assertFalse(gate.shouldCapture(capturedAtNanos = 1_001L))
        gate.reset()
        assertTrue(gate.shouldCapture(capturedAtNanos = 1_001L))
    }

    @Test
    fun slowSensorCadenceIsExplicitlyDerivedFromOneSharedSecond() {
        assertTrue(HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS == 100L)
        assertTrue(
            HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS ==
                HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS * 1_000_000L
        )
        assertTrue(SLOW_SYSTEM_TELEMETRY_INTERVAL_MILLIS == 1_000L)
        assertTrue(
            SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS ==
                SLOW_SYSTEM_TELEMETRY_INTERVAL_MILLIS * 1_000_000L
        )
    }

    @Test
    fun everyTelemetryProducerUsesTheSharedHighResolutionCadence() {
        val uiRoot = locateProductionSource("ui")
        val producers =
            uiRoot.walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .map { file -> file to file.readText() }
                .filter { (_, source) -> source.contains("DiagnosticPerformanceSample.fromProgressState") }
                // This card appends a progress sample already captured by its owner; it does not acquire telemetry.
                .filterNot { (file, _) -> file.name == "DiagnosticLoadingProgressCard.kt" }
                .toList()

        assertTrue("The telemetry-producer inventory must not become empty", producers.isNotEmpty())
        producers.forEach { (file, source) ->
            assertTrue(
                "${file.relativeTo(uiRoot).path} bypasses the shared high-resolution sampling policy",
                source.contains("HighResolutionTelemetryCaptureGate") ||
                    source.contains("HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS") ||
                    source.contains("HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS")
            )
        }
    }

    @Test
    fun systemTelemetrySamplerUsesTheSharedSlowSensorCadence() {
        val source =
            locateProductionSource("diagnostics/benchmark/runtime/DiagnosticSystemTelemetry.kt")
                .readText()
                .replace(Regex("\\s+"), " ")

        assertTrue(source.contains("import com.robotkinematicslab.mobile.ui.shared.progress.SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS"))
        assertTrue(source.contains("refreshedAtNanos + SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS"))
    }

    private fun locateProductionSource(relativePath: String): File {
        val suffix = "app/src/main/java/com/robotkinematicslab/mobile/$relativePath"
        val start = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { root -> File(root, suffix) }
            .firstOrNull(File::exists)
            ?: error("Cannot locate production source $suffix from ${start.absolutePath}")
    }
}
