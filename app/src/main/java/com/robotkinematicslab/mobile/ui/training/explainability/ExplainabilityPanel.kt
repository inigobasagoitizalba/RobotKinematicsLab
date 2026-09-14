package com.robotkinematicslab.mobile.ui.training.explainability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.robotkinematicslab.mobile.ui.shared.EvidenceSelectionDropdown
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.training.StoredModelChoice
import com.robotkinematicslab.mobile.ui.training.storedModelChoices
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilityEligibility
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilityPreflight
import com.robotkinematicslab.mobile.ml.explainability.workCounts
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticSystemTelemetrySampler
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilityPhase
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilityProgress
import com.robotkinematicslab.mobile.ml.explainability.ExplainabilitySessionCoordinator
import com.robotkinematicslab.mobile.ml.explainability.StoredExplainabilityReport
import com.robotkinematicslab.mobile.ml.research.FeatureFamilyAnalyzer
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartHeatMapCell
import com.robotkinematicslab.mobile.ui.charts.advanced.heatmaps.ProfessionalHeatMapChart
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.basic.LocalChartCardsCollapsible
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideFactory
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartGuideKind
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationController
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.diagnostic.DiagnosticDisclosureCard
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.ExpandableSelectionCollection
import com.robotkinematicslab.mobile.ui.shared.ScientificEntityNameResolver
import com.robotkinematicslab.mobile.ui.shared.politeLiveRegion
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingCardConfig
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticLoadingProgressCard
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStatus
import com.robotkinematicslab.mobile.ui.shared.progress.DiagnosticTimelineStep
import com.robotkinematicslab.mobile.ui.shared.progress.HighResolutionTelemetryCaptureGate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun ExplainabilityPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val owner = remember(context) { ExplainabilitySessionCoordinator.get(context) }
    ExplainabilitySessionPanel(owner, modifier)
}

