package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/** Exact replay contract for the evaluated classifier, including all ordered inputs and test predictions. */
data class TrainingInferenceContract(
    val corpusSha256: String,
    val modelSha256: String,
    val schemaSha256: String,
    val inferenceSha256: String,
    val maximumRows: Int,
    val randomSeed: Int,
    val splitStrategy: TrainingSplitStrategy,
    val sampleAcrossEntireFile: Boolean,
    val expectedDataRowCount: Long? = null,
    val requiredNewestRows: Int = 0,
    val protocolVersion: Int = VERSION
) {
    init {
        require(protocolVersion in MINIMUM_VERSION..VERSION) { "Unsupported inference replay protocol; retrain this model." }
        require(maximumRows > 0)
        require(expectedDataRowCount == null || expectedDataRowCount > 0L)
        require(requiredNewestRows in 0..maximumRows)
        require(requiredNewestRows == 0 || (sampleAcrossEntireFile && expectedDataRowCount != null))
        require(listOf(corpusSha256, modelSha256, schemaSha256, inferenceSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    fun verifyCorpus(file: File, checkCancellation: () -> Unit = {}) {
        require(corpusSha256 == corpusDigest(file, checkCancellation)) {
            "The dataset has changed since training. Exact original inference cannot be explained; restore the original dataset or retrain."
        }
    }

    fun verify(dataset: TrainingDataset, split: TrainingDatasetSplit, normalization: FeatureNormalization, model: LocalClassifierModel,
               checkCancellation: () -> Unit = {}) {
        checkCancellation()
        require(schemaSha256 == schemaDigest(dataset.featureNames)) { "The ordered feature schema changed since training." }
        require(modelSha256 == modelDigest(dataset.featureNames, normalization, model, checkCancellation)) { "The model or normalization changed since evaluation." }
        require(split.strategy == splitStrategy)
        require(
            inferenceSha256 == inferenceDigest(
                dataset,
                split,
                normalization,
                model,
                protocolVersion = protocolVersion,
                checkCancellation = checkCancellation
            )
        ) {
            "The reconstructed rows, split, normalized inputs or predictions differ from the original evaluated inference."
        }
    }

    fun write(output: DataOutputStream) {
        output.writeInt(protocolVersion)
        listOf(corpusSha256, modelSha256, schemaSha256, inferenceSha256).forEach(output::writeUTF)
        output.writeInt(maximumRows)
        output.writeInt(randomSeed)
        output.writeUTF(splitStrategy.name)
        output.writeBoolean(sampleAcrossEntireFile)
        output.writeBoolean(expectedDataRowCount != null)
        expectedDataRowCount?.let(output::writeLong)
        output.writeInt(requiredNewestRows)
    }

    companion object {
        const val VERSION = 2
        private const val MINIMUM_VERSION = 1

        fun read(input: DataInputStream): TrainingInferenceContract {
            val version = input.readInt()
            require(version in MINIMUM_VERSION..VERSION) { "Unsupported inference replay protocol." }
            val corpus = input.readUTF()
            val model = input.readUTF()
            val schema = input.readUTF()
            val inference = input.readUTF()
            val maximumRows = input.readInt()
            val randomSeed = input.readInt()
            val split = TrainingSplitStrategy.valueOf(input.readUTF())
            val sampleAcross = input.readBoolean()
            val expectedRows = if (version >= 2 && input.readBoolean()) input.readLong() else null
            val requiredNewest = if (version >= 2) input.readInt() else 0
            return TrainingInferenceContract(
                corpus,
                model,
                schema,
                inference,
                maximumRows,
                randomSeed,
                split,
                sampleAcross,
                expectedRows,
                requiredNewest,
                version
            )
        }

        fun capture(corpusSha256: String, dataset: PreparedTrainingDataset, model: LocalClassifierModel,
                    maximumRows: Int, randomSeed: Int, sampleAcrossEntireFile: Boolean,
                    expectedDataRowCount: Long? = null, requiredNewestRows: Int = 0,
                    checkCancellation: () -> Unit = {}): TrainingInferenceContract =
            TrainingInferenceContract(corpusSha256, modelDigest(dataset.source.featureNames, dataset.normalization, model, checkCancellation),
                schemaDigest(dataset.source.featureNames), inferenceDigest(dataset.source, dataset.split, dataset.normalization, model,
                    dataset.normalizedFeatures, VERSION, checkCancellation), maximumRows, randomSeed, dataset.split.strategy,
                sampleAcrossEntireFile, expectedDataRowCount, requiredNewestRows)

        fun corpusDigest(file: File, checkCancellation: () -> Unit = {}): String {
            checkCancellation()
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkCancellation()
                    val size = input.read(buffer)
                    if (size < 0) break
                    digest.update(buffer, 0, size)
                }
            }
            return digest.digest().hex()
        }

        fun schemaDigest(names: List<String>): String = digest { output ->
            output.writeInt(names.size)
            names.forEach(output::writeUTF)
        }

        fun modelDigest(names: List<String>, normalization: FeatureNormalization, model: LocalClassifierModel,
                        checkCancellation: () -> Unit = {}): String = digest { output ->
            checkCancellation()
            output.writeUTF(schemaDigest(names))
            output.writeUTF(model.kind.name)
            output.writeInt(model.inputFeatureCount)
            output.writeInt(model.hiddenUnitCount)
            output.writeInt(model.classCount)
            listOf(normalization.means, normalization.standardDeviations, model.inputWeights, model.hiddenBiases,
                model.outputWeights, model.outputBiases).forEach { values -> output.floats(values, checkCancellation) }
        }

        private fun inferenceDigest(dataset: TrainingDataset, split: TrainingDatasetSplit,
                                    normalization: FeatureNormalization, model: LocalClassifierModel,
                                    originalNormalized: Array<FloatArray>? = null,
                                    protocolVersion: Int = VERSION,
                                    checkCancellation: () -> Unit = {}): String = digest { output ->
            checkCancellation()
            output.writeInt(protocolVersion)
            output.writeUTF(dataset.profile.name)
            output.writeInt(dataset.samples.size)
            val test = split.testIndices.toHashSet()
            listOf(split.trainIndices, split.validationIndices, split.testIndices).forEach { indices ->
                output.writeInt(indices.size)
                indices.forEach(output::writeInt)
            }
            dataset.samples.forEachIndexed { index, sample ->
                checkCancellation()
                output.writeLong(sample.sourceRowIndex)
                output.writeLong(sample.splitFingerprint)
                output.writeLong(sample.robotFingerprint)
                output.writeInt(sample.labelIndex)
                output.floats(sample.features)
                val normalized = originalNormalized?.get(index) ?: ClassifierFeatureNormalizer.normalize(sample.features, normalization)
                output.floats(normalized)
                if (index in test) output.floats(model.probabilities(normalized))
            }
        }

        private fun digest(write: (DataOutputStream) -> Unit): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val sink = object : OutputStream() { override fun write(value: Int) {} ; override fun write(bytes: ByteArray, offset: Int, length: Int) {} }
            DataOutputStream(DigestOutputStream(sink, digest)).use(write)
            return digest.digest().hex()
        }
        private fun DataOutputStream.floats(values: FloatArray, checkCancellation: () -> Unit = {}) {
            writeInt(values.size)
            values.forEachIndexed { index, value ->
                if (index % 1024 == 0) checkCancellation()
                writeInt(value.toRawBits())
            }
        }
        private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Shared train/inference arithmetic: Double intermediates, one final Float rounding. */
object ClassifierFeatureNormalizer {
    fun normalize(features: FloatArray, normalization: FeatureNormalization): FloatArray {
        require(features.size == normalization.means.size)
        require(features.all(Float::isFinite))
        return FloatArray(features.size) { index ->
            ((features[index].toDouble() - normalization.means[index].toDouble()) /
                normalization.standardDeviations[index].toDouble()).coerceIn(-8.0, 8.0).toFloat()
        }
    }
}
