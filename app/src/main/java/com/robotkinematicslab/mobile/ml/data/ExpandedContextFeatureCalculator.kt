package com.robotkinematicslab.mobile.ml.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pre-solve feature engineering for the deliberately large research profile.
 *
 * Every value is derived from the robot, initial state, target and solver configuration.
 * Solver outputs, convergence flags, iterations and final errors are intentionally absent.
 */
data class ExpandedContextInput(
    val jointTypes: List<String>,
    val theta: List<Double>,
    val d: List<Double>,
    val a: List<Double>,
    val alpha: List<Double>,
    val minimums: List<Double>,
    val maximums: List<Double>,
    val homes: List<Double>,
    val seeds: List<Double>,
    val targetX: Double,
    val targetY: Double,
    val targetZ: Double,
    val tolerance: Double,
    val damping: Double,
    val maxStep: Double
)

object ExpandedContextFeatureCalculator {

    fun featureNames(maximumJointCount: Int): List<String> = buildList {
        addAll(GLOBAL_FEATURE_NAMES)
        repeat(maximumJointCount) { index ->
            val joint = index + 1
            JOINT_FEATURE_SUFFIXES.forEach { suffix -> add("joint_${joint}_$suffix") }
        }
    }

    fun calculate(input: ExpandedContextInput, maximumJointCount: Int): DoubleArray {
        validate(input, maximumJointCount)
        val count = input.jointTypes.size
        val reach = conservativeReach(input).coerceAtLeast(EPS)
        val targetRadius = norm3(input.targetX, input.targetY, input.targetZ)
        val targetCylindricalRadius = hypot(input.targetX, input.targetY)
        val kinematics = seedKinematics(input)
        val error = doubleArrayOf(
            input.targetX - kinematics.endEffector[0],
            input.targetY - kinematics.endEffector[1],
            input.targetZ - kinematics.endEffector[2]
        )
        val errorNorm = norm3(error[0], error[1], error[2])
        val errorDirection =
            if (errorNorm > EPS) DoubleArray(3) { error[it] / errorNorm }
            else doubleArrayOf(0.0, 0.0, 0.0)
        val gram = scaledGram(kinematics.jacobian, input)
        val singularValues = symmetricEigenvalues(gram)
            .map { sqrt(max(0.0, it)) }
            .sortedDescending()
        val sigmaMax = singularValues[0]
        val sigmaMiddle = singularValues[1]
        val sigmaThird = singularValues[2]
        val expectedRank = count.coerceIn(1, 3)
        val sigmaExpectedMin = singularValues[expectedRank - 1]
        val condition = if (sigmaExpectedMin > EPS) sigmaMax / sigmaExpectedMin else CONDITION_CAP
        val reciprocalCondition = if (condition > EPS) 1.0 / condition else 0.0
        val spectralEnergy = singularValues.sumOf { it * it }
        val spectralWeights =
            if (spectralEnergy > EPS) singularValues.map { it * it / spectralEnergy }
            else List(3) { 0.0 }
        val spectralEntropy = -spectralWeights.sumOf { weight -> if (weight > EPS) weight * ln(weight) else 0.0 }
        val effectiveRank = exp(spectralEntropy)
        val rankThreshold = max(EPS, sigmaMax * 1e-9)
        val observedRank = singularValues.count { it > rankThreshold }
        val manipulability = singularValues.take(expectedRank).fold(1.0) { product, value -> product * value }

        val singularityMultiplier = when {
            sigmaExpectedMin <= 1e-5 || condition >= 1e5 -> 10.0
            sigmaExpectedMin <= 1e-3 || condition >= 1e3 -> 3.0
            else -> 1.0
        }
        val lambda = max(input.damping * singularityMultiplier, errorNorm * 0.5).coerceAtLeast(EPS)
        val dls = dlsPreview(kinematics.jacobian, input, error, lambda)
        val spans = List(count) { input.maximums[it] - input.minimums[it] }
        val halfSpans = spans.map { max(it * 0.5, EPS) }
        val centers = List(count) { (input.minimums[it] + input.maximums[it]) * 0.5 }
        val lowerMargins = List(count) { (input.seeds[it] - input.minimums[it]) / spans[it] }
        val upperMargins = List(count) { (input.maximums[it] - input.seeds[it]) / spans[it] }
        val nearestMargins = List(count) { min(lowerMargins[it], upperMargins[it]) }
        val signedPositions = List(count) { (input.seeds[it] - centers[it]) / halfSpans[it] }
        val barriers = nearestMargins.map { -ln(max(it, LIMIT_EPS)) }
        val directionalHeadrooms =
            List(count) { index ->
                val step = dls.rawJointStep[index]
                val available = if (step >= 0.0) input.maximums[index] - input.seeds[index]
                else input.seeds[index] - input.minimums[index]
                if (abs(step) > EPS) available / abs(step) else HEADROOM_CAP
            }
        val towardNearerLimit =
            List(count) { index ->
                val nearerDirection = if (lowerMargins[index] <= upperMargins[index]) -1.0 else 1.0
                if (dls.rawJointStep[index] * nearerDirection > 0.0) 1.0 else 0.0
            }
        val normalizedSteps = List(count) { dls.rawJointStep[it] / halfSpans[it] }
        val clippedScale =
            if (dls.normalizedStepNorm > input.maxStep && dls.normalizedStepNorm > EPS) {
                input.maxStep / dls.normalizedStepNorm
            } else 1.0
        val clippedJointStep = DoubleArray(count) { dls.rawJointStep[it] * clippedScale }
        val predictedCartesianStep = multiplyJacobian(kinematics.jacobian, clippedJointStep)
        val predictedResidual = DoubleArray(3) { error[it] - predictedCartesianStep[it] }
        val predictedStepNorm = norm3(predictedCartesianStep[0], predictedCartesianStep[1], predictedCartesianStep[2])
        val predictedResidualNorm = norm3(predictedResidual[0], predictedResidual[1], predictedResidual[2])
        val alignment =
            if (errorNorm > EPS && predictedStepNorm > EPS) {
                dot3(error, predictedCartesianStep) / (errorNorm * predictedStepNorm)
            } else 0.0
        val directionalMobilitySquared = quadraticForm(gram, errorDirection)
        val directionalMobility = sqrt(max(0.0, directionalMobilitySquared))

        val staticExtents = List(count) { hypot(input.a[it], input.d[it]) }
        val activeExtents =
            List(count) { index ->
                val activeD = if (input.jointTypes[index].equals("PRISMATIC", true)) input.seeds[index] else input.d[index]
                hypot(input.a[index], activeD)
            }
        val totalExtent = staticExtents.sum().coerceAtLeast(EPS)
        val maxExtent = staticExtents.maxOrNull() ?: 0.0
        val minExtent = staticExtents.minOrNull() ?: 0.0
        val extentShares = staticExtents.map { it / totalExtent }
        val extentEntropy = -extentShares.sumOf { share -> if (share > EPS) share * ln(share) else 0.0 }
        val totalAbsA = input.a.sumOf(::abs)
        val totalAbsD = input.d.sumOf(::abs)
        val alphaSin = input.alpha.map(::sin)
        val alphaCos = input.alpha.map(::cos)
        val prismaticFlags = input.jointTypes.map { if (it.equals("PRISMATIC", true)) 1.0 else 0.0 }
        val typeTransitions = (1 until count).count { prismaticFlags[it] != prismaticFlags[it - 1] }
        val longestTypeRun = longestRun(prismaticFlags)
        val innerRadialBound = max(0.0, maxExtent - (totalExtent - maxExtent))

        val values = ArrayList<Double>(featureNames(maximumJointCount).size)
        values += listOf(
            kinematics.endEffector[0], kinematics.endEffector[1], kinematics.endEffector[2],
            kinematics.endEffector[0] / reach, kinematics.endEffector[1] / reach, kinematics.endEffector[2] / reach,
            error[0], error[1], error[2], error[0] / reach, error[1] / reach, error[2] / reach,
            errorNorm, errorNorm / reach,
            targetCylindricalRadius, targetCylindricalRadius / reach,
            safeDirectionComponent(input.targetY, input.targetX, true),
            safeDirectionComponent(input.targetY, input.targetX, false),
            safeElevationComponent(targetCylindricalRadius, input.targetZ, true),
            safeElevationComponent(targetCylindricalRadius, input.targetZ, false),
            norm3(kinematics.endEffector[0], kinematics.endEffector[1], kinematics.endEffector[2]) / reach,
            (targetRadius - norm3(kinematics.endEffector[0], kinematics.endEffector[1], kinematics.endEffector[2])) / reach,

            sigmaMax, sigmaMiddle, sigmaThird,
            safeLog(sigmaMax), safeLog(sigmaMiddle), safeLog(sigmaThird),
            condition.coerceAtMost(CONDITION_CAP), safeLog(condition), reciprocalCondition,
            manipulability, safeLog(manipulability), spectralEnergy, spectralEntropy,
            effectiveRank, observedRank / 3.0,
            if (sigmaMax > EPS) sigmaExpectedMin / sigmaMax else 0.0,
            if (sigmaMax > EPS) sigmaMiddle / sigmaMax else 0.0,

            directionalMobility,
            directionalMobility / max(sigmaMax, EPS),
            dls.rawJointStepNorm,
            dls.rawJointStepNorm / sqrt(count.toDouble()),
            dls.rawJointStep.maxOfOrNull(::abs) ?: 0.0,
            dls.normalizedStepNorm,
            rms(normalizedSteps),
            normalizedSteps.maxOfOrNull(::abs) ?: 0.0,
            clippedScale,
            predictedStepNorm,
            predictedResidualNorm,
            predictedResidualNorm / max(errorNorm, EPS),
            (errorNorm - predictedResidualNorm) / max(errorNorm, EPS),
            alignment,
            lambda,
            lambda / max(sigmaExpectedMin, EPS),
            lambda / max(sigmaMax, EPS),
            dls.normalizedStepNorm / max(errorNorm, EPS),
            directionalHeadrooms.minOrNull()?.coerceAtMost(HEADROOM_CAP) ?: 0.0,
            directionalHeadrooms.average().coerceAtMost(HEADROOM_CAP),
            towardNearerLimit.average(),
            normalizedSteps.count { abs(it) > 1.0 }.toDouble() / count,

            nearestMargins.average(), standardDeviation(nearestMargins), nearestMargins.minOrNull() ?: 0.0,
            nearestMargins.maxOrNull() ?: 0.0, harmonicMean(nearestMargins),
            barriers.average(), rms(barriers), barriers.maxOrNull() ?: 0.0,
            nearestMargins.count { it <= 0.05 }.toDouble() / count,
            nearestMargins.count { it <= 0.10 }.toDouble() / count,
            nearestMargins.count { it <= 0.20 }.toDouble() / count,
            signedPositions.average(), rms(signedPositions), signedPositions.maxOfOrNull(::abs) ?: 0.0,
            spans.average(), standardDeviation(spans), spans.maxOrNull() ?: 0.0,
            (spans.maxOrNull() ?: 0.0) / max(spans.minOrNull() ?: 0.0, EPS),

            totalExtent, maxExtent, minExtent, standardDeviation(staticExtents),
            standardDeviation(staticExtents) / max(staticExtents.average(), EPS),
            maxExtent / totalExtent, extentEntropy, exp(extentEntropy),
            innerRadialBound, innerRadialBound / reach,
            (targetRadius - innerRadialBound) / reach,
            (reach - targetRadius) / reach,
            min(abs(targetRadius - innerRadialBound), abs(reach - targetRadius)) / reach,
            totalAbsA, totalAbsD, totalAbsA / max(totalAbsA + totalAbsD, EPS),
            alphaSin.average(), alphaCos.average(),
            hypot(alphaSin.average(), alphaCos.average()),
            input.alpha.count { abs(it) > 1e-6 }.toDouble() / count,
            max(0, count - 3).toDouble() / max(count, 1),
            count.toDouble() / 3.0,
            typeTransitions.toDouble() / max(count - 1, 1),
            longestTypeRun.toDouble() / count
        )

        repeat(maximumJointCount) { index ->
            if (index >= count) {
                repeat(JOINT_FEATURE_SUFFIXES.size) { values += 0.0 }
            } else {
                val span = spans[index]
                val halfSpan = halfSpans[index]
                val normalizedHome = (input.homes[index] - centers[index]) / halfSpan
                val normalizedSeedHome = (input.seeds[index] - input.homes[index]) / halfSpan
                val revolute = input.jointTypes[index].equals("REVOLUTE", true)
                val activeTheta = if (revolute) input.seeds[index] else input.theta[index]
                values += listOf(
                    signedPositions[index],
                    lowerMargins[index],
                    upperMargins[index],
                    nearestMargins[index],
                    barriers[index],
                    normalizedHome,
                    normalizedSeedHome,
                    if (revolute) sin(input.seeds[index]) else 0.0,
                    if (revolute) cos(input.seeds[index]) else 0.0,
                    staticExtents[index] / reach,
                    activeExtents[index] / reach,
                    sin(input.alpha[index]),
                    cos(input.alpha[index]),
                    sin(activeTheta),
                    cos(activeTheta)
                )
                check(span > 0.0)
            }
        }

        check(values.size == featureNames(maximumJointCount).size) {
            "Expanded context schema mismatch: ${values.size} values for ${featureNames(maximumJointCount).size} names."
        }
        check(values.all(Double::isFinite)) { "Expanded context produced a non-finite feature." }
        return values.toDoubleArray()
    }

