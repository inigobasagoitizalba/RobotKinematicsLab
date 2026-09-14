package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import kotlin.math.abs
import kotlin.math.max

data class TrainingDatasetRequirements(
    val requestedRows: Int,
    val minimumRobotCount: Int,
    val exactTolerance: Double? = null,
    val exactMaxIterations: Int? = null,
    val exactDamping: Double? = null,
    val exactMaxStep: Double? = null,
    val targetMode: DatasetTargetMode? = null,
    val reachableFraction: Double? = null,
    val requireCurrentRandomProtocol: Boolean = true
)

enum class DatasetCompatibilityLevel(val displayName: String) {
    COMPATIBLE("Compatible"),
    PARTIAL("Partially compatible"),
    INCOMPATIBLE("Incompatible")
}

data class TrainingDatasetCompatibility(
    val level: DatasetCompatibilityLevel,
    val reasons: List<String>
)

object TrainingDatasetCompatibilityEvaluator {

    fun evaluate(
        manifest: DatasetManifest,
        requirements: TrainingDatasetRequirements
    ): TrainingDatasetCompatibility {
        require(requirements.requestedRows >= 30)
        require(requirements.minimumRobotCount in 1..10_000)
        require(requirements.requestedRows <= 1_000_000)
        require(listOfNotNull(requirements.exactTolerance, requirements.exactDamping, requirements.exactMaxStep).all { it.isFinite() && it > 0.0 })
        require(requirements.exactMaxIterations == null || requirements.exactMaxIterations > 0)
        require(requirements.reachableFraction == null || requirements.reachableFraction.isFinite() && requirements.reachableFraction in 0.0..1.0)
        val blockers = mutableListOf<String>()
        if (!manifest.hasCompleteBatchProvenance) {
            blockers += "dataset append history is incomplete or inconsistent"
        }
        if (manifest.rowCount > Int.MAX_VALUE) blockers += "dataset row count exceeds the supported reader range"
        if (manifest.batches.isNotEmpty()) {
            if (manifest.batches.map { it.generationIndex }.sorted() != (0 until manifest.generationCount).toList() ||
                manifest.batches.map { it.batchId }.distinct().size != manifest.batches.size ||
                manifest.batches.any { it.robotIds.distinct().size != it.robotIds.size } ||
                manifest.batches.flatMap { it.robotIds }.toSet() != manifest.robotIds.toSet()) {
                blockers += "dataset append identities or robot provenance are inconsistent"
            }
        }
        if (manifest.rowCount < 30L) blockers += "fewer than 30 trainable rows"
        if (manifest.robotIds.distinct().size < requirements.minimumRobotCount) {
            blockers += "${manifest.robotIds.distinct().size} robots; ${requirements.minimumRobotCount} required"
        }
        if (manifest.batches.isEmpty() && manifest.generationCount == 1) {
            validateContract(
                label = "dataset",
                ikConfig = manifest.ikConfig,
                targetMode = manifest.targetMode,
                reachableFraction = manifest.reachableFraction,
                randomProtocol = manifest.randomProtocol,
                requirements = requirements,
                blockers = blockers
            )
        }
        manifest.batches.forEach { batch ->
            val label = "batch ${batch.generationIndex + 1}"
            validateContract(label, batch.ikConfig, batch.targetMode, batch.reachableFraction, batch.randomProtocol, requirements, blockers)
        }
        if (blockers.isNotEmpty()) {
            return TrainingDatasetCompatibility(DatasetCompatibilityLevel.INCOMPATIBLE, blockers)
        }
        if (manifest.rowCount < requirements.requestedRows) {
            return TrainingDatasetCompatibility(
                DatasetCompatibilityLevel.PARTIAL,
                listOf("${manifest.rowCount} recorded rows available of ${requirements.requestedRows} requested; fewer rows are usable, not corruption")
            )
        }
        return TrainingDatasetCompatibility(
            DatasetCompatibilityLevel.COMPATIBLE,
            listOf("solver contract, target sampling and requested capacity match")
        )
    }

    private fun sameScientificValue(actual: Double, required: Double): Boolean {
        if (!actual.isFinite() || !required.isFinite()) return false
        val scale = max(abs(actual), abs(required))
        return abs(actual - required) <= scale * 1e-12
    }

    private fun validateContract(
        label: String,
        ikConfig: com.robotkinematicslab.mobile.domain.config.IKConfig,
        targetMode: DatasetTargetMode,
        reachableFraction: Double,
        randomProtocol: String,
        requirements: TrainingDatasetRequirements,
        blockers: MutableList<String>
    ) {
        requirements.exactTolerance?.let { required ->
            if (!sameScientificValue(ikConfig.tolerance, required)) blockers += "$label tolerance ${ikConfig.tolerance} m, requires $required m"
        }
        requirements.exactMaxIterations?.let { required ->
            if (ikConfig.maxIterations != required) blockers += "$label has ${ikConfig.maxIterations} IK iterations, requires $required"
        }
        requirements.exactDamping?.let { required ->
            if (!sameScientificValue(ikConfig.damping, required)) blockers += "$label damping ${ikConfig.damping}, requires $required"
        }
        requirements.exactMaxStep?.let { required ->
            if (!sameScientificValue(ikConfig.maxStep, required)) blockers += "$label maximum step ${ikConfig.maxStep}, requires $required"
        }
        requirements.targetMode?.let { required ->
            if (targetMode != required) blockers += "$label target mode $targetMode, requires $required"
        }
        requirements.reachableFraction?.let { required ->
            if (!sameScientificValue(reachableFraction, required)) blockers += "$label reachable fraction $reachableFraction, requires $required"
        }
        if (requirements.requireCurrentRandomProtocol && randomProtocol != ScientificRandomProtocol.ID) {
            blockers += "$label uses legacy random protocol $randomProtocol"
        }
    }
}
