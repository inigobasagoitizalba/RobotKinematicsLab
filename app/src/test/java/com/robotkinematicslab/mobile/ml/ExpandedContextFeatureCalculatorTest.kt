package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.ExpandedContextFeatureCalculator
import com.robotkinematicslab.mobile.ml.data.ExpandedContextInput
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedContextFeatureCalculatorTest {

    @Test
    fun profileContractsRemainSeparateAndExplicit() {
        assertEquals(108, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.BASELINE_KINEMATICS).size)
        assertEquals(130, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_ENHANCED).size)
        assertEquals(383, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_EXPANDED).size)
    }

    @Test
    fun planarSeedGeometryAndAllExpandedFeaturesAreFinite() {
        val input =
            ExpandedContextInput(
                jointTypes = listOf("REVOLUTE", "REVOLUTE"),
                theta = listOf(0.0, 0.0),
                d = listOf(0.0, 0.0),
                a = listOf(1.0, 1.0),
                alpha = listOf(0.0, 0.0),
                minimums = listOf(-PI, -PI),
                maximums = listOf(PI, PI),
                homes = listOf(0.0, 0.0),
                seeds = listOf(0.0, 0.0),
                targetX = 1.0,
                targetY = 1.0,
                targetZ = 0.0,
                tolerance = 1e-6,
                damping = 1e-3,
                maxStep = 0.2
            )
        val names = ExpandedContextFeatureCalculator.featureNames(10)
        val values = ExpandedContextFeatureCalculator.calculate(input, 10)
        val byName = names.indices.associate { names[it] to values[it] }

        assertEquals(names.size, values.size)
        assertTrue(values.all(Double::isFinite))
        assertEquals(2.0, byName.getValue("seed_end_effector_x"), 1e-10)
        assertEquals(0.0, byName.getValue("seed_end_effector_y"), 1e-10)
        assertEquals(-1.0, byName.getValue("seed_target_error_x"), 1e-10)
        assertEquals(1.0, byName.getValue("seed_target_error_y"), 1e-10)
        assertEquals(sqrt(2.0), byName.getValue("recomputed_initial_error"), 1e-10)
        assertEquals(0.0, byName.getValue("joint_1_seed_signed_position"), 1e-10)
        assertEquals(0.5, byName.getValue("joint_1_seed_nearest_margin"), 1e-10)
    }

    @Test
    fun mixedChainUsesPrismaticSeedAsItsActiveDhDistance() {
        val input =
            ExpandedContextInput(
                jointTypes = listOf("PRISMATIC"),
                theta = listOf(0.0),
                d = listOf(0.0),
                a = listOf(0.0),
                alpha = listOf(0.0),
                minimums = listOf(0.0),
                maximums = listOf(2.0),
                homes = listOf(0.5),
                seeds = listOf(1.25),
                targetX = 0.0,
                targetY = 0.0,
                targetZ = 1.5,
                tolerance = 1e-6,
                damping = 1e-3,
                maxStep = 0.2
            )
        val names = ExpandedContextFeatureCalculator.featureNames(10)
        val values = ExpandedContextFeatureCalculator.calculate(input, 10)
        val byName = names.indices.associate { names[it] to values[it] }

        assertEquals(1.25, byName.getValue("seed_end_effector_z"), 1e-10)
        assertEquals(0.25, byName.getValue("seed_target_error_z"), 1e-10)
        assertEquals(1.0, byName.getValue("jacobian_sigma_max"), 1e-10)
    }

    @Test
    fun hostileCoordinatesOutsideLimitsAreRejectedBeforeTheyBecomeFiniteLookingFeatures() {
        val valid = singleRevoluteInput()

        assertThrows(IllegalArgumentException::class.java) {
            ExpandedContextFeatureCalculator.calculate(valid.copy(seeds = listOf(1.01)), 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpandedContextFeatureCalculator.calculate(valid.copy(homes = listOf(-1.01)), 10)
        }
    }

    @Test
    fun contradictoryActiveDhFieldIsRejectedInsteadOfBeingSilentlyIgnored() {
        val valid = singleRevoluteInput()

        assertThrows(IllegalArgumentException::class.java) {
            ExpandedContextFeatureCalculator.calculate(valid.copy(theta = listOf(0.2)), 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpandedContextFeatureCalculator.calculate(
                valid.copy(
                    jointTypes = listOf("PRISMATIC"),
                    theta = listOf(0.3),
                    d = listOf(0.2),
                    minimums = listOf(0.0),
                    maximums = listOf(1.0),
                    homes = listOf(0.5),
                    seeds = listOf(0.5)
                ),
                10
            )
        }
    }

    private fun singleRevoluteInput() =
        ExpandedContextInput(
            jointTypes = listOf("REVOLUTE"),
            theta = listOf(0.0),
            d = listOf(0.1),
            a = listOf(0.5),
            alpha = listOf(0.0),
            minimums = listOf(-1.0),
            maximums = listOf(1.0),
            homes = listOf(0.0),
            seeds = listOf(0.25),
            targetX = 0.3,
            targetY = 0.2,
            targetZ = 0.1,
            tolerance = 1e-6,
            damping = 1e-3,
            maxStep = 0.2
        )
}