    private fun validate(input: ExpandedContextInput, maximumJointCount: Int) {
        val count = input.jointTypes.size
        require(count in 1..maximumJointCount)
        val lists = listOf(input.theta, input.d, input.a, input.alpha, input.minimums, input.maximums, input.homes, input.seeds)
        require(lists.all { it.size == count })
        require(input.jointTypes.all { it.equals("REVOLUTE", true) || it.equals("PRISMATIC", true) })
        require(lists.flatten().all(Double::isFinite))
        require((0 until count).all { input.maximums[it] > input.minimums[it] })
        require(
            (0 until count).all { index ->
                input.homes[index] in input.minimums[index]..input.maximums[index] &&
                    input.seeds[index] in input.minimums[index]..input.maximums[index]
            }
        ) { "Home and seed coordinates must remain inside their declared joint limits." }
        require(
            (0 until count).all { index ->
                when {
                    input.jointTypes[index].equals("REVOLUTE", true) ->
                        abs(input.theta[index]) <= ACTIVE_DH_ZERO_EPS

                    else -> abs(input.d[index]) <= ACTIVE_DH_ZERO_EPS
                }
            }
        ) { "The active DH field must be zero because the joint state supplies it." }
        require(listOf(input.targetX, input.targetY, input.targetZ, input.tolerance, input.damping, input.maxStep).all(Double::isFinite))
        require(input.tolerance > 0.0 && input.damping > 0.0 && input.maxStep > 0.0)
    }

