package com.robotkinematicslab.mobile.ui.shared.progress

import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryArchiveFeedbackTest {

    @Test
    fun savedFeedbackReportsBothSamplesAndCharts() {
        val message =
            telemetryArchiveFeedbackMessage(
                TelemetryArchiveFeedback.Saved(sessionSampleCount = 321, figureCount = 20)
            )

        assertTrue(message.contains("321 telemetry samples"))
        assertTrue(message.contains("20 chart images"))
    }

    @Test
    fun failedFeedbackSeparatesCompletedRunFromArchiveFailure() {
        val message =
            telemetryArchiveFeedbackMessage(
                TelemetryArchiveFeedback.Failed("storage unavailable")
            )

        assertTrue(message.contains("scientific run completed"))
        assertTrue(message.contains("storage unavailable"))
    }
}
