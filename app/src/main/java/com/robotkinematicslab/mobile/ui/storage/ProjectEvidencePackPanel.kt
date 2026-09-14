package com.robotkinematicslab.mobile.ui.storage

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.robotkinematicslab.mobile.storage.evidence.EvidenceLibraryCatalog
import com.robotkinematicslab.mobile.storage.evidence.EvidenceLibraryFilter
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.storage.evidence.ProjectArtifact
import com.robotkinematicslab.mobile.storage.evidence.ProjectArtifactPreview
import com.robotkinematicslab.mobile.storage.evidence.ProjectEvidencePackRepository
import com.robotkinematicslab.mobile.storage.evidence.ProjectEvidenceSnapshot
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProjectEvidencePackPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val projectIdentity = com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository.resolveActiveProjectRoot(applicationContext).canonicalPath
    val repository = remember(applicationContext, projectIdentity) { ProjectEvidencePackRepository(applicationContext) }
    val scope = rememberCoroutineScope()
    var snapshot by remember(repository) { mutableStateOf<ProjectEvidenceSnapshot?>(null) }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Building the first verified project index…") }
    var preview by remember(repository) { mutableStateOf<ProjectArtifactPreview?>(null) }
    var selectedPreviewPath by rememberSaveable(projectIdentity) { mutableStateOf<String?>(null) }
    var pendingProjectRepository by remember { mutableStateOf<ProjectEvidencePackRepository?>(null) }
    var pendingArtifactRepository by remember { mutableStateOf<ProjectEvidencePackRepository?>(null) }
    var selectedPreviewSha by rememberSaveable(projectIdentity) { mutableStateOf<String?>(null) }
    var artifactForExport by remember(repository) { mutableStateOf<ProjectArtifact?>(null) }

    fun refresh() {
        if (busy && snapshot != null) return
        busy = true
        status = "Indexing files and verifying SHA-256 checksums…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.refreshIndex() } }
                .onSuccess { refreshed ->
                    snapshot = refreshed
                    status =
                        "Verified ${refreshed.artifacts.size} files · ${formatBytes(refreshed.totalBytes)}."
                }
                .onFailure { error ->
                    status = "The project index could not be completed: ${error.message ?: "unknown error"}"
                }
            busy = false
        }
    }

    val projectExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri == null) {
                status = "Project export cancelled; no source data were changed."
            } else {
                busy = true
                status = "Creating and re-verifying the portable project package…"
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val temporary = File.createTempFile(
                                "rkl-project-evidence-",
                                ".zip",
                                applicationContext.cacheDir
                            )
                            try {
                                val exported = temporary.outputStream().buffered().use(requireNotNull(pendingProjectRepository) { "The export selection expired; open export again." }::writePortableZip)
                                try {
                                    applicationContext.contentResolver.openOutputStream(uri, "w").use { output ->
                                        requireNotNull(output) { "Android could not open the selected destination." }
                                        temporary.inputStream().buffered().use { input -> input.copyTo(output) }
                                    }
                                } catch (error: Throwable) {
                                    runCatching { applicationContext.contentResolver.delete(uri, null, null) }
                                    throw error
                                }
                                exported
                            } finally {
                                temporary.delete()
                            }
                        }
                    }.onSuccess { exported ->
                        snapshot = exported
                        status =
                            "Portable package saved · ${exported.artifacts.size} files · " +
                                "${formatBytes(exported.totalBytes)}."
                    }.onFailure { error ->
                        status = "Export stopped safely: ${error.message ?: "unknown error"}"
                    }
                    busy = false
                }
            }
        }

    val artifactExportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val artifact = artifactForExport
            artifactForExport = null
            if (uri == null || artifact == null) {
                status = "File export cancelled; the project copy remains unchanged."
            } else {
                busy = true
                status = "Exporting ${artifact.fileName}…"
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val temporary = File.createTempFile(
                                "rkl-artifact-",
                                ".verified",
                                applicationContext.cacheDir
                            )
                            try {
                                temporary.outputStream().buffered().use { output ->
                                    requireNotNull(pendingArtifactRepository) { "The export selection expired; open export again." }.copyArtifact(artifact, output)
                                }
                                try {
                                    applicationContext.contentResolver.openOutputStream(uri, "w").use { output ->
                                        requireNotNull(output) { "Android could not open the selected destination." }
                                        temporary.inputStream().buffered().use { input -> input.copyTo(output) }
                                    }
                                } catch (error: Throwable) {
                                    runCatching { applicationContext.contentResolver.delete(uri, null, null) }
                                    throw error
                                }
                            } finally {
                                temporary.delete()
                            }
                        }
                    }.onSuccess {
                        status = "${artifact.fileName} exported without changing the project source."
                    }.onFailure { error ->
                        status = "File export failed safely: ${error.message ?: "unknown error"}"
                    }
                    busy = false
                }
            }
        }

    LaunchedEffect(repository) { refresh() }
    LaunchedEffect(repository, selectedPreviewPath) {
        val path=selectedPreviewPath
        if(path!=null && preview?.artifact?.relativePath!=path) {
            runCatching { withContext(Dispatchers.IO) { repository.preview(path).also { require(selectedPreviewSha==null || it.artifact.sha256==selectedPreviewSha) { "The selected artifact changed since it was opened." } } } }.onSuccess { preview=it }.onFailure { status="Preview unavailable: ${it.message}";selectedPreviewPath=null }
        }
    }

    ProjectEvidencePackContent(
        snapshot = snapshot,
        busy = busy,
        status = status,
        modifier = modifier,
        onRefresh = ::refresh,
        onExportProject = {
            snapshot?.let { pendingProjectRepository = repository; projectExportLauncher.launch(it.archiveFileName) }
        },
        onOpenArtifact = { artifact ->
            if (!busy) {
                busy = true
                status = "Opening ${artifact.fileName}…"
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { repository.preview(artifact) } }
                        .onSuccess { loaded ->
                            selectedPreviewSha = loaded.artifact.sha256
                            selectedPreviewPath = loaded.artifact.relativePath
                            preview = loaded
                            status = "Read-only preview opened."
                        }
                        .onFailure { error ->
                            status = "Preview unavailable: ${error.message ?: "unknown error"}"
                        }
                    busy = false
                }
            }
        }
    )

    preview?.let { selectedPreview ->
        ProjectArtifactPreviewDialog(
            preview = selectedPreview,
            onDismiss = { preview = null; selectedPreviewPath = null },
            onExport = {
                pendingArtifactRepository = repository
                artifactForExport = selectedPreview.artifact
                artifactExportLauncher.launch(selectedPreview.artifact.fileName)
            }
        )
    }
}

