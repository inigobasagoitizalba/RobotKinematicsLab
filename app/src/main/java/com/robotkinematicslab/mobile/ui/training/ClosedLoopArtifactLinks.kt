package com.robotkinematicslab.mobile.ui.training

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.robotkinematicslab.mobile.ml.closedloop.ClosedLoopEvent
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.storage.evidence.ProjectArtifactPreview
import com.robotkinematicslab.mobile.storage.evidence.ProjectEvidencePackRepository
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.charts.presentation.AutomaticFigureExportStatus
import com.robotkinematicslab.mobile.ui.storage.ProjectArtifactPreviewDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Opens only exact event paths and figures whose recorded analysis id matches this run/session. */
@Composable
internal fun ClosedLoopArtifactLinks(sessionId: String, event: ClosedLoopEvent) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { ProjectEvidencePackRepository(context) }
    val paths = remember(context) { AppStoragePaths(context) }
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ProjectArtifactPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var figures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun open(path: String) { scope.launch {
        runCatching { withContext(Dispatchers.IO) {
            val file = File(path).canonicalFile
            require(file.toPath().startsWith(paths.rootDirectory.canonicalFile.toPath())) { "Artifact is outside the active project." }
            repository.preview(file.relativeTo(paths.rootDirectory.canonicalFile).path)
        } }.onSuccess { preview = it }.onFailure { message = it.message ?: "Artifact unavailable." }
    } }
    event.datasetPath?.let { path ->
        TextButton(onClick = { open(path) }) { Text("Inspect current source CSV") }
        Text("The CSV may include later appended rows. The event’s corpus digest identifies the original training bytes; this preview does not reconstruct them.")
    }
    event.modelPath?.let { path -> TextButton(onClick = { open(path) }) { Text("Inspect this attempt’s model artifact") } }
    TextButton(onClick = { scope.launch {
        runCatching { withContext(Dispatchers.IO) {
            val snapshot = repository.inspect()
            snapshot.artifacts.filter { it.fileName == "figure-index.properties" }.flatMap { index ->
                ChartFigureExporter.readManifestEntries(File(snapshot.rootPath, index.relativePath)).filter {
                    it.status == AutomaticFigureExportStatus.GENERATED && (it.analysisId == event.runId || it.analysisId == sessionId)
                }.mapNotNull { figure ->
                    val file = File(paths.figuresDirectory, figure.relativePath).canonicalFile
                    val artifact = snapshot.artifacts.firstOrNull { File(snapshot.rootPath, it.relativePath).canonicalFile == file }
                    if(artifact != null && artifact.sha256 == figure.sha256) figure.title to file.path else null
                }
            }.distinct()
        } }.onSuccess { figures = it; message = if(it.isEmpty()) "No verified figure is indexed for this exact run or session." else "Verified indexed figures; session figures describe the whole loop." }
            .onFailure { message = it.message ?: "Figure index unavailable." }
    } }) { Text("Find figures for this run and session") }
    figures.forEach { (title, path) -> TextButton(onClick = { open(path) }) { Text(title) } }
    message?.let { Text(it) }
    preview?.let { ProjectArtifactPreviewDialog(it, onDismiss = { preview = null }) }
}
