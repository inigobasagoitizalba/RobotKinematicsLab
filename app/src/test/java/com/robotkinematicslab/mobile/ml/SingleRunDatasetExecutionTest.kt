package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.*
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.*
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.*
import com.robotkinematicslab.mobile.ui.training.buildConfig
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SingleRunDatasetExecutionTest {
    @Test fun previewConfigurationAndEngineUseExactlyTheSameDatasetAndQuantity() {
        val manifest = fixture()
        val file = File(manifest.csvPath)
        val historical = file.readBytes()
        val requirements = TrainingDatasetRequirements(50_000, 3)
        val digest = SingleRunDatasetPlanner.inspect(manifest, requirements)
        val config = config(manifest, requirements, digest)
        assertEquals(manifest.csvPath, config.datasetPath)
        assertEquals(120, config.maximumRows)
        assertEquals(SingleRunDatasetPlanner.plan(manifest, requirements, 1_000).effectiveRows, config.maximumRows)
        val result = LocalTrainingEngine().train(config)
        val baseline = requireNotNull(result.comparison.baseline)
        assertEquals(config.maximumRows, baseline.trainRowCount + baseline.validationRowCount + baseline.testRowCount)
        assertArrayEquals(historical, file.readBytes())
    }

    @Test fun changingCsvAfterVerifiedPreviewRejectsExecutionAndPreservesChangedEvidence() {
        val manifest = fixture()
        val requirements = TrainingDatasetRequirements(50_000, 3)
        val config = config(manifest, requirements, SingleRunDatasetPlanner.inspect(manifest, requirements))
        val file = File(manifest.csvPath)
        file.appendText("\n")
        val historical = file.readBytes()
        assertThrows(IllegalArgumentException::class.java) { LocalTrainingEngine().train(config) }
        assertArrayEquals(historical, file.readBytes())
    }

    @Test fun incompatibleCsvMetadataAndCancellationCannotRewriteHistoricalBytes() {
        val manifest = fixture()
        val file = File(manifest.csvPath)
        val historical = file.readBytes()
        val req = TrainingDatasetRequirements(50_000, 3)
        val mismatches = listOf(manifest.copy(rowCount = 121),
            manifest.copy(ikConfig = manifest.ikConfig.copy(tolerance = 1e-8)),
            manifest.copy(robotIds = listOf("wrong1", "wrong2", "wrong3", "wrong4")),
            manifest.copy(targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE),
            manifest.copy(generationCount = 2))
        mismatches.forEach { incompatible ->
            assertThrows(incompatible.toString(), IllegalArgumentException::class.java) {
                SingleRunDatasetPlanner.inspect(incompatible, req)
            }
            assertArrayEquals(historical, file.readBytes())
        }
        assertThrows(LocalTrainingCancelledException::class.java) {
            SingleRunDatasetPlanner.inspect(manifest, req) { true }
        }
        assertArrayEquals(historical, file.readBytes())
    }

    private fun config(manifest: DatasetManifest, requirements: TrainingDatasetRequirements, digest: String): LocalTrainingConfig =
        requireNotNull(buildConfig(manifest, "single-run-verified",
            listOf(FeatureSelectionSpec.complete(TrainingFeatureProfile.BASELINE_KINEMATICS)),
            TrainingModelKind.LINEAR_SOFTMAX, TrainingResourceMode.QUICK, TrainingSplitStrategy.SAMPLE_GROUPED,
            "50000", "1", "16", "0.003", "0.001", "8", "91", "1", 1, 1_000, requirements, digest).first)

    private fun fixture(): DatasetManifest {
        val robots = DatasetRobotPresets().buildDefaults().take(4)
        val file = Files.createTempFile("single-run-contract", ".csv").toFile()
        val solver = IKConfig(maxIterations = 60, tolerance = 1e-4, damping = 0.05, maxStep = 0.05)
        val result = ScientificDatasetGenerator().generate(DatasetGenerationConfig(
            datasetName = "single-run", robots = robots, samplesPerRobot = 30, randomSeed = 2604,
            targetMode = DatasetTargetMode.MIXED, reachableFraction = 0.65,
            filterMode = DatasetFilterMode.ALL, append = false, ikConfig = solver), file, 0, 0)
        assertTrue(result.completed)
        assertEquals(120, result.addedRows)
        return DatasetManifest(datasetName = "single-run", csvPath = file.absolutePath,
            rowCount = result.addedRows.toLong(), generationCount = 1,
            robotIds = robots.map { it.id }, samplesPerRobotLastRun = 30, randomSeed = 2604,
            targetMode = DatasetTargetMode.MIXED, reachableFraction = 0.65, filterMode = DatasetFilterMode.ALL,
            lastUpdatedEpochMillis = 1, ikConfig = solver)
    }
}
