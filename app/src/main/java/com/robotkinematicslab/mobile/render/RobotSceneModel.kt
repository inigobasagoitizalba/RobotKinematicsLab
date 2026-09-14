package com.robotkinematicslab.mobile.render

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure, testable scene geometry used by [RobotScene3D]. Keeping the viewport maths out of
 * Compose lets the stress tests verify thousands of robots without requiring an emulator.
 */
data class RobotSceneViewport(
    val widthPx: Float,
    val heightPx: Float,
    val centerX: Float,
    val centerY: Float,
    val pixelsPerMeter: Float,
    val workspaceRadius: Double,
    val gridExtent: Double,
    val gridStep: Double
)

data class RobotScenePoint(
    val x: Float,
    val y: Float
)

internal data class RobotSceneProjectionContext(
    val viewport: RobotSceneViewport,
    val cosYaw: Float,
    val sinYaw: Float,
    val cosPitch: Float,
    val sinPitch: Float
)

data class RobotSceneLink(
    val jointIndex: Int,
    val start: Vec3,
    val end: Vec3,
    val jointType: JointType
)

data class RobotSceneJointMarker(
    val jointIndex: Int,
    val position: Vec3,
    val jointType: JointType,
    val isBase: Boolean
)

data class RobotSceneGeometry(
    val links: List<RobotSceneLink>,
    val jointMarkers: List<RobotSceneJointMarker>,
    val endEffector: Vec3?
)

private const val DEFAULT_WORKSPACE_RADIUS = 1.0
private const val MIN_WORKSPACE_RADIUS = 0.10
private const val VIEWPORT_EDGE_MARGIN_PX = 18f
private const val VIEWPORT_SCALE_MARGIN = 0.90f
private const val SLIDER_RESERVED_WIDTH_RATIO = 0.14f

fun robotWorkspaceRadius(robot: RobotDefinition?): Double {
    if (robot == null || robot.dhParameters.isEmpty() || robot.joints.isEmpty()) {
        return DEFAULT_WORKSPACE_RADIUS
    }

    val radius = RobotReachEnvelope.conservativeRadialUpperBound(robot)

    return radius
        .takeIf { it.isFinite() && it > 0.0 }
        ?.coerceAtLeast(MIN_WORKSPACE_RADIUS)
        ?: DEFAULT_WORKSPACE_RADIUS
}

fun buildRobotSceneGeometry(
    jointPositions: List<Vec3>,
    jointTypes: List<JointType>
): RobotSceneGeometry {
    val segmentCount = min(jointTypes.size, (jointPositions.size - 1).coerceAtLeast(0))

    val links = List(segmentCount) { index ->
        RobotSceneLink(
            jointIndex = index,
            start = jointPositions[index],
            end = jointPositions[index + 1],
            jointType = jointTypes[index]
        )
    }

    // In standard DH, joint i acts about/along z_(i-1), so its marker belongs at
    // the start of transform i; placing it after the transform shifts the marker by one frame.
    val markers = List(segmentCount) { index ->
        RobotSceneJointMarker(
            jointIndex = index,
            position = jointPositions[index],
            jointType = jointTypes[index],
            isBase = index == 0
        )
    }

    return RobotSceneGeometry(
        links = links,
        jointMarkers = markers,
        endEffector = jointPositions.getOrNull(segmentCount)
    )
}

fun buildRobotSceneViewport(
    canvasWidth: Float,
    canvasHeight: Float,
    workspaceRadius: Double
): RobotSceneViewport {
    val width = canvasWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = canvasHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val radius = workspaceRadius
        .takeIf { it.isFinite() && it > 0.0 }
        ?.coerceAtLeast(MIN_WORKSPACE_RADIUS)
        ?: DEFAULT_WORKSPACE_RADIUS

    val centerX = width * 0.45f
    val centerY = height * 0.54f
    val sliderLeft = width * (1f - SLIDER_RESERVED_WIDTH_RATIO)

    val horizontalRadiusPx = min(
        centerX - VIEWPORT_EDGE_MARGIN_PX,
        sliderLeft - centerX - VIEWPORT_EDGE_MARGIN_PX
    ).coerceAtLeast(1f)
    val verticalRadiusPx = min(
        centerY - VIEWPORT_EDGE_MARGIN_PX,
        height - centerY - VIEWPORT_EDGE_MARGIN_PX
    ).coerceAtLeast(1f)

    val pixelsPerMeter =
        (min(horizontalRadiusPx, verticalRadiusPx) * VIEWPORT_SCALE_MARGIN / radius)
            .toFloat()
            .coerceAtLeast(1e-4f)

    val gridStep = niceGridStep(radius)
    val gridExtent = kotlin.math.ceil(radius / gridStep) * gridStep

    return RobotSceneViewport(
        widthPx = width,
        heightPx = height,
        centerX = centerX,
        centerY = centerY,
        pixelsPerMeter = pixelsPerMeter,
        workspaceRadius = radius,
        gridExtent = gridExtent,
        gridStep = gridStep
    )
}

