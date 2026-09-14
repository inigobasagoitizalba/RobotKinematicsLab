package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint

/** Immutable evidence row used by the in-app cumulative feature-growth chart. */
internal data class FeatureGrowthObservation(
    val featureSelectionId: String,
    val featureCount: Int,
    val independentTestMacroF1: Double
)

/**
 * Keeps every chart point tied to its originating feature contract before sorting it for display.
 * Invalid or duplicate contracts are rejected instead of silently drawing a misleading curve.
 */
internal fun buildFeatureGrowthChartPoints(
    observations: List<FeatureGrowthObservation>
): List<ChartLinePoint> {
    require(observations.map(FeatureGrowthObservation::featureSelectionId).distinct().size == observations.size) {
        "Every feature-growth observation must belong to a unique feature selection."
    }
    observations.forEach { observation ->
        require(observation.featureCount > 0) { "Feature counts must be positive." }
        require(observation.independentTestMacroF1.isFinite()) { "Macro-F1 must be finite." }
        require(observation.independentTestMacroF1 in 0.0..1.0) { "Macro-F1 must be between zero and one." }
    }
    return observations
        .sortedWith(compareBy(FeatureGrowthObservation::featureCount, FeatureGrowthObservation::featureSelectionId))
        .map { observation ->
            ChartLinePoint(
                x = observation.featureCount.toDouble(),
                y = observation.independentTestMacroF1
            )
        }
}
