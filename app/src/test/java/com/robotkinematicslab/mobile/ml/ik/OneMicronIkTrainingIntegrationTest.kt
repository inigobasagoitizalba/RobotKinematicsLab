package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OneMicronIkTrainingIntegrationTest {

    @Test
    fun readerSeparatesMalformedRobotRowsFromFalsifiedMicronEvidence() {
        val root = Files.createTempDirectory("one-micron-reader-integrity").toFile()
        val csv = File(root, "dataset.csv")
        val generation =
            ScientificDatasetGenerator().generate(
                DatasetGenerationConfig(
                    datasetName = "micron-reader-integrity",
                    robots = DatasetRobotPresets().buildDefaults().take(1),
                    samplesPerRobot = 32,
                    randomSeed = 2604,
                    targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE,
                    reachableFraction = 1.0,
                    filterMode = DatasetFilterMode.ACCEPTED_ONLY,
                    append = false,
                    maxAttemptsMultiplier = 100,
                    ikConfig = oneMicronSolverConfig()
                ),
                csv,
                0,
                0
            )
        assertTrue(generation.completed)

        val lines = csv.readLines().toMutableList()
        val header = lines.first().split(',')
        fun replace(row: Int, column: String, value: String) {
            val fields = lines[row].split(',').toMutableList()
            fields[header.indexOf(column)] = value
            lines[row] = fields.joinToString(",")
        }
        val thetaCount = lines[1].split(',')[header.indexOf("dhThetaRad")].split(';').size
        replace(1, "dhThetaRad", List(thetaCount) { if (it == 0) "0.01" else "0" }.joinToString(";"))
        replace(2, "finalError", "0.000001")
        csv.writeText(lines.joinToString("\n", postfix = "\n"))

        val loaded = OneMicronIkDatasetReader().load(
            csv,
            OneMicronIkFeatureProfile.KINEMATICS_108,
            maximumRows = 32
        )

        assertEquals(30, loaded.samples.size)
        assertEquals(1, loaded.skippedRows)
        assertEquals(1, loaded.rejectedByMicronContract)
    }

    @Test
    fun cancellationDuringDatasetReadIsReportedAsCancellationAndNeverSaved() {
        val root = Files.createTempDirectory("one-micron-cancel").toFile()
        val csv = File(root, "dataset.csv").apply {
            writeText(com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter.HEADER.joinToString(",") + "\n")
        }
        val models = File(root, "models")
        val training = File(root, "training")

        assertThrows(OneMicronIkTrainingCancelledException::class.java) {
            OneMicronIkTrainingEngine().run(
                OneMicronIkTrainingConfig(
                    runName = "cancelled",
                    datasetPath = csv.absolutePath,
                    profile = OneMicronIkFeatureProfile.KINEMATICS_108,
                    maximumRows = 30,
                    epochs = 1,
                    batchSize = 8,
                    hiddenUnits = 8,
                    verificationSampleLimit = 1
                ),
                OneMicronIkStorageRepository(training, models),
                cancellationRequested = { true }
            )
        }

        assertTrue(models.listFiles().isNullOrEmpty())
        assertTrue(training.listFiles().isNullOrEmpty())
    }

    @Test
    fun interruptedCallingThreadCancelsBeforeDatasetCertification() {
        val root = Files.createTempDirectory("one-micron-thread-interrupt").toFile()
        val csv = File(root, "dataset.csv").apply {
            writeText(com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter.HEADER.joinToString(",") + "\n")
        }

        Thread.currentThread().interrupt()
        try {
            assertThrows(OneMicronIkTrainingCancelledException::class.java) {
                OneMicronIkTrainingEngine().run(
                    OneMicronIkTrainingConfig(
                        runName = "thread-interrupted",
                        datasetPath = csv.absolutePath,
                        profile = OneMicronIkFeatureProfile.KINEMATICS_108,
                        maximumRows = 30,
                        epochs = 1,
                        batchSize = 8,
                        hiddenUnits = 8,
                        verificationSampleLimit = 1
                    )
                )
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun generatedMicronRowsTrainVerifySaveAndReloadAsSeparateModelType() {
        val root = Files.createTempDirectory("one-micron-integration").toFile()
        val csv = File(root, "dataset.csv")
        val robots = DatasetRobotPresets().buildDefaults().take(3)
        val generation =
            ScientificDatasetGenerator().generate(
                DatasetGenerationConfig(
                    datasetName = "micron-test",
                    robots = robots,
                    samplesPerRobot = 12,
                    randomSeed = 2604,
                    targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE,
                    reachableFraction = 1.0,
                    filterMode = DatasetFilterMode.ACCEPTED_ONLY,
                    append = false,
                    maxAttemptsMultiplier = 100,
                    ikConfig = oneMicronSolverConfig()
                ),
                csv,
                0,
                0
            )
        assertTrue(generation.completed)

        val storage = OneMicronIkStorageRepository(File(root, "training"), File(root, "models"))
        val result =
            OneMicronIkTrainingEngine().run(
                OneMicronIkTrainingConfig(
                    runName = "integration",
                    datasetPath = csv.absolutePath,
                    profile = OneMicronIkFeatureProfile.KINEMATICS_108,
                    maximumRows = 36,
                    epochs = 2,
                    batchSize = 8,
                    hiddenUnits = 8,
                    earlyStoppingPatience = 2,
                    splitStrategy = TrainingSplitStrategy.ROBOT_HELD_OUT,
                    verificationSampleLimit = 4
                ),
                storage
            )
        assertTrue(result.modelPath.isNotBlank())
        assertTrue(File(result.modelPath).isFile)
        assertTrue(File(result.reportPath).isFile)
        assertTrue(result.verification.samples > 0)
        val metrics = result.verification
        assertEquals(metrics.samples, metrics.observations.size)
        fun rate(path: VerifiedIkPath) = metrics.observations.count { it.path == path }.toDouble() / metrics.samples
        assertEquals(rate(VerifiedIkPath.NEURAL_DIRECT), metrics.directNeuralSuccessRate, 1e-12)
        assertEquals(rate(VerifiedIkPath.NEURAL_REFINED), metrics.refinedOnlySuccessRate, 1e-12)
        assertEquals(rate(VerifiedIkPath.DETERMINISTIC_FALLBACK), metrics.fallbackOnlySuccessRate, 1e-12)
        assertEquals(rate(VerifiedIkPath.FAILED), metrics.failedPipelineRate, 1e-12)
        assertEquals(metrics.observations.count { it.pureSolverCertified }.toDouble() / metrics.samples,
            metrics.pureSolverSuccessRate, 1e-12)
        assertEquals(metrics.observations.sumOf { it.refinementIterations + it.fallbackIterations }.toDouble() / metrics.samples,
            metrics.meanPipelineIterations, 1e-12)
        val evidence = File(File(result.reportPath).parentFile, "verification-cases.csv")
        assertEquals(metrics.samples + 1, evidence.readLines().size)

        val loaded = storage.loadModel(File(result.modelPath))
        assertEquals(result.runId, loaded.runId)
        assertEquals(108, loaded.model.inputFeatureCount)
        assertArrayEquals(result.model.inputWeights, loaded.model.inputWeights, 0f)
    }
}
