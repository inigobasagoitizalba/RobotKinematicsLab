package com.robotkinematicslab.mobile.workspace.analysis

import com.robotkinematicslab.mobile.performance.compute.ComputeWorkerThreadFactory
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.RobotReachEnvelope
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.reproducibility.ScientificRandom
import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.fk.MutableFKPositionOnlyResult
import com.robotkinematicslab.mobile.validation.robot.RobotDefinitionValidator
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

class RobotWorkspaceAnalyzer(
    private val validator: RobotDefinitionValidator = RobotDefinitionValidator(),
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    fun analyze(
        robot: RobotDefinition,
        config: RobotWorkspaceAnalysisConfig,
        requestedWorkerCount: Int = 1,
        cancellationRequested: () -> Boolean = { false },
        onProgress: (RobotWorkspaceAnalysisProgress) -> Unit = {}
    ): RobotWorkspaceStudy {
        val startedAt = clockMillis()
        val totalWork = config.sampleCount + FIXED_WORK_UNITS
        report(onProgress, WorkspaceAnalysisPhase.VALIDATING_ROBOT, 0, totalWork, "Validating DH rows and joint limits.")
        ensureNotCancelled(cancellationRequested)
        val validation = validator.validate(robot)
        require(validation.isValid) {
            "Workspace analysis requires a valid robot: ${validation.issues.joinToString { it.message }}"
        }
        val radius = RobotReachEnvelope.conservativeRadialUpperBound(robot)
        require(radius.isFinite() && radius > 0.0) {
            "The robot must have a positive finite conservative reach envelope."
        }
        val fingerprint = robotWorkspaceFingerprint(robot)

        report(
            onProgress,
            WorkspaceAnalysisPhase.BUILDING_SAMPLE_SEQUENCE,
            1,
            totalWork,
            "Building a reproducible shifted-Halton joint-space sequence."
        )
        val plans = buildSamplePlan(robot, config, fingerprint)
        ensureNotCancelled(cancellationRequested)

        val workerCount = effectiveWorkerCount(requestedWorkerCount, plans.size)
        val completed = AtomicInteger(0)
        val samples = ArrayList<RobotWorkspaceSample>(plans.size)
        var invalidSamples = 0
        val executor =
            Executors.newFixedThreadPool(
                workerCount,
                ComputeWorkerThreadFactory("workspace-worker")
            )
        try {
            val tasks =
                List(workerCount) { workerIndex ->
                    Callable {
                        val solver = ForwardKinematicsSolver()
                        val output = MutableFKPositionOnlyResult()
                        val local = ArrayList<RobotWorkspaceSample>((plans.size / workerCount) + 1)
                        var localInvalid = 0
                        var index = workerIndex
                        while (index < plans.size) {
                            ensureNotCancelled(cancellationRequested)
                            val plan = plans[index]
                            if (solver.solvePositionOnlyInto(robot, plan.jointValues, output)) {
                                local +=
                                    RobotWorkspaceSample(
                                        sequenceIndex = plan.sequenceIndex,
                                        replicationIndex = plan.replicationIndex,
                                        kind = plan.kind,
                                        jointValues = plan.jointValues,
                                        endEffector = Vec3(output.x, output.y, output.z)
                                    )
                            } else {
                                localInvalid++
                            }
                            val done = completed.incrementAndGet()
                            if (done == 1 || done % PROGRESS_INTERVAL == 0 || done == plans.size) {
                                report(
                                    onProgress,
                                    WorkspaceAnalysisPhase.RUNNING_FORWARD_KINEMATICS,
                                    2 + done,
                                    totalWork,
                                    "Mapping joint-space samples through forward kinematics."
                                )
                            }
                            index += workerCount
                        }
                        WorkerResult(local, localInvalid)
                    }
                }
            val futures =
                try {
                    executor.invokeAll(tasks)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw RobotWorkspaceAnalysisCancelledException()
                }
            futures.forEach { future ->
                val result =
                    try {
                        future.get()
                    } catch (error: ExecutionException) {
                        val cause = error.cause
                        if (cause is RuntimeException) throw cause
                        throw IllegalStateException("Robot workspace analysis worker failed.", cause)
                    }
                samples += result.samples
                invalidSamples += result.invalidCount
            }
        } finally {
            executor.shutdownNow()
        }
        ensureNotCancelled(cancellationRequested)
        samples.sortBy(RobotWorkspaceSample::sequenceIndex)
        require(samples.isNotEmpty()) { "Forward kinematics rejected every workspace sample." }

        report(
            onProgress,
            WorkspaceAnalysisPhase.BUILDING_VOXELS,
            config.sampleCount + 3,
            totalWork,
            "Mapping reachable samples into the conservative 3D envelope."
        )
        val voxelization = buildVoxels(samples, radius, config.voxelResolution)
        ensureNotCancelled(cancellationRequested)

        report(
            onProgress,
            WorkspaceAnalysisPhase.CLASSIFYING_SPACE,
            config.sampleCount + 4,
            totalWork,
            "Separating observed core, observed frontier and unobserved candidates."
        )
        val cellVolume = voxelization.cellSize * voxelization.cellSize * voxelization.cellSize
        val observedCount = voxelization.voxels.count { it.classification != WorkspaceVoxelClass.UNOBSERVED_CANDIDATE }
        val unobservedCount = voxelization.voxels.size - observedCount
        val observedVolume = observedCount * cellVolume
        val unobservedVolume = unobservedCount * cellVolume
        val classifiedVolume = voxelization.voxels.size * cellVolume
        val convergence = buildConvergence(samples, radius, config.voxelResolution, cellVolume)
        val priorVolume = convergence.getOrNull(convergence.lastIndex - 1)?.observedVoxelVolumeCubicMeters ?: observedVolume
        val relativeGain =
            if (observedVolume > 0.0) {
                ((observedVolume - priorVolume).coerceAtLeast(0.0) / observedVolume).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

        report(
            onProgress,
            WorkspaceAnalysisPhase.FINALIZING,
            config.sampleCount + 5,
            totalWork,
            "Finalizing reproducibility and empirical convergence evidence."
        )
        val createdAt = clockMillis()
        val safeName = robot.name.trim().ifBlank { "robot" }
        val study =
            RobotWorkspaceStudy(
                studyId = "workspace-$createdAt-${STUDY_SEQUENCE.incrementAndGet()}-${fingerprint.take(12)}",
                studyName = "$safeName workspace",
                createdAtEpochMillis = createdAt,
                robotFingerprint = fingerprint,
                robot = robot,
                config = config,
                workerCount = workerCount,
                durationMillis = (createdAt - startedAt).coerceAtLeast(0L),
                conservativeRadiusMeters = radius,
                voxelCellSizeMeters = voxelization.cellSize,
                conservativeSphereVolumeCubicMeters = (4.0 / 3.0) * PI * radius * radius * radius,
                classifiedEnvelopeVolumeCubicMeters = classifiedVolume,
                observedVoxelVolumeCubicMeters = observedVolume,
                unobservedCandidateVolumeCubicMeters = unobservedVolume,
                observedEnvelopeFraction = observedCount.toDouble() / voxelization.voxels.size.toDouble(),
                lastQuarterRelativeVolumeGain = relativeGain,
                invalidSampleCount = invalidSamples,
                samples = samples,
                voxels = voxelization.voxels,
                convergence = convergence
            )
        report(onProgress, WorkspaceAnalysisPhase.COMPLETED, totalWork, totalWork, "Robot workspace study is ready.")
        return study
    }

    private fun buildSamplePlan(
        robot: RobotDefinition,
        config: RobotWorkspaceAnalysisConfig,
        fingerprint: String
    ): List<SamplePlan> {
        val plans = ArrayList<SamplePlan>(config.sampleCount)
        val home = robot.joints.map { it.homeValue }

        fun add(kind: WorkspaceSampleKind, values: List<Double>, replication: Int = -1) {
            if (plans.size < config.sampleCount) {
                plans += SamplePlan(plans.size, replication, kind, values)
            }
        }

        add(WorkspaceSampleKind.HOME, home)
        robot.joints.forEachIndexed { index, joint ->
            add(WorkspaceSampleKind.SINGLE_JOINT_LIMIT, home.toMutableList().also { it[index] = joint.minValue })
            add(WorkspaceSampleKind.SINGLE_JOINT_LIMIT, home.toMutableList().also { it[index] = joint.maxValue })
        }

        val cornerRandom =
            ScientificRandom(
                ScientificRandomProtocol.deriveSeed(config.randomSeed, "workspace-corners", fingerprint)
            )
        buildJointLimitCorners(robot, cornerRandom).forEach { values ->
            add(WorkspaceSampleKind.JOINT_LIMIT_CORNER, values)
        }

        val haltonBases = firstPrimeBases(robot.joints.size)
        val shifts =
            List(config.replicationCount) { replication ->
                val random =
                    ScientificRandom(
                        ScientificRandomProtocol.deriveSeed(
                            config.randomSeed,
                            "workspace-halton-shift",
                            fingerprint,
                            replication.toString()
                        )
                    )
                DoubleArray(robot.joints.size) { random.nextDouble(0.0, 1.0) }
            }
        var qmcIndex = 0
        while (plans.size < config.sampleCount) {
            val replication = qmcIndex % config.replicationCount
            val indexWithinReplication = qmcIndex / config.replicationCount + 1
            val values =
                robot.joints.mapIndexed { dimension, joint ->
                    val unit =
                        (radicalInverse(indexWithinReplication, haltonBases[dimension]) + shifts[replication][dimension]) % 1.0
                    joint.minValue + (joint.maxValue - joint.minValue) * unit
                }
            add(WorkspaceSampleKind.QUASI_RANDOM, values, replication)
            qmcIndex++
        }
        return plans
    }

    /**
     * Keeps the historical, fully enumerated protocol for the original 1-10 joint range. Higher
     * dimensional robots cannot enumerate 2^N corners safely, so a small deterministic design is
     * used instead: both global extremes, two alternating extremes and seeded unique probes.
     */
    private fun buildJointLimitCorners(
        robot: RobotDefinition,
        random: ScientificRandom
    ): List<List<Double>> {
        if (robot.joints.size <= MAXIMUM_ENUMERATED_CORNER_DIMENSIONS) {
            val totalCorners = 1 shl robot.joints.size
            val cornerIndices = (0 until totalCorners).toMutableList()
            for (index in cornerIndices.lastIndex downTo 1) {
                val swap = random.nextInt(index + 1)
                val value = cornerIndices[index]
                cornerIndices[index] = cornerIndices[swap]
                cornerIndices[swap] = value
            }
            return cornerIndices.take(min(MAXIMUM_CORNER_PROBES, totalCorners)).map { corner ->
                robot.joints.mapIndexed { jointIndex, joint ->
                    if (corner and (1 shl jointIndex) == 0) joint.minValue else joint.maxValue
                }
            }
        }

        val corners = linkedSetOf<List<Double>>()

        fun structuredCorner(useMaximum: (Int) -> Boolean): List<Double> =
            robot.joints.mapIndexed { index, joint ->
                if (useMaximum(index)) joint.maxValue else joint.minValue
            }

        corners += structuredCorner { false }
        corners += structuredCorner { true }
        corners += structuredCorner { index -> index % 2 == 0 }
        corners += structuredCorner { index -> index % 2 != 0 }

        while (corners.size < MAXIMUM_CORNER_PROBES) {
            corners += structuredCorner { random.nextInt(2) == 1 }
        }
        return corners.toList()
    }

    /** Returns one mutually prime Halton base per joint without imposing a dimension ceiling. */
    private fun firstPrimeBases(count: Int): IntArray {
        val result = IntArray(count)
        var found = 0
        var candidate = 2
        while (found < count) {
            var divisor = 2
            var prime = true
            while (divisor * divisor <= candidate) {
                if (candidate % divisor == 0) {
                    prime = false
                    break
                }
                divisor++
            }
            if (prime) result[found++] = candidate
            candidate++
        }
        return result
    }

    private fun buildVoxels(
        samples: List<RobotWorkspaceSample>,
        radius: Double,
        resolution: Int
    ): Voxelization {
        val cellSize = radius * 2.0 / resolution.toDouble()
        val occupied = linkedMapOf<VoxelKey, MutableVoxelHit>()
        samples.forEach { sample ->
            val key = voxelKey(sample.endEffector, radius, cellSize, resolution)
            occupied.getOrPut(key) { MutableVoxelHit(sample.sequenceIndex) }.add(sample.sequenceIndex)
        }

        val envelope = linkedSetOf<VoxelKey>()
        val halfDiagonal = sqrt(3.0) * cellSize * 0.5
        repeat(resolution) { x ->
            repeat(resolution) { y ->
                repeat(resolution) { z ->
                    val key = VoxelKey(x, y, z)
                    val center = voxelCenter(key, radius, cellSize)
                    if (center.norm() <= radius + halfDiagonal) envelope += key
                }
            }
        }
        occupied.keys.forEach(envelope::add)

        val voxels =
            envelope.map { key ->
                val hit = occupied[key]
                val classification =
                    if (hit == null) {
                        WorkspaceVoxelClass.UNOBSERVED_CANDIDATE
                    } else if (NEIGHBOURS.all { delta -> VoxelKey(key.x + delta.x, key.y + delta.y, key.z + delta.z) in occupied }) {
                        WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE
                    } else {
                        WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY
                    }
                RobotWorkspaceVoxel(
                    xIndex = key.x,
                    yIndex = key.y,
                    zIndex = key.z,
                    center = voxelCenter(key, radius, cellSize),
                    classification = classification,
                    sampleHitCount = hit?.count ?: 0,
                    firstObservedSampleIndex = hit?.firstSampleIndex
                )
            }
        return Voxelization(voxels, cellSize)
    }

    private fun buildConvergence(
        samples: List<RobotWorkspaceSample>,
        radius: Double,
        resolution: Int,
        cellVolume: Double
    ): List<WorkspaceConvergenceCheckpoint> {
        val thresholds =
            listOf(0.25, 0.50, 0.75, 1.0)
                .map { fraction -> (samples.size * fraction).toInt().coerceIn(1, samples.size) }
                .distinct()
        val occupied = hashSetOf<VoxelKey>()
        val checkpoints = ArrayList<WorkspaceConvergenceCheckpoint>(thresholds.size)
        var thresholdIndex = 0
        samples.forEachIndexed { index, sample ->
            occupied += voxelKey(sample.endEffector, radius, radius * 2.0 / resolution, resolution)
            if (thresholdIndex < thresholds.size && index + 1 == thresholds[thresholdIndex]) {
                checkpoints +=
                    WorkspaceConvergenceCheckpoint(
                        sampleCount = index + 1,
                        occupiedVoxelCount = occupied.size,
                        observedVoxelVolumeCubicMeters = occupied.size * cellVolume
                    )
                thresholdIndex++
            }
        }
        return checkpoints
    }

    private fun radicalInverse(index: Int, base: Int): Double {
        var value = index
        var inverseBase = 1.0 / base.toDouble()
        var result = 0.0
        while (value > 0) {
            result += (value % base) * inverseBase
            value /= base
            inverseBase /= base.toDouble()
        }
        return result
    }

    private fun voxelKey(point: Vec3, radius: Double, cellSize: Double, resolution: Int): VoxelKey =
        VoxelKey(
            x = floor((point.x + radius) / cellSize).toInt().coerceIn(0, resolution - 1),
            y = floor((point.y + radius) / cellSize).toInt().coerceIn(0, resolution - 1),
            z = floor((point.z + radius) / cellSize).toInt().coerceIn(0, resolution - 1)
        )

    private fun voxelCenter(key: VoxelKey, radius: Double, cellSize: Double): Vec3 =
        Vec3(
            -radius + (key.x + 0.5) * cellSize,
            -radius + (key.y + 0.5) * cellSize,
            -radius + (key.z + 0.5) * cellSize
        )

    private fun report(
        callback: (RobotWorkspaceAnalysisProgress) -> Unit,
        phase: WorkspaceAnalysisPhase,
        completed: Int,
        total: Int,
        message: String
    ) {
        callback(RobotWorkspaceAnalysisProgress(phase, completed.coerceIn(0, total), total, message))
    }

    private fun ensureNotCancelled(cancellationRequested: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || cancellationRequested()) {
            throw RobotWorkspaceAnalysisCancelledException()
        }
    }

    private data class SamplePlan(
        val sequenceIndex: Int,
        val replicationIndex: Int,
        val kind: WorkspaceSampleKind,
        val jointValues: List<Double>
    )

    private data class WorkerResult(
        val samples: List<RobotWorkspaceSample>,
        val invalidCount: Int
    )

    private data class VoxelKey(val x: Int, val y: Int, val z: Int)
    private data class VoxelDelta(val x: Int, val y: Int, val z: Int)
    private data class Voxelization(val voxels: List<RobotWorkspaceVoxel>, val cellSize: Double)

    private class MutableVoxelHit(firstIndex: Int) {
        var count: Int = 0
            private set
        var firstSampleIndex: Int = firstIndex
            private set

        fun add(index: Int) {
            count++
            if (index < firstSampleIndex) firstSampleIndex = index
        }
    }

    companion object {
        private const val MAXIMUM_ENUMERATED_CORNER_DIMENSIONS = 10
        const val MAXIMUM_WORKERS = 8

        fun effectiveWorkerCount(requested: Int, samples: Int): Int {
            require(samples > 0)
            return requested.coerceIn(1, min(MAXIMUM_WORKERS, samples))
        }
        private const val MAXIMUM_CORNER_PROBES = 64
        private const val PROGRESS_INTERVAL = 256
        private const val FIXED_WORK_UNITS = 6
        private val STUDY_SEQUENCE = AtomicLong(0L)
        private val NEIGHBOURS =
            listOf(
                VoxelDelta(-1, 0, 0),
                VoxelDelta(1, 0, 0),
                VoxelDelta(0, -1, 0),
                VoxelDelta(0, 1, 0),
                VoxelDelta(0, 0, -1),
                VoxelDelta(0, 0, 1)
            )
    }
}

fun robotWorkspaceFingerprint(robot: RobotDefinition): String {
    val canonical =
        buildString {
            append(robot.name.trim())
            robot.joints.indices.forEach { index ->
                val joint = robot.joints[index]
                val dh = robot.dhParameters[index]
                append('|').append(joint.name.trim())
                append('|').append(joint.type.name)
                append('|').append(java.lang.Double.doubleToLongBits(joint.minValue))
                append('|').append(java.lang.Double.doubleToLongBits(joint.maxValue))
                append('|').append(java.lang.Double.doubleToLongBits(joint.homeValue))
                append('|').append(java.lang.Double.doubleToLongBits(dh.theta))
                append('|').append(java.lang.Double.doubleToLongBits(dh.d))
                append('|').append(java.lang.Double.doubleToLongBits(dh.a))
                append('|').append(java.lang.Double.doubleToLongBits(dh.alpha))
            }
        }
    val fingerprint = ScientificRandomProtocol.deriveSeed(0, "robot-workspace-definition", canonical)
    return java.lang.Long.toUnsignedString(fingerprint, 16).lowercase(Locale.US).padStart(16, '0')
}

class RobotWorkspaceAnalysisCancelledException : RuntimeException("Robot workspace analysis cancelled.")
