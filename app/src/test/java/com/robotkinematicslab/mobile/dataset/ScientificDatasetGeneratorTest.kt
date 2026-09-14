package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScientificDatasetGeneratorTest {

    @Test
    fun interruptedCallerPreservesExistingDatasetBeforeOpeningTheWriter() {
        val file = Files.createTempFile("dataset-sequential-interrupt", ".csv").toFile()
        file.writeText("known-good\n")

        Thread.currentThread().interrupt()
        try {
            val result =
                ScientificDatasetGenerator().generate(
                    config = reproducibilityConfig(randomSeed = 42).copy(samplesPerRobot = 64, append = false),
                    csvFile = file,
                    existingRowCount = 1L,
                    generationIndex = 1,
                    cancellationRequested = AtomicBoolean(false)
                )

            assertFalse(result.completed)
            assertTrue(result.cancelled)
            assertEquals(0, result.addedRows)
            assertEquals(1L, result.totalRows)
            assertEquals("known-good\n", file.readText())
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun identicalConfiguration_reproducesAllScientificColumns() {
        val firstFile = Files.createTempFile("seed-repeat-a", ".csv").toFile()
        val secondFile = Files.createTempFile("seed-repeat-b", ".csv").toFile()
        val config = reproducibilityConfig(randomSeed = 42)

        ScientificDatasetGenerator().generate(config, firstFile, 0L, 0)
        ScientificDatasetGenerator().generate(config, secondFile, 0L, 0)

        assertEquals(stableRows(firstFile), stableRows(secondFile))
    }

    @Test
    fun differentBaseSeed_changesDatasetSamples() {
        val firstFile = Files.createTempFile("seed-change-a", ".csv").toFile()
        val secondFile = Files.createTempFile("seed-change-b", ".csv").toFile()

        ScientificDatasetGenerator().generate(
            reproducibilityConfig(randomSeed = 42),
            firstFile,
            0L,
            0
        )
        ScientificDatasetGenerator().generate(
            reproducibilityConfig(randomSeed = 43),
            secondFile,
            0L,
            0
        )

        val targetColumns = setOf("targetX", "targetY", "targetZ", "seedJointValues")
        assertNotEquals(
            stableRows(firstFile).map { row -> row.filterKeys(targetColumns::contains) },
            stableRows(secondFile).map { row -> row.filterKeys(targetColumns::contains) }
        )
    }

    @Test
    fun robotSpecificSequence_doesNotDependOnRobotListOrder() {
        val firstFile = Files.createTempFile("robot-order-a", ".csv").toFile()
        val secondFile = Files.createTempFile("robot-order-b", ".csv").toFile()
        val robots = DatasetRobotPresets().buildDefaults().take(2)
        val config =
            reproducibilityConfig(randomSeed = 42).copy(
                robots = robots,
                samplesPerRobot = 2
            )

        ScientificDatasetGenerator().generate(config, firstFile, 0L, 0)
        ScientificDatasetGenerator().generate(
            config.copy(robots = robots.reversed()),
            secondFile,
            0L,
            0
        )

        assertEquals(
            stableRowsByRobot(firstFile),
            stableRowsByRobot(secondFile)
        )
    }

    @Test
    fun generationStreamsRowsAndAppendUsesDifferentRobotSeed() {
        val file = Files.createTempFile("generated-scientific-dataset", ".csv").toFile()
        val robot = DatasetRobotPresets().buildDefaults().first()
        val generator = ScientificDatasetGenerator()
        val baseConfig =
            DatasetGenerationConfig(
                datasetName = "unit-test",
                robots = listOf(robot),
                samplesPerRobot = 2,
                randomSeed = 42,
                targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE,
                reachableFraction = 1.0,
                filterMode = DatasetFilterMode.ALL,
                append = false,
                ikConfig = IKConfig(maxIterations = 10)
            )

        val first =
            generator.generate(
                config = baseConfig,
                csvFile = file,
                existingRowCount = 0L,
                generationIndex = 0
            )
        val second =
            generator.generate(
                config = baseConfig.copy(append = true),
                csvFile = file,
                existingRowCount = first.totalRows,
                generationIndex = 1
            )

        assertTrue(first.completed)
        assertTrue(second.completed)
        assertEquals(2, first.addedRows)
        assertEquals(4L, second.totalRows)

        val lines = file.readLines()
        assertEquals(5, lines.size)
        assertEquals(1, lines.count { it.startsWith("schemaVersion,") })

        val firstSeed = lines[1].split(',')[4]
        val secondBatchSeed = lines[3].split(',')[4]
        assertFalse(firstSeed == secondBatchSeed)
        assertTrue(lines.first().contains("randomProtocol"))
        assertTrue(lines.drop(1).all { it.contains("FK_PROVEN_REACHABLE") })
    }

    @Test
    fun mixedModeProducesExactRequestedClassBalance() {
        val file = Files.createTempFile("mixed-scientific-dataset", ".csv").toFile()
        val robot = DatasetRobotPresets().buildDefaults().first()

        val result =
            ScientificDatasetGenerator().generate(
                config =
                    DatasetGenerationConfig(
                        datasetName = "mixed-test",
                        robots = listOf(robot),
                        samplesPerRobot = 10,
                        randomSeed = 7,
                        targetMode = DatasetTargetMode.MIXED,
                        reachableFraction = 0.7,
                        filterMode = DatasetFilterMode.ALL,
                        append = false,
                        ikConfig = IKConfig(maxIterations = 1)
                    ),
                csvFile = file,
                existingRowCount = 0L,
                generationIndex = 0
            )

        val lines = file.readLines()
        val targetClassColumn = lines.first().split(',').indexOf("targetClass")
        val targetClasses = lines.drop(1).map { it.split(',')[targetClassColumn] }
        assertTrue(result.completed)
        assertEquals(7, targetClasses.count { it == "FK_PROVEN_REACHABLE" })
        assertEquals(3, targetClasses.count { it == "GUARANTEED_UNREACHABLE" })
    }

    @Test
    fun targetClassPlanAcceptsDocumentedBoundariesAndRejectsInvalidFractions() {
        assertEquals(0, plannedReachableTargetCount(DatasetTargetMode.MIXED, 0.0, 1))
        assertEquals(1, plannedReachableTargetCount(DatasetTargetMode.MIXED, 0.5, 1))
        assertEquals(1, plannedReachableTargetCount(DatasetTargetMode.MIXED, 1.0, 1))
        assertEquals(0, plannedReachableTargetCount(DatasetTargetMode.GUARANTEED_UNREACHABLE, 0.7, 9))
        assertEquals(9, plannedReachableTargetCount(DatasetTargetMode.FK_PROVEN_REACHABLE, 0.7, 9))

        assertThrows(IllegalArgumentException::class.java) {
            plannedReachableTargetCount(DatasetTargetMode.MIXED, Double.NaN, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            plannedReachableTargetCount(DatasetTargetMode.MIXED, 1.01, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            plannedReachableTargetCount(DatasetTargetMode.MIXED, 0.5, -1)
        }
    }

    @Test
    fun invalidAppendCoordinatesAndRobotIdentityAreRejectedBeforeWriting() {
        val file = Files.createTempFile("invalid-generation-contract", ".csv").toFile()
        val base = reproducibilityConfig(randomSeed = 42).copy(samplesPerRobot = 1)

        assertThrows(IllegalArgumentException::class.java) {
            ScientificDatasetGenerator().generate(base, file, existingRowCount = -1L, generationIndex = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificDatasetGenerator().generate(base, file, existingRowCount = 0L, generationIndex = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificDatasetGenerator().generate(
                base.copy(robots = listOf(base.robots.first().copy(id = ""))),
                file,
                existingRowCount = 0L,
                generationIndex = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificDatasetGenerator().generate(
                base.copy(robots = listOf(base.robots.first(), base.robots.first())),
                file,
                existingRowCount = 0L,
                generationIndex = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScientificDatasetGenerator().generate(
                base,
                file,
                existingRowCount = Long.MAX_VALUE,
                generationIndex = 0
            )
        }
    }

    private fun reproducibilityConfig(randomSeed: Int): DatasetGenerationConfig {
        return DatasetGenerationConfig(
            datasetName = "seed-reproducibility-test",
            robots = listOf(DatasetRobotPresets().buildDefaults().first()),
            samplesPerRobot = 4,
            randomSeed = randomSeed,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5,
            filterMode = DatasetFilterMode.ALL,
            append = false,
            ikConfig = IKConfig(maxIterations = 10)
        )
    }

    private fun stableRows(file: java.io.File): List<Map<String, String>> {
        val lines = file.readLines()
        val header = lines.first().split(',')

        return lines.drop(1).map { line ->
            header.zip(line.split(',')).toMap() - "solveDurationNanos"
        }
    }

    private fun stableRowsByRobot(
        file: java.io.File
    ): Map<String, List<Map<String, String>>> {
        return stableRows(file)
            .map { row -> row - "globalRowIndex" }
            .groupBy { row -> requireNotNull(row["robotId"]) }
    }
}
