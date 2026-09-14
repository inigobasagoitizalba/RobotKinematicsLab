package com.robotkinematicslab.mobile.ml.data

/**
 * Exact, ordered feature contract for one experimental arm.
 *
 * Names are stored instead of only a count so that two 128-input experiments cannot be confused
 * when they contain different variables.  The source profile states which complete encoder must
 * be evaluated before this immutable projection is applied.
 */
data class FeatureSelectionSpec(
    val id: String,
    val displayName: String,
    val sourceProfile: TrainingFeatureProfile,
    val includedFeatureNames: List<String>
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Feature-set id must be filesystem safe." }
        require(displayName.isNotBlank()) { "Feature-set name must not be blank." }
        require(includedFeatureNames.isNotEmpty()) { "Select at least one input variable." }
        require(includedFeatureNames.distinct().size == includedFeatureNames.size) {
            "A feature set cannot contain duplicate variables."
        }
    }

    val featureCount: Int get() = includedFeatureNames.size

    companion object {
        fun complete(profile: TrainingFeatureProfile): FeatureSelectionSpec {
            val names = ScientificDatasetTrainingReader.featureNames(profile)
            return FeatureSelectionSpec(
                id = profile.name.lowercase(),
                displayName = "${profile.displayName} · ${names.size}",
                sourceProfile = profile,
                includedFeatureNames = names
            )
        }
    }
}

/** Parses human-friendly one-based positions such as `1-108, 130, 201-205`. */
object FeatureIndexSelectionParser {
    fun parse(text: String, maximumCount: Int): Result<List<Int>> = runCatching {
        require(maximumCount > 0)
        require(text.isNotBlank()) { "Select at least one feature position." }
        val indices = linkedSetOf<Int>()
        val tokens = text.split(',').map(String::trim)
        require(tokens.none(String::isEmpty)) {
            "Feature positions must not contain an empty comma-separated item."
        }
        tokens.forEach { token ->
            require(token.matches(Regex("[0-9]+(?:\\s*-\\s*[0-9]+)?"))) {
                "Feature token '$token' is invalid. Use one-based positions or ranges such as 1-108,130."
            }
            val bounds = token.split('-').map(String::trim)
            when (bounds.size) {
                1 -> indices += oneBasedToIndex(bounds[0], maximumCount)
                2 -> {
                    val first = oneBasedToIndex(bounds[0], maximumCount)
                    val last = oneBasedToIndex(bounds[1], maximumCount)
                    require(first <= last) { "Feature range '$token' is reversed." }
                    (first..last).forEach(indices::add)
                }
                else -> error("Feature token '$token' is invalid.")
            }
        }
        require(indices.isNotEmpty()) { "Select at least one feature position." }
        indices.toList()
    }

    private fun oneBasedToIndex(value: String, maximumCount: Int): Int {
        val oneBased = value.toIntOrNull() ?: error("Feature position '$value' is not a whole number.")
        require(oneBased in 1..maximumCount) {
            "Feature position $oneBased is outside 1-$maximumCount."
        }
        return oneBased - 1
    }
}

data class FeatureSelectionPreview(
    val sourceProfile: TrainingFeatureProfile,
    val oneBasedPositions: List<Int>,
    val featureNames: List<String>
) {
    init {
        require(oneBasedPositions.isNotEmpty())
        require(oneBasedPositions.size == featureNames.size)
        require(oneBasedPositions.all { it > 0 })
        require(oneBasedPositions.distinct().size == oneBasedPositions.size)
    }
}

fun previewFeatureSelection(
    sourceProfile: TrainingFeatureProfile,
    expression: String
): Result<FeatureSelectionPreview> {
    val names = ScientificDatasetTrainingReader.featureNames(sourceProfile)
    return FeatureIndexSelectionParser.parse(expression, names.size).map { indices ->
        FeatureSelectionPreview(
            sourceProfile = sourceProfile,
            oneBasedPositions = indices.map { it + 1 },
            featureNames = indices.map(names::get)
        )
    }
}

fun TrainingDataset.project(selection: FeatureSelectionSpec): TrainingDataset {
    require(profile == selection.sourceProfile) { "Feature selection source profile does not match the encoded dataset." }
    val sourceIndices = selection.includedFeatureNames.map { name ->
        featureNames.indexOf(name).takeIf { it >= 0 }
            ?: error("Selected variable '$name' is absent from ${profile.displayName}.")
    }
    return copy(
        featureNames = selection.includedFeatureNames,
        samples = samples.map { sample ->
            sample.copy(features = FloatArray(sourceIndices.size) { index -> sample.features[sourceIndices[index]] })
        }
    )
}
