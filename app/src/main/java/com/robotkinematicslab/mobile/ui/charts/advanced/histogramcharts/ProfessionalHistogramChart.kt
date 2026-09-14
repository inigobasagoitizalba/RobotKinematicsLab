package com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.EmptyProfessionalChartMessage
import com.robotkinematicslab.mobile.ui.charts.basic.ChartAxisInfoRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartLegendMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
fun ProfessionalHistogramChart(
    title: String,
    subtitle: String,
    values: List<Double>,
    xAxisLabel: String,
    yAxisLabel: String = "Frequency",
    binCount: Int = 10,
    color: Color,
    logScale: Boolean = false,
    modifier: Modifier = Modifier,
    directionOverride: ChartReadingDirection? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    var inspectorOpen by remember {
        mutableStateOf(false)
    }

    val cleanValues =
        remember(
            values,
            logScale
        ) {
            cleanHistogramValues(
                values = values,
                logScale = logScale
            )
        }

    val bins =
        remember(
            cleanValues,
            binCount
        ) {
            buildHistogramBins(
                values = cleanValues,
                requestedBinCount = binCount.coerceAtLeast(1)
            )
        }

    val stats =
        remember(
            cleanValues,
            bins
        ) {
            computeHistogramStats(
                values = cleanValues,
                bins = bins
            )
        }

    val guide =
        remember(title, subtitle, xAxisLabel, yAxisLabel, directionOverride) {
            ChartGuideFactory.forChart(
                kind = ChartGuideKind.HISTOGRAM,
                title = title,
                subtitle = subtitle,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                supportsDataInspector = true,
                directionOverride = directionOverride
            )
        }

    ChartSectionCard(
        title = title,
        subtitle = subtitle,
        guide = guide
    ) {
        if (cleanValues.isEmpty() || bins.isEmpty() || stats == null) {
            EmptyProfessionalChartMessage()
            return@ChartSectionCard
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    histogramPreviewHeight(
                        sampleCount = cleanValues.size,
                        binCount = bins.size
                    ).dp
                )
                .background(
                    color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
                    shape = if (publicationMode) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                )
                .clickable {
                    inspectorOpen = true
                }
        ) {
            HistogramCanvas(
                bins = bins,
                stats = stats,
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                color = color,
                scale = 1f,
                offsetX = 0f,
                showAxisLabels = true,
                showBinLabels = false,
                modifier = Modifier.fillMaxSize()
            )

            if (!publicationMode && !com.robotkinematicslab.mobile.ui.charts.basic.LocalChartFigureCaptureInProgress.current) Text(
                text = "Open chart inspector",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF202124)
            )
        }

        if (presentation.showStatistics) {
            ChartAxisInfoRow(xAxisLabel = xAxisLabel, yAxisLabel = yAxisLabel)
        }

        if (presentation.showLegend) {
            ChartLegendMetricRow(label = "Bars", value = bins.size.toString(), color = color)
        }

        if (presentation.showStatistics) {
            ChartMetricRow(label = "Samples", value = stats.sampleCount.toString())
            ChartMetricRow(label = "Min", value = formatHistogramDouble(stats.min))
            ChartMetricRow(label = "Max", value = formatHistogramDouble(stats.max))
            ChartMetricRow(label = "Mean", value = formatHistogramDouble(stats.mean))
            ChartMetricRow(label = "Median", value = formatHistogramDouble(stats.median))
            ChartMetricRow(label = "Std. deviation", value = formatHistogramDouble(stats.standardDeviation))
            ChartMetricRow(
                label = "Peak bin",
                value = "${stats.peakBin.count} samples · ${stats.peakBin.label}"
            )
            if (logScale) ChartMetricRow(label = "Scale", value = "Natural log")
        }
    }

    if (inspectorOpen && stats != null) {
        HistogramInspectorDialog(
            title = title,
            subtitle = subtitle,
            bins = bins,
            stats = stats,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            color = color,
            guide = guide,
            onDismiss = {
                inspectorOpen = false
            }
        )
    }
}
