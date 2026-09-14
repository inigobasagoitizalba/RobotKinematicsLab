package com.robotkinematicslab.mobile.ui.charts.spatial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass
import kotlin.math.abs

data class RobotWorkspaceDisplayOptions(
    val showReachableCore: Boolean = true,
    val showReachableBoundary: Boolean = true,
    val showUnobservedCandidates: Boolean = false,
    val showJointLimits: Boolean = true,
    val showDeadSpaceXRay: Boolean = true
)

@Composable
internal fun RobotWorkspaceScene3D(
    study: RobotWorkspaceStudy,
    revealedSampleCount: Int,
    camera: WorkspaceCamera,
    displayOptions: RobotWorkspaceDisplayOptions,
    renderMode: RobotWorkspaceRenderMode,
    envelopeConstruction: RobotWorkspaceEnvelopeConstruction?,
    constructionProgress: Double,
    selectedVoxelId: String?,
    robotPose: WorkspaceRobotPose?,
    endEffectorTrace: List<Vec3>,
    onCameraChange: (WorkspaceCamera) -> Unit,
    onVoxelSelected: (RobotWorkspaceVoxel?) -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds =
        remember(study.conservativeRadiusMeters) {
            val radius = study.conservativeRadiusMeters
            WorkspaceBounds3D(
                minimum = Vec3(-radius, -radius, -radius),
                maximum = Vec3(radius, radius, radius),
                center = Vec3.ZERO,
                radius = radius * 1.08
            )
        }
    val visibleVoxels =
        remember(study.voxels, revealedSampleCount, displayOptions) {
            displayWorkspaceVoxels(
                study = study,
                revealedSampleCount = revealedSampleCount,
                options = displayOptions
            )
        }
    val surfaceRevealCount =
        remember(study.samples.size, revealedSampleCount, renderMode) {
            if (renderMode == RobotWorkspaceRenderMode.POINT_CLOUD) {
                revealedSampleCount
            } else {
                val bucketSize = (study.samples.size / SURFACE_REVEAL_BUCKET_COUNT).coerceAtLeast(1)
                if (revealedSampleCount >= study.samples.size) study.samples.size
                else (revealedSampleCount / bucketSize) * bucketSize
            }
        }
    val surfaceFaces =
        remember(study.voxels, surfaceRevealCount, displayOptions, renderMode) {
            if (renderMode == RobotWorkspaceRenderMode.SURFACE_SHELL) {
                buildWorkspaceSurfaceFaces(study, surfaceRevealCount, displayOptions)
            } else {
                emptyList()
            }
        }
    // Camera updates must not restart the active pointer coroutine. Otherwise the state write from
    // the first drag/pinch event cancels the detector that is meant to receive the remaining events.
    val currentCamera by rememberUpdatedState(camera)
    val currentOnCameraChange by rememberUpdatedState(onCameraChange)
    val currentOnVoxelSelected by rememberUpdatedState(onVoxelSelected)

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(visibleVoxels, bounds) {
                    detectTapGestures { touch ->
                        currentOnVoxelSelected(
                            selectWorkspaceVoxel(
                                voxels = visibleVoxels,
                                bounds = bounds,
                                widthPx = size.width.toFloat(),
                                heightPx = size.height.toFloat(),
                                camera = currentCamera,
                                touch = touch
                            )
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val cameraAtEvent = currentCamera
                        currentOnCameraChange(
                            cameraAtEvent.copy(
                                yaw = cameraAtEvent.yaw + pan.x * 0.008f + rotation * 0.012f,
                                pitch = (cameraAtEvent.pitch - pan.y * 0.008f).coerceIn(-1.35f, 1.35f),
                                zoom = (cameraAtEvent.zoom * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            )
                        )
                    }
                }
    ) {
        drawRect(WorkspaceBackgroundColor)
        val projection = buildWorkspaceProjectionContext(bounds, size.width, size.height, camera)
        fun project(point: Vec3): WorkspaceProjectedPoint = projectWorkspacePoint(point, projection)
        fun offset(point: Vec3): Offset = project(point).let { Offset(it.x, it.y) }

        drawWorkspaceGrid(bounds, ::offset)

        when (renderMode) {
            RobotWorkspaceRenderMode.POINT_CLOUD -> {
                val projectedVoxels =
                    visibleVoxels
                        .map { voxel -> voxel to project(voxel.center) }
                        .sortedBy { (_, point) -> point.depth }
                val cellRadiusPx =
                    (study.voxelCellSizeMeters.toFloat() * projection.pixelsPerMeter * 0.40f)
                        .coerceIn(2.2f, 10f)

                projectedVoxels.forEach { (voxel, projected) ->
                    val baseColor = voxelColor(voxel.classification)
                    val alpha = if (voxel.classification == WorkspaceVoxelClass.UNOBSERVED_CANDIDATE) 0.15f else 0.86f
                    val center = Offset(projected.x, projected.y)
                    val radius =
                        (cellRadiusPx * (1f + projected.depth / bounds.radius.toFloat() * 0.08f))
                            .coerceIn(1.8f, 11f)
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = alpha * 0.75f), baseColor.copy(alpha = alpha)),
                                center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                                radius = radius * 1.4f
                            ),
                        radius = radius,
                        center = center
                    )
                    if (voxel.id == selectedVoxelId) {
                        drawCircle(Color.White, radius + 4f, center, style = Stroke(width = 2.5f))
                    }
                }
            }
            RobotWorkspaceRenderMode.SURFACE_SHELL -> {
                surfaceFaces
                    .map { face -> face to project(face.center) }
                    .sortedBy { (_, point) -> point.depth }
                    .forEach { (face, _) ->
                        val color =
                            when (face.region) {
                                WorkspaceSurfaceRegion.OBSERVED_REACHABLE -> WorkspaceAcceptedColor
                                WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE -> WorkspaceRejectedColor
                            }
                        val path = Path().apply {
                            face.corners.forEachIndexed { index, corner ->
                                val projected = project(corner)
                                if (index == 0) moveTo(projected.x, projected.y) else lineTo(projected.x, projected.y)
                            }
                            close()
                        }
                        drawPath(
                            path = path,
                            color = color.copy(alpha = if (face.region == WorkspaceSurfaceRegion.OBSERVED_REACHABLE) 0.20f else 0.10f)
                        )
                        drawPath(path = path, color = color.copy(alpha = 0.42f), style = Stroke(width = 0.85f))
                    }
            }
            RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION -> {
                envelopeConstruction?.let { construction ->
                    drawEnvelopeConstruction(
                        construction = construction,
                        progress = constructionProgress,
                        options = displayOptions,
                        project = ::project
                    )
                }
            }
        }

        if (renderMode != RobotWorkspaceRenderMode.ANIMATED_CONSTRUCTION) {
            endEffectorTrace.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = WorkspaceEndEffectorColor.copy(alpha = 0.62f),
                    start = offset(start),
                    end = offset(end),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        robotPose?.let { pose ->
            drawWorkspaceRobotPose(
                pose = if (displayOptions.showJointLimits) pose else pose.copy(jointLimitGuides = emptyList()),
                project = ::offset
            )
        }
    }
}

