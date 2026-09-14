package com.robotkinematicslab.mobile.process

import android.content.Context
import com.robotkinematicslab.mobile.logging.AppLog
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResearchProcessReporter internal constructor(
    private val coordinator: ResearchProcessCoordinator,
    val processId: String,
    private val lifecycleToken: Long
) {
    fun report(
        progressFraction: Double? = null,
        stage: String,
        detail: String
    ) {
        coordinator.update(processId, lifecycleToken, ResearchProcessStatus.RUNNING, progressFraction, stage, detail)
    }

    fun preparing(stage: String, detail: String) {
        coordinator.update(processId, lifecycleToken, ResearchProcessStatus.PREPARING, null, stage, detail)
    }

    fun pausing(detail: String) {
        coordinator.update(processId, lifecycleToken, ResearchProcessStatus.PAUSING, null, "Finishing safe boundary", detail)
    }

    fun completed(
        detail: String,
        resultReference: ResearchResultReference? = null
    ) {
        coordinator.finish(
            processId,
            lifecycleToken,
            ResearchProcessStatus.SUCCEEDED,
            "Completed",
            detail,
            resultReference
        )
    }

    fun paused(detail: String) {
        coordinator.finish(processId, lifecycleToken, ResearchProcessStatus.PAUSED, "Paused safely", detail)
    }

    fun cancelled(detail: String) {
        coordinator.finish(processId, lifecycleToken, ResearchProcessStatus.CANCELLED, "Cancelled", detail)
    }

    fun failed(detail: String) {
        coordinator.fail(processId, lifecycleToken, detail)
    }
}

/**
 * Process-wide owner for user-initiated, long-running scientific work. The scope
 * intentionally survives Compose navigation, but not a deliberate full app close.
 */
