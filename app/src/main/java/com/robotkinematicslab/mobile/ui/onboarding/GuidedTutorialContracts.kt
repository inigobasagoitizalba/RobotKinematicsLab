package com.robotkinematicslab.mobile.ui.onboarding

/**
 * Compatibility identifiers retained by scientific screens after the delivery tutorial was
 * removed. They are inert and can be reused by a future help system without affecting results.
 */
@JvmInline
value class TutorialTargetId(val value: String)

enum class TutorialInteraction {
    OBSERVE,
    TAP,
    CHOOSE,
    TYPE,
    SCROLL,
    DRAG,
    PINCH,
    LONG_PRESS,
    DOUBLE_TAP,
    WAIT,
    OPTIONAL_MUTATION
}

enum class TutorialActionResult {
    IN_PROGRESS,
    COMPLETED,
    REJECTED
}

data class TutorialActionReport(
    val target: TutorialTargetId?,
    val interaction: TutorialInteraction,
    val result: TutorialActionResult = TutorialActionResult.COMPLETED,
    val detail: String? = null
)

fun interface TutorialActionReporter {
    fun report(action: TutorialActionReport)
}

fun TutorialActionReporter.report(
    target: TutorialTargetId?,
    interaction: TutorialInteraction,
    result: TutorialActionResult = TutorialActionResult.COMPLETED,
    detail: String? = null
) {
    report(TutorialActionReport(target, interaction, result, detail))
}

object TutorialTargets {
    val ProjectLibrary = TutorialTargetId("project-library")
    val CreateProject = TutorialTargetId("create-project-button")
    val ProjectEditor = TutorialTargetId("project-editor-dialog")
    val ProjectCard = TutorialTargetId("tutorial-project-card")
    val ProjectHome = TutorialTargetId("project-home")
    val ProjectContinue = TutorialTargetId("project-continue")
    val SectionHome = TutorialTargetId("project-section:home")
    val SectionPrepare = TutorialTargetId("project-section:prepare")
    val SectionExperiment = TutorialTargetId("project-section:experiment")
    val SectionLibrary = TutorialTargetId("project-section:library")
    val ProjectContext = TutorialTargetId("project-context")

