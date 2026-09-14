package com.robotkinematicslab.mobile.ml.storage

import android.content.Context
import com.robotkinematicslab.mobile.ml.data.TrainingInferenceContract
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.LocalClassifierModel
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.LocalTrainingRunResult
import com.robotkinematicslab.mobile.ml.training.TrainedProfileResult
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.provenance.AppBuildIdentity
import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

data class StoredTrainingVariantEvidence(
    val featureSelectionId: String,
    val featureSelectionName: String,
    val profile: TrainingFeatureProfile,
    val featureCount: Int,
    val trainRowCount: Int,
    val validationRowCount: Int,
    val testRowCount: Int,
    val testAccuracy: Double,
    val testBalancedAccuracy: Double,
    val testMacroF1: Double,
    val testLogLoss: Double,
    val testBrierScore: Double,
    val testExpectedCalibrationError: Double,
    val inferenceNanosPerSample: Double,
    val trainingDurationMillis: Long,
    val parameterCount: Int,
    val splitEvidence: com.robotkinematicslab.mobile.ml.data.TrainingSplitEvidence? = null,
    val datasetWarnings: List<String> = emptyList()
)

data class TrainingRunSummary(
    val runId: String,
    val runName: String,
    val datasetPath: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val baselineTestMacroF1: Double?,
    val contextTestMacroF1: Double?,
    val macroF1Delta: Double,
    val randomSeed: Int,
    val splitStrategy: TrainingSplitStrategy,
    val maximumRows: Int,
    val directoryPath: String,
    val historyCsvPath: String,
    val modelPaths: List<String>,
    val expandedContextTestMacroF1: Double? = null,
    val expandedMacroF1DeltaVsBaseline: Double = Double.NaN,
    val expandedMacroF1DeltaVsContext: Double = Double.NaN,
    val variants: List<StoredTrainingVariantEvidence> = emptyList(),
    val sampleAcrossEntireDataset: Boolean = false,
    val expectedDatasetRows: Long? = null,
    val requiredNewestRows: Int = 0,
    val trainingControls: com.robotkinematicslab.mobile.ml.training.StoredTrainingControls? = null
)

data class StoredTrainingIteration(
    val globalIteration: Int,
    val profile: TrainingFeatureProfile,
    val candidateId: String,
    val modelKind: TrainingModelKind,
    val hiddenUnits: Int,
    val epoch: Int,
    val trainingLoss: Double,
    val validationAccuracy: Double,
    val validationBalancedAccuracy: Double,
    val validationMacroF1: Double,
    val validationLogLoss: Double,
    val elapsedMillis: Long,
    val featureSelectionId: String = profile.name.lowercase(),
    val featureSelectionName: String = profile.displayName
)

data class StoredLocalModel(
    val runId: String,
    val profile: TrainingFeatureProfile,
    val candidateId: String,
    val featureNames: List<String>,
    val normalization: FeatureNormalization,
    val model: LocalClassifierModel,
    val featureSelectionId: String = profile.name.lowercase(),
    val featureSelectionName: String = profile.displayName,
    val inferenceContract: TrainingInferenceContract? = null
)

