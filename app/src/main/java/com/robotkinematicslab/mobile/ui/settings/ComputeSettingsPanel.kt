package com.robotkinematicslab.mobile.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.robotkinematicslab.mobile.performance.compute.AndroidDeviceComputeProfiler
import com.robotkinematicslab.mobile.performance.compute.ComputeResourcePreset
import com.robotkinematicslab.mobile.performance.compute.ComputeResourceSettings
import com.robotkinematicslab.mobile.performance.compute.ComputeSettingsRepository
import com.robotkinematicslab.mobile.performance.compute.SafeComputePolicyResolver
import com.robotkinematicslab.mobile.process.ResearchProcessNotificationPublisher
import com.robotkinematicslab.mobile.ui.accessibility.AppAccessibilityPreferences
import com.robotkinematicslab.mobile.ui.accessibility.AppContentScale
import com.robotkinematicslab.mobile.ui.accessibility.LocalReadAloudController
import com.robotkinematicslab.mobile.ui.accessibility.ReadAloudResult
import com.robotkinematicslab.mobile.ui.charts.basic.ChartMetricRow
import com.robotkinematicslab.mobile.ui.charts.basic.ChartSectionCard
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationSettingsContent
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController
import com.robotkinematicslab.mobile.ui.help.ResearchGlossaryDialog
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.help.JargonHelpNotice
import com.robotkinematicslab.mobile.ui.diagnostic.DiagnosticDisclosureCard
import com.robotkinematicslab.mobile.ui.onboarding.LocalTutorialActionReporter
import com.robotkinematicslab.mobile.ui.onboarding.TutorialActionResult
import com.robotkinematicslab.mobile.ui.onboarding.TutorialInteraction
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.report
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadBadge
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadChoiceButton
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadClassifier
import com.robotkinematicslab.mobile.ui.shared.resources.ResourceLoadLegend
import com.robotkinematicslab.mobile.ui.theme.AppVisualThemePreferences
import java.util.Locale

