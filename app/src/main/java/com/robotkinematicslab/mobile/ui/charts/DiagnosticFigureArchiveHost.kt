package com.robotkinematicslab.mobile.ui.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.charts.basic.LocalChartCardsCollapsible
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Invisible, bounded evidence renderer for one completed diagnostic run.
 *
 * It advances through analysis families sequentially so only one family is resident for automatic
 * PNG capture at a time. The normal chart browser remains independent: archive completeness does
 * not depend on the user's selection or on opening a disclosure card.
 */
@Composable
internal fun DiagnosticFigureArchiveHost(
    report: Layer1DiagnosticReport,
    executionId: String,
    modifier: Modifier = Modifier
) {
    val preparedData by
        produceState<DiagnosticChartPreparedData?>(
            initialValue = null,
            key1 = executionId
        ) {
            value =
                withContext(Dispatchers.Default) {
                    prepareDiagnosticArchiveData(report)
                }
        }
    val batches = diagnosticFigureArchiveBatches
    var batchIndex by remember(executionId) { mutableIntStateOf(0) }
    val batch = batches.getOrNull(batchIndex) ?: return
    val currentPreparedData = preparedData ?: return

    LaunchedEffect(executionId, batch.stableId, currentPreparedData) {
        // Chart captures are globally serialized. Keep the family composed long enough for every
        // card in this bounded batch to reach GENERATED or FAILED in the immutable manifest.
        delay(DIAGNOSTIC_ARCHIVE_BATCH_RESIDENCE_MILLIS)
        batchIndex += 1
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // A non-zero draw alpha guarantees that Compose records every child graphics layer.
                // The real diagnostic screen is drawn above this host, so this remains imperceptible.
                .alpha(0.001f)
                .clearAndSetSemantics { }
    ) {
        ProvideAutomaticFigureLibraryContext(
            collection = "diagnostics",
            analysisId = report.experimentName,
            executionId = executionId
        ) {
            CompositionLocalProvider(LocalChartCardsCollapsible provides false) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (batch) {
                        is DiagnosticFigureArchiveBatch.AnalysisFamily ->
                            DiagnosticChartPageRouter(
                                selectedCategory = batch.category,
                                selectedViewMode = DiagnosticChartViewMode.ALL_CHARTS,
                                report = report,
                                preparedData = currentPreparedData
                            )

                        DiagnosticFigureArchiveBatch.UniqueTwoDimensionalMatrices ->
                            DiagnosticTwoDimensionalMatrixArchivePage(
                                report = report,
                                heatMapIndex = requireNotNull(currentPreparedData.heatMapIndex)
                            )
                    }
                }
            }
        }
    }
}

internal fun prepareDiagnosticArchiveData(
    report: Layer1DiagnosticReport
): DiagnosticChartPreparedData {
    val sorted = sortDiagnosticRuns(partitionDiagnosticRuns(report.runResults))
    val metricCounts = countDiagnosticMetricFiniteness(sorted.sequentialRuns)
    return DiagnosticChartPreparedData(
        sequentialRuns = sorted.sequentialRuns,
        oracleRuns = sorted.oracleRuns,
        displayRuns = sorted.sequentialRuns,
        finiteMetricCount = metricCounts.first,
        nonFiniteMetricCount = metricCounts.second,
        categoryGroupCount = 0,
        heatMapIndex = buildDiagnosticHeatMapIndex(sorted.sequentialRuns)
    )
}

private const val DIAGNOSTIC_ARCHIVE_BATCH_RESIDENCE_MILLIS = 12_000L