@Composable
internal fun ProjectEvidencePackContent(
    snapshot: ProjectEvidenceSnapshot?,
    busy: Boolean,
    status: String,
    onRefresh: () -> Unit,
    onExportProject: () -> Unit,
    onOpenArtifact: (ProjectArtifact) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedCategories by rememberSaveable(snapshot?.project?.id) {
        mutableStateOf(
            if (snapshot?.categories?.containsKey("figures") == true) setOf("figures") else emptySet()
        )
    }
    var query by rememberSaveable(snapshot?.project?.id) { mutableStateOf("") }
    var categoryFilter by rememberSaveable(snapshot?.project?.id) { mutableStateOf("All") }
    var typeFilter by rememberSaveable(snapshot?.project?.id) { mutableStateOf("All") }
    var experimentFilter by rememberSaveable(snapshot?.project?.id) { mutableStateOf("") }
    var dateFrom by rememberSaveable(snapshot?.project?.id) { mutableStateOf("") }
    var dateTo by rememberSaveable(snapshot?.project?.id) { mutableStateOf("") }
    var grouping by rememberSaveable(snapshot?.project?.id) { mutableStateOf("Category") }
    val filtered = remember(snapshot,query,categoryFilter,typeFilter,experimentFilter,dateFrom,dateTo) {
        runCatching { EvidenceLibraryCatalog.filter(snapshot?.artifacts.orEmpty(),EvidenceLibraryFilter(query,categoryFilter,typeFilter,experimentFilter,dateFrom,dateTo)) }
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag(PROJECT_EVIDENCE_PACK_TAG),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "PORTABLE PROJECT EVIDENCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text("One project, one complete context", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            JargonAwareText(
                "Browse the real files below or export one verified .zip containing the datasets, models, runs, figures, manifests and a guide for another person or AI. Automatically generated PNG charts are grouped inside Figures by analysis family, date, experiment and chart type.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            snapshot?.let { current ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EvidenceMetric("FILES", current.artifacts.size.toString(), Modifier.weight(1f))
                    EvidenceMetric("FOLDERS", current.categories.size.toString(), Modifier.weight(1f))
                    EvidenceMetric("SIZE", formatBytes(current.totalBytes), Modifier.weight(1f))
                }
                Text(
                    current.rootPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                }
            }

            FilledTonalButton(
                onClick = onExportProject,
                enabled = snapshot != null && !busy,
                modifier = Modifier.fillMaxWidth().testTag(PROJECT_EXPORT_TAG)
            ) {
                Text("Export complete project for AI / review")
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(PROJECT_REFRESH_TAG)
            ) {
                Text("Rebuild manifests and verify checksums")
            }

            Text("Find project evidence",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(query,{ query=it },label={Text("Search names, experiments or sources")},modifier=Modifier.fillMaxWidth().testTag("evidence-library-search"))
            LibraryFilterMenu("Category",listOf("All")+snapshot?.categories?.keys.orEmpty(),categoryFilter,{categoryFilter=it}) { if(it=="All") it else EvidenceLibraryCatalog.categoryName(it) }
            LibraryFilterMenu("Type",listOf("All")+snapshot?.artifacts.orEmpty().map { it.format.name }.distinct().sorted(),typeFilter,{typeFilter=it}) { if(it=="All") it else com.robotkinematicslab.mobile.storage.evidence.ProjectArtifactFormat.valueOf(it).displayName }
            LibraryFilterMenu("Group by",listOf("Category","Experiment"),grouping,{grouping=it;expandedCategories=emptySet()}) { it }
            OutlinedTextField(experimentFilter,{experimentFilter=it},label={Text("Experiment contains")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(dateFrom,{dateFrom=it},label={Text("From YYYY-MM-DD")},modifier=Modifier.weight(1f))
                OutlinedTextField(dateTo,{dateTo=it},label={Text("To YYYY-MM-DD")},modifier=Modifier.weight(1f))
            }
            Text("Dates use UTC. ${filtered.getOrNull()?.size ?: 0} matching artifacts / ${snapshot?.artifacts?.size ?: 0} indexed.",modifier=Modifier.testTag("evidence-library-count"))
            filtered.exceptionOrNull()?.let { Text("Filter unavailable: ${it.message}",color=MaterialTheme.colorScheme.error) }
            if(filtered.getOrNull()?.isEmpty()==true) Text(if(snapshot?.artifacts.isNullOrEmpty()) "No evidence has been indexed in this project." else "No artifacts match these filters.")
            Text("Project folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            JargonAwareText(
                "These are the actual project folders. Opening a file is read-only; exporting creates a copy. A manifest records what belongs to the project, while SHA-256 checksums reveal whether saved bytes changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            filtered.getOrDefault(emptyList()).groupBy { if(grouping=="Category") it.category else it.experimentId ?: "Unlinked / provenance not recorded" }
                .toSortedMap().forEach { (category, artifacts) ->
                    androidx.compose.runtime.key(snapshot?.project?.id, grouping, category) {
                        val expanded=category in expandedCategories
                        ProjectFolderRow(category=category,artifacts=artifacts,expanded=expanded,onToggle={ expandedCategories=if(expanded) expandedCategories-category else expandedCategories+category },onOpenArtifact=onOpenArtifact)
                    }
                }
        }
    }
}

