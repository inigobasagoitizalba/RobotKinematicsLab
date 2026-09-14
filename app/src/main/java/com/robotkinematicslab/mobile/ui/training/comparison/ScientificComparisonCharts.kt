package com.robotkinematicslab.mobile.ui.training.comparison

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonPoint
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonSession
import com.robotkinematicslab.mobile.ml.comparison.ModelPointPrediction
import com.robotkinematicslab.mobile.ml.research.ScientificEvidenceCalculator
import com.robotkinematicslab.mobile.ml.research.ScientificPredictionObservation
import com.robotkinematicslab.mobile.ml.research.ConformalClassificationCalculator
import com.robotkinematicslab.mobile.ml.research.ConformalClassificationEvidence
import com.robotkinematicslab.mobile.ml.research.ConformalClassificationObservation
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ScatterLegendItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import java.util.Locale

@Composable
internal fun ScientificComparisonCharts(session: ModelComparisonSession) {
    val evidence = remember(session) { ComparisonScientificEvidence.from(session) }
    if (evidence == null) {
        ChartSectionCard(
            title = "Scientific reliability evidence",
            subtitle = "These diagrams require valid held-out predictions from both models."
        ) {
            JargonAwareText("No comparable held-out evidence is available.")
        }
        return
    }

    ChartSectionCard(
        title = "Scientific reliability dashboard",
        subtitle =
            "Every value below is recomputed from the exact held-out rows loaded above. " +
                "No training or validation row is substituted."
    ) {
        ChartMetricRow("Held-out rows visualized", evidence.rowCount.toString())
        ChartMetricRow("Robots represented", evidence.robotLabels.size.toString())
        ChartMetricRow("Topologies represented", evidence.topologyLabels.size.toString())
        ChartMetricRow("${session.baselineLabel} AURC", decimal(evidence.leftRiskArea))
        ChartMetricRow("${session.contextLabel} AURC", decimal(evidence.rightRiskArea))
        JargonAwareText("AURC is the area under the risk/coverage curve; lower is better.")
    }

    ProfessionalLineChart(
        title = "${session.baselineLabel} · risk versus retained coverage",
        subtitle = "High-confidence predictions are retained first. The vertical value is the error rate among retained predictions.",
        points = evidence.leftRiskPoints,
        xAxisLabel = "Coverage fraction",
        yAxisLabel = "Selective risk",
        color = BaselineColor,
        directionOverride = ChartReadingDirection.TRADE_OFF
    )
    ProfessionalLineChart(
        title = "${session.contextLabel} · risk versus retained coverage",
        subtitle = "A useful safety model keeps risk low while accepting a large fraction of cases.",
        points = evidence.rightRiskPoints,
        xAxisLabel = "Coverage fraction",
        yAxisLabel = "Selective risk",
        color = ContextColor,
        directionOverride = ChartReadingDirection.TRADE_OFF
    )

    ProfessionalHeatMapChart(
        title = "Held-out accuracy by robot",
        subtitle = "Rows with little support remain visible in the inspector with their exact sample count and Wilson interval in the summary below.",
        rowLabels = evidence.robotLabels,
        columnLabels = evidence.modelLabels,
        cells = evidence.robotAccuracyCells,
        xAxisLabel = "Model",
        yAxisLabel = "Robot"
    )

    ProfessionalHeatMapChart(
        title = "Calibration gap by robot",
        subtitle = "Absolute difference between mean confidence and observed accuracy. Lower and cooler cells are better calibrated.",
        rowLabels = evidence.robotLabels,
        columnLabels = evidence.modelLabels,
        cells = evidence.robotCalibrationCells,
        xAxisLabel = "Model",
        yAxisLabel = "Robot",
        directionOverride = ChartReadingDirection.LOWER_TENDS_BETTER
    )

    ProfessionalHeatMapChart(
        title = "Held-out accuracy by topology",
        subtitle = "This reveals whether gains are broad or concentrated in one kinematic family.",
        rowLabels = evidence.topologyLabels,
        columnLabels = evidence.modelLabels,
        cells = evidence.topologyAccuracyCells,
        xAxisLabel = "Model",
        yAxisLabel = "Joint topology"
    )

    ProfessionalScatterChart(
        title = "Confidence calibration by robot",
        subtitle = "A perfectly calibrated slice lies on the confidence = accuracy diagonal. Tap any point for the robot and exact values.",
        points = evidence.calibrationPoints,
        xAxisLabel = "Mean confidence",
        yAxisLabel = "Observed accuracy",
        legendItems =
            listOf(
                ScatterLegendItem(session.baselineLabel, "Robot slices", BaselineColor),
                ScatterLegendItem(session.contextLabel, "Robot slices", ContextColor)
            ),
        directionOverride = ChartReadingDirection.TARGET_DEPENDENT
    )

    ProfessionalHorizontalBarChart(
        title = "Worst-case probability loss",
        subtitle = "Loss is 1 minus the probability assigned to the true class. P95, P99 and CVaR expose failures hidden by the mean.",
        items = evidence.tailRiskBars,
        xAxisLabel = "Probability loss × 10,000"
    )

    if (evidence.leftConformal != null && evidence.rightConformal != null) {
        ChartSectionCard(
            title = "Split-conformal prediction sets · 90% target",
            subtitle =
                "Half of the displayed held-out rows calibrate the threshold; the other half evaluates it. " +
                    "Those two subsets are disjoint."
        ) {
            ChartMetricRow(
                "${session.baselineLabel} coverage / set size",
                "${percent(evidence.leftConformal.empiricalCoverage)} / ${decimal(evidence.leftConformal.averageSetSize)}"
            )
            ChartMetricRow(
                "${session.contextLabel} coverage / set size",
                "${percent(evidence.rightConformal.empiricalCoverage)} / ${decimal(evidence.rightConformal.averageSetSize)}"
            )
            ChartMetricRow("Target marginal coverage", "90.00%")
            JargonAwareText("Coverage below target signals under-coverage; very large sets signal weak usefulness despite coverage.")
        }
        ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
            title = "Conformal efficiency",
            subtitle = "Singleton sets are decisive; empty sets are a warning. Exact empirical coverage remains above.",
            items =
                listOf(
                    ChartBarItem("${session.baselineLabel} singleton · ${percent(evidence.leftConformal.singletonRate)}", (evidence.leftConformal.singletonRate * 10_000).toInt(), BaselineColor),
                    ChartBarItem("${session.contextLabel} singleton · ${percent(evidence.rightConformal.singletonRate)}", (evidence.rightConformal.singletonRate * 10_000).toInt(), ContextColor),
                    ChartBarItem("${session.baselineLabel} empty · ${percent(evidence.leftConformal.emptySetRate)}", (evidence.leftConformal.emptySetRate * 10_000).toInt(), Color(0xFFC62828)),
                    ChartBarItem("${session.contextLabel} empty · ${percent(evidence.rightConformal.emptySetRate)}", (evidence.rightConformal.emptySetRate * 10_000).toInt(), Color(0xFFEF6C00))
                ),
            xAxisLabel = "Rate (%)",
            directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
        )
    }

    ProfessionalScatterChart(
        title = "Confidence versus truth loss",
        subtitle = "Confident mistakes appear in the upper-right region and deserve manual inspection.",
        points = evidence.confidenceLossPoints,
        xAxisLabel = "Prediction confidence",
        yAxisLabel = "1 − probability assigned to truth",
        legendItems =
            listOf(
                ScatterLegendItem(session.baselineLabel, "Held-out predictions", BaselineColor),
                ScatterLegendItem(session.contextLabel, "Held-out predictions", ContextColor)
            ),
        directionOverride = ChartReadingDirection.PATTERN_NOT_RANK
    )
}

