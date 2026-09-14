package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import android.content.Context
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.nio.file.Files
import java.util.Properties

data class NumericalSafetySavedSession(
    val id: String,
    val directoryPath: String,
    val reportPath: String,
    val trialCsvPath: String,
    val trialCount: Int
)

class NumericalSafetyStorageRepository(
    private val rootDirectory: File
) {
    constructor(context: Context) : this(
        File(AppStoragePaths(context).workspaceStudiesDirectory, "numerical-safety")
    )

    @Synchronized
    fun save(report: NumericalSafetyReport): NumericalSafetySavedSession {
        require(report.createdAtEpochMillis > 0L) { "Numerical-safety timestamp must be positive." }
        require(rootDirectory.mkdirs() || rootDirectory.isDirectory) {
            "Numerical-safety storage directory is unavailable."
        }
        val id = "numerical-safety-${report.createdAtEpochMillis}-${report.config.randomSeed}"
        val directory = File(rootDirectory, id)
        require(!directory.exists()) { "Numerical-safety session already exists: $id" }
        val stagingDirectory = Files.createTempDirectory(rootDirectory.toPath(), ".$id-").toFile()
        val finalReportFile = File(directory, "report.txt")
        val finalCsvFile = File(directory, "paired-trials.csv")
        try {
            NumericalSafetyReportWriter.writeReadableReport(report, File(stagingDirectory, finalReportFile.name))
            NumericalSafetyReportWriter.writeTrialCsv(report, File(stagingDirectory, finalCsvFile.name))
            Properties().apply {
                setProperty("schemaVersion", "1")
                setProperty("protocolId", report.protocolId)
                setProperty("createdAtEpochMillis", report.createdAtEpochMillis.toString())
                setProperty("randomSeed", report.config.randomSeed.toString())
                setProperty("trialCount", report.trials.size.toString())
                setProperty("validGuardedSuccessRate", report.validGuardedSummary.validatedSuccessRate.toString())
                setProperty("validUnguardedSuccessRate", report.validUnguardedSummary?.validatedSuccessRate?.toString().orEmpty())
                setProperty("reportPath", finalReportFile.absolutePath)
                setProperty("trialCsvPath", finalCsvFile.absolutePath)
            }.also { properties ->
                File(stagingDirectory, "manifest.properties").outputStream().buffered().use { output ->
                    properties.store(output, "Robot Kinematics Lab numerical-safety ablation")
                }
            }
            moveWithoutReplacing(stagingDirectory, directory)
        } catch (failure: Throwable) {
            stagingDirectory.deleteRecursively()
            throw failure
        }
        return NumericalSafetySavedSession(
            id = id,
            directoryPath = directory.absolutePath,
            reportPath = finalReportFile.absolutePath,
            trialCsvPath = finalCsvFile.absolutePath,
            trialCount = report.trials.size
        )
    }

    private fun moveWithoutReplacing(source: File, destination: File) {
        Files.move(source.toPath(), destination.toPath())
    }
}