    private fun conservativeReach(input: ExpandedContextInput): Double =
        input.jointTypes.indices.sumOf { index ->
            val radialD =
                if (input.jointTypes[index].equals("PRISMATIC", true)) {
                    max(abs(input.minimums[index]), abs(input.maximums[index]))
                } else abs(input.d[index])
            hypot(input.a[index], radialD)
        }

    private data class SeedKinematics(val endEffector: DoubleArray, val jacobian: Array<DoubleArray>)

    private fun seedKinematics(input: ExpandedContextInput): SeedKinematics {
        val count = input.jointTypes.size
        var rotation = identity3()
        var position = doubleArrayOf(0.0, 0.0, 0.0)
        val origins = Array(count) { DoubleArray(3) }
        val axes = Array(count) { DoubleArray(3) }
        repeat(count) { index ->
            origins[index] = position.clone()
            axes[index] = doubleArrayOf(rotation[0][2], rotation[1][2], rotation[2][2])
            val revolute = input.jointTypes[index].equals("REVOLUTE", true)
            val theta = if (revolute) input.seeds[index] else input.theta[index]
            val d = if (revolute) input.d[index] else input.seeds[index]
            val localRotation = dhRotation(theta, input.alpha[index])
            val localTranslation = doubleArrayOf(input.a[index] * cos(theta), input.a[index] * sin(theta), d)
            val translated = multiply(rotation, localTranslation)
            position = DoubleArray(3) { position[it] + translated[it] }
            rotation = multiply(rotation, localRotation)
        }
        val jacobian = Array(3) { DoubleArray(count) }
        repeat(count) { index ->
            val column =
                if (input.jointTypes[index].equals("PRISMATIC", true)) axes[index]
                else cross(axes[index], DoubleArray(3) { position[it] - origins[index][it] })
            repeat(3) { row -> jacobian[row][index] = column[row] }
        }
        return SeedKinematics(position, jacobian)
    }

