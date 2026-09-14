package com.robotkinematicslab.mobile.ui.storage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.dataset.DatasetManifest
import com.robotkinematicslab.mobile.dataset.CorruptDatasetManifest
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun StorageAccessCard(
    eyebrow: String,
    title: String,
    description: String,
    primaryLabel: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    secondaryTag: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tutorialReporter = LocalTutorialActionReporter.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("storage-category-details")
                .tutorialAnchor(TutorialTargets.StorageDetails)
                .background(
                    brush =
                        Brush.linearGradient(
                            listOf(
                                colors.primaryContainer,
                                colors.tertiaryContainer.copy(alpha = 0.78f)
                            )
                        ),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JargonAwareText(
            text = eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            fontWeight = FontWeight.Bold
        )
        JargonAwareText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        JargonAwareText(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = {
                onPrimary()
                tutorialReporter.report(
                    target = TutorialTargets.StorageDirectAccess,
                    interaction = TutorialInteraction.TAP,
                    detail = "Storage direct-access destination opened."
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(primaryTag)
                    .tutorialAnchor(
                        targetId = TutorialTargets.StorageDirectAccess,
                        actionEnabled = false
                    )
        ) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && secondaryTag != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().testTag(secondaryTag)
            ) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
internal fun RobotStorageDetails(
    robots: List<SavedRobot>,
    onOpenRobots: () -> Unit
) {
    var showAll by remember(robots.size) { mutableStateOf(false) }
    val visibleRobots = if (showAll) robots else robots.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageAccessCard(
        eyebrow = "ROBOT LIBRARY",
        title = "Inspect or edit robot definitions",
        description =
            "The records below are the persistent DH models used by dataset generation. Open the library for the industrial image catalogue, full robot editor and selection controls.",
        primaryLabel = "Open Robot Library",
        primaryTag = "StorageDirectAccess:ROBOTS",
        onPrimary = onOpenRobots
    )
    StorageCollectionHeading(
        title = "Saved DH definitions",
        countLabel = "${robots.size} robots",
        description = "Joint topology, DH-row count and identity are shown without opening the editor."
    )
    if (robots.isEmpty()) {
        StorageEmptyRecord("No saved robot definition is currently available.")
    } else {
        visibleRobots.forEach { savedRobot ->
            val robot = savedRobot.robot
            val topology =
                robot.joints.joinToString("-") { joint ->
                    when (joint.type) {
                        JointType.REVOLUTE -> "R"
                        JointType.PRISMATIC -> "P"
                    }
                }
            StorageRecordCard(
                title = robot.name,
                badge = topology.ifBlank { "NO JOINTS" }
            ) {
                Text("${robot.joints.size} joints · ${robot.dhParameters.size} DH rows")
                Text(
                    "Library ID: ${savedRobot.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        StorageRecordsToggle(
            total = robots.size,
            showAll = showAll,
            itemName = "robots",
            onToggle = { showAll = !showAll }
        )
    }
}

@Composable
internal fun DatasetStorageDetails(
    datasets: List<DatasetManifest>,
    corruptManifests: List<CorruptDatasetManifest>,
    quarantineMessage: String?,
    onOpenDatasets: () -> Unit,
    onQuarantineManifest: (CorruptDatasetManifest) -> Unit
) {
    var showAll by remember(datasets.size, corruptManifests.size) { mutableStateOf(false) }
    val visibleDatasets = if (showAll) datasets else datasets.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageAccessCard(
        eyebrow = "SCIENTIFIC DATASETS",
        title = "Browse datasets or append new rows",
        description =
            "The records below summarize the reproducibility manifest. Open Saved Datasets to inspect each dataset in context or configure another deterministic batch.",
        primaryLabel = "Open Saved Datasets",
        primaryTag = "StorageDirectAccess:DATASETS",
        onPrimary = onOpenDatasets
    )
    StorageCollectionHeading(
        title = "Dataset registry",
        countLabel = "${datasets.size} valid · ${corruptManifests.size} damaged",
        description =
            "Only valid manifests can be opened by Dataset and Training. Damaged manifests remain visible here for safe quarantine."
    )
    quarantineMessage?.let { message ->
        StorageDetailCard("Manifest quarantine") {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    corruptManifests.forEach { corrupt ->
        StorageRecordCard(
            title = corrupt.datasetName ?: corrupt.fileName,
            badge = "DAMAGED"
        ) {
            Text(corrupt.reason, color = MaterialTheme.colorScheme.error)
            Text(
                "Manifest: ${corrupt.manifestPath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            corrupt.referencedCsvPath?.let { csvPath ->
                Text(
                    "Referenced CSV is preserved: $csvPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { onQuarantineManifest(corrupt) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("quarantine-dataset-manifest:${corrupt.fileName}")
            ) {
                Text("Quarantine damaged manifest")
            }
        }
    }
    if (datasets.isEmpty() && corruptManifests.isEmpty()) {
        StorageEmptyRecord("No scientific dataset has been generated or imported yet.")
    } else if (datasets.isNotEmpty()) {
        visibleDatasets.forEach { manifest ->
            val csvFile = File(manifest.csvPath)
            val provenanceLabel = if (manifest.hasCompleteBatchProvenance) "TRACEABLE" else "LEGACY"
            StorageRecordCard(
                title = manifest.datasetName,
                badge = provenanceLabel
            ) {
                Text(
                    "${formatCount(manifest.rowCount)} rows · ${manifest.robotIds.size} robots · " +
                        "${manifest.generationCount} batches"
                )
                Text(
                    "${manifest.targetMode.name.replace('_', ' ')} · ${manifest.filterMode.name.replace('_', ' ')} · seed ${manifest.randomSeed}"
                )
                Text(
                    "CSV ${if (csvFile.isFile) formatBytes(csvFile.length()) else "missing"} · Updated ${formatDate(manifest.lastUpdatedEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (csvFile.isFile) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                )
            }
        }
        StorageRecordsToggle(
            total = datasets.size,
            showAll = showAll,
            itemName = "datasets",
            onToggle = { showAll = !showAll }
        )
    }
}

@Composable
internal fun ModelStorageDetails(
    runs: List<TrainingRunSummary>,
    indexedFileCount: Int,
    modelsDirectoryPath: String,
    onOpenModels: () -> Unit
) {
    val modelFiles =
        remember(modelsDirectoryPath, indexedFileCount) {
            File(modelsDirectoryPath)
                .listFiles(File::isFile)
                ?.sortedByDescending(File::lastModified)
                .orEmpty()
        }
    val runByModelPath =
        remember(runs) {
            runs.flatMap { run -> run.modelPaths.map { path -> File(path).absolutePath to run } }.toMap()
        }
    var showAll by remember(modelFiles.size) { mutableStateOf(false) }
    val visibleFiles = if (showAll) modelFiles else modelFiles.take(MAX_INITIAL_STORAGE_RECORDS)

    StorageAccessCard(
        eyebrow = "AI MODEL REGISTRY",
        title = "Compare trained model artifacts",
        description =
            "Every model keeps its feature order, training-only normalization and learned weights. Open the comparator to select any stored models and inspect their evidence side by side.",
        primaryLabel = "Open Model Comparison",
        primaryTag = "StorageDirectAccess:MODELS",
        onPrimary = onOpenModels
    )
    StorageCollectionHeading(
        title = "Stored model artifacts",
        countLabel = "${modelFiles.size} models",
        description = "Files are read directly from the managed model registry."
    )
    if (modelFiles.isEmpty()) {
        StorageEmptyRecord(
            if (indexedFileCount > 0) {
                "$indexedFileCount indexed entries exist, but no readable model file is currently available. Refresh Storage after checking the research-pack import."
            } else {
                "No trained model artifact is currently stored."
            }
        )
    } else {
        visibleFiles.forEach { file ->
            val run = runByModelPath[file.absolutePath]
            StorageRecordCard(
                title = file.nameWithoutExtension,
                badge = file.extension.uppercase(Locale.ROOT).ifBlank { "MODEL" }
            ) {
                Text("${formatBytes(file.length())} · ${if (file.canRead()) "Readable" else "Unavailable"}")
                Text(
                    run?.let { "Training run: ${it.runName} · seed ${it.randomSeed}" }
                        ?: "Bundled or certified model artifact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        StorageRecordsToggle(
            total = modelFiles.size,
            showAll = showAll,
            itemName = "models",
            onToggle = { showAll = !showAll }
        )
    }
}

@Composable
private fun StorageCollectionHeading(
    title: String,
    countLabel: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            JargonAwareText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            JargonAwareText(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(999.dp)
        ) {
            JargonAwareText(
                countLabel,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StorageRecordCard(
    title: String,
    badge: String,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Record titles and badges are stored identifiers/data, not authored help copy.
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = colors.primaryContainer,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StorageEmptyRecord(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp)
    ) {
        JargonAwareText(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StorageRecordsToggle(
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

private fun formatCount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

private const val MAX_INITIAL_STORAGE_RECORDS = 20