class ResearchProcessCoordinator private constructor(
    context: Context,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : Closeable {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val cancellationActions = ConcurrentHashMap<String, () -> Unit>()
    private val lifecycleTokens = ConcurrentHashMap<String, Long>()
    private val nextLifecycleToken = AtomicLong(0L)
    private val _processes = MutableStateFlow<List<ResearchProcessSnapshot>>(emptyList())
    val processes: StateFlow<List<ResearchProcessSnapshot>> = _processes.asStateFlow()

    @Synchronized
    fun launch(
        id: String,
        title: String,
        kind: ResearchProcessKind,
        cancellationAction: (() -> Unit)? = null,
        block: suspend (ResearchProcessReporter) -> Unit
    ): Result<Job> =
        runCatching {
            require(id.isNotBlank())
            require(title.isNotBlank())
            check(jobs[id]?.isActive != true) { "$title is already running." }
            check(_processes.value.none { it.id == id && it.status.isActive }) { "$title is already running." }

            val now = clockMillis()
            val lifecycleToken = nextLifecycleToken.incrementAndGet()
            val reporter = ResearchProcessReporter(this, id, lifecycleToken)
            lateinit var job: Job
            job =
                scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        block(reporter)
                        if (snapshot(id)?.status?.isActive == true) {
                            reporter.completed("$title completed successfully.")
                        }
                    } catch (cancelled: CancellationException) {
                        reporter.cancelled("$title was cancelled before completion.")
                        throw cancelled
                    } catch (error: Exception) {
                        reporter.failed(error.message ?: "$title failed unexpectedly.")
                        AppLog.e(TAG) { "Tracked process failed | id=$id, error=${error.message}" }
                    } finally {
                        jobs.remove(id, job)
                        if (lifecycleTokens[id] == lifecycleToken) {
                            cancellationActions.remove(id)
                        }
                    }
                }
            job.invokeOnCompletion { cause ->
                // A lazy coroutine can be cancelled before its body executes, in which case the
                // catch/finally block above never runs. Close the visible lifecycle from the
                // completion callback as well so an immediate cancel cannot remain in PAUSING.
                if (cause is CancellationException) {
                    reporter.cancelled("$title was cancelled before completion.")
                    jobs.remove(id, job)
                }
            }
            // Publish the process only after its cancellable job is registered. Otherwise a
            // very fast UI cancellation can land between the visible PREPARING state and the
            // jobs assignment, leaving an apparently cancelled calculation running.
            jobs[id] = job
            lifecycleTokens[id] = lifecycleToken
            cancellationAction?.let { cancellationActions[id] = it }
            upsert(
                ResearchProcessSnapshot(
                    id = id,
                    jobId = jobId(id, lifecycleToken),
                    lifecycleSequence = lifecycleToken,
                    title = title,
                    kind = kind,
                    status = ResearchProcessStatus.PREPARING,
                    progressFraction = null,
                    stage = "Preparing",
                    detail = "The requested scientific process is being prepared.",
                    startedAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    canCancel = true
                )
            )
            ResearchProcessForegroundService.start(applicationContext)
            job.start()
            job
        }

    @Synchronized
    fun beginExternal(
        id: String,
        title: String,
        kind: ResearchProcessKind,
        stage: String,
        detail: String,
        cancellationAction: (() -> Unit)? = null
    ): ResearchProcessReporter {
        val existing = snapshot(id)
        val lifecycleToken: Long
        if (existing?.status?.isActive != true) {
            val now = clockMillis()
            lifecycleToken = nextLifecycleToken.incrementAndGet()
            lifecycleTokens[id] = lifecycleToken
            upsert(
                ResearchProcessSnapshot(
                    id = id,
                    jobId = jobId(id, lifecycleToken),
                    lifecycleSequence = lifecycleToken,
                    title = title,
                    kind = kind,
                    status = ResearchProcessStatus.PREPARING,
                    progressFraction = null,
                    stage = stage,
                    detail = detail,
                    startedAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    canCancel = cancellationAction != null
                )
            )
            cancellationAction?.let { cancellationActions[id] = it }
            ResearchProcessForegroundService.start(applicationContext)
        } else {
            lifecycleToken = lifecycleTokens[id] ?: nextLifecycleToken.incrementAndGet().also {
                lifecycleTokens[id] = it
            }
            if (cancellationAction != null) {
                cancellationActions[id] = cancellationAction
                if (!existing.canCancel) {
                    upsert(
                        existing.copy(
                            canCancel = true,
                            updatedAtEpochMillis = clockMillis()
                        )
                    )
                }
            }
        }
        return ResearchProcessReporter(this, id, lifecycleToken)
    }

    fun snapshot(id: String): ResearchProcessSnapshot? =
        _processes.value
            .filter { it.id == id }
            .maxWithOrNull(
                compareBy<ResearchProcessSnapshot> { it.status.isActive }
                    .thenBy { it.updatedAtEpochMillis }
                    .thenBy { it.startedAtEpochMillis }
                    .thenBy { it.lifecycleSequence }
            )

    private fun snapshot(id: String, lifecycleToken: Long): ResearchProcessSnapshot? =
        _processes.value.firstOrNull { it.jobId == jobId(id, lifecycleToken) }

    internal fun fail(id: String, lifecycleToken: Long, detail: String) {
        val kind = snapshot(id, lifecycleToken)?.kind
        finish(
            id = id,
            lifecycleToken = lifecycleToken,
            status = ResearchProcessStatus.FAILED,
            stage = researchProcessFailureStage(kind),
            detail = detail
        )
    }

    fun isActive(id: String): Boolean = snapshot(id)?.status?.isActive == true

    fun requestCancel(id: String): Boolean {
        val cancellation =
            synchronized(this) {
                val current = snapshot(id) ?: return false
                if (!current.status.isActive || !current.canCancel) return false
                val action = cancellationActions[id]
                val job = jobs[id]
                val lifecycleToken = lifecycleTokens[id] ?: return false
                if (action == null && job == null) return false
                update(
                    id,
                    lifecycleToken,
                    ResearchProcessStatus.PAUSING,
                    current.safeProgressFraction,
                    "Finishing safe boundary",
                    "Cancellation was requested by the user."
                )
                Triple(action, job, lifecycleToken)
            }
        // User callbacks and coroutine cancellation are deliberately outside the coordinator
        // lock: an engine is allowed to checkpoint synchronously without blocking state readers.
        val action = cancellation.first
        val job = cancellation.second
        val lifecycleToken = cancellation.third
        if (action == null) {
            job?.cancel(CancellationException("Cancellation was requested by the user."))
            return true
        }

        val failure = runCatching(action).exceptionOrNull()
        if (failure == null) return true

        AppLog.e(TAG) {
            "Safe-stop callback failed | id=$id, error=${failure.javaClass.simpleName}"
        }
        if (job != null) {
            // A coordinator-owned coroutine can still be contained if its cooperative engine
            // callback fails. Its completion callback closes the visible process lifecycle.
            job.cancel(
                CancellationException("The safe-stop callback failed.").apply {
                    initCause(failure)
                }
            )
            return true
        }

        // An externally-owned engine cannot be marked as stopped when its only
        // cancellation bridge failed. Keep it visible and tell the user that work may continue.
        update(
            id = id,
            lifecycleToken = lifecycleToken,
            status = ResearchProcessStatus.RUNNING,
            progressFraction = snapshot(id)?.safeProgressFraction,
            stage = "Still running",
            detail = "The safe-stop request failed. The process remains active; retry or close the app."
        )
        return false
    }

    /**
     * Used only when Android reports that the user deliberately removed the app
     * task. Every engine receives its normal cooperative cancellation signal
     * before coroutine jobs are cancelled, so checkpoint-aware work can stop at
     * a safe boundary instead of continuing invisibly.
     */
    fun cancelAll(reason: String) {
        val activeProcesses = _processes.value.filter { it.status.isActive }
        activeProcesses.forEach { process ->
            val id = process.id
            val lifecycleToken = lifecycleTokens[id] ?: return@forEach
            runCatching { cancellationActions[id]?.invoke() }
                .onFailure { error ->
                    AppLog.e(TAG) {
                        "Safe-stop callback failed during app close | id=$id, error=${error.javaClass.simpleName}"
                    }
                }
            jobs[id]?.cancel(CancellationException(reason))
            finish(id, lifecycleToken, ResearchProcessStatus.CANCELLED, "App closed", reason)
        }
    }

    @Synchronized
    fun clearFinished() {
        _processes.value = _processes.value.filter { it.status.isActive }
    }

    @Synchronized
    internal fun update(
        id: String,
        lifecycleToken: Long,
        status: ResearchProcessStatus,
        progressFraction: Double?,
        stage: String,
        detail: String
    ) {
        if (lifecycleTokens[id] != lifecycleToken) return
        val current = snapshot(id, lifecycleToken) ?: return
        if (current.status.isTerminal) return
        upsert(
            current.copy(
                status = status,
                progressFraction = progressFraction?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0),
                stage = stage.ifBlank { current.stage },
                detail = detail.ifBlank { current.detail },
                updatedAtEpochMillis = clockMillis()
            )
        )
    }

    internal fun finish(
        id: String,
        lifecycleToken: Long,
        status: ResearchProcessStatus,
        stage: String,
        detail: String,
        resultReference: ResearchResultReference? = null
    ) {
        val finished =
            synchronized(this) {
                require(status.isTerminal)
                if (lifecycleTokens[id] != lifecycleToken) return
                val current = snapshot(id, lifecycleToken) ?: return
                if (current.status.isTerminal) return
                current.copy(
                    status = status,
                    progressFraction = if (status == ResearchProcessStatus.SUCCEEDED) 1.0 else current.safeProgressFraction,
                    stage = stage,
                    detail = detail,
                    updatedAtEpochMillis = clockMillis(),
                    canCancel = false,
                    resultReference = resultReference
                ).also { terminal ->
                    upsert(terminal)
                    cancellationActions.remove(id)
                    lifecycleTokens.remove(id, lifecycleToken)
                }
            }
        // NotificationManager crosses a system-process boundary. Never hold the process-state
        // lock while publishing, because a slow binder call would stall progress and cancellation.
        ResearchProcessNotificationPublisher(applicationContext).postResult(finished)
    }

    @Synchronized
    private fun upsert(snapshot: ResearchProcessSnapshot) {
        _processes.value =
            (_processes.value.filterNot { it.jobId == snapshot.jobId } + snapshot)
                .sortedByDescending(ResearchProcessSnapshot::updatedAtEpochMillis)
                .take(MAXIMUM_RETAINED_PROCESSES)
    }

    override fun close() {
        cancelAll("The process coordinator was closed.")
        jobs.clear()
        cancellationActions.clear()
        lifecycleTokens.clear()
    }

    companion object {
        private const val TAG = "ResearchProcessCoordinator"
        private const val MAXIMUM_RETAINED_PROCESSES = 24

        @Volatile
        private var instance: ResearchProcessCoordinator? = null

        private fun jobId(processId: String, lifecycleToken: Long): String = "$processId:$lifecycleToken"

        fun get(context: Context): ResearchProcessCoordinator =
            instance ?: synchronized(this) {
                instance ?: ResearchProcessCoordinator(context).also { instance = it }
            }
    }
}