fun projectRobotScenePoint(
    point: Vec3,
    viewport: RobotSceneViewport,
    yaw: Float,
    pitch: Float
): RobotScenePoint {
    return projectRobotScenePoint(
        point = point,
        context = buildRobotSceneProjectionContext(viewport, yaw, pitch)
    )
}

internal fun buildRobotSceneProjectionContext(
    viewport: RobotSceneViewport,
    yaw: Float,
    pitch: Float
): RobotSceneProjectionContext {
    return RobotSceneProjectionContext(
        viewport = viewport,
        cosYaw = cos(yaw),
        sinYaw = sin(yaw),
        cosPitch = cos(pitch),
        sinPitch = sin(pitch)
    )
}

internal fun projectRobotScenePoint(
    point: Vec3,
    context: RobotSceneProjectionContext
): RobotScenePoint {
    val x = point.x.toFloat()
    val y = point.y.toFloat()
    val z = point.z.toFloat()

    val rotatedX = x * context.cosYaw - z * context.sinYaw
    val rotatedZ = x * context.sinYaw + z * context.cosYaw
    val rotatedY = y * context.cosPitch - rotatedZ * context.sinPitch

    return RobotScenePoint(
        x = context.viewport.centerX + rotatedX * context.viewport.pixelsPerMeter,
        y = context.viewport.centerY - rotatedY * context.viewport.pixelsPerMeter
    )
}

fun screenToRobotTargetOnHeightPlane(
    screenX: Float,
    screenY: Float,
    viewport: RobotSceneViewport,
    yaw: Float,
    pitch: Float,
    currentY: Double
): Vec3? {
    if (!screenX.isFinite() || !screenY.isFinite() || !currentY.isFinite()) return null

    val screenDx = (screenX - viewport.centerX) / viewport.pixelsPerMeter
    val screenDy = -(screenY - viewport.centerY) / viewport.pixelsPerMeter

    val cosYaw = cos(yaw)
    val sinYaw = sin(yaw)
    val cosPitch = cos(pitch)
    val sinPitch = sin(pitch)

    if (!cosPitch.isFinite() || !sinPitch.isFinite() || abs(sinPitch) < 1e-5f) return null

    val yPlane = currentY.toFloat()
    val rotatedX = screenDx
    val rotatedZ = (yPlane * cosPitch - screenDy) / sinPitch

    val worldX = rotatedX * cosYaw + rotatedZ * sinYaw
    val worldZ = -rotatedX * sinYaw + rotatedZ * cosYaw

    if (!worldX.isFinite() || !worldZ.isFinite()) return null

    val limit = viewport.workspaceRadius
    return Vec3(
        x = worldX.toDouble().coerceIn(-limit, limit),
        y = currentY.coerceIn(-limit, limit),
        z = worldZ.toDouble().coerceIn(-limit, limit)
    )
}

fun targetYFromSlider(
    touchY: Float,
    sliderTop: Float,
    sliderBottom: Float,
    currentTarget: Vec3,
    workspaceRadius: Double
): Vec3 {
    val limit = workspaceRadius
        .takeIf { it.isFinite() && it > 0.0 }
        ?: DEFAULT_WORKSPACE_RADIUS
    val clampedY = touchY.coerceIn(sliderTop, sliderBottom)
    val normalized = 1f - ((clampedY - sliderTop) / (sliderBottom - sliderTop))
    val y = -limit + normalized * (limit * 2.0)

    return Vec3(currentTarget.x, y.coerceIn(-limit, limit), currentTarget.z)
}

fun sliderYFromTarget(
    targetY: Double,
    sliderTop: Float,
    sliderBottom: Float,
    workspaceRadius: Double
): Float {
    val limit = workspaceRadius
        .takeIf { it.isFinite() && it > 0.0 }
        ?: DEFAULT_WORKSPACE_RADIUS
    val normalized = ((targetY.coerceIn(-limit, limit) + limit) / (limit * 2.0)).toFloat()
    return sliderBottom - normalized * (sliderBottom - sliderTop)
}

private fun niceGridStep(radius: Double): Double {
    val rawStep = (radius / 4.0).coerceAtLeast(1e-6)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceNormalized * magnitude
}
