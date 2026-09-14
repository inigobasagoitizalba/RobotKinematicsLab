package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol

data class SavedRobot(
    val id: String,
    val robot: RobotDefinition
)

enum class DatasetTargetMode {
    MIXED,
    FK_PROVEN_REACHABLE,
    GUARANTEED_UNREACHABLE
}

enum class DatasetFilterMode {
    ALL,
    ACCEPTED_ONLY,
    REJECTED_ONLY
}

enum class DatasetAcceptanceClass {
    ACCEPTED,
    UNCERTAIN,
    REJECTED
}

data class DatasetGenerationConfig(
    val datasetName: String,
    val robots: List<SavedRobot>,
    val samplesPerRobot: Int,
    val randomSeed: Int,
    val targetMode: DatasetTargetMode,
    val reachableFraction: Double,
    val filterMode: DatasetFilterMode,
    val append: Boolean,
    val maxAttemptsMultiplier: Int = 30,
    val ikConfig: IKConfig = IKConfig(),
    val metricPolicy: DiagnosticMetricPolicy = DiagnosticMetricPolicy()
)

data class DatasetGenerationProgress(
    val requestedRows: Int,
    val addedRows: Int,
    val attempts: Int,
    val currentRobotIndex: Int,
    val totalRobots: Int,
    val currentRobotName: String,
    val message: String
) {
    val fraction: Float
        get() =
            if (requestedRows <= 0) {
                0f
            } else {
                (addedRows.toDouble() / requestedRows.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }
}

data class DatasetGenerationResult(
    val csvPath: String,
    val addedRows: Int,
    val totalRows: Long,
    val attempts: Int,
    val completed: Boolean,
    val cancelled: Boolean,
    val message: String
)

data class DatasetManifest(
    val datasetName: String,
    val csvPath: String,
    val rowCount: Long,
    val generationCount: Int,
    val robotIds: List<String>,
    val samplesPerRobotLastRun: Int,
    val randomSeed: Int,
    val targetMode: DatasetTargetMode,
    val reachableFraction: Double,
    val filterMode: DatasetFilterMode,
    val lastUpdatedEpochMillis: Long,
    val ikConfig: IKConfig = IKConfig(),
    val metricPolicy: DiagnosticMetricPolicy = DiagnosticMetricPolicy(),
    val randomProtocol: String = ScientificRandomProtocol.ID,
    val batches: List<DatasetGenerationBatch> = emptyList(),
    val scientificFingerprint: String? = null
) {
    init {
        require(scientificFingerprint == null || DatasetScientificContract.isValidFingerprint(scientificFingerprint))
        require(datasetName.isNotBlank())
        require(csvPath.isNotBlank())
        require(rowCount >= 0L)
        require(generationCount > 0)
        require(robotIds.isNotEmpty() && robotIds.all(String::isNotBlank) && robotIds.distinct().size == robotIds.size)
        require(samplesPerRobotLastRun > 0)
        require(reachableFraction.isFinite() && reachableFraction in 0.0..1.0)
        require(lastUpdatedEpochMillis >= 0L)
        require(ikConfig.maxIterations > 0)
        require(ikConfig.tolerance.isFinite() && ikConfig.tolerance > 0.0)
        require(ikConfig.damping.isFinite() && ikConfig.damping > 0.0)
        require(ikConfig.maxStep.isFinite() && ikConfig.maxStep > 0.0)
        require(randomProtocol.isNotBlank())
        require(batches.map(DatasetGenerationBatch::generationIndex).distinct().size == batches.size)
        require(batches.all { it.rowStart >= 0L && it.rowCount > 0L })
    }

    val hasCompleteBatchProvenance: Boolean
        get() =
            (generationCount == 1 && batches.isEmpty()) ||
                (batches.size == generationCount &&
                batches.sortedBy(DatasetGenerationBatch::rowStart)
                    .fold(0L) { expectedStart, batch ->
                        if (batch.rowStart != expectedStart) Long.MIN_VALUE
                        else expectedStart + batch.rowCount
                    } == rowCount)
}

data class DatasetGenerationBatch(
    val generationIndex: Int,
    val batchId: String,
    val rowStart: Long,
    val rowCount: Long,
    val robotIds: List<String>,
    val samplesPerRobot: Int,
    val randomSeed: Int,
    val targetMode: DatasetTargetMode,
    val reachableFraction: Double,
    val filterMode: DatasetFilterMode,
    val createdAtEpochMillis: Long,
    val ikConfig: IKConfig,
    val metricPolicy: DiagnosticMetricPolicy,
    val randomProtocol: String = ScientificRandomProtocol.ID
) {
    init {
        require(generationIndex >= 0)
        require(batchId.isNotBlank())
        require(rowStart >= 0L)
        require(rowCount > 0L)
        require(robotIds.isNotEmpty() && robotIds.all(String::isNotBlank))
        require(samplesPerRobot > 0)
        require(reachableFraction.isFinite() && reachableFraction in 0.0..1.0)
        require(createdAtEpochMillis >= 0L)
        require(randomProtocol.isNotBlank())
    }
}

/**
 * Converts the schema-supported single-generation legacy representation into an explicit first
 * batch before an append. This keeps the complete row provenance contiguous after the second run.
 */
fun DatasetManifest.provenanceBatchesForAppend(): List<DatasetGenerationBatch> {
    if (batches.isNotEmpty()) return batches
    if (generationCount != 1 || rowCount <= 0L) return emptyList()
    return listOf(
        DatasetGenerationBatch(
            generationIndex = 0,
            batchId = "legacy-generation-0",
            rowStart = 0L,
            rowCount = rowCount,
            robotIds = robotIds,
            samplesPerRobot = samplesPerRobotLastRun,
            randomSeed = randomSeed,
            targetMode = targetMode,
            reachableFraction = reachableFraction,
            filterMode = filterMode,
            createdAtEpochMillis = lastUpdatedEpochMillis,
            ikConfig = ikConfig,
            metricPolicy = metricPolicy,
            randomProtocol = randomProtocol
        )
    )
}

data class DatasetRow(
    val globalRowIndex: Long,
    val batchId: String,
    val baseRandomSeed: Int,
    val robotRandomSeed: Long,
    val robotId: String,
    val robot: RobotDefinition,
    val ikConfig: IKConfig,
    val metricPolicy: DiagnosticMetricPolicy,
    val sampleIndex: Int,
    val targetClass: String,
    val targetSamplingStrategy: String,
    val targetSourceJointValues: List<Double>?,
    val seedJointValues: List<Double>,
    val targetX: Double,
    val targetY: Double,
    val targetZ: Double,
    val solutionJointValues: List<Double>,
    val solverAccepted: Boolean,
    val acceptanceClass: String,
    val status: String,
    val detailCode: String,
    val converged: Boolean,
    val finalError: Double,
    val iterations: Int,
    val initialError: Double,
    val improvement: Double,
    val improvementRatio: Double,
    val progressClass: String,
    val seedDistanceBucket: String,
    val iterationSaturationRatio: Double,
    val jointDeltaNorm: Double,
    val maxSingleJointMovement: Double,
    val seedMinNormalizedLimitMargin: Double,
    val seedConditionNumber: Double,
    val seedLogConditionNumber: Double,
    val normalizedJointTravelRms: Double,
    val finalMinNormalizedLimitMargin: Double,
    val backtrackingRetryCount: Int,
    val solveDurationNanos: Long,
    val nearLimitJointCount: Int,
    val nearLimitJointNames: List<String>,
    val jointLimitPressureRatio: Double,
    val randomProtocol: String = ScientificRandomProtocol.ID
)
