package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.SavedRobot
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in desktop exporter for the AutoML model-selection campaign.
 *
 * The normal test suite skips this class. Set RKL_AUTOML_OUTPUT to generate a
 * robot-balanced CSV containing every built-in robot topology. Independent
 * robot shards are safe to generate in parallel because the scientific random
 * protocol derives each stream from the stable robot id.
 */
class AutoMlDatasetExportTest {

    @Test
    fun exportRobotBalancedScientificDatasetWhenRequested() {
        val outputPath = System.getenv("RKL_AUTOML_OUTPUT")
        assumeTrue("AutoML dataset export is opt-in.", !outputPath.isNullOrBlank())

        val output = File(requireNotNull(outputPath)).absoluteFile
        val samplesPerRobot =
            System.getenv("RKL_AUTOML_SAMPLES_PER_ROBOT")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_SAMPLES_PER_ROBOT
        val robotSet = System.getenv("RKL_AUTOML_ROBOT_SET") ?: RESEARCH_GRID
        val robots =
            when (robotSet) {
                BUILT_IN_PRESETS -> DatasetRobotPresets().buildDefaults()
                RESEARCH_GRID -> buildResearchRobotGrid()
                else -> error("Unknown RKL_AUTOML_ROBOT_SET: $robotSet")
            }
        val workerCount =
            System.getenv("RKL_AUTOML_GENERATION_WORKERS")
                ?.toIntOrNull()
                ?.coerceIn(1, robots.size)
                ?: DEFAULT_WORKERS.coerceAtMost(robots.size)
        val outputDirectory = requireNotNull(output.parentFile)
        outputDirectory.mkdirs()
        val shardDirectory =
            Files.createTempDirectory(outputDirectory.toPath(), "automl-dataset-shards-")
                .toFile()
        val executor = Executors.newFixedThreadPool(workerCount)

        val shards =
            try {
                robots.mapIndexed { robotIndex, robot ->
                    executor.submit(
                        Callable {
                            val shard = File(shardDirectory, "robot-${robotIndex.toString().padStart(2, '0')}.csv")
                            val generation =
                                ScientificDatasetGenerator().generate(
                                    config =
                                        DatasetGenerationConfig(
                                            datasetName = "automl-model-selection",
                                            robots = listOf(robot),
                                            samplesPerRobot = samplesPerRobot,
                                            randomSeed = GENERATION_SEED,
                                            targetMode = DatasetTargetMode.MIXED,
                                            reachableFraction = REACHABLE_FRACTION,
                                            filterMode = DatasetFilterMode.ALL,
                                            append = false,
                                            ikConfig =
                                                IKConfig(
                                                    maxIterations = 120,
                                                    tolerance = 1e-4,
                                                    damping = 0.05,
                                                    maxStep = 0.05
                                                )
                                        ),
                                    csvFile = shard,
                                    existingRowCount = robotIndex.toLong() * samplesPerRobot,
                                    generationIndex = 0
                                )
                            check(generation.completed) {
                                "Generation failed for ${robot.id}: ${generation.message}"
                            }
                            check(generation.addedRows == samplesPerRobot)
                            GeneratedShard(robotIndex, shard)
                        }
                    )
                }.map { future -> future.get() }
            } finally {
                executor.shutdownNow()
            }

        val temporary = File(outputDirectory, "${output.name}.partial")
        var writtenRows = 0
        temporary.bufferedWriter().use { writer ->
            shards.sortedBy(GeneratedShard::robotIndex).forEachIndexed { shardIndex, shard ->
                shard.file.useLines { lines ->
                    lines.forEachIndexed { lineIndex, line ->
                        if (shardIndex == 0 || lineIndex > 0) {
                            writer.appendLine(line)
                            if (lineIndex > 0) writtenRows += 1
                        }
                    }
                }
            }
        }

        val expectedRows = robots.size * samplesPerRobot
        assertEquals(expectedRows, writtenRows)
        temporary.copyTo(output, overwrite = true)
        assertTrue(temporary.delete())
        assertTrue(shardDirectory.deleteRecursively())

        println(
            "AUTOML_DATASET path=${output.absolutePath},rows=$writtenRows," +
                "robots=${robots.size},samplesPerRobot=$samplesPerRobot," +
                "robotSet=$robotSet,workers=$workerCount,seed=$GENERATION_SEED"
        )
    }

    private fun buildResearchRobotGrid(): List<SavedRobot> {
        val factory = DiagnosticRobotFactory()
        val stresses = listOf(0.15, 0.45, 0.75)
        val specifications =
            buildList {
                for (linkCount in 2..10) {
                    add(Triple(linkCount, DiagnosticJointMode.REVOLUTE_ONLY, stresses))
                    add(Triple(linkCount, DiagnosticJointMode.PRISMATIC_ONLY, stresses))
                    if (linkCount >= 3) {
                        add(Triple(linkCount, DiagnosticJointMode.MIXED, stresses))
                    }
                    if (linkCount >= 4) {
                        add(Triple(linkCount, DiagnosticJointMode.AUTO, stresses))
                    }
                }
            }

        return specifications.flatMap { (linkCount, mode, modeStresses) ->
            modeStresses.map { stress ->
                val stressPercent = (stress * 100.0).toInt()
                val modeId = mode.name.lowercase().replace('_', '-')
                SavedRobot(
                    id = "research-${linkCount.toString().padStart(2, '0')}l-$modeId-s$stressPercent",
                    robot =
                        factory.buildSeedRobot(
                            linkCount = linkCount,
                            jointMode = mode,
                            stressLevel = stress
                        ).copy(
                            name = "Research $linkCount-Link ${mode.name} S$stressPercent"
                        )
                )
            }
        }
    }

    private data class GeneratedShard(
        val robotIndex: Int,
        val file: File
    )

    private companion object {
        const val DEFAULT_SAMPLES_PER_ROBOT = 1_000
        const val DEFAULT_WORKERS = 6
        const val GENERATION_SEED = 2_604
        const val REACHABLE_FRACTION = 0.65
        const val BUILT_IN_PRESETS = "built-in-presets"
        const val RESEARCH_GRID = "research-grid"
    }
}
