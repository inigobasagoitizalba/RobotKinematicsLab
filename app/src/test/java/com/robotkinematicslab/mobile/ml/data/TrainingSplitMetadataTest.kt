package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.storage.TrainingSplitMetadata
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class TrainingSplitMetadataTest {
    private val evidence=TrainingSplitEvidence(TrainingSplitEvidence.PROTOCOL,TrainingSplitStrategy.ROBOT_HELD_OUT,listOf(20,5,5),listOf(4,1,1),listOf(listOf(10,5,5),listOf(3,2,0),listOf(2,1,2)),true)
    @Test fun effectiveRowsGroupsClassesAndFallbackRoundTripAndLegacyIsExplicit() {
        val p=Properties().also { TrainingSplitMetadata.write(it,"variant.0",evidence) }
        assertEquals(evidence,TrainingSplitMetadata.read(p,"variant.0",listOf(20,5,5),TrainingSplitStrategy.ROBOT_HELD_OUT))
        assertNull(TrainingSplitMetadata.read(Properties(),"variant.0",listOf(20,5,5),TrainingSplitStrategy.ROBOT_HELD_OUT))
    }
    @Test fun partialUnknownOrInconsistentPartitionEvidenceRejects() {
        val original=Properties().also { TrainingSplitMetadata.write(it,"variant.0",evidence) }
        listOf("splitClasses" to null,"splitProtocol" to "future", "splitGroups" to "21:1:1", "splitClasses" to "10:5:6;3:2:0;2:1:2", "splitRows" to "19:5:5", "splitStrategy" to "SAMPLE_GROUPED", "splitSortedFallback" to "unknown").forEach { (key,value) ->
            val p=Properties().apply { putAll(original); if(value==null) remove("variant.0.$key") else setProperty("variant.0.$key",value) }
            assertThrows(IllegalArgumentException::class.java) { TrainingSplitMetadata.read(p,"variant.0",listOf(20,5,5),TrainingSplitStrategy.ROBOT_HELD_OUT) }
            assertEquals(evidence,TrainingSplitMetadata.read(original,"variant.0",listOf(20,5,5),TrainingSplitStrategy.ROBOT_HELD_OUT))
        }
    }
    @Test fun partitionBuilderRejectsCrossPartitionGroupsAndMissingRows() {
        val rows=(0..5).map { EncodedTrainingSample(floatArrayOf(1f),it%3,it.toLong(),it.toLong(),it.toLong()) }
        val valid=TrainingDatasetSplit(intArrayOf(0,1),intArrayOf(2,3),intArrayOf(4,5),true,TrainingSplitStrategy.SAMPLE_GROUPED)
        assertEquals(listOf(2,2,2),TrainingSplitEvidence.from(rows,valid).rowCounts)
        assertThrows(IllegalArgumentException::class.java) { TrainingSplitEvidence.from(rows,valid.copy(testIndices=intArrayOf(0,5))) }
        assertThrows(IllegalArgumentException::class.java) { TrainingSplitEvidence.from(rows,valid.copy(testIndices=intArrayOf(4))) }
    }
}
