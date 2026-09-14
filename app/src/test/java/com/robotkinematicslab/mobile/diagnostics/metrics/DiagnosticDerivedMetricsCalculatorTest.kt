package com.robotkinematicslab.mobile.diagnostics.metrics
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class DiagnosticDerivedMetricsCalculatorTest {

    private val calculator =
        DiagnosticDerivedMetricsCalculator()

    private val robot =
        RobotDefinition(
            name = "Mixed scale robot",
            dhParameters =
                List(3) {
                    DHParameter(
                        theta = 0.0,
                        d = 0.0,
                        a = 0.1,
                        alpha = 0.0
                    )
                },
            joints =
                listOf(
                    JointDefinition(
                        name = "J1",
                        type = JointType.REVOLUTE,
                        minValue = -1.0,
                        maxValue = 1.0,
                        homeValue = 0.0
                    ),
                    JointDefinition(
                        name = "J2",
                        type = JointType.PRISMATIC,
                        minValue = 0.0,
                        maxValue = 10.0,
                        homeValue = 5.0
                    ),
                    JointDefinition(
                        name = "J3",
                        type = JointType.PRISMATIC,
                        minValue = 0.0,
                        maxValue = 0.5,
                        homeValue = 0.25
                    )
                )
        )

    @Test
    fun minimumNormalizedMargin_isScaleIndependent() {
        val centeredState =
            RobotState(
                jointValues = listOf(0.0, 5.0, 0.25)
            )

        val stateAtOneLimit =
            RobotState(
                jointValues = listOf(0.0, 5.0, 0.5)
            )

        assertEquals(
            0.5,
            calculator.minNormalizedLimitMargin(robot, centeredState),
            1e-12
        )
        assertEquals(
            0.0,
            calculator.minNormalizedLimitMargin(robot, stateAtOneLimit),
            1e-12
        )
    }

    @Test
    fun normalizedJointTravelRms_comparesMixedJointRanges() {
        val seed =
            RobotState(
                jointValues = listOf(0.0, 5.0, 0.25)
            )

        val solution =
            RobotState(
                jointValues = listOf(1.0, 10.0, 0.5)
            )

        assertEquals(
            0.5,
            calculator.normalizedJointTravelRms(
                robot = robot,
                seedState = seed,
                solutionState = solution
            ),
            1e-12
        )
    }

    @Test
    fun logConditionNumber_isFiniteAndCappedForDatasetExport() {
        assertEquals(
            3.0,
            calculator.logConditionNumber(1_000.0),
            1e-12
        )
        assertEquals(
            DiagnosticDerivedMetricsCalculator.LOG_CONDITION_NUMBER_CAP,
            calculator.logConditionNumber(Double.POSITIVE_INFINITY),
            1e-12
        )
        assertTrue(
            calculator.logConditionNumber(Double.NaN).isNaN()
        )
    }

    @Test
    fun normalizedJointTravelRms_usesShortestPathForFullTurnRevoluteJoint() {
        val fullTurnRobot =
            RobotDefinition(
                name = "Full turn robot",
                dhParameters =
                    listOf(
                        DHParameter(0.0, 0.0, 0.1, 0.0)
                    ),
                joints =
                    listOf(
                        JointDefinition(
                            name = "J1",
                            type = JointType.REVOLUTE,
                            minValue = -PI,
                            maxValue = PI,
                            homeValue = 0.0
                        )
                    )
            )

        val travel =
            calculator.normalizedJointTravelRms(
                robot = fullTurnRobot,
                seedState = RobotState(listOf(PI - 0.1)),
                solutionState = RobotState(listOf(-PI + 0.1))
            )

        assertEquals(
            0.2 / (2.0 * PI),
            travel,
            1e-12
        )
    }

    @Test
    fun stateSizeMismatch_returnsMissingMetricInsteadOfInventedValue() {
        val mismatchedState =
            RobotState(
                jointValues = listOf(0.0)
            )

        assertTrue(
            calculator.minNormalizedLimitMargin(
                robot = robot,
                state = mismatchedState
            ).isNaN()
        )
    }
}
