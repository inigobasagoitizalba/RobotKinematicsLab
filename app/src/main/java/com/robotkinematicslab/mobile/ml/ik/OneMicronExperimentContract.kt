package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetScientificContract
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy

/** Typed, reviewable input to the Verified IK preflight. UI preview and execution share this object. */
data class OneMicronExperimentRequest(
    val runName: String,
    val datasetPath: String,
    val datasetScientificFingerprint: String?,
    val datasetRowCount: Long,
    val datasetRobotIds: List<String>,
    val featureSelections: List<OneMicronFeatureSelectionSpec>,
    val splitStrategy: TrainingSplitStrategy,
    val requestedRows: Int,
    val epochs: Int,
    val batchSize: Int,
    val hiddenUnits: Int,
    val learningRate: Double,
    val randomSeed: Int,
    val verificationSampleLimit: Int,
    val workerCount: Int,
    val workingMemoryBudgetBytes: Long,
    val solverConfig: IKConfig
)

data class OneMicronExperimentPlan(
    val request: OneMicronExperimentRequest,
    /** Common cap used by every arm. This keeps the comparison controlled. */
    val effectiveRows: Int,
    val armMemoryPlans: List<OneMicronIkEffectiveMemoryPlan>
) {
    init {
        require(effectiveRows >= 30)
        require(armMemoryPlans.size == request.featureSelections.size)
        require(armMemoryPlans.all { it.effectiveRows == effectiveRows && it.isRunnable })
    }

    val wasCapped: Boolean
        get() = effectiveRows < request.requestedRows

    fun trainingConfigs(): List<OneMicronIkTrainingConfig> =
        request.featureSelections.map { selection ->
            OneMicronIkTrainingConfig(
                runName = "${request.runName.trim()}-${selection.id}",
                datasetPath = request.datasetPath,
                profile = selection.sourceProfile,
                maximumRows = effectiveRows,
                requestedMaximumRows = request.requestedRows,
                sourceDatasetRows = request.datasetRowCount,
                datasetScientificFingerprint = request.datasetScientificFingerprint,
                datasetRobotIds = request.datasetRobotIds,
                epochs = request.epochs,
                batchSize = request.batchSize,
                learningRate = request.learningRate,
                hiddenUnits = request.hiddenUnits,
                workerCount = request.workerCount,
                randomSeed = request.randomSeed,
                splitStrategy = request.splitStrategy,
                verificationSampleLimit = request.verificationSampleLimit,
                maximumWorkingMemoryBytes = request.workingMemoryBudgetBytes,
                solverConfig = request.solverConfig,
                featureSelection = selection
            )
        }
}

object OneMicronExperimentPlanner {
    private const val MAXIMUM_ROWS = 1_000_000

    fun plan(request: OneMicronExperimentRequest): OneMicronExperimentPlan {
        validate(request)
        val availableRows = request.datasetRowCount.coerceAtMost(MAXIMUM_ROWS.toLong()).toInt()
        val requestedAvailableRows = minOf(request.requestedRows, availableRows)
        val individualPlans =
            request.featureSelections.map { selection ->
                OneMicronIkMemoryEstimator.effectivePlan(
                    requestedRows = requestedAvailableRows,
                    featureCount = selection.featureCount,
                    hiddenUnits = request.hiddenUnits,
                    workerCount = request.workerCount,
                    batchSize = request.batchSize,
                    budgetBytes = request.workingMemoryBudgetBytes
                )
            }
        val commonRows = individualPlans.minOf(OneMicronIkEffectiveMemoryPlan::effectiveRows)
        require(commonRows >= 30) {
            "The model, optimizer and active worker buffers leave room for fewer than 30 certified rows."
        }
        val commonPlans =
            request.featureSelections.map { selection ->
                OneMicronIkEffectiveMemoryPlan(
                    requestedRows = request.requestedRows,
                    effectiveRows = commonRows,
                    featureCount = selection.featureCount,
                    hiddenUnits = request.hiddenUnits,
                    workerCount = request.workerCount,
                    batchSize = request.batchSize,
                    budgetBytes = request.workingMemoryBudgetBytes,
                    estimatedBytes =
                        OneMicronIkMemoryEstimator.estimatedTrainingWorkingSetBytes(
                            featureCount = selection.featureCount,
                            rows = commonRows,
                            hiddenUnits = request.hiddenUnits,
                            workerCount = request.workerCount,
                            batchSize = request.batchSize
                        )
                )
            }
        return OneMicronExperimentPlan(request, commonRows, commonPlans)
    }

