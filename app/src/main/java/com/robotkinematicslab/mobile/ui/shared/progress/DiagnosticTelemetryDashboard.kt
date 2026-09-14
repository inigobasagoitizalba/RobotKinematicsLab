package com.robotkinematicslab.mobile.ui.shared.progress

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.SustainedPerformanceAnalyzer
import com.robotkinematicslab.mobile.ui.charts.basic.RoundedRatioBar
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.LineChartInspectorDialog
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.computeLineChartStats
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal const val TELEMETRY_DASHBOARD_TAG = "telemetry_dashboard"
internal const val TELEMETRY_RAW_BUTTON_TAG = "telemetry_raw_button"

@Composable
internal fun DiagnosticTelemetryDashboard(
    samples: List<DiagnosticPerformanceSample>,
    finished: Boolean,
    comparisonSessions: List<StoredTelemetrySession> = emptyList()
) {
    val colors = MaterialTheme.colorScheme
    val chartGroups =
        remember(samples) {
            buildTelemetryChartGroups(samples)
        }
    val sustainedEvidence = remember(samples) { SustainedPerformanceAnalyzer.analyze(samples) }

    var selectedCategory by remember {
        mutableStateOf(TelemetryDashboardCategory.FLOW)
    }

    val categoryGroups = chartGroups[selectedCategory].orEmpty()

    var explicitlySelectedChartId by remember(selectedCategory) {
        mutableStateOf<String?>(null)
    }

    val selectedGroup =
        categoryGroups.firstOrNull { it.id == explicitlySelectedChartId }
            ?: preferredTelemetryChartGroup(categoryGroups)

    val capturedDurationSeconds =
        remember(samples) {
            val elapsed = samples.map { it.elapsedSeconds }.filter(Double::isFinite)
            if (elapsed.size >= 2) {
                ((elapsed.maxOrNull() ?: 0.0) - (elapsed.minOrNull() ?: 0.0)).coerceAtLeast(0.0)
            } else {
                0.0
            }
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TELEMETRY_DASHBOARD_TAG)
            .tutorialAnchor(TutorialTargets.Telemetry),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer.copy(alpha = 0.34f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            JargonAwareText(
                text = if (finished) "Final Performance Dashboard" else "Live Performance Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            JargonHelpNotice()
            JargonAwareText(
                text =
                    if (finished) {
                        "The complete telemetry timeline is frozen for inspection. Heap and runtime memory show different parts of app memory; GC marks automatic memory cleanup, and thermal throttling means heat may slow the processor."
                    } else {
                        "Charts update from the same telemetry measurements used by the raw view. Heap and runtime memory show different parts of app memory; GC marks automatic memory cleanup, and thermal throttling means heat may slow the processor."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            if (samples.size >= 2) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface.copy(alpha = 0.92f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        JargonAwareText(
                            "Sustained evidence · ${sustainedEvidence.stabilityLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (sustainedEvidence.hasSustainedWindow) {
                            JargonAwareText(
                                "Throughput retained ${formatTelemetryValue(sustainedEvidence.throughputRetentionPercent, "%")} · " +
                                    "CPU frequency retained ${formatTelemetryValue(sustainedEvidence.cpuFrequencyRetentionPercent, "%")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface
                            )
                            JargonAwareText(
                                "Process CPU ${formatTelemetryValue(sustainedEvidence.processCpuCoreEquivalentPercent, "%")} · " +
                                    "GC duty ${formatTelemetryValue(sustainedEvidence.garbageCollectionDutyPercent, "%")} · " +
                                    "peak runtime ${formatTelemetryValue(sustainedEvidence.peakRuntimeMemoryMb, "MiB")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            JargonAwareText(
                                "${sustainedEvidence.sampleCount} samples over " +
                                    "${formatTelemetryValue(sustainedEvidence.observedDurationSeconds, "s")}. " +
                                    "A sustained-performance claim requires at least 4 samples spanning 5 seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface
                            )
                            JargonAwareText(
                                "The individual telemetry charts remain valid snapshots; no retention or thermal-stability conclusion is inferred from this short run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                        JargonAwareText(
                            "Retention = last-quarter median / first-quarter median × 100 (max(1, N/4) timed captures each). Process CPU and GC duty divide cumulative counter change by elapsed time between first and last capture; missing/reset counters give N/A. Within-session evidence only. Cross-device claims require the same workload and settings; energy is not inferred without a power sensor.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.error
                        )
                    }
                }
            }

            TelemetryCategorySelector(
                selected = selectedCategory,
                onSelected = { selectedCategory = it }
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    JargonAwareText(
                        text = "${selectedCategory.icon} ${selectedCategory.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    JargonAwareText(
                        text = selectedCategory.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            TelemetryChartSelector(
                groups = categoryGroups,
                selectedId = selectedGroup?.id,
                onSelected = { explicitlySelectedChartId = it }
            )

            if (selectedGroup == null) {
                TelemetryEmptyChartMessage(
                    TelemetryChartEvidence(
                        status = if (finished) TelemetryEvidenceStatus.UNAVAILABLE else TelemetryEvidenceStatus.WAITING,
                        capturedPointCount = 0,
                        availableSeriesCount = 0,
                        expectedSeriesCount = 0,
                        title = if (finished) "No telemetry charts were produced" else "Preparing telemetry charts",
                        detail = "No compatible chart definition is available for this category."
                    )
                )
            } else {
                val combinedGroup =
                    remember(selectedGroup, selectedCategory, comparisonSessions) {
                        combineTelemetryComparisons(
                            current = selectedGroup,
                            category = selectedCategory,
                            sessions = comparisonSessions
                        )
                    }
                InteractiveTelemetryChart(
                    group = combinedGroup,
                    evidence = assessTelemetryChartEvidence(
                        group = selectedGroup,
                        capturedSampleCount = samples.size,
                        capturedDurationSeconds = capturedDurationSeconds,
                        finished = finished
                    )
                )
            }

            if (finished) {
                latestTelemetrySample(samples)?.let { sample ->
                    FinalTelemetryBarChart(
                        bars = buildFinalTelemetryBars(sample)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TelemetryCategorySelector(
    selected: TelemetryDashboardCategory,
    onSelected: (TelemetryDashboardCategory) -> Unit
) {
    CompactSelectionMenu(
        options = TelemetryDashboardCategory.entries,
        selected = selected,
        label = { category -> "${category.icon} ${category.label}" },
        onSelected = onSelected,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun TelemetryChartSelector(
    groups: List<TelemetryChartGroup>,
    selectedId: String?,
    onSelected: (String) -> Unit
) {
    val selectedGroup = groups.firstOrNull { it.id == selectedId } ?: groups.firstOrNull()
    if (selectedGroup != null) {
        CompactSelectionMenu(
            options = groups,
            selected = selectedGroup,
            label = { it.title },
            onSelected = { group -> onSelected(group.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun InteractiveTelemetryChart(
    group: TelemetryChartGroup,
    evidence: TelemetryChartEvidence? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val visibleSeries = group.series.filter { it.points.isNotEmpty() }
    val resolvedEvidence = evidence ?: assessTelemetryChartEvidence(
        group = group,
        capturedSampleCount = group.series.flatMap { it.points }.map { it.sampleIndex }.distinct().size,
        capturedDurationSeconds = group.series.flatMap { it.points }.let { points ->
            if (points.size >= 2) {
                ((points.maxOfOrNull { it.elapsedSeconds } ?: 0.0) -
                    (points.minOfOrNull { it.elapsedSeconds } ?: 0.0)).coerceAtLeast(0.0)
            } else {
                0.0
            }
        },
        finished = true
    )
    var selectedSampleIndex by remember(group.id, group.series) {
        mutableStateOf<Int?>(null)
    }
    var inspectorOpen by remember(group.id) {
        mutableStateOf(false)
    }
    var inspectorSeriesId by remember(group.id) { mutableStateOf<String?>(null) }
    val inspectorSeries = visibleSeries.firstOrNull { it.id == inspectorSeriesId } ?: visibleSeries.firstOrNull()
    val guide =
        remember(group.id, group.title, group.subtitle, group.unit) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.LINE,
                title = group.title,
                subtitle = group.subtitle,
                xAxisLabel = "Elapsed time (s)",
                yAxisLabel = group.unit,
                supportsDataInspector = true,
                directionOverride = telemetryReadingDirection(group.id)
            )
        }

    ChartSectionCard(
        title = group.title,
        subtitle = group.subtitle,
        guide = guide,
        // Live dashboard cards are interactive previews. The immutable, run-specific final figure
        // is rendered from the complete sample set by FinalTelemetryFigureArchiver.
        automaticExportKey = null,
        modifier =
            Modifier
                .testTag("telemetry-chart-gestures")
                .tutorialAnchor(TutorialTargets.TelemetryGestures)
    ) {
        if (visibleSeries.isEmpty()) {
            TelemetryEmptyChartMessage(resolvedEvidence)
        } else {
            if (presentation.showStatistics) {
                TelemetryEvidenceSummary(
                    evidence = resolvedEvidence,
                    maximumSeriesPointCount = visibleSeries.maxOf { it.points.size }
                )
            }
            TelemetryPlot(
                group = group.copy(series = visibleSeries),
                selectedSampleIndex = selectedSampleIndex,
                onSampleSelected = { selectedSampleIndex = it },
                onOpenInspector = { inspectorOpen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )

            if (presentation.showGuidance) {
                JargonAwareText(
                    text = "Tap the chart to inspect the nearest captured sample.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF31558B)
                )
            }

            Text("Captured points by signal: " + group.series.joinToString { "${it.label}: ${it.points.size}" } +
                ". Empty signals are unavailable; line breaks retain missing captures. Colors identify signals, not quality.",
                style = MaterialTheme.typography.bodySmall)
            if (visibleSeries.size > 1) {
                Text("Signal to inspect", style = MaterialTheme.typography.labelLarge)
                CompactSelectionMenu(options = visibleSeries, selected = inspectorSeries,
                    label = { it.label }, onSelected = { inspectorSeriesId = it.id })
            }
            OutlinedButton(
                onClick = { inspectorOpen = true },
                modifier = Modifier.fillMaxWidth().testTag("telemetry-open-chart-inspector")
            ) {
                Text("Open chart inspector")
            }

            if (presentation.showLegend) {
                visibleSeries.chunked(2).forEachIndexed { rowIndex, seriesRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seriesRow.forEachIndexed { columnIndex, series ->
                            val color = telemetrySeriesColor(rowIndex * 2 + columnIndex)
                            TelemetryLegendPill(
                                label = series.label,
                                value = series.points.lastOrNull()?.value,
                                unit = group.unit,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (seriesRow.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (presentation.showStatistics) {
                selectedSampleIndex?.let { selected ->
                    TelemetrySelectionCard(
                        group = group.copy(series = visibleSeries),
                        sampleIndex = selected
                    )
                }
            }
        }
    }

    if (inspectorOpen) {
        TelemetryInspectorDialog(
            group = group.copy(series = listOfNotNull(inspectorSeries)),
            color = telemetrySeriesColor(visibleSeries.indexOf(inspectorSeries).coerceAtLeast(0)),
            onDismiss = { inspectorOpen = false }
        )
    }

}

@Composable
private fun TelemetryPlot(
    group: TelemetryChartGroup,
    selectedSampleIndex: Int?,
    onSampleSelected: (Int) -> Unit,
    onOpenInspector: () -> Unit,
    modifier: Modifier
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val allPoints = group.series.flatMap { it.points }
    val xMin = allPoints.minOfOrNull { it.elapsedSeconds } ?: 0.0
    val xMax = allPoints.maxOfOrNull { it.elapsedSeconds } ?: 1.0
    val rawYMin = allPoints.minOfOrNull { it.value } ?: 0.0
    val rawYMax = allPoints.maxOfOrNull { it.value } ?: 1.0
    val xRange = max(xMax - xMin, 1e-9)
    val rawYRange = max(rawYMax - rawYMin, 1e-9)
    val yPadding = max(rawYRange * 0.1, max(abs(rawYMax), 1.0) * 0.015)
    val yMin = rawYMin - yPadding
    val yMax = rawYMax + yPadding
    val yRange = max(yMax - yMin, 1e-9)

    Canvas(
        modifier =
            modifier
                .background(
                    if (publicationMode) Color.White else Color(0xFFF4F7FB),
                    if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
                )
                .pointerInput(group, xMin, xRange) {
                    detectTapGestures(
                        onDoubleTap = { onOpenInspector() },
                        onTap = { tap ->
                            val left = 64.dp.toPx()
                            val right = 16.dp.toPx()
                            val width = (size.width - left - right).coerceAtLeast(1f)
                            val tappedTime = xMin + ((tap.x - left) / width).coerceIn(0f, 1f) * xRange
                            allPoints.minByOrNull { abs(it.elapsedSeconds - tappedTime) }
                                ?.let { onSampleSelected(it.sampleIndex) }
                        }
                    )
                }
    ) {
        val left = 64.dp.toPx()
        val right = 16.dp.toPx()
        val top = 26.dp.toPx()
        val bottom = 45.dp.toPx()
        val width = (size.width - left - right).coerceAtLeast(1f)
        val height = (size.height - top - bottom).coerceAtLeast(1f)

        if (presentation.showGrid) {
            repeat(5) { gridIndex ->
                val ratio = gridIndex / 4f
                val y = top + height * ratio
                drawLine(
                    color = Color(0xFFD9E2EC),
                    start = Offset(left, y),
                    end = Offset(left + width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        drawLine(
            color = Color(0xFF7B8794),
            start = Offset(left, top),
            end = Offset(left, top + height),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color(0xFF7B8794),
            start = Offset(left, top + height),
            end = Offset(left + width, top + height),
            strokeWidth = 1.dp.toPx()
        )

        val selectedPoint = allPoints.firstOrNull { it.sampleIndex == selectedSampleIndex }
        if (selectedPoint != null) {
            val selectedX = left + width * ((selectedPoint.elapsedSeconds - xMin) / xRange).toFloat()
            drawLine(
                color = Color(0xFF7B8794),
                start = Offset(selectedX, top),
                end = Offset(selectedX, top + height),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        group.series.forEachIndexed { seriesIndex, series ->
            val color = telemetrySeriesColor(seriesIndex)
            val displayPoints = telemetryPointsForRendering(series.points)
            val path = Path()
            displayPoints.forEachIndexed { pointIndex, point ->
                val x = left + width * ((point.elapsedSeconds - xMin) / xRange).toFloat()
                val y = top + height - height * ((point.value - yMin) / yRange).toFloat()
                if (pointIndex == 0 || point.breakBefore) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (presentation.showLines && displayPoints.size >= 2) {
                val lineWidth =
                    when {
                        displayPoints.size <= 24 -> 1.8.dp.toPx()
                        displayPoints.size <= 128 -> 1.3.dp.toPx()
                        else -> 0.85.dp.toPx()
                    }
                drawPath(path, color, style = Stroke(width = lineWidth))
            }
            val showMarkersForDensity =
                presentation.showMarkers &&
                    (displayPoints.size <= 1 || width / (displayPoints.size - 1) >= 4.dp.toPx())
            if (showMarkersForDensity) {
                displayPoints.forEach { point ->
                    val x = left + width * ((point.elapsedSeconds - xMin) / xRange).toFloat()
                    val y = top + height - height * ((point.value - yMin) / yRange).toFloat()
                    val selected = point.sampleIndex == selectedSampleIndex
                    if (selected) {
                        drawCircle(Color.White, radius = 5.5.dp.toPx(), center = Offset(x, y))
                    }
                    drawCircle(
                        color,
                        radius = if (selected) 3.8.dp.toPx() else 2.1.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            if (presentation.showValueLabels) series.points.lastOrNull()?.let { last ->
                val x = left + width * ((last.elapsedSeconds - xMin) / xRange).toFloat()
                val y = top + height - height * ((last.value - yMin) / yRange).toFloat()
                drawTelemetryLabel(
                    text = formatTelemetryValue(last.value, group.unit),
                    x = x,
                    y = (y - 9.dp.toPx() - seriesIndex * 10.dp.toPx()).coerceAtLeast(11.dp.toPx()),
                    color = color
                )
            }
        }

        drawTelemetryAxisLabel(formatTelemetryValue(rawYMax, group.unit), 4.dp.toPx(), top + 8.dp.toPx())
        drawTelemetryAxisLabel(formatTelemetryValue(rawYMin, group.unit), 4.dp.toPx(), top + height)
        drawTelemetryAxisLabel(formatElapsedTime(xMin), left, size.height - 22.dp.toPx())
        drawTelemetryAxisLabel(formatElapsedTime(xMax), left + width - 36.dp.toPx(), size.height - 22.dp.toPx())
        drawTelemetryAxisTitle("Elapsed time (s)", left + width / 2f, size.height - 7.dp.toPx())
        drawTelemetryAxisLabel(group.unit, 4.dp.toPx(), 13.dp.toPx())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTelemetryLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color
) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 8.dp.toPx()
            textAlign = Paint.Align.RIGHT
            this.color = color.toArgb()
            isFakeBoldText = true
        }
        canvas.nativeCanvas.drawText(text.take(13), x - 6.dp.toPx(), y, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTelemetryAxisLabel(
    text: String,
    x: Float,
    y: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 8.dp.toPx()
            this.color = Color(0xFF52606D).toArgb()
        }
        canvas.nativeCanvas.drawText(text.take(12), x, y, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTelemetryAxisTitle(
    text: String,
    x: Float,
    y: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 9.dp.toPx()
            textAlign = Paint.Align.CENTER
            this.color = Color(0xFF334E68).toArgb()
            isFakeBoldText = true
        }
        canvas.nativeCanvas.drawText(text.take(28), x, y, paint)
    }
}

@Composable
private fun TelemetryLegendPill(
    label: String,
    value: Double?,
    unit: String,
    color: Color,
    modifier: Modifier
) {
    val publicationMode =
        LocalChartPresentationController.current.preferences.preset ==
            ChartPresentationPreset.PUBLICATION
    Row(
        modifier = modifier
            .background(
                if (publicationMode) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
                if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.background(color, RoundedCornerShape(99.dp)).padding(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            JargonAwareText(label, style = MaterialTheme.typography.labelSmall, maxLines = 3)
            JargonAwareText(
                value?.let { formatTelemetryValue(it, unit) } ?: "N/A",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetrySelectionCard(
    group: TelemetryChartGroup,
    sampleIndex: Int
) {
    val sampleTime = group.series.asSequence().flatMap { it.points.asSequence() }
        .firstOrNull { it.sampleIndex == sampleIndex }
        ?.elapsedSeconds

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            JargonAwareText(
                "Sample $sampleIndex · ${sampleTime?.let(::formatElapsedTime) ?: "N/A"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            group.series.forEachIndexed { index, series ->
                val point = series.points.firstOrNull { it.sampleIndex == sampleIndex }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.background(telemetrySeriesColor(index), RoundedCornerShape(99.dp)).padding(4.dp))
                    JargonAwareText(series.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    JargonAwareText(
                        point?.let { formatTelemetryValue(it.value, group.unit) } ?: "N/A",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryInspectorDialog(group: TelemetryChartGroup, color: Color, onDismiss: () -> Unit) {
    val series = group.series.firstOrNull() ?: return
    val points = remember(series) { telemetryInspectorPoints(series) }
    val stats = remember(points) { computeLineChartStats(points) } ?: return
    val guide = ChartGuideFactory.forChart(kind = ChartGuideKind.LINE, title = group.title,
        subtitle = group.subtitle, xAxisLabel = "Elapsed time (s)", yAxisLabel = group.unit,
        supportsDataInspector = true, directionOverride = telemetryReadingDirection(group.id))
    LineChartInspectorDialog(title = "${group.title} · ${series.label}", subtitle = group.subtitle,
        points = points, stats = stats, xAxisLabel = "Elapsed time (s)", yAxisLabel = group.unit,
        color = color, guide = guide, onDismiss = onDismiss)
}

@Composable
private fun FinalTelemetryBarChart(
    bars: List<TelemetryFinalBar>
) {
    if (bars.isEmpty()) return
    val presentation = LocalChartPresentationController.current.preferences
    val guide =
        remember {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.BAR,
                title = "Final memory snapshot",
                subtitle = "Snapshot only. Runtime bars / managed heap maximum; native bars / native heap size; system bars / device total. Ratios have different denominators and are not comparable resources.",
                xAxisLabel = "Share of matching capacity",
                yAxisLabel = "Memory category",
                directionOverride = ChartReadingDirection.TARGET_DEPENDENT
            )
        }
    ChartSectionCard(
        title = "Final memory snapshot",
        subtitle = "Snapshot only. Runtime bars / managed heap maximum; native bars / native heap size; system bars / device total. Ratios have different denominators and are not comparable resources.",
        guide = guide,
        // Avoid a second UI-driven copy beside the deterministic final-run archive.
        automaticExportKey = null
    ) {
        bars.forEachIndexed { index, bar ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    JargonAwareText(bar.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    if (presentation.showValueLabels) {
                        JargonAwareText(
                            "${formatTelemetryNumber(bar.value)} / ${formatTelemetryNumber(bar.capacity)} ${bar.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                RoundedRatioBar(
                    ratio = (bar.value / bar.capacity).coerceIn(0.0, 1.0),
                    color = telemetrySeriesColor(index),
                    modifier = Modifier.height(14.dp)
                )
            }
        }
    }
}

private fun telemetryReadingDirection(groupId: String): ChartReadingDirection =
    when (groupId) {
        "throughput", "sustained_retention" -> ChartReadingDirection.HIGHER_TENDS_BETTER
        "eta" -> ChartReadingDirection.PATTERN_NOT_RANK
        "thermal_timeline", "process_utilisation", "runtime_memory", "system_memory", "system_load" ->
            ChartReadingDirection.TARGET_DEPENDENT
        else -> ChartReadingDirection.PATTERN_NOT_RANK
    }

@Composable
private fun TelemetryEvidenceSummary(
    evidence: TelemetryChartEvidence,
    maximumSeriesPointCount: Int
) {
    val detail =
        when {
            maximumSeriesPointCount < 2 ->
                "Snapshot only: one measured point is available, so no time trend can be inferred."

            evidence.hasPartialSeries ->
                evidence.detail

            else ->
                "${evidence.capturedPointCount} measured chart points · ${evidence.availableSeriesCount} signal(s)."
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (evidence.hasPartialSeries) Color(0xFFFFF8E1) else Color(0xFFE8F5E9)
    ) {
        JargonAwareText(
            detail,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF455A64)
        )
    }
}

@Composable
private fun TelemetryEmptyChartMessage(
    evidence: TelemetryChartEvidence
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color =
            if (evidence.status == TelemetryEvidenceStatus.UNAVAILABLE) {
                Color(0xFFFFF8E1)
            } else {
                Color(0xFFF8FAFC)
            }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            JargonAwareText(
                evidence.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF102A43)
            )
            JargonAwareText(
                evidence.detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF52606D)
            )
        }
    }
}

internal fun downsampleTelemetryPoints(
    points: List<TelemetryChartPoint>,
    maximumPoints: Int
): List<TelemetryChartPoint> {
    require(maximumPoints > 0) { "maximumPoints must be positive." }
    return telemetryPointsForRendering(points)
}

/** Every captured measurement is part of both the interactive and exported scientific curve. */
internal fun telemetryPointsForRendering(
    points: List<TelemetryChartPoint>
): List<TelemetryChartPoint> = points

private fun telemetrySeriesColor(index: Int): Color =
    TELEMETRY_COLORS[index.mod(TELEMETRY_COLORS.size)]

private fun formatTelemetryValue(value: Double, unit: String): String =
    if (value.isFinite()) "${formatTelemetryNumber(value)} $unit" else "N/A"

private fun formatTelemetryNumber(value: Double): String =
    if (!value.isFinite()) {
        "N/A"
    } else if (abs(value) >= 1_000.0) {
        String.format(Locale.US, "%.0f", value)
    } else if (abs(value) >= 100.0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        String.format(Locale.US, "%.2f", value)
    }

private fun formatElapsedTime(seconds: Double): String =
    if (seconds.isFinite()) String.format(Locale.US, "%.1fs", seconds) else "N/A"

private val TELEMETRY_COLORS =
    listOf(
        Color(0xFF1565C0),
        Color(0xFFE65100),
        Color(0xFF2E7D32),
        Color(0xFF7B1FA2),
        Color(0xFFC62828),
        Color(0xFF00838F),
        Color(0xFF6D4C41),
        Color(0xFF455A64),
        Color(0xFFAD1457),
        Color(0xFF558B2F),
        Color(0xFF283593),
        Color(0xFFF9A825),
        Color(0xFF00695C),
        Color(0xFF8D6E63),
        Color(0xFF4527A0),
        Color(0xFF0277BD),
        Color(0xFFD84315),
        Color(0xFF37474F)
    )

private fun combineTelemetryComparisons(
    current: TelemetryChartGroup,
    category: TelemetryDashboardCategory,
    sessions: List<StoredTelemetrySession>
): TelemetryChartGroup {
    if (sessions.isEmpty()) return current
    val currentSeries =
        current.series.map { series ->
            series.copy(id = "current_${series.id}", label = "Current · ${series.label}")
        }
    val comparisons =
        sessions.flatMapIndexed { sessionIndex, session ->
            val matching = session.chartGroups[category].orEmpty().firstOrNull { it.id == current.id }
                ?: return@flatMapIndexed emptyList()
            val sessionLabel = "S${sessionIndex + 1} ${session.sessionType}"
            matching.series.map { series ->
                series.copy(
                    id = "${session.id}_${series.id}",
                    label = "$sessionLabel · ${series.label}"
                )
            }
        }
    return current.copy(
        subtitle = "${current.subtitle} Overlay: current + ${sessions.size} saved session(s).",
        series = currentSeries + comparisons
    )
}
