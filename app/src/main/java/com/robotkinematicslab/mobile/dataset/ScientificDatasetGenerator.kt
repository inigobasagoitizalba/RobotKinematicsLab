package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKResult
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

private data class GeneratedTarget(
    val target: Vec3,
    val targetClass: String,
    val samplingStrategy: String,
    val sourceJointValues: List<Double>?
)

private data class GeneratedUnreachableTarget(
    val target: Vec3,
    val samplingStrategy: String
)

class ScientificDatasetGenerator(
    private val metricsCalculatorFactory: (DiagnosticMetricPolicy) -> DatasetRunMetricsCalculator =
        { policy -> DatasetRunMetricsCalculator(policy) },
    private val writerFactory: () -> ScientificDatasetCsvWriter = { ScientificDatasetCsvWriter() }
) {

    fun generate(
        config: DatasetGenerationConfig,
        csvFile: File,
        existingRowCount: Long,
        generationIndex: Int,
        cancellationRequested: AtomicBoolean = AtomicBoolean(false),
        onProgress: ((DatasetGenerationProgress) -> Unit)? = null,
        onCheckpoint: ((addedRows: Int, totalRows: Long) -> Unit)? = null,
        sampleIndexOffset: Int = 0,
        sampleCountPerRobot: Int = config.samplesPerRobot
    ): DatasetGenerationResult {
        require(config.datasetName.isNotBlank()) { "Dataset name must not be blank." }
        require(config.datasetName.length <= MAXIMUM_STORED_NAME_LENGTH) {
            "Dataset name must contain at most $MAXIMUM_STORED_NAME_LENGTH characters."
        }
        require(config.datasetName.none(Char::isISOControl)) {
            "Dataset name must not contain line breaks or control characters."
        }
        require(config.robots.isNotEmpty()) { "Select at least one robot." }
        require(
            config.robots.all {
                it.id.isNotBlank() &&
                    it.id.length <= MAXIMUM_STORED_NAME_LENGTH &&
                    it.id.none(Char::isISOControl)
            }
        ) { "Every dataset robot needs a bounded, control-character-free stable identifier." }
        require(config.robots.map(SavedRobot::id).distinct().size == config.robots.size) {
            "Dataset robot identifiers must be unique."
        }
        require(config.samplesPerRobot > 0) { "Samples per robot must be positive." }
        require(sampleIndexOffset >= 0) { "Sample index offset cannot be negative." }
        require(sampleCountPerRobot > 0) { "A generation slice must contain at least one sample." }
        require(sampleIndexOffset <= config.samplesPerRobot - sampleCountPerRobot) {
            "The generation slice must remain inside the configured samples-per-robot range."
        }
        require(existingRowCount >= 0L) { "Existing dataset row count cannot be negative." }
        require(generationIndex >= 0) { "Dataset generation index cannot be negative." }
        require(config.reachableFraction in 0.0..1.0) {
            "Reachable fraction must be between 0 and 1."
        }
        require(config.maxAttemptsMultiplier > 0) {
            "Attempt multiplier must be positive."
        }
        require(
            config.ikConfig.maxIterations > 0 &&
            config.ikConfig.tolerance.isFinite() && config.ikConfig.tolerance > 0.0 &&
            config.ikConfig.damping.isFinite() && config.ikConfig.damping > 0.0 &&
            config.ikConfig.maxStep.isFinite() && config.ikConfig.maxStep > 0.0
        ) { "IK configuration must contain finite positive values." }

        val robotValidator = RobotDefinitionValidator()
        config.robots.forEach { savedRobot ->
            val validation = robotValidator.validate(savedRobot.robot)
            require(validation.isValid) {
                "Robot ${savedRobot.id} is invalid: ${validation.issues.joinToString { it.message }}"
            }
        }

        val requestedRowsLong =
            sampleCountPerRobot.toLong() * config.robots.size.toLong()
        require(requestedRowsLong <= Int.MAX_VALUE) {
            "This run requests too many rows. Split it into append batches."
        }
        require(existingRowCount <= Long.MAX_VALUE - requestedRowsLong) {
            "Appending this batch would overflow the dataset row index."
        }

        val requestedRows = requestedRowsLong.toInt()
        if (isCancellationRequested(cancellationRequested)) {
            return DatasetGenerationResult(
                csvPath = csvFile.absolutePath,
                addedRows = 0,
                totalRows = existingRowCount,
                attempts = 0,
                completed = false,
                cancelled = true,
                message = "Dataset generation cancelled before writing started."
            )
        }
        val batchId = "batch-${generationIndex + 1}"
        val kinematicsService = KinematicsService(config.ikConfig)
        val metricsCalculator = metricsCalculatorFactory(config.metricPolicy)
        val writer = writerFactory()
        var addedRows = 0
        var totalAttempts = 0
        var stoppedByAttemptLimit = false

        writer.open(
            file = csvFile,
            append = config.append
        )

        try {
            config.robots.forEachIndexed { robotIndex, savedRobot ->
                if (isCancellationRequested(cancellationRequested)) {
                    return@forEachIndexed
                }

                val robotSeed =
                    ScientificRandomProtocol.deriveSeed(
                        config.randomSeed,
                        "dataset-robot",
                        "robot-id=${savedRobot.id}",
                        "generation=$generationIndex"
                    )
                val reachableSamples = reachableSamplePlan(config, savedRobot, robotSeed)
                val unreachableReachBound =
                    lazy(LazyThreadSafetyMode.NONE) {
                        RobotReachEnvelope.conservativeRadialUpperBound(savedRobot.robot)
                            .coerceAtLeast(MINIMUM_REACH_BOUND)
                    }
                var robotRows = 0
                var robotAttempts = 0

                publishProgress(
                    onProgress = onProgress,
                    requestedRows = requestedRows,
                    addedRows = addedRows,
                    attempts = totalAttempts,
                    robotIndex = robotIndex,
                    robotCount = config.robots.size,
                    robotName = savedRobot.robot.name,
                    message = "Generating rows for ${savedRobot.robot.name}."
                )

                val sliceEnd = sampleIndexOffset + sampleCountPerRobot
                for (sampleIndex in sampleIndexOffset until sliceEnd) {
                    if (isCancellationRequested(cancellationRequested) || stoppedByAttemptLimit) break

                    var acceptedSample: Triple<GeneratedTarget, RobotState, IKResult>? = null
                    var sampleAttempt = 0
                    while (
                        acceptedSample == null &&
                        sampleAttempt < config.maxAttemptsMultiplier &&
                        !isCancellationRequested(cancellationRequested)
                    ) {
                        val random =
                            ScientificRandom(
                                ScientificRandomProtocol.deriveSeed(
                                    config.randomSeed,
                                    "robot-seed=$robotSeed",
                                    "sample=$sampleIndex",
                                    "attempt=$sampleAttempt"
                                )
                            )
                        sampleAttempt += 1
                        robotAttempts += 1
                        totalAttempts += 1
                        val generatedTarget =
                            generateTarget(
                                reachable = reachableSamples[sampleIndex],
                                robot = savedRobot.robot,
                                random = random,
                                kinematicsService = kinematicsService,
                                unreachableReachBound = unreachableReachBound
                            ) ?: continue
                        val seedState = randomRobotState(savedRobot.robot, random)
                        val result =
                            kinematicsService.computeIK(
                                robot = savedRobot.robot,
                                initialState = seedState,
                                target = generatedTarget.target
                            )
                        if (passesFilter(config.filterMode, result)) {
                            acceptedSample = Triple(generatedTarget, seedState, result)
                        }
                    }

                    if (acceptedSample == null) {
                        if (!isCancellationRequested(cancellationRequested)) stoppedByAttemptLimit = true
                        break
                    }

                    val (generatedTarget, seedState, result) = acceptedSample

                    val metrics =
                        metricsCalculator.calculate(
                            kinematicsService = kinematicsService,
                            robot = savedRobot.robot,
                            seedState = seedState,
                            target = generatedTarget.target,
                            result = result,
                            maxIterations = config.ikConfig.maxIterations
                        )

                    writer.write(
                        DatasetRow(
                            globalRowIndex = existingRowCount + addedRows.toLong(),
                            batchId = batchId,
                            baseRandomSeed = config.randomSeed,
                            robotRandomSeed = robotSeed,
                            robotId = savedRobot.id,
                            robot = savedRobot.robot,
                            ikConfig = config.ikConfig,
                            metricPolicy = config.metricPolicy,
                            sampleIndex = sampleIndex,
                            targetClass = generatedTarget.targetClass,
                            targetSamplingStrategy = generatedTarget.samplingStrategy,
                            targetSourceJointValues = generatedTarget.sourceJointValues,
                            seedJointValues = seedState.jointValues,
                            targetX = generatedTarget.target.x,
                            targetY = generatedTarget.target.y,
                            targetZ = generatedTarget.target.z,
                            solutionJointValues = result.state.jointValues,
                            solverAccepted = isAccepted(result.status),
                            acceptanceClass = acceptanceClass(result.status).name,
                            status = result.status.name,
                            detailCode = result.detailCode.name,
                            converged = result.converged,
                            finalError = result.finalError,
                            iterations = result.iterations,
                            initialError = metrics.initialError,
                            improvement = metrics.improvement,
                            improvementRatio = metrics.improvementRatio,
                            progressClass = metrics.progressClass.name,
                            seedDistanceBucket = metrics.seedDistanceBucket.name,
                            iterationSaturationRatio = metrics.iterationSaturationRatio,
                            jointDeltaNorm = metrics.jointDeltaNorm,
                            maxSingleJointMovement = metrics.maxSingleJointMovement,
                            seedMinNormalizedLimitMargin =
                                metrics.seedMinNormalizedLimitMargin,
                            seedConditionNumber = result.diagnostics.seedConditionNumber,
                            seedLogConditionNumber = metrics.seedLogConditionNumber,
                            normalizedJointTravelRms = metrics.normalizedJointTravelRms,
                            finalMinNormalizedLimitMargin =
                                metrics.finalMinNormalizedLimitMargin,
                            backtrackingRetryCount = result.diagnostics.backtrackingRetryCount,
                            solveDurationNanos = result.diagnostics.solveDurationNanos,
                            nearLimitJointCount = metrics.nearLimitJointCount,
                            nearLimitJointNames = metrics.nearLimitJointNames,
                            jointLimitPressureRatio = metrics.jointLimitPressureRatio
                        )
                    )

                    robotRows += 1
                    addedRows += 1

                    if (addedRows % FLUSH_INTERVAL == 0) {
                        writer.flush()
                    }

                    if (addedRows == 1 || addedRows % CHECKPOINT_INTERVAL == 0) {
                        onCheckpoint?.invoke(
                            addedRows,
                            existingRowCount + addedRows.toLong()
                        )
                    }

                    if (addedRows % PROGRESS_INTERVAL == 0 || robotRows == sampleCountPerRobot) {
                        publishProgress(
                            onProgress = onProgress,
                            requestedRows = requestedRows,
                            addedRows = addedRows,
                            attempts = totalAttempts,
                            robotIndex = robotIndex,
                            robotCount = config.robots.size,
                            robotName = savedRobot.robot.name,
                            message = "$addedRows of $requestedRows rows written."
                        )
                    }
                }

                if (robotRows < sampleCountPerRobot && !isCancellationRequested(cancellationRequested)) {
                    stoppedByAttemptLimit = true
                }
            }
        } finally {
            writer.close()
        }

        val cancelled = isCancellationRequested(cancellationRequested)
        val completed = !cancelled && !stoppedByAttemptLimit && addedRows == requestedRows
        val message =
            when {
                cancelled -> "Generation cancelled. The rows already written are valid and were kept."
                stoppedByAttemptLimit ->
                    "Stopped at $addedRows rows after $totalAttempts attempts. The selected result filter was too restrictive."
                completed -> "Dataset generation completed."
                else -> "Dataset generation ended with $addedRows of $requestedRows requested rows."
            }

        publishProgress(
            onProgress = onProgress,
            requestedRows = requestedRows,
            addedRows = addedRows,
            attempts = totalAttempts,
            robotIndex = (config.robots.size - 1).coerceAtLeast(0),
            robotCount = config.robots.size,
            robotName = config.robots.last().robot.name,
            message = message
        )

        return DatasetGenerationResult(
            csvPath = csvFile.absolutePath,
            addedRows = addedRows,
            totalRows = existingRowCount + addedRows.toLong(),
            attempts = totalAttempts,
            completed = completed,
            cancelled = cancelled,
            message = message
        )
    }

    private fun isCancellationRequested(flag: AtomicBoolean): Boolean =
        flag.get() || Thread.currentThread().isInterrupted

    private fun generateTarget(
        reachable: Boolean,
        robot: RobotDefinition,
        random: ScientificRandom,
        kinematicsService: KinematicsService,
        unreachableReachBound: Lazy<Double>
    ): GeneratedTarget? {
        if (!reachable) {
            val unreachable = randomUnreachableTarget(unreachableReachBound.value, random)
            return GeneratedTarget(
                target = unreachable.target,
                targetClass = "GUARANTEED_UNREACHABLE",
                samplingStrategy = unreachable.samplingStrategy,
                sourceJointValues = null
            )
        }

        val sourceState = randomRobotState(robot, random)
        val fk =
            kinematicsService.computeFK(
                robot = robot,
                state = sourceState
            )

        if (fk.status != FKStatus.SUCCESS && fk.status != FKStatus.SUCCESS_WITH_WARNING) {
            return null
        }

        return GeneratedTarget(
            target = fk.endEffectorPosition,
            targetClass = "FK_PROVEN_REACHABLE",
            samplingStrategy = "UNIFORM_JOINT_SPACE_FK",
            sourceJointValues = sourceState.jointValues
        )
    }

    /**
     * Exact, shuffled target-class allocation shared by sequential and parallel slices. Each row
     * then owns an independent deterministic random stream, so worker scheduling cannot alter the
     * scientific sample or consume another row's random numbers after a rejected attempt.
     */
    private fun reachableSamplePlan(
        config: DatasetGenerationConfig,
        robot: SavedRobot,
        robotSeed: Long
    ): BooleanArray {
        val reachableCount =
            plannedReachableTargetCount(config.targetMode, config.reachableFraction, config.samplesPerRobot)
        if (reachableCount == 0) return BooleanArray(config.samplesPerRobot)
        if (reachableCount == config.samplesPerRobot) return BooleanArray(config.samplesPerRobot) { true }

        val indices = IntArray(config.samplesPerRobot) { it }
        val random =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(
                    config.randomSeed,
                    "robot-seed=$robotSeed",
                    "target-class-plan",
                    "robot-id=${robot.id}"
                )
            )
        for (index in indices.lastIndex downTo 1) {
            val swap = random.nextInt(index + 1)
            val temporary = indices[index]
            indices[index] = indices[swap]
            indices[swap] = temporary
        }
        return BooleanArray(config.samplesPerRobot).also { plan ->
            for (index in 0 until reachableCount) plan[indices[index]] = true
        }
    }

    private fun randomRobotState(
        robot: RobotDefinition,
        random: ScientificRandom
    ): RobotState {
        return RobotState(
            robot.joints.map { joint ->
                random.nextDouble(joint.minValue, joint.maxValue)
            }
        )
    }

    private fun randomUnreachableTarget(
        reachBound: Double,
        random: ScientificRandom
    ): GeneratedUnreachableTarget {
        val nearBoundary = random.nextInt(2) == 0
        val lowerFactor = if (nearBoundary) 1.01 else 1.15
        val upperFactor = if (nearBoundary) 1.10 else 1.70
        val radius = random.nextDouble(reachBound * lowerFactor, reachBound * upperFactor)
        val zDirection = random.nextDouble(-1.0, 1.0)
        val azimuth = random.nextDouble(-PI, PI)
        val planarScale = sqrt((1.0 - zDirection * zDirection).coerceAtLeast(0.0))

        return GeneratedUnreachableTarget(
            target = Vec3(
                x = radius * planarScale * cos(azimuth),
                y = radius * planarScale * sin(azimuth),
                z = radius * zDirection
            ),
            samplingStrategy =
                if (nearBoundary) {
                    "OUTSIDE_CONSERVATIVE_REACH_BOUND_NEAR"
                } else {
                    "OUTSIDE_CONSERVATIVE_REACH_BOUND_FAR"
                }
        )
    }

    private fun passesFilter(
        filterMode: DatasetFilterMode,
        result: IKResult
    ): Boolean {
        val accepted = isAccepted(result.status)

        return when (filterMode) {
            DatasetFilterMode.ALL -> true
            DatasetFilterMode.ACCEPTED_ONLY -> accepted
            DatasetFilterMode.REJECTED_ONLY -> !accepted
        }
    }

    private fun isAccepted(status: IKStatus): Boolean {
        return status == IKStatus.SUCCESS || status == IKStatus.SUCCESS_WITH_WARNING
    }

    private fun acceptanceClass(status: IKStatus): DatasetAcceptanceClass {
        return when (status) {
            IKStatus.SUCCESS -> DatasetAcceptanceClass.ACCEPTED
            IKStatus.SUCCESS_WITH_WARNING -> DatasetAcceptanceClass.UNCERTAIN
            else -> DatasetAcceptanceClass.REJECTED
        }
    }

    private fun publishProgress(
        onProgress: ((DatasetGenerationProgress) -> Unit)?,
        requestedRows: Int,
        addedRows: Int,
        attempts: Int,
        robotIndex: Int,
        robotCount: Int,
        robotName: String,
        message: String
    ) {
        onProgress?.invoke(
            DatasetGenerationProgress(
                requestedRows = requestedRows,
                addedRows = addedRows,
                attempts = attempts,
                currentRobotIndex = robotIndex,
                totalRobots = robotCount,
                currentRobotName = robotName,
                message = message
            )
        )
    }

    companion object {
        private const val MINIMUM_REACH_BOUND = 0.05
        private const val FLUSH_INTERVAL = 250
        private const val CHECKPOINT_INTERVAL = 1_000
        private const val PROGRESS_INTERVAL = 50
        private const val MAXIMUM_STORED_NAME_LENGTH = 160
    }
}

internal fun plannedReachableTargetCount(
    mode: DatasetTargetMode,
    reachableFraction: Double,
    sampleCount: Int
): Int {
    require(reachableFraction.isFinite() && reachableFraction in 0.0..1.0)
    require(sampleCount >= 0)
    return when (mode) {
        DatasetTargetMode.MIXED ->
            (sampleCount.toDouble() * reachableFraction)
                .roundToInt()
                .coerceIn(0, sampleCount)

        DatasetTargetMode.FK_PROVEN_REACHABLE -> sampleCount
        DatasetTargetMode.GUARANTEED_UNREACHABLE -> 0
    }
}
