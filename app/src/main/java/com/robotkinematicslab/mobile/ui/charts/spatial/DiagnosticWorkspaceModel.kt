package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.math.utility.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class WorkspaceColorMetric(
    val title: String,
    val description: String,
    val unit: String
) {
    OUTCOME(
        title = "Outcome",
        description = "Green is accepted; red is rejected.",
        unit = "% accepted"
    ),
    FINAL_ERROR(
        title = "Final error",
        description = "Green is lower residual error; red is higher.",
        unit = "m"
    ),
    ITERATIONS(
        title = "Iterations",
        description = "Green is lower solver cost; red is higher.",
        unit = "iterations"
    ),
    JOINT_LIMIT_PRESSURE(
        title = "Joint pressure",
        description = "Green is farther from limits; red is higher pressure.",
        unit = "ratio"
    )
}

data class DiagnosticWorkspaceFilter(
    val seed: Int? = null,
    val linkCount: Int? = null,
    val jointMode: DiagnosticJointMode? = null
)

data class WorkspaceBounds3D(
    val minimum: Vec3,
    val maximum: Vec3,
    val center: Vec3,
    val radius: Double
)

data class WorkspacePointAggregate(
    val id: String,
    val position: Vec3,
    val runCount: Int,
    val acceptedCount: Int,
    val averageFinalError: Double,
    val averageIterations: Double,
    val averageJointLimitPressure: Double,
    val representativeRun: DiagnosticRunResult
) {
    val acceptanceRate: Double
        get() = acceptedCount.toDouble() / runCount.coerceAtLeast(1).toDouble()

    val isAggregated: Boolean
        get() = runCount > 1

    fun value(metric: WorkspaceColorMetric): Double {
        return when (metric) {
            WorkspaceColorMetric.OUTCOME -> acceptanceRate
            WorkspaceColorMetric.FINAL_ERROR -> averageFinalError
            WorkspaceColorMetric.ITERATIONS -> averageIterations
            WorkspaceColorMetric.JOINT_LIMIT_PRESSURE -> averageJointLimitPressure
        }
    }
}

data class DiagnosticWorkspace3DData(
    val points: List<WorkspacePointAggregate>,
    val bounds: WorkspaceBounds3D,
    val totalRunCount: Int,
    val validPositionCount: Int,
    val filteredRunCount: Int,
    val invalidPositionCount: Int,
    val voxelResolution: Int?
) {
    val displayedPointCount: Int
        get() = points.size

    val isAggregated: Boolean
        get() = voxelResolution != null
}

data class WorkspaceMetricRange(
    val minimum: Double,
    val maximum: Double
)

data class WorkspaceCamera(
    val yaw: Float = -0.65f,
    val pitch: Float = 0.45f,
    val zoom: Float = 1f
)

data class WorkspaceProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float
)

internal data class WorkspaceProjectionContext(
    val bounds: WorkspaceBounds3D,
    val safeWidth: Float,
    val safeHeight: Float,
    val cosYaw: Float,
    val sinYaw: Float,
    val cosPitch: Float,
    val sinPitch: Float,
    val pixelsPerMeter: Float
)

internal fun validWorkspaceRuns(
    runs: List<DiagnosticRunResult>
): List<DiagnosticRunResult> {
    return runs.filter { run ->
        run.target.x.isFinite() && run.target.y.isFinite() && run.target.z.isFinite()
    }
}

internal fun filterWorkspaceRuns(
    runs: List<DiagnosticRunResult>,
    filter: DiagnosticWorkspaceFilter
): List<DiagnosticRunResult> {
    return runs.filter { run ->
        (filter.seed == null || run.seed == filter.seed) &&
                (filter.linkCount == null || run.linkCount == filter.linkCount) &&
                (filter.jointMode == null || run.jointMode == filter.jointMode)
    }
}