class TrainingStorageRepository(
    private val trainingDirectory: File,
    private val modelsDirectory: File,
    private val buildIdentity: AppBuildIdentity = AppBuildIdentity.UNKNOWN
) {

    constructor(context: Context) : this(
        AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData).trainingDirectory,
        AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData).modelsDirectory,
        AppBuildIdentity.current()
    )

    @Synchronized
    fun save(result: LocalTrainingRunResult): LocalTrainingRunResult {
        requireSafeRunId(result.runId)
        require(result.startedAtEpochMillis > 0L && result.finishedAtEpochMillis >= result.startedAtEpochMillis) {
            "Training run timestamps are invalid."
        }
        require(trainingDirectory.mkdirs() || trainingDirectory.isDirectory) { "Training storage is unavailable." }
        require(modelsDirectory.mkdirs() || modelsDirectory.isDirectory) { "Model storage is unavailable." }
        val runDirectory = File(trainingDirectory, result.runId)
        require(!runDirectory.exists()) { "Training run already exists: ${result.runId}" }
        val historyFile = File(runDirectory, "iteration-history.csv")
        val modelFiles = result.comparison.variants.map { profile ->
            File(
                modelsDirectory,
                "${result.runId}-${safeFileStem(profile.featureSelectionId)}-${stableIdDigest(profile.featureSelectionId)}.rklm"
            )
        }
        require(modelFiles.map { it.name }.distinct().size == modelFiles.size) {
            "Training variants resolve to duplicate model identities."
        }
        require(modelFiles.none(File::exists)) { "A model already exists for training run ${result.runId}." }
        require(runDirectory.mkdirs()) { "Training run directory could not be created." }
        val modelPaths = mutableListOf<String>()
        val summaryFile = File(runDirectory, "summary.properties")
        try {
            writeHistory(historyFile, result)
            result.comparison.variants.zip(modelFiles).forEach { (profile, modelFile) ->
                writeModelAtomically(modelFile, result.runId, profile)
                modelPaths += modelFile.absolutePath
            }
            writeSummary(
                summaryFile,
                result.copy(
                    summaryPath = summaryFile.absolutePath,
                    historyCsvPath = historyFile.absolutePath,
                    modelPaths = modelPaths
                )
            )
            TrainingResultEvidenceArchive.save(runDirectory, result)
        } catch (failure: Throwable) {
            modelFiles.forEach { model ->
                model.delete()
                File(model.parentFile, "${model.name}.tmp").delete()
            }
            runDirectory.deleteRecursively()
            throw failure
        }

        return result.copy(
            summaryPath = summaryFile.absolutePath,
            historyCsvPath = historyFile.absolutePath,
            modelPaths = modelPaths
        )
    }

    fun listRuns(): List<TrainingRunSummary> {
        trainingDirectory.mkdirs()
        return trainingDirectory.listFiles(File::isDirectory)
            ?.mapNotNull { directory -> readSummary(File(directory, "summary.properties")) }
            ?.sortedByDescending { it.startedAtEpochMillis }
            .orEmpty()
    }

    fun loadModel(file: File): StoredLocalModel {
        require(file.isFile && file.length() in 1..MAXIMUM_STORED_MODEL_BYTES) {
            "Stored model file size is invalid."
        }
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readUTF() == MODEL_MAGIC) { "Not a Robot Kinematics Lab model file." }
            val schemaVersion = input.readInt()
            require(schemaVersion in 1..MODEL_SCHEMA_VERSION) { "Unsupported local model schema." }
            val runId = input.readUTF()
            val profile = TrainingFeatureProfile.valueOf(input.readUTF())
            val featureSelectionId = if (schemaVersion >= 2) input.readUTF() else profile.name.lowercase()
            val featureSelectionName = if (schemaVersion >= 2) input.readUTF() else profile.displayName
            val candidateId = input.readUTF()
            val kind = TrainingModelKind.valueOf(input.readUTF())
            val inputFeatureCount = input.readInt()
            val classCount = input.readInt()
            val hiddenUnitCount = input.readInt()
            require(inputFeatureCount in 1..MAXIMUM_FEATURE_COUNT) { "Stored model input size is invalid." }
            require(classCount in 2..MAXIMUM_CLASS_COUNT) { "Stored model class count is invalid." }
            require(hiddenUnitCount in 0..MAXIMUM_HIDDEN_UNIT_COUNT) { "Stored model hidden size is invalid." }
            require(kind != TrainingModelKind.AUTOMATIC) { "Stored model kind must be concrete." }
            if (kind == TrainingModelKind.LINEAR_SOFTMAX) {
                require(hiddenUnitCount == 0) { "Linear stored models cannot contain hidden units." }
            } else {
                require(hiddenUnitCount > 0) { "Neural stored models require hidden units." }
            }
            val featureNameCount = input.readInt()
            require(featureNameCount == inputFeatureCount) { "Feature schema does not match model input size." }
            val featureNames = List(featureNameCount) { input.readUTF() }
            require(featureNames.all(String::isNotBlank) && featureNames.distinct().size == featureNames.size) {
                "Stored model feature names are invalid."
            }
            val means = input.readFloatArray(inputFeatureCount, "normalization means")
            val deviations = input.readFloatArray(inputFeatureCount, "normalization deviations")
            val inputWeightCount =
                checkedArrayProduct(
                    inputFeatureCount,
                    if (kind == TrainingModelKind.LINEAR_SOFTMAX) classCount else hiddenUnitCount,
                    "input weights"
                )
            val inputWeights = input.readFloatArray(inputWeightCount, "input weights")
            val hiddenBiases =
                input.readFloatArray(
                    if (kind == TrainingModelKind.LINEAR_SOFTMAX) 0 else hiddenUnitCount,
                    "hidden biases"
                )
            val outputWeights =
                input.readFloatArray(
                    if (kind == TrainingModelKind.LINEAR_SOFTMAX) 0
                    else checkedArrayProduct(hiddenUnitCount, classCount, "output weights"),
                    "output weights"
                )
            val outputBiases = input.readFloatArray(classCount, "output biases")
            val inferenceContract = if (schemaVersion >= 3 && input.readBoolean()) TrainingInferenceContract.read(input) else null
            require(input.read() == -1) { "Unexpected trailing data in stored model." }
            return StoredLocalModel(
                runId = runId,
                profile = profile,
                candidateId = candidateId,
                featureNames = featureNames,
                normalization = FeatureNormalization(means, deviations),
                model =
                    LocalClassifierModel(
                        kind = kind,
                        inputFeatureCount = inputFeatureCount,
                        classCount = classCount,
                        hiddenUnitCount = hiddenUnitCount,
                        inputWeights = inputWeights,
                        hiddenBiases = hiddenBiases,
                        outputWeights = outputWeights,
                        outputBiases = outputBiases
                    ),
                featureSelectionId = featureSelectionId,
                featureSelectionName = featureSelectionName,
                inferenceContract = inferenceContract
            )
        }
    }

    fun loadIterationHistory(run: TrainingRunSummary): List<StoredTrainingIteration> {
        requireSafeRunId(run.runId)
        val directory = File(run.directoryPath).canonicalFile
        require(directory.parentFile == trainingDirectory.canonicalFile && directory.name == run.runId) {
            "Training run is outside managed storage."
        }
        val current = requireNotNull(readSummary(File(directory, SUMMARY_FILE_NAME))) {
            "Training run summary is missing or invalid."
        }
        require(current.runId == run.runId) { "Training run identity changed on disk." }
        val file = File(run.historyCsvPath)
        require(file.canonicalFile == File(directory, HISTORY_FILE_NAME).canonicalFile) {
            "Training history is outside the selected run."
        }
        require(file.isFile) { "Training iteration history does not exist: ${file.absolutePath}" }
        return file.bufferedReader().use { reader ->
            val header = reader.readLine()
            val legacyHistory = header == LEGACY_HISTORY_HEADER
            require(legacyHistory || header == HISTORY_HEADER) { "Unsupported training-history schema." }
            reader.lineSequence()
                .filter(String::isNotBlank)
                .mapIndexed { lineIndex, line ->
                    val values = parseCsvLine(line)
                    val expectedColumns = if (legacyHistory) LEGACY_HISTORY_COLUMN_COUNT else HISTORY_COLUMN_COUNT
                    require(values.size == expectedColumns) {
                        "Training-history row ${lineIndex + 2} has ${values.size} columns; expected $expectedColumns."
                    }
                    require(values[0] == HISTORY_SCHEMA_VERSION) {
                        "Training-history row ${lineIndex + 2} uses an unsupported schema."
                    }
                    require(values[1] == run.runId) {
                        "Training-history row ${lineIndex + 2} belongs to another run."
                    }
                    StoredTrainingIteration(
                        globalIteration = values[2].toInt(),
                        profile = TrainingFeatureProfile.valueOf(values[3]),
                        candidateId = values[4],
                        modelKind = TrainingModelKind.valueOf(values[5]),
                        hiddenUnits = values[6].toInt(),
                        epoch = values[7].toInt(),
                        trainingLoss = values[8].finiteDouble("training loss", lineIndex),
                        validationAccuracy = values[9].finiteDouble("validation accuracy", lineIndex),
                        validationBalancedAccuracy = values[10].finiteDouble("balanced accuracy", lineIndex),
                        validationMacroF1 = values[11].finiteDouble("macro-F1", lineIndex),
                        validationLogLoss = values[12].finiteDouble("validation log loss", lineIndex),
                        elapsedMillis = values[13].toLong().also { require(it >= 0L) },
                        featureSelectionId = if (legacyHistory) values[3].lowercase() else values[14],
                        featureSelectionName = if (legacyHistory) TrainingFeatureProfile.valueOf(values[3]).displayName else values[15]
                    ).also { iteration ->
                        require(iteration.globalIteration >= 0)
                        require(iteration.epoch >= 0)
                        require(iteration.hiddenUnits >= 0)
                        require(iteration.validationAccuracy in 0.0..1.0)
                        require(iteration.validationBalancedAccuracy in 0.0..1.0)
                        require(iteration.validationMacroF1 in 0.0..1.0)
                        require(iteration.trainingLoss >= 0.0)
                        require(iteration.validationLogLoss >= 0.0)
                    }
                }
                .toList()
        }
    }

    private fun writeHistory(
        file: File,
        result: LocalTrainingRunResult
    ) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.bufferedWriter().use { writer ->
            writer.appendLine(HISTORY_HEADER)
            result.iterations.forEach { iteration ->
                writer.appendCsv(
                    listOf(
                        HISTORY_SCHEMA_VERSION,
                        result.runId,
                        iteration.globalIteration.toString(),
                        iteration.profile.name,
                        iteration.candidateId,
                        iteration.modelKind.name,
                        iteration.hiddenUnits.toString(),
                        iteration.epoch.toString(),
                        iteration.trainingLoss.toString(),
                        iteration.validationMetrics.accuracy.toString(),
                        iteration.validationMetrics.balancedAccuracy.toString(),
                        iteration.validationMetrics.macroF1.toString(),
                        iteration.validationMetrics.logLoss.toString(),
                        iteration.elapsedMillis.toString(),
                        iteration.featureSelectionId,
                        iteration.featureSelectionName
                    )
                )
            }
        }
        moveReplacing(temporary, file)
    }

    private fun writeModelAtomically(
        file: File,
        runId: String,
        profile: TrainedProfileResult
    ) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        DataOutputStream(FileOutputStream(temporary).buffered()).use { output ->
            output.writeUTF(MODEL_MAGIC)
            output.writeInt(MODEL_SCHEMA_VERSION)
            output.writeUTF(runId)
            output.writeUTF(profile.profile.name)
            output.writeUTF(profile.featureSelectionId)
            output.writeUTF(profile.featureSelectionName)
            output.writeUTF(profile.candidateId)
            output.writeUTF(profile.model.kind.name)
            output.writeInt(profile.model.inputFeatureCount)
            output.writeInt(profile.model.classCount)
            output.writeInt(profile.model.hiddenUnitCount)
            output.writeInt(profile.featureNames.size)
            profile.featureNames.forEach(output::writeUTF)
            output.writeFloatArray(profile.normalization.means)
            output.writeFloatArray(profile.normalization.standardDeviations)
            output.writeFloatArray(profile.model.inputWeights)
            output.writeFloatArray(profile.model.hiddenBiases)
            output.writeFloatArray(profile.model.outputWeights)
            output.writeFloatArray(profile.model.outputBiases)
            output.writeBoolean(profile.inferenceContract != null)
            profile.inferenceContract?.write(output)
        }
        moveReplacing(temporary, file)
    }

    private fun writeSummary(
        file: File,
        result: LocalTrainingRunResult
    ) {
        val baseline = result.comparison.baseline
        val context = result.comparison.contextEnhanced
        val expanded = result.comparison.contextExpanded
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SUMMARY_SCHEMA_VERSION.toString())
                setProperty("producerApplicationId", buildIdentity.applicationId)
                setProperty("producerVersionName", buildIdentity.versionName)
                setProperty("producerVersionCode", buildIdentity.versionCode.toString())
                setProperty("producerBuildType", buildIdentity.buildType)
                setProperty("runId", result.runId)
                setProperty("runName", result.config.runName)
                setProperty("datasetPath", result.config.datasetPath)
                setProperty("startedAtEpochMillis", result.startedAtEpochMillis.toString())
                setProperty("finishedAtEpochMillis", result.finishedAtEpochMillis.toString())
                setProperty("randomSeed", result.config.randomSeed.toString())
                setProperty("resourceMode", result.config.resourceMode.name)
                setProperty("splitStrategy", result.config.splitStrategy.name)
                setProperty("requestedModelKind", result.config.modelKind.name)
                setProperty("maximumRows", result.config.maximumRows.toString())
                setProperty("sampleAcrossEntireDataset", result.config.sampleAcrossEntireDataset.toString())
                result.config.expectedDatasetRows?.let { setProperty("expectedDatasetRows", it.toString()) }
                setProperty("requiredNewestRows", result.config.requiredNewestRows.toString())
                setProperty("epochs", result.config.epochs.toString())
                setProperty("batchSize", result.config.batchSize.toString())
                setProperty("workerCount", result.config.workerCount.toString())
                setProperty("learningRate", result.config.learningRate.toString())
                setProperty("l2Regularization", result.config.l2Regularization.toString())
                TrainingControlMetadata.write(this, com.robotkinematicslab.mobile.ml.training.StoredTrainingControls.from(result.config, result.workerBatchCounts))
                setProperty("macroF1Delta", result.comparison.macroF1Delta.toString())
                setProperty("balancedAccuracyDelta", result.comparison.balancedAccuracyDelta.toString())
                setProperty("logLossDelta", result.comparison.logLossDelta.toString())
                setProperty("inferenceNanosDelta", result.comparison.inferenceNanosDelta.toString())
                setProperty("expandedMacroF1DeltaVsBaseline", result.comparison.expandedMacroF1DeltaVsBaseline.toString())
                setProperty("expandedMacroF1DeltaVsContext", result.comparison.expandedMacroF1DeltaVsContext.toString())
                setProperty("expandedBalancedAccuracyDeltaVsBaseline", result.comparison.expandedBalancedAccuracyDeltaVsBaseline.toString())
                setProperty("expandedLogLossDeltaVsBaseline", result.comparison.expandedLogLossDeltaVsBaseline.toString())
                setProperty("expandedInferenceNanosDeltaVsBaseline", result.comparison.expandedInferenceNanosDeltaVsBaseline.toString())
                setProperty("historyCsvPath", result.historyCsvPath)
                setProperty("modelPaths", result.modelPaths.joinToString(PATH_SEPARATOR))
                setProperty("variantCount", result.comparison.variants.size.toString())
                result.comparison.variants.forEachIndexed { index, variant ->
                    writeVariant("variant.$index", variant)
                }
                baseline?.let { writeProfile("baseline", it) }
                context?.let { writeProfile("context", it) }
                expanded?.let { writeProfile("expanded", it) }
            }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().buffered().use { properties.store(it, "Robot Kinematics Lab training run") }
        moveReplacing(temporary, file)
    }

    private fun Properties.writeProfile(prefix: String, profile: TrainedProfileResult) {
        setProperty("$prefix.profile", profile.profile.name)
        setProperty("$prefix.candidateId", profile.candidateId)
        setProperty("$prefix.bestEpoch", profile.bestEpoch.toString())
        setProperty("$prefix.parameterCount", profile.parameterCount.toString())
        setProperty("$prefix.trainRows", profile.trainRowCount.toString())
        setProperty("$prefix.validationRows", profile.validationRowCount.toString())
        setProperty("$prefix.testRows", profile.testRowCount.toString())
        setProperty("$prefix.testAccuracy", profile.testMetrics.accuracy.toString())
        setProperty("$prefix.testBalancedAccuracy", profile.testMetrics.balancedAccuracy.toString())
        setProperty("$prefix.testMacroF1", profile.testMetrics.macroF1.toString())
        setProperty("$prefix.testLogLoss", profile.testMetrics.logLoss.toString())
        setProperty("$prefix.testBrierScore", profile.testMetrics.brierScore.toString())
        setProperty("$prefix.testExpectedCalibrationError", profile.testMetrics.expectedCalibrationError.toString())
        setProperty("$prefix.inferenceNanosPerSample", profile.testMetrics.inferenceNanosPerSample.toString())
        setProperty("$prefix.confusionMatrix", profile.testMetrics.confusionMatrix.joinToString(";") { it.joinToString(":") })
        setProperty("$prefix.duplicateFingerprints", profile.duplicateFingerprintCount.toString())
        setProperty("$prefix.duplicatesKeptTogether", profile.duplicateFingerprintsKeptTogether.toString())
    }

    private fun Properties.writeVariant(prefix: String, variant: TrainedProfileResult) {
        setProperty("$prefix.featureSelectionId", variant.featureSelectionId)
        setProperty("$prefix.featureSelectionName", variant.featureSelectionName)
        setProperty("$prefix.profile", variant.profile.name)
        setProperty("$prefix.featureCount", variant.featureNames.size.toString())
        setProperty("$prefix.trainRows", variant.trainRowCount.toString())
        setProperty("$prefix.validationRows", variant.validationRowCount.toString())
        setProperty("$prefix.testRows", variant.testRowCount.toString())
        setProperty("$prefix.testAccuracy", variant.testMetrics.accuracy.toString())
        setProperty("$prefix.testBalancedAccuracy", variant.testMetrics.balancedAccuracy.toString())
        setProperty("$prefix.testMacroF1", variant.testMetrics.macroF1.toString())
        setProperty("$prefix.testLogLoss", variant.testMetrics.logLoss.toString())
        setProperty("$prefix.testBrierScore", variant.testMetrics.brierScore.toString())
        setProperty("$prefix.testExpectedCalibrationError", variant.testMetrics.expectedCalibrationError.toString())
        setProperty("$prefix.inferenceNanosPerSample", variant.testMetrics.inferenceNanosPerSample.toString())
        setProperty("$prefix.trainingDurationMillis", variant.trainingDurationMillis.toString())
        setProperty("$prefix.parameterCount", variant.parameterCount.toString())
        variant.splitEvidence?.let { TrainingSplitMetadata.write(this,prefix,it) }
        setProperty("$prefix.datasetWarnings",variant.datasetWarnings.joinToString("\n"))
    }

    private fun readSummary(file: File): TrainingRunSummary? {
        if (!file.isFile || file.length() !in 1..MAXIMUM_SUMMARY_BYTES) return null
        return runCatching {
            val properties = Properties().apply {
                file.inputStream().buffered().use(::load)
            }
            require(properties.getProperty("schemaVersion").toInt() == SUMMARY_SCHEMA_VERSION)
            val directory = requireNotNull(file.parentFile).canonicalFile
            require(directory.parentFile == trainingDirectory.canonicalFile)
            val runId = properties.getProperty("runId").also(::requireSafeRunId)
            require(runId == directory.name)
            val historyFile = File(directory, HISTORY_FILE_NAME).canonicalFile
            require(historyFile.isFile)
            val storedHistory = File(properties.getProperty("historyCsvPath", "")).canonicalFile
            require(storedHistory == historyFile)
            val modelPaths = properties.getProperty("modelPaths", "")
                .split(PATH_SEPARATOR)
                .filter(String::isNotBlank)
            require(modelPaths.isNotEmpty())
            modelPaths.forEach { path ->
                val modelFile = File(path).canonicalFile
                require(modelFile.parentFile == modelsDirectory.canonicalFile && modelFile.isFile)
                require(modelFile.name.startsWith("$runId-"))
            }
            val startedAt = properties.getProperty("startedAtEpochMillis").toLong()
            val finishedAt = properties.getProperty("finishedAtEpochMillis").toLong()
            require(startedAt > 0L && finishedAt >= startedAt)
            TrainingRunSummary(
                runId = runId,
                runName = properties.getProperty("runName"),
                datasetPath = properties.getProperty("datasetPath"),
                startedAtEpochMillis = startedAt,
                finishedAtEpochMillis = finishedAt,
                baselineTestMacroF1 = properties.getProperty("baseline.testMacroF1")?.toDoubleOrNull(),
                contextTestMacroF1 = properties.getProperty("context.testMacroF1")?.toDoubleOrNull(),
                macroF1Delta = properties.getProperty("macroF1Delta")?.toDoubleOrNull() ?: Double.NaN,
                randomSeed = properties.getProperty("randomSeed").toInt(),
                splitStrategy = TrainingSplitStrategy.valueOf(properties.getProperty("splitStrategy")),
                maximumRows = properties.getProperty("maximumRows").toInt().also { require(it > 0) },
                directoryPath = directory.absolutePath,
                historyCsvPath = historyFile.absolutePath,
                modelPaths = modelPaths,
                expandedContextTestMacroF1 = properties.getProperty("expanded.testMacroF1")?.toDoubleOrNull(),
                expandedMacroF1DeltaVsBaseline =
                    properties.getProperty("expandedMacroF1DeltaVsBaseline")?.toDoubleOrNull() ?: Double.NaN,
                expandedMacroF1DeltaVsContext =
                    properties.getProperty("expandedMacroF1DeltaVsContext")?.toDoubleOrNull() ?: Double.NaN,
                variants = properties.readVariants(),
                sampleAcrossEntireDataset =
                    properties.getProperty("sampleAcrossEntireDataset")?.toBooleanStrictOrNull() ?: false,
                expectedDatasetRows = properties.getProperty("expectedDatasetRows")?.toLongOrNull(),
                requiredNewestRows = properties.getProperty("requiredNewestRows")?.toIntOrNull() ?: 0,
                trainingControls = TrainingControlMetadata.read(properties)
            )
        }.getOrNull()
    }

    private fun Properties.readVariants(): List<StoredTrainingVariantEvidence> {
        val count = getProperty("variantCount")?.toIntOrNull() ?: 0
        require(count in 0..MAXIMUM_STORED_VARIANTS) { "Stored training variant count is invalid." }
        return (0 until count).map { index ->
                val prefix = "variant.$index"
                StoredTrainingVariantEvidence(
                    featureSelectionId = getProperty("$prefix.featureSelectionId"),
                    featureSelectionName = getProperty("$prefix.featureSelectionName"),
                    profile = TrainingFeatureProfile.valueOf(getProperty("$prefix.profile")),
                    featureCount = getProperty("$prefix.featureCount").toInt().also { require(it > 0) },
                    trainRowCount = getProperty("$prefix.trainRows").toInt().also { require(it > 0) },
                    validationRowCount = getProperty("$prefix.validationRows").toInt().also { require(it > 0) },
                    testRowCount = getProperty("$prefix.testRows").toInt().also { require(it > 0) },
                    testAccuracy = getProperty("$prefix.testAccuracy").finiteUnitMetric(),
                    testBalancedAccuracy = getProperty("$prefix.testBalancedAccuracy").finiteUnitMetric(),
                    testMacroF1 = getProperty("$prefix.testMacroF1").finiteUnitMetric(),
                    testLogLoss = getProperty("$prefix.testLogLoss").toDouble().also { require(it.isFinite() && it >= 0.0) },
                    testBrierScore = getProperty("$prefix.testBrierScore").toDouble(),
                    testExpectedCalibrationError = getProperty("$prefix.testExpectedCalibrationError").toDouble(),
                    inferenceNanosPerSample = getProperty("$prefix.inferenceNanosPerSample").toDouble(),
                    trainingDurationMillis = getProperty("$prefix.trainingDurationMillis").toLong().also { require(it >= 0L) },
                    parameterCount = getProperty("$prefix.parameterCount").toInt().also { require(it > 0) },
                    splitEvidence = TrainingSplitMetadata.read(this,prefix,listOf(getProperty("$prefix.trainRows").toInt(),getProperty("$prefix.validationRows").toInt(),getProperty("$prefix.testRows").toInt()),TrainingSplitStrategy.valueOf(getProperty("splitStrategy"))),
                    datasetWarnings = getProperty("$prefix.datasetWarnings", "").lineSequence().filter(String::isNotBlank).toList()
                )
        }
    }

    private fun String.finiteUnitMetric(): Double =
        toDouble().also { require(it.isFinite() && it in 0.0..1.0) }

    private fun DataOutputStream.writeFloatArray(values: FloatArray) {
        writeInt(values.size)
        values.forEach(::writeFloat)
    }

    private fun DataInputStream.readFloatArray(expectedSize: Int, label: String): FloatArray {
        val size = readInt()
        require(size == expectedSize && size in 0..MAXIMUM_STORED_ARRAY_SIZE) {
            "Stored model $label size is invalid."
        }
        return FloatArray(size) { readFloat() }
    }

    private fun checkedArrayProduct(first: Int, second: Int, label: String): Int {
        val size = first.toLong() * second.toLong()
        require(size in 0..MAXIMUM_STORED_ARRAY_SIZE.toLong()) {
            "Stored model $label size is invalid."
        }
        return size.toInt()
    }

    private fun BufferedWriter.appendCsv(values: List<String>) {
        appendLine(values.joinToString(",") { value ->
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        })
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                line[index] == '"' -> quoted = !quoted
                line[index] == ',' && !quoted -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(line[index])
            }
            index++
        }
        require(!quoted) { "Training history contains an unclosed quoted field." }
        result += current.toString()
        return result
    }

    private fun String.finiteDouble(label: String, zeroBasedLineIndex: Int): Double {
        val value = toDouble()
        require(value.isFinite()) { "$label is not finite on training-history row ${zeroBasedLineIndex + 2}." }
        return value
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun safeFileStem(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(80)
            .ifBlank { "feature-set" }

    private fun stableIdDigest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun requireSafeRunId(value: String) {
        require(SAFE_RUN_ID.matches(value) && value != "." && value != "..") {
            "Training run identifier is not storage-safe."
        }
    }

    companion object {
        private const val SUMMARY_SCHEMA_VERSION = 1
        private const val HISTORY_SCHEMA_VERSION = "1"
        private const val MODEL_SCHEMA_VERSION = 3
        private const val MODEL_MAGIC = "ROBOT_KINEMATICS_LOCAL_MODEL"
        private const val MAXIMUM_STORED_ARRAY_SIZE = 10_000_000
        private const val MAXIMUM_STORED_MODEL_BYTES = 64L * 1024L * 1024L
        private const val MAXIMUM_FEATURE_COUNT = 10_000
        private const val MAXIMUM_CLASS_COUNT = 1_024
        private const val MAXIMUM_HIDDEN_UNIT_COUNT = 65_536
        private const val MAXIMUM_STORED_VARIANTS = 10_000
        private const val MAXIMUM_SUMMARY_BYTES = 16L * 1024L * 1024L
        private const val SUMMARY_FILE_NAME = "summary.properties"
        private const val HISTORY_FILE_NAME = "iteration-history.csv"
        private val SAFE_RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
        private const val PATH_SEPARATOR = "\u001F"
        private const val LEGACY_HISTORY_COLUMN_COUNT = 14
        private const val HISTORY_COLUMN_COUNT = 16
        private const val LEGACY_HISTORY_HEADER =
            "schemaVersion,runId,globalIteration,featureProfile,candidateId,modelKind,hiddenUnits," +
                "epoch,trainingLoss,validationAccuracy,validationBalancedAccuracy,validationMacroF1," +
                "validationLogLoss,elapsedMillis"
        private const val HISTORY_HEADER =
            "schemaVersion,runId,globalIteration,featureProfile,candidateId,modelKind,hiddenUnits," +
                "epoch,trainingLoss,validationAccuracy,validationBalancedAccuracy,validationMacroF1," +
                "validationLogLoss,elapsedMillis,featureSelectionId,featureSelectionName"
    }
}