@Composable
internal fun ExplainabilitySessionPanel(owner: ExplainabilitySessionCoordinator, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val session by owner.state.collectAsState()
    val repository = remember(context) { TrainingStorageRepository(context) }
    val telemetrySampler = remember(context) { DiagnosticSystemTelemetrySampler(context) }
    var runs by remember { mutableStateOf<List<TrainingRunSummary>>(emptyList()) }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var selectedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRun = runs.firstOrNull { it.runId == selectedRunId }
    var selectedModelPath by rememberSaveable { mutableStateOf<String?>(null) }
    var sampleCountText by rememberSaveable { mutableStateOf(ExplainabilityPreflight.DEFAULT_SAMPLES.toString()) }
    var stepCountText by rememberSaveable { mutableStateOf(ExplainabilityPreflight.DEFAULT_STEPS.toString()) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val active = session.running
    val controlsEnabled = !active && !session.loading
    val progress = session.progress
    var telemetry by remember(owner) { mutableStateOf<DiagnosticProgressState?>(null) }
    val performanceSamples = remember(owner) { mutableStateListOf<DiagnosticPerformanceSample>() }
    val telemetryCaptureGate = remember(owner) { HighResolutionTelemetryCaptureGate() }
    LaunchedEffect(refreshRevision) {
        val loaded = withContext(Dispatchers.IO) { repository.listRuns().filter { it.modelPaths.isNotEmpty() } }
        runs = loaded
        if (selectedRunId == null) selectedRunId = loaded.firstOrNull()?.runId
        if (selectedModelPath == null) selectedModelPath = loaded.firstOrNull { it.runId == selectedRunId }?.modelPaths?.firstOrNull()
    }
    var modelChoices by remember { mutableStateOf<List<StoredModelChoice>>(emptyList()) }
    var modelEvidence by remember { mutableStateOf<ExplainabilityEligibility?>(null) }
    var evidenceMessage by remember { mutableStateOf("Choose a saved model.") }
    var checkingEvidence by remember { mutableStateOf(false) }
    val modelKey=selectedRunId to selectedModelPath
    var verifiedKey by remember { mutableStateOf<Pair<String?,String?>?>(null) }
    val sampleMaximum=sampleCountText.toIntOrNull()
    val integrationSteps=stepCountText.toIntOrNull()
    val inputErrors=ExplainabilityPreflight.controlErrors(sampleMaximum,integrationSteps)
    LaunchedEffect(selectedRunId,runs) {
        modelChoices=selectedRun?.let { run -> withContext(Dispatchers.IO) { storedModelChoices(repository,run) } }.orEmpty()
    }
    LaunchedEffect(modelKey,refreshRevision,runs) {
        modelEvidence=null;verifiedKey=null;checkingEvidence=true
        val run=selectedRun;val path=selectedModelPath
        if(run==null || path==null) { evidenceMessage="Choose an available run and one of its models.";checkingEvidence=false;return@LaunchedEffect }
        val jobContext=currentCoroutineContext()
        val checked=withContext(Dispatchers.IO) { runCatching { ExplainabilityPreflight.validate(repository,run,path,ExplainabilityPreflight.DEFAULT_SAMPLES,ExplainabilityPreflight.DEFAULT_STEPS) { !jobContext.isActive } } }
        currentCoroutineContext().ensureActive()
        modelEvidence=checked.getOrNull();verifiedKey=modelKey;checkingEvidence=false
        evidenceMessage=checked.fold({ "Verified ${it.availableTestRows} original test predictions for ${it.modelLabel}." },{ it.message ?: "The original model evidence cannot be verified." })
    }
    LaunchedEffect(session.request?.executionId) {
        session.request?.let {
            selectedRunId = it.runId; selectedModelPath = it.modelPath
            sampleCountText = it.samples.toString(); stepCountText = it.steps.toString()
        }
        localMessage = null
        performanceSamples.clear(); telemetryCaptureGate.reset(); telemetry = null
    }
    LaunchedEffect(session.status) { localMessage = null }
    LaunchedEffect(progress, active) {
        if (progress != null && telemetryCaptureGate.shouldCapture(force = !active)) {
            val mapped = mapExplainabilityProgress(progress, session.startedAtEpochMillis, telemetrySampler).copy(isRunning = active)
            telemetry = mapped
            performanceSamples += DiagnosticPerformanceSample.fromProgressState(performanceSamples.size + 1, mapped)
        } else if (!active) telemetry = telemetry?.copy(
            isRunning = false,
            phase = if (session.status == com.robotkinematicslab.mobile.ml.explainability.ExplainabilitySessionStatus.COMPLETED) DiagnosticProgressPhase.COMPLETED else DiagnosticProgressPhase.FAILED,
            message = session.message
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Explainable AI Studio", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        JargonHelpNotice()
        Text(com.robotkinematicslab.mobile.ml.explainability.ExplainabilityOutcomeScope.DESCRIPTION)
        JargonAwareText("Explain what drives the model globally and why it made an individual prediction. Evidence is calculated only from the held-out test set, so the explanation never trains the model.")

        AppDisclosureSection("1 · Model evidence",modelEvidence?.modelLabel ?: "Choose a run and model",testTag="xai-model-evidence") {
            EvidenceSelectionDropdown("Training run",runs,selectedRun,{ "${it.runName} · seed ${it.randomSeed}" },onSelected={ run -> selectedRunId=run.runId;selectedModelPath=run.modelPaths.firstOrNull() },enabled=controlsEnabled,testTag="xai-run-selector")
            EvidenceSelectionDropdown("Stored model",modelChoices,modelChoices.firstOrNull { it.path==selectedModelPath },StoredModelChoice::label,onSelected={ selectedModelPath=it.path },enabled=controlsEnabled,testTag="xai-model-selector")
            AppDisclosureSection("Model contract and variables","Technical identity and the original input feature schema",testTag="xai-model-contract") {
                selectedModelPath?.let { Text("Technical model ID: ${ScientificEntityNameResolver.model(it).technicalId}") }
                com.robotkinematicslab.mobile.ui.training.FeatureSetExplorer("Explainable AI / Model evidence",selectedRun?.datasetPath)
            }
            OutlinedButton(onClick={ refreshRevision+=1 },enabled=controlsEnabled,modifier=Modifier.fillMaxWidth()) { Text("Refresh stored models") }
            Text(if(checkingEvidence) "Verifying original model, normalized inputs and predictions…" else evidenceMessage,modifier=Modifier.testTag("xai-model-eligibility"))
        }

        AppDisclosureSection(
            title = "2 · Explanation controls",
            summary = "Maximum $sampleCountText test cases · $stepCountText integration steps",
            testTag = "xai-explanation-settings",
            modifier = Modifier
                .testTag("training-explainability-controls")
                .tutorialAnchor(TutorialTargets.TrainingExplainability)
        ) {
            OutlinedTextField(sampleCountText, { sampleCountText = it.take(12) }, label = { Text("Held-out samples") }, enabled = controlsEnabled, isError=sampleMaximum==null || sampleMaximum !in ExplainabilityPreflight.MIN_SAMPLES..ExplainabilityPreflight.MAX_SAMPLES,
                supportingText={ Text("Default ${ExplainabilityPreflight.DEFAULT_SAMPLES}; ${ExplainabilityPreflight.MIN_SAMPLES}–${ExplainabilityPreflight.MAX_SAMPLES}. Maximum cases, evenly spaced through the original ordered test indices. More cases cost more work; greater global stability is not guaranteed.") },singleLine=true,modifier = Modifier.fillMaxWidth().testTag("xai-sample-count"))
            OutlinedTextField(stepCountText, { stepCountText = it.take(12) }, label = { Text("Integration steps") }, enabled = controlsEnabled,isError=integrationSteps==null || integrationSteps !in ExplainabilityPreflight.MIN_STEPS..ExplainabilityPreflight.MAX_STEPS,
                supportingText={ Text("Default ${ExplainabilityPreflight.DEFAULT_STEPS}; ${ExplainabilityPreflight.MIN_STEPS}–${ExplainabilityPreflight.MAX_STEPS}. Midpoint gradient evaluations along each baseline-to-case path. More steps increase work and can reduce approximation error; inspect measured completeness.") },singleLine=true,modifier = Modifier.fillMaxWidth().testTag("xai-step-count"))
            JargonAwareText("Integrated Gradients attributes the predicted-class logit minus the closest alternative logit. The path starts at zero normalized inputs (training means before clipping/normalization), which need not describe a physically realizable robot. The model is never retrained. Global importance averages absolute local attributions; global stability compares the first and second halves of these cases, not independent retraining runs.")
            modelEvidence?.let { Text("Requested maximum $sampleCountText; ${it.availableTestRows} verified test cases available; effective ${sampleMaximum?.let { n -> minOf(n,it.availableTestRows) } ?: "invalid"}. Each case adds $stepCountText gradient evaluations, plus model/replay/aggregation work.") }
            inputErrors.forEach { Text(it,color=androidx.compose.material3.MaterialTheme.colorScheme.error) }
            if (!active) {
                Button(
                    onClick = {
                        val run = selectedRun
                        val modelPath = selectedModelPath
                        val samples = sampleCountText.toIntOrNull()
                        val steps = stepCountText.toIntOrNull()
                        if (run == null || modelPath == null || samples !in 16..5000 || steps !in 8..256) {
                            localMessage = "Select a model and enter controls inside the safe ranges."
                        } else {
                            localMessage = null
                            owner.start(run, modelPath, samples!!, steps!!, modelEvidence?.modelFileSha256).onFailure { localMessage = it.message }
                        }
                    },
                    enabled = controlsEnabled && !checkingEvidence && modelEvidence!=null && verifiedKey==modelKey && inputErrors.isEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("explainability-generate")
                ) { Text(if (session.loading) "Loading saved evidence…" else "Generate explainability report") }
            }
            JargonAwareText(localMessage ?: if(session.request==null) (if(modelEvidence!=null) "Model selected. Review controls and generate a report." else evidenceMessage) else session.message,
                modifier = Modifier.politeLiveRegion().testTag("training-explainability-status"))
        }

        // Stopping an active process must remain reachable when configuration is folded or recreated.
        if (active) {
            OutlinedButton(
                onClick = { owner.cancel(); localMessage = "Cancellation requested…" },
                modifier = Modifier.fillMaxWidth().testTag("explainability-cancel")
            ) { Text("Cancel safely") }
        }

        progress?.let { update -> val counts=update.workCounts();Text("Predictions explained: ${counts.completedPredictions}/${counts.totalPredictions}. Auxiliary work: ${counts.completedAuxiliary}/${counts.totalAuxiliary}. Total work units: ${update.completedWork}/${update.totalWork}.",modifier=Modifier.testTag("xai-work-counts")) }
        telemetry?.let { state ->
            DiagnosticLoadingProgressCard(
                progressState = state,
                performanceSamples = performanceSamples,
                config = DiagnosticLoadingCardConfig(
                    runningTitle = "Explainability Analysis Running",
                    finishedTitle = if (session.status == com.robotkinematicslab.mobile.ml.explainability.ExplainabilitySessionStatus.COMPLETED) "Explainability Analysis Complete" else "Explainability Analysis Stopped",
                    progressSectionTitle = "Explanation Progress",
                    completedLabel = "Predictions explained",
                    remainingLabel = "Predictions left",
                    rateLabel = "Explanations per second",
                    showBenchmarkCoordinates = false,
                    telemetrySessionType = "Explainable AI"
                ),
                timeline = explainabilityTimeline(progress?.phase ?: ExplainabilityPhase.LOADING_MODEL)
            )
        }
        session.report?.let { report ->
            Text("Saved explanation · ${report.result.runId} · ${report.result.candidateId}",
                modifier = Modifier.testTag("explainability-saved-report"))
            ExplainabilityReport(report)
        }
    }
}

@Composable
private fun ExplainabilityReport(completedReport: StoredExplainabilityReport) {
    val result = completedReport.result
    val exportSession =
        remember(result, completedReport.completedAtEpochMillis) {
            ExplainabilityFigureExportContract.createSession(
                runId = result.runId,
                candidateId = result.candidateId,
                featureSelectionName = result.featureSelectionName,
                explainedSampleCount = result.explainedSampleCount,
                integratedGradientSteps = result.integratedGradientSteps,
                completedAtEpochMillis = completedReport.completedAtEpochMillis
            )
        }
    val topFeatures = result.globalImportance.take(20)
    val featureFamilies = remember(result.globalImportance) {
        FeatureFamilyAnalyzer.aggregate(result.globalImportance)
    }
    val familyCoActivation = remember(result.localExplanations) {
        FeatureFamilyAnalyzer.coActivation(result.localExplanations)
    }
    val presentation = LocalChartPresentationController.current
    val requiredExportPresentation =
        remember(presentation) {
            ChartPresentationController(
                preferences = presentation.preferences.copy(autoSaveFigures = true),
                update = presentation.update
            )
        }
    ProvideAutomaticFigureLibraryContext(
        collection = exportSession.collection,
        analysisId = exportSession.analysisId,
        executionId = exportSession.executionId
    ) {
      CompositionLocalProvider(LocalChartPresentationController provides requiredExportPresentation) {
        ChartSectionCard("Explanation quality", "A low completeness error means the displayed attributions faithfully reconstruct the model's decision margin.") {
            ChartMetricRow("Evidence execution", "${exportSession.analysisId} / ${exportSession.executionId}")
            ChartMetricRow("Model", result.candidateId)
            ChartMetricRow("Feature selection", result.featureSelectionName)
            ChartMetricRow("Held-out predictions", result.explainedSampleCount.toString())
            ChartMetricRow("Integrated-gradient steps", result.integratedGradientSteps.toString())
            ChartMetricRow("Explained-sample accuracy", percent(result.explainedSampleAccuracy))
            ChartMetricRow("Global importance stability", if(result.explainedSampleCount < 2) "Not estimable from one case" else percent(result.globalImportanceStability))
            Text("Stability compares mean absolute attributions in the first and second halves of these explained cases; it is not agreement across independent trainings.")
            ChartMetricRow("Mean completeness error", decimal(result.meanCompletenessError))
            ChartMetricRow("Maximum completeness error", decimal(result.maximumCompletenessError))
            ChartMetricRow("Generation time", "${result.durationMillis} ms")
        }

        // These five chart composables stay in the composition even when their own chart cards are
        // collapsed. ChartSectionCard can therefore render a deterministic export host at report
        // completion instead of depending on the user opening every disclosure manually.
        CompositionLocalProvider(LocalChartCardsCollapsible provides true) {
        val maximumImportance = (topFeatures.maxOfOrNull { it.meanAbsoluteAttribution } ?: 0.0).coerceAtLeast(1e-12)
        if (topFeatures.isNotEmpty()) {
            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title = ExplainabilityFigureId.GLOBAL_FEATURE_IMPORTANCE.title,
                subtitle = "Mean absolute contribution to the predicted-versus-runner-up logit margin, normalized to the strongest feature. ${exportSession.metadata}.",
                items = topFeatures.map {
                    ChartBarItem(
                        "${ExplainabilityFeatureNames.describe(it.featureName)} · ${decimal(it.meanAbsoluteAttribution)}",
                        ((it.meanAbsoluteAttribution / maximumImportance) * 10_000).toInt().coerceIn(0, 10_000),
                        Color(0xFF1976D2)
                    )
                },
                xAxisLabel = "Relative importance (strongest = 100%)"
            )
        } else {
            MissingExplainabilityFigure(
                figureId = ExplainabilityFigureId.GLOBAL_FEATURE_IMPORTANCE,
                metadata = exportSession.metadata
            )
        }

        val maximumFamilyImportance =
            (featureFamilies.maxOfOrNull { it.totalAbsoluteImportance } ?: 0.0).coerceAtLeast(1e-12)
        if (featureFamilies.isNotEmpty()) {
            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title = ExplainabilityFigureId.SCIENTIFIC_FAMILY_IMPORTANCE.title,
                subtitle =
                    "Bars show the sum of absolute held-out attribution within each family. " +
                        "Feature count and exact total remain in each label. ${exportSession.metadata}.",
                items = featureFamilies.map { family ->
                    ChartBarItem(
                        label =
                            "${family.familyId} · ${family.featureCount} vars · " +
                                decimal(family.totalAbsoluteImportance),
                        value =
                            ((family.totalAbsoluteImportance / maximumFamilyImportance) * 10_000.0)
                                .toInt()
                                .coerceIn(0, 10_000),
                        color = familyColor(family.familyId)
                    )
                },
                xAxisLabel = "Relative family importance (strongest = 100%)"
            )
        } else {
            MissingExplainabilityFigure(
                figureId = ExplainabilityFigureId.SCIENTIFIC_FAMILY_IMPORTANCE,
                metadata = exportSession.metadata
            )
        }
        }

        DiagnosticDisclosureCard(
            "Feature-family numeric evidence",
            "Exact family totals remain available without being duplicated inside every exported chart."
        ) {
        featureFamilies.take(8).forEach { family ->
            ChartMetricRow(
                family.familyId,
                "${family.featureCount} variables · mean ${decimal(family.meanAbsoluteImportance)} · signed ${signed(family.signedImportance)}"
            )
        }
        JargonAwareText(
            "Importance is descriptive evidence, not proof of physical causation. Remove a family only after a paired ablation on the same split and seeds."
        )
        }

        CompositionLocalProvider(LocalChartCardsCollapsible provides true) {
    if (familyCoActivation.isNotEmpty()) {
            val labels = familyCoActivation.map { it.rowFamily }.distinct()
            ProfessionalHeatMapChart(
                title = ExplainabilityFigureId.FAMILY_CO_ACTIVATION.title,
                subtitle =
                    "Descriptive mean |family attribution| product, not causal proof or a SHAP interaction value. ${exportSession.metadata}.",
                rowLabels = labels,
                columnLabels = labels,
                cells = familyCoActivation.map { interaction ->
                    ChartHeatMapCell(
                        row = interaction.rowFamily,
                        column = interaction.columnFamily,
                        value = interaction.normalizedStrength,
                        displayValue =
                            "relative ${percent(interaction.normalizedStrength)} · raw ${decimal(interaction.meanAbsoluteProduct)} · n=${interaction.sampleCount}",
                        color = coActivationColor(interaction.normalizedStrength)
                    )
                },
                xAxisLabel = "Feature family",
                yAxisLabel = "Feature family"
            )
    } else {
            MissingExplainabilityFigure(
                figureId = ExplainabilityFigureId.FAMILY_CO_ACTIVATION,
                metadata = exportSession.metadata
            )
    }

        val topNames = topFeatures.take(12).map { it.featureName }
        val rankKey =
            topNames.mapIndexed { index, name -> "${topNames.size - index}=$name" }
                .joinToString("; ")
        val points = result.localExplanations.flatMap { explanation ->
            explanation.attributions.filter { it.featureName in topNames }.map { attribution ->
                val rank = topNames.indexOf(attribution.featureName)
                ChartPoint(
                    x = attribution.attribution,
                    y = (topNames.size - rank).toDouble(),
                    color = if (attribution.normalizedFeatureValue >= 0.0) Color(0xFFE91E63) else Color(0xFF168AE8),
                    label = "${ExplainabilityFeatureNames.describe(attribution.featureName)}: ${signed(attribution.attribution)}"
                )
            }
        }
        if (points.isNotEmpty()) {
            ProfessionalScatterChart(
                ExplainabilityFigureId.FEATURE_IMPACT_DISTRIBUTION.title,
                "Pink is above the training mean; blue is below it. Positive values support the predicted class. Rank key: $rankKey. ${exportSession.metadata}.",
                points,
                "Attribution to decision margin",
                "Global feature rank"
            )
        } else {
            MissingExplainabilityFigure(
                figureId = ExplainabilityFigureId.FEATURE_IMPACT_DISTRIBUTION,
                metadata = exportSession.metadata
            )
        }

    if (result.localExplanations.isNotEmpty()) {
        var selectedIndex by rememberSaveable(result.runId, result.candidateId, result.featureSelectionName, result.durationMillis) {
            mutableIntStateOf(result.localExplanations.indexOfFirst { it.trueLabel != it.predictedLabel }.coerceAtLeast(0))
        }
        val selected = result.localExplanations[selectedIndex.coerceIn(result.localExplanations.indices)]
        DiagnosticDisclosureCard("Local prediction controls", "Move through correct and incorrect held-out cases; every viewed case receives a separate PNG name.") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedIndex = if (selectedIndex <= 0) result.localExplanations.lastIndex else selectedIndex - 1 }, modifier = Modifier.weight(1f)) { Text("Previous") }
                OutlinedButton(onClick = { selectedIndex = if (selectedIndex >= result.localExplanations.lastIndex) 0 else selectedIndex + 1 }, modifier = Modifier.weight(1f)) { Text("Next") }
            }
            OutlinedButton(
                onClick = {
                    val laterError = (selectedIndex + 1 until result.localExplanations.size).firstOrNull { index ->
                        val item = result.localExplanations[index]
                        item.trueLabel != item.predictedLabel
                    }
                    val anyError = result.localExplanations.indexOfFirst { it.trueLabel != it.predictedLabel }
                    selectedIndex = laterError ?: anyError.takeIf { it >= 0 } ?: selectedIndex
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Next model error") }
            ChartMetricRow("Case", "${selectedIndex + 1} / ${result.localExplanations.size}")
            ChartMetricRow("Source row", selected.sourceRowIndex.toString())
            ChartMetricRow("Truth / prediction", "${selected.trueLabel} / ${selected.predictedLabel}")
            ChartMetricRow("Closest alternative", selected.contrastLabel.name)
            ChartMetricRow("Confidence", percent(selected.confidence))
            ChartMetricRow("Decision margin", decimal(selected.predictionMargin))
        }
            val strongestLocal = (selected.attributions.maxOfOrNull { abs(it.attribution) } ?: 0.0).coerceAtLeast(1e-12)
            ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
                title =
                    ExplainabilityFigureExportContract.waterfallTitle(
                        caseNumber = selectedIndex + 1,
                        sourceRowIndex = selected.sourceRowIndex
                    ),
                subtitle =
                    "Green supports ${selected.predictedLabel}; red pulls toward ${selected.contrastLabel}. " +
                        "Raw values are signed logit-margin contributions, not probability changes. Truth=${selected.trueLabel}, confidence=${percent(selected.confidence)}. ${exportSession.metadata}.",
                items = selected.attributions.sortedByDescending { abs(it.attribution) }.take(15).map {
                    ChartBarItem(
                        "${ExplainabilityFeatureNames.describe(it.featureName)} · ${signed(it.attribution)}",
                        ((abs(it.attribution) / strongestLocal) * 10_000).toInt().coerceIn(0, 10_000),
                        if (it.attribution >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                },
                xAxisLabel = "Relative local magnitude (strongest = 100%)"
            )
        DiagnosticDisclosureCard("Local prediction interpretation", "Plain-language interpretation for the currently selected held-out case.") {
            HorizontalDivider()
            Text("Why did the model prefer this class for this test case?")
            Text("Positive signed contributions support ${selected.predictedLabel} over ${selected.contrastLabel}; negative contributions oppose that margin relative to the training-mean baseline. Local magnitude is specific to this case. Confidence is a model probability, not physical verification.")
            ChartMetricRow("Attribution completeness error",decimal(selected.completenessError))
            JargonAwareText(plainLanguage(selected.predictedLabel.name, selected.contrastLabel.name, selected.attributions.sortedByDescending { abs(it.attribution) }.take(5).map { it.featureName }))
        }
    } else {
        MissingExplainabilityFigure(
            figureId = ExplainabilityFigureId.LOCAL_CONTRIBUTION_WATERFALL,
            metadata = exportSession.metadata
        )
    }
        }
      }
    }
}

