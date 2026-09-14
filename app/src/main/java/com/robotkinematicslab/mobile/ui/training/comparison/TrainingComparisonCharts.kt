package com.robotkinematicslab.mobile.ui.training.comparison

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonSession
import com.robotkinematicslab.mobile.ml.storage.StoredTrainingIteration
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartDoubleBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.HorizontalDoubleBarChart
import java.util.Locale

internal enum class HistoryMetric(
    val label: String,
    val axisLabel: String,
    val value: (StoredTrainingIteration) -> Double
) {
    LOSS("Training loss", "Cross-entropy loss", StoredTrainingIteration::trainingLoss),
    MACRO_F1("Macro-F1", "Validation macro-F1", StoredTrainingIteration::validationMacroF1),
    BALANCED_ACCURACY(
        "Balanced accuracy",
        "Validation balanced accuracy",
        StoredTrainingIteration::validationBalancedAccuracy
    ),
    LOG_LOSS("Validation log loss", "Validation log loss", StoredTrainingIteration::validationLogLoss)
}

internal data class ArchivedTrainingHistorySeries(
    val selectionId: String,
    val selectionLabel: String,
    val metric: HistoryMetric,
    val points: List<ChartLinePoint>
)

internal fun buildCompleteTrainingHistorySeries(
    history: List<StoredTrainingIteration>,
    selections: List<Pair<String, String>>
): List<ArchivedTrainingHistorySeries> =
    HistoryMetric.entries.flatMap { metric ->
        selections.map { (selectionId, selectionLabel) ->
            ArchivedTrainingHistorySeries(
                selectionId = selectionId,
                selectionLabel = selectionLabel,
                metric = metric,
                points =
                    history
                        .asSequence()
                        .filter { it.featureSelectionId == selectionId }
                        .sortedBy(StoredTrainingIteration::globalIteration)
                        .mapNotNull { iteration ->
                            val value = metric.value(iteration)
                            value.takeIf(Double::isFinite)?.let {
                                ChartLinePoint(iteration.globalIteration.toDouble(), it)
                            }
                        }
                        .toList()
            )
        }
    }

@Composable
fun TrainingComparisonCharts(session: ModelComparisonSession) {
    val aggregate = session.aggregate
    HorizontalDoubleBarChart(
        title = "Same held-out points · prediction accuracy",
        subtitle =
            "Both stored models were evaluated on the identical reconstructed test partition. " +
                "This visual sample contains ${aggregate.displayedPointCount} of ${aggregate.heldOutPointCount} comparable points.",
        items =
            listOf(
                ChartDoubleBarItem(
                    label = session.baselineLabel,
                    value = aggregate.baselineAccuracy,
                    displayValue = percent(aggregate.baselineAccuracy),
                    color = BaselineColor
                ),
                ChartDoubleBarItem(
                    label = session.contextLabel,
                    value = aggregate.contextAccuracy,
                    displayValue = percent(aggregate.contextAccuracy),
                    color = ContextColor
                ),
                ChartDoubleBarItem(
                    label = "Prediction disagreement",
                    value = aggregate.disagreementRate,
                    displayValue = percent(aggregate.disagreementRate),
                    color = DisagreementColor
                )
            )
    )

    HorizontalDoubleBarChart(
        title = "Who was correct?",
        subtitle = "Counts are mutually exclusive across the visualized held-out points.",
        items =
            listOf(
                ChartDoubleBarItem("${session.contextLabel} only", aggregate.contextOnlyCorrectCount.toDouble(), aggregate.contextOnlyCorrectCount.toString(), ContextColor),
                ChartDoubleBarItem("${session.baselineLabel} only", aggregate.baselineOnlyCorrectCount.toDouble(), aggregate.baselineOnlyCorrectCount.toString(), BaselineColor),
                ChartDoubleBarItem("Both correct", aggregate.bothCorrectCount.toDouble(), aggregate.bothCorrectCount.toString(), Color(0xFF1565C0)),
                ChartDoubleBarItem("Both wrong", aggregate.bothWrongCount.toDouble(), aggregate.bothWrongCount.toString(), Color(0xFFC62828))
            )
    )

    ChartSectionCard(
        title = "Complete training-history archive",
        subtitle = "All four stored measures are rendered and archived; no figure depends on opening a metric selector."
    ) {
        ChartMetricRow("Recorded epochs", session.history.size.toString())
        ChartMetricRow("Split", session.run.splitStrategy.displayName)
        ChartMetricRow("Training seed", session.run.randomSeed.toString())
        ChartMetricRow("Archived history metrics", HistoryMetric.entries.size.toString())
    }

    listOf(
        listOf(session.baselineSelectionId,session.baselineLabel,session.leftCandidateId),
        listOf(session.contextSelectionId,session.contextLabel,session.rightCandidateId)
    ).forEachIndexed { side, selection ->
        val selectionId=requireNotNull(selection[0]);val label=requireNotNull(selection[1]);val candidate=selection[2]
        val color=if(side==0) BaselineColor else ContextColor
        val selectedHistory=session.history.filter { it.featureSelectionId==selectionId && (candidate==null || it.candidateId==candidate) }
        buildCompleteTrainingHistorySeries(selectedHistory,listOf(selectionId to label)).forEach { series ->
            ProfessionalLineChart(
                title="${if(side==0) "Left" else "Right"} · ${series.selectionLabel} · ${series.metric.label}",
                subtitle="Saved epoch evidence for the selected model candidate; validation is separate from the paired test.",
                points=series.points,xAxisLabel="Global training iteration",yAxisLabel=series.metric.axisLabel,color=color)
        }
        val elapsed=selectedHistory.sortedBy(StoredTrainingIteration::globalIteration).map { ChartLinePoint(it.globalIteration.toDouble(),it.elapsedMillis/1000.0) }
        ProfessionalLineChart(title="${if(side==0) "Left" else "Right"} · $label · cumulative training time",
            subtitle="Wall-clock evidence for this candidate; device load and thermal state can affect this curve.",
            points=elapsed,xAxisLabel="Global training iteration",yAxisLabel="Elapsed seconds",color=color)
    }

    ScientificComparisonCharts(session)
}

internal val BaselineColor = Color(0xFF546E7A)
internal val ContextColor = Color(0xFF2E7D32)
internal val ExpandedContextColor = Color(0xFF6A1B9A)
private val DisagreementColor = Color(0xFFF57C00)

private fun percent(value: Double): String =
    if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100.0) else "N/A"
