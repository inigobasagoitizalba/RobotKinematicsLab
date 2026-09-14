package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.training.TrainingResourceMode
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingResourceModeControlsTest {
    @Test
    fun `training workload modes have explicit ordered load semantics`() {
        assertEquals(ResourceLoadLevel.CONSERVATIVE, TrainingResourceMode.QUICK.loadLevel())
        assertEquals(ResourceLoadLevel.MODERATE, TrainingResourceMode.BALANCED.loadLevel())
        assertEquals(ResourceLoadLevel.HIGH, TrainingResourceMode.MAXIMUM_ACCURACY.loadLevel())
    }
}
