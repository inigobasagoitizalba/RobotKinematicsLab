package com.robotkinematicslab.mobile.ml.ik

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, robot-sharded desktop generator for the independently certified 1 µm corpus. */
class OneMicronIkCorpusExportTest {

    @Test
    fun exportCertifiedCorpusWhenRequested() {
        val outputPath = System.getenv("RKL_ONE_MICRON_CORPUS_OUTPUT")
        assumeTrue(!outputPath.isNullOrBlank())
        val output = File(requireNotNull(outputPath)).absoluteFile
        val outputDirectory = requireNotNull(output.parentFile)
        val samplesPerRobot = System.getenv("RKL_ONE_MICRON_SAMPLES_PER_ROBOT")?.toIntOrNull() ?: 1_000
        val robots = DatasetRobotPresets().buildDefaults()
        val workers = (System.getenv("RKL_ONE_MICRON_GENERATION_WORKERS")?.toIntOrNull() ?: 6).coerceIn(1, robots.size)
        outputDirectory.mkdirs()
        val shardsDirectory = Files.createTempDirectory(outputDirectory.toPath(), "micron-shards-").toFile()
        val executor = Executors.newFixedThreadPool(workers)
        val started = System.nanoTime()
        val shards =
            try {
                robots.mapIndexed { index, robot ->
                    executor.submit(
                        Callable {
                            val shard = File(shardsDirectory, "${index.toString().padStart(2, '0')}.csv")
                            val generated =
                                ScientificDatasetGenerator().generate(
                                    DatasetGenerationConfig(
                                        datasetName = "verified-one-micron-ik",
                                        robots = listOf(robot),
                                        samplesPerRobot = samplesPerRobot,
                                        randomSeed = 2604,
                                        targetMode = DatasetTargetMode.FK_PROVEN_REACHABLE,
                                        reachableFraction = 1.0,
                                        filterMode = DatasetFilterMode.ACCEPTED_ONLY,
                                        append = false,
                                        maxAttemptsMultiplier = 100,
                                        ikConfig = oneMicronSolverConfig()
                                    ),
                                    shard,
                                    index.toLong() * samplesPerRobot,
                                    0
                                )
                            check(generated.completed) { "${robot.id}: ${generated.message}" }
                            index to shard
                        }
                    )
                }.map { it.get() }
            } finally {
                executor.shutdownNow()
            }
        val partial = File(outputDirectory, "${output.name}.partial")
        var rows = 0
        partial.bufferedWriter().use { writer ->
            shards.sortedBy { it.first }.forEachIndexed { shardIndex, (_, file) ->
                file.useLines { lines ->
                    lines.forEachIndexed { lineIndex, line ->
                        if (shardIndex == 0 || lineIndex > 0) {
                            writer.appendLine(line)
                            if (lineIndex > 0) rows++
                        }
                    }
                }
            }
        }
        partial.copyTo(output, overwrite = true)
        partial.delete()
        shardsDirectory.deleteRecursively()
        assertEquals(robots.size * samplesPerRobot, rows)
        assertTrue(output.isFile)
        println(
            "ONE_MICRON_CORPUS path=${output.absolutePath},rows=$rows,robots=${robots.size}," +
                "workers=$workers,elapsed_ms=${(System.nanoTime() - started) / 1_000_000}"
        )
    }
}
