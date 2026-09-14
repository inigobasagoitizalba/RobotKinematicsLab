package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureEncoder
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureProfile
import java.io.File
import java.io.PushbackReader

enum class FeatureSetDomain(val displayName: String) {
    CLASSIFICATION("Solver-outcome classification"),
    VERIFIED_IK("Verified 1 µm neural IK")
}

data class RegisteredFeatureSet(
    val technicalId: String,
    val displayName: String,
    val domain: FeatureSetDomain,
    val sourceContract: String,
    val featureNames: List<String>,
    val description: String,
    val comparisonPurpose: String,
    val derivedFromSharedScientificCsv: Boolean = true
) {
    init {
        require(technicalId.matches(Regex("[A-Za-z0-9._-]{1,100}")))
        require(displayName.isNotBlank())
        require(sourceContract.isNotBlank())
        require(featureNames.isNotEmpty() && featureNames.distinct().size == featureNames.size)
        require(description.isNotBlank())
        require(comparisonPurpose.isNotBlank())
    }

    val featureCount: Int get() = featureNames.size
}

object FeatureSetCatalog {
    /** One registry owner; cards are metadata for the existing encoder-backed entries. */
    fun classificationDefinitions():List<FeatureDefinition> = FeatureDefinitionData.classification
    fun definitions(entry:RegisteredFeatureSet):List<FeatureDefinition> = FeatureDefinitionData.forEntry(entry)
    fun definition(technicalId:String,domain:FeatureSetDomain=FeatureSetDomain.CLASSIFICATION):FeatureDefinition? =
        definitions(entries().first { it.domain==domain && it.featureCount==(if(domain==FeatureSetDomain.CLASSIFICATION) 468 else 361) }).firstOrNull { it.technicalId==technicalId }
    fun definitionFingerprint(entry:RegisteredFeatureSet):String = FeatureDefinitionIntegrity.fingerprint(definitions(entry))

    val frozenCumulativeCounts: List<Int> = listOf(108, 130, 152, 169, 191, 209, 233, 383)
    val researchCumulativeCounts: List<Int> =
        frozenCumulativeCounts + listOf(393, 403, 413, 423, 433, 443, 453, 463, 468)

    fun entries(): List<RegisteredFeatureSet> {
        val classificationComplete =
            TrainingFeatureProfile.entries.map { profile ->
                val names = ScientificDatasetTrainingReader.featureNames(profile)
                RegisteredFeatureSet(
                    technicalId = "classification.${profile.name.lowercase()}",
                    displayName = profile.displayName,
                    domain = FeatureSetDomain.CLASSIFICATION,
                    sourceContract = profile.name,
                    featureNames = names,
                    description = profile.scientificDescription,
                    comparisonPurpose =
                        when (profile) {
                            TrainingFeatureProfile.BASELINE_KINEMATICS -> "Reference arm using the raw kinematic and solver inputs."
                            TrainingFeatureProfile.CONTEXT_ENHANCED -> "Tests the first pre-solve context block against the baseline."
                            TrainingFeatureProfile.CONTEXT_EXPANDED -> "Tests the frozen expanded context against smaller nested contracts."
                            TrainingFeatureProfile.CONTEXT_RESEARCH_V2 -> "Tests the versioned experimental candidates beyond the frozen 383-variable contract."
                        }
                )
            }
        val expandedNames = ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_EXPANDED)
        val frozenIntermediate =
            frozenCumulativeCounts
                .filterNot { count -> classificationComplete.any { it.featureCount == count } }
                .map { count ->
                    RegisteredFeatureSet(
                        technicalId = "classification.cumulative-$count",
                        displayName = "Cumulative context · $count variables",
                        domain = FeatureSetDomain.CLASSIFICATION,
                        sourceContract = TrainingFeatureProfile.CONTEXT_EXPANDED.name,
                        featureNames = expandedNames.take(count),
                        description = "The first $count variables of the frozen, ordered 383-variable expanded contract.",
                        comparisonPurpose = "One nested arm in the 108 → 383 cumulative growth campaign."
                    )
                }
        val researchNames = ScientificDatasetTrainingReader.featureNames(TrainingFeatureProfile.CONTEXT_RESEARCH_V2)
        val researchIntermediate =
            researchCumulativeCounts
                .filter { it > 383 && it < 468 }
                .map { count ->
                    RegisteredFeatureSet(
                        technicalId = "classification.research-cumulative-$count",
                        displayName = "Research growth · $count variables",
                        domain = FeatureSetDomain.CLASSIFICATION,
                        sourceContract = TrainingFeatureProfile.CONTEXT_RESEARCH_V2.name,
                        featureNames = researchNames.take(count),
                        description = "The frozen 383 variables followed by ${count - 383} ordered experimental v2 candidates.",
                        comparisonPurpose = "One nested arm for measuring incremental evidence beyond the frozen expanded contract."
                    )
                }
        val verifiedIk =
            OneMicronIkFeatureProfile.entries.map { profile ->
                RegisteredFeatureSet(
                    technicalId = "verified-ik.${profile.name.lowercase()}",
                    displayName = profile.displayName,
                    domain = FeatureSetDomain.VERIFIED_IK,
                    sourceContract = profile.name,
                    featureNames = OneMicronIkFeatureEncoder.featureNames(profile),
                    description = profile.description,
                    comparisonPurpose = "Compares direct neural IK inputs under the same independent deterministic verification contract."
                )
            }
        return classificationComplete + frozenIntermediate + researchIntermediate + verifiedIk
    }
}

