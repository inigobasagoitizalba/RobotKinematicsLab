package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactSelectionMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallChoiceSetKeepsEveryOptionVisibleWithoutAHorizontalCarousel() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    CompactSelectionMenu(
                        options = listOf("Create", "Robots", "Saved", "Quality", "Continuous"),
                        selected = "Create",
                        label = { it },
                        onSelected = {},
                        modifier = Modifier.testTag("compact-menu"),
                        optionTestTag = { "compact-option-$it" }
                    )
                }
            }
        }

        val labels = listOf("Create", "Robots", "Saved", "Quality", "Continuous")
        labels.forEach { label ->
            composeRule.onNodeWithTag("compact-option-$label").assertIsDisplayed()
        }

        val menuBounds = composeRule.onNodeWithTag("compact-menu").fetchSemanticsNode().boundsInRoot
        val optionRows =
            labels
                .map { label ->
                    composeRule.onNodeWithTag("compact-option-$label").fetchSemanticsNode().boundsInRoot
                }
                .groupBy { bounds -> bounds.top.roundToInt() }

        optionRows.values.forEach { row ->
            val rowCenter = (row.minOf { it.left } + row.maxOf { it.right }) / 2f
            assertTrue(
                "Every wrapped option row should be horizontally centred.",
                abs(rowCenter - menuBounds.center.x) <= 2f
            )
        }
    }

    @Test
    fun largeChoiceSetKeepsEveryOptionVisibleAndSelectsDirectly() {
        val selected = mutableStateOf("One")
        val options = listOf("One", "Two", "Three", "Four", "Five", "Six")

        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    CompactSelectionMenu(
                        options = options,
                        selected = selected.value,
                        label = { it },
                        onSelected = { selected.value = it },
                        optionTestTag = { "compact-option-$it" }
                    )
                }
            }
        }

        options.forEach { option ->
            composeRule.onNodeWithTag("compact-option-$option").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("compact-option-Six").performClick().assertIsSelected()
    }
}