private data class ComparisonScientificEvidence(
    val rowCount: Int,
    val modelLabels: List<String>,
    val robotLabels: List<String>,
    val topologyLabels: List<String>,
    val leftRiskArea: Double,
    val rightRiskArea: Double,
    val leftRiskPoints: List<ChartLinePoint>,
    val rightRiskPoints: List<ChartLinePoint>,
    val robotAccuracyCells: List<ChartHeatMapCell>,
    val robotCalibrationCells: List<ChartHeatMapCell>,
    val topologyAccuracyCells: List<ChartHeatMapCell>,
    val calibrationPoints: List<ChartPoint>,
    val tailRiskBars: List<ChartBarItem>,
    val confidenceLossPoints: List<ChartPoint>,
    val leftConformal: ConformalClassificationEvidence?,
    val rightConformal: ConformalClassificationEvidence?
) {
    companion object {
        fun from(session: ModelComparisonSession): ComparisonScientificEvidence? = runCatching {
            if (session.points.isEmpty()) return null
            val left = session.points.toObservations(LEFT_MODEL_ID, ModelComparisonPoint::baselinePrediction)
            val right = session.points.toObservations(RIGHT_MODEL_ID, ModelComparisonPoint::contextPrediction)
            val leftRisk = ScientificEvidenceCalculator.selectiveRiskCurve(left)
            val rightRisk = ScientificEvidenceCalculator.selectiveRiskCurve(right)
            val byRobot = ScientificEvidenceCalculator.generalizationByRobot(left + right)
            val byTopology = ScientificEvidenceCalculator.generalizationByTopology(left + right)
            val calibration = ScientificEvidenceCalculator.calibrationByRobot(left + right)
            val leftTail = ScientificEvidenceCalculator.tailRisk(left)
            val rightTail = ScientificEvidenceCalculator.tailRisk(right)
            val leftDisplay = if (session.baselineLabel == session.contextLabel) "${session.baselineLabel} · left" else session.baselineLabel
            val rightDisplay = if (session.baselineLabel == session.contextLabel) "${session.contextLabel} · right" else session.contextLabel
            val modelLabels = listOf(leftDisplay, rightDisplay)
            val robots = byRobot.map { it.sliceId }.distinct().sorted()
            val topologies = byTopology.map { it.sliceId }.distinct().sorted()
            ComparisonScientificEvidence(
                rowCount = session.points.size,
                modelLabels = modelLabels,
                robotLabels = robots,
                topologyLabels = topologies,
                leftRiskArea = leftRisk.areaUnderRiskCoverage,
                rightRiskArea = rightRisk.areaUnderRiskCoverage,
                leftRiskPoints = leftRisk.points.map { ChartLinePoint(it.coverage, it.risk) },
                rightRiskPoints = rightRisk.points.map { ChartLinePoint(it.coverage, it.risk) },
                robotAccuracyCells = byRobot.map { slice ->
                    ChartHeatMapCell(
                        row = slice.sliceId,
                        column = displayModelLabel(slice.modelId, leftDisplay, rightDisplay),
                        value = slice.accuracy,
                        displayValue = "${percent(slice.accuracy)} · n=${slice.sampleCount} · 95% CI ${percent(slice.wilsonLower95)}–${percent(slice.wilsonUpper95)}",
                        color = accuracyColor(slice.accuracy)
                    )
                },
                robotCalibrationCells = calibration.map { slice ->
                    ChartHeatMapCell(
                        row = slice.sliceId,
                        column = displayModelLabel(slice.modelId, leftDisplay, rightDisplay),
                        value = slice.absoluteGap,
                        displayValue = "gap ${percent(slice.absoluteGap)} · confidence ${percent(slice.meanConfidence)} · accuracy ${percent(slice.empiricalAccuracy)} · n=${slice.sampleCount}",
                        color = calibrationColor(slice.absoluteGap)
                    )
                },
                topologyAccuracyCells = byTopology.map { slice ->
                    ChartHeatMapCell(
                        row = slice.sliceId,
                        column = displayModelLabel(slice.modelId, leftDisplay, rightDisplay),
                        value = slice.accuracy,
                        displayValue = "${percent(slice.accuracy)} · n=${slice.sampleCount}",
                        color = accuracyColor(slice.accuracy)
                    )
                },
                calibrationPoints = calibration.map { slice ->
                    ChartPoint(
                        x = slice.meanConfidence,
                        y = slice.empiricalAccuracy,
                        color = if (slice.modelId == LEFT_MODEL_ID) BaselineColor else ContextColor,
                        label = "${displayModelLabel(slice.modelId, leftDisplay, rightDisplay)} · ${slice.sliceId} · gap ${percent(slice.absoluteGap)} · n=${slice.sampleCount}"
                    )
                },
                tailRiskBars = tailBars(session.baselineLabel, leftTail.p95Loss, leftTail.p99Loss, leftTail.cvar95Loss, leftTail.cvar99Loss, BaselineColor) +
                    tailBars(session.contextLabel, rightTail.p95Loss, rightTail.p99Loss, rightTail.cvar95Loss, rightTail.cvar99Loss, ContextColor),
                confidenceLossPoints =
                    left.map { observation ->
                        ChartPoint(
                            observation.confidence,
                            1.0 - observation.probabilityAssignedToTruth,
                            BaselineColor,
                            "${session.baselineLabel} · ${observation.robotId} · row ${observation.sampleId}"
                        )
                    } + right.map { observation ->
                        ChartPoint(
                            observation.confidence,
                            1.0 - observation.probabilityAssignedToTruth,
                            ContextColor,
                            "${session.contextLabel} · ${observation.robotId} · row ${observation.sampleId}"
                        )
                    },
                leftConformal = conformal(session.points, ModelComparisonPoint::baselinePrediction),
                rightConformal = conformal(session.points, ModelComparisonPoint::contextPrediction)
            )
        }.getOrNull()
    }
}

