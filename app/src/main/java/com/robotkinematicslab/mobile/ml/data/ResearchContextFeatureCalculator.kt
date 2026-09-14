package com.robotkinematicslab.mobile.ml.data

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Versioned, opt-in nonlinear context added after the frozen 383-variable contract.
 *
 * These 85 candidates are computed before the solver runs. They deliberately contain no label,
 * convergence flag, iteration count or final error, so they cannot leak the answer into training.
 */
object ResearchContextFeatureCalculator {

    const val SCHEMA_ID = "research-context-v2-85"
    const val FEATURE_COUNT_AT_TEN_JOINTS = 85

    fun featureNames(maximumJointCount: Int): List<String> = buildList {
        addAll(GLOBAL_NAMES)
        repeat(maximumJointCount) { index ->
            JOINT_SUFFIXES.forEach { suffix -> add("research_v2_joint_${index + 1}_$suffix") }
        }
    }

    fun calculate(input: ExpandedContextInput, maximumJointCount: Int): DoubleArray {
        validate(input, maximumJointCount)
        val count = input.jointTypes.size
        val spans = List(count) { input.maximums[it] - input.minimums[it] }
        val centers = List(count) { (input.maximums[it] + input.minimums[it]) * 0.5 }
        val halfSpans = spans.map { max(it * 0.5, EPS) }
        val pressure = List(count) { index ->
            abs((input.seeds[index] - centers[index]) / halfSpans[index]).coerceIn(-1.0, 1.0)
        }
        val margins = pressure.map { (1.0 - it) * 0.5 }
        val homeDisplacement = List(count) { index ->
            ((input.seeds[index] - input.homes[index]) / halfSpans[index]).coerceIn(-2.0, 2.0)
        }
        val extents = List(count) { index ->
            val activeD =
                if (input.jointTypes[index].equals("PRISMATIC", true)) input.seeds[index]
                else input.d[index]
            hypot(input.a[index], activeD)
        }
        val reach = extents.sum().coerceAtLeast(EPS)
        val maximumExtent = extents.maxOrNull() ?: 0.0
        val innerBound = max(0.0, maximumExtent - (reach - maximumExtent))
        val targetRadius = hypot(hypot(input.targetX, input.targetY), input.targetZ)
        val utilization = targetRadius / reach
        val outerPressure = max(0.0, utilization)
        val innerPressure = max(0.0, (innerBound - targetRadius) / reach)
        val nearestBoundaryDistance =
            min(abs(targetRadius - innerBound), abs(reach - targetRadius)) / reach
        val pressureSquares = pressure.map { it * it }
        val pressureFourths = pressureSquares.map { it * it }
        val dominantLink = maximumExtent / reach
        val innerVoidFraction = innerBound / reach

        val values = ArrayList<Double>(featureNames(maximumJointCount).size)
        values += listOf(
            outerPressure * outerPressure,
            square(square(outerPressure)),
            innerPressure * innerPressure,
            1.0 / max(nearestBoundaryDistance, MARGIN_FLOOR),
            if (nearestBoundaryDistance <= 0.05) 1.0 else 0.0,
            if (nearestBoundaryDistance <= 0.10) 1.0 else 0.0,
            pressureSquares.average(),
            pressureFourths.average(),
            sqrt(pressureSquares.map(::square).average()),
            standardDeviation(pressure),
            margins.count { it <= 0.02 }.toDouble() / count,
            margins.count { it <= 0.05 }.toDouble() / count,
            dominantLink * dominantLink,
            innerVoidFraction * innerVoidFraction,
            gini(extents)
        )

        repeat(maximumJointCount) { index ->
            if (index >= count) {
                repeat(JOINT_SUFFIXES.size) { values += 0.0 }
            } else {
                val pressureSquared = pressureSquares[index]
                val displacementSquared = square(homeDisplacement[index])
                val extentShare = extents[index] / reach
                values += listOf(
                    pressureSquared,
                    pressureFourths[index],
                    1.0 / max(margins[index], MARGIN_FLOOR),
                    exp(-10.0 * margins[index]),
                    displacementSquared,
                    displacementSquared * pressureSquared,
                    extentShare * pressureSquared
                )
            }
        }

        check(values.size == featureNames(maximumJointCount).size) {
            "Research context schema mismatch: ${values.size} values for ${featureNames(maximumJointCount).size} names."
        }
        check(values.all(Double::isFinite)) { "Research context produced a non-finite feature." }
        return values.toDoubleArray()
    }

    private fun validate(input: ExpandedContextInput, maximumJointCount: Int) {
        val count = input.jointTypes.size
        require(count in 1..maximumJointCount)
        val lists =
            listOf(
                input.theta,
                input.d,
                input.a,
                input.alpha,
                input.minimums,
                input.maximums,
                input.homes,
                input.seeds
            )
        require(lists.all { it.size == count })
        require(lists.flatten().all(Double::isFinite))
        require(input.jointTypes.all { it.equals("REVOLUTE", true) || it.equals("PRISMATIC", true) })
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
        require(listOf(input.targetX, input.targetY, input.targetZ).all(Double::isFinite))
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        var norm = 0.0
        values.forEach { value -> norm = hypot(norm, value - mean) }
        return norm / sqrt(values.size.toDouble())
    }

    private fun gini(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        if (mean <= EPS) return 0.0
        val absoluteDifferences = values.sumOf { left -> values.sumOf { right -> abs(left - right) } }
        return absoluteDifferences / (2.0 * values.size * values.size * mean)
    }

    private fun square(value: Double): Double = value * value

    private val GLOBAL_NAMES =
        listOf(
            "research_v2_workspace_outer_pressure_squared",
            "research_v2_workspace_outer_pressure_fourth",
            "research_v2_workspace_inner_pressure_squared",
            "research_v2_workspace_boundary_inverse_distance",
            "research_v2_workspace_boundary_within_05",
            "research_v2_workspace_boundary_within_10",
            "research_v2_joint_pressure_squared_mean",
            "research_v2_joint_pressure_fourth_mean",
            "research_v2_joint_pressure_squared_rms",
            "research_v2_joint_pressure_dispersion",
            "research_v2_critical_joint_fraction_02",
            "research_v2_critical_joint_fraction_05",
            "research_v2_dominant_link_fraction_squared",
            "research_v2_inner_void_fraction_squared",
            "research_v2_link_extent_gini"
        )

    private val JOINT_SUFFIXES =
        listOf(
            "limit_pressure_squared",
            "limit_pressure_fourth",
            "inverse_nearest_margin",
            "limit_exponential_pressure",
            "home_displacement_squared",
            "home_displacement_x_limit_pressure",
            "link_leverage_x_limit_pressure"
        )

    private const val EPS = 1e-12
    private const val ACTIVE_DH_ZERO_EPS = 1e-12
    private const val MARGIN_FLOOR = 1e-4
}
