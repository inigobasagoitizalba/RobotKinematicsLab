package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.dataset.DatasetManifest
import java.io.File

/** The summary and execution use this same cap. Requested rows include train, validation and test. */
data class SingleRunDatasetPlan(
    val manifest: DatasetManifest,
    val requirements: TrainingDatasetRequirements,
    val safetyRowLimit: Int,
    val compatibility: TrainingDatasetCompatibility,
    val effectiveRows: Int
) {
    val canTrain: Boolean get() = compatibility.level != DatasetCompatibilityLevel.INCOMPATIBLE && effectiveRows >= 30
}

object SingleRunDatasetPlanner {
    fun plan(manifest: DatasetManifest, requirements: TrainingDatasetRequirements, safetyRowLimit: Int): SingleRunDatasetPlan {
        val verdict = TrainingDatasetCompatibilityEvaluator.evaluate(manifest, requirements)
        val rows = minOf(manifest.rowCount, requirements.requestedRows.toLong(), safetyRowLimit.coerceAtLeast(0).toLong()).toInt()
        return SingleRunDatasetPlan(manifest, requirements, safetyRowLimit, verdict, rows)
    }

    fun preserveSelection(selected: DatasetManifest?, manifests: List<DatasetManifest>, requirements: TrainingDatasetRequirements?): DatasetManifest? {
        if (requirements == null) return selected?.let { current -> manifests.firstOrNull { it.csvPath == current.csvPath } }
        fun eligible(value: DatasetManifest) = TrainingDatasetCompatibilityEvaluator.evaluate(value, requirements).level != DatasetCompatibilityLevel.INCOMPATIBLE
        return selected?.let { current -> manifests.firstOrNull { it.csvPath == current.csvPath && eligible(it) } }
            ?: manifests.firstOrNull(::eligible)
    }

    fun inspect(manifest: DatasetManifest, requirements: TrainingDatasetRequirements, cancelled: () -> Boolean = { false }): String {
        require(TrainingDatasetCompatibilityEvaluator.evaluate(manifest, requirements).level != DatasetCompatibilityLevel.INCOMPATIBLE)
        val file = File(manifest.csvPath)
        val check = { if (cancelled()) throw com.robotkinematicslab.mobile.ml.training.LocalTrainingCancelledException() }
        val digest = TrainingInferenceContract.corpusDigest(file, check)
        val loaded = ScientificDatasetTrainingReader().load(file, TrainingFeatureProfile.BASELINE_KINEMATICS, 30,
            cancellationRequested = cancelled, sampleAcrossEntireFile = true, expectedDataRowCount = manifest.rowCount,
            requireEveryRowValid = true, expectedManifest = manifest, requirements = requirements)
        check()
        requireNotNull(loaded.dataset) { loaded.errorMessage ?: "Dataset validation failed." }
        require(digest == TrainingInferenceContract.corpusDigest(file, check)) { "Dataset changed during validation; refresh it." }
        return digest
    }
}
