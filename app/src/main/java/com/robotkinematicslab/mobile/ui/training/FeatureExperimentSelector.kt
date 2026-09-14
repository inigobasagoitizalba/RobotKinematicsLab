package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ml.data.FeatureIndexSelectionParser
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.data.FeatureSelectionPreview
import com.robotkinematicslab.mobile.ml.data.FeatureSetCatalog
import com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader
import com.robotkinematicslab.mobile.ml.data.TrainingFeatureProfile
import com.robotkinematicslab.mobile.ml.data.previewFeatureSelection
import com.robotkinematicslab.mobile.ml.research.FeatureFamilyAnalyzer
import com.robotkinematicslab.mobile.ml.ik.OneMicronFeatureSelectionSpec
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureEncoder
import com.robotkinematicslab.mobile.ml.ik.OneMicronIkFeatureProfile
import com.robotkinematicslab.mobile.ml.data.FeatureExperimentArchive
import com.robotkinematicslab.mobile.ml.data.FeatureSetDomain
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion

internal val CUMULATIVE_FEATURE_COUNTS = FeatureSetCatalog.frozenCumulativeCounts
internal val RESEARCH_CUMULATIVE_FEATURE_COUNTS =
    FeatureSetCatalog.researchCumulativeCounts

internal enum class FeatureCampaignPreset {
    CUMULATIVE_GROWTH,
    RESEARCH_GROWTH,
    PAIRED_FAMILY_ABLATION
}

internal data class FeatureCampaignDefinition(
    val title: String,
    val configurationSummary: String,
    val scientificQuestion: String,
    val selections: List<FeatureSelectionSpec>
)

internal fun cumulativeFeatureGrowthSelections(): List<FeatureSelectionSpec> {
    val profile = TrainingFeatureProfile.CONTEXT_EXPANDED
    val names = ScientificDatasetTrainingReader.featureNames(profile)
    require(names.size >= CUMULATIVE_FEATURE_COUNTS.last()) {
        "Expanded context no longer contains the 383-variable cumulative experiment contract."
    }
    return CUMULATIVE_FEATURE_COUNTS.map { count ->
        FeatureSelectionSpec(
            id = "cumulative-$count",
            displayName = "Cumulative context · $count",
            sourceProfile = profile,
            includedFeatureNames = names.take(count)
        )
    }
}

internal fun researchFeatureGrowthSelections(): List<FeatureSelectionSpec> {
    val profile = TrainingFeatureProfile.CONTEXT_RESEARCH_V2
    val names = ScientificDatasetTrainingReader.featureNames(profile)
    require(names.size == RESEARCH_CUMULATIVE_FEATURE_COUNTS.last()) {
        "Research context must retain its versioned 468-variable contract."
    }
    return RESEARCH_CUMULATIVE_FEATURE_COUNTS.map { count ->
        FeatureSelectionSpec(
            id = "research-cumulative-$count",
            displayName = "Research growth · $count",
            sourceProfile = profile,
            includedFeatureNames = names.take(count)
        )
    }
}

internal fun featureFamilyAblationSelections(): List<FeatureSelectionSpec> {
    val profile = TrainingFeatureProfile.CONTEXT_RESEARCH_V2
    val names = ScientificDatasetTrainingReader.featureNames(profile)
    val families = names.groupBy(FeatureFamilyAnalyzer::familyOf).toSortedMap()
    val full = FeatureSelectionSpec.complete(profile).copy(id = "ablation-full-468", displayName = "Ablation control · all 468")
    return listOf(full) + families.mapNotNull { (family, familyNames) ->
        val retained = names.filterNot(familyNames.toSet()::contains)
        retained.takeIf(List<String>::isNotEmpty)?.let {
            val stem = family.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            FeatureSelectionSpec(
                id = "ablation-without-$stem".take(80),
                displayName = "Without $family · ${retained.size}",
                sourceProfile = profile,
                includedFeatureNames = retained
            )
        }
    }
}

