package com.robotkinematicslab.mobile.ui.training.comparison

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import com.robotkinematicslab.mobile.ml.comparison.ModelComparisonSession
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import java.util.Locale

@Composable internal fun ComparisonMetricChanges(session:ModelComparisonSession) {
    val left=session.leftTestMetrics ?: return
    val right=session.rightTestMetrics ?: return
    AppDisclosureSection("Metric changes · right minus left","Complete verified test: ${left.sampleCount} identical ordered cases",testTag="compare-metric-changes",initiallyExpanded=true) {
        Text("● Left: ${session.baselineLabel} ↔ ◆ Right: ${session.contextLabel}")
        fun format(value:Double)=if(value.isFinite()) String.format(Locale.US,"%+.4f",value) else "Unavailable"
        ChartMetricRow("Accuracy · higher is better",format(100*(right.accuracy-left.accuracy))+" percentage points")
        ChartMetricRow("Macro-F1 · higher is better",format(right.macroF1-left.macroF1)+" score (0–1)")
        ChartMetricRow("Log loss · lower is better",format(right.logLoss-left.logLoss)+" nats / case")
        ChartMetricRow("Multiclass Brier · lower is better",format(right.brierScore-left.brierScore)+" score")
        ChartMetricRow("ECE · lower is better",format(100*(right.expectedCalibrationError-left.expectedCalibrationError))+" percentage points")
        ChartMetricRow("Inference latency · lower is faster",format((right.inferenceNanosPerSample-left.inferenceNanosPerSample)/1000)+" µs / case")
        Text("These differences can trade predictive quality against calibration and speed; they do not establish a universal winner. Latency was remeasured sequentially on this device and is sensitive to runtime load. Charts below use the disclosed visual subset.")
    }
}
