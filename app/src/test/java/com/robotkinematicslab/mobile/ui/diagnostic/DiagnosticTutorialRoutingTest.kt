package com.robotkinematicslab.mobile.ui.diagnostic

import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticTutorialRoutingTest {

    @Test
    fun `run result and chart entry controls remain on the base surface`() {
        assertEquals(
            DiagnosticTutorialSurface.BASE,
            diagnosticSurfaceForTutorialTarget(TutorialTargets.DiagnosticsRun)
        )
        assertEquals(
            DiagnosticTutorialSurface.BASE,
            diagnosticSurfaceForTutorialTarget(TutorialTargets.DiagnosticsCharts)
        )
    }

    @Test
    fun `deep chart and numerical safety targets resolve to their existing surfaces`() {
        assertEquals(
            DiagnosticTutorialSurface.CHARTS,
            diagnosticSurfaceForTutorialTarget(TutorialTargets.ChartGestures)
        )
        assertEquals(
            DiagnosticTutorialSurface.NUMERICAL_SAFETY,
            diagnosticSurfaceForTutorialTarget(TutorialTargets.NumericalSafetyConfiguration)
        )
    }

    @Test
    fun `unrelated and absent hints preserve the current diagnostic surface`() {
        assertNull(diagnosticSurfaceForTutorialTarget(TutorialTargets.Training))
        assertNull(diagnosticSurfaceForTutorialTarget(null))
    }
}
