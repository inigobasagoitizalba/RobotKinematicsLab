package com.robotkinematicslab.mobile.ml.data

enum class TrainingFeatureProfile(
    val displayName: String,
    val scientificDescription: String
) {
    BASELINE_KINEMATICS(
        displayName = "Baseline kinematics",
        scientificDescription =
            "Robot DH parameters, joint definitions, solver configuration, seed state and target only."
    ),
    CONTEXT_ENHANCED(
        displayName = "Context enhanced",
        scientificDescription =
            "The same baseline plus pre-solve geometric, workspace, conditioning and joint-margin context."
    ),
    CONTEXT_EXPANDED(
        displayName = "Expanded research context",
        scientificDescription =
            "The frozen context profile plus pre-solve Jacobian spectrum, manipulability, directional DLS, limit-pressure and normalized chain descriptors."
    ),
    CONTEXT_RESEARCH_V2(
        displayName = "Experimental nonlinear context v2",
        scientificDescription =
            "The frozen 383-variable profile plus 85 versioned, pre-solve nonlinear boundary, pressure and geometry interaction candidates."
    )
}

enum class TrainingLabel(
    val csvValue: String
) {
    ACCEPTED("ACCEPTED"),
    UNCERTAIN("UNCERTAIN"),
    REJECTED("REJECTED");

    companion object {
        fun fromCsv(
            acceptanceClass: String?,
            solverAccepted: String?,
            status: String?
        ): TrainingLabel? {
            val statusLabel =
                when (status?.trim()?.uppercase()) {
                    "SUCCESS" -> ACCEPTED
                    "SUCCESS_WITH_WARNING" -> UNCERTAIN
                    "INVALID_INPUT",
                    "NO_CONVERGENCE",
                    "MAX_ITERATIONS_REACHED",
                    "NUMERICAL_FAILURE" -> REJECTED
                    else -> return null
                }
            val accepted = solverAccepted?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: return null
            val expectedAccepted = statusLabel != REJECTED
            if (accepted != expectedAccepted) return null

            val explicit =
                acceptanceClass?.trim()?.uppercase()?.let { value ->
                    entries.firstOrNull { label -> label.csvValue == value }
                } ?: return null
            return explicit.takeIf { label -> label == statusLabel }
        }
    }
}

enum class TrainingSplit {
    TRAIN,
    VALIDATION,
    TEST
}

enum class TrainingSplitStrategy(
    val displayName: String,
    val scientificDescription: String
) {
    SAMPLE_GROUPED(
        displayName = "Grouped samples",
        scientificDescription =
            "Same canonical robot geometry/limits, target coordinates and initial joint state stay together (robot names and IDs do not split aliases). Robots may appear in all partitions; this tests new sampled scenarios, not necessarily new mechanisms."
    ),
    ROBOT_HELD_OUT(
        displayName = "Hold out complete robots",
        scientificDescription =
            "Each canonical robot geometry and joint-limit definition belongs to one partition; aliases stay together. This tests transfer to excluded definitions, not necessarily unseen topology families. At least three independent definitions are required."
    )
}

data class EncodedTrainingSample(
    val features: FloatArray,
    val labelIndex: Int,
    val splitFingerprint: Long,
    val robotFingerprint: Long,
    val sourceRowIndex: Long,
    val robotId: String = "",
    val topologyKey: String = "",
    val targetClass: String = "",
    val targetSamplingStrategy: String = ""
)

data class TrainingDataset(
    val sourcePath: String,
    val profile: TrainingFeatureProfile,
    val featureNames: List<String>,
    val samples: List<EncodedTrainingSample>,
    val skippedRowCount: Int,
    val duplicateFingerprintCount: Int
) {
    init {
        require(featureNames.isNotEmpty()) { "Training datasets require at least one feature." }
        require(samples.all { it.features.size == featureNames.size }) {
            "Every training row must match the feature schema."
        }
        require(samples.all { sample -> sample.features.all(Float::isFinite) }) {
            "Training features must be finite."
        }
        require(samples.all { sample -> sample.labelIndex in TrainingLabel.entries.indices }) {
            "Training labels must belong to the declared outcome classes."
        }
    }
}

data class TrainingDatasetSplit(
    val trainIndices: IntArray,
    val validationIndices: IntArray,
    val testIndices: IntArray,
    val duplicateFingerprintsKeptTogether: Boolean,
    val strategy: TrainingSplitStrategy,
    val trainClassCounts: List<Int> = emptyList(),
    val validationClassCounts: List<Int> = emptyList(),
    val testClassCounts: List<Int> = emptyList(),
    val usedSortedFallback: Boolean = false
) {
    val missingTrainingClasses: List<TrainingLabel>
        get() = missingClasses(trainClassCounts)
    val missingValidationClasses: List<TrainingLabel>
        get() = missingClasses(validationClassCounts)
    val missingTestClasses: List<TrainingLabel>
        get() = missingClasses(testClassCounts)

    private fun missingClasses(counts: List<Int>): List<TrainingLabel> =
        if (counts.isEmpty()) emptyList()
        else TrainingLabel.entries.filter { label -> counts.getOrElse(label.ordinal) { 0 } == 0 }
}

data class FeatureNormalization(
    val means: FloatArray,
    val standardDeviations: FloatArray
) {
    init {
        require(means.size == standardDeviations.size)
        require(means.all(Float::isFinite))
        require(standardDeviations.all { it.isFinite() && it > 0f })
    }
}

data class PreparedTrainingDataset(
    val source: TrainingDataset,
    val split: TrainingDatasetSplit,
    val normalizedFeatures: Array<FloatArray>,
    val normalization: FeatureNormalization,
    val classWeights: FloatArray,
    val shortcutWarnings: List<String> = emptyList()
)

data class TrainingDatasetLoadProgress(
    val rowsRead: Int,
    val rowsAccepted: Int,
    val rowsSkipped: Int,
    val message: String
)

data class TrainingDatasetLoadResult(
    val dataset: TrainingDataset?,
    val errorMessage: String?
)
