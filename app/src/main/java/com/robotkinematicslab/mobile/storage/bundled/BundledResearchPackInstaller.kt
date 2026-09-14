package com.robotkinematicslab.mobile.storage.bundled

import android.content.Context
import android.os.storage.StorageManager
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationBatch
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.storage.AtomicFilePublisher
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPInputStream

data class BundledResearchPackStatus(
    val packId: String,
    val displayName: String,
    val available: Boolean,
    val installed: Boolean,
    val datasetCount: Int,
    val classifierModelCount: Int,
    val oneMicronModelCount: Int,
    val installedBytes: Long,
    val message: String
) {
    val modelCount: Int
        get() = classifierModelCount + oneMicronModelCount
}

data class BundledResearchPackProgress(
    val completedArtifacts: Int,
    val totalArtifacts: Int,
    val message: String
) {
    val fraction: Double
        get() = if (totalArtifacts == 0) 1.0 else completedArtifacts.toDouble() / totalArtifacts
}

data class BundledResearchPackInstallResult(
    val status: BundledResearchPackStatus,
    val installedArtifacts: Int,
    val reusedArtifacts: Int
)

/**
 * Installs computer-generated scientific evidence shipped inside the APK.
 *
 * Assets remain compressed in the APK and are expanded atomically into the same versioned
 * repositories used by on-device generation. A completed marker makes later app launches cheap
 * and, importantly, prevents a dataset extended by the user from being silently restored.
 */
