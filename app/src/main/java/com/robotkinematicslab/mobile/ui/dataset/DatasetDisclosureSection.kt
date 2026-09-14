package com.robotkinematicslab.mobile.ui.dataset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.shared.disclosureStateDescription

@Composable
internal fun DatasetDisclosureSection(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    expandRequest: Int = 0,
    testTag: String? = null,
    content: @Composable () -> Unit
) {
    AppDisclosureSection(
        title = title,
        summary = summary,
        modifier = modifier,
        initiallyExpanded = initiallyExpanded,
        expandRequest = expandRequest,
        testTag = testTag,
        content = { content() }
    )
}

internal fun nextDatasetDisclosureState(expanded: Boolean): Boolean = !expanded

internal fun datasetDisclosureStateDescription(expanded: Boolean): String =
    disclosureStateDescription(expanded)
