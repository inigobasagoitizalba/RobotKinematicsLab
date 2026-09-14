package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.robotkinematicslab.mobile.ml.data.*
import org.junit.Rule
import org.junit.Test

class FeatureDictionaryUiTest {
    @get:Rule val rule = createComposeRule()
    private fun research() = FeatureSetCatalog.entries().first {
        it.domain == FeatureSetDomain.CLASSIFICATION && it.featureCount == 468
    }
    private fun reveal(tag: String) = rule.onNodeWithTag("feature-dictionary-list").performScrollToNode(hasTestTag(tag))
    private fun search(value: String) {
        reveal("dictionary-search")
        rule.onNodeWithTag("dictionary-search").performTextReplacement(value)
    }
    @Test fun nameIdAndIndexJumpResolveTheSameRegisteredCard() {
        val entry = research()
        val feature = FeatureSetCatalog.definitions(entry)[382]
        rule.setContent { MaterialTheme { FeatureDictionaryContent(entry) } }
        search("#383")
        reveal("dictionary-feature-383")
        rule.onNodeWithTag("dictionary-feature-383").assertTextContains(feature.technicalId, substring = true).performClick()
        reveal("dictionary-detail-383")
        rule.onNodeWithText(feature.definition).assertExists()
        search(feature.technicalId)
        rule.onNodeWithTag("dictionary-count").assertTextContains("1 matching variables / 468")
        search("no-such-feature-id")
        rule.onNodeWithText("No variables match these filters.").assertExists()
    }
    @Test fun rangesExpandedFamilyAndInvalidRangeUseOneContract() {
        val entry = research()
        rule.setContent { MaterialTheme { FeatureDictionaryContent(entry) } }
        reveal("dictionary-range")
        rule.onNodeWithTag("dictionary-range").performTextReplacement("108–130")
        rule.onNodeWithTag("dictionary-count").assertTextContains("23 matching variables / 468")
        rule.onNodeWithTag("dictionary-range").performTextReplacement("130-108")
        rule.onNodeWithTag("dictionary-range-error").assertExists()
        rule.onNodeWithTag("dictionary-count").assertTextContains("0 matching variables / 468")
        reveal("dictionary-expanded-additions")
        rule.onNodeWithTag("dictionary-expanded-additions").performClick()
        rule.onNodeWithTag("dictionary-count").assertTextContains("253 matching variables / 383")
        val expanded = FeatureSetCatalog.entries().first { it.domain == FeatureSetDomain.CLASSIFICATION && it.featureCount == 383 }
        val family = FeatureSetCatalog.definitions(expanded)[130].family
        reveal("dictionary-family")
        rule.onNodeWithTag("dictionary-family").performClick()
        rule.onNodeWithTag("dictionary-family-option:$family").performScrollTo().performClick()
        val count = FeatureSetCatalog.definitions(expanded).count { it.index >= 131 && it.family == family }
        rule.onNodeWithTag("dictionary-count").assertTextContains("$count matching variables / 383")
        val baseline = FeatureSetCatalog.entries().first { it.domain == FeatureSetDomain.CLASSIFICATION && it.featureCount == 108 }
        reveal("dictionary-set")
        rule.onNodeWithTag("dictionary-set").performClick()
        rule.onNodeWithTag("dictionary-set-option:${baseline.technicalId}").performScrollTo().performClick()
        rule.onNodeWithTag("dictionary-count").assertTextContains("108 matching variables / 108")
        rule.onNodeWithTag("dictionary-range").assertTextContains("")
    }
    @Test fun selectedPreviewResolvesParserOrderAndRestoresSearchWithoutChangingOriginDraft() {
        val restoration = StateRestorationTester(rule)
        val entry = research()
        val positions = FeatureIndexSelectionParser.parse("1-108,130,201-205", 468).getOrThrow().map { it + 1 }
        restoration.setContent { MaterialTheme { Column {
            var draft by rememberSaveable { mutableStateOf("Retained arm") }
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.testTag("origin-draft"))
            FeatureDictionaryContent(entry, selectedPositions = positions)
        } } }
        rule.onNodeWithTag("dictionary-count").assertTextContains("114 matching variables / 114")
        search("#130")
        reveal("dictionary-feature-130")
        rule.onNodeWithTag("dictionary-feature-130").performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("dictionary-search").assertTextContains("#130")
        rule.onNodeWithTag("dictionary-feature-130").assertExists()
        rule.onNodeWithTag("origin-draft").assertTextContains("Retained arm")
        search("#129")
        rule.onNodeWithTag("dictionary-count").assertTextContains("0 matching variables / 114")
    }
    @Test fun allExistingExplorerOriginsReachSharedDictionaryAndReturnToDraft() {
        var origin by mutableStateOf("Experiment Design")
        rule.setContent { MaterialTheme { Column {
            var draft by rememberSaveable { mutableStateOf("Selection retained") }
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.testTag("origin-draft"))
            FeatureSetExplorer(origin, null)
        } } }
        listOf("Experiment Design", "Feature-set help", "Custom builder", "Explainable AI / Model evidence").forEach { label ->
            rule.runOnIdle { origin = label }
            rule.onNodeWithTag("feature-catalog-open").performClick()
            rule.onNodeWithTag("feature-dictionary-open").performClick()
            rule.onNodeWithText("Shared feature dictionary").assertExists()
            rule.onNodeWithTag("feature-catalog-return").performClick()
            rule.onNodeWithTag("origin-draft").assertTextContains("Selection retained")
        }
    }
}
