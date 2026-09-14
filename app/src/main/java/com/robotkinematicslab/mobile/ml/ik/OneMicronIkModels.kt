package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetSplit
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy

const val ONE_MICRON_METERS: Double = 1e-6
const val ONE_MICRON_MAX_JOINTS: Int = 10

class OneMicronIkTrainingCancelledException : RuntimeException("One-micron training was cancelled.")

enum class OneMicronIkFeatureProfile(
    val displayName: String,
    val description: String
) {
    KINEMATICS_108(
        "Kinematics · 108",
        "DH table, joint types and limits, target, seed and solver contract."
    ),
    PHYSICS_CONTEXT_361(
        "Physics context · 361",
        "The same 108 inputs plus 253 pre-solve geometric, Jacobian, singularity and limit-pressure descriptors."
    )
}

data class OneMicronIkSample(
    val features: FloatArray,
    val normalizedJointDelta: FloatArray,
    val outputMask: FloatArray,
    val robotIndex: Int,
    val seedState: RobotState,
    val target: Vec3,
    val deterministicIterations: Int,
    val sourceRowIndex: Long,
    val splitFingerprint: Long,
    val robotFingerprint: Long,
    /** Fresh FK residual recomputed by the reader; never copied from the CSV claim. */
    val certifiedCartesianErrorMeters: Double = Double.NaN,
    val recordedCartesianErrorMeters: Double = Double.NaN
)

data class OneMicronIkDataset(
    val sourcePath: String,
    val profile: OneMicronIkFeatureProfile,
    val featureNames: List<String>,
    val robots: List<RobotDefinition>,
    val robotIds: List<String>,
    val samples: List<OneMicronIkSample>,
    val rowsRead: Int,
    val skippedRows: Int,
    val rejectedByMicronContract: Int
)

data class PreparedOneMicronIkDataset(
    val source: OneMicronIkDataset,
    val split: TrainingDatasetSplit,
    val normalization: FeatureNormalization,
    val normalizedFeatures: Array<FloatArray>
)

data class OneMicronIkTrainingConfig(
    val runName: String,
    val datasetPath: String,
    val profile: OneMicronIkFeatureProfile = OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361,
    val maximumRows: Int = 100_000,
    /** Original user request; [maximumRows] is the validated effective cap used by the job. */
    val requestedMaximumRows: Int = maximumRows,
    val sourceDatasetRows: Long = -1L,
    val datasetScientificFingerprint: String? = null,
    val datasetRobotIds: List<String> = emptyList(),
    val epochs: Int = 50,
    val batchSize: Int = 128,
    val learningRate: Double = 0.001,
    val l2Regularization: Double = 1e-5,
    val hiddenUnits: Int = 64,
    val workerCount: Int = 1,
    val randomSeed: Int = 42,
    val earlyStoppingPatience: Int = 8,
    val splitStrategy: TrainingSplitStrategy = TrainingSplitStrategy.SAMPLE_GROUPED,
    val verificationSampleLimit: Int = 1_000,
    /** Hard working-set boundary supplied by Android's safe compute policy. */
    val maximumWorkingMemoryBytes: Long = Long.MAX_VALUE,
    val solverConfig: IKConfig = oneMicronSolverConfig(),
    val featureSelection: OneMicronFeatureSelectionSpec? = null
) {
    val resolvedFeatureSelection: OneMicronFeatureSelectionSpec
        get() = featureSelection ?: OneMicronFeatureSelectionSpec.complete(profile)
}

data class OneMicronIkEffectiveMemoryPlan(
    val requestedRows: Int,
    val effectiveRows: Int,
    val featureCount: Int,
    val hiddenUnits: Int,
    val workerCount: Int,
    val batchSize: Int,
    val budgetBytes: Long,
    val estimatedBytes: Long
) {
    val isRunnable: Boolean
        get() = effectiveRows >= 30 && estimatedBytes <= budgetBytes

    val wasCapped: Boolean
        get() = effectiveRows < requestedRows
}

/** Conservative phone-side estimate covering raw and normalized feature matrices plus row metadata. */
object OneMicronIkMemoryEstimator {
    private const val ROW_METADATA_BYTES = 1_024L
    private const val FLOAT_BYTES = 4L
    // Current model, best checkpoint, a transient replacement checkpoint and two Adam moments.
    private const val LIVE_PARAMETER_COPIES = 5L

