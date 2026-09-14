package com.robotkinematicslab.mobile.ui.editor

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.ui.input.ScientificNumberParser
import java.util.Locale

data class RobotBuildResult(
    val robot: RobotDefinition? = null,
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean
        get() = robot != null && errors.isEmpty()
}

class RobotEditorMapper {

    fun toEditorState(robot: RobotDefinition): RobotEditorState {
        require(robot.joints.size == robot.dhParameters.size) {
            "Robot joint and DH parameter counts must match."
        }

        return RobotEditorState(
            robotName = robot.name,
            dhRows =
                robot.joints.indices.map { index ->
                    val joint = robot.joints[index]
                    val dh = robot.dhParameters[index]

                    DhInputRow(
                        jointTypeText = joint.type.name,
                        thetaText = formatNumber(Math.toDegrees(dh.theta)),
                        dText = formatNumber(dh.d * 1000.0),
                        aText = formatNumber(dh.a * 1000.0),
                        alphaText = formatNumber(Math.toDegrees(dh.alpha)),
                        minText =
                            formatNumber(
                                if (joint.type == JointType.REVOLUTE) {
                                    Math.toDegrees(joint.minValue)
                                } else {
                                    joint.minValue * 1000.0
                                }
                            ),
                        maxText =
                            formatNumber(
                                if (joint.type == JointType.REVOLUTE) {
                                    Math.toDegrees(joint.maxValue)
                                } else {
                                    joint.maxValue * 1000.0
                                }
                            ),
                        homeText =
                            formatNumber(
                                if (joint.type == JointType.REVOLUTE) {
                                    Math.toDegrees(joint.homeValue)
                                } else {
                                    joint.homeValue * 1000.0
                                }
                            )
                    )
                }
        )
    }

    fun buildRobotDefinition(state: RobotEditorState): RobotBuildResult {
        val errors = mutableListOf<String>()

        val name = state.robotName.trim()
        if (name.isBlank()) {
            errors += "Robot name must not be blank."
        }

        if (name.contains('\n') || name.contains('\r')) {
            errors += "Robot name must be a single line."
        }

        if (state.dhRows.isEmpty()) {
            errors += "At least one DH row is required."
        }

        val dhParameters = mutableListOf<DHParameter>()
        val joints = mutableListOf<JointDefinition>()

        state.dhRows.forEachIndexed { index, row ->
            val rowNumber = index + 1

            val jointType = parseJointType(row.jointTypeText)
            if (jointType == null) {
                errors += "Row $rowNumber: joint type must be REVOLUTE or PRISMATIC."
                return@forEachIndexed
            }

            val thetaDeg = ScientificNumberParser.parseDouble(row.thetaText)
            val dMm = ScientificNumberParser.parseDouble(row.dText)
            val aMm = ScientificNumberParser.parseDouble(row.aText)
            val alphaDeg = ScientificNumberParser.parseDouble(row.alphaText)
            val minRaw = ScientificNumberParser.parseDouble(row.minText)
            val maxRaw = ScientificNumberParser.parseDouble(row.maxText)
            val homeRaw = ScientificNumberParser.parseDouble(row.homeText)

            if (thetaDeg == null) {
                errors += "Row $rowNumber: θ must be a valid finite number."
            }

            if (dMm == null) {
                errors += "Row $rowNumber: d must be a valid finite number."
            }

            if (aMm == null) {
                errors += "Row $rowNumber: a must be a valid finite number."
            }

            if (alphaDeg == null) {
                errors += "Row $rowNumber: α must be a valid finite number."
            }

            if (minRaw == null) {
                errors += "Row $rowNumber: min must be a valid finite number."
            }

            if (maxRaw == null) {
                errors += "Row $rowNumber: max must be a valid finite number."
            }

            if (homeRaw == null) {
                errors += "Row $rowNumber: home must be a valid finite number."
            }

            if (
                thetaDeg == null ||
                dMm == null ||
                aMm == null ||
                alphaDeg == null ||
                minRaw == null ||
                maxRaw == null ||
                homeRaw == null
            ) {
                return@forEachIndexed
            }

            if (
                !thetaDeg.isFinite() ||
                !dMm.isFinite() ||
                !aMm.isFinite() ||
                !alphaDeg.isFinite() ||
                !minRaw.isFinite() ||
                !maxRaw.isFinite() ||
                !homeRaw.isFinite()
            ) {
                errors += "Row $rowNumber: all DH values and limits must be finite."
                return@forEachIndexed
            }

            val thetaRad = Math.toRadians(thetaDeg)
            val dMeters = dMm / 1000.0
            val aMeters = aMm / 1000.0
            val alphaRad = Math.toRadians(alphaDeg)

            val minValue = when (jointType) {
                JointType.REVOLUTE -> Math.toRadians(minRaw)
                JointType.PRISMATIC -> minRaw / 1000.0
            }

            val maxValue = when (jointType) {
                JointType.REVOLUTE -> Math.toRadians(maxRaw)
                JointType.PRISMATIC -> maxRaw / 1000.0
            }

            val homeValue = when (jointType) {
                JointType.REVOLUTE -> Math.toRadians(homeRaw)
                JointType.PRISMATIC -> homeRaw / 1000.0
            }

            if (maxValue <= minValue) {
                errors += "Row $rowNumber: max must be greater than min."
                return@forEachIndexed
            }

            if (homeValue < minValue || homeValue > maxValue) {
                errors += "Row $rowNumber: home must be within min and max."
                return@forEachIndexed
            }

            val canonicalTheta = when (jointType) {
                JointType.REVOLUTE -> 0.0
                JointType.PRISMATIC -> thetaRad
            }

            val canonicalD = when (jointType) {
                JointType.REVOLUTE -> dMeters
                JointType.PRISMATIC -> 0.0
            }

            dhParameters += DHParameter(
                theta = canonicalTheta,
                d = canonicalD,
                a = aMeters,
                alpha = alphaRad
            )

            joints += JointDefinition(
                name = "J$rowNumber",
                type = jointType,
                minValue = minValue,
                maxValue = maxValue,
                homeValue = homeValue
            )
        }

        if (errors.isNotEmpty()) {
            return RobotBuildResult(
                robot = null,
                errors = errors
            )
        }

        return RobotBuildResult(
            robot = RobotDefinition(
                name = name,
                dhParameters = dhParameters,
                joints = joints
            ),
            errors = emptyList()
        )
    }

    private fun parseJointType(raw: String): JointType? {
        return when (raw.trim().uppercase()) {
            "R", "REVOLUTE" -> JointType.REVOLUTE
            "P", "PRISMATIC" -> JointType.PRISMATIC
            else -> null
        }
    }

    private fun formatNumber(value: Double): String {
        val formatted = String.format(Locale.US, "%.10f", value)
        return formatted.trimEnd('0').trimEnd('.').ifBlank { "0" }
    }
}
