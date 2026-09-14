package com.robotkinematicslab.mobile.ui.robotview

import com.robotkinematicslab.mobile.domain.*
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.*
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.ik.VerifiedIkPath
import com.robotkinematicslab.mobile.service.KinematicsService
import org.junit.Assert.*
import org.junit.Test

class RobotViewIkStateTest {
    private val robot = RobotDefinition("Vertical stage", listOf(DHParameter(0.0, 0.0, 0.0, 0.0)),
        listOf(JointDefinition("J1", JointType.PRISMATIC, 0.0, 1.0, 0.2)))
    private fun context(z: Double = 0.7, model: String? = null) = RobotViewIkContext(robot, Vec3(0.0, 0.0, z), model)
    private fun begin(z: Double = 0.7, model: String? = null) = RobotViewIkState().begin(context(z, model), RobotState(listOf(0.2)))
    private fun response(z: Double = 0.7, status: IKStatus = IKStatus.SUCCESS, error: Double = 0.0,
        path: VerifiedIkPath? = null) = RobotViewIkResponse(
        IKResult(RobotState(listOf(z)), status, status in setOf(IKStatus.SUCCESS, IKStatus.SUCCESS_WITH_WARNING), 4, error,
            metadata = if (path == null) SolverMetadata.fromIKConfig(IKConfig()) else null),
        2.0, path, "Neural proposal did not certify; deterministic fallback was required.", 0.03)

    @Test fun actualSolverSuccessPublishesOnlyIndependentlyCheckedCurrentPosition() {
        val started = begin()
        val result = KinematicsService().computeIK(robot, started.request!!.initialState, started.request.context.target)
        val completed = started.complete(started.request, RobotViewIkResponse(result, 1.0))
        assertEquals(RobotViewIkPhase.VERIFIED, completed.phase)
        assertTrue(completed.checkedErrorMeters!! <= IKConfig().tolerance)
        assertEquals(started.request, completed.request)
        assertEquals(result.state, completed.poseToApply)
    }

    @Test fun targetChangeImmediatelyRevokesGreenSuccessAndLateResponseCannotRestoreIt() {
        val first = begin()
        val successful = first.complete(first.request!!, response())
        val changed = successful.invalidate("Target changed").begin(context(0.8), RobotState(listOf(0.7)))
        assertEquals(RobotViewIkPhase.CALCULATING, changed.phase)
        assertFalse(changed.hasResult); assertNull(changed.checkedErrorMeters); assertNull(changed.poseToApply)
        assertEquals(changed, changed.complete(first.request, response()))
    }

    @Test fun reversedCompletionOrderKeepsLatestRequestAndRejectsLateFailureAndDuplicate() {
        val first = begin()
        val second = first.begin(context(0.8), RobotState(listOf(0.2)))
        val completed = second.complete(second.request!!, response(z = 0.8))
        assertEquals(RobotViewIkPhase.VERIFIED, completed.phase)
        assertEquals(completed, completed.complete(first.request!!, response()))
        assertEquals(completed, completed.fail(first.request, "Old request failed"))
        assertEquals(completed, completed.complete(second.request, response(z = 0.1)))
        assertEquals(0.8, completed.request!!.context.target.z, 0.0)
    }

    @Test fun setupReturnRobotChangeAndModelChangeEachRequireANewVerification() {
        val initial = begin(model = "model-a:hash-a")
        val setup = initial.invalidate("Entered Setup")
        assertEquals(setup, setup.complete(initial.request!!, response(path = VerifiedIkPath.NEURAL_DIRECT)))
        val returned = setup.begin(context(model = "model-a:hash-a"), RobotState(listOf(0.2)))
        assertTrue(returned.request!!.generation > initial.request.generation)
        assertFalse(returned.hasResult)
        val renamedRobot = returned.invalidate().begin(context().copy(robot = robot.copy(name = "New robot")), RobotState(listOf(0.2)))
        assertEquals(renamedRobot, renamedRobot.complete(returned.request, response(path = VerifiedIkPath.NEURAL_DIRECT)))
        val otherModel = renamedRobot.invalidate().begin(context(model = "model-a:changed-hash"), RobotState(listOf(0.2)))
        assertEquals(otherModel, otherModel.complete(initial.request, response(path = VerifiedIkPath.NEURAL_DIRECT)))
    }

