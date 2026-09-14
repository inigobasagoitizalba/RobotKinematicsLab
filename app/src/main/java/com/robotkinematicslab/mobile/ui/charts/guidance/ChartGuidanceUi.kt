package com.robotkinematicslab.mobile.ui.charts.guidance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

const val CHART_GUIDE_DIALOG_TAG = "chart-guide-dialog"
const val CHART_GUIDE_QUICK_POPUP_TAG = "chart-guide-quick-popup"

@Composable
fun ChartTitleWithGuide(
    title: String,
    guide: ChartGuide?,
    onOpenGuide: () -> Unit,
    onOpenFigureSettings: (() -> Unit)? = null
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("chart-guidance-and-figures")
                .tutorialAnchor(TutorialTargets.ChartGuidanceAndFigures),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JargonAwareText(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (guide != null) {
            ChartGuideButton(
                title = title,
                guide = guide,
                onOpenGuide = onOpenGuide
            )
        }
        if (onOpenFigureSettings != null) {
            ChartFigureSettingsButton(
                title = title,
                onClick = onOpenFigureSettings
            )
        }
    }
}

@Composable
private fun ChartFigureSettingsButton(
    title: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .testTag("chart-figure-button:$title")
                .heightIn(min = 48.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Open chart appearance and saving options for $title."
                },
        shape = CircleShape,
        color = colors.surfaceVariant.copy(alpha = 0.72f),
        contentColor = colors.onSurfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "APPEARANCE & SAVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartGuideButton(
    title: String,
    guide: ChartGuide,
    onOpenGuide: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var quickGuideOpen by remember(title) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.TopEnd
    ) {
        Surface(
            modifier =
                Modifier
                    .testTag("chart-guide-button:$title")
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription =
                            "Reading guide for $title. Activate for a quick explanation; use the More information action for full scientific guidance."
                        customActions =
                            listOf(
                                CustomAccessibilityAction(
                                    label = "More information about $title",
                                    action = {
                                        onOpenGuide()
                                        true
                                    }
                                )
                            )
                    }
                    .combinedClickable(
                        onClick = { quickGuideOpen = true },
                        onDoubleClick = {
                            quickGuideOpen = false
                            onOpenGuide()
                        },
                        onLongClick = {
                            quickGuideOpen = false
                            onOpenGuide()
                        }
                    ),
            shape = CircleShape,
            color = colors.primaryContainer.copy(alpha = 0.74f),
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.48f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("i", fontWeight = FontWeight.Black)
                Text(
                    text = "GUIDE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
            }
        }
        DropdownMenu(
            expanded = quickGuideOpen,
            onDismissRequest = { quickGuideOpen = false },
            modifier = Modifier.testTag(CHART_GUIDE_QUICK_POPUP_TAG)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = guide.direction.badgeLabel,
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                JargonAwareText(
                    text = guide.whatItShows,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface
                )
                TextButton(
                    onClick = {
                        quickGuideOpen = false
                        onOpenGuide()
                    }
                ) {
                    Text("More information")
                }
            }
        }
    }
}

@Composable
fun ChartQuickGuide(
    guide: ChartGuide,
    expanded: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.secondaryContainer.copy(alpha = 0.34f),
        contentColor = colors.onSecondaryContainer,
        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier =
                        Modifier
                            .background(colors.secondary, CircleShape)
                            .padding(4.dp)
                )
                Text(
                    text = "HOW TO INTERPRET",
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                text = guide.direction.badgeLabel,
                color = colors.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
            LabeledGuideValue("MEASURES", guide.whatItShows)
            guide.unitOrScale?.let { LabeledGuideValue("UNIT / SCALE", it) }
            LabeledGuideValue("WHY IT MATTERS", guide.whyItMatters)
            if (expanded) {
                LabeledGuideValue("WHAT TO LOOK FOR", guide.researchQuestion)
                Text(
                    text = "Bold underlined terms: tap once for a definition, double tap for detail. GUIDE: tap for a summary, double tap or hold for the full reading guide.",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun LabeledGuideValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        JargonAwareText(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun ChartGuideDialogHost(
    title: String,
    guide: ChartGuide,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        modifier = Modifier.testTag(CHART_GUIDE_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("How to read this")
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                    shape = CircleShape
                ) {
                    Text(
                        text = guide.typeLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                GuideDetail("What it measures", guide.whatItShows)
                guide.unitOrScale?.let { GuideDetail("Unit or scale", it) }
                GuideDetail("Why it matters", guide.whyItMatters)
                GuideDetail("How to read it", guide.howToRead)
                GuideDetail("Question this graph helps answer", guide.researchQuestion)
                GuideDetail("Which direction looks better?", guide.direction.explanation)
                GuideDetail("Scientific caution", guide.caution)
                Text(
                    text =
                        if (guide.supportsDataInspector) {
                            "Tip: tap the plot itself to open its data inspector, then touch a point, cell or bar for the exact stored value."
                        } else {
                            "Tip: use the exact labels and legend beneath this compact view; this chart does not open a separate data inspector."
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
private fun GuideDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        JargonAwareText(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
