package com.robotkinematicslab.mobile.ui.training.results

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.robotkinematicslab.mobile.ml.storage.*
import com.robotkinematicslab.mobile.ui.training.TrainingDisclosureSection
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoricalTrainingRunCard(run:TrainingRunSummary) {
    var open by rememberSaveable(run.runId) { mutableStateOf(false) }
    var snapshot by remember(run.runId) { mutableStateOf<HistoricalRunEvidence?>(null) }
    var error by remember(run.runId) { mutableStateOf<String?>(null) }
    var history by remember(run.runId) { mutableStateOf<List<StoredTrainingIteration>>(emptyList()) }
    var selectedId by rememberSaveable(run.runId) { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    LaunchedEffect(open,run.runId) {
        if(open) {
            error=null;snapshot=null;history=emptyList()
            val result=withContext(Dispatchers.IO) { runCatching { TrainingResultEvidenceArchive.load(File(run.directoryPath),run.runId) } }
            result.onSuccess { snapshot=it; if(it.profiles.none { profile -> profile.id==selectedId }) selectedId=it.profiles.firstOrNull()?.id }.onFailure { error=it.message }
            withContext(Dispatchers.IO) { runCatching { TrainingStorageRepository(context).loadIterationHistory(run) } }.onSuccess { history=it }
        }
    }
    TrainingDisclosureSection(title=run.runName,subtitle=DateFormat.getDateTimeInstance().format(Date(run.startedAtEpochMillis))+" · completed stored run") {
        Text("${run.variants.size} recorded configurations. This run contains models and results; telemetry sessions contain runtime measurements and are separate evidence.")
        run.variants.forEach { Text("${it.featureSelectionName}: independent-test macro-F1 ${ScientificMetricFormat.RATIO.format(it.testMacroF1)} · n=${it.testRowCount}") }
        if(run.variants.isEmpty()) Text("Legacy summary: complete configuration-level evidence was not recorded. Baseline ${run.baselineTestMacroF1?.let { ScientificMetricFormat.RATIO.format(it) } ?: "unavailable"}; context ${run.contextTestMacroF1?.let { ScientificMetricFormat.RATIO.format(it) } ?: "not recorded or not run"}.")
        Button(onClick={open=true}, modifier=Modifier.testTag("historical-open-${run.runId}")) { Text("Open stored results") }
    }
    if(open) Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(run.runName,style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={open=false}, modifier=Modifier.testTag("historical-return")) { Text("Back to training history") }
            Text("Original split: ${run.splitStrategy.displayName}; seed ${run.randomSeed}; row cap ${run.maximumRows}. Opening this result never trains or evaluates on the current dataset.")
            error?.let { Text("Complete evidence unavailable: $it. The original summary below remains available; missing fields are not substituted.",color=MaterialTheme.colorScheme.error) }
            if(snapshot==null && error==null) Text("Loading original evidence…")
            snapshot?.let { stored ->
                CompactSelectionMenu(stored.profiles,stored.profiles.firstOrNull { it.id==selectedId },{ it.name },{ selectedId=it.id })
                stored.profiles.firstOrNull { it.id==selectedId }?.let { profile ->
                    ProvideAutomaticFigureLibraryContext(collection="training",analysisId=run.runId,executionId="training-${run.runId}") {
                        Text("${profile.name}: ${profile.featureNames.size} exact stored variables; candidate ${profile.candidate}; selected epoch ${profile.bestEpoch}.",modifier=Modifier.testTag("historical-profile-${profile.id}"))
                        NamedClassSupport(profile.test)
                        ScientificTestSlices(profile.slices,"${run.runId}:${profile.id}")
                        ScientificClassificationFigures(profile.name,profile.test)
                        val observations=history.filter { it.featureSelectionId==profile.id }
                        if(observations.isNotEmpty()) ProfessionalLineChart(title="${profile.name} · recorded validation history",subtitle="Original stored epochs. Gaps separate candidates; selected epoch ${profile.bestEpoch} belongs to ${profile.candidate}. Validation is not independent test.",points=observations.mapIndexed { index,it -> ChartLinePoint(it.globalIteration.toDouble(),it.validationMacroF1,index>0 && observations[index-1].candidateId!=it.candidateId,"Candidate ${it.candidateId}; epoch ${it.epoch}; iteration ${it.globalIteration}; elapsed ${it.elapsedMillis} ms") },xAxisLabel="Recorded epoch event",yAxisLabel="Validation macro-F1",color=Color.Blue)
                        else Text("Iteration history unavailable for this exact configuration.")
                    }
                    TrainingDisclosureSection(title="Technical result details",subtitle="Original identities, feature order and warnings.") {
                        Text("Run ${run.runId}; configuration ${profile.id}; dataset ${stored.datasetPath}; summary SHA-256 ${stored.summarySha256}")
                        profile.featureNames.forEachIndexed { index,name -> Text("${index+1}. $name") }
                        profile.warnings.forEach { Text(it) }
                    }
                }
            }
            TrainingDisclosureSection(title="Original run provenance",subtitle="Files and metadata remain associated with this run ID.") {
                Text("Run ${run.runId}; directory ${run.directoryPath}; original dataset ${run.datasetPath}")
                Text("History ${run.historyCsvPath}: ${if(File(run.historyCsvPath).isFile) "available" else "missing"}")
                run.modelPaths.forEach { Text("Model $it: ${if(File(it).isFile) "available" else "missing"}") }
                Text(run.trainingControls?.toString() ?: "Complete optimizer controls were not recorded in this legacy run.")
                run.variants.forEach { Text("${it.featureSelectionName}: F1 ${it.testMacroF1}; n=${it.testRowCount}; selected-model time ${it.trainingDurationMillis/1000.0} s; ${it.parameterCount} parameters") }
            }
        } }
    }
}
