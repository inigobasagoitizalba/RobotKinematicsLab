package com.robotkinematicslab.mobile.ml.comparison

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.storage.StoredTrainingIteration
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary

enum class ComparisonLoadPhase {
    LOADING_BASELINE,
    LOADING_CONTEXT,
    REBUILDING_TEST_SPLIT,
    READING_VISUAL_EVIDENCE,
    RUNNING_INFERENCE,
    COMPLETED
}

data class ModelComparisonLoadProgress(
    val phase: ComparisonLoadPhase,
    val completedWorkUnits: Int,
    val totalWorkUnits: Int,
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

data class ModelPointPrediction(
    val predictedLabel: TrainingLabel,
    val probabilities: List<Double>,
    val confidence: Double,
    val probabilityAssignedToTruth: Double,
    val correct: Boolean
)

data class ModelComparisonPoint(
    val sourceRowIndex: Long,
    val globalRowIndex: Long,
    val robotId: String,
    val robot: RobotDefinition,
    val seedState: RobotState,
    val solutionState: RobotState,
    val target: Vec3,
    val targetClass: String,
    val oracleLabel: TrainingLabel,
    val oracleStatus: String,
    val oracleDetailCode: String,
    val finalErrorMeters: Double,
    val iterations: Int,
    val solveDurationNanos: Long,
    val baselinePrediction: ModelPointPrediction,
    val contextPrediction: ModelPointPrediction
) {
    val modelsDisagree: Boolean
        get() = baselinePrediction.predictedLabel != contextPrediction.predictedLabel
}

data class ModelComparisonAggregate(
    val displayedPointCount: Int,
    val heldOutPointCount: Int,
    val baselineAccuracy: Double,
    val contextAccuracy: Double,
    val disagreementRate: Double,
    val contextOnlyCorrectCount: Int,
    val baselineOnlyCorrectCount: Int,
    val bothCorrectCount: Int,
    val bothWrongCount: Int
)

data class ModelComparisonSession(
    val run: TrainingRunSummary,
    val points: List<ModelComparisonPoint>,
    val aggregate: ModelComparisonAggregate,
    val history: List<StoredTrainingIteration>,
    val baselineLabel: String = "Baseline",
    val contextLabel: String = "Context",
    val baselineSelectionId: String = "baseline_kinematics",
    val contextSelectionId: String = "context_enhanced",
    val leftCandidateId:String? = null,
    val rightCandidateId:String? = null,
    val leftTestMetrics: com.robotkinematicslab.mobile.ml.training.ClassificationMetrics? = null,
    val rightTestMetrics: com.robotkinematicslab.mobile.ml.training.ClassificationMetrics? = null
)

enum class ComparisonPointFilter(val displayName: String) {
    ALL("All held-out points"),
    DISAGREEMENTS("Models disagree"),
    CONTEXT_WINS("Right model correct only"),
    BASELINE_WINS("Left model correct only"),
    BOTH_WRONG("Both wrong")
}

fun List<ModelComparisonPoint>.filteredBy(filter: ComparisonPointFilter): List<ModelComparisonPoint> =
    when (filter) {
        ComparisonPointFilter.ALL -> this
        ComparisonPointFilter.DISAGREEMENTS -> filter(ModelComparisonPoint::modelsDisagree)
        ComparisonPointFilter.CONTEXT_WINS -> filter { it.contextPrediction.correct && !it.baselinePrediction.correct }
        ComparisonPointFilter.BASELINE_WINS -> filter { it.baselinePrediction.correct && !it.contextPrediction.correct }
        ComparisonPointFilter.BOTH_WRONG -> filter { !it.baselinePrediction.correct && !it.contextPrediction.correct }
    }
