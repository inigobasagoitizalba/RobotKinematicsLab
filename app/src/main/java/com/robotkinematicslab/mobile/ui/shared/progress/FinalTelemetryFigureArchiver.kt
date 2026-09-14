package com.robotkinematicslab.mobile.ui.shared.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import com.robotkinematicslab.mobile.ui.charts.presentation.AutomaticFigureExportStatus
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immutable description of one figure that must exist when a telemetry run completes.
 *
 * This inventory is deliberately independent from the dashboard's selected drop-down item: the
 * dashboard remains an interactive preview, while this model represents the complete scientific
 * evidence contract for the run.
 */
internal data class FinalTelemetryFigureSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val unit: String,
    val series: List<TelemetryChartSeries> = emptyList(),
    val bars: List<TelemetryFinalBar> = emptyList()
) {
    init {
        require(series.isEmpty() || bars.isEmpty()) { "A telemetry figure cannot mix lines and bars." }
    }

    val isBarFigure: Boolean
        get() = id == "final_memory_snapshot"

    val figureType: String
        get() = if (isBarFigure) "telemetry-bar-$id" else "telemetry-line-$id"

    val dataKey: String
        get() = buildString {
            append("telemetry-final-v1|")
            appendEscaped(id)
            append('|')
            appendEscaped(unit)
            series.forEach { currentSeries ->
                append("|series:")
                appendEscaped(currentSeries.id)
                append(':')
                appendEscaped(currentSeries.label)
                currentSeries.points.forEach { point ->
                    append('|')
                    append(point.sampleIndex)
                    append(':')
                    append(point.elapsedSeconds.toBits().toString(16))
                    append(':')
                    append(point.value.toBits().toString(16))
                    append(point.breakBefore)
                }
            }
            bars.forEach { bar ->
                append("|bar:")
                appendEscaped(bar.label)
                append(':')
                append(bar.value.toBits().toString(16))
                append(':')
                append(bar.capacity.toBits().toString(16))
                append(':')
                appendEscaped(bar.unit)
            }
        }
}

internal val REQUIRED_FINAL_TELEMETRY_FIGURE_IDS: List<String> =
    listOf(
        "throughput",
        "eta",
        "sustained_retention",
        "thermal_timeline",
        "process_utilisation",
        "runtime_memory",
        "native_heap",
        "system_memory",
        "system_load",
        "cpu_time",
        "cpu_frequency",
        "gc_counts",
        "gc_time",
        "gc_count_rate",
        "gc_time_rate",
        "allocation_pressure",
        "hotspot_calls",
        "hotspot_total",
        "hotspot_latency",
        "final_memory_snapshot"
    )

internal fun buildFinalTelemetryFigureSpecs(
    samples: List<DiagnosticPerformanceSample>
): List<FinalTelemetryFigureSpec> {
    require(samples.isNotEmpty()) { "Final telemetry figures require at least one captured sample." }
    val lineFigures =
        buildTelemetryChartGroups(samples).values.flatten().map { group ->
            FinalTelemetryFigureSpec(
                id = group.id,
                title = group.title,
                subtitle = group.subtitle,
                unit = group.unit,
                series = group.series
            )
        }
    val finalSnapshot =
        FinalTelemetryFigureSpec(
            id = "final_memory_snapshot",
            title = "Final memory snapshot",
            subtitle = "Last timed snapshot, in MiB: runtime / heap maximum; native / native size; system / device total. Different denominators are not comparable capacities.",
            unit = "% of capacity",
            bars = latestTelemetrySample(samples)?.let(::buildFinalTelemetryBars).orEmpty()
        )
    return (lineFigures + finalSnapshot).also { figures ->
        check(figures.map { it.id }.distinct().size == figures.size) {
            "The final telemetry figure inventory contains duplicate identifiers."
        }
    }
}

