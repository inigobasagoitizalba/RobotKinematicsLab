package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.robotkinematicslab.mobile.ui.theme.RobotKinematicsLabTheme
import org.junit.Rule
import org.junit.Test

class AccessibleCollectionsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun olderSelectionRemainsVisibleAndShowAllRevealsEverySavedItem() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                ExpandableSelectionCollection(
                    items = (1..6).toList(),
                    initialVisibleCount = 3,
                    itemName = "saved runs",
                    isSelected = { it == 6 },
                    toggleTestTag = "show-all"
                ) { item -> Text("Run $item") }
            }
        }

        composeRule.onNodeWithText("Run 6").assertIsDisplayed()
        composeRule.onNodeWithText("Run 4").assertDoesNotExist()
        composeRule
            .onNodeWithText("current selection remains visible", substring = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("show-all").performClick()

        composeRule.onNodeWithText("Run 4").assertIsDisplayed()
        composeRule.onNodeWithText("Showing all 6 saved runs.").assertIsDisplayed()
    }

    @Test
    fun selectionAndDynamicStatusExposeAccessibilitySemantics() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                Column {
                    AccessibleSelectionButton(
                        selected = true,
                        label = "Selected run",
                        enabled = true,
                        onClick = {}
                    )
                    Text(
                        "Training failed safely.",
                        Modifier.politeLiveRegion().testTag("status")
                    )
                }
            }
        }

        composeRule.onNodeWithText("Selected run").assertIsSelected()
        composeRule.onNodeWithTag("status").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Polite
            )
        )
    }

    @Test
    fun listScopeAndParentExpansionRemainIndependentAndLongListCanCollapseAtItsEnd() {
        composeRule.setContent {
            RobotKinematicsLabTheme {
                var parentExpanded by remember { mutableStateOf(true) }
                if (parentExpanded) {
                    ExpandableSelectionCollection(
                        items = (1..13).toList(),
                        initialVisibleCount = 8,
                        itemName = "saved workspace studies",
                        isSelected = { false },
                        toggleTestTag = "workspace-scope",
                        onCollapseParent = { parentExpanded = false }
                    ) { item -> Text("Study $item") }
                } else {
                    Text("Saved studies collapsed")
                }
            }
        }

        composeRule.onNodeWithText("Study 9").assertDoesNotExist()
        composeRule.onNodeWithTag("workspace-scope").performClick()
        composeRule.onNodeWithText("Study 13").assertIsDisplayed()
        composeRule.onNodeWithText("Collapse saved workspace studies").performClick()
        composeRule.onNodeWithText("Saved studies collapsed").assertIsDisplayed()
    }
}
