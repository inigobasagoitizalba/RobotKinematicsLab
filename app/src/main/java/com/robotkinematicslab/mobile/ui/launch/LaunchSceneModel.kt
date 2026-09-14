package com.robotkinematicslab.mobile.ui.launch

import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** One independently articulated actor in the launch-scene choreography. */
internal data class LaunchRobotPose(
    val id: String,
    val jointPositions: List<Vec3>,
    val colorIndex: Int
)

/** Immutable, pure frame consumed by the Compose renderer. */
internal data class LaunchSceneFrame(
    val robots: List<LaunchRobotPose>,
    val dataCore: Vec3,
    val dataCorePulse: Double,
    val orbitRadians: Double
)

/**
 * Produces the small deterministic kinematic choreography shown before the project library.
 *
 * The three chains are intentionally separate actors: they are never concatenated and therefore
 * cannot be mistaken for one robot. Every moving segment is a fixed-length rotary link, keeping
 * the illustration finite, mechanically coherent and continuous for any supplied progress value.
 */
internal fun buildLaunchSceneFrame(progress: Double): LaunchSceneFrame {
    val phase = normalizedLaunchProgress(progress)
    val orbit = phase * TWO_PI
    val dataCore =
        Vec3(
            x = cos(orbit) * 0.20,
            y = 0.58 + sin(orbit * 2.0) * 0.07,
            z = sin(orbit) * 0.20
        )

    val robots =
        List(ACTOR_COUNT) { actorIndex ->
            buildLaunchRobotPose(
                actorIndex = actorIndex,
                orbit = orbit,
                dataCore = dataCore
            )
        }

    return LaunchSceneFrame(
        robots = robots,
        dataCore = dataCore,
        dataCorePulse = 0.5 + 0.5 * sin(orbit * 3.0),
        orbitRadians = orbit
    )
}

internal fun normalizedLaunchProgress(value: Double): Double {
    if (!value.isFinite()) return 0.0
    return value - floor(value)
}

internal fun normalizedLaunchYaw(value: Float): Float {
    if (!value.isFinite()) return 0f
    val fullTurn = TWO_PI.toFloat()
    val wrapped = value % fullTurn
    return when {
        wrapped > PI.toFloat() -> wrapped - fullTurn
        wrapped < -PI.toFloat() -> wrapped + fullTurn
        else -> wrapped
    }
}

private fun buildLaunchRobotPose(
    actorIndex: Int,
    orbit: Double,
    dataCore: Vec3
): LaunchRobotPose {
    val actorAngle = actorIndex.toDouble() * TWO_PI / ACTOR_COUNT.toDouble()
    val motionPhase = orbit + actorIndex.toDouble() * TWO_PI / ACTOR_COUNT.toDouble()
    val outward = Vec3(cos(actorAngle), 0.0, sin(actorAngle))
    val inward = outward * -1.0
    val tangent = Vec3(-sin(actorAngle), 0.0, cos(actorAngle))

    val base = outward * 1.05
    val shoulder = base + Vec3(0.0, 0.20, 0.0)
    val upperElevation = 0.68 + 0.11 * sin(motionPhase * 2.0)
    val upperDirection =
        inward * cos(upperElevation) + Vec3.UNIT_Y * sin(upperElevation)
    val elbow = shoulder + upperDirection * UPPER_ARM_LENGTH

    val forearmElevation = -0.16 + 0.19 * sin(motionPhase * 2.0)
    val forearmAzimuth = 0.24 * sin(motionPhase)
    val forearmHorizontal =
        inward * cos(forearmAzimuth) + tangent * sin(forearmAzimuth)
    val forearmDirection =
        forearmHorizontal * cos(forearmElevation) + Vec3.UNIT_Y * sin(forearmElevation)
    val wrist = elbow + forearmDirection * FOREARM_LENGTH

    val toCore = dataCore - wrist
    val toolDirection = toCore.normalizedOr(inward)
    val tool = wrist + toolDirection * TOOL_LENGTH

    return LaunchRobotPose(
        id = "launch-robot-$actorIndex",
        jointPositions = listOf(base, shoulder, elbow, wrist, tool),
        colorIndex = actorIndex
    )
}

private fun Vec3.normalizedOr(fallback: Vec3): Vec3 {
    val length = sqrt(x * x + y * y + z * z)
    return if (length.isFinite() && length > 1e-12) this * (1.0 / length) else fallback
}

private const val ACTOR_COUNT = 3
internal const val LAUNCH_BASE_COLUMN_LENGTH = 0.20
internal const val LAUNCH_UPPER_ARM_LENGTH = 0.48
internal const val LAUNCH_FOREARM_LENGTH = 0.36
internal const val LAUNCH_TOOL_LENGTH = 0.24
private const val UPPER_ARM_LENGTH = LAUNCH_UPPER_ARM_LENGTH
private const val FOREARM_LENGTH = LAUNCH_FOREARM_LENGTH
private const val TOOL_LENGTH = LAUNCH_TOOL_LENGTH
private const val TWO_PI = 2.0 * PI
