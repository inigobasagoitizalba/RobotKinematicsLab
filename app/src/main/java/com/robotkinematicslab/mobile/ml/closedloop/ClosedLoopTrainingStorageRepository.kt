package com.robotkinematicslab.mobile.ml.closedloop

import android.content.Context
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.TrainingSplitStrategy
import com.robotkinematicslab.mobile.ml.model.TrainingModelKind
import com.robotkinematicslab.mobile.ml.training.LocalTrainingConfig
import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.BufferedWriter
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

data class ClosedLoopSessionSummary(
    val sessionId: String,
    val sessionName: String,
    val datasetName: String,
    val outcome: ClosedLoopOutcome,
    val phase: ClosedLoopPhase,
    val completedCycles: Int,
    val currentDatasetRows: Long,
    val bestMacroF1: Double?,
    val bestOracleDisagreementRate: Double?,
    val updatedAtEpochMillis: Long,
    val directoryPath: String,
    val statusMessage: String = "",
    val evaluationProfile: TrainingFeatureProfile? = null
)

class ClosedLoopTrainingStorageRepository(
    private val rootDirectory: File,
    private val beforeSummaryPublication: () -> Unit = {}
) : ClosedLoopSessionStore {

    constructor(context: Context) : this(
        AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData).closedLoopTrainingDirectory
    )

    @Synchronized
    override fun save(snapshot: ClosedLoopSessionSnapshot) {
        requireSafeSessionId(snapshot.sessionId)
        validateSnapshotEnvelope(snapshot)
        val directory = File(rootDirectory, snapshot.sessionId).apply { mkdirs() }
        val historyFile = File(directory, "history-${java.util.UUID.randomUUID()}.csv")
        writeHistoryAtomically(historyFile, snapshot.events)
        beforeSummaryPublication()
        writePropertiesAtomically(
            File(directory, SUMMARY_FILE),
            snapshot,
            snapshot.events.size,
            sha256(historyFile),
            historyFile.name
        )
    }

    fun listSessions(): List<ClosedLoopSessionSummary> {
        rootDirectory.mkdirs()
        return rootDirectory.listFiles(File::isDirectory)
            ?.mapNotNull { directory -> loadFromDirectory(directory)?.toSummary(directory) }
            ?.sortedByDescending { it.updatedAtEpochMillis }
            .orEmpty()
    }

    fun load(sessionId: String): ClosedLoopSessionSnapshot? =
        loadFromDirectory(File(rootDirectory, requireSafeSessionId(sessionId)))

    fun telemetryFile(sessionId: String): File =
        File(File(rootDirectory, requireSafeSessionId(sessionId)), TELEMETRY_FILE)

    @Synchronized
    fun saveTelemetry(sessionId: String, samples: List<DiagnosticPerformanceSample>): File {
        val file = telemetryFile(sessionId)
        AtomicFilePublisher.write(file) { temporary ->
            temporary.bufferedWriter().use { writer ->
                writer.appendLine(TELEMETRY_HEADER)
                samples.forEach { sample ->
                    writer.appendCsv(
                        listOf(
                        sample.sampleIndex.toString(),
                        sample.timestampMs.toString(),
                        sample.elapsedSeconds.toString(),
                        sample.completedRuns.toString(),
                        sample.totalRuns.toString(),
                        sample.remainingRuns.toString(),
                        sample.progressPercent.toString(),
                        sample.calculationsPerSecond.toString(),
                        sample.estimatedSecondsRemaining.toString(),
                        sample.phase.name,
                        sample.runtimeMemoryUsedMb.toString(),
                        sample.runtimeMemoryFreeMb.toString(),
                        sample.runtimeMemoryTotalMb.toString(),
                        sample.runtimeMemoryMaxMb.toString(),
                        sample.nativeHeapAllocatedMb.toString(),
                        sample.nativeHeapFreeMb.toString(),
                        sample.nativeHeapSizeMb.toString(),
                        sample.availableSystemMemoryMb.toString(),
                        sample.totalSystemMemoryMb.toString(),
                        sample.lowMemory?.toString().orEmpty(),
                        sample.cpuCoreCount.toString(),
                        sample.systemLoadAverage.toString(),
                        sample.processCpuTimeMs.toString(),
                        sample.currentThreadCpuTimeMs.toString(),
                        sample.readableCpuFrequencyCoreCount.toString(),
                        sample.cpuFrequencyMinMhz.toString(),
                        sample.cpuFrequencyAverageMhz.toString(),
                        sample.cpuFrequencyMaxMhz.toString(),
                        sample.gcCount.toString(),
                        sample.gcTimeMs.toString(),
                        sample.blockingGcCount.toString(),
                        sample.blockingGcTimeMs.toString(),
                        sample.batteryLevelPercent.toString(),
                        sample.batteryTemperatureCelsius.toString(),
                        sample.isCharging?.toString().orEmpty(),
                        sample.thermalStatus,
                        sample.socTemperatureCelsius.toString(),
                        sample.allocationHotspots.joinToString(separator = " | ") { hotspot ->
                            "${hotspot.label}:calls=${hotspot.callCount}:allocatedMb=${hotspot.totalPositiveDeltaMb}"
                        },
                            sample.note
                        )
                    )
                }
            }
        }
        return file
    }

    private fun writePropertiesAtomically(
        file: File,
        snapshot: ClosedLoopSessionSnapshot,
        historyEventCount: Int,
        historySha256: String,
        historyFileName: String
    ) {
        val config = snapshot.config
        val training = config.localTrainingConfig
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("sessionId", snapshot.sessionId)
                setProperty("sessionName", config.sessionName)
                setProperty("datasetName", config.datasetName)
                setProperty("phase", snapshot.phase.name)
                setProperty("outcome", snapshot.outcome.name)
                setProperty("startedAtEpochMillis", snapshot.startedAtEpochMillis.toString())
                setProperty("updatedAtEpochMillis", snapshot.updatedAtEpochMillis.toString())
                setProperty("currentDatasetRows", snapshot.currentDatasetRows.toString())
                setProperty("completedCycles", snapshot.completedCycles.toString())
                setProperty("currentTrainingAttempt", snapshot.currentTrainingAttempt.toString())
                setProperty("untrainedAppendedRows", snapshot.untrainedAppendedRows.toString())
                setProperty("checkpointProtocolVersion", snapshot.checkpointProtocolVersion.toString())
                setNullable("currentCorpusSha256", snapshot.currentCorpusSha256)
                setNullable("bestRunId", snapshot.bestRunId)
                setNullable("bestModelPath", snapshot.bestModelPath)
                setNullable("bestMacroF1", snapshot.bestMacroF1?.toString())
                setNullable("bestOracleDisagreementRate", snapshot.bestOracleDisagreementRate?.toString())
                setProperty("statusMessage", snapshot.statusMessage)
                setProperty("historyEventCount", historyEventCount.toString())
                setProperty("historySha256", historySha256)
                setProperty("historyFileName", historyFileName)

                setProperty("evaluationProfile", config.evaluationProfile.name)
                setProperty("maximumCycles", config.maximumCycles.toString())
                setProperty("samplesPerRobotIncrement", config.samplesPerRobotIncrement.toString())
                setProperty("maximumDatasetRows", config.maximumDatasetRows.toString())
                setProperty("maximumInferenceRetries", config.maximumInferenceRetries.toString())
                setProperty("maximumTrainingRetries", config.maximumTrainingRetries.toString())
                setProperty("minimumMacroF1", config.minimumMacroF1.toString())
                setProperty("maximumOracleDisagreementRate", config.maximumOracleDisagreementRate.toString())
                setProperty("minimumMacroF1Improvement", config.minimumMacroF1Improvement.toString())
                setProperty("maximumStagnantCycles", config.maximumStagnantCycles.toString())
                setProperty("maximumElapsedMinutes", config.maximumElapsedMinutes.toString())
                setProperty("automaticallyGrowDataset", config.automaticallyGrowDataset.toString())

                setProperty("training.runName", training.runName)
                setProperty("training.datasetPath", training.datasetPath)
                setProperty("training.compareFeatureProfiles", training.compareFeatureProfiles.toString())
                setProperty("training.singleFeatureProfile", training.singleFeatureProfile.name)
                setProperty("training.modelKind", training.modelKind.name)
                setProperty("training.resourceMode", training.resourceMode.name)
                setProperty("training.splitStrategy", training.splitStrategy.name)
                setProperty("training.maximumRows", training.maximumRows.toString())
                setProperty("training.epochs", training.epochs.toString())
                setProperty("training.batchSize", training.batchSize.toString())
                setProperty("training.learningRate", training.learningRate.toString())
                setProperty("training.l2Regularization", training.l2Regularization.toString())
                setProperty("training.hiddenUnits", training.hiddenUnits.toString())
                setProperty("training.randomSeed", training.randomSeed.toString())
                setProperty("training.earlyStoppingPatience", training.earlyStoppingPatience.toString())
                setProperty("training.workerCount", training.workerCount.toString())
                setProperty("training.sampleAcrossEntireDataset", training.sampleAcrossEntireDataset.toString())
                setProperty("training.featureSelections.count", training.featureSelections.size.toString())
                training.featureSelections.forEachIndexed { index, selection ->
                    setProperty("training.feature.$index.id", selection.id)
                    setProperty("training.feature.$index.name", selection.displayName)
                    setProperty("training.feature.$index.profile", selection.sourceProfile.name)
                    setProperty("training.feature.$index.columns", Base64.getEncoder().encodeToString(selection.includedFeatureNames.joinToString("\n").toByteArray(Charsets.UTF_8)))
                }

                snapshot.latestEvidence?.let { evidence ->
                    setProperty("latest.runId", evidence.runId)
                    setProperty("latest.profile", evidence.profile.name)
                    setProperty("latest.datasetRowsUsed", evidence.datasetRowsUsed.toString())
                    setProperty("latest.macroF1", evidence.macroF1.toString())
                    setProperty("latest.balancedAccuracy", evidence.balancedAccuracy.toString())
                    setProperty("latest.accuracy", evidence.accuracy.toString())
                    setProperty("latest.logLoss", evidence.logLoss.toString())
                    setProperty("latest.independentTestMacroF1", evidence.independentTestMacroF1.toString())
                    setProperty("latest.independentTestBalancedAccuracy", evidence.independentTestBalancedAccuracy.toString())
                    setProperty("latest.independentTestAccuracy", evidence.independentTestAccuracy.toString())
                    setProperty("latest.independentTestLogLoss", evidence.independentTestLogLoss.toString())
                    setProperty("latest.inferenceNanosPerSample", evidence.inferenceNanosPerSample.toString())
                    setProperty("latest.inferenceValid", evidence.inferenceValid.toString())
                    setProperty("latest.inferenceMessage", evidence.inferenceMessage)
                    setProperty("latest.modelPath", evidence.modelPath)
                    setProperty("latest.trainingDurationMillis", evidence.trainingDurationMillis.toString())
                    setProperty("latest.parameterCount", evidence.parameterCount.toString())
                    setNullable("latest.validationRows", evidence.validationRows?.toString())
                    setNullable("latest.reportingTestRows", evidence.reportingTestRows?.toString())
                    setNullable("latest.corpusSha256", evidence.corpusSha256)
                    setNullable("latest.modelSha256", evidence.modelSha256)
                    setNullable("latest.featureSelectionId", evidence.featureSelectionId)
                }
            }
        AtomicFilePublisher.write(file) { temporary ->
            temporary.outputStream().buffered().use { properties.store(it, "Closed-loop AI training session") }
        }
    }

    private fun writeHistoryAtomically(file: File, events: List<ClosedLoopEvent>) {
        AtomicFilePublisher.write(file) { temporary ->
            temporary.bufferedWriter().use { writer ->
                writer.appendLine(HISTORY_HEADER)
                events.forEach { event ->
                    writer.appendCsv(
                        listOf(
                        event.eventIndex.toString(),
                        event.timestampEpochMillis.toString(),
                        event.phase.name,
                        event.cycle.toString(),
                        event.datasetRows.toString(),
                        event.trainingAttempt.toString(),
                        event.runId.orEmpty(),
                        event.macroF1?.toString().orEmpty(),
                        event.oracleDisagreementRate?.toString().orEmpty(),
                            Base64.getEncoder().encodeToString(event.message.toByteArray(Charsets.UTF_8)),
                            Base64.getEncoder().encodeToString(event.modelPath.orEmpty().toByteArray(Charsets.UTF_8)),
                            Base64.getEncoder().encodeToString(event.datasetPath.orEmpty().toByteArray(Charsets.UTF_8)),
                            event.evaluationProfile?.name.orEmpty(), event.effectiveRows?.toString().orEmpty(), event.corpusSha256.orEmpty()
                        )
                    )
                }
            }
        }
    }

    private fun loadFromDirectory(directory: File): ClosedLoopSessionSnapshot? {
        val file = File(directory, SUMMARY_FILE)
        if (!file.exists()) return null
        return runCatching {
            require(directory.canonicalFile.parentFile == rootDirectory.canonicalFile)
            val properties = Properties().apply { file.inputStream().buffered().use(::load) }
            val schemaVersion = properties.getProperty("schemaVersion").toInt()
            require(schemaVersion in MINIMUM_SCHEMA_VERSION..SCHEMA_VERSION)
            val storedSessionId = requireSafeSessionId(properties.required("sessionId"))
            require(storedSessionId == directory.name) { "Closed-loop session identity differs from its directory." }
            val historyFile = File(directory, (properties.getProperty("historyFileName") ?: HISTORY_FILE).also { require(it.matches(Regex("[A-Za-z0-9._-]+")) && it.endsWith(".csv")) })
            if (schemaVersion >= INTEGRITY_SCHEMA_VERSION) {
                require(historyFile.isFile) { "Closed-loop history is missing." }
                require(properties.required("historyEventCount").toInt() >= 0)
                require(properties.required("historySha256") == sha256(historyFile)) {
                    "Closed-loop history does not match its published summary."
                }
            }
            val training =
                LocalTrainingConfig(
                    runName = properties.required("training.runName"),
                    datasetPath = properties.required("training.datasetPath"),
                    compareFeatureProfiles = properties.required("training.compareFeatureProfiles").toBooleanStrict(),
                    singleFeatureProfile = TrainingFeatureProfile.valueOf(properties.required("training.singleFeatureProfile")),
                    modelKind = TrainingModelKind.valueOf(properties.required("training.modelKind")),
                    resourceMode = TrainingResourceMode.valueOf(properties.required("training.resourceMode")),
                    splitStrategy = TrainingSplitStrategy.valueOf(properties.required("training.splitStrategy")),
                    maximumRows = properties.required("training.maximumRows").toInt(),
                    epochs = properties.required("training.epochs").toInt(),
                    batchSize = properties.required("training.batchSize").toInt(),
                    learningRate = properties.required("training.learningRate").toDouble(),
                    l2Regularization = properties.required("training.l2Regularization").toDouble(),
                    hiddenUnits = properties.required("training.hiddenUnits").toInt(),
                    randomSeed = properties.required("training.randomSeed").toInt(),
                    earlyStoppingPatience = properties.required("training.earlyStoppingPatience").toInt(),
                    workerCount = properties.getProperty("training.workerCount")?.toIntOrNull() ?: 1,
                    sampleAcrossEntireDataset =
                        properties.getProperty("training.sampleAcrossEntireDataset")?.toBooleanStrictOrNull() ?: false,
                    featureSelections = (0 until (properties.getProperty("training.featureSelections.count")?.toInt() ?: 0)).map { index ->
                        com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec(properties.required("training.feature.$index.id"),
                            properties.required("training.feature.$index.name"), TrainingFeatureProfile.valueOf(properties.required("training.feature.$index.profile")),
                            String(Base64.getDecoder().decode(properties.required("training.feature.$index.columns")), Charsets.UTF_8).split("\n"))
                    }
                )
            val config =
                ClosedLoopTrainingConfig(
                    sessionName = properties.required("sessionName"),
                    datasetName = properties.required("datasetName"),
                    localTrainingConfig = training,
                    evaluationProfile = TrainingFeatureProfile.valueOf(properties.required("evaluationProfile")),
                    maximumCycles = properties.required("maximumCycles").toInt(),
                    samplesPerRobotIncrement = properties.required("samplesPerRobotIncrement").toInt(),
                    maximumDatasetRows = properties.required("maximumDatasetRows").toLong(),
                    maximumInferenceRetries = properties.required("maximumInferenceRetries").toInt(),
                    maximumTrainingRetries = properties.required("maximumTrainingRetries").toInt(),
                    minimumMacroF1 = properties.required("minimumMacroF1").toDouble(),
                    maximumOracleDisagreementRate = properties.required("maximumOracleDisagreementRate").toDouble(),
                    minimumMacroF1Improvement = properties.required("minimumMacroF1Improvement").toDouble(),
                    maximumStagnantCycles = properties.required("maximumStagnantCycles").toInt(),
                    maximumElapsedMinutes = properties.required("maximumElapsedMinutes").toInt(),
                    automaticallyGrowDataset = properties.required("automaticallyGrowDataset").toBooleanStrict()
                )
            val snapshot = ClosedLoopSessionSnapshot(
                sessionId = storedSessionId,
                config = config,
                phase = ClosedLoopPhase.valueOf(properties.required("phase")),
                outcome = ClosedLoopOutcome.valueOf(properties.required("outcome")),
                startedAtEpochMillis = properties.required("startedAtEpochMillis").toLong(),
                updatedAtEpochMillis = properties.required("updatedAtEpochMillis").toLong(),
                currentDatasetRows = properties.required("currentDatasetRows").toLong(),
                completedCycles = properties.required("completedCycles").toInt(),
                currentTrainingAttempt = properties.required("currentTrainingAttempt").toInt(),
                bestRunId = properties.optional("bestRunId"),
                bestModelPath = properties.optional("bestModelPath"),
                bestMacroF1 = properties.optional("bestMacroF1")?.toDouble(),
                bestOracleDisagreementRate = properties.optional("bestOracleDisagreementRate")?.toDouble(),
                latestEvidence = readEvidence(properties),
                statusMessage = properties.required("statusMessage"),
                events = readHistory(historyFile),
                untrainedAppendedRows = properties.getProperty("untrainedAppendedRows")?.toIntOrNull() ?: 0,
                checkpointProtocolVersion = properties.getProperty("checkpointProtocolVersion")?.toInt() ?: 0,
                currentCorpusSha256 = properties.optional("currentCorpusSha256")
            )
            validateSnapshotEnvelope(snapshot)
            if (schemaVersion >= INTEGRITY_SCHEMA_VERSION) {
                require(snapshot.events.size == properties.required("historyEventCount").toInt()) {
                    "Closed-loop history count differs from its published summary."
                }
            }
            snapshot
        }.getOrNull()
    }

    private fun readEvidence(properties: Properties): ClosedLoopCycleEvidence? {
        val runId = properties.optional("latest.runId") ?: return null
        return ClosedLoopCycleEvidence(
            runId = runId,
            profile = TrainingFeatureProfile.valueOf(properties.required("latest.profile")),
            datasetRowsUsed = properties.required("latest.datasetRowsUsed").toInt(),
            macroF1 = properties.required("latest.macroF1").toDouble(),
            balancedAccuracy = properties.required("latest.balancedAccuracy").toDouble(),
            accuracy = properties.required("latest.accuracy").toDouble(),
            logLoss = properties.required("latest.logLoss").toDouble(),
            independentTestMacroF1 = properties.required("latest.independentTestMacroF1").toDouble(),
            independentTestBalancedAccuracy = properties.required("latest.independentTestBalancedAccuracy").toDouble(),
            independentTestAccuracy = properties.required("latest.independentTestAccuracy").toDouble(),
            independentTestLogLoss = properties.required("latest.independentTestLogLoss").toDouble(),
            inferenceNanosPerSample = properties.required("latest.inferenceNanosPerSample").toDouble(),
            inferenceValid = properties.required("latest.inferenceValid").toBooleanStrict(),
            inferenceMessage = properties.required("latest.inferenceMessage"),
            modelPath = properties.required("latest.modelPath"),
            trainingDurationMillis = properties.required("latest.trainingDurationMillis").toLong(),
            parameterCount = properties.required("latest.parameterCount").toInt(),
            validationRows = properties.optional("latest.validationRows")?.toInt(),
            reportingTestRows = properties.optional("latest.reportingTestRows")?.toInt(),
            corpusSha256 = properties.optional("latest.corpusSha256"),
            modelSha256 = properties.optional("latest.modelSha256"),
            featureSelectionId = properties.optional("latest.featureSelectionId")
        )
    }

    private fun readHistory(file: File): List<ClosedLoopEvent> {
        if (!file.exists()) return emptyList()
        return file.bufferedReader().use { reader ->
            val header = reader.readLine()
            require(header == HISTORY_HEADER || header == LEGACY_HISTORY_HEADER) { "Unsupported closed-loop history schema." }
            val columnCount = if(header == HISTORY_HEADER) 15 else 10
            reader.lineSequence().filter(String::isNotBlank).mapIndexed { index, line ->
                val values = parseCsvLine(line)
                require(values.size == columnCount) {
                    "Closed-loop history row ${index + 2} has an invalid column count."
                }
                ClosedLoopEvent(
                        eventIndex = values[0].toInt(),
                        timestampEpochMillis = values[1].toLong(),
                        phase = ClosedLoopPhase.valueOf(values[2]),
                        cycle = values[3].toInt(),
                        datasetRows = values[4].toLong(),
                        trainingAttempt = values[5].toInt(),
                        runId = values[6].ifBlank { null },
                        macroF1 = values[7].toDoubleOrNull(),
                        oracleDisagreementRate = values[8].toDoubleOrNull(),
                        message = String(Base64.getDecoder().decode(values[9]), Charsets.UTF_8),
                        modelPath = if(columnCount == 15) String(Base64.getDecoder().decode(values[10]), Charsets.UTF_8).ifBlank { null } else null,
                        datasetPath = if(columnCount == 15) String(Base64.getDecoder().decode(values[11]), Charsets.UTF_8).ifBlank { null } else null,
                        evaluationProfile = if(columnCount == 15) values[12].takeIf(String::isNotBlank)?.let(TrainingFeatureProfile::valueOf) else null,
                        effectiveRows = if(columnCount == 15) values[13].takeIf(String::isNotBlank)?.toInt() else null,
                        corpusSha256 = if(columnCount == 15) values[14].ifBlank { null } else null
                    ).also { event ->
                        require(event.eventIndex >= 0 && event.timestampEpochMillis > 0L)
                        require(event.cycle >= 0 && event.datasetRows >= 0L && event.trainingAttempt >= 0)
                        require(event.effectiveRows == null || event.effectiveRows >= 0)
                        event.corpusSha256?.let { require(it.matches(Regex("[0-9a-f]{64}"))) }
                        require(event.macroF1 == null || event.macroF1.isFinite() && event.macroF1 in 0.0..1.0)
                        require(
                            event.oracleDisagreementRate == null ||
                                event.oracleDisagreementRate.isFinite() && event.oracleDisagreementRate in 0.0..1.0
                        )
                    }
            }.toList()
        }
    }

    private fun ClosedLoopSessionSnapshot.toSummary(directory: File): ClosedLoopSessionSummary =
        ClosedLoopSessionSummary(
            sessionId = sessionId,
            sessionName = config.sessionName,
            datasetName = config.datasetName,
            outcome = outcome,
            phase = phase,
            completedCycles = completedCycles,
            currentDatasetRows = currentDatasetRows,
            bestMacroF1 = bestMacroF1,
            bestOracleDisagreementRate = bestOracleDisagreementRate,
            updatedAtEpochMillis = updatedAtEpochMillis,
            directoryPath = directory.absolutePath,
            statusMessage = statusMessage,
            evaluationProfile = config.evaluationProfile
        )

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "Missing closed-loop session property: $key" }

    private fun Properties.optional(key: String): String? = getProperty(key)?.takeIf(String::isNotBlank)

    private fun Properties.setNullable(key: String, value: String?) {
        if (value != null) setProperty(key, value)
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
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    value.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    values += value.toString()
                    value.clear()
                }
                else -> value.append(character)
            }
            index++
        }
        values += value.toString()
        require(!quoted) { "Closed-loop history contains an unclosed quoted field." }
        return values
    }

    private fun validateSnapshotEnvelope(snapshot: ClosedLoopSessionSnapshot) {
        require(snapshot.checkpointProtocolVersion in 0..1)
        snapshot.currentCorpusSha256?.let { require(it.matches(Regex("[0-9a-f]{64}"))) }
        if(snapshot.outcome == ClosedLoopOutcome.ACCEPTED) {
            require(snapshot.phase == ClosedLoopPhase.COMPLETED && snapshot.completedCycles > 0)
            require(ClosedLoopAcceptanceGate.evaluate(snapshot.config, requireNotNull(snapshot.latestEvidence)).accepted) {
                "An accepted checkpoint must contain a matching successful validation decision."
            }
        }
        require(snapshot.startedAtEpochMillis > 0L && snapshot.updatedAtEpochMillis >= snapshot.startedAtEpochMillis)
        require(snapshot.currentDatasetRows >= 0L && snapshot.completedCycles >= 0 && snapshot.currentTrainingAttempt >= 0)
        require(snapshot.untrainedAppendedRows >= 0 && snapshot.untrainedAppendedRows.toLong() <= snapshot.currentDatasetRows) {
            "Pending appended-row evidence is inconsistent with the current dataset size."
        }
        require(snapshot.bestMacroF1 == null || snapshot.bestMacroF1.isFinite() && snapshot.bestMacroF1 in 0.0..1.0)
        require(
            snapshot.bestOracleDisagreementRate == null ||
                snapshot.bestOracleDisagreementRate.isFinite() && snapshot.bestOracleDisagreementRate in 0.0..1.0
        )
        snapshot.latestEvidence?.let { evidence ->
            require(evidence.datasetRowsUsed >= 0 && evidence.trainingDurationMillis >= 0L && evidence.parameterCount > 0)
            listOf(
                evidence.macroF1,
                evidence.balancedAccuracy,
                evidence.accuracy,
                evidence.independentTestMacroF1,
                evidence.independentTestBalancedAccuracy,
                evidence.independentTestAccuracy
            ).forEach { value -> require(value.isFinite() && value in 0.0..1.0) }
            require(evidence.logLoss.isFinite() && evidence.logLoss >= 0.0)
            require(evidence.independentTestLogLoss.isFinite() && evidence.independentTestLogLoss >= 0.0)
            require(evidence.inferenceNanosPerSample.isFinite() && evidence.inferenceNanosPerSample >= 0.0)
        }
        require(snapshot.events.map(ClosedLoopEvent::eventIndex).distinct().size == snapshot.events.size) {
            "Closed-loop event indexes must be unique."
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").also { digest ->
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun requireSafeSessionId(value: String): String {
        require(SAFE_SESSION_ID.matches(value) && value != "." && value != "..") {
            "Closed-loop session identifier is not storage-safe."
        }
        return value
    }

    companion object {
        private const val SCHEMA_VERSION = 2
        private const val MINIMUM_SCHEMA_VERSION = 1
        private const val INTEGRITY_SCHEMA_VERSION = 2
        private const val SUMMARY_FILE = "session.properties"
        private const val HISTORY_FILE = "closed-loop-history.csv"
        private const val TELEMETRY_FILE = "resource-telemetry.csv"
        private const val HISTORY_COLUMN_COUNT = 10
        private val SAFE_SESSION_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")
        private const val LEGACY_HISTORY_HEADER =
            "eventIndex,timestampEpochMillis,phase,cycle,datasetRows,trainingAttempt,runId," +
                "macroF1,oracleDisagreementRate,messageBase64"
        private const val HISTORY_HEADER = LEGACY_HISTORY_HEADER + ",modelPathBase64,datasetPathBase64,evaluationProfile,effectiveRows,corpusSha256"
        private const val TELEMETRY_HEADER =
            "sampleIndex,timestampMs,elapsedSeconds,completedWork,totalWork,remainingWork,progressPercent," +
                "workPerSecond,estimatedSecondsRemaining,phase,runtimeMemoryUsedMb,runtimeMemoryFreeMb,runtimeMemoryTotalMb," +
                "runtimeMemoryMaxMb,nativeHeapAllocatedMb,nativeHeapFreeMb,nativeHeapSizeMb," +
                "availableSystemMemoryMb,totalSystemMemoryMb,lowMemory,cpuCoreCount,systemLoadAverage," +
                "processCpuTimeMs,currentThreadCpuTimeMs,readableCpuFrequencyCoreCount,cpuFrequencyMinMhz," +
                "cpuFrequencyAverageMhz,cpuFrequencyMaxMhz,gcCount,gcTimeMs,blockingGcCount,blockingGcTimeMs," +
                "batteryLevelPercent,batteryTemperatureCelsius,isCharging,thermalStatus,socTemperatureCelsius," +
                "allocationHotspots,note"
    }
}
