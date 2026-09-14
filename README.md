# RobotKinematicsLab

RobotKinematicsLab is an Android application for studying the kinematics of serial robots. It brings robot definition, forward and inverse kinematics, workspace analysis, dataset generation, diagnostic testing and local machine-learning experiments into one project-based interface.

The app is designed for repeatable research. It records the robot, configuration, random seed, datasets, models and results used in each experiment. Charts and evidence can be saved for later inspection or exported as a portable project package.

## What you can do

- Create revolute, prismatic and mixed serial robots using Denavit-Hartenberg parameters.
- Visualise robots and targets in an interactive 3D view.
- Calculate forward kinematics from joint values.
- Solve position-based inverse kinematics and verify the final Cartesian error.
- Explore sampled 3D workspaces, boundaries and internal dead space.
- Generate reproducible datasets for different robot families and solver settings.
- Run diagnostic and numerical-safety experiments.
- Train local classification and neural inverse-kinematics models.
- Compare models on the same held-out data.
- Inspect feature definitions and model explanations.
- Save charts as PNG files and export research evidence.

## Requirements

To build the project you need:

- Android Studio.
- Android SDK 36.
- JDK 11 or a compatible JDK configured in Android Studio.
- An Android device or emulator running Android 8.0, API 26, or later.

The Gradle wrapper is included with the project.

## Running the app

Open the repository folder in Android Studio and wait for Gradle synchronisation to finish. Select the `app` run configuration, choose a device or emulator and press **Run**.

You can also build and install it from a terminal:

