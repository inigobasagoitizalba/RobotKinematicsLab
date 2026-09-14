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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.max

data class WorkspaceRobotPose(
    val jointPositions: List<Vec3>,
    val jointTypes: List<JointType>,
    val target: Vec3? = null,
    val endEffector: Vec3,
    val jointLimitGuides: List<WorkspaceJointLimitGuide> = emptyList()
)

@Composable
fun WorkspaceScene3D(
    data: DiagnosticWorkspace3DData,
    metric: WorkspaceColorMetric,
    camera: WorkspaceCamera,
    selectedPointId: String?,
    selectedRobotPose: WorkspaceRobotPose?,
    onCameraChange: (WorkspaceCamera) -> Unit,
    onPointSelected: (WorkspacePointAggregate?) -> Unit,
    modifier: Modifier = Modifier
) {
    val metricRange = remember(data.points, metric) {
        workspaceMetricRange(data.points, metric)
    }
    val maximumAggregateCount = remember(data.points) {
        data.points.maxOfOrNull { it.runCount } ?: 1
    }
    // Keep gesture detectors alive while the camera changes. Keying pointerInput with camera used
    // to cancel the active detector after its first update, which made an orbit/pinch appear to
    // advance by one frame only.
    val currentCamera by rememberUpdatedState(camera)
    val currentOnCameraChange by rememberUpdatedState(onCameraChange)
    val currentOnPointSelected by rememberUpdatedState(onPointSelected)

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(data.points, data.bounds) {
                    detectTapGestures { touch ->
                        currentOnPointSelected(
                            selectWorkspacePoint(
                                points = data.points,
                                bounds = data.bounds,
                                widthPx = size.width.toFloat(),
                                heightPx = size.height.toFloat(),
                                camera = currentCamera,
                                touchX = touch.x,
                                touchY = touch.y
                            )
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val cameraAtEvent = currentCamera
                        val updated =
                            cameraAtEvent.copy(
                                yaw = cameraAtEvent.yaw + pan.x * 0.008f + rotation * 0.012f,
                                pitch =
                                    (cameraAtEvent.pitch - pan.y * 0.008f)
                                        .coerceIn(-1.35f, 1.35f),
                                zoom = (cameraAtEvent.zoom * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            )
                        currentOnCameraChange(updated)
                    }
                }
    ) {
        drawRect(color = WorkspaceBackgroundColor)

        val projectionContext =
            buildWorkspaceProjectionContext(
                bounds = data.bounds,
                widthPx = size.width,
                heightPx = size.height,
                camera = camera
            )

        fun project(point: Vec3): WorkspaceProjectedPoint {
            return projectWorkspacePoint(point, projectionContext)
        }

        fun projectedOffset(point: Vec3): Offset {
            val projected = project(point)
            return Offset(projected.x, projected.y)
        }

        drawWorkspaceGrid(
            bounds = data.bounds,
            project = ::projectedOffset
        )

        val projectedPoints =
            data.points
                .map { point -> point to project(point.position) }
                .sortedBy { (_, projected) -> projected.depth }

        projectedPoints.forEach { (point, projected) ->
            val baseRadius = workspaceSphereRadius(point.runCount, maximumAggregateCount)
            val depthScale =
                (1f + projected.depth / data.bounds.radius.toFloat().coerceAtLeast(0.05f) * 0.10f)
                    .coerceIn(0.82f, 1.18f)
            val radius = baseRadius * depthScale
            val color =
                workspaceMetricColor(
                    workspaceGoodnessFraction(
                        point = point,
                        metric = metric,
                        range = metricRange
                    )
                )
            val center = Offset(projected.x, projected.y)

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color.White.copy(alpha = 0.88f),
                                color,
                                color.copy(alpha = 0.72f)
                            ),
                        center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f),
                        radius = radius * 1.45f
                    ),
                radius = radius,
                center = center
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.24f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.2f)
            )

            if (point.id == selectedPointId) {
                drawCircle(
                    color = Color.White,
                    radius = radius + 5f,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }
        }

        selectedRobotPose?.let { pose ->
            drawWorkspaceRobotPose(
                pose = pose,
                project = ::projectedOffset
            )
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorkspaceGrid(
    bounds: WorkspaceBounds3D,
    project: (Vec3) -> Offset
) {
    val extent = bounds.radius
    val center = bounds.center
    val groundY = 0.0.coerceIn(bounds.minimum.y, bounds.maximum.y)
    val lineCount = 8

    repeat(lineCount + 1) { index ->
        val ratio = index.toDouble() / lineCount.toDouble()
        val coordinate = center.x - extent + ratio * extent * 2.0
        drawLine(
            color = WorkspaceGridColor,
            start = project(Vec3(coordinate, groundY, center.z - extent)),
            end = project(Vec3(coordinate, groundY, center.z + extent)),
            strokeWidth = 1.1f
        )
    }

    repeat(lineCount + 1) { index ->
        val ratio = index.toDouble() / lineCount.toDouble()
        val coordinate = center.z - extent + ratio * extent * 2.0
        drawLine(
            color = WorkspaceGridColor,
            start = project(Vec3(center.x - extent, groundY, coordinate)),
            end = project(Vec3(center.x + extent, groundY, coordinate)),
            strokeWidth = 1.1f
        )
    }

    val origin = Vec3.ZERO
    val axisLength = max(extent * 0.45, 0.10)
    drawLine(
        color = WorkspaceAxisXColor,
        start = project(origin),
        end = project(Vec3(axisLength, 0.0, 0.0)),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = WorkspaceAxisYColor,
        start = project(origin),
        end = project(Vec3(0.0, axisLength, 0.0)),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = WorkspaceAxisZColor,
        start = project(origin),
        end = project(Vec3(0.0, 0.0, axisLength)),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorkspaceRobotPose(
    pose: WorkspaceRobotPose,
    project: (Vec3) -> Offset
) {
    pose.jointLimitGuides.forEach { guide ->
        val guideColor = jointLimitGuideColor(guide.jointIndex)
        when (guide.jointType) {
            JointType.REVOLUTE -> {
                if (guide.boundaryPoints.size >= 2) {
                    val sector = Path().apply {
                        moveTo(project(guide.origin).x, project(guide.origin).y)
                        guide.boundaryPoints.forEach { point ->
                            val projected = project(point)
                            lineTo(projected.x, projected.y)
                        }
                        close()
                    }
                    drawPath(sector, guideColor.copy(alpha = 0.13f))
                    val arc = Path().apply {
                        guide.boundaryPoints.forEachIndexed { index, point ->
                            val projected = project(point)
                            if (index == 0) moveTo(projected.x, projected.y) else lineTo(projected.x, projected.y)
                        }
                    }
                    drawPath(arc, guideColor.copy(alpha = 0.90f), style = Stroke(width = 2.3f))
                    if (!guide.isFullRotation) {
                        drawLine(guideColor.copy(alpha = 0.72f), project(guide.origin), project(guide.boundaryPoints.first()), 1.8f)
                        drawLine(guideColor.copy(alpha = 0.72f), project(guide.origin), project(guide.boundaryPoints.last()), 1.8f)
                    }
                    drawLine(guideColor, project(guide.origin), project(guide.currentPoint), 2.5f, cap = StrokeCap.Round)
                    drawCircle(guideColor, 4.5f, project(guide.currentPoint))
                }
            }

            JointType.PRISMATIC -> {
                if (guide.boundaryPoints.size == 2) {
                    drawLine(
                        color = guideColor.copy(alpha = 0.88f),
                        start = project(guide.boundaryPoints[0]),
                        end = project(guide.boundaryPoints[1]),
                        strokeWidth = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                        cap = StrokeCap.Round
                    )
                    guide.boundaryPoints.forEach { drawCircle(guideColor, 5.5f, project(it)) }
                    drawCircle(Color.White, 4f, project(guide.currentPoint))
                }
            }
        }
    }

    val segmentCount =
        minOf(
            pose.jointTypes.size,
            (pose.jointPositions.size - 1).coerceAtLeast(0)
        )

    repeat(segmentCount) { index ->
        val type = pose.jointTypes[index]
        drawLine(
            color =
                when (type) {
                    JointType.REVOLUTE -> WorkspaceRevoluteLinkColor
                    JointType.PRISMATIC -> WorkspacePrismaticLinkColor
                },
            start = project(pose.jointPositions[index]),
            end = project(pose.jointPositions[index + 1]),
            strokeWidth = if (type == JointType.REVOLUTE) 8f else 10f,
            cap = StrokeCap.Round
        )

        drawCircle(
            color =
                when (type) {
                    JointType.REVOLUTE -> WorkspaceRevoluteJointColor
                    JointType.PRISMATIC -> WorkspacePrismaticJointColor
                },
            radius = 8f,
            center = project(pose.jointPositions[index])
        )
    }

    val endEffector = project(pose.endEffector)

    pose.target?.let { targetPosition ->
        val target = project(targetPosition)
        drawLine(
            color = WorkspaceErrorVectorColor,
            start = endEffector,
            end = target,
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
        )
        drawCircle(
            color = WorkspaceTargetColor,
            radius = 13f,
            center = target,
            style = Stroke(width = 3f)
        )
    }
    drawCircle(
        color = WorkspaceEndEffectorColor,
        radius = 10f,
        center = endEffector
    )
}

private fun jointLimitGuideColor(index: Int): Color =
    JOINT_LIMIT_GUIDE_COLORS[index.mod(JOINT_LIMIT_GUIDE_COLORS.size)]

internal fun workspaceMetricColor(goodness: Double): Color {
    val fraction = goodness.coerceIn(0.0, 1.0).toFloat()
    return if (fraction < 0.5f) {
        lerpColor(
            start = WorkspaceRejectedColor,
            end = WorkspaceWarningColor,
            fraction = fraction * 2f
        )
    } else {
        lerpColor(
            start = WorkspaceWarningColor,
            end = WorkspaceAcceptedColor,
            fraction = (fraction - 0.5f) * 2f
        )
    }
}

private fun lerpColor(
    start: Color,
    end: Color,
    fraction: Float
): Color {
    val safe = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * safe,
        green = start.green + (end.green - start.green) * safe,
        blue = start.blue + (end.blue - start.blue) * safe,
        alpha = start.alpha + (end.alpha - start.alpha) * safe
    )
}

internal val WorkspaceBackgroundColor = Color(0xFF101820)
internal val WorkspaceGridColor = Color(0xFF28455E)
internal val WorkspaceAxisXColor = Color(0xFFE57373)
internal val WorkspaceAxisYColor = Color(0xFF81C784)
internal val WorkspaceAxisZColor = Color(0xFF64B5F6)
internal val WorkspaceAcceptedColor = Color(0xFF2E7D32)
internal val WorkspaceWarningColor = Color(0xFFF9A825)
internal val WorkspaceRejectedColor = Color(0xFFC62828)
internal val WorkspaceRevoluteLinkColor = Color(0xFF42A5F5)
internal val WorkspacePrismaticLinkColor = Color(0xFF66BB6A)
internal val WorkspaceRevoluteJointColor = Color(0xFFFFD54F)
internal val WorkspacePrismaticJointColor = Color(0xFFCE93D8)
internal val WorkspaceEndEffectorColor = Color(0xFF4DD0E1)
internal val WorkspaceTargetColor = Color(0xFFFF6E6E)
internal val WorkspaceErrorVectorColor = Color(0xFFFFAB40)
private val JOINT_LIMIT_GUIDE_COLORS =
    listOf(
        Color(0xFFFFC107),
        Color(0xFFAB47BC),
        Color(0xFF26C6DA),
        Color(0xFFFF7043),
        Color(0xFF9CCC65)
    )
