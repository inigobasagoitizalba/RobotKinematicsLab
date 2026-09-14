package com.robotkinematicslab.mobile.ml.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedCrossValidationPlannerTest {
    @Test
    fun robotGroupsNeverLeakAcrossFoldBoundaryAndEveryRowIsValidatedOnce() {
        val samples = samples()
        val folds = GroupedCrossValidationPlanner().plan(samples, ScientificGroupingLevel.ROBOT, 3, 42)

        assertEquals(3, folds.size)
        assertEquals(samples.indices.toSet(), folds.flatMap { it.validationIndices.toList() }.toSet())
        assertEquals(samples.size, folds.sumOf { it.validationIndices.size })
        folds.forEach { fold ->
            val trainingRobots = fold.trainingIndices.map { samples[it].robotId }.toSet()
            val validationRobots = fold.validationIndices.map { samples[it].robotId }.toSet()
            assertTrue(trainingRobots.intersect(validationRobots).isEmpty())
        }
    }

    @Test
    fun topologyPlanIsDeterministicAndCapsFoldCountAtAvailableGroups() {
        val samples = samples()
        val planner = GroupedCrossValidationPlanner()
        val first = planner.plan(samples, ScientificGroupingLevel.TOPOLOGY, 10, 2604)
        val second = planner.plan(samples, ScientificGroupingLevel.TOPOLOGY, 10, 2604)

        assertEquals(2, first.size)
        assertEquals(first.map { it.validationIndices.toList() }, second.map { it.validationIndices.toList() })
        first.forEach { fold ->
            val trainingTopologies = fold.trainingIndices.map { samples[it].topologyKey }.toSet()
            assertFalse(fold.validationGroups.any(trainingTopologies::contains))
        }
    }

    private fun samples(): List<EncodedTrainingSample> =
        listOf("r1", "r1", "r2", "r2", "r3", "r3").mapIndexed { index, robot ->
            EncodedTrainingSample(
                features = floatArrayOf(index.toFloat()),
                labelIndex = index % 3,
                splitFingerprint = index.toLong(),
                robotFingerprint = robot.hashCode().toLong(),
                sourceRowIndex = index.toLong(),
                robotId = robot,
                topologyKey = if (robot == "r3") "prismatic" else "revolute"
            )
        }
}
