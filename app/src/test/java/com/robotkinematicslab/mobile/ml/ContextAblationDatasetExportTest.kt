package com.robotkinematicslab.mobile.ml

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetGenerationConfig
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.dataset.ScientificDatasetGenerator
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
 * Opt-in, parallel export harness for the desktop dataset-scaling campaign.
 *
 * Normal unit-test runs skip this exporter. The Python campaign enables it with
 * RKL_ABLATION_BASE_DATASET and RKL_ABLATION_OUTPUT. It preserves the original
 * 1k pilot as batch 1, generates batches 2..10 in independent robot shards and
 * merges them canonically, so every 1k learning-curve step is a nested prefix.
 */
class ContextAblationDatasetExportTest {

    @Test
    fun extendPilotToTenThousandRowsWhenRequested() {
        val basePath = System.getenv("RKL_ABLATION_BASE_DATASET")
        val outputPath = System.getenv("RKL_ABLATION_OUTPUT")
        assumeTrue(
            "Dataset export is opt-in.",
            !basePath.isNullOrBlank() && !outputPath.isNullOrBlank()
        )

        val base = File(requireNotNull(basePath)).absoluteFile
        val output = File(requireNotNull(outputPath)).absoluteFile
        require(base.canonicalFile != output.canonicalFile) { "The output must not overwrite the original pilot." }
        if (!base.exists() && System.getenv("RKL_ABLATION_CREATE_PILOT") == "true") {
            val generated = ScientificDatasetGenerator().generate(
                DatasetGenerationConfig(
                    datasetName = "context-ablation-pilot",
                    robots = DatasetRobotPresets().buildDefaults().take(ROBOT_COUNT),
                    samplesPerRobot = ROWS_PER_ROBOT_PER_BATCH,
                    randomSeed = GENERATION_SEED,
                    targetMode = DatasetTargetMode.MIXED,
                    reachableFraction = 0.65,
                    filterMode = DatasetFilterMode.ALL,
                    append = false,
                    ikConfig = ContextAblationPilotValidator.solverConfig
                ), base, 0L, 0
            )
            check(generated.completed) { generated.message }
        }
        require(base.isFile) { "Base 1k dataset does not exist: ${base.absolutePath}" }
        ContextAblationPilotValidator.validate(base)

        val outputDirectory = requireNotNull(output.parentFile)
        outputDirectory.mkdirs()
        val shardDirectory =
            Files.createTempDirectory(outputDirectory.toPath(), "context-ablation-shards-")
                .toFile()
        val robots = DatasetRobotPresets().buildDefaults().take(ROBOT_COUNT)
        val executor = Executors.newFixedThreadPool(ROBOT_COUNT)

        val shards =
            try {
                val futures =
                    (1 until BATCH_COUNT).flatMap { generationIndex ->
                        robots.mapIndexed { robotIndex, robot ->
                            executor.submit(
                                Callable {
                                    val shard =
                                        File(
                                            shardDirectory,
                                            "batch-${generationIndex + 1}-robot-$robotIndex.csv"
                                        )
                                    val generation =
                                        ScientificDatasetGenerator().generate(
                                            config =
                                                DatasetGenerationConfig(
                                                    datasetName = "context-ablation-10k",
                                                    robots = listOf(robot),
                                                    samplesPerRobot = ROWS_PER_ROBOT_PER_BATCH,
                                                    randomSeed = GENERATION_SEED,
                                                    targetMode = DatasetTargetMode.MIXED,
                                                    reachableFraction = 0.65,
                                                    filterMode = DatasetFilterMode.ALL,
                                                    append = false,
                                                    ikConfig = ContextAblationPilotValidator.solverConfig
                                                ),
                                            csvFile = shard,
                                            existingRowCount =
                                                generationIndex.toLong() * ROWS_PER_BATCH +
                                                    robotIndex.toLong() * ROWS_PER_ROBOT_PER_BATCH,
                                            generationIndex = generationIndex
                                        )
                                    check(generation.completed) {
                                        "Generation failed for batch ${generationIndex + 1}, " +
                                            "robot ${robot.id}: ${generation.message}"
                                    }
                                    check(generation.addedRows == ROWS_PER_ROBOT_PER_BATCH)
                                    GeneratedShard(generationIndex, robotIndex, shard)
                                }
                            )
                        }
                    }
                futures.map { future -> future.get() }
            } finally {
                executor.shutdownNow()
            }

        val temporary = File(outputDirectory, "${output.name}.partial")
        var writtenRows = 0
        temporary.bufferedWriter().use { writer ->
            base.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    writer.appendLine(line)
                    if (index > 0) writtenRows += 1
                }
            }
            shards.sortedWith(compareBy(GeneratedShard::generationIndex, GeneratedShard::robotIndex))
                .forEach { shard ->
                    shard.file.useLines { lines ->
                        lines.drop(1).forEach { line ->
                            writer.appendLine(line)
                            writtenRows += 1
                        }
                    }
                }
        }

        assertEquals(TOTAL_ROWS, writtenRows)
        temporary.copyTo(output, overwrite = true)
        assertTrue(temporary.delete())
        assertTrue(shardDirectory.deleteRecursively())

        println(
            "CONTEXT_ABLATION_DATASET " +
                "path=${output.absolutePath},rows=$writtenRows,robots=${robots.size}," +
                "batches=$BATCH_COUNT,workers=$ROBOT_COUNT,seed=$GENERATION_SEED"
        )
    }

    private data class GeneratedShard(
        val generationIndex: Int,
        val robotIndex: Int,
        val file: File
    )

    private companion object {
        const val ROBOT_COUNT = 4
        const val ROWS_PER_ROBOT_PER_BATCH = 250
        const val ROWS_PER_BATCH = ROBOT_COUNT * ROWS_PER_ROBOT_PER_BATCH
        const val BATCH_COUNT = 10
        const val TOTAL_ROWS = ROWS_PER_BATCH * BATCH_COUNT
        const val GENERATION_SEED = 2_604
    }
}

