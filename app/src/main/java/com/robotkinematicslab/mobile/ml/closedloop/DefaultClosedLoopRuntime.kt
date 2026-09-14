package com.robotkinematicslab.mobile.ml.closedloop

import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetGenerationBatch
import com.robotkinematicslab.mobile.dataset.DatasetScientificContract
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.ParallelScientificDatasetGenerator
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.dataset.provenanceBatchesForAppend
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ml.training.LocalTrainingEngine
import com.robotkinematicslab.mobile.ml.training.LocalTrainingProgress
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class DefaultClosedLoopTrainingCycleRunner(
    private val trainingEngine: LocalTrainingEngine,
    private val trainingStorage: TrainingStorageRepository,
    private val datasetStorage: DatasetStorageRepository? = null
) : ClosedLoopTrainingCycleRunner {

    override fun train(
        request: ClosedLoopTrainingRequest,
        cancellationRequested: AtomicBoolean,
        onProgress: (LocalTrainingProgress) -> Unit
    ): ClosedLoopCycleEvidence {
        require(request.config.resolvedFeatureSelections.count { it.sourceProfile == request.evaluationProfile } == 1) {
            "The judged profile must identify exactly one trained feature set."
        }
        val executedConfig = datasetStorage?.let { storage ->
            val manifest = storage.listManifests().single { it.csvPath == request.config.datasetPath }
            require(manifest.rowCount == request.config.expectedDatasetRows) { "Dataset changed since the cycle was planned." }
            val requirements = com.robotkinematicslab.mobile.ml.data.TrainingDatasetRequirements(request.config.maximumRows,
                if(request.config.splitStrategy == com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy.ROBOT_HELD_OUT) 3 else 1)
            val digest = com.robotkinematicslab.mobile.ml.data.SingleRunDatasetPlanner.inspect(manifest, requirements, cancellationRequested::get)
            request.config.copy(managedDatasetManifest = manifest, datasetRequirements = requirements, expectedCorpusSha256 = digest)
        } ?: request.config
        val result =
            trainingEngine.train(
                config = executedConfig,
                cancellationRequested = cancellationRequested::get,
                onProgress = onProgress
            )
        val storedResult = trainingStorage.save(result)
        val profileResult =
            when (request.evaluationProfile) {
                TrainingFeatureProfile.BASELINE_KINEMATICS -> storedResult.comparison.baseline
                TrainingFeatureProfile.CONTEXT_ENHANCED -> storedResult.comparison.contextEnhanced
                TrainingFeatureProfile.CONTEXT_EXPANDED -> storedResult.comparison.contextExpanded
                TrainingFeatureProfile.CONTEXT_RESEARCH_V2 ->
                    storedResult.comparison.variants.firstOrNull {
                        it.profile == TrainingFeatureProfile.CONTEXT_RESEARCH_V2
                    }
            } ?: error(
                "The requested ${request.evaluationProfile.displayName} profile was not trained. " +
                    "Enable profile comparison or select the matching single profile."
            )
        val modelPath =
            storedResult.modelPaths.firstOrNull { path ->
                runCatching {
                    trainingStorage.loadModel(File(path)).featureSelectionId == profileResult.featureSelectionId
                }.getOrDefault(false)
            } ?: error("The selected model artifact was not written.")
        val verification = verifyStoredInference(profileResult, File(modelPath))

        return ClosedLoopCycleEvidence(
            runId = storedResult.runId,
            profile = profileResult.profile,
            datasetRowsUsed =
                profileResult.trainRowCount + profileResult.validationRowCount + profileResult.testRowCount,
            macroF1 = profileResult.validationMetrics.macroF1,
            balancedAccuracy = profileResult.validationMetrics.balancedAccuracy,
            accuracy = profileResult.validationMetrics.accuracy,
            logLoss = profileResult.validationMetrics.logLoss,
            independentTestMacroF1 = profileResult.testMetrics.macroF1,
            independentTestBalancedAccuracy = profileResult.testMetrics.balancedAccuracy,
            independentTestAccuracy = profileResult.testMetrics.accuracy,
            independentTestLogLoss = profileResult.testMetrics.logLoss,
            inferenceNanosPerSample = profileResult.testMetrics.inferenceNanosPerSample,
            inferenceValid = verification.first,
            inferenceMessage = verification.second,
            modelPath = modelPath,
            trainingDurationMillis = profileResult.trainingDurationMillis,
            parameterCount = profileResult.parameterCount,
            validationRows = profileResult.validationRowCount,
            reportingTestRows = profileResult.testRowCount,
            corpusSha256 = profileResult.inferenceContract?.corpusSha256,
            modelSha256 = profileResult.inferenceContract?.modelSha256,
            featureSelectionId = profileResult.featureSelectionId
        )
    }

    private fun verifyStoredInference(
        original: com.robotkinematicslab.mobile.ml.training.TrainedProfileResult,
        modelFile: File
    ): Pair<Boolean, String> {
        return runCatching {
            val stored = trainingStorage.loadModel(modelFile)
            require(stored.featureSelectionId == original.featureSelectionId) { "Stored feature-set identity changed." }
            require(com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract.modelDigest(stored.featureNames, stored.normalization, stored.model) ==
                com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract.modelDigest(original.featureNames, original.normalization, original.model)) {
                "Stored parameters or normalization differ from the evaluated model."
            }
            require(stored.profile == original.profile) { "Stored model profile does not match the selected profile." }
            require(stored.featureNames == original.featureNames) { "Stored model feature contract changed during persistence." }
            require(stored.normalization.means.size == stored.model.inputFeatureCount)
            require(stored.normalization.standardDeviations.size == stored.model.inputFeatureCount)
            require(stored.normalization.means.all(Float::isFinite))
            require(stored.normalization.standardDeviations.all { it.isFinite() && it > 0f })
            require(stored.model.inputWeights.all(Float::isFinite))
            require(stored.model.hiddenBiases.all(Float::isFinite))
            require(stored.model.outputWeights.all(Float::isFinite))
            require(stored.model.outputBiases.all(Float::isFinite))

            val normalizedMeanProbe = FloatArray(stored.model.inputFeatureCount)
            val originalProbabilities = original.model.probabilities(normalizedMeanProbe)
            val restoredProbabilities = stored.model.probabilities(normalizedMeanProbe)
            require(restoredProbabilities.all { it.isFinite() && it in 0f..1f })
            require(abs(restoredProbabilities.sum() - 1f) <= PROBABILITY_TOLERANCE)
            require(
                originalProbabilities.indices.all { index ->
                    abs(originalProbabilities[index] - restoredProbabilities[index]) <= ROUND_TRIP_TOLERANCE
                }
            ) { "Stored-model inference differs from the in-memory model." }
            require(original.testMetrics.accuracy.isFinite() && original.testMetrics.accuracy in 0.0..1.0)
            require(original.testMetrics.macroF1.isFinite() && original.testMetrics.macroF1 in 0.0..1.0)
            require(original.testMetrics.balancedAccuracy.isFinite() && original.testMetrics.balancedAccuracy in 0.0..1.0)
            require(original.testMetrics.logLoss.isFinite() && original.testMetrics.logLoss >= 0.0)
            require(original.testMetrics.testableSampleCount())
            require(original.validationMetrics.accuracy.isFinite() && original.validationMetrics.accuracy in 0.0..1.0)
            require(original.validationMetrics.macroF1.isFinite() && original.validationMetrics.macroF1 in 0.0..1.0)
            require(
                original.validationMetrics.balancedAccuracy.isFinite() &&
                    original.validationMetrics.balancedAccuracy in 0.0..1.0
            )
            require(original.validationMetrics.logLoss.isFinite() && original.validationMetrics.logLoss >= 0.0)
            require(original.validationMetrics.testableSampleCount())
            true to "Inference verified: finite probabilities, stable schema and exact stored-model round trip."
        }.getOrElse { error ->
            false to "Inference verification failed: ${error.message ?: error::class.java.simpleName}"
        }
    }

    private fun com.robotkinematicslab.mobile.ml.training.ClassificationMetrics.testableSampleCount(): Boolean =
        sampleCount > 0 && confusionMatrix.sumOf { row -> row.sum() } == sampleCount

    companion object {
        private const val PROBABILITY_TOLERANCE = 1e-4f
        private const val ROUND_TRIP_TOLERANCE = 1e-7f
    }
}

