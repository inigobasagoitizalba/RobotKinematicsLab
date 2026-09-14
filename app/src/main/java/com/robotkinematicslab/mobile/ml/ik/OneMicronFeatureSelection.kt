package com.robotkinematicslab.mobile.ml.ik

/** Exact named input contract for one neural-IK experiment arm. */
data class OneMicronFeatureSelectionSpec(
    val id: String,
    val displayName: String,
    val sourceProfile: OneMicronIkFeatureProfile,
    val includedFeatureNames: List<String>
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,80}")))
        require(displayName.isNotBlank())
        require(includedFeatureNames.isNotEmpty())
        require(includedFeatureNames.distinct().size == includedFeatureNames.size)
    }

    val featureCount: Int get() = includedFeatureNames.size

    companion object {
        fun complete(profile: OneMicronIkFeatureProfile): OneMicronFeatureSelectionSpec {
            val names = OneMicronIkFeatureEncoder.featureNames(profile)
            return OneMicronFeatureSelectionSpec(profile.name.lowercase(), "${profile.displayName} · ${names.size}", profile, names)
        }
    }
}

fun OneMicronIkDataset.project(selection: OneMicronFeatureSelectionSpec): OneMicronIkDataset {
    require(profile == selection.sourceProfile)
    val indices = selection.includedFeatureNames.map { name ->
        featureNames.indexOf(name).takeIf { it >= 0 }
            ?: error("Selected variable '$name' is absent from ${profile.displayName}.")
    }
    return copy(
        featureNames = selection.includedFeatureNames,
        samples = samples.map { sample ->
            sample.copy(features = FloatArray(indices.size) { index -> sample.features[indices[index]] })
        }
    )
}
