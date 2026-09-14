package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared disclosure geometry and accessibility contract for scientific configuration/results. */
@Composable
fun AppDisclosureSection(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    expandRequest: Int = 0,
    testTag: String? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val stableKey = testTag ?: title
    var expanded by rememberSaveable(stableKey) { mutableStateOf(initiallyExpanded) }

    LaunchedEffect(expandRequest) {
        if (expandRequest > 0) expanded = true
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppDisclosureHeader(
            title = title,
            summary = summary,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            testTag = testTag,
            containerColor = containerColor
        )
        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .then(
                            testTag?.let { Modifier.testTag("$it-content") } ?: Modifier
                        ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

/** Stateless form used where a parent owns expansion, such as collapse/expand-all groups. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AppDisclosureHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    containerColor: Color? = null
) {
    val toggle = { onExpandedChange(!expanded) }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .combinedClickable(
                    role = Role.Button,
                    onClick = toggle,
                    onDoubleClick = toggle
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = title
                    stateDescription = disclosureStateDescription(expanded)
                },
        shape = MaterialTheme.shapes.medium,
        color = containerColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun disclosureStateDescription(expanded: Boolean): String =
    if (expanded) "Expanded" else "Collapsed"
