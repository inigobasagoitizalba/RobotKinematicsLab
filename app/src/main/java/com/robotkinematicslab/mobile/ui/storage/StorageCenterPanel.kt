package com.robotkinematicslab.mobile.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.dataset.RobotLibraryRepository
import com.robotkinematicslab.mobile.storage.AppStorageRepository
import com.robotkinematicslab.mobile.storage.AppStorageSnapshot
import com.robotkinematicslab.mobile.storage.DiagnosticSessionSummary
import com.robotkinematicslab.mobile.storage.StorageCategory
import com.robotkinematicslab.mobile.storage.StorageCategorySummary
import com.robotkinematicslab.mobile.storage.bundled.BundledResearchPackInstaller
import com.robotkinematicslab.mobile.storage.bundled.BundledResearchPackStatus
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.process.ResearchProcessCoordinator
import com.robotkinematicslab.mobile.process.ResearchProcessIds
import com.robotkinematicslab.mobile.process.ResearchProcessKind
import com.robotkinematicslab.mobile.ui.shared.progress.StoredTelemetryComparisonWorkspace
import com.robotkinematicslab.mobile.ui.shared.progress.TelemetrySessionRepository
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudySummary
import com.robotkinematicslab.mobile.workspace.storage.RobotWorkspaceStudyRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageCenterPanel(
    modifier: Modifier = Modifier,
    onOpenRobots: () -> Unit,
    onOpenDatasets: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenTraining: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AppStorageRepository(context) }
    val robotRepository = remember(context) { RobotLibraryRepository(context) }
    val datasetRepository = remember(context) { DatasetStorageRepository(context) }
    val trainingRepository = remember(context) { TrainingStorageRepository(context) }
    val telemetryRepository = remember(context) { TelemetrySessionRepository(context) }
    val workspaceRepository = remember(context) { RobotWorkspaceStudyRepository(context) }
    val bundledInstaller = remember(context) { BundledResearchPackInstaller(context) }
    val scope = rememberCoroutineScope()
    val processCoordinator = remember(context) { ResearchProcessCoordinator.get(context) }
    val trackedProcesses by processCoordinator.processes.collectAsState()
    val researchPackProcessActive =
        trackedProcesses.any {
            it.id == ResearchProcessIds.RESEARCH_PACK_IMPORT && it.status.isActive
        }
    var robots by remember { mutableStateOf(robotRepository.loadOrCreateDefaults()) }
    var robotRecoveryCopies by remember { mutableStateOf(robotRepository.listRecoveryCopies()) }
    var datasetIndex by remember { mutableStateOf(datasetRepository.inspectManifestIndex()) }
    var datasetManifestMessage by remember { mutableStateOf<String?>(null) }
    var trainingRuns by remember { mutableStateOf(trainingRepository.listRuns()) }
    var telemetrySessionCount by remember { mutableIntStateOf(telemetryRepository.listSessions().size) }
    var workspaceStudies by remember { mutableStateOf(workspaceRepository.listStudies()) }
    var snapshot by remember { mutableStateOf(repository.snapshot()) }
    var selectedCategory by remember { mutableStateOf<StorageCategory?>(null) }
    var bundledStatus by remember { mutableStateOf(bundledInstaller.inspect()) }
    var bundledImporting by remember { mutableStateOf(false) }
    var bundledProgress by remember { mutableStateOf(bundledStatus.message) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("storage-root")
                .tutorialAnchor(TutorialTargets.Storage),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Project Storage",
            style = MaterialTheme.typography.headlineSmall
        )
        JargonHelpNotice()
        JargonAwareText(
            text =
                "One durable, organized home for robots, experiment sessions, datasets, AI models and training runs. App updates keep these files; uninstalling the app may remove app-private storage.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StorageCard(title = "Storage overview") {
            Text(
                text = snapshot.rootPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("${snapshot.categories.sumOf { it.fileCount }} files · ${formatBytes(snapshot.categories.sumOf { it.byteCount })}")
            OutlinedButton(
                onClick = {
                    robots = robotRepository.loadOrCreateDefaults()
                    robotRecoveryCopies = robotRepository.listRecoveryCopies()
                    datasetIndex = datasetRepository.inspectManifestIndex()
                    trainingRuns = trainingRepository.listRuns()
                    telemetrySessionCount = telemetryRepository.listSessions().size
                    workspaceStudies = workspaceRepository.listStudies()
                    snapshot = repository.snapshot()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh storage index")
            }
            if (robotRecoveryCopies.isNotEmpty()) {
                Text(
                    "Robot-library recovery evidence: ${robotRecoveryCopies.size} preserved file(s).",
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    robotRecoveryCopies.first().absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ProjectEvidencePackPanel()

        Text(
            text = "Stored evidence",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Open a category to inspect its records here. Use the action inside to continue in the screen that owns those data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("storage-categories")
                    .tutorialAnchor(TutorialTargets.StorageCategories),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            snapshot.categories.forEach { categorySummary ->
                val isSelected = selectedCategory == categorySummary.category
                StorageCategoryCard(
                    summary = categorySummary,
                    itemCount =
                        when (categorySummary.category) {
                            StorageCategory.ROBOTS -> robots.size
                            StorageCategory.SESSIONS ->
                                snapshot.diagnosticSessions.size + telemetrySessionCount + workspaceStudies.size
                            StorageCategory.DATASETS -> datasetIndex.totalEntryCount
                            StorageCategory.MODELS,
                            StorageCategory.TRAINING -> categorySummary.fileCount
                        },
                    selected = isSelected,
                    onToggle = {
                        selectedCategory = if (isSelected) null else categorySummary.category
                    }
                )

                if (isSelected) {
                    when (categorySummary.category) {
                    StorageCategory.ROBOTS ->
                        RobotStorageDetails(
                            robots = robots,
                            onOpenRobots = onOpenRobots
                        )

                    StorageCategory.SESSIONS -> {
                        StorageAccessCard(
                            eyebrow = "RUNS AND TELEMETRY",
                            title = "Continue with experimental evidence",
                            description =
                                "Review the stored reports and telemetry below, open Diagnostics to create another benchmark, or return to the 3D Workspace for saved reach studies.",
                            primaryLabel = "Open Diagnostics",
                            primaryTag = "StorageDirectAccess:SESSIONS",
                            onPrimary = onOpenDiagnostics,
                            secondaryLabel = "Open 3D Workspace",
                            secondaryTag = "StorageDirectAccess:WORKSPACES",
                            onSecondary = onOpenWorkspace
                        )
                        DiagnosticSessionsList(snapshot.diagnosticSessions)
                        WorkspaceStudiesList(workspaceStudies)
                        StorageDetailCard("Diagram comparison workspace") {
                            Text(
                                "$telemetrySessionCount chart sessions belong to this project. The library below can also read compatible curves from every other project for a controlled overlay."
                            )
                            Text(
                                "Charts are rebuilt from saved numeric curves. Comparison is read-only: it does not activate another project, rerun an experiment or alter source evidence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StoredTelemetryComparisonWorkspace()
                    }

                    StorageCategory.DATASETS ->
                        DatasetStorageDetails(
                            datasets = datasetIndex.validManifests,
                            corruptManifests = datasetIndex.corruptManifests,
                            quarantineMessage = datasetManifestMessage,
                            onOpenDatasets = onOpenDatasets,
                            onQuarantineManifest = { corruptManifest ->
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            datasetRepository.quarantineCorruptManifest(corruptManifest)
                                        }
                                    }.onSuccess { quarantinedFile ->
                                        datasetManifestMessage =
                                            "Manifest moved to recoverable quarantine: ${quarantinedFile.name}. CSV data was not changed."
                                        datasetIndex = datasetRepository.inspectManifestIndex()
                                        snapshot = repository.snapshot()
                                    }.onFailure { error ->
                                        datasetManifestMessage =
                                            "Manifest was not quarantined: ${error.message ?: "unknown storage error"}"
                                    }
                                }
                            }
                        )

                    StorageCategory.MODELS ->
                        ModelStorageDetails(
                            runs = trainingRuns,
                            indexedFileCount = categorySummary.fileCount,
                            modelsDirectoryPath = categorySummary.directoryPath,
                            onOpenModels = onOpenModels
                        )

                    StorageCategory.TRAINING -> {
                        StorageAccessCard(
                            eyebrow = "TRAINING EVIDENCE",
                            title = "Inspect or continue model training",
                            description =
                                "The registry below shows completed runs and held-out metrics. Open AI Training for full iteration histories, configurations and new controlled runs.",
                            primaryLabel = "Open Training Runs",
                            primaryTag = "StorageDirectAccess:TRAINING",
                            onPrimary = onOpenTraining
                        )
                        TrainingRunsList(trainingRuns)
                    }
                    }
                }
            }
        }

        BundledResearchPackCard(
            status = bundledStatus,
            importing = bundledImporting || researchPackProcessActive,
            progressMessage = bundledProgress,
            onInstall = {
                if (!bundledImporting && !researchPackProcessActive) {
                    bundledImporting = true
                    processCoordinator.launch(
                        id = ResearchProcessIds.RESEARCH_PACK_IMPORT,
                        title = "Research pack import",
                        kind = ResearchProcessKind.DATASET
                    ) { reporter ->
                        runCatching {
                            withContext(Dispatchers.IO) {
                                bundledInstaller.installIfNeeded { update ->
                                    reporter.report(
                                        progressFraction = update.fraction,
                                        stage = "Verifying bundled evidence",
                                        detail = update.message
                                    )
                                    scope.launch {
                                        bundledProgress =
                                            "${update.completedArtifacts}/${update.totalArtifacts} · ${update.message}"
                                    }
                                }
                            }
                        }.onSuccess { result ->
                            bundledStatus = result.status
                            bundledProgress = result.status.message
                            datasetIndex = datasetRepository.inspectManifestIndex()
                            trainingRuns = trainingRepository.listRuns()
                            snapshot = repository.snapshot()
                            reporter.completed(result.status.message)
                        }.onFailure { error ->
                            bundledStatus = bundledInstaller.inspect()
                            bundledProgress = "Import stopped safely: ${error.message ?: error::class.java.simpleName}"
                            reporter.failed(bundledProgress)
                        }
                        bundledImporting = false
                    }.onFailure { error ->
                        bundledImporting = false
                        bundledProgress = error.message ?: "Research pack import could not be started."
                    }
                }
            },
            onRefresh = {
                bundledStatus = bundledInstaller.inspect()
                bundledProgress = bundledStatus.message
                datasetIndex = datasetRepository.inspectManifestIndex()
                trainingRuns = trainingRepository.listRuns()
                snapshot = repository.snapshot()
            },
            onOpenDatasets = onOpenDatasets,
            onOpenModels = onOpenModels
        )

        StorageCard(title = "What Storage preserves") {
            StorageDetailCard("Robot definitions") {
                JargonAwareText("The shared DH library plus the active Robot Lab robot, joints, target and FK/IK mode.")
            }
            StorageDetailCard("Diagnostic and telemetry sessions") {
                JargonAwareText("Readable reports, complete run and case CSV files, manifests, reusable telemetry curves and comparison selections.")
            }
            StorageDetailCard("3D workspace studies") {
                JargonAwareText("The robot snapshot, deterministic seed, sampled joint sequence, occupancy voxels and convergence evidence used to reproduce a workspace.")
            }
            StorageDetailCard("Datasets, models and training") {
                JargonAwareText("Dataset CSV files and manifests; model weights and feature schemas; training configuration, held-out evaluation and iteration history.")
            }
            Text(
                text = "Legacy robot and dataset files are copied into this structure once when no organized destination exists; originals are left untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BundledResearchPackCard(
    status: BundledResearchPackStatus,
    importing: Boolean,
    progressMessage: String,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDatasets: () -> Unit,
    onOpenModels: () -> Unit
) {
    StorageCard(
        title = "Bundled APK research library",
        modifier =
            Modifier
                .testTag("storage-bundled-library")
                .tutorialAnchor(TutorialTargets.StorageBundledLibrary)
    ) {
        Text(
            "What this is",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "A research pack is a versioned collection of datasets and trained models created on the computer and compressed inside this APK. Importing it makes those artifacts available on the phone without recalculating them.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!status.available) {
            Text(status.message, color = MaterialTheme.colorScheme.error)
            return@StorageCard
        }
        Text(status.displayName)
        Text(
            "${status.datasetCount} ready-made datasets · ${status.modelCount} trained models " +
                "(${status.classifierModelCount} classifiers + ${status.oneMicronModelCount} certified IK)"
        )
        Text(
            if (status.installed) {
                "Ready on this device · ${formatBytes(status.installedBytes)} expanded model/dataset data"
            } else {
                "Stored compressed inside this APK; it will be imported without recalculating the datasets."
            },
            color =
                if (status.installed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
        )
        Text(
            progressMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!status.installed) {
            FilledTonalButton(
                onClick = onInstall,
                enabled = !importing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (importing) "Importing verified files…" else "Import bundled research pack")
            }
        }
        OutlinedButton(onClick = onRefresh, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh pack status")
        }
        if (status.installed) {
            FilledTonalButton(
                onClick = onOpenDatasets,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("StoragePackAccess:DATASETS")
            ) {
                Text("Browse imported datasets")
            }
            OutlinedButton(
                onClick = onOpenModels,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("StoragePackAccess:MODELS")
            ) {
                Text("Open imported models")
            }
        }
        Text(
            "The original compressed files remain inside the APK. Imported copies use reserved versioned names, and later app starts or updates do not overwrite datasets you extend on the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WorkspaceStudiesList(studies: List<RobotWorkspaceStudySummary>) {
    if (studies.isEmpty()) {
        StorageDetailCard("3D workspace studies") {
            Text("No robot workspace study has been stored yet.")
            Text("Use Workspace to generate a reproducible 3D reach study.")
        }
        return
    }

    var showAll by remember(studies.size) { mutableStateOf(false) }
    val visibleStudies = if (showAll) studies else studies.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageCollectionSummary(
        title = "3D workspace studies",
        count = studies.size,
        itemName = "studies"
    )
    visibleStudies.forEach { study ->
        StorageDetailCard(study.studyName) {
            Text(
                "${study.sampleCount} samples · ${study.voxelResolution}³ grid · " +
                    "${String.format(Locale.US, "%.2f", study.observedEnvelopeFraction * 100.0)}% observed"
            )
            Text(formatDate(study.createdAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            Text(
                study.directoryPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    StorageShowAllButton(
        total = studies.size,
        showAll = showAll,
        itemName = "studies",
        onToggle = { showAll = !showAll }
    )
}

@Composable
private fun StorageCategoryCard(
    summary: StorageCategorySummary,
    itemCount: Int,
    selected: Boolean,
    onToggle: () -> Unit
) {
    StorageCard(title = "${summary.category.symbol()}  ${summary.category.title}") {
        Text(summary.category.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$itemCount indexed items")
            Text(formatBytes(summary.byteCount))
        }
        Text(
            text = "Last change: ${formatDate(summary.lastUpdatedEpochMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selected) {
            FilledTonalButton(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("StorageCategoryToggle:${summary.category.name}")
            ) {
                Text("Hide details")
            }
        } else {
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("StorageCategoryToggle:${summary.category.name}")
            ) {
                Text("View details")
            }
        }
    }
}

@Composable
private fun DiagnosticSessionsList(
    sessions: List<DiagnosticSessionSummary>
) {
    if (sessions.isEmpty()) {
        StorageDetailCard("Diagnostic sessions") {
            Text("No completed diagnostic session has been stored yet.")
            Text("The next completed benchmark will be saved automatically with its complete run history.")
        }
        return
    }

    var showAll by remember(sessions.size) { mutableStateOf(false) }
    val visibleSessions = if (showAll) sessions else sessions.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageCollectionSummary(
        title = "Diagnostic reports",
        count = sessions.size,
        itemName = "sessions"
    )
    visibleSessions.forEach { session ->
        StorageDetailCard(session.experimentName) {
            Text("${session.verdict} · ${session.runCount} runs · ${session.caseCount} cases")
            Text("Seeds: ${session.seeds.joinToString()} · Links: ${session.linkCounts.joinToString()}")
            Text(
                text = formatDate(session.createdAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = session.directoryPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    StorageShowAllButton(
        total = sessions.size,
        showAll = showAll,
        itemName = "sessions",
        onToggle = { showAll = !showAll }
    )
}

@Composable
private fun TrainingRunsList(runs: List<TrainingRunSummary>) {
    if (runs.isEmpty()) {
        StorageDetailCard("Training run registry") {
            Text("No completed local training run has been stored yet.")
            Text("Use AI Training after generating a scientific dataset.")
        }
        return
    }

    var showAll by remember(runs.size) { mutableStateOf(false) }
    val visibleRuns = if (showAll) runs else runs.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageCollectionSummary(
        title = "Training run registry",
        count = runs.size,
        itemName = "runs"
    )
    visibleRuns.forEach { run ->
        StorageDetailCard(run.runName) {
            Text(
                "Baseline F1 ${run.baselineTestMacroF1?.let(::formatMetric) ?: "N/A"} · " +
                    "Context F1 ${run.contextTestMacroF1?.let(::formatMetric) ?: "N/A"} · " +
                    "Delta ${formatSignedMetric(run.macroF1Delta)}"
            )
            Text(formatDate(run.startedAtEpochMillis), style = MaterialTheme.typography.bodySmall)
            Text(
                run.directoryPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    StorageShowAllButton(
        total = runs.size,
        showAll = showAll,
        itemName = "runs",
        onToggle = { showAll = !showAll }
    )
}

@Composable
private fun StorageCollectionSummary(
    title: String,
    count: Int,
    itemName: String
) {
    StorageDetailCard(title) {
        Text("$count saved $itemName, newest first.")
        if (count > MAX_INITIAL_STORAGE_RECORDS) {
            Text(
                "The newest $MAX_INITIAL_STORAGE_RECORDS are shown initially to keep this screen responsive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageShowAllButton(
    total: Int,
    showAll: Boolean,
    itemName: String,
    onToggle: () -> Unit
) {
    if (total <= MAX_INITIAL_STORAGE_RECORDS) return
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (showAll) {
                "Show newest $MAX_INITIAL_STORAGE_RECORDS"
            } else {
                "Show all $total $itemName"
            }
        )
    }
}

@Composable
private fun StorageCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JargonAwareText(text = title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
            content()
        }
    }
}

@Composable
internal fun StorageDetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(colors.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        JargonAwareText(text = title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
        content()
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val kib = bytes / 1_024.0
    if (kib < 1_024.0) return String.format(Locale.US, "%.1f KiB", kib)
    return String.format(Locale.US, "%.1f MiB", kib / 1_024.0)
}

internal fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "No saved files yet"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
}

private fun formatMetric(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.4f", value) else "N/A"

private fun formatSignedMetric(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%+.4f", value) else "N/A"

private fun StorageCategory.symbol(): String =
    when (this) {
        StorageCategory.ROBOTS -> "◇"
        StorageCategory.SESSIONS -> "◷"
        StorageCategory.DATASETS -> "▤"
        StorageCategory.MODELS -> "◆"
        StorageCategory.TRAINING -> "↗"
    }

private const val MAX_INITIAL_STORAGE_RECORDS = 20
