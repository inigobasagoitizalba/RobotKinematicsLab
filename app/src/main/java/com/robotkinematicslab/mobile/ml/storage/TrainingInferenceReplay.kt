package com.robotkinematicslab.mobile.ml.storage

import com.robotkinematicslab.mobile.ml.data.*
import java.io.File

/** Replays the persisted selection and rejects evidence that is not the exact evaluated inference. */
object TrainingInferenceReplay {
    fun rebuild(run: TrainingRunSummary, stored: StoredLocalModel,
                reader: ScientificDatasetTrainingReader = ScientificDatasetTrainingReader(),
                cancellationRequested: () -> Boolean = { false },
                checkCancellation: () -> Unit = {
                    if (cancellationRequested() || Thread.currentThread().isInterrupted)
                        throw com.robotkinematicslab.mobile.ml.training.LocalTrainingCancelledException()
                }): Pair<TrainingDataset, TrainingDatasetSplit> {
        checkCancellation()
        require(stored.runId == run.runId) { "The selected model belongs to another training run." }
        val contract = requireNotNull(stored.inferenceContract) {
            "This legacy model has no original inference replay contract. Retrain it before creating a verified explanation."
        }
        require(contract.maximumRows == run.maximumRows && contract.randomSeed == run.randomSeed &&
            contract.splitStrategy == run.splitStrategy) { "The training summary disagrees with the stored inference contract." }
        require(
            contract.sampleAcrossEntireFile ==
                (run.sampleAcrossEntireDataset || run.splitStrategy == TrainingSplitStrategy.ROBOT_HELD_OUT) &&
                contract.expectedDataRowCount == run.expectedDatasetRows &&
                contract.requiredNewestRows == run.requiredNewestRows
        ) { "The stored row-selection contract disagrees with the training summary." }
        val file = File(run.datasetPath)
        contract.verifyCorpus(file, checkCancellation)
        val loaded = reader.load(file, stored.profile, contract.maximumRows, cancellationRequested,
            sampleAcrossEntireFile = contract.sampleAcrossEntireFile,
            samplingSeed = contract.randomSeed,
            expectedDataRowCount = contract.expectedDataRowCount,
            requiredNewestRows = contract.requiredNewestRows)
        checkCancellation()
        val source = requireNotNull(loaded.dataset) { loaded.errorMessage ?: "The original dataset could not be reconstructed." }
        val dataset = source.project(FeatureSelectionSpec("inference-replay", "Original inference", stored.profile, stored.featureNames))
        val split = TrainingDatasetPreparer().split(dataset.samples, contract.randomSeed, contract.splitStrategy)
        contract.verifyCorpus(file, checkCancellation)
        contract.verify(dataset, split, stored.normalization, stored.model, checkCancellation)
        return dataset to split
    }
}
