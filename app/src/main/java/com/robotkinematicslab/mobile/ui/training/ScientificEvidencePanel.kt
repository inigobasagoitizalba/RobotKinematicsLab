package com.robotkinematicslab.mobile.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ScatterLegendItem
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ml.research.ScientificEvidenceCalculator
import com.robotkinematicslab.mobile.ml.research.TrainingScaleObservation
import com.robotkinematicslab.mobile.ml.storage.StoredTrainingVariantEvidence
import com.robotkinematicslab.mobile.ml.storage.TrainingRunSummary
import com.robotkinematicslab.mobile.ml.storage.TrainingStorageRepository
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.horizontalbarcharts.ProfessionalHorizontalBarChart
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts.ProfessionalScatterChart
import com.robotkinematicslab.mobile.ui.charts.basic.ChartBarItem
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.guidance.ChartReadingDirection
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartFigureExporter
import com.robotkinematicslab.mobile.ui.charts.presentation.ProvideAutomaticFigureLibraryContext
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import java.util.Locale

@Composable
fun ScientificEvidencePanel(modifier: Modifier = Modifier, onOpenExperiment: ((TrainingLabMode) -> Unit)? = null, onOpenDatasetQuality: (() -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { TrainingStorageRepository(context) }
    var runs by remember { mutableStateOf(repository.listRuns()) }
    var selectedRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRun = runs.firstOrNull { it.runId==selectedRunId } ?: runs.firstOrNull()
    val points = remember(selectedRun) { listOfNotNull(selectedRun).toEvidencePoints() }
    val scaleEvidence = remember(points) {
        points.takeIf(List<TrainingEvidencePoint>::isNotEmpty)?.let { available ->
            ScientificEvidenceCalculator.trainingScaleEvidence(
                available.map { point ->
                    TrainingScaleObservation(
                        label = point.id,
                        rowCount = point.rowCount,
                        score = point.macroF1,
                        trainingMillis = point.trainingMillis,
                        peakMemoryBytes = 0L
                    )
                }
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChartSectionCard(
            title = "Scientific evidence centre",
            subtitle =
                "Learning curves, feature-growth evidence and cost/quality trade-offs are reconstructed from stored runs. " +
                    "Future runs retain every arbitrary feature arm, not only the three legacy summaries.",
            modifier = Modifier
                .testTag("training-scientific-evidence-controls")
                .tutorialAnchor(TutorialTargets.TrainingScientificEvidence)
        ) {
            ChartMetricRow("Stored training runs", runs.size.toString())
            ChartMetricRow("Configurations with recorded evidence in selected experiment", points.size.toString())
            Text("Runs are kept separate: a shared feature count or dataset filename does not establish identical test rows, corpus version or training conditions.")
            CompactSelectionMenu(runs, selectedRun, { it.runName }, { selectedRunId=it.runId })
            selectedRun?.let { run -> evidenceExclusions(run).forEach { Text(it) } }
            TrainingDisclosureSection(title="Registered feature contracts",subtitle="A feature count is not a model identity or proof that a configuration was trained.") {
                Text("Frozen source contracts include 108, 130 and 383 variables. Research v2 adds 85 pre-solve nonlinear candidate variables for 468 total; intermediate registered sets remain separate experimental configurations.")
            }
            OutlinedButton(
                onClick = { runs = repository.listRuns() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh stored evidence") }
        }

        if (points.isEmpty() || scaleEvidence == null) {
            ChartSectionCard(
                title = "No stored scale evidence yet",
                subtitle = "Run a controlled comparison or the 108 → 468 campaign to populate these diagrams."
            ) {
                Text("The panel never invents placeholder scores.")
            }
        } else {
            val evidenceExecutionId =
                remember(runs) {
                    "stored-evidence-" +
                        ChartFigureExporter.automaticDataFingerprint(
                            runs.map { run ->
                                listOf(
                                    run.runId,
                                    run.finishedAtEpochMillis,
                                    run.variants
                                )
                            }
                        )
                }
            ProvideAutomaticFigureLibraryContext(
                collection = "scientific-training-evidence",
                analysisId = "stored-training-runs",
                executionId = evidenceExecutionId
            ) {
                TrainingDisclosureSection(
                    title = "Scale and feature-growth evidence",
                    subtitle = "Learning curves, feature-growth evidence and cost/quality trade-offs are reconstructed from stored runs.",
                    initiallyExpanded = true
                ) {
                    TrainingEvidenceCharts(points, scaleEvidence.paretoEfficientLabels)
                    FeatureFamilyAblationEvidence(points)
                }
            }
        }

        selectedRun?.let { com.robotkinematicslab.mobile.ui.training.results.HistoricalTrainingRunCard(it) }
        RobustnessAuditPanel(runs = runs)

        TrainingDisclosureSection(
            title = "Recommended experimental sequence",
            subtitle = "These controls already exist in Single run → feature experiment builder."
        ) {
            val steps=listOf(
                Triple("1 · Frozen growth", "Compare nested feature contracts through 383 inputs under the same split; requires a generated dataset.", TrainingLabMode.CONTROLLED_SINGLE_RUN),
                Triple("2 · Candidate growth", "Test registered candidates beyond 383; a saved plan is not trained evidence.", TrainingLabMode.CONTROLLED_SINGLE_RUN),
                Triple("3 · Family ablation", "Compare a full control with one input family removed; requires matching evaluation conditions.", TrainingLabMode.CONTROLLED_SINGLE_RUN),
                Triple("4 · Robot-held-out generalization", "Hold whole robots outside training to test transfer; requires multiple robot groups.", TrainingLabMode.CONTROLLED_SINGLE_RUN),
                Triple("5 · Reliability", "Inspect discrimination, calibration and cost together for selected saved models.", TrainingLabMode.RESULT_COMPARISON),
                Triple("7 · Explanation", "Inspect feature-family attribution of a stored model; attribution is model sensitivity, not physical causation.", TrainingLabMode.EXPLAINABILITY)
            )
            steps.forEach { (name,objective,destination) ->
                if(name.startsWith("7")) {
                    Text("6 · Coverage / OOD",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    Text("Check how held-out samples differ from training coverage; requires compatible stored samples.")
                    if(onOpenDatasetQuality!=null) OutlinedButton(onClick=onOpenDatasetQuality) { Text("Open experiment · Coverage / OOD") }
                }
                Text(name,style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Text(objective)
                if(onOpenExperiment!=null) OutlinedButton(onClick={onOpenExperiment(destination)}) { Text("Open experiment · $name") }
            }
            Text("Coverage / OOD: open Dataset → Quality → held-out OOD audit. It needs compatible stored samples; distance from a training distribution is not a proof of physical impossibility.")

        }

        TrainingDisclosureSection(
            title = "Evidence boundaries",
            subtitle = "What this application can and cannot claim from these diagrams."
        ) {
            Text("Accuracy / F1",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Measured on untouched test rows")
            Text("Calibration",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Confidence versus observed frequency")
            Text("OOD",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Distance warning, not a physical impossibility proof")
            Text("Attribution",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Model reasoning evidence, not causal physics")
            Text("Energy",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Requires device-supported power instrumentation; never estimated from RAM")
            Text("1 µm",style=androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Text("Must be proven by Cartesian residual, not classification accuracy")
        }
    }
}

@Composable
private fun TrainingEvidenceCharts(
    points: List<TrainingEvidencePoint>,
    paretoIds: Set<String>
) {
    ProfessionalScatterChart(
        title = "Dataset scale versus held-out Macro-F1",
        subtitle = "Each point is one trained, saved configuration in the selected run. Up means higher test F1; right means more effectively used rows. Same-sized configurations remain separate; different models/features prevent a causal scale conclusion.",
        points = points.map { point ->
            ChartPoint(
                x = point.rowCount.toDouble(),
                y = point.macroF1,
                color = profileColor(point.featureCount),
                label = "${point.name} · run ${point.runId} · configuration ${point.selectionId} · ${point.rowCount} effective rows · ${point.featureCount} variables · test F1 ${point.macroF1}"
            )
        },
        legendItems = featureCountLegend(points),
        xAxisLabel = "Rows used",
        yAxisLabel = "Independent-test Macro-F1"
    )

    ProfessionalScatterChart(
        title = "Feature count versus held-out Macro-F1",
        subtitle = "Each point is an actually evaluated input contract. Pearson correlation is descriptive of these observations, with n shown; changing architecture or features is not a controlled causal intervention. Cost is measured separately.",
        points = points.map { point ->
            ChartPoint(
                x = point.featureCount.toDouble(),
                y = point.macroF1,
                color = profileColor(point.featureCount),
                label = "${point.name} · run ${point.runId} · configuration ${point.selectionId} · ${point.featureCount} variables · test F1 ${point.macroF1}"
            )
        },
        legendItems = featureCountLegend(points),
        xAxisLabel = "Input variables",
        yAxisLabel = "Independent-test Macro-F1"
    )

    ProfessionalScatterChart(
        title = "Training cost versus held-out quality",
        subtitle = "Green points are Pareto-efficient: no stored arm is both at least as accurate and no slower.",
        points = points.map { point ->
            ChartPoint(
                x = point.trainingMillis / 1_000.0,
                y = point.macroF1,
                color = if (point.id in paretoIds) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
                label = "${point.name} · ${decimal(point.trainingMillis / 1_000.0)} s · ${percent(point.macroF1)}"
            )
        },
        legendItems = listOf(ScatterLegendItem("Pareto-efficient within this run", "No observed arm is both faster and at least as accurate", Color(0xFF2E7D32)),ScatterLegendItem("Dominated within this run", "Descriptive cost/quality comparison",Color(0xFF9E9E9E))),
        xAxisLabel = "Selected-model training seconds",
        yAxisLabel = "Independent-test Macro-F1"
    )

    points.groupBy(TrainingEvidencePoint::selectionId)
        .values
        .filter { group -> group.map(TrainingEvidencePoint::rowCount).distinct().size >= 2 }
        .sortedByDescending(List<TrainingEvidencePoint>::size)
        .take(6)
        .forEach { group ->
            val ordered = group.sortedBy(TrainingEvidencePoint::rowCount)
            ProfessionalLineChart(
                title = "${ordered.first().name} · learning curve",
                subtitle = "Same stored feature selection across increasing dataset sizes.",
                points = ordered.map { ChartLinePoint(it.rowCount.toDouble(), it.macroF1) },
                xAxisLabel = "Rows used",
                yAxisLabel = "Independent-test Macro-F1",
                color = profileColor(ordered.first().featureCount)
            )
        }

    val latest = points.sortedByDescending(TrainingEvidencePoint::timestamp).take(12)
    ProfessionalHorizontalBarChart(
        metricFormat = com.robotkinematicslab.mobile.ui.charts.presentation.ScientificMetricFormat.BASIS_POINTS,
        title = "Recent independent-test Macro-F1",
        subtitle = "Recent actual model arms; labels preserve feature count and score.",
        items = latest.map { point ->
            ChartBarItem(
                label = "${point.name} · ${point.featureCount} · ${percent(point.macroF1)}",
                value = (point.macroF1 * 10_000.0).toInt(),
                color = profileColor(point.featureCount)
            )
        },
        xAxisLabel = "Macro-F1 (%)"
    )
}

@Composable
private fun FeatureFamilyAblationEvidence(points: List<TrainingEvidencePoint>) {
    val latestAblation = points
        .groupBy(TrainingEvidencePoint::runId)
        .values
        .filter { run ->
            run.any { it.selectionId == "ablation-full-468" } &&
                run.any { it.selectionId.startsWith("ablation-without-") }
        }
        .maxByOrNull { run -> run.maxOf(TrainingEvidencePoint::timestamp) }
        ?: return
    val control = latestAblation.first { it.selectionId == "ablation-full-468" }
    val removed = latestAblation.filter { it.selectionId.startsWith("ablation-without-") }
    val scoreEvidence = removed.map { arm ->
        AblationPoint(
            family = arm.name.substringAfter("Without ").substringBefore(" ·"),
            removedFeatureCount = (control.featureCount - arm.featureCount).coerceAtLeast(0),
            scoreLossPercentagePoints = (control.macroF1 - arm.macroF1) * 100.0,
            trainingSavingPercent = relativeSaving(control.trainingMillis.toDouble(), arm.trainingMillis.toDouble()),
            inferenceSavingPercent = relativeSaving(control.inferenceNanosPerSample, arm.inferenceNanosPerSample)
        )
    }

    ChartSectionCard(
        title = "Latest paired feature-family ablation",
        subtitle = "Every point shares one stored run, seed, split and 468-variable control. Positive score loss means the removed family helped."
    ) {
        ChartMetricRow("Control Macro-F1", percent(control.macroF1))
        ChartMetricRow("Families tested", scoreEvidence.size.toString())
        ChartMetricRow("Helpful when present", scoreEvidence.count { it.scoreLossPercentagePoints > ABLATION_NEUTRAL_PP }.toString())
        ChartMetricRow("Neutral within ±0.05 pp", scoreEvidence.count { kotlin.math.abs(it.scoreLossPercentagePoints) <= ABLATION_NEUTRAL_PP }.toString())
        ChartMetricRow("Removal improved score", scoreEvidence.count { it.scoreLossPercentagePoints < -ABLATION_NEUTRAL_PP }.toString())
    }

    ProfessionalScatterChart(
        title = "Feature-family ablation effect",
        subtitle = "Signed independent-test Macro-F1 change after removing exactly one family. This is controlled ablation evidence, not attribution.",
        points = scoreEvidence.map { evidence ->
            ChartPoint(
                x = evidence.removedFeatureCount.toDouble(),
                y = evidence.scoreLossPercentagePoints,
                color = when {
                    evidence.scoreLossPercentagePoints > ABLATION_NEUTRAL_PP -> Color(0xFF2E7D32)
                    evidence.scoreLossPercentagePoints < -ABLATION_NEUTRAL_PP -> Color(0xFFC62828)
                    else -> Color(0xFF607D8B)
                },
                label = "${evidence.family} · ${evidence.removedFeatureCount} removed · ${signedDecimal(evidence.scoreLossPercentagePoints)} pp"
            )
        },
        xAxisLabel = "Variables removed",
        yAxisLabel = "Control − ablated Macro-F1 (pp)",
        directionOverride = ChartReadingDirection.TARGET_DEPENDENT
    )

    val costEvidence = scoreEvidence.filter {
        it.trainingSavingPercent.isFinite() && it.inferenceSavingPercent.isFinite()
    }
    if (costEvidence.isNotEmpty()) {
        ProfessionalScatterChart(
            title = "Ablation cost savings",
            subtitle = "Positive axes mean the smaller arm was faster. A family earns its cost only when read together with the score-loss chart.",
            points = costEvidence.map { evidence ->
                ChartPoint(
                    x = evidence.inferenceSavingPercent,
                    y = evidence.trainingSavingPercent,
                    color = Color(0xFF1565C0),
                    label = "${evidence.family} · inference ${signedDecimal(evidence.inferenceSavingPercent)}% · training ${signedDecimal(evidence.trainingSavingPercent)}%"
                )
            },
            xAxisLabel = "Inference time saved (%)",
            yAxisLabel = "Training time saved (%)"
        )
    }
}

internal data class TrainingEvidencePoint(
    val id: String,
    val runId: String,
    val selectionId: String,
    val name: String,
    val rowCount: Int,
    val featureCount: Int,
    val macroF1: Double,
    val trainingMillis: Long,
    val inferenceNanosPerSample: Double,
    val timestamp: Long
)

private data class AblationPoint(
    val family: String,
    val removedFeatureCount: Int,
    val scoreLossPercentagePoints: Double,
    val trainingSavingPercent: Double,
    val inferenceSavingPercent: Double
)

internal fun evidenceExclusions(run:TrainingRunSummary):List<String> {
    if(run.variants.isEmpty()) return listOf("Legacy run excluded from scale plots: exact configuration row counts and fit timings were not recorded.")
    return run.variants.mapNotNull { variant ->
        val rows=variant.trainRowCount.toLong()+variant.validationRowCount+variant.testRowCount
        if(variant.testMacroF1 !in 0.0..1.0 || variant.trainRowCount<0 || variant.validationRowCount<0 || variant.testRowCount<=0 || rows>Int.MAX_VALUE || variant.featureCount<=0 || variant.trainingDurationMillis<0) "${variant.featureSelectionName} excluded: missing or invalid rows, feature count, score or timing." else null
    }
}
internal fun List<TrainingRunSummary>.toEvidencePoints():List<TrainingEvidencePoint> = flatMap { run ->
    run.variants.filter { variant ->
        variant.testMacroF1 in 0.0..1.0 && variant.trainRowCount>=0 && variant.validationRowCount>=0 && variant.testRowCount>0 && variant.trainRowCount.toLong()+variant.validationRowCount+variant.testRowCount<=Int.MAX_VALUE && variant.featureCount>0 && variant.trainingDurationMillis>=0
    }.map { run.point(it) }
}
private fun featureCountLegend(points:List<TrainingEvidencePoint>):List<ScatterLegendItem> = points.groupBy { profileColor(it.featureCount) }.map { (color,group) ->
    ScatterLegendItem("Input variable counts: ${group.map { it.featureCount }.distinct().sorted().joinToString()}", "${group.size} recorded configurations",color)
}

private fun TrainingRunSummary.point(variant: StoredTrainingVariantEvidence) =
    TrainingEvidencePoint(
        id = "$runId:${variant.featureSelectionId}",
        runId = runId,
        selectionId = variant.featureSelectionId,
        name = variant.featureSelectionName,
        rowCount = variant.trainRowCount + variant.validationRowCount + variant.testRowCount,
        featureCount = variant.featureCount,
        macroF1 = variant.testMacroF1,
        trainingMillis = variant.trainingDurationMillis,
        inferenceNanosPerSample = variant.inferenceNanosPerSample,
        timestamp = startedAtEpochMillis
    )

private fun TrainingRunSummary.legacyPoint(
    selectionId: String,
    name: String,
    featureCount: Int,
    score: Double
) = TrainingEvidencePoint(
    id = "$runId:$selectionId",
    runId = runId,
    selectionId = selectionId,
    name = name,
    rowCount = maximumRows,
    featureCount = featureCount,
    macroF1 = score,
    trainingMillis = (finishedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0L),
    inferenceNanosPerSample = Double.NaN,
    timestamp = startedAtEpochMillis
)

private fun relativeSaving(control: Double, reduced: Double): Double =
    if (control.isFinite() && reduced.isFinite() && control > 0.0) (control - reduced) / control * 100.0 else Double.NaN

private fun profileColor(featureCount: Int): Color =
    when {
        featureCount <= 108 -> Color(0xFF546E7A)
        featureCount <= 130 -> Color(0xFF2E7D32)
        featureCount <= 383 -> Color(0xFF6A1B9A)
        else -> Color(0xFFAD1457)
    }

private fun decimal(value: Double): String = String.format(Locale.US, "%.3f", value)
private fun signedDecimal(value: Double): String = String.format(Locale.US, "%+.3f", value)
private fun percent(value: Double): String = String.format(Locale.US, "%.2f%%", value * 100.0)
private const val ABLATION_NEUTRAL_PP = 0.05
