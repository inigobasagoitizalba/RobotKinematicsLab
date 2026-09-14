package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.storage.*
import com.robotkinematicslab.mobile.ml.data.*
import org.junit.Assert.*
import org.junit.Test

class ScientificScaleCohortTest {
    private fun variant(id:String)=StoredTrainingVariantEvidence(id,"Config $id",TrainingFeatureProfile.BASELINE_KINEMATICS,108,70,15,15,.8,.8,.8,.1,.1,.1,100.0,1000,108)
    private fun run(id:String,variants:List<StoredTrainingVariantEvidence>)=TrainingRunSummary(id,"Run $id","/same-file.csv",1,2,.8,null,Double.NaN,42,TrainingSplitStrategy.SAMPLE_GROUPED,999,"/run/$id","/run/$id/history.csv",emptyList(),variants=variants)
    @Test fun repeatedSizesKeepConfigurationIdentitiesAndEffectiveRowCounts() {
        val run=run("first",listOf(variant("a"),variant("b")))
        val points=listOf(run).toEvidencePoints()
        assertEquals(2,points.size);assertEquals(2,points.map { it.id }.distinct().size);assertTrue(points.all { it.rowCount==100 && it.rowCount!=run.maximumRows })
        assertEquals(setOf("first"),points.map { it.runId }.toSet())
        val other=run("second",listOf(variant("a")))
        assertNotEquals(points.first().id,listOf(other).toEvidencePoints().first().id)
    }
    @Test fun legacyInvalidAndZeroSupportAreExcludedWithReasons() {
        val legacy=run("old",emptyList());assertTrue(listOf(legacy).toEvidencePoints().isEmpty());assertTrue(evidenceExclusions(legacy).single().contains("Legacy"))
        val corrupt=run("invalid",listOf(variant("a").copy(testMacroF1=Double.NaN),variant("b").copy(testRowCount=0),variant("c").copy(trainRowCount=Int.MAX_VALUE)))
        assertEquals(3,evidenceExclusions(corrupt).size);assertTrue(listOf(corrupt).toEvidencePoints().isEmpty())
    }
}