    fun estimatedWorkingSetBytes(profile: OneMicronIkFeatureProfile, rows: Int): Long {
        return estimatedWorkingSetBytes(OneMicronIkFeatureEncoder.featureNames(profile).size, rows)
    }

    fun estimatedWorkingSetBytes(featureCount: Int, rows: Int): Long {
        require(featureCount > 0)
        require(rows >= 0)
        val bytesPerRow = featureCount.toLong() * 8L + ROW_METADATA_BYTES
        return if (rows == 0 || bytesPerRow <= Long.MAX_VALUE / rows) {
            bytesPerRow * rows
        } else {
            Long.MAX_VALUE
        }
    }

    fun safeRowLimit(profile: OneMicronIkFeatureProfile, budgetBytes: Long): Int {
        val bytesPerRow = estimatedWorkingSetBytes(profile, 1).coerceAtLeast(1L)
        val rowsThatFit = budgetBytes.coerceAtLeast(0L) / bytesPerRow
        return if (rowsThatFit < 30L) {
            0
        } else {
            rowsThatFit.coerceAtMost(1_000_000L).toInt()
        }
    }

    /**
     * Includes the training-only allocations omitted by the dataset row estimate: model/checkpoint,
     * Adam moments and one gradient buffer per worker plus the deterministic reduction buffer.
     */
    fun estimatedTrainingWorkingSetBytes(
        profile: OneMicronIkFeatureProfile,
        rows: Int,
        hiddenUnits: Int,
        workerCount: Int,
        batchSize: Int
    ): Long {
        return estimatedTrainingWorkingSetBytes(
            featureCount = OneMicronIkFeatureEncoder.featureNames(profile).size,
            rows = rows,
            hiddenUnits = hiddenUnits,
            workerCount = workerCount,
            batchSize = batchSize
        )
    }

    fun estimatedTrainingWorkingSetBytes(
        featureCount: Int,
        rows: Int,
        hiddenUnits: Int,
        workerCount: Int,
        batchSize: Int
    ): Long {
        require(featureCount > 0)
        require(rows >= 0)
        require(hiddenUnits > 0)
        require(workerCount > 0)
        require(batchSize > 0)
        val featureCountLong = featureCount.toLong()
        val hidden = hiddenUnits.toLong()
        val parameterFloats =
            saturatingAdd(
                saturatingAdd(saturatingMultiply(featureCountLong, hidden), hidden),
                saturatingAdd(saturatingMultiply(hidden, ONE_MICRON_MAX_JOINTS.toLong()), ONE_MICRON_MAX_JOINTS.toLong())
            )
        val persistentTrainingBytes =
            saturatingMultiply(
                saturatingMultiply(parameterFloats, FLOAT_BYTES),
                LIVE_PARAMETER_COPIES
            )
        val scratchFloatsPerGradient =
            saturatingAdd(
                parameterFloats,
                saturatingAdd(hidden * 2L, ONE_MICRON_MAX_JOINTS.toLong() * 2L)
            )
        val activeWorkers = minOf(workerCount, batchSize, rows.coerceAtLeast(1)).toLong()
        val gradientBytes =
            saturatingMultiply(
                saturatingMultiply(scratchFloatsPerGradient, FLOAT_BYTES),
                activeWorkers + 1L
            )
        return saturatingAdd(
            estimatedWorkingSetBytes(featureCount, rows),
            saturatingAdd(persistentTrainingBytes, gradientBytes)
        )
    }

    fun safeTrainingRowLimit(
        profile: OneMicronIkFeatureProfile,
        budgetBytes: Long,
        hiddenUnits: Int,
        workerCount: Int,
        batchSize: Int
    ): Int {
        return safeTrainingRowLimit(
            featureCount = OneMicronIkFeatureEncoder.featureNames(profile).size,
            budgetBytes = budgetBytes,
            hiddenUnits = hiddenUnits,
            workerCount = workerCount,
            batchSize = batchSize
        )
    }