private fun conformal(
    points: List<ModelComparisonPoint>,
    prediction: (ModelComparisonPoint) -> ModelPointPrediction
): ConformalClassificationEvidence? {
    if (points.size < 4) return null
    val observations = points.mapIndexed { index, point ->
        ConformalClassificationObservation(
            sampleId = index.toLong(),
            truthIndex = point.oracleLabel.ordinal,
            probabilities = prediction(point).probabilities.toDoubleArray()
        )
    }
    val calibration = observations.filterIndexed { index, _ -> index % 2 == 0 }
    val evaluation = observations.filterIndexed { index, _ -> index % 2 == 1 }
    return runCatching {
        ConformalClassificationCalculator.calculate(calibration, evaluation, alpha = 0.10)
    }.getOrNull()
}

private fun List<ModelComparisonPoint>.toObservations(
    modelId: String,
    prediction: (ModelComparisonPoint) -> ModelPointPrediction
): List<ScientificPredictionObservation> = map { point ->
    val result = prediction(point)
    ScientificPredictionObservation(
        sampleId = point.sourceRowIndex,
        modelId = modelId,
        robotId = point.robotId.ifBlank { point.robot.name.ifBlank { "Unknown robot" } },
        topologyKey = point.robot.joints.joinToString("-") { it.type.name }.ifBlank { "Unknown topology" },
        confidence = result.confidence.coerceIn(0.0, 1.0),
        probabilityAssignedToTruth = result.probabilityAssignedToTruth.coerceIn(0.0, 1.0),
        correct = result.correct
    )
}

