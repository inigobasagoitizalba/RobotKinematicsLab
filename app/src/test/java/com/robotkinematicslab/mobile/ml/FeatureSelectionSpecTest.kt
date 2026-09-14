package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.EncodedTrainingSample
import com.robotkinematicslab.mobile.ml.data.FeatureIndexSelectionParser
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingDataset
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.project
import com.robotkinematicslab.mobile.ml.data.previewFeatureSelection
import com.robotkinematicslab.mobile.ml.ik.OneMicronFeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkDataset
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureEncoder
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureProfile
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkSample
import com.robotkinematicslab.mobile.ml.ik.project
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSelectionSpecTest {
    @Test
    fun parsesArbitraryOrderedRangesWithoutDuplicates() {
        assertEquals(listOf(0, 1, 2, 7, 8), FeatureIndexSelectionParser.parse("1-3, 8, 9, 2", 10).getOrThrow())
    }

    @Test
    fun rejectsEmptyReversedAndOutOfBoundsContracts() {
        assertTrue(FeatureIndexSelectionParser.parse("", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("8-2", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("11", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("0", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("1,,2", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("1-2-3", 10).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("1.5", 10).isFailure)
    }

    @Test
    fun previewUsesTheSameParserOrderNamesAndExplicitDuplicatePolicy() {
        val names = ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_EXPANDED)
        val preview = previewFeatureSelection(TrainingFeatureProfile.CONTEXT_EXPANDED, "1-3, 2, 130, 201-205").getOrThrow()

        assertEquals(listOf(1, 2, 3, 130, 201, 202, 203, 204, 205), preview.oneBasedPositions)
        assertEquals(preview.oneBasedPositions.map { names[it - 1] }, preview.featureNames)
        assertEquals(9, preview.featureNames.size)
    }

    @Test
    fun projectsNamesAndEveryRowInTheSameOrder() {
        val sourceNames = ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.BASELINE_KINEMATICS)
        val source =
            TrainingDataset(
                sourcePath = "memory",
                profile = TrainingFeatureProfile.BASELINE_KINEMATICS,
                featureNames = sourceNames,
                samples = listOf(EncodedTrainingSample(FloatArray(sourceNames.size) { it.toFloat() }, 0, 1, 2, 3)),
                skippedRowCount = 0,
                duplicateFingerprintCount = 0
            )
        val selection = FeatureSelectionSpec("target-only", "Target only", source.profile, sourceNames.slice(listOf(1, 3, 2)))
        val projected = source.project(selection)

        assertEquals(selection.includedFeatureNames, projected.featureNames)
        assertArrayEquals(floatArrayOf(1f, 3f, 2f), projected.samples.single().features, 0f)
    }

    @Test
    fun neuralIkProjectionCanSelectAnySubsetOfThe361Contract() {
        val profile = OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361
        val names = OneMicronIkFeatureEncoder.featureNames(profile)
        val sample = OneMicronIkSample(
            features = FloatArray(names.size) { it.toFloat() },
            normalizedJointDelta = FloatArray(10), outputMask = FloatArray(10), robotIndex = 0,
            seedState = RobotState(listOf(0.0)), target = Vec3(0.0, 0.0, 0.0),
            deterministicIterations = 1, sourceRowIndex = 1, splitFingerprint = 2, robotFingerprint = 3
        )
        val dataset = OneMicronIkDataset("memory", profile, names, emptyList(), emptyList(), listOf(sample), 1, 0, 0)
        val selection = OneMicronFeatureSelectionSpec("three", "Three", profile, listOf(names[360], names[0], names[108]))

        val projected = dataset.project(selection)

        assertEquals(selection.includedFeatureNames, projected.featureNames)
        assertArrayEquals(floatArrayOf(360f, 0f, 108f), projected.samples.single().features, 0f)
    }
}
