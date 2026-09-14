package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in pack builder. It deliberately invokes the exact JVM-compatible engine and serializer
 * used by the Android UI, then reloads every model before allowing it to be bundled in the APK.
 */
class BundledClassifierPackTrainingTest {

    @Test
    fun trainReloadAndPersistBundledClassifierComparison() {
        val datasetPath = System.getenv("RKL_BUNDLED_CLASSIFIER_DATASET").orEmpty()
        val outputPath = System.getenv("RKL_BUNDLED_CLASSIFIER_OUTPUT").orEmpty()
        assumeTrue("Set both bundled classifier paths to run the pack builder.", datasetPath.isNotBlank() && outputPath.isNotBlank())
        val dataset = File(datasetPath)
        assertTrue("Bundled training dataset is missing.", dataset.isFile)
        val root = File(outputPath).apply(File::mkdirs)
        val maximumRows = envInt("RKL_BUNDLED_CLASSIFIER_ROWS", 10_000)
        val epochs = envInt("RKL_BUNDLED_CLASSIFIER_EPOCHS", 30)
        val batchSize = envInt("RKL_BUNDLED_CLASSIFIER_BATCH_SIZE", 256)
        val hiddenUnits = envInt("RKL_BUNDLED_CLASSIFIER_HIDDEN_UNITS", 32)
        val randomSeed = envInt("RKL_BUNDLED_CLASSIFIER_SEED", 2604)
        val runLabel = System.getenv("RKL_BUNDLED_CLASSIFIER_RUN_NAME")
            ?.takeIf(String::isNotBlank)
            ?: "Bundled ${compactRowLabel(maximumRows)} · all feature profiles"
        val repository = TrainingStorageRepository(File(root, "training"), File(root, "models"))
        val result = LocalTrainingEngine().train(
            LocalTrainingConfig(
                runName = runLabel,
                datasetPath = dataset.absolutePath,
                compareFeatureProfiles = true,
                modelKind = TrainingModelKind.COMPACT_MLP,
                resourceMode = TrainingResourceMode.BALANCED,
                splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
                maximumRows = maximumRows,
                epochs = epochs,
                batchSize = batchSize,
                learningRate = 0.003,
                l2Regularization = 1e-4,
                hiddenUnits = hiddenUnits,
                randomSeed = randomSeed,
                earlyStoppingPatience = 6,
                workerCount = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(1, 8)
            )
        )
        val saved = repository.save(result)
        assertEquals(TrainingFeatureProfile.entries.size, saved.modelPaths.size)
        assertEquals(TrainingFeatureProfile.entries.size, saved.comparison.variants.size)
        saved.modelPaths.forEach { path ->
            val file = File(path)
            assertTrue(file.isFile && file.length() > 0L)
            val loaded = repository.loadModel(file)
            assertEquals(loaded.featureNames.size, loaded.model.inputFeatureCount)
        }
        val summary = repository.listRuns().single { it.runId == saved.runId }
        assertEquals(TrainingFeatureProfile.entries.size, summary.variants.size)
        assertTrue(repository.loadIterationHistory(summary).isNotEmpty())
        println("[bundled-pack] ${saved.runId} saved to ${root.absolutePath}")
    }

    private fun envInt(name: String, default: Int): Int =
        System.getenv(name)?.toIntOrNull()?.takeIf { it > 0 } ?: default

    private fun compactRowLabel(rows: Int): String =
        if (rows % 1_000 == 0) "${rows / 1_000}k" else rows.toString()
}