internal fun featureCampaignDefinition(preset: FeatureCampaignPreset): FeatureCampaignDefinition =
    when (preset) {
        FeatureCampaignPreset.CUMULATIVE_GROWTH ->
            FeatureCampaignDefinition(
                title = "108 → 383 cumulative growth",
                configurationSummary = "8 nested same-order arms: ${CUMULATIVE_FEATURE_COUNTS.joinToString()}",
                scientificQuestion = "Does adding each predeclared context block improve the result under the same training comparison?",
                selections = cumulativeFeatureGrowthSelections()
            )
        FeatureCampaignPreset.RESEARCH_GROWTH ->
            FeatureCampaignDefinition(
                title = "108 → 468 research growth",
                configurationSummary = "${RESEARCH_CUMULATIVE_FEATURE_COUNTS.size} nested same-order arms ending at the versioned 468-variable contract",
                scientificQuestion = "Do the experimental v2 candidates add evidence beyond the frozen 383-variable profile?",
                selections = researchFeatureGrowthSelections()
            )
        FeatureCampaignPreset.PAIRED_FAMILY_ABLATION ->
            FeatureCampaignDefinition(
                title = "Paired feature-family ablation",
                configurationSummary = "One complete 468-variable control plus one same-seed arm for each removed feature family",
                scientificQuestion = "Which feature families change the result when each arm differs only by that omitted family?",
                selections = featureFamilyAblationSelections()
            )
    }

internal fun customFeatureSetNameError(
    proposedName: String,
    selections: List<FeatureSelectionSpec>
): String? {
    val normalized = proposedName.trim()
    return when {
        normalized.isBlank() -> "Enter a name for the feature set."
        selections.any { it.displayName.trim().equals(normalized, ignoreCase = true) } ->
            "A comparison arm already uses this name. Choose a distinct name."
        else -> null
    }
}