private fun displayModelLabel(modelId: String, left: String, right: String): String =
    if (modelId == LEFT_MODEL_ID) left else right

private fun tailBars(
    model: String,
    p95: Double,
    p99: Double,
    cvar95: Double,
    cvar99: Double,
    color: Color
): List<ChartBarItem> =
    listOf(
        "$model · P95" to p95,
        "$model · P99" to p99,
        "$model · CVaR95" to cvar95,
        "$model · CVaR99" to cvar99
    ).map { (label, value) -> ChartBarItem("$label · ${decimal(value)}", (value * 10_000.0).toInt(), color) }

private fun accuracyColor(value: Double): Color =
    when {
        value >= 0.90 -> Color(0xFF2E7D32)
        value >= 0.75 -> Color(0xFF7CB342)
        value >= 0.60 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

private fun calibrationColor(value: Double): Color =
    when {
        value <= 0.02 -> Color(0xFF2E7D32)
        value <= 0.05 -> Color(0xFF7CB342)
        value <= 0.10 -> Color(0xFFF9A825)
        else -> Color(0xFFC62828)
    }

private fun decimal(value: Double): String = String.format(Locale.US, "%.5f", value)
private fun percent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)

private const val LEFT_MODEL_ID = "scientific-left-model"
private const val RIGHT_MODEL_ID = "scientific-right-model"
