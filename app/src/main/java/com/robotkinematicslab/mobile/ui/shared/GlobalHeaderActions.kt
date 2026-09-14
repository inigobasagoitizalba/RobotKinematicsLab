package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val GLOBAL_HEADER_ACTIONS_TAG = "global-header-actions"
const val GLOBAL_HEADER_LOCAL_TAG = "global-header-local"

internal enum class GlobalHeaderAction {
    LOCAL,
    SETTINGS
}

internal fun globalHeaderActionOrder(
    hasSettings: Boolean
): List<GlobalHeaderAction> =
    listOfNotNull(
        GlobalHeaderAction.LOCAL,
        GlobalHeaderAction.SETTINGS.takeIf { hasSettings }
    )

/** The single app-wide contract for the local-processing badge and Settings action. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun GlobalHeaderActions(
    onOpenSettings: (() -> Unit)?,
    settingsSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier.testTag(GLOBAL_HEADER_ACTIONS_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 3
    ) {
        globalHeaderActionOrder(hasSettings = onOpenSettings != null).forEach { action ->
            when (action) {
                GlobalHeaderAction.LOCAL ->
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.CenterVertically)
                                .testTag(GLOBAL_HEADER_LOCAL_TAG),
                        shape = RoundedCornerShape(999.dp),
                        color = colors.tertiaryContainer,
                        border = BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.22f))
                    ) {
                        Text(
                            text = "LOCAL",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onTertiaryContainer
                        )
                    }

                GlobalHeaderAction.SETTINGS ->
                    GlobalSettingsShortcut(
                        onOpenSettings = requireNotNull(onOpenSettings),
                        selected = settingsSelected,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
            }
        }
    }
}