internal fun finalTelemetryArchiveKey(
    samples: List<DiagnosticPerformanceSample>
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    buildFinalTelemetryFigureSpecs(samples).forEach { figure ->
        digest.update(figure.dataKey.toByteArray(Charsets.UTF_8))
        digest.update(byteArrayOf(0))
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal data class FinalTelemetryArchiveResult(
    val expectedFigureCount: Int,
    val generatedFigureCount: Int,
    val existingFigureCount: Int,
    val failures: List<String>
) {
    val complete: Boolean
        get() = failures.isEmpty() && generatedFigureCount + existingFigureCount == expectedFigureCount
}

/** Archives one deterministic final PNG for every telemetry figure in a completed run. */
internal class FinalTelemetryFigureArchiver(
    private val context: Context
) {
    suspend fun archive(
        projectId: String,
        session: StoredTelemetrySession,
        samples: List<DiagnosticPerformanceSample>
    ): FinalTelemetryArchiveResult = withContext(Dispatchers.IO) {
        val figures = buildFinalTelemetryFigureSpecs(samples)
        val paths = AppStoragePaths(context).also(AppStoragePaths::ensureStructureAndMigrateLegacyData)
        val executionId = "telemetry-${session.id}"
        val existingEntries =
            readExistingEntries(
                figuresDirectory = paths.figuresDirectory,
                analysisId = projectId,
                executionId = executionId
            )
        var generated = 0
        var existing = 0
        val failures = mutableListOf<String>()
        val presentationFingerprint =
            ChartFigureExporter.automaticDataFingerprint(TELEMETRY_RENDER_PRESENTATION_VERSION)

        figures.forEach { figure ->
            val dataFingerprint = ChartFigureExporter.automaticDataFingerprint(figure.dataKey)
            val alreadyGenerated =
                existingEntries.any { entry ->
                        entry.status == AutomaticFigureExportStatus.GENERATED &&
                        entry.figureType == figure.figureType &&
                        entry.dataFingerprint == dataFingerprint &&
                        entry.presentationFingerprint == presentationFingerprint
                }
            if (alreadyGenerated) {
                existing += 1
                return@forEach
            }

            val bitmap = FinalTelemetryBitmapRenderer.render(session.title, figure)
            try {
                ChartFigureExporter.saveAutomaticPng(
                    context = context,
                    title = "${session.title} · ${figure.title}",
                    collection = "telemetry",
                    analysisId = projectId,
                    executionId = executionId,
                    figureType = figure.figureType,
                    dataFingerprint = dataFingerprint,
                    presentationFingerprint = presentationFingerprint,
                    image = bitmap.asImageBitmap()
                ).fold(
                    onSuccess = { generated += 1 },
                    onFailure = { error ->
                        failures += "${figure.id}: ${error.message ?: error::class.java.simpleName}"
                    }
                )
            } finally {
                bitmap.recycle()
            }
        }

        FinalTelemetryArchiveResult(
            expectedFigureCount = figures.size,
            generatedFigureCount = generated,
            existingFigureCount = existing,
            failures = failures
        )
    }

    private fun readExistingEntries(
        figuresDirectory: File,
        analysisId: String,
        executionId: String
    ) = File(File(figuresDirectory, "automatic"), "telemetry")
        .listFiles(File::isDirectory)
        .orEmpty()
        .map { dateDirectory ->
            File(
                File(
                    dateDirectory,
                    ChartFigureExporter.safeFileStem(analysisId)
                ),
                ChartFigureExporter.safeFileStem(executionId)
            )
        }
        .map { executionDirectory -> File(executionDirectory, "figure-index.properties") }
        .filter(File::isFile)
        .flatMap { manifest ->
            runCatching { ChartFigureExporter.readManifestEntries(manifest) }.getOrDefault(emptyList())
        }
}

private object FinalTelemetryBitmapRenderer {
    private const val WIDTH = 1600
    private const val HEIGHT = 1000
    private const val LEFT = 145f
    private const val RIGHT = 65f
    private const val TOP = 245f
    private const val BOTTOM = 125f
    private val seriesColors =
        intArrayOf(
            Color.rgb(37, 99, 235),
            Color.rgb(5, 150, 105),
            Color.rgb(220, 38, 38),
            Color.rgb(124, 58, 237),
            Color.rgb(217, 119, 6),
            Color.rgb(8, 145, 178)
        )

    fun render(runTitle: String, figure: FinalTelemetryFigureSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawHeader(canvas, runTitle, figure)
        if (figure.isBarFigure) {
            drawBars(canvas, figure.bars)
        } else {
            drawLines(canvas, figure)
        }
        drawFooter(canvas, figure)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas, runTitle: String, figure: FinalTelemetryFigureSpec) {
        canvas.drawText(
            figure.title,
            LEFT,
            80f,
            textPaint(44f, Color.rgb(15, 23, 42), bold = true)
        )
        canvas.drawText(
            runTitle.take(100),
            LEFT,
            126f,
            textPaint(25f, Color.rgb(49, 85, 139), bold = true)
        )
        drawWrappedText(
            canvas = canvas,
            text = figure.subtitle,
            x = LEFT,
            firstBaseline = 168f,
            maxWidth = WIDTH - LEFT - RIGHT,
            paint = textPaint(23f, Color.rgb(71, 85, 105)),
            lineHeight = 31f,
            maximumLines = 2
        )
    }

    private fun drawLines(canvas: Canvas, figure: FinalTelemetryFigureSpec) {
        val availableSeries = figure.series.filter { it.points.isNotEmpty() }
        if (availableSeries.isEmpty()) {
            drawUnavailable(canvas)
            return
        }
        val allPoints = availableSeries.flatMap { it.points }
        val xMin = allPoints.minOf { it.elapsedSeconds }
        val xMax = allPoints.maxOf { it.elapsedSeconds }
        val yMinRaw = allPoints.minOf { it.value }
        val yMaxRaw = allPoints.maxOf { it.value }
        val xRange = max(xMax - xMin, 1e-12)
        val rawYRange = max(yMaxRaw - yMinRaw, 1e-12)
        val yPadding = max(rawYRange * 0.08, max(abs(yMaxRaw), 1.0) * 0.012)
        val yMin = yMinRaw - yPadding
        val yMax = yMaxRaw + yPadding
        val yRange = max(yMax - yMin, 1e-12)
        val plotWidth = WIDTH - LEFT - RIGHT
        val plotHeight = HEIGHT - TOP - BOTTOM

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 2f
        }
        repeat(6) { index ->
            val ratio = index / 5f
            val y = TOP + plotHeight * ratio
            canvas.drawLine(LEFT, y, LEFT + plotWidth, y, gridPaint)
            val value = yMax - (yRange * ratio)
            canvas.drawText(
                formatNumber(value),
                LEFT - 18f,
                y + 8f,
                textPaint(20f, Color.rgb(71, 85, 105), align = Paint.Align.RIGHT)
            )
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            strokeWidth = 3f
        }
        canvas.drawLine(LEFT, TOP, LEFT, TOP + plotHeight, axisPaint)
        canvas.drawLine(LEFT, TOP + plotHeight, LEFT + plotWidth, TOP + plotHeight, axisPaint)

        availableSeries.forEachIndexed { index, series ->
            val color = seriesColors[index % seriesColors.size]
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            // Final evidence is rendered from every captured sample. The high-resolution bitmap is
            // deliberately independent from the compact dashboard's preview level of detail.
            val points = telemetryPointsForRendering(series.points)
            val path = Path()
            points.forEachIndexed { pointIndex, point ->
                val x = LEFT + ((point.elapsedSeconds - xMin) / xRange * plotWidth).toFloat()
                val y = TOP + plotHeight - ((point.value - yMin) / yRange * plotHeight).toFloat()
                if (pointIndex == 0 || point.breakBefore) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (points.size > 1) canvas.drawPath(path, paint)
            points.lastOrNull()?.let { last ->
                val x = LEFT + ((last.elapsedSeconds - xMin) / xRange * plotWidth).toFloat()
                val y = TOP + plotHeight - ((last.value - yMin) / yRange * plotHeight).toFloat()
                canvas.drawCircle(x, y, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }
        }

        canvas.drawText(
            formatElapsed(xMin),
            LEFT,
            TOP + plotHeight + 36f,
            textPaint(20f, Color.rgb(71, 85, 105))
        )
        canvas.drawText(
            formatElapsed(xMax),
            LEFT + plotWidth,
            TOP + plotHeight + 36f,
            textPaint(20f, Color.rgb(71, 85, 105), align = Paint.Align.RIGHT)
        )
        canvas.drawText(
            "Elapsed time (s)",
            LEFT + plotWidth / 2f,
            TOP + plotHeight + 72f,
            textPaint(22f, Color.rgb(51, 65, 85), bold = true, align = Paint.Align.CENTER)
        )
        canvas.drawText(
            figure.unit,
            24f,
            TOP - 20f,
            textPaint(21f, Color.rgb(51, 65, 85), bold = true)
        )

        var legendX = LEFT
        var legendY = HEIGHT - 38f
        availableSeries.forEachIndexed { index, series ->
            val label = series.label.take(32)
            val labelPaint = textPaint(21f, Color.rgb(30, 41, 59))
            val itemWidth = 44f + labelPaint.measureText(label) + 38f
            if (legendX + itemWidth > WIDTH - RIGHT) {
                legendX = LEFT
                legendY += 32f
            }
            val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = seriesColors[index % seriesColors.size] }
            canvas.drawCircle(legendX + 9f, legendY - 7f, 8f, colorPaint)
            canvas.drawText(label, legendX + 28f, legendY, labelPaint)
            legendX += itemWidth
        }
    }

    private fun drawBars(canvas: Canvas, bars: List<TelemetryFinalBar>) {
        if (bars.isEmpty()) {
            drawUnavailable(canvas)
            return
        }
        val plotWidth = WIDTH - LEFT - RIGHT
        val availableHeight = HEIGHT - TOP - BOTTOM
        val barStride = availableHeight / bars.size
        val labelPaint = textPaint(22f, Color.rgb(30, 41, 59), bold = true)
        val valuePaint = textPaint(20f, Color.rgb(71, 85, 105), align = Paint.Align.RIGHT)
        bars.forEachIndexed { index, bar ->
            val y = TOP + index * barStride
            val ratio = (bar.value / bar.capacity).coerceIn(0.0, 1.0).toFloat()
            canvas.drawText(bar.label, LEFT, y + 24f, labelPaint)
            canvas.drawText(
                "${formatNumber(bar.value)} ${bar.unit} (${formatNumber(ratio.toDouble() * 100.0)}%)",
                LEFT + plotWidth,
                y + 24f,
                valuePaint
            )
            val trackTop = y + 38f
            val trackBottom = trackTop + 22f
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240) }
            canvas.drawRoundRect(LEFT, trackTop, LEFT + plotWidth, trackBottom, 11f, 11f, trackPaint)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = seriesColors[index % seriesColors.size] }
            canvas.drawRoundRect(LEFT, trackTop, LEFT + plotWidth * ratio, trackBottom, 11f, 11f, fillPaint)
        }
    }

    private fun drawUnavailable(canvas: Canvas) {
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 250, 252) }
        canvas.drawRoundRect(LEFT, TOP, WIDTH - RIGHT, HEIGHT - BOTTOM, 22f, 22f, boxPaint)
        canvas.drawText(
            "Metric unavailable for this completed run",
            WIDTH / 2f,
            (TOP + HEIGHT - BOTTOM) / 2f,
            textPaint(30f, Color.rgb(71, 85, 105), bold = true, align = Paint.Align.CENTER)
        )
        canvas.drawText(
            "No value has been invented; the absence is retained as evidence.",
            WIDTH / 2f,
            (TOP + HEIGHT - BOTTOM) / 2f + 48f,
            textPaint(23f, Color.rgb(100, 116, 139), align = Paint.Align.CENTER)
        )
    }

    private fun drawFooter(canvas: Canvas, figure: FinalTelemetryFigureSpec) {
        canvas.drawText(
            "Final run state · ${figure.id} · Source: application telemetry",
            WIDTH - RIGHT,
            HEIGHT - 18f,
            textPaint(18f, Color.rgb(100, 116, 139), align = Paint.Align.RIGHT)
        )
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        firstBaseline: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float,
        maximumLines: Int
    ) {
        val words = text.split(Regex("\\s+")).filter(String::isNotBlank)
        var line = ""
        var lineIndex = 0
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = candidate
            } else {
                canvas.drawText(line, x, firstBaseline + lineIndex * lineHeight, paint)
                lineIndex += 1
                if (lineIndex >= maximumLines) return
                line = word
            }
        }
        if (line.isNotEmpty() && lineIndex < maximumLines) {
            canvas.drawText(line, x, firstBaseline + lineIndex * lineHeight, paint)
        }
    }

    private fun textPaint(
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        textAlign = align
    }

    private fun formatNumber(value: Double): String =
        when {
            !value.isFinite() -> "N/A"
            abs(value) >= 1_000.0 -> String.format(java.util.Locale.US, "%,.0f", value)
            abs(value) >= 100.0 -> String.format(java.util.Locale.US, "%.1f", value)
            else -> String.format(java.util.Locale.US, "%.2f", value)
        }

    private fun formatElapsed(value: Double): String =
        if (value.isFinite()) String.format(java.util.Locale.US, "%.2f s", value) else "N/A"
}

private const val TELEMETRY_RENDER_PRESENTATION_VERSION = "telemetry-final-render-v2-full-detail"

private fun StringBuilder.appendEscaped(value: String) {
    append(value.replace("\\", "\\\\").replace("|", "\\|").replace(":", "\\:"))
}
