package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.ui.shared.CompactSelectionMenu
import com.robotkinematicslab.mobile.ui.shared.AccessibleSelectionButton
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import java.util.Locale

@Composable
fun DiagnosticInputCard(
    reachableCountText: String,
    onReachableCountChange: (String) -> Unit,

    unreachableCountText: String,
    onUnreachableCountChange: (String) -> Unit,

    robotLinkCountText: String,
    onRobotLinkCountChange: (String) -> Unit,

    minLinkCountText: String,
    onMinLinkCountChange: (String) -> Unit,

    maxLinkCountText: String,
    onMaxLinkCountChange: (String) -> Unit,

    samplesPerLinkCountText: String,
    onSamplesPerLinkCountChange: (String) -> Unit,

    unlimitedSampleCountText: String,
    onUnlimitedSampleCountChange: (String) -> Unit,

    seedText: String,
    onSeedChange: (String) -> Unit,

    experimentalMode: Boolean,
    onExperimentalModeChange: (Boolean) -> Unit,

    unlimitedSamplesEnabled: Boolean,
    onUnlimitedSamplesEnabledChange: (Boolean) -> Unit,

    manualRangeMode: Boolean,
    onManualRangeModeChange: (Boolean) -> Unit,

    selectedJointMode: DiagnosticJointMode,
    onJointModeChange: (DiagnosticJointMode) -> Unit,

    runAllTopologies: Boolean,
    onRunAllTopologiesChange: (Boolean) -> Unit,

    stressLevel: Float,
    onStressLevelChange: (Float) -> Unit,

    ikMaxIterationsText: String,
    onIkMaxIterationsChange: (String) -> Unit,

    ikToleranceText: String,
    onIkToleranceChange: (String) -> Unit,

    ikDampingText: String,
    onIkDampingChange: (String) -> Unit,

    ikMaxStepText: String,
    onIkMaxStepChange: (String) -> Unit,

    onUseAutoBenchmarkPreset: () -> Unit,
    onUseBalancedPreset: () -> Unit,
    onUsePrecisionPreset: () -> Unit,
    onUseExplorationPreset: () -> Unit,
    isRunning: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    setupEvaluation: DiagnosticSetupEvaluation? = null
) {
    val checked = setupEvaluation ?: DiagnosticSetupContract.evaluate(com.robotkinematicslab.mobile.storage.DiagnosticDraft(
        reachableCountText = reachableCountText, unreachableCountText = unreachableCountText,
        robotLinkCountText = robotLinkCountText, minLinkCountText = minLinkCountText, maxLinkCountText = maxLinkCountText,
        samplesPerLinkCountText = samplesPerLinkCountText, unlimitedSampleCountText = unlimitedSampleCountText,
        seedText = seedText, experimentalMode = experimentalMode, unlimitedSamplesEnabled = unlimitedSamplesEnabled,
        manualRangeMode = manualRangeMode, jointMode = selectedJointMode, runAllTopologies = runAllTopologies,
        stressLevel = stressLevel, ikMaxIterationsText = ikMaxIterationsText, ikToleranceText = ikToleranceText,
        ikDampingText = ikDampingText, ikMaxStepText = ikMaxStepText))
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("diagnostics-configuration")
                .tutorialAnchor(TutorialTargets.DiagnosticsConfiguration),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Experiment Setup",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onUseAutoBenchmarkPreset,
                enabled = !isRunning,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics-presets")
                        .tutorialAnchor(TutorialTargets.DiagnosticsPresets)
            ) {
                Text("Apply full Auto benchmark defaults")
            }

            Text("Full Auto preset: 2–10 links × 500 samples × 5 distinct seeds × 1 AUTO topology = 22,500 sequential runs, plus 180 reachable-target oracle checks. Solver presets below change only four IK settings.", style = MaterialTheme.typography.bodySmall)
            DiagnosticSetupSection(
                title = "Robot range and experiment limits",
                subtitle =
                    (if (manualRangeMode) {
                        "$minLinkCountText–$maxLinkCountText links"
                    } else {
                        "$robotLinkCountText links"
                    }) + if (experimentalMode) " · experimental" else " · safe mode",
                testTag = "diagnostics-section-robot-range"
            ) {
            Text("Safe mode permits 2–10 links. Experimental permits 2–100 (including 11+). These are algorithmic support ranges, not a hardware safety certification.", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manual link range")

                Switch(
                    checked = manualRangeMode,
                    enabled = !isRunning,
                    onCheckedChange = onManualRangeModeChange
                )
            }

            if (manualRangeMode) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
OutlinedTextField(
                    value = minLinkCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Minimum links"),
                supportingText = checked.errors["Minimum links"]?.let { error -> { Text(error) } },
                    onValueChange = onMinLinkCountChange,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            if (experimentalMode) {
                                "Min links (2–100)"
                            } else {
                                "Min links (2–10)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = maxLinkCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Maximum links"),
                supportingText = checked.errors["Maximum links"]?.let { error -> { Text(error) } },
                    onValueChange = onMaxLinkCountChange,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            if (experimentalMode) {
                                "Max links (2–100)"
                            } else {
                                "Max links (2–10)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
            } else {
                OutlinedTextField(
                    value = robotLinkCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Robot links"),
                supportingText = checked.errors["Robot links"]?.let { error -> { Text(error) } },
                    onValueChange = onRobotLinkCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (experimentalMode) {
                                "Robot link count, 2 to 100"
                            } else {
                                "Robot link count, 2 to 10"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Experimental mode")

                Switch(
                    checked = experimentalMode,
                    enabled = !isRunning,
                    onCheckedChange = onExperimentalModeChange
                )
            }

            if (experimentalMode) {
                Text(
                    text = "Experimental mode allows 11+ links. These results are marked experimental only.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            }

            DiagnosticSetupSection(
                title = "Targets, samples and seeds",
                subtitle = "$reachableCountText reachable · $unreachableCountText unreachable · seed $seedText",
                testTag = "diagnostics-section-sampling"
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extended sample mode (1–1,000,000)")

                Switch(
                    checked = unlimitedSamplesEnabled,
                    onCheckedChange = onUnlimitedSamplesEnabledChange,
                    enabled = experimentalMode && !isRunning
                )
            }

            if (unlimitedSamplesEnabled) {
                Text(
                    text = "Warning: unlimited sample mode can take a long time and may affect app responsiveness.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
OutlinedTextField(
                value = reachableCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Reachable targets"),
                supportingText = checked.errors["Reachable targets"]?.let { error -> { Text(error) } },
                onValueChange = onReachableCountChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Reachable target count")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            OutlinedTextField(
                value = unreachableCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Unreachable targets"),
                supportingText = checked.errors["Unreachable targets"]?.let { error -> { Text(error) } },
                onValueChange = onUnreachableCountChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Unreachable target count")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            }

            OutlinedTextField(
                value = samplesPerLinkCountText,
                enabled = !isRunning && !unlimitedSamplesEnabled,
                isError = checked.errors.containsKey("Samples per link"),
                supportingText = checked.errors["Samples per link"]?.let { error -> { Text(error) } },
                onValueChange = onSamplesPerLinkCountChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Samples per link count, normal range 1 to 5,000")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            if (unlimitedSamplesEnabled) {
                OutlinedTextField(
                    value = unlimitedSampleCountText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Extended samples"),
                supportingText = checked.errors["Extended samples"]?.let { error -> { Text(error) } },
                    onValueChange = onUnlimitedSampleCountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Extended sample count, 1 to 1,000,000")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }

            OutlinedTextField(
                value = seedText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("Seeds"),
                supportingText = checked.errors["Seeds"]?.let { error -> { Text(error) } },
                onValueChange = onSeedChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Seeds, comma separated")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default
            )

            JargonAwareText(
                text = "Example seeds: 42, 101, 202. Each distinct seed multiplies the experiment; duplicates count once and invalid tokens block the plan.",
                style = MaterialTheme.typography.bodySmall
            )
            JargonAwareText(
                text = "A seed repeats the same pseudo-random choices, so using several seeds shows whether the result depends on luck.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }

            DiagnosticSetupSection(
                title = "Joint topology and stress",
                subtitle = "${selectedJointMode.name} · stress ${String.format(Locale.US, "%.2f", stressLevel)}",
                testTag = "diagnostics-section-topology"
            ) {
            Text(
                text = "Joint Mode",
                style = MaterialTheme.typography.titleSmall
            )

            DiagnosticJointModeSelector(selectedJointMode, onJointModeChange, enabled = !isRunning)
            Text(
                text = "Selected joint mode: ${selectedJointMode.name}. AUTO derives joint types from link count; it is a fourth benchmark mode when all topologies are enabled.",
                style = MaterialTheme.typography.bodySmall
            )

            JargonAwareText(
                text = "Topology means the robot's link count and joint-type pattern. Running all topologies checks whether one structure hides a failure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Run all topologies")

                Spacer(modifier = Modifier.width(12.dp))

                Switch(
                    checked = runAllTopologies,
                    enabled = !isRunning,
                    onCheckedChange = onRunAllTopologiesChange
                )
            }

            Text(
                text = "Stress Level: ${
                    String.format(
                        Locale.US,
                        "%.2f",
                        stressLevel
                    )
                }",
                style = MaterialTheme.typography.bodyMedium
            )

            Text("Geometry stress, 0–1: 0 uses the least perturbed geometry; 1 uses the strongest built-in geometry/limit modifications. 0.50 is halfway along this input scale, not 50% device risk. Twist patterns change at 0.20 and 0.50; lengths and joint ranges also vary. Slider color identifies selection only.", style = MaterialTheme.typography.bodySmall)
            Slider(
                enabled = !isRunning,
                value = stressLevel,
                onValueChange = onStressLevelChange,
                valueRange = 0.0f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )
            }

            DiagnosticSetupSection(
                title = "IK solver settings",
                subtitle = "$ikMaxIterationsText iterations · tolerance $ikToleranceText",
                testTag = "diagnostics-section-solver"
            ) {
            JargonAwareText(
                text = "DLS repeatedly reduces the residual, or final error. Tolerance is the largest final error accepted as success; maximum iterations limits attempts, and maximum step limits each move.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
OutlinedTextField(
                value = ikMaxIterationsText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("IK iterations"),
                supportingText = checked.errors["IK iterations"]?.let { error -> { Text(error) } },
                onValueChange = onIkMaxIterationsChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Iterations")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            OutlinedTextField(
                value = ikToleranceText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("IK tolerance (m)"),
                supportingText = checked.errors["IK tolerance (m)"]?.let { error -> { Text(error) } },
                onValueChange = onIkToleranceChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Tolerance (m)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
OutlinedTextField(
                value = ikDampingText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("IK damping"),
                supportingText = checked.errors["IK damping"]?.let { error -> { Text(error) } },
                onValueChange = onIkDampingChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Damping")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )

            OutlinedTextField(
                value = ikMaxStepText,
                enabled = !isRunning,
                isError = checked.errors.containsKey("IK maximum step"),
                supportingText = checked.errors["IK maximum step"]?.let { error -> { Text(error) } },
                onValueChange = onIkMaxStepChange,
                modifier = Modifier.weight(1f),
                label = {
                    Text("Max joint step")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                )
            )
            }

            CompactSelectionMenu(options = DiagnosticSolverPreset.entries,
                selected = DiagnosticSolverPreset.entries.firstOrNull { it.config.ikMaxIterations.toString() == ikMaxIterationsText.trim() &&
                    it.config.ikTolerance == com.robotkinematicslab.mobile.ui.input.ScientificNumberParser.parseDouble(ikToleranceText) &&
                    it.config.ikDamping == com.robotkinematicslab.mobile.ui.input.ScientificNumberParser.parseDouble(ikDampingText) &&
                    it.config.ikMaxStep == com.robotkinematicslab.mobile.ui.input.ScientificNumberParser.parseDouble(ikMaxStepText) },
                label = { it.label }, enabled = !isRunning,
                onSelected = { when(it) {
                    DiagnosticSolverPreset.BALANCED -> onUseBalancedPreset()
                    DiagnosticSolverPreset.PRECISION -> onUsePrecisionPreset()
                    DiagnosticSolverPreset.EXPLORATION -> onUseExplorationPreset()
                } })
            Text("Solver presets change iterations/tolerance/damping/maximum step only; they do not change targets, links or seeds. Tolerance: metres, 1e−9–1; damping: 1e−9–10 in this DLS formulation; maximum joint step: 1e−6–10 radians for revolute or metres for prismatic coordinates. Iteration budget: 1–10,000. More iterations or smaller tolerance can add work without guaranteeing convergence.", style = MaterialTheme.typography.bodySmall)
            }

            DiagnosticSetupSection(
                title = "Run preview",
                subtitle = "Exact planned counts; qualitative workload category",
                testTag = "diagnostics-section-preview"
            ) {
                DiagnosticValidatedPreview(checked)
                DiagnosticSetupSection("Reproducibility details", "Seed protocol and experiment scope", "diagnostics-protocol") {
                    Text("Random protocol: ${ScientificRandomProtocol.ID}. Fixed seeds and settings reproduce pseudo-random choices; they do not establish comparability across different devices or solver configurations.")
                }

            }

            if (isRunning) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("diagnostics-cancel")
                            .tutorialAnchor(TutorialTargets.DiagnosticsCancel)
                ) {
                    Text("Cancel Diagnostic Experiment")
                }
            } else {
                checked.errors.values.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onRun,
                    enabled = checked.canRun,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("diagnostics-run")
                            .tutorialAnchor(TutorialTargets.DiagnosticsRun)
                ) {
                    Text("Run Diagnostic Experiment")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSetupSection(title: String, subtitle: String, testTag: String,
    initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    AppDisclosureSection(title = title, summary = subtitle, initiallyExpanded = initiallyExpanded, testTag = testTag) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun DiagnosticJointModeSelector(selected: DiagnosticJointMode, onSelected: (DiagnosticJointMode) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DiagnosticJointMode.entries.forEach { mode ->
            AccessibleSelectionButton(selected = selected == mode, label = when(mode) {
                DiagnosticJointMode.AUTO -> "AUTO"; DiagnosticJointMode.REVOLUTE_ONLY -> "REV"
                DiagnosticJointMode.PRISMATIC_ONLY -> "PRISM"; DiagnosticJointMode.MIXED -> "MIXED"
            }, enabled = enabled, maxLines = 1, onClick = { onSelected(mode) }, modifier = Modifier.weight(1f).testTag("diagnostic-mode-${mode.name}"))
        }
    }
}

@Composable
internal fun DiagnosticValidatedPreview(checked: DiagnosticSetupEvaluation) {
    if(!checked.canRun) { Text("Plan unavailable: " + checked.errors.values.joinToString(" "), color = MaterialTheme.colorScheme.error); return }
    val plan = requireNotNull(checked.plan)
    val category = estimateRuntimeRisk(plan.totalPlannedSequentialRuns, plan.isExperimental, plan.isUnlimited)
    Text(checked.breakdown)
    Text("Workload category: $category. This heuristic uses run count and mode flags, not measured device temperature or a time prediction. No calibrated duration is available.")
    Text("Criteria: LOW ≤10,000; MEDIUM >10,000; HIGH >50,000 or Experimental; EXTREME >100,000 or extended samples.")
}

@Composable
fun InputRunPreviewCard(
    minLinkCountText: String,
    maxLinkCountText: String,
    robotLinkCountText: String,
    samplesPerLinkCountText: String,
    unlimitedSampleCountText: String,
    seedText: String,
    manualRangeMode: Boolean,
    runAllTopologies: Boolean,
    experimentalMode: Boolean,
    unlimitedSamplesEnabled: Boolean
) {
    DiagnosticValidatedPreview(DiagnosticSetupContract.evaluate(com.robotkinematicslab.mobile.storage.DiagnosticDraft(
        minLinkCountText = minLinkCountText, maxLinkCountText = maxLinkCountText,
        robotLinkCountText = robotLinkCountText, samplesPerLinkCountText = samplesPerLinkCountText,
        unlimitedSampleCountText = unlimitedSampleCountText, seedText = seedText,
        manualRangeMode = manualRangeMode, runAllTopologies = runAllTopologies,
        experimentalMode = experimentalMode, unlimitedSamplesEnabled = unlimitedSamplesEnabled)))
}
