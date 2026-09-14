package com.robotkinematicslab.mobile.performance.compute

/** Reports repeated CPU work cycles without coupling the scientific engines to Android. */
interface ComputeWorkCycleReporter : AutoCloseable {
    fun startCycle(): Long
    fun finishCycle(startedAtNanos: Long)
    override fun close()
}

object NoOpComputeWorkCycleReporter : ComputeWorkCycleReporter {
    override fun startCycle(): Long = 0L
    override fun finishCycle(startedAtNanos: Long) = Unit
    override fun close() = Unit
}
