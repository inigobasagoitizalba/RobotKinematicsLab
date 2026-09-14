package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingTutorialRoutingTest {

    @Test
    fun `each detailed training target selects its owning surface`() {
        val expected =
            mapOf(
                TutorialTargets.TrainingSingleRun to TrainingLabMode.CONTROLLED_SINGLE_RUN,
                TutorialTargets.TrainingSingleExecution to TrainingLabMode.CONTROLLED_SINGLE_RUN,
                TutorialTargets.TrainingClosedLoop to TrainingLabMode.CLOSED_LOOP_AUTOMATION,
                TutorialTargets.TrainingComparison to TrainingLabMode.RESULT_COMPARISON,
                TutorialTargets.TrainingExplainability to TrainingLabMode.EXPLAINABILITY,
                TutorialTargets.TrainingOneMicron to TrainingLabMode.ONE_MICRON_IK,
                TutorialTargets.TrainingScientificEvidence to TrainingLabMode.SCIENTIFIC_EVIDENCE
            )

        expected.forEach { (target, mode) ->
            assertEquals(mode, trainingModeForTutorialTarget(target))
        }
    }

    @Test
    fun `menu root and unrelated hints preserve the user selected mode`() {
        assertNull(trainingModeForTutorialTarget(TutorialTargets.TrainingModeMenu))
        assertNull(trainingModeForTutorialTarget(TutorialTargets.Diagnostics))
        assertNull(trainingModeForTutorialTarget(null))
    }
}
