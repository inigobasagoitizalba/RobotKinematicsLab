package com.robotkinematicslab.mobile.ml.explainability

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.security.MessageDigest
import java.security.DigestOutputStream

internal enum class ExplainabilitySessionStatus { IDLE, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

internal data class ExplainabilityRequest(
    val executionId: String,
    val runId: String,
    val modelPath: String,
    val modelSha256: String,
    val samples: Int,
    val steps: Int
)

internal data class StoredExplainabilityReport(
    val request: ExplainabilityRequest,
    val result: ExplainabilityResult,
    val completedAtEpochMillis: Long
)

internal data class ExplainabilitySessionState(
    val loading: Boolean = true,
    val status: ExplainabilitySessionStatus = ExplainabilitySessionStatus.IDLE,
    val request: ExplainabilityRequest? = null,
    val report: StoredExplainabilityReport? = null,
    val progress: ExplainabilityProgress? = null,
    val startedAtEpochMillis: Long = 0L,
    val message: String = "Loading saved explainability evidence…"
) {
    val running: Boolean get() = status == ExplainabilitySessionStatus.RUNNING
}

/** One captured project root; immutable completed executions are never overwritten or removed. */
internal class ExplainabilitySessionRepository(private val directory: File) {
    private val journal get() = File(directory, "session.properties")

    @Synchronized
    fun restore(): ExplainabilitySessionState {
        if (!journal.exists()) return ExplainabilitySessionState(loading = false,
            message = "Choose a stored model to explain its untouched test predictions.")
        return try {
            val properties = Properties().apply { journal.inputStream().use { load(it) } }
            val request = properties.getProperty("executionId")?.let {
                ExplainabilityRequest(it, properties.getProperty("runId"), properties.getProperty("modelPath"),
                    properties.getProperty("modelSha256"), properties.getProperty("samples").toInt(), properties.getProperty("steps").toInt())
            }
            val persistedStatus = ExplainabilitySessionStatus.valueOf(properties.getProperty("status"))
            // A crash after committing the report but before replacing the journal is still complete.
            val ownReport = request?.let { reportFile(it.executionId).takeIf(File::isFile)?.let(::readReport) }
            require(ownReport == null || ownReport.request == request) { "Report identity does not match its session" }
            require(persistedStatus != ExplainabilitySessionStatus.COMPLETED || ownReport != null) { "Completed report file is missing" }
            val priorReport = ownReport ?: properties.getProperty("reportExecutionId")?.let {
                reportFile(it).takeIf(File::isFile)?.let(::readReport)
            }
            val status = when {
                ownReport != null -> ExplainabilitySessionStatus.COMPLETED
                persistedStatus == ExplainabilitySessionStatus.RUNNING -> ExplainabilitySessionStatus.INTERRUPTED
                else -> persistedStatus
            }
            ExplainabilitySessionState(loading = false, status = status, request = request, report = priorReport,
                startedAtEpochMillis = properties.getProperty("startedAt", "0").toLong(),
                message = when (status) {
                    ExplainabilitySessionStatus.INTERRUPTED -> "The previous explanation was interrupted when the app stopped. No analysis is running. Generate a new report to retry; earlier completed evidence is retained."
                    ExplainabilitySessionStatus.COMPLETED -> "Saved explainability report restored. Figures are available below."
                    else -> properties.getProperty("message", "No explanation is running.")
                })
        } catch (error: Exception) {
            ExplainabilitySessionState(loading = false, status = ExplainabilitySessionStatus.FAILED,
                message = "Saved explainability evidence could not be read: ${error.message}. Existing files were preserved.")
        }
    }

    @Synchronized
    fun saveState(state: ExplainabilitySessionState) {
        val properties = Properties().apply {
            setProperty("status", state.status.name)
            setProperty("message", state.message)
            setProperty("startedAt", state.startedAtEpochMillis.toString())
            state.request?.let { request ->
                setProperty("executionId", request.executionId); setProperty("runId", request.runId)
                setProperty("modelPath", request.modelPath); setProperty("modelSha256", request.modelSha256)
                setProperty("samples", request.samples.toString()); setProperty("steps", request.steps.toString())
            }
            state.report?.let { setProperty("reportExecutionId", it.request.executionId) }
        }
        atomicWrite(journal) { output -> properties.store(output, "Explainability session v1") }
    }

