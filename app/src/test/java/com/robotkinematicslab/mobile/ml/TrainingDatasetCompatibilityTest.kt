package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationBatch
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.DatasetCompatibilityLevel
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetCompatibilityEvaluator
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDatasetCompatibilityTest {

    @Test
    fun separatesExactMismatchFromSafeRowShortfall() {
        val manifest = manifest()
        val partial = TrainingDatasetCompatibilityEvaluator.evaluate(
            manifest,
            requirements(requestedRows = 20_000)
        )
        assertEquals(DatasetCompatibilityLevel.PARTIAL, partial.level)

        val incompatible = TrainingDatasetCompatibilityEvaluator.evaluate(
            manifest,
            requirements(requestedRows = 5_000).copy(exactTolerance = 1e-6)
        )
        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE, incompatible.level)
        assertTrue(incompatible.reasons.single().contains("tolerance"))
    }

    @Test
    fun acceptsAnExactScientificContract() {
        val result = TrainingDatasetCompatibilityEvaluator.evaluate(manifest(), requirements(5_000))
        assertEquals(DatasetCompatibilityLevel.COMPATIBLE, result.level)
    }

    @Test
    fun rejectsAnAppendedManifestWithoutCompleteBatchHistory() {
        val corrupted = manifest().copy(generationCount = 2, rowCount = 20_000, batches = emptyList())

        val result = TrainingDatasetCompatibilityEvaluator.evaluate(corrupted, requirements(5_000))

        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE, result.level)
        assertTrue(result.reasons.any { it.contains("append history") })
    }

    @Test
    fun rejectsAHiddenMismatchInAnEarlierBatch() {
        val first = batch(0, 0, tolerance = 1e-6)
        val second = batch(1, 5_000, tolerance = 1e-4)
        val mixed = manifest().copy(generationCount = 2, rowCount = 10_000, batches = listOf(first, second))

        val result = TrainingDatasetCompatibilityEvaluator.evaluate(mixed, requirements(5_000))

        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE, result.level)
        assertTrue(result.reasons.any { it.contains("batch 1 tolerance") })
    }

    @Test
    fun acceptsCompleteHomogeneousBatchHistory() {
        val complete =
            manifest().copy(
                generationCount = 2,
                rowCount = 10_000,
                batches = listOf(batch(0, 0), batch(1, 5_000))
            )

        assertEquals(
            DatasetCompatibilityLevel.COMPATIBLE,
            TrainingDatasetCompatibilityEvaluator.evaluate(complete, requirements(5_000)).level
        )
    }

    @Test
    fun everyEligibilityFilterRejectsItsOwnMismatchAndSolverCanBeOptional() {
        val base = requirements(5_000)
        val mismatches = listOf(base.copy(minimumRobotCount = 3), base.copy(exactTolerance = 2e-4),
            base.copy(exactMaxIterations = 999), base.copy(exactDamping = 0.01), base.copy(exactMaxStep = 0.1),
            base.copy(targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE), base.copy(reachableFraction = 0.8))
        mismatches.forEach { assertEquals(it.toString(), DatasetCompatibilityLevel.INCOMPATIBLE,
            TrainingDatasetCompatibilityEvaluator.evaluate(manifest(), it).level) }
        val differentSolver = manifest().copy(ikConfig = IKConfig(tolerance = 1e-12))
        assertEquals(DatasetCompatibilityLevel.COMPATIBLE, TrainingDatasetCompatibilityEvaluator.evaluate(differentSolver,
            TrainingDatasetRequirements(5_000, 2)).level)
        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE, TrainingDatasetCompatibilityEvaluator.evaluate(differentSolver,
            TrainingDatasetRequirements(5_000, 2, exactTolerance = 2e-12)).level)
        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE, TrainingDatasetCompatibilityEvaluator.evaluate(
            manifest().copy(randomProtocol = "legacy"), base).level)
    }

    @Test
    fun rejectsDuplicatedAppendIdentityAndRobotIdentity() {
        val repeated = manifest().copy(generationCount = 2,
            batches = listOf(batch(0, 0), batch(1, 5_000).copy(batchId = "batch-1")))
        assertEquals(DatasetCompatibilityLevel.INCOMPATIBLE,
            TrainingDatasetCompatibilityEvaluator.evaluate(repeated, requirements(5_000)).level)
        assertThrows(IllegalArgumentException::class.java) {
            manifest().copy(robotIds = listOf("r1", "r1"))
        }
        assertEquals(
            DatasetCompatibilityLevel.COMPATIBLE,
            TrainingDatasetCompatibilityEvaluator.evaluate(manifest(), requirements(5_000)).level
        )
    }

    @Test
    fun effectiveQuantityAndSelectionShareOnePlan() {
        val available = manifest().copy(rowCount = 11_000)
        val requested = requirements(50_000)
        val planner = com.robotkinematicslab.mobile.ml.data.SingleRunDatasetPlanner
        val plan = planner.plan(available, requested, 100_000)
        assertEquals(DatasetCompatibilityLevel.PARTIAL, plan.compatibility.level)
        assertEquals(11_000, plan.effectiveRows)
        assertTrue(plan.canTrain)
        assertEquals(8_000, planner.plan(available, requested, 8_000).effectiveRows)
        assertTrue(!planner.plan(available, requested, 29).canTrain)
        val other = available.copy(datasetName = "other", csvPath = "/tmp/other.csv")
        assertEquals(other, planner.preserveSelection(other, listOf(available, other), requested))
        assertEquals(available, planner.preserveSelection(other, listOf(available, other.copy(ikConfig = IKConfig(tolerance = 1e-12))), requested))
    }

    private fun requirements(requestedRows: Int) =
        TrainingDatasetRequirements(
            requestedRows = requestedRows,
            minimumRobotCount = 2,
            exactTolerance = 1e-4,
            exactMaxIterations = 1_000,
            exactDamping = 1e-3,
            exactMaxStep = 0.2,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5
        )

    private fun manifest() =
        DatasetManifest(
            datasetName = "research",
            csvPath = "/tmp/research.csv",
            rowCount = 10_000,
            generationCount = 1,
            robotIds = listOf("r1", "r2"),
            samplesPerRobotLastRun = 5_000,
            randomSeed = 42,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5,
            filterMode = DatasetFilterMode.ALL,
            lastUpdatedEpochMillis = 1,
            ikConfig = IKConfig(maxIterations = 1_000, tolerance = 1e-4, damping = 1e-3, maxStep = 0.2)
        )

    private fun batch(index: Int, rowStart: Long, tolerance: Double = 1e-4) =
        DatasetGenerationBatch(
            generationIndex = index,
            batchId = "batch-${index + 1}",
            rowStart = rowStart,
            rowCount = 5_000,
            robotIds = listOf("r1", "r2"),
            samplesPerRobot = 2_500,
            randomSeed = 42 + index,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5,
            filterMode = DatasetFilterMode.ALL,
            createdAtEpochMillis = index.toLong() + 1,
            ikConfig = IKConfig(maxIterations = 1_000, tolerance = tolerance, damping = 1e-3, maxStep = 0.2),
            metricPolicy = com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy()
        )
}
