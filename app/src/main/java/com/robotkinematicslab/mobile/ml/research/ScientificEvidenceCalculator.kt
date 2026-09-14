package com.robotkinematicslab.mobile.ml.research

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

object ScientificEvidenceCalculator {

    fun selectiveRiskCurve(
        observations: List<ScientificPredictionObservation>,
        maximumPoints: Int = 40
    ): SelectiveRiskCurve {
        require(observations.isNotEmpty()) { "Risk/coverage requires held-out predictions." }
        require(maximumPoints >= 2) { "At least two risk/coverage points are required." }
        val modelIds = observations.map(ScientificPredictionObservation::modelId).distinct()
        require(modelIds.size == 1) { "A risk/coverage curve describes exactly one model." }
        require(observations.map(ScientificPredictionObservation::sampleId).distinct().size == observations.size) {
            "Held-out sample identifiers must be unique within one model."
        }

        val ordered = observations.sortedWith(
            compareByDescending<ScientificPredictionObservation> { it.confidence }
                .thenBy(ScientificPredictionObservation::sampleId)
        )
        val acceptedCounts = sampledPrefixCounts(ordered.size, maximumPoints)
        var wrongSoFar = 0
        var cursor = 0
        val points = acceptedCounts.map { acceptedCount ->
            while (cursor < acceptedCount) {
                if (!ordered[cursor].correct) wrongSoFar++
                cursor++
            }
            SelectiveRiskPoint(
                acceptedCount = acceptedCount,
                coverage = acceptedCount.toDouble() / ordered.size,
                risk = wrongSoFar.toDouble() / acceptedCount,
                confidenceThreshold = ordered[acceptedCount - 1].confidence
            )
        }
        return SelectiveRiskCurve(
            modelId = modelIds.single(),
            totalCount = ordered.size,
            points = points,
            areaUnderRiskCoverage = trapezoidArea(points)
        )
    }

    fun generalizationByRobot(
        observations: List<ScientificPredictionObservation>
    ): List<GeneralizationSlice> = groupedGeneralization(observations) { it.robotId }

    fun generalizationByTopology(
        observations: List<ScientificPredictionObservation>
    ): List<GeneralizationSlice> = groupedGeneralization(observations) { it.topologyKey }

    fun calibrationByRobot(
        observations: List<ScientificPredictionObservation>
    ): List<CalibrationSlice> = groupedCalibration(observations) { it.robotId }

    fun calibrationByTopology(
        observations: List<ScientificPredictionObservation>
    ): List<CalibrationSlice> = groupedCalibration(observations) { it.topologyKey }

    fun tailRisk(observations: List<ScientificPredictionObservation>): TailRiskSummary {
        require(observations.isNotEmpty()) { "Tail-risk analysis requires held-out predictions." }
        val modelIds = observations.map(ScientificPredictionObservation::modelId).distinct()
        require(modelIds.size == 1) { "Tail-risk analysis describes exactly one model." }
        require(observations.map(ScientificPredictionObservation::sampleId).distinct().size == observations.size) {
            "Tail-risk sample identifiers must be unique within one model."
        }
        val losses = observations.map { 1.0 - it.probabilityAssignedToTruth }.sorted()
        return TailRiskSummary(
            modelId = modelIds.single(),
            sampleCount = losses.size,
            medianLoss = quantile(losses, 0.50),
            p95Loss = quantile(losses, 0.95),
            p99Loss = quantile(losses, 0.99),
            maximumLoss = losses.last(),
            cvar95Loss = conditionalTailMean(losses, 0.95),
            cvar99Loss = conditionalTailMean(losses, 0.99)
        )
    }

    fun pipelineErrorBudget(observations: List<PipelineErrorObservation>): PipelineErrorBudget {
        require(observations.isNotEmpty()) { "An error budget requires pipeline observations." }
        require(observations.map(PipelineErrorObservation::sampleId).distinct().size == observations.size) {
            "Pipeline sample identifiers must be unique."
        }
        fun mean(value: (PipelineErrorObservation) -> Double) = observations.map(value).average()
        val initial = mean(PipelineErrorObservation::initialErrorMeters)
        val afterValidation = mean(PipelineErrorObservation::afterValidationErrorMeters)
        val afterRepair = mean(PipelineErrorObservation::afterRepairErrorMeters)
        val afterSolver = mean(PipelineErrorObservation::afterSolverErrorMeters)
        val final = mean(PipelineErrorObservation::finalErrorMeters)
        return PipelineErrorBudget(
            sampleCount = observations.size,
            meanInitialErrorMeters = initial,
            validationDeltaMeters = initial - afterValidation,
            repairDeltaMeters = afterValidation - afterRepair,
            solverDeltaMeters = afterRepair - afterSolver,
            finalizationDeltaMeters = afterSolver - final,
            meanFinalErrorMeters = final,
            totalErrorReductionFraction = if (initial > 0.0) (initial - final) / initial else 0.0,
            repairUseRate = observations.count(PipelineErrorObservation::repairApplied).toDouble() / observations.size,
            fallbackUseRate = observations.count(PipelineErrorObservation::fallbackApplied).toDouble() / observations.size
        )
    }

