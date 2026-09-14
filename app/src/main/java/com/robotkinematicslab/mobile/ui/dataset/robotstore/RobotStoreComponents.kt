package com.robotkinematicslab.mobile.ui.dataset.robotstore

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

@Composable
internal fun RobotStoreHero(
    availableCount: Int,
    selectedCount: Int,
    onAddRobot: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    editingEnabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val selectionFraction =
        if (availableCount == 0) 0f else selectedCount.toFloat() / availableCount.toFloat()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.primary,
                            colors.tertiary,
                            Color(0xFF11182B)
                        )
                    )
                )
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "DATASET-READY DH LIBRARY",
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Text(
            text = "Configure generation models",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text =
                "$availableCount admitted synthetic/custom DH models · $selectedCount selected for the next generation run",
            color = Color.White.copy(alpha = 0.86f),
            style = MaterialTheme.typography.bodyMedium
        )
        LinearProgressIndicator(
            progress = { selectionFraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF62F5D2),
            trackColor = Color.White.copy(alpha = 0.20f)
        )
        Button(
            onClick = onAddRobot,
            enabled = editingEnabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tutorialAnchor(TutorialTargets.RobotLibraryCustom),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = colors.primary
                )
        ) {
            Text("+ Create custom DH robot", fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Select all", maxLines = 2)
            }
            OutlinedButton(
                onClick = onSelectNone,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Clear selection", maxLines = 2)
            }
        }
    }
}

