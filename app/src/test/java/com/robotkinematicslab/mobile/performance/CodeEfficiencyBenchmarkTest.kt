package com.robotkinematicslab.mobile.performance

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.RobotLibraryCodec
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticPerformanceConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSamplingConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSeedConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticSolverConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticTopologyConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.DiagnosticStatsAggregator
import com.robotkinematicslab.mobile.diagnostics.benchmark.execution.Layer1DiagnosticExperiment
import com.robotkinematicslab.mobile.diagnostics.benchmark.generation.DiagnosticRobotFactory
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.render.buildRobotSceneViewport
import com.robotkinematicslab.mobile.render.buildRobotSceneProjectionContext
import com.robotkinematicslab.mobile.render.projectRobotScenePoint
import com.robotkinematicslab.mobile.render.robotWorkspaceRadius
import com.robotkinematicslab.mobile.render.RobotScenePoint
import com.robotkinematicslab.mobile.service.KinematicsService
import com.robotkinematicslab.mobile.ui.charts.spatial.DiagnosticWorkspaceFilter
import com.robotkinematicslab.mobile.ui.charts.spatial.buildDiagnosticWorkspace3DData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Repeatable macro benchmark for before/after engineering comparisons.
 *
 * It intentionally has no timing assertions: CI and laptops have different performance. Exact
 * checksums are asserted across rounds so an optimization cannot silently change the work being
 * measured. Run this test alone with a quiet machine and compare EFFICIENCY lines.
 */
class CodeEfficiencyBenchmarkTest {

    @Test
    fun representativeApplicationWorkloads() {
        benchmarkForwardKinematics()
        benchmarkInverseKinematics()
        benchmarkDiagnosticPipeline()
        benchmarkDiagnosticAggregation()
        benchmarkWorkspacePreparation()
        benchmarkRobotSceneProjection()
        benchmarkRobotLibraryCodec()
    }

    private fun benchmarkForwardKinematics() {
        val robot =
            DiagnosticRobotFactory().buildSeedRobot(
                linkCount = 10,
                jointMode = DiagnosticJointMode.MIXED,
                stressLevel = 0.85
            )
        val states =
            List(256) { sample ->
                RobotState(
                    robot.joints.mapIndexed { index, joint ->
                        val fraction = ((sample * 37 + index * 17) % 251) / 250.0
                        joint.minValue + (joint.maxValue - joint.minValue) * fraction
                    }
                )
            }
        val service = KinematicsService()

        benchmark(name = "fk_10_link", operations = FK_OPERATIONS) {
            var checksum = 0L
            repeat(FK_OPERATIONS) { index ->
                val result = service.computeFK(robot, states[index and 255])
                checksum = checksum xor result.endEffectorPosition.x.toBits()
                checksum += result.endEffectorPosition.y.toBits()
                checksum = checksum xor result.endEffectorPosition.z.toBits()
            }
            checksum
        }
    }

    private fun benchmarkInverseKinematics() {
        val robot =
            DiagnosticRobotFactory().buildSeedRobot(
                linkCount = 3,
                jointMode = DiagnosticJointMode.REVOLUTE_ONLY,
                stressLevel = 0.35
            )
        val service =
            KinematicsService(
                IKConfig(
                    maxIterations = 120,
                    tolerance = 1e-5,
                    damping = 0.05,
                    maxStep = 0.02
                )
            )
        val home = RobotState(robot.joints.map { it.homeValue })
        val targets =
            List(IK_OPERATIONS) { sample ->
                val source =
                    RobotState(
                        robot.joints.mapIndexed { index, joint ->
                            val fraction = ((sample * 13 + index * 29) % 97) / 96.0
                            joint.minValue + (joint.maxValue - joint.minValue) * fraction
                        }
                    )
                service.computeFK(robot, source).endEffectorPosition
            }

        benchmark(name = "ik_3_link", operations = IK_OPERATIONS) {
            var checksum = 0L
            targets.forEach { target ->
                val result = service.computeIK(robot, home, target)
                checksum += result.iterations.toLong()
                checksum = checksum xor result.finalError.toBits()
                result.state.jointValues.forEach { checksum = checksum xor it.toBits() }
            }
            checksum
        }
    }

    private fun benchmarkDiagnosticPipeline() {
        val config = diagnosticConfig()

        benchmark(
            name = "diagnostic_pipeline",
            operations = DIAGNOSTIC_SEQUENTIAL_OPERATIONS,
            warmupRounds = 1,
            measuredRounds = 4
        ) {
            val report = Layer1DiagnosticExperiment().runExperiment(config)
            var checksum = report.runResults.size.toLong()
            report.runResults.forEach { run ->
                checksum += run.iterations.toLong()
                checksum = checksum xor run.finalError.toBits()
            }
            checksum
        }
    }

