package com.robotkinematicslab.mobile.ml.comparison

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetPreparer
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.project
import com.robotkinematicslab.mobile.ml.storage.StoredLocalModel
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.validation.input.RobotStateValidator
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import com.robotkinematicslab.mobile.ml.data.ClassifierFeatureNormalizer
import java.io.File

class ModelComparisonLoader(
    private val trainingStorage: TrainingStorageRepository,
    private val trainingReader: ScientificDatasetTrainingReader = ScientificDatasetTrainingReader(),
    private val datasetPreparer: TrainingDatasetPreparer = TrainingDatasetPreparer()
) {

    fun load(
        run: TrainingRunSummary,
        leftModelPath: String? = null,
        rightModelPath: String? = null,
        maximumVisualPoints: Int = DEFAULT_MAXIMUM_VISUAL_POINTS,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (ModelComparisonLoadProgress) -> Unit = {},
        expectedEligibility: ComparisonEligibility? = null
    ): ModelComparisonSession {
        require(maximumVisualPoints in 1..MAXIMUM_ALLOWED_VISUAL_POINTS)
        require(run.maximumRows > 0)
        checkCancellation(cancellationRequested)
        val totalWork = safeTotalWork(run.maximumRows)
        report(onProgress,ComparisonLoadPhase.LOADING_BASELINE,0,totalWork,"Verifying both original inference contracts and test populations.")
        val pair = ComparisonReplayContract.rebuild(trainingStorage,run,leftModelPath,rightModelPath,cancellationRequested)
        require(expectedEligibility == null || eligibility(pair) == expectedEligibility) { "Model evidence changed after validation. Validate the selections again." }
        val baselineModel = pair.left.model
        val contextModel = pair.right.model
        val datasetFile = File(run.datasetPath)
        val eligible = pair.left.samples.zip(pair.right.samples)
        report(onProgress,ComparisonLoadPhase.REBUILDING_TEST_SPLIT,run.maximumRows * 2,totalWork,"Both complete held-out sets have identical rows, order, labels and grouping identities.")
        val selected = evenlySample(eligible, maximumVisualPoints)
        val neededRows = selected.mapTo(hashSetOf()) { it.first.sourceRowIndex }

        report(
            onProgress,
            ComparisonLoadPhase.READING_VISUAL_EVIDENCE,
            run.maximumRows * 2 + 1,
            totalWork,
            "Reading robot poses and deterministic solver evidence for selected points."
        )
        val rawRows = readRawRows(datasetFile, neededRows, cancellationRequested)
        val points = ArrayList<ModelComparisonPoint>(selected.size)
        selected.forEachIndexed { index, (baselineSample, contextSample) ->
            checkCancellation(cancellationRequested)
            val raw = requireNotNull(rawRows[baselineSample.sourceRowIndex]) { "A selected test row has no valid physical visualization evidence." }
            if (baselineSample.labelIndex != contextSample.labelIndex || baselineSample.labelIndex != raw.oracleLabel.ordinal) {
                error("The raw reference label changed during paired comparison.")
            }
            points +=
                raw.toComparisonPoint(
                    baselinePrediction = predict(baselineModel, baselineSample),
                    contextPrediction = predict(contextModel, contextSample)
                )
            if (index == 0 || index % PROGRESS_INTERVAL == 0 || index == selected.lastIndex) {
                report(
                    onProgress,
                    ComparisonLoadPhase.RUNNING_INFERENCE,
                    run.maximumRows * 2 + 2 + index,
                    totalWork,
                    "Running both stored models on the same held-out points."
                )
            }
        }
        require(points.size == selected.size && points.isNotEmpty()) { "The paired visual sample is incomplete." }
        requireNotNull(baselineModel.inferenceContract).verifyCorpus(datasetFile) { checkCancellation(cancellationRequested) }
        checkCancellation(cancellationRequested)
        val leftMetrics = evaluateTest(pair.left,run)
        checkCancellation(cancellationRequested)
        val rightMetrics = evaluateTest(pair.right,run)
        checkCancellation(cancellationRequested)
        val history = trainingStorage.loadIterationHistory(run)
        val aggregate = aggregate(points, eligible.size)
        report(
            onProgress,
            ComparisonLoadPhase.COMPLETED,
            totalWork,
            totalWork,
            "Visual comparison is ready."
        )
        return ModelComparisonSession(
            run, points, aggregate, history,
            baselineLabel = "${baselineModel.featureSelectionName} · ${baselineModel.featureNames.size}",
            contextLabel = "${contextModel.featureSelectionName} · ${contextModel.featureNames.size}",
            baselineSelectionId = baselineModel.featureSelectionId,
            contextSelectionId = contextModel.featureSelectionId,
            leftCandidateId=baselineModel.candidateId,rightCandidateId=contextModel.candidateId,
            leftTestMetrics=leftMetrics,rightTestMetrics=rightMetrics
        )
    }

    fun validateSelection(run:TrainingRunSummary,leftModelPath:String?,rightModelPath:String?,cancellationRequested:()->Boolean = { false }):ComparisonEligibility {
        require(leftModelPath != null) { "Choose a left model." }
        require(rightModelPath != null) { "Choose a right model." }
        val pair=ComparisonReplayContract.rebuild(trainingStorage,run,leftModelPath,rightModelPath,cancellationRequested)
        return eligibility(pair)
    }

    private fun evaluateTest(test:VerifiedModelTest,run:TrainingRunSummary):com.robotkinematicslab.mobile.ml.training.ClassificationMetrics {
        val model=test.model
        val source=com.robotkinematicslab.mobile.ml.data.TrainingDataset(run.datasetPath,model.profile,model.featureNames,test.samples,0,0)
        val indices=IntArray(test.samples.size) { it }
        val split=com.robotkinematicslab.mobile.ml.data.TrainingDatasetSplit(intArrayOf(),intArrayOf(),indices,true,run.splitStrategy)
        val prepared=com.robotkinematicslab.mobile.ml.data.PreparedTrainingDataset(source,split,
            Array(test.samples.size) { ClassifierFeatureNormalizer.normalize(test.samples[it].features,model.normalization) },
            model.normalization,FloatArray(model.model.classCount) { 1f })
        return com.robotkinematicslab.mobile.ml.training.ClassificationEvaluator.evaluate(model.model,prepared,indices,measureInference=true)
    }

    private fun eligibility(pair:VerifiedComparisonPair) = ComparisonEligibility(pair.left.samples.size,ComparisonReplayContract.label(pair.left.model),ComparisonReplayContract.label(pair.right.model),requireNotNull(pair.left.model.inferenceContract).modelSha256,requireNotNull(pair.right.model.inferenceContract).modelSha256)

    private fun validateModelSchema(model: StoredLocalModel, featureNames: List<String>) {
        require(model.featureNames == featureNames) {
            "Stored ${model.profile.displayName} model does not match the current feature schema."
        }
        require(model.normalization.means.size == featureNames.size)
        require(model.normalization.standardDeviations.size == featureNames.size)
    }

    private fun predict(model: StoredLocalModel, sample: EncodedTrainingSample): ModelPointPrediction {
        val normalized = ClassifierFeatureNormalizer.normalize(sample.features, model.normalization)
        val probabilities = model.model.probabilities(normalized).map(Float::toDouble)
        require(probabilities.all(Double::isFinite))
        val predictedIndex = probabilities.indices.maxByOrNull(probabilities::get) ?: 0
        val truth = TrainingLabel.entries[sample.labelIndex]
        return ModelPointPrediction(
            predictedLabel = TrainingLabel.entries[predictedIndex],
            probabilities = probabilities,
            confidence = probabilities[predictedIndex],
            probabilityAssignedToTruth = probabilities[sample.labelIndex],
            correct = predictedIndex == sample.labelIndex
        )
    }

    private fun readRawRows(
        file: File,
        neededSourceRows: Set<Long>,
        cancellationRequested: () -> Boolean
    ): Map<Long, RawVisualRow> {
        val result = HashMap<Long, RawVisualRow>(neededSourceRows.size)
        file.bufferedReader().use { reader ->
            val header = ScientificDatasetTrainingReader.parseCsvLine(reader.readLine() ?: error("Dataset is empty."))
            val column = header.withIndex().associate { it.value to it.index }
            var sourceRow = 0L
            while (result.size < neededSourceRows.size) {
                checkCancellation(cancellationRequested)
                val line = reader.readLine() ?: break
                sourceRow++
                if (sourceRow !in neededSourceRows) continue
                val values = runCatching { ScientificDatasetTrainingReader.parseCsvLine(line) }.getOrNull() ?: continue
                parseRawRow(values, column, sourceRow)?.let { result[sourceRow] = it }
            }
        }
        return result
    }

    private fun parseRawRow(
        values: List<String>,
        column: Map<String, Int>,
        sourceRowIndex: Long
    ): RawVisualRow? = runCatching {
        fun text(name: String): String = values[column.getValue(name)]
        fun doubles(name: String): List<Double> =
            text(name).split(';').filter(String::isNotBlank).map(String::toDouble).also { list ->
                require(list.all(Double::isFinite))
            }

        val jointTypes = text("jointTypes").split(';').map(JointType::valueOf)
        val theta = doubles("dhThetaRad")
        val d = doubles("dhDMeters")
        val a = doubles("dhAMeters")
        val alpha = doubles("dhAlphaRad")
        val minimums = doubles("jointMinValues")
        val maximums = doubles("jointMaxValues")
        val homes = doubles("jointHomeValues")
        val seeds = doubles("seedJointValues")
        val solutions = doubles("solutionJointValues")
        val jointCount = text("jointCount").toInt()
        require(listOf(jointTypes, theta, d, a, alpha, minimums, maximums, homes, seeds, solutions).all { it.size == jointCount })
        val robot =
            RobotDefinition(
                name = text("robotName"),
                dhParameters =
                    List(jointCount) { index -> DHParameter(theta[index], d[index], a[index], alpha[index]) },
                joints =
                    List(jointCount) { index ->
                        JointDefinition(
                            name = "J${index + 1}",
                            type = jointTypes[index],
                            minValue = minimums[index],
                            maxValue = maximums[index],
                            homeValue = homes[index]
                        )
                    }
            )
        val seedState = RobotState(seeds)
        val solutionState = RobotState(solutions)
        require(RobotDefinitionValidator().validate(robot).isValid)
        require(RobotStateValidator().validate(robot, seedState).isValid)
        require(RobotStateValidator().validate(robot, solutionState).isValid)
        val target = Vec3(text("targetX").toDouble(), text("targetY").toDouble(), text("targetZ").toDouble())
        require(target.x.isFinite() && target.y.isFinite() && target.z.isFinite())
        val oracleLabel = TrainingLabel.valueOf(text("acceptanceClass"))
        RawVisualRow(
            sourceRowIndex = sourceRowIndex,
            globalRowIndex = text("globalRowIndex").toLong().also { require(it >= 0L) },
            robotId = text("robotId").also { require(it.isNotBlank()) },
            robot = robot,
            seedState = seedState,
            solutionState = solutionState,
            target = target,
            targetClass = text("targetClass"),
            oracleLabel = oracleLabel,
            oracleStatus = text("status"),
            oracleDetailCode = text("detailCode"),
            finalErrorMeters = text("finalError").toDouble().also { require(!it.isInfinite() && (it.isNaN() || it >= 0.0)) },
            iterations = text("iterations").toInt().also { require(it >= 0) },
            solveDurationNanos = text("solveDurationNanos").toLong().also { require(it >= 0L) }
        )
    }.getOrNull()

    private fun RawVisualRow.toComparisonPoint(
        baselinePrediction: ModelPointPrediction,
        contextPrediction: ModelPointPrediction
    ): ModelComparisonPoint =
        ModelComparisonPoint(
            sourceRowIndex = sourceRowIndex,
            globalRowIndex = globalRowIndex,
            robotId = robotId,
            robot = robot,
            seedState = seedState,
            solutionState = solutionState,
            target = target,
            targetClass = targetClass,
            oracleLabel = oracleLabel,
            oracleStatus = oracleStatus,
            oracleDetailCode = oracleDetailCode,
            finalErrorMeters = finalErrorMeters,
            iterations = iterations,
            solveDurationNanos = solveDurationNanos,
            baselinePrediction = baselinePrediction,
            contextPrediction = contextPrediction
        )

    private fun aggregate(points: List<ModelComparisonPoint>, heldOutPointCount: Int): ModelComparisonAggregate {
        val count = points.size.toDouble()
        return ModelComparisonAggregate(
            displayedPointCount = points.size,
            heldOutPointCount = heldOutPointCount,
            baselineAccuracy = points.count { it.baselinePrediction.correct } / count,
            contextAccuracy = points.count { it.contextPrediction.correct } / count,
            disagreementRate = points.count(ModelComparisonPoint::modelsDisagree) / count,
            contextOnlyCorrectCount = points.count { it.contextPrediction.correct && !it.baselinePrediction.correct },
            baselineOnlyCorrectCount = points.count { it.baselinePrediction.correct && !it.contextPrediction.correct },
            bothCorrectCount = points.count { it.baselinePrediction.correct && it.contextPrediction.correct },
            bothWrongCount = points.count { !it.baselinePrediction.correct && !it.contextPrediction.correct }
        )
    }

    private fun <T> evenlySample(values: List<T>, maximum: Int): List<T> {
        if (values.size <= maximum) return values
        if (maximum == 1) return listOf(values.first())
        return List(maximum) { index ->
            values[index.toLong().times(values.lastIndex).div(maximum - 1L).toInt()]
        }
    }

    private fun safeTotalWork(maximumRows: Int): Int =
        (maximumRows.toLong() * 2L + MAXIMUM_ALLOWED_VISUAL_POINTS + 2L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    private fun report(
        callback: (ModelComparisonLoadProgress) -> Unit,
        phase: ComparisonLoadPhase,
        completed: Int,
        total: Int,
        message: String
    ) {
        callback(
            ModelComparisonLoadProgress(
                phase = phase,
                completedWorkUnits = completed.coerceIn(0, total),
                totalWorkUnits = total,
                message = message
            )
        )
    }

    private fun checkCancellation(cancellationRequested: () -> Boolean) {
        if (cancellationRequested()) throw ModelComparisonCancelledException()
    }

    private data class RawVisualRow(
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
        val solveDurationNanos: Long
    )

    companion object {
        const val DEFAULT_MAXIMUM_VISUAL_POINTS = 5_000
        const val MAXIMUM_ALLOWED_VISUAL_POINTS = 20_000
        private const val PROGRESS_INTERVAL = 250
    }
}

class ModelComparisonCancelledException : RuntimeException("Visual comparison cancelled.")
