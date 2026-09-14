package com.robotkinematicslab.mobile.ui.training

import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CumulativeFeatureGrowthSelectionsTest {

    @Test
    fun `preset creates all eight strictly nested feature contracts`() {
        val selections = cumulativeFeatureGrowthSelections()

        assertEquals(CUMULATIVE_FEATURE_COUNTS, selections.map { it.featureCount })
        assertEquals(selections.size, selections.map { it.id }.distinct().size)
        selections.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(larger.includedFeatureNames.take(smaller.featureCount) == smaller.includedFeatureNames)
        }
    }

    @Test
    fun `research growth keeps frozen points then adds candidates in bounded steps`() {
        val selections = researchFeatureGrowthSelections()

        assertEquals(RESEARCH_CUMULATIVE_FEATURE_COUNTS, selections.map(FeatureSelectionSpec::featureCount))
        assertEquals(468, selections.last().featureCount)
        selections.zipWithNext().forEach { (left, right) ->
            assertTrue(right.includedFeatureNames.take(left.featureCount) == left.includedFeatureNames)
        }
    }

    @Test
    fun `family ablation always retains a nonempty strict subset plus one full control`() {
        val selections = featureFamilyAblationSelections()

        assertEquals(468, selections.first().featureCount)
        assertTrue(selections.size > 3)
        assertTrue(selections.drop(1).all { it.featureCount in 1 until 468 })
        assertEquals(selections.size, selections.map(FeatureSelectionSpec::id).distinct().size)
    }

    @Test
    fun `campaign previews exactly match the arms applied after confirmation`() {
        val cumulative = featureCampaignDefinition(FeatureCampaignPreset.CUMULATIVE_GROWTH)
        val research = featureCampaignDefinition(FeatureCampaignPreset.RESEARCH_GROWTH)
        val ablation = featureCampaignDefinition(FeatureCampaignPreset.PAIRED_FAMILY_ABLATION)

        assertEquals(cumulativeFeatureGrowthSelections(), cumulative.selections)
        assertEquals(researchFeatureGrowthSelections(), research.selections)
        assertEquals(featureFamilyAblationSelections(), ablation.selections)
        listOf(cumulative, research, ablation).forEach { definition ->
            assertTrue(definition.title.isNotBlank())
            assertTrue(definition.configurationSummary.isNotBlank())
            assertTrue(definition.scientificQuestion.endsWith("?"))
            assertTrue(definition.selections.isNotEmpty())
        }
    }

    @Test
    fun `custom arm names accept a new identity and reject blank or duplicate names`() {
        val selections = cumulativeFeatureGrowthSelections().take(2)

        assertEquals(null, customFeatureSetNameError("My focused contract", selections))
        assertTrue(customFeatureSetNameError("   ", selections).orEmpty().contains("Enter a name"))
        assertTrue(
            customFeatureSetNameError(
                selections.first().displayName.uppercase(),
                selections
            ).orEmpty().contains("already uses this name")
        )
    }
}
