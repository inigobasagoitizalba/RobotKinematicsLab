package com.robotkinematicslab.mobile.ui.charts.spatial

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.result.FKStatus
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-rendered regression coverage for animated workspace construction.
 *
 * These checks deliberately exercise the real Compose canvas. They prevent a mathematically valid
 * reconstruction from regressing into the two visual failures reported during development:
 * exterior empty space rendered as red dead space and a 2R sweep that hides its central cavity.
 * The PNG files are retained in the app's external test directory so the same frames can be
 * inspected by a person after the assertions pass.
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceAnimatedConstructionVisualRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun prismaticExteriorAndTwoLinkInteriorRenderWithTheExpectedEvidence() {
        val prismaticStudy = buildPrismaticStudy()
        val prismaticConstruction = buildRobotWorkspaceEnvelopeConstruction(prismaticStudy)
        assertEquals(WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION, prismaticConstruction.strategy)
        assertEquals(0, prismaticConstruction.candidateDeadSpaceVoxelCount)
        assertFalse(prismaticConstruction.hasInnerDeadSpace)

        val twoLinkStudy = buildTwoLinkStudy()
        val twoLinkConstruction = buildRobotWorkspaceEnvelopeConstruction(twoLinkStudy)
        assertEquals(WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP, twoLinkConstruction.strategy)
        assertTrue(twoLinkConstruction.hasInnerDeadSpace)

        val threeLinkStudy = buildArticulatedThreeLinkStudy()
        val threeLinkConstruction = buildRobotWorkspaceEnvelopeConstruction(threeLinkStudy)
        assertEquals(WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP, threeLinkConstruction.strategy)
        assertTrue(threeLinkConstruction.outerCurve.size > 2)
        assertTrue(threeLinkConstruction.innerCurve.size > 2)

        var frame by
            mutableStateOf(
                VisualFrame(
                    study = prismaticStudy,
                    construction = prismaticConstruction,
                    progress = 1.0
                )
            )
        composeRule.setContent {
            RobotWorkspaceScene3D(
                study = frame.study,
                revealedSampleCount = frame.study.samples.size,
                camera = WorkspaceCamera(yaw = -0.65f, pitch = 0.45f, zoom = 1.0f),
                displayOptions =
                    RobotWorkspaceDisplayOptions(
                        showReachableCore = true,
                        showReachableBoundary = true,
                        showUnobservedCandidates = frame.showUnobservedCandidates,
                        showJointLimits = false,
                        showDeadSpaceXRay = true
                    ),
                renderMode = RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION,
                envelopeConstruction = frame.construction,
                constructionProgress = frame.progress,
                selectedVoxelId = null,
                robotPose = buildPose(frame),
                endEffectorTrace = emptyList(),
                onCameraChange = {},
                onVoxelSelected = {},
                modifier = Modifier.fillMaxSize().testTag(SCENE_TAG)
            )
        }

        composeRule.waitForIdle()
        val prismaticImage = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
        saveEvidence("workspace-animation-4p-final-no-exterior-dead-space.png", prismaticImage.asAndroidBitmap())
        val prismaticColours = countScientificColours(prismaticImage.toPixelMap())
        assertTrue("The reachable 4P envelope was not drawn", prismaticColours.green > 100)
        composeRule.runOnUiThread { frame = frame.copy(showUnobservedCandidates = false) }
        composeRule.waitForIdle()
        val prismaticWithoutCandidateLayer = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
        assertEquals(
            "Enabling the candidate layer changed the 4P render even though classification found no internal dead space",
            0,
            countDifferentPixels(prismaticImage.toPixelMap(), prismaticWithoutCandidateLayer.toPixelMap())
        )

        composeRule.runOnUiThread {
            frame =
                VisualFrame(
                    study = twoLinkStudy,
                    construction = twoLinkConstruction,
                    progress = MID_SWEEP_PROGRESS,
                    showUnobservedCandidates = true
                )
        }
        composeRule.waitForIdle()
        val twoLinkImage = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
        saveEvidence("workspace-animation-2r-mid-sweep-with-inner-dead-space.png", twoLinkImage.asAndroidBitmap())
        val twoLinkColours = countScientificColours(twoLinkImage.toPixelMap())
        assertTrue("The 2R reachable sweep was not visible", twoLinkColours.green > 100)
        assertTrue("The 2R central dead-space sweep was not visible", twoLinkColours.red > 100)
        composeRule.runOnUiThread { frame = frame.copy(showUnobservedCandidates = false) }
        composeRule.waitForIdle()
        val twoLinkWithoutCandidateLayer = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
        assertTrue(
            "The 2R candidate dead-space layer made no visible difference",
            countDifferentPixels(twoLinkImage.toPixelMap(), twoLinkWithoutCandidateLayer.toPixelMap()) > 100
        )

        val phaseFrames =
            listOf(
                "01-freeze-reference" to 0.05,
                "02-trace-outer-boundary" to 0.23,
                "03-trace-inner-boundary" to 0.46,
                "04-sweep-boundaries" to 0.74,
                "05-fill-envelope" to 0.96
            )
        var previousPixels: androidx.compose.ui.graphics.PixelMap? = null
        phaseFrames.forEach { (label, progress) ->
            composeRule.runOnUiThread {
                frame =
                    VisualFrame(
                        study = threeLinkStudy,
                        construction = threeLinkConstruction,
                        progress = progress,
                        showUnobservedCandidates = true
                    )
            }
            composeRule.waitForIdle()
            val image = composeRule.onNodeWithTag(SCENE_TAG).captureToImage()
            saveEvidence("workspace-animation-3r-$label.png", image.asAndroidBitmap())
            val pixels = image.toPixelMap()
            previousPixels?.let { previous ->
                assertTrue(
                    "Construction phase $label did not visibly advance",
                    countDifferentPixels(previous, pixels) > 100
                )
            }
            previousPixels = pixels
        }
    }

    private fun buildPrismaticStudy(): RobotWorkspaceStudy {
        val robot =
            requireNotNull(
                DatasetRobotPresets().buildDefaults()
                    .firstOrNull { saved -> saved.id == "preset-04-prismatic" }
            ).robot
        return RobotWorkspaceAnalyzer().analyze(
            robot = robot,
            config =
                RobotWorkspaceAnalysisConfig(
                    sampleCount = 2_048,
                    randomSeed = 40_404,
                    voxelResolution = 16,
                    replicationCount = 2
                ),
            requestedWorkerCount = 2
        )
    }

    private fun buildTwoLinkStudy(): RobotWorkspaceStudy {
        val robot =
            RobotDefinition(
                name = "Analytic 2R annulus",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.0, a = 0.70, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.0, a = 0.30, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                        JointDefinition("Elbow", JointType.REVOLUTE, -PI, PI, 0.0)
                    )
            )
        return RobotWorkspaceAnalyzer().analyze(
            robot = robot,
            config =
                RobotWorkspaceAnalysisConfig(
                    sampleCount = 2_048,
                    randomSeed = 20_202,
                    voxelResolution = 20,
                    replicationCount = 4
                ),
            requestedWorkerCount = 2
        )
    }

    private fun buildArticulatedThreeLinkStudy(): RobotWorkspaceStudy {
        val robot =
            RobotDefinition(
                name = "Articulated 3R workspace",
                dhParameters =
                    listOf(
                        DHParameter(theta = 0.0, d = 0.20, a = 0.0, alpha = PI / 2.0),
                        DHParameter(theta = 0.0, d = 0.0, a = 0.65, alpha = 0.0),
                        DHParameter(theta = 0.0, d = 0.0, a = 0.45, alpha = 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                        JointDefinition("Shoulder", JointType.REVOLUTE, -PI / 3.0, PI / 2.0, 0.1),
                        JointDefinition("Elbow", JointType.REVOLUTE, -2.0 * PI / 3.0, 2.0 * PI / 3.0, -0.2)
                    )
            )
        return RobotWorkspaceAnalyzer().analyze(
            robot = robot,
            config =
                RobotWorkspaceAnalysisConfig(
                    sampleCount = 4_096,
                    randomSeed = 30_303,
                    voxelResolution = 20,
                    replicationCount = 4
                ),
            requestedWorkerCount = 2
        )
    }

    private fun buildPose(frame: VisualFrame): WorkspaceRobotPose {
        val jointValues =
            requireNotNull(
                constructionJointValues(
                    construction = frame.construction,
                    robot = frame.study.robot,
                    progress = frame.progress
                )
            )
        val result = ForwardKinematicsSolver().solve(frame.study.robot, RobotState(jointValues))
        assertTrue(result.status == FKStatus.SUCCESS || result.status == FKStatus.SUCCESS_WITH_WARNING)
        return WorkspaceRobotPose(
            jointPositions = result.jointPositions,
            jointTypes = frame.study.robot.joints.map { joint -> joint.type },
            endEffector = result.endEffectorPosition
        )
    }

    private fun saveEvidence(name: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "workspace-animation")
        assertTrue(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, name)).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun countScientificColours(pixelMap: androidx.compose.ui.graphics.PixelMap): ScientificColourCount {
        var green = 0
        var red = 0
        for (y in 0 until pixelMap.height) {
            for (x in 0 until pixelMap.width) {
                val colour = pixelMap[x, y]
                if (colour.green > colour.red * 1.18f && colour.green > colour.blue * 1.08f && colour.green > 0.18f) {
                    green++
                }
                if (colour.red > colour.green * 1.25f && colour.red > colour.blue * 1.12f && colour.red > 0.18f) {
                    red++
                }
            }
        }
        return ScientificColourCount(green = green, red = red)
    }

    private fun countDifferentPixels(
        first: androidx.compose.ui.graphics.PixelMap,
        second: androidx.compose.ui.graphics.PixelMap
    ): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        var different = 0
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (first[x, y] != second[x, y]) different++
            }
        }
        return different
    }

    private data class VisualFrame(
        val study: RobotWorkspaceStudy,
        val construction: RobotWorkspaceEnvelopeConstruction,
        val progress: Double,
        val showUnobservedCandidates: Boolean = true
    )

    private data class ScientificColourCount(
        val green: Int,
        val red: Int
    )

    private companion object {
        const val SCENE_TAG = "workspace-animation-scene"
        const val MID_SWEEP_PROGRESS = 0.74
    }
}
