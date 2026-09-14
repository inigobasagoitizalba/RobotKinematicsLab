package com.robotkinematicslab.mobile.ui.help

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val JARGON_QUICK_INFO_TAG = "jargon-quick-info"
const val JARGON_DETAIL_DIALOG_TAG = "jargon-detail-dialog"

/** Disable inline styling while a publication figure is being captured. */
val LocalJargonHelpEnabled = compositionLocalOf { true }

/** A compact, reusable legend that makes the inline-help interaction discoverable to newcomers. */
@Composable
fun JargonHelpNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("jargon-help-notice"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
    ) {
        Text(
            text = "ⓘ New here? Bold underlined terms are explained. Tap one for a plain-language meaning; press and hold for why it matters.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
fun WithoutJargonDecoration(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalJargonHelpEnabled provides false, content = content)
}

/**
 * Drop-in text for explanatory surfaces. Technical terms are bold, underlined and colour accented,
 * so they remain discoverable without relying on colour alone.
 *
 * A tap on a term opens its short definition. A double tap or press-and-hold opens the full
 * explanation. Accessibility services get explicit quick-help and detailed-help actions for every
 * term because their own double-tap gesture is reserved for activation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JargonAwareText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = TextStyle.Default
) {
    val enabled = LocalJargonHelpEnabled.current
    val matches = remember(text, enabled) {
        if (enabled) JargonCatalog.findMatches(text) else emptyList()
    }
    if (matches.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            style = style
        )
        return
    }

    val colors = MaterialTheme.colorScheme
    val resolvedTextColor = color.takeOrElse { colors.onSurface }
    val annotated = remember(text, matches, colors.primary, colors.primaryContainer) {
        jargonAnnotatedString(
            text = text,
            matches = matches,
            accent = colors.primary,
            accentBackground = colors.primaryContainer.copy(alpha = 0.28f)
        )
    }
    var textLayout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    var quickDefinition by remember(text) { mutableStateOf<JargonDefinition?>(null) }
    var detailedDefinition by remember(text) { mutableStateOf<JargonDefinition?>(null) }
    val uniqueDefinitions = remember(matches) { matches.map(JargonMatch::definition).distinctBy(JargonDefinition::id) }
    val firstDefinition = uniqueDefinitions.first()

    Box(
        modifier =
            modifier
                .testTag("jargon-text:${firstDefinition.id}")
                // Keep the displayed text on the same merged semantic node as the caller's
                // test tag/content description. This preserves existing UI contracts while
                // adding the two help actions.
                .semantics(mergeDescendants = true) {
                    onClick(label = "Explain ${firstDefinition.term}") {
                        quickDefinition = firstDefinition
                        true
                    }
                    customActions =
                        uniqueDefinitions.flatMap { definition ->
                            listOf(
                                CustomAccessibilityAction(
                                    label = "Quick definition of ${definition.term}",
                                    action = {
                                        detailedDefinition = null
                                        quickDefinition = definition
                                        true
                                    }
                                ),
                                CustomAccessibilityAction(
                                    label = "More information about ${definition.term}",
                                    action = {
                                        quickDefinition = null
                                        detailedDefinition = definition
                                        true
                                    }
                                )
                            )
                        }
                }
    ) {
        Text(
            text = annotated,
            modifier =
                Modifier.pointerInput(text, matches) {
                    detectTapGestures(
                        onTap = { position ->
                            quickDefinition = textLayout.definitionAt(position, matches)
                        },
                        onDoubleTap = { position ->
                            val selected = textLayout.definitionAt(position, matches)
                            if (selected != null) {
                                quickDefinition = null
                                detailedDefinition = selected
                            }
                        },
                        onLongPress = { position ->
                            val selected = textLayout.definitionAt(position, matches)
                            if (selected != null) {
                                quickDefinition = null
                                detailedDefinition = selected
                            }
                        }
                    )
                },
            color = resolvedTextColor,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            style = style,
            onTextLayout = { textLayout = it }
        )

        DropdownMenu(
            expanded = quickDefinition != null,
            onDismissRequest = { quickDefinition = null },
            modifier =
                Modifier
                    .testTag(JARGON_QUICK_INFO_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            quickDefinition?.let { definition ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.88f)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TECHNICAL TERM",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = definition.term,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = definition.plainMeaning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            quickDefinition = null
                            detailedDefinition = definition
                        }
                    ) {
                        Text("More information")
                    }
                }
            }
        }
    }

    JargonDetailDialog(
        definition = detailedDefinition,
        onDismiss = { detailedDefinition = null }
    )
}

@Composable
private fun JargonDetailDialog(
    definition: JargonDefinition?,
    onDismiss: () -> Unit
) {
    if (definition == null) return

    AlertDialog(
        modifier = Modifier.testTag(JARGON_DETAIL_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Technical term",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(definition.term)
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JargonDetailSection("In plain language", definition.plainMeaning)
                JargonDetailSection("Why it matters here", definition.whyItMatters)
                Text(
                    text = "Tip: a bold, underlined accent marks terms with contextual help. Tap once for a short definition; double tap, press and hold, or use More information for this full explanation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
private fun JargonDetailSection(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun jargonAnnotatedString(
    text: String,
    matches: List<JargonMatch>,
    accent: Color,
    accentBackground: Color
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        matches.forEach { match ->
            addStyle(
                style =
                    SpanStyle(
                        color = accent,
                        background = accentBackground,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ),
                start = match.start,
                end = match.endExclusive
            )
        }
    }

private fun TextLayoutResult?.definitionAt(
    position: androidx.compose.ui.geometry.Offset,
    matches: List<JargonMatch>
): JargonDefinition? {
    val offset = this?.getOffsetForPosition(position) ?: return null
    return matches.firstOrNull { offset >= it.start && offset < it.endExclusive }?.definition
}
