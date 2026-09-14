package com.robotkinematicslab.mobile.performance.compute

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi

/**
 * Android Dynamic Performance Framework bridge for repeated training minibatches.
 *
 * It never forces a frequency or pins a core. On supporting Android 12+ devices it identifies
 * each long-lived worker to the scheduler and reports real cycle durations; unsupported devices
 * transparently keep the same computation without hints.
 */
class AndroidPerformanceHintReporter(context: Context) : ComputeWorkCycleReporter {
    private val delegate: ComputeWorkCycleReporter =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31PerformanceHintReporter(context.applicationContext)
        } else {
            NoOpComputeWorkCycleReporter
        }

    override fun startCycle(): Long = delegate.startCycle()

    override fun finishCycle(startedAtNanos: Long) = delegate.finishCycle(startedAtNanos)

    override fun close() = delegate.close()
}

@RequiresApi(Build.VERSION_CODES.S)
private class Api31PerformanceHintReporter(context: Context) : ComputeWorkCycleReporter {
    private val manager: PerformanceHintManager? =
        context.getSystemService(PerformanceHintManager::class.java)
    private val sessions = mutableMapOf<Int, PerformanceHintManager.Session>()

    override fun startCycle(): Long {
        val activeManager = manager ?: return 0L
        val tid = Process.myTid()
        synchronized(sessions) {
            if (!sessions.containsKey(tid)) {
                runCatching {
                    activeManager.createHintSession(
                        intArrayOf(tid),
                        TARGET_WORK_DURATION_NANOS
                    )
                }.getOrNull()?.let { sessions[tid] = it }
            }
        }
        return SystemClock.elapsedRealtimeNanos()
    }

    override fun finishCycle(startedAtNanos: Long) {
        if (startedAtNanos <= 0L) return
        val elapsed = (SystemClock.elapsedRealtimeNanos() - startedAtNanos).coerceAtLeast(1L)
        val session = synchronized(sessions) { sessions[Process.myTid()] } ?: return
        runCatching { session.reportActualWorkDuration(elapsed) }
    }

    override fun close() {
        val toClose = synchronized(sessions) {
            sessions.values.toList().also { sessions.clear() }
        }
        toClose.forEach { session -> runCatching { session.close() } }
    }

    private companion object {
        // An intentionally demanding but credible deadline for one CPU minibatch shard.
        private const val TARGET_WORK_DURATION_NANOS = 25_000_000L
    }
}
