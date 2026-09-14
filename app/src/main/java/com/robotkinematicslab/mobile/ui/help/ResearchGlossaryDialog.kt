package com.robotkinematicslab.mobile.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ResearchGlossaryDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val entries =
        remember(normalizedQuery) {
            if (normalizedQuery.isBlank()) {
                ResearchGlossaryCatalog.entries
            } else {
                ResearchGlossaryCatalog.entries.filter { entry ->
                    listOf(entry.term, entry.plainMeaning, entry.whyItMatters)
                        .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
                }
            }
        }

    AlertDialog(
        modifier = Modifier.testTag("research-glossary-dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Research glossary") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Plain-language definitions for terms used across robots, diagnostics, datasets and AI.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth().testTag("research-glossary-search"),
                    label = { Text("Search terms") },
                    singleLine = true
                )
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matching term. Try a shorter scientific keyword.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = ResearchGlossaryEntry::term) { entry ->
                            GlossaryEntryCard(entry)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GlossaryEntryCard(entry: ResearchGlossaryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                entry.term,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(entry.plainMeaning, style = MaterialTheme.typography.bodySmall)
            Text(
                "Why it matters: ${entry.whyItMatters}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
