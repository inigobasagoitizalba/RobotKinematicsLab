package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A compact selection surface whose label wraps instead of silently becoming an ellipsis. */
@Composable
fun AdaptiveSelectionTile(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 2
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .semantics { this.selected = selected }
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .defaultMinSize(minHeight = 48.dp)
                .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) colors.primary else colors.surface,
            contentColor = if (selected) colors.onPrimary else colors.onSurface,
            border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
            shadowElevation = if (selected) 2.dp else 0.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                softWrap = maxLines > 1
            )
        }
    }
}

/** Compact, non-scrolling selector that keeps every option visible in one wrapping pill cloud. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CompactSelectionMenu(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionTestTag: ((T) -> String?)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            options.forEach { option ->
                AdaptiveSelectionTile(
                    selected = option == selected,
                    label = label(option),
                    onClick = {
                        if (option != selected) onSelected(option)
                    },
                    enabled = enabled,
                    modifier =
                        optionTestTag
                            ?.invoke(option)
                            ?.let { Modifier.testTag(it) }
                            ?: Modifier
                )
            }
        }
    }
}

/** Source-compatible overload for callers migrated from the former dropdown policy. */
@Composable
fun <T> CompactSelectionMenu(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dropdownThreshold: Int,
    optionTestTag: ((T) -> String?)? = null
) {
    CompactSelectionMenu(
        options = options,
        selected = selected,
        label = label,
        onSelected = onSelected,
        modifier = modifier,
        enabled = enabled,
        optionTestTag = optionTestTag
    )
}

/** Equal-width version for short groups such as two-way FK/IK choices. */
@Composable
fun RowScope.WeightedSelectionTile(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AdaptiveSelectionTile(
        selected = selected,
        label = label,
        onClick = onClick,
        modifier = modifier.weight(1f),
        enabled = enabled
    )
}