    @Synchronized
    fun saveReport(report: StoredExplainabilityReport) {
        validateReport(report)
        val destination = reportFile(report.request.executionId)
        check(!destination.exists()) { "This explainability execution is already committed." }
        atomicWrite(destination) { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val out = DataOutputStream(DigestOutputStream(stream, digest))
            out.writeInt(0x58414931)
            with(report.request) {
                out.writeUTF(executionId); out.writeUTF(runId); out.writeUTF(modelPath); out.writeUTF(modelSha256)
                out.writeInt(samples); out.writeInt(steps)
            }
            out.writeLong(report.completedAtEpochMillis)
            with(report.result) {
                out.writeUTF(runId); out.writeUTF(profile.name); out.writeUTF(candidateId)
                out.writeInt(explainedSampleCount); out.writeInt(integratedGradientSteps)
                out.writeUTF(featureSelectionName)
                out.writeDouble(explainedSampleAccuracy); out.writeDouble(globalImportanceStability)
                out.writeDouble(meanCompletenessError); out.writeDouble(maximumCompletenessError); out.writeLong(durationMillis)
                out.writeInt(globalImportance.size)
                globalImportance.forEach { out.writeUTF(it.featureName); out.writeDouble(it.meanAbsoluteAttribution); out.writeDouble(it.meanSignedAttribution) }
                out.writeInt(localExplanations.size)
                localExplanations.forEach { local ->
                    out.writeLong(local.sourceRowIndex); out.writeUTF(local.trueLabel.name)
                    out.writeUTF(local.predictedLabel.name); out.writeUTF(local.contrastLabel.name)
                    out.writeDouble(local.confidence); out.writeDouble(local.baseMargin)
                    out.writeDouble(local.predictionMargin); out.writeDouble(local.completenessError)
                    out.writeInt(local.attributions.size)
                    local.attributions.forEach { out.writeUTF(it.featureName); out.writeDouble(it.attribution); out.writeDouble(it.normalizedFeatureValue) }
                }
            }
            out.flush()
            stream.write(digest.digest())
        }
    }

    private fun readReport(file: File): StoredExplainabilityReport {
        verifyChecksum(file)
        return DataInputStream(file.inputStream().buffered()).use { input ->
        require(input.readInt() == 0x58414931) { "Unsupported explainability report format" }
        val request = ExplainabilityRequest(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readInt(), input.readInt())
        require(file.name == "${request.executionId}.xai")
        val completedAt = input.readLong()
        val runId = input.readUTF(); val profile = TrainingFeatureProfile.valueOf(input.readUTF()); val candidate = input.readUTF()
        val sampleCount = input.readInt().also { require(it in 1..5000) }
        val steps = input.readInt().also { require(it in 8..256) }
        val selection = input.readUTF()
        val accuracy = input.readDouble(); val stability = input.readDouble()
        val meanError = input.readDouble(); val maxError = input.readDouble(); val duration = input.readLong()
        val importance = List(input.readCount(1024)) { GlobalFeatureImportance(input.readUTF(), input.readDouble(), input.readDouble()) }
        val localCount = input.readCount(5000)
        require(localCount == sampleCount && localCount.toLong() * importance.size <= 2_500_000L)
        val locals = List(localCount) {
            val row = input.readLong(); val truth = TrainingLabel.valueOf(input.readUTF())
            val prediction = TrainingLabel.valueOf(input.readUTF()); val contrast = TrainingLabel.valueOf(input.readUTF())
            val confidence = input.readDouble(); val base = input.readDouble(); val margin = input.readDouble(); val error = input.readDouble()
            val featureCount = input.readCount(1024).also { require(it == importance.size) }
            val attributes = List(featureCount) { FeatureAttribution(input.readUTF(), input.readDouble(), input.readDouble()) }
            LocalPredictionExplanation(row, truth, prediction, contrast, confidence, base, margin, error, attributes)
        }
        input.readFully(ByteArray(32))
        require(input.read() == -1) { "Unexpected report trailing data" }
        require(runId == request.runId && completedAt > 0)
        StoredExplainabilityReport(request, ExplainabilityResult(runId, profile, candidate, sampleCount, steps,
            importance, locals, accuracy, stability, meanError, maxError, duration, selection), completedAt).also(::validateReport)
        }
    }