/** Validate every pilot row before allocating workers or publishing any output. */
internal object ContextAblationPilotValidator {
    val solverConfig = IKConfig(120, 1e-4, 0.05, 0.05)

    fun validate(file: File, rowsPerRobot: Int = 250) {
        require(rowsPerRobot > 0)
        val robots = DatasetRobotPresets().buildDefaults().take(4)
        val reachableCounts = IntArray(robots.size)
        file.bufferedReader().use { reader ->
            val header = com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter.HEADER
            require(reader.readLine() == header.joinToString(",")) { "Pilot ordered CSV schema is incompatible." }
            var rowIndex = 0
            while (true) {
                val line = reader.readLine() ?: break
                require(rowIndex < robots.size * rowsPerRobot) { "Pilot contains too many rows." }
                val values = com.robotkinematicslab.mobile.ml.data.ScientificDatasetTrainingReader.parseCsvLine(line)
                require(values.size == header.size) { "Pilot CSV row has a different schema." }
                val fields = header.zip(values).toMap()
                val robotIndex = rowIndex / rowsPerRobot
                val sampleIndex = rowIndex % rowsPerRobot
                val saved = robots[robotIndex]
                val robot = saved.robot
                fun text(name: String, expected: String) {
                    require(fields[name] == expected) { "Pilot $name is incompatible at row ${rowIndex + 1}." }
                }
                fun number(name: String, expected: Double) {
                    val serialized = java.lang.String.format(java.util.Locale.US, "%.12g", expected).toDouble()
                    require(fields[name]?.toDoubleOrNull() == serialized) { "Pilot $name is incompatible at row ${rowIndex + 1}." }
                }
                fun numbers(name: String, expected: List<Double>) {
                    val actual = fields[name]?.split(';')?.map { it.toDoubleOrNull() }
                    val serialized = expected.map { java.lang.String.format(java.util.Locale.US, "%.12g", it).toDouble() }
                    require(actual == serialized) { "Pilot $name changes robot geometry or joint order at row ${rowIndex + 1}." }
                }
                text("schemaVersion", com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter.SCHEMA_VERSION)
                text("randomProtocol", com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol.ID)
                text("robotId", saved.id)
                text("jointTypes", robot.joints.joinToString(";") { it.type.name })
                number("jointCount", robot.joints.size.toDouble())
                number("globalRowIndex", rowIndex.toDouble())
                number("sampleIndex", sampleIndex.toDouble())
                text("batchId", "batch-1")
                number("baseRandomSeed", 2604.0)
                val robotSeed = com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol.deriveSeed(
                    2604, "dataset-robot", "robot-id=${saved.id}", "generation=0")
                text("robotRandomSeed", robotSeed.toString())
                number("ikMaxIterations", solverConfig.maxIterations.toDouble())
                number("ikToleranceMeters", solverConfig.tolerance)
                number("ikDamping", solverConfig.damping)
                number("ikMaxStep", solverConfig.maxStep)
                numbers("dhThetaRad", robot.dhParameters.map { it.theta })
                numbers("dhDMeters", robot.dhParameters.map { it.d })
                numbers("dhAMeters", robot.dhParameters.map { it.a })
                numbers("dhAlphaRad", robot.dhParameters.map { it.alpha })
                numbers("jointMinValues", robot.joints.map { it.minValue })
                numbers("jointMaxValues", robot.joints.map { it.maxValue })
                numbers("jointHomeValues", robot.joints.map { it.homeValue })
                val policy = com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy()
                number("metricNumericalEpsilon", policy.numericalEpsilon)
                number("metricNearSuccessErrorMeters", policy.nearSuccessErrorMeters)
                number("metricCloseMissErrorMeters", policy.closeMissErrorMeters)
                number("metricStalledImprovementRatioEpsilon", policy.stalledImprovementRatioEpsilon)
                number("metricJointLimitMarginRatio", policy.jointLimitMarginRatio)
                number("metricEasySeedDistanceUpperMeters", policy.easySeedDistanceUpperMeters)
                number("metricMediumSeedDistanceUpperMeters", policy.mediumSeedDistanceUpperMeters)
                number("metricHardSeedDistanceUpperMeters", policy.hardSeedDistanceUpperMeters)
                number("metricLogConditionNumberCap", policy.logConditionNumberCap)
                when (fields["targetClass"]) {
                    "FK_PROVEN_REACHABLE" -> reachableCounts[robotIndex]++
                    "GUARANTEED_UNREACHABLE" -> Unit
                    else -> error("Pilot target class is incompatible.")
                }
                rowIndex++
            }
            require(rowIndex == robots.size * rowsPerRobot) { "Pilot row count or per-robot balance is incompatible." }
        }
        val expectedReachable = (rowsPerRobot * 0.65 + 0.5).toInt()
        require(reachableCounts.all { it == expectedReachable }) { "Pilot reachable/unreachable balance is incompatible." }
    }
}
