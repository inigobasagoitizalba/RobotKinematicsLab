package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import java.io.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TrainingInferenceContractTest {
    private fun prepared(): PreparedTrainingDataset = TrainingDatasetPreparer().prepare(
        TrainingDataset("fixture", TrainingFeatureProfile.BASELINE_KINEMATICS, listOf("target_x", "joint_1_seed"),
            List(90) { index -> EncodedTrainingSample(floatArrayOf(index.toFloat(), (index % 7).toFloat()),
                index % 3, index.toLong(), (index % 9).toLong(), index + 1L) }, 0, 0), 42)
    private fun model() = LocalClassifierModel(TrainingModelKind.LINEAR_SOFTMAX, 2, 3, 0,
        floatArrayOf(1f, 0.2f, 0.1f, 1f, -1f, -1f), floatArrayOf(), floatArrayOf(), floatArrayOf(0f, 0f, 0f))
    private fun capture(data: PreparedTrainingDataset, model: LocalClassifierModel) =
        TrainingInferenceContract.capture("a".repeat(64), data, model, 90, 42, false)

    @Test fun cancellationInterruptsCorpusHashAndInferenceReplay() {
        val file = Files.createTempFile("inference-cancel", ".csv").toFile()
        file.writeBytes(ByteArray(256 * 1024))
        var checks = 0
        assertThrows(InterruptedIOException::class.java) {
            TrainingInferenceContract.corpusDigest(file) {
                if (++checks == 3) throw InterruptedIOException("cancelled")
            }
        }
        assertEquals(3, checks)
        val data = prepared()
        val model = model()
        val contract = capture(data, model)
        var inferenceChecks = 0
        assertThrows(InterruptedIOException::class.java) {
            contract.verify(data.source, data.split, data.normalization, model) {
                if (++inferenceChecks == 20) throw InterruptedIOException("cancelled")
            }
        }
        assertEquals(20, inferenceChecks)
    }

    @Test fun exactContractRoundTripRejectsChangedRowsSplitsSchemaModelAndNormalization() {
        val data = prepared()
        val model = model()
        val original = capture(data, model)
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use(original::write) }.toByteArray()
        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(TrainingInferenceContract::read)
        assertEquals(original, restored)
        restored.verify(data.source, data.split, data.normalization, model)
        fun rejected(source: TrainingDataset = data.source, split: TrainingDatasetSplit = data.split,
                     norm: FeatureNormalization = data.normalization, changedModel: LocalClassifierModel = model) {
            assertThrows(IllegalArgumentException::class.java) { restored.verify(source, split, norm, changedModel) }
        }
        rejected(source = data.source.copy(featureNames = data.source.featureNames.reversed()))
        rejected(source = data.source.copy(samples = data.source.samples.reversed()))
        val changed = data.source.samples.toMutableList()
        changed[0] = changed[0].copy(features = floatArrayOf(999f, 0f))
        rejected(source = data.source.copy(samples = changed))
        rejected(split = data.split.copy(testIndices = data.split.testIndices.reversedArray()))
        rejected(norm = data.normalization.copy(means = data.normalization.means.map { it + 1f }.toFloatArray()))
        rejected(changedModel = model.copy(outputBiases = floatArrayOf(1f, 0f, 0f)))
    }

    @Test fun corpusIdentityRejectsReplacementEvenWhenPathAndLengthAreUnchanged() {
        val file = Files.createTempFile("inference-corpus", ".csv").toFile()
        file.writeText("original")
        val data = prepared()
        val contract = capture(data, model()).copy(corpusSha256 = TrainingInferenceContract.corpusDigest(file))
        contract.verifyCorpus(file)
        file.writeText("modified")
        assertThrows(IllegalArgumentException::class.java) { contract.verifyCorpus(file) }
    }

    @Test fun originalFloatVectorMustMatchReplayBitForBit() {
        val data = prepared()
        val broken = data.normalizedFeatures.map { it.clone() }.toTypedArray()
        broken[0][0] += 0.001f
        val contract = capture(data.copy(normalizedFeatures = broken), model())
        assertThrows(IllegalArgumentException::class.java) {
            contract.verify(data.source, data.split, data.normalization, model())
        }
    }

    @Test fun rowSelectionContractRoundTripsAndRejectsUnverifiableNewestRows() {
        val valid =
            capture(prepared(), model()).copy(
                sampleAcrossEntireFile = true,
                expectedDataRowCount = 1_400,
                requiredNewestRows = 40
            )
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use(valid::write) }.toByteArray()

        assertEquals(valid, DataInputStream(ByteArrayInputStream(bytes)).use(TrainingInferenceContract::read))
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(sampleAcrossEntireFile = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(expectedDataRowCount = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(requiredNewestRows = valid.maximumRows + 1)
        }
    }

    @Test fun versionOneReplayContractRemainsReadableWithLegacyRowSelectionDefaults() {
        val original = capture(prepared(), model())
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(1)
                listOf(
                    original.corpusSha256,
                    original.modelSha256,
                    original.schemaSha256,
                    original.inferenceSha256
                ).forEach(output::writeUTF)
                output.writeInt(original.maximumRows)
                output.writeInt(original.randomSeed)
                output.writeUTF(original.splitStrategy.name)
                output.writeBoolean(original.sampleAcrossEntireFile)
            }
        }.toByteArray()

        val restored = DataInputStream(ByteArrayInputStream(bytes)).use(TrainingInferenceContract::read)

        assertEquals(1, restored.protocolVersion)
        assertEquals(null, restored.expectedDataRowCount)
        assertEquals(0, restored.requiredNewestRows)
    }
}
