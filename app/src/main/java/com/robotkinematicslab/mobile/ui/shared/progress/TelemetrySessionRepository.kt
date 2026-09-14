package com.robotkinematicslab.mobile.ui.shared.progress

import android.content.Context
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties

internal data class StoredTelemetrySession(
    val id: String,
    val projectName: String,
    val sessionType: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val sampleCount: Int,
    val fingerprint: String,
    val chartGroups: Map<TelemetryDashboardCategory, List<TelemetryChartGroup>>,
    val directoryPath: String
)

internal class TelemetrySessionRepository(
    private val rootDirectory: File
) {
    constructor(context: Context) : this(
        AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData).telemetryDirectory
    )

    @Synchronized
    fun save(
        projectName: String,
        sessionType: String,
        title: String,
        samples: List<DiagnosticPerformanceSample>
    ): StoredTelemetrySession {
        require(samples.isNotEmpty()) { "A telemetry session needs at least one sample." }
        rootDirectory.mkdirs()
        val normalizedProject = projectName.trim().ifBlank { DEFAULT_PROJECT_NAME }
        val normalizedType = sessionType.trim().ifBlank { "Operation" }
        val normalizedTitle = title.trim().ifBlank { "$normalizedType telemetry" }
        val fingerprint = telemetryFingerprint(normalizedProject, normalizedType, normalizedTitle, samples)
        listSessions().firstOrNull { it.fingerprint == fingerprint }?.let { return it }

        val createdAt = System.currentTimeMillis()
        val id = "${createdAt}-${safeFileStem(normalizedType)}-${fingerprint.take(10)}"
        val directory = File(File(rootDirectory, safeFileStem(normalizedProject)), id).apply { mkdirs() }
        val groups = buildTelemetryChartGroups(samples)

        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("id", id)
                setProperty("projectName", normalizedProject)
                setProperty("sessionType", normalizedType)
                setProperty("title", normalizedTitle)
                setProperty("createdAtEpochMillis", createdAt.toString())
                setProperty("sampleCount", samples.size.toString())
                setProperty("fingerprint", fingerprint)
            }
        val curvesFile = File(directory, CURVES_FILE)
        writeCurvesAtomically(curvesFile, groups)
        val curvesFingerprint = fileFingerprint(curvesFile)
        properties.setProperty(CURVES_FINGERPRINT_KEY, curvesFingerprint)
        properties.setProperty(
            INTEGRITY_FINGERPRINT_KEY,
            sessionIntegrityFingerprint(
                id = id,
                projectName = normalizedProject,
                sessionType = normalizedType,
                title = normalizedTitle,
                createdAtEpochMillis = createdAt,
                sampleCount = samples.size,
                telemetryFingerprint = fingerprint,
                curvesFingerprint = curvesFingerprint
            )
        )
        writePropertiesAtomically(File(directory, MANIFEST_FILE), properties)

        return StoredTelemetrySession(
            id = id,
            projectName = normalizedProject,
            sessionType = normalizedType,
            title = normalizedTitle,
            createdAtEpochMillis = createdAt,
            sampleCount = samples.size,
            fingerprint = fingerprint,
            chartGroups = groups,
            directoryPath = directory.absolutePath
        )
    }

    @Synchronized
    fun listSessions(): List<StoredTelemetrySession> {
        if (!rootDirectory.isDirectory) return emptyList()
        return rootDirectory.listFiles(File::isDirectory).orEmpty()
            .flatMap { project -> project.listFiles(File::isDirectory).orEmpty().asList() }
            .mapNotNull(::loadSession)
            .sortedByDescending { it.createdAtEpochMillis }
    }

    private fun loadSession(directory: File): StoredTelemetrySession? =
        runCatching {
            val manifest = Properties().apply {
                File(directory, MANIFEST_FILE).inputStream().buffered().use(::load)
            }
            require(manifest.getProperty("schemaVersion")?.toIntOrNull() == SCHEMA_VERSION) {
                "Unsupported telemetry session schema."
            }
            val id = requireManifestText(manifest, "id")
            val projectName = requireManifestText(manifest, "projectName")
            val sessionType = requireManifestText(manifest, "sessionType")
            val title = requireManifestText(manifest, "title")
            val createdAtEpochMillis =
                requireNotNull(manifest.getProperty("createdAtEpochMillis")?.toLongOrNull()) {
                    "Telemetry session creation time is missing or invalid."
                }.also { require(it > 0L) { "Telemetry session creation time must be positive." } }
            val sampleCount =
                requireNotNull(manifest.getProperty("sampleCount")?.toIntOrNull()) {
                    "Telemetry session sample count is missing or invalid."
                }.also { require(it > 0) { "Telemetry session sample count must be positive." } }
            val fingerprint = requireManifestText(manifest, "fingerprint")
            val curvesFile = File(directory, CURVES_FILE)
            val storedCurvesFingerprint = manifest.getProperty(CURVES_FINGERPRINT_KEY)?.trim()?.takeIf(String::isNotEmpty)
            val storedIntegrityFingerprint = manifest.getProperty(INTEGRITY_FINGERPRINT_KEY)?.trim()?.takeIf(String::isNotEmpty)
            require((storedCurvesFingerprint == null) == (storedIntegrityFingerprint == null)) {
                "Telemetry session integrity metadata is incomplete."
            }
            if (storedCurvesFingerprint != null && storedIntegrityFingerprint != null) {
                require(fileFingerprint(curvesFile) == storedCurvesFingerprint) {
                    "Telemetry curve evidence no longer matches its saved fingerprint."
                }
                require(
                    sessionIntegrityFingerprint(
                        id = id,
                        projectName = projectName,
                        sessionType = sessionType,
                        title = title,
                        createdAtEpochMillis = createdAtEpochMillis,
                        sampleCount = sampleCount,
                        telemetryFingerprint = fingerprint,
                        curvesFingerprint = storedCurvesFingerprint
                    ) == storedIntegrityFingerprint
                ) {
                    "Telemetry session metadata no longer matches its saved fingerprint."
                }
            }
            StoredTelemetrySession(
                id = id,
                projectName = projectName,
                sessionType = sessionType,
                title = title,
                createdAtEpochMillis = createdAtEpochMillis,
                sampleCount = sampleCount,
                fingerprint = fingerprint,
                chartGroups = readCurves(curvesFile),
                directoryPath = directory.absolutePath
            )
        }.getOrNull()

    private fun writeCurvesAtomically(
        destination: File,
        groups: Map<TelemetryDashboardCategory, List<TelemetryChartGroup>>
    ) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.bufferedWriter().use { writer ->
            writer.appendLine(CURVE_HEADER)
            groups.forEach { (category, categoryGroups) ->
                categoryGroups.forEachIndexed { groupIndex, group ->
                    group.series.forEachIndexed { seriesIndex, series ->
                        series.points.forEach { point ->
                            writer.appendLine(
                                listOf(
                                    category.name,
                                    groupIndex.toString(),
                                    group.id,
                                    encode(group.title),
                                    encode(group.subtitle),
                                    encode(group.unit),
                                    seriesIndex.toString(),
                                    series.id,
                                    encode(series.label),
                                    point.sampleIndex.toString(),
                                    point.elapsedSeconds.toString(),
                                    point.value.toString(),
                                    point.breakBefore.toString()
                                ).joinToString("\t")
                            )
                        }
                    }
                }
            }
        }
        moveReplacing(temporary, destination)
    }

    private fun readCurves(file: File): Map<TelemetryDashboardCategory, List<TelemetryChartGroup>> {
        data class PointRow(
            val category: TelemetryDashboardCategory,
            val groupOrder: Int,
            val groupId: String,
            val title: String,
            val subtitle: String,
            val unit: String,
            val seriesOrder: Int,
            val seriesId: String,
            val seriesLabel: String,
            val point: TelemetryChartPoint
        )

        val rows = file.useLines { lines ->
            val iterator = lines.iterator()
            require(iterator.hasNext()) { "Telemetry curve header is missing." }
            val header = iterator.next()
            require(header == CURVE_HEADER || header == LEGACY_CURVE_HEADER) { "Telemetry curve header is unsupported." }
            val expectedFields = if (header == CURVE_HEADER) 13 else 12
            iterator.asSequence().mapIndexed { rowIndex, line ->
                val fields = line.split('\t')
                require(fields.size == expectedFields) {
                    "Telemetry curve row ${rowIndex + 2} has ${fields.size} fields; expected $expectedFields."
                }
                val groupOrder = fields[1].toInt()
                val seriesOrder = fields[6].toInt()
                val sampleIndex = fields[9].toInt()
                val elapsedSeconds = fields[10].toDouble()
                val value = fields[11].toDouble()
                require(groupOrder >= 0 && seriesOrder >= 0 && sampleIndex >= 0) {
                    "Telemetry curve row ${rowIndex + 2} contains a negative index."
                }
                require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0 && value.isFinite()) {
                    "Telemetry curve row ${rowIndex + 2} contains a non-finite or negative time value."
                }
                PointRow(
                    category = TelemetryDashboardCategory.valueOf(fields[0]),
                    groupOrder = groupOrder,
                    groupId = fields[2].also { require(it.isNotBlank()) },
                    title = decode(fields[3]),
                    subtitle = decode(fields[4]) + if(expectedFields == 12) " Legacy captures have no recorded continuity flags; connections are not inferred." else "",
                    unit = decode(fields[5]),
                    seriesOrder = seriesOrder,
                    seriesId = fields[7].also { require(it.isNotBlank()) },
                    seriesLabel = decode(fields[8]),
                    point = TelemetryChartPoint(sampleIndex, elapsedSeconds, value,
                        breakBefore = if (expectedFields == 13) requireNotNull(fields[12].toBooleanStrictOrNull()) else true)
                )
            }.toList()
        }
        require(rows.isNotEmpty()) { "Telemetry curve file has no data rows." }

        return rows.groupBy { it.category }.mapValues { (_, categoryRows) ->
            categoryRows.groupBy { it.groupId }.values
                .sortedBy { it.first().groupOrder }
                .map { groupRows ->
                    val first = groupRows.first()
                    require(
                        groupRows.all { row ->
                            row.groupOrder == first.groupOrder &&
                                row.title == first.title &&
                                row.subtitle == first.subtitle &&
                                row.unit == first.unit
                        }
                    ) { "Telemetry curve group '${first.groupId}' has conflicting metadata." }
                    TelemetryChartGroup(
                        id = first.groupId,
                        title = first.title,
                        subtitle = first.subtitle,
                        unit = first.unit,
                        series =
                            groupRows.groupBy { it.seriesId }.values
                                .sortedBy { it.first().seriesOrder }
                                .map { seriesRows ->
                                    val firstSeries = seriesRows.first()
                                    require(
                                        seriesRows.all { row ->
                                            row.seriesOrder == firstSeries.seriesOrder &&
                                                row.seriesLabel == firstSeries.seriesLabel
                                        }
                                    ) { "Telemetry curve series '${firstSeries.seriesId}' has conflicting metadata." }
                                    require(seriesRows.map { it.point.sampleIndex }.distinct().size == seriesRows.size) {
                                        "Telemetry curve series '${firstSeries.seriesId}' repeats a sample index."
                                    }
                                    TelemetryChartSeries(
                                        id = firstSeries.seriesId,
                                        label = firstSeries.seriesLabel,
                                        points = seriesRows.map { it.point }.sortedBy { it.elapsedSeconds }
                                    )
                                }
                    )
                }
        }
    }

    private fun writePropertiesAtomically(destination: File, properties: Properties) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.outputStream().buffered().use { properties.store(it, "Telemetry chart session") }
        moveReplacing(temporary, destination)
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

    private fun telemetryFingerprint(
        projectName: String,
        sessionType: String,
        title: String,
        samples: List<DiagnosticPerformanceSample>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(projectName, sessionType, title, samples.size.toString()).forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(byteArrayOf(0))
        }
        samples.forEach { sample ->
            digest.update(sample.toString().toByteArray(Charsets.UTF_8))
            digest.update(byteArrayOf(0))
        }
        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }

    private fun fileFingerprint(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sessionIntegrityFingerprint(
        id: String,
        projectName: String,
        sessionType: String,
        title: String,
        createdAtEpochMillis: Long,
        sampleCount: Int,
        telemetryFingerprint: String,
        curvesFingerprint: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            SCHEMA_VERSION.toString(),
            id,
            projectName,
            sessionType,
            title,
            createdAtEpochMillis.toString(),
            sampleCount.toString(),
            telemetryFingerprint,
            curvesFingerprint
        ).forEach { value ->
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(byteArrayOf(0))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeFileStem(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(100).ifBlank { "project" }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private fun requireManifestText(manifest: Properties, key: String): String =
        requireNotNull(manifest.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)) {
            "Telemetry session manifest field '$key' is missing."
        }

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MANIFEST_FILE = "session.properties"
        private const val CURVES_FILE = "telemetry-curves.tsv"
        private const val CURVES_FINGERPRINT_KEY = "curvesSha256"
        private const val INTEGRITY_FINGERPRINT_KEY = "integritySha256"
        private const val DEFAULT_PROJECT_NAME = "Robot Kinematics Research"
        private const val LEGACY_CURVE_HEADER =
            "category\tgroupOrder\tgroupId\ttitleBase64\tsubtitleBase64\tunitBase64\t" +
                "seriesOrder\tseriesId\tseriesLabelBase64\tsampleIndex\telapsedSeconds\tvalue"
        private const val CURVE_HEADER = LEGACY_CURVE_HEADER + "\tbreakBefore"
    }
}
