package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedOneMicronIkEngineTest {

    @Test
    fun unchangedNeuralProposalIsOnlyAcceptedAfterIndependentFkVerification() {
        val robot = DatasetRobotPresets().buildDefaults().first().robot
        val seed = RobotState(robot.joints.map { it.homeValue })
        val target = ForwardKinematicsSolver().solve(robot, seed).endEffectorPosition
        val names = OneMicronIkFeatureEncoder.featureNames(OneMicronIkFeatureProfile.KINEMATICS_108)
        val stored = zeroModel(names)

        val result = VerifiedOneMicronIkEngine(stored).solve(robot, seed, target)

        assertTrue(result.verified)
        assertEquals(VerifiedIkPath.NEURAL_DIRECT, result.path)
        assertTrue(result.finalErrorMeters <= ONE_MICRON_METERS)
    }

    private fun zeroModel(names: List<String>): StoredOneMicronIkModel =
        StoredOneMicronIkModel(
            runId = "test",
            profile = OneMicronIkFeatureProfile.KINEMATICS_108,
            solverConfig = oneMicronSolverConfig(),
            featureNames = names,
            normalization = FeatureNormalization(FloatArray(names.size), FloatArray(names.size) { 1f }),
            model =
                LocalIkRegressionModel(
                    inputFeatureCount = names.size,
                    hiddenUnitCount = 4,
                    inputWeights = FloatArray(names.size * 4),
                    hiddenBiases = FloatArray(4),
                    outputWeights = FloatArray(4 * ONE_MICRON_MAX_JOINTS),
                    outputBiases = FloatArray(ONE_MICRON_MAX_JOINTS)
                )
        )
}

