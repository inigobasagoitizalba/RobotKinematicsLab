package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind

enum class TrainingResourceMode(
    val displayName: String
) {
    QUICK("Quick prototype"),
    BALANCED("Balanced"),
    MAXIMUM_ACCURACY("Maximum accuracy")
}

data class LocalTrainingConfig(
    val runName: String,
    val datasetPath: String,
    val compareFeatureProfiles: Boolean = true,
    val singleFeatureProfile: TrainingFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
    val modelKind: TrainingModelKind = TrainingModelKind.AUTOMATIC,
    val resourceMode: TrainingResourceMode = TrainingResourceMode.BALANCED,
    val splitStrategy: TrainingSplitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED,
    val maximumRows: Int = 50_000,
    val epochs: Int = 40,
    val batchSize: Int = 128,
    val learningRate: Double = 0.003,
    val l2Regularization: Double = 1e-4,
    val hiddenUnits: Int = 24,
    val randomSeed: Int = 42,
    val earlyStoppingPatience: Int = 8,
    /** Fixed upper bound; the live safety guard may temporarily use fewer workers. */
    val workerCount: Int = 1,
    val featureSelections: List<FeatureSelectionSpec> = emptyList(),
    /** Scan and sample the complete CSV instead of taking an ordered prefix. */
    val sampleAcrossEntireDataset: Boolean = false,
    /** Verified manifest row count used to reject a concurrent or partial dataset change. */
    val expectedDatasetRows: Long? = null,
    /** Newest committed rows that must be included in the bounded sample after closed-loop growth. */
    val requiredNewestRows: Int = 0,
    /** Single Run's validated selection; legacy/programmatic runs keep their existing loading policy. */
    val managedDatasetManifest: com.robotkinematicslab.mobile.dataset.DatasetManifest? = null,
    val datasetRequirements: com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements? = null,
    val expectedCorpusSha256: String? = null
) {
    /**
     * Optional explicit experiment arms. Empty preserves the legacy all/single-profile behaviour.
     * Every arm stores exact feature names, so arbitrary and same-sized subsets remain distinct.
     */
    val resolvedFeatureSelections: List<FeatureSelectionSpec>
        get() = featureSelections.ifEmpty {
            if (compareFeatureProfiles) {
                TrainingFeatureProfile.entries.map(FeatureSelectionSpec::complete)
            } else {
                listOf(FeatureSelectionSpec.complete(singleFeatureProfile))
            }
        }
}

enum class LocalTrainingPhase {
    IDLE,
    READING_DATASET,
    PREPARING_SPLITS,
    TRAINING_BASELINE,
    TRAINING_CONTEXT,
    EVALUATING,
    SAVING,
    COMPLETED,
    FAILED
}

data class ClassificationMetrics(
    val sampleCount: Int,
    val accuracy: Double,
    val balancedAccuracy: Double,
    val macroF1: Double,
    val logLoss: Double,
    val confusionMatrix: List<List<Int>>,
    val inferenceNanosPerSample: Double = Double.NaN,
    /** Multiclass Brier score; lower means the complete probability vector is more truthful. */
    val brierScore: Double = Double.NaN,
    /** Expected calibration error over equal-width confidence bins; lower is better. */
    val expectedCalibrationError: Double = Double.NaN,
    val calibrationBins: List<CalibrationBin> = emptyList(),
    /** True-label support per class. A zero means recall/F1 for that class is not measurable. */
    val classSupport: List<Int> = emptyList()
) {
    val missingTruthClassIndices: List<Int>
        get() = classSupport.indices.filter { classSupport[it] == 0 }
    val hasCompleteClassCoverage: Boolean
        get() = classSupport.isNotEmpty() && missingTruthClassIndices.isEmpty()
}

data class CalibrationBin(
    val lowerConfidence: Double,
    val upperConfidence: Double,
    val sampleCount: Int,
    val meanConfidence: Double,
    val empiricalAccuracy: Double
)

data class TrainingIterationMetrics(
    val globalIteration: Int,
    val profile: TrainingFeatureProfile,
    val candidateId: String,
    val modelKind: TrainingModelKind,
    val hiddenUnits: Int,
    val epoch: Int,
    val trainingLoss: Double,
    val validationMetrics: ClassificationMetrics,
    val elapsedMillis: Long,
    val featureSelectionId: String = profile.name.lowercase(),
    val featureSelectionName: String = profile.displayName
)

data class TrainedProfileResult(
    val profile: TrainingFeatureProfile,
    val candidateId: String,
    val model: LocalClassifierModel,
    val normalization: FeatureNormalization,
    val featureNames: List<String>,
    val trainRowCount: Int,
    val validationRowCount: Int,
    val testRowCount: Int,
    val skippedRowCount: Int,
    val duplicateFingerprintCount: Int,
    val duplicateFingerprintsKeptTogether: Boolean,
    val bestEpoch: Int,
    val validationMetrics: ClassificationMetrics,
    val testMetrics: ClassificationMetrics,
    val trainingDurationMillis: Long,
    val parameterCount: Int,
    val testSlices: List<ClassificationSliceMetrics> = emptyList(),
    val datasetWarnings: List<String> = emptyList(),
    val featureSelectionId: String = profile.name.lowercase(),
    val featureSelectionName: String = profile.displayName,
    val inferenceContract: com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract? = null,
    val splitEvidence: com.robotkinematicslab.mobile.ml.data.TrainingSplitEvidence? = null
)

data class ClassificationSliceMetrics(
    val id: String,
    val displayName: String,
    val metrics: ClassificationMetrics
)

data class TrainingComparison(
    val baseline: TrainedProfileResult?,
    val contextEnhanced: TrainedProfileResult?,
    val contextExpanded: TrainedProfileResult?,
    val macroF1Delta: Double,
    val balancedAccuracyDelta: Double,
    val logLossDelta: Double,
    val inferenceNanosDelta: Double,
    val expandedMacroF1DeltaVsBaseline: Double,
    val expandedMacroF1DeltaVsContext: Double,
    val expandedBalancedAccuracyDeltaVsBaseline: Double,
    val expandedLogLossDeltaVsBaseline: Double,
    val expandedInferenceNanosDeltaVsBaseline: Double,
    /** Every selected experiment arm, including arbitrary subsets and same-source-profile variants. */
    val variants: List<TrainedProfileResult> = listOfNotNull(baseline, contextEnhanced, contextExpanded)
)

data class LocalTrainingRunResult(
    val runId: String,
    val config: LocalTrainingConfig,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val iterations: List<TrainingIterationMetrics>,
    val comparison: TrainingComparison,
    val summaryPath: String = "",
    val historyCsvPath: String = "",
    val modelPaths: List<String> = emptyList(),
    /** Completed optimizer batches grouped by the actual number of gradient shards used. */
    val workerBatchCounts: Map<Int, Long> = emptyMap()
)

data class LocalTrainingProgress(
    val phase: LocalTrainingPhase,
    val completedWorkUnits: Int,
    val totalWorkUnits: Int,
    val profile: TrainingFeatureProfile?,
    val candidateId: String?,
    val epoch: Int?,
    val message: String
) {
    val fraction: Double
        get() =
            if (totalWorkUnits > 0) {
                completedWorkUnits.toDouble().div(totalWorkUnits).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
}
