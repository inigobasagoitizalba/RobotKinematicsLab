package com.robotkinematicslab.mobile.ml.research

import kotlin.math.ceil

/** Split-conformal prediction sets using held-out calibration rows. */
object ConformalClassificationCalculator {

    fun calculate(
        calibration: List<ConformalClassificationObservation>,
        evaluation: List<ConformalClassificationObservation>,
        alpha: Double
    ): ConformalClassificationEvidence {
        require(calibration.isNotEmpty()) { "Conformal calibration rows are required." }
        require(evaluation.isNotEmpty()) { "Independent conformal evaluation rows are required." }
        require(alpha.isFinite() && alpha in 0.0..1.0 && alpha > 0.0 && alpha < 1.0)
        val classCount = calibration.first().probabilities.size
        require(calibration.all { it.probabilities.size == classCount })
        require(evaluation.all { it.probabilities.size == classCount })
        require(calibration.map { it.sampleId }.distinct().size == calibration.size) {
            "Conformal calibration sample identifiers must be unique."
        }
        require(evaluation.map { it.sampleId }.distinct().size == evaluation.size) {
            "Conformal evaluation sample identifiers must be unique."
        }
        require((calibration.map { it.sampleId } intersect evaluation.map { it.sampleId }.toSet()).isEmpty()) {
            "Calibration and conformal evaluation rows must be disjoint."
        }

        val scores = calibration.map { 1.0 - it.probabilities[it.truthIndex] }.sorted()
        val rank = ceil((scores.size + 1.0) * (1.0 - alpha)).toInt().coerceIn(1, scores.size)
        val scoreThreshold = scores[rank - 1]
        val probabilityThreshold = (1.0 - scoreThreshold).coerceIn(0.0, 1.0)
        var covered = 0
        var totalSetSize = 0
        var singleton = 0
        var empty = 0
        evaluation.forEach { row ->
            val members = row.probabilities.indices.filter { row.probabilities[it] >= probabilityThreshold }
            if (row.truthIndex in members) covered++
            totalSetSize += members.size
            if (members.size == 1) singleton++
            if (members.isEmpty()) empty++
        }
        return ConformalClassificationEvidence(
            alpha = alpha,
            calibrationCount = calibration.size,
            evaluationCount = evaluation.size,
            probabilityThreshold = probabilityThreshold,
            empiricalCoverage = covered.toDouble() / evaluation.size,
            averageSetSize = totalSetSize.toDouble() / evaluation.size,
            singletonRate = singleton.toDouble() / evaluation.size,
            emptySetRate = empty.toDouble() / evaluation.size
        )
    }
}
