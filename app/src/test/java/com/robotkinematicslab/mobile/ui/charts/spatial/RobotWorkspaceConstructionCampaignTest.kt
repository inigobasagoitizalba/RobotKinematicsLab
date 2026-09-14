package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression campaign for the workspace construction engine, including dimensions above the UI's
 * normal 1-10-link demonstration range. */
class RobotWorkspaceConstructionCampaignTest {

    @Test
    fun constructionIsFiniteContinuousAndDeterministicAcrossJointCountsAndTopologies() {
        val factory = DiagnosticRobotFactory()
        val robots =
            buildList {
                add(singleJointRobot())
                for (linkCount in 2..10) {
                    listOf(
                        DiagnosticJointMode.REVOLUTE_ONLY,
                        DiagnosticJointMode.PRISMATIC_ONLY,
                        DiagnosticJointMode.MIXED
                    ).forEach { mode ->
                        add(factory.buildSeedRobot(linkCount, mode, stressLevel = linkCount / 10.0))
                    }
                }
                add(factory.buildSeedRobot(25, DiagnosticJointMode.MIXED, stressLevel = 0.85))
            }

        robots.forEachIndexed { index, robot ->
            val study =
                RobotWorkspaceAnalyzer().analyze(
                    robot = robot,
                    config =
                        RobotWorkspaceAnalysisConfig(
                            sampleCount = 256,
                            randomSeed = 10_000 + index,
                            voxelResolution = 10,
                            replicationCount = 2
                        ),
                    requestedWorkerCount = 2
                )
            val first = buildRobotWorkspaceEnvelopeConstruction(study)
            val second = buildRobotWorkspaceEnvelopeConstruction(study)

            assertEquals("${robot.name}: deterministic construction", first, second)
            assertTrue("${robot.name}: mesh", first.triangles.isNotEmpty())
            first.triangles.forEach { triangle ->
                assertTrue("${robot.name}: finite triangle", triangle.a.isFinite() && triangle.b.isFinite() && triangle.c.isFinite())
                assertTrue("${robot.name}: reveal", triangle.revealFraction in 0.0..1.0)
            }
            (0..100).forEach { step ->
                val values = requireNotNull(constructionJointValues(first, robot, step / 100.0))
                assertEquals(robot.joints.size, values.size)
                values.forEachIndexed { jointIndex, value ->
                    val joint = robot.joints[jointIndex]
                    assertTrue("${robot.name}: joint $jointIndex finite", value.isFinite())
                    assertTrue("${robot.name}: joint $jointIndex bounded", value in joint.minValue..joint.maxValue)
                }
            }
            assertSmoothConstructionMotion(robot, first)
        }
    }

    private fun assertSmoothConstructionMotion(
        robot: RobotDefinition,
        construction: RobotWorkspaceEnvelopeConstruction
    ) {
        assertEquals(WorkspaceEnvelopeConstructionStage.entries.toSet(), construction.stageMotions.keys)
        WorkspaceEnvelopeConstructionStage.entries.zipWithNext().forEach { (before, after) ->
            assertEquals(
                "${robot.name}: stage continuity $before -> $after",
                requireNotNull(construction.stageMotion(before)).jointValues.last(),
                requireNotNull(construction.stageMotion(after)).jointValues.first()
            )
        }
        construction.stageMotions.values.forEach { motion ->
            motion.jointValues.zipWithNext().forEach { (start, end) ->
                robot.joints.indices.forEach { jointIndex ->
                    val joint = robot.joints[jointIndex]
                    val span = joint.maxValue - joint.minValue
                    val direct = abs(end[jointIndex] - start[jointIndex])
                    val shortest =
                        if (joint.type == JointType.REVOLUTE && span >= 2.0 * PI - 1e-6) {
                            minOf(direct, abs(direct - 2.0 * PI), abs(direct + 2.0 * PI))
                        } else {
                            direct
                        }
                    assertTrue(
                        "${robot.name}: normalized animation step ${shortest / span}",
                        shortest / span <= 0.035 + 1e-12
                    )
                }
            }
        }
    }

    private fun singleJointRobot(): RobotDefinition =
        RobotDefinition(
            name = "One-link revolute",
            dhParameters = listOf(DHParameter(theta = 0.0, d = 0.1, a = 0.5, alpha = 0.0)),
            joints =
                listOf(
                    JointDefinition(
                        name = "J1",
                        type = JointType.REVOLUTE,
                        minValue = -PI / 2.0,
                        maxValue = PI / 2.0,
                        homeValue = 0.0
                    )
                )
        )
}
