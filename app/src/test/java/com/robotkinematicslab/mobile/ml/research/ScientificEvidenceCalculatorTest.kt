package com.robotkinematicslab.mobile.ml.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificEvidenceCalculatorTest {

    @Test
    fun `risk coverage uses the exact accepted prefix and improves when uncertain failures are rejected`() {
        val observations =
            listOf(
                prediction(1, confidence = 0.99, truthProbability = 0.95, correct = true),
                prediction(2, confidence = 0.90, truthProbability = 0.85, correct = true),
                prediction(3, confidence = 0.60, truthProbability = 0.20, correct = false),
                prediction(4, confidence = 0.40, truthProbability = 0.10, correct = false)
            )

        val result = ScientificEvidenceCalculator.selectiveRiskCurve(observations)

        assertEquals(listOf(1, 2, 3, 4), result.points.map(SelectiveRiskPoint::acceptedCount))
        assertEquals(0.0, result.points[1].risk, 1e-12)
        assertEquals(0.5, result.points.last().risk, 1e-12)
        assertEquals(1.0, result.points.last().coverage, 1e-12)
        assertTrue(result.areaUnderRiskCoverage in 0.0..1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `risk coverage rejects duplicate evidence rows`() {
        ScientificEvidenceCalculator.selectiveRiskCurve(
            listOf(
                prediction(7, confidence = 0.9, truthProbability = 0.8, correct = true),
                prediction(7, confidence = 0.8, truthProbability = 0.7, correct = true)
            )
        )
    }

    @Test
    fun `robot slices retain calibration and binomial uncertainty`() {
        val observations =
            listOf(
                prediction(1, robot = "A", confidence = 0.9, truthProbability = 0.8, correct = true),
                prediction(2, robot = "A", confidence = 0.7, truthProbability = 0.6, correct = false),
                prediction(3, robot = "B", confidence = 0.8, truthProbability = 0.7, correct = true)
            )

        val slices = ScientificEvidenceCalculator.generalizationByRobot(observations)
        val robotA = slices.single { it.sliceId == "A" }

        assertEquals(0.5, robotA.accuracy, 1e-12)
        assertEquals(0.8, robotA.meanConfidence, 1e-12)
        assertEquals(0.3, robotA.calibrationGap, 1e-12)
        assertTrue(robotA.wilsonLower95 < robotA.accuracy)
        assertTrue(robotA.wilsonUpper95 > robotA.accuracy)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `robot slices reject duplicated held out evidence`() {
        ScientificEvidenceCalculator.generalizationByRobot(
            listOf(
                prediction(1, confidence = 0.9, truthProbability = 0.8, correct = true),
                prediction(1, confidence = 0.9, truthProbability = 0.8, correct = true)
            )
        )
    }

    @Test
    fun `tail risk exposes catastrophic confident mistakes`() {
        val observations = (0 until 100).map { index ->
            prediction(
                id = index.toLong(),
                confidence = 0.9,
                truthProbability = if (index == 99) 0.0 else 0.9,
                correct = index != 99
            )
        }

        val result = ScientificEvidenceCalculator.tailRisk(observations)

        assertEquals(0.1, result.medianLoss, 1e-12)
        assertEquals(1.0, result.maximumLoss, 1e-12)
        assertTrue(result.cvar99Loss > result.p95Loss)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tail risk rejects duplicated held out evidence`() {
        ScientificEvidenceCalculator.tailRisk(
            listOf(
                prediction(1, confidence = 0.9, truthProbability = 0.8, correct = true),
                prediction(1, confidence = 0.9, truthProbability = 0.8, correct = true)
            )
        )
    }

    @Test
    fun `pipeline error budget preserves signed stage contributions`() {
        val result =
            ScientificEvidenceCalculator.pipelineErrorBudget(
                listOf(
                    PipelineErrorObservation(1, 1.0, 0.9, 0.4, 0.1, 0.08, repairApplied = true, fallbackApplied = false),
                    PipelineErrorObservation(2, 1.0, 1.1, 0.6, 0.2, 0.10, repairApplied = true, fallbackApplied = true)
                )
            )

        assertEquals(1.0, result.meanInitialErrorMeters, 1e-12)
        assertEquals(0.0, result.validationDeltaMeters, 1e-12)
        assertEquals(0.5, result.repairDeltaMeters, 1e-12)
        assertEquals(0.91, result.totalErrorReductionFraction, 1e-12)
        assertEquals(0.5, result.fallbackUseRate, 1e-12)
    }

    @Test
    fun `robustness analysis is paired by perturbation magnitude`() {
        val result =
            ScientificEvidenceCalculator.robustness(
                listOf(
                    PerturbationObservation(1, 0.01, 0.1, 0.2, baselineCorrect = true, perturbedCorrect = true),
                    PerturbationObservation(2, 0.01, 0.1, 0.8, baselineCorrect = true, perturbedCorrect = false),
                    PerturbationObservation(1, 0.10, 0.1, 0.9, baselineCorrect = true, perturbedCorrect = false)
                )
            )

        assertEquals(2, result.size)
        assertEquals(0.4, result.first().meanLossIncrease, 1e-12)
        assertEquals(0.5, result.first().accuracyDrop, 1e-12)
        assertEquals(1.0, result.last().failureIntroductionRate, 1e-12)
    }

    @Test
    fun `pareto frontier rejects a slower lower scoring training point`() {
        val result =
            ScientificEvidenceCalculator.trainingScaleEvidence(
                listOf(
                    TrainingScaleObservation("small", 1_000, 0.70, 100, 1_000),
                    TrainingScaleObservation("dominated", 2_000, 0.69, 200, 2_000),
                    TrainingScaleObservation("accurate", 3_000, 0.80, 300, 3_000)
                )
            )

        assertTrue("small" in result.paretoEfficientLabels)
        assertTrue("accurate" in result.paretoEfficientLabels)
        assertTrue("dominated" !in result.paretoEfficientLabels)
    }

    private fun prediction(
        id: Long,
        robot: String = "robot",
        confidence: Double,
        truthProbability: Double,
        correct: Boolean
    ) = ScientificPredictionObservation(
        sampleId = id,
        modelId = "model",
        robotId = robot,
        topologyKey = "R-R-R",
        confidence = confidence,
        probabilityAssignedToTruth = truthProbability,
        correct = correct
    )
}
