package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.config.IKConfig
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DatasetScientificContractTest {
    private fun config() = DatasetGenerationConfig(
        datasetName = "contract", robots = DatasetRobotPresets().buildDefaults().take(2),
        samplesPerRobot = 5, randomSeed = 42, targetMode = DatasetTargetMode.MIXED,
        reachableFraction = 0.5, filterMode = DatasetFilterMode.ALL, append = false,
        ikConfig = IKConfig()
    )

    @Test fun canonicalIdentityIsStableAndSensitiveToColumnRobotAndJointOrder() {
        val config = config()
        val hash = DatasetScientificContract.fingerprint(config)
        assertTrue(DatasetScientificContract.isValidFingerprint(hash))
        assertEquals(hash, DatasetScientificContract.fingerprint(config.copy()))
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config, ScientificDatasetCsvWriter.HEADER.reversed()))
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config.copy(robots = config.robots.reversed())))
        val first = config.robots.first()
        val reordered = first.copy(robot = first.robot.copy(
            joints = first.robot.joints.reversed(), dhParameters = first.robot.dhParameters.reversed()
        ))
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config.copy(robots = listOf(reordered) + config.robots.drop(1))))
    }

    @Test fun geometryAndScientificSettingsAreBoundEvenWhenRobotIdDoesNotChange() {
        val config = config()
        val first = config.robots.first()
        val changed = first.copy(robot = first.robot.copy(
            dhParameters = first.robot.dhParameters.mapIndexed { i, dh -> if (i == 0) dh.copy(a = dh.a + 0.01) else dh }
        ))
        val hash = DatasetScientificContract.fingerprint(config)
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config.copy(robots = listOf(changed) + config.robots.drop(1))))
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config.copy(ikConfig = config.ikConfig.copy(tolerance = 1e-6))))
        assertNotEquals(hash, DatasetScientificContract.fingerprint(config.copy(metricPolicy = config.metricPolicy.copy(logConditionNumberCap = 10.0))))
    }

    @Test fun newSamplingSeedsAndSizesAreCompatibleButCannotRelabelPendingBatch() {
        val config = config()
        val resampled = config.copy(randomSeed = 99, samplesPerRobot = 20, append = true)
        assertEquals(DatasetScientificContract.fingerprint(config), DatasetScientificContract.fingerprint(resampled))
        val pending = DatasetScientificContract.batchFingerprint(config, 0L, 0)
        assertNotEquals(pending, DatasetScientificContract.batchFingerprint(config.copy(randomSeed = 99), 0L, 0))
        assertNotEquals(pending, DatasetScientificContract.batchFingerprint(config.copy(samplesPerRobot = 20), 0L, 0))
        assertNotEquals(pending, DatasetScientificContract.batchFingerprint(config, 5L, 1))
    }

    @Test fun manifestRoundTripAllowsCompatibleAppendAndRejectsUnverifiableLegacy() {
        val directory = Files.createTempDirectory("dataset-contract").toFile()
        try {
            val config = config()
            val repository = DatasetStorageRepository(directory)
            val manifest = buildDatasetManifest(null, config, directory.resolve("contract.csv").absolutePath, 10L, 10L, 0, 1L)
            repository.saveManifest(manifest)
            val reloaded = requireNotNull(repository.loadManifest(config.datasetName))
            assertEquals(manifest, reloaded)
            DatasetScientificContract.requireCompatible(reloaded, config.copy(randomSeed = 43, samplesPerRobot = 8))
            assertThrows(IllegalStateException::class.java) {
                DatasetScientificContract.requireCompatible(reloaded, config.copy(robots = config.robots.reversed()))
            }
            val legacy = reloaded.copy(scientificFingerprint = null)
            repository.saveManifest(legacy)
            assertNotNull(repository.loadManifest(config.datasetName))
            assertThrows(IllegalStateException::class.java) {
                DatasetScientificContract.requireCompatible(legacy, config)
            }
        } finally { directory.deleteRecursively() }
    }
}