private fun DrawScope.drawEnvelopeConstruction(
    construction: RobotWorkspaceEnvelopeConstruction,
    progress: Double,
    options: RobotWorkspaceDisplayOptions,
    project: (Vec3) -> WorkspaceProjectedPoint
) {
    val stage = workspaceEnvelopeConstructionStage(progress)
    val stageFraction = workspaceEnvelopeStageFraction(progress)
    val sweepRevealFraction =
        if (
            stage == WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY &&
            construction.strategy == WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP
        ) {
            construction.axisymmetricSweepRevealFraction(stageFraction)
        } else {
            stageFraction
        }
    val showReachable = options.showReachableCore || options.showReachableBoundary
    val showUnobserved = options.showUnobservedCandidates
    val visibleTriangles =
        construction.triangles.filter { triangle ->
            val regionVisible =
                when (triangle.region) {
                    WorkspaceSurfaceRegion.OBSERVED_REACHABLE -> showReachable
                    WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE -> showUnobserved
                }
            val constructionVisible =
                when (stage) {
                    WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS -> false
                    WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY ->
                        false
                    WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY ->
                        false
                    WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY -> triangle.revealFraction <= sweepRevealFraction
                    WorkspaceEnvelopeConstructionStage.FILL_SURFACE -> true
                }
            regionVisible && constructionVisible
        }
    val finalFillFraction =
        if (stage == WorkspaceEnvelopeConstructionStage.FILL_SURFACE) stageFraction else 0.0
    val lightDirection = Vec3(0.35, -0.45, 0.82)
    val projectedTriangles =
        visibleTriangles
            .map { triangle -> triangle to project(triangle.center) }
            .sortedBy { (_, center) -> center.depth }
    val renderOrder =
        if (options.showDeadSpaceXRay) {
            projectedTriangles.filter { (triangle, _) -> triangle.region == WorkspaceSurfaceRegion.OBSERVED_REACHABLE } +
                projectedTriangles.filter { (triangle, _) -> triangle.region == WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE }
        } else {
            projectedTriangles
        }
    val sparseEdgeStride = (renderOrder.size / MAXIMUM_CONSTRUCTION_GUIDE_EDGES).coerceAtLeast(1)
    val edgeVisibility =
        when (stage) {
            WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY -> 1.0f
            WorkspaceEnvelopeConstructionStage.FILL_SURFACE -> (1.0 - finalFillFraction).toFloat()
            else -> 0.0f
        }
    renderOrder.forEachIndexed { index, (triangle, _) ->
            val baseColor =
                when (triangle.region) {
                    WorkspaceSurfaceRegion.OBSERVED_REACHABLE -> WorkspaceAcceptedColor
                    WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE -> WorkspaceRejectedColor
                }
            val normalLength = triangle.normal.norm()
            val light =
                if (normalLength > 0.0) {
                    (0.52 + 0.48 * abs(triangle.normal.dot(lightDirection) / normalLength)).coerceIn(0.0, 1.0)
                } else {
                    0.52
                }
            val shaded =
                Color(
                    red = (baseColor.red * light.toFloat()).coerceIn(0f, 1f),
                    green = (baseColor.green * light.toFloat()).coerceIn(0f, 1f),
                    blue = (baseColor.blue * light.toFloat()).coerceIn(0f, 1f),
                    alpha =
                        if (triangle.region == WorkspaceSurfaceRegion.OBSERVED_REACHABLE) {
                            (0.065 + finalFillFraction * 0.095).toFloat()
                        } else {
                            (0.25 + finalFillFraction * 0.15).toFloat()
                        }
                )
            val path = trianglePath(triangle, project)
            drawPath(path = path, color = shaded)
            if (
                index % sparseEdgeStride == 0 &&
                edgeVisibility > 0.02f &&
                construction.strategy == WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION
            ) {
                val edgeAlpha =
                    if (triangle.region == WorkspaceSurfaceRegion.OBSERVED_REACHABLE) {
                        0.12f * edgeVisibility
                    } else {
                        0.42f * edgeVisibility
                    }
                drawPath(path = path, color = baseColor.copy(alpha = edgeAlpha), style = Stroke(width = 0.7f))
            }
        }

    if (construction.strategy == WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION) {
        drawGeneralConstructionTrace(
            construction = construction,
            stage = stage,
            stageFraction = stageFraction,
            showReachable = showReachable,
            showUnobserved = showUnobserved,
            project = project
        )
    } else {
        val outerFraction =
            when (stage) {
                WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS -> 0.0
                WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY ->
                    construction.stageMotion(stage)?.traceCompletionFraction(stageFraction) ?: 0.0
                else -> 1.0
            }
        val innerFraction =
            when (stage) {
                WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS,
                WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY -> 0.0
                WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY ->
                    construction.stageMotion(stage)?.traceCompletionFraction(stageFraction) ?: 0.0
                else -> 1.0
            }
        if (showReachable) {
            drawMirroredRadialCurve(
                construction.outerCurve,
                construction.frozenBaseRadians,
                outerFraction,
                WorkspaceAcceptedColor,
                project
            )
        }
        if (showUnobserved && construction.hasInnerDeadSpace) {
            drawMirroredRadialCurve(
                construction.innerCurve,
                construction.frozenBaseRadians,
                innerFraction,
                WorkspaceRejectedColor,
                project
            )
        }
        if (stage == WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY) {
            val currentAngle =
                construction.sweepStartRadians +
                    (construction.sweepEndRadians - construction.sweepStartRadians) * sweepRevealFraction
            val guideCount = (sweepRevealFraction * CONSTRUCTION_SWEEP_GUIDES).toInt()
            for (guide in 0 until guideCount) {
                val guideAngle =
                    construction.sweepStartRadians +
                        (construction.sweepEndRadians - construction.sweepStartRadians) *
                        (guide + 1).toDouble() / CONSTRUCTION_SWEEP_GUIDES
                if (showReachable) {
                    drawMirroredRadialCurve(
                        construction.outerCurve,
                        guideAngle,
                        1.0,
                        WorkspaceAcceptedColor,
                        project,
                        alpha = 0.16f,
                        strokeWidth = 1.0f
                    )
                }
                if (showUnobserved && construction.hasInnerDeadSpace) {
                    drawMirroredRadialCurve(
                        construction.innerCurve,
                        guideAngle,
                        1.0,
                        WorkspaceRejectedColor,
                        project,
                        alpha = 0.28f,
                        strokeWidth = 1.15f
                    )
                }
            }
            if (showReachable) {
                drawMirroredRadialCurve(construction.outerCurve, currentAngle, 1.0, WorkspaceAcceptedColor, project)
            }
            if (showUnobserved && construction.hasInnerDeadSpace) {
                drawMirroredRadialCurve(construction.innerCurve, currentAngle, 1.0, WorkspaceRejectedColor, project)
            }
        }
    }
}

