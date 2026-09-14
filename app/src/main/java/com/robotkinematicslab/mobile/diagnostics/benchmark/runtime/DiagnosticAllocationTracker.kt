package com.robotkinematicslab.mobile.diagnostics.benchmark.runtime

import kotlin.math.max

data class DiagnosticAllocationHotspot(
    val label: String,
    val callCount: Long,
    val totalPositiveDeltaMb: Double,
    val averagePositiveDeltaKb: Double,
    val worstPositiveDeltaMb: Double,
    val totalElapsedMs: Long,
    val averageElapsedMs: Double
)

object DiagnosticAllocationTracker {

    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val BYTES_PER_KB = 1024.0

    private data class MutableAllocationStat(
        var callCount: Long = 0L,
        var totalPositiveDeltaBytes: Long = 0L,
        var worstPositiveDeltaBytes: Long = 0L,
        var totalElapsedMs: Long = 0L
    )

    private val lock =
        Any()

    private val stats =
        linkedMapOf<String, MutableAllocationStat>()

    fun reset() {
        synchronized(lock) {
            stats.clear()
        }
    }

    inline fun <T> measure(
        label: String,
        block: () -> T
    ): T {
        val runtime =
            Runtime.getRuntime()

        val beforeBytes =
            runtime.totalMemory() - runtime.freeMemory()

        val startMs =
            System.currentTimeMillis()

        try {
            return block()
        } finally {
            val endMs =
                System.currentTimeMillis()

            val afterBytes =
                runtime.totalMemory() - runtime.freeMemory()

            val rawDeltaBytes =
                afterBytes - beforeBytes

            val positiveDeltaBytes =
                if (rawDeltaBytes > 0L) {
                    rawDeltaBytes
                } else {
                    0L
                }

            val elapsedMs =
                max(0L, endMs - startMs)

            record(
                label = label,
                positiveDeltaBytes = positiveDeltaBytes,
                elapsedMs = elapsedMs
            )
        }
    }

    fun record(
        label: String,
        positiveDeltaBytes: Long,
        elapsedMs: Long
    ) {
        synchronized(lock) {
            val stat =
                stats.getOrPut(label) {
                    MutableAllocationStat()
                }

            stat.callCount += 1L
            stat.totalPositiveDeltaBytes += positiveDeltaBytes
            stat.worstPositiveDeltaBytes =
                max(
                    stat.worstPositiveDeltaBytes,
                    positiveDeltaBytes
                )
            stat.totalElapsedMs += elapsedMs
        }
    }

    fun snapshot(): List<DiagnosticAllocationHotspot> {
        synchronized(lock) {
            return stats.map { entry ->
                val label =
                    entry.key

                val stat =
                    entry.value

                val averagePositiveDeltaKb =
                    if (stat.callCount > 0L) {
                        stat.totalPositiveDeltaBytes.toDouble() /
                                stat.callCount.toDouble() /
                                BYTES_PER_KB
                    } else {
                        0.0
                    }

                val averageElapsedMs =
                    if (stat.callCount > 0L) {
                        stat.totalElapsedMs.toDouble() /
                                stat.callCount.toDouble()
                    } else {
                        0.0
                    }

                DiagnosticAllocationHotspot(
                    label = label,
                    callCount = stat.callCount,
                    totalPositiveDeltaMb =
                        stat.totalPositiveDeltaBytes.toDouble() / BYTES_PER_MB,
                    averagePositiveDeltaKb = averagePositiveDeltaKb,
                    worstPositiveDeltaMb =
                        stat.worstPositiveDeltaBytes.toDouble() / BYTES_PER_MB,
                    totalElapsedMs = stat.totalElapsedMs,
                    averageElapsedMs = averageElapsedMs
                )
            }.sortedByDescending {
                it.totalPositiveDeltaMb
            }
        }
    }
}
