package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSeedDistanceBucket
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticWorkspaceModelTest {

    @Test
    fun workspacePreparation_filtersInvalidCoordinatesAndRequestedTopology() {
        val runs =
            listOf(
                run(1, Vec3(0.1, 0.2, 0.3), seed = 42),
                run(2, Vec3(0.4, 0.5, 0.6), seed = 101),
                run(3, Vec3(Double.NaN, 0.0, 0.0), seed = 42)
            )

        val data =
            buildDiagnosticWorkspace3DData(
                allRuns = runs,
                filter = DiagnosticWorkspaceFilter(seed = 42)
            )

        assertEquals(3, data.totalRunCount)
        assertEquals(2, data.validPositionCount)
        assertEquals(1, data.invalidPositionCount)
        assertEquals(1, data.filteredRunCount)
        assertEquals(1, data.points.size)
    }

    @Test
    fun voxelAggregation_isDeterministicBoundedAndConservesEveryRun() {
        val runs =
            List(10_000) { index ->
                val x = (index % 100) / 50.0 - 1.0
                val y = ((index / 100) % 10) / 5.0 - 1.0
                val z = (index / 1_000) / 5.0 - 1.0
                run(index, Vec3(x, y, z), accepted = index % 3 != 0)
            }
        val bounds = calculateWorkspaceBounds(runs)

        val first = aggregateWorkspaceRuns(runs, bounds, maximumDisplayedPoints = 1_000)
        val second = aggregateWorkspaceRuns(runs, bounds, maximumDisplayedPoints = 1_000)

        assertTrue(first.first.size <= 1_000)
        assertEquals(10_000, first.first.sumOf { it.runCount })
        assertEquals(first.second, second.second)
        assertEquals(first.first.map { it.id to it.runCount }, second.first.map { it.id to it.runCount })
    }

    @Test
    fun bounds_includeRobotOriginAndRemainUsableForCoincidentPoints() {
        val bounds =
            calculateWorkspaceBounds(
                listOf(
                    run(1, Vec3(2.0, 2.0, 2.0)),
                    run(2, Vec3(2.0, 2.0, 2.0))
                )
            )

        assertEquals(0.0, bounds.minimum.x, 0.0)
        assertEquals(2.0, bounds.maximum.x, 0.0)
        assertTrue(bounds.radius > 0.0)
    }

    @Test
    fun projectionAndHitTesting_selectTheVisiblePoint() {
        val run = run(1, Vec3(0.25, -0.10, 0.4))
        val data = buildDiagnosticWorkspace3DData(listOf(run), DiagnosticWorkspaceFilter())
        val camera = WorkspaceCamera(yaw = 0f, pitch = 0f, zoom = 1f)
        val projected =
            projectWorkspacePoint(
                point = run.target,
                bounds = data.bounds,
                widthPx = 800f,
                heightPx = 600f,
                camera = camera
            )

        val selected =
            selectWorkspacePoint(
                points = data.points,
                bounds = data.bounds,
                widthPx = 800f,
                heightPx = 600f,
                camera = camera,
                touchX = projected.x,
                touchY = projected.y
            )

        assertNotNull(selected)
        assertEquals(run.runIndex, selected?.representativeRun?.runIndex)
    }

    @Test
    fun sharedProjectionContext_preservesTheOriginalProjectionExactly() {
        val bounds =
            WorkspaceBounds3D(
                minimum = Vec3(-2.0, -3.0, -4.0),
                maximum = Vec3(4.0, 5.0, 6.0),
                center = Vec3(1.0, 1.0, 1.0),
                radius = 7.25
            )
        val camera = WorkspaceCamera(yaw = -0.73f, pitch = 0.41f, zoom = 1.37f)
        val context = buildWorkspaceProjectionContext(bounds, 937f, 613f, camera)
        val points =
            listOf(
                Vec3.ZERO,
                bounds.minimum,
                bounds.maximum,
                Vec3(0.123456789, -2.75, 5.125)
            )

        points.forEach { point ->
            assertEquals(
                legacyProjectWorkspacePoint(point, bounds, 937f, 613f, camera),
                projectWorkspacePoint(point, context)
            )
        }
    }

    @Test
    fun singlePassHitTesting_preservesDistanceDepthAndStableTieOrdering() {
        val bounds =
            WorkspaceBounds3D(
                minimum = Vec3(-1.0, -1.0, -1.0),
                maximum = Vec3(1.0, 1.0, 1.0),
                center = Vec3.ZERO,
                radius = 1.0
            )
        val camera = WorkspaceCamera(yaw = 0f, pitch = 0f, zoom = 1f)
        val fartherBack = point("back", Vec3(0.0, 0.0, -0.5), 1)
        val nearerFront = point("front", Vec3(0.0, 0.0, 0.5), 2)
        val sameAsFrontButLater = point("front-later", Vec3(0.0, 0.0, 0.5), 3)

        val selected =
            selectWorkspacePoint(
                points = listOf(fartherBack, nearerFront, sameAsFrontButLater),
                bounds = bounds,
                widthPx = 800f,
                heightPx = 600f,
                camera = camera,
                touchX = 400f,
                touchY = 312f
            )

        assertEquals("front", selected?.id)
    }

    @Test
    fun colorMetric_preservesOutcomeDirection() {
        val accepted = aggregateWorkspaceRuns(listOf(run(1, Vec3.ZERO, accepted = true)), calculateWorkspaceBounds(emptyList())).first.single()
        val rejected = aggregateWorkspaceRuns(listOf(run(2, Vec3.ZERO, accepted = false)), calculateWorkspaceBounds(emptyList())).first.single()
        val range = WorkspaceMetricRange(0.0, 1.0)

        assertEquals(1.0, workspaceGoodnessFraction(accepted, WorkspaceColorMetric.OUTCOME, range), 0.0)
        assertEquals(0.0, workspaceGoodnessFraction(rejected, WorkspaceColorMetric.OUTCOME, range), 0.0)
    }

    @Test
    fun voxelAggregation_excludesNonFiniteMetricsInsteadOfTreatingThemAsPerfectZeros() {
        val valid = run(1, Vec3.ZERO).copy(finalError = 0.25, jointLimitPressureRatio = 0.6)
        val invalid = run(2, Vec3.ZERO).copy(finalError = Double.NaN, jointLimitPressureRatio = Double.POSITIVE_INFINITY)

        val point =
            aggregateWorkspaceRuns(
                runs = listOf(valid, invalid),
                bounds = calculateWorkspaceBounds(listOf(valid, invalid)),
                maximumDisplayedPoints = 1
            ).first.single()

        assertEquals(0.25, point.averageFinalError, 0.0)
        assertEquals(0.6, point.averageJointLimitPressure, 0.0)
    }

    private fun run(
        index: Int,
        target: Vec3,
        seed: Int = 42,
        accepted: Boolean = true
    ): DiagnosticRunResult {
        return DiagnosticRunResult(
            seed = seed,
            linkCount = 3,
            jointMode = DiagnosticJointMode.AUTO,
            runIndex = index,
            runKind = DiagnosticRunKind.SEQUENTIAL_STABILITY_CHECK,
            selectedCaseId = "case-$index",
            transitionFromCaseId = null,
            transitionToCaseId = "case-$index",
            expectedClass = DiagnosticExpectedClass.REACHABLE,
            solverAccepted = accepted,
            status = if (accepted) "SUCCESS" else "NO_CONVERGENCE",
            detailCode = "NONE",
            finalError = if (accepted) 0.001 else 0.1,
            iterations = if (accepted) 5 else 800,
            target = target,
            sourceJointState = null,
            seedJointState = RobotState(listOf(0.0, 0.0, 0.0)),
            solutionJointValues = listOf(0.1, 0.2, 0.3),
            initialError = 0.2,
            improvement = 0.199,
            improvementRatio = 0.995,
            progressClass = if (accepted) DiagnosticProgressClass.SOLVED else DiagnosticProgressClass.STALLED,
            seedDistanceBucket = DiagnosticSeedDistanceBucket.EASY,
            seedMinNormalizedLimitMargin = 0.5,
            seedLogConditionNumber = 1.0,
            iterationSaturationRatio = 0.1,
            jointDeltaNorm = 0.1,
            maxSingleJointMovement = 0.1,
            normalizedJointTravelRms = 0.1,
            finalMinNormalizedLimitMargin = 0.4,
            backtrackingRetryCount = 0,
            solveDurationNanos = 1_000L,
            nearLimitJointCount = 0,
            nearLimitJointNames = emptyList(),
            jointLimitPressureRatio = if (accepted) 0.1 else 0.9,
            note = "test"
        )
    }

    private fun point(id: String, position: Vec3, index: Int): WorkspacePointAggregate {
        return WorkspacePointAggregate(
            id = id,
            position = position,
            runCount = 1,
            acceptedCount = 1,
            averageFinalError = 0.0,
            averageIterations = 1.0,
            averageJointLimitPressure = 0.0,
            representativeRun = run(index, position)
        )
    }

    private fun legacyProjectWorkspacePoint(
        point: Vec3,
        bounds: WorkspaceBounds3D,
        widthPx: Float,
        heightPx: Float,
        camera: WorkspaceCamera
    ): WorkspaceProjectedPoint {
        val safeWidth = widthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeHeight = heightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeZoom = camera.zoom.takeIf { it.isFinite() }?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: 1f
        val relativeX = (point.x - bounds.center.x).toFloat()
        val relativeY = (point.y - bounds.center.y).toFloat()
        val relativeZ = (point.z - bounds.center.z).toFloat()
        val cosYaw = kotlin.math.cos(camera.yaw)
        val sinYaw = kotlin.math.sin(camera.yaw)
        val cosPitch = kotlin.math.cos(camera.pitch)
        val sinPitch = kotlin.math.sin(camera.pitch)
        val rotatedX = relativeX * cosYaw - relativeZ * sinYaw
        val yawDepth = relativeX * sinYaw + relativeZ * cosYaw
        val rotatedY = relativeY * cosPitch - yawDepth * sinPitch
        val depth = relativeY * sinPitch + yawDepth * cosPitch
        val pixelsPerMeter =
            (kotlin.math.min(safeWidth, safeHeight) * 0.42f /
                    bounds.radius.toFloat().coerceAtLeast(1e-4f)) * safeZoom

        return WorkspaceProjectedPoint(
            x = safeWidth * 0.5f + rotatedX * pixelsPerMeter,
            y = safeHeight * 0.52f - rotatedY * pixelsPerMeter,
            depth = depth
        )
    }
}
