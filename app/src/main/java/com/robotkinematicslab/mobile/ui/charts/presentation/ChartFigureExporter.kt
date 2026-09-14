package com.robotkinematicslab.mobile.ui.charts.presentation

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.robotkinematicslab.mobile.provenance.AppBuildIdentity
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChartFigureExportResult(
    val displayName: String,
    val locationLabel: String,
    val relativePath: String? = null,
    val sha256: String? = null
)

internal data class AutomaticFigureExportTrigger(
    val collection: String,
    val analysisId: String,
    val executionId: String,
    val title: String,
    val figureType: String,
    val dataFingerprint: String,
    val presentationFingerprint: String
)

internal data class AutomaticFigureManifestEntry(
    val title: String,
    val collection: String,
    val analysisId: String,
    val executionId: String,
    val figureType: String,
    val relativePath: String,
    val sha256: String? = null,
    val dataFingerprint: String,
    val presentationFingerprint: String? = null,
    val exportedAtEpochMillis: Long,
    val status: AutomaticFigureExportStatus = AutomaticFigureExportStatus.GENERATED,
    val failureMessage: String? = null
)

internal enum class AutomaticFigureExportStatus {
    EXPECTED,
    GENERATED,
    FAILED
}

object ChartFigureExporter {
    suspend fun savePng(
        context: Context,
        title: String,
        image: ImageBitmap,
        metadataProperties: Map<String, String> = emptyMap()
    ): Result<ChartFigureExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = image.asAndroidBitmap()
                val figuresDirectory =
                    AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData).figuresDirectory
                val projectFigure = synchronized(EXPORT_LOCK) {
                    val displayName =
                        immutableDisplayName(
                            figuresDirectory = figuresDirectory,
                            requestedStem = "${safeFileStem(title)}_${TIMESTAMP_FORMAT.format(LocalDateTime.now())}"
                        )
                    saveProjectEvidenceCopy(
                        figuresDirectory = figuresDirectory,
                        displayName = displayName,
                        title = title,
                        bitmap = bitmap,
                        metadataProperties = metadataProperties
                    )
                }
                val galleryLocation = runCatching {
                    saveGalleryCopy(context, projectFigure)
                }.getOrNull()
                ChartFigureExportResult(
                    displayName = projectFigure.name,
                    relativePath = projectFigure.absolutePath,
                    sha256 = sha256(projectFigure),
                    locationLabel =
                        galleryLocation?.let { "$it · project evidence / figures" }
                            ?: "project evidence / figures (gallery copy unavailable)"
                )
            }
        }

    /** Saves one immutable rendering in a run-specific evidence directory. */
    suspend fun saveAutomaticPng(
        context: Context,
        title: String,
        collection: String,
        analysisId: String,
        executionId: String,
        figureType: String,
        dataFingerprint: String,
        presentationFingerprint: String? = null,
        image: ImageBitmap,
        caption: String = ""
    ): Result<ChartFigureExportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = image.asAndroidBitmap()
                val paths = AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData)
                val exportedAt = LocalDateTime.now()
                val relativeDirectory =
                    automaticRelativeDirectory(
                        collection = collection,
                        date = DATE_FORMAT.format(exportedAt),
                        analysisId = analysisId,
                        executionId = executionId,
                        figureType = figureType
                    )
                val destinationDirectory = File(paths.figuresDirectory, relativeDirectory)
                val projectFigure = synchronized(EXPORT_LOCK) {
                    val manifestFile = File(destinationDirectory.parentFile, MANIFEST_FILE_NAME)
                    val existing =
                        reusableGeneratedFigureAcrossDates(
                            figuresRoot = paths.figuresDirectory,
                            collection = collection,
                            analysisId = analysisId,
                            executionId = executionId,
                            title = title,
                            figureType = figureType,
                            dataFingerprint = dataFingerprint,
                            presentationFingerprint = presentationFingerprint
                        )
                    if (existing != null) return@synchronized existing

                    val displayName =
                        immutableDisplayName(
                            figuresDirectory = destinationDirectory,
                            requestedStem = "${safeFileStem(title)}-${dataFingerprint.take(12)}"
                        )
                    val relativePath = "$relativeDirectory${File.separator}$displayName"
                    val expectedEntry =
                        AutomaticFigureManifestEntry(
                            title = title,
                            collection = collection,
                            analysisId = analysisId,
                            executionId = executionId,
                            figureType = figureType,
                            relativePath = relativePath,
                            dataFingerprint = dataFingerprint,
                            presentationFingerprint = presentationFingerprint,
                            exportedAtEpochMillis = System.currentTimeMillis(),
                            status = AutomaticFigureExportStatus.EXPECTED
                        )
                    appendManifestEntry(manifestFile, expectedEntry)
                    var savedFigure: File? = null
                    try {
                        val completedFigure = saveProjectEvidenceCopy(
                            figuresDirectory = destinationDirectory,
                            displayName = displayName,
                            title = title,
                            bitmap = bitmap,
                            scientificRole = "automatic-final-figure",
                            metadataProperties =
                                mapOf(
                                    "caption" to caption,
                                    "collection" to collection,
                                    "analysisId" to analysisId,
                                    "executionId" to executionId,
                                    "figureType" to figureType,
                                    "dataFingerprint" to dataFingerprint,
                                    "presentationFingerprint" to presentationFingerprint.orEmpty(),
                                    "figureRelativePath" to relativePath
                                )
                        )
                        savedFigure = completedFigure
                        val figureHash = sha256(completedFigure)
                        appendManifestEntry(
                            manifestFile = manifestFile,
                            entry = expectedEntry.copy(
                                sha256 = figureHash,
                                exportedAtEpochMillis = System.currentTimeMillis(),
                                status = AutomaticFigureExportStatus.GENERATED
                            )
                        )
                        completedFigure
                    } catch (error: Throwable) {
                        savedFigure?.delete()
                        savedFigure?.let { figure ->
                            File(
                                figure.parentFile,
                                "${figure.name.removeSuffix(".png")}.properties"
                            ).delete()
                        }
                        runCatching {
                            appendManifestEntry(
                                manifestFile = manifestFile,
                                entry = expectedEntry.copy(
                                    exportedAtEpochMillis = System.currentTimeMillis(),
                                    status = AutomaticFigureExportStatus.FAILED,
                                    failureMessage = error.message ?: error::class.java.simpleName
                                )
                            )
                        }
                        throw error
                    }
                }
                val relativePath = "$relativeDirectory${File.separator}${projectFigure.name}"
                ChartFigureExportResult(
                    displayName = projectFigure.name,
                    locationLabel = "project evidence / figures / $relativeDirectory",
                    relativePath = relativePath,
                    sha256 = sha256(projectFigure)
                )
            }
        }

    /**
     * Returns the already-finalised figure for an identical run/title/data contract.
     *
     * Reopening a results screen must be idempotent: it must not create `-2`, `-3`, … copies.
     * If the manifest points to missing or modified evidence, fail closed instead of silently
     * accepting or replacing a corrupted scientific artifact.
     */
    internal fun reusableGeneratedFigure(
        manifestFile: File,
        figuresRoot: File,
        collection: String,
        analysisId: String,
        executionId: String,
        title: String,
        figureType: String,
        dataFingerprint: String,
        presentationFingerprint: String? = null
    ): File? {
        val matching =
            readManifestEntries(manifestFile).firstOrNull { entry ->
                entry.status == AutomaticFigureExportStatus.GENERATED &&
                    entry.collection == collection &&
                    entry.analysisId == analysisId &&
                    entry.executionId == executionId &&
                    entry.title == title &&
                    entry.figureType == figureType &&
                    entry.dataFingerprint == dataFingerprint &&
                    entry.presentationFingerprint == presentationFingerprint
        } ?: return null
        val root = figuresRoot.canonicalFile
        require(!File(matching.relativePath).isAbsolute) {
            "Automatic figure manifest requires a relative path."
        }
        val figure = File(root, matching.relativePath).canonicalFile
        require(figure.toPath().startsWith(root.toPath())) {
            "Automatic figure manifest points outside the figure library."
        }
        check(figure.isFile) { "Finalised automatic figure is missing: ${matching.relativePath}" }
        check(sha256(figure) == matching.sha256) {
            "Finalised automatic figure failed its SHA-256 integrity check: ${matching.relativePath}"
        }
        return figure
    }

    /** Finds an identical execution even when its result screen is reopened on a later day. */
    internal fun reusableGeneratedFigureAcrossDates(
        figuresRoot: File,
        collection: String,
        analysisId: String,
        executionId: String,
        title: String,
        figureType: String,
        dataFingerprint: String,
        presentationFingerprint: String? = null
    ): File? {
        val collectionDirectory =
            File(File(figuresRoot, "automatic"), safeFileStem(collection))
        val analysisDirectoryName = safeFileStem(analysisId)
        val executionDirectoryName = safeFileStem(executionId)
        val manifests =
            collectionDirectory.listFiles()
                .orEmpty()
                .asSequence()
                .filter(File::isDirectory)
                .sortedByDescending(File::getName)
                .map { dateDirectory ->
                    File(
                        File(File(dateDirectory, analysisDirectoryName), executionDirectoryName),
                        MANIFEST_FILE_NAME
                    )
                }
                .filter(File::isFile)
        for (manifest in manifests) {
            val reusable =
                reusableGeneratedFigure(
                    manifestFile = manifest,
                    figuresRoot = figuresRoot,
                    collection = collection,
                    analysisId = analysisId,
                    executionId = executionId,
                    title = title,
                    figureType = figureType,
                    dataFingerprint = dataFingerprint,
                    presentationFingerprint = presentationFingerprint
                )
            if (reusable != null) return reusable
        }
        return null
    }

    internal fun safeFileStem(title: String): String {
        val normalized =
            title.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(64)
        return normalized.ifBlank { "scientific-figure" }
    }

    internal fun automaticRelativeDirectory(
        collection: String,
        date: String,
        analysisId: String,
        executionId: String,
        figureType: String
    ): String =
        listOf(
            "automatic",
            safeFileStem(collection),
            safeFileStem(date),
            safeFileStem(analysisId),
            safeFileStem(executionId),
            safeFileStem(figureType)
        ).joinToString(File.separator)

    internal fun newAutomaticFigureExecutionId(now: LocalDateTime = LocalDateTime.now()): String =
        "run-${EXECUTION_TIMESTAMP_FORMAT.format(now)}-${UUID.randomUUID().toString().take(8)}"

    internal fun automaticDataFingerprint(value: Any?): String =
        sha256(value?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()).take(16)

    internal fun automaticExportTrigger(
        collection: String,
        analysisId: String,
        executionId: String,
        title: String,
        figureType: String,
        dataKey: Any?,
        presentationKey: Any?
    ): AutomaticFigureExportTrigger =
        AutomaticFigureExportTrigger(
            collection = collection,
            analysisId = analysisId,
            executionId = executionId,
            title = title,
            figureType = figureType,
            dataFingerprint = automaticDataFingerprint(dataKey),
            presentationFingerprint = automaticDataFingerprint(presentationKey)
        )

    internal fun supportsAutomaticStaticExport(title: String): Boolean {
        val normalised = title.lowercase()
        return !(normalised.contains("interactive 3d") || normalised.contains("3d heat map"))
    }

    internal fun immutableDisplayName(figuresDirectory: File, requestedStem: String): String {
        figuresDirectory.mkdirs()
        var candidate = "$requestedStem.png"
        var suffix = 2
        while (File(figuresDirectory, candidate).exists()) {
            candidate = "$requestedStem-$suffix.png"
            suffix += 1
        }
        return candidate
    }

    internal fun appendManifestEntry(
        manifestFile: File,
        entry: AutomaticFigureManifestEntry
    ) {
        require(entry.relativePath.isNotBlank()) { "Figure relative path is required." }
        require(entry.executionId.isNotBlank()) { "Figure execution id is required." }
        when (entry.status) {
            AutomaticFigureExportStatus.EXPECTED -> {
                require(entry.sha256 == null) { "Expected figures cannot already have a hash." }
                require(entry.failureMessage == null) { "Expected figures cannot have a failure." }
            }
            AutomaticFigureExportStatus.GENERATED -> {
                require(entry.sha256?.let(SHA256_PATTERN::matches) == true) {
                    "Generated figure SHA-256 is invalid."
                }
                require(entry.failureMessage == null) { "Generated figures cannot have a failure." }
            }
            AutomaticFigureExportStatus.FAILED -> {
                require(entry.sha256 == null) { "Failed figures cannot have a completed hash." }
                require(!entry.failureMessage.isNullOrBlank()) { "Failed figures need a reason." }
            }
        }
        synchronized(MANIFEST_LOCK) {
            val properties = Properties()
            if (manifestFile.exists()) {
                manifestFile.inputStream().buffered().use(properties::load)
            }
            val count = properties.getProperty("entryCount")?.toIntOrNull() ?: 0
            val existingIndex =
                (0 until count).firstOrNull { index ->
                    properties.getProperty("entry.$index.relativePath") == entry.relativePath
                }
            if (existingIndex != null) {
                val previousStatus =
                    properties.getProperty("entry.$existingIndex.status")
                        ?.let(AutomaticFigureExportStatus::valueOf)
                require(
                    previousStatus == AutomaticFigureExportStatus.EXPECTED &&
                        entry.status != AutomaticFigureExportStatus.EXPECTED
                ) { "The figure is already finalised in this immutable manifest." }
            }

            val index = existingIndex ?: count
            val prefix = "entry.$index"
            properties.setProperty("schemaVersion", MANIFEST_SCHEMA_VERSION)
            properties.setProperty("entryCount", (if (existingIndex == null) count + 1 else count).toString())
            properties.setProperty("$prefix.title", entry.title)
            properties.setProperty("$prefix.collection", entry.collection)
            properties.setProperty("$prefix.analysisId", entry.analysisId)
            properties.setProperty("$prefix.executionId", entry.executionId)
            properties.setProperty("$prefix.figureType", entry.figureType)
            properties.setProperty("$prefix.relativePath", entry.relativePath)
            if (entry.sha256 != null) {
                properties.setProperty("$prefix.sha256", entry.sha256)
            } else {
                properties.remove("$prefix.sha256")
            }
            properties.setProperty("$prefix.dataFingerprint", entry.dataFingerprint)
            if (entry.presentationFingerprint != null) {
                properties.setProperty("$prefix.presentationFingerprint", entry.presentationFingerprint)
            } else {
                properties.remove("$prefix.presentationFingerprint")
            }
            properties.setProperty("$prefix.status", entry.status.name)
            if (entry.failureMessage != null) {
                properties.setProperty("$prefix.failureMessage", entry.failureMessage)
            } else {
                properties.remove("$prefix.failureMessage")
            }
            properties.setProperty(
                "$prefix.exportedAtEpochMillis",
                entry.exportedAtEpochMillis.toString()
            )
            check(manifestFile.parentFile?.exists() == true || manifestFile.parentFile?.mkdirs() == true) {
                "The automatic figure manifest directory could not be created."
            }
            val temporary = File(manifestFile.parentFile, "${manifestFile.name}.tmp")
            try {
                temporary.outputStream().buffered().use { output ->
                    properties.store(output, "Robot Kinematics Lab immutable figure index")
                }
                moveReplacing(temporary, manifestFile)
            } finally {
                temporary.delete()
            }
        }
    }

    internal fun readManifestEntries(manifestFile: File): List<AutomaticFigureManifestEntry> {
        if (!manifestFile.exists()) return emptyList()
        val properties = Properties()
        manifestFile.inputStream().buffered().use(properties::load)
        require(properties.getProperty("schemaVersion") == MANIFEST_SCHEMA_VERSION) {
            "Unsupported automatic figure manifest schema."
        }
        val count = requireNotNull(properties.getProperty("entryCount")?.toIntOrNull()) {
            "Automatic figure manifest entry count is missing."
        }
        return (0 until count).map { index ->
            val prefix = "entry.$index"
            AutomaticFigureManifestEntry(
                title = properties.required("$prefix.title"),
                collection = properties.required("$prefix.collection"),
                analysisId = properties.required("$prefix.analysisId"),
                executionId = properties.required("$prefix.executionId"),
                figureType = properties.required("$prefix.figureType"),
                relativePath = properties.required("$prefix.relativePath"),
                sha256 = properties.getProperty("$prefix.sha256"),
                dataFingerprint = properties.required("$prefix.dataFingerprint"),
                presentationFingerprint = properties.getProperty("$prefix.presentationFingerprint"),
                exportedAtEpochMillis =
                    requireNotNull(properties.required("$prefix.exportedAtEpochMillis").toLongOrNull()) {
                        "Invalid figure export timestamp."
                    },
                status =
                    AutomaticFigureExportStatus.valueOf(properties.required("$prefix.status")),
                failureMessage = properties.getProperty("$prefix.failureMessage")
            )
        }.also { entries ->
            require(entries.map { it.relativePath }.distinct().size == entries.size) {
                "Duplicate figure paths in immutable manifest."
            }
            entries.forEach { entry ->
                when (entry.status) {
                    AutomaticFigureExportStatus.EXPECTED ->
                        require(entry.sha256 == null && entry.failureMessage == null)
                    AutomaticFigureExportStatus.GENERATED ->
                        require(entry.sha256?.let(SHA256_PATTERN::matches) == true)
                    AutomaticFigureExportStatus.FAILED ->
                        require(entry.sha256 == null && !entry.failureMessage.isNullOrBlank())
                }
            }
        }
    }

    private fun saveProjectEvidenceCopy(
        figuresDirectory: File,
        displayName: String,
        title: String,
        bitmap: android.graphics.Bitmap,
        scientificRole: String = "illustrative-export",
        metadataProperties: Map<String, String> = emptyMap()
    ): File {
        require(bitmap.width > 0 && bitmap.height > 0 && bitmap.width.toLong() * bitmap.height <= 100_000_000L) { "Invalid figure dimensions." }
        check(figuresDirectory.exists() || figuresDirectory.mkdirs()) {
            "The project figure directory could not be created."
        }
        val destination = File(figuresDirectory, displayName)
        val temporary = File(figuresDirectory, "$displayName.tmp")
        val sidecar = File(figuresDirectory, "${displayName.removeSuffix(".png")}.properties")
        val sidecarTemporary = File(figuresDirectory, "${sidecar.name}.tmp")
        try {
            temporary.outputStream().buffered().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                    "Project PNG compression failed."
                }
            }
            val identity = AppBuildIdentity.current()
            val metadata =
                Properties().apply {
                    setProperty("schemaVersion", "1")
                    setProperty("title", title)
                    setProperty("figureFile", displayName)
                    setProperty("figureSha256", sha256(temporary))
                    setProperty("mediaType", "image/png")
                    setProperty("pixelWidth", bitmap.width.toString())
                    setProperty("pixelHeight", bitmap.height.toString())
                    setProperty("renderStyle", "Academic presentation; no universal Harvard graphic standard claimed")
                    setProperty("scientificRole", scientificRole)
                    setProperty("numericSourceRequiredForReproduction", "true")
                    setProperty("exportedAtEpochMillis", System.currentTimeMillis().toString())
                    setProperty("producerApplicationId", identity.applicationId)
                    setProperty("producerVersionName", identity.versionName)
                    setProperty("producerVersionCode", identity.versionCode.toString())
                    setProperty("producerBuildType", identity.buildType)
                    metadataProperties.forEach { (key, value) -> setProperty(key, value) }
                }
            sidecarTemporary.outputStream().buffered().use { output ->
                metadata.store(output, "Robot Kinematics Lab scientific figure")
            }
            moveReplacing(temporary, destination)
            try {
                moveReplacing(sidecarTemporary, sidecar)
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
            return destination
        } finally {
            temporary.delete()
            sidecarTemporary.delete()
        }
    }

    private fun saveGalleryCopy(context: Context, projectFigure: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, projectFigure.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Robot Kinematics Lab"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val uri =
                requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
                    "Android did not provide a destination for the figure."
                }
            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Android could not open the figure destination." }
                    projectFigure.inputStream().buffered().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
            return "Pictures / Robot Kinematics Lab"
        }

        val externalPicturesDirectory =
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) {
                "Android did not provide an app picture directory."
            }
        val directory = externalPicturesDirectory.resolve("Robot Kinematics Lab")
        check(directory.exists() || directory.mkdirs()) {
            "The figure directory could not be created."
        }
        val destination = File(directory, projectFigure.name)
        projectFigure.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf("image/png"),
            null
        )
        return destination.parentFile?.absolutePath.orEmpty()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream().buffered(), digest).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (input.read(buffer) >= 0) Unit
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)?.takeIf(String::isNotBlank)) {
            "Automatic figure manifest field '$key' is missing."
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

    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    private val EXECUTION_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private const val MANIFEST_FILE_NAME = "figure-index.properties"
    private const val MANIFEST_SCHEMA_VERSION = "1"
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val EXPORT_LOCK = Any()
    private val MANIFEST_LOCK = Any()
}