class BundledResearchPackInstaller internal constructor(
    private val paths: AppStoragePaths,
    private val datasetRepository: DatasetStorageRepository,
    private val allocatableBytes: () -> Long = { paths.rootDirectory.usableSpace },
    private val openAsset: (String) -> InputStream
) {

    constructor(context: Context) : this(
        paths = AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData),
        datasetRepository = DatasetStorageRepository(context),
        allocatableBytes = {
            kotlin.runCatching {
                val storage = context.getSystemService(StorageManager::class.java)
                val storageUuid = storage.getUuidForPath(context.filesDir)
                storage.getAllocatableBytes(storageUuid)
            }.getOrElse { context.filesDir.usableSpace }
        },
        openAsset = { path -> context.assets.open(path) }
    )

    fun inspect(): BundledResearchPackStatus {
        val catalog = loadCatalogOrNull()
            ?: return BundledResearchPackStatus(
                packId = "",
                displayName = "Bundled research pack",
                available = false,
                installed = false,
                datasetCount = 0,
                classifierModelCount = 0,
                oneMicronModelCount = 0,
                installedBytes = 0L,
                message = "This build does not contain a bundled research pack."
            )
        val installed = markerFile(catalog).isFile
        return status(
            catalog = catalog,
            installed = installed,
            message = if (installed) "Installed and ready for local use." else "Included in the APK; first-run import is pending."
        )
    }

    fun installIfNeeded(
        onProgress: (BundledResearchPackProgress) -> Unit = {}
    ): BundledResearchPackInstallResult = synchronized(INSTALL_LOCK) {
        installLocked(onProgress)
    }

    private fun installLocked(
        onProgress: (BundledResearchPackProgress) -> Unit
    ): BundledResearchPackInstallResult {
        val catalog = requireNotNull(loadCatalogOrNull()) { "This APK has no bundled research pack." }
        if (markerFile(catalog).isFile) {
            return BundledResearchPackInstallResult(status(catalog, true, "Already installed."), 0, catalog.totalArtifacts)
        }
        val safetyReserve = maxOf(MINIMUM_FREE_SPACE_RESERVE_BYTES, catalog.expandedBytes / 5L)
        require(allocatableBytes() >= catalog.expandedBytes + safetyReserve) {
            "Not enough free storage for the bundled research pack. " +
                "Required ${catalog.expandedBytes + safetyReserve} bytes including the safety reserve."
        }

        var completed = 0
        var installed = 0
        var reused = 0
        fun progress(message: String) {
            onProgress(BundledResearchPackProgress(completed, catalog.totalArtifacts, message))
        }

        progress("Preparing ${catalog.displayName}…")
        catalog.datasets.forEach { dataset ->
            progress("Importing ${dataset.displayName}…")
            val destination = File(paths.datasetsDirectory, dataset.fileName)
            if (copyVerifiedAsset(dataset.assetPath, destination, dataset.sha256, dataset.rawBytes, dataset.gzipCompressed)) installed++ else reused++
            installDatasetManifest(dataset, destination)
            completed++
            progress("Dataset ready: ${dataset.displayName}")
        }

        catalog.oneMicronRuns.forEach { run ->
            progress("Installing certified IK model ${run.displayName}…")
            val modelDestination = File(paths.oneMicronModelsDirectory, run.modelFileName)
            if (copyVerifiedAsset(run.modelAssetPath, modelDestination, run.modelSha256, run.modelBytes, false)) installed++ else reused++
            val runDirectory = File(paths.oneMicronTrainingDirectory, run.runId).apply(File::mkdirs)
            val historyDestination = File(runDirectory, "learning-curve.csv")
            if (copyVerifiedAsset(run.historyAssetPath, historyDestination, run.historySha256, run.historyBytes, false)) installed++ else reused++
            val reportDestination = File(runDirectory, "one-micron-report.properties")
            installPatchedProperties(
                assetPath = run.reportAssetPath,
                expectedAssetSha256 = run.reportSha256,
                expectedAssetBytes = run.reportBytes,
                destination = reportDestination,
                replacements = mapOf(
                    "datasetPath" to datasetDestination(catalog, run.datasetId).absolutePath,
                    "modelPath" to modelDestination.absolutePath,
                    "learningCurvePath" to historyDestination.absolutePath
                )
            ).also { if (it) installed++ else reused++ }
            completed += 3
            progress("Certified IK model ready: ${run.displayName}")
        }

        catalog.classifierRuns.forEach { run ->
            progress("Installing classifier comparison ${run.displayName}…")
            val runDirectory = File(paths.trainingDirectory, run.runId).apply(File::mkdirs)
            val historyDestination = File(runDirectory, "iteration-history.csv")
            if (copyVerifiedAsset(run.historyAssetPath, historyDestination, run.historySha256, run.historyBytes, false)) installed++ else reused++
            completed++

            val modelDestinations = run.models.map { model ->
                val destination = File(paths.modelsDirectory, model.fileName)
                if (copyVerifiedAsset(model.assetPath, destination, model.sha256, model.bytes, false)) installed++ else reused++
                completed++
                progress("Model ready: ${model.displayName}")
                destination
            }

            val summaryDestination = File(runDirectory, "summary.properties")
            installPatchedProperties(
                assetPath = run.summaryAssetPath,
                expectedAssetSha256 = run.summarySha256,
                expectedAssetBytes = run.summaryBytes,
                destination = summaryDestination,
                replacements = mapOf(
                    "datasetPath" to datasetDestination(catalog, run.datasetId).absolutePath,
                    "historyCsvPath" to historyDestination.absolutePath,
                    "modelPaths" to modelDestinations.joinToString(PATH_SEPARATOR) { it.absolutePath }
                )
            ).also { if (it) installed++ else reused++ }
            completed++
            progress("Classifier comparison ready: ${run.displayName}")
        }

        writeMarker(catalog)
        onProgress(BundledResearchPackProgress(catalog.totalArtifacts, catalog.totalArtifacts, "Bundled research pack ready."))
        return BundledResearchPackInstallResult(
            status(catalog, true, "Imported successfully with verified SHA-256 integrity."),
            installedArtifacts = installed,
            reusedArtifacts = reused
        )
    }

    private fun installDatasetManifest(dataset: BundledDataset, csvFile: File) {
        val config = IKConfig(dataset.maxIterations, dataset.tolerance, dataset.damping, dataset.maxStep)
        val metricPolicy = DiagnosticMetricPolicy()
        val batch = DatasetGenerationBatch(
            generationIndex = 0,
            batchId = "bundled-${dataset.id}",
            rowStart = 0L,
            rowCount = dataset.rowCount,
            robotIds = dataset.robotIds,
            samplesPerRobot = dataset.samplesPerRobot,
            randomSeed = dataset.randomSeed,
            targetMode = dataset.targetMode,
            reachableFraction = dataset.reachableFraction,
            filterMode = dataset.filterMode,
            createdAtEpochMillis = dataset.createdAtEpochMillis,
            ikConfig = config,
            metricPolicy = metricPolicy,
            randomProtocol = dataset.randomProtocol
        )
        datasetRepository.saveManifest(
            DatasetManifest(
                datasetName = dataset.displayName,
                csvPath = csvFile.absolutePath,
                rowCount = dataset.rowCount,
                generationCount = 1,
                robotIds = dataset.robotIds,
                samplesPerRobotLastRun = dataset.samplesPerRobot,
                randomSeed = dataset.randomSeed,
                targetMode = dataset.targetMode,
                reachableFraction = dataset.reachableFraction,
                filterMode = dataset.filterMode,
                lastUpdatedEpochMillis = dataset.createdAtEpochMillis,
                ikConfig = config,
                metricPolicy = metricPolicy,
                randomProtocol = dataset.randomProtocol,
                batches = listOf(batch)
            )
        )
    }

    private fun copyVerifiedAsset(
        assetPath: String,
        destination: File,
        expectedSha256: String,
        expectedBytes: Long,
        gzipCompressed: Boolean
    ): Boolean {
        destination.parentFile?.mkdirs()
        if (destination.isFile && sha256(destination).equals(expectedSha256, ignoreCase = true)) return false
        require(!destination.exists()) {
            "Refusing to overwrite modified bundled artifact ${destination.name}. Remove it explicitly before restoring the pack."
        }
        return AtomicFilePublisher.write(destination) { temporary ->
            openAsset(assetPath).buffered().use { source ->
                val input = if (gzipCompressed) GZIPInputStream(source) else source
                input.use { decoded -> temporary.outputStream().buffered().use(decoded::copyTo) }
            }
            require(temporary.length() == expectedBytes) {
                "Unexpected expanded size while importing ${destination.name}."
            }
            require(sha256(temporary).equals(expectedSha256, ignoreCase = true)) {
                "SHA-256 mismatch while importing ${destination.name}."
            }
            true
        }
    }

    private fun installPatchedProperties(
        assetPath: String,
        expectedAssetSha256: String,
        expectedAssetBytes: Long,
        destination: File,
        replacements: Map<String, String>
    ): Boolean {
        if (destination.isFile) return false
        val properties = Properties()
        val bytes = openAsset(assetPath).buffered().use(InputStream::readBytes)
        require(bytes.size.toLong() == expectedAssetBytes) { "Unexpected size while reading ${destination.name}." }
        require(sha256(bytes).equals(expectedAssetSha256, ignoreCase = true)) {
            "SHA-256 mismatch while reading ${destination.name}."
        }
        ByteArrayInputStream(bytes).use(properties::load)
        replacements.forEach(properties::setProperty)
        return AtomicFilePublisher.write(destination) { temporary ->
            temporary.outputStream().buffered().use { properties.store(it, "Robot Kinematics Lab bundled research evidence") }
            true
        }
    }

    private fun datasetDestination(catalog: BundledResearchPackCatalog, datasetId: String): File {
        val dataset = catalog.datasets.singleOrNull { it.id == datasetId }
            ?: error("Bundled run refers to unknown dataset '$datasetId'.")
        return File(paths.datasetsDirectory, dataset.fileName)
    }

    private fun status(
        catalog: BundledResearchPackCatalog,
        installed: Boolean,
        message: String
    ): BundledResearchPackStatus = BundledResearchPackStatus(
        packId = catalog.packId,
        displayName = catalog.displayName,
        available = true,
        installed = installed,
        datasetCount = catalog.datasets.size,
        classifierModelCount = catalog.classifierRuns.sumOf { it.models.size },
        oneMicronModelCount = catalog.oneMicronRuns.size,
        installedBytes = if (installed) installedBytes(catalog) else 0L,
        message = message
    )

    private fun installedBytes(catalog: BundledResearchPackCatalog): Long = buildList {
        catalog.datasets.forEach { add(File(paths.datasetsDirectory, it.fileName)) }
        catalog.oneMicronRuns.forEach { add(File(paths.oneMicronModelsDirectory, it.modelFileName)) }
        catalog.classifierRuns.flatMapTo(this) { run -> run.models.map { File(paths.modelsDirectory, it.fileName) } }
    }.filter(File::isFile).sumOf(File::length)

    private fun markerFile(catalog: BundledResearchPackCatalog): File =
        File(paths.rootDirectory, ".${catalog.packId}.installed.properties")

    private fun writeMarker(catalog: BundledResearchPackCatalog) {
        val marker = markerFile(catalog)
        val properties = Properties().apply {
            setProperty("schemaVersion", CATALOG_SCHEMA.toString())
            setProperty("packId", catalog.packId)
            setProperty("installedAtEpochMillis", System.currentTimeMillis().toString())
            setProperty("artifactCount", catalog.totalArtifacts.toString())
        }
        AtomicFilePublisher.write(marker) { temporary ->
            temporary.outputStream().buffered().use { properties.store(it, "Bundled research pack installation") }
        }
    }

    private fun loadCatalogOrNull(): BundledResearchPackCatalog? = runCatching {
        val properties = Properties()
        openAsset(CATALOG_ASSET).buffered().use(properties::load)
        properties.toCatalog()
    }.getOrNull()

    private fun Properties.toCatalog(): BundledResearchPackCatalog {
        require(requiredInt("schemaVersion") == CATALOG_SCHEMA) { "Unsupported bundled research pack schema." }
        val datasets = (0 until requiredInt("datasetCount")).map { index ->
            val prefix = "dataset.$index."
            BundledDataset(
                id = required(prefix + "id"),
                displayName = required(prefix + "displayName"),
                assetPath = required(prefix + "assetPath"),
                fileName = required(prefix + "fileName"),
                sha256 = requiredSha(prefix + "sha256"),
                rawBytes = requiredLong(prefix + "rawBytes"),
                gzipCompressed = required(prefix + "compression") == "GZIP",
                rowCount = requiredLong(prefix + "rowCount"),
                robotIds = required(prefix + "robotIds").split(LIST_SEPARATOR).filter(String::isNotBlank),
                samplesPerRobot = requiredInt(prefix + "samplesPerRobot"),
                randomSeed = requiredInt(prefix + "randomSeed"),
                targetMode = DatasetTargetMode.valueOf(required(prefix + "targetMode")),
                reachableFraction = requiredDouble(prefix + "reachableFraction"),
                filterMode = DatasetFilterMode.valueOf(required(prefix + "filterMode")),
                maxIterations = requiredInt(prefix + "maxIterations"),
                tolerance = requiredDouble(prefix + "tolerance"),
                damping = requiredDouble(prefix + "damping"),
                maxStep = requiredDouble(prefix + "maxStep"),
                randomProtocol = getProperty(prefix + "randomProtocol", ScientificRandomProtocol.ID),
                createdAtEpochMillis = requiredLong(prefix + "createdAtEpochMillis")
            )
        }
        require(datasets.map(BundledDataset::id).distinct().size == datasets.size)
        val micronRuns = (0 until requiredInt("oneMicronRunCount")).map { index ->
            val prefix = "oneMicronRun.$index."
            BundledOneMicronRun(
                runId = required(prefix + "runId"),
                displayName = required(prefix + "displayName"),
                datasetId = required(prefix + "datasetId"),
                modelAssetPath = required(prefix + "modelAssetPath"),
                modelFileName = required(prefix + "modelFileName"),
                modelSha256 = requiredSha(prefix + "modelSha256"),
                modelBytes = requiredLong(prefix + "modelBytes"),
                reportAssetPath = required(prefix + "reportAssetPath"),
                reportSha256 = requiredSha(prefix + "reportSha256"),
                reportBytes = requiredLong(prefix + "reportBytes"),
                historyAssetPath = required(prefix + "historyAssetPath"),
                historySha256 = requiredSha(prefix + "historySha256"),
                historyBytes = requiredLong(prefix + "historyBytes")
            )
        }
        val classifierRuns = (0 until requiredInt("classifierRunCount")).map { index ->
            val prefix = "classifierRun.$index."
            val models = (0 until requiredInt(prefix + "modelCount")).map { modelIndex ->
                val modelPrefix = prefix + "model.$modelIndex."
                BundledClassifierModel(
                    displayName = required(modelPrefix + "displayName"),
                    assetPath = required(modelPrefix + "assetPath"),
                    fileName = required(modelPrefix + "fileName"),
                    sha256 = requiredSha(modelPrefix + "sha256"),
                    bytes = requiredLong(modelPrefix + "bytes")
                )
            }
            BundledClassifierRun(
                runId = required(prefix + "runId"),
                displayName = required(prefix + "displayName"),
                datasetId = required(prefix + "datasetId"),
                summaryAssetPath = required(prefix + "summaryAssetPath"),
                summarySha256 = requiredSha(prefix + "summarySha256"),
                summaryBytes = requiredLong(prefix + "summaryBytes"),
                historyAssetPath = required(prefix + "historyAssetPath"),
                historySha256 = requiredSha(prefix + "historySha256"),
                historyBytes = requiredLong(prefix + "historyBytes"),
                models = models
            )
        }
        val catalog = BundledResearchPackCatalog(
            packId = required("packId"),
            displayName = required("displayName"),
            datasets = datasets,
            oneMicronRuns = micronRuns,
            classifierRuns = classifierRuns
        )
        val datasetIds = datasets.map(BundledDataset::id).toSet()
        require((micronRuns.map(BundledOneMicronRun::datasetId) + classifierRuns.map(BundledClassifierRun::datasetId)).all(datasetIds::contains))
        return catalog
    }

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)?.takeIf(String::isNotBlank)) { "Missing bundled-pack property '$key'." }

    private fun Properties.requiredInt(key: String): Int = required(key).toInt().also { require(it >= 0) }
    private fun Properties.requiredLong(key: String): Long = required(key).toLong().also { require(it >= 0L) }
    private fun Properties.requiredDouble(key: String): Double = required(key).toDouble().also { require(it.isFinite()) }
    private fun Properties.requiredSha(key: String): String = required(key).lowercase().also {
        require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256 for '$key'." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance(SHA_256).digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class BundledResearchPackCatalog(
        val packId: String,
        val displayName: String,
        val datasets: List<BundledDataset>,
        val oneMicronRuns: List<BundledOneMicronRun>,
        val classifierRuns: List<BundledClassifierRun>
    ) {
        val totalArtifacts: Int
            get() = datasets.size + oneMicronRuns.size * 3 + classifierRuns.sumOf { 2 + it.models.size }
        val expandedBytes: Long
            get() = datasets.sumOf(BundledDataset::rawBytes) +
                oneMicronRuns.sumOf { it.modelBytes + it.reportBytes + it.historyBytes } +
                classifierRuns.sumOf { run ->
                    run.summaryBytes + run.historyBytes + run.models.sumOf(BundledClassifierModel::bytes)
                }
    }

    private data class BundledDataset(
        val id: String,
        val displayName: String,
        val assetPath: String,
        val fileName: String,
        val sha256: String,
        val rawBytes: Long,
        val gzipCompressed: Boolean,
        val rowCount: Long,
        val robotIds: List<String>,
        val samplesPerRobot: Int,
        val randomSeed: Int,
        val targetMode: DatasetTargetMode,
        val reachableFraction: Double,
        val filterMode: DatasetFilterMode,
        val maxIterations: Int,
        val tolerance: Double,
        val damping: Double,
        val maxStep: Double,
        val randomProtocol: String,
        val createdAtEpochMillis: Long
    )

    private data class BundledOneMicronRun(
        val runId: String,
        val displayName: String,
        val datasetId: String,
        val modelAssetPath: String,
        val modelFileName: String,
        val modelSha256: String,
        val modelBytes: Long,
        val reportAssetPath: String,
        val reportSha256: String,
        val reportBytes: Long,
        val historyAssetPath: String,
        val historySha256: String,
        val historyBytes: Long
    )

    private data class BundledClassifierRun(
        val runId: String,
        val displayName: String,
        val datasetId: String,
        val summaryAssetPath: String,
        val summarySha256: String,
        val summaryBytes: Long,
        val historyAssetPath: String,
        val historySha256: String,
        val historyBytes: Long,
        val models: List<BundledClassifierModel>
    )

    private data class BundledClassifierModel(
        val displayName: String,
        val assetPath: String,
        val fileName: String,
        val sha256: String,
        val bytes: Long
    )

    companion object {
        private const val CATALOG_ASSET = "research_pack/catalog.properties"
        private const val CATALOG_SCHEMA = 1
        private const val SHA_256 = "SHA-256"
        private const val LIST_SEPARATOR = "|"
        private const val PATH_SEPARATOR = "\u001F"
        private const val MINIMUM_FREE_SPACE_RESERVE_BYTES = 256L * 1024L * 1024L
        private val INSTALL_LOCK = Any()
    }
}
