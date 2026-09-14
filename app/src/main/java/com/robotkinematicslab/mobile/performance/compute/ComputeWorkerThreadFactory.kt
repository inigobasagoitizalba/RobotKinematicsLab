package com.robotkinematicslab.mobile.performance.compute

import android.os.Process
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates CPU workers that remain in Android's foreground scheduling group while the user-started
 * research process is active. The compute policy already reserves a logical core for Android/UI;
 * lowering these workers to background priority as well would unnecessarily starve the selected
 * cores and make a nominal multi-core run behave like a low-priority maintenance task.
 */
class ComputeWorkerThreadFactory(
    private val namePrefix: String,
    private val daemon: Boolean = true
) : ThreadFactory {
    private val nextNumber = AtomicInteger(0)

    override fun newThread(task: Runnable): Thread =
        Thread(
            {
                setForegroundComputePriority()
                task.run()
            },
            "$namePrefix-${nextNumber.incrementAndGet()}"
        ).apply {
            isDaemon = daemon
            priority = Thread.NORM_PRIORITY
        }

    private fun setForegroundComputePriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        } catch (_: RuntimeException) {
            // Local JVM tests use Android stubs. The real device path still applies this priority.
        } catch (_: LinkageError) {
            // Keep the pure-JVM scientific engines usable outside Android.
        }
    }
}
