package com.robotkinematicslab.mobile.ui.shared.progress

import java.util.concurrent.atomic.AtomicLong

/**
 * Shared upper bound for live telemetry capture.
 *
 * Ten samples per second are frequent enough to retain short memory, CPU and GC changes while
 * keeping the Android system sampler out of the application hot loop.
 */
internal const val HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS = 100L

internal const val HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS =
    HIGH_RESOLUTION_TELEMETRY_INTERVAL_MILLIS * 1_000_000L

/** Expensive vendor/system sensors are intentionally refreshed less often than runtime counters. */
internal const val SLOW_SYSTEM_TELEMETRY_INTERVAL_MILLIS = 1_000L

internal const val SLOW_SYSTEM_TELEMETRY_INTERVAL_NANOS =
    SLOW_SYSTEM_TELEMETRY_INTERVAL_MILLIS * 1_000_000L

/**
 * Thread-safe capture gate for progress callbacks that may arrive faster than the telemetry
 * cadence. The first point is always accepted; callers force phase boundaries and terminal points.
 */
internal class HighResolutionTelemetryCaptureGate {
    private val lastCaptureNanos = AtomicLong(NO_CAPTURE)

    fun reset() {
        lastCaptureNanos.set(NO_CAPTURE)
    }

    fun shouldCapture(
        capturedAtNanos: Long = System.nanoTime(),
        force: Boolean = false
    ): Boolean {
        while (true) {
            val previousCaptureNanos = lastCaptureNanos.get()
            val intervalPassed =
                previousCaptureNanos == NO_CAPTURE ||
                    capturedAtNanos - previousCaptureNanos >=
                        HIGH_RESOLUTION_TELEMETRY_INTERVAL_NANOS
            if (!force && !intervalPassed) {
                return false
            }
            if (lastCaptureNanos.compareAndSet(previousCaptureNanos, capturedAtNanos)) {
                return true
            }
        }
    }

    private companion object {
        const val NO_CAPTURE = Long.MIN_VALUE
    }
}