internal fun calculateWorkspaceBounds(
    runs: List<DiagnosticRunResult>
): WorkspaceBounds3D {
    if (runs.isEmpty()) {
        return WorkspaceBounds3D(
            minimum = Vec3(-1.0, -1.0, -1.0),
            maximum = Vec3(1.0, 1.0, 1.0),
            center = Vec3.ZERO,
            radius = 1.0
        )
    }

    var minX = 0.0
    var minY = 0.0
    var minZ = 0.0
    var maxX = 0.0
    var maxY = 0.0
    var maxZ = 0.0

    runs.forEach { run ->
        minX = min(minX, run.target.x)
        minY = min(minY, run.target.y)
        minZ = min(minZ, run.target.z)
        maxX = max(maxX, run.target.x)
        maxY = max(maxY, run.target.y)
        maxZ = max(maxZ, run.target.z)
    }

    val center =
        Vec3(
            x = (minX + maxX) * 0.5,
            y = (minY + maxY) * 0.5,
            z = (minZ + maxZ) * 0.5
        )
    val halfX = (maxX - minX) * 0.5
    val halfY = (maxY - minY) * 0.5
    val halfZ = (maxZ - minZ) * 0.5
    val radius = max(halfX, max(halfY, halfZ)).coerceAtLeast(0.05) * 1.08

    return WorkspaceBounds3D(
        minimum = Vec3(minX, minY, minZ),
        maximum = Vec3(maxX, maxY, maxZ),
        center = center,
        radius = radius
    )
}

internal fun aggregateWorkspaceRuns(
    runs: List<DiagnosticRunResult>,
    bounds: WorkspaceBounds3D,
    maximumDisplayedPoints: Int = DEFAULT_MAXIMUM_WORKSPACE_POINTS
): Pair<List<WorkspacePointAggregate>, Int?> {
    require(maximumDisplayedPoints > 0) {
        "maximumDisplayedPoints must be positive."
    }

    if (runs.size <= maximumDisplayedPoints) {
        return runs.mapIndexed { index, run ->
            WorkspacePointAggregate(
                id = "run-${run.seed}-${run.linkCount}-${run.runIndex}-$index",
                position = run.target,
                runCount = 1,
                acceptedCount = if (run.solverAccepted) 1 else 0,
                averageFinalError = run.finalError,
                averageIterations = run.iterations.toDouble(),
                averageJointLimitPressure = run.jointLimitPressureRatio,
                representativeRun = run
            )
        } to null
    }

    val resolution =
        floor(maximumDisplayedPoints.toDouble().pow(1.0 / 3.0))
            .toInt()
            .coerceAtLeast(1)
    val bins = linkedMapOf<WorkspaceVoxelKey, MutableWorkspaceAggregate>()

    runs.forEach { run ->
        val key =
            WorkspaceVoxelKey(
                x = workspaceBin(run.target.x, bounds.minimum.x, bounds.maximum.x, resolution),
                y = workspaceBin(run.target.y, bounds.minimum.y, bounds.maximum.y, resolution),
                z = workspaceBin(run.target.z, bounds.minimum.z, bounds.maximum.z, resolution)
            )
        bins.getOrPut(key) { MutableWorkspaceAggregate(run) }.add(run)
    }

    val points =
        bins.entries.map { (key, value) ->
            value.freeze(id = "voxel-${key.x}-${key.y}-${key.z}")
        }

    return points to resolution
}

internal fun buildDiagnosticWorkspace3DData(
    allRuns: List<DiagnosticRunResult>,
    filter: DiagnosticWorkspaceFilter,
    maximumDisplayedPoints: Int = DEFAULT_MAXIMUM_WORKSPACE_POINTS
): DiagnosticWorkspace3DData {
    val validRuns = validWorkspaceRuns(allRuns)
    val filteredRuns = filterWorkspaceRuns(validRuns, filter)
    val bounds = calculateWorkspaceBounds(filteredRuns)
    val (points, resolution) =
        aggregateWorkspaceRuns(
            runs = filteredRuns,
            bounds = bounds,
            maximumDisplayedPoints = maximumDisplayedPoints
        )

    return DiagnosticWorkspace3DData(
        points = points,
        bounds = bounds,
        totalRunCount = allRuns.size,
        validPositionCount = validRuns.size,
        filteredRunCount = filteredRuns.size,
        invalidPositionCount = allRuns.size - validRuns.size,
        voxelResolution = resolution
    )
}

