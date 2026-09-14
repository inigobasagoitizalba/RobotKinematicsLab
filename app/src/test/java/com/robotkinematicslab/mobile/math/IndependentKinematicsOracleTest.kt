package com.robotkinematicslab.mobile.math

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.domain.result.IKStatus
import com.robotkinematicslab.mobile.math.utility.AngleNormalizer
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.ik.InverseKinematicsSolver
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-corruption checks whose expected values do not call Matrix4.fromDH or either FK path.
 * This prevents two implementations with the same DH convention defect from validating each other.
 */
class IndependentKinematicsOracleTest {

    @Test
    fun planarTwoLinkFkMatchesClosedFormOracleAcrossQuadrantsAndMultipleTurns() {
        val robot = planarRobot()
        val solver = ForwardKinematicsSolver()
        val cases =
            listOf(
                0.0 to 0.0,
                PI / 2.0 to -PI / 2.0,
                -PI / 3.0 to PI / 5.0,
                PI - 1e-9 to -PI + 2e-9,
                7.0 * PI + 0.2 to -5.0 * PI - 0.4
            )

        cases.forEach { (q1, q2) ->
            val expectedJoint1 =
                Vec3(
                    x = FIRST_LINK * cos(q1),
                    y = FIRST_LINK * sin(q1),
                    z = FIRST_D
                )
            val expectedEnd =
                Vec3(
                    x = FIRST_LINK * cos(q1) + SECOND_LINK * cos(q1 + q2),
                    y = FIRST_LINK * sin(q1) + SECOND_LINK * sin(q1 + q2),
                    z = FIRST_D + SECOND_D
                )
            val full = solver.solve(robot, RobotState(listOf(q1, q2)))
            val fast = solver.solvePositionOnly(robot, listOf(q1, q2))

            assertUsable(full.status)
            assertUsable(fast.status)
            assertVectorEquals(expectedJoint1, full.jointPositions[1], ORACLE_TOLERANCE)
            assertVectorEquals(expectedEnd, full.endEffectorPosition, ORACLE_TOLERANCE)
            assertVectorEquals(expectedEnd, fast.position, ORACLE_TOLERANCE)

            val totalAngle = q1 + q2
            assertEquals(cos(totalAngle), full.endEffectorTransform.m[0][0], ORACLE_TOLERANCE)
            assertEquals(-sin(totalAngle), full.endEffectorTransform.m[0][1], ORACLE_TOLERANCE)
            assertEquals(sin(totalAngle), full.endEffectorTransform.m[1][0], ORACLE_TOLERANCE)
            assertEquals(cos(totalAngle), full.endEffectorTransform.m[1][1], ORACLE_TOLERANCE)
        }
    }

    @Test
    fun fullTurnShiftIsAMetamorphicSymmetryOfFkPose() {
        val robot = planarRobot()
        val solver = ForwardKinematicsSolver()
        val reference = solver.solve(robot, RobotState(listOf(0.37, -1.11)))
        val shifted = solver.solve(robot, RobotState(listOf(0.37 + 2.0 * PI, -1.11 - 4.0 * PI)))

        assertUsable(reference.status)
        assertUsable(shifted.status)
        assertVectorEquals(reference.endEffectorPosition, shifted.endEffectorPosition, 2e-12)
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                assertEquals(
                    "pose[$row][$column] must be periodic",
                    reference.endEffectorTransform.m[row][column],
                    shifted.endEffectorTransform.m[row][column],
                    2e-12
                )
            }
        }
    }

    @Test
    fun inverseKinematicsPreservesEquivalentPeriodicBranchesAndMeetsAnAnalyticResidualOracle() {
        val robot = singleTurnRobot()
        val config = IKConfig(maxIterations = 200, tolerance = 1e-8, damping = 1e-4, maxStep = 0.5)
        val solver = InverseKinematicsSolver(ForwardKinematicsSolver(), config)
        val targetAngle = 0.75
        val target = Vec3(cos(targetAngle), sin(targetAngle), 0.0)

        val first = solver.solve(robot, RobotState(listOf(-0.4)), target)
        val shifted = solver.solve(robot, RobotState(listOf(-0.4 + 2.0 * PI)), target)

        assertIkSuccess(first.status)
        assertIkSuccess(shifted.status)
        assertTrue(first.converged)
        assertTrue(shifted.converged)

        val firstAngle = first.state.jointValues.single()
        val shiftedAngle = shifted.state.jointValues.single()
        val firstOracleResidual = hypot(cos(firstAngle) - target.x, sin(firstAngle) - target.y)
        val shiftedOracleResidual = hypot(cos(shiftedAngle) - target.x, sin(shiftedAngle) - target.y)

        assertEquals(firstOracleResidual, first.finalError, 2e-12)
        assertEquals(shiftedOracleResidual, shifted.finalError, 2e-12)
        assertTrue(firstOracleResidual <= config.tolerance)
        assertTrue(shiftedOracleResidual <= config.tolerance)
        assertEquals(2.0 * PI, shiftedAngle - firstAngle, 2e-7)
    }

    @Test
    fun angleNormalizationMatchesAnIndependentCircularOracleAndIsPeriodic() {
        val normalizer = AngleNormalizer()
        val angles = listOf(-123.456, -7.2, -0.25, 0.0, 0.25, 19.8, 123.456)

        angles.forEach { angle ->
            val circularOracle = atan2(sin(angle), cos(angle))
            assertEquals(circularOracle, normalizer.normalizeRadians(angle), 2e-13)
            for (turns in -10..10) {
                assertEquals(
                    circularOracle,
                    normalizer.normalizeRadians(angle + turns * 2.0 * PI),
                    2e-13
                )
            }
        }
    }

    private fun planarRobot(): RobotDefinition =
        RobotDefinition(
            name = "Closed-form planar 2R",
            dhParameters =
                listOf(
                    DHParameter(theta = 0.0, d = FIRST_D, a = FIRST_LINK, alpha = 0.0),
                    DHParameter(theta = 0.0, d = SECOND_D, a = SECOND_LINK, alpha = 0.0)
                ),
            joints =
                listOf(
                    JointDefinition("Q1", JointType.REVOLUTE, -16.0 * PI, 16.0 * PI, 0.0),
                    JointDefinition("Q2", JointType.REVOLUTE, -16.0 * PI, 16.0 * PI, 0.0)
                )
        )

    private fun singleTurnRobot(): RobotDefinition =
        RobotDefinition(
            name = "Analytic one-link IK",
            dhParameters = listOf(DHParameter(theta = 0.0, d = 0.0, a = 1.0, alpha = 0.0)),
            joints =
                listOf(
                    JointDefinition("Q", JointType.REVOLUTE, -4.0 * PI, 4.0 * PI, 0.0)
                )
        )

    private fun assertVectorEquals(expected: Vec3, actual: Vec3, tolerance: Double) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.z, actual.z, tolerance)
    }

    private fun assertUsable(status: FKStatus) {
        assertTrue(status == FKStatus.SUCCESS || status == FKStatus.SUCCESS_WITH_WARNING)
    }

    private fun assertIkSuccess(status: IKStatus) {
        assertTrue(status == IKStatus.SUCCESS || status == IKStatus.SUCCESS_WITH_WARNING)
    }

    private companion object {
        const val FIRST_LINK = 0.7
        const val SECOND_LINK = 1.2
        const val FIRST_D = 0.3
        const val SECOND_D = -0.1
        const val ORACLE_TOLERANCE = 2e-12
    }
}