    private fun scaledGram(jacobian: Array<DoubleArray>, input: ExpandedContextInput): Array<DoubleArray> {
        val gram = Array(3) { DoubleArray(3) }
        repeat(input.jointTypes.size) { column ->
            val scale = max((input.maximums[column] - input.minimums[column]) * 0.5, EPS)
            repeat(3) { row ->
                repeat(3) { other -> gram[row][other] += jacobian[row][column] * jacobian[other][column] * scale * scale }
            }
        }
        check(gram.all { row -> row.all(Double::isFinite) }) {
            "Scaled Jacobian energy overflowed while building expanded context."
        }
        return gram
    }

    private data class DlsPreview(
        val rawJointStep: DoubleArray,
        val rawJointStepNorm: Double,
        val normalizedStepNorm: Double
    )

    private fun dlsPreview(
        jacobian: Array<DoubleArray>,
        input: ExpandedContextInput,
        error: DoubleArray,
        lambda: Double
    ): DlsPreview {
        val gram = scaledGram(jacobian, input)
        repeat(3) { gram[it][it] += lambda * lambda }
        val taskStep = solveSymmetric3x3(gram, error)
        val raw = DoubleArray(input.jointTypes.size)
        val normalized = DoubleArray(raw.size)
        repeat(raw.size) { column ->
            val scale = max((input.maximums[column] - input.minimums[column]) * 0.5, EPS)
            raw[column] = scale * scale * (
                jacobian[0][column] * taskStep[0] +
                    jacobian[1][column] * taskStep[1] +
                    jacobian[2][column] * taskStep[2]
                )
            normalized[column] = raw[column] / scale
        }
        return DlsPreview(raw, stableNorm(raw), stableNorm(normalized))
    }

