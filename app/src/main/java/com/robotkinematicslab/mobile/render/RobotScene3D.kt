package com.robotkinematicslab.mobile.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.math.utility.Vec3
import java.util.Locale

/** Interactive Compose renderer for the robot scene and camera controls. */
private const val SLIDER_WIDTH_RATIO = 0.10f
private const val SLIDER_MARGIN = 18f
private const val SLIDER_TRACK_WIDTH = 10f

internal val RobotSceneBackgroundColor = Color(0xFF101820)
internal val RevoluteLinkColor = Color(0xFF42A5F5)
internal val PrismaticLinkColor = Color(0xFF66BB6A)
internal val RevoluteJointColor = Color(0xFFFFD54F)
internal val PrismaticJointColor = Color(0xFFCE93D8)
internal val EndEffectorColor = Color(0xFF4DD0E1)
internal val TargetColor = Color(0xFFFF6E6E)

@Composable
fun RobotScene3D(
    jointPositions: List<Vec3>,
    jointTypes: List<JointType>,
    targetPoint: Vec3?,
    endEffector: Vec3?,
    workspaceRadius: Double,
    enabled: Boolean,
    onTargetSelected: (Vec3) -> Unit,
    modifier: Modifier = Modifier
) {
    var yaw by remember { mutableFloatStateOf(-0.65f) }
    var pitch by remember { mutableFloatStateOf(0.45f) }

    val latestTargetPoint by rememberUpdatedState(targetPoint)
    val geometry = remember(jointPositions, jointTypes) {
        buildRobotSceneGeometry(jointPositions, jointTypes)
    }
    val prismaticLinkPathEffect = remember {
        PathEffect.dashPathEffect(
            intervals = floatArrayOf(8f, 6f),
            phase = 0f
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(enabled, workspaceRadius) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    if (enabled) {
                        val current = latestTargetPoint ?: Vec3.ZERO
                        val canvasWidth = size.width.toFloat()
                        val canvasHeight = size.height.toFloat()
                        val viewport = buildRobotSceneViewport(
                            canvasWidth = canvasWidth,
                            canvasHeight = canvasHeight,
                            workspaceRadius = workspaceRadius
                        )

                        val sliderBounds = buildYSliderBounds(
                            canvasWidth = canvasWidth,
                            canvasHeight = canvasHeight
                        )

                        val isInsideSlider =
                            change.position.x in sliderBounds.left..sliderBounds.right &&
                                    change.position.y in sliderBounds.top..sliderBounds.bottom

                        if (isInsideSlider) {
                            val updated = targetYFromSlider(
                                touchY = change.position.y,
                                sliderTop = sliderBounds.top,
                                sliderBottom = sliderBounds.bottom,
                                currentTarget = current,
                                workspaceRadius = viewport.workspaceRadius
                            )
                            onTargetSelected(updated)
                        } else {
                            val projected = screenToRobotTargetOnHeightPlane(
                                screenX = change.position.x,
                                screenY = change.position.y,
                                viewport = viewport,
                                yaw = yaw,
                                pitch = pitch,
                                currentY = current.y
                            )

                            if (projected != null) {
                                onTargetSelected(projected)
                            }
                        }
                    } else {
                        yaw += dragAmount.x * 0.01f
                        pitch = (pitch - dragAmount.y * 0.01f).coerceIn(-1.2f, 1.2f)
                    }
                }
            }
    ) {
        val backgroundColor = RobotSceneBackgroundColor
        val gridColor = Color(0xFF28455E)
        val axisXColor = Color(0xFFE57373)
        val axisYColor = Color(0xFF81C784)
        val axisZColor = Color(0xFF64B5F6)
        val revoluteLinkColor = RevoluteLinkColor
        val prismaticLinkColor = PrismaticLinkColor
        val revoluteJointColor = RevoluteJointColor
        val prismaticJointColor = PrismaticJointColor
        val eeColor = EndEffectorColor
        val targetColor = TargetColor
        val sliderTrackColor = Color(0x55FFFFFF)
        val sliderThumbColor = Color(0xFF81C784)
        val sliderAccentColor = Color(0xAA81C784)

        drawRect(color = backgroundColor)

        val viewport = buildRobotSceneViewport(
            canvasWidth = size.width,
            canvasHeight = size.height,
            workspaceRadius = workspaceRadius
        )
        val projectionContext = buildRobotSceneProjectionContext(viewport, yaw, pitch)

        fun project(point: Vec3): Offset {
            val projected = projectRobotScenePoint(point, projectionContext)
            return Offset(projected.x, projected.y)
        }

        fun drawLink(
            start: Vec3,
            end: Vec3,
            jointType: JointType
        ) {
            val start2D = project(start)
            val end2D = project(end)

            when (jointType) {
                JointType.REVOLUTE -> {
                    drawLine(
                        color = revoluteLinkColor,
                        start = start2D,
                        end = end2D,
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                }

                JointType.PRISMATIC -> {
                    drawLine(
                        color = prismaticLinkColor,
                        start = start2D,
                        end = end2D,
                        strokeWidth = 10f,
                        cap = StrokeCap.Butt
                    )

                    drawLine(
                        color = Color.Black.copy(alpha = 0.45f),
                        start = start2D,
                        end = end2D,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                        pathEffect = prismaticLinkPathEffect
                    )
                }
            }
        }

        fun drawJointMarker(
            centerPoint: Vec3,
            jointType: JointType?,
            isBase: Boolean = false
        ) {
            val projected = project(centerPoint)

            when (jointType) {
                JointType.REVOLUTE -> {
                    drawCircle(
                        color = revoluteJointColor,
                        radius = if (isBase) 11f else 9f,
                        center = projected
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.35f),
                        radius = if (isBase) 11f else 9f,
                        center = projected,
                        style = Stroke(width = 2f)
                    )
                }

                JointType.PRISMATIC -> {
                    val half = if (isBase) 11f else 9f
                    drawRect(
                        color = prismaticJointColor,
                        topLeft = Offset(projected.x - half, projected.y - half),
                        size = Size(half * 2, half * 2)
                    )

                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(projected.x - half, projected.y - half),
                        size = Size(half * 2, half * 2),
                        style = Stroke(width = 2f)
                    )
                }

                null -> {
                    drawCircle(
                        color = revoluteJointColor,
                        radius = 9f,
                        center = projected
                    )
                }
            }

            if (isBase) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.88f),
                    radius = 15f,
                    center = projected,
                    style = Stroke(width = 2.5f)
                )
            }
        }

        val gridExtent = viewport.gridExtent
        val gridStep = viewport.gridStep

        var gx = -gridExtent
        while (gx <= gridExtent + 1e-9) {
            drawLine(
                color = gridColor,
                start = project(Vec3(gx, 0.0, -gridExtent)),
                end = project(Vec3(gx, 0.0, gridExtent)),
                strokeWidth = 1.5f
            )
            gx += gridStep
        }

        var gz = -gridExtent
        while (gz <= gridExtent + 1e-9) {
            drawLine(
                color = gridColor,
                start = project(Vec3(-gridExtent, 0.0, gz)),
                end = project(Vec3(gridExtent, 0.0, gz)),
                strokeWidth = 1.5f
            )
            gz += gridStep
        }

        val axisLength = (gridStep * 1.5).coerceAtMost(viewport.workspaceRadius)

        drawLine(
            color = axisXColor,
            start = project(Vec3(0.0, 0.0, 0.0)),
            end = project(Vec3(axisLength, 0.0, 0.0)),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = axisYColor,
            start = project(Vec3(0.0, 0.0, 0.0)),
            end = project(Vec3(0.0, axisLength, 0.0)),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = axisZColor,
            start = project(Vec3(0.0, 0.0, 0.0)),
            end = project(Vec3(0.0, 0.0, axisLength)),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        geometry.links.forEach { link ->
            drawLink(link.start, link.end, link.jointType)
        }

        geometry.jointMarkers.forEach { marker ->
            drawJointMarker(
                centerPoint = marker.position,
                jointType = marker.jointType,
                isBase = marker.isBase
            )
        }

        if (endEffector != null) {
            val p = project(endEffector)

            drawCircle(
                color = eeColor,
                radius = 11f,
                center = p
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = 11f,
                center = p,
                style = Stroke(width = 2f)
            )
        }

        if (targetPoint != null) {
            val p = project(targetPoint)
            val groundProjection = project(Vec3(targetPoint.x, 0.0, targetPoint.z))
            val r = 13f

            drawLine(
                color = targetColor.copy(alpha = 0.45f),
                start = groundProjection,
                end = p,
                strokeWidth = 2f
            )

            drawCircle(
                color = targetColor,
                radius = r,
                center = p,
                style = Stroke(width = 3f)
            )

            drawLine(
                color = targetColor,
                start = Offset(p.x - r, p.y),
                end = Offset(p.x + r, p.y),
                strokeWidth = 3f
            )

            drawLine(
                color = targetColor,
                start = Offset(p.x, p.y - r),
                end = Offset(p.x, p.y + r),
                strokeWidth = 3f
            )
        }

        if (enabled) {
            val slider = buildYSliderBounds(
                canvasWidth = size.width,
                canvasHeight = size.height
            )

            val trackCenterX = (slider.left + slider.right) * 0.5f
            val thumbY = sliderYFromTarget(
                targetY = targetPoint?.y ?: 0.0,
                sliderTop = slider.top,
                sliderBottom = slider.bottom,
                workspaceRadius = viewport.workspaceRadius
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(slider.left, slider.top),
                size = Size(slider.right - slider.left, slider.bottom - slider.top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
            )

            drawLine(
                color = sliderTrackColor,
                start = Offset(trackCenterX, slider.top + 16f),
                end = Offset(trackCenterX, slider.bottom - 16f),
                strokeWidth = SLIDER_TRACK_WIDTH,
                cap = StrokeCap.Round
            )

            drawLine(
                color = sliderAccentColor,
                start = Offset(trackCenterX, thumbY),
                end = Offset(trackCenterX, slider.bottom - 16f),
                strokeWidth = SLIDER_TRACK_WIDTH,
                cap = StrokeCap.Round
            )

            drawCircle(
                color = sliderThumbColor,
                radius = 16f,
                center = Offset(trackCenterX, thumbY)
            )

            drawCircle(
                color = Color.Black.copy(alpha = 0.30f),
                radius = 16f,
                center = Offset(trackCenterX, thumbY),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun RobotSceneLegend(
    workspaceRadius: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F6F9))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Visual key · auto-fit workspace ±${String.format(Locale.US, "%.2f", workspaceRadius)} m",
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF263238)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RobotSceneLegendItem(RevoluteJointColor, true, "R joint")
            RobotSceneLegendItem(PrismaticJointColor, false, "P joint")
            RobotSceneLegendItem(EndEffectorColor, true, "End effector")
            RobotSceneLegendItem(TargetColor, true, "Target")
        }

        Text(
            text = "Links: blue = revolute-driven · green = prismatic-driven · base has a white ring",
            color = Color(0xFF455A64)
        )
    }
}

@Composable
private fun RobotSceneLegendItem(
    color: Color,
    circular: Boolean,
    label: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(11.dp)) {
            if (circular) {
                drawCircle(color)
            } else {
                drawRect(color)
            }
        }
        Text(label, color = Color(0xFF37474F))
    }
}

private data class SliderBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun buildYSliderBounds(
    canvasWidth: Float,
    canvasHeight: Float
): SliderBounds {
    val width = canvasWidth * SLIDER_WIDTH_RATIO
    val left = canvasWidth - width - SLIDER_MARGIN
    val right = canvasWidth - SLIDER_MARGIN
    val top = canvasHeight * 0.18f
    val bottom = canvasHeight * 0.88f

    return SliderBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
    )
}
