package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.ExpandedContextInput
import com.robotkinematicslab.mobile.ml.data.ResearchContextFeatureCalculator
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchContextFeatureCalculatorTest {

    @Test
    fun `frozen profiles keep their contracts and research v2 adds exactly 85 candidates`() {
        assertEquals(108, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.BASELINE_KINEMATICS).size)
        assertEquals(130, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_ENHANCED).size)
        assertEquals(383, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_EXPANDED).size)
        assertEquals(468, ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_RESEARCH_V2).size)
        assertEquals(85, ResearchContextFeatureCalculator.featureNames(10).size)
    }

    @Test
    fun `all research candidates are finite at centres and exact limits`() {
        listOf(0.0, -1.0, 1.0).forEach { seed ->
            val values = ResearchContextFeatureCalculator.calculate(input(seed), maximumJointCount = 10)
            assertEquals(85, values.size)
            assertTrue(values.all(Double::isFinite))
        }
    }

    @Test
    fun `nonlinear pressure rises monotonically towards a joint limit`() {
        val names = ResearchContextFeatureCalculator.featureNames(10)
        val pressureIndex = names.indexOf("research_v2_joint_1_limit_pressure_squared")
        val centre = ResearchContextFeatureCalculator.calculate(input(0.0), 10)[pressureIndex]
        val middle = ResearchContextFeatureCalculator.calculate(input(0.5), 10)[pressureIndex]
        val limit = ResearchContextFeatureCalculator.calculate(input(1.0), 10)[pressureIndex]

        assertEquals(0.0, centre, 1e-12)
        assertTrue(middle > centre)
        assertTrue(limit > middle)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `research candidates reject a seed outside its declared limits`() {
        ResearchContextFeatureCalculator.calculate(input(1.01), 10)
    }

    @Test
    fun `unknown joint types and corrupt home coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ResearchContextFeatureCalculator.calculate(
                input(0.0).copy(jointTypes = listOf("ALIEN")),
                10
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResearchContextFeatureCalculator.calculate(
                input(0.0).copy(homes = listOf(1.01)),
                10
            )
        }
    }

    @Test
    fun `active dh corruption is rejected consistently with the robot contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            ResearchContextFeatureCalculator.calculate(
                input(0.0).copy(theta = listOf(0.01)),
                10
            )
        }
    }

    private fun input(seed: Double) =
        ExpandedContextInput(
            jointTypes = listOf("REVOLUTE"),
            theta = listOf(0.0),
            d = listOf(0.1),
            a = listOf(0.5),
            alpha = listOf(0.0),
            minimums = listOf(-1.0),
            maximums = listOf(1.0),
            homes = listOf(0.0),
            seeds = listOf(seed),
            targetX = 0.3,
            targetY = 0.2,
            targetZ = 0.1,
            tolerance = 1e-6,
            damping = 1e-3,
            maxStep = 0.2
        )
}
