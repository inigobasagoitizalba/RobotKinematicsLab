package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.RobotMorphologyFingerprint
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import java.io.File
import java.util.Random
import kotlin.math.hypot

class OneMicronIkDatasetReader(
    private val maximumSupportedJointCount: Int = ONE_MICRON_MAX_JOINTS,
    private val fkSolver: ForwardKinematicsSolver = ForwardKinematicsSolver()
) {

    private val robotValidator = RobotDefinitionValidator()

    fun load(
        file: File,
        profile: OneMicronIkFeatureProfile,
        maximumRows: Int,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (OneMicronIkLoadProgress) -> Unit = {},
        sampleAcrossEntireFile: Boolean = false,
        samplingSeed: Int = 0
    ): OneMicronIkDataset {
        require(file.isFile) { "Dataset CSV does not exist: ${file.absolutePath}" }
        require(maximumRows > 0)

        val robots = mutableListOf<RobotDefinition>()
        val robotIds = mutableListOf<String>()
        val robotIndexById = linkedMapOf<String, Int>()
        val samples = ArrayList<OneMicronIkSample>(maximumRows.coerceAtMost(100_000))
        var rowsRead = 0
        var skipped = 0
        var rejectedByContract = 0
        var acceptedRowsSeen = 0
        val samplingRandom = Random(samplingSeed.toLong())

        file.bufferedReader().use { reader ->
            val header = reader.readLine()?.let(ScientificDatasetTrainingReader::parseCsvLine)
                ?: error("Dataset CSV is empty.")
            val columns = header.withIndex().associate { it.value to it.index }
            val missing = REQUIRED_COLUMNS.filterNot(columns::containsKey)
            require(missing.isEmpty()) { "Dataset schema is missing: ${missing.joinToString()}" }

            while ((sampleAcrossEntireFile || samples.size < maximumRows) && !cancellationRequested()) {
                val line = reader.readLine() ?: break
                rowsRead++
                val values = runCatching { ScientificDatasetTrainingReader.parseCsvLine(line) }.getOrNull()
                if (values == null) {
                    skipped++
                    continue
                }
                val parsed = parseRow(values, columns, profile, rowsRead.toLong())
                when {
                    parsed == null -> skipped++
                    !parsed.contractSatisfied -> rejectedByContract++
                    else -> {
                        val knownIndex = robotIndexById[parsed.robotId]
                        val robotIndex =
                            if (knownIndex == null) {
                                robotIndexById[parsed.robotId] = robots.size
                                robotIds += parsed.robotId
                                robots += parsed.robot
                                robots.lastIndex
                            } else {
                                require(robots[knownIndex] == parsed.robot) {
                                    "Robot id ${parsed.robotId} changes definition inside the dataset."
                                }
                                knownIndex
                            }
                        val sample = parsed.sample.copy(robotIndex = robotIndex)
                        acceptedRowsSeen++
                        if (!sampleAcrossEntireFile || samples.size < maximumRows) {
                            samples += sample
                        } else {
                            val replacementIndex = samplingRandom.nextInt(acceptedRowsSeen)
                            if (replacementIndex < maximumRows) samples[replacementIndex] = sample
                        }
                    }
                }
                if (rowsRead == 1 || rowsRead % 2_000 == 0) {
                    onProgress(
                        OneMicronIkLoadProgress(
                            rowsRead,
                            samples.size,
                            skipped + rejectedByContract,
                            "Certifying successful rows against independent forward kinematics."
                        )
                    )
                }
            }
        }
        if (cancellationRequested()) throw OneMicronIkTrainingCancelledException()
        require(samples.size >= 30) {
            "At least 30 independently certified 1 µm rows are required; found ${samples.size}."
        }
        return OneMicronIkDataset(
            sourcePath = file.absolutePath,
            profile = profile,
            featureNames = OneMicronIkFeatureEncoder.featureNames(profile, maximumSupportedJointCount),
            robots = robots,
            robotIds = robotIds,
            samples = samples,
            rowsRead = rowsRead,
            skippedRows = skipped,
            rejectedByMicronContract = rejectedByContract
        )
    }

    private fun parseRow(
        values: List<String>,
        columns: Map<String, Int>,
        profile: OneMicronIkFeatureProfile,
        rowIndex: Long
    ): ParsedRow? {
        fun text(name: String): String? = columns[name]?.let(values::getOrNull)
        fun finite(name: String): Double? = text(name)?.toDoubleOrNull()?.takeIf(Double::isFinite)
        fun list(name: String): List<Double>? =
            text(name)?.split(';')?.filter(String::isNotBlank)
                ?.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }

        if (text("schemaVersion") != ScientificDatasetCsvWriter.SCHEMA_VERSION) return null
        val robotId = text("robotId")?.takeIf(String::isNotBlank) ?: return null
        val count = text("jointCount")?.toIntOrNull()?.takeIf { it in 1..maximumSupportedJointCount } ?: return null
        val types = text("jointTypes")?.split(';')?.map { runCatching { JointType.valueOf(it) }.getOrNull() ?: return null }
            ?: return null
        val theta = list("dhThetaRad") ?: return null
        val d = list("dhDMeters") ?: return null
        val a = list("dhAMeters") ?: return null
        val alpha = list("dhAlphaRad") ?: return null
        val minimums = list("jointMinValues") ?: return null
        val maximums = list("jointMaxValues") ?: return null
        val homes = list("jointHomeValues") ?: return null
        val seeds = list("seedJointValues") ?: return null
        val solutions = list("solutionJointValues") ?: return null
        if (listOf(types.size, theta.size, d.size, a.size, alpha.size, minimums.size, maximums.size, homes.size, seeds.size, solutions.size).any { it != count }) return null
        if ((0 until count).any { maximums[it] <= minimums[it] || seeds[it] !in minimums[it]..maximums[it] || solutions[it] !in minimums[it]..maximums[it] }) return null

        val robot =
            RobotDefinition(
                name = text("robotName").orEmpty().ifBlank { robotId },
                dhParameters = List(count) { DHParameter(theta[it], d[it], a[it], alpha[it]) },
                joints = List(count) { JointDefinition("Joint ${it + 1}", types[it], minimums[it], maximums[it], homes[it]) }
            )
        if (!robotValidator.validate(robot).isValid) return null
        val target = Vec3(finite("targetX") ?: return null, finite("targetY") ?: return null, finite("targetZ") ?: return null)
        val seed = RobotState(seeds)
        val solution = RobotState(solutions)
        val config =
            IKConfig(
                maxIterations = text("ikMaxIterations")?.toIntOrNull()?.takeIf { it > 0 } ?: return null,
                tolerance = finite("ikToleranceMeters")?.takeIf { it > 0.0 } ?: return null,
                damping = finite("ikDamping")?.takeIf { it > 0.0 } ?: return null,
                maxStep = finite("ikMaxStep")?.takeIf { it > 0.0 } ?: return null
            )
        val accepted = text("solverAccepted")?.toBooleanStrictOrNull() ?: return null
        val converged = text("converged")?.toBooleanStrictOrNull() ?: return null
        val successfulStatus = text("status") == "SUCCESS" || text("status") == "SUCCESS_WITH_WARNING"
        val recordedError = finite("finalError") ?: return null
        val iterations = text("iterations")?.toIntOrNull()?.takeIf { it in 0..config.maxIterations } ?: return null

        val independentFk = fkSolver.solve(robot, solution)
        val independentError =
            if (independentFk.status == FKStatus.SUCCESS || independentFk.status == FKStatus.SUCCESS_WITH_WARNING) {
                hypot(
                    hypot(independentFk.endEffectorPosition.x - target.x, independentFk.endEffectorPosition.y - target.y),
                    independentFk.endEffectorPosition.z - target.z
                )
            } else {
                Double.POSITIVE_INFINITY
            }
        val contractSatisfied =
            accepted && converged && successfulStatus &&
                config.tolerance <= ONE_MICRON_METERS &&
                recordedError <= ONE_MICRON_METERS &&
                independentError.isFinite() && independentError <= ONE_MICRON_METERS &&
                kotlin.math.abs(recordedError - independentError) <= RECORDED_RESIDUAL_AGREEMENT_METERS

        val features = runCatching { OneMicronIkFeatureEncoder.encode(robot, seed, target, config, profile) }.getOrNull()
            ?: return null
        val delta = FloatArray(maximumSupportedJointCount)
        val mask = FloatArray(maximumSupportedJointCount)
        repeat(count) { index ->
            delta[index] = ((solutions[index] - seeds[index]) / (maximums[index] - minimums[index])).toFloat()
            mask[index] = 1f
        }
        if (delta.any { !it.isFinite() || it !in -1.000001f..1.000001f }) return null
        val robotFingerprint = RobotMorphologyFingerprint.fromRobot(robot)
        val fingerprint =
            RobotMorphologyFingerprint.sample(
                robotFingerprint = robotFingerprint,
                target = listOf(target.x, target.y, target.z),
                seedValues = seeds
            )
        return ParsedRow(
            robotId = robotId,
            robot = robot,
            contractSatisfied = contractSatisfied,
            sample =
                OneMicronIkSample(
                    features = features,
                    normalizedJointDelta = delta,
                    outputMask = mask,
                    robotIndex = -1,
                    seedState = seed,
                    target = target,
                    deterministicIterations = iterations,
                    sourceRowIndex = rowIndex,
                    splitFingerprint = fingerprint,
                    robotFingerprint = robotFingerprint,
                    certifiedCartesianErrorMeters = independentError,
                    recordedCartesianErrorMeters = recordedError
                )
        )
    }

    private data class ParsedRow(
        val robotId: String,
        val robot: RobotDefinition,
        val contractSatisfied: Boolean,
        val sample: OneMicronIkSample
    )

    companion object {
        // The 100k reference corpus has a worst serialization/recomputation delta below 2e-11 m.
        // Keep margin for 12-significant-digit CSV serialization without accepting falsified residuals.
        private const val RECORDED_RESIDUAL_AGREEMENT_METERS = 5e-11
        private val REQUIRED_COLUMNS =
            listOf(
                "schemaVersion", "robotId", "robotName", "jointCount", "jointTypes",
                "dhThetaRad", "dhDMeters", "dhAMeters", "dhAlphaRad",
                "jointMinValues", "jointMaxValues", "jointHomeValues",
                "ikMaxIterations", "ikToleranceMeters", "ikDamping", "ikMaxStep",
                "seedJointValues", "targetX", "targetY", "targetZ", "solutionJointValues",
                "solverAccepted", "status", "converged", "finalError", "iterations"
            )
    }
}
