package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneMicronIkFeatureEncoderTest {

    @Test
    fun featureProfilesHaveStableRuntimeReproducibleSchemas() {
        val robot = DatasetRobotPresets().buildDefaults().last().robot
        val seed = RobotState(robot.joints.map { it.homeValue })
        val target = ForwardKinematicsSolver().solve(robot, seed).endEffectorPosition

        val baseline = OneMicronIkFeatureEncoder.encode(
            robot, seed, target, oneMicronSolverConfig(), OneMicronIkFeatureProfile.KINEMATICS_108
        )
        val context = OneMicronIkFeatureEncoder.encode(
            robot, seed, target, oneMicronSolverConfig(), OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361
        )

        assertEquals(108, baseline.size)
        assertEquals(361, context.size)
        assertEquals(108, OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108).size)
        assertEquals(361, OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361).size)
        assertTrue(baseline.all(Float::isFinite))
        assertTrue(context.all(Float::isFinite))
    }

    @Test
    fun memoryEstimatorAllowsFewerContextRowsInsideTheSamePhoneBudget() {
        val budget = 96L * 1024L * 1024L
        val baselineRows = OneMicronIkMemoryEstimator.safeRowLimit(OneMicronIkFeatureProfile.KINEMATICS_108, budget)
        val contextRows = OneMicronIkMemoryEstimator.safeRowLimit(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361, budget)

        assertTrue(contextRows >= 30)
        assertTrue(contextRows < baselineRows)
        assertTrue(
            OneMicronIkMemoryEstimator.estimatedWorkingSetBytes(
                OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361,
                contextRows
            ) <= budget
        )
    }
}
