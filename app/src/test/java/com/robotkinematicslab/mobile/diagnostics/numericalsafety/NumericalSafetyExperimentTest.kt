package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticAllocationTracker
import com.robotkinematicslab.mobile.domain.config.IKConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericalSafetyExperimentTest {

    @Test
    fun pairedCampaignIsDeterministicApartFromTiming() {
        val config = compactConfig(includeAdversarial = true)
        val first = NumericalSafetyExperiment().run(config)
        val second = NumericalSafetyExperiment().run(config)

        assertEquals(first.protocolId, second.protocolId)
        assertEquals(first.trials.size, second.trials.size)
        first.trials.zip(second.trials).forEach { (left, right) ->
            assertEquals(left.index, right.index)
            assertEquals(left.cohort, right.cohort)
            assertEquals(left.scenario, right.scenario)
            assertEquals(left.seed, right.seed)
            assertEquals(left.target, right.target)
            assertEquals(left.guarded.copy(durationNanos = 0L), right.guarded.copy(durationNanos = 0L))
            assertEquals(
                left.unguarded?.copy(durationNanos = 0L),
                right.unguarded?.copy(durationNanos = 0L)
            )
        }
    }

    @Test
    fun validCampaignNeverLetsGuardedSolverClaimAnUncertifiedSuccess() {
        val report = NumericalSafetyExperiment().run(compactConfig(includeAdversarial = false))
        val valid = report.trials.filter { it.cohort == NumericalSafetyCohort.VALID_CAMPAIGN }

        assertTrue(valid.isNotEmpty())
        assertTrue(valid.all { it.target.isFinite() })
        assertTrue(valid.none { it.guarded.unsafeSuccess })
        assertTrue(valid.none { it.guarded.exceptionContained })
        assertEquals(
            report.validGuardedSummary.validatedSuccessCount,
            valid.count { it.guarded.validatedSuccess }
        )
    }

    @Test
    fun pairedResidualImpactUsesOnlyFiniteStatesInsideJointLimits() {
        val report = NumericalSafetyExperiment().run(compactConfig(includeAdversarial = false))
        val impact = requireNotNull(report.validImpact)
        val admissiblePairs =
            report.trials.filter { trial ->
                val raw = trial.unguarded ?: return@filter false
                trial.guarded.stateFinite && trial.guarded.jointLimitsValid &&
                    raw.stateFinite && raw.jointLimitsValid &&
                    trial.guarded.independentResidualMeters.isFinite() &&
                    raw.independentResidualMeters.isFinite()
            }

        assertEquals(admissiblePairs.size, impact.pairedFiniteResidualCount)
        assertEquals(
            admissiblePairs.sumOf { requireNotNull(it.unguarded).independentResidualMeters },
            impact.pairedRawResidualSumMeters,
            0.0
        )
        assertEquals(
            admissiblePairs.sumOf { it.guarded.independentResidualMeters },
            impact.pairedGuardedResidualSumMeters,
            0.0
        )
        assertTrue(admissiblePairs.all { it.guarded.jointLimitsValid && requireNotNull(it.unguarded).jointLimitsValid })
    }

    @Test
    fun adversarialInputsAreSeparatedAndContainedWithoutFalseSuccess() {
        val report = NumericalSafetyExperiment().run(compactConfig(includeAdversarial = true))
        val injected = report.trials.filter { it.cohort == NumericalSafetyCohort.ADVERSARIAL_PROBES }

        assertEquals(4, injected.size)
        assertTrue(injected.none { it.guarded.reportedConverged })
        assertTrue(injected.none { it.guarded.unsafeSuccess })
        assertTrue(injected.all { it.unguarded != null })
        assertTrue(injected.any { it.unguarded?.nonFiniteEncountered == true })
        assertNotNull(report.adversarialImpact)
        assertTrue(requireNotNull(report.adversarialImpact).rawNonFiniteEventsPrevented > 0)
    }

    @Test
    fun unguardedReferenceCanBeDisabledWithoutChangingProductionExecution() {
        val withReference = NumericalSafetyExperiment().run(compactConfig(includeAdversarial = false))
        val guardedOnly =
            NumericalSafetyExperiment().run(
                compactConfig(includeAdversarial = false).copy(includeUnguardedReference = false)
            )

        assertTrue(guardedOnly.trials.all { it.unguarded == null })
        assertNull(guardedOnly.validUnguardedSummary)
        assertNull(guardedOnly.validImpact)
        assertEquals(
            withReference.trials.map { it.guarded.copy(durationNanos = 0L) },
            guardedOnly.trials.map { it.guarded.copy(durationNanos = 0L) }
        )
    }

    @Test
    fun invalidExperimentConfigurationFailsBeforeAnyNumericalWork() {
        assertFails { compactConfig(false).copy(linkCounts = emptyList()) }
        assertFails { compactConfig(false).copy(validTrialsPerTopology = 0) }
        assertFails {
            compactConfig(false).copy(
                ikConfig = IKConfig(maxIterations = 0, tolerance = 1e-6, damping = 0.01, maxStep = 0.02)
            )
        }
        assertFails {
            compactConfig(false).copy(
                ikConfig = IKConfig(maxIterations = 20, tolerance = Double.NaN, damping = 0.01, maxStep = 0.02)
            )
        }
    }

    @Test
    fun cancellationStopsCampaignInsteadOfPublishingAPartialReport() {
        var checks = 0
        val experiment = NumericalSafetyExperiment(isCancellationRequested = { ++checks > 3 })
        assertFails { experiment.run(compactConfig(includeAdversarial = true)) }
    }

    @Test
    fun numericalBranchesPublishRealAllocationHotspotEvidence() {
        val report = NumericalSafetyExperiment().run(compactConfig(includeAdversarial = false))
        val hotspots = DiagnosticAllocationTracker.snapshot().associateBy { it.label }

        assertEquals(report.trials.size.toLong(), hotspots.getValue("Protected IK branch").callCount)
        assertEquals(report.trials.size.toLong(), hotspots.getValue("Unguarded reference branch").callCount)
    }

    private fun compactConfig(includeAdversarial: Boolean): NumericalSafetyExperimentConfig =
        NumericalSafetyExperimentConfig(
            linkCounts = listOf(3),
            jointModes = listOf(DiagnosticJointMode.MIXED),
            validTrialsPerTopology = 4,
            includeAdversarialProbes = includeAdversarial,
            includeUnguardedReference = true,
            carryStateBetweenTargets = true,
            randomSeed = 2604,
            stressLevel = 0.9,
            ikConfig = IKConfig(
                maxIterations = 120,
                tolerance = 1e-5,
                damping = 0.02,
                maxStep = 0.03
            )
        )

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Expected operation to fail safely.", failed)
    }
}