```text
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

The generated debug APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Getting started

When the app opens, create a research project or select an existing one. A project keeps its robots, datasets, experiments and saved evidence together.

The main navigation contains four sections:

- **Home** shows the current project and suggests the next useful action.
- **Prepare** contains the robot editor, workspace analysis and dataset builder.
- **Experiment** contains training and diagnostic tools.
- **Library** provides access to saved evidence, storage controls and settings.

For a first complete experiment, follow this order:

1. Create a project.
2. Define or select a robot.
3. Inspect its workspace if required.
4. Generate a dataset.
5. Train and compare models.
6. Run diagnostics.
7. Review the saved evidence and export the project.

## Using the bundled research data

The APK includes a research pack containing prepared datasets, trained models and their associated training records. Importing it is optional, but it is the quickest way to explore the app without running every experiment from the beginning.

Open **Library → Storage**, find **Research pack import**, and press **Import bundled research pack**. The app verifies the size and SHA-256 digest of each packaged file before installing it. Refresh the storage index when the import finishes.

## Creating a robot

Open **Prepare → Robot Lab → Robot Setup**. You can select a saved robot or create a new definition.

For each joint:

1. Choose whether it is revolute or prismatic.
2. Enter the displayed Denavit-Hartenberg values.
3. Set the minimum, maximum and home position.
4. Add or remove joints as required.
5. Validate the definition and apply it to Robot Lab.

The editor shows the required unit beside every field. Under the app's standard-DH convention, a revolute joint varies theta and a prismatic joint varies d.

Industrial robot cards provide references and source material. They do not become active experiment definitions until the required DH parameters and joint limits have been entered and validated.

## Forward kinematics

Open **Prepare → Robot Lab → Robot View** and select **Forward Kinematics**. Move the joint controls to change the robot state. The 3D view and end-effector position update from the current joint values.

The status panel reports whether the calculation succeeded and displays any validation warning. Resolve invalid parameters before using a result as experimental evidence.

## Inverse kinematics

In **Robot View**, select **Inverse Kinematics** and choose a Cartesian target in the 3D scene. You can use the deterministic solver or enable the available verified neural-assisted mode.

After calculating a solution, inspect:

- Solver status.
- Final position error.
- Number of deterministic iterations.
- Joint-limit compliance.
- Whether refinement or fallback was required.

The one-micrometre experiment measures position. A neural prediction is used as a proposal or warm start; the deterministic solver and a fresh forward-kinematics residual check decide whether the result is accepted.

## Exploring a robot workspace

Open **Prepare → Explore the 3D workspace** and choose a saved robot. Select a quality preset or enter the sample count, voxel resolution, number of replications and scientific seed manually. Press **Generate, animate and save workspace**.

The result includes:

- A sampled 3D workspace.
- Estimated boundary and dead-space geometry.
- Individual voxel information.
- Workspace construction animation.
- Convergence charts.
- Stability across repeated shifted sampling sequences.

Use the same robot definition, settings and seed when reproducing a workspace study. The 3D view explains the sampled result; quantitative claims should use the saved measurements and convergence evidence.

## Generating a dataset

Open **Prepare → Generate the dataset**. The builder guides you through six sections:

1. Enter the dataset name.
2. Select one or more robots.
3. Choose the number of rows, target mode, reachable-target proportion, retention policy and random seed.
4. Configure the IK solver.
5. Select any planned model comparisons.
6. Generate and save the dataset.

During generation, the app validates the configuration, produces targets, runs the solver, creates enriched rows and saves checkpoints. A manifest is stored beside the CSV so the experiment can be inspected and reproduced.

To inspect or extend existing data, use the **Saved datasets** and **Quality** areas in the dataset builder. Only append data when the existing manifest and CSV describe the same experiment. Use a new dataset name when changing the robot population or study design.

## Running diagnostics

Open **Experiment → Run diagnostics**. Choose a prepared configuration or set the robot dimensions, topologies, seeds, sample count and solver values yourself. Start the run and wait for the results screen.

The diagnostic area provides distributions and comparisons for outcomes, residual errors, iterations, robot topology, link count, joint limits and runtime behaviour. Completed results can be opened as charts and saved to the figure library.

The **Numerical safety** area compares the protected solver with an unguarded experimental control. Configure the seeds and command count, run the comparison and inspect the certified-result rate, numerical events and residual burden. The unguarded solver exists only for this comparison and should not be used for normal robot commands.

## Training models

Open **Experiment → Train and compare models**. The training area contains several tools.

### Controlled single run

Select a validated dataset, choose the data split, feature configurations and resource mode, and start training. When it finishes, review the held-out metrics and save the result. Use the same split and seed when comparing feature configurations.

### Closed-loop automation

Select the dataset, evaluated feature profile, acceptance conditions and maximum number of cycles. The app can repeat data preparation, candidate training and evaluation until the configured gate is met or the cycle limit is reached.

### Result comparison

Select a saved run and two different models from that run. Load the comparison to inspect aggregate results and individual held-out observations. Comparisons are meaningful only when both models use the same held-out population.

### Explainability

Select a saved run and model, choose the number of samples and integration steps, and start the analysis. The results show global and local feature contributions. These explanations describe the selected model and observations; they should not be interpreted as causal proof.

### One-micrometre IK

Select a compatible FK-proven reachable dataset, choose the split and feature configuration, and train the model. Review raw proposal accuracy separately from deterministic refinement, fallback and final verified success.

## Saving charts and figures

Charts are available from completed diagnostic, training, comparison and workspace results. Open the required result, choose a chart category and use the figure controls to save it.

For a manuscript figure:

1. Select the **Publication figure** presentation preset.
2. Check the title, axis labels, units and legend.
3. Save the chart as a PNG.
4. Open **Library → Storage** and refresh the index.
5. Review the saved image and its provenance information.

## Storage and project export

Open **Library → Storage** to inspect saved datasets, models, training runs, diagnostics, workspaces and figures. Refresh the index after completing or importing an experiment.

To create a portable copy, open the project evidence package area, refresh its file index, review the included material and export the ZIP. Exported evidence uses project-relative paths and records file hashes.

## Long-running operations

Dataset generation, diagnostics, workspace analysis and training can continue as visible foreground processes. Grant notification permission if you want progress to remain visible while using another screen or application.

Use the active-process indicator to return to a running operation, inspect telemetry or cancel it safely. Completed operations remain available through the project and Storage screens.

## Settings

Open **Library → Settings** to configure:

- Compute and memory policy.
- Chart presentation and saved-image appearance.
- Text size and contrast.
- Motion and accessibility preferences.
- Read-aloud assistance.

Android may reduce the effective compute budget when the device is under memory, temperature or power pressure.

## Testing

Run JVM tests with:

```text
./gradlew :app:testDebugUnitTest
```

Run device and Compose UI tests with a connected device or emulator:

```text
./gradlew :app:connectedDebugAndroidTest
```

The repository also contains larger research tests and command-line tools. Read the required environment variables in the relevant test or tool before running a full-corpus campaign.

## Repository structure

```text
app/src/main/java/com/robotkinematicslab/mobile/   Application source
app/src/main/res/                                  Android resources
app/src/main/assets/research_pack/                 Bundled datasets, models and records
app/src/test/                                      JVM tests and research campaigns
app/src/androidTest/                               Device and interface tests
tools/                                             Experiment, training and plotting utilities
audit-artifacts/                                   Retained research outputs
docs/emulator-evidence/                            Interface verification captures
gradle/                                            Gradle wrapper files
```

Build output, IDE state, local SDK paths and temporary files are excluded through `.gitignore`.

Two raw research CSV files are larger than GitHub's 100 MB individual-file limit and are excluded by name. The compressed datasets required by the APK remain in `app/src/main/assets/research_pack/datasets/`. Use Git LFS, a release archive or an institutional repository if the original raw corpora must also be distributed.

## Interpreting results

When reporting results produced by the app:

- State which robot definitions, dataset, split and seeds were used.
- Preserve the recorded dataset and model hashes.
- Distinguish position accuracy from full-pose accuracy.
- Describe iteration reductions as speed improvements only when total execution time was measured.
- Report raw neural proposals separately from refinement, fallback and final verification.
- Retain failed and non-finite cases unless an exclusion is explicitly justified.

## Common problems

**Android Studio cannot find the SDK:** select an installed Android SDK 36. Android Studio will create a local `local.properties` file automatically.

**A bundled file cannot be imported:** open Storage, refresh the index and retry the research-pack import. If a modified file already uses the expected name, remove or rename it before restoring the packaged copy.

**A dataset cannot be extended:** inspect its manifest and validation warning. Repair the CSV and manifest pair or create a dataset with a new name.

**Training controls are disabled:** select a compatible dataset, resolve its validation warnings and check that no conflicting experiment is running.

**A saved figure is missing:** allow the experiment to finish, use the chart's save control and refresh the Storage index.
