package com.robotkinematicslab.mobile.ui.training.explainability
import org.junit.Assert.*
import org.junit.Test
class ExplainabilityFeatureNamesTest {
    @Test fun verifiedJointNamesKeepExactIdsAndUnknownFeaturesRemainExplicit() {
        assertEquals("Joint 2 initial joint value (solver seed) · joint_2_seed",ExplainabilityFeatureNames.describe("joint_2_seed"))
        assertEquals("Joint 4 DH a link length · joint_4_dh_a",ExplainabilityFeatureNames.describe("joint_4_dh_a"))
        assertEquals("Joint 4 presence indicator · joint_4_present",ExplainabilityFeatureNames.describe("joint_4_present"))
        for(id in listOf("new_feature","joint_11_seed","joint_0_present")) assertEquals("$id · verified description unavailable",ExplainabilityFeatureNames.describe(id))
    }
}
