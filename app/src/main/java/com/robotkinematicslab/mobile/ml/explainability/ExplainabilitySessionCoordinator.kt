package com.robotkinematicslab.mobile.ml.explainability

import android.content.Context
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Process/project owner. No coroutine ever captures a Compose state or composition scope. */
internal class ExplainabilitySessionCoordinator internal constructor(
    context: Context,
    projectRoot: File,
    private val explain: (TrainingRunSummary, String, Int, Int, () -> Boolean, (ExplainabilityProgress) -> Unit) -> ExplainabilityResult =
        LocalModelExplainabilityEngine(TrainingStorageRepository(context.applicationContext))::explain
) {
    private val repository = ExplainabilitySessionRepository(File(projectRoot, "training/explainability"))
    private val processes = ResearchProcessCoordinator.get(context.applicationContext)
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(ExplainabilitySessionState())
    val state: StateFlow<ExplainabilitySessionState> = mutableState.asStateFlow()
    val processId = "explainability:" + sha256(projectRoot.canonicalPath.toByteArray()).take(24)

    init {
        recoveryScope.launch {
            val recovered = repository.restore()
            mutableState.value = recovered
        }
    }

    @Synchronized
    fun start(run: TrainingRunSummary, modelPath: String, samples: Int, steps: Int, expectedModelSha256:String? = null): Result<Unit> = runCatching {
        check(!state.value.loading) { "Saved evidence is still being loaded." }
        check(!state.value.running && !processes.isActive(processId)) { "An explanation is already running for this project." }
        require(modelPath in run.modelPaths) { "Select a model belonging to this training run." }
        require(samples in 16..5000 && steps in 8..256) { "Explanation controls are outside their safe ranges." }
        val cancellation = AtomicBoolean(false)
        val request = ExplainabilityRequest(UUID.randomUUID().toString(), run.runId, modelPath, "", samples, steps)
        mutableState.value = state.value.copy(status = ExplainabilitySessionStatus.RUNNING, request = request,
            progress = null, startedAtEpochMillis = System.currentTimeMillis(), message = "Preparing explainability evidence…")
        val launched = processes.launch(processId, "Explainable AI report", ResearchProcessKind.ANALYSIS,
            cancellationAction = { cancellation.set(true) }) { process ->
            try {
                val ownedRequest = withContext(Dispatchers.IO) {
                    repository.saveState(state.value)
                    request.copy(modelSha256 = modelDigest(File(modelPath), cancellation::get)).also {
                        require(expectedModelSha256 == null || it.modelSha256 == expectedModelSha256) { "The model changed after validation. Select it again before explaining." }
                        mutableState.value = state.value.copy(request = it)
                        repository.saveState(state.value)
                    }
                }
                val result = withContext(Dispatchers.Default) {
                    explain(run, modelPath, samples, steps, cancellation::get) { progress ->
                        mutableState.value = state.value.copy(progress = progress, message = progress.message)
                        process.report(if (progress.totalWork > 0) progress.completedWork.toDouble() / progress.totalWork else null,
                            progress.phase.name.lowercase().replace('_', ' '), progress.message)
                    }
                }
                if (cancellation.get()) throw ExplainabilityCancelledException()
                withContext(Dispatchers.IO) {
                    require(modelDigest(File(modelPath), cancellation::get) == ownedRequest.modelSha256) { "The model changed during explanation. No report was committed; select the original model and retry." }
                }
                val report = StoredExplainabilityReport(ownedRequest, result, System.currentTimeMillis())
                val completed = state.value.copy(status = ExplainabilitySessionStatus.COMPLETED, report = report,
                    message = "Explainability report completed and saved from ${result.explainedSampleCount} held-out predictions.")
                // Completion is truthful only after both report and session pointer are committed.
                withContext(Dispatchers.IO) { repository.saveReport(report); repository.saveState(completed) }
                mutableState.value = completed
                process.completed(completed.message)
            } catch (cancelled: CancellationException) {
                finish(ExplainabilitySessionStatus.CANCELLED, "Explanation generation cancelled safely.")
                process.cancelled(state.value.message)
                throw cancelled
            } catch (_: ExplainabilityCancelledException) {
                finish(ExplainabilitySessionStatus.CANCELLED, "Explanation generation cancelled safely.")
                process.cancelled(state.value.message)
            } catch (error: Exception) {
                finish(ExplainabilitySessionStatus.FAILED, "Explainability failed: ${error.message ?: error::class.simpleName}")
                process.failed(state.value.message)
            }
        }
        launched.onFailure { error ->
            mutableState.value = state.value.copy(status = ExplainabilitySessionStatus.FAILED,
                message = error.message ?: "Explainability could not start.")
        }.getOrThrow().invokeOnCompletion {
            // Covers cancellation before the lazy process body starts.
            if (state.value.request?.executionId == request.executionId && state.value.running) {
                recoveryScope.launch { finish(ExplainabilitySessionStatus.CANCELLED, "Explanation stopped before completion.") }
            }
        }
    }

    fun cancel(): Boolean = processes.requestCancel(processId)

    private suspend fun finish(status: ExplainabilitySessionStatus, message: String) {
        val terminal = state.value.copy(status = status, progress = null, message = message)
        withContext(NonCancellable + Dispatchers.IO) {
            val persistenceError = runCatching { repository.saveState(terminal) }.exceptionOrNull()
            mutableState.value = terminal.copy(message = message +
                (persistenceError?.let { " Session status could not be saved: ${it.message}" } ?: ""))
        }
    }

    private fun modelDigest(file: File, cancelled: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                if (cancelled()) throw ExplainabilityCancelledException()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ExplainabilitySessionCoordinator>()
        fun get(context: Context): ExplainabilitySessionCoordinator {
            val root = AppStoragePaths(context.applicationContext).rootDirectory.canonicalFile
            return instances.computeIfAbsent(root.path) { ExplainabilitySessionCoordinator(context.applicationContext, root) }
        }
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
