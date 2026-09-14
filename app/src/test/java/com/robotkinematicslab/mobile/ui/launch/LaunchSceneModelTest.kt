package com.robotkinematicslab.mobile.ui.launch

import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSceneModelTest {

    @Test
    fun progressIsWrappedAndRejectsNonFiniteInput() {
        assertEquals(0.25, normalizedLaunchProgress(4.25), 0.0)
        assertEquals(0.75, normalizedLaunchProgress(-0.25), 0.0)
        assertEquals(0.0, normalizedLaunchProgress(Double.NaN), 0.0)
        assertEquals(0.0, normalizedLaunchProgress(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun everyAnimationFrameContainsThreeIndependentFiniteRobots() {
        repeat(1_001) { sample ->
            val frame = buildLaunchSceneFrame(sample.toDouble() / 1_000.0)

            assertEquals(3, frame.robots.size)
            assertEquals(3, frame.robots.map { it.id }.distinct().size)
            assertTrue(frame.dataCorePulse in 0.0..1.0)
            assertFinite(frame.dataCore.x, frame.dataCore.y, frame.dataCore.z)

            frame.robots.forEach { robot ->
                assertEquals(5, robot.jointPositions.size)
                robot.jointPositions.forEach { point ->
                    assertFinite(point.x, point.y, point.z)
                    assertTrue(point.x in -1.6..1.6)
                    assertTrue(point.y in -0.1..1.3)
                    assertTrue(point.z in -1.6..1.6)
                }
                robot.jointPositions.zipWithNext().forEach { (start, end) ->
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val dz = end.z - start.z
                    val length = sqrt(dx * dx + dy * dy + dz * dz)
                    assertTrue("Every visual link must have a real, bounded length", length in 0.05..0.65)
                }
            }
        }
    }

    @Test
    fun animationLoopEndsOnItsStartingPoseWithoutDiscontinuity() {
        val start = buildLaunchSceneFrame(0.0)
        val end = buildLaunchSceneFrame(1.0)
        val justBeforeRestart = buildLaunchSceneFrame(1.0 - 1e-7)

        assertEquals(start, end)
        start.robots.zip(justBeforeRestart.robots).forEach { (first, last) ->
            first.jointPositions.zip(last.jointPositions).forEach { (a, b) ->
                assertEquals(a.x, b.x, 1e-5)
                assertEquals(a.y, b.y, 1e-5)
                assertEquals(a.z, b.z, 1e-5)
            }
        }
        assertEquals(start.dataCore.x, justBeforeRestart.dataCore.x, 1e-5)
        assertEquals(start.dataCore.y, justBeforeRestart.dataCore.y, 1e-5)
        assertEquals(start.dataCore.z, justBeforeRestart.dataCore.z, 1e-5)
    }

    @Test
    fun manualYawIsFiniteAndAlwaysNormalized() {
        assertEquals(0f, normalizedLaunchYaw(Float.NaN), 0f)
        listOf(-100_000f, -7f, -3f, 0f, 3f, 7f, 100_000f).forEach { yaw ->
            assertTrue(normalizedLaunchYaw(yaw) in -Math.PI.toFloat()..Math.PI.toFloat())
        }
    }

    @Test
    fun launchButtonAlwaysChoosesAContrastingContentColor() {
        assertEquals(Color.Black, launchReadableContentColor(Color.White))
        assertEquals(Color.White, launchReadableContentColor(Color(0xFF05090F)))
        assertEquals(Color.Black, launchReadableContentColor(Color(0xFF66E7FF)))
    }

    @Test
    fun rotaryLinkLengthsRemainConstantAcrossTheWholeCycle() {
        val expected =
            listOf(
                LAUNCH_BASE_COLUMN_LENGTH,
                LAUNCH_UPPER_ARM_LENGTH,
                LAUNCH_FOREARM_LENGTH,
                LAUNCH_TOOL_LENGTH
            )

        repeat(401) { sample ->
            buildLaunchSceneFrame(sample / 400.0).robots.forEach { robot ->
                val actual =
                    robot.jointPositions.zipWithNext().map { (start, end) ->
                        val dx = end.x - start.x
                        val dy = end.y - start.y
                        val dz = end.z - start.z
                        sqrt(dx * dx + dy * dy + dz * dz)
                    }
                expected.zip(actual).forEach { (expectedLength, actualLength) ->
                    assertEquals(expectedLength, actualLength, 1e-12)
                }
            }
        }
    }

    private fun assertFinite(vararg values: Double) {
        assertTrue(values.all(Double::isFinite))
    }
}
