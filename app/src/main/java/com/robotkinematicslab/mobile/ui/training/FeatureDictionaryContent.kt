package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ml.data.*

/** The shared registry owns all definitions and order; this component only navigates them. */
@Composable
internal fun FeatureDictionaryContent(
    initialEntry: RegisteredFeatureSet,
    modifier: Modifier = Modifier,
    selectedPositions: List<Int>? = null
) {
    val entries = remember { FeatureSetCatalog.entries() }
    var selectedId by rememberSaveable(initialEntry.technicalId) { mutableStateOf(initialEntry.technicalId) }
    val entry = entries.firstOrNull { it.technicalId == selectedId } ?: initialEntry
    val definitions = remember(entry) { FeatureSetCatalog.definitions(entry) }
    var query by rememberSaveable(initialEntry.technicalId) { mutableStateOf("") }
    var range by rememberSaveable(initialEntry.technicalId) { mutableStateOf("") }
    var family by rememberSaveable(initialEntry.technicalId) { mutableStateOf("All families") }
    var expandedId by rememberSaveable(initialEntry.technicalId) { mutableStateOf<String?>(null) }
    val scroll = rememberLazyListState()
    val rangeResult = remember(range, definitions.size) {
        if (range.isBlank()) null else FeatureIndexSelectionParser.parse(range.replace('–', '-'), definitions.size)
    }
    val allowedRange = rangeResult?.getOrNull()?.map { it + 1 }?.toSet()
    val selected = selectedPositions?.toSet()
    val indexQuery = query.trim().removePrefix("#").toIntOrNull()
    val visible = definitions.filter { feature ->
        (selected == null || feature.index in selected) &&
            (rangeResult == null || (allowedRange != null && feature.index in allowedRange)) &&
            (family == "All families" || feature.family == family) &&
            (query.isBlank() || if (indexQuery != null) feature.index == indexQuery else
                feature.technicalId.contains(query.trim(), true) || feature.humanName.contains(query.trim(), true))
    }.let { filtered ->
        // A custom model can request non-monotonic input order: preserve the parser's first occurrence order.
        if (selectedPositions == null) filtered else selectedPositions.mapNotNull { position -> filtered.firstOrNull { it.index == position } }
    }
    LazyColumn(modifier.heightIn(max = 480.dp).fillMaxWidth().testTag("feature-dictionary-list"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("${entry.displayName} · ${entry.domain.displayName}")
            Text("Indices are one-based within ${entry.sourceContract}; they are not dataset row numbers. Verified IK uses its own index space.", style = MaterialTheme.typography.bodySmall)
            Text("Derived variables use the original scientific CSV columns; a feature index is not necessarily a direct CSV column. These are the current registered definitions, not a reconstructed historical model schema.", style = MaterialTheme.typography.bodySmall)
            if (selectedPositions == null) {
                DictionaryChoice("Feature set", entry.technicalId, entries.map { it.technicalId }, { id -> entries.first { it.technicalId == id }.displayName }, "dictionary-set") {
                    selectedId = it; range = ""; family = "All families"; expandedId = null
                }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Search name or ID; jump to #383") }, modifier = Modifier.fillMaxWidth().testTag("dictionary-search"))
            OutlinedTextField(range, { range = it }, label = { Text("Index range, e.g. 108–130") }, modifier = Modifier.fillMaxWidth().testTag("dictionary-range"), isError = rangeResult?.isFailure == true)
            rangeResult?.exceptionOrNull()?.let { Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("dictionary-range-error")) }
            DictionaryChoice("Family", family, listOf("All families") + definitions.map { it.family }.distinct(), { it }, "dictionary-family") { family = it }
            if (selectedPositions == null && entry.domain == FeatureSetDomain.CLASSIFICATION) {
                TextButton(onClick = {
                    selectedId = entries.first { it.domain == FeatureSetDomain.CLASSIFICATION && it.sourceContract == TrainingFeatureProfile.CONTEXT_EXPANDED.name && it.featureCount == 383 }.technicalId
                    range = "131-383"; query = ""; family = "All families"; expandedId = null
                }, modifier = Modifier.testTag("dictionary-expanded-additions")) { Text("Expanded additions beyond Context 130") }
            }
            Text("${visible.size} matching variables / ${selectedPositions?.size ?: definitions.size}", modifier = Modifier.testTag("dictionary-count"))
            if (visible.isEmpty()) Text("No variables match these filters.")
        }
        items(visible, key = { it.technicalId }) { feature ->
            OutlinedButton(onClick = { expandedId = if (expandedId == feature.technicalId) null else feature.technicalId }, modifier = Modifier.fillMaxWidth().testTag("dictionary-feature-${feature.index}")) {
                Text("#${feature.index} · ${feature.technicalId}\n${feature.humanName} · ${feature.family}")
            }
            if (expandedId == feature.technicalId) {
                Column(Modifier.fillMaxWidth().testTag("dictionary-detail-${feature.index}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Definition" to feature.definition, "Origin" to feature.origin, "Calculation" to feature.calculation,
                        "Units" to feature.units, "Dependencies" to feature.dependencies.joinToString(), "Scientific meaning" to feature.meaning,
                        "Possible use" to feature.possibleUse, "Limitations" to feature.limitations,
                        "Implementation source" to feature.implementationSource, "Formula version" to feature.formulaVersion).forEach { (label, value) ->
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(value.ifBlank { "Not documented by the source registry." }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryChoice(label: String, selected: String, options: List<String>, display: (String) -> String, tag: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$label: ${display(selected)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { value -> DropdownMenuItem(text = { Text(display(value)) }, onClick = { onSelect(value); open = false }, modifier = Modifier.testTag("$tag-option:$value")) }
        }
    }
}
