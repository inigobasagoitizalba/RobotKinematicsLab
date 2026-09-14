package com.robotkinematicslab.mobile.ml.ik

import android.content.Context
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class OneMicronIkStorageRepository(
    private val trainingDirectory: File,
    private val modelsDirectory: File
) {
    constructor(context: Context) : this(pathsFor(context))

    private constructor(paths: AppStoragePaths) : this(
        paths.oneMicronTrainingDirectory,
        paths.oneMicronModelsDirectory
    )

    @Synchronized
    fun save(result: OneMicronIkTrainingResult): OneMicronIkTrainingResult {
        requireSafeRunId(result.runId)
        require(result.startedAtEpochMillis > 0L && result.finishedAtEpochMillis >= result.startedAtEpochMillis) {
            "One-micron training timestamps are invalid."
        }
        require(trainingDirectory.mkdirs() || trainingDirectory.isDirectory) { "One-micron run storage is unavailable." }
        require(modelsDirectory.mkdirs() || modelsDirectory.isDirectory) { "One-micron model storage is unavailable." }
        val runDirectory = File(trainingDirectory, result.runId)
        val modelFile = File(modelsDirectory, "${result.runId}.rkl-micron")
        if (runDirectory.isDirectory && !modelFile.exists()) {
            quarantineInterruptedRun(runDirectory)
        }
        require(!runDirectory.exists() && !modelFile.exists()) {
            "One-micron training run already exists: ${result.runId}"
        }
        val stagingDirectory = Files.createTempDirectory(trainingDirectory.toPath(), ".${result.runId}-").toFile()
        val reportFile = File(runDirectory, "one-micron-report.properties")
        val historyFile = File(runDirectory, "learning-curve.csv")
        val saved = result.copy(modelPath = modelFile.absolutePath, reportPath = reportFile.absolutePath)
        var publishedRunDirectory = false
        try {
            writeHistory(File(stagingDirectory, historyFile.name), saved)
            writeVerificationObservations(File(stagingDirectory, "verification-cases.csv"), saved)
            writeReport(File(stagingDirectory, reportFile.name), saved, historyFile)
            moveWithoutReplacing(stagingDirectory, runDirectory)
            publishedRunDirectory = true
            // The model file is the publication marker used by listModelFiles(). Writing it last
            // prevents a partially persisted run from appearing as usable after an interrupted save.
            writeModel(modelFile, saved)
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            if (publishedRunDirectory && !modelFile.exists()) runDirectory.deleteRecursively()
            throw failure
        }
        return saved
    }

    fun loadModel(file: File): StoredOneMicronIkModel {
        require(file.isFile && file.length() in 1..MAXIMUM_STORED_MODEL_BYTES) {
            "Stored one-micron model file size is invalid."
        }
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readUTF() == MAGIC) { "Not a Robot Kinematics Lab one-micron model." }
            require(input.readInt() == SCHEMA)
            val runId = input.readUTF()
            requireSafeRunId(runId)
            val profile = OneMicronIkFeatureProfile.valueOf(input.readUTF())
            val config = IKConfig(input.readInt(), input.readDouble(), input.readDouble(), input.readDouble())
            val completeNames = OneMicronIkFeatureEncoder.featureNames(profile)
            val nameCount = input.readInt()
            require(nameCount in 1..completeNames.size) { "Stored one-micron feature count is invalid." }
            val names = List(nameCount) { input.readUTF() }
            require(names.distinct().size == names.size && names.all(completeNames::contains)) {
                "Stored one-micron feature schema is invalid."
            }
            val normalization =
                FeatureNormalization(
                    input.readFloats(nameCount, "normalization means"),
                    input.readFloats(nameCount, "normalization deviations")
                )
            val inputCount = input.readInt()
            val hiddenCount = input.readInt()
            val outputCount = input.readInt()
            require(inputCount == nameCount) { "Stored one-micron input size does not match its feature schema." }
            require(hiddenCount in 1..MAXIMUM_HIDDEN_UNIT_COUNT) { "Stored one-micron hidden size is invalid." }
            require(outputCount == ONE_MICRON_MAX_JOINTS) { "Stored one-micron output size is invalid." }
            val model =
                LocalIkRegressionModel(
                    inputCount,
                    hiddenCount,
                    outputCount,
                    input.readFloats(checkedArrayProduct(inputCount, hiddenCount, "input weights"), "input weights"),
                    input.readFloats(hiddenCount, "hidden biases"),
                    input.readFloats(checkedArrayProduct(hiddenCount, outputCount, "output weights"), "output weights"),
                    input.readFloats(outputCount, "output biases")
                )
            require(input.read() == -1) { "Unexpected trailing data in one-micron model." }
            return StoredOneMicronIkModel(runId, profile, config, names, normalization, model)
        }
    }

    fun listModelFiles(): List<File> =
        modelsDirectory.apply(File::mkdirs).listFiles { file -> file.isFile && file.extension == "rkl-micron" }
            ?.sortedByDescending(File::lastModified).orEmpty()

    private fun writeModel(file: File, result: OneMicronIkTrainingResult) {
        val parent = requireNotNull(file.parentFile) { "One-micron model requires a parent directory." }
        val temporary = Files.createTempFile(parent.toPath(), ".${file.name}-", ".tmp").toFile()
        try {
            DataOutputStream(FileOutputStream(temporary).buffered()).use { output ->
                output.writeUTF(MAGIC)
                output.writeInt(SCHEMA)
                output.writeUTF(result.runId)
                output.writeUTF(result.config.resolvedFeatureSelection.sourceProfile.name)
                output.writeInt(result.config.solverConfig.maxIterations)
                output.writeDouble(result.config.solverConfig.tolerance)
                output.writeDouble(result.config.solverConfig.damping)
                output.writeDouble(result.config.solverConfig.maxStep)
                output.writeInt(result.featureNames.size)
                result.featureNames.forEach(output::writeUTF)
                output.writeFloats(result.normalization.means)
                output.writeFloats(result.normalization.standardDeviations)
                output.writeInt(result.model.inputFeatureCount)
                output.writeInt(result.model.hiddenUnitCount)
                output.writeInt(result.model.outputCount)
                output.writeFloats(result.model.inputWeights)
                output.writeFloats(result.model.hiddenBiases)
                output.writeFloats(result.model.outputWeights)
                output.writeFloats(result.model.outputBiases)
            }
            moveWithoutReplacing(temporary, file)
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun writeReport(file: File, result: OneMicronIkTrainingResult, historyFile: File) {
        val metrics = result.verification
        val properties = Properties().apply {
            setProperty("schemaVersion", SCHEMA.toString())
            setProperty("runId", result.runId)
            setProperty("runName", result.config.runName)
            setProperty("profile", result.config.profile.name)
            setProperty("featureSelectionId", result.config.resolvedFeatureSelection.id)
            setProperty("featureSelectionName", result.config.resolvedFeatureSelection.displayName)
            setProperty("featureCount", result.featureNames.size.toString())
            setProperty("orderedFeatureNames", result.featureNames.joinToString(","))
            setProperty("datasetPath", result.config.datasetPath)
            setProperty("datasetScientificFingerprint", result.config.datasetScientificFingerprint.orEmpty())
            setProperty("datasetRobotIds", result.config.datasetRobotIds.joinToString(","))
            setProperty("sourceDatasetRows", result.config.sourceDatasetRows.toString())
            setProperty("requestedMaximumRows", result.config.requestedMaximumRows.toString())
            setProperty("effectiveMaximumRows", result.config.maximumRows.toString())
            setProperty("splitStrategy", result.config.splitStrategy.name)
            setProperty("epochsRequested", result.config.epochs.toString())
            setProperty("batchSize", result.config.batchSize.toString())
            setProperty("hiddenUnits", result.config.hiddenUnits.toString())
            setProperty("learningRate", result.config.learningRate.toString())
            setProperty("l2Regularization", result.config.l2Regularization.toString())
            setProperty("randomSeed", result.config.randomSeed.toString())
            setProperty("earlyStoppingPatience", result.config.earlyStoppingPatience.toString())
            setProperty("requestedWorkers", result.config.workerCount.toString())
            setProperty("workingMemoryBudgetBytes", result.config.maximumWorkingMemoryBytes.toString())
            setProperty("untouchedCasesRequested", result.config.verificationSampleLimit.toString())
            setProperty("solverMaxIterations", result.config.solverConfig.maxIterations.toString())
            setProperty("solverToleranceMeters", result.config.solverConfig.tolerance.toString())
            setProperty("solverDamping", result.config.solverConfig.damping.toString())
            setProperty("solverMaximumStep", result.config.solverConfig.maxStep.toString())
            setProperty("certifiedToleranceMeters", metrics.certifiedToleranceMeters.toString())
            setProperty("trainRows", result.trainRows.toString())
            setProperty("validationRows", result.validationRows.toString())
            setProperty("testRows", result.testRows.toString())
            setProperty("bestEpoch", result.bestEpoch.toString())
            setProperty("rawNeuralSuccessRate", metrics.rawNeuralSuccessRate.toString())
            setProperty("directNeuralSuccessRate", metrics.directNeuralSuccessRate.toString())
            setProperty("refinedOnlySuccessRate", metrics.refinedOnlySuccessRate.toString())
            setProperty("fallbackOnlySuccessRate", metrics.fallbackOnlySuccessRate.toString())
            setProperty("failedPipelineRate", metrics.failedPipelineRate.toString())
            setProperty("pureSolverSuccessRate", metrics.pureSolverSuccessRate.toString())
            setProperty("verificationSampleCount", metrics.samples.toString())
            setProperty("verificationCasesPath", File(historyFile.parentFile, "verification-cases.csv").absolutePath)
            setProperty("pipelineRouteDenominator", "all verified test cases; mutually exclusive routes")
            setProperty("pureSolverDenominator", "all verified test cases; independent comparator")
            setProperty("neuralThenRefineSuccessRate", metrics.neuralThenRefineSuccessRate.toString())
            setProperty("deterministicBaselineSuccessRate", metrics.deterministicBaselineSuccessRate.toString())
            setProperty("rawMedianErrorMeters", metrics.rawMedianErrorMeters.toString())
            setProperty("rawP95ErrorMeters", metrics.rawP95ErrorMeters.toString())
            setProperty("meanRefinedIterations", metrics.meanRefinedIterations.toString())
            setProperty("meanBaselineIterations", metrics.meanBaselineIterations.toString())
            setProperty("meanNeuralInferenceNanos", metrics.meanNeuralInferenceNanos.toString())
            setProperty("verifiedPipelineSuccessRate", metrics.verifiedPipelineSuccessRate.toString())
            setProperty("rawCumulativeErrorMeters", metrics.rawCumulativeErrorMeters.toString())
            setProperty("protectedCumulativeErrorMeters", metrics.protectedCumulativeErrorMeters.toString())
            setProperty("cumulativeErrorAvoidedPercent", metrics.cumulativeErrorAvoidedPercent.toString())
            setProperty("meanPipelineIterations", metrics.meanPipelineIterations.toString())
            setProperty("iterationSavingsPercent", metrics.iterationSavingsPercent.toString())
            setProperty("modelPath", result.modelPath)
            setProperty("learningCurvePath", historyFile.absolutePath)
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().buffered().use { properties.store(it, "Verified one-micron neural IK run") }
        moveReplacing(temporary, file)
    }

    private fun writeVerificationObservations(file: File, result: OneMicronIkTrainingResult) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("schema_version,source_row,robot_fingerprint,pipeline_path,raw_residual_m,refined_residual_m,pure_solver_residual_m,final_pipeline_residual_m,pure_solver_certified,refinement_iterations,fallback_iterations,pure_solver_iterations")
            result.verification.observations.forEach { row ->
                writer.appendLine(listOf(1, row.sourceRowIndex, row.robotFingerprint, row.path.name,
                    row.rawResidualMeters, row.refinedResidualMeters, row.pureSolverResidualMeters,
                    row.finalPipelineResidualMeters, row.pureSolverCertified, row.refinementIterations,
                    row.fallbackIterations, row.pureSolverIterations).joinToString(","))
            }
        }
    }

    private fun writeHistory(file: File, result: OneMicronIkTrainingResult) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.bufferedWriter().use { writer ->
            writer.appendLine("schema_version,run_id,epoch,training_masked_mse,validation_masked_mse,elapsed_ms")
            result.epochs.forEach { epoch ->
                writer.appendLine(
                    listOf(
                        SCHEMA,
                        result.runId,
                        epoch.epoch,
                        epoch.trainingLoss,
                        epoch.validationLoss,
                        epoch.elapsedMillis
                    ).joinToString(",")
                )
            }
        }
        moveReplacing(temporary, file)
    }

    private fun DataOutputStream.writeFloats(values: FloatArray) {
        writeInt(values.size)
        values.forEach(::writeFloat)
    }

    private fun DataInputStream.readFloats(expectedSize: Int, label: String): FloatArray {
        val size = readInt()
        require(size == expectedSize && size in 0..MAXIMUM_STORED_ARRAY_SIZE) {
            "Stored one-micron $label size is invalid."
        }
        return FloatArray(size) { readFloat() }
    }

    private fun checkedArrayProduct(first: Int, second: Int, label: String): Int {
        val size = first.toLong() * second.toLong()
        require(size in 0..MAXIMUM_STORED_ARRAY_SIZE.toLong()) {
            "Stored one-micron $label size is invalid."
        }
        return size.toInt()
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

    private fun moveWithoutReplacing(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath())
    }

    private fun quarantineInterruptedRun(runDirectory: File) {
        val quarantine =
            Files.createTempDirectory(
                trainingDirectory.toPath(),
                ".${runDirectory.name}-interrupted-"
            ).toFile()
        check(quarantine.delete()) { "Could not reserve interrupted-run quarantine storage." }
        moveWithoutReplacing(runDirectory, quarantine)
    }

    private fun requireSafeRunId(runId: String) {
        require(SAFE_RUN_ID.matches(runId) && runId != "." && runId != "..") {
            "One-micron run identifier is not storage-safe."
        }
    }

    companion object {
        private const val MAGIC = "RKL_VERIFIED_MICRON_IK"
        private const val SCHEMA = 1
        private const val MAXIMUM_STORED_ARRAY_SIZE = 10_000_000
        private const val MAXIMUM_STORED_MODEL_BYTES = 64L * 1024L * 1024L
        private const val MAXIMUM_HIDDEN_UNIT_COUNT = 65_536
        private val SAFE_RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")

        private fun pathsFor(context: Context): AppStoragePaths =
            AppStoragePaths(context).also { it.ensureStructureAndMigrateLegacyData() }
    }
}
