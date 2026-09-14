package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val GLOBAL_SETTINGS_SHORTCUT_TAG = "global-settings-shortcut"

internal fun globalSettingsShortcutEnabled(selected: Boolean): Boolean = !selected

/**
 * One predictable, accessible entry point to device-level presentation and compute settings.
 *
 * The fixed touch target remains usable when the content text scale is increased, while the
 * familiar gear avoids taking title space from narrow phone screens.
 */
@Composable
fun GlobalSettingsShortcut(
    onOpenSettings: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpenSettings,
        enabled = globalSettingsShortcutEnabled(selected),
        modifier =
            modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag(GLOBAL_SETTINGS_SHORTCUT_TAG)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription =
                        if (selected) {
                            "Settings, current screen"
                        } else {
                            "Open Settings"
                        }
                },
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "⚙",
                fontSize = 23.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