    private fun benchmarkWorkspacePreparation() {
        val report = Layer1DiagnosticExperiment().runExperiment(diagnosticConfig())
        val source = report.runResults.first()
        val runs =
            List(WORKSPACE_RUN_COUNT) { index ->
                source.copy(
                    runIndex = index,
                    target =
                        source.target.copy(
                            x = ((index % 100) - 50) / 25.0,
                            y = (((index / 100) % 100) - 50) / 25.0,
                            z = ((index % 37) - 18) / 12.0
                        ),
                    finalError = (index % 1000) / 100_000.0,
                    iterations = index % 121,
                    jointLimitPressureRatio = (index % 100) / 100.0
                )
            }

        benchmark(name = "workspace_3d_prepare", operations = WORKSPACE_RUN_COUNT) {
            val data =
                buildDiagnosticWorkspace3DData(
                    allRuns = runs,
                    filter = DiagnosticWorkspaceFilter()
                )
            var checksum = data.points.sumOf { it.runCount }.toLong()
            data.points.forEach { point ->
                checksum = checksum xor point.position.x.toBits()
                checksum += point.acceptedCount.toLong()
            }
            checksum
        }
    }

    private fun benchmarkDiagnosticAggregation() {
        val report = Layer1DiagnosticExperiment().runExperiment(diagnosticConfig())
        val sourceRuns = report.runResults
        val runs =
            List(AGGREGATION_RUN_COUNT) { index ->
                sourceRuns[index % sourceRuns.size].copy(runIndex = index)
            }
        val aggregator = DiagnosticStatsAggregator(report.config.metricPolicy)

        benchmark(name = "diagnostic_aggregation", operations = AGGREGATION_RUN_COUNT) {
            val summary = aggregator.buildSummary(report.targetCases, runs)
            val statuses = aggregator.buildStatusDistribution(runs)
            val details = aggregator.buildDetailCodeDistribution(runs)
            val acceptance = aggregator.buildDatasetAcceptanceSummary(runs)
            val cases = aggregator.buildPerCaseAggregates(report.targetCases, runs)
            val transitions = aggregator.buildTransitionAggregates(runs)
            val extremes = aggregator.buildExtremeRunSummaries(runs)
            var checksum = summary.totalRuns.toLong() + summary.sequentialAcceptedCount
            checksum += statuses.sumOf { it.count }.toLong()
            checksum += details.sumOf { it.count }.toLong()
            checksum += acceptance.reachableAcceptedCount.toLong()
            checksum += cases.sumOf { it.sequentialRunCount }.toLong()
            checksum += transitions.sumOf { it.runCount }.toLong()
            checksum += extremes.sumOf { it.runIndex }.toLong()
            checksum
        }
    }

    private fun benchmarkRobotSceneProjection() {
        val robot =
            DiagnosticRobotFactory().buildSeedRobot(
                linkCount = 10,
                jointMode = DiagnosticJointMode.MIXED,
                stressLevel = 0.85
            )
        val state = RobotState(robot.joints.map { it.homeValue })
        val positions = KinematicsService().computeFK(robot, state).jointPositions
        val viewport = buildRobotSceneViewport(1080f, 1600f, robotWorkspaceRadius(robot))
        val operationCount = RENDER_REPETITIONS * positions.size

        val legacyChecksum = benchmark(name = "robot_scene_projection_legacy", operations = operationCount) {
            var checksum = 0L
            repeat(RENDER_REPETITIONS) { repetition ->
                positions.forEach { position ->
                    val projected =
                        legacyProjectRobotScenePoint(
                            point = position,
                            viewport = viewport,
                            yaw = -0.65f + (repetition and 1) * 0.0001f,
                            pitch = 0.45f
                        )
                    checksum += projected.x.toRawBits().toLong()
                    checksum = checksum xor projected.y.toRawBits().toLong()
                }
            }
            checksum
        }
        val optimizedChecksum = benchmark(name = "robot_scene_projection_context", operations = operationCount) {
            var checksum = 0L
            repeat(RENDER_REPETITIONS) { repetition ->
                val projectionContext =
                    buildRobotSceneProjectionContext(
                        viewport = viewport,
                        yaw = -0.65f + (repetition and 1) * 0.0001f,
                        pitch = 0.45f
                    )
                positions.forEach { position ->
                    val projected = projectRobotScenePoint(position, projectionContext)
                    checksum += projected.x.toRawBits().toLong()
                    checksum = checksum xor projected.y.toRawBits().toLong()
                }
            }
            checksum
        }
        assertEquals(legacyChecksum, optimizedChecksum)
    }

