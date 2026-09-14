package com.robotkinematicslab.mobile.ui.dataset.robotstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustrialRobotReferenceCatalogTest {

    @Test
    fun catalogAdmitsOnlyFixedVariantsWithPrimaryManualAndKinematicEvidence() {
        val entries = IndustrialRobotReferenceCatalog.entries

        assertEquals(3, entries.size)
        assertEquals(7, IndustrialRobotReferenceCatalog.QUARANTINED_REFERENCE_COUNT)
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertEquals(entries.size, entries.map { it.displayName }.distinct().size)
        assertTrue(entries.all { it.fixedVariant.isNotBlank() })
        assertTrue(entries.all { it.artworkResId != 0 })
        assertTrue(entries.all { it.specifications.isNotEmpty() })
        assertTrue(entries.all { it.jointLimits.isNotEmpty() })
        assertTrue(entries.all { it.documents.isNotEmpty() })
        assertTrue(
            entries.all {
                it.evidenceStatus ==
                    RobotReferenceEvidenceStatus.PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED
            }
        )
        assertTrue(entries.all { it.kinematicEvidence.sourceUrl.startsWith("https://") })
        assertTrue(entries.all { it.artworkEvidence.note.contains("artwork", ignoreCase = true) })
    }

    @Test
    fun everyManualUsesPinnedOfficialRemoteEvidenceWithIntegrityMetadata() {
        val documents = IndustrialRobotReferenceCatalog.entries.flatMap { it.documents }
        val sha256 = Regex("[0-9a-f]{64}")

        assertEquals(4, documents.size)
        documents.forEach { document ->
            assertEquals(null, document.resourceId)
            assertTrue(document.localFileName.endsWith(".pdf"))
            assertTrue(document.sourceUrl.startsWith("https://"))
            assertTrue(document.officialDownloadUrl?.startsWith("https://") == true)
            assertTrue(document.expectedSha256?.matches(sha256) == true)
            assertTrue((document.expectedByteCount ?: 0L) > 1_000L)
            assertTrue((document.expectedPageCount ?: 0) > 50)
            assertTrue(document.provenanceNote.isNotBlank())
            assertFalse(document.sourceLabel.contains("mirror", ignoreCase = true))
            assertFalse(document.sourceLabel.contains("archive", ignoreCase = true))
        }
    }

    @Test
    fun manualAndSeparateKinematicSourcesAreNeverConflated() {
        val ur5e = IndustrialRobotReferenceCatalog.entries.single { it.id == "ur5e" }
        val fr3 = IndustrialRobotReferenceCatalog.entries.single { it.id == "franka-research-3" }
        val gen3 = IndustrialRobotReferenceCatalog.entries.single { it.id == "kinova-gen3" }

        assertFalse(ur5e.kinematicEvidence.containedInManual)
        assertFalse(fr3.kinematicEvidence.containedInManual)
        assertTrue(gen3.kinematicEvidence.containedInManual)
        assertTrue(gen3.documents.single().relevantPages.contains("7 DoF DH"))
        assertTrue(gen3.documents.single().relevantPages.contains("Table 94"))
    }

    @Test
    fun asymmetricAndContinuousLimitsRemainExplicit() {
        val fr3 = IndustrialRobotReferenceCatalog.entries.single { it.id == "franka-research-3" }
        val gen3 = IndustrialRobotReferenceCatalog.entries.single { it.id == "kinova-gen3" }

        assertEquals(
            "-3.0770 to -0.1169 rad",
            fr3.jointLimits.single { it.joint == "J4" }.range
        )
        assertEquals(
            "R07 continuous control range · no external cable",
            gen3.jointLimits.single { it.joint == "J1" }.range
        )
        assertTrue(gen3.fixedVariant.contains("fixed base"))
        assertTrue(gen3.fixedVariant.contains("no external actuator cable"))
    }
}
