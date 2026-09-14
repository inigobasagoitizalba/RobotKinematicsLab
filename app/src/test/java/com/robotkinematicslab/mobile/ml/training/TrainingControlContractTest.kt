package com.robotkinematicslab.mobile.ml.training

import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingControlMetadata
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class TrainingControlContractTest {
    private fun config() = LocalTrainingConfig("test","fixture.csv")
    @Test fun everySearchBudgetMatchesExactExecutionCandidatesAndFixedModelsIgnoreIt() {
        assertEquals(listOf(0,12),TrainingControlContract.candidateWidths(TrainingModelKind.AUTOMATIC,TrainingResourceMode.QUICK,24))
        assertEquals(listOf(0,24,32),TrainingControlContract.candidateWidths(TrainingModelKind.AUTOMATIC,TrainingResourceMode.BALANCED,24))
        assertEquals(listOf(0,24,48,72),TrainingControlContract.candidateWidths(TrainingModelKind.AUTOMATIC,TrainingResourceMode.MAXIMUM_ACCURACY,24))
        assertEquals(listOf(0,32),TrainingControlContract.candidateWidths(TrainingModelKind.AUTOMATIC,TrainingResourceMode.BALANCED,32))
        TrainingResourceMode.entries.forEach { mode ->
            assertEquals(listOf(0),TrainingControlContract.candidateWidths(TrainingModelKind.LINEAR_SOFTMAX,mode,24))
            assertEquals(listOf(37),TrainingControlContract.candidateWidths(TrainingModelKind.COMPACT_MLP,mode,37))
        }
        assertFalse(TrainingControlContract.hiddenUnitsApply(TrainingModelKind.AUTOMATIC,TrainingResourceMode.QUICK))
        assertTrue(TrainingControlContract.usesDefaultControls(config()))
        assertFalse(TrainingControlContract.usesDefaultControls(config().copy(learningRate=.004)))
        assertThrows(IllegalArgumentException::class.java) { TrainingControlContract.candidateWidths(TrainingModelKind.AUTOMATIC,TrainingResourceMode.BALANCED,513) }
    }
    @Test fun executionBoundsRejectEveryInvalidControlAndAcceptBothEnds() {
        assertTrue(TrainingControlContract.issues(config().copy(epochs=1,batchSize=1,learningRate=1e-6,l2Regularization=0.0,hiddenUnits=2,earlyStoppingPatience=1,workerCount=1)).isEmpty())
        assertTrue(TrainingControlContract.issues(config().copy(epochs=1000,batchSize=8192,learningRate=1.0,l2Regularization=1.0,hiddenUnits=512,earlyStoppingPatience=100,workerCount=64)).isEmpty())
        val invalid = listOf(config().copy(epochs=0),config().copy(epochs=1001),config().copy(batchSize=8193),config().copy(batchSize=0),config().copy(learningRate=Double.NaN),config().copy(learningRate=1e-7),config().copy(l2Regularization=Double.POSITIVE_INFINITY),config().copy(l2Regularization=-1.0),config().copy(hiddenUnits=1),config().copy(hiddenUnits=513),config().copy(earlyStoppingPatience=0),config().copy(earlyStoppingPatience=101),config().copy(workerCount=0),config().copy(workerCount=65))
        invalid.forEach { assertFalse(TrainingControlContract.issues(it).isEmpty()) }
    }
    @Test fun completeStoredControlsRoundTripAndPartialNonfiniteOrImpossibleWorkerEvidenceReject() {
        val c = StoredTrainingControls.from(config().copy(hiddenUnits=37,epochs=7,earlyStoppingPatience=3,workerCount=4),mapOf(1 to 9L,3 to 2L))
        val p = Properties().also { TrainingControlMetadata.write(it,c) }
        assertEquals(c,TrainingControlMetadata.read(p))
        assertNull(TrainingControlMetadata.read(Properties()))
        listOf("hiddenUnits" to null,"earlyStoppingPatience" to "0","learningRate" to "NaN","effectiveWorkerBatches" to "5:2","effectiveWorkerBatches" to "1:0","effectiveWorkerBatches" to "1:2;1:3","controlsSchemaVersion" to "9").forEach { (key,value) ->
            val altered = Properties().apply { putAll(p); if(value==null) remove(key) else setProperty(key,value) }
            assertThrows(IllegalArgumentException::class.java) { TrainingControlMetadata.read(altered) }
            assertEquals(c,TrainingControlMetadata.read(p))
        }
    }
}