    private fun legacyProjectRobotScenePoint(
        point: com.robotkinematicslab.mobile.math.utility.Vec3,
        viewport: com.robotkinematicslab.mobile.render.RobotSceneViewport,
        yaw: Float,
        pitch: Float
    ): RobotScenePoint {
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        val z = point.z.toFloat()
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val rotatedX = x * cosYaw - z * sinYaw
        val rotatedZ = x * sinYaw + z * cosYaw
        val rotatedY = y * cosPitch - rotatedZ * sinPitch
        return RobotScenePoint(
            x = viewport.centerX + rotatedX * viewport.pixelsPerMeter,
            y = viewport.centerY - rotatedY * viewport.pixelsPerMeter
        )
    }

    private fun benchmarkRobotLibraryCodec() {
        val presets = DatasetRobotPresets().buildDefaults()
        val robots =
            List(CODEC_ROBOT_COUNT) { index ->
                val preset = presets[index % presets.size]
                preset.copy(id = "${preset.id}-$index")
            }
        val codec = RobotLibraryCodec()

        benchmark(name = "robot_library_round_trip", operations = CODEC_ROBOT_COUNT) {
            val output = ByteArrayOutputStream()
            codec.write(robots, output)
            val decoded = codec.read(ByteArrayInputStream(output.toByteArray()))
            var checksum = decoded.size.toLong()
            decoded.forEach { saved ->
                checksum += saved.id.hashCode().toLong()
                checksum += saved.robot.joints.size.toLong()
            }
            checksum
        }
    }

    private fun diagnosticConfig(): DiagnosticBenchmarkConfig {
        return DiagnosticBenchmarkConfig(
            sampling =
                DiagnosticSamplingConfig(
                    reachableCount = 4,
                    unreachableCount = 3,
                    runCount = DIAGNOSTIC_SAMPLES_PER_LINK,
                    samplesPerLinkCount = DIAGNOSTIC_SAMPLES_PER_LINK
                ),
            seeds = DiagnosticSeedConfig(listOf(42)),
            topology =
                DiagnosticTopologyConfig(
                    robotLinkCount = 2,
                    minLinkCount = 2,
                    maxLinkCount = 4,
                    jointMode = DiagnosticJointMode.AUTO,
                    runAllTopologies = false,
                    stressLevel = 0.5
                ),
            solver = DiagnosticSolverConfig(ikMaxIterations = 80),
            performance =
                DiagnosticPerformanceConfig(
                    storeFullRunHistory = true,
                    storePerCaseDetails = true,
                    storeTransitionDetails = true,
                    storeExtremeRunDetails = true
                )
        )
    }

    private fun benchmark(
        name: String,
        operations: Int,
        warmupRounds: Int = 2,
        measuredRounds: Int = 7,
        block: () -> Long
    ): Long {
        repeat(warmupRounds) {
            benchmarkSink = benchmarkSink xor block()
        }

        val wallSamples = LongArray(measuredRounds)
        val checksums = LongArray(measuredRounds)

        repeat(measuredRounds) { round ->
            val wallBefore = System.nanoTime()
            val checksum = block()
            wallSamples[round] = System.nanoTime() - wallBefore
            checksums[round] = checksum
            benchmarkSink = benchmarkSink xor checksum
        }

        checksums.forEach { checksum -> assertEquals(checksums.first(), checksum) }
        assertEquals(
            "The optimized workload changed its deterministic output for $name.",
            EXPECTED_CHECKSUMS.getValue(name),
            checksums.first()
        )
        val wallMedian = wallSamples.median()

        println(
            "EFFICIENCY|$name|operations=$operations" +
                "|wall_ns=$wallMedian|wall_ns_per_op=${wallMedian.toDouble() / operations}" +
                "|checksum=${checksums.first()}"
        )
        return checksums.first()
    }

    private fun LongArray.median(): Long {
        val sorted = sortedArray()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val FK_OPERATIONS = 20_000
        const val IK_OPERATIONS = 32
        const val DIAGNOSTIC_SAMPLES_PER_LINK = 12
        const val DIAGNOSTIC_SEQUENTIAL_OPERATIONS = 36
        const val WORKSPACE_RUN_COUNT = 10_000
        const val AGGREGATION_RUN_COUNT = 50_000
        const val RENDER_REPETITIONS = 25_000
        const val CODEC_ROBOT_COUNT = 1_000

        val EXPECTED_CHECKSUMS =
            mapOf(
                "fk_10_link" to 6440952178041079730L,
                "ik_3_link" to -9183416201819146706L,
                "diagnostic_pipeline" to 9198792426027480584L,
                "diagnostic_aggregation" to 196953L,
                "workspace_3d_prepare" to -4896865040090671395L,
                "robot_scene_projection_legacy" to 307998743490560L,
                "robot_scene_projection_context" to 307998743490560L,
                "robot_library_round_trip" to -600833663656L
            )

        @Volatile
        var benchmarkSink: Long = 0L
    }
}
