package com.robotkinematicslab.mobile.ml.research

import kotlin.math.ceil
import kotlin.math.sqrt

/** Deterministic, bounded coverage/OOD analysis using training-only normalization. */
object DatasetCoverageAnalyzer {

    fun analyze(
        reference: List<CoverageObservation>,
        evaluation: List<CoverageObservation>,
        outOfDistributionDistance: Double? = null,
        maximumReferencePoints: Int = 2_000
    ): DatasetCoverageSummary {
        require(reference.isNotEmpty()) { "Coverage analysis requires reference rows." }
        require(evaluation.isNotEmpty()) { "Coverage analysis requires evaluation rows." }
        require(outOfDistributionDistance == null || (outOfDistributionDistance.isFinite() && outOfDistributionDistance > 0.0))
        require(maximumReferencePoints > 0)
        val featureCount = reference.first().features.size
        require(featureCount > 0) { "Coverage observations require at least one feature." }
        require(reference.all { it.features.size == featureCount })
        require(evaluation.all { it.features.size == featureCount })
        require(reference.map(CoverageObservation::sampleId).distinct().size == reference.size) {
            "Coverage reference sample identifiers must be unique."
        }
        require(evaluation.map(CoverageObservation::sampleId).distinct().size == evaluation.size) {
            "Coverage evaluation sample identifiers must be unique."
        }
        require((reference.map(CoverageObservation::sampleId).toSet() intersect evaluation.map(CoverageObservation::sampleId).toSet()).isEmpty()) {
            "Coverage reference and evaluation rows must be disjoint."
        }
        require((reference + evaluation).all { row -> row.features.all(Double::isFinite) }) {
            "Coverage observations must be finite."
        }

        val means = DoubleArray(featureCount)
        reference.forEach { row ->
            repeat(featureCount) { index -> means[index] += row.features[index] }
        }
        repeat(featureCount) { means[it] /= reference.size }

        val deviations = DoubleArray(featureCount)
        val minimums = DoubleArray(featureCount) { Double.POSITIVE_INFINITY }
        val maximums = DoubleArray(featureCount) { Double.NEGATIVE_INFINITY }
        reference.forEach { row ->
            repeat(featureCount) { index ->
                val value = row.features[index]
                val delta = value - means[index]
                deviations[index] += delta * delta
                if (value < minimums[index]) minimums[index] = value
                if (value > maximums[index]) maximums[index] = value
            }
        }
        repeat(featureCount) { index ->
            deviations[index] = sqrt(deviations[index] / reference.size).takeIf { it >= 1e-12 } ?: 1.0
        }
        val constantFeatures = (0 until featureCount).count { maximums[it] - minimums[it] < 1e-12 }

        val boundedReference = deterministicSample(reference, maximumReferencePoints)
        val normalizedReference = boundedReference.map { normalize(it.features, means, deviations) }
        val calibrationDistances =
            if (normalizedReference.size >= 2) {
                normalizedReference.indices.map { index ->
                    nearestNormalizedDistance(
                        point = normalizedReference[index],
                        candidates = normalizedReference,
                        excludedCandidateIndex = index
                    )
                }.sorted()
            } else {
                emptyList()
            }
        val calibratedDistanceThreshold =
            outOfDistributionDistance
                ?: calibrationDistances.takeIf(List<Double>::isNotEmpty)
                    ?.let { quantile(it, DEFAULT_CALIBRATION_QUANTILE) }
                    ?.coerceAtLeast(MINIMUM_DISTANCE_THRESHOLD)
                ?: DEFAULT_SINGLE_REFERENCE_THRESHOLD
        val points = evaluation.map { row ->
            val normalized = normalize(row.features, means, deviations)
            val nearest = nearestNormalizedDistance(normalized, normalizedReference)
            val outsideFraction =
                row.features.indices.count { index ->
                    row.features[index] < minimums[index] || row.features[index] > maximums[index]
                }.toDouble() / featureCount
            CoveragePoint(
                sampleId = row.sampleId,
                groupId = row.groupId,
                normalizedNearestReferenceDistance = nearest,
                outsideTrainingRangeFraction = outsideFraction,
                // A single marginal range escape becomes almost certain as feature
                // dimensionality grows. Keep it as visible evidence, while calibrating
                // the multivariate OOD decision only from reference-partition neighbours.
                outOfDistribution = nearest > calibratedDistanceThreshold
            )
        }
        val distances = points.map(CoveragePoint::normalizedNearestReferenceDistance).sorted()
        return DatasetCoverageSummary(
            referenceCount = reference.size,
            comparedReferenceCount = boundedReference.size,
            evaluationCount = evaluation.size,
            referenceGroupCount = reference.map(CoverageObservation::groupId).distinct().size,
            evaluationGroupCount = evaluation.map(CoverageObservation::groupId).distinct().size,
            featureCount = featureCount,
            points = points,
            calibratedDistanceThreshold = calibratedDistanceThreshold,
            meanNearestDistance = distances.average(),
            p95NearestDistance = quantile(distances, 0.95),
            outOfDistributionRate = points.count(CoveragePoint::outOfDistribution).toDouble() / points.size,
            rangeEscapeRate = points.count { it.outsideTrainingRangeFraction > 0.0 }.toDouble() / points.size,
            meanOutsideTrainingRangeFraction = points.map(CoveragePoint::outsideTrainingRangeFraction).average(),
            constantFeatureCount = constantFeatures
        )
    }

    private fun normalize(values: DoubleArray, means: DoubleArray, deviations: DoubleArray): DoubleArray =
        DoubleArray(values.size) { index -> (values[index] - means[index]) / deviations[index] }

    /**
     * Exact nearest-neighbour RMS distance with no per-candidate allocations.
     * Once a candidate's partial squared distance already exceeds the best complete
     * candidate it cannot win, so the remaining dimensions can be skipped safely.
     */
    private fun nearestNormalizedDistance(
        point: DoubleArray,
        candidates: List<DoubleArray>,
        excludedCandidateIndex: Int = -1
    ): Double {
        var bestSquaredSum = Double.POSITIVE_INFINITY
        var candidateIndex = 0
        while (candidateIndex < candidates.size) {
            if (candidateIndex == excludedCandidateIndex) {
                candidateIndex++
                continue
            }
            val candidate = candidates[candidateIndex]
            var squaredSum = 0.0
            var featureIndex = 0
            while (featureIndex < point.size && squaredSum < bestSquaredSum) {
                val delta = point[featureIndex] - candidate[featureIndex]
                squaredSum += delta * delta
                featureIndex++
            }
            if (squaredSum < bestSquaredSum) bestSquaredSum = squaredSum
            candidateIndex++
        }
        return sqrt(bestSquaredSum / point.size)
    }

    private fun deterministicSample(
        values: List<CoverageObservation>,
        maximumCount: Int
    ): List<CoverageObservation> {
        if (values.size <= maximumCount) return values
        if (maximumCount == 1) return listOf(values.first())
        return (0 until maximumCount).map { index ->
            values[((values.size - 1L) * index / (maximumCount - 1L)).toInt()]
        }
    }

    private fun quantile(sorted: List<Double>, probability: Double): Double {
        val position = probability.coerceIn(0.0, 1.0) * sorted.lastIndex
        val lower = position.toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[upper] * fraction
    }

    private const val DEFAULT_CALIBRATION_QUANTILE = 0.99
    private const val MINIMUM_DISTANCE_THRESHOLD = 1e-9
    private const val DEFAULT_SINGLE_REFERENCE_THRESHOLD = 3.0
}
