package com.robotkinematicslab.mobile.ml.closedloop

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetScientificContract
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.DatasetCompatibilityLevel
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetCompatibilityEvaluator
import com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedLoopRuntimeIntegrationTest {

    @Test
    fun growthRejectsLegacyAndChangedRobotGeometryBeforeWritingAnyRows() {
        val root = Files.createTempDirectory("closed-loop-contract").toFile()
        val storage = DatasetStorageRepository(File(root, "datasets"))
        val library = RobotLibraryRepository(File(root, "robots/library.rklb"))
        val robots = DatasetRobotPresets().buildDefaults().take(1)
        library.save(robots)
        val csv = storage.resolveCsvFile("contract")
        val config = DatasetGenerationConfig("contract", robots, 1, 73, DatasetTargetMode.MIXED, 0.65,
            DatasetFilterMode.ALL, append = false, ikConfig = IKConfig(maxIterations = 20))
        val generated = ScientificDatasetGenerator().generate(config, csv, 0, 0)
        assertTrue(generated.completed)
        val manifest = DatasetManifest("contract", csv.absolutePath, generated.totalRows, 1, robots.map { it.id },
            1, 73, DatasetTargetMode.MIXED, 0.65, DatasetFilterMode.ALL, 1,
            ikConfig = config.ikConfig)
        val before = csv.readBytes()
        val grower = DefaultClosedLoopDatasetGrower(storage, library)
        fun blocked(expected: DatasetManifest) {
            storage.saveManifest(expected)
            val result = grower.grow(ClosedLoopDatasetGrowthRequest("contract", 1, generated.totalRows, 100),
                AtomicBoolean(false), {})
            assertTrue(!result.completed)
            assertEquals(0, result.addedRows)
            assertTrue(before.contentEquals(csv.readBytes()))
            assertEquals(expected, storage.loadManifest("contract"))
        }
        blocked(manifest)
        val originalRobot = robots.single()
        val changedDh = originalRobot.robot.dhParameters.toMutableList()
        changedDh[0] = changedDh[0].copy(a = changedDh[0].a + 0.01)
        library.save(listOf(originalRobot.copy(robot = originalRobot.robot.copy(dhParameters = changedDh))))
        blocked(manifest.copy(scientificFingerprint = DatasetScientificContract.fingerprint(config)))
    }

    @Test
    fun realDataFactoryGrowthThenStoredModelInferenceVerificationCompletes() {
        val root = Files.createTempDirectory("closed-loop-runtime").toFile()
        val datasetStorage = DatasetStorageRepository(File(root, "datasets"))
        val robotLibrary = RobotLibraryRepository(File(root, "robots/library.rklb"))
        val robots = DatasetRobotPresets().buildDefaults().take(3)
        robotLibrary.save(robots)
        val csv = datasetStorage.resolveCsvFile("closed-loop-data")
        val ikConfig = IKConfig(maxIterations = 80, tolerance = 1e-4, damping = 0.05, maxStep = 0.05)
        val initial =
            ScientificDatasetGenerator().generate(
                config =
                    DatasetGenerationConfig(
                        datasetName = "closed-loop-data",
                        robots = robots,
                        samplesPerRobot = 30,
                        randomSeed = 73,
                        targetMode = DatasetTargetMode.MIXED,
                        reachableFraction = 0.65,
                        filterMode = DatasetFilterMode.ALL,
                        append = false,
                        ikConfig = ikConfig
                    ),
                csvFile = csv,
                existingRowCount = 0,
                generationIndex = 0
            )
        assertTrue(initial.completed)
        datasetStorage.saveManifest(
            DatasetManifest(
                datasetName = "closed-loop-data",
                csvPath = csv.absolutePath,
                rowCount = initial.totalRows,
                generationCount = 1,
                robotIds = robots.map { it.id },
                samplesPerRobotLastRun = 30,
                randomSeed = 73,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.65,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1,
                ikConfig = ikConfig,
                scientificFingerprint = DatasetScientificContract.fingerprint(DatasetGenerationConfig(
                    "closed-loop-data", robots, 30, 73, DatasetTargetMode.MIXED, 0.65, DatasetFilterMode.ALL,
                    append = false, ikConfig = ikConfig))
            )
        )

        val growthProgress = mutableListOf<ClosedLoopDatasetGrowthProgress>()
        val growth =
            DefaultClosedLoopDatasetGrower(datasetStorage, robotLibrary).grow(
                request =
                    ClosedLoopDatasetGrowthRequest(
                        datasetName = "closed-loop-data",
                        samplesPerRobot = 10,
                        expectedCurrentRows = initial.totalRows,
                        maximumTotalRows = 200
                    ),
                cancellationRequested = AtomicBoolean(false),
                onProgress = growthProgress::add
            )

        assertTrue(growth.completed)
        assertEquals(30, growth.addedRows)
        assertEquals(120L, growth.totalRows)
        assertTrue(growthProgress.isNotEmpty())
        val grownManifest = requireNotNull(datasetStorage.loadManifest("closed-loop-data"))
        assertEquals(2, grownManifest.generationCount)
        assertEquals(2, grownManifest.batches.size)
        assertTrue(grownManifest.hasCompleteBatchProvenance)
        assertEquals(
            DatasetCompatibilityLevel.COMPATIBLE,
            TrainingDatasetCompatibilityEvaluator.evaluate(
                grownManifest,
                TrainingDatasetRequirements(requestedRows = 120, minimumRobotCount = 1)
            ).level
        )

        val trainingStorage =
            TrainingStorageRepository(
                trainingDirectory = File(root, "training"),
                modelsDirectory = File(root, "models")
            )
        val evidence =
            DefaultClosedLoopTrainingCycleRunner(LocalTrainingEngine(), trainingStorage).train(
                request =
                    ClosedLoopTrainingRequest(
                        config =
                            LocalTrainingConfig(
                                runName = "runtime-integration",
                                datasetPath = csv.absolutePath,
                                compareFeatureProfiles = false,
                                singleFeatureProfile = TrainingFeatureProfile.CONTEXT_ENHANCED,
                                modelKind = TrainingModelKind.LINEAR_SOFTMAX,
                                maximumRows = 120,
                                epochs = 8,
                                batchSize = 32,
                                learningRate = 0.01,
                                earlyStoppingPatience = 3
                            ),
                        evaluationProfile = TrainingFeatureProfile.CONTEXT_ENHANCED
                    ),
                cancellationRequested = AtomicBoolean(false),
                onProgress = {}
            )

        assertTrue(evidence.inferenceValid)
        assertTrue(File(evidence.modelPath).exists())
        assertTrue(evidence.macroF1.isFinite())
        assertEquals(120, evidence.datasetRowsUsed)
    }

    @Test
    fun cancellationDuringGrowth_keepsCsvAndManifestAtTheirCommittedState() {
        val root = Files.createTempDirectory("closed-loop-cancel-growth").toFile()
        val datasetStorage = DatasetStorageRepository(File(root, "datasets"))
        val robotLibrary = RobotLibraryRepository(File(root, "robots/library.rklb"))
        val robots = DatasetRobotPresets().buildDefaults().take(1)
        robotLibrary.save(robots)
        val csv = datasetStorage.resolveCsvFile("cancel-safe-data")
        val initial = ScientificDatasetGenerator().generate(
            config = DatasetGenerationConfig(
                datasetName = "cancel-safe-data",
                robots = robots,
                samplesPerRobot = 3,
                randomSeed = 73,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.65,
                filterMode = DatasetFilterMode.ALL,
                append = false,
                ikConfig = IKConfig(maxIterations = 20)
            ),
            csvFile = csv,
            existingRowCount = 0,
            generationIndex = 0
        )
        val manifest = DatasetManifest(
            datasetName = "cancel-safe-data",
            csvPath = csv.absolutePath,
            rowCount = initial.totalRows,
            generationCount = 1,
            robotIds = robots.map { it.id },
            samplesPerRobotLastRun = 3,
            randomSeed = 73,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.65,
            filterMode = DatasetFilterMode.ALL,
            lastUpdatedEpochMillis = 1,
            ikConfig = IKConfig(maxIterations = 20),
            scientificFingerprint = DatasetScientificContract.fingerprint(DatasetGenerationConfig(
                "cancel-safe-data", robots, 3, 73, DatasetTargetMode.MIXED, 0.65, DatasetFilterMode.ALL,
                append = false, ikConfig = IKConfig(maxIterations = 20)))
        )
        datasetStorage.saveManifest(manifest)
        val csvBefore = csv.readBytes()
        val cancellation = AtomicBoolean(false)

        val result = DefaultClosedLoopDatasetGrower(datasetStorage, robotLibrary).grow(
            request = ClosedLoopDatasetGrowthRequest(
                datasetName = manifest.datasetName,
                samplesPerRobot = 20,
                expectedCurrentRows = initial.totalRows,
                maximumTotalRows = 100
            ),
            cancellationRequested = cancellation,
            onProgress = { if (it.addedRows >= 1) cancellation.set(true) }
        )

        assertTrue(!result.completed)
        assertEquals(0, result.addedRows)
        assertTrue(csvBefore.contentEquals(csv.readBytes()))
        assertEquals(manifest, datasetStorage.loadManifest(manifest.datasetName))
    }
}