/** Shared control used wherever a classification feature experiment is configured. */
@Composable
fun FeatureExperimentSelector(
    selections: List<FeatureSelectionSpec>,
    enabled: Boolean,
    onSelectionsChange: (List<FeatureSelectionSpec>) -> Unit,
    modifier: Modifier = Modifier,
    originLabel: String = "Feature experiment",
    sourceCsvPath: String? = null
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var customName by rememberSaveable { mutableStateOf("Custom feature set") }
    var sourceProfile by rememberSaveable { mutableStateOf(TrainingFeatureProfile.CONTEXT_EXPANDED) }
    var positions by rememberSaveable { mutableStateOf("1-383") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingCampaign by rememberSaveable { mutableStateOf<FeatureCampaignPreset?>(null) }
    var previewOpen by rememberSaveable { mutableStateOf(false) }
    val variablePreview = if (previewOpen) previewFeatureSelection(sourceProfile, positions).getOrNull() else null

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Hide feature experiment builder" else "Choose and compare feature sets (${selections.size})")
        }
        FeatureSetExplorer(originLabel, sourceCsvPath)
        FeatureExperimentArchiveControls(originLabel, FeatureSetDomain.CLASSIFICATION, selections.map { FeatureExperimentArchive.classification(it) }, enabled) { restored ->
            onSelectionsChange(restored.map { FeatureSelectionSpec(it.id, it.name, TrainingFeatureProfile.valueOf(it.source), it.selectedNames) })
        }
        if (!expanded) return@Column

        Text("Selected comparison arms", style = MaterialTheme.typography.titleSmall)
        if (selections.isEmpty()) {
            Text("Add at least one feature set.", color = MaterialTheme.colorScheme.error)
        }
        selections.forEach { selection ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selection.displayName.removeSuffix(" · ${selection.featureCount}").removeSuffix(" · ${selection.featureCount} variables")} · ${selection.featureCount} variables", modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onSelectionsChange(selections.filterNot { it.id == selection.id }) },
                    enabled = enabled
                ) { Text("Remove") }
            }
        }

        Text("Quick presets", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = { pendingCampaign = FeatureCampaignPreset.CUMULATIVE_GROWTH },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Use complete 108 → 383 growth campaign") }
        Text(
            "Adds the eight nested contracts: 108, 130, 152, 169, 191, 209, 233 and 383 variables.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { pendingCampaign = FeatureCampaignPreset.RESEARCH_GROWTH },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Use complete 108 → 468 research campaign") }
        Text(
            "Keeps the frozen eight points, then adds the 85 v2 candidates in ten-variable steps and a final 468 point.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { pendingCampaign = FeatureCampaignPreset.PAIRED_FAMILY_ABLATION },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Use paired feature-family ablation campaign") }
        Text(
            "Trains the complete 468-variable control and one same-seed arm with each scientific family removed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!enabled) {
            Text(
                "Feature comparisons are locked while the current scientific operation is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TrainingFeatureProfile.entries.forEach { profile ->
            val preset = FeatureSelectionSpec.complete(profile)
            OutlinedButton(
                onClick = {
                    if (selections.none { it.id == preset.id }) onSelectionsChange(selections + preset)
                },
                enabled = enabled && selections.none { it.id == preset.id },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add ${preset.displayName}") }
            Text(
                profile.scientificDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("Arbitrary subset", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use one-based positions and ranges, for example 1-108,130,201-205. The parser preserves the written order; repeated positions are included once at their first occurrence. The exact variable names are stored with the model.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(customName, { customName = it }, label = { Text("Feature-set name") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        CompactSelectionMenu(options = TrainingFeatureProfile.entries, selected = sourceProfile,
            label = { "${it.displayName} (${ScientificDatasetTrainingReader.featureNames(it).size})" },
            onSelected = { sourceProfile = it; positions = "1-${ScientificDatasetTrainingReader.featureNames(it).size}"; error = null }, enabled = enabled)
        OutlinedTextField(positions, { positions = it }, label = { Text("Variables to include") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.politeLiveRegion().testTag("feature-experiment-status")
            )
        }
        OutlinedButton(
            onClick = {
                previewFeatureSelection(sourceProfile, positions)
                    .onSuccess {
                        previewOpen = true
                        error = null
                    }
                    .onFailure { error = it.message }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Preview selected variables")
        }
        Button(
            onClick = {
                val nameError = customFeatureSetNameError(customName, selections)
                if (nameError != null) {
                    error = nameError
                } else {
                    previewFeatureSelection(sourceProfile, positions)
                        .onSuccess { preview ->
                            if (selections.any { it.sourceProfile == sourceProfile && it.includedFeatureNames == preview.featureNames }) {
                                error = "This exact ordered configuration is already selected."
                                return@onSuccess
                            }
                            val stem = customName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(68).ifBlank { "custom" }
                            var id = stem
                            var suffix = 2
                            while (selections.any { it.id == id }) id = "$stem-${suffix++}"
                            onSelectionsChange(
                                selections + FeatureSelectionSpec(id, customName.trim(), sourceProfile, preview.featureNames)
                            )
                            error = null
                        }
                        .onFailure { error = it.message }
                }
            },
            enabled = enabled && customName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add custom comparison arm") }
    }

    pendingCampaign?.let { preset ->
        val definition = featureCampaignDefinition(preset)
        AlertDialog(
            onDismissRequest = { pendingCampaign = null },
            title = { Text(definition.title) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("An arm means one experimental configuration. This is a plan; no model has been trained by applying it.")
                    definition.selections.forEachIndexed { index, selection ->
                        Text("${index + 1}. ${selection.displayName}: ${selection.featureCount} variables; ${selection.sourceProfile.name}; ID ${selection.id}")
                    }
                    Text(definition.configurationSummary)
                    Text(definition.scientificQuestion)
                    Text(
                        "Confirming replaces the current comparison arms. Dataset rows are reused because every arm is derived from the complete versioned CSV record; training still validates the required columns before use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCampaign = null }) { Text("Keep current arms") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSelectionsChange(definition.selections)
                        error = null
                        pendingCampaign = null
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag("confirm-feature-campaign")
                ) {
                    Text("Apply ${definition.selections.size} arms")
                }
            }
        )
    }

    variablePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text("Selected variables · ${preview.featureNames.size}") },
            text = {
                val contract = FeatureSetCatalog.entries().first {
                    it.domain == FeatureSetDomain.CLASSIFICATION && it.sourceContract == preview.sourceProfile.name &&
                        it.featureCount == ScientificDatasetTrainingReader.featureNames(preview.sourceProfile).size
                }
                FeatureDictionaryContent(contract, selectedPositions = preview.oneBasedPositions)
            },
            confirmButton = {
                TextButton(onClick = { previewOpen = false }) { Text("Back to experiment") }
            }
        )
    }
}

@Composable
fun OneMicronFeatureExperimentSelector(
    selections: List<OneMicronFeatureSelectionSpec>,
    enabled: Boolean,
    onSelectionsChange: (List<OneMicronFeatureSelectionSpec>) -> Unit,
    originLabel: String = "Verified IK",
    sourceCsvPath: String? = null
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var customName by rememberSaveable { mutableStateOf("Custom neural IK set") }
    var source by rememberSaveable { mutableStateOf(OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361) }
    var positions by rememberSaveable { mutableStateOf("1-361") }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Hide neural-IK feature builder" else "Choose neural-IK feature sets (${selections.size})")
        }
        FeatureSetExplorer(originLabel, sourceCsvPath, com.robotkinematicslab.mobile.ml.data.FeatureSetDomain.VERIFIED_IK)
        FeatureExperimentArchiveControls(originLabel, FeatureSetDomain.VERIFIED_IK, selections.map { FeatureExperimentArchive.verifiedIk(it) }, enabled) { restored ->
            onSelectionsChange(restored.map { OneMicronFeatureSelectionSpec(it.id, it.name, OneMicronIkFeatureProfile.valueOf(it.source), it.selectedNames) })
        }
        if (!expanded) return@Column
        selections.forEach { selection ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${selection.displayName.removeSuffix(" · ${selection.featureCount}").removeSuffix(" · ${selection.featureCount} variables")} · ${selection.featureCount} variables", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onSelectionsChange(selections.filterNot { it.id == selection.id }) }, enabled = enabled) { Text("Remove") }
            }
        }
        OneMicronIkFeatureProfile.entries.forEach { profile ->
            val preset = OneMicronFeatureSelectionSpec.complete(profile)
            OutlinedButton(
                onClick = { if (selections.none { it.id == preset.id }) onSelectionsChange(selections + preset) },
                enabled = enabled && selections.none { it.id == preset.id },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add ${preset.displayName}") }
            Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TrainingDisclosureSection(
            title = "Advanced · manual variable selection",
            subtitle = "Choose exact one-based positions from one source contract. Version, names and model-input order are preserved in the saved feature configuration.",
            initiallyExpanded = false
        ) {
            OutlinedTextField(customName, { customName = it }, label = { Text("Custom configuration name") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            CompactSelectionMenu(options = OneMicronIkFeatureProfile.entries, selected = source,
                label = { "${it.displayName} (${OneMicronIkFeatureEncoder.featureNames(it).size})" },
                onSelected = { source = it; positions = "1-${OneMicronIkFeatureEncoder.featureNames(it).size}"; error = null }, enabled = enabled)
            OutlinedTextField(positions, { positions = it }, label = { Text("Variables, e.g. 1-108,200,250-361") }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.politeLiveRegion().testTag("one-micron-feature-experiment-status")
                )
            }
            OutlinedButton(onClick = {
                val names = OneMicronIkFeatureEncoder.featureNames(source)
                FeatureIndexSelectionParser.parse(positions, names.size).onSuccess { showPreview = true; error = null }.onFailure { error = it.message }
            }, enabled = enabled) { Text("Preview selected variables") }
            Button(
                onClick = {
                    if(customName.isBlank() || selections.any { it.displayName.equals(customName.trim(), true) }) {
                        error = "Use a nonblank, distinct configuration name."
                        return@Button
                    }
                    val names = OneMicronIkFeatureEncoder.featureNames(source)
                    FeatureIndexSelectionParser.parse(positions, names.size).onSuccess { indices ->
                        if(selections.any { it.sourceProfile == source && it.includedFeatureNames == indices.map(names::get) }) { error = "This exact ordered configuration is already selected."; return@onSuccess }
                        val stem = customName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(68).ifBlank { "custom-ik" }
                        var id = stem
                        var suffix = 2
                        while (selections.any { it.id == id }) id = "$stem-${suffix++}"
                        onSelectionsChange(selections + OneMicronFeatureSelectionSpec(id, customName.trim(), source, indices.map(names::get)))
                        error = null
                    }.onFailure { error = it.message }
                },
                enabled = enabled && customName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add custom neural-IK feature configuration") }
        }
    }
    if(showPreview) {
        val names = OneMicronIkFeatureEncoder.featureNames(source)
        val indices = FeatureIndexSelectionParser.parse(positions, names.size).getOrNull().orEmpty()
        AlertDialog(onDismissRequest = { showPreview = false }, title = { Text("Selected variables · ${indices.size}") },
            text = {
                val contract = FeatureSetCatalog.entries().first { it.domain == FeatureSetDomain.VERIFIED_IK && it.sourceContract == source.name }
                FeatureDictionaryContent(contract, selectedPositions = indices.map { it + 1 })
            }, confirmButton = { TextButton(onClick = { showPreview = false }) { Text("Back to experiment") } })
    }
}