@Composable
internal fun IndustrialRobotReferenceGallery(
    modifier: Modifier = Modifier,
    eyebrow: String = "INDUSTRIAL TECHNICAL LIBRARY",
    title: String = "Commercial references · not simulation presets",
    description: String =
        "Three commercial variants are documented here. The ten built-in mathematical presets are synthetic robots, not ten commercial models. Manufacturer PDFs can be downloaded once or imported locally for offline use.",
    boundaryText: String =
        "Scientific boundary: ${IndustrialRobotReferenceCatalog.QUARANTINED_REFERENCE_COUNT} former cards were removed because they relied on a brochure, mirror, archive, missing DH source or ambiguous variant. These remaining references are still not dataset models until their frame adapters pass independent FK landmark tests."
) {
    val tutorialReporter = LocalTutorialActionReporter.current
    var selectedReference by remember { mutableStateOf<IndustrialRobotReference?>(null) }
    val references = IndustrialRobotReferenceCatalog.entries

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            eyebrow = eyebrow,
            title = title,
            description = description,
            badge = "${references.size} REFERENCES"
        )

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(276.dp)
                    .testTag("IndustrialRobotReferenceGallery"),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = references, key = IndustrialRobotReference::id) { reference ->
                IndustrialRobotReferenceCard(
                    reference = reference,
                    onClick = {
                        selectedReference = reference
                        tutorialReporter.report(
                            target = TutorialTargets.RobotLibraryGallery,
                            interaction = TutorialInteraction.TAP,
                            detail = "Industrial reference opened: ${reference.id}."
                        )
                    }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = boundaryText,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    selectedReference?.let { reference ->
        RobotReferenceDialog(
            reference = reference,
            onDismiss = { selectedReference = null }
        )
    }
}

@Composable
private fun IndustrialRobotReferenceCard(
    reference: IndustrialRobotReference,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .width(238.dp)
                .fillMaxSize()
                .testTag("RobotReferenceCard:${reference.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1020)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(184.dp)
        ) {
            SampledRobotArtwork(
                drawableResId = reference.artworkResId,
                contentDescription = "Neon illustration of ${reference.displayName}",
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.58f to Color.Transparent,
                                1f to Color(0xE6000000)
                            )
                        )
            )
            ReferenceStatusPill(
                status = reference.evidenceStatus,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
            )
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                shape = CircleShape,
                color = Color(0xCC111827),
                contentColor = Color(0xFFE5E7EB)
            ) {
                Text(
                    text = "STYLIZED · NOT TO SCALE",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = reference.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = reference.morphology,
                color = Color(0xFFC7D7FF),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Official manuals · official kinematics",
                color = Color(0xFF67E8F9),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RobotReferenceDialog(
    reference: IndustrialRobotReference,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var selectedDocument by remember(reference.id) { mutableStateOf<RobotReferenceDocument?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(reference.displayName) },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color(0xFF0B1020))
                ) {
                    SampledRobotArtwork(
                        drawableResId = reference.artworkResId,
                        contentDescription = "Neon illustration of ${reference.displayName}",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(reference.morphology, fontWeight = FontWeight.SemiBold)
                RobotReferenceDataRow("Fixed variant", reference.fixedVariant)
                ReferenceStatusPill(status = reference.evidenceStatus)
                Text(reference.evidenceStatus.explanation)

                RobotReferenceDialogSectionTitle("Technical specifications")
                reference.specifications.forEach { specification ->
                    RobotReferenceDataRow(
                        label = specification.label,
                        value = specification.value
                    )
                }

                RobotReferenceDialogSectionTitle("Manufacturer joint limits")
                reference.jointLimits.forEach { limit ->
                    RobotReferenceDataRow(
                        label = limit.joint,
                        value = limit.range
                    )
                }

                RobotReferenceDialogSectionTitle("Official complete manuals")
                reference.documents.forEachIndexed { index, document ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(document.kind.label, style = MaterialTheme.typography.labelMedium)
                            Text(document.title, fontWeight = FontWeight.SemiBold)
                            RobotReferenceDataRow("Revision", document.revision)
                            RobotReferenceDataRow("Coverage", document.relevantPages)
                            RobotReferenceDataRow("Publisher", document.sourceLabel)
                            RobotReferenceDataRow("APK licence status", document.redistributionStatus.label)
                            RobotReferenceDataRow("Offline access", document.offlineStrategy.label)
                            Text(
                                text = document.redistributionStatus.explanation,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = document.provenanceNote,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { selectedDocument = document },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("RobotReferencePdfButton:${reference.id}:$index")
                                        .tutorialAnchor(TutorialTargets.RobotLibraryPdf)
                            ) {
                                Text("Open verified official PDF")
                            }
                        }
                    }
                }

                RobotReferenceDialogSectionTitle("Official kinematic evidence")
                RobotReferenceDataRow("Convention", reference.kinematicEvidence.convention)
                RobotReferenceDataRow("Source", reference.kinematicEvidence.sourceLabel)
                RobotReferenceDataRow(
                    "DH inside manual",
                    if (reference.kinematicEvidence.containedInManual) "Yes" else "No · separate official source"
                )
                Text(
                    text = reference.kinematicEvidence.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { uriHandler.openUri(reference.kinematicEvidence.sourceUrl) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("RobotKinematicEvidenceButton:${reference.id}")
                ) {
                    Text("Open manufacturer kinematic source")
                }

                RobotReferenceDialogSectionTitle("Artwork traceability")
                RobotReferenceDataRow("Compared with", reference.artworkEvidence.reviewedAgainst)
                Text(
                    text = reference.artworkEvidence.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { uriHandler.openUri(reference.artworkEvidence.sourceUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open artwork comparison source")
                }
                Text(
                    text =
                        "Dataset status: evidence admitted, import pending. The commercial name is never attached to a synthetic model until the exact convention adapter and FK landmarks pass.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("RobotReferenceDialogClose:${reference.id}")
            ) {
                Text("Close")
            }
        }
    )

    selectedDocument?.let { document ->
        RobotReferencePdfDialog(
            document = document,
            onDismiss = { selectedDocument = null }
        )
    }
}

@Composable
private fun RobotReferenceDialogSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun RobotReferenceDataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.88f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.12f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReferenceStatusPill(
    status: RobotReferenceEvidenceStatus,
    modifier: Modifier = Modifier
) {
    val (container, content) =
        when (status) {
            RobotReferenceEvidenceStatus.PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED ->
                Color(0xDD0B5D4B) to Color(0xFFE1FFF6)
        }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Text(
            text = status.shortLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun DatasetReadyModelsHeader(
    visibleCount: Int,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            eyebrow = "VERIFIED LOCAL LIBRARY",
            title = "Dataset-ready DH models",
            description =
                "These are the definitions actually selectable for generation and training. They stay separate from the primary-evidence reference library above until a traceable DH conversion passes FK checks.",
            badge = "$visibleCount / $totalCount SHOWN"
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("RobotStoreSearch"),
            label = { Text("Search name or R/P topology") },
            supportingText = { Text("Example: 6-link, revolute, mixed or R-P") },
            singleLine = true
        )
    }
}

@Composable
internal fun DatasetReadyRobotCard(
    savedRobot: SavedRobot,
    isSelected: Boolean,
    deleteArmed: Boolean,
    workspaceStudyCount: Int,
    workspacePreparing: Boolean,
    workspaceError: String?,
    onToggleSelected: () -> Unit,
    onEdit: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onRetryWorkspace: () -> Unit,
    onDelete: () -> Unit,
    editingEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val robot = savedRobot.robot
    val topology = robot.joints.joinToString("-") { joint ->
        when (joint.type) {
            JointType.REVOLUTE -> "R"
            JointType.PRISMATIC -> "P"
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("DatasetRobotCard:${savedRobot.id}"),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) colors.primaryContainer.copy(alpha = 0.58f) else colors.surface
            ),
        border =
            BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colors.primary else colors.outlineVariant
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = robot.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${robot.joints.size} joints · ${topology.ifBlank { "No topology" }}",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (DatasetRobotPresets.isSyntheticPresetId(savedRobot.id)) {
                        Text(
                            text = "SYNTHETIC MATHEMATICAL PRESET · not a commercial robot",
                            color = colors.tertiary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = Color(0xFF0E6A52),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text(
                        text = "DATASET READY",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                onClick = onToggleSelected,
                modifier = Modifier.fillMaxWidth(),
                color =
                    if (isSelected) colors.primary.copy(alpha = 0.12f)
                    else colors.surfaceVariant.copy(alpha = 0.52f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Text(
                        text = if (isSelected) "Included in next dataset" else "Add to next dataset",
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onEdit,
                enabled = editingEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit DH model")
            }

            AppDisclosureSection(
                title = "Robot details",
                summary = "$workspaceStudyCount saved workspace ${if (workspaceStudyCount == 1) "study" else "studies"} · technical actions",
                testTag = "DatasetRobotDetails:${savedRobot.id}"
            ) {
                RobotTopologyPreview(
                    savedRobot = savedRobot,
                    topology = topology,
                    isSelected = isSelected
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Owned workspace evidence",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    when {
                        workspacePreparing -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "Preparing the reproducible baseline workspace automatically…",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        workspaceStudyCount > 0 -> {
                            Text(
                                "$workspaceStudyCount saved ${if (workspaceStudyCount == 1) "study" else "studies"} · Point cloud · Surface shell · Animated construction",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = onOpenWorkspace,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("RobotWorkspaceOpen:${savedRobot.id}")
                            ) {
                                Text("Open this robot's 3D workspace")
                            }
                        }
                        workspaceError != null -> {
                            Text(
                                "Workspace preparation failed safely: $workspaceError",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error
                            )
                            OutlinedButton(
                                onClick = onRetryWorkspace,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Retry workspace preparation")
                            }
                        }
                        else -> {
                            Text(
                                "Baseline workspace queued. Its three visual modes share one scientific study.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDelete,
                    enabled = editingEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
                ) {
                    Text(if (deleteArmed) "Confirm delete" else "Delete")
                }
            }
        }
    }
}

@Composable
private fun RobotTopologyPreview(
    savedRobot: SavedRobot,
    topology: String,
    isSelected: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val joints = savedRobot.robot.joints
    val revoluteColor = Color(0xFF54D7FF)
    val prismaticColor = Color(0xFFFFB44C)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF071426),
                                if (isSelected) colors.primary.copy(alpha = 0.88f) else Color(0xFF18233D),
                                Color(0xFF35133B)
                            )
                    )
                )
                .semantics {
                    contentDescription =
                        "Schematic joint topology for ${savedRobot.robot.name}: ${topology.ifBlank { "none" }}"
                }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size
            val gridColor = Color.White.copy(alpha = 0.07f)
            repeat(6) { index ->
                val x = canvasSize.width * index / 5f
                drawLine(gridColor, Offset(x, 0f), Offset(x, canvasSize.height), 1f)
            }
            repeat(4) { index ->
                val y = canvasSize.height * index / 3f
                drawLine(gridColor, Offset(0f, y), Offset(canvasSize.width, y), 1f)
            }

            val base = Offset(canvasSize.width * 0.07f, canvasSize.height * 0.76f)
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(base.x, canvasSize.height * 0.90f),
                end = base,
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(canvasSize.width * 0.025f, canvasSize.height * 0.91f),
                end = Offset(canvasSize.width * 0.115f, canvasSize.height * 0.91f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )

            if (joints.isEmpty()) return@Canvas

            val points =
                buildList {
                    add(base)
                    joints.forEachIndexed { index, _ ->
                        val fraction = (index + 1f) / (joints.size + 0.45f)
                        val x = canvasSize.width * (0.07f + 0.88f * fraction)
                        val wave = sin((index + 1) * 1.18).toFloat()
                        val y = canvasSize.height * (0.50f - 0.27f * wave)
                        add(
                            Offset(
                                x,
                                y.coerceIn(canvasSize.height * 0.16f, canvasSize.height * 0.80f)
                            )
                        )
                    }
                }

            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = Color.White.copy(alpha = 0.88f),
                    start = start,
                    end = end,
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF90F4FF).copy(alpha = 0.32f),
                    start = start,
                    end = end,
                    strokeWidth = 14f,
                    cap = StrokeCap.Round
                )
            }

            joints.forEachIndexed { index, joint ->
                val point = points[index + 1]
                val nodeColor =
                    if (joint.type == JointType.REVOLUTE) revoluteColor else prismaticColor
                drawCircle(nodeColor.copy(alpha = 0.20f), radius = 15f, center = point)
                if (joint.type == JointType.REVOLUTE) {
                    drawCircle(nodeColor, radius = 7.5f, center = point)
                    drawCircle(Color(0xFF071426), radius = 2.5f, center = point)
                } else {
                    drawRect(
                        color = nodeColor,
                        topLeft = Offset(point.x - 7f, point.y - 7f),
                        size = androidx.compose.ui.geometry.Size(14f, 14f)
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopologyLegendDot(color = revoluteColor)
            Text("R revolute", color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(2.dp))
            TopologyLegendDot(color = prismaticColor)
            Text("P prismatic", color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.labelSmall)
        }
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            color = Color.Black.copy(alpha = 0.42f),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Text(
                text = "SCHEMATIC · $topology",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopologyLegendDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
    )
}

@Composable
private fun SectionHeading(
    eyebrow: String,
    title: String,
    description: String,
    badge: String
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    color = colors.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                )
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = description,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * The source PNGs are deliberately high resolution. Decode a display-sized sample on an IO
 * dispatcher so the horizontal gallery does not allocate ten full 2048px bitmaps or block UI.
 */
@Composable
private fun SampledRobotArtwork(
    @DrawableRes drawableResId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val resources = LocalContext.current.resources
    val bitmap by
        produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null,
            key1 = resources,
            key2 = drawableResId
        ) {
            value =
                withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(
                        resources,
                        drawableResId,
                        BitmapFactory.Options().apply {
                            inSampleSize = 2
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        }
                    )?.asImageBitmap()
                }
        }

    val loadedBitmap = bitmap
    if (loadedBitmap == null) {
        Box(
            modifier =
                modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFF11182B), Color(0xFF3A1450), Color(0xFF0B4F5B))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading artwork…",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    } else {
        Image(
            bitmap = loadedBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
