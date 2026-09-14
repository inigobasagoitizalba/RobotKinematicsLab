package com.robotkinematicslab.mobile.render

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RobotScene3DRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneToTenLinks_andBothBaseJointShapes_renderWithCorrectPaletteInsideViewport() {
        val scenes = buildScenes()
        var currentScene by mutableStateOf(scenes.first())

        composeRule.setContent {
            RobotScene3D(
                jointPositions = currentScene.positions,
                jointTypes = currentScene.types,
                targetPoint = null,
                endEffector = currentScene.positions.last(),
                workspaceRadius = currentScene.workspaceRadius,
                enabled = false,
                onTargetSelected = {},
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SCENE_TAG)
            )
        }

        scenes.forEach { scene ->
            composeRule.runOnUiThread { currentScene = scene }
            composeRule.waitForIdle()

            val image = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
            val pixels = image.toPixelMap()
            val context = "scene=${scene.name} types=${scene.types} radius=${scene.workspaceRadius}"

            assertTrue("Render surface is unexpectedly small: $context", pixels.width > 100)
            assertTrue("Render surface is unexpectedly short: $context", pixels.height > 100)
            assertTrue(
                "End effector colour is missing: $context",
                countColour(pixels, EndEffectorColor) > 20
            )

            if (JointType.REVOLUTE in scene.types) {
                assertTrue(
                    "Revolute link colour is missing: $context",
                    countColour(pixels, RevoluteLinkColor) > 10
                )
                assertTrue(
                    "Revolute joint colour is missing: $context",
                    countColour(pixels, RevoluteJointColor) > 20
                )
            }

            if (JointType.PRISMATIC in scene.types) {
                assertTrue(
                    "Prismatic link colour is missing: $context",
                    countColour(pixels, PrismaticLinkColor) > 10
                )
                assertTrue(
                    "Prismatic joint colour is missing: $context",
                    countColour(pixels, PrismaticJointColor) > 20
                )
            }

            val bounds = robotPaletteBounds(pixels)
            assertTrue("No robot pixels found: $context", bounds.pixelCount > 50)
            assertTrue("Robot is clipped on the left: $context bounds=$bounds", bounds.minX > 0)
            assertTrue("Robot is clipped on the right: $context bounds=$bounds", bounds.maxX < pixels.width - 1)
            assertTrue("Robot is clipped at the top: $context bounds=$bounds", bounds.minY > 0)
            assertTrue("Robot is clipped at the bottom: $context bounds=$bounds", bounds.maxY < pixels.height - 1)
        }

        assertEquals(11, scenes.size)
    }

    private fun buildScenes(): List<RenderScene> {
        val solver = ForwardKinematicsSolver()
        val robots = (1..10).map { linkCount ->
            buildRobot(linkCount, prismaticBase = false)
        } + buildRobot(linkCount = 4, prismaticBase = true)

        return robots.map { robot ->
            val state = RobotState(robot.joints.map { it.homeValue })
            val fk = solver.solve(robot, state)
            assertTrue(
                "Render fixture FK failed for ${robot.name}: ${fk.status}",
                fk.status == FKStatus.SUCCESS || fk.status == FKStatus.SUCCESS_WITH_WARNING
            )
            RenderScene(
                name = robot.name,
                positions = fk.jointPositions,
                types = robot.joints.map { it.type },
                workspaceRadius = robotWorkspaceRadius(robot)
            )
        }
    }

    private fun buildRobot(linkCount: Int, prismaticBase: Boolean): RobotDefinition {
        val types = List(linkCount) { index ->
            when {
                index == 0 && prismaticBase -> JointType.PRISMATIC
                index == 0 -> JointType.REVOLUTE
                index % 3 == 1 -> JointType.PRISMATIC
                else -> JointType.REVOLUTE
            }
        }

        return RobotDefinition(
            name = if (prismaticBase) "4-link P-base" else "$linkCount-link render case",
            dhParameters = types.mapIndexed { index, type ->
                DHParameter(
                    theta = if (type == JointType.PRISMATIC) index * 0.17 else 0.0,
                    d = if (type == JointType.REVOLUTE) 0.04 * ((index + 1) % 3) else 0.0,
                    a = 0.16 + 0.025 * (index % 4),
                    alpha = when (index % 4) {
                        1 -> PI / 2.0
                        3 -> -PI / 2.0
                        else -> 0.0
                    }
                )
            },
            joints = types.mapIndexed { index, type ->
                when (type) {
                    JointType.REVOLUTE -> JointDefinition(
                        name = "J${index + 1}",
                        type = type,
                        minValue = -PI,
                        maxValue = PI,
                        homeValue = 0.18 * index
                    )

                    JointType.PRISMATIC -> JointDefinition(
                        name = "J${index + 1}",
                        type = type,
                        minValue = 0.02,
                        maxValue = 0.42,
                        homeValue = 0.16 + 0.015 * index
                    )
                }
            }
        )
    }

    private fun countColour(pixels: androidx.compose.ui.graphics.PixelMap, colour: Color): Int {
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] == colour) count += 1
            }
        }
        return count
    }

    private fun robotPaletteBounds(pixels: androidx.compose.ui.graphics.PixelMap): PixelBounds {
        val palette = setOf(
            RevoluteLinkColor,
            PrismaticLinkColor,
            RevoluteJointColor,
            PrismaticJointColor,
            EndEffectorColor
        )
        var minX = pixels.width
        var minY = pixels.height
        var maxX = -1
        var maxY = -1
        var count = 0

        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] in palette) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    count += 1
                }
            }
        }

        return PixelBounds(minX, minY, maxX, maxY, count)
    }

    private data class RenderScene(
        val name: String,
        val positions: List<com.robotkinematicslab.mobile.math.utility.Vec3>,
        val types: List<JointType>,
        val workspaceRadius: Double
    )

    private data class PixelBounds(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val pixelCount: Int
    )

    companion object {
        private const val SCENE_TAG = "robot-scene-stress-test"
    }
}
