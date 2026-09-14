package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.model.TrainingModelKind

/** Shared execution/UI rules. ResourceMode selects candidate widths; it never sets optimizer
 * controls, row caps or device workers. Those are separate parts of the recorded plan. */
object TrainingControlContract {
    fun issues(config: LocalTrainingConfig): Map<String, String> = buildMap {
        if (config.epochs !in 1..1000) put("epochs", "Use 1–1,000 epochs per candidate.")
        if (config.batchSize !in 1..8192) put("batch", "Use 1–8,192 rows per mini-batch.")
        if (!config.learningRate.isFinite() || config.learningRate !in 1e-6..1.0) put("learningRate", "Use a finite learning rate from 0.000001 to 1.")
        if (!config.l2Regularization.isFinite() || config.l2Regularization !in 0.0..1.0) put("l2", "Use finite L2 regularization from 0 to 1.")
        if (config.hiddenUnits !in 2..512) put("hidden", "Use 2–512 hidden units.")
        if (config.earlyStoppingPatience !in 1..100) put("patience", "Use 1–100 epochs of patience.")
        if (config.workerCount !in 1..64) put("workers", "Use 1–64 requested workers; runtime safety can reduce this limit.")
    }

    fun hiddenUnitsApply(kind: TrainingModelKind, mode: TrainingResourceMode): Boolean =
        kind == TrainingModelKind.COMPACT_MLP || (kind == TrainingModelKind.AUTOMATIC && mode != TrainingResourceMode.QUICK)

    fun candidateWidths(kind: TrainingModelKind, mode: TrainingResourceMode, hiddenUnits: Int): List<Int> {
        require(hiddenUnits in 2..512)
        return when (kind) {
            TrainingModelKind.LINEAR_SOFTMAX -> listOf(0)
            TrainingModelKind.COMPACT_MLP -> listOf(hiddenUnits)
            TrainingModelKind.AUTOMATIC -> listOf(0) + when (mode) {
                TrainingResourceMode.QUICK -> listOf(12)
                TrainingResourceMode.BALANCED -> listOf(hiddenUnits.coerceAtLeast(16),32).distinct()
                TrainingResourceMode.MAXIMUM_ACCURACY -> listOf(hiddenUnits.coerceAtLeast(24),48,72).distinct()
            }
        }
    }

    internal fun candidates(config: LocalTrainingConfig): List<CandidateSpecification> =
        candidateWidths(config.modelKind,config.resourceMode,config.hiddenUnits).map { width ->
            if (width == 0) CandidateSpecification("linear-softmax",TrainingModelKind.LINEAR_SOFTMAX,0)
            else CandidateSpecification("compact-mlp-$width",TrainingModelKind.COMPACT_MLP,width)
        }

    fun searchDescription(kind: TrainingModelKind, mode: TrainingResourceMode, hidden: Int): String =
        candidateWidths(kind,mode,hidden).joinToString(" + ") { if(it == 0) "linear softmax" else "one-layer ReLU network ($it hidden units)" } +
            ". Validation macro-F1 selects the candidate, with validation log loss breaking near-ties. Test data is evaluated after selection. More capacity or candidates need more work and need not improve test performance."

    fun usesDefaultControls(config: LocalTrainingConfig): Boolean =
        config.epochs == 40 && config.batchSize == 128 && config.learningRate == .003 &&
            config.l2Regularization == 1e-4 && config.hiddenUnits == 24 && config.randomSeed == 42 && config.earlyStoppingPatience == 8
}

/** Actual numeric controls recovered from a completed run, never reconstructed from today's presets. */
data class StoredTrainingControls(
    val modelKind: TrainingModelKind,
    val resourceMode: TrainingResourceMode,
    val epochs: Int,
    val batchSize: Int,
    val learningRate: Double,
    val l2Regularization: Double,
    val hiddenUnits: Int,
    val randomSeed: Int,
    val earlyStoppingPatience: Int,
    val requestedWorkers: Int,
    val workerBatchCounts: Map<Int,Long>
) {
    init {
        val config = asConfig()
        require(TrainingControlContract.issues(config).isEmpty()) { "Invalid stored training controls." }
        require(workerBatchCounts.all { (workers,batches) -> workers in 1..requestedWorkers && batches > 0 }) { "Invalid applied worker counts." }
    }
    fun asConfig() = LocalTrainingConfig("stored controls","stored dataset",modelKind=modelKind,resourceMode=resourceMode,
        epochs=epochs,batchSize=batchSize,learningRate=learningRate,l2Regularization=l2Regularization,
        hiddenUnits=hiddenUnits,randomSeed=randomSeed,earlyStoppingPatience=earlyStoppingPatience,workerCount=requestedWorkers)
    companion object {
        fun from(config: LocalTrainingConfig, batches: Map<Int,Long>) = StoredTrainingControls(config.modelKind,config.resourceMode,
            config.epochs,config.batchSize,config.learningRate,config.l2Regularization,config.hiddenUnits,config.randomSeed,
            config.earlyStoppingPatience,config.workerCount,batches.toSortedMap())
    }
}
