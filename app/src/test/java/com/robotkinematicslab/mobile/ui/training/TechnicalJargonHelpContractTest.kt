package com.robotkinematicslab.mobile.ui.training

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects situated novice help on the research screens without coupling tests to Compose layout. */
class TechnicalJargonHelpContractTest {

    @Test
    fun principalTechnicalScreensKeepDiscoverableJargonHelp() {
        listOf(
            "ui/training/LocalTrainingPanel.kt",
            "ui/training/ClosedLoopTrainingPanel.kt",
            "ui/training/RobustnessAuditPanel.kt",
            "ui/training/explainability/ExplainabilityPanel.kt",
            "ui/training/comparison/TrainingComparisonPanel.kt",
            "ui/storage/StorageCenterPanel.kt",
            "ui/settings/ComputeSettingsPanel.kt"
        ).forEach { relativePath ->
            val source = source(relativePath)
            assertTrue("$relativePath must keep the novice help notice", source.contains("JargonHelpNotice()"))
            assertTrue("$relativePath must keep situated explanations", source.contains("JargonAwareText("))
        }
    }

    @Test
    fun comparisonUsesHumanReferenceLabelsInsteadOfBareOracleCopy() {
        val source = source("ui/training/comparison/TrainingComparisonPanel.kt")

        assertTrue(source.contains("Deterministic IK reference (oracle)"))
        assertTrue(source.contains("Probability assigned to correct label"))
        assertFalse(source.contains("ChartMetricRow(\"Oracle\""))
        assertFalse(source.contains("ChartMetricRow(\"Oracle label\""))
        assertFalse(source.contains("ChartMetricRow(\"Probability on truth\""))
    }

    @Test
    fun criticalExplanationsUseCatalogFriendlyTerms() {
        assertTrue(source("ui/training/LocalTrainingPanel.kt").contains("held-out test set"))
        assertTrue(source("ui/training/ClosedLoopTrainingPanel.kt").contains("committed checkpoint"))
        assertTrue(source("ui/training/RobustnessAuditPanel.kt").contains("standard deviation"))
        assertTrue(source("ui/storage/ProjectEvidencePackPanel.kt").contains("SHA-256 checksums"))
        assertTrue(source("ui/settings/ComputeSettingsPanel.kt").contains("thermal headroom"))
    }

    private fun source(relativePath: String): String {
        val suffix = "app/src/main/java/com/robotkinematicslab/mobile/$relativePath"
        val start = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { root -> File(root, suffix) }
            .firstOrNull(File::isFile)
            ?.readText()
            ?: error("Cannot locate production source $suffix from ${start.absolutePath}")
    }
}
