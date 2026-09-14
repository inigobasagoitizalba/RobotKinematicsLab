package com.robotkinematicslab.mobile.audit
import org.junit.Assert.*
import org.junit.Test
class OptInCampaignInputsTest {
    @Test fun nonemptyExplicitSeedsArePreservedInOrder() {
        assertEquals(listOf(2604),OptInCampaignInputs.seeds("2604"))
        assertEquals(listOf(2606,2604,-1,0),OptInCampaignInputs.seeds("2606, 2604,-1,0"))
    }
    @Test fun enabledCampaignCannotPassWithoutTrainingOrCountDuplicateSeedsAsReplications() {
        for(raw in listOf(""," ",",", "2604,",",2604","abc","2604,2604","2147483648")) {
            assertThrows("Rejected: $raw",IllegalArgumentException::class.java) { OptInCampaignInputs.seeds(raw) }
        }
    }
}
