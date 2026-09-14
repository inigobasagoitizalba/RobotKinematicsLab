package com.robotkinematicslab.mobile.ui.training.results

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import com.robotkinematicslab.mobile.ml.training.*
import com.robotkinematicslab.mobile.ui.charts.advanced.*
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.training.TrainingDisclosureSection
import kotlin.math.roundToInt

@Composable
fun ScientificClassificationFigures(name: String, metrics: ClassificationMetrics) {
    val classes=remember(metrics) { runCatching { ScientificResultEvidence.classes(metrics) } }
    if(classes.isFailure) { Text("Evidence unavailable: ${classes.exceptionOrNull()?.message}"); return }
    val labels=classes.getOrThrow().map { ScientificResultEvidence.className(it.label) }
    val cells=ScientificResultEvidence.confusion(metrics).map { value ->
        val ratio=value.rowFraction
        ChartHeatMapCell(ScientificResultEvidence.className(value.actual),ScientificResultEvidence.className(value.predicted),ratio ?: 0.0,
            if(ratio==null) "No actual cases · n=0" else "${ScientificMetricFormat.RATIO.format(ratio)} · ${value.count}/${value.denominator} actual ${ScientificResultEvidence.className(value.actual)}",
            if(ratio==null) Color.Gray else (if(value.actual==value.predicted) Color(0xFF2E7D32) else Color(0xFFC62828)).copy(alpha=(0.18+ratio*0.82).toFloat()))
    }
    Text("Accepted, Uncertain and Rejected are deterministic solver outcome labels (success, success with warning, and rejected/failure classes). Predictions are learned labels and do not certify a new physical solution.")
    ProfessionalHeatMapChart("$name · normalized confusion matrix","Rows are actual class; columns are predicted class. Percentages divide each cell count by all actual cases in its row; gray means no support.",labels,labels,cells,"Predicted class","Actual class")
    classes.getOrThrow().filter { it.support==0 }.forEach { Text("${ScientificResultEvidence.className(it.label)} recall unavailable: no actual cases.") }
    ProfessionalHorizontalBarChart(title="$name · class recall",subtitle="Correctly predicted cases / all actual cases of the class. Unsupported classes are omitted, not scored as zero. Neutral blue does not encode approval.",
        items=classes.getOrThrow().mapNotNull { value -> value.recall?.let { ChartBarItem("${ScientificResultEvidence.className(value.label)} · ${value.truePositive}/${value.support}",(it*10000).roundToInt(),Color(0xFF1565C0)) } },xAxisLabel="Recall (%)",metricFormat=ScientificMetricFormat.BASIS_POINTS)
    val calibration=runCatching { ScientificResultEvidence.calibration(metrics) }
    if(calibration.isFailure) Text("Calibration evidence unavailable: ${calibration.exceptionOrNull()?.message}")
    else if(calibration.getOrThrow().isEmpty()) Text("Calibration bins were not recorded for this result.")
    else ProfessionalLineChart(title="$name · reliability curve",subtitle="Top predicted-class confidence versus observed correctness; ${metrics.sampleCount} cases. ECE ${ScientificMetricFormat.RATIO.format(metrics.expectedCalibrationError)} weights each supported bin by its sample count. Dashed gray y=x is an ideal reference, not measured data. Empty bins are gaps.",points=calibration.getOrThrow(),xAxisLabel="Mean predicted confidence (0–1)",yAxisLabel="Observed accuracy (0–1)",color=Color(0xFF1565C0),referenceDiagonal=true)
}

@Composable
fun ScientificTestSlices(slices: List<ClassificationSliceMetrics>, identity: String) {
    var group by rememberSaveable(identity) { mutableStateOf("target-class") }
    TrainingDisclosureSection(title="Scientific test slices",subtitle="Change grouping without combining or averaging independently evaluated subsets.") {
        CompactSelectionMenu(listOf("target-class","target-source","robot","topology"),group,{ it.replace('-',' ').replaceFirstChar(Char::uppercase) },{ group=it })
        val selected=runCatching { ScientificResultEvidence.slices(slices,group) }
        if(selected.isFailure) Text("Slices unavailable: ${selected.exceptionOrNull()?.message}")
        else {
            if(selected.getOrThrow().isEmpty()) Text("No $group slices were recorded.")
            selected.getOrThrow().forEach { slice ->
                TrainingDisclosureSection(title="${ScientificResultEvidence.sliceName(slice)} · n=${slice.metrics.sampleCount} · F1 ${ScientificMetricFormat.RATIO.format(slice.metrics.macroF1)}",subtitle="This metric belongs only to the stored subset.") {
                    NamedClassSupport(slice.metrics)
                    Text("Technical subset: ${slice.id}; original label: ${slice.displayName}")
                    if(group=="robot") Text("The stored robot identifier is preserved. A separate human robot name/topology was not recorded in this slice; robots are never merged by topology.")
                    if(group=="topology") Text("R means revolute and P prismatic, in kinematic joint order.")
                    Text("Raw support: ${slice.metrics.classSupport}; raw macro-F1: ${slice.metrics.macroF1}")
                }
            }
        }
    }
}
@Composable
fun NamedClassSupport(metrics: ClassificationMetrics) {
    val classes=runCatching { ScientificResultEvidence.classes(metrics) }
    classes.onSuccess { values -> values.forEach { ChartMetricRow("Actual ${ScientificResultEvidence.className(it.label)}",if(it.support==0) "0 · no support" else it.support.toString()) } }
    classes.onFailure { Text("Class support unavailable: ${it.message}") }
}