internal fun workspaceMetricRange(
    points: List<WorkspacePointAggregate>,
    metric: WorkspaceColorMetric
): WorkspaceMetricRange {
    if (metric == WorkspaceColorMetric.OUTCOME) {
        return WorkspaceMetricRange(0.0, 1.0)
    }

    val values = points.map { it.value(metric) }.filter(Double::isFinite)
    if (values.isEmpty()) {
        return WorkspaceMetricRange(0.0, 1.0)
    }

    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: minimum
    return WorkspaceMetricRange(minimum, maximum)
}

internal fun workspaceGoodnessFraction(
    point: WorkspacePointAggregate,
    metric: WorkspaceColorMetric,
    range: WorkspaceMetricRange
): Double {
    if (metric == WorkspaceColorMetric.OUTCOME) {
        return point.acceptanceRate.coerceIn(0.0, 1.0)
    }

    val value = point.value(metric)
    val span = range.maximum - range.minimum
    if (!value.isFinite()) return 0.0
    if (!span.isFinite() || span <= 1e-12) return 1.0

    val normalized = ((value - range.minimum) / span).coerceIn(0.0, 1.0)
    return 1.0 - normalized
}

internal fun projectWorkspacePoint(
    point: Vec3,
    bounds: WorkspaceBounds3D,
    widthPx: Float,
    heightPx: Float,
    camera: WorkspaceCamera
): WorkspaceProjectedPoint {
    return projectWorkspacePoint(
        point = point,
        context = buildWorkspaceProjectionContext(bounds, widthPx, heightPx, camera)
    )
}

internal fun buildWorkspaceProjectionContext(
    bounds: WorkspaceBounds3D,
    widthPx: Float,
    heightPx: Float,
    camera: WorkspaceCamera
): WorkspaceProjectionContext {
    val safeWidth = widthPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeHeight = heightPx.takeIf { it.isFinite() && it > 0f } ?: 1f
    val safeZoom = camera.zoom.takeIf { it.isFinite() }?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: 1f
    val cosYaw = kotlin.math.cos(camera.yaw)
    val sinYaw = kotlin.math.sin(camera.yaw)
    val cosPitch = kotlin.math.cos(camera.pitch)
    val sinPitch = kotlin.math.sin(camera.pitch)

    return WorkspaceProjectionContext(
        bounds = bounds,
        safeWidth = safeWidth,
        safeHeight = safeHeight,
        cosYaw = cosYaw,
        sinYaw = sinYaw,
        cosPitch = cosPitch,
        sinPitch = sinPitch,
        pixelsPerMeter =
            (min(safeWidth, safeHeight) * 0.42f /
                    bounds.radius.toFloat().coerceAtLeast(1e-4f)) * safeZoom
    )
}

internal fun projectWorkspacePoint(
    point: Vec3,
    context: WorkspaceProjectionContext
): WorkspaceProjectedPoint {
    val relativeX = (point.x - context.bounds.center.x).toFloat()
    val relativeY = (point.y - context.bounds.center.y).toFloat()
    val relativeZ = (point.z - context.bounds.center.z).toFloat()

    val rotatedX = relativeX * context.cosYaw - relativeZ * context.sinYaw
    val yawDepth = relativeX * context.sinYaw + relativeZ * context.cosYaw
    val rotatedY = relativeY * context.cosPitch - yawDepth * context.sinPitch
    val depth = relativeY * context.sinPitch + yawDepth * context.cosPitch

    return WorkspaceProjectedPoint(
        x = context.safeWidth * 0.5f + rotatedX * context.pixelsPerMeter,
        y = context.safeHeight * 0.52f - rotatedY * context.pixelsPerMeter,
        depth = depth
    )
}

