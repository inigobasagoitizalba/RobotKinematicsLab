package com.robotkinematicslab.mobile.ui.shared.resources

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceLoadClassifierTest {
    @Test
    fun `bounded choices are classified into three stable bands`() {
        assertEquals(ResourceLoadLevel.CONSERVATIVE, ResourceLoadClassifier.classify(5, 5, 50))
        assertEquals(ResourceLoadLevel.CONSERVATIVE, ResourceLoadClassifier.classify(20, 5, 50))
        assertEquals(ResourceLoadLevel.MODERATE, ResourceLoadClassifier.classify(25, 5, 50))
        assertEquals(ResourceLoadLevel.MODERATE, ResourceLoadClassifier.classify(35, 5, 50))
        assertEquals(ResourceLoadLevel.HIGH, ResourceLoadClassifier.classify(40, 5, 50))
        assertEquals(ResourceLoadLevel.HIGH, ResourceLoadClassifier.classify(50, 5, 50))
    }

    @Test
    fun `worker usage is classified relative to the safe worker ceiling`() {
        assertEquals(ResourceLoadLevel.CONSERVATIVE, ResourceLoadClassifier.classify(2, 1, 7))
        assertEquals(ResourceLoadLevel.MODERATE, ResourceLoadClassifier.classify(4, 1, 7))
        assertEquals(ResourceLoadLevel.HIGH, ResourceLoadClassifier.classify(7, 1, 7))
    }

    @Test
    fun `out of range and degenerate inputs stay deterministic`() {
        assertEquals(ResourceLoadLevel.CONSERVATIVE, ResourceLoadClassifier.classify(-100, 5, 50))
        assertEquals(ResourceLoadLevel.HIGH, ResourceLoadClassifier.classify(500, 5, 50))
        assertEquals(ResourceLoadLevel.CONSERVATIVE, ResourceLoadClassifier.classify(1, 1, 1))
    }
}
