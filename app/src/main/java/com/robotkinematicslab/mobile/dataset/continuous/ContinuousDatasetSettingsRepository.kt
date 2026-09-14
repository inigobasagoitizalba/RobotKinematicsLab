package com.robotkinematicslab.mobile.dataset.continuous

import android.content.Context
import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class ContinuousDatasetSettingsRepository(
    private val preferencesDirectory: File
) {
    constructor(context: Context) : this(
        AppStoragePaths(context)
            .also(AppStoragePaths::ensureStructureAndMigrateLegacyData)
            .preferencesDirectory
    )

    private val stateFile = File(preferencesDirectory, FILE_NAME)

    @Synchronized
    fun load(): ContinuousDatasetRecovery? {
        if (!stateFile.isFile || stateFile.length() !in 1..MAX_FILE_BYTES) return null
        return runCatching {
            val properties = Properties().apply { stateFile.inputStream().buffered().use(::load) }
            require(properties.getProperty("schemaVersion")?.toIntOrNull() == SCHEMA_VERSION)
            val plan =
                ContinuousDatasetPlan(
                    datasetName = properties.getProperty("datasetName"),
                    robotIds = properties.getProperty("robotIds").split(UNIT_SEPARATOR).filter(String::isNotBlank),
                    randomSeed = properties.getProperty("randomSeed").toInt(),
                    targetMode = DatasetTargetMode.valueOf(properties.getProperty("targetMode")),
                    reachableFraction = properties.getProperty("reachableFraction").toDouble(),
                    filterMode = DatasetFilterMode.valueOf(properties.getProperty("filterMode")),
                    ikConfig =
                        IKConfig(
                            maxIterations = properties.getProperty("ikMaxIterations").toInt(),
                            tolerance = properties.getProperty("ikTolerance").toDouble(),
                            damping = properties.getProperty("ikDamping").toDouble(),
                            maxStep = properties.getProperty("ikMaxStep").toDouble()
                        ),
                    metricPolicy = properties.readMetricPolicy(),
                    maxAttemptsMultiplier = properties.getProperty("maxAttemptsMultiplier").toInt(),
                    cpuBudgetPercent = properties.getProperty("cpuBudgetPercent").toInt(),
                    memoryBudgetPercent = properties.getProperty("memoryBudgetPercent").toInt()
                ).validated()
            val pending =
                if (properties.getProperty("pending")?.toBooleanStrictOrNull() == true) {
                    ContinuousDatasetPendingBatch(
                        existingRowCount = properties.getProperty("pendingExistingRowCount").toLong(),
                        generationIndex = properties.getProperty("pendingGenerationIndex").toInt(),
                        samplesPerRobot = properties.getProperty("pendingSamplesPerRobot").toInt(),
                        startedAtEpochMillis = properties.getProperty("pendingStartedAtEpochMillis").toLong(),
                        batchFingerprint = properties.getProperty("pendingBatchFingerprint")
                    )
                } else {
                    null
                }
            ContinuousDatasetRecovery(
                plan = plan,
                wasRunning = properties.getProperty("wasRunning")?.toBooleanStrictOrNull() == true,
                pendingBatch = pending
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(recovery: ContinuousDatasetRecovery) {
        val plan = recovery.plan.validated()
        require(preferencesDirectory.mkdirs() || preferencesDirectory.isDirectory) {
            "Continuous dataset settings storage is unavailable."
        }
        val properties =
            Properties().apply {
                setProperty("schemaVersion", SCHEMA_VERSION.toString())
                setProperty("datasetName", plan.datasetName)
                setProperty("robotIds", plan.robotIds.joinToString(UNIT_SEPARATOR.toString()))
                setProperty("randomSeed", plan.randomSeed.toString())
                setProperty("targetMode", plan.targetMode.name)
                setProperty("reachableFraction", plan.reachableFraction.toString())
                setProperty("filterMode", plan.filterMode.name)
                setProperty("ikMaxIterations", plan.ikConfig.maxIterations.toString())
                setProperty("ikTolerance", plan.ikConfig.tolerance.toString())
                setProperty("ikDamping", plan.ikConfig.damping.toString())
                setProperty("ikMaxStep", plan.ikConfig.maxStep.toString())
                writeMetricPolicy(plan.metricPolicy)
                setProperty("maxAttemptsMultiplier", plan.maxAttemptsMultiplier.toString())
                setProperty("cpuBudgetPercent", plan.cpuBudgetPercent.toString())
                setProperty("memoryBudgetPercent", plan.memoryBudgetPercent.toString())
                setProperty("wasRunning", recovery.wasRunning.toString())
                setProperty("pending", (recovery.pendingBatch != null).toString())
                recovery.pendingBatch?.let { pending ->
                    setProperty("pendingExistingRowCount", pending.existingRowCount.toString())
                    setProperty("pendingGenerationIndex", pending.generationIndex.toString())
                    setProperty("pendingSamplesPerRobot", pending.samplesPerRobot.toString())
                    setProperty("pendingStartedAtEpochMillis", pending.startedAtEpochMillis.toString())
                    pending.batchFingerprint?.let { setProperty("pendingBatchFingerprint", it) }
                }
            }
        val temporary =
            Files.createTempFile(
                preferencesDirectory.toPath(),
                ".$FILE_NAME-",
                ".tmp"
            ).toFile()
        try {
            temporary.outputStream().buffered().use { properties.store(it, "Continuous dataset state") }
            try {
                Files.move(
                    temporary.toPath(),
                    stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun Properties.writeMetricPolicy(policy: DiagnosticMetricPolicy) {
        setProperty("metricNumericalEpsilon", policy.numericalEpsilon.toString())
        setProperty("metricNearSuccessErrorMeters", policy.nearSuccessErrorMeters.toString())
        setProperty("metricCloseMissErrorMeters", policy.closeMissErrorMeters.toString())
        setProperty("metricStalledImprovementRatioEpsilon", policy.stalledImprovementRatioEpsilon.toString())
        setProperty("metricJointLimitMarginRatio", policy.jointLimitMarginRatio.toString())
        setProperty("metricEasySeedDistanceUpperMeters", policy.easySeedDistanceUpperMeters.toString())
        setProperty("metricMediumSeedDistanceUpperMeters", policy.mediumSeedDistanceUpperMeters.toString())
        setProperty("metricHardSeedDistanceUpperMeters", policy.hardSeedDistanceUpperMeters.toString())
        setProperty("metricLogConditionNumberCap", policy.logConditionNumberCap.toString())
    }

    private fun Properties.readMetricPolicy(): DiagnosticMetricPolicy {
        val defaults = DiagnosticMetricPolicy()
        return DiagnosticMetricPolicy(
            numericalEpsilon = getProperty("metricNumericalEpsilon")?.toDoubleOrNull() ?: defaults.numericalEpsilon,
            nearSuccessErrorMeters = getProperty("metricNearSuccessErrorMeters")?.toDoubleOrNull() ?: defaults.nearSuccessErrorMeters,
            closeMissErrorMeters = getProperty("metricCloseMissErrorMeters")?.toDoubleOrNull() ?: defaults.closeMissErrorMeters,
            stalledImprovementRatioEpsilon = getProperty("metricStalledImprovementRatioEpsilon")?.toDoubleOrNull() ?: defaults.stalledImprovementRatioEpsilon,
            jointLimitMarginRatio = getProperty("metricJointLimitMarginRatio")?.toDoubleOrNull() ?: defaults.jointLimitMarginRatio,
            easySeedDistanceUpperMeters = getProperty("metricEasySeedDistanceUpperMeters")?.toDoubleOrNull() ?: defaults.easySeedDistanceUpperMeters,
            mediumSeedDistanceUpperMeters = getProperty("metricMediumSeedDistanceUpperMeters")?.toDoubleOrNull() ?: defaults.mediumSeedDistanceUpperMeters,
            hardSeedDistanceUpperMeters = getProperty("metricHardSeedDistanceUpperMeters")?.toDoubleOrNull() ?: defaults.hardSeedDistanceUpperMeters,
            logConditionNumberCap = getProperty("metricLogConditionNumberCap")?.toDoubleOrNull() ?: defaults.logConditionNumberCap
        )
    }

    companion object {
        private const val FILE_NAME = "continuous-dataset.properties"
        private const val SCHEMA_VERSION = 1
        private const val MAX_FILE_BYTES = 256L * 1024L
        private const val UNIT_SEPARATOR = '\u001F'
    }
}
