package com.robotkinematicslab.mobile.render

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotSceneStressTest {

    private val fkSolver = ForwardKinematicsSolver()

    @Test
    fun standardDh_jointMarkersAreAttachedToTheCorrectFrames() {
        val positions = listOf(
            Vec3(0.0, 0.0, 0.0),
            Vec3(0.2, 0.0, 0.0),
            Vec3(0.2, 0.3, 0.0),
            Vec3(0.2, 0.3, 0.4)
        )
        val types = listOf(JointType.PRISMATIC, JointType.REVOLUTE, JointType.PRISMATIC)

        val geometry = buildRobotSceneGeometry(positions, types)

        assertEquals(3, geometry.links.size)
        assertEquals(3, geometry.jointMarkers.size)
        assertEquals(types, geometry.links.map { it.jointType })
        assertEquals(types, geometry.jointMarkers.map { it.jointType })
        assertEquals(positions.take(3), geometry.jointMarkers.map { it.position })
        assertEquals(positions.last(), geometry.endEffector)
        assertTrue(geometry.jointMarkers.first().isBase)
        assertTrue(geometry.jointMarkers.drop(1).none { it.isBase })
    }

    @Test
    fun workspaceRadius_accountsForFullPrismaticTravel() {
        val robot = RobotDefinition(
            name = "Radius check",
            dhParameters = listOf(
                DHParameter(theta = 0.0, d = 0.25, a = -0.40, alpha = 0.0),
                DHParameter(theta = 0.2, d = 0.0, a = 0.15, alpha = PI / 2.0)
            ),
            joints = listOf(
                JointDefinition("J1", JointType.REVOLUTE, -PI, PI, 0.0),
                JointDefinition("J2", JointType.PRISMATIC, -0.10, 0.75, 0.20)
            )
        )

        val expectedConservativeRadius =
            kotlin.math.hypot(0.40, 0.25) + kotlin.math.hypot(0.15, 0.75)
        assertEquals(expectedConservativeRadius, robotWorkspaceRadius(robot), 1e-12)
    }

    @Test
    fun deterministicStress_2500RobotsFromOneToTenLinksRemainVisibleAndMapped() {
        val random = Random(STRESS_SEED)
        var checked = 0

        for (linkCount in 1..10) {
            repeat(CASES_PER_LINK_COUNT) { caseIndex ->
                val robot = randomRobot(random, linkCount, caseIndex)
                val state = randomState(random, robot)
                val fk = fkSolver.solve(robot, state)
                val context = failureContext(robot, state, linkCount, caseIndex)

                assertTrue("FK failed: $context status=${fk.status}", fk.status.isAccepted())
                assertEquals("Unexpected position count: $context", linkCount + 1, fk.jointPositions.size)

                val geometry = buildRobotSceneGeometry(
                    jointPositions = fk.jointPositions,
                    jointTypes = robot.joints.map { it.type }
                )
                assertEquals("Link mapping mismatch: $context", linkCount, geometry.links.size)
                assertEquals("Marker mapping mismatch: $context", linkCount, geometry.jointMarkers.size)
                assertEquals("End-effector mismatch: $context", fk.endEffectorPosition, geometry.endEffector)

                val radius = robotWorkspaceRadius(robot)
                val viewport = buildRobotSceneViewport(412f, 320f, radius)

                fk.jointPositions.forEachIndexed { positionIndex, position ->
                    assertTrue("Non-finite FK point $positionIndex: $context", position.isFinite())
                    assertTrue(
                        "Workspace bound underestimated at point $positionIndex: $context radius=$radius point=$position",
                        position.norm() <= radius + 1e-9
                    )

                    val projected = projectRobotScenePoint(position, viewport, DEFAULT_YAW, DEFAULT_PITCH)
                    assertProjectedInsideViewport(projected, viewport, context)
                }

                assertEquals(
                    "Joint types shifted or recoloured: $context",
                    robot.joints.map { it.type },
                    geometry.jointMarkers.map { it.jointType }
                )

                checked += 1
            }
        }

        assertEquals(2_500, checked)
    }

    @Test
    fun projectionAndInteractionUseTheSameAdaptiveScale() {
        val random = Random(STRESS_SEED + 1)

        repeat(2_000) { caseIndex ->
            val radius = 0.10 + random.nextDouble() * 7.90
            val viewport = buildRobotSceneViewport(360f, 320f, radius)
            val point = randomPointInsideSphere(random, radius * 0.80)
            val projected = projectRobotScenePoint(point, viewport, DEFAULT_YAW, DEFAULT_PITCH)
            val recovered = screenToRobotTargetOnHeightPlane(
                screenX = projected.x,
                screenY = projected.y,
                viewport = viewport,
                yaw = DEFAULT_YAW,
                pitch = DEFAULT_PITCH,
                currentY = point.y
            )

            assertNotNull("Projection could not be inverted at case $caseIndex", recovered)
            assertEquals("X round-trip failed at case $caseIndex", point.x, recovered!!.x, 2e-5)
            assertEquals("Y round-trip failed at case $caseIndex", point.y, recovered.y, 2e-5)
            assertEquals("Z round-trip failed at case $caseIndex", point.z, recovered.z, 2e-5)

            val sliderY = sliderYFromTarget(point.y, 20f, 300f, radius)
            val fromSlider = targetYFromSlider(sliderY, 20f, 300f, point, radius)
            assertEquals("Slider round-trip failed at case $caseIndex", point.y, fromSlider.y, 2e-5)
        }
    }

    @Test
    fun sharedProjectionContext_preservesTheOriginalProjectionExactly() {
        val viewport = buildRobotSceneViewport(412f, 320f, 3.75)
        val yaw = -0.73f
        val pitch = 0.41f
        val context = buildRobotSceneProjectionContext(viewport, yaw, pitch)
        val points =
            listOf(
                Vec3.ZERO,
                Vec3(3.75, -3.75, 3.75),
                Vec3(-1.25, 2.50, -0.75),
                Vec3(0.123456789, -0.987654321, 1.23456789)
            )

        points.forEach { point ->
            assertEquals(
                legacyProjectRobotScenePoint(point, viewport, yaw, pitch),
                projectRobotScenePoint(point, context)
            )
        }
    }

    private fun randomRobot(random: Random, linkCount: Int, caseIndex: Int): RobotDefinition {
        val types = List(linkCount) {
            if (random.nextBoolean()) JointType.REVOLUTE else JointType.PRISMATIC
        }

        val dh = types.map { type ->
            DHParameter(
                theta = if (type == JointType.PRISMATIC) random.between(-PI, PI) else 0.0,
                d = if (type == JointType.REVOLUTE) random.between(-0.45, 0.45) else 0.0,
                a = random.between(-0.80, 0.80),
                alpha = TWISTS[random.nextInt(TWISTS.size)]
            )
        }

        val joints = types.mapIndexed { index, type ->
            when (type) {
                JointType.REVOLUTE -> JointDefinition(
                    name = "J${index + 1}",
                    type = type,
                    minValue = -PI,
                    maxValue = PI,
                    homeValue = 0.0
                )

                JointType.PRISMATIC -> {
                    val min = random.between(-0.40, 0.05)
                    val max = random.between(0.10, 0.90)
                    JointDefinition(
                        name = "J${index + 1}",
                        type = type,
                        minValue = min,
                        maxValue = max,
                        homeValue = (min + max) / 2.0
                    )
                }
            }
        }

        return RobotDefinition(
            name = "Scene stress L$linkCount C$caseIndex",
            dhParameters = dh,
            joints = joints
        )
    }

    private fun legacyProjectRobotScenePoint(
        point: Vec3,
        viewport: RobotSceneViewport,
        yaw: Float,
        pitch: Float
    ): RobotScenePoint {
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        val z = point.z.toFloat()
        val cosYaw = kotlin.math.cos(yaw)
        val sinYaw = kotlin.math.sin(yaw)
        val cosPitch = kotlin.math.cos(pitch)
        val sinPitch = kotlin.math.sin(pitch)
        val rotatedX = x * cosYaw - z * sinYaw
        val rotatedZ = x * sinYaw + z * cosYaw
        val rotatedY = y * cosPitch - rotatedZ * sinPitch

        return RobotScenePoint(
            x = viewport.centerX + rotatedX * viewport.pixelsPerMeter,
            y = viewport.centerY - rotatedY * viewport.pixelsPerMeter
        )
    }

    private fun randomState(random: Random, robot: RobotDefinition): RobotState {
        return RobotState(
            robot.joints.map { joint -> random.between(joint.minValue, joint.maxValue) }
        )
    }

    private fun randomPointInsideSphere(random: Random, radius: Double): Vec3 {
        while (true) {
            val candidate = Vec3(
                random.between(-radius, radius),
                random.between(-radius, radius),
                random.between(-radius, radius)
            )
            if (candidate.norm() <= radius) return candidate
        }
    }

    private fun assertProjectedInsideViewport(
        point: RobotScenePoint,
        viewport: RobotSceneViewport,
        context: String
    ) {
        assertTrue("Projected X is not finite: $context", point.x.isFinite())
        assertTrue("Projected Y is not finite: $context", point.y.isFinite())
        assertTrue("Robot clipped on the left: $context x=${point.x}", point.x >= 0f)
        assertTrue(
            "Robot overlaps the target slider: $context x=${point.x}",
            point.x <= viewport.widthPx * 0.86f
        )
        assertTrue("Robot clipped at the top: $context y=${point.y}", point.y >= 0f)
        assertTrue("Robot clipped at the bottom: $context y=${point.y}", point.y <= viewport.heightPx)
    }

    private fun failureContext(
        robot: RobotDefinition,
        state: RobotState,
        linkCount: Int,
        caseIndex: Int
    ): String {
        return "seed=$STRESS_SEED links=$linkCount case=$caseIndex " +
                "types=${robot.joints.map { it.type }} dh=${robot.dhParameters} state=${state.jointValues}"
    }

    private fun FKStatus.isAccepted(): Boolean {
        return this == FKStatus.SUCCESS || this == FKStatus.SUCCESS_WITH_WARNING
    }

    private fun Random.between(min: Double, max: Double): Double {
        return min + nextDouble() * (max - min)
    }

    companion object {
        private const val STRESS_SEED = 0x1B2_2026L
        private const val CASES_PER_LINK_COUNT = 250
        private const val DEFAULT_YAW = -0.65f
        private const val DEFAULT_PITCH = 0.45f
        private val TWISTS = doubleArrayOf(-PI, -PI / 2.0, 0.0, PI / 2.0, PI)
    }
}