class DefaultClosedLoopDatasetGrower(
    private val datasetStorage: DatasetStorageRepository,
    private val robotLibrary: RobotLibraryRepository,
    private val generator: ParallelScientificDatasetGenerator = ParallelScientificDatasetGenerator(),
    private val workerCountProvider: () -> Int = { 1 }
) : ClosedLoopDatasetGrower {

    override fun grow(
        request: ClosedLoopDatasetGrowthRequest,
        cancellationRequested: AtomicBoolean,
        onProgress: (ClosedLoopDatasetGrowthProgress) -> Unit
    ): ClosedLoopDatasetGrowthResult {
        val manifest =
            datasetStorage.loadManifest(request.datasetName)
                ?: return ClosedLoopDatasetGrowthResult(
                    addedRows = 0,
                    totalRows = request.expectedCurrentRows,
                    completed = false,
                    message = "The managed dataset manifest no longer exists."
                )
        val csvFile = File(manifest.csvPath)
        val actualRows = datasetStorage.countCsvDataRows(csvFile)
        if (actualRows != manifest.rowCount) {
            return ClosedLoopDatasetGrowthResult(
                addedRows = 0,
                totalRows = actualRows,
                completed = false,
                message =
                    "Dataset CSV and manifest disagree " +
                        "(manifest ${manifest.rowCount}, CSV $actualRows). Repair or regenerate it before growth."
            )
        }
        if (actualRows != request.expectedCurrentRows) {
            return ClosedLoopDatasetGrowthResult(
                addedRows = 0,
                totalRows = actualRows,
                completed = false,
                message =
                    "Dataset row count changed outside this session " +
                        "(expected ${request.expectedCurrentRows}, found $actualRows)."
            )
        }
        val libraryById = robotLibrary.loadOrCreateDefaults().associateBy { it.id }
        val missingRobotIds = manifest.robotIds.filterNot(libraryById::containsKey)
        if (missingRobotIds.isNotEmpty()) {
            return ClosedLoopDatasetGrowthResult(
                addedRows = 0,
                totalRows = actualRows,
                completed = false,
                message = "Robot definitions are missing from the library: ${missingRobotIds.joinToString()}."
            )
        }
        val robots = manifest.robotIds.map(libraryById::getValue)
        if (robots.isEmpty()) {
            return ClosedLoopDatasetGrowthResult(
                addedRows = 0,
                totalRows = actualRows,
                completed = false,
                message = "The dataset manifest contains no robots."
            )
        }
        val remainingRows = (request.maximumTotalRows - actualRows).coerceAtLeast(0L)
        val safeSamplesPerRobot =
            minOf(
                request.samplesPerRobot.toLong(),
                remainingRows / robots.size.toLong(),
                request.maximumRowsEligibleForNextTraining.toLong() / robots.size.toLong()
            ).toInt()
        if (safeSamplesPerRobot <= 0) {
            return ClosedLoopDatasetGrowthResult(
                addedRows = 0,
                totalRows = actualRows,
                completed = false,
                message =
                    "The dataset ceiling or training-row cap leaves no complete per-robot batch " +
                        "whose new rows can all enter the next bounded sample."
            )
        }
        val generationConfig =
            DatasetGenerationConfig(
                datasetName = manifest.datasetName,
                robots = robots,
                samplesPerRobot = safeSamplesPerRobot,
                randomSeed = manifest.randomSeed,
                targetMode = manifest.targetMode,
                reachableFraction = manifest.reachableFraction,
                filterMode = manifest.filterMode,
                append = true,
                ikConfig = manifest.ikConfig,
                metricPolicy = manifest.metricPolicy
            )
        val contractFailure = runCatching { DatasetScientificContract.requireCompatible(manifest, generationConfig) }.exceptionOrNull()
        if (contractFailure != null) {
            return ClosedLoopDatasetGrowthResult(0, actualRows, false,
                contractFailure.message ?: "The dataset scientific contract is incompatible with growth.")
        }
        val result =
            generator.generate(
                config = generationConfig,
                csvFile = csvFile,
                existingRowCount = actualRows,
                generationIndex = manifest.generationCount,
                workerCount = workerCountProvider().coerceAtLeast(1),
                cancellationRequested = cancellationRequested,
                lockedPreflight = {
                    check(datasetStorage.loadManifest(request.datasetName) == manifest) {
                        "The dataset manifest changed before growth acquired its write lock. Retry the operation."
                    }
                    DatasetScientificContract.requireCompatible(manifest, generationConfig)
                },
                onProgress = { progress ->
                    onProgress(
                        ClosedLoopDatasetGrowthProgress(
                            requestedRows = progress.requestedRows,
                            addedRows = progress.addedRows,
                            attempts = progress.attempts,
                            currentRobotIndex = progress.currentRobotIndex,
                            totalRobots = progress.totalRobots,
                            currentRobotName = progress.currentRobotName,
                            message = progress.message
                        )
                    )
                }
            )
        if (result.completed && result.addedRows > 0) {
            datasetStorage.saveManifest(
                updatedManifest(
                    manifest = manifest,
                    config = generationConfig,
                    totalRows = result.totalRows,
                    addedRows = result.addedRows.toLong()
                )
            )
        }
        return ClosedLoopDatasetGrowthResult(
            addedRows = result.addedRows,
            totalRows = result.totalRows,
            completed = result.completed,
            message = result.message
        )
    }

    private fun updatedManifest(
        manifest: DatasetManifest,
        config: DatasetGenerationConfig,
        totalRows: Long,
        addedRows: Long
    ): DatasetManifest {
        val generationIndex = manifest.generationCount
        val timestamp = System.currentTimeMillis()
        val batch = DatasetGenerationBatch(
            generationIndex = generationIndex,
            batchId = "batch-${generationIndex + 1}",
            rowStart = totalRows - addedRows,
            rowCount = addedRows,
            robotIds = config.robots.map { it.id },
            samplesPerRobot = config.samplesPerRobot,
            randomSeed = config.randomSeed,
            targetMode = config.targetMode,
            reachableFraction = config.reachableFraction,
            filterMode = config.filterMode,
            createdAtEpochMillis = timestamp,
            ikConfig = config.ikConfig,
            metricPolicy = config.metricPolicy
        )
        return manifest.copy(
            rowCount = totalRows,
            generationCount = generationIndex + 1,
            samplesPerRobotLastRun = config.samplesPerRobot,
            lastUpdatedEpochMillis = timestamp,
            batches = (manifest.provenanceBatchesForAppend() + batch).sortedBy { it.generationIndex }
        )
    }
}
