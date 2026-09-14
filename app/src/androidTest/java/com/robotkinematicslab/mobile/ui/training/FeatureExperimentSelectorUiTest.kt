package com.robotkinematicslab.mobile.ui.training

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeatureExperimentSelectorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun campaignDoesNotReplaceCurrentArmsUntilExactPreviewIsConfirmed() {
        val original = listOf(FeatureSelectionSpec.complete(TrainingFeatureProfile.BASELINE_KINEMATICS))
        val applied = mutableStateOf(original)
        composeRule.setContent {
            MaterialTheme {
                FeatureExperimentSelector(
                    selections = applied.value,
                    enabled = true,
                    onSelectionsChange = { applied.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Use complete 108 → 383 growth campaign").performClick()
        composeRule.runOnIdle { assertEquals(original, applied.value) }
        composeRule.onNodeWithText("108 → 383 cumulative growth").assertIsDisplayed()
        composeRule.onNodeWithText("8 nested same-order arms: 108, 130, 152, 169, 191, 209, 233, 383")
            .performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(original, applied.value) }

        composeRule.onNodeWithTag("confirm-feature-campaign").performClick()

        composeRule.runOnIdle {
            assertEquals(CUMULATIVE_FEATURE_COUNTS, applied.value.map(FeatureSelectionSpec::featureCount))
        }
        composeRule.onNodeWithText("108 → 383 cumulative growth").assertDoesNotExist()
    }

    @Test
    fun dismissingPreviewPreservesCurrentArms() {
        val original = listOf(FeatureSelectionSpec.complete(TrainingFeatureProfile.CONTEXT_ENHANCED))
        val applied = mutableStateOf(original)
        composeRule.setContent {
            MaterialTheme {
                FeatureExperimentSelector(
                    selections = applied.value,
                    enabled = true,
                    onSelectionsChange = { applied.value = it }
                )
            }
        }

        composeRule.onNodeWithText("Use paired feature-family ablation campaign").performClick()
        composeRule.onNodeWithText("Keep current arms").performClick()

        composeRule.runOnIdle { assertEquals(original, applied.value) }
    }
}