    @Test fun fallbackAndRefinementRemainWarningsRatherThanUnqualifiedNeuralSuccess() {
        for (path in listOf(VerifiedIkPath.NEURAL_REFINED, VerifiedIkPath.DETERMINISTIC_FALLBACK)) {
            val start = begin(model = "model:sha256")
            val result = start.complete(start.request!!, response(path = path))
            assertEquals(RobotViewIkPhase.WARNING, result.phase)
            assertEquals(path, result.response!!.aiPath)
            assertEquals(0.0, result.checkedErrorMeters!!, 0.0)
            assertTrue(result.message.contains("independent", ignoreCase = true))
        }
        val start = begin(model = "model:sha256")
        assertEquals(RobotViewIkPhase.VERIFIED, start.complete(start.request!!, response(path = VerifiedIkPath.NEURAL_DIRECT)).phase)
    }

    @Test fun finiteNonconvergedCandidateCanBeShownButNeverCertified() {
        val start = begin()
        val failed = start.complete(start.request!!, response(0.2, IKStatus.NO_CONVERGENCE, 0.5))
        assertEquals(RobotViewIkPhase.FAILED, failed.phase)
        assertNotNull(failed.poseToApply)
        assertEquals(0.5, failed.checkedErrorMeters!!, 1e-15)
        assertTrue(failed.message.contains("not a verified solution"))
    }

    @Test fun corruptNumbersStateStatusErrorToleranceAndSourceAreQuarantined() {
        val start = begin()
        val valid = response()
        val rejected = listOf(
            valid.copy(elapsedMillis = Double.NaN), valid.copy(elapsedMillis = -1.0),
            valid.copy(result = valid.result.copy(iterations = -1)),
            valid.copy(result = valid.result.copy(finalError = Double.NaN)),
            valid.copy(result = valid.result.copy(finalError = -1.0)),
            valid.copy(result = valid.result.copy(state = RobotState(listOf(Double.NaN)))),
            valid.copy(result = valid.result.copy(state = RobotState(listOf(2.0)))),
            valid.copy(result = valid.result.copy(state = RobotState(emptyList()))),
            valid.copy(result = valid.result.copy(converged = false)),
            valid.copy(result = valid.result.copy(state = RobotState(listOf(0.2)), finalError = 0.0)),
            valid.copy(result = valid.result.copy(state = RobotState(listOf(0.2)), finalError = 0.5)),
            valid.copy(result = valid.result.copy(metadata = SolverMetadata.fromIKConfig(IKConfig(tolerance = 0.1)))),
            valid.copy(aiPath = VerifiedIkPath.NEURAL_DIRECT)
        )
        rejected.forEachIndexed { index, response ->
            val failed = start.complete(start.request!!, response)
            assertEquals("corruption $index", RobotViewIkPhase.FAILED, failed.phase)
            assertNull("corruption $index", failed.poseToApply)
            assertNull("corruption $index", failed.checkedErrorMeters)
        }
    }

    @Test fun invalidRequestsAreNotSubmittedAndInputSnapshotsNeverMutateFkOrDh() {
        assertNull(RobotViewIkState().begin(context(Double.NaN), RobotState(listOf(0.2))).request)
        assertNull(RobotViewIkState().begin(context(), RobotState(listOf(2.0))).request)
        assertNull(RobotViewIkState().begin(context().copy(robot = robot.copy(joints = emptyList())), RobotState(listOf(0.2))).request)
        val seed = mutableListOf(0.2)
        val before = KinematicsService().computeFK(robot, RobotState(seed.toList()))
        val state = RobotViewIkState().begin(context(), RobotState(seed))
        seed[0] = 0.8
        assertEquals(listOf(0.2), state.request!!.initialState.jointValues)
        state.complete(state.request, response())
        val after = KinematicsService().computeFK(robot, RobotState(listOf(0.2)))
        assertEquals(before.endEffectorPosition, after.endEffectorPosition)
        assertEquals(DHParameter(0.0, 0.0, 0.0, 0.0), robot.dhParameters.single())
    }

    @Test fun aiAvailabilityMatchesLoadingMissingAndUnsupportedRobotCapacity() {
        assertTrue(robotViewAiAvailability(false, 10, 3).available)
        assertFalse(robotViewAiAvailability(true, 10, 3).available)
        assertFalse(robotViewAiAvailability(false, null, 3).available)
        assertFalse(robotViewAiAvailability(false, 10, null).available)
        assertFalse(robotViewAiAvailability(false, 10, 11).available)
    }
}
