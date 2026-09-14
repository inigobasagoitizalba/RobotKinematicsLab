package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection

@Composable
internal fun TrainingDisclosureSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    AppDisclosureSection(
        title = title,
        summary = subtitle,
        modifier = modifier,
        initiallyExpanded = initiallyExpanded,
        testTag = trainingDisclosureTag(title),
        content = content
    )
}

internal fun trainingDisclosureTag(title: String): String =
    "training-disclosure-" +
        title
            .lowercase()
            .map { character -> if (character.isLetterOrDigit()) character else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
