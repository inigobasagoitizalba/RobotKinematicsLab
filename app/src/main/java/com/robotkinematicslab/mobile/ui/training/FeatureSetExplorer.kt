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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Read-only overlay: the origin stays composed, retaining its draft and scroll position. */
@Composable
fun FeatureSetExplorer(
    originLabel: String,
    sourceCsvPath: String?,
    domain: FeatureSetDomain = FeatureSetDomain.CLASSIFICATION
) {
    var stage by rememberSaveable { mutableStateOf("closed") }
    var entryId by rememberSaveable { mutableStateOf("") }
    var capturedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedOrigin by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var row by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val catalogue = remember { FeatureSetCatalog.entries() }
    val entry = catalogue.firstOrNull { it.technicalId == entryId }
    val catalogScroll = rememberLazyListState()
    val detailScroll = rememberLazyListState()
    var preview by remember { mutableStateOf<ScientificCsvPreview?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stage, capturedPath) {
        if (stage == "csv") {
            preview = null
            failure = null
            val result = withContext(Dispatchers.IO) {
                runCatching { ScientificCsvPreviewReader.read(File(requireNotNull(capturedPath)), 20) }
            }
            result.onSuccess { preview = it }.onFailure { failure = it.message ?: "Source CSV cannot be read." }
        }
    }
    OutlinedButton(onClick = {
        capturedOrigin = originLabel
        capturedPath = sourceCsvPath
        stage = "catalog"
    }, modifier = Modifier.fillMaxWidth().testTag("feature-catalog-open")) { Text("View all feature sets") }
    if (stage == "closed") return
    if (stage == "dictionary" || stage == "variables") {
        val dictionaryEntry = entry ?: catalogue.first {
            it.domain == domain && it.featureCount == if (domain == FeatureSetDomain.CLASSIFICATION) 468 else 361
        }
        AlertDialog(onDismissRequest = { stage = "closed" }, title = { Text("Shared feature dictionary") },
            text = { FeatureDictionaryContent(dictionaryEntry) },
            confirmButton = { TextButton(onClick = { stage = "closed" }, modifier = Modifier.testTag("feature-catalog-return")) { Text("Back to $capturedOrigin") } },
            dismissButton = { TextButton(onClick = { stage = if (entry == null) "catalog" else "explain" }) { Text("Back to feature sets") } })
        return
    }
    if (stage == "confirm") {
        AlertDialog(onDismissRequest = { stage = "explain" }, title = { Text("Open source CSV?") },
            text = { Text("Read up to 20 rows from the shared scientific CSV. Feature sets are derived contracts, not separate CSV files.\n${capturedPath.orEmpty()}") },
            confirmButton = { TextButton(onClick = { stage = "csv" }, modifier = Modifier.testTag("confirm-source-csv")) { Text("Open CSV preview") } },
            dismissButton = { TextButton(onClick = { stage = "explain" }) { Text("Cancel") } })
        return
    }
    AlertDialog(
        onDismissRequest = { stage = "closed" },
        title = { Text(when(stage) { "catalog" -> "Registered feature sets"; "csv" -> "Source CSV preview"; "variables" -> "Ordered variables"; else -> entry?.displayName.orEmpty() }) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp).fillMaxWidth().testTag("feature-inspector-list"),
                state = if(stage == "catalog") catalogScroll else detailScroll,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Origin: $capturedOrigin", style = MaterialTheme.typography.labelSmall) }
                when(stage) {
                    "catalog" -> {
                        item {
                            TextButton(onClick = { entryId = ""; stage = "dictionary" }, modifier = Modifier.testTag("feature-dictionary-open")) { Text("Browse shared feature dictionary") }
                            OutlinedTextField(query, { query = it }, label = { Text("Find a feature set") })
                        }
                        items(catalogue.filter { (it.displayName + it.technicalId).contains(query, true) }, key = { it.technicalId }) { candidate ->
                            Text(candidate.displayName.removeSuffix(" · ${candidate.featureCount} variables").removeSuffix(" · ${candidate.featureCount}"))
                            Text("${candidate.featureCount} variables • ${candidate.domain.displayName}")
                            if(candidate.domain != domain) Text("Available for inspection; selection belongs to ${candidate.domain.displayName}.")
                            TextButton(onClick = { entryId = candidate.technicalId; stage = "explain" }) { Text("Explain ${candidate.technicalId}") }
                        }
                    }
                    "csv" -> {
                        item { Text("Up to 20 rows; 40 source columns per page. Values retain original text precision. Units are not declared by this CSV header; no units are inferred.") }
                        failure?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("csv-preview-error")) } }
                        if(preview == null && failure == null) item { Text("Reading source CSV…") }
                        preview?.let { data ->
                            item {
                                Text("${data.rows.size} preview rows; ${data.columns.size} columns${if(data.hasMoreRows) "; additional rows omitted" else ""}.")
                                Text(data.sourcePath)
                                if(data.rows.isNotEmpty()) {
                                    Text("Row ${row.coerceAtMost(data.rows.lastIndex) + 1}")
                                    Row { TextButton(onClick = { row = (row - 1).coerceAtLeast(0) }, enabled = row > 0) { Text("Previous row") }
                                        TextButton(onClick = { row++ }, enabled = row < data.rows.lastIndex) { Text("Next row") } }
                                }
                                Row { TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous columns") }
                                    TextButton(onClick = { page++ }, enabled = (page + 1) * 40 < data.columns.size) { Text("Next columns") } }
                            }
                            items(data.columns.withIndex().drop(page * 40).take(40)) { column ->
                                Text("${column.index + 1}. ${column.value}")
                                Text(data.rows.getOrNull(row)?.get(column.index) ?: "No data row", modifier = Modifier.testTag("csv-cell-${column.index}"))
                            }
                        }
                    }
                    else -> entry?.let { selected ->
                        item {
                            Text(selected.description)
                            Text("${selected.featureCount} unique variables. Source contract: ${selected.sourceContract}. ID: ${selected.technicalId}")
                            Text(selected.comparisonPurpose)
                            Text("More derived variables add computation and memory cost; predictive improvement must be measured in a controlled comparison.")
                            Text("Derived from the shared scientific CSV. There is no dedicated CSV for this feature set. The catalogue describes registered contracts, not the historical schema of a saved model.")
                            TextButton(onClick = { stage = "variables" }) { Text("View variables") }
                            TextButton(onClick = { row = 0; page = 0; stage = "confirm" }, enabled = capturedPath != null) { Text("View source CSV") }
                            if(capturedPath == null) Text("Source CSV unavailable: select or generate a dataset at the origin first.")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { stage = "closed" }, modifier = Modifier.testTag("feature-catalog-return")) { Text("Back to $capturedOrigin") } },
        dismissButton = { if(stage != "catalog") TextButton(onClick = { stage = "catalog" }) { Text("Back to feature sets") } }
    )
}
