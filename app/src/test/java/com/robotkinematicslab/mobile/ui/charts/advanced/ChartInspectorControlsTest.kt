package com.robotkinematicslab.mobile.ui.charts.advanced

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartInspectorControlsTest {

    @Test
    fun steppedZoom_movesBothDirectionsAndHonoursBounds() {
        assertEquals(3f, steppedInspectorZoom(2f, factor = 1.5f, maximumScale = 24f))
        assertEquals(1f, steppedInspectorZoom(1.2f, factor = 0.5f, maximumScale = 24f))
        assertEquals(24f, steppedInspectorZoom(20f, factor = 1.5f, maximumScale = 24f))
    }
}