    /** Smallest supported changes first; callers must show and explicitly apply every changed field. */
    fun recommendedSafePlan(request: OneMicronExperimentRequest): OneMicronExperimentPlan? {
        val candidates =
            listOf(
                request,
                request.copy(batchSize = 1),
                request.copy(hiddenUnits = 4),
                request.copy(hiddenUnits = 4, batchSize = 1)
            ).distinct()
        return candidates.firstNotNullOfOrNull { candidate -> runCatching { plan(candidate) }.getOrNull() }
    }

    private fun validate(request: OneMicronExperimentRequest) {
        require(request.runName.isNotBlank()) { "Enter a run name." }
        require(request.datasetPath.isNotBlank()) { "Select a compatible 1 µm dataset." }
        require(request.datasetRowCount >= 30L) { "The selected dataset has fewer than 30 rows." }
        require(request.datasetRobotIds.size >= 3 && request.datasetRobotIds.distinct().size == request.datasetRobotIds.size) {
            "Verified IK requires at least three distinct robot definitions."
        }
        request.datasetScientificFingerprint?.let { fingerprint ->
            require(DatasetScientificContract.isValidFingerprint(fingerprint)) {
                "The selected dataset scientific fingerprint is invalid."
            }
        }
        require(request.featureSelections.isNotEmpty()) { "Select at least one neural-IK feature configuration." }
        require(request.featureSelections.map(OneMicronFeatureSelectionSpec::id).distinct().size == request.featureSelections.size) {
            "Feature configuration identifiers must be distinct."
        }
        request.featureSelections.forEach { selection ->
            val sourceNames = OneMicronIkFeatureEncoder.featureNames(selection.sourceProfile)
            require(selection.includedFeatureNames.all(sourceNames::contains)) {
                "Feature configuration '${selection.displayName}' contains a variable outside ${selection.sourceProfile.displayName}."
            }
        }
        require(request.requestedRows in 30..MAXIMUM_ROWS) { "Rows must be between 30 and $MAXIMUM_ROWS." }
        require(request.epochs in 1..1_000) { "Epochs must be between 1 and 1,000." }
        require(request.batchSize in 1..100_000) { "Batch size must be between 1 and 100,000." }
        require(request.hiddenUnits in 4..1_024) { "Hidden units must be between 4 and 1,024." }
        require(request.learningRate.isFinite() && request.learningRate in 1e-6..1.0) {
            "Learning rate must be finite and between 0.000001 and 1."
        }
        require(request.verificationSampleLimit > 0) { "Untouched cases to verify must be positive." }
        require(request.workerCount in 1..256) { "The effective worker count is outside the supported range." }
        require(request.workingMemoryBudgetBytes > 0L) { "The working-memory budget must be positive." }
        require(request.solverConfig.maxIterations > 0)
        require(
            request.solverConfig.tolerance.isFinite() &&
                request.solverConfig.tolerance > 0.0 &&
                request.solverConfig.tolerance <= ONE_MICRON_METERS
        ) { "The verification solver tolerance must be 1e-6 metres or stricter." }
        require(request.solverConfig.damping.isFinite() && request.solverConfig.damping > 0.0)
        require(request.solverConfig.maxStep.isFinite() && request.solverConfig.maxStep > 0.0)
    }
}

/** Uses unrounded metres. Formatting must never decide scientific pass/fail. */
object OneMicronVerificationCriterion {
    fun acceptsPositionResidual(errorMeters: Double, toleranceMeters: Double = ONE_MICRON_METERS): Boolean {
        require(toleranceMeters.isFinite() && toleranceMeters > 0.0)
        return errorMeters.isFinite() && errorMeters >= 0.0 && errorMeters <= toleranceMeters
    }
}
