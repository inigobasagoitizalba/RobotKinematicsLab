package com.robotkinematicslab.mobile.ui.dataset.robotstore

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotReferenceCatalogIntegrityTest {

    @Test
    fun validCatalogSeparatesTenSyntheticPresetsFromThreeAdmittedCommercialReferences() {
        val synthetic = DatasetRobotPresets().buildDefaults()
        val commercial = IndustrialRobotReferenceCatalog.entries

        RobotReferenceCatalogIntegrity.requireValid(
            commercial,
            synthetic.map { it.id }.toSet(),
            IndustrialRobotReferenceCatalog.QUARANTINED_REFERENCE_COUNT
        )
        assertEquals(10, synthetic.size)
        assertEquals(3, commercial.size)
        assertEquals(7, IndustrialRobotReferenceCatalog.QUARANTINED_REFERENCE_COUNT)
        assertTrue(commercial.none { reference -> synthetic.any { it.id == reference.id } })
    }

    @Test
    fun manualWithoutRedistributionPermissionCannotMasqueradeAsBundledContent() {
        val valid = IndustrialRobotReferenceCatalog.entries.first()
        val invalid =
            valid.copy(
                documents =
                    listOf(
                        valid.documents.first().copy(resourceId = valid.artworkResId)
                    )
            )

        assertThrows(IllegalArgumentException::class.java) {
            RobotReferenceCatalogIntegrity.requireValid(
                listOf(invalid),
                emptySet(),
                RobotReferenceCatalogIntegrity.REVIEWED_COMMERCIAL_CANDIDATE_COUNT - 1
            )
        }
    }

    @Test
    fun everyAdmittedManualHasPinnedIntegrityAndDurableOfflineStrategy() {
        val documents = IndustrialRobotReferenceCatalog.entries.flatMap(IndustrialRobotReference::documents)

        assertEquals(4, documents.size)
        assertTrue(documents.all { it.resourceId == null })
        assertTrue(documents.all { it.redistributionStatus == RobotManualRedistributionStatus.PERMISSION_NOT_ESTABLISHED })
        assertTrue(documents.all { it.offlineStrategy == RobotManualOfflineStrategy.VERIFIED_PRIVATE_COPY })
        assertEquals(48_153_588L, documents.sumOf { requireNotNull(it.expectedByteCount) })
    }
}
