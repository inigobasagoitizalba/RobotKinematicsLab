package com.robotkinematicslab.mobile.ml.comparison

import com.robotkinematicslab.mobile.ml.data.*
import com.robotkinematicslab.mobile.ml.storage.*
import com.robotkinematicslab.mobile.ml.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ComparisonReplayContractTest {
    private fun row(id:Long)=EncodedTrainingSample(floatArrayOf(id.toFloat()),0,id*10,2,id)
    @Test fun orderedPopulationAllowsDifferentFeatureVectorsButRejectsEveryIdentityMutation() {
        val a=listOf(row(1),row(2))
        ComparisonReplayContract.verifyOrderedTest(a,a.map { it.copy(features=floatArrayOf(99f,17f)) })
        val invalid=listOf(a.reversed(),a.take(1),emptyList(),listOf(a[0].copy(labelIndex=1),a[1]),listOf(a[0].copy(splitFingerprint=3),a[1]),listOf(a[0].copy(robotFingerprint=4),a[1]),listOf(a[0].copy(sourceRowIndex=99),a[1]))
        invalid.forEach { b -> assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.verifyOrderedTest(a,b) } }
        assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.verifyOrderedTest(emptyList(),emptyList()) }
    }
    @Test fun explicitSelectionNeverFallsBackAndRequiresTwoExistingDistinctFiles() {
        val dir=Files.createTempDirectory("comparison-selection").toFile()
        try {
            val left=dir.resolve("left.rklm").apply { writeText("left") }.path
            val right=dir.resolve("right.rklm").apply { writeText("right") }.path
            val run=run(listOf(left,right))
            assertEquals(left to right,ComparisonReplayContract.selectedPaths(run,left,right))
            assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.selectedPaths(run,left,left) }
            assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.selectedPaths(run,"unknown",right) }
            assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.selectedPaths(run.copy(modelPaths=listOf(left)),left,null) }
            dir.resolve("right.rklm").delete()
            assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.selectedPaths(run,left,right) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun contractsRejectLegacySameModelAndChangedCorpusSamplingSeedOrProtocol() {
        val a=model("a");val b=model("b");val run=run(emptyList())
        ComparisonReplayContract.verifyContracts(run,a,b)
        val contract=requireNotNull(b.inferenceContract)
        val invalid=listOf(b.copy(runId="other"),b.copy(inferenceContract=null),a,
            b.copy(inferenceContract=contract.copy(corpusSha256="c".repeat(64))),
            b.copy(inferenceContract=contract.copy(randomSeed=7)),
            b.copy(inferenceContract=contract.copy(maximumRows=101)),
            b.copy(inferenceContract=contract.copy(sampleAcrossEntireFile=true)),
            b.copy(inferenceContract=contract.copy(protocolVersion=1)))
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { ComparisonReplayContract.verifyContracts(run,a,it) } }
    }
    private fun model(hash:String)=StoredLocalModel("run",TrainingFeatureProfile.BASELINE_KINEMATICS,"candidate",listOf("x"),FeatureNormalization(floatArrayOf(0f),floatArrayOf(1f)),
        LocalClassifierModel(TrainingModelKind.LINEAR_SOFTMAX,1,3,0,floatArrayOf(1f,0f,-1f),floatArrayOf(),floatArrayOf(),floatArrayOf(0f,0f,0f)),
        inferenceContract=TrainingInferenceContract("0".repeat(64),hash.repeat(64),"1".repeat(64),"2".repeat(64),100,42,TrainingSplitStrategy.SAMPLE_GROUPED,false))
    private fun run(paths:List<String>)=TrainingRunSummary("run","run","/source",1,2,0.5,0.6,0.1,42,TrainingSplitStrategy.SAMPLE_GROUPED,100,"/runs","/history",paths)
}