    val RobotLab = TutorialTargetId("tutorial-robot-lab")
    val RobotLabScreenSelector = TutorialTargetId("robot-lab-screen-selector")
    val RobotSetupTab = TutorialTargetId("robot-lab-setup-tab")
    val RobotViewTab = TutorialTargetId("robot-lab-view-tab")
    val RobotSetup = TutorialTargetId("robot-lab-setup")
    val RobotView = TutorialTargetId("robot-lab-view")
    val RobotName = TutorialTargetId("robot-lab-name")
    val RobotJointCard = TutorialTargetId("robot-lab-joint-card")
    val RobotJointType = TutorialTargetId("robot-lab-joint-type")
    val RobotDhParameters = TutorialTargetId("robot-lab-dh-parameters")
    val RobotJointLimits = TutorialTargetId("robot-lab-joint-limits")
    val RobotAddJoint = TutorialTargetId("robot-lab-add-joint")
    val RobotRemoveJoint = TutorialTargetId("robot-lab-remove-joint")
    val RobotApply = TutorialTargetId("robot-lab-apply")
    val RobotDemo = TutorialTargetId("robot-lab-demo")
    val RobotFkMode = TutorialTargetId("robot-lab-fk-mode")
    val RobotIkMode = TutorialTargetId("robot-lab-ik-mode")
    val RobotScene = TutorialTargetId("robot-lab-scene")
    val RobotJointSliders = TutorialTargetId("robot-lab-joint-sliders")
    val Workspace = TutorialTargetId("tutorial-workspace")
    val WorkspaceRobotSelector = TutorialTargetId("workspace-robot-selector")
    val WorkspaceStudyContract = TutorialTargetId("workspace-study-contract")
    val WorkspaceQualityPreset = TutorialTargetId("workspace-quality-preset")
    val WorkspaceNumericContract = TutorialTargetId("workspace-numeric-contract")
    val WorkspaceGenerate = TutorialTargetId("workspace-generate-study")
    val WorkspaceCancel = TutorialTargetId("workspace-cancel-study")
    val WorkspaceModeSelector = TutorialTargetId("workspace-mode-selector")
    val WorkspaceScene = TutorialTargetId("workspace-scene")
    val WorkspacePlayback = TutorialTargetId("workspace-playback")
    val WorkspaceSpeed = TutorialTargetId("workspace-speed")
    val WorkspaceReveal = TutorialTargetId("workspace-reveal")
    val WorkspaceConstructionTimeline = TutorialTargetId("workspace-construction-timeline")
    val WorkspaceVisibilityLayers = TutorialTargetId("workspace-visibility-layers")
    val WorkspaceSelectedCell = TutorialTargetId("workspace-selected-cell")
    val WorkspaceResult = TutorialTargetId("workspace-result")
    val WorkspaceSavedStudies = TutorialTargetId("workspace-saved-studies")
    val Dataset = TutorialTargetId("tutorial-dataset")
    val DatasetSectionSelector = TutorialTargetId("tutorial-dataset-sections")
    val DatasetRobots = TutorialTargetId("dataset-section-ROBOTS")
    val DatasetCreate = TutorialTargetId("dataset-section-CREATE")
    val DatasetSaved = TutorialTargetId("dataset-section-SAVED")
    val DatasetQuality = TutorialTargetId("dataset-section-QUALITY")
    val DatasetContinuous = TutorialTargetId("dataset-section-CONTINUOUS")
    val DatasetCreateRobots = TutorialTargetId("dataset-create-robots")
    val DatasetCreateRows = TutorialTargetId("dataset-create-rows")
    val DatasetCreateSeed = TutorialTargetId("dataset-create-seed")
    val DatasetCreatePopulation = TutorialTargetId("dataset-create-population")
    val DatasetCreateRetention = TutorialTargetId("dataset-create-retention")
    val DatasetCreateIk = TutorialTargetId("dataset-create-ik")
    val DatasetCreateFeatures = TutorialTargetId("dataset-create-features")
    val DatasetGenerateReplace = TutorialTargetId("dataset-generate-replace")
    val DatasetAppend = TutorialTargetId("dataset-append")
    val DatasetCancel = TutorialTargetId("dataset-cancel")
    val RobotLibraryGallery = TutorialTargetId("robot-library-gallery")
    val RobotLibraryPdf = TutorialTargetId("robot-library-pdf")
    val RobotLibrarySearch = TutorialTargetId("robot-library-search")
    val RobotLibraryModel = TutorialTargetId("robot-library-model")
    val RobotLibraryCustom = TutorialTargetId("robot-library-custom")
    val DatasetQualityDataset = TutorialTargetId("dataset-quality-dataset")
    val DatasetQualityProfile = TutorialTargetId("dataset-quality-profile")
    val DatasetQualityCalculate = TutorialTargetId("dataset-quality-calculate")
    val DatasetQualityCancel = TutorialTargetId("dataset-quality-cancel")
    val DatasetQualityResult = TutorialTargetId("dataset-quality-result")
    val ContinuousCpu = TutorialTargetId("continuous-cpu")
    val ContinuousMemory = TutorialTargetId("continuous-memory")
    val ContinuousSafeguards = TutorialTargetId("continuous-safeguards")
    val ContinuousStart = TutorialTargetId("continuous-start")
    val ContinuousPause = TutorialTargetId("continuous-pause")
    val ContinuousResume = TutorialTargetId("continuous-resume")
    val ContinuousStatus = TutorialTargetId("continuous-status")
    val Diagnostics = TutorialTargetId("tutorial-diagnostics")
    val DiagnosticsConfiguration = TutorialTargetId("diagnostics-configuration")
    val DiagnosticsPresets = TutorialTargetId("diagnostics-presets")
    val DiagnosticsRun = TutorialTargetId("diagnostics-run")
    val DiagnosticsCancel = TutorialTargetId("diagnostics-cancel")
    val DiagnosticsResults = TutorialTargetId("diagnostics-results")
    val DiagnosticsResultFolders = TutorialTargetId("diagnostics-result-folders")
    val DiagnosticsCharts = TutorialTargetId("diagnostics-charts")
    val DiagnosticsChartCategory = TutorialTargetId("diagnostics-chart-category")
    val DiagnosticsMatrix = TutorialTargetId("diagnostics-matrix-and-3d")
    val DiagnosticsWorkspace3D = TutorialTargetId("diagnostics-workspace-3d")
    val DiagnosticsWorkspace3DGestures = TutorialTargetId("diagnostics-workspace-3d-gestures")
    val ChartGestures = TutorialTargetId("chart-inspector-gestures")
    val ChartGuidanceAndFigures = TutorialTargetId("chart-guidance-and-figures")
    val NumericalSafety = TutorialTargetId("OpenNumericalSafetyAudit")
    val NumericalSafetyConfiguration = TutorialTargetId("numerical-safety-configuration")
    val NumericalSafetyRun = TutorialTargetId("RunNumericalSafetyAudit")
    val NumericalSafetyCancel = TutorialTargetId("numerical-safety-cancel")
    val NumericalSafetyResults = TutorialTargetId("numerical-safety-results")
    val Telemetry = TutorialTargetId("telemetry_dashboard")
    val TelemetryRaw = TutorialTargetId("telemetry_raw_button")
    val TelemetryLoading = TutorialTargetId("telemetry-loading")
    val TelemetryTimeline = TutorialTargetId("telemetry-timeline")
    val TelemetryGestures = TutorialTargetId("telemetry-chart-gestures")
    val Training = TutorialTargetId("tutorial-training")
    val TrainingModeMenu = TutorialTargetId("training-mode-menu")
    val TrainingSingleRun = TutorialTargetId("training-single-run")
    val TrainingSingleExecution = TutorialTargetId("training-single-execution")
    val TrainingClosedLoop = TutorialTargetId("training-closed-loop")
    val TrainingComparison = TutorialTargetId("training-comparison")
    val TrainingExplainability = TutorialTargetId("training-explainability")
    val TrainingOneMicron = TutorialTargetId("training-one-micron")
    val TrainingScientificEvidence = TutorialTargetId("training-scientific-evidence")
    val Storage = TutorialTargetId("tutorial-storage")
    val StorageCategories = TutorialTargetId("storage-categories")
    val StorageDetails = TutorialTargetId("storage-category-details")
    val StorageDirectAccess = TutorialTargetId("storage-direct-access")
    val StorageBundledLibrary = TutorialTargetId("storage-bundled-library")
    val Settings = TutorialTargetId("tutorial-settings")
    val SettingsPresentation = TutorialTargetId("settings-presentation")
    val SettingsComputePolicy = TutorialTargetId("settings-compute-policy")
    val SettingsWorkingMemory = TutorialTargetId("settings-working-memory")
    val SettingsNotifications = TutorialTargetId("settings-process-notifications")
    val SettingsSafetyContract = TutorialTargetId("settings-safety-contract")
    val SettingsHelp = TutorialTargetId("settings-help")
    val ReplayTutorial = TutorialTargetId("settings-replay-tutorial")
    val Glossary = TutorialTargetId("settings-open-glossary")
}

