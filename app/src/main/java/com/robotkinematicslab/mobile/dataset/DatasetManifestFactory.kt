package com.robotkinematicslab.mobile.dataset

fun buildDatasetManifest(
    existingManifest: DatasetManifest?,
    config: DatasetGenerationConfig,
    csvPath: String,
    totalRows: Long,
    addedRows: Long,
    generationIndex: Int,
    updatedAtEpochMillis: Long
): DatasetManifest {
    require(addedRows > 0L && addedRows <= totalRows)
    require(generationIndex >= 0)
    DatasetScientificContract.requireCompatible(existingManifest, config)

    val currentBatch =
        DatasetGenerationBatch(
            generationIndex = generationIndex,
            batchId = "batch-${generationIndex + 1}",
            rowStart = totalRows - addedRows,
            rowCount = addedRows,
            robotIds = config.robots.map(SavedRobot::id).distinct(),
            samplesPerRobot = config.samplesPerRobot,
            randomSeed = config.randomSeed,
            targetMode = config.targetMode,
            reachableFraction = config.reachableFraction,
            filterMode = config.filterMode,
            createdAtEpochMillis = updatedAtEpochMillis,
            ikConfig = config.ikConfig,
            metricPolicy = config.metricPolicy
        )
    val batches =
        existingManifest?.provenanceBatchesForAppend().orEmpty()
            .filterNot { it.generationIndex == generationIndex } + currentBatch

    return DatasetManifest(
        datasetName = config.datasetName,
        csvPath = csvPath,
        rowCount = totalRows,
        generationCount = generationIndex + 1,
        robotIds = (existingManifest?.robotIds.orEmpty() + currentBatch.robotIds).distinct(),
        samplesPerRobotLastRun = config.samplesPerRobot,
        randomSeed = config.randomSeed,
        targetMode = config.targetMode,
        reachableFraction = config.reachableFraction,
        filterMode = config.filterMode,
        lastUpdatedEpochMillis = updatedAtEpochMillis,
        ikConfig = config.ikConfig,
        metricPolicy = config.metricPolicy,
        batches = batches.sortedBy(DatasetGenerationBatch::generationIndex),
        scientificFingerprint = DatasetScientificContract.fingerprint(config)
    )
}
