package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.robotkinematicslab.mobile.ml.data.*
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun FeatureExperimentArchiveControls(origin: String, domain: FeatureSetDomain, configurations: List<ArchivedFeatureConfiguration>, enabled: Boolean, onRestore: (List<ArchivedFeatureConfiguration>) -> Unit) {
    val context = LocalContext.current
    val repository = remember(origin, domain) {
        val root = ResearchProjectRepository.resolveActiveProjectRoot(context)
        val key = origin.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        FeatureExperimentArchive(File(root, "feature_experiments/$key-${domain.name}.fexp"), domain)
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column {
        OutlinedButton(enabled = enabled && !busy && configurations.isNotEmpty(), onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.save(configurations) } }
                message = result.fold({ "Configuration plan saved with versioned, ordered variables." }, { it.message })
                busy = false
            }
        }) { Text("Save configurations") }
        OutlinedButton(enabled = enabled && !busy, onClick = {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.load() } }
                result.onSuccess(onRestore)
                message = result.fold({ "Saved configurations reopened. No training was started." }, { it.message })
                busy = false
            }
        }) { Text("Reopen configurations") }
    }
    message?.let { Text(it) }
}
