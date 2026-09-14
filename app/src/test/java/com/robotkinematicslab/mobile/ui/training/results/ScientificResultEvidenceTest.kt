package com.robotkinematicslab.mobile.ui.training.results

import com.robotkinematicslab.mobile.ml.training.*
import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import org.junit.Assert.*
import org.junit.Test

class ScientificResultEvidenceTest {
    private fun metrics(matrix:List<List<Int>>) = ClassificationMetrics(matrix.sumOf { it.sum() },0.5,0.5,0.5,0.5,matrix,classSupport=matrix.map { it.sum() })
    @Test fun confusionRetainsRealClassOrderCountsAndNamedDenominators() {
        val raw=metrics(listOf(listOf(466,0,0),listOf(0,97,0),listOf(0,0,319)))
        val named=ScientificResultEvidence.classes(raw)
        assertEquals(TrainingLabel.entries,named.map { it.label });assertEquals(listOf(466,97,319),named.map { it.support })
        assertTrue(named.all { it.recall==1.0 });assertEquals(882,ScientificResultEvidence.confusion(raw).sumOf { it.count })
        val mixed=metrics(listOf(listOf(3,0,1),listOf(0,0,0),listOf(1,0,5)))
        assertNull(ScientificResultEvidence.classes(mixed)[1].recall)
        val cell=ScientificResultEvidence.confusion(mixed)[2];assertEquals(1,cell.count);assertEquals(4,cell.denominator);assertEquals(0.25,cell.rowFraction!!,0.0)
    }
    @Test fun corruptMatricesAndSupportAreRejectedRatherThanRelabelled() {
        val valid=metrics(listOf(listOf(1,0,0),listOf(0,0,0),listOf(0,0,0)))
        listOf(valid.copy(classSupport=listOf(0,1,0)),valid.copy(sampleCount=2),valid.copy(confusionMatrix=listOf(listOf(1))),valid.copy(confusionMatrix=listOf(listOf(-1,0,0),listOf(0,0,0),listOf(0,0,0)))).forEach { assertTrue(runCatching { ScientificResultEvidence.classes(it) }.isFailure) }
    }
    @Test fun weightedCalibrationPreservesSupportedBinsAndBreaksMissingIntervals() {
        val bins=listOf(CalibrationBin(.4,.5,2,.45,.5),CalibrationBin(.8,.9,2,.85,1.0))
        val raw=metrics(listOf(listOf(2,0,0),listOf(0,2,0),listOf(0,0,0))).copy(calibrationBins=bins,expectedCalibrationError=.1)
        val points=ScientificResultEvidence.calibration(raw)
        assertEquals(listOf(.45,.85),points.map { it.x });assertEquals(listOf(.5,1.0),points.map { it.y });assertTrue(points[1].breakBefore)
        assertTrue(points[0].label.contains("n=2"))
        assertTrue(runCatching { ScientificResultEvidence.calibration(raw.copy(expectedCalibrationError=.8)) }.isFailure)
        assertTrue(runCatching { ScientificResultEvidence.calibration(raw.copy(calibrationBins=listOf(bins[0].copy(meanConfidence=Double.NaN),bins[1]))) }.isFailure)
        assertTrue(runCatching { ScientificResultEvidence.calibration(raw.copy(sampleCount=5)) }.isFailure)
    }
    @Test fun sliceGroupingDoesNotAggregateRobotsWithSameTopology() {
        val raw=metrics(listOf(listOf(1,0,0),listOf(0,0,0),listOf(0,0,0)))
        val slices=listOf(ClassificationSliceMetrics("robot:p0p","Robot: p0p",raw),ClassificationSliceMetrics("robot:p1m","Robot: p1m",raw),ClassificationSliceMetrics("topology:REVOLUTE-PRISMATIC","Topology: REVOLUTE-PRISMATIC",raw.copy(sampleCount=2)))
        assertEquals(slices.take(2),ScientificResultEvidence.slices(slices,"robot"))
        assertEquals("R–P · 2 joints",ScientificResultEvidence.sliceName(slices.last()))
        assertTrue(runCatching { ScientificResultEvidence.slices(slices+slices.first(),"robot") }.isFailure)
        assertTrue(ScientificResultEvidence.slices(slices,"target-source").isEmpty())
    }
}