private fun WorkspaceConstructionMotion.traceCompletionFraction(stageFraction: Double): Double {
    if (jointValues.size <= traceStartIndex) return 0.0
    val scaledIndex = stageFraction.coerceIn(0.0, 1.0) * jointValues.lastIndex
    if (scaledIndex <= traceStartIndex) return 0.0
    val traceLength = (jointValues.lastIndex - traceStartIndex).coerceAtLeast(1)
    return ((scaledIndex - traceStartIndex) / traceLength).coerceIn(0.0, 1.0)
}

private fun RobotWorkspaceEnvelopeConstruction.axisymmetricSweepRevealFraction(
    stageFraction: Double
): Double {
    val motion = stageMotion(WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY) ?: return 0.0
    if (motion.jointValues.isEmpty()) return 0.0
    val scaledIndex = stageFraction.coerceIn(0.0, 1.0) * motion.jointValues.lastIndex
    if (scaledIndex <= motion.traceStartIndex) return 0.0
    val leftIndex = scaledIndex.toInt().coerceIn(0, motion.jointValues.lastIndex)
    val rightIndex = (leftIndex + 1).coerceAtMost(motion.jointValues.lastIndex)
    val localFraction = scaledIndex - leftIndex
    val leftAngle = motion.jointValues[leftIndex].first()
    val rightAngle = motion.jointValues[rightIndex].first()
    val angle = leftAngle + (rightAngle - leftAngle) * localFraction
    val span = (sweepEndRadians - sweepStartRadians).coerceAtLeast(1e-12)
    return ((angle - sweepStartRadians) / span).coerceIn(0.0, 1.0)
}