@Composable
fun ComputeSettingsPanel(
    visualTheme: AppVisualThemePreferences,
    onVisualThemeChange: (AppVisualThemePreferences) -> Unit,
    accessibilityPreferences: AppAccessibilityPreferences,
    onAccessibilityPreferencesChange: (AppAccessibilityPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    val tutorialReporter = LocalTutorialActionReporter.current
    val repository = remember(context) { ComputeSettingsRepository(context) }
    val profiler = remember(context) { AndroidDeviceComputeProfiler(context) }
    var settings by remember { mutableStateOf(repository.load()) }
    var device by remember { mutableStateOf(profiler.read()) }
    var glossaryVisible by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("Settings are applied when the next training run starts.") }
    var notificationState by remember { mutableStateOf(readResearchNotificationState(context)) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationState = readResearchNotificationState(context)
            tutorialReporter.report(
                target = TutorialTargets.SettingsNotifications,
                interaction = TutorialInteraction.CHOOSE,
                result = if (notificationState.appAllowed) TutorialActionResult.COMPLETED else TutorialActionResult.REJECTED,
                detail = if (notificationState.appAllowed) "Notification permission granted." else "Notification permission remains blocked."
            )
        }
    val notificationSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            notificationState = readResearchNotificationState(context)
            val deliveryReady =
                notificationState.appAllowed &&
                    notificationState.progressChannelAllowed &&
                    notificationState.resultChannelAllowed
            tutorialReporter.report(
                target = TutorialTargets.SettingsNotifications,
                interaction = TutorialInteraction.CHOOSE,
                result = if (deliveryReady) TutorialActionResult.COMPLETED else TutorialActionResult.REJECTED,
                detail = if (deliveryReady) "Research notification delivery verified." else "One or more notification channels remain blocked."
            )
        }
    val policy = remember(settings, device) { SafeComputePolicyResolver.resolve(settings, device) }
    val chartPresentation = LocalChartPresentationController.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("settings-root")
                .tutorialAnchor(TutorialTargets.Settings),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        JargonHelpNotice()
        Text(
            "Personalise the research console and control local training resources. " +
                "Scientific calculations are never changed by appearance settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppearanceSettingsCard(
            preferences = visualTheme,
            onPreferencesChange = onVisualThemeChange
        )

        DiagnosticDisclosureCard(
            title = "Accessibility",
            subtitle = "Text size, contrast, motion and read-aloud assistance",
            modifier = Modifier.testTag("settings-accessibility-disclosure")
        ) {
            AccessibilitySettingsCard(
                preferences = accessibilityPreferences,
                onPreferencesChange = onAccessibilityPreferencesChange
            )
        }

        DiagnosticDisclosureCard(
            title = "Chart appearance & save",
            subtitle = "Chart style, labels, grids and saved-image appearance",
            modifier = Modifier.testTag("settings-figures-disclosure")
        ) {
        ChartSectionCard(
            title = "Chart appearance & save",
            subtitle =
                "One persistent display contract for diagnostic, telemetry, training and comparison charts. It changes presentation only; stored values and calculations remain untouched.",
            guide = null,
            modifier =
                Modifier
                    .testTag("settings-presentation")
                    .tutorialAnchor(TutorialTargets.SettingsPresentation)
        ) {
            ChartPresentationSettingsContent(
                preferences = chartPresentation.preferences,
                onPreferencesChange = chartPresentation.update
            )
        }
        }

        DiagnosticDisclosureCard(
            title = "Long-process notifications",
            subtitle = if (notificationState.appAllowed) "Android delivery allowed" else "Android delivery blocked",
            modifier = Modifier.testTag("settings-notifications-disclosure")
        ) {
        ChartSectionCard(
            title = "Research glossary",
            subtitle = "Look up the scientific language used throughout the application without leaving Settings.",
            modifier =
                Modifier
                    .testTag("settings-help")
                    .tutorialAnchor(TutorialTargets.SettingsHelp)
        ) {
            OutlinedButton(
                onClick = {
                    glossaryVisible = true
                    tutorialReporter.report(
                        target = TutorialTargets.Glossary,
                        interaction = TutorialInteraction.TAP,
                        detail = "Research glossary opened."
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("open-research-glossary")
                        .tutorialAnchor(
                            targetId = TutorialTargets.Glossary,
                            actionEnabled = false
                        )
            ) {
                Text("Open research glossary")
            }
            Text(
                "Definitions describe measurement meaning and interpretation; they do not alter stored results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ChartSectionCard(
            title = "Long-process notifications",
            subtitle =
                "One visible safety contract for every user-started dataset, diagnostic, training, analysis, workspace and import process.",
            modifier =
                Modifier
                    .testTag("settings-process-notifications")
                    .tutorialAnchor(TutorialTargets.SettingsNotifications)
        ) {
            ChartMetricRow("Android delivery", if (notificationState.appAllowed) "Allowed" else "Blocked")
            ChartMetricRow("Live progress channel", if (notificationState.progressChannelAllowed) "Enabled" else "Blocked")
            ChartMetricRow("Completion alerts", if (notificationState.resultChannelAllowed) "Enabled" else "Blocked")
            ChartMetricRow("In-app activity centre", "Always available")
            ChartMetricRow("Lock-screen detail", "Private until unlocked")
            Text(
                "Changing screen or temporarily using another app keeps active work visible. Removing the app from Recents requests a safe stop; force-stop or an operating-system kill ends in-flight work, while already committed checkpoints remain stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Notifications contain only a private process title and status. Full measurements, paths and error details stay inside the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                Button(
                    onClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("request-process-notification-permission")
                ) {
                    Text("Request notification permission")
                }
            }
            OutlinedButton(
                onClick = {
                    notificationSettingsLauncher.launch(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("open-process-notification-settings")
            ) {
                Text("Open Android notification controls")
            }
        }
        }

        DiagnosticDisclosureCard(
            title = "Scientific data and safety",
            subtitle = "Offline storage, exports and protected numerical paths",
            modifier = Modifier.testTag("settings-safety-disclosure")
        ) {
        ChartSectionCard(
            title = "Scientific data & safety contract",
            subtitle =
                "What the installed application protects automatically, and which responsibilities remain with the researcher.",
            modifier =
                Modifier
                    .testTag("settings-safety-contract")
                    .tutorialAnchor(TutorialTargets.SettingsSafetyContract)
        ) {
            ChartMetricRow("Network permission", "Not requested")
            ChartMetricRow("Scientific project files", "Excluded from automatic backup / transfer")
            ChartMetricRow("External exports", "Explicit user action only")
            ChartMetricRow("Production numerical paths", "Protected and validated")
            Text(
                "The deliberately unguarded solver exists only inside the labelled Mathematical Safety A/B Lab as a negative control. It is not selected by Robot Lab, dataset generation or model verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "App-local storage is not a dissertation backup. Export the evidence you must retain before uninstalling, clearing app data or changing device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        }

        DiagnosticDisclosureCard(
            title = "Device and compute resources",
            subtitle = "${policy.effectiveWorkerCount} worker(s) · ${formatBytes(policy.workingMemoryBudgetBytes)} working budget",
            modifier = Modifier.testTag("settings-compute-disclosure")
        ) {
        JargonAwareText(
            "A worker is one parallel calculation slot. More workers may finish sooner, but they use more app heap and create more heat; the safe ceiling preserves thermal headroom and keeps the interface responsive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChartSectionCard(
            title = "This device",
            subtitle = "Live capabilities reported by Android. Refresh after the phone warms up or other apps change memory use."
        ) {
            ChartMetricRow("Device", device.deviceName.ifBlank { "Android device" })
            ChartMetricRow("Logical CPU cores", device.logicalCpuCores.toString())
            ChartMetricRow("Reserved for Android/UI", policy.reservedCpuCores.toString())
            ChartMetricRow("Safe worker ceiling", policy.safeMaximumWorkers.toString())
            ChartMetricRow("Physical RAM", formatBytes(device.totalSystemMemoryBytes))
            ChartMetricRow("Currently available", formatBytes(device.availableSystemMemoryBytes))
            ChartMetricRow("App heap limit", formatBytes(policy.appHeapLimitBytes))
            ChartMetricRow("Thermal state", device.thermalLevel.name)
            ChartMetricRow(
                "Android performance hints",
                if (device.performanceHintsSupported) "Active for training" else "Unavailable on this device"
            )
            if (device.thermalHeadroom.isFinite()) {
                ChartMetricRow(
                    "Thermal pressure forecast (10 s)",
                    String.format(Locale.US, "%.0f%%", device.thermalHeadroom * 100.0)
                )
            }
            OutlinedButton(
                onClick = { device = profiler.read() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh device status") }
        }

        ChartSectionCard(
            title = "CPU policy",
            subtitle = "This dissertation build keeps long calculations responsive and thermally bounded on unknown Android devices.",
            modifier =
                Modifier
                    .testTag("settings-compute-policy")
                    .tutorialAnchor(TutorialTargets.SettingsComputePolicy)
        ) {
            ResourceLoadLegend(modifier = Modifier.fillMaxWidth())
            listOf(ComputeResourcePreset.ECO, ComputeResourcePreset.BALANCED).forEach { preset ->
                val previewPolicy = SafeComputePolicyResolver.resolve(settings.copy(preset = preset), device)
                val loadLevel =
                    ResourceLoadClassifier.classify(
                        value = previewPolicy.effectiveWorkerCount,
                        minimum = 1,
                        maximum = previewPolicy.safeMaximumWorkers
                    )
                ResourceLoadChoiceButton(
                    selected = settings.preset == preset,
                    label = preset.displayName,
                    level = loadLevel,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (settings.preset != preset) {
                            settings = settings.copy(preset = preset)
                            tutorialReporter.report(
                                target = TutorialTargets.SettingsComputePolicy,
                                interaction = TutorialInteraction.CHOOSE,
                                detail = "Compute preset selected: ${preset.name}."
                            )
                        }
                    }
                )
                if (settings.preset == preset) {
                    Text(preset.description, style = MaterialTheme.typography.bodySmall)
                }
            }

            ChartMetricRow("Workers next run", policy.effectiveWorkerCount.toString())
            ResourceLoadBadge(
                level =
                    ResourceLoadClassifier.classify(
                        value = policy.effectiveWorkerCount,
                        minimum = 1,
                        maximum = policy.safeMaximumWorkers
                    ),
                prefix = "${policy.effectiveWorkerCount}/${policy.safeMaximumWorkers} safe workers"
            )
            Text(policy.safetyMessage, color = safetyColor(policy.lowMemory, policy.thermalLevel.name))
        }

        ChartSectionCard(
            title = "Working memory",
            subtitle =
                "Android does not grant an app direct control of all physical RAM. " +
                    "This percentage applies only to the app's own heap allowance.",
            modifier =
                Modifier
                    .testTag("settings-working-memory")
                    .tutorialAnchor(TutorialTargets.SettingsWorkingMemory)
        ) {
            ResourceLoadLegend(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(20, 35).forEach { percent ->
                    ResourceLoadChoiceButton(
                        selected = settings.requestedWorkingMemoryPercent == percent,
                        label = "$percent%",
                        level = ResourceLoadClassifier.classify(percent, 20, 35),
                        onClick = { settings = settings.copy(requestedWorkingMemoryPercent = percent) },
                        modifier = Modifier.weight(1f),
                        showLevelInLabel = false
                    )
                }
            }
            val effectiveMemoryPercent = settings.requestedWorkingMemoryPercent.coerceAtMost(35)
            ResourceLoadBadge(
                level = ResourceLoadClassifier.classify(effectiveMemoryPercent, 20, 35),
                prefix = "$effectiveMemoryPercent% effective heap allowance"
            )
            ChartMetricRow("Effective working budget", formatBytes(policy.workingMemoryBudgetBytes))
            ChartMetricRow("Estimated safe training-row cap", policy.estimatedSafeTrainingRows.toString())
            Text(
                "This compute profile never requests more than 35% of the app heap. Android and current system pressure may lower the effective budget further.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(14.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Always-on protection", style = MaterialTheme.typography.titleMedium)
            Text("✓ At most two workers are permitted, with Android/UI headroom retained")
            Text("✓ Severe thermal pressure forces single-worker execution")
            Text("✓ Android low-memory state forces single-worker execution")
            Text("✓ The working-memory request is capped at 35% of the app heap")
            Text(
                "High-throughput and custom modes are unavailable in this dissertation build because sustained device testing showed thermal and interface-responsiveness risk. These protections are intentionally not switchable.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        }

        Button(
            onClick = {
                settings = repository.save(settings)
                device = profiler.read()
                savedMessage = "Saved. The next training run will use ${policy.effectiveWorkerCount} worker(s)."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save compute settings") }
        OutlinedButton(
            onClick = {
                settings = repository.save(ComputeResourceSettings())
                device = profiler.read()
                savedMessage = "Balanced safe defaults restored."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restore safe defaults") }
        Text(savedMessage)
    }

    ResearchGlossaryDialog(
        visible = glossaryVisible,
        onDismiss = { glossaryVisible = false }
    )
}

@Composable
private fun AccessibilitySettingsCard(
    preferences: AppAccessibilityPreferences,
    onPreferencesChange: (AppAccessibilityPreferences) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val readAloudController = LocalReadAloudController.current
    val accessibilityManager =
        remember(context) {
            context.getSystemService(AccessibilityManager::class.java)
        }
    var readAloudStatus by remember { mutableStateOf("Ready when you are.") }

    ChartSectionCard(
        title = "Accessibility",
        subtitle =
            "These display and assistance options apply immediately, persist across restarts and never alter scientific values or calculations."
    ) {
        Text("Interface and text size", style = MaterialTheme.typography.titleSmall)
        Text(
            "Scales the whole interface so controls and diagrams reflow together. Android's system font setting is still respected.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        AppContentScale.entries.forEach { scale ->
            val selected = preferences.contentScale == scale
            if (selected) {
                Button(
                    onClick = { onPreferencesChange(preferences.copy(contentScale = scale)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("accessibility-scale:${scale.persistedId}")
                ) {
                    Text("✓ ${scale.displayName}")
                }
            } else {
                OutlinedButton(
                    onClick = { onPreferencesChange(preferences.copy(contentScale = scale)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("accessibility-scale:${scale.persistedId}")
                ) {
                    Text(scale.displayName)
                }
            }
        }

        AccessibilityToggle(
            title = "High contrast",
            description =
                "Uses a stable black-on-white research palette with darker controls. It temporarily overrides wallpaper colours.",
            checked = preferences.highContrast,
            testTag = "accessibility-high-contrast",
            onCheckedChange = { enabled ->
                onPreferencesChange(preferences.copy(highContrast = enabled))
            }
        )
        AccessibilityToggle(
            title = "Stronger text weight",
            description = "Makes body copy and labels easier to distinguish without changing their meaning.",
            checked = preferences.boldText,
            testTag = "accessibility-bold-text",
            onCheckedChange = { enabled ->
                onPreferencesChange(preferences.copy(boldText = enabled))
            }
        )
        AccessibilityToggle(
            title = "Reduce automatic motion",
            description =
                "Stops automatic scientific replays. Manually requested workspace animation remains available.",
            checked = preferences.reduceMotion,
            testTag = "accessibility-reduce-motion",
            onCheckedChange = { enabled ->
                onPreferencesChange(preferences.copy(reduceMotion = enabled))
            }
        )
        AccessibilityToggle(
            title = "Show read-aloud guide",
            description =
                "Adds a button that speaks a concise guide for each screen. TalkBack remains the recommended option for reading every control.",
            checked = preferences.readAloudEnabled,
            testTag = "accessibility-read-aloud",
            onCheckedChange = { enabled ->
                if (!enabled) readAloudController.stop()
                onPreferencesChange(preferences.copy(readAloudEnabled = enabled))
            }
        )

        if (preferences.readAloudEnabled) {
            Text(
                "Speech rate · ${String.format(Locale.US, "%.1f", preferences.speechRate)}×",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = preferences.speechRate,
                onValueChange = { rate ->
                    onPreferencesChange(preferences.copy(speechRate = rate))
                },
                valueRange =
                    AppAccessibilityPreferences.MINIMUM_SPEECH_RATE..AppAccessibilityPreferences.MAXIMUM_SPEECH_RATE,
                steps = 7,
                modifier = Modifier.fillMaxWidth().testTag("accessibility-speech-rate")
            )
            Button(
                onClick = {
                    readAloudStatus =
                        when (
                            readAloudController.speak(
                                text =
                                    "Accessibility preview. The app can enlarge controls, increase contrast, reduce automatic motion, and read a concise guide for each screen.",
                                speechRate = preferences.speechRate
                            )
                        ) {
                            ReadAloudResult.STARTED -> "Reading preview aloud."
                            ReadAloudResult.INITIALIZING -> "The device speech engine is still starting. Try again in a moment."
                            ReadAloudResult.UNAVAILABLE -> "No compatible device speech voice is currently available."
                        }
                },
                modifier = Modifier.fillMaxWidth().testTag("accessibility-preview-speech")
            ) {
                Text("Read accessibility preview aloud")
            }
            OutlinedButton(
                onClick = {
                    readAloudController.stop()
                    readAloudStatus = "Speech stopped."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop reading")
            }
            Text(
                readAloudStatus,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Android accessibility services", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(
                    if (accessibilityManager?.isTouchExplorationEnabled == true) {
                        "A touch-exploration screen reader is active."
                    } else {
                        "No touch-exploration screen reader is currently detected."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open-android-accessibility")
                ) {
                    Text("Open Android accessibility settings")
                }
            }
        }

        Text(
            "Audio never starts automatically. Built-in Material controls retain at least a 48 dp touch target, and critical state is written as text rather than communicated by colour alone.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.primary
        )
        OutlinedButton(
            onClick = {
                readAloudController.stop()
                onPreferencesChange(AppAccessibilityPreferences())
                readAloudStatus = "Accessibility defaults restored."
            },
            modifier = Modifier.fillMaxWidth().testTag("restore-accessibility-defaults")
        ) {
            Text("Restore accessibility defaults")
        }
    }
}

@Composable
private fun AccessibilityToggle(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceVariant.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun AppearanceSettingsCard(
    preferences: AppVisualThemePreferences,
    onPreferencesChange: (AppVisualThemePreferences) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ChartSectionCard(
        title = "Appearance",
        subtitle = "The fixed research palette keeps screenshots, chart surroundings and contrast consistent across devices."
    ) {
        ChartMetricRow("Application palette", "Ocean Lab")
        ChartMetricRow("Device dynamic colours", "Disabled")
        Text(
            "Scientific charts retain their measurement and status colours; decorative palette switching is disabled in this display profile.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.primary
        )
    }
}

private data class ResearchNotificationSettingsState(
    val appAllowed: Boolean,
    val progressChannelAllowed: Boolean,
    val resultChannelAllowed: Boolean
)

private fun readResearchNotificationState(context: android.content.Context): ResearchNotificationSettingsState {
    // Channel creation is idempotent and does not override choices the user already made in
    // Android settings. Creating them here lets this screen report their real state before the
    // first long process is launched.
    ResearchProcessNotificationPublisher(context)
    val runtimePermissionGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val appAllowed = runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    val manager = context.getSystemService(NotificationManager::class.java)
    fun channelAllowed(id: String): Boolean =
        appAllowed && manager.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
    return ResearchNotificationSettingsState(
        appAllowed = appAllowed,
        progressChannelAllowed = channelAllowed(ResearchProcessNotificationPublisher.ONGOING_CHANNEL_ID),
        resultChannelAllowed = channelAllowed(ResearchProcessNotificationPublisher.RESULT_CHANNEL_ID)
    )
}

private fun formatBytes(bytes: Long): String =
    if (bytes <= 0L) {
        "Unavailable"
    } else {
        String.format(Locale.US, "%.0f MiB", bytes.toDouble() / 1_048_576.0)
    }

private fun safetyColor(lowMemory: Boolean, thermalName: String): Color =
    if (lowMemory || thermalName in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")) {
        Color(0xFFC62828)
    } else {
        Color(0xFF2E7D32)
    }