    fun safeTrainingRowLimit(
        featureCount: Int,
        budgetBytes: Long,
        hiddenUnits: Int,
        workerCount: Int,
        batchSize: Int
    ): Int {
        require(featureCount > 0)
        if (budgetBytes <= 0L || hiddenUnits <= 0 || workerCount <= 0 || batchSize <= 0) return 0
        var low = 0
        var high = 1_000_000
        while (low < high) {
            val candidate = low + (high - low + 1) / 2
            if (
                estimatedTrainingWorkingSetBytes(
                    featureCount = featureCount,
                    rows = candidate,
                    hiddenUnits = hiddenUnits,
                    workerCount = workerCount,
                    batchSize = batchSize
                ) <= budgetBytes
            ) {
                low = candidate
            } else {
                high = candidate - 1
            }
        }
        return if (low < 30) 0 else low
    }

    fun effectivePlan(
        requestedRows: Int,
        featureCount: Int,
        hiddenUnits: Int,
        workerCount: Int,
        batchSize: Int,
        budgetBytes: Long
    ): OneMicronIkEffectiveMemoryPlan {
        require(requestedRows >= 30)
        require(featureCount > 0)
        require(hiddenUnits > 0)
        require(workerCount > 0)
        require(batchSize > 0)
        val safeRows = safeTrainingRowLimit(featureCount, budgetBytes, hiddenUnits, workerCount, batchSize)
        val effectiveRows = minOf(requestedRows, safeRows)
        val estimatedBytes =
            if (effectiveRows >= 30) {
                estimatedTrainingWorkingSetBytes(featureCount, effectiveRows, hiddenUnits, workerCount, batchSize)
            } else {
                estimatedTrainingWorkingSetBytes(featureCount, 30, hiddenUnits, workerCount, batchSize)
            }
        return OneMicronIkEffectiveMemoryPlan(
            requestedRows = requestedRows,
            effectiveRows = effectiveRows,
            featureCount = featureCount,
            hiddenUnits = hiddenUnits,
            workerCount = workerCount,
            batchSize = batchSize,
            budgetBytes = budgetBytes,
            estimatedBytes = estimatedBytes
        )
    }

    private fun saturatingMultiply(left: Long, right: Long): Long =
        if (left == 0L || right == 0L) {
            0L
        } else if (left > Long.MAX_VALUE / right) {
            Long.MAX_VALUE
        } else {
            left * right
        }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}

enum class OneMicronIkTrainingPhase {
    READING_AND_CERTIFYING,
    PREPARING_SPLITS,
    TRAINING,
    VERIFYING_CARTESIAN_ERROR,
    SAVING,
    COMPLETED,
    FAILED
}