    private fun solveSymmetric3x3(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val a = Array(3) { matrix[it].clone() }
        val b = rhs.clone()
        repeat(3) { pivot ->
            var best = pivot
            for (row in pivot + 1 until 3) if (abs(a[row][pivot]) > abs(a[best][pivot])) best = row
            if (best != pivot) {
                val row = a[pivot]
                a[pivot] = a[best]
                a[best] = row
                val value = b[pivot]
                b[pivot] = b[best]
                b[best] = value
            }
            val diagonal = if (abs(a[pivot][pivot]) > EPS) a[pivot][pivot] else EPS
            for (row in pivot + 1 until 3) {
                val factor = a[row][pivot] / diagonal
                for (column in pivot until 3) a[row][column] -= factor * a[pivot][column]
                b[row] -= factor * b[pivot]
            }
        }
        val result = DoubleArray(3)
        for (row in 2 downTo 0) {
            var value = b[row]
            for (column in row + 1 until 3) value -= a[row][column] * result[column]
            result[row] = value / if (abs(a[row][row]) > EPS) a[row][row] else EPS
        }
        return result
    }

    private fun symmetricEigenvalues(input: Array<DoubleArray>): List<Double> {
        val a = Array(3) { input[it].clone() }
        repeat(32) {
            var p = 0
            var q = 1
            var best = abs(a[0][1])
            if (abs(a[0][2]) > best) { p = 0; q = 2; best = abs(a[0][2]) }
            if (abs(a[1][2]) > best) { p = 1; q = 2; best = abs(a[1][2]) }
            if (best < EPS) return listOf(a[0][0], a[1][1], a[2][2])
            val app = a[p][p]
            val aqq = a[q][q]
            val apq = a[p][q]
            val tau = (aqq - app) / (2.0 * apq)
            val t = if (tau >= 0.0) 1.0 / (tau + hypot(1.0, tau)) else -1.0 / (-tau + hypot(1.0, tau))
            val c = 1.0 / hypot(1.0, t)
            val s = t * c
            for (k in 0..2) if (k != p && k != q) {
                val akp = a[k][p]
                val akq = a[k][q]
                a[k][p] = c * akp - s * akq
                a[p][k] = a[k][p]
                a[k][q] = s * akp + c * akq
                a[q][k] = a[k][q]
            }
            a[p][p] = c * c * app - 2.0 * s * c * apq + s * s * aqq
            a[q][q] = s * s * app + 2.0 * s * c * apq + c * c * aqq
            a[p][q] = 0.0
            a[q][p] = 0.0
        }
        return listOf(a[0][0], a[1][1], a[2][2])
    }