@Composable
private fun MissingExplainabilityFigure(
    figureId: ExplainabilityFigureId,
    metadata: String
) {
    val kind =
        when (figureId) {
            ExplainabilityFigureId.GLOBAL_FEATURE_IMPORTANCE,
            ExplainabilityFigureId.SCIENTIFIC_FAMILY_IMPORTANCE,
            ExplainabilityFigureId.LOCAL_CONTRIBUTION_WATERFALL -> ChartGuideKind.BAR

            ExplainabilityFigureId.FAMILY_CO_ACTIVATION -> ChartGuideKind.HEAT_MAP
            ExplainabilityFigureId.FEATURE_IMPACT_DISTRIBUTION -> ChartGuideKind.SCATTER
        }
    val subtitle = "No finite held-out evidence was available for this required figure. $metadata."
    ChartSectionCard(
        title = figureId.title,
        subtitle = subtitle,
        guide =
            ChartGuideFactory.forChart(
                kind = kind,
                title = figureId.title,
                subtitle = subtitle
            ),
        automaticExportKey = "required-empty-evidence:$metadata"
    ) {
        ChartMetricRow("Figure status", "No finite evidence available")
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    AccessibleSelectionButton(
        selected = selected,
        label = label,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun mapExplainabilityProgress(update: ExplainabilityProgress, startedAt: Long, sampler: DiagnosticSystemTelemetrySampler): DiagnosticProgressState {
    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 1_000.0
    val counts=update.workCounts()
    val rate = if (elapsed > 0) counts.completedPredictions / elapsed else 0.0
    val remaining = (counts.totalPredictions - counts.completedPredictions).coerceAtLeast(0)
    val phase = when (update.phase) {
        ExplainabilityPhase.LOADING_MODEL, ExplainabilityPhase.READING_DATASET, ExplainabilityPhase.REBUILDING_SPLIT -> DiagnosticProgressPhase.PLANNING
        ExplainabilityPhase.EXPLAINING_SAMPLES -> DiagnosticProgressPhase.SEQUENTIAL_RUNS
        ExplainabilityPhase.AGGREGATING -> DiagnosticProgressPhase.AGGREGATING
        ExplainabilityPhase.COMPLETED -> DiagnosticProgressPhase.COMPLETED
    }
    return DiagnosticProgressState(
        isRunning = update.phase != ExplainabilityPhase.COMPLETED,
        phase = phase,
        completedRuns = counts.completedPredictions,
        totalRuns = counts.totalPredictions,
        runsPerSecond = rate,
        estimatedSecondsRemaining = if (rate > 0) remaining / rate else Double.NaN,
        elapsedSeconds = elapsed,
        message = update.message,
        telemetry = sampler.sample()
    )
}

private fun explainabilityTimeline(phase: ExplainabilityPhase): List<DiagnosticTimelineStep> {
    val ordered = listOf(
        ExplainabilityPhase.LOADING_MODEL to "Load model contract",
        ExplainabilityPhase.READING_DATASET to "Validate scientific data",
        ExplainabilityPhase.REBUILDING_SPLIT to "Rebuild held-out evidence",
        ExplainabilityPhase.EXPLAINING_SAMPLES to "Calculate local attributions",
        ExplainabilityPhase.AGGREGATING to "Build global explanations"
    )
    val current = ordered.indexOfFirst { it.first == phase }
    return ordered.mapIndexed { index, pair -> DiagnosticTimelineStep(pair.second, when {
        phase == ExplainabilityPhase.COMPLETED || index < current -> DiagnosticTimelineStatus.COMPLETE
        index == current -> DiagnosticTimelineStatus.RUNNING
        else -> DiagnosticTimelineStatus.PENDING
    }) }
}

private fun plainLanguage(predicted: String, contrast: String, features: List<String>): String =
    "The model preferred $predicted over $contrast. The largest absolute model contributions were ${features.joinToString(", ") { ExplainabilityFeatureNames.describe(it) }}. These are model attributions, not physical causes; tap the charts to inspect their direction and magnitude."

private fun decimal(value: Double) = if (value.isFinite()) String.format(Locale.US, "%.6f", value) else "N/A"
private fun percent(value: Double) = if (value.isFinite()) String.format(Locale.US, "%.2f%%", value * 100) else "N/A"
private fun signed(value: Double) = if (value.isFinite()) String.format(Locale.US, "%+.5f", value) else "N/A"

private fun familyColor(family: String): Color {
    val palette =
        listOf(
            Color(0xFF1565C0),
            Color(0xFF2E7D32),
            Color(0xFF6A1B9A),
            Color(0xFFEF6C00),
            Color(0xFF00838F),
            Color(0xFFC62828),
            Color(0xFF5D4037),
            Color(0xFF455A64)
        )
    return palette[(family.hashCode() and Int.MAX_VALUE) % palette.size]
}

private fun coActivationColor(value: Double): Color =
    when {
        value >= 0.75 -> Color(0xFF6A1B9A)
        value >= 0.50 -> Color(0xFF1976D2)
        value >= 0.25 -> Color(0xFF26A69A)
        else -> Color(0xFFCFD8DC)
    }
