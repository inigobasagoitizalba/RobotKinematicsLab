package com.robotkinematicslab.mobile.ui.charts.spatial

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticTopologyConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticExperiment
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ui.charts.DiagnosticChartCategory
import com.robotkinematicslab.mobile.ui.charts.HeatMapChartsPage
import com.robotkinematicslab.mobile.ui.charts.buildDiagnosticHeatMapIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceScene3DInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneContinuousOrbitGestureProducesMultipleCameraFrames() {
        val data =
            DiagnosticWorkspace3DData(
                points = emptyList(),
                bounds =
                    WorkspaceBounds3D(
                        minimum = Vec3(-1.0, -1.0, -1.0),
                        maximum = Vec3(1.0, 1.0, 1.0),
                        center = Vec3.ZERO,
                        radius = 1.0
                    ),
                totalRunCount = 0,
                validPositionCount = 0,
                filteredRunCount = 0,
                invalidPositionCount = 0,
                voxelResolution = null
            )
        var camera by mutableStateOf(WorkspaceCamera(yaw = 0f, pitch = 0f))
        var cameraUpdates by mutableIntStateOf(0)

        composeRule.setContent {
            WorkspaceScene3D(
                data = data,
                metric = WorkspaceColorMetric.OUTCOME,
                camera = camera,
                selectedPointId = null,
                selectedRobotPose = null,
                onCameraChange = {
                    camera = it
                    cameraUpdates += 1
                },
                onPointSelected = {},
                modifier = Modifier.fillMaxSize().testTag(SCENE_TAG)
            )
        }

        composeRule.onNodeWithTag(SCENE_TAG).performTouchInput {
            swipe(
                start = Offset(center.x * 0.25f, center.y * 0.70f),
                end = Offset(center.x * 1.70f, center.y * 0.35f),
                durationMillis = 480L
            )
        }

        composeRule.runOnIdle {
            assertTrue("Orbit gesture collapsed to a single camera frame", cameraUpdates >= 4)
            assertTrue(camera.yaw != 0f)
            assertTrue(camera.pitch != 0f)
        }
    }

    @Test
    fun centeredSphere_canBeSelectedWithoutDisablingCameraLayer() {
        val run = run()
        val point =
            WorkspacePointAggregate(
                id = "center",
                position = Vec3.ZERO,
                runCount = 1,
                acceptedCount = 1,
                averageFinalError = run.finalError,
                averageIterations = run.iterations.toDouble(),
                averageJointLimitPressure = run.jointLimitPressureRatio,
                representativeRun = run
            )
        val data =
            DiagnosticWorkspace3DData(
                points = listOf(point),
                bounds =
                    WorkspaceBounds3D(
                        minimum = Vec3(-1.0, -1.0, -1.0),
                        maximum = Vec3(1.0, 1.0, 1.0),
                        center = Vec3.ZERO,
                        radius = 1.0
                    ),
                totalRunCount = 1,
                validPositionCount = 1,
                filteredRunCount = 1,
                invalidPositionCount = 0,
                voxelResolution = null
            )
        var camera by mutableStateOf(WorkspaceCamera(yaw = 0f, pitch = 0f))
        var selectedRunIndex by mutableIntStateOf(-1)

        composeRule.setContent {
            WorkspaceScene3D(
                data = data,
                metric = WorkspaceColorMetric.OUTCOME,
                camera = camera,
                selectedPointId = null,
                selectedRobotPose = null,
                onCameraChange = { camera = it },
                onPointSelected = { selectedRunIndex = it?.representativeRun?.runIndex ?: -1 },
                modifier = Modifier.fillMaxSize().testTag(SCENE_TAG)
            )
        }

        val sceneBounds = composeRule.onNodeWithTag(SCENE_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(SCENE_TAG).performTouchInput {
            click(Offset(sceneBounds.width * 0.5f, sceneBounds.height * 0.52f))
        }

        composeRule.runOnIdle {
            assertEquals(run.runIndex, selectedRunIndex)
        }
    }

    @Test
    fun matrixExplorer_switchesToPreparedInteractiveWorkspace() {
        val report =
            Layer1DiagnosticExperiment().runExperiment(
                DiagnosticBenchmarkConfig(
                    sampling =
                        DiagnosticSamplingConfig(
                            reachableCount = 1,
                            unreachableCount = 1,
                            runCount = 2,
                            samplesPerLinkCount = 2
                        ),
                    seeds = DiagnosticSeedConfig(listOf(42)),
                    topology =
                        DiagnosticTopologyConfig(
                            robotLinkCount = 2,
                            minLinkCount = 2,
                            maxLinkCount = 2
                        ),
                    solver = DiagnosticSolverConfig(ikMaxIterations = 10),
                    performance =
                        DiagnosticPerformanceConfig(
                            storeFullRunHistory = true,
                            storePerCaseDetails = true
                        )
                )
            )
        val runs =
            report.runResults.filter {
                it.runKind == DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK
            }

        composeRule.setContent {
            Column(modifier = Modifier.fillMaxSize()) {
                HeatMapChartsPage(
                    report = report,
                    heatMapIndex = buildDiagnosticHeatMapIndex(runs),
                    focusCategory = DiagnosticChartCategory.OVERVIEW,
                    sequentialRuns = runs
                )
            }
        }

        composeRule.onNodeWithText("3D Workspace").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Interactive 3D Workspace", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Interactive 3D Workspace", substring = true).assertIsDisplayed()
    }

    private fun run(): DiagnosticRunResult {
        return DiagnosticRunResult(
            seed = 42,
            linkCount = 3,
            jointMode = DiagnosticJointMode.AUTO,
            runIndex = 7,
            runKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
            selectedCaseId = "center",
            transitionFromCaseId = null,
            transitionToCaseId = "center",
            expectedClass = DiagnosticExpectedClass.REACHABLE,
            solverAccepted = true,
            status = "SUCCESS",
            detailCode = "NONE",
            finalError = 0.001,
            iterations = 5,
            target = Vec3.ZERO,
            sourceJointState = null,
            seedJointState = RobotState(listOf(0.0, 0.0, 0.0)),
            solutionJointValues = listOf(0.0, 0.0, 0.0),
            initialError = 0.1,
            improvement = 0.099,
            improvementRatio = 0.99,
            progressClass = DiagnosticProgressClass.SOLVED,
            seedDistanceBucket = DiagnosticSeedDistanceBucket.EASY,
            seedMinNormalizedLimitMargin = 0.5,
            seedLogConditionNumber = 1.0,
            iterationSaturationRatio = 0.1,
            jointDeltaNorm = 0.1,
            maxSingleJointMovement = 0.1,
            normalizedJointTravelRms = 0.1,
            finalMinNormalizedLimitMargin = 0.5,
            backtrackingRetryCount = 0,
            solveDurationNanos = 1_000L,
            nearLimitJointCount = 0,
            nearLimitJointNames = emptyList(),
            jointLimitPressureRatio = 0.0,
            note = "test"
        )
    }

    private companion object {
        const val SCENE_TAG = "workspace-scene-3d"
    }
}