private fun DrawScope.drawGeneralConstructionTrace(
    construction: RobotWorkspaceEnvelopeConstruction,
    stage: WorkspaceEnvelopeConstructionStage,
    stageFraction: Double,
    showReachable: Boolean,
    showUnobserved: Boolean,
    project: (Vec3) -> WorkspaceProjectedPoint
) {
    val outer = construction.stageMotion(WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY)
    val inner = construction.stageMotion(WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY)
    val fadeCompleted = if (stage == WorkspaceEnvelopeConstructionStage.FILL_SURFACE) 0.0f else 0.34f

    if (showReachable && outer != null) {
        val fraction =
            when {
                stage.ordinal < WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY.ordinal -> 0.0
                stage == WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY -> stageFraction
                else -> 1.0
            }
        drawConstructionMotion(
            motion = outer,
            revealedFraction = fraction,
            color = WorkspaceAcceptedColor,
            alpha =
                if (stage == WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY) 0.96f else fadeCompleted,
            project = project
        )
    }
    if (showUnobserved && inner?.traceRegion != null) {
        val fraction =
            when {
                stage.ordinal < WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY.ordinal -> 0.0
                stage == WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY -> stageFraction
                else -> 1.0
            }
        drawConstructionMotion(
            motion = inner,
            revealedFraction = fraction,
            color = WorkspaceRejectedColor,
            alpha =
                if (stage == WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY) 0.98f else fadeCompleted,
            project = project
        )
    }
}

