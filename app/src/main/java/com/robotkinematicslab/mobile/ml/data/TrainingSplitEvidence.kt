package com.robotkinematicslab.mobile.ml.data

/** Compact partition statistics complement the inference contract that binds exact row membership. */
data class TrainingSplitEvidence(
    val protocol: String,
    val strategy: TrainingSplitStrategy,
    val rowCounts: List<Int>,
    val groupCounts: List<Int>,
    val classCounts: List<List<Int>>,
    val usedSortedFallback: Boolean
) {
    init {
        require(protocol == PROTOCOL)
        require(rowCounts.size == 3 && rowCounts.all { it > 0 })
        require(groupCounts.size == 3 && groupCounts.indices.all { groupCounts[it] in 1..rowCounts[it] })
        require(classCounts.size == 3 && classCounts.indices.all { index ->
            classCounts[index].size == TrainingLabel.entries.size && classCounts[index].all { it >= 0 } &&
                classCounts[index].sumOf(Int::toLong) == rowCounts[index].toLong()
        }) { "Partition class counts must add up to the recorded rows." }
    }
    companion object {
        const val PROTOCOL = "rkl-group-bucket-v1"
        fun from(samples: List<EncodedTrainingSample>, split: TrainingDatasetSplit): TrainingSplitEvidence {
            val partitions=listOf(split.trainIndices,split.validationIndices,split.testIndices)
            val groups=partitions.map { indices -> indices.map { i ->
                if(split.strategy==TrainingSplitStrategy.SAMPLE_GROUPED) samples[i].splitFingerprint else samples[i].robotFingerprint
            }.toSet() }
            require(groups[0].intersect(groups[1]).isEmpty() && groups[0].intersect(groups[2]).isEmpty() && groups[1].intersect(groups[2]).isEmpty()) { "Split groups overlap." }
            require(partitions.flatMap { it.toList() }.sorted() == samples.indices.toList()) { "Every input row must belong to exactly one partition." }
            return TrainingSplitEvidence(PROTOCOL,split.strategy,partitions.map { it.size },groups.map { it.size },
                partitions.map { indices -> TrainingLabel.entries.map { label -> indices.count { samples[it].labelIndex == label.ordinal } } },split.usedSortedFallback)
        }
    }
}
