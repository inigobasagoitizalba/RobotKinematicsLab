package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.domain.result.IKDetailCode
import com.robotkinematicslab.mobile.domain.result.IKStatus
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

class ScientificDatasetTrainingReader(
    private val maximumSupportedJointCount: Int = 10
) {

    fun load(
        file: File,
        profile: TrainingFeatureProfile,
        maximumRows: Int,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (TrainingDatasetLoadProgress) -> Unit = {},
        sampleAcrossEntireFile: Boolean = false,
        samplingSeed: Int = 0,
        expectedDataRowCount: Long? = null,
        requireEveryRowValid: Boolean = false,
        requiredNewestRows: Int = 0,
        expectedManifest: com.robotkinematicslab.mobile.dataset.DatasetManifest? = null,
        requirements: TrainingDatasetRequirements? = null
    ): TrainingDatasetLoadResult {
        if (!file.exists() || !file.isFile) {
            return TrainingDatasetLoadResult(null, "Dataset CSV does not exist: ${file.absolutePath}")
        }
        if (maximumRows <= 0) {
            return TrainingDatasetLoadResult(null, "Maximum training rows must be positive.")
        }
        if (expectedDataRowCount != null && expectedDataRowCount !in 1..Int.MAX_VALUE.toLong()) {
            return TrainingDatasetLoadResult(null, "Expected dataset row count is outside the supported range.")
        }
        if (requiredNewestRows < 0 || requiredNewestRows > maximumRows) {
            return TrainingDatasetLoadResult(null, "Required newest rows must fit inside the selected training row cap.")
        }
        if (requiredNewestRows > 0 && (expectedDataRowCount == null || !sampleAcrossEntireFile)) {
            return TrainingDatasetLoadResult(
                null,
                "Guaranteed newest-row participation requires a verified row count and full-corpus sampling."
            )
        }

        return runCatching {
            file.bufferedReader().use { reader ->
                val headerLine = reader.readLine()
                    ?: return TrainingDatasetLoadResult(null, "Dataset CSV is empty.")
                val header = parseCsvLine(headerLine)
                val index = header.withIndex().associate { it.value to it.index }
                val missing = REQUIRED_COLUMNS.filterNot(index::containsKey)
                require(missing.isEmpty()) {
                    "Dataset schema is missing required columns: ${missing.joinToString()}"
                }

                val featureNames = featureNames(profile, maximumSupportedJointCount)
                val samples = ArrayList<EncodedTrainingSample>(maximumRows.coerceAtMost(100_000))
                val fingerprints = HashSet<Long>()
                val morphologyByRobotId = HashMap<String, Long>()
                var duplicateCount = 0
                var rowsRead = 0
                var rowsSkipped = 0
                var acceptedRowsSeen = 0
                val samplingRandom = Random(samplingSeed.toLong())
                val selectedRawRows =
                    expectedDataRowCount?.let { expected ->
                        require(sampleAcrossEntireFile) {
                            "Expected-row sampling requires sampleAcrossEntireFile=true."
                        }
                        DeterministicCsvRowSampler.indicesIncludingTail(
                            totalRows = expected.toInt(),
                            maximumRows = maximumRows,
                            seed = samplingSeed,
                            requiredTailRows = requiredNewestRows
                        )
                    }
                val scanEntireFile = sampleAcrossEntireFile || requireEveryRowValid

                while (scanEntireFile || samples.size < maximumRows) {
                    if (cancellationRequested()) {
                        return TrainingDatasetLoadResult(null, CANCELLATION_MESSAGE)
                    }
                    val line = reader.readLine() ?: break
                    rowsRead++
                    val selectedForEncoding =
                        selectedRawRows?.contains(rowsRead - 1)
                            ?: (sampleAcrossEntireFile || samples.size < maximumRows)
                    val mustInspectRow = selectedForEncoding || requireEveryRowValid
                    val values = if (mustInspectRow) runCatching { parseCsvLine(line) }.getOrNull() else null
                    val encoded = values?.let { encode(it, index, profile, rowsRead.toLong()) }
                    if (encoded == null) {
                        if (mustInspectRow) rowsSkipped++
                    } else {
                        if (expectedManifest != null) verifyManagedRow(values!!, index, rowsRead.toLong(), expectedManifest)
                        val previousMorphology = morphologyByRobotId.putIfAbsent(
                            encoded.robotId,
                            encoded.robotFingerprint
                        )
                        require(previousMorphology == null || previousMorphology == encoded.robotFingerprint) {
                            "Robot id ${encoded.robotId} changes physical definition inside the dataset."
                        }
                        if (selectedForEncoding) {
                            acceptedRowsSeen++
                            if (selectedRawRows != null || !sampleAcrossEntireFile || samples.size < maximumRows) {
                                samples += encoded
                            } else {
                                // Algorithm R: a deterministic, uniform bounded sample over the
                                // complete file. This prevents an ordered CSV from turning a small
                                // ROBOT_HELD_OUT run into a single-robot prefix.
                                val replacementIndex = samplingRandom.nextInt(acceptedRowsSeen)
                                if (replacementIndex < maximumRows) {
                                    samples[replacementIndex] = encoded
                                }
                            }
                        }
                    }

                    if (rowsRead == 1 || rowsRead % PROGRESS_INTERVAL == 0) {
                        onProgress(
                            TrainingDatasetLoadProgress(
                                rowsRead = rowsRead,
                                rowsAccepted = samples.size,
                                rowsSkipped = rowsSkipped,
                                message = "Reading and validating dataset rows."
                            )
                        )
                    }
                }

                if (requirements != null) {
                    require(morphologyByRobotId.values.distinct().size >= requirements.minimumRobotCount) {
                        "CSV contains ${morphologyByRobotId.values.distinct().size} distinct robot definitions; ${requirements.minimumRobotCount} required."
                    }
                }
                if (expectedDataRowCount != null) {
                    require(rowsRead.toLong() == expectedDataRowCount) {
                        "Dataset manifest declares $expectedDataRowCount rows but the CSV contains $rowsRead."
                    }
                }
                if (requireEveryRowValid) {
                    require(rowsSkipped == 0) {
                        "Dataset integrity validation rejected $rowsSkipped malformed or scientifically invalid rows."
                    }
                }

                require(samples.size >= MINIMUM_ROWS) {
                    "At least $MINIMUM_ROWS valid rows are required; found ${samples.size}."
                }

                samples.forEach { sample ->
                    if (!fingerprints.add(sample.splitFingerprint)) duplicateCount++
                }

                onProgress(
                    TrainingDatasetLoadProgress(
                        rowsRead = rowsRead,
                        rowsAccepted = samples.size,
                        rowsSkipped = rowsSkipped,
                        message = "Dataset validated and encoded."
                    )
                )

                TrainingDatasetLoadResult(
                    dataset =
                        TrainingDataset(
                            sourcePath = file.absolutePath,
                            profile = profile,
                            featureNames = featureNames,
                            samples = samples,
                            skippedRowCount = rowsSkipped,
                            duplicateFingerprintCount = duplicateCount
                        ),
                    errorMessage = null
                )
            }
        }.getOrElse { error ->
            TrainingDatasetLoadResult(
                dataset = null,
                errorMessage = error.message ?: "Dataset could not be read."
            )
        }
    }

    private fun verifyManagedRow(values: List<String>, columns: Map<String, Int>, row: Long,
                                 manifest: com.robotkinematicslab.mobile.dataset.DatasetManifest) {
        fun text(name: String) = columns[name]?.let(values::getOrNull)
        val batch = manifest.batches.firstOrNull { row - 1 >= it.rowStart && row - 1 < it.rowStart + it.rowCount }
        require(manifest.batches.isEmpty() || batch != null) { "CSV row $row is outside the recorded append history." }
        val solver = batch?.ikConfig ?: manifest.ikConfig
        val robots = batch?.robotIds ?: manifest.robotIds
        require(text("robotId") in robots) { "CSV row $row has a robot absent from its recorded generation batch." }
        require(text("randomProtocol") == (batch?.randomProtocol ?: manifest.randomProtocol)) { "CSV row $row random provenance differs from its metadata." }
        fun same(name: String, expected: Double) {
            val actual = text(name)?.toDoubleOrNull()
            require(actual != null && actual.isFinite() && kotlin.math.abs(actual - expected) <= maxOf(kotlin.math.abs(actual), kotlin.math.abs(expected)) * 1e-10) {
                "CSV row $row $name differs from its recorded solver configuration."
            }
        }
        require(text("ikMaxIterations")?.toIntOrNull() == solver.maxIterations) { "CSV row $row IK iteration limit differs from its metadata." }
        same("ikToleranceMeters", solver.tolerance)
        same("ikDamping", solver.damping)
        same("ikMaxStep", solver.maxStep)
        val mode = batch?.targetMode ?: manifest.targetMode
        if (mode != com.robotkinematicslab.mobile.dataset.DatasetTargetMode.MIXED) {
            require(text("targetClass") == mode.name) { "CSV row $row target population differs from its recorded mode." }
        }
    }

    private fun encode(
        values: List<String>,
        index: Map<String, Int>,
        profile: TrainingFeatureProfile,
        sourceRowIndex: Long
    ): EncodedTrainingSample? {
        fun text(name: String): String? = index[name]?.let(values::getOrNull)
        fun finiteDouble(name: String): Double? = text(name)?.toDoubleOrNull()?.takeIf(Double::isFinite)

        if (text("schemaVersion") != ScientificDatasetCsvWriter.SCHEMA_VERSION) return null
        val jointCount = text("jointCount")?.toIntOrNull() ?: return null
        if (jointCount !in 1..maximumSupportedJointCount) return null

        val jointTypes = text("jointTypes").semicolonValues()
        if (jointTypes.any { type -> type != "REVOLUTE" && type != "PRISMATIC" }) return null
        val theta = text("dhThetaRad").finiteDoubleList()
        val d = text("dhDMeters").finiteDoubleList()
        val a = text("dhAMeters").finiteDoubleList()
        val alpha = text("dhAlphaRad").finiteDoubleList()
        val minimums = text("jointMinValues").finiteDoubleList()
        val maximums = text("jointMaxValues").finiteDoubleList()
        val homes = text("jointHomeValues").finiteDoubleList()
        val seeds = text("seedJointValues").finiteDoubleList()

        val requiredLists = listOf(theta, d, a, alpha, minimums, maximums, homes, seeds)
        if (jointTypes.size != jointCount || requiredLists.any { it?.size != jointCount }) return null

        val safeTheta = theta ?: return null
        val safeD = d ?: return null
        val safeA = a ?: return null
        val safeAlpha = alpha ?: return null
        val safeMinimums = minimums ?: return null
        val safeMaximums = maximums ?: return null
        val safeHomes = homes ?: return null
        val safeSeeds = seeds ?: return null
        if ((0 until jointCount).any { safeMaximums[it] <= safeMinimums[it] }) return null
        if (
            (0 until jointCount).any { jointIndex ->
                safeHomes[jointIndex] !in safeMinimums[jointIndex]..safeMaximums[jointIndex] ||
                    safeSeeds[jointIndex] !in safeMinimums[jointIndex]..safeMaximums[jointIndex]
            }
        ) return null
        if (
            (0 until jointCount).any { jointIndex ->
                when (jointTypes[jointIndex]) {
                    "REVOLUTE" -> abs(safeTheta[jointIndex]) > ACTIVE_DH_ZERO_EPS
                    "PRISMATIC" -> abs(safeD[jointIndex]) > ACTIVE_DH_ZERO_EPS
                    else -> true
                }
            }
        ) return null

        val targetX = finiteDouble("targetX") ?: return null
        val targetY = finiteDouble("targetY") ?: return null
        val targetZ = finiteDouble("targetZ") ?: return null
        val maxIterations = text("ikMaxIterations")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val tolerance = finiteDouble("ikToleranceMeters")?.takeIf { it > 0.0 } ?: return null
        val damping = finiteDouble("ikDamping")?.takeIf { it > 0.0 } ?: return null
        val maxStep = finiteDouble("ikMaxStep")?.takeIf { it > 0.0 } ?: return null
        val normalizedStatus = text("status")?.trim()?.uppercase() ?: return null
        val status = runCatching { IKStatus.valueOf(normalizedStatus) }.getOrNull() ?: return null
        val detailCode =
            text("detailCode")
                ?.trim()
                ?.uppercase()
                ?.let { runCatching { IKDetailCode.valueOf(it) }.getOrNull() }
                ?: return null
        val successDetailConsistent =
            when (status) {
                IKStatus.SUCCESS -> detailCode == IKDetailCode.NONE
                IKStatus.SUCCESS_WITH_WARNING ->
                    detailCode == IKDetailCode.NEAR_SINGULARITY_WARNING ||
                        detailCode == IKDetailCode.CRITICAL_SINGULARITY_DAMPED
                else -> true
            }
        if (!successDetailConsistent) return null
        val label =
            TrainingLabel.fromCsv(
                acceptanceClass = text("acceptanceClass"),
                solverAccepted = text("solverAccepted"),
                status = normalizedStatus
            ) ?: return null
        val converged = text("converged")?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: return null
        val successStatus = normalizedStatus == "SUCCESS" || normalizedStatus == "SUCCESS_WITH_WARNING"
        if (converged != successStatus) return null
        val iterations = text("iterations")?.toIntOrNull()?.takeIf { it in 0..maxIterations } ?: return null
        val finalError = text("finalError")?.toDoubleOrNull() ?: return null
        if (finalError == Double.POSITIVE_INFINITY || finalError == Double.NEGATIVE_INFINITY) return null
        if (finalError.isFinite() && finalError < 0.0) return null
        if (successStatus) {
            if (!finalError.isFinite() || finalError > tolerance) return null
        }

        val features = ArrayList<Float>(featureNames(profile, maximumSupportedJointCount).size)
        features += jointCount.toFloat() / maximumSupportedJointCount.toFloat()
        features += targetX.toFloat()
        features += targetY.toFloat()
        features += targetZ.toFloat()
        features += (maxIterations.toDouble() / 10_000.0).toFloat()
        features += ln(tolerance.coerceAtLeast(1e-12)).toFloat()
        features += ln(damping.coerceAtLeast(1e-12)).toFloat()
        features += ln(maxStep.coerceAtLeast(1e-12)).toFloat()

        repeat(maximumSupportedJointCount) { jointIndex ->
            val present = jointIndex < jointCount
            features += if (present) 1f else 0f
            features += if (present && jointTypes[jointIndex].equals("PRISMATIC", true)) 1f else 0f
            features += safeTheta.getOrZero(jointIndex).toFloat()
            features += safeD.getOrZero(jointIndex).toFloat()
            features += safeA.getOrZero(jointIndex).toFloat()
            features += safeAlpha.getOrZero(jointIndex).toFloat()
            features += safeMinimums.getOrZero(jointIndex).toFloat()
            features += safeMaximums.getOrZero(jointIndex).toFloat()
            features += safeHomes.getOrZero(jointIndex).toFloat()
            features += safeSeeds.getOrZero(jointIndex).toFloat()
        }

        if (profile != TrainingFeatureProfile.BASELINE_KINEMATICS) {
            val reachBound =
                (0 until jointCount).sumOf { jointIndex ->
                    val radialD =
                        if (jointTypes[jointIndex].equals("PRISMATIC", true)) {
                            maxOf(abs(safeMinimums[jointIndex]), abs(safeMaximums[jointIndex]))
                        } else {
                            abs(safeD[jointIndex])
                        }
                    hypot(safeA[jointIndex], radialD)
                }.coerceAtLeast(1e-12)
            val targetRadius = hypot(hypot(targetX, targetY), targetZ)
            val spans = (0 until jointCount).map { safeMaximums[it] - safeMinimums[it] }
            val linkExtents =
                (0 until jointCount).map { jointIndex ->
                    hypot(safeA[jointIndex], safeD[jointIndex])
                }
            val normalizedSeedOffsets =
                (0 until jointCount).map { jointIndex ->
                    (safeSeeds[jointIndex] - safeHomes[jointIndex]) / spans[jointIndex]
                }
            val prismaticCount = jointTypes.count { it.equals("PRISMATIC", true) }
            val initialError = finiteDouble("initialError")
            val seedMargin = finiteDouble("seedMinNormalizedLimitMargin")
            val seedLogCondition = finiteDouble("seedLogConditionNumber")

            features += (initialError ?: 0.0).toFloat()
            features += if (initialError != null) 1f else 0f
            features += (seedMargin ?: 0.0).toFloat()
            features += if (seedMargin != null) 1f else 0f
            features += (seedLogCondition ?: 0.0).toFloat()
            features += if (seedLogCondition != null) 1f else 0f
            features += targetRadius.toFloat()
            features += reachBound.toFloat()
            features += (targetRadius / reachBound).toFloat()
            features += (prismaticCount.toDouble() / jointCount).toFloat()
            features += ((jointCount - prismaticCount).toDouble() / jointCount).toFloat()
            features += linkExtents.average().toFloat()
            features += standardDeviation(linkExtents).toFloat()
            features += spans.average().toFloat()
            features += (spans.minOrNull() ?: 0.0).toFloat()
            features += (targetX / reachBound).toFloat()
            features += (targetY / reachBound).toFloat()
            features += (targetZ / reachBound).toFloat()
            features += sqrt(normalizedSeedOffsets.map { it * it }.average()).toFloat()
            features += normalizedSeedOffsets.map(::abs).average().toFloat()
            features += (normalizedSeedOffsets.maxOfOrNull(::abs) ?: 0.0).toFloat()
            features += abs(1.0 - targetRadius / reachBound).toFloat()
        }

        if (
            profile == TrainingFeatureProfile.CONTEXT_EXPANDED ||
            profile == TrainingFeatureProfile.CONTEXT_RESEARCH_V2
        ) {
            val expanded =
                runCatching {
                    ExpandedContextFeatureCalculator.calculate(
                        input =
                            ExpandedContextInput(
                                jointTypes = jointTypes,
                                theta = safeTheta,
                                d = safeD,
                                a = safeA,
                                alpha = safeAlpha,
                                minimums = safeMinimums,
                                maximums = safeMaximums,
                                homes = safeHomes,
                                seeds = safeSeeds,
                                targetX = targetX,
                                targetY = targetY,
                                targetZ = targetZ,
                                tolerance = tolerance,
                                damping = damping,
                                maxStep = maxStep
                            ),
                        maximumJointCount = maximumSupportedJointCount
                    )
                }.getOrNull() ?: return null
            expanded.forEach { features += it.toFloat() }
        }

        if (profile == TrainingFeatureProfile.CONTEXT_RESEARCH_V2) {
            val research =
                runCatching {
                    ResearchContextFeatureCalculator.calculate(
                        input =
                            ExpandedContextInput(
                                jointTypes = jointTypes,
                                theta = safeTheta,
                                d = safeD,
                                a = safeA,
                                alpha = safeAlpha,
                                minimums = safeMinimums,
                                maximums = safeMaximums,
                                homes = safeHomes,
                                seeds = safeSeeds,
                                targetX = targetX,
                                targetY = targetY,
                                targetZ = targetZ,
                                tolerance = tolerance,
                                damping = damping,
                                maxStep = maxStep
                            ),
                        maximumJointCount = maximumSupportedJointCount
                    )
                }.getOrNull() ?: return null
            research.forEach { features += it.toFloat() }
        }

        if (features.any { !it.isFinite() }) return null

        val robotId = text("robotId")?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val robotFingerprint =
            RobotMorphologyFingerprint.fromComponents(
                jointTypes = jointTypes.take(jointCount),
                theta = safeTheta,
                d = safeD,
                a = safeA,
                alpha = safeAlpha,
                minimums = safeMinimums,
                maximums = safeMaximums,
                homes = safeHomes
            )
        val fingerprint =
            RobotMorphologyFingerprint.sample(
                robotFingerprint = robotFingerprint,
                target = listOf(targetX, targetY, targetZ),
                seedValues = safeSeeds
            )

        return EncodedTrainingSample(
            features = features.toFloatArray(),
            labelIndex = label.ordinal,
            splitFingerprint = fingerprint,
            robotFingerprint = robotFingerprint,
            sourceRowIndex = sourceRowIndex,
            robotId = robotId,
            topologyKey = jointTypes.take(jointCount).joinToString("-") { it.uppercase() },
            targetClass = text("targetClass").orEmpty().uppercase(),
            targetSamplingStrategy = text("targetSamplingStrategy").orEmpty().uppercase()
        )
    }

    companion object {
        private const val MINIMUM_ROWS = 30
        private const val PROGRESS_INTERVAL = 2_000
        internal const val CANCELLATION_MESSAGE = "Dataset validation was cancelled."
        private const val ACTIVE_DH_ZERO_EPS = 1e-12

        private val REQUIRED_COLUMNS =
            listOf(
                "schemaVersion",
                "robotId",
                "jointCount",
                "jointTypes",
                "dhThetaRad",
                "dhDMeters",
                "dhAMeters",
                "dhAlphaRad",
                "jointMinValues",
                "jointMaxValues",
                "jointHomeValues",
                "ikMaxIterations",
                "ikToleranceMeters",
                "ikDamping",
                "ikMaxStep",
                "seedJointValues",
                "targetX",
                "targetY",
                "targetZ",
                "solverAccepted",
                "acceptanceClass",
                "status",
                "detailCode",
                "converged",
                "finalError",
                "iterations"
            )

        fun featureNames(
            profile: TrainingFeatureProfile,
            maximumJointCount: Int = 10
        ): List<String> {
            val names =
                mutableListOf(
                    "joint_count_ratio",
                    "target_x",
                    "target_y",
                    "target_z",
                    "ik_iteration_budget_ratio",
                    "log_ik_tolerance",
                    "log_ik_damping",
                    "log_ik_max_step"
                )
            repeat(maximumJointCount) { index ->
                val joint = index + 1
                names += "joint_${joint}_present"
                names += "joint_${joint}_is_prismatic"
                names += "joint_${joint}_dh_theta"
                names += "joint_${joint}_dh_d"
                names += "joint_${joint}_dh_a"
                names += "joint_${joint}_dh_alpha"
                names += "joint_${joint}_minimum"
                names += "joint_${joint}_maximum"
                names += "joint_${joint}_home"
                names += "joint_${joint}_seed"
            }
            if (profile != TrainingFeatureProfile.BASELINE_KINEMATICS) {
                names +=
                    listOf(
                        "initial_cartesian_error",
                        "initial_error_available",
                        "seed_min_normalized_limit_margin",
                        "seed_margin_available",
                        "seed_log_condition_number",
                        "seed_condition_available",
                        "target_radius",
                        "conservative_reach_bound",
                        "target_reach_ratio",
                        "prismatic_joint_fraction",
                        "revolute_joint_fraction",
                        "mean_link_extent",
                        "link_extent_standard_deviation",
                        "mean_joint_span",
                        "minimum_joint_span",
                        "normalized_target_x",
                        "normalized_target_y",
                        "normalized_target_z",
                        "seed_home_offset_rms",
                        "seed_home_offset_mean_absolute",
                        "seed_home_offset_max_absolute",
                        "workspace_boundary_proximity"
                    )
            }
            if (
                profile == TrainingFeatureProfile.CONTEXT_EXPANDED ||
                profile == TrainingFeatureProfile.CONTEXT_RESEARCH_V2
            ) {
                names += ExpandedContextFeatureCalculator.featureNames(maximumJointCount)
            }
            if (profile == TrainingFeatureProfile.CONTEXT_RESEARCH_V2) {
                names += ResearchContextFeatureCalculator.featureNames(maximumJointCount)
            }
            return names
        }

        internal fun parseCsvLine(line: String): List<String> {
            val values = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var index = 0
            while (index < line.length) {
                val character = line[index]
                when {
                    character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                        current.append('"')
                        index++
                    }
                    character == '"' -> quoted = !quoted
                    character == ',' && !quoted -> {
                        values += current.toString()
                        current.setLength(0)
                    }
                    else -> current.append(character)
                }
                index++
            }
            require(!quoted) { "Dataset CSV contains an unclosed quoted field." }
            values += current.toString()
            return values
        }

        private fun String?.semicolonValues(): List<String> =
            this?.split(';')?.filter(String::isNotBlank).orEmpty()

        private fun String?.finiteDoubleList(): List<Double>? {
            val strings = semicolonValues()
            val values = strings.map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null }
            return values
        }

        private fun List<Double>.getOrZero(index: Int): Double = getOrNull(index) ?: 0.0

        private fun standardDeviation(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val mean = values.average()
            return sqrt(values.sumOf { value -> (value - mean) * (value - mean) } / values.size)
        }
    }
}