internal fun selectWorkspacePoint(
    points: List<WorkspacePointAggregate>,
    bounds: WorkspaceBounds3D,
    widthPx: Float,
    heightPx: Float,
    camera: WorkspaceCamera,
    touchX: Float,
    touchY: Float,
    maximumDistancePx: Float = 28f
): WorkspacePointAggregate? {
    val maxDistanceSquared = maximumDistancePx * maximumDistancePx
    val projectionContext = buildWorkspaceProjectionContext(bounds, widthPx, heightPx, camera)
    val candidateComparator =
        compareBy<WorkspaceSelectionCandidate> { it.distanceSquared }
            .thenByDescending { it.depth }
    var bestCandidate: WorkspaceSelectionCandidate? = null

    points.forEach { point ->
        val projected = projectWorkspacePoint(point.position, projectionContext)
        val dx = projected.x - touchX
        val dy = projected.y - touchY
        val candidate =
            WorkspaceSelectionCandidate(
                point = point,
                distanceSquared = dx * dx + dy * dy,
                depth = projected.depth
            )
        val currentBest = bestCandidate

        if (
            candidate.distanceSquared <= maxDistanceSquared &&
            (currentBest == null || candidateComparator.compare(candidate, currentBest) < 0)
        ) {
            bestCandidate = candidate
        }
    }

    return bestCandidate?.point
}

internal fun workspaceSphereRadius(
    pointCount: Int,
    maximumPointCount: Int
): Float {
    if (pointCount <= 1 || maximumPointCount <= 1) return 5.5f
    val normalized =
        sqrt(pointCount.toDouble() / maximumPointCount.toDouble())
            .coerceIn(0.0, 1.0)
    return (5.5 + normalized * 7.5).toFloat()
}

private data class WorkspaceVoxelKey(
    val x: Int,
    val y: Int,
    val z: Int
)

private data class WorkspaceSelectionCandidate(
    val point: WorkspacePointAggregate,
    val distanceSquared: Float,
    val depth: Float
)

private class MutableWorkspaceAggregate(
    val representativeRun: DiagnosticRunResult
) {
    private var count: Int = 0
    private var accepted: Int = 0
    private var xSum: Double = 0.0
    private var ySum: Double = 0.0
    private var zSum: Double = 0.0
    private var finalErrorSum: Double = 0.0
    private var finalErrorCount: Int = 0
    private var iterationsSum: Double = 0.0
    private var pressureSum: Double = 0.0
    private var pressureCount: Int = 0

    fun add(run: DiagnosticRunResult) {
        count += 1
        if (run.solverAccepted) accepted += 1
        xSum += run.target.x
        ySum += run.target.y
        zSum += run.target.z
        if (run.finalError.isFinite()) {
            finalErrorSum += run.finalError
            finalErrorCount += 1
        }
        iterationsSum += run.iterations.toDouble()
        if (run.jointLimitPressureRatio.isFinite()) {
            pressureSum += run.jointLimitPressureRatio
            pressureCount += 1
        }
    }

    fun freeze(id: String): WorkspacePointAggregate {
        val denominator = count.coerceAtLeast(1).toDouble()
        return WorkspacePointAggregate(
            id = id,
            position = Vec3(xSum / denominator, ySum / denominator, zSum / denominator),
            runCount = count,
            acceptedCount = accepted,
            averageFinalError =
                if (finalErrorCount > 0) finalErrorSum / finalErrorCount.toDouble() else Double.NaN,
            averageIterations = iterationsSum / denominator,
            averageJointLimitPressure =
                if (pressureCount > 0) pressureSum / pressureCount.toDouble() else Double.NaN,
            representativeRun = representativeRun
        )
    }
}

private fun workspaceBin(
    value: Double,
    minimum: Double,
    maximum: Double,
    resolution: Int
): Int {
    val span = maximum - minimum
    if (!span.isFinite() || span <= 1e-12) return 0
    return floor(((value - minimum) / span) * resolution.toDouble())
        .toInt()
        .coerceIn(0, resolution - 1)
}

internal const val DEFAULT_MAXIMUM_WORKSPACE_POINTS = 1_728
internal const val MIN_ZOOM = 0.55f
internal const val MAX_ZOOM = 8f
