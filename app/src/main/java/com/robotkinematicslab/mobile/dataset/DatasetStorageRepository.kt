package com.robotkinematicslab.mobile.dataset

import android.content.Context
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import com.robotkinematicslab.mobile.provenance.AppBuildIdentity
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

data class DatasetAppendTarget(
    val manifest: DatasetManifest?,
    val csvFile: File,
    val existingRowCount: Long
)

data class CorruptDatasetManifest(
    val fileName: String,
    val manifestPath: String,
    val datasetName: String?,
    val referencedCsvPath: String?,
    val reason: String,
    val lastModifiedEpochMillis: Long
)

data class DatasetManifestIndex(
    val validManifests: List<DatasetManifest>,
    val corruptManifests: List<CorruptDatasetManifest>
) {
    val totalEntryCount: Int
        get() = validManifests.size + corruptManifests.size
}

class DatasetStorageRepository(
    private val rootDirectory: File,
    private val buildIdentity: AppBuildIdentity = AppBuildIdentity.UNKNOWN
) {

    constructor(context: Context) : this(
        rootDirectory =
            AppStoragePaths(context).also {
                it.ensureStructureAndMigrateLegacyData()
            }.datasetsDirectory,
        buildIdentity = AppBuildIdentity.current()
    )

    fun outputDirectory(): File {
        rootDirectory.mkdirs()
        return rootDirectory
    }

    fun resolveCsvFile(datasetName: String): File {
        loadManifest(datasetName)?.let { existing -> return File(existing.csvPath) }
        return File(
            outputDirectory(),
            "${storageFileStem(datasetName)}.csv"
        )
    }

    fun loadManifest(datasetName: String): DatasetManifest? {
        val normalizedName = datasetName.trim()
        return manifestCandidates(normalizedName)
            .asSequence()
            .mapNotNull(::readManifest)
            .firstOrNull { manifest -> manifest.datasetName == normalizedName }
    }

    fun listManifests(): List<DatasetManifest> {
        return manifestFiles()
            .mapNotNull(::readManifest)
            .sortedByDescending(DatasetManifest::lastUpdatedEpochMillis)
    }

    /**
     * Keeps unreadable manifests visible to Storage without admitting them to dataset consumers.
     * Inspection is read-only: neither the manifest nor its referenced CSV is changed here.
     */
    fun inspectManifestIndex(): DatasetManifestIndex {
        val valid = mutableListOf<DatasetManifest>()
        val corrupt = mutableListOf<CorruptDatasetManifest>()
        manifestFiles().forEach { file ->
            val manifest = readManifest(file)
            if (manifest != null) {
                valid += manifest
            } else {
                corrupt += inspectCorruptManifest(file)
            }
        }
        return DatasetManifestIndex(
            validManifests = valid.sortedByDescending(DatasetManifest::lastUpdatedEpochMillis),
            corruptManifests = corrupt.sortedByDescending(CorruptDatasetManifest::lastModifiedEpochMillis)
        )
    }

    /** Moves only a still-invalid manifest into a recoverable subdirectory; CSV data is untouched. */
    fun quarantineCorruptManifest(entry: CorruptDatasetManifest): File {
        val managedDirectory = outputDirectory().canonicalFile
        val source = File(entry.manifestPath).canonicalFile
        require(source.parentFile == managedDirectory && source.name == entry.fileName) {
            "Only a manifest from the managed dataset directory can be quarantined."
        }
        check(source.isFile) { "The damaged dataset manifest no longer exists." }
        check(readManifest(source) == null) {
            "The dataset manifest is valid now and must not be quarantined."
        }

        val quarantineDirectory = File(managedDirectory, CORRUPT_MANIFEST_DIRECTORY_NAME).apply { mkdirs() }
        check(quarantineDirectory.isDirectory) { "Could not create the manifest quarantine directory." }
        val destination = uniqueQuarantineDestination(quarantineDirectory, source.name)
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
        return destination
    }

    fun countCsvDataRows(file: File): Long {
        if (!file.exists() || file.length() == 0L) {
            return 0L
        }

        return file.bufferedReader().useLines { lines ->
            (lines.count() - 1L).coerceAtLeast(0L)
        }
    }

    /**
     * Resolves an append target only after proving that its CSV and manifest describe the same
     * dataset. A completely new name is allowed, but a partial, unreadable, or inconsistent
     * stored dataset is never treated as new data: callers must repair or rename it first.
     */
    fun resolveAppendOrCreateTarget(datasetName: String): DatasetAppendTarget {
        val normalizedName = datasetName.trim()
        require(normalizedName.isNotBlank()) { "Dataset name must not be blank." }

        val candidates = manifestCandidates(normalizedName)
        val manifest = loadManifest(normalizedName)
        if (manifest == null) {
            val invalidOwnedManifest =
                candidates.firstOrNull { candidate ->
                    if (!candidate.exists()) {
                        false
                    } else {
                        val storedName = readStoredDatasetName(candidate)
                        storedName == null || storedName == normalizedName
                    }
                }
            check(invalidOwnedManifest == null) {
                "A dataset manifest with this name exists but is unreadable or invalid. Append was blocked to protect scientific provenance."
            }

            val csvFile = resolveCsvFile(normalizedName)
            check(!csvFile.exists()) {
                "A CSV with this name exists without a valid manifest. Append was blocked to protect scientific provenance."
            }
            return DatasetAppendTarget(
                manifest = null,
                csvFile = csvFile,
                existingRowCount = 0L
            )
        }

        check(manifest.hasCompleteBatchProvenance) {
            "The saved dataset manifest has incomplete or inconsistent batch provenance. Append was blocked."
        }
        val csvFile = File(manifest.csvPath)
        check(csvFile.isFile) {
            "The saved dataset manifest exists, but its CSV file is missing. Append was blocked."
        }
        val expectedHeader = ScientificDatasetCsvWriter.HEADER.joinToString(",")
        val actualHeader = csvFile.bufferedReader().use { reader -> reader.readLine() }
        check(actualHeader == expectedHeader) {
            "The saved dataset CSV uses a different or invalid schema. Append was blocked."
        }
        val actualRows = countCsvDataRows(csvFile)
        check(actualRows == manifest.rowCount) {
            "The CSV contains $actualRows rows but its manifest records ${manifest.rowCount}. Append was blocked."
        }
        return DatasetAppendTarget(
            manifest = manifest,
            csvFile = csvFile,
            existingRowCount = actualRows
        )
    }

    fun saveManifest(manifest: DatasetManifest) {
        val file =
            manifestCandidates(manifest.datasetName)
                .firstOrNull { candidate ->
                    readStoredDatasetName(candidate) == manifest.datasetName.trim()
                }
                ?: File(outputDirectory(), "${storageFileStem(manifest.datasetName)}.properties")

        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                manifest.scientificFingerprint?.let { setProperty("scientificFingerprint", it) }
                setProperty("producerApplicationId", buildIdentity.applicationId)
                setProperty("producerVersionName", buildIdentity.versionName)
                setProperty("producerVersionCode", buildIdentity.versionCode.toString())
                setProperty("producerBuildType", buildIdentity.buildType)
                setProperty("datasetName", manifest.datasetName)
                setProperty("csvPath", manifest.csvPath)
                setProperty("rowCount", manifest.rowCount.toString())
                setProperty("generationCount", manifest.generationCount.toString())
                setProperty("robotIds", manifest.robotIds.joinToString(UNIT_SEPARATOR))
                setProperty("samplesPerRobotLastRun", manifest.samplesPerRobotLastRun.toString())
                setProperty("randomSeed", manifest.randomSeed.toString())
                setProperty("randomProtocol", manifest.randomProtocol)
                setProperty("targetMode", manifest.targetMode.name)
                setProperty("reachableFraction", manifest.reachableFraction.toString())
                setProperty("filterMode", manifest.filterMode.name)
                setProperty("lastUpdatedEpochMillis", manifest.lastUpdatedEpochMillis.toString())
                setProperty("ikMaxIterations", manifest.ikConfig.maxIterations.toString())
                setProperty("ikTolerance", manifest.ikConfig.tolerance.toString())
                setProperty("ikDamping", manifest.ikConfig.damping.toString())
                setProperty("ikMaxStep", manifest.ikConfig.maxStep.toString())
                setProperty("metricNumericalEpsilon", manifest.metricPolicy.numericalEpsilon.toString())
                setProperty("metricNearSuccessErrorMeters", manifest.metricPolicy.nearSuccessErrorMeters.toString())
                setProperty("metricCloseMissErrorMeters", manifest.metricPolicy.closeMissErrorMeters.toString())
                setProperty(
                    "metricStalledImprovementRatioEpsilon",
                    manifest.metricPolicy.stalledImprovementRatioEpsilon.toString()
                )
                setProperty("metricJointLimitMarginRatio", manifest.metricPolicy.jointLimitMarginRatio.toString())
                setProperty("metricEasySeedDistanceUpperMeters", manifest.metricPolicy.easySeedDistanceUpperMeters.toString())
                setProperty("metricMediumSeedDistanceUpperMeters", manifest.metricPolicy.mediumSeedDistanceUpperMeters.toString())
                setProperty("metricHardSeedDistanceUpperMeters", manifest.metricPolicy.hardSeedDistanceUpperMeters.toString())
                setProperty("metricLogConditionNumberCap", manifest.metricPolicy.logConditionNumberCap.toString())
                setProperty("batchCount", manifest.batches.size.toString())
                manifest.batches.sortedBy(DatasetGenerationBatch::generationIndex).forEachIndexed { index, batch ->
                    val prefix = "batch.$index."
                    setProperty(prefix + "generationIndex", batch.generationIndex.toString())
                    setProperty(prefix + "batchId", batch.batchId)
                    setProperty(prefix + "rowStart", batch.rowStart.toString())
                    setProperty(prefix + "rowCount", batch.rowCount.toString())
                    setProperty(prefix + "robotIds", batch.robotIds.joinToString(UNIT_SEPARATOR))
                    setProperty(prefix + "samplesPerRobot", batch.samplesPerRobot.toString())
                    setProperty(prefix + "randomSeed", batch.randomSeed.toString())
                    setProperty(prefix + "targetMode", batch.targetMode.name)
                    setProperty(prefix + "reachableFraction", batch.reachableFraction.toString())
                    setProperty(prefix + "filterMode", batch.filterMode.name)
                    setProperty(prefix + "createdAtEpochMillis", batch.createdAtEpochMillis.toString())
                    setProperty(prefix + "randomProtocol", batch.randomProtocol)
                    writeIkConfig(prefix, batch.ikConfig)
                    writeMetricPolicy(prefix, batch.metricPolicy)
                }
            }

        try {
            AtomicFilePublisher.write(file) { temporaryFile ->
                temporaryFile.outputStream().buffered().use { output ->
                    properties.store(output, "Robot Kinematics Lab dataset manifest")
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Could not finish saving the dataset manifest.", error)
        }
    }

    fun safeFileStem(rawName: String): String {
        val sanitized =
            rawName.trim()
                .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .trim('_', '.', '-')
                .take(80)

        return sanitized.ifBlank { "robot_kinematics_dataset" }
    }

    /**
     * Historical datasets used only the sanitized display name as their filename. Two different
     * names can sanitize to the same value, so a new colliding dataset receives a deterministic
     * suffix while the exact owner of an existing legacy filename keeps its original path.
     */
    private fun storageFileStem(datasetName: String): String {
        val normalizedName = datasetName.trim()
        val legacyStem = safeFileStem(normalizedName)
        val legacyManifest = File(outputDirectory(), "$legacyStem.properties")
        if (!legacyManifest.exists() || readStoredDatasetName(legacyManifest) == normalizedName) {
            return legacyStem
        }
        return "$legacyStem-${stableNameDigest(normalizedName)}"
    }

    private fun manifestCandidates(datasetName: String): List<File> {
        val legacyStem = safeFileStem(datasetName)
        val collisionSafeStem = "$legacyStem-${stableNameDigest(datasetName)}"
        return listOf(
            File(outputDirectory(), "$legacyStem.properties"),
            File(outputDirectory(), "$collisionSafeStem.properties")
        )
    }

    private fun readStoredDatasetName(file: File): String? {
        if (!file.isFile || file.length() !in 1..MAXIMUM_MANIFEST_BYTES) return null
        return runCatching {
            Properties().apply { file.inputStream().buffered().use(::load) }
                .getProperty("datasetName")
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun manifestFiles(): List<File> =
        outputDirectory()
            .listFiles { file ->
                file.isFile && file.extension.equals("properties", ignoreCase = true)
            }
            ?.toList()
            .orEmpty()

    private fun inspectCorruptManifest(file: File): CorruptDatasetManifest {
        val properties =
            if (file.isFile && file.length() in 1..MAXIMUM_MANIFEST_BYTES) {
                runCatching {
                    Properties().apply { file.inputStream().buffered().use(::load) }
                }.getOrNull()
            } else {
                null
            }
        val storedSchema = properties?.getProperty("schemaVersion")?.toIntOrNull()
        val reason =
            when {
                !file.isFile -> "Manifest file is no longer available."
                file.length() == 0L -> "Manifest file is empty."
                file.length() > MAXIMUM_MANIFEST_BYTES -> "Manifest exceeds the safe size limit."
                properties == null -> "Manifest properties are unreadable."
                storedSchema != null && storedSchema !in 1..SCHEMA_VERSION ->
                    "Manifest schema $storedSchema is unsupported."
                properties.getProperty("datasetName").isNullOrBlank() -> "Manifest has no dataset name."
                properties.getProperty("csvPath").isNullOrBlank() -> "Manifest has no CSV path."
                else -> "Manifest failed dataset validation."
            }
        return CorruptDatasetManifest(
            fileName = file.name,
            manifestPath = file.absolutePath,
            datasetName = properties?.getProperty("datasetName")?.trim()?.takeIf(String::isNotBlank),
            referencedCsvPath = properties?.getProperty("csvPath")?.trim()?.takeIf(String::isNotBlank),
            reason = reason,
            lastModifiedEpochMillis = file.lastModified()
        )
    }

    private fun uniqueQuarantineDestination(directory: File, sourceName: String): File {
        val baseName = "$sourceName.corrupt"
        var candidate = File(directory, baseName)
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "$baseName-$suffix")
            suffix += 1
        }
        return candidate
    }

    private fun stableNameDigest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(NAME_DIGEST_BYTES)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun readManifest(file: File): DatasetManifest? {
        if (!file.isFile || file.length() !in 1..MAXIMUM_MANIFEST_BYTES) {
            return null
        }

        return runCatching {
            val properties = Properties()
            file.inputStream().buffered().use(properties::load)
            val storedSchema = properties.getProperty("schemaVersion")?.toIntOrNull() ?: 1
            require(storedSchema in 1..SCHEMA_VERSION) {
                "Unsupported dataset manifest schema."
            }
            val storedCsvPath = properties.getProperty("csvPath") ?: return null
            val storedCsvFile = File(storedCsvPath)
            val organizedCsvFile = File(outputDirectory(), storedCsvFile.name)
            val resolvedCsvPath =
                if (
                    organizedCsvFile.exists() &&
                        storedCsvFile.absoluteFile.parentFile != outputDirectory().absoluteFile
                ) {
                    organizedCsvFile.absolutePath
                } else {
                    storedCsvPath
                }
            require(
                File(resolvedCsvPath).canonicalFile.parentFile == outputDirectory().canonicalFile
            ) { "Dataset manifest points outside the managed dataset directory." }

            val topLevelIkConfig =
                IKConfig(
                    maxIterations = properties.intOrDefault("ikMaxIterations", IKConfig().maxIterations),
                    tolerance = properties.doubleOrDefault("ikTolerance", IKConfig().tolerance),
                    damping = properties.doubleOrDefault("ikDamping", IKConfig().damping),
                    maxStep = properties.doubleOrDefault("ikMaxStep", IKConfig().maxStep)
                )
            val topLevelMetricPolicy = properties.readMetricPolicy("")
            val generationCount = properties.getProperty("generationCount").toInt()
            require(generationCount in 1..MAXIMUM_GENERATION_COUNT) {
                "Dataset generation count is outside the supported storage range."
            }
            val rowCount = properties.getProperty("rowCount").toLong()
            val robotIds =
                properties.getProperty("robotIds", "")
                    .split(UNIT_SEPARATOR)
                    .filter { it.isNotBlank() }
            val samplesPerRobot = properties.getProperty("samplesPerRobotLastRun").toInt()
            val randomSeed = properties.getProperty("randomSeed").toInt()
            val targetMode = DatasetTargetMode.valueOf(properties.getProperty("targetMode"))
            val reachableFraction = properties.getProperty("reachableFraction").toDouble()
            val filterMode = DatasetFilterMode.valueOf(properties.getProperty("filterMode"))
            val lastUpdated = properties.getProperty("lastUpdatedEpochMillis").toLong()
            val randomProtocol =
                properties.getProperty(
                    "randomProtocol",
                    com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol.LEGACY_UNVERSIONED_ID
                )
            val batches =
                if (storedSchema >= 4) {
                    val count = properties.getProperty("batchCount")?.toIntOrNull() ?: error("Missing batch count")
                    require(count in 0..MAXIMUM_GENERATION_COUNT) {
                        "Dataset batch count is outside the supported storage range."
                    }
                    (0 until count).map { index -> properties.readBatch(index) }
                } else if (generationCount == 1 && rowCount > 0L) {
                    listOf(
                        DatasetGenerationBatch(
                            generationIndex = 0,
                            batchId = "legacy-generation-0",
                            rowStart = 0L,
                            rowCount = rowCount,
                            robotIds = robotIds,
                            samplesPerRobot = samplesPerRobot,
                            randomSeed = randomSeed,
                            targetMode = targetMode,
                            reachableFraction = reachableFraction,
                            filterMode = filterMode,
                            createdAtEpochMillis = lastUpdated,
                            ikConfig = topLevelIkConfig,
                            metricPolicy = topLevelMetricPolicy,
                            randomProtocol = randomProtocol
                        )
                    )
                } else {
                    emptyList()
                }

            DatasetManifest(
                datasetName = properties.getProperty("datasetName") ?: return null,
                csvPath = resolvedCsvPath,
                rowCount = rowCount,
                generationCount = generationCount,
                robotIds = robotIds,
                samplesPerRobotLastRun = samplesPerRobot,
                randomSeed = randomSeed,
                targetMode = targetMode,
                reachableFraction = reachableFraction,
                filterMode = filterMode,
                lastUpdatedEpochMillis = lastUpdated,
                ikConfig = topLevelIkConfig,
                metricPolicy = topLevelMetricPolicy,
                randomProtocol = randomProtocol,
                batches = batches,
                scientificFingerprint = properties.getProperty("scientificFingerprint")
            )
        }.getOrNull()
    }

    private fun Properties.intOrDefault(key: String, default: Int): Int {
        return getProperty(key)?.toIntOrNull() ?: default
    }

    private fun Properties.doubleOrDefault(key: String, default: Double): Double {
        return getProperty(key)?.toDoubleOrNull() ?: default
    }

    private fun Properties.setPropertyValue(key: String, value: Any) {
        setProperty(key, value.toString())
    }

    private fun Properties.writeIkConfig(prefix: String, config: IKConfig) {
        setPropertyValue(prefix + "ikMaxIterations", config.maxIterations)
        setPropertyValue(prefix + "ikTolerance", config.tolerance)
        setPropertyValue(prefix + "ikDamping", config.damping)
        setPropertyValue(prefix + "ikMaxStep", config.maxStep)
    }

    private fun Properties.writeMetricPolicy(prefix: String, policy: DiagnosticMetricPolicy) {
        setPropertyValue(prefix + "metricNumericalEpsilon", policy.numericalEpsilon)
        setPropertyValue(prefix + "metricNearSuccessErrorMeters", policy.nearSuccessErrorMeters)
        setPropertyValue(prefix + "metricCloseMissErrorMeters", policy.closeMissErrorMeters)
        setPropertyValue(prefix + "metricStalledImprovementRatioEpsilon", policy.stalledImprovementRatioEpsilon)
        setPropertyValue(prefix + "metricJointLimitMarginRatio", policy.jointLimitMarginRatio)
        setPropertyValue(prefix + "metricEasySeedDistanceUpperMeters", policy.easySeedDistanceUpperMeters)
        setPropertyValue(prefix + "metricMediumSeedDistanceUpperMeters", policy.mediumSeedDistanceUpperMeters)
        setPropertyValue(prefix + "metricHardSeedDistanceUpperMeters", policy.hardSeedDistanceUpperMeters)
        setPropertyValue(prefix + "metricLogConditionNumberCap", policy.logConditionNumberCap)
    }

    private fun Properties.readMetricPolicy(prefix: String): DiagnosticMetricPolicy {
        val defaults = DiagnosticMetricPolicy()
        return DiagnosticMetricPolicy(
            numericalEpsilon = doubleOrDefault(prefix + "metricNumericalEpsilon", defaults.numericalEpsilon),
            nearSuccessErrorMeters = doubleOrDefault(prefix + "metricNearSuccessErrorMeters", defaults.nearSuccessErrorMeters),
            closeMissErrorMeters = doubleOrDefault(prefix + "metricCloseMissErrorMeters", defaults.closeMissErrorMeters),
            stalledImprovementRatioEpsilon = doubleOrDefault(prefix + "metricStalledImprovementRatioEpsilon", defaults.stalledImprovementRatioEpsilon),
            jointLimitMarginRatio = doubleOrDefault(prefix + "metricJointLimitMarginRatio", defaults.jointLimitMarginRatio),
            easySeedDistanceUpperMeters = doubleOrDefault(prefix + "metricEasySeedDistanceUpperMeters", defaults.easySeedDistanceUpperMeters),
            mediumSeedDistanceUpperMeters = doubleOrDefault(prefix + "metricMediumSeedDistanceUpperMeters", defaults.mediumSeedDistanceUpperMeters),
            hardSeedDistanceUpperMeters = doubleOrDefault(prefix + "metricHardSeedDistanceUpperMeters", defaults.hardSeedDistanceUpperMeters),
            logConditionNumberCap = doubleOrDefault(prefix + "metricLogConditionNumberCap", defaults.logConditionNumberCap)
        )
    }

    private fun Properties.readBatch(index: Int): DatasetGenerationBatch {
        val prefix = "batch.$index."
        return DatasetGenerationBatch(
            generationIndex = getProperty(prefix + "generationIndex").toInt(),
            batchId = getProperty(prefix + "batchId"),
            rowStart = getProperty(prefix + "rowStart").toLong(),
            rowCount = getProperty(prefix + "rowCount").toLong(),
            robotIds = getProperty(prefix + "robotIds").split(UNIT_SEPARATOR).filter(String::isNotBlank),
            samplesPerRobot = getProperty(prefix + "samplesPerRobot").toInt(),
            randomSeed = getProperty(prefix + "randomSeed").toInt(),
            targetMode = DatasetTargetMode.valueOf(getProperty(prefix + "targetMode")),
            reachableFraction = getProperty(prefix + "reachableFraction").toDouble(),
            filterMode = DatasetFilterMode.valueOf(getProperty(prefix + "filterMode")),
            createdAtEpochMillis = getProperty(prefix + "createdAtEpochMillis").toLong(),
            ikConfig = IKConfig(
                maxIterations = getProperty(prefix + "ikMaxIterations").toInt(),
                tolerance = getProperty(prefix + "ikTolerance").toDouble(),
                damping = getProperty(prefix + "ikDamping").toDouble(),
                maxStep = getProperty(prefix + "ikMaxStep").toDouble()
            ),
            metricPolicy = readMetricPolicy(prefix),
            randomProtocol = getProperty(prefix + "randomProtocol")
        )
    }

    companion object {
        private const val SCHEMA_VERSION = 5
        private const val UNIT_SEPARATOR = "\u001F"
        private const val MAXIMUM_GENERATION_COUNT = 10_000
        private const val MAXIMUM_MANIFEST_BYTES = 16L * 1024L * 1024L
        private const val NAME_DIGEST_BYTES = 8
        private const val CORRUPT_MANIFEST_DIRECTORY_NAME = "corrupt-manifests"
    }
}
