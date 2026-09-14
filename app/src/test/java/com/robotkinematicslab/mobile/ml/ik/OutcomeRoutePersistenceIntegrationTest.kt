package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.ml.data.FeatureNormalization
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilityOutcomeScope
import com.robotkinematicslab.mobile.ui.training.explainability.ExplainabilityFigureExportContract
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** Synthetic route evidence intentionally exercises every branch without a costly training campaign. */
class OutcomeRoutePersistenceIntegrationTest {
    @Test fun exclusiveRoutesKeepTheirCommonDenominatorThroughPersistenceAndXaiExport() {
        val observations = listOf(
            OneMicronVerificationObservation(1, 42, VerifiedIkPath.NEURAL_DIRECT, 0.0, Double.NaN, 0.0, 0.0, true, 0, 0, 0),
            OneMicronVerificationObservation(2, 42, VerifiedIkPath.NEURAL_REFINED, 0.01, 0.0, 0.0, 0.0, true, 3, 0, 4),
            OneMicronVerificationObservation(3, 42, VerifiedIkPath.DETERMINISTIC_FALLBACK, 0.01, 0.02, 0.0, 0.0, true, 3, 4, 4),
            OneMicronVerificationObservation(4, 42, VerifiedIkPath.FAILED, 0.01, 0.02, 0.03, 0.01, false, 3, 4, 4))
        val metrics = OneMicronIkVerificationMetrics(4, .25, .5, .75, .01, .01, .01, .01, 2.25, 3.0, 1.0,
            verifiedPipelineSuccessRate = .75, observations = observations)
        val chartRates = listOf(metrics.directNeuralSuccessRate, metrics.refinedOnlySuccessRate, metrics.fallbackOnlySuccessRate, metrics.failedPipelineRate)
        assertEquals(listOf(.25,.25,.25,.25), chartRates)
        assertEquals(1.0, chartRates.sum(), 0.0)
        assertEquals(.75, metrics.pureSolverSuccessRate, 0.0) // Independent comparator overlaps three routes.
        assertThrows(IllegalArgumentException::class.java) { metrics.copy(rawNeuralSuccessRate = .75) }
        assertThrows(IllegalArgumentException::class.java) { metrics.copy(samples = 3) }
        assertThrows(IllegalArgumentException::class.java) { metrics.copy(observations = observations.map { it.copy(sourceRowIndex = 1) }) }
        assertThrows(IllegalArgumentException::class.java) {
            observations.last().copy(pureSolverCertified = true, pureSolverResidualMeters = 0.0).requireScientificallyConsistent(ONE_MICRON_METERS)
        }
        val profile = OneMicronIkFeatureProfile.KINEMATICS_108
        val names = OneMicronIkFeatureEncoder.featureNames(profile)
        val root = Files.createTempDirectory("route-evidence").toFile()
        val result = OneMicronIkTrainingResult("route-audit", OneMicronIkTrainingConfig("route-audit", "synthetic-evidence.csv", profile),
            LocalIkRegressionModel(inputFeatureCount = names.size, hiddenUnitCount = 4, inputWeights = FloatArray(names.size*4), hiddenBiases = FloatArray(4), outputWeights = FloatArray(4*ONE_MICRON_MAX_JOINTS), outputBiases = FloatArray(ONE_MICRON_MAX_JOINTS)),
            FeatureNormalization(FloatArray(names.size), FloatArray(names.size) { 1f }), names, emptyList(), 0, 4, 4, 4, 0, metrics, 1, 2)
        val saved = OneMicronIkStorageRepository(File(root,"runs"),File(root,"models")).save(result)
        val properties = Properties().apply { File(saved.reportPath).inputStream().use { load(it) } }
        listOf("directNeuralSuccessRate","refinedOnlySuccessRate","fallbackOnlySuccessRate","failedPipelineRate").forEach {
            assertEquals(.25, properties.getProperty(it).toDouble(), 0.0)
        }
        assertEquals("4", properties.getProperty("verificationSampleCount"))
        assertEquals(.75, properties.getProperty("pureSolverSuccessRate").toDouble(), 0.0)
        assertTrue(properties.getProperty("pureSolverDenominator").contains("independent comparator"))
        val csv = File(properties.getProperty("verificationCasesPath")).readLines().drop(1)
        assertEquals(observations.map { it.path.name }, csv.map { it.split(',')[3] })
        VerifiedIkPath.entries.forEach { assertFalse(ExplainabilityOutcomeScope.canExplainIkRoute(it)) }
        val xaiExport = ExplainabilityFigureExportContract.createSession("classifier-run", "classifier", "inputs", 4, 8, 1)
        assertTrue(xaiExport.metadata.contains(ExplainabilityOutcomeScope.DESCRIPTION))
        assertTrue(xaiExport.metadata.contains("do not explain"))
    }
}
