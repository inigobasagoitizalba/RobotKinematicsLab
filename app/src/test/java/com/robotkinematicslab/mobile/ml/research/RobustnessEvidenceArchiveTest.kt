package com.robotkinematicslab.mobile.ml.research

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RobustnessEvidenceArchiveTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun report(model:File)=StoredModelRobustnessResult("Original model",108,2,"training-standard-deviation σ",listOf(RobustnessSlice(0.0,2,0.0,0.0,0.0),RobustnessSlice(.1,2,.2,.5,.5)),"run-original",RobustnessEvidenceArchive.sha256(model),"b".repeat(64),listOf(12,44))
    @Test fun validResultReopensWithExactModelCorpusAndSampleRows() {
        val model=File(temporary.root,"model.bin").apply { writeText("original bytes") }
        val original=report(model);val saved=RobustnessEvidenceArchive.save(temporary.root,original)
        assertEquals(original,RobustnessEvidenceArchive.read(saved));assertEquals(original,RobustnessEvidenceArchive.latest(temporary.root,"run-original",model))
        assertTrue(runCatching { RobustnessEvidenceArchive.latest(temporary.root,"other-run",model) }.isFailure)
    }
    @Test fun zeroReplayCorruptionAndMissingModelAreExplicitFailures() {
        val model=File(temporary.root,"model.bin").apply { writeText("original bytes") };val original=report(model)
        listOf(original.copy(sampleRowIds=listOf(12,12)),original.copy(slices=listOf(RobustnessSlice(0.0,2,.01,0.0,0.0))),original.copy(slices=listOf(RobustnessSlice(.1,2,Double.NaN,0.0,0.0))),original.copy(slices=listOf(RobustnessSlice(.1,3,0.0,0.0,0.0)))).forEach { assertTrue(runCatching { RobustnessEvidenceArchive.save(temporary.root,it) }.isFailure) }
        val file=RobustnessEvidenceArchive.save(temporary.root,original);val bytes=file.readBytes();bytes[20]=(bytes[20].toInt() xor 1).toByte();file.writeBytes(bytes)
        assertTrue(runCatching { RobustnessEvidenceArchive.latest(temporary.root,"run-original",model) }.isFailure)
        model.writeText("changed model");assertNull(RobustnessEvidenceArchive.latest(temporary.root,"run-original",model))
        model.delete();assertTrue(runCatching { RobustnessEvidenceArchive.latest(temporary.root,"run-original",model) }.isFailure)
    }
    @Test fun failureIntroductionDenominatorIsAllPairedSamples() {
        val observations=listOf(PerturbationObservation(1,.1,0.0,.8,true,false),PerturbationObservation(2,.1,.8,.8,false,false))
        assertEquals(.5,ScientificEvidenceCalculator.robustness(observations).single().failureIntroductionRate,0.0)
    }
}
