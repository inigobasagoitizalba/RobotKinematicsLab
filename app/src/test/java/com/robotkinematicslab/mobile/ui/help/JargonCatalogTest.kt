package com.robotkinematicslab.mobile.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JargonCatalogTest {

    @Test
    fun registryHasUniqueStableIdsAndCompleteExplanations() {
        val definitions = JargonCatalog.definitions

        assertTrue(definitions.size >= 55)
        assertEquals(definitions.size, definitions.map(JargonDefinition::id).distinct().size)
        definitions.forEach { definition ->
            assertTrue(definition.id.isNotBlank())
            assertTrue(definition.term.isNotBlank())
            assertTrue(definition.plainMeaning.isNotBlank())
            assertTrue(definition.whyItMatters.isNotBlank())
        }
    }

    @Test
    fun resolverMatchesAliasesCaseInsensitivelyAndAcrossPunctuation() {
        val matches = JargonCatalog.findMatches("FK, NaN and a CONFUSION MATRIX need explanation.")

        assertEquals(
            listOf("Forward kinematics (FK)", "Non-finite value", "Confusion matrix"),
            matches.map { it.definition.term }
        )
    }

    @Test
    fun resolverUsesLongestMatchAndNeverReturnsOverlappingTerms() {
        val matches = JargonCatalog.findMatches("The calibration gap and data provenance are stored.")

        assertEquals(listOf("Calibration", "Provenance"), matches.map { it.definition.term })
        assertTrue(matches.zipWithNext().all { (left, right) -> left.endExclusive <= right.start })
    }

    @Test
    fun resolverDoesNotMatchAcronymsInsideOrdinaryWords() {
        val matches = JargonCatalog.findMatches("The hiking view and skin settings are available.")

        assertFalse(matches.any { it.displayedText.equals("IK", ignoreCase = true) })
        assertTrue(matches.isEmpty())
    }

    @Test
    fun repeatedTermsRemainIndependentlyInteractive() {
        val matches = JargonCatalog.findMatches("MSE improves while a second MSE run is held out.")

        assertEquals(2, matches.count { it.definition.term == "Mean squared error (MSE)" })
        assertEquals(listOf("MSE", "MSE"), matches.map(JargonMatch::displayedText))
    }

    @Test
    fun resolverDoesNotConfuseSensitivityAnalysisWithClassificationRecall() {
        val matches =
            JargonCatalog.findMatches(
                "Seed sensitivity and numerical sensitivity are audited before recall score."
            )

        assertEquals(
            listOf("Sensitivity analysis", "Sensitivity analysis", "Recall"),
            matches.map { match -> match.definition.term }
        )
    }
}
