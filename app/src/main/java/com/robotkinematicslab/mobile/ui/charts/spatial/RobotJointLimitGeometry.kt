package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Matrix4
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class WorkspaceJointLimitGuide(
    val jointIndex: Int,
    val jointName: String,
    val jointType: JointType,
    val minimumValue: Double,
    val maximumValue: Double,
    val currentValue: Double,
    val origin: Vec3,
    val axis: Vec3,
    val boundaryPoints: List<Vec3>,
    val currentPoint: Vec3,
    val isFullRotation: Boolean
)

/**
 * Builds joint-limit guides in the same standard-DH frames used by forward kinematics.
 * Each guide origin and axis are captured before the corresponding active DH transform.
 */
internal fun buildRobotJointLimitGuides(
    robot: RobotDefinition,
    jointValues: List<Double>,
    visualRadiusMeters: Double
): List<WorkspaceJointLimitGuide> {
    require(robot.dhParameters.size == robot.joints.size)
    require(jointValues.size == robot.joints.size)
    require(visualRadiusMeters.isFinite() && visualRadiusMeters > 0.0)

    var transform = Matrix4.identity()
    return robot.joints.mapIndexed { index, joint ->
        val baseParameter = robot.dhParameters[index]
        val value = jointValues[index]
        require(value.isFinite() && value in joint.minValue..joint.maxValue)

        val origin = transform.translation()
        val xAxis = transform.rotationColumn(0)
        val yAxis = transform.rotationColumn(1)
        val zAxis = transform.rotationColumn(2)
        val guideRadius = visualRadiusMeters * (1.0 - index * 0.035).coerceAtLeast(0.62)

        val guide =
            when (joint.type) {
                JointType.REVOLUTE -> {
                    val requestedSpan = (joint.maxValue - joint.minValue).coerceAtLeast(0.0)
                    val fullRotation = requestedSpan >= TWO_PI - FULL_ROTATION_EPSILON
                    val displayedSpan = requestedSpan.coerceAtMost(TWO_PI)
                    val segmentCount =
                        ((displayedSpan / TWO_PI) * REVOLUTE_GUIDE_SEGMENTS)
                            .toInt()
                            .coerceIn(MINIMUM_REVOLUTE_GUIDE_SEGMENTS, REVOLUTE_GUIDE_SEGMENTS)
                    val boundary =
                        (0..segmentCount).map { segment ->
                            val fraction = segment.toDouble() / segmentCount.toDouble()
                            val angle = joint.minValue + displayedSpan * fraction
                            origin + xAxis * (cos(angle) * guideRadius) + yAxis * (sin(angle) * guideRadius)
                        }
                    WorkspaceJointLimitGuide(
                        jointIndex = index,
                        jointName = joint.name,
                        jointType = joint.type,
                        minimumValue = joint.minValue,
                        maximumValue = joint.maxValue,
                        currentValue = value,
                        origin = origin,
                        axis = zAxis,
                        boundaryPoints = boundary,
                        currentPoint =
                            origin + xAxis * (cos(value) * guideRadius) + yAxis * (sin(value) * guideRadius),
                        isFullRotation = fullRotation
                    )
                }

                JointType.PRISMATIC ->
                    WorkspaceJointLimitGuide(
                        jointIndex = index,
                        jointName = joint.name,
                        jointType = joint.type,
                        minimumValue = joint.minValue,
                        maximumValue = joint.maxValue,
                        currentValue = value,
                        origin = origin,
                        axis = zAxis,
                        boundaryPoints =
                            listOf(
                                origin + zAxis * joint.minValue,
                                origin + zAxis * joint.maxValue
                            ),
                        currentPoint = origin + zAxis * value,
                        isFullRotation = false
                    )
            }

        val activeParameter =
            when (joint.type) {
                JointType.REVOLUTE -> baseParameter.copy(theta = value)
                JointType.PRISMATIC -> baseParameter.copy(d = value)
            }
        transform = transform * Matrix4.fromDH(activeParameter)
        guide
    }
}

private fun Matrix4.rotationColumn(column: Int): Vec3 =
    Vec3(m[0][column], m[1][column], m[2][column]).normalized()

private const val TWO_PI = 2.0 * PI
private const val REVOLUTE_GUIDE_SEGMENTS = 48
private const val MINIMUM_REVOLUTE_GUIDE_SEGMENTS = 6
private const val FULL_ROTATION_EPSILON = 1e-9
