package com.robotkinematicslab.mobile.ml.data

import org.junit.Assert.*
import org.junit.Test

class TrainingSplitContractTest {
    private fun samples() = (0 until 180).map { i -> EncodedTrainingSample(floatArrayOf((i%7).toFloat()),i%3,(i/2).toLong(),(i/30).toLong(),i.toLong()) }
    private fun partitions(split:TrainingDatasetSplit) = listOf(split.trainIndices,split.validationIndices,split.testIndices)
    @Test fun eachActualGroupingKeyIsDisjointAndReproducibleAndEveryRowAppearsOnce() {
        val data=samples();val preparer=TrainingDatasetPreparer()
        TrainingSplitStrategy.entries.forEach { strategy ->
            val split=preparer.split(data,42,strategy)
            val again=preparer.split(data,42,strategy)
            partitions(split).zip(partitions(again)).forEach { (a,b) -> assertArrayEquals(a,b) }
            assertEquals(data.indices.toList(),partitions(split).flatMap { it.toList() }.sorted())
            val groups=partitions(split).map { indices -> indices.map { if(strategy==TrainingSplitStrategy.SAMPLE_GROUPED) data[it].splitFingerprint else data[it].robotFingerprint }.toSet() }
            assertTrue(groups[0].intersect(groups[1]).isEmpty());assertTrue(groups[0].intersect(groups[2]).isEmpty());assertTrue(groups[1].intersect(groups[2]).isEmpty())
        }
        assertFalse(preparer.split(data,42).trainIndices.contentEquals(preparer.split(data,42,TrainingSplitStrategy.ROBOT_HELD_OUT).trainIndices))
    }
    @Test fun validationAndTestCannotChangeTrainingNormalizationOrClassWeights() {
        val data=samples();val preparer=TrainingDatasetPreparer()
        val split=preparer.split(data,42)
        val held=(split.validationIndices+split.testIndices).toSet()
        fun dataset(rows:List<EncodedTrainingSample>) = TrainingDataset("fixture",TrainingFeatureProfile.BASELINE_KINEMATICS,listOf("x"),rows,0,90)
        val original=preparer.prepare(dataset(data),42)
        val changed=preparer.prepare(dataset(data.mapIndexed { i,sample -> if(i in held) sample.copy(features=floatArrayOf(1e8f),labelIndex=2) else sample }),42)
        assertArrayEquals(original.normalization.means,changed.normalization.means,0f)
        assertArrayEquals(original.normalization.standardDeviations,changed.normalization.standardDeviations,0f)
        assertArrayEquals(original.classWeights,changed.classWeights,0f)
    }
    @Test fun insufficientIndependentGroupsRejectAndMissingClassesAreExplicit() {
        val preparer=TrainingDatasetPreparer()
        assertThrows(IllegalArgumentException::class.java) { preparer.split(samples().map { it.copy(robotFingerprint=1) },42,TrainingSplitStrategy.ROBOT_HELD_OUT) }
        assertThrows(IllegalArgumentException::class.java) { preparer.split(samples().map { it.copy(splitFingerprint=1) },42) }
        val singleClass=TrainingDataset("fixture",TrainingFeatureProfile.BASELINE_KINEMATICS,listOf("x"),samples().map { it.copy(labelIndex=0) },0,0)
        assertThrows(IllegalArgumentException::class.java) { preparer.prepare(singleClass,42) }
        val missing=preparer.split(samples().map { it.copy(labelIndex=it.labelIndex%2) },42)
        assertTrue(preparer.splitWarnings(missing).any { it.contains("Test partition has no rows for REJECTED") })
        assertTrue(preparer.splitWarnings(missing).any { it.contains("not exact row proportions") })
    }
}
