package com.robotkinematicslab.mobile.process

enum class ResearchProcessKind(val displayName: String) {
    DIAGNOSTIC("Diagnostic"),
    DATASET("Dataset"),
    TRAINING("AI training"),
    ANALYSIS("Scientific analysis"),
    VISUALIZATION("Visualization")
}

object ResearchProcessIds {
    const val DIAGNOSTIC_BENCHMARK = "diagnostic_benchmark"
    const val DATASET_GENERATION = "dataset_generation"
    const val LOCAL_TRAINING = "local_training"
    const val CLOSED_LOOP_TRAINING = "closed_loop_training"
    const val ONE_MICRON_TRAINING = "one_micron_training"
    const val ROBUSTNESS_AUDIT = "robustness_audit"
    const val TRAINING_COMPARISON = "training_comparison"
    const val EXPLAINABILITY = "explainability"
    const val NUMERICAL_SAFETY_AUDIT = "numerical_safety_audit"
    const val DATASET_QUALITY_AUDIT = "dataset_quality_audit"
    const val WORKSPACE_ANALYSIS = "workspace_analysis"
    const val RESEARCH_PACK_IMPORT = "research_pack_import"

    fun continuousDataset(projectId: String): String = "continuous_dataset:$projectId"
}

enum class ResearchProcessStatus {
    PREPARING,
    RUNNING,
    PAUSING,
    SUCCEEDED,
    PAUSED,
    CANCELLED,
    FAILED;

    val isActive: Boolean
        get() = this == PREPARING || this == RUNNING || this == PAUSING

    val isTerminal: Boolean
        get() = !isActive
}

enum class ResearchResultDestination {
    TRAINING_RUN
}

data class ResearchResultReference(
    val destination: ResearchResultDestination,
    val artifactId: String
) {
    init {
        require(artifactId.isNotBlank() && artifactId.length <= 512 && artifactId.none(Char::isISOControl)) {
            "A result reference requires a bounded, printable artifact ID."
        }
    }
}

data class ResearchProcessSnapshot(
    val id: String,
    val jobId: String = id,
    val lifecycleSequence: Long = 0L,
    val title: String,
    val kind: ResearchProcessKind,
    val status: ResearchProcessStatus,
    val progressFraction: Double?,
    val stage: String,
    val detail: String,
    val startedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val canCancel: Boolean,
    val resultReference: ResearchResultReference? = null
) {
    val safeProgressFraction: Double?
        get() = progressFraction?.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0)

    val actionableResult: ResearchResultReference?
        get() = resultReference.takeIf { status == ResearchProcessStatus.SUCCEEDED }
}

data class ResearchProcessSummary(
    val active: List<ResearchProcessSnapshot>,
    val recent: List<ResearchProcessSnapshot>
) {
    val overallProgressFraction: Double?
        get() {
            if (active.isEmpty()) return null
            val known = active.map(ResearchProcessSnapshot::safeProgressFraction)
            // An average that silently omits an indeterminate task can look almost complete
            // while another task is still only preparing. Keep the aggregate indeterminate
            // until every active process reports a scientifically meaningful fraction.
            return known.takeIf { values -> values.all { it != null } }
                ?.filterNotNull()
                ?.average()
        }

    companion object {
        fun from(processes: List<ResearchProcessSnapshot>): ResearchProcessSummary =
            ResearchProcessSummary(
                active = processes.filter { it.status.isActive }.sortedBy(ResearchProcessSnapshot::startedAtEpochMillis),
                recent = processes.filter { it.status.isTerminal }.sortedByDescending(ResearchProcessSnapshot::updatedAtEpochMillis)
            )
    }
}

internal fun researchProcessFailureStage(kind: ResearchProcessKind?): String =
    kind?.let { "${it.displayName} failed" } ?: "Scientific process failed"
