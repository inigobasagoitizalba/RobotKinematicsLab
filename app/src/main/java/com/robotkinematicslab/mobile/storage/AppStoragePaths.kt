package com.robotkinematicslab.mobile.storage

import android.content.Context
import android.os.Environment
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.io.File

class AppStoragePaths private constructor(
    private val documentsRoot: File,
    private val internalFilesRoot: File,
    projectRootOverride: File?
) {

    constructor(documentsRoot: File, internalFilesRoot: File) : this(
        documentsRoot = documentsRoot,
        internalFilesRoot = internalFilesRoot,
        projectRootOverride = null
    )

    constructor(context: Context) : this(
        documentsRoot =
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir,
        internalFilesRoot = context.filesDir,
        projectRootOverride = ResearchProjectRepository.resolveActiveProjectRoot(context)
    )

    val rootDirectory: File = projectRootOverride ?: File(documentsRoot, ROOT_DIRECTORY_NAME)
    private val shouldMigrateLegacyData =
        projectRootOverride == null || projectRootOverride == File(documentsRoot, ROOT_DIRECTORY_NAME)
    val robotsDirectory: File = File(rootDirectory, "robots")
    val sessionsDirectory: File = File(rootDirectory, "sessions")
    val diagnosticsDirectory: File = File(sessionsDirectory, "diagnostics")
    val telemetryDirectory: File = File(sessionsDirectory, "telemetry")
    val robotLabDirectory: File = File(sessionsDirectory, "robot-lab")
    val workspaceStudiesDirectory: File = File(sessionsDirectory, "workspace-studies")
    val datasetsDirectory: File = File(rootDirectory, "datasets")
    val modelsDirectory: File = File(rootDirectory, "models")
    val oneMicronModelsDirectory: File = File(modelsDirectory, "one-micron-ik")
    val trainingDirectory: File = File(rootDirectory, "training")
    val oneMicronTrainingDirectory: File = File(trainingDirectory, "one-micron-ik")
    val closedLoopTrainingDirectory: File = File(trainingDirectory, "closed-loop")
    val figuresDirectory: File = File(rootDirectory, "figures")
    val preferencesDirectory: File = File(rootDirectory, "preferences")
    val robotLibraryFile: File = File(robotsDirectory, "dataset_robot_library.rklb")

    @Synchronized
    fun ensureStructureAndMigrateLegacyData() {
        listOf(
            rootDirectory,
            robotsDirectory,
            sessionsDirectory,
            diagnosticsDirectory,
            telemetryDirectory,
            robotLabDirectory,
            workspaceStudiesDirectory,
            datasetsDirectory,
            modelsDirectory,
            oneMicronModelsDirectory,
            trainingDirectory,
            oneMicronTrainingDirectory,
            closedLoopTrainingDirectory,
            figuresDirectory,
            preferencesDirectory
        ).forEach(File::mkdirs)

        if (!shouldMigrateLegacyData) return

        migrateFileIfNeeded(
            source = File(internalFilesRoot, LEGACY_ROBOT_LIBRARY_NAME),
            destination = robotLibraryFile
        )

        val legacyDatasets = File(documentsRoot, LEGACY_DATASET_DIRECTORY_NAME)
        if (legacyDatasets.exists() && legacyDatasets.isDirectory) {
            legacyDatasets.listFiles()?.forEach { source ->
                if (source.isFile) {
                    migrateFileIfNeeded(
                        source = source,
                        destination = File(datasetsDirectory, source.name)
                    )
                }
            }
        }
    }

    private fun migrateFileIfNeeded(
        source: File,
        destination: File
    ) {
        if (!source.exists() || destination.exists()) return
        destination.parentFile?.mkdirs()
        runCatching {
            source.copyTo(destination, overwrite = false)
        }
    }

    companion object {
        const val ROOT_DIRECTORY_NAME = "RobotKinematicsLab"
        private const val LEGACY_ROBOT_LIBRARY_NAME = "dataset_robot_library.rklb"
        private const val LEGACY_DATASET_DIRECTORY_NAME = "RobotKinematicsDatasets"
    }
}