data class OneMicronIkTrainingProgress(
    val phase: OneMicronIkTrainingPhase,
    val completed: Int,
    val total: Int,
    val message: String
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

data class OneMicronIkEpochMetrics(
    val epoch: Int,
    val trainingLoss: Double,
    val validationLoss: Double,
    val elapsedMillis: Long
)

data class OneMicronIkVerificationMetrics(
    val samples: Int,
    val rawNeuralSuccessRate: Double,
    val neuralThenRefineSuccessRate: Double,
    val deterministicBaselineSuccessRate: Double,
    val rawMedianErrorMeters: Double,
    val rawP95ErrorMeters: Double,
    val refinedMedianErrorMeters: Double,
    val baselineMedianErrorMeters: Double,
    val meanRefinedIterations: Double,
    val meanBaselineIterations: Double,
    val meanNeuralInferenceNanos: Double,
    val verifiedPipelineSuccessRate: Double = Double.NaN,
    val rawCumulativeErrorMeters: Double = Double.NaN,
    val protectedCumulativeErrorMeters: Double = Double.NaN,
    val cumulativeErrorAvoidedPercent: Double = Double.NaN,
    val meanPipelineIterations: Double = Double.NaN,
    val iterationSavingsPercent: Double = Double.NaN,
    val certifiedToleranceMeters: Double = ONE_MICRON_METERS,
    val observations: List<OneMicronVerificationObservation> = emptyList()
) {
    init {
        require(samples >= 0)
        require(certifiedToleranceMeters.isFinite() && certifiedToleranceMeters > 0.0)
        listOf(rawNeuralSuccessRate, neuralThenRefineSuccessRate, deterministicBaselineSuccessRate).forEach { rate ->
            require(rate.isFinite() && rate in 0.0..1.0)
        }
        listOf(verifiedPipelineSuccessRate).filter(Double::isFinite).forEach { rate ->
            require(rate in 0.0..1.0)
        }
        require(neuralThenRefineSuccessRate + 1e-12 >= rawNeuralSuccessRate)
        if (verifiedPipelineSuccessRate.isFinite()) {
            require(verifiedPipelineSuccessRate + 1e-12 >= neuralThenRefineSuccessRate)
        }
        if (observations.isNotEmpty()) {
            require(observations.size == samples) { "Every verified case must retain one route observation." }
            require(observations.map(OneMicronVerificationObservation::sourceRowIndex).distinct().size == observations.size) {
                "A source row cannot be counted twice in one verification result."
            }
            observations.forEach { observation -> observation.requireScientificallyConsistent(certifiedToleranceMeters) }
            fun routeRate(path: VerifiedIkPath): Double = observations.count { it.path == path }.toDouble() / samples
            require(closeRate(rawNeuralSuccessRate, routeRate(VerifiedIkPath.NEURAL_DIRECT)))
            require(
                closeRate(
                    neuralThenRefineSuccessRate,
                    routeRate(VerifiedIkPath.NEURAL_DIRECT) + routeRate(VerifiedIkPath.NEURAL_REFINED)
                )
            )
            require(
                closeRate(
                    verifiedPipelineSuccessRate,
                    1.0 - routeRate(VerifiedIkPath.FAILED)
                )
            )
            require(closeRate(deterministicBaselineSuccessRate, observations.count { it.pureSolverCertified }.toDouble() / samples))
        }
    }

    /** Mutually exclusive pipeline outcomes, all using the same verified-case denominator. */
    val directNeuralSuccessRate: Double get() = rawNeuralSuccessRate
    val refinedOnlySuccessRate: Double get() = exclusiveRate(neuralThenRefineSuccessRate, rawNeuralSuccessRate)
    val fallbackOnlySuccessRate: Double get() = exclusiveRate(verifiedPipelineSuccessRate, neuralThenRefineSuccessRate)
    val failedPipelineRate: Double get() = exclusiveRate(1.0, verifiedPipelineSuccessRate)
    /** Independent comparator, never a fifth mutually exclusive pipeline outcome. */
    val pureSolverSuccessRate: Double get() = deterministicBaselineSuccessRate

    private fun exclusiveRate(total: Double, included: Double): Double =
        if (total.isFinite() && included.isFinite() && total in 0.0..1.0 && included in 0.0..1.0 && total + 1e-12 >= included)
            (total - included).coerceAtLeast(0.0) else Double.NaN

    private fun closeRate(left: Double, right: Double): Boolean =
        left.isFinite() && right.isFinite() && kotlin.math.abs(left - right) <= 1e-12
}

data class OneMicronVerificationObservation(
    val sourceRowIndex: Long,
    val robotFingerprint: Long,
    val path: VerifiedIkPath,
    val rawResidualMeters: Double,
    val refinedResidualMeters: Double,
    val pureSolverResidualMeters: Double,
    val finalPipelineResidualMeters: Double,
    val pureSolverCertified: Boolean,
    val refinementIterations: Int,
    val fallbackIterations: Int,
    val pureSolverIterations: Int
) {
    internal fun requireScientificallyConsistent(toleranceMeters: Double) {
        require(sourceRowIndex >= 0L)
        require(listOf(rawResidualMeters, finalPipelineResidualMeters).all { it.isFinite() && it >= 0.0 })
        require(listOf(refinementIterations, fallbackIterations, pureSolverIterations).all { it >= 0 })
        if (pureSolverCertified) {
            require(OneMicronVerificationCriterion.acceptsPositionResidual(pureSolverResidualMeters, toleranceMeters))
        }
        when (path) {
            VerifiedIkPath.NEURAL_DIRECT -> {
                require(OneMicronVerificationCriterion.acceptsPositionResidual(rawResidualMeters, toleranceMeters))
                require(finalPipelineResidualMeters.toBits() == rawResidualMeters.toBits())
                require(refinementIterations == 0 && fallbackIterations == 0)
            }
            VerifiedIkPath.NEURAL_REFINED -> {
                require(!OneMicronVerificationCriterion.acceptsPositionResidual(rawResidualMeters, toleranceMeters))
                require(OneMicronVerificationCriterion.acceptsPositionResidual(refinedResidualMeters, toleranceMeters))
                require(finalPipelineResidualMeters.toBits() == refinedResidualMeters.toBits())
                require(refinementIterations > 0 && fallbackIterations == 0)
            }
            VerifiedIkPath.DETERMINISTIC_FALLBACK -> {
                require(!OneMicronVerificationCriterion.acceptsPositionResidual(rawResidualMeters, toleranceMeters))
                require(pureSolverCertified)
                require(OneMicronVerificationCriterion.acceptsPositionResidual(finalPipelineResidualMeters, toleranceMeters))
                require(finalPipelineResidualMeters.toBits() == pureSolverResidualMeters.toBits())
            }
            VerifiedIkPath.FAILED -> {
                require(!pureSolverCertified) { "A certified deterministic fallback cannot be labelled pipeline failure." }
                require(!OneMicronVerificationCriterion.acceptsPositionResidual(rawResidualMeters, toleranceMeters))
            }
        }
    }
}

enum class ReliabilityBand(val displayName: String) {
    RESEARCH_GRADE("95% lower bound ≥ 99.9%"),
    STRONG("95% lower bound ≥ 99%"),
    MODERATE("95% lower bound ≥ 95%"),
    LIMITED("95% lower bound < 95%")
}

data class OneMicronReliabilityAssessment(
    val observedCertifiedRate: Double,
    val wilson95LowerBound: Double,
    val wilson95UpperBound: Double,
    val band: ReliabilityBand,
    val cumulativeErrorAvoidedPercent: Double,
    val cumulativeDistanceAvoidedMeters: Double,
    val meanRawResidualMeters: Double,
    val meanProtectedResidualMeters: Double,
    val protectedResidualBudgetPerThousandCommandsMeters: Double,
    val iterationSavingsPercent: Double,
    val interpretation: String
)

data class OneMicronIkTrainingResult(
    val runId: String,
    val config: OneMicronIkTrainingConfig,
    val model: LocalIkRegressionModel,
    val normalization: FeatureNormalization,
    val featureNames: List<String>,
    val epochs: List<OneMicronIkEpochMetrics>,
    val bestEpoch: Int,
    val trainRows: Int,
    val validationRows: Int,
    val testRows: Int,
    val skippedRows: Int,
    val verification: OneMicronIkVerificationMetrics,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val modelPath: String = "",
    val reportPath: String = ""
)

data class StoredOneMicronIkModel(
    val runId: String,
    val profile: OneMicronIkFeatureProfile,
    val solverConfig: IKConfig,
    val featureNames: List<String>,
    val normalization: FeatureNormalization,
    val model: LocalIkRegressionModel
) {
    init {
        require(runId.isNotBlank())
        val completeNames = OneMicronIkFeatureEncoder.featureNames(profile)
        require(featureNames.isNotEmpty() && featureNames.all(completeNames::contains)) {
            "Stored model variables must be an exact subset of its declared source profile."
        }
        require(featureNames.distinct().size == featureNames.size)
        require(model.inputFeatureCount == featureNames.size)
        require(model.outputCount == ONE_MICRON_MAX_JOINTS)
        require(normalization.means.size == featureNames.size)
        require(solverConfig.maxIterations > 0)
        require(solverConfig.tolerance.isFinite() && solverConfig.tolerance > 0.0)
        require(solverConfig.damping.isFinite() && solverConfig.damping > 0.0)
        require(solverConfig.maxStep.isFinite() && solverConfig.maxStep > 0.0)
    }
}

enum class VerifiedIkPath {
    NEURAL_DIRECT,
    NEURAL_REFINED,
    DETERMINISTIC_FALLBACK,
    FAILED
}

data class VerifiedOneMicronIkResult(
    val state: RobotState,
    val path: VerifiedIkPath,
    val verified: Boolean,
    val finalErrorMeters: Double,
    val neuralErrorMeters: Double,
    val refinementIterations: Int,
    val fallbackIterations: Int,
    val inferenceNanos: Long,
    val message: String
)

data class OneMicronIkLoadProgress(
    val rowsRead: Int,
    val rowsAccepted: Int,
    val rowsSkipped: Int,
    val message: String
)

fun oneMicronSolverConfig(): IKConfig =
    IKConfig(
        maxIterations = 800,
        tolerance = ONE_MICRON_METERS,
        damping = 0.01,
        maxStep = 0.02
    )
