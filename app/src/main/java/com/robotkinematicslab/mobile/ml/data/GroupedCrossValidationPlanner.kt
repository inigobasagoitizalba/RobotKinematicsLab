package com.robotkinematicslab.mobile.ml.data

enum class ScientificGroupingLevel {
    ROBOT,
    TOPOLOGY
}

data class GroupedValidationFold(
    val foldIndex: Int,
    val trainingIndices: IntArray,
    val validationIndices: IntArray,
    val validationGroups: Set<String>
)

/**
 * Deterministic grouped K-fold plan. Related rows never cross the training/validation boundary
 * of a fold, preventing robot or topology identity leakage in dissertation comparisons.
 */
class GroupedCrossValidationPlanner {
    fun plan(
        samples: List<EncodedTrainingSample>,
        grouping: ScientificGroupingLevel,
        requestedFolds: Int,
        seed: Int
    ): List<GroupedValidationFold> {
        require(requestedFolds >= 2) { "Cross-validation requires at least two folds." }
        val grouped = samples.indices.groupBy { index -> groupKey(samples[index], grouping) }
        require(grouped.keys.none(String::isBlank)) { "$grouping grouping contains a blank identity." }
        require(grouped.size >= 2) { "$grouping cross-validation requires at least two groups." }
        val foldCount = requestedFolds.coerceAtMost(grouped.size)
        val buckets = List(foldCount) { mutableListOf<Map.Entry<String, List<Int>>>() }

        grouped.entries
            .sortedWith(compareBy<Map.Entry<String, List<Int>>> { stableRank(it.key, seed) }.thenBy { it.key })
            .forEachIndexed { index, entry -> buckets[index % foldCount] += entry }

        return buckets.mapIndexed { foldIndex, validationEntries ->
            val validationGroups = validationEntries.mapTo(linkedSetOf()) { it.key }
            val validation = validationEntries.flatMap { it.value }.sorted().toIntArray()
            val training = samples.indices.filter { groupKey(samples[it], grouping) !in validationGroups }.toIntArray()
            require(training.isNotEmpty() && validation.isNotEmpty())
            GroupedValidationFold(foldIndex, training, validation, validationGroups)
        }
    }

    private fun groupKey(sample: EncodedTrainingSample, grouping: ScientificGroupingLevel): String =
        when (grouping) {
            ScientificGroupingLevel.ROBOT -> sample.robotId
            ScientificGroupingLevel.TOPOLOGY -> sample.topologyKey
        }

    private fun stableRank(value: String, seed: Int): Long {
        var hash = -3750763034362895579L xor seed.toLong()
        value.forEach { character ->
            hash = (hash xor character.code.toLong()) * 1099511628211L
        }
        return hash
    }
}
