package com.robotkinematicslab.mobile

import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotLabTutorialRoutingTest {

    @Test
    fun `editor targets reveal setup without choosing a kinematics mode`() {
        assertEquals(
            Layer1Screen.ROBOT_SETUP,
            robotLabScreenForTutorialTarget(TutorialTargets.RobotDhParameters)
        )
        assertNull(robotLabControlModeForTutorialTarget(TutorialTargets.RobotDhParameters))
    }

    @Test
    fun `forward and inverse targets reveal the real view and matching mode`() {
        assertEquals(
            Layer1Screen.ROBOT_VIEW,
            robotLabScreenForTutorialTarget(TutorialTargets.RobotFkMode)
        )
        assertEquals(
            ControlMode.FK,
            robotLabControlModeForTutorialTarget(TutorialTargets.RobotFkMode)
        )
        assertEquals(
            ControlMode.IK,
            robotLabControlModeForTutorialTarget(TutorialTargets.RobotIkMode)
        )
        assertEquals(
            ControlMode.FK,
            robotLabControlModeForTutorialTarget(TutorialTargets.RobotJointSliders)
        )
        assertEquals(
            ControlMode.IK,
            robotLabControlModeForTutorialTarget(TutorialTargets.RobotScene)
        )
    }

    @Test
    fun `unrelated and absent hints never alter robot lab state`() {
        assertNull(robotLabScreenForTutorialTarget(TutorialTargets.Dataset))
        assertNull(robotLabControlModeForTutorialTarget(TutorialTargets.Dataset))
        assertNull(robotLabScreenForTutorialTarget(null))
        assertNull(robotLabControlModeForTutorialTarget(null))
    }

    @Test
    fun `real screen taps report the selected tab even when it is already visible`() {
        val viewAction =
            robotLabScreenSelectionTutorialAction(
                previous = Layer1Screen.ROBOT_SETUP,
                selected = Layer1Screen.ROBOT_VIEW
            )

        assertEquals(TutorialTargets.RobotViewTab, viewAction?.target)
        assertEquals(TutorialInteraction.TAP, viewAction?.interaction)
        val repeatedTap =
            robotLabScreenSelectionTutorialAction(
                previous = Layer1Screen.ROBOT_VIEW,
                selected = Layer1Screen.ROBOT_VIEW
            )
        assertEquals(TutorialTargets.RobotViewTab, repeatedTap?.target)
        assertEquals(TutorialInteraction.TAP, repeatedTap?.interaction)
    }

    @Test
    fun `mode choice and completed gesture have distinct tutorial semantics`() {
        val choice =
            robotLabModeSelectionTutorialAction(
                previous = ControlMode.IK,
                selected = ControlMode.FK
            )
        val fkGesture = robotLabGestureTutorialAction(TutorialTargets.RobotJointSliders)
        val ikGesture = robotLabGestureTutorialAction(TutorialTargets.RobotScene)

        assertEquals(TutorialTargets.RobotFkMode, choice?.target)
        assertEquals(TutorialInteraction.CHOOSE, choice?.interaction)
        assertEquals(TutorialTargets.RobotJointSliders, fkGesture.target)
        assertEquals(TutorialInteraction.DRAG, fkGesture.interaction)
        assertEquals(TutorialTargets.RobotScene, ikGesture.target)
        assertEquals(TutorialInteraction.DRAG, ikGesture.interaction)
        assertNull(robotLabModeSelectionTutorialAction(ControlMode.FK, ControlMode.FK))
    }

    @Test
    fun `show me mode reveal suppresses each side effect generation exactly once`() {
        assertTrue(
            shouldSuppressRobotLabSideEffectForTutorialReveal(
                currentMode = ControlMode.IK,
                revealedMode = ControlMode.IK,
                revealGeneration = 3L,
                consumedGeneration = 2L
            )
        )
        assertFalse(
            shouldSuppressRobotLabSideEffectForTutorialReveal(
                currentMode = ControlMode.IK,
                revealedMode = ControlMode.IK,
                revealGeneration = 3L,
                consumedGeneration = 3L
            )
        )
        assertFalse(
            shouldSuppressRobotLabSideEffectForTutorialReveal(
                currentMode = ControlMode.FK,
                revealedMode = ControlMode.IK,
                revealGeneration = 3L,
                consumedGeneration = 2L
            )
        )
    }
}
