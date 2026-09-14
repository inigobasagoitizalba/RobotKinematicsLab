package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Returns the newest items while collapsed, plus every selected item outside that window.
 * Collapsing a long collection therefore never makes the user's current selection disappear.
 */
internal fun <T> visibleSelectionItems(
    items: List<T>,
    showAll: Boolean,
    initialVisibleCount: Int,
    isSelected: (T) -> Boolean
): List<T> {
    require(initialVisibleCount > 0) { "initialVisibleCount must be positive" }
    if (showAll || items.size <= initialVisibleCount) return items
    return items.take(initialVisibleCount) + items.drop(initialVisibleCount).filter(isSelected)
}

/** A compact-by-default collection that always offers an explicit path to every saved item. */
@Composable
fun <T> ExpandableSelectionCollection(
    items: List<T>,
    initialVisibleCount: Int,
    itemName: String,
    isSelected: (T) -> Boolean,
    modifier: Modifier = Modifier,
    toggleTestTag: String? = null,
    onCollapseParent: (() -> Unit)? = null,
    showAllOverride: Boolean? = null,
    onShowAllChange: ((Boolean) -> Unit)? = null,
    itemKey: ((T) -> Any)? = null,
    collapseLabel: String = "Collapse $itemName",
    itemContent: @Composable (T) -> Unit
) {
    var localShowAll by rememberSaveable(itemName, initialVisibleCount) { mutableStateOf(false) }
    val showAll = showAllOverride ?: localShowAll
    val visible = visibleSelectionItems(items, showAll, initialVisibleCount, isSelected)
    val hiddenCount = (items.size - visible.size).coerceAtLeast(0)
    val selectedOutsideInitialWindow =
        !showAll && items.drop(initialVisibleCount).any(isSelected)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { item ->
            key(itemKey?.invoke(item) ?: item) { itemContent(item) }
        }
        if (items.size > initialVisibleCount) {
            Text(
                text =
                    when {
                        showAll -> "Showing all ${items.size} $itemName."
                        selectedOutsideInitialWindow ->
                            "$hiddenCount older $itemName hidden. The current selection remains visible and is never cleared by collapsing this list."
                        else ->
                            "$hiddenCount older $itemName hidden. Show all to inspect every saved item."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    if (onShowAllChange != null) onShowAllChange(!showAll)
                    else localShowAll = !showAll
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(toggleTestTag?.let { Modifier.testTag(it) } ?: Modifier)
            ) {
                Text(
                    if (showAll) {
                        "Show newest $initialVisibleCount"
                    } else {
                        "Show all ${items.size} $itemName"
                    }
                )
            }
            if (onCollapseParent != null) {
                OutlinedButton(
                    onClick = onCollapseParent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(collapseLabel)
                }
            }
        }
    }
}

/** Full-width selected/unselected action with an explicit radio-selection accessibility contract. */
@Composable
fun AccessibleSelectionButton(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    AdaptiveSelectionTile(
        selected = selected,
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        maxLines = maxLines
    )
}

/** Announces asynchronous status and validation changes without interrupting the current task. */
fun Modifier.politeLiveRegion(): Modifier =
    semantics { liveRegion = LiveRegionMode.Polite }
