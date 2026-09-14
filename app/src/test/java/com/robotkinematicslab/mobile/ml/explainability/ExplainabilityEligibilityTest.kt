package com.robotkinematicslab.mobile.ml.explainability

import org.junit.Assert.*
import org.junit.Test

class ExplainabilityEligibilityTest {
    @Test fun controlDefaultsAndExactBoundariesAreValidAndMalformedValuesAreNot() {
        assertEquals(512,ExplainabilityPreflight.DEFAULT_SAMPLES);assertEquals(32,ExplainabilityPreflight.DEFAULT_STEPS)
        listOf(16 to 8,512 to 32,5000 to 256).forEach { (n,s) -> assertTrue(ExplainabilityPreflight.controlErrors(n,s).isEmpty()) }
        listOf(null to 32,15 to 32,5001 to 32,512 to null,512 to 7,512 to 257).forEach { (n,s) -> assertTrue(ExplainabilityPreflight.controlErrors(n,s).isNotEmpty()) }
    }
    @Test fun predictionsAndAuxiliaryWorkRemainSeparateAtEveryPhaseAndForSmallTestSets() {
        fun counts(phase:ExplainabilityPhase,n:Int,total:Int)=ExplainabilityProgress(phase,n,total,"").workCounts()
        assertEquals(ExplainabilityWorkCounts(0,512,0),counts(ExplainabilityPhase.LOADING_MODEL,0,516))
        assertEquals(ExplainabilityWorkCounts(0,512,2),counts(ExplainabilityPhase.REBUILDING_SPLIT,2,516))
        assertEquals(ExplainabilityWorkCounts(1,512,3),counts(ExplainabilityPhase.EXPLAINING_SAMPLES,4,516))
        assertEquals(ExplainabilityWorkCounts(512,512,3),counts(ExplainabilityPhase.AGGREGATING,515,516))
        assertEquals(ExplainabilityWorkCounts(512,512,4),counts(ExplainabilityPhase.COMPLETED,516,516))
        assertEquals(ExplainabilityWorkCounts(3,3,4),counts(ExplainabilityPhase.COMPLETED,7,7))
        assertThrows(IllegalArgumentException::class.java) { counts(ExplainabilityPhase.COMPLETED,8,7) }
        assertThrows(IllegalArgumentException::class.java) { counts(ExplainabilityPhase.LOADING_MODEL,0,3) }
    }
}
