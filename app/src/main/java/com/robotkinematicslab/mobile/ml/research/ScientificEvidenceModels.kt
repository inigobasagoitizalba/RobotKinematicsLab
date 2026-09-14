package com.robotkinematicslab.mobile.ml.research

/** One immutable prediction made on data that was not used to fit the model. */
data class ScientificPredictionObservation(
    val sampleId: Long,
    val modelId: String,
    val robotId: String,
    val topologyKey: String,
    val confidence: Double,
    val probabilityAssignedToTruth: Double,
    val correct: Boolean
) {
    init {
        require(modelId.isNotBlank())
        require(robotId.isNotBlank())
        require(topologyKey.isNotBlank())
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(probabilityAssignedToTruth.isFinite() && probabilityAssignedToTruth in 0.0..1.0)
    }
}

data class SelectiveRiskPoint(
    val acceptedCount: Int,
    val coverage: Double,
    val risk: Double,
    val confidenceThreshold: Double
)

data class SelectiveRiskCurve(
    val modelId: String,
    val totalCount: Int,
    val points: List<SelectiveRiskPoint>,
    /** Area under the risk/coverage curve. Lower is better. */
    val areaUnderRiskCoverage: Double
)

data class GeneralizationSlice(
    val modelId: String,
    val sliceId: String,
    val sampleCount: Int,
    val accuracy: Double,
    val meanConfidence: Double,
    val meanTruthProbability: Double,
    val calibrationGap: Double,
    val wilsonLower95: Double,
    val wilsonUpper95: Double
)

data class CalibrationSlice(
    val modelId: String,
    val sliceId: String,
    val sampleCount: Int,
    val meanConfidence: Double,
    val empiricalAccuracy: Double,
    val absoluteGap: Double
)

data class TailRiskSummary(
    val modelId: String,
    val sampleCount: Int,
    val medianLoss: Double,
    val p95Loss: Double,
    val p99Loss: Double,
    val maximumLoss: Double,
    val cvar95Loss: Double,
    val cvar99Loss: Double
)

data class PipelineErrorObservation(
    val sampleId: Long,
    val initialErrorMeters: Double,
    val afterValidationErrorMeters: Double,
    val afterRepairErrorMeters: Double,
    val afterSolverErrorMeters: Double,
    val finalErrorMeters: Double,
    val repairApplied: Boolean,
    val fallbackApplied: Boolean
) {
    init {
        listOf(
            initialErrorMeters,
            afterValidationErrorMeters,
            afterRepairErrorMeters,
            afterSolverErrorMeters,
            finalErrorMeters
        ).forEach { require(it.isFinite() && it >= 0.0) }
    }
}

data class PipelineErrorBudget(
    val sampleCount: Int,
    val meanInitialErrorMeters: Double,
    val validationDeltaMeters: Double,
    val repairDeltaMeters: Double,
    val solverDeltaMeters: Double,
    val finalizationDeltaMeters: Double,
    val meanFinalErrorMeters: Double,
    val totalErrorReductionFraction: Double,
    val repairUseRate: Double,
    val fallbackUseRate: Double
)

data class PerturbationObservation(
    val sampleId: Long,
    val magnitude: Double,
    val baselineLoss: Double,
    val perturbedLoss: Double,
    val baselineCorrect: Boolean,
    val perturbedCorrect: Boolean
) {
    init {
        require(magnitude.isFinite() && magnitude >= 0.0)
        require(baselineLoss.isFinite() && baselineLoss >= 0.0)
        require(perturbedLoss.isFinite() && perturbedLoss >= 0.0)
    }
}

data class RobustnessSlice(
    val magnitude: Double,
    val sampleCount: Int,
    val meanLossIncrease: Double,
    val accuracyDrop: Double,
    val failureIntroductionRate: Double
)

data class TrainingScaleObservation(
    val label: String,
    val rowCount: Int,
    val score: Double,
    val trainingMillis: Long,
    val peakMemoryBytes: Long = 0L
) {
    init {
        require(label.isNotBlank())
        require(rowCount > 0)
        require(score.isFinite() && score in 0.0..1.0)
        require(trainingMillis >= 0L)
        require(peakMemoryBytes >= 0L)
    }
}

data class TrainingScaleEvidence(
    val observations: List<TrainingScaleObservation>,
    val paretoEfficientLabels: Set<String>,
    val scoreGainPerAdditionalThousandRows: List<Double>
)

data class FeatureFamilyImportance(
    val familyId: String,
    val featureCount: Int,
    val totalAbsoluteImportance: Double,
    val meanAbsoluteImportance: Double,
    val signedImportance: Double
)

data class FeatureFamilyAblation(
    val familyId: String,
    val fullModelScore: Double,
    val ablatedModelScore: Double,
    val scoreDelta: Double,
    val trainingTimeDeltaMillis: Long,
    val inferenceTimeDeltaNanos: Double
)

data class FeatureFamilyCoActivation(
    val rowFamily: String,
    val columnFamily: String,
    val meanAbsoluteProduct: Double,
    val normalizedStrength: Double,
    val sampleCount: Int
)

data class CoverageObservation(
    val sampleId: Long,
    val groupId: String,
    val features: DoubleArray
) {
    init {
        require(groupId.isNotBlank())
        require(features.isNotEmpty())
        require(features.all(Double::isFinite))
    }

    override fun equals(other: Any?): Boolean =
        other is CoverageObservation &&
            sampleId == other.sampleId &&
            groupId == other.groupId &&
            features.contentEquals(other.features)

    override fun hashCode(): Int = 31 * (31 * sampleId.hashCode() + groupId.hashCode()) + features.contentHashCode()
}

data class CoveragePoint(
    val sampleId: Long,
    val groupId: String,
    val normalizedNearestReferenceDistance: Double,
    val outsideTrainingRangeFraction: Double,
    val outOfDistribution: Boolean
)

data class DatasetCoverageSummary(
    val referenceCount: Int,
    val comparedReferenceCount: Int,
    val evaluationCount: Int,
    val referenceGroupCount: Int,
    val evaluationGroupCount: Int,
    val featureCount: Int,
    val points: List<CoveragePoint>,
    val calibratedDistanceThreshold: Double,
    val meanNearestDistance: Double,
    val p95NearestDistance: Double,
    val outOfDistributionRate: Double,
    val rangeEscapeRate: Double,
    val meanOutsideTrainingRangeFraction: Double,
    val constantFeatureCount: Int
)

data class ConformalClassificationObservation(
    val sampleId: Long,
    val truthIndex: Int,
    val probabilities: DoubleArray
) {
    init {
        require(truthIndex in probabilities.indices)
        require(probabilities.isNotEmpty())
        require(probabilities.all { it.isFinite() && it in 0.0..1.0 })
        require(kotlin.math.abs(probabilities.sum() - 1.0) <= 1e-3)
    }

    override fun equals(other: Any?): Boolean =
        other is ConformalClassificationObservation &&
            sampleId == other.sampleId &&
            truthIndex == other.truthIndex &&
            probabilities.contentEquals(other.probabilities)

    override fun hashCode(): Int = 31 * (31 * sampleId.hashCode() + truthIndex) + probabilities.contentHashCode()
}

data class ConformalClassificationEvidence(
    val alpha: Double,
    val calibrationCount: Int,
    val evaluationCount: Int,
    val probabilityThreshold: Double,
    val empiricalCoverage: Double,
    val averageSetSize: Double,
    val singletonRate: Double,
    val emptySetRate: Double
)
