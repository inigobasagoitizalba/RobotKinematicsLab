package com.robotkinematicslab.mobile.ui.dataset

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DatasetQualityCancellationContractTest {

    @Test
    fun `active audit passes its cancellation gate`() {
        ensureDatasetQualityAuditActive { false }
    }

    @Test
    fun `cancelled audit cannot publish a completed result`() {
        val error = assertThrows(CancellationException::class.java) {
            ensureDatasetQualityAuditActive { true }
        }

        assertEquals("Dataset coverage analysis was cancelled.", error.message)
    }
}
