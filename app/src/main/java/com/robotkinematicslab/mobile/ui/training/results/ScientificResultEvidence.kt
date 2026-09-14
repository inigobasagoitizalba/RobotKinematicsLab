package com.robotkinematicslab.mobile.ui.training.results

import com.robotkinematicslab.mobile.ml.data.TrainingLabel
import com.robotkinematicslab.mobile.ml.training.*
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import kotlin.math.abs

/** Class order comes from the evaluator's TrainingLabel ordinal contract, never a UI sort. */
data class ClassEvidence(val label: TrainingLabel, val support: Int, val truePositive: Int) {
    val recall: Double? get() = if(support == 0) null else truePositive.toDouble()/support
}
data class ConfusionEvidence(val actual: TrainingLabel, val predicted: TrainingLabel, val count: Int, val denominator: Int) {
    val rowFraction: Double? get() = if(denominator == 0) null else count.toDouble()/denominator
}
object ScientificResultEvidence {
    fun classes(metrics: ClassificationMetrics): List<ClassEvidence> {
        val matrix = metrics.confusionMatrix
        require(matrix.size == TrainingLabel.entries.size && matrix.all { it.size == TrainingLabel.entries.size && it.all { value -> value >= 0 } }) { "Confusion matrix does not match the recorded class contract." }
        val support = matrix.map { row -> row.sumOf(Int::toLong).also { require(it <= Int.MAX_VALUE) }.toInt() }
        require(support.sumOf(Int::toLong) == metrics.sampleCount.toLong()) { "Confusion counts disagree with sample support." }
        require(metrics.classSupport.isEmpty() || metrics.classSupport == support) { "Class support disagrees with confusion rows." }
        return TrainingLabel.entries.mapIndexed { index,label -> ClassEvidence(label,support[index],matrix[index][index]) }
    }
    fun confusion(metrics: ClassificationMetrics): List<ConfusionEvidence> {
        val classes = classes(metrics)
        return classes.flatMapIndexed { index,actual -> classes.mapIndexed { prediction,predicted -> ConfusionEvidence(actual.label,predicted.label,metrics.confusionMatrix[index][prediction],actual.support) } }
    }
    fun calibration(metrics: ClassificationMetrics): List<ChartLinePoint> {
        require(metrics.sampleCount >= 0)
        val bins=metrics.calibrationBins
        require(bins.size<=1000)
        var upper = 0.0
        val points = bins.mapIndexedNotNull { index,bin ->
            require(bin.lowerConfidence.isFinite() && bin.upperConfidence.isFinite() && bin.lowerConfidence >= 0 && bin.upperConfidence <= 1 && bin.lowerConfidence < bin.upperConfidence && (index==0 || bin.lowerConfidence >= upper - 1e-12) && bin.sampleCount>=0) { "Invalid or overlapping calibration bins." }
            val gap = index>0 && bin.lowerConfidence > upper + 1e-12
            upper=bin.upperConfidence
            if(bin.sampleCount==0) return@mapIndexedNotNull null
            require(bin.meanConfidence in bin.lowerConfidence..bin.upperConfidence && bin.empiricalAccuracy in 0.0..1.0) { "Invalid calibration observation." }
            ChartLinePoint(bin.meanConfidence,bin.empiricalAccuracy,breakBefore=gap || (index>0 && bins[index-1].sampleCount==0),label="Bin [${bin.lowerConfidence}, ${bin.upperConfidence}] · n=${bin.sampleCount}; mean confidence=${bin.meanConfidence}; observed accuracy=${bin.empiricalAccuracy}")
        }
        require(bins.sumOf { it.sampleCount.toLong() } == metrics.sampleCount.toLong() || bins.isEmpty()) { "Calibration support differs from the evaluated partition." }
        if(bins.isNotEmpty() && metrics.sampleCount>0 && metrics.expectedCalibrationError.isFinite()) {
            val ece=bins.filter { it.sampleCount>0 }.sumOf { it.sampleCount.toDouble()/metrics.sampleCount * abs(it.empiricalAccuracy-it.meanConfidence) }
            require(abs(ece-metrics.expectedCalibrationError)<1e-9) { "Recorded ECE disagrees with weighted calibration bins." }
        }
        return points
    }
    fun slices(slices: List<ClassificationSliceMetrics>, group: String): List<ClassificationSliceMetrics> {
        require(group in listOf("target-class","target-source","robot","topology"))
        require(slices.map { it.id }.distinct().size == slices.size) { "Duplicate slice identity." }
        return slices.filter { it.id.substringBefore(':')==group }.sortedBy { it.id }
    }
    fun iterations(iterations: List<TrainingIterationMetrics>): List<ChartLinePoint> = iterations.mapIndexed { index,value ->
        require(value.globalIteration>=0 && value.epoch>=0 && value.validationMetrics.macroF1 in 0.0..1.0)
        ChartLinePoint(value.globalIteration.toDouble(),value.validationMetrics.macroF1,
            breakBefore=index>0 && iterations[index-1].candidateId!=value.candidateId,
            label="${value.featureSelectionName}; candidate ${value.candidateId}; epoch ${value.epoch}; recorded iteration ${value.globalIteration}; elapsed ${value.elapsedMillis} ms")
    }
    fun sliceName(slice: ClassificationSliceMetrics): String {
        val raw=slice.id.substringAfter(':')
        if(slice.id.startsWith("robot:")) return "Robot $raw"
        if(slice.id.startsWith("topology:")) {
            val joints=raw.split('-')
            val short=joints.map { when(it.uppercase()) { "REVOLUTE","R" -> "R"; "PRISMATIC","P" -> "P"; else -> "?" } }
            if(short.none { it=="?" }) return "${short.joinToString("–")} · ${short.size} joints"
        }
        return slice.displayName
    }
    fun warningExplanation(raw:String):String {
        if("predicts one outcome" !in raw) return raw
        return "Observed concentration of labels: $raw This describes the recorded subset, not a proven model prediction. It may follow the target generator (for example guaranteed unreachable targets); it can also provide a learning shortcut. Check performance on other sources before drawing a general conclusion."
    }
    fun className(label: TrainingLabel)=label.name.lowercase().replaceFirstChar(Char::uppercase)
}
