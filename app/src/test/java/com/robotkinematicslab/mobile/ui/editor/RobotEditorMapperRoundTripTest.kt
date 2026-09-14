package com.robotkinematicslab.mobile.ui.editor

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotEditorMapperRoundTripTest {

    @Test
    fun storedRobotCanBeEditedAndMappedBackWithoutUnitDrift() {
        val expected = DatasetRobotPresets().buildDefaults()[7].robot
        val mapper = RobotEditorMapper()

        val actual = mapper.buildRobotDefinition(mapper.toEditorState(expected))

        assertTrue(actual.errors.toString(), actual.isSuccess)
        val rebuilt = requireNotNull(actual.robot)
        assertEquals(expected.name, rebuilt.name)
        assertEquals(expected.joints.map { it.type }, rebuilt.joints.map { it.type })

        expected.dhParameters.indices.forEach { index ->
            val expectedDh = expected.dhParameters[index]
            val actualDh = rebuilt.dhParameters[index]
            assertEquals(expectedDh.theta, actualDh.theta, 1e-10)
            assertEquals(expectedDh.d, actualDh.d, 1e-10)
            assertEquals(expectedDh.a, actualDh.a, 1e-10)
            assertEquals(expectedDh.alpha, actualDh.alpha, 1e-10)

            val expectedJoint = expected.joints[index]
            val actualJoint = rebuilt.joints[index]
            assertEquals(expectedJoint.minValue, actualJoint.minValue, 1e-10)
            assertEquals(expectedJoint.maxValue, actualJoint.maxValue, 1e-10)
            assertEquals(expectedJoint.homeValue, actualJoint.homeValue, 1e-10)
        }
    }

    @Test
    fun nonFiniteEditorValuesAreRejected() {
        val mapper = RobotEditorMapper()
        val state =
            RobotEditorState(
                robotName = "Invalid",
                dhRows = listOf(DhInputRow(aText = "NaN"))
            )

        val result = mapper.buildRobotDefinition(state)

        assertTrue(result.robot == null)
        assertTrue(result.errors.any { it.contains("finite") })
    }
    @Test
    fun everyNumericEditorFieldRejectsNonFiniteOverflowAndAmbiguousInput() {
        val fields:List<(String)->DhInputRow> = listOf(
            { DhInputRow(thetaText=it) },{ DhInputRow(dText=it) },{ DhInputRow(aText=it) },
            { DhInputRow(alphaText=it) },{ DhInputRow(minText=it) },
            { DhInputRow(maxText=it) },{ DhInputRow(homeText=it) }
        )
        for (raw in listOf("NaN","Infinity","+Infinity","-Infinity","1e309","-1e309","1,234.5","1.234,5","1,2,3","--2","")) {
            fields.forEachIndexed { index,row ->
                val result=RobotEditorMapper().buildRobotDefinition(RobotEditorState(robotName="Invalid",dhRows=listOf(row(raw))))
                assertTrue("Field $index accepted $raw",result.robot==null)
                assertTrue("Field $index lacks finite-number guidance for $raw",result.errors.any { it.contains("finite") })
            }
        }
    }

    @Test
    fun finiteCommaDecimalsAndScientificNotationKeepEditorUnits() {
        val result=RobotEditorMapper().buildRobotDefinition(RobotEditorState(robotName="Finite",dhRows=listOf(DhInputRow(aText="1000,5",dText="1e3"))))
        assertTrue(result.errors.toString(),result.isSuccess)
        val dh=requireNotNull(result.robot).dhParameters.single()
        assertEquals(1.0005,dh.a,1e-12)
        assertEquals(1.0,dh.d,1e-12)
    }

}