    private fun validateReport(report: StoredExplainabilityReport) {
        val result = report.result
        val request = report.request
        require(request.runId.isNotBlank() && request.modelPath.isNotBlank())
        require(request.modelSha256.matches(Regex("[a-f0-9]{64}")))
        require(request.samples in 16..5000 && request.steps in 8..256)
        require(request.runId == result.runId && request.steps == result.integratedGradientSteps)
        require(report.completedAtEpochMillis > 0 && result.durationMillis >= 0)
        require(result.candidateId.isNotBlank() && result.featureSelectionName.isNotBlank())
        require(result.explainedSampleCount in 1..request.samples && result.localExplanations.size == result.explainedSampleCount)
        require(result.globalImportance.size in 1..1024)
        require(result.localExplanations.size.toLong() * result.globalImportance.size <= 2_500_000L)
        require(result.explainedSampleAccuracy.isFinite() && result.explainedSampleAccuracy in 0.0..1.0)
        require(result.globalImportanceStability.isFinite() && result.globalImportanceStability in 0.0..1.0)
        require(result.meanCompletenessError.isFinite() && result.meanCompletenessError >= 0)
        require(result.maximumCompletenessError.isFinite() && result.maximumCompletenessError >= 0 && result.meanCompletenessError <= result.maximumCompletenessError + 1e-12 * maxOf(1.0, result.maximumCompletenessError))
        val names = result.globalImportance.map { it.featureName }
        require(names.all(String::isNotBlank) && names.distinct().size == names.size)
        result.globalImportance.forEach {
            require(it.meanAbsoluteAttribution.isFinite() && it.meanAbsoluteAttribution >= 0 && it.meanSignedAttribution.isFinite())
        }
        val order = result.localExplanations.first().attributions.map { it.featureName }
        require(order.size == names.size && order.toSet() == names.toSet())
        require(result.localExplanations.map { it.sourceRowIndex }.distinct().size == result.localExplanations.size)
        result.localExplanations.forEach { local ->
            require(local.sourceRowIndex >= 0 && local.predictedLabel != local.contrastLabel)
            require(local.confidence.isFinite() && local.confidence in 0.0..1.0)
            require(local.baseMargin.isFinite() && local.predictionMargin.isFinite())
            require(local.completenessError.isFinite() && local.completenessError >= 0)
            require(local.attributions.map { it.featureName } == order)
            local.attributions.forEach { require(it.attribution.isFinite() && it.normalizedFeatureValue.isFinite()) }
        }
    }

    private fun verifyChecksum(file: File) {
        require(file.length() in 36L..536_870_912L) { "Invalid report byte length" }
        file.inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            var remaining = file.length() - 32
            val buffer = ByteArray(65536)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                require(count > 0) { "Truncated report" }
                digest.update(buffer, 0, count); remaining -= count
            }
            val expected = ByteArray(32)
            DataInputStream(input).readFully(expected)
            require(MessageDigest.isEqual(expected, digest.digest())) { "Explainability report checksum mismatch" }
        }
    }

    private fun DataInputStream.readCount(maximum: Int): Int = readInt().also { require(it in 0..maximum) }
    private fun reportFile(executionId: String): File {
        require(UUID.fromString(executionId).toString() == executionId) { "Invalid explanation execution identifier" }
        return File(directory, "$executionId.xai")
    }
    private fun atomicWrite(destination: File, write: (FileOutputStream) -> Unit) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create explanation storage" }
        val temporary = File(directory, ".${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output -> write(output); output.fd.sync() }
            // Same-directory atomic replacement: a failed commit preserves the previous journal.
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
