package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class RobotLibraryCodec {

    companion object {
        private const val MAGIC = 0x524B4C42
        private const val VERSION = 1
        private const val MAX_ROBOTS = 100_000
        private const val MAX_JOINTS_PER_ROBOT = 1_000
        private const val MAX_TOTAL_JOINTS = 100_000
    }

    fun write(
        robots: List<SavedRobot>,
        output: OutputStream
    ) {
        require(robots.size <= MAX_ROBOTS) {
            "Robot library is too large."
        }
        require(robots.sumOf { it.robot.joints.size.toLong() } <= MAX_TOTAL_JOINTS) {
            "Robot library contains too many joints in total."
        }

        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(robots.size)

            robots.forEach { savedRobot ->
                val robot = savedRobot.robot

                require(robot.joints.size == robot.dhParameters.size) {
                    "Robot ${robot.name} has mismatched joint and DH counts."
                }
                require(robot.joints.size <= MAX_JOINTS_PER_ROBOT) {
                    "Robot ${robot.name} has too many joints."
                }

                data.writeUTF(savedRobot.id)
                data.writeUTF(robot.name)
                data.writeInt(robot.joints.size)

                robot.joints.indices.forEach { index ->
                    val joint = robot.joints[index]
                    val dh = robot.dhParameters[index]

                    data.writeUTF(joint.name)
                    data.writeUTF(joint.type.name)
                    data.writeDouble(joint.minValue)
                    data.writeDouble(joint.maxValue)
                    data.writeDouble(joint.homeValue)
                    data.writeDouble(dh.theta)
                    data.writeDouble(dh.d)
                    data.writeDouble(dh.a)
                    data.writeDouble(dh.alpha)
                }
            }
        }
    }

    fun read(
        input: InputStream
    ): List<SavedRobot> {
        DataInputStream(input).use { data ->
            require(data.readInt() == MAGIC) {
                "Unknown robot library format."
            }
            require(data.readInt() == VERSION) {
                "Unsupported robot library version."
            }

            val robotCount = data.readInt()
            require(robotCount in 0..MAX_ROBOTS) {
                "Invalid robot count in library."
            }

            var totalJointCount = 0
            val robots = ArrayList<SavedRobot>(robotCount)
            repeat(robotCount) {
                val id = data.readUTF()
                val robotName = data.readUTF()
                val jointCount = data.readInt()

                require(jointCount in 1..MAX_JOINTS_PER_ROBOT) {
                    "Invalid joint count for robot $robotName."
                }
                totalJointCount += jointCount
                require(totalJointCount <= MAX_TOTAL_JOINTS) {
                    "Robot library contains too many joints in total."
                }

                val joints = ArrayList<JointDefinition>(jointCount)
                val dhParameters = ArrayList<DHParameter>(jointCount)

                repeat(jointCount) {
                    val jointName = data.readUTF()
                    val jointType = JointType.valueOf(data.readUTF())
                    val minValue = data.readDouble()
                    val maxValue = data.readDouble()
                    val homeValue = data.readDouble()
                    val theta = data.readDouble()
                    val d = data.readDouble()
                    val a = data.readDouble()
                    val alpha = data.readDouble()

                    joints +=
                        JointDefinition(
                            name = jointName,
                            type = jointType,
                            minValue = minValue,
                            maxValue = maxValue,
                            homeValue = homeValue
                        )

                    dhParameters +=
                        DHParameter(
                            theta = theta,
                            d = d,
                            a = a,
                            alpha = alpha
                        )
                }

                robots += SavedRobot(
                    id = id,
                    robot =
                        RobotDefinition(
                            name = robotName,
                            dhParameters = dhParameters,
                            joints = joints
                        )
                )
            }
            require(data.read() == -1) { "Unexpected trailing data in robot library." }
            return robots
        }
    }
}