private fun DrawScope.drawConstructionMotion(
    motion: WorkspaceConstructionMotion,
    revealedFraction: Double,
    color: Color,
    alpha: Float,
    project: (Vec3) -> WorkspaceProjectedPoint
) {
    if (motion.endEffectorPoints.size < 2 || revealedFraction <= 0.0 || alpha <= 0.0f) return
    val scaled = revealedFraction.coerceIn(0.0, 1.0) * motion.endEffectorPoints.lastIndex
    val completedIndex = scaled.toInt().coerceIn(0, motion.endEffectorPoints.lastIndex)
    val firstTraceSegment = motion.traceStartIndex.coerceIn(0, motion.endEffectorPoints.lastIndex)
    for (index in firstTraceSegment until completedIndex) {
        val start = project(motion.endEffectorPoints[index])
        val end = project(motion.endEffectorPoints[index + 1])
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = if (alpha > 0.8f) 3.8f else 1.8f,
            cap = StrokeCap.Round
        )
    }
    if (completedIndex >= firstTraceSegment) {
        val localFraction = scaled - completedIndex
        val startPoint = motion.endEffectorPoints[completedIndex]
        val headPoint =
            if (completedIndex < motion.endEffectorPoints.lastIndex) {
                val endPoint = motion.endEffectorPoints[completedIndex + 1]
                startPoint + (endPoint - startPoint) * localFraction
            } else {
                startPoint
            }
        val head = project(headPoint)
        drawCircle(
            color = color.copy(alpha = alpha.coerceAtLeast(0.5f)),
            radius = if (alpha > 0.8f) 5.2f else 3.2f,
            center = Offset(head.x, head.y)
        )
    }
}

private fun DrawScope.drawMirroredRadialCurve(
    curve: List<WorkspaceRadialPoint>,
    angleRadians: Double,
    revealedFraction: Double,
    color: Color,
    project: (Vec3) -> WorkspaceProjectedPoint,
    alpha: Float = 0.95f,
    strokeWidth: Float = 4.0f
) {
    drawRadialCurve(curve, angleRadians, revealedFraction, color, project, alpha, strokeWidth)
    drawRadialCurve(curve, angleRadians + Math.PI, revealedFraction, color, project, alpha, strokeWidth)
}