    fun robustness(observations: List<PerturbationObservation>): List<RobustnessSlice> {
        require(observations.isNotEmpty()) { "Robustness analysis requires paired perturbations." }
        require(observations.map { it.sampleId to it.magnitude }.distinct().size == observations.size) {
            "Each sample/magnitude perturbation pair must be unique."
        }
        return observations.groupBy(PerturbationObservation::magnitude)
            .toSortedMap()
            .map { (magnitude, group) ->
                val baseAccuracy = group.count(PerturbationObservation::baselineCorrect).toDouble() / group.size
                val perturbedAccuracy = group.count(PerturbationObservation::perturbedCorrect).toDouble() / group.size
                RobustnessSlice(
                    magnitude = magnitude,
                    sampleCount = group.size,
                    meanLossIncrease = group.map { it.perturbedLoss - it.baselineLoss }.average(),
                    accuracyDrop = baseAccuracy - perturbedAccuracy,
                    failureIntroductionRate =
                        group.count { it.baselineCorrect && !it.perturbedCorrect }.toDouble() / group.size
                )
            }
    }

    fun trainingScaleEvidence(observations: List<TrainingScaleObservation>): TrainingScaleEvidence {
        require(observations.isNotEmpty())
        require(observations.map(TrainingScaleObservation::label).distinct().size == observations.size) {
            "Scale-observation labels must be unique."
        }
        val ordered = observations.sortedWith(compareBy(TrainingScaleObservation::rowCount, TrainingScaleObservation::label))
        val pareto = ordered.filter { candidate ->
            ordered.none { other ->
                other !== candidate &&
                    other.score >= candidate.score &&
                    other.trainingMillis <= candidate.trainingMillis &&
                    other.peakMemoryBytes <= candidate.peakMemoryBytes &&
                    (other.score > candidate.score ||
                        other.trainingMillis < candidate.trainingMillis ||
                        other.peakMemoryBytes < candidate.peakMemoryBytes)
            }
        }.map(TrainingScaleObservation::label).toSet()
        val gains = ordered.zipWithNext { left, right ->
            val additionalThousands = (right.rowCount - left.rowCount) / 1_000.0
            if (additionalThousands > 0.0) (right.score - left.score) / additionalThousands else Double.NaN
        }
        return TrainingScaleEvidence(ordered, pareto, gains)
    }

    private fun groupedGeneralization(
        observations: List<ScientificPredictionObservation>,
        slice: (ScientificPredictionObservation) -> String
    ): List<GeneralizationSlice> {
        require(observations.isNotEmpty())
        require(observations.map { it.modelId to it.sampleId }.distinct().size == observations.size) {
            "Generalization evidence must contain each model/sample pair exactly once."
        }
        return observations.groupBy { it.modelId to slice(it) }
            .toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .map { (key, group) ->
                val accuracy = group.count(ScientificPredictionObservation::correct).toDouble() / group.size
                val meanConfidence = group.map(ScientificPredictionObservation::confidence).average()
                val interval = wilsonInterval(group.count(ScientificPredictionObservation::correct), group.size)
                GeneralizationSlice(
                    modelId = key.first,
                    sliceId = key.second,
                    sampleCount = group.size,
                    accuracy = accuracy,
                    meanConfidence = meanConfidence,
                    meanTruthProbability = group.map(ScientificPredictionObservation::probabilityAssignedToTruth).average(),
                    calibrationGap = abs(meanConfidence - accuracy),
                    wilsonLower95 = interval.first,
                    wilsonUpper95 = interval.second
                )
            }
    }

    private fun groupedCalibration(
        observations: List<ScientificPredictionObservation>,
        slice: (ScientificPredictionObservation) -> String
    ): List<CalibrationSlice> = groupedGeneralization(observations, slice).map { group ->
        CalibrationSlice(
            modelId = group.modelId,
            sliceId = group.sliceId,
            sampleCount = group.sampleCount,
            meanConfidence = group.meanConfidence,
            empiricalAccuracy = group.accuracy,
            absoluteGap = group.calibrationGap
        )
    }

    private fun sampledPrefixCounts(total: Int, maximumPoints: Int): List<Int> {
        if (total <= maximumPoints) return (1..total).toList()
        return (0 until maximumPoints)
            .map { index -> 1 + ((total - 1L) * index / (maximumPoints - 1L)).toInt() }
            .distinct()
    }

    private fun trapezoidArea(points: List<SelectiveRiskPoint>): Double {
        if (points.size < 2) return points.firstOrNull()?.risk ?: 0.0
        val fromZero = points.first().coverage * points.first().risk
        return fromZero + points.zipWithNext().sumOf { (left, right) ->
            (right.coverage - left.coverage) * (left.risk + right.risk) * 0.5
        }
    }

    private fun quantile(sorted: List<Double>, probability: Double): Double {
        require(sorted.isNotEmpty())
        val position = probability.coerceIn(0.0, 1.0) * (sorted.lastIndex)
        val lower = position.toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction
    }

    private fun conditionalTailMean(sorted: List<Double>, probability: Double): Double {
        val threshold = quantile(sorted, probability)
        return sorted.filter { it >= threshold }.average()
    }

    private fun wilsonInterval(successes: Int, total: Int): Pair<Double, Double> {
        if (total <= 0) return Double.NaN to Double.NaN
        val z = 1.959963984540054
        val n = total.toDouble()
        val p = successes / n
        val denominator = 1.0 + z * z / n
        val centre = (p + z * z / (2.0 * n)) / denominator
        val margin = z * sqrt((p * (1.0 - p) / n) + (z * z / (4.0 * n * n))) / denominator
        return max(0.0, centre - margin) to (centre + margin).coerceAtMost(1.0)
    }
}
