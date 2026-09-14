package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol

/**
 * Stable identity for the physical/numerical robot definition used by ML splits.
 *
 * Display ids and names are deliberately excluded: aliases for the same mechanism
 * must remain in the same held-out partition or the reported transfer score leaks
 * an already-seen morphology into validation/test data.
 */
internal object RobotMorphologyFingerprint {

    fun fromRobot(robot: RobotDefinition): Long =
        fromComponents(
            jointTypes = robot.joints.map { it.type.name },
            theta = robot.dhParameters.map { it.theta },
            d = robot.dhParameters.map { it.d },
            a = robot.dhParameters.map { it.a },
            alpha = robot.dhParameters.map { it.alpha },
            minimums = robot.joints.map { it.minValue },
            maximums = robot.joints.map { it.maxValue },
            homes = robot.joints.map { it.homeValue }
        )

    fun fromComponents(
        jointTypes: List<String>,
        theta: List<Double>,
        d: List<Double>,
        a: List<Double>,
        alpha: List<Double>,
        minimums: List<Double>,
        maximums: List<Double>,
        homes: List<Double>
    ): Long {
        val coordinates = ArrayList<String>(1 + jointTypes.size * 8)
        coordinates += jointTypes.size.toString()
        for (index in jointTypes.indices) {
            coordinates += jointTypes[index].uppercase()
            coordinates += theta[index].canonicalBits()
            coordinates += d[index].canonicalBits()
            coordinates += a[index].canonicalBits()
            coordinates += alpha[index].canonicalBits()
            coordinates += minimums[index].canonicalBits()
            coordinates += maximums[index].canonicalBits()
            coordinates += homes[index].canonicalBits()
        }
        return ScientificRandomProtocol.deriveSeed(
            ROBOT_MORPHOLOGY_DOMAIN,
            *coordinates.toTypedArray()
        )
    }

    /**
     * Stable identity for one physical pre-solve scenario.
     *
     * It intentionally ignores robot display ids and CSV number formatting so an
     * aliased or textually reformatted duplicate cannot cross a grouped split.
     */
    fun sample(
        robotFingerprint: Long,
        target: List<Double>,
        seedValues: List<Double>
    ): Long {
        require(target.size == 3)
        require((target + seedValues).all(Double::isFinite))
        val coordinates = ArrayList<String>(2 + target.size + seedValues.size)
        coordinates += robotFingerprint.toString(16)
        coordinates += seedValues.size.toString()
        target.forEach { coordinates += it.canonicalBits() }
        seedValues.forEach { coordinates += it.canonicalBits() }
        return ScientificRandomProtocol.deriveSeed(
            SAMPLE_SCENARIO_DOMAIN,
            *coordinates.toTypedArray()
        )
    }

    private fun Double.canonicalBits(): String =
        java.lang.Double.doubleToLongBits(if (this == 0.0) 0.0 else this).toString(16)

    private const val ROBOT_MORPHOLOGY_DOMAIN = 0x524F424F
    private const val SAMPLE_SCENARIO_DOMAIN = 0x53414D50
}
