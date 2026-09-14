package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Versioned, length-delimited scientific identity. List order and exact Double bits matter. */
object DatasetScientificContract {
    private const val VERSION = "rkl-dataset-contract-v1"

    fun fingerprint(
        config: DatasetGenerationConfig,
        columns: List<String> = ScientificDatasetCsvWriter.HEADER
    ): String = digest {
        writeUTF(VERSION)
        writeUTF(ScientificDatasetCsvWriter.SCHEMA_VERSION)
        writeUTF(ScientificRandomProtocol.ID)
        writeInt(columns.size)
        columns.forEach(::writeUTF)
        writeInt(config.robots.size)
        config.robots.forEach { saved ->
            writeUTF(saved.id)
            val robot = saved.robot
            writeInt(robot.joints.size)
            robot.joints.forEach { joint ->
                writeUTF(joint.name)
                writeUTF(joint.type.name)
                writeDouble(joint.minValue)
                writeDouble(joint.maxValue)
                writeDouble(joint.homeValue)
            }
            writeInt(robot.dhParameters.size)
            robot.dhParameters.forEach { dh ->
                writeDouble(dh.theta)
                writeDouble(dh.d)
                writeDouble(dh.a)
                writeDouble(dh.alpha)
            }
        }
        with(config.ikConfig) {
            writeInt(maxIterations)
            writeDouble(tolerance)
            writeDouble(damping)
            writeDouble(maxStep)
        }
        with(config.metricPolicy) {
            writeDouble(numericalEpsilon)
            writeDouble(nearSuccessErrorMeters)
            writeDouble(closeMissErrorMeters)
            writeDouble(stalledImprovementRatioEpsilon)
            writeDouble(jointLimitMarginRatio)
            writeDouble(easySeedDistanceUpperMeters)
            writeDouble(mediumSeedDistanceUpperMeters)
            writeDouble(hardSeedDistanceUpperMeters)
            writeDouble(logConditionNumberCap)
        }
        writeUTF(config.targetMode.name)
        writeDouble(config.reachableFraction)
        writeUTF(config.filterMode.name)
    }

    /** Sampling seeds and budgets may vary on append; a pending batch must retain them exactly. */
    fun batchFingerprint(config: DatasetGenerationConfig, rowStart: Long, generationIndex: Int): String = digest {
        writeUTF("rkl-dataset-batch-v1")
        writeUTF(fingerprint(config))
        writeInt(config.randomSeed)
        writeInt(config.samplesPerRobot)
        writeInt(config.maxAttemptsMultiplier)
        writeLong(rowStart)
        writeInt(generationIndex)
    }

    fun requireCompatible(manifest: DatasetManifest?, config: DatasetGenerationConfig) {
        if (manifest == null) return
        check(manifest.scientificFingerprint != null) {
            "This legacy dataset has no verifiable scientific fingerprint. It remains readable; start a new dataset instead of appending."
        }
        check(manifest.scientificFingerprint == fingerprint(config)) {
            "Dataset schema, ordered robots or scientific settings changed. Append was blocked; start a new dataset for this configuration."
        }
    }

    fun isValidFingerprint(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))

    private fun digest(write: DataOutputStream.() -> Unit): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.write() }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