data class ScientificCsvPreview(
    val sourcePath: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val hasMoreRows: Boolean
) {
    init {
        require(sourcePath.isNotBlank())
        require(columns.isNotEmpty() && columns.distinct().size == columns.size)
        require(rows.all { it.size == columns.size })
    }
}

object ScientificCsvPreviewReader {
    internal const val MAXIMUM_LINE_CHARACTERS: Int = 1_048_576

    fun read(file: File, maximumRows: Int = 20): ScientificCsvPreview {
        require(maximumRows in 1..100)
        require(file.isFile) { "The source CSV is unavailable." }
        return PushbackReader(file.reader().buffered(), 1).use { reader ->
            val headerLine = reader.readBoundedLine() ?: error("The source CSV is empty.")
            val columns = ScientificDatasetTrainingReader.parseCsvLine(headerLine)
            require(columns.isNotEmpty() && columns.none(String::isBlank) && columns.distinct().size == columns.size) {
                "The source CSV header is invalid or contains duplicate columns."
            }
            val rows = ArrayList<List<String>>(maximumRows)
            var hasMore = false
            while (true) {
                if (rows.size == maximumRows) {
                    hasMore = reader.read() != -1
                    break
                }
                val line = reader.readBoundedLine() ?: break
                val values = ScientificDatasetTrainingReader.parseCsvLine(line)
                require(values.size == columns.size) {
                    "CSV row ${rows.size + 1} has ${values.size} values; ${columns.size} were expected."
                }
                rows += values
            }
            ScientificCsvPreview(file.absolutePath, columns, rows, hasMore)
        }
    }

    /**
     * Dataset files may legitimately be large, so the preview cannot cap total file size.
     * It does cap each record before allocating its full contents. This prevents a corrupt
     * single-line file from turning a bounded preview into an unbounded memory read.
     */
    private fun PushbackReader.readBoundedLine(): String? {
        val line = StringBuilder(minOf(256, MAXIMUM_LINE_CHARACTERS))
        while (true) {
            val next = read()
            when (next) {
                -1 -> return line.takeIf { it.isNotEmpty() }?.toString()
                '\n'.code -> return line.toString()
                '\r'.code -> {
                    val following = read()
                    if (following != '\n'.code && following != -1) unread(following)
                    return line.toString()
                }
                else -> {
                    require(line.length < MAXIMUM_LINE_CHARACTERS) {
                        "A CSV record exceeds the safe preview limit of $MAXIMUM_LINE_CHARACTERS characters."
                    }
                    line.append(next.toChar())
                }
            }
        }
    }
}