    private fun safeDirectionComponent(y: Double, x: Double, sine: Boolean): Double {
        if (hypot(x, y) <= EPS) return 0.0
        val angle = atan2(y, x)
        return if (sine) sin(angle) else cos(angle)
    }

    private fun safeElevationComponent(radial: Double, z: Double, sine: Boolean): Double {
        if (hypot(radial, z) <= EPS) return 0.0
        val angle = atan2(z, radial)
        return if (sine) sin(angle) else cos(angle)
    }

    private fun multiplyJacobian(jacobian: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(3) { row -> vector.indices.sumOf { column -> jacobian[row][column] * vector[column] } }

    private fun quadraticForm(matrix: Array<DoubleArray>, vector: DoubleArray): Double =
        vector.indices.sumOf { row -> vector[row] * vector.indices.sumOf { column -> matrix[row][column] * vector[column] } }

    private fun dot3(a: DoubleArray, b: DoubleArray): Double = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun norm3(x: Double, y: Double, z: Double): Double = hypot(hypot(x, y), z)
    private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun identity3(): Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0)
    )

    private fun dhRotation(theta: Double, alpha: Double): Array<DoubleArray> = arrayOf(
        doubleArrayOf(cos(theta), -sin(theta) * cos(alpha), sin(theta) * sin(alpha)),
        doubleArrayOf(sin(theta), cos(theta) * cos(alpha), -cos(theta) * sin(alpha)),
        doubleArrayOf(0.0, sin(alpha), cos(alpha))
    )

    private fun multiply(left: Array<DoubleArray>, right: Array<DoubleArray>): Array<DoubleArray> =
        Array(3) { row -> DoubleArray(3) { column -> (0..2).sumOf { left[row][it] * right[it][column] } } }

    private fun multiply(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray =
        DoubleArray(3) { row -> (0..2).sumOf { matrix[row][it] * vector[it] } }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        return stableNorm(values.map { it - mean }.toDoubleArray()) / sqrt(values.size.toDouble())
    }

    private fun rms(values: List<Double>): Double =
        stableNorm(values.toDoubleArray()) / sqrt(max(values.size, 1).toDouble())

    private fun stableNorm(values: DoubleArray): Double {
        var norm = 0.0
        values.forEach { value -> norm = hypot(norm, value) }
        return norm
    }
    private fun harmonicMean(values: List<Double>): Double = values.size / values.sumOf { 1.0 / max(it, LIMIT_EPS) }
    private fun safeLog(value: Double): Double = ln(value.coerceIn(EPS, CONDITION_CAP))

    private fun longestRun(values: List<Double>): Int {
        var longest = 1
        var current = 1
        for (index in 1 until values.size) {
            current = if (values[index] == values[index - 1]) current + 1 else 1
            longest = max(longest, current)
        }
        return longest
    }

    private val GLOBAL_FEATURE_NAMES = listOf(
        "seed_end_effector_x", "seed_end_effector_y", "seed_end_effector_z",
        "normalized_seed_end_effector_x", "normalized_seed_end_effector_y", "normalized_seed_end_effector_z",
        "seed_target_error_x", "seed_target_error_y", "seed_target_error_z",
        "normalized_seed_target_error_x", "normalized_seed_target_error_y", "normalized_seed_target_error_z",
        "recomputed_initial_error", "normalized_initial_error",
        "target_cylindrical_radius", "normalized_target_cylindrical_radius",
        "target_azimuth_sin", "target_azimuth_cos", "target_elevation_sin", "target_elevation_cos",
        "normalized_seed_radius", "normalized_target_seed_radial_delta",

        "jacobian_sigma_max", "jacobian_sigma_middle", "jacobian_sigma_third",
        "log_jacobian_sigma_max", "log_jacobian_sigma_middle", "log_jacobian_sigma_third",
        "jacobian_condition_number", "log_recomputed_jacobian_condition", "jacobian_reciprocal_condition",
        "jacobian_manipulability", "log_jacobian_manipulability", "jacobian_spectral_energy",
        "jacobian_spectral_entropy", "jacobian_effective_rank", "jacobian_task_rank_ratio",
        "jacobian_isotropy", "jacobian_middle_to_max_ratio",

        "directional_manipulability", "normalized_directional_manipulability",
        "dls_raw_step_l2", "dls_raw_step_rms", "dls_raw_step_max_abs",
        "dls_normalized_step_l2", "dls_normalized_step_rms", "dls_normalized_step_max_abs",
        "dls_max_step_scale", "dls_predicted_cartesian_step", "dls_predicted_residual",
        "dls_predicted_residual_ratio", "dls_predicted_improvement_ratio", "dls_error_alignment_cosine",
        "dls_preview_lambda", "dls_lambda_to_sigma_min", "dls_lambda_to_sigma_max",
        "dls_joint_effort_per_error", "dls_min_directional_headroom", "dls_mean_directional_headroom",
        "dls_toward_nearer_limit_fraction", "dls_predicted_limit_violation_fraction",

        "seed_margin_mean", "seed_margin_standard_deviation", "seed_margin_min", "seed_margin_max",
        "seed_margin_harmonic_mean", "seed_limit_barrier_mean", "seed_limit_barrier_rms", "seed_limit_barrier_max",
        "seed_near_limit_fraction_05", "seed_near_limit_fraction_10", "seed_near_limit_fraction_20",
        "seed_signed_position_mean", "seed_signed_position_rms", "seed_signed_position_max_abs",
        "joint_span_mean_recomputed", "joint_span_standard_deviation", "joint_span_max", "joint_span_condition_ratio",

        "total_link_extent", "maximum_link_extent", "minimum_link_extent", "link_extent_std_recomputed",
        "link_extent_coefficient_of_variation", "dominant_link_fraction", "link_extent_entropy",
        "effective_link_count", "inner_radial_bound_proxy", "normalized_inner_radial_bound",
        "normalized_inner_boundary_gap", "normalized_outer_boundary_gap", "normalized_nearest_radial_boundary_gap",
        "total_absolute_dh_a", "total_absolute_dh_d", "dh_a_extent_fraction",
        "mean_dh_alpha_sin", "mean_dh_alpha_cos", "dh_alpha_circular_concentration",
        "nonzero_twist_fraction", "redundant_dof_fraction", "task_dof_ratio",
        "joint_type_transition_fraction", "longest_joint_type_run_fraction"
    )

    private val JOINT_FEATURE_SUFFIXES = listOf(
        "seed_signed_position", "seed_lower_margin", "seed_upper_margin", "seed_nearest_margin",
        "seed_limit_barrier", "home_signed_position", "seed_home_delta_normalized",
        "seed_sin", "seed_cos", "static_extent_ratio", "active_extent_ratio",
        "dh_alpha_sin", "dh_alpha_cos", "active_theta_sin", "active_theta_cos"
    )

    private const val EPS = 1e-12
    private const val ACTIVE_DH_ZERO_EPS = 1e-12
    private const val LIMIT_EPS = 1e-6
    private const val CONDITION_CAP = 1e12
    private const val HEADROOM_CAP = 1e6
}