@Composable
private fun ProjectFolderRow(
    category: String,
    artifacts: List<ProjectArtifact>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenArtifact: (ProjectArtifact) -> Unit
) {
    var visibleArtifactCount by rememberSaveable(category, artifacts.size) {
        mutableIntStateOf(INITIAL_VISIBLE_ARTIFACTS.coerceAtMost(artifacts.size))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onToggle)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("EvidenceFolder:$category")
                        .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium)
                Text(
                    EvidenceLibraryCatalog.categoryName(category),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${artifacts.size} · ${formatBytes(artifacts.sumOf(ProjectArtifact::byteCount))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider()
                artifacts.take(visibleArtifactCount).forEach { artifact ->
                    ProjectArtifactRow(artifact = artifact, onOpen = { onOpenArtifact(artifact) })
                }
                if (visibleArtifactCount < artifacts.size) {
                    OutlinedButton(
                        onClick = {
                            visibleArtifactCount =
                                (visibleArtifactCount + ARTIFACT_PAGE_SIZE).coerceAtMost(artifacts.size)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show ${minOf(ARTIFACT_PAGE_SIZE, artifacts.size - visibleArtifactCount)} more files")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectArtifactRow(artifact: ProjectArtifact, onOpen: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = false, onClick = onOpen)
                .testTag("EvidenceFile:${artifact.relativePath}")
                .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (artifact.format.textPreviewSupported) "▤" else "◆", color = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                EvidenceLibraryCatalog.visibleName(artifact),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${artifact.format.displayName} · ${formatBytes(artifact.byteCount)} · ${EvidenceLibraryCatalog.date(artifact)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("View", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ProjectArtifactPreviewDialog(
    preview: ProjectArtifactPreview,
    onDismiss: () -> Unit,
    onExport: (() -> Unit)? = null
) {
    var columnQuery by remember(preview.artifact.relativePath) { mutableStateOf("") }
    val columnPairs =
        remember(preview, columnQuery) {
            preview.columnNames.zipAll(preview.firstRowValues)
                .filter { (name, _) -> name.contains(columnQuery, ignoreCase = true) }
                .take(MAX_VISIBLE_COLUMN_PAIRS)
        }
    val previewBitmap = remember(preview.imageBytes) { decodeImagePreview(preview.imageBytes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PROJECT_PREVIEW_DIALOG_TAG),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(EvidenceLibraryCatalog.visibleName(preview.artifact), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${preview.artifact.format.displayName} · ${formatBytes(preview.artifact.byteCount)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(preview.explanation, style = MaterialTheme.typography.bodySmall)
                Text(preview.artifact.provenanceStatus)
                preview.artifact.method?.let { Text("Creation method: $it") }
                preview.artifact.experimentId?.let { Text("Recorded experiment: $it") }
                preview.artifact.sourceDataset?.let { Text("Recorded dataset: $it") }
                preview.artifact.caption?.let { Text(it) }
                com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection(title="Technical file details",summary="Original path, file identity and checksum.") {
                    SelectionContainer { Text("${preview.artifact.relativePath}\nSHA-256 ${preview.artifact.sha256}\nOriginal filename: ${preview.artifact.fileName}",style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
                }
                if(preview.imageBytes!=null && previewBitmap==null) Text("Image preview unavailable: the file is not a decodable PNG. Original bytes remain available for inspection/export.",color=MaterialTheme.colorScheme.error)
                previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Preview of ${preview.artifact.fileName}",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                preview.columnCount?.let { count ->
                    Text("$count columns detected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (preview.columnNames.isNotEmpty()) {
                        OutlinedTextField(
                            value = columnQuery,
                            onValueChange = { columnQuery = it.take(80) },
                            label = { Text("Find a variable") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        columnPairs.forEach { (name, value) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(name.ifBlank { "Unnamed column" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(value.ifBlank { "No value in the first row" }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                }
                            }
                        }
                        if (preview.columnNames.size > columnPairs.size && columnQuery.isBlank()) {
                            Text(
                                "Showing the first $MAX_VISIBLE_COLUMN_PAIRS variables. Search by name to inspect any other column.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (preview.lines.isNotEmpty()) {
                    Text("Raw preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            preview.lines.forEachIndexed { index, line ->
                                Text(
                                    "${(index + 1).toString().padStart(3)}  $line",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (preview.truncated) {
                        Text(
                            "Preview truncated for responsiveness; export the file to inspect every row.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (onExport != null) TextButton(onClick = onExport, modifier = Modifier.testTag(PROJECT_EXPORT_FILE_TAG)) {
                Text("Export file")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun EvidenceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<String>.zipAll(values: List<String>): List<Pair<String, String>> =
    indices.map { index -> this[index] to values.getOrElse(index) { "" } }

private fun decodeImagePreview(bytes: ByteArray?): androidx.compose.ui.graphics.ImageBitmap? {
    if (bytes == null) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > MAX_PREVIEW_IMAGE_SIDE ||
        bounds.outHeight / sampleSize > MAX_PREVIEW_IMAGE_SIDE
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

internal const val PROJECT_EVIDENCE_PACK_TAG = "project-evidence-pack"
internal const val PROJECT_EXPORT_TAG = "project-evidence-export"
internal const val PROJECT_REFRESH_TAG = "project-evidence-refresh"
internal const val PROJECT_PREVIEW_DIALOG_TAG = "project-evidence-preview"
internal const val PROJECT_EXPORT_FILE_TAG = "project-evidence-export-file"
private const val MAX_VISIBLE_COLUMN_PAIRS = 64
private const val INITIAL_VISIBLE_ARTIFACTS = 40
private const val ARTIFACT_PAGE_SIZE = 40
private const val MAX_PREVIEW_IMAGE_SIDE = 1_600


@Composable
private fun LibraryFilterMenu(label:String,options:List<String>,selected:String,onSelect:(String)->Unit,display:(String)->String) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) { Text("$label: ${display(selected)}") }
        androidx.compose.material3.DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            options.forEach { option -> androidx.compose.material3.DropdownMenuItem(text={Text(display(option))},onClick={onSelect(option);open=false}) }
        }
    }
}
