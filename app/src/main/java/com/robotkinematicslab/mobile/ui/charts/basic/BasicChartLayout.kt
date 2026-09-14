package com.robotkinematicslab.mobile.ui.charts.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideDialogHost
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartQuickGuide
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartTitleWithGuide
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationDialog
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.charts.presentation.inferAutomaticFigureCollection
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.LocalJargonHelpEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val LocalChartCardsCollapsible = compositionLocalOf { false }
val LocalChartFigureCaptureInProgress = compositionLocalOf { false }

@Composable
fun ChartSectionCard(
    title: String,
    subtitle: String? = null,
    guide: ChartGuide? = ChartGuideFactory.inferForCard(title, subtitle),
    modifier: Modifier = Modifier,
    automaticExportKey: Any? = Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current.applicationContext
    val presentation = LocalChartPresentationController.current
    val preferences = presentation.preferences
    val libraryContext = LocalAutomaticFigureLibraryContext.current
    val isFigure = guide != null && guide.kind != ChartGuideKind.SUMMARY
    val supportsAutomaticStaticExport =
        isFigure && ChartFigureExporter.supportsAutomaticStaticExport(title)
    val collapsible = LocalChartCardsCollapsible.current && isFigure
    val fallbackExecutionId = remember { ChartFigureExporter.newAutomaticFigureExecutionId() }
    val automaticCollection =
        libraryContext?.collection ?: inferAutomaticFigureCollection(title)
    val automaticAnalysisId = libraryContext?.analysisId ?: "latest-results"
    val automaticExecutionId = libraryContext?.executionId ?: fallbackExecutionId
    val automaticFigureType = guide?.kind?.name?.lowercase()?.replace('_', '-') ?: "chart"
    val automaticTrigger =
        ChartFigureExporter.automaticExportTrigger(
            collection = automaticCollection,
            analysisId = automaticAnalysisId,
            executionId = automaticExecutionId,
            title = title,
            figureType = automaticFigureType,
            dataKey = automaticExportKey,
            presentationKey =
                listOf(
                    CHART_RENDER_PRESENTATION_VERSION,
                    preferences.copy(autoSaveFigures = true)
                )
        )
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    var guideOpen by remember(title) { mutableStateOf(false) }
    var presentationOpen by remember(title) { mutableStateOf(false) }
    var exportInProgress by remember(title) { mutableStateOf(false) }
    var exportProvenance by remember(automaticTrigger) { mutableStateOf<String?>(null) }
    var provenanceOpen by remember(automaticTrigger) { mutableStateOf(false) }
    var exportStatus by remember(automaticTrigger) { mutableStateOf<String?>(null) }
    var automaticExportAttempt by remember(automaticTrigger) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(title) { mutableStateOf(!collapsible) }
    var measuredCardSize by remember(title) { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { measuredCardSize = it }
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                },
        shape =
            if (preferences.preset == ChartPresentationPreset.PUBLICATION && isFigure) {
                RoundedCornerShape(0.dp)
            } else {
                MaterialTheme.shapes.large
            },
        color =
            if (preferences.preset == ChartPresentationPreset.PUBLICATION && isFigure) {
                Color.White
            } else {
                colors.surface.copy(alpha = 0.96f)
            },
        border =
            BorderStroke(
                1.dp,
                if (preferences.preset == ChartPresentationPreset.PUBLICATION && isFigure) {
                    Color(0xFFB8B8B8)
                } else {
                    colors.outlineVariant.copy(alpha = 0.75f)
                }
            ),
        shadowElevation =
            if (preferences.preset == ChartPresentationPreset.PUBLICATION && isFigure) 0.dp else 2.dp
    ) {
        CompositionLocalProvider(
            LocalJargonHelpEnabled provides !exportInProgress,
            LocalChartFigureCaptureInProgress provides exportInProgress
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChartTitleWithGuide(
                    title = title,
                    guide = if (exportInProgress) null else guide,
                    onOpenGuide = { guideOpen = true },
                    onOpenFigureSettings =
                        if (isFigure && !exportInProgress && expanded) {
                            { presentationOpen = true }
                        } else {
                            null
                        }
                )

                if (!subtitle.isNullOrBlank()) {
                    JargonAwareText(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                val visibleExportStatus = exportStatus
                if (!exportInProgress && !visibleExportStatus.isNullOrBlank()) {
                    if(exportProvenance != null) androidx.compose.material3.TextButton(onClick = { provenanceOpen = true }) { Text("View provenance") }
                    val failed = visibleExportStatus.contains("could not", ignoreCase = true)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color =
                            if (failed) colors.errorContainer
                            else colors.secondaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = visibleExportStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (failed) colors.onErrorContainer else colors.onSecondaryContainer
                            )
                            if (failed && supportsAutomaticStaticExport && automaticExportKey != null) {
                                OutlinedButton(
                                    onClick = { automaticExportAttempt += 1 },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry saving chart")
                                }
                            }
                        }
                    }
                }

                if (collapsible && !exportInProgress) {
                    OutlinedButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (expanded) "Hide chart" else "View chart")
                    }
                }

                if (guide != null && (expanded || exportInProgress)) {
                    // The compact interpretation block is part of the scientific figure caption
                    // by design. Export hides interaction/decorations, but retains what the chart
                    // measures, its units and why the evidence matters.
                    ChartQuickGuide(
                        guide = guide,
                        expanded = preferences.showGuidance && !exportInProgress
                    )
                }

                if (expanded || exportInProgress) content()
            }
        }
    }

    LaunchedEffect(automaticTrigger, supportsAutomaticStaticExport, automaticExportAttempt) {
        if (
            !supportsAutomaticStaticExport ||
            automaticExportKey == null
        ) return@LaunchedEffect
        delay(AUTOMATIC_EXPORT_SETTLE_MILLIS)
        // Hidden evidence hosts can request dozens of final figures at once. Capture them one by
        // one so Android never has to retain many full-resolution off-screen layers concurrently.
        AUTOMATIC_FIGURE_CAPTURE_MUTEX.withLock {
            exportInProgress = true
            exportStatus = null
            try {
                val capturedImage =
                    runCatching capture@{
                        repeat(AUTOMATIC_EXPORT_CAPTURE_ATTEMPTS) {
                            withFrameNanos { }
                            if (
                                measuredCardSize.width > 0 &&
                                measuredCardSize.height > 0
                            ) {
                                runCatching { graphicsLayer.toImageBitmap() }
                                    .getOrNull()
                                    ?.let { return@capture it }
                            }
                        }
                        error("Chart left composition before a non-zero export surface was available.")
                    }

                if (capturedImage.isFailure) {
                    exportStatus =
                        "Automatic PNG could not be captured: " +
                            (capturedImage.exceptionOrNull()?.message ?: "chart was not visible")
                    return@withLock
                }

                val result =
                    ChartFigureExporter.saveAutomaticPng(
                        context = context,
                        title = title,
                        collection = automaticCollection,
                        analysisId = automaticAnalysisId,
                        executionId = automaticExecutionId,
                        figureType = automaticFigureType,
                        dataFingerprint = automaticTrigger.dataFingerprint,
                        presentationFingerprint = automaticTrigger.presentationFingerprint,
                        image = capturedImage.getOrThrow(),
                        caption = subtitle.orEmpty()
                    )
                exportStatus =
                    result.fold(
                        onSuccess = {
                            exportProvenance = "${it.displayName}\n${it.locationLabel}\n${it.relativePath.orEmpty()}\nSHA-256: ${it.sha256.orEmpty()}\nRun: $automaticExecutionId\nAnalysis: $automaticAnalysisId\nData: ${automaticTrigger.dataFingerprint}"
                            "Figure saved."
                        },
                        onFailure = {
                            "Automatic PNG could not be saved: ${it.message ?: "unknown error"}"
                        }
                    )
            } finally {
                exportInProgress = false
            }
        }
    }

    if(provenanceOpen) androidx.compose.material3.AlertDialog(onDismissRequest = { provenanceOpen = false }, title = { Text("Figure provenance") }, text = { Text(exportProvenance.orEmpty()) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { provenanceOpen = false }) { Text("Close") } })

    if (guide != null) {
        ChartGuideDialogHost(
            title = title,
            guide = guide,
            visible = guideOpen,
            onDismiss = { guideOpen = false }
        )
    }

    ChartPresentationDialog(
        visible = presentationOpen,
        preferences = preferences,
        onPreferencesChange = {
            presentation.update(it)
            exportStatus = null
        },
        exportStatus = exportStatus,
        exportInProgress = exportInProgress,
        onExportPng =
            if (isFigure) {
                {
                    coroutineScope.launch {
                        exportInProgress = true
                        exportStatus = null
                        try {
                            val capturedImage =
                                runCatching capture@{
                                    repeat(AUTOMATIC_EXPORT_CAPTURE_ATTEMPTS) {
                                        withFrameNanos { }
                                        if (
                                            measuredCardSize.width > 0 &&
                                            measuredCardSize.height > 0
                                        ) {
                                            runCatching { graphicsLayer.toImageBitmap() }
                                                .getOrNull()
                                                ?.let { return@capture it }
                                        }
                                    }
                                    error("Chart is not attached to a non-zero export surface.")
                                }

                            if (capturedImage.isFailure) {
                                exportStatus =
                                    "Figure could not be captured: " +
                                        (capturedImage.exceptionOrNull()?.message ?: "chart was not visible")
                                return@launch
                            }

                            val result =
                                ChartFigureExporter.savePng(
                                    context = context,
                                    title = title,
                                    image = capturedImage.getOrThrow(),
                                    metadataProperties = mapOf("caption" to subtitle.orEmpty(), "executionId" to automaticExecutionId, "analysisId" to automaticAnalysisId, "dataFingerprint" to automaticTrigger.dataFingerprint, "presentationFingerprint" to automaticTrigger.presentationFingerprint)
                                )
                            exportStatus =
                                result.fold(
                                    onSuccess = {
                                        exportProvenance = "${it.displayName}\n${it.relativePath.orEmpty()}\nSHA-256: ${it.sha256.orEmpty()}\nRun: $automaticExecutionId"
                                        "Figure saved."
                                    },
                                    onFailure = {
                                        "Figure could not be saved: ${it.message ?: "unknown error"}"
                                    }
                                )
                        } finally {
                            exportInProgress = false
                        }
                    }
                }
            } else {
                null
            },
        onDismiss = { presentationOpen = false }
    )
}

private const val AUTOMATIC_EXPORT_SETTLE_MILLIS = 350L
private const val AUTOMATIC_EXPORT_CAPTURE_ATTEMPTS = 4
private const val CHART_RENDER_PRESENTATION_VERSION = "chart-render-v3-scientific-layout"
private val AUTOMATIC_FIGURE_CAPTURE_MUTEX = Mutex()

@Composable
fun ChartMetricRow(
    label: String,
    value: String
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JargonAwareText(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 4,
            softWrap = true
        )

        // Values can contain user-defined robot, dataset or run names. Only the authored label is
        // eligible for glossary decoration; treating identifiers as prose creates false matches.
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            textAlign = TextAlign.End,
            maxLines = 4,
            softWrap = true
        )
    }
}

@Composable
fun ChartAxisInfoRow(
    xAxisLabel: String,
    yAxisLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChartMetricRow(
            label = "X axis",
            value = xAxisLabel
        )

        ChartMetricRow(
            label = "Y axis",
            value = yAxisLabel
        )
    }
}