private fun DrawScope.drawRadialCurve(
    curve: List<WorkspaceRadialPoint>,
    angleRadians: Double,
    revealedFraction: Double,
    color: Color,
    project: (Vec3) -> WorkspaceProjectedPoint,
    alpha: Float = 0.95f,
    strokeWidth: Float = 4.0f
) {
    if (curve.isEmpty() || revealedFraction <= 0.0) return
    if (curve.size == 1) {
        val point = project(envelopePoint(curve.single(), angleRadians))
        val isActiveProfile = alpha >= 0.8f && strokeWidth >= 3.0f
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = if (isActiveProfile) 4.5f else (strokeWidth * 1.5f).coerceAtLeast(1.5f),
            center = Offset(point.x, point.y)
        )
        return
    }
    val scaledReveal = revealedFraction.coerceIn(0.0, 1.0) * (curve.size - 1)
    val completeSegments = scaledReveal.toInt().coerceIn(0, curve.lastIndex)
    curve.zipWithNext().take(completeSegments).forEach { (start, end) ->
        val projectedStart = project(envelopePoint(start, angleRadians))
        val projectedEnd = project(envelopePoint(end, angleRadians))
        drawLine(
            color = color.copy(alpha = alpha),
            start = Offset(projectedStart.x, projectedStart.y),
            end = Offset(projectedEnd.x, projectedEnd.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
    val traceHead =
        if (completeSegments >= curve.lastIndex) {
            curve.last()
        } else {
            val localFraction = scaledReveal - completeSegments
            val start = curve[completeSegments]
            val end = curve[completeSegments + 1]
            val partialEnd =
                WorkspaceRadialPoint(
                    radiusMeters = start.radiusMeters + (end.radiusMeters - start.radiusMeters) * localFraction,
                    heightMeters = start.heightMeters + (end.heightMeters - start.heightMeters) * localFraction
                )
            if (localFraction > 0.0) {
                val projectedStart = project(envelopePoint(start, angleRadians))
                val projectedEnd = project(envelopePoint(partialEnd, angleRadians))
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(projectedStart.x, projectedStart.y),
                    end = Offset(projectedEnd.x, projectedEnd.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            partialEnd
        }
    if (alpha >= 0.8f && strokeWidth >= 3.0f) {
        val projectedHead = project(envelopePoint(traceHead, angleRadians))
        drawCircle(
            color = color,
            radius = strokeWidth * 1.4f,
            center = Offset(projectedHead.x, projectedHead.y)
        )
    }
}

private fun trianglePath(
    triangle: WorkspaceEnvelopeTriangle,
    project: (Vec3) -> WorkspaceProjectedPoint
): Path {
    val a = project(triangle.a)
    val b = project(triangle.b)
    val c = project(triangle.c)
    return Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
}

internal fun displayWorkspaceVoxels(
    study: RobotWorkspaceStudy,
    revealedSampleCount: Int,
    options: RobotWorkspaceDisplayOptions,
    maximumDisplayedVoxels: Int = MAXIMUM_DISPLAYED_WORKSPACE_VOXELS
): List<RobotWorkspaceVoxel> {
    require(maximumDisplayedVoxels > 0)
    val revealIndex = revealedSampleCount.coerceAtLeast(0) - 1
    val candidates =
        study.voxels.filter { voxel ->
            when (voxel.classification) {
                WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE ->
                    options.showReachableCore && (voxel.firstObservedSampleIndex ?: Int.MAX_VALUE) <= revealIndex
                WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY ->
                    options.showReachableBoundary && (voxel.firstObservedSampleIndex ?: Int.MAX_VALUE) <= revealIndex
                WorkspaceVoxelClass.UNOBSERVED_CANDIDATE ->
                    options.showUnobservedCandidates && revealedSampleCount >= study.samples.size
            }
        }
    if (candidates.size <= maximumDisplayedVoxels) return candidates
    val stride = (candidates.size + maximumDisplayedVoxels - 1) / maximumDisplayedVoxels
    return candidates.filterIndexed { index, _ -> index % stride == 0 }.take(maximumDisplayedVoxels)
}

private fun selectWorkspaceVoxel(
    voxels: List<RobotWorkspaceVoxel>,
    bounds: WorkspaceBounds3D,
    widthPx: Float,
    heightPx: Float,
    camera: WorkspaceCamera,
    touch: Offset
): RobotWorkspaceVoxel? {
    val projection = buildWorkspaceProjectionContext(bounds, widthPx, heightPx, camera)
    return voxels
        .asSequence()
        .map { voxel ->
            val point = projectWorkspacePoint(voxel.center, projection)
            val dx = point.x - touch.x
            val dy = point.y - touch.y
            VoxelSelection(voxel, dx * dx + dy * dy, point.depth)
        }
        .filter { it.distanceSquared <= MAXIMUM_SELECTION_DISTANCE_PX * MAXIMUM_SELECTION_DISTANCE_PX }
        .minWithOrNull(compareBy<VoxelSelection> { it.distanceSquared }.thenByDescending { it.depth })
        ?.voxel
}

private fun voxelColor(classification: WorkspaceVoxelClass): Color =
    when (classification) {
        WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE -> WorkspaceAcceptedColor
        WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY -> WorkspaceWarningColor
        WorkspaceVoxelClass.UNOBSERVED_CANDIDATE -> WorkspaceRejectedColor
    }

private data class VoxelSelection(
    val voxel: RobotWorkspaceVoxel,
    val distanceSquared: Float,
    val depth: Float
)

private const val MAXIMUM_DISPLAYED_WORKSPACE_VOXELS = 5_000
private const val MAXIMUM_SELECTION_DISTANCE_PX = 30f
private const val SURFACE_REVEAL_BUCKET_COUNT = 120
private const val MAXIMUM_CONSTRUCTION_GUIDE_EDGES = 220
private const val CONSTRUCTION_SWEEP_GUIDES = 4
