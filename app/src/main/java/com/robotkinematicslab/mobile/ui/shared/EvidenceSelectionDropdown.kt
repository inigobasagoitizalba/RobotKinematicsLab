package com.robotkinematicslab.mobile.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Uniform compact selection for persisted evidence, shared by Compare and Explainable AI. */
@Composable
fun <T> EvidenceSelectionDropdown(title:String,items:List<T>,selected:T?,label:(T)->String,onSelected:(T)->Unit,
    modifier:Modifier=Modifier,enabled:Boolean=true,testTag:String=title) {
    var expanded by rememberSaveable(testTag) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(title,style=MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={ expanded=true },enabled=enabled && items.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag(testTag)) {
                Text(selected?.let(label) ?: "Choose $title")
            }
            DropdownMenu(expanded=expanded && enabled,onDismissRequest={ expanded=false }) {
                items.forEach { item -> DropdownMenuItem(text={ Text(label(item)) },onClick={ onSelected(item);expanded=false }) }
            }
        }
    }
}
